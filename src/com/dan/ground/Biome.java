package com.dan.ground;

import java.util.ArrayList;
import java.util.List;

/**
 * Welche Arten wie dicht wachsen. Jeder Eintrag nennt die Art, die Schicht der Bedeckung, aus der sie
 * ihre Dichte nimmt (0 Gras, 1 Blumen, 2 Steine, 3 Erde), die Dichte je Quadratmeter bei voller
 * Bedeckung und ob sie es feucht (+1) oder trocken (−1) mag.
 */
public final class Biome {
    public final String name;

    public static final class Entry {
        public final Plant plant;
        public final int layer;
        public float perM2;
        public final float moisture;

        Entry(Plant plant, int layer, float perM2, float moisture) { this.plant = plant; this.layer = layer; this.perM2 = perM2; this.moisture = moisture; }
    }

    public final List<Entry> entries = new ArrayList<>();

    public Biome(String name) { this.name = name; }

    public Biome add(Plant p, int layer, float perM2, float moisture) { entries.add(new Entry(p, layer, perM2, moisture)); return this; }

    @Override public String toString() { return name; }

    /** Bergwiese in Yellowstone: Schwingel und Weizengras, Seggen in den Senken, Sommerblumen, Geröll. */
    public static Biome yellowstone() {
        return new Biome("Wiese · Yellowstone")
                .add(Plant.idahoFescue(), 0, 9.0f, -0.3f).add(Plant.wheatgrass(), 0, 3.0f, -0.5f).add(Plant.sedge(), 0, 4.0f, 1)
                .add(Plant.lupine(), 1, 2.0f, 0).add(Plant.paintbrush(), 1, 1.4f, -0.3f).add(Plant.balsamroot(), 1, 0.7f, -0.6f)
                .add(Plant.harebell(), 1, 1.6f, 0)
                .add(Plant.pebble(), 2, 14, 0).add(Plant.stone(), 2, 1.2f, 0)
                .add(Plant.soil(), 3, 0.25f, 0);
    }

    /** Blumenwiese in Mitteleuropa: dichtes Rispengras, Margeriten, Mohn, Kornblumen, Löwenzahn. */
    public static Biome europe() {
        return new Biome("Wiese · Mitteleuropa")
                .add(Plant.meadowGrass(), 0, 14.0f, 0).add(Plant.sedge(), 0, 2.0f, 1)
                .add(Plant.oxeyeDaisy(), 1, 2.5f, 0).add(Plant.poppy(), 1, 1.3f, -0.5f).add(Plant.cornflower(), 1, 1.3f, -0.3f)
                .add(Plant.dandelion(), 1, 2.5f, 0.2f)
                .add(Plant.pebble(), 2, 8, 0).add(Plant.stone(), 2, 0.6f, 0)
                .add(Plant.soil(), 3, 0.2f, 0);
    }

    /** Kiesbank am Fluss: viele Kiesel und Steine, wenig Gras, Seggen am Wasser. */
    public static Biome gravelBar() {
        return new Biome("Kiesbank")
                .add(Plant.sedge(), 0, 2.0f, 1).add(Plant.wheatgrass(), 0, 0.6f, -1)
                .add(Plant.harebell(), 1, 0.3f, 0)
                .add(Plant.pebble(), 2, 60, 0).add(Plant.stone(), 2, 5, 0)
                .add(Plant.soil(), 3, 0.1f, 0);
    }
}
