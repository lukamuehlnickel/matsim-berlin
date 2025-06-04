package org.matsim.run;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.network.*;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.utils.geometry.geotools.MGC;
import picocli.CommandLine;

import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToDoubleFunction;

/**
 * Extends the basic AdjustNetwork tool with support for additional attributes in the *shape* file:
 * <ul>
 *     <li><b>pkw</b> – if true, allows the full motor-vehicle set (bike, car, freight, ride, truck)</li>
 *     <li><b>fahrrad</b> – if true, adds <code>bike</code> to the allowed modes</li>
 *     <li><b>speed</b> – is converted from km/h to m/s and written both as the link’s freespeed and
 *         a custom attribute <code>allowed_speed</code></li>
 *     <li><b>capacity</b> – link capacity in veh/h (fallback = 1 800)</li>
 *     <li><b>twoWay / oneway</b> – directionality. If a boolean field <code>twoWay</code> exists, its value is used.
 *         Otherwise a <code>oneway</code> field is interpreted (true ⇒ one-way, false ⇒ two-way). If neither is
 *         present, the link is treated as <em>one-way</em> (no reverse link).</li>
 *     <li><b>type</b> – copied unchanged into the link. If <code>Link#setType(String)</code> is available at runtime
 *         it will be invoked; otherwise the value is stored as a custom attribute <code>type</code>.</li>
 *     <li><b>node_type</b> – copied for *nodes* only and stored under the attribute name <code>type</code>.
 *         The value is <em>not</em> written to links.</li>
 * </ul>
 */
@CommandLine.Command(
        name = "adjust-network",
        description = "Delete links, then extend network via shapefile (fuzzy matching + attributes)"
)
public class AdjustNetwork implements MATSimAppCommand {

    /* ── CLI ── */
    @CommandLine.Option(names = "--network", required = true) Path networkFile;
    @CommandLine.Option(names = "--remove-links")             Path removeCsv;
    @CommandLine.Mixin                                        ShpOptions shp = new ShpOptions();
    @CommandLine.Option(names = "--matching-distance", defaultValue = "10") double matchRadius;
    @CommandLine.Option(names = "--output", required = true)  Path output;

    /* ── intern ── */
    private final Logger log = LogManager.getLogger(getClass());
    private Network network;
    private final AtomicLong nodeCounter = new AtomicLong();

    public static void main(String[] a) { new AdjustNetwork().execute(a); }

    @Override public Integer call() throws Exception {

        network = NetworkUtils.readNetwork(networkFile.toString());

        if (removeCsv != null) deleteLinks();
        if (shp.isDefined())   addLinksFromShape();

        new NetworkWriter(network).write(output.toString());
        log.info("✓ wrote {}", output);
        return 0;
    }

    /* ----- Links löschen + verwaiste Nodes ------ */
    private void deleteLinks() throws Exception {
        var ids = Files.readAllLines(removeCsv).stream()
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        ids.forEach(id -> network.removeLink(Id.createLinkId(id)));
        log.info("• removed {} links", ids.size());

        var lonely = network.getNodes().values().stream()
                .filter(n -> n.getInLinks().isEmpty() && n.getOutLinks().isEmpty())
                .map(Node::getId).toList();
        lonely.forEach(network::removeNode);
        log.info("• removed {} isolated nodes", lonely.size());
    }

    /* ----- Shape-Import mit Quadtree-Index ------ */
    private void addLinksFromShape() {

        /* dynamischer Index */
        Quadtree idx = new Quadtree();
        network.getNodes().values().forEach(n ->
                idx.insert(MGC.coord2Point(n.getCoord()).getEnvelopeInternal(), n));

        /* 1. Features einsammeln */
        List<SimpleFeature> feats = new ArrayList<>();
        shp.readFeatures().forEach(feats::add);

        /* 2. Sortieren: 'priority'-Links zuerst */
        feats.sort(Comparator.comparingInt(f -> {
            String t = Objects.toString(f.getAttribute("node_type"), "");
            return "priority".equalsIgnoreCase(t) ? 0 : 1;
        }));

        /* 3. Abarbeiten */
        long added = 0;
        for (SimpleFeature f : feats) {
            Geometry g = (Geometry) f.getDefaultGeometry();
            if (g instanceof LineString ls) {
                added += processLine(ls, f, idx);
            } else if (g instanceof MultiLineString mls) {
                for (int i = 0; i < mls.getNumGeometries(); i++)
                    added += processLine((LineString) mls.getGeometryN(i), f, idx);
            }
        }
        log.info("• added {} links (incl. reverse)", added);
    }

    /* ----- einzelne Linie  ----- */
    private long processLine(LineString ls, SimpleFeature f, Quadtree idx) {

        Node from = matchNode(idx, ls.getCoordinateN(0));
        Node to   = matchNode(idx, ls.getCoordinateN(ls.getNumPoints() - 1));

        Id<Link> id = Id.createLinkId(f.getID());
        if (network.getLinks().containsKey(id))
            id = Id.createLinkId(id + "_dup" + UUID.randomUUID());

        Link link = network.getFactory().createLink(id, from, to);
        setAttrs(link, ls, f);

        /* ----- node_type nur für Nodes als „type“ übernehmen ----- */
        String nodeType = Objects.toString(f.getAttribute("node_type"), "").trim();
        if (!nodeType.isEmpty()) {
            if (from.getAttributes().getAttribute("type") == null)
                from.getAttributes().putAttribute("type", nodeType);
            if (to.getAttributes().getAttribute("type") == null)
                to.getAttributes().putAttribute("type", nodeType);
        }

        network.addLink(link);

        boolean twoWay = determineTwoWay(f);
        if (twoWay) {
            Link rev = network.getFactory().createLink(Id.createLinkId("-" + id), to, from);
            copyAttrs(link, rev);
            network.addLink(rev);
            return 2;
        }
        return 1;
    }

