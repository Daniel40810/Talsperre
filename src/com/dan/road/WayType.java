package com.dan.road;

/**
 * Art eines Weges mit Querschnitt, Belag, Markierung, Ausstattung und Trassierung. Die vier
 * Voreinstellungen {@link #path()}, {@link #track()}, {@link #road()} und {@link #motorway()} liefern je
 * Aufruf eine frische Kopie; alle Werte sind öffentlich und lassen sich ändern.
 */
public final class WayType {
    public enum Kind { PATH, TRACK, ROAD, MOTORWAY }
    public enum Paving { DIRT, GRAVEL, ASPHALT }
    /** Markierung: keine, amerikanisch (gelbe Doppellinie in der Mitte), europäisch (weiß gestrichelt). */
    public enum Marking { NONE, US, EU }

    public String name;
    public Kind kind;
    public Paving paving;
    public Marking marking = Marking.NONE;

    // ---- Querschnitt (Meter)
    /** Fahrstreifen je Richtung (0: ein gemeinsamer Streifen, etwa Pfad und Feldweg). */
    public int lanes;
    /** Breite eines Fahrstreifens, bei Pfad und Feldweg die ganze Breite. */
    public float laneWidth;
    /** Befestigter Randstreifen je Seite (Seitenstreifen der Autobahn). */
    public float shoulder;
    /** Bankett je Seite: Kies oder Gras, bevor die Böschung beginnt. */
    public float verge;
    /** Mittelstreifen zwischen den Richtungsfahrbahnen (Autobahn). */
    public float median;
    /** Dachgefälle der Fahrbahn (Anteil, 0,02 = 2 %). */
    public float crown = 0.02f;
    /** Wie hoch die Fahrbahn über dem umgebenden Boden liegt. */
    public float thickness = 0.12f;
    /** Feldweg: Abstand der beiden Fahrspuren und Breite einer Spur; der Streifen dazwischen ist Gras. */
    public float rutSpacing = 1.7f, rutWidth = 0.5f;
    /** Pfad: wie stark die Breite schwankt (Anteil). */
    public float wander;

    // ---- Trassierung
    /** Größte Steigung (Anteil) und Länge, über die das Höhenprofil geglättet wird (m). */
    public float maxGrade, smoothing;
    /** Größte Querneigung in Kurven (Anteil) und Entwurfsgeschwindigkeit (m/s). */
    public float maxBank, designSpeed;
    /** Höchstgeschwindigkeit (m/s) und seitliche Beschleunigung, die Fahrer in Kurven zulassen (m/s²). */
    public float speedLimit, lateralAccel = 2.2f;
    /** Lichte Höhe von Brücken über Wasser (m). */
    public float clearance;
    /** Unebenheit für das Federn der Fahrzeuge (0 glatt .. 1 holprig). */
    public float roughness;

    // ---- Ausstattung
    public boolean guardrail, medianBarrier, posts, snowPoles, signs;

    // ---- Farben (linear)
    public float[] surface, worn, vergeColor, lineWhite = {0.62f, 0.62f, 0.58f}, lineYellow = {0.55f, 0.36f, 0.05f};
    /** Wie alt der Belag ist: 0 frisch und dunkel, 1 grau mit Flicken und Rissen. */
    public float age = 0.5f;

    /** Halbe Breite bis zum Ende des Banketts. */
    public float half() {
        if (kind == Kind.PATH || kind == Kind.TRACK) return laneWidth / 2 + verge;
        return median / 2 + lanes * laneWidth + shoulder + verge;
    }

    /** Halbe Breite der befestigten Fläche (ohne Bankett). */
    public float paved() {
        if (kind == Kind.PATH || kind == Kind.TRACK) return laneWidth / 2;
        return median / 2 + lanes * laneWidth + shoulder;
    }

    /** Mitte des Fahrstreifens k (0 = außen .. lanes−1 = innen) in Fahrtrichtung rechts, als Abstand zur Achse. */
    public float laneCenter(int k) {
        if (kind == Kind.PATH) return laneWidth * 0.2f;
        if (kind == Kind.TRACK) return 0;
        return median / 2 + (lanes - 1 - k + 0.5f) * laneWidth;
    }

    private WayType() { }

