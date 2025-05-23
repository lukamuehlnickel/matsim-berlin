package org.matsim.run;

import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.*;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.gis.ShapeFileReader;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

// berlin-v6.4-network-with-pt.xml.gz
public class SchumacherQuartier {

    /* ─ Dateipfade ─ */
    private static final String INPUT_NET  = "C:/Users/lukam/Downloads/berlin-v6.4-network-with-pt.xml.gz";
    private static final String SHP_FILE   = "input/schumacherquartier/roads.shp";
    private static final String OUTPUT_NET = "output/modified_berlin-v6.4-pt.xml.gz";
    /* ─────────────── */

    private final Network network          = NetworkUtils.readNetwork(INPUT_NET);
    private final Map<String, Node> nodeIdx = new HashMap<>();
    private final AtomicLong linkCounter    = new AtomicLong();
    private final AtomicLong nodeCounter    = new AtomicLong();

    public static void main(String[] args) {
        new SchumacherQuartier().run();
    }

    /* ===== Hauptablauf ===== */
    private void run() {

        /* 1) vorhandene Nodes indizieren */
        for (Node n : network.getNodes().values()) {
            nodeIdx.put(keyFor(n.getCoord()), n);
        }
        linkCounter.set(network.getLinks().size());

        /* 2) Shapefile komplett einlesen (keine Streaming-Variante) */
        for (SimpleFeature feat : ShapeFileReader.getAllFeatures(SHP_FILE)) {

            /* ★ Geometrietyp prüfen */
            Geometry geom = (Geometry) feat.getDefaultGeometry();

            if (geom instanceof LineString ls) {
                processLineString(ls, feat);              // ★
            }
            else if (geom instanceof MultiLineString mls) {
                for (int g = 0; g < mls.getNumGeometries(); g++) {
                    processLineString((LineString) mls.getGeometryN(g), feat);  // ★
                }
            }
            /* Punkte, Polygone o. Ä. werden ignoriert */
        }

        /* 3) erweitertes Netz schreiben */
        new NetworkWriter(network).write(OUTPUT_NET);
        System.out.println("→ Neues Netzwerk: " + OUTPUT_NET);
    }

    /* ===== Verarbeitung einer einzelnen Linie ===== */
    private void processLineString(LineString ls, SimpleFeature feat) {           // ★
        Coordinate[] pts = ls.getCoordinates();

        Node from = getOrCreate(pts[0]);
        for (int i = 1; i < pts.length; i++) {
            Node to = getOrCreate(pts[i]);

            boolean twoWay = !"yes".equalsIgnoreCase(
                    Objects.toString(feat.getAttribute("oneway"), ""));

            addConnection(from, to, twoWay, ls.getLength(), feat);
            from = to;
        }
    }

    /* ===== Nodes ===== */
    private Node getOrCreate(Coordinate c) {
        Coord coord = new Coord(c.x, c.y);
        String key  = keyFor(coord);

        return nodeIdx.computeIfAbsent(key, k -> {
            Node n = network.getFactory().createNode(
                    Id.createNodeId("ext_n" + nodeCounter.incrementAndGet()), coord);
            network.addNode(n);
            return n;
        });
    }
    private String keyFor(Coord coord) {
        return String.format(Locale.US, "%.2f_%.2f", coord.getX(), coord.getY());
    }

    /* ===== Links ===== */
    private void addConnection(Node from, Node to, boolean twoWay,
                               double shpLen, SimpleFeature feat) {

        createAndAddLink(from, to, "ab", shpLen, feat);
        if (twoWay) createAndAddLink(to, from, "ba", shpLen, feat);
    }

    private void createAndAddLink(Node from, Node to, String suffix,
                                  double shpLen, SimpleFeature feat) {

        String id = "ext_l" + linkCounter.incrementAndGet() + "_" + suffix;
        if (network.getLinks().containsKey(Id.createLinkId(id))) return;

        Link l = network.getFactory().createLink(Id.createLinkId(id), from, to);

        double len = (shpLen > 0)
                ? shpLen
                : CoordUtils.calcEuclideanDistance(from.getCoord(), to.getCoord());
        l.setLength(len);

        double v = Optional.ofNullable((Number) feat.getAttribute("speed"))
                .map(Number::doubleValue).orElse(50.0);
        l.setFreespeed(v / 3.6);
        l.setCapacity(1800);
        l.setNumberOfLanes(1);
        l.setAllowedModes(Set.of("car", "bike"));

        network.addLink(l);
    }
}