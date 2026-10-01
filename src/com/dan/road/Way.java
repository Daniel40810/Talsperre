package com.dan.road;

/**
 * Ein Weg: Achse durch Stützpunkte (Catmull-Rom), gleichmäßig alle {@link #STEP} Meter abgetastet,
 * mit Höhenprofil, Querneigung, Brücken und der Geschwindigkeit, mit der Fahrer die Kurven nehmen.
 * <p>
 * Das Höhenprofil entsteht aus dem Boden: geglättet über {@link WayType#smoothing} Meter, die
 * Steigung auf {@link WayType#maxGrade} begrenzt, über Wasser mindestens {@link WayType#clearance}
 * Meter hoch. Wo der Weg hoch über Wasser oder einer Senke liegt, wird er zur Brücke. In Kurven neigt
 * sich die Fahrbahn nach innen, außen liegt höher.
 * <p>
 * Lage quer zur Achse: {@code u} positiv nach rechts in Richtung wachsender Bogenlänge {@code s}.
 */
public final class Way {
    public static final float STEP = 2;

    public final WayType type;
    public final boolean closed;
    private final double[] ctrl;

    /** Abtastpunkte: Lage, Höhe der Achse, Richtung, Krümmung (1/m, positiv = Rechtskurve), Querneigung. */
    public int n;
    public float[] x, z, y, tx, tz, curv, bank, ground, vc;
    public boolean[] bridge;
    /** Länge (m). */
    public float length;
    /** Nummer im Netz. */
    public int index;

    /** Weg durch die Punkte x0, z0, x1, z1, …; geschlossen: der letzte Punkt verbindet sich mit dem ersten. */
    public Way(WayType type, boolean closed, double... xz) {
        if (xz.length < 4 || xz.length % 2 != 0) throw new IllegalArgumentException("mindestens zwei Punkte (x, z)");
        this.type = type;
        this.closed = closed;
        this.ctrl = xz.clone();
    }

    // ------------------------------------------------------------ Bau

    void build(Ground g) {
        // dichte Linie aus Catmull-Rom
        int m = ctrl.length / 2;
        java.util.List<double[]> dense = new java.util.ArrayList<>();
        int segs = closed ? m : m - 1;
        for (int k = 0; k < segs; k++) {
            double[] p0 = cp(k - 1), p1 = cp(k), p2 = cp(k + 1), p3 = cp(k + 2);
            double len = Math.hypot(p2[0] - p1[0], p2[1] - p1[1]);
            int st = Math.max(2, (int) Math.ceil(len / 0.5));
            for (int s = 0; s < st; s++) {
                double t = s / (double) st;
                dense.add(new double[]{cat(p0[0], p1[0], p2[0], p3[0], t), cat(p0[1], p1[1], p2[1], p3[1], t)});
            }
        }
        dense.add(closed ? dense.get(0) : cp(m - 1));
        double[] acc = new double[dense.size()];
        for (int i = 1; i < dense.size(); i++) acc[i] = acc[i - 1] + Math.hypot(dense.get(i)[0] - dense.get(i - 1)[0], dense.get(i)[1] - dense.get(i - 1)[1]);
        double total = acc[acc.length - 1];
        n = Math.max(2, (int) Math.round(total / STEP)) + (closed ? 0 : 1);
        int nseg = closed ? n : n - 1;
        double ds = total / nseg;
        length = (float) total;
        x = new float[n]; z = new float[n]; y = new float[n]; tx = new float[n]; tz = new float[n];
        curv = new float[n]; bank = new float[n]; ground = new float[n]; vc = new float[n];
        bridge = new boolean[n];
        int j = 0;
        for (int i = 0; i < n; i++) {
            double s = i * ds;
            while (j + 2 < acc.length && acc[j + 1] < s) j++;
            double f = (s - acc[j]) / Math.max(1e-9, acc[j + 1] - acc[j]);
            f = Math.max(0, Math.min(1, f));
            x[i] = (float) (dense.get(j)[0] + (dense.get(j + 1)[0] - dense.get(j)[0]) * f);
            z[i] = (float) (dense.get(j)[1] + (dense.get(j + 1)[1] - dense.get(j)[1]) * f);
        }
        step = (float) ds;
        for (int i = 0; i < n; i++) {
            int a = nb(i, -1), b = nb(i, 1);
            float dx = x[b] - x[a], dz = z[b] - z[a], l = (float) Math.hypot(dx, dz) + 1e-9f;
            tx[i] = dx / l; tz[i] = dz / l;
        }
        // Krümmung über ±8 m gemittelt
        int w = Math.max(1, Math.round(8 / step));
        for (int i = 0; i < n; i++) {
            int a = nb(i, -w), b = nb(i, w);
            float dtx = tx[b] - tx[a], dtz = tz[b] - tz[a];
            float span = step * (closed ? 2 * w : Math.max(1, b - a));
            // rechts = (−tz, tx)
            curv[i] = (dtx * -tz[i] + dtz * tx[i]) / span;
        }
        profile(g);
    }