    public WayType copy() {
        WayType t = new WayType();
        for (java.lang.reflect.Field f : WayType.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            try {
                Object v = f.get(this);
                if (v instanceof float[] a) v = a.clone();
                f.set(t, v);
            } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }
        return t;
    }

    /** Trampelpfad oder Wanderweg: festgetretene Erde, schmal, folgt dem Gelände. */
    public static WayType path() {
        WayType t = new WayType();
        t.name = "Pfad"; t.kind = Kind.PATH; t.paving = Paving.DIRT;
        t.laneWidth = 1.0f; t.verge = 0.35f; t.wander = 0.3f; t.crown = 0; t.thickness = 0.03f;
        t.maxGrade = 0.35f; t.smoothing = 3; t.maxBank = 0; t.designSpeed = 2;
        t.speedLimit = 1.4f; t.lateralAccel = 1.5f; t.clearance = 1.2f; t.roughness = 0.6f;
        t.surface = new float[]{0.16f, 0.12f, 0.08f}; t.worn = new float[]{0.22f, 0.18f, 0.13f};
        t.vergeColor = new float[]{0.10f, 0.11f, 0.05f};
        return t;
    }

    /** Feldweg: zwei Fahrspuren aus Kies und Erde, dazwischen Gras. */
    public static WayType track() {
        WayType t = new WayType();
        t.name = "Feldweg"; t.kind = Kind.TRACK; t.paving = Paving.GRAVEL;
        t.laneWidth = 3.0f; t.verge = 0.5f; t.crown = 0.01f; t.thickness = 0.06f;
        t.maxGrade = 0.14f; t.smoothing = 10; t.maxBank = 0; t.designSpeed = 8;
        t.speedLimit = 8; t.lateralAccel = 1.8f; t.clearance = 2; t.roughness = 0.8f;
        t.surface = new float[]{0.14f, 0.12f, 0.095f}; t.worn = new float[]{0.19f, 0.165f, 0.13f};
        t.vergeColor = new float[]{0.10f, 0.12f, 0.05f};
        return t;
    }

    /** Landstraße mit zwei Fahrstreifen, Leitpfosten und Markierung nach amerikanischer Art. */
    public static WayType road() {
        WayType t = new WayType();
        t.name = "Straße"; t.kind = Kind.ROAD; t.paving = Paving.ASPHALT; t.marking = Marking.US;
        t.lanes = 1; t.laneWidth = 3.4f; t.shoulder = 0.6f; t.verge = 1.2f;
        t.maxGrade = 0.07f; t.smoothing = 60; t.maxBank = 0.06f; t.designSpeed = 20;
        t.speedLimit = 20; t.clearance = 3; t.roughness = 0.15f;
        t.posts = true; t.snowPoles = true; t.signs = true;
        t.surface = new float[]{0.055f, 0.055f, 0.058f}; t.worn = new float[]{0.14f, 0.135f, 0.13f};
        t.vergeColor = new float[]{0.16f, 0.145f, 0.12f};
        return t;
    }

    /** Autobahn: je Richtung zwei Fahrstreifen und ein Seitenstreifen, Mittelstreifen mit Schutzplanke. */
    public static WayType motorway() {
        WayType t = new WayType();
        t.name = "Autobahn"; t.kind = Kind.MOTORWAY; t.paving = Paving.ASPHALT; t.marking = Marking.EU;
        t.lanes = 2; t.laneWidth = 3.75f; t.shoulder = 2.5f; t.verge = 1.5f; t.median = 4;
        t.maxGrade = 0.04f; t.smoothing = 160; t.maxBank = 0.07f; t.designSpeed = 36;
        t.speedLimit = 36; t.lateralAccel = 2.4f; t.clearance = 5; t.roughness = 0.05f;
        t.guardrail = true; t.medianBarrier = true; t.posts = true; t.signs = true;
        t.surface = new float[]{0.06f, 0.06f, 0.062f}; t.worn = new float[]{0.13f, 0.13f, 0.125f};
        t.vergeColor = new float[]{0.15f, 0.14f, 0.12f};
        t.age = 0.3f;
        return t;
    }

    /** Die vier Voreinstellungen in der Reihenfolge Pfad, Feldweg, Straße, Autobahn. */
    public static WayType[] all() { return new WayType[]{path(), track(), road(), motorway()}; }
}
