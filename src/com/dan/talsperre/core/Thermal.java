package com.dan.talsperre.core;

/**
 * Temperaturfeld der heißen Quellen und Geysire und die Farben, die daraus folgen. Jede Quelle hat
 * ein Becken (oder nur einen Schlot) mit der Temperatur am Quellmund; nach außen kühlt das Wasser
 * ab, entlang der Abflussrinnen („Finger“) langsamer, bergab weiter.
 * <p>
 * Farben nach dem National Park Service (alkalische Becken): über 75 °C blasse, graue und rosa
 * Fäden (Thermocrinis), bis 73 °C gelbgrüne Cyanobakterien, beim Abkühlen Orange, Rost und Braun;
 * im sonnigen Sommer kräftiger orange, im Winter olivgrün. Die Grenzen werden weich überblendet.
 */
public final class Thermal {
    /** Becken, Schlot, Krater, Schlammtopf. */
    public enum Kind { POOL, VENT, CRATER, MUD }

    public static final class Spring {
        public final String name;
        public final Kind kind;
        public final double x, z;
        /** Wasserspiegel bzw. Boden am Schlot (y). */
        public double y;
        /** Halbachsen des Beckens in x und z (0 bei einem Schlot) und die Tiefe in der Mitte. */
        public final double ax, az, depth;
        /**
         * Temperatur am Quellmund und Abfall bis zum Beckenrand (°C). t0 kann sich ändern (Morning
         * Glory im Zeitraffer, Doublet Pool während Giantess); danach {@link Thermal#changed} rufen.
         */
        public volatile double t0;
        public final double drop;
        /** Abfluss: Richtung (Bogenmaß, 0 = Osten, π/2 = Süden), Reichweite in Metern, Grundbreite des Saums. */
        public final double runDir, runLen, apron;
        /** Anzahl der Finger rundum und Anteil des Beckens, ab dem der flache Schelf beginnt. */
        public final double fingers, shelf;
        /** Reichweite für die Vorprüfung (Quadrat). */
        final double reach2;
        /** Wie dicht die Matten wachsen 0..1: Geysire schütten nur zeitweise Wasser aus, dort weniger. */
        public double mats = 1;

        public Spring(String name, Kind kind, double x, double z, double ax, double az, double depth, double t0, double drop,
                      double runDir, double runLen, double apron, double fingers, double shelf) {
            this.name = name; this.kind = kind; this.x = x; this.z = z; this.ax = ax; this.az = az; this.depth = depth;
            this.t0 = t0; this.drop = drop; this.runDir = runDir; this.runLen = runLen; this.apron = apron;
            this.fingers = fingers; this.shelf = shelf;
            double r = Math.max(ax, az) + runLen * 1.6 + apron * 4 + 4;
            reach2 = r * r;
        }

        /** Temperatur am Beckenrand. */
        public double rim() { return t0 - drop; }

        /** Normierter Abstand zur Mitte (1 = Beckenrand); bei einem Schlot in Metern / 1 m. */
        public double u(double px, double pz) {
            double dx = (px - x) / Math.max(0.6, ax), dz = (pz - z) / Math.max(0.6, az);
            return Math.sqrt(dx * dx + dz * dz);
        }

        /** Fläche des Wasserspiegels in m². */
        public double area() { return Math.PI * ax * az; }
    }

    public final java.util.List<Spring> springs = new java.util.ArrayList<>();
    /** Lufttemperatur am Boden (°C) für den Saum; Jahreszeit 0 (Winter) bis 1 (Hochsommer). */
    public static volatile float ambient = 12, season = 0.8f;
    /** Schneedecke 0..1 (aus {@link com.dan.talsperre.effects.Climate#snow}) und Raureif 0..1 (kalte Luft). */
    public static volatile float snow = 0, rime = 0;

    public Spring add(Spring s) { springs.add(s); grid = null; return s; }

    /** Zählt jede Änderung an einer Quelle hoch; der Bildrechner verwirft dann sein gespeichertes Licht. */
    public static volatile int generation;

    /** Die Quelle s hat eine neue Temperatur: ihre Kacheln werden neu gebacken. */
    public void changed(Spring s) {
        Grid g = grid;
        if (g != null) {
            double r = Math.sqrt(s.reach2), span = TS * CELL;
            int i0 = (int) Math.floor((s.x - r) / span) - g.tx0, i1 = (int) Math.floor((s.x + r) / span) - g.tx0;
            int j0 = (int) Math.floor((s.z - r) / span) - g.tz0, j1 = (int) Math.floor((s.z + r) / span) - g.tz0;
            for (int j = Math.max(0, j0); j <= Math.min(g.nz - 1, j1); j++)
                for (int i = Math.max(0, i0); i <= Math.min(g.nx - 1, i1); i++) g.t[j * g.nx + i] = null;
        }
        generation++;
    }

