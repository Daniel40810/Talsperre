package com.dan.ground;

/**
 * Eine Art der Bodendecke: ein Gras (Horst aus Halmen), eine Blume (Stängel, Blätter, Blüte), ein Stein
 * oder offene Erde. Alle Felder sind öffentlich und lassen sich ändern; die Voreinstellungen stehen als
 * statische Methoden bereit. Farben linear (nicht sRGB), Längen in Metern, Tage im Jahr 1..365.
 */
public final class Plant {
    public enum Kind { GRASS, FLOWER, STONE, SOIL }

    public final String name, latin;
    public final Kind kind;
    /** Höhe (Gras, Blume) bzw. Durchmesser (Stein, Erde) und Streuung ±. */
    public float size = 0.4f, sizeVar = 0.3f;
    /** Gras: Halme je Horst, Breite eines Halms, wie weit sie auseinanderstehen, Neigung nach außen. */
    public int blades = 7;
    public float bladeWidth = 0.012f, spread = 0.06f, lean = 0.35f;
    /** Wie steif (1 = mittel; Blumenstängel steifer, feines Gras weicher). */
    public float stiffness = 1;
    /** Gras: Farbe frisch, trocken (Hochsommer), im Winter; Spitzen heller. */
    public float[] green = {0.06f, 0.14f, 0.03f}, dry = {0.30f, 0.24f, 0.11f}, winter = {0.20f, 0.17f, 0.12f};
    /** Tage: ergrünt, beginnt zu trocknen, ganz trocken. */
    public int greenUp = 120, dryStart = 200, dryFull = 240;
    /** Blume: Blütenblätter (Anzahl, Farbe), Mitte, Größe der Blüte; Blütezeit (Beginn, Höhepunkt, Ende). */
    public int petals = 5;
    public float[] petal = {0.6f, 0.05f, 0.05f}, center = {0.5f, 0.35f, 0.02f};
    public float head = 0.03f;
    /** Blütenstand als Ähre (Lupine, Indian Paintbrush): Blüten entlang der Spitze statt einer Scheibe. */
    public boolean spike;
    public int bloomStart = 150, bloomPeak = 180, bloomEnd = 215;
    /** Stein, Erde: Farbe. */
    public float[] color = {0.25f, 0.24f, 0.22f};

    public Plant(String name, String latin, Kind kind) { this.name = name; this.latin = latin; this.kind = kind; }

    @Override public String toString() { return name; }

    // ------------------------------------------------------------ Gräser

    /** Idaho-Schwingel (Festuca idahoensis): blaugrüne Horste, trocken goldgelb; häufig in Yellowstone. */
    public static Plant idahoFescue() {
        Plant p = new Plant("Idaho-Schwingel", "Festuca idahoensis", Kind.GRASS);
        p.size = 0.35f; p.blades = 13; p.bladeWidth = 0.010f; p.spread = 0.06f; p.lean = 0.45f; p.stiffness = 0.8f;
        p.green = new float[]{0.07f, 0.13f, 0.06f}; p.dry = new float[]{0.34f, 0.27f, 0.12f};
        p.greenUp = 130; p.dryStart = 195; p.dryFull = 230;
        return p;
    }

    /** Ährenweizengras (Pseudoroegneria spicata): hohe, lockere Horste. */
    public static Plant wheatgrass() {
        Plant p = new Plant("Ährenweizengras", "Pseudoroegneria spicata", Kind.GRASS);
        p.size = 0.6f; p.blades = 8; p.bladeWidth = 0.012f; p.spread = 0.08f; p.lean = 0.3f; p.stiffness = 0.9f;
        p.green = new float[]{0.08f, 0.15f, 0.05f}; p.dry = new float[]{0.36f, 0.29f, 0.14f};
        p.greenUp = 125; p.dryStart = 200; p.dryFull = 240;
        return p;
    }

    /** Segge (Carex): dunkelgrün, bleibt lange frisch; in feuchten Senken und am Ufer. */
    public static Plant sedge() {
        Plant p = new Plant("Segge", "Carex", Kind.GRASS);
        p.size = 0.45f; p.blades = 10; p.bladeWidth = 0.013f; p.spread = 0.06f; p.lean = 0.5f; p.stiffness = 0.7f;
        p.green = new float[]{0.04f, 0.11f, 0.03f}; p.dry = new float[]{0.20f, 0.18f, 0.08f};
        p.greenUp = 115; p.dryStart = 245; p.dryFull = 285;
        return p;
    }

    /** Wiesen-Rispengras (Poa pratensis): dichte, weiche Wiese in Mitteleuropa. */
    public static Plant meadowGrass() {
        Plant p = new Plant("Wiesen-Rispengras", "Poa pratensis", Kind.GRASS);
        p.size = 0.3f; p.blades = 14; p.bladeWidth = 0.011f; p.spread = 0.08f; p.lean = 0.4f; p.stiffness = 0.75f;
        p.green = new float[]{0.06f, 0.16f, 0.03f}; p.dry = new float[]{0.24f, 0.22f, 0.10f};
        p.greenUp = 80; p.dryStart = 215; p.dryFull = 260;
        return p;
    }

    // ------------------------------------------------------------ Blumen

    /** Indian Paintbrush (Castilleja): leuchtend rote Hochblätter an der Spitze. */
    public static Plant paintbrush() {
        Plant p = new Plant("Indian Paintbrush", "Castilleja miniata", Kind.FLOWER);
        p.size = 0.35f; p.spike = true; p.head = 0.07f; p.petal = new float[]{0.62f, 0.06f, 0.03f}; p.center = new float[]{0.5f, 0.08f, 0.03f};
        p.bloomStart = 165; p.bloomPeak = 190; p.bloomEnd = 225; p.stiffness = 1.3f;
        return p;
    }