    private float step;

    /** Abstand zweier Abtastpunkte (m). */
    public float step() { return step; }

    /** Nachbar i+d, geschlossen im Kreis, offen am Ende festgehalten. */
    int nb(int i, int d) {
        int k = i + d;
        if (closed) return Math.floorMod(k, n);
        return Math.max(0, Math.min(n - 1, k));
    }

    private double[] cp(int k) {
        int m = ctrl.length / 2;
        if (closed) k = Math.floorMod(k, m);
        else k = Math.max(0, Math.min(m - 1, k));
        return new double[]{ctrl[2 * k], ctrl[2 * k + 1]};
    }

    private static double cat(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    private void profile(Ground g) {
        float pv = type.paved();
        float[] water = new float[n];
        boolean[] wet = new boolean[n];
        for (int i = 0; i < n; i++) {
            float rx = -tz[i], rz = tx[i];
            float c = g.height(x[i], z[i]);
            if (type.kind == WayType.Kind.PATH) ground[i] = c;
            else {
                float l = g.height(x[i] - rx * pv, z[i] - rz * pv), r = g.height(x[i] + rx * pv, z[i] + rz * pv);
                ground[i] = (2 * c + l + r) / 4;
            }
            float wl = Float.NaN;
            for (int k = -1; k <= 1; k++) {
                float q = g.water(x[i] + rx * pv * k, z[i] + rz * pv * k);
                if (q == q && (wl != wl || q > wl)) wl = q;
            }
            water[i] = wl;
            wet[i] = wl == wl;
        }
        // Wasser: Brückenstellen um ein paar Meter verlängern (Widerlager)
        int ext = Math.max(1, Math.round((type.kind == WayType.Kind.PATH ? 1.5f : 6) / step));
        boolean[] span = new boolean[n];
        for (int i = 0; i < n; i++) if (wet[i]) for (int k = -ext; k <= ext; k++) span[nb(i, k)] = true;
        float[] need = new float[n];
        for (int i = 0; i < n; i++) {
            float wl = water[i];
            if (wl != wl) for (int k = -ext; k <= ext && wl != wl; k++) wl = water[nb(i, k)];
            need[i] = span[i] && wl == wl ? wl + type.clearance : -Float.MAX_VALUE;
        }
        System.arraycopy(ground, 0, y, 0, n);
        int half = Math.max(1, Math.round(type.smoothing / 2 / step));
        float[] tmp = new float[n];
        for (int it = 0; it < 4; it++) {
            for (int i = 0; i < n; i++) y[i] = Math.max(y[i], need[i]);
            for (int pass = 0; pass < 3; pass++) {
                // gleitendes Mittel über ±half (Summen im Kreis bzw. mit festgehaltenen Enden)
                for (int i = 0; i < n; i++) {
                    double s = 0;
                    for (int k = -half; k <= half; k++) s += y[nb(i, k)];
                    tmp[i] = (float) (s / (2 * half + 1));
                }
                System.arraycopy(tmp, 0, y, 0, n);
            }
            for (int i = 0; i < n; i++) y[i] = Math.max(y[i], need[i]);
            // ein Pfad bleibt am Boden (höchstens wenige Zentimeter darunter)
            if (type.kind == WayType.Kind.PATH) for (int i = 0; i < n; i++) if (!span[i]) y[i] = Math.max(y[i], ground[i] - 0.05f);
        }
        // Steigung begrenzen: vorwärts und rückwärts (an Brücken nur nach oben ausweichen)
        float gmax = type.maxGrade * step;
        for (int rep = 0; rep < 3; rep++) {
            for (int i = 1; i < n; i++) y[i] = Math.max(Math.min(y[i], y[i - 1] + gmax), Math.max(y[i - 1] - gmax, need[i]));
            for (int i = n - 2; i >= 0; i--) y[i] = Math.max(Math.min(y[i], y[i + 1] + gmax), Math.max(y[i + 1] - gmax, need[i]));
        }
        // Brücke: über Wasser oder hoch über dem Boden
        float high = type.kind == WayType.Kind.PATH ? 2 : type.kind == WayType.Kind.TRACK ? 4 : 10;
        for (int i = 0; i < n; i++) bridge[i] = span[i] || y[i] - ground[i] > high;
        // einzelne Lücken schließen
        for (int i = 0; i < n; i++) if (!bridge[i] && bridge[nb(i, -1)] && bridge[nb(i, 1)]) bridge[i] = true;
        // Querneigung und Kurvengeschwindigkeit
        for (int i = 0; i < n; i++) {
            float k = curv[i], v = type.designSpeed;
            float e = Math.min(type.maxBank, v * v * Math.abs(k) / 9.81f * 0.5f);
            bank[i] = -Math.signum(k) * e;
            float lim = (float) Math.sqrt((type.lateralAccel + 9.81f * e) / Math.max(1e-5f, Math.abs(k)));
            vc[i] = Math.min(type.speedLimit, lim);
        }
    }

    // ------------------------------------------------------------ Abfragen

    /** Bogenlänge s auf den Weg gebracht: im Kreis gefaltet oder an den Enden festgehalten. */
    public double wrap(double s) {
        if (closed) { s %= length; return s < 0 ? s + length : s; }
        return Math.max(0, Math.min(length, s));
    }

    /**
     * Punkt der Fahrbahnoberfläche bei Bogenlänge s und Querlage u (rechts positiv), mit Dachgefälle und
     * Querneigung: out[0..2] = x, y, z, out[3], out[4] = Richtung x, z, out[5] = Querneigung.
     */
    public void at(double s, double u, float[] out) {
        s = wrap(s);
        double fi = s / step;
        int i = (int) Math.floor(fi);
        if (!closed && i >= n - 1) i = n - 2;
        float f = (float) (fi - i);
        int a = nb(i, 0), b = nb(i, 1);
        float cx = x[a] + (x[b] - x[a]) * f, cz = z[a] + (z[b] - z[a]) * f, cy = y[a] + (y[b] - y[a]) * f;
        float dx = tx[a] + (tx[b] - tx[a]) * f, dz = tz[a] + (tz[b] - tz[a]) * f, l = (float) Math.hypot(dx, dz) + 1e-9f;
        dx /= l; dz /= l;
        float bk = bank[a] + (bank[b] - bank[a]) * f;
        out[0] = cx - dz * (float) u; out[1] = cy + cross((float) u, bk); out[2] = cz + dx * (float) u;
        out[3] = dx; out[4] = dz; out[5] = bk;
    }

    /** Höhe der Fahrbahn über der Achse bei Querlage u und Querneigung bk. */
    float cross(float u, float bk) {
        float au = Math.abs(u), pv = type.paved();
        float cr = type.crown * Math.min(au, pv);
        float keep = 1 - Math.min(1, Math.abs(bk) / Math.max(1e-4f, type.crown));
        float drop = au > pv ? (au - pv) * 0.04f : 0;   // Bankett fällt leicht ab
        return bk * u - cr * keep - drop;
    }

    /** Ist die Stelle s eine Brücke? */
    public boolean bridgeAt(double s) {
        int i = (int) Math.round(wrap(s) / step);
        return bridge[closed ? Math.floorMod(i, n) : Math.min(n - 1, i)];
    }

    /** Kurvengeschwindigkeit bei s (m/s). */
    public float curveSpeed(double s) {
        int i = (int) Math.round(wrap(s) / step);
        return vc[closed ? Math.floorMod(i, n) : Math.min(n - 1, i)];
    }

    /** Anzahl der Abschnitte zwischen Abtastpunkten. */
    public int segments() { return closed ? n : n - 1; }

    /**
     * Fußpunkt von (px, pz) auf dem Abschnitt i → i+1: o[0] = Bogenlänge, o[1] = Querlage (rechts
     * positiv), o[2] = Abstand im Quadrat.
     */
    void project(int i, double px, double pz, double[] o) {
        int b = nb(i, 1);
        double ax = x[i], az = z[i], dx = x[b] - ax, dz = z[b] - az;
        double l2 = dx * dx + dz * dz + 1e-12;
        double t = ((px - ax) * dx + (pz - az) * dz) / l2;
        double tc = Math.max(0, Math.min(1, t));
        double qx = ax + dx * tc, qz = az + dz * tc;
        double l = Math.sqrt(l2);
        o[0] = (i + tc) * step;
        o[1] = ((pz - az) * dx - (px - ax) * dz) / l;
        double ex = px - qx, ez = pz - qz;
        o[2] = ex * ex + ez * ez;
    }
}