    /* ----- Standard- und Shape-Attribute übernehmen ----- */
    private void setAttrs(Link l, LineString geom, SimpleFeature f) {
        double speedKmh = Optional.ofNullable((Number) f.getAttribute("Speed"))
                .map(Number::doubleValue).orElse(30.0);
        double speedMs  = speedKmh / 3.6;
        double capacity = Optional.ofNullable((Number) f.getAttribute("capacity"))
                .map(Number::doubleValue).orElse(1800.0);
        int lanes = Optional.ofNullable((Number) f.getAttribute("lanes"))
                .map(Number::intValue).orElse(1);

        l.setLength(geom.getLength());
        l.setFreespeed(speedMs);
        l.setCapacity(capacity);
        l.setNumberOfLanes(lanes);

        Set<String> modes = new HashSet<>();
        if (isTrue(f.getAttribute("pkw")))     modes.addAll(Set.of("bike", "car", "freight", "ride", "truck"));
        if (isTrue(f.getAttribute("fahrrad"))) modes.add("bike");
        if (modes.isEmpty())                   modes.add("car");
        l.setAllowedModes(modes);

        applyType(l, Objects.toString(f.getAttribute("type"), "").trim());

        l.getAttributes().putAttribute("allowed_speed", speedMs);
        l.getAttributes().putAttribute("capacity_shape", capacity);
    }

    /** Trägt den Typ in das Link-Objekt oder alternativ als Custom-Attribut. */
    private static void applyType(Link l, String typeAttr) {
        if (typeAttr == null || typeAttr.isEmpty()) return;
        try {
            Method m = l.getClass().getMethod("setType", String.class);
            m.invoke(l, typeAttr);
        } catch (ReflectiveOperationException e) {
            l.getAttributes().putAttribute("type", typeAttr);
        }
    }

    /*  Attribute in den Reverse-Link übernehmen */
    private void copyAttrs(Link a, Link b) {
        b.setLength(a.getLength());
        b.setFreespeed(a.getFreespeed());
        b.setCapacity(a.getCapacity());
        b.setNumberOfLanes(a.getNumberOfLanes());
        b.setAllowedModes(new HashSet<>(a.getAllowedModes()));

        try {
            Method getType = a.getClass().getMethod("getType");
            Object t = getType.invoke(a);
            if (t instanceof String s && !s.isEmpty()) {
                applyType(b, s);
            }
        } catch (ReflectiveOperationException ignored) {
            Object s = a.getAttributes().getAttribute("type");
            if (s instanceof String str && !str.isEmpty()) {
                applyType(b, str);
            }
        }

        a.getAttributes().getAsMap().forEach((k, v) -> b.getAttributes().putAttribute(k, v));
    }

    /* ---- Richtung bestimmen ---- */
    private boolean determineTwoWay(SimpleFeature f) {
        Object twoWayAttr = f.getAttribute("twoWay");
        if (twoWayAttr != null) return isTrue(twoWayAttr);
        Object onewayAttr = f.getAttribute("oneway");
        if (onewayAttr != null) return !isTrue(onewayAttr);
        return false; // Default: einspurig
    }

    /* ----- fuzzy Matching  (legt fehlende Nodes an) ----- */
    @SuppressWarnings("unchecked")
    private Node matchNode(Quadtree idx, Coordinate c) {

        Point p = MGC.coordinate2Point(c);
        double r = matchRadius;

        ToDoubleFunction<Node> dist = n ->
                NetworkUtils.getEuclideanDistance(
                        n.getCoord(), MGC.coordinate2Coord(c));

        /* 1. Roh-Liste aus dem Quadtree holen */
        List<?> raw = idx.query(p.buffer(r).getEnvelopeInternal());

        /* 2. Nur echte Nodes behalten und sortieren */
        List<Node> cand = raw.stream()
                .filter(Node.class::isInstance)
                .map(Node.class::cast)
                .filter(n -> dist.applyAsDouble(n) < r)
                .sorted(Comparator.comparingDouble(dist))
                .toList();

        /* 3. Treffer? → den nächsten zurückgeben */
        if (!cand.isEmpty())
            return cand.get(0);

        /* 4. Kein Treffer → neuen Node anlegen & indexen */
        Node newN = network.getFactory().createNode(
                Id.createNodeId("ext_n" + nodeCounter.incrementAndGet()),
                MGC.coordinate2Coord(c));
        network.addNode(newN);
        idx.insert(p.getEnvelopeInternal(), newN);
        return newN;
    }

    /* ---- Util: boolean-Feld aus dem Shape robust auslesen ---- */
    private static boolean isTrue(Object attr) {
        if (attr == null) return false;
        if (attr instanceof Boolean b) return b;
        String s = attr.toString().trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
    }
}