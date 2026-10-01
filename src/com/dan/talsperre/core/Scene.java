package com.dan.talsperre.core;

/**
 * Szene: das Dreiecksnetz von Gelände, Fluss und allem, was darauf steht, dazu Gelände und Quellen
 * für den Bildrechner und die beschrifteten Stellen. Himmel und Luft rechnet der Bildrechner dazu.
 */
public final class Scene {
    public final String name;
    public final Mesh mesh;
    public final Terrain terrain;
    public final Thermal thermal;
    /** Punktlichter (Lampen, Fenster); kann fehlen. */
    public com.dan.talsperre.effects.Lamps lamps;
    /** Leben auf Straße, See und in der Luft (Phase 8). */
    public com.dan.talsperre.world.Life life;

    public Scene(String name, Mesh mesh, Terrain terrain, Thermal thermal) {
        this.name = name;
        this.mesh = mesh;
        this.terrain = terrain;
        this.thermal = thermal;
    }

    /** Eine beschriftete Stelle: Name, feste Zeile, Zeile mit dem Zustand (wird laufend gesetzt), Ort. */
    public static final class Marker {
        public final String name;
        /** Zeile unter dem Namen; mit Datenbank aus GEY_SITE.tafel. */
        public volatile String line;
        public volatile String live;
        public final double x, y, z;
        /** Ort 0 = Upper Geyser Basin, 1 = Midway. */
        public final int site;

        public Marker(String name, String line, double x, double y, double z, int site) {
            this.name = name; this.line = line; this.x = x; this.y = y; this.z = z; this.site = site;
        }
    }

    public final java.util.List<Marker> markers = new java.util.ArrayList<>();
    /** Anzahl Bäume und tote Stämme (für die Statuszeile). */
    public int trees, snags;

    public int triangles() { return mesh == null ? 0 : mesh.nt; }
}