    /** Silberlupine (Lupinus argenteus): violettblaue Ähre. */
    public static Plant lupine() {
        Plant p = new Plant("Silberlupine", "Lupinus argenteus", Kind.FLOWER);
        p.size = 0.45f; p.spike = true; p.head = 0.11f; p.petal = new float[]{0.14f, 0.10f, 0.45f}; p.center = new float[]{0.20f, 0.15f, 0.50f};
        p.bloomStart = 160; p.bloomPeak = 185; p.bloomEnd = 215; p.stiffness = 1.3f;
        return p;
    }

    /** Pfeilblättrige Balsamwurzel (Balsamorhiza sagittata): große gelbe Sonnen im Frühsommer. */
    public static Plant balsamroot() {
        Plant p = new Plant("Balsamwurzel", "Balsamorhiza sagittata", Kind.FLOWER);
        p.size = 0.35f; p.petals = 13; p.head = 0.05f; p.petal = new float[]{0.80f, 0.55f, 0.02f}; p.center = new float[]{0.45f, 0.30f, 0.02f};
        p.bloomStart = 140; p.bloomPeak = 160; p.bloomEnd = 185; p.stiffness = 1.4f;
        return p;
    }

    /** Glockenblume (Campanula rotundifolia): kleine hellblaue Glocken, spät im Sommer. */
    public static Plant harebell() {
        Plant p = new Plant("Rundblättrige Glockenblume", "Campanula rotundifolia", Kind.FLOWER);
        p.size = 0.3f; p.petals = 5; p.head = 0.025f; p.petal = new float[]{0.20f, 0.25f, 0.62f}; p.center = new float[]{0.3f, 0.35f, 0.7f};
        p.bloomStart = 190; p.bloomPeak = 215; p.bloomEnd = 250; p.stiffness = 0.8f;
        return p;
    }

    /** Margerite (Leucanthemum vulgare): weiß mit gelber Mitte. */
    public static Plant oxeyeDaisy() {
        Plant p = new Plant("Margerite", "Leucanthemum vulgare", Kind.FLOWER);
        p.size = 0.45f; p.petals = 16; p.head = 0.035f; p.petal = new float[]{0.85f, 0.85f, 0.80f}; p.center = new float[]{0.75f, 0.55f, 0.02f};
        p.bloomStart = 140; p.bloomPeak = 165; p.bloomEnd = 200; p.stiffness = 1.1f;
        return p;
    }

    /** Klatschmohn (Papaver rhoeas): große rote Blüte, dunkle Mitte. */
    public static Plant poppy() {
        Plant p = new Plant("Klatschmohn", "Papaver rhoeas", Kind.FLOWER);
        p.size = 0.5f; p.petals = 4; p.head = 0.045f; p.petal = new float[]{0.70f, 0.03f, 0.02f}; p.center = new float[]{0.03f, 0.03f, 0.03f};
        p.bloomStart = 145; p.bloomPeak = 170; p.bloomEnd = 205; p.stiffness = 0.9f;
        return p;
    }

    /** Kornblume (Centaurea cyanus): tiefblau. */
    public static Plant cornflower() {
        Plant p = new Plant("Kornblume", "Centaurea cyanus", Kind.FLOWER);
        p.size = 0.5f; p.petals = 9; p.head = 0.028f; p.petal = new float[]{0.06f, 0.14f, 0.62f}; p.center = new float[]{0.10f, 0.08f, 0.35f};
        p.bloomStart = 155; p.bloomPeak = 180; p.bloomEnd = 225; p.stiffness = 1.0f;
        return p;
    }

    /** Löwenzahn (Taraxacum officinale): gelb im Frühling, dann weiße Pusteblumen. */
    public static Plant dandelion() {
        Plant p = new Plant("Löwenzahn", "Taraxacum officinale", Kind.FLOWER);
        p.size = 0.22f; p.petals = 18; p.head = 0.028f; p.petal = new float[]{0.85f, 0.62f, 0.02f}; p.center = new float[]{0.8f, 0.55f, 0.02f};
        p.bloomStart = 95; p.bloomPeak = 120; p.bloomEnd = 150; p.stiffness = 1.2f;
        return p;
    }

    // ------------------------------------------------------------ Steine und Erde

    /** Kiesel: klein, rund, grau bis braun. */
    public static Plant pebble() {
        Plant p = new Plant("Kiesel", "", Kind.STONE);
        p.size = 0.07f; p.sizeVar = 0.5f; p.color = new float[]{0.15f, 0.135f, 0.12f};
        return p;
    }

    /** Stein: faustgroß bis kopfgroß, eckig. */
    public static Plant stone() {
        Plant p = new Plant("Stein", "", Kind.STONE);
        p.size = 0.25f; p.sizeVar = 0.6f; p.color = new float[]{0.13f, 0.12f, 0.11f};
        return p;
    }

    /** Offene Erde: kahle Stelle (Tritt, Wühlstelle, Suhle). */
    public static Plant soil() {
        Plant p = new Plant("Offene Erde", "", Kind.SOIL);
        p.size = 1.2f; p.sizeVar = 0.5f; p.color = new float[]{0.13f, 0.10f, 0.07f};
        return p;
    }
}