    /** Die Quelle mit diesem Namen oder null. */
    public Spring byName(String n) {
        for (Spring s : springs) if (s.name.equals(n)) return s;
        return null;
    }

    /** Setzt die Jahreszeit aus dem Tag im Jahr (Höhepunkt Mitte Juli). */
    public static void setDay(int day) {
        season = (float) (0.5 + 0.5 * Math.cos(2 * Math.PI * (day - 200) / 365.0));
    }

    // ------------------------------------------------------------ Temperatur

    /** Temperatur des Wassers bzw. Bodens am Punkt (°C); ambient, wo keine Quelle wirkt. */
    public float temp(double px, double pz) {
        return sample((float) px, (float) pz, null);
    }

    /**
     * Wie {@link #temp}, aber genau gerechnet und ohne Kacheln anzulegen: für einzelne Abfragen über
     * große Flächen (Bäume setzen, Weideplätze suchen), die sonst Kacheln füllen würden, die nie ins
     * Bild kommen.
     */
    public float tempExact(double px, double pz) {
        float best = ambient;
        double[] r = new double[1];
        for (Spring s : springs) {
            double dx = px - s.x, dz = pz - s.z;
            if (dx * dx + dz * dz > s.reach2) continue;
            double g = field(s, px, pz, r);
            float t = (float) (ambient + (r[0] - ambient) * g);
            if (t > best) best = t;
        }
        return best;
    }

    /** Temperatur, die Quelle s am Punkt erzeugt (genau gerechnet, ohne Raster). */
    public float tempOf(Spring s, double px, double pz) {
        double[] r = new double[1];
        double g = field(s, px, pz, r);
        return (float) (ambient + (r[0] - ambient) * g);
    }

    /**
     * Temperaturfeld einer Quelle, zerlegt in Anteil g (0..1) und Randtemperatur r: am Punkt herrscht
     * ambient + (r − ambient)·g. So hängt das Raster nicht von der Lufttemperatur ab, die mit der
     * Tageszeit wandert. Im Becken ist g = 1 und r die Wassertemperatur.
     */
    private double field(Spring s, double px, double pz, double[] rOut) {
        double u = s.u(px, pz);
        if (u <= 1 && s.kind != Kind.VENT) { rOut[0] = s.t0 - s.drop * u * u; return 1; }
        double dx = px - s.x, dz = pz - s.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double rimR = s.kind == Kind.VENT ? 0.8 : dist / Math.max(1e-6, u);
        double d = Math.max(0, dist - rimR);
        double th = Math.atan2(dz, dx);
        // Finger: schmale Rinnen, die vom Rand weg laufen. Gemessen wird quer zum Abfluss in Metern
        // (Bogen auf dem Kreis um die Quelle), damit eine Rinne nach außen nicht breiter wird;
        // entlang des Abflusses ändert sich das Muster langsam, die Rinnen verzweigen und enden.
        double k = (s.kind == Kind.VENT ? 0.9 : 0.45) / (1 + rimR / 20);
        double tr = th - s.runDir;
        tr -= Math.round(tr / (2 * Math.PI)) * 2 * Math.PI;   // −π..π, Naht gegenüber dem Abfluss
        double finger = fingerAt(tr, d, rimR, k, s);
        double edge = Math.abs(tr) - (Math.PI - 0.35);
        if (edge > 0) {
            double w = edge / 0.35 * 0.5;
            finger += (fingerAt(tr - Math.signum(tr) * 2 * Math.PI, d, rimR, k, s) - finger) * w;
        }
        double dir = Math.max(0, Math.cos(th - s.runDir));
        dir = dir * dir * dir * dir;
        double L = s.apron * (0.5 + 0.5 * finger) + s.runLen * dir * (0.15 + 0.85 * finger * finger);
        rOut[0] = s.kind == Kind.VENT ? s.t0 : s.rim();
        return Math.exp(-d / Math.max(0.3, L));
    }

    // ------------------------------------------------------------ Raster

