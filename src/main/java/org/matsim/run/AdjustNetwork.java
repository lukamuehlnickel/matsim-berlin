package org.matsim.run;

import org.apache.logging.log4j.*;
import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.quadtree.Quadtree;               // ★ Quadtree
import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.network.*;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.utils.geometry.geotools.MGC;
import picocli.CommandLine;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToDoubleFunction;

@CommandLine.Command(name = "adjust-network",
        description = "Delete links, then extend network via shapefile (fuzzy matching + attributes)")
public class AdjustNetwork implements MATSimAppCommand {

    /* ── CLI ── */
    @CommandLine.Option(names="--network", required=true) Path networkFile;
    @CommandLine.Option(names="--remove-links")            Path removeCsv;
    @CommandLine.Mixin                                     ShpOptions shp = new ShpOptions();
    @CommandLine.Option(names="--matching-distance", defaultValue="10") double matchRadius;
    @CommandLine.Option(names="--output", required=true)   Path output;

    /* ── intern ── */
    private final Logger log = LogManager.getLogger(getClass());
    private Network network;
    private final AtomicLong nodeCounter = new AtomicLong();

    public static void main(String[] a){ new AdjustNetwork().execute(a); }

    @Override public Integer call() throws Exception {

        network = NetworkUtils.readNetwork(networkFile.toString());

        if (removeCsv!=null) deleteLinks();
        if (shp.isDefined()) addLinksFromShape();

        new NetworkWriter(network).write(output.toString());
        log.info("✓ wrote {}", output);
        return 0;
    }

    /* ----- Links löschen + verwaiste Nodes ------ */
    private void deleteLinks() throws Exception {
        var ids = Files.readAllLines(removeCsv).stream()
                .map(String::trim).filter(s->!s.isEmpty()).toList();
        ids.forEach(id -> network.removeLink(Id.createLinkId(id)));
        log.info("• removed {} links", ids.size());

        var lonely = network.getNodes().values().stream()
                .filter(n->n.getInLinks().isEmpty()&&n.getOutLinks().isEmpty())
                .map(Node::getId).toList();
        lonely.forEach(network::removeNode);
        log.info("• removed {} isolated nodes", lonely.size());
    }

    /* ----- Shape-Import mit Quadtree-Index ------ */
    private void addLinksFromShape() {

        /* dynamischer Index */
        Quadtree idx = new Quadtree();                                            // ★
        network.getNodes().values().forEach(n ->
                idx.insert(MGC.coord2Point(n.getCoord()).getEnvelopeInternal(), n));

        long added = 0;

        for (SimpleFeature f : shp.readFeatures()) {

            Geometry g = (Geometry) f.getDefaultGeometry();
            if (g instanceof LineString ls) {
                added += processLine(ls, f, idx);
            } else if (g instanceof MultiLineString mls) {
                for (int i=0;i<mls.getNumGeometries();i++)
                    added += processLine((LineString) mls.getGeometryN(i), f, idx);
            }
        }
        log.info("• added {} links (incl. reverse)", added);
    }

    /* ----- einzelne Linie  ----- */
    private long processLine(LineString ls, SimpleFeature f, Quadtree idx){

        Node from = matchNode(idx, ls.getCoordinateN(0));
        Node to   = matchNode(idx, ls.getCoordinateN(ls.getNumPoints()-1));

        Id<Link> id = Id.createLinkId(f.getID());
        if (network.getLinks().containsKey(id))
            id = Id.createLinkId(id+"_dup"+UUID.randomUUID());

        Link link = network.getFactory().createLink(id, from, to);
        setAttrs(link, ls, f);
        network.addLink(link);

        boolean twoWay = !"yes".equalsIgnoreCase(
                Objects.toString(f.getAttribute("oneway"),""));
        if (twoWay){
            Link rev = network.getFactory().createLink(Id.createLinkId(id+"_rev"), to, from);
            copyAttrs(link, rev);
            network.addLink(rev);
            return 2;
        }
        return 1;
    }

    /* ----- Attribute ----- */
    private void setAttrs(Link l, LineString geom, SimpleFeature f){
        double v = Optional.ofNullable((Number)f.getAttribute("speed"))
                .map(Number::doubleValue).orElse(50.0);
        l.setLength(geom.getLength());
        l.setFreespeed(v/3.6);
        l.setCapacity(1800);
        l.setNumberOfLanes(1);
        l.setAllowedModes(Set.of("car"));
    }
    private void copyAttrs(Link a, Link b){
        b.setLength(a.getLength());
        b.setFreespeed(a.getFreespeed());
        b.setCapacity(a.getCapacity());
        b.setNumberOfLanes(a.getNumberOfLanes());
        b.setAllowedModes(a.getAllowedModes());
    }

    /* ----- fuzzy Matching  (legt fehlende Nodes an) ----- */
    @SuppressWarnings("unchecked")
    private Node matchNode(Quadtree idx, Coordinate c){

        Point p = MGC.coordinate2Point(c);
        double r = matchRadius;

        ToDoubleFunction<Object> dist = n ->
                NetworkUtils.getEuclideanDistance(((Node)n).getCoord(),
                        MGC.coordinate2Coord(c));

        var cand = idx.query(p.buffer(r).getEnvelopeInternal()).stream()
                .map(Node.class::cast)
                .filter(n->dist.applyAsDouble(n)<r)
                .sorted(Comparator.comparingDouble(dist))
                .toList();

        if (!cand.isEmpty()) return (Node) cand.getFirst();

        /* kein Treffer → neuen Node anlegen & indexen */
        Node newN = network.getFactory().createNode(
                Id.createNodeId("ext_n"+nodeCounter.incrementAndGet()),
                MGC.coordinate2Coord(c));
        network.addNode(newN);
        idx.insert(p.getEnvelopeInternal(), newN);                             // ★
        return newN;
    }
}
