package com.dan.river;

/**
 * Der Lauf eines Flusses: Mittellinie in der Ebene (x, z), Wasserspiegel und halbe Breite je Punkt.
 * Aus Stützpunkten wird eine gleichmäßig unterteilte Linie (Catmull-Rom), dazu Bogenlänge, Richtung,
 * Krümmung und Gefälle je Punkt. Ein Suchgitter findet zu jedem Ort schnell die Stelle am Fluss.
 * <p>
 * Koordinaten in Metern; die Höhe ist y. Querlage n: positiv auf der Seite (−tz, tx) der Fließrichtung
 * (tx, tz); bei x nach Osten und z nach Süden ist das in Fließrichtung rechts.
 */
public final class RiverPath {
    /** Punkte der unterteilten Linie. */
    public final int n;
    public final float[] x, z, level, half, s, tx, tz, curv, slope;
    /** Gesamtlänge (m). */
    public final float length;

    private final float gx0, gz0, cell;
    private final int gnx, gnz;
    private final int[][] grid;
    private final float reach;
    private final double minSep2;

    /**
     * Aus Stützpunkten (je Zeile x, z, Wasserspiegel, halbe Breite), unterteilt auf etwa step Meter.
     * reach: bis zu welchem Abstand vom Ufer {@link #locate} noch Treffer liefert.
     */
    public RiverPath(double[][] nodes, double step, double reach) {
        java.util.List<float[]> pts = new java.util.ArrayList<>();
        int m = nodes.length;
        for (int k = 0; k < m - 1; k++) {
            double[] p0 = nodes[Math.max(0, k - 1)], p1 = nodes[k], p2 = nodes[k + 1], p3 = nodes[Math.min(m - 1, k + 2)];
            double len = Math.hypot(p2[0] - p1[0], p2[1] - p1[1]);
            int steps = Math.max(1, (int) Math.ceil(len / step));
            for (int i = 0; i < steps; i++) {
                double t = i / (double) steps;
                pts.add(new float[]{(float) cr(p0[0], p1[0], p2[0], p3[0], t), (float) cr(p0[1], p1[1], p2[1], p3[1], t),
                        (float) (p1[2] + (p2[2] - p1[2]) * t), (float) (p1[3] + (p2[3] - p1[3]) * t)});
            }
        }
        double[] e = nodes[m - 1];
        pts.add(new float[]{(float) e[0], (float) e[1], (float) e[2], (float) e[3]});
        this.n = pts.size();
        x = new float[n]; z = new float[n]; level = new float[n]; half = new float[n];
        for (int i = 0; i < n; i++) { float[] q = pts.get(i); x[i] = q[0]; z[i] = q[1]; level[i] = q[2]; half[i] = q[3]; }
        s = new float[n]; tx = new float[n]; tz = new float[n]; curv = new float[n]; slope = new float[n];
        this.length = derive();
        this.reach = (float) reach;
        // engste Stelle zwischen Teilen des Laufs, die mehr als 10 Punkte auseinander liegen (Mäanderhals):
        // bis zur Hälfte davon ist die Suche um den letzten Treffer sicher
        double sep = Double.MAX_VALUE;
        for (int a = 0; a < n; a += 2)
            for (int b = a + 10; b < n; b += 2) {
                double dx = x[a] - x[b], dz = z[a] - z[b];
                sep = Math.min(sep, dx * dx + dz * dz);
            }
        minSep2 = sep == Double.MAX_VALUE ? Double.MAX_VALUE : sep;
        // Suchgitter: je Zelle die Abschnitte, deren Umgebung sie berührt
        float maxHalf = 0, x0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            maxHalf = Math.max(maxHalf, half[i]);
            x0 = Math.min(x0, x[i]); z0 = Math.min(z0, z[i]); x1 = Math.max(x1, x[i]); z1 = Math.max(z1, z[i]);
        }
        float pad = maxHalf + this.reach + 1;
        cell = Math.max(8, 2 * pad);
        gx0 = x0 - pad; gz0 = z0 - pad;
        gnx = (int) ((x1 + pad - gx0) / cell) + 1; gnz = (int) ((z1 + pad - gz0) / cell) + 1;
        java.util.List<java.util.List<Integer>> tmp = new java.util.ArrayList<>(gnx * gnz);
        for (int i = 0; i < gnx * gnz; i++) tmp.add(null);
        for (int i = 0; i + 1 < n; i++) {
            float r = Math.max(half[i], half[i + 1]) + this.reach + 1;
            int ia = (int) ((Math.min(x[i], x[i + 1]) - r - gx0) / cell), ib = (int) ((Math.max(x[i], x[i + 1]) + r - gx0) / cell);
            int ja = (int) ((Math.min(z[i], z[i + 1]) - r - gz0) / cell), jb = (int) ((Math.max(z[i], z[i + 1]) + r - gz0) / cell);
            for (int j = Math.max(0, ja); j <= Math.min(gnz - 1, jb); j++)
                for (int ii = Math.max(0, ia); ii <= Math.min(gnx - 1, ib); ii++) {
                    int c = j * gnx + ii;
                    if (tmp.get(c) == null) tmp.set(c, new java.util.ArrayList<>(4));
                    tmp.get(c).add(i);
                }
        }
        grid = new int[gnx * gnz][];
        for (int c = 0; c < grid.length; c++) {
            java.util.List<Integer> l = tmp.get(c);
            if (l == null) continue;
            grid[c] = new int[l.size()];
            for (int k = 0; k < l.size(); k++) grid[c][k] = l.get(k);
        }
    }

    private float derive() {
        float acc = 0;
        for (int i = 0; i < n; i++) {
            if (i > 0) acc += (float) Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
            s[i] = acc;
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            float dx = x[b] - x[a], dz = z[b] - z[a], l = (float) Math.hypot(dx, dz) + 1e-6f;
            tx[i] = dx / l; tz[i] = dz / l;
        }
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            float ds = s[b] - s[a] + 1e-6f;
            // Krümmung mit Vorzeichen: positiv, wenn der Fluss zur Seite n > 0 abbiegt
            float dtx = tx[b] - tx[a], dtz = tz[b] - tz[a];
            curv[i] = (dtx * -tz[i] + dtz * tx[i]) / ds;
            slope[i] = Math.max(0, (level[a] - level[b]) / ds);
        }
        // Krümmung und Gefälle glätten (über gut drei Breiten)
        smooth(curv, 3);
        smooth(slope, 3);
        return acc;
    }

    private void smooth(float[] a, int passes) {
        float[] b = new float[n];
        for (int p = 0; p < passes; p++) {
            for (int i = 0; i < n; i++) b[i] = (a[Math.max(0, i - 1)] + 2 * a[i] + a[Math.min(n - 1, i + 1)]) / 4;
            System.arraycopy(b, 0, a, 0, n);
        }
    }

    private static double cr(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    /** Wo ein Ort am Fluss liegt. */
    public static final class Loc {
        /** Bogenlänge, Querlage (m), halbe Breite, Wasserspiegel, Richtung, Krümmung (1/m), Gefälle (m/m). */
        public float s, n, half, level, tx, tz, curv, slope;
        /** Abschnitt und Lage darin 0..1. */
        public int seg = -1;
        public float t;
        /** Quer zur Breite: −1 (Ufer auf der Seite n < 0) bis 1; |u| > 1 außerhalb. */
        public float u() { return n / half; }
    }

    /**
     * Stelle am Fluss für (px, pz) in out; false, wenn der Ort weiter als reach vom Ufer liegt (out
     * bleibt dann unbestimmt).
     */
    public boolean locate(double px, double pz, Loc out) {
        int i = (int) ((px - gx0) / cell), j = (int) ((pz - gz0) / cell);
        if (i < 0 || j < 0 || i >= gnx || j >= gnz) return false;
        int[] segs = grid[j * gnx + i];
        if (segs == null) return false;
        double best = Double.MAX_VALUE;
        int bs = -1;
        double bt = 0;
        // Erst um den letzten Treffer suchen (Nachbarpixel liegen fast immer am selben Abschnitt): liegt
        // das Beste innen im Fenster und nah genug, gilt es, sonst die volle Suche im Gitter
        int h = out.seg;
        if (h >= 0 && h < n - 1) {
            int a = Math.max(0, h - 3), b = Math.min(n - 2, h + 3);
            for (int k = a; k <= b; k++) {
                double ax = x[k], az = z[k], dx = x[k + 1] - ax, dz = z[k + 1] - az;
                double t = ((px - ax) * dx + (pz - az) * dz) / (dx * dx + dz * dz + 1e-12);
                t = t < 0 ? 0 : (t > 1 ? 1 : t);
                double qx = ax + dx * t - px, qz = az + dz * t - pz, d2 = qx * qx + qz * qz;
                if (d2 < best) { best = d2; bs = k; bt = t; }
            }
            double lim = half[bs] + reach;
            boolean inner = (bs > a || a == 0) && (bs < b || b == n - 2);
            if (!inner || best > lim * lim || best > 0.2 * minSep2) { best = Double.MAX_VALUE; bs = -1; }
        }
        if (bs < 0) {
            for (int k : segs) {
                double ax = x[k], az = z[k], dx = x[k + 1] - ax, dz = z[k + 1] - az;
                double t = ((px - ax) * dx + (pz - az) * dz) / (dx * dx + dz * dz + 1e-12);
                t = t < 0 ? 0 : (t > 1 ? 1 : t);
                double qx = ax + dx * t - px, qz = az + dz * t - pz, d2 = qx * qx + qz * qz;
                if (d2 < best) { best = d2; bs = k; bt = t; }
            }
        }
        if (bs < 0) return false;
        float t = (float) bt;
        int k = bs;
        out.seg = k; out.t = t;
        out.half = half[k] + (half[k + 1] - half[k]) * t;
        if (Math.sqrt(best) > out.half + reach) return false;
        out.s = s[k] + (s[k + 1] - s[k]) * t;
        out.level = level[k] + (level[k + 1] - level[k]) * t;
        float ux = tx[k] + (tx[k + 1] - tx[k]) * t, uz = tz[k] + (tz[k + 1] - tz[k]) * t, ul = (float) Math.hypot(ux, uz) + 1e-9f;
        out.tx = ux / ul; out.tz = uz / ul;
        out.curv = curv[k] + (curv[k + 1] - curv[k]) * t;
        out.slope = slope[k] + (slope[k + 1] - slope[k]) * t;
        float cx = x[k] + (x[k + 1] - x[k]) * t, cz = z[k] + (z[k + 1] - z[k]) * t;
        out.n = (float) ((px - cx) * -out.tz + (pz - cz) * out.tx);
        return true;
    }

    /** Ort auf der Mittellinie bei Bogenlänge sq (geklemmt): out = x, z, Wasserspiegel, halbe Breite, tx, tz. */
    public void at(double sq, float[] out) {
        sq = Math.max(0, Math.min(length, sq));
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) { int mid = (lo + hi) >>> 1; if (s[mid] <= sq) lo = mid; else hi = mid; }
        float t = (float) ((sq - s[lo]) / Math.max(1e-6, s[hi] - s[lo]));
        out[0] = x[lo] + (x[hi] - x[lo]) * t; out[1] = z[lo] + (z[hi] - z[lo]) * t;
        out[2] = level[lo] + (level[hi] - level[lo]) * t; out[3] = half[lo] + (half[hi] - half[lo]) * t;
        out[4] = tx[lo] + (tx[hi] - tx[lo]) * t; out[5] = tz[lo] + (tz[hi] - tz[lo]) * t;
    }
}