    /**
     * Das Temperaturfeld wird nicht mehr je Bildpunkt gerechnet (das war ein Drittel der Zeit fürs
     * Licht), sondern in Kacheln von 8 × 8 m mit 25 cm Raster vorgehalten, erst wenn sie gebraucht
     * werden, und bilinear gelesen. Je Rasterpunkt: g, r·g der wärmsten Quelle, welche Quelle das ist
     * und welche am nächsten liegt (für die Terrassenstufen).
     */
    private static final float CELL = 0.25f;
    private static final int TS = 32, TN = TS + 1;
    /** Bezugstemperatur der Luft, mit der beim Backen die wärmste Quelle gewählt wird. */
    private static final double A0 = 8;

    private static final class Tile {
        /** g und r·g als Festkomma (g·65535, r·g·500), wärmste und nächste Quelle (Index + 1). */
        final char[] g = new char[TN * TN], rg = new char[TN * TN];
        final byte[] hot = new byte[TN * TN], near = new byte[TN * TN];
    }

    private static final Tile EMPTY = new Tile();
    private static final float G_Q = 65535f, RG_Q = 500f;

    /** Kacheln über dem Rechteck, das alle Quellen mit ihrer Reichweite umfasst. */
    private static final class Grid {
        final int tx0, tz0, nx, nz;
        final Tile[] t;

        Grid(int tx0, int tz0, int nx, int nz) { this.tx0 = tx0; this.tz0 = tz0; this.nx = nx; this.nz = nz; t = new Tile[nx * nz]; }
    }

    private volatile Grid grid;

    private Grid grid() {
        Grid g = grid;
        if (g != null) return g;
        synchronized (this) {
            if (grid != null) return grid;
            double x0 = 1e9, z0 = 1e9, x1 = -1e9, z1 = -1e9;
            for (Spring s : springs) {
                double r = Math.sqrt(s.reach2);
                x0 = Math.min(x0, s.x - r); x1 = Math.max(x1, s.x + r);
                z0 = Math.min(z0, s.z - r); z1 = Math.max(z1, s.z + r);
            }
            float span = TS * CELL;
            if (springs.isEmpty()) g = new Grid(0, 0, 0, 0);
            else {
                int tx0 = (int) Math.floor(x0 / span), tz0 = (int) Math.floor(z0 / span);
                g = new Grid(tx0, tz0, (int) Math.floor(x1 / span) - tx0 + 1, (int) Math.floor(z1 / span) - tz0 + 1);
            }
            grid = g;
            return g;
        }
    }

    /** Anzahl der gebackenen Kacheln (für Messungen). */
    public int tileCount() {
        Grid g = grid;
        int n = 0;
        if (g != null) for (Tile t : g.t) if (t != null && t != EMPTY) n++;
        return n;
    }

    private Tile tile(int tx, int tz) {
        Grid g = grid();
        int i = tx - g.tx0, j = tz - g.tz0;
        if (i < 0 || j < 0 || i >= g.nx || j >= g.nz) return EMPTY;
        int k = j * g.nx + i;
        Tile t = g.t[k];
        if (t == null) g.t[k] = t = bake(tx, tz);   // zwei Fäden backen schlimmstenfalls dieselbe Kachel
        return t;
    }

    private Tile bake(int tx, int tz) {
        double x0 = tx * TS * CELL, z0 = tz * TS * CELL, x1 = x0 + TS * CELL, z1 = z0 + TS * CELL;
        java.util.List<Spring> in = new java.util.ArrayList<>();
        java.util.List<Integer> idx = new java.util.ArrayList<>();
        for (int i = 0; i < springs.size(); i++) {
            Spring s = springs.get(i);
            double cx = Math.max(x0, Math.min(x1, s.x)), cz = Math.max(z0, Math.min(z1, s.z));
            double dx = cx - s.x, dz = cz - s.z;
            if (dx * dx + dz * dz <= s.reach2) { in.add(s); idx.add(i); }
        }
        if (in.isEmpty()) return EMPTY;
        Tile t = new Tile();
        double[] r = new double[1];
        for (int j = 0; j < TN; j++) {
            double pz = z0 + j * CELL;
            for (int i = 0; i < TN; i++) {
                double px = x0 + i * CELL;
                int c = j * TN + i;
                double best = 0, bg = 0, br = 0, nd = Double.MAX_VALUE;
                int bi = -1, ni = -1;
                for (int k = 0; k < in.size(); k++) {
                    Spring s = in.get(k);
                    double dx = px - s.x, dz = pz - s.z, d2 = dx * dx + dz * dz;
                    if (d2 > s.reach2) continue;
                    if (d2 < nd) { nd = d2; ni = idx.get(k); }
                    double g = field(s, px, pz, r);
                    double v = (r[0] - A0) * g;
                    if (bi < 0 || v > best) { best = v; bg = g; br = r[0]; bi = idx.get(k); }
                }
                t.g[c] = (char) Math.round(bg * G_Q); t.rg[c] = (char) Math.round(Math.max(0, Math.min(131, br * bg)) * RG_Q);
                t.hot[c] = (byte) (bi + 1); t.near[c] = (byte) (ni + 1);
            }
        }
        return t;
    }

    /**
     * Temperatur aus dem Raster; info (falls nicht null) bekommt die wärmste und die nächste Quelle
     * am nächstgelegenen Rasterpunkt (Index + 1, 0 = keine).
     */
    private float sample(float px, float pz, int[] info) {
        float u = px / CELL, v = pz / CELL;
        int iu = (int) Math.floor(u), iv = (int) Math.floor(v);
        int tx = Math.floorDiv(iu, TS), tz = Math.floorDiv(iv, TS);
        Tile t = tile(tx, tz);
        if (t == EMPTY) { if (info != null) { info[0] = 0; info[1] = 0; } return ambient; }
        int li = iu - tx * TS, lj = iv - tz * TS;
        float fu = u - iu, fv = v - iv;
        int c = lj * TN + li;
        float g = bil(t.g, c, fu, fv) / G_Q, rg = bil(t.rg, c, fu, fv) / RG_Q;
        if (info != null) {
            int n = c + (fu > 0.5f ? 1 : 0) + (fv > 0.5f ? TN : 0);
            info[0] = t.hot[n]; info[1] = t.near[n];
        }
        float a = ambient;
        return Math.max(a, a * (1 - g) + rg);
    }

    private static float bil(char[] a, int c, float fu, float fv) {
        float ab = a[c] + (a[c + 1] - a[c]) * fu, cd = a[c + TN] + (a[c + TN + 1] - a[c + TN]) * fu;
        return ab + (cd - ab) * fv;
    }

    /** Rinnenmuster 0..1 an Winkel tr (zum Abfluss) und Abstand d vom Rand. */
    private static double fingerAt(double tr, double d, double rimR, double k, Spring s) {
        double lat = tr * (Math.max(2, rimR) + 0.4 * d);
        // Rinnen mäandern: seitlicher Versatz, der sich entlang des Abflusses langsam ändert
        lat += (Noise.tex((float) (d * 0.045 + s.x * 0.01), (float) (tr * 2.2 + 3.3)) - 0.5) * (7 + 0.08 * rimR);
        float n1 = Noise.tex((float) (lat * k + s.x * 0.013), (float) (d * 0.012 + s.z * 0.011));
        float n2 = Noise.tex((float) (lat * k * 2.3 + 5.3), (float) (d * 0.03 + 1.7));
        return smooth(0.47, 0.62, n1 * 0.7 + n2 * 0.3);
    }

    // ------------------------------------------------------------ Becken

    /** Das Becken, in dem der Punkt liegt, oder null. */
    public Spring poolAt(double px, double pz) {
        for (Spring s : springs) {
            if (s.kind == Kind.VENT) continue;
            if (s.u(px, pz) <= 1.02) return s;
        }
        return null;
    }

    /** Tiefe des Beckens s am Punkt (m): flacher Schelf am Rand, zur Mitte ein Trichter. */
    public static float depth(Spring s, double px, double pz) {
        double u = Math.min(1, s.u(px, pz));
        double sh = s.shelf, shelfD = Math.min(0.6, s.depth * 0.25);
        double d;
        if (u > sh) d = 0.04 + shelfD * smooth(0, 1, (1 - u) / (1 - sh));
        else d = shelfD + (s.depth - shelfD) * Math.pow(1 - u / sh, 1.4);
        return (float) d;
    }

    // ------------------------------------------------------------ Farben

    /**
     * Farbe (linear) für eine Temperatur: Tabelle in 5-°C-Schritten von 20 bis 95 °C, Sommer und Winter.
     * Werte ohne Matten (über 75 °C) sind heller Sinter mit einem Hauch Rosa.
     */
    private static final float[][] SUMMER = {
            {0.20f, 0.10f, 0.045f}, {0.24f, 0.10f, 0.04f}, {0.30f, 0.11f, 0.035f}, {0.40f, 0.14f, 0.035f},  // 20..35
            {0.50f, 0.18f, 0.035f}, {0.60f, 0.24f, 0.04f}, {0.66f, 0.30f, 0.045f}, {0.68f, 0.38f, 0.05f},  // 40..55
            {0.68f, 0.47f, 0.06f}, {0.64f, 0.56f, 0.09f}, {0.55f, 0.58f, 0.16f}, {0.58f, 0.52f, 0.36f},    // 60..75
            {0.60f, 0.53f, 0.48f}, {0.60f, 0.57f, 0.54f}, {0.58f, 0.57f, 0.55f}, {0.56f, 0.56f, 0.55f}};   // 80..95
    private static final float[][] WINTER = {
            {0.12f, 0.10f, 0.05f}, {0.14f, 0.12f, 0.05f}, {0.17f, 0.15f, 0.05f}, {0.20f, 0.19f, 0.06f},
            {0.23f, 0.23f, 0.07f}, {0.27f, 0.28f, 0.08f}, {0.32f, 0.33f, 0.09f}, {0.38f, 0.38f, 0.10f},
            {0.46f, 0.44f, 0.11f}, {0.52f, 0.50f, 0.13f}, {0.52f, 0.54f, 0.20f}, {0.56f, 0.51f, 0.38f},
            {0.60f, 0.53f, 0.48f}, {0.60f, 0.57f, 0.54f}, {0.58f, 0.57f, 0.55f}, {0.56f, 0.56f, 0.55f}};

    public static void color(float t, float[] o) {
        float f = (t - 20) / 5f;
        if (f < 0) f = 0; if (f > 15) f = 15;
        int i = Math.min(14, (int) f);
        float k = f - i;
        float s = season;
        for (int c = 0; c < 3; c++) {
            float a = SUMMER[i][c] + (SUMMER[i + 1][c] - SUMMER[i][c]) * k;
            float b = WINTER[i][c] + (WINTER[i + 1][c] - WINTER[i][c]) * k;
            o[c] = b + (a - b) * s;
        }
    }

    /**
     * Überblendet die Bodenfarbe o am Punkt mit den Matten: je wärmer der Boden, desto stärker.
     * Feine Sinterterrassen (Stufen quer zum Abfluss) nur, solange ein Pixel kleiner ist als sie.
     */
    public float overlay(float px, float pz, float foot, float[] o) {
        int[] info = new int[2];
        float t = sample(px, pz, info);
        if (info[0] == 0) return t;
        Spring hot = springs.get(info[0] - 1);
        float w = (float) (smooth(ambient + 6, ambient + 22, t) * (t > 74 ? 1 : hot.mats));
        if (w <= 0.001f) return t;
        float c0, c1, c2;
        {
            float[] c = COL.get();
            color(t, c);
            c0 = c[0]; c1 = c[1]; c2 = c[2];
        }
        float fineVis = 1 - (float) smooth(0.1, 0.6, foot);
        if (fineVis > 0) {
            // Terrassenstufen: Wellen quer zur Richtung zur nächsten Quelle
            if (info[1] > 0) {
                Spring s = springs.get(info[1] - 1);
                double d = Math.hypot(px - s.x, pz - s.z);
                float warp = Noise.tex(px * 0.35f, pz * 0.35f) * 9 + Noise.tex(px * 1.1f + 3, pz * 1.1f) * 3;
                float step = (float) Math.sin(d * 13.0 + warp);
                float k = 1 + 0.05f * step * fineVis;
                c0 *= k; c1 *= k; c2 *= k;
            }
            float grain = (Noise.tex(px * 1.7f + 3.3f, pz * 1.7f) - 0.5f) * 0.12f * fineVis;
            c0 *= 1 + grain; c1 *= 1 + grain; c2 *= 1 + grain;
        }
        o[0] += (c0 - o[0]) * w; o[1] += (c1 - o[1]) * w; o[2] += (c2 - o[2]) * w;
        return t;
    }

    private static final ThreadLocal<float[]> COL = ThreadLocal.withInitial(() -> new float[3]);

    /**
     * Wie nah die nächste heiße Quelle ist, 0..1 (1 direkt daneben, 0 ab range Metern). Für den
     * Raureif: im Winter friert der Dampf an Bäumen und Holz in der Nähe der Quellen fest.
     */
    public float near(double px, double pz, double range) {
        double bd = range * range;
        for (Spring s : springs) {
            double dx = px - s.x, dz = pz - s.z, d = dx * dx + dz * dz;
            if (d < bd) bd = d;
        }
        double t = 1 - Math.sqrt(bd) / range;
        return (float) (t * t);
    }

    /** Wasserfilm am Boden 0..1 (warmer Abfluss glänzt). */
    public float film(float px, float pz) {
        float t = temp(px, pz);
        return (float) smooth(ambient + 12, ambient + 35, t);
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
