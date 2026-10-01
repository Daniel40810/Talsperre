package com.dan.talsperre.core;

import java.util.stream.IntStream;

/**
 * Gelände des Edertals um die Sperrmauer, prozedural aus dem Lauf der Eder: ein flacher Talboden, eine
 * Stufe zum Fluss, bewaldete Hänge, in der Ferne die Mittelgebirgsrücken. Koordinaten in Metern, x nach
 * Osten, z nach Süden, Ursprung in der Mitte der Mauerkrone; y = 0 ist der Stauziel-Wasserspiegel des
 * Edersees (244,97 m ü. NHN). Der See liegt im Süden (z &gt; 0), die Eder fließt nach Norden ab.
 * <p>
 * Phase 2: das Tal nach Maß. Der Seegrund liegt an der Mauer 41,7 m unter Stauziel und steigt nach Südwesten
 * an; der See windet sich in Armen nach Westen. Unterhalb der Mauer fließt die Eder durch eine schmale Aue.
 * Die Hänge steigen nach einem Profil aus Steilstufe und Plateau auf rund 190 m über dem Talgrund (Kellerwald).
 * <p>
 * Das Netz besteht aus drei Gittern: fein (8 m) um die Mauer, mittel (48 m) über das Tal, grob (672 m)
 * bis 60 km. {@link #sample} liefert die Höhe genau so, wie die Dreiecke sie zeigen.
 */
public final class Terrain {
    /** Höhe von y = 0 über dem Meer. */
    public static final double DATUM = 244.97;

    // ------------------------------------------------------------ Gitter

    /** Ein Höhengitter mit Löchern (dort liegt ein feineres Gitter). */
    public static final class Grid {
        public final double x0, z0, cell;
        public final int nx, nz;           // Zellen
        final double[][] holes;            // je x0, x1, z0, z1
        public final float[] h, nxs, nys, nzs;   // Knoten (nx+1)·(nz+1)

        Grid(double x0, double x1, double z0, double z1, double cell, double[]... holes) {
            this.x0 = x0; this.z0 = z0; this.cell = cell;
            this.nx = (int) Math.round((x1 - x0) / cell);
            this.nz = (int) Math.round((z1 - z0) / cell);
            this.holes = holes;
            int n = (nx + 1) * (nz + 1);
            h = new float[n]; nxs = new float[n]; nys = new float[n]; nzs = new float[n];
        }

        public double x1() { return x0 + nx * cell; }
        public double z1() { return z0 + nz * cell; }

        boolean contains(double x, double z) { return x >= x0 && x <= x1() && z >= z0 && z <= z1(); }

        /** Liegt die Zelle (i, j) in einem Loch? */
        public boolean inHole(int i, int j) {
            double cx = x0 + (i + 0.5) * cell, cz = z0 + (j + 0.5) * cell;
            for (double[] o : holes) if (cx > o[0] && cx < o[1] && cz > o[2] && cz < o[3]) return true;
            return false;
        }

        /** Höhe wie die Dreiecke (a,b,c) und (a,c,d) sie zeigen. */
        float at(double x, double z) {
            double u = (x - x0) / cell, v = (z - z0) / cell;
            int i = Math.max(0, Math.min(nx - 1, (int) Math.floor(u))), j = Math.max(0, Math.min(nz - 1, (int) Math.floor(v)));
            float fx = (float) Math.max(0, Math.min(1, u - i)), fy = (float) Math.max(0, Math.min(1, v - j));
            int w = nx + 1, a = j * w + i;
            float ha = h[a], hb = h[a + 1], hc = h[a + w + 1], hd = h[a + w];
            return fx >= fy ? ha + (hb - ha) * fx + (hc - hb) * fy : ha + (hc - hd) * fx + (hd - ha) * fy;
        }
    }

    static final double[] UPPER = {-1500, 1500, -1500, 1500};
    static final double[] MIDGRID = {-9600, 6000, -6000, 6000};
    public final Grid fine = new Grid(UPPER[0], UPPER[1], UPPER[2], UPPER[3], 8);
    public final Grid mid = new Grid(MIDGRID[0], MIDGRID[1], MIDGRID[2], MIDGRID[3], 48, UPPER);
    public final Grid far = new Grid(-60480, 60480, -60480, 60480, 672, MIDGRID);

    // ------------------------------------------------------------ Eder


    /**
     * Talachse als Stützpunkte, vom Kopf des Sees bis unter die Mauer und die Eder abwärts: x, z, Höhe des
     * Talgrunds über y = 0, halbe Breite des Wasserlaufs, halbe Breite des flachen Talbodens. Südlich der
     * Mauer (z &gt; 0) liegt der Talgrund unter dem See; die Ufer ergeben sich aus dem Hangprofil.
     */
    static final double[][] NODES = {
            {-8500, 2350, -21, 14, 110}, {-7300, 2000, -22.5, 14, 130}, {-6300, 1800, -24, 14, 140},
            {-5300, 1900, -25, 14, 150}, {-4400, 2350, -27, 14, 160}, {-3400, 2600, -29, 14, 190}, {-2400, 2450, -31, 14, 150},
            {-1500, 2000, -33, 14, 170}, {-900, 1400, -35, 14, 140}, {-450, 800, -38, 14, 110}, {-130, 350, -40.5, 14, 60},
            {0, 100, -41.4, 14, 38}, {0, 0, -41.7, 14, 35}, {10, -100, -42.5, 13, 35}, {40, -500, -44, 13, 40},
            {110, -1300, -46, 13, 60}, {150, -2200, -48, 13, 70}, {90, -3300, -51, 14, 60}, {20, -4600, -54, 14, 70},
            {-40, -6000, -58, 15, 80}, {-60, -9000, -64, 16, 90}};

    /** Unterteilter Lauf: Punkte im Abstand von rund 25 m. */
    final float[] rx, rz, re, rh, rw;
    final int rn;

    // ------------------------------------------------------------ geebnete Stellen

    /** Stellen mit bekannter Höhe: x, z, Radius, Höhe über y = 0 (NaN: Höhe vor Ort), Übergang. */
    private final java.util.List<double[]> pads = new java.util.ArrayList<>();

    /** Ebnet den Boden um (x, z) im Radius r auf die Höhe level (NaN: die Höhe dort), Übergang über blend Meter. */
    public void pad(double x, double z, double r, double level, double blend) {
        pads.add(new double[]{x, z, r, level, blend});
    }

    /**
     * Formt das Gelände nach der Formel, etwa Straßen mit Damm und Einschnitt, und färbt den Boden (die
     * Fahrbahn aus der Ferne). Wird vor {@link #build} gesetzt.
     */
    public interface Shaper {
        double shape(double x, double z, double h);

        /** Färbt die Bodenfarbe rgb (linear) an (x, z); foot ist die Größe eines Pixels in Metern. */
        default void paint(float x, float z, float foot, float[] rgb) { }
    }

    public volatile Shaper shaper;

    /** Hügel oder Mulde: Gauß-Beule der Höhe h und Breite r. */
    private final java.util.List<double[]> bumps = new java.util.ArrayList<>();

    public void bump(double x, double z, double r, double h) { bumps.add(new double[]{x, z, r, h}); }

    // ------------------------------------------------------------ Bodenkarte

    /** Raster fein (4 m über den feinen Gittern) und mittel (24 m über dem mittleren). */
    private Raster rFine, rMid;

    static final class Raster {
        final double x0, z0, cell;
        final int nx, nz;
        final float[] dist, fx, fz, sinter, forest;
        /** Abstand zum Waldrand in Metern (nur innerhalb des Waldes > 0). */
        float[] edge;

        Raster(double x0, double x1, double z0, double z1, double cell) {
            this.x0 = x0; this.z0 = z0; this.cell = cell;
            nx = (int) Math.round((x1 - x0) / cell) + 1;
            nz = (int) Math.round((z1 - z0) / cell) + 1;
            int n = nx * nz;
            dist = new float[n]; fx = new float[n]; fz = new float[n]; sinter = new float[n]; forest = new float[n];
        }

        boolean contains(double x, double z) {
            return x >= x0 && z >= z0 && x < x0 + (nx - 1) * cell && z < z0 + (nz - 1) * cell;
        }
    }

    /** Felsflächen (Mitte x, z, Radius), ab Phase 2: Steinbruch, Felsrippen am Hang. */
    static final double[][] SINTER = {};

    /** Heiße Quellen und Geysire für die Farben am Boden; wird beim Bau gesetzt. */
    public volatile Thermal thermal;

    public Terrain() {
        // Lauf unterteilen (Catmull-Rom), etwa alle 25 m ein Punkt
        java.util.List<float[]> pts = new java.util.ArrayList<>();
        int n = NODES.length;
        for (int k = 0; k < n - 1; k++) {
            double[] p0 = NODES[Math.max(0, k - 1)], p1 = NODES[k], p2 = NODES[k + 1], p3 = NODES[Math.min(n - 1, k + 2)];
            double len = Math.hypot(p2[0] - p1[0], p2[1] - p1[1]);
            int steps = Math.max(1, (int) Math.ceil(len / 25));
            for (int s = 0; s < steps; s++) {
                double t = s / (double) steps;
                float[] q = new float[5];
                for (int c = 0; c < 2; c++) q[c] = (float) catmull(p0[c], p1[c], p2[c], p3[c], t);
                q[2] = (float) (p1[2] + (p2[2] - p1[2]) * t);
                q[3] = (float) (p1[3] + (p2[3] - p1[3]) * t);
                q[4] = (float) (p1[4] + (p2[4] - p1[4]) * t);
                pts.add(q);
            }
        }
        pts.add(new float[]{(float) NODES[n - 1][0], (float) NODES[n - 1][1], (float) NODES[n - 1][2], (float) NODES[n - 1][3], (float) NODES[n - 1][4]});
        rn = pts.size();
        rx = new float[rn]; rz = new float[rn]; re = new float[rn]; rh = new float[rn]; rw = new float[rn];
        for (int i = 0; i < rn; i++) { float[] q = pts.get(i); rx[i] = q[0]; rz[i] = q[1]; re[i] = q[2]; rh[i] = q[3]; rw[i] = q[4]; }
        // Mäander: der Lauf schwingt seitlich
        float[] ox = new float[rn], oz = new float[rn];
        double sArc = 0;
        for (int i = 0; i < rn; i++) {
            int a = Math.max(0, i - 1), b = Math.min(rn - 1, i + 1);
            double dx = rx[b] - rx[a], dz = rz[b] - rz[a], l = Math.hypot(dx, dz);
            if (i > 0) sArc += Math.hypot(rx[i] - rx[i - 1], rz[i] - rz[i - 1]);
            double near = smoothD(250, 900, Math.hypot(rx[i], rz[i])) * (rz[i] < 0 ? 1 : 0.25);
            double w = (Noise.fbm((float) (sArc / 260), 0.5f, 31.1f, 3) - 0.5) * 2 * 22 * near;
            ox[i] = (float) (-dz / l * w); oz[i] = (float) (dx / l * w);
        }
        for (int i = 0; i < rn; i++) { rx[i] += ox[i]; rz[i] += oz[i]; }
        for (int i = 0; i < rn; i++) rh[i] *= (float) (0.8 + 0.4 * Noise.fbm(i * 0.11f, 7.7f, 2.2f, 2));
    }

    /**
     * Rechnet Gitter und Bodenkarten. Vorher werden mit {@link #pad} und {@link #bump} die Stellen
     * eingetragen, deren Höhe bekannt ist, und die Hügel (etwa der Aussichtspunkt über Midway).
     */
    public void build() {
        fill(fine); fill(mid); fill(far);
        rFine = new Raster(fine.x0, fine.x1(), fine.z0, fine.z1(), 4);
        rMid = new Raster(mid.x0, mid.x1(), mid.z0, mid.z1(), 24);
        fillRaster(rFine);
        fillRaster(rMid);
        edges(rFine);
    }

    private static double catmull(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    /** Nächster Punkt am Fluss: Abstand, Wasserspiegel, halbe Breite, Fließrichtung x, z; o[5] = Seite (+1 rechts). */
    public void nearest(double x, double z, double[] o) {
        double best = Double.MAX_VALUE, be = 0, bh = 10, bw = 40, bfx = 0, bfz = -1, side = 0;
        for (int i = 0; i + 1 < rn; i++) {
            double ax = rx[i], az = rz[i], dx = rx[i + 1] - ax, dz = rz[i + 1] - az;
            double l2 = dx * dx + dz * dz;
            double t = ((x - ax) * dx + (z - az) * dz) / l2;
            t = t < 0 ? 0 : (t > 1 ? 1 : t);
            double qx = ax + dx * t - x, qz = az + dz * t - z;
            double d2 = qx * qx + qz * qz;
            if (d2 < best) {
                best = d2;
                be = re[i] + (re[i + 1] - re[i]) * t;
                bh = rh[i] + (rh[i + 1] - rh[i]) * t;
                bw = rw[i] + (rw[i + 1] - rw[i]) * t;
                double l = Math.sqrt(l2);
                bfx = dx / l; bfz = dz / l;
                // rechts in Fließrichtung (x Osten, z Süden): Kreuzprodukt
                side = Math.signum(dx * (z - az) - dz * (x - ax));
            }
        }
        o[0] = Math.sqrt(best); o[1] = be; o[2] = bh; o[3] = bfx; o[4] = bfz;
        if (o.length > 5) o[5] = side;
        if (o.length > 6) o[6] = bw;
    }

    /** Anteil Hang 0..1 (0 Talboden, 1 Hang) für den Abstand d zur Talachse bei halber Talbodenbreite rw. */
    private static double wallT(double x, double z, double d, double rw) {
        double n1 = Noise.fbm((float) (x / 900), (float) (z / 900), 1.3f, 3);
        return smoothD(rw + 10, rw + 230, d + 90 * (n1 - 0.5));
    }

    /** Die Höhenformel an (x, z), ohne Gitter. */
    public double exact(double x, double z) {
        double[] o = new double[7];
        nearest(x, z, o);
        return height(x, z, o);
    }

    private double height(double x, double z, double[] o) {
        double d = o[0], ws = o[1], hf = o[2], rw = o[6];
        double s = Math.max(0, d - rw);
        double nA = Noise.fbm((float) (x / 2500), (float) (z / 2500), 7.1f, 3);
        double nB = Noise.fbm((float) (x / 260), (float) (z / 260), 13.7f, 4);
        double nC = Noise.fbm((float) (x / 900), (float) (z / 900), 4.4f, 4);
        // Hangprofil: oberhalb des Talbodens steil, dann flacher bis zum Plateau (Kellerwald)
        double hmax = 185 + 60 * (nA - 0.5);
        double prof = hmax * (1 - Math.exp(-s / 600));
        // Rippen und Rinnen quer zum Hang, Kuppen auf dem Plateau
        double ribs = 16 * (Noise.fbm((float) (x / 150), (float) (z / 150), 3.9f, 4) - 0.5) * smoothD(10, 160, s);
        double hills = 45 * (nC - 0.45) * smoothD(250, 900, s);
        double nearK = smoothD(hf, hf + 60, d);
        // Aue: sanfte Wellen auf dem flachen Talboden
        double flood = 1.4 * (nB - 0.5) * (1 - smoothD(0.6 * rw, rw + 20, d)) * nearK;
        // Flussbett 1,6 m unter dem Spiegel, Ufer 0,6 m darüber
        double carve = -2.2 * (1 - smoothD(0.7 * hf, 1.4 * hf, d));
        double micro = 0.8 * (Noise.fbm((float) (x / 40), (float) (z / 40), 3.3f, 2) - 0.5) * (1 - smoothD(0, 200, s)) * nearK;
        double r = Math.hypot(x, z);
        double rn2 = Noise.fbm((float) (x / 9000), (float) (z / 9000), 21.9f, 5);
        double ridge = 1 - Math.abs(2 * rn2 - 1);
        double mount = (160 * rn2 + 340 * ridge * ridge * ridge) * smoothD(5500, 20000, r) * smoothD(300, 1500, s);
        double h = ws + 0.6 + prof + ribs + hills + flood + carve + micro + mount;
        for (double[] b : bumps) h += b[3] * Math.exp(-((x - b[0]) * (x - b[0]) + (z - b[1]) * (z - b[1])) / (b[2] * b[2]));
        for (double[] p : pads) {
            double dd = Math.hypot(x - p[0], z - p[1]);
            if (dd >= p[2] + p[4]) continue;
            double lvl = p[3];
            if (lvl != lvl) continue;
            double w = 1 - smoothD(p[2], p[2] + p[4], dd);
            h += (lvl - h) * w;
        }
        Shaper sh = shaper;
        if (sh != null) h = sh.shape(x, z, h);
        return h;
    }

    private static double gauss(double dx, double dz, double r) { return Math.exp(-(dx * dx + dz * dz) / (r * r)); }

    private void fill(Grid g) {
        int w = g.nx + 1;
        IntStream.range(0, g.nz + 1).parallel().forEach(j -> {
            double[] o = new double[7];
            for (int i = 0; i <= g.nx; i++) {
                double x = g.x0 + i * g.cell, z = g.z0 + j * g.cell;
                nearest(x, z, o);
                g.h[j * w + i] = (float) height(x, z, o);
            }
        });
        for (int j = 0; j <= g.nz; j++) {
            for (int i = 0; i <= g.nx; i++) {
                int a = j * w + i;
                double hl = g.h[j * w + Math.max(0, i - 1)], hr = g.h[j * w + Math.min(g.nx, i + 1)];
                double hu = g.h[Math.max(0, j - 1) * w + i], hd = g.h[Math.min(g.nz, j + 1) * w + i];
                double sx = (hr - hl) / ((Math.min(g.nx, i + 1) - Math.max(0, i - 1)) * g.cell);
                double sz = (hd - hu) / ((Math.min(g.nz, j + 1) - Math.max(0, j - 1)) * g.cell);
                double l = Math.sqrt(sx * sx + 1 + sz * sz);
                g.nxs[a] = (float) (-sx / l); g.nys[a] = (float) (1 / l); g.nzs[a] = (float) (-sz / l);
            }
        }
    }

    private void fillRaster(Raster r) {
        IntStream.range(0, r.nz).parallel().forEach(j -> {
            double[] o = new double[7];
            for (int i = 0; i < r.nx; i++) {
                double x = r.x0 + i * r.cell, z = r.z0 + j * r.cell;
                nearest(x, z, o);
                int p = j * r.nx + i;
                double d = o[0], hf = o[2];
                r.dist[p] = (float) (d - hf);
                r.fx[p] = (float) o[3]; r.fz[p] = (float) o[4];
                double s = 0;
                for (double[] q : SINTER) s = Math.max(s, gauss(x - q[0], z - q[1], q[2]));
                double ns = Noise.fbm((float) (x / 70), (float) (z / 70), 5.5f, 3);
                double rw = o[6];
                double wt = wallT(x, z, d, rw);
                // flache Sinterflächen am Talboden, dazu die großen Gruppen
                double flats = smoothD(0.54, 0.66, Noise.fbm((float) (x / 160), (float) (z / 160), 9.1f, 3)) * (1 - smoothD(0.05, 0.3, wt))
                        * smoothD(hf + 10, hf + 40, d) * (1 - smoothD(260, 420, d));
                double raw = Math.max(s * (0.55 + 0.9 * ns), flats * 0.0);
                double sn = smoothD(0.30, 0.42, raw) * (0.8 + 0.2 * smoothD(0.42, 0.7, raw));
                sn *= smoothD(hf + 3, hf + 12, d);
                double hh = height(x, z, o);
                double above = hh - o[1];
                // Wald: Hänge und Plateau, nicht auf Aue, Uferstreifen und unter dem Seespiegel; Lichtungen aus Rauschen
                double nf = Noise.fbm((float) (x / 180), (float) (z / 180), 17.3f, 3);
                double fo = Math.max(smoothD(0.10, 0.42, wt + 0.3 * (nf - 0.5)), smoothD(330, 520, d) * smoothD(0.42, 0.58, nf));
                fo *= smoothD(2, 14, hh);
                fo *= smoothD(rw + 6, rw + 40, d);
                r.sinter[p] = (float) sn;
                r.forest[p] = (float) Math.max(0, Math.min(1, fo));
            }
        });
    }

    /** Abstand zum Waldrand (Meter) je Rasterpunkt, Chamfer-Abstandstransformation in zwei Durchgängen. */
    private static void edges(Raster r) {
        int nx = r.nx, nz = r.nz;
        float[] e = new float[nx * nz];
        float c = (float) r.cell, cd = c * 1.4142f, big = 1e9f;
        for (int p = 0; p < e.length; p++) e[p] = r.forest[p] > 0.5f ? big : 0;
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) {
            int p = j * nx + i;
            if (e[p] == 0) continue;
            float v = e[p];
            if (i > 0) v = Math.min(v, e[p - 1] + c);
            if (j > 0) v = Math.min(v, e[p - nx] + c);
            if (i > 0 && j > 0) v = Math.min(v, e[p - nx - 1] + cd);
            if (i < nx - 1 && j > 0) v = Math.min(v, e[p - nx + 1] + cd);
            e[p] = v;
        }
        for (int j = nz - 1; j >= 0; j--) for (int i = nx - 1; i >= 0; i--) {
            int p = j * nx + i;
            if (e[p] == 0) continue;
            float v = e[p];
            if (i < nx - 1) v = Math.min(v, e[p + 1] + c);
            if (j < nz - 1) v = Math.min(v, e[p + nx] + c);
            if (i < nx - 1 && j < nz - 1) v = Math.min(v, e[p + nx + 1] + cd);
            if (i > 0 && j < nz - 1) v = Math.min(v, e[p + nx - 1] + cd);
            e[p] = v;
        }
        r.edge = e;
    }

    private static double smoothD(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static float smooth(float a, float b, float x) {
        float t = (x - a) / (b - a);
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------ Abfragen zur Laufzeit

    /** Bodenhöhe genau wie im Netz. */
    public float sample(double x, double z) {
        if (fine.contains(x, z)) return fine.at(x, z);
        if (mid.contains(x, z)) return mid.at(x, z);
        if (far.contains(x, z)) return far.at(x, z);
        return far.h[0];
    }

    /**
     * Himmelssicht 0..1 am Boden (x, z) in Höhe h: je Richtung der höchste Horizont in 30, 90 und
     * 250 m Abstand. In Mulden und am Fuß der Hänge sieht der Boden weniger Himmel und wird dunkler.
     */
    public float skyView(double x, double z, double h) {
        double sum = 0;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + 0.2, cx = Math.cos(a), cz = Math.sin(a), best = 0;
            for (double r : new double[]{30, 90, 250}) {
                double e = (sample(x + cx * r, z + cz * r) - h) / r;
                if (e > best) best = e;
            }
            sum += best / Math.sqrt(1 + best * best);
        }
        return (float) Math.max(0.35, 1 - sum / 8 * 1.3);
    }

    /** Wasserspiegel des Flusses an der Stelle des Flusspunktes i. */
    public float riverLevel(int i) { return re[i]; }

    public int riverPoints() { return rn; }
    public float riverX(int i) { return rx[i]; }
    public float riverZ(int i) { return rz[i]; }
    public float riverHalf(int i) { return rh[i]; }

    private static final float[] NONE = {9999, 0, -1, 0, 1};

    private Raster rasterAt(double x, double z) {
        if (rFine.contains(x, z)) return rFine;
        if (rMid.contains(x, z)) return rMid;
        return null;
    }

    /**
     * Bodenkarte am Punkt: Abstand zum Ufer (negativ im Wasser), Fließrichtung x, z, Sinter und Wald
     * 0..1, bilinear. Außerhalb der Raster: weit vom Fluss, Wald.
     */
    public void ground(float x, float z, float[] o) {
        Raster r = rasterAt(x, z);
        if (r == null) { System.arraycopy(NONE, 0, o, 0, 5); return; }
        float u = (float) ((x - r.x0) / r.cell), v = (float) ((z - r.z0) / r.cell);
        int i = Math.min(r.nx - 2, (int) u), j = Math.min(r.nz - 2, (int) v);
        float fu = Math.min(1, u - i), fv = Math.min(1, v - j);
        int p = j * r.nx + i, q = p + r.nx;
        o[0] = bil(r.dist, p, q, fu, fv);
        o[1] = r.fx[fu < 0.5f ? (fv < 0.5f ? p : q) : (fv < 0.5f ? p + 1 : q + 1)];
        o[2] = r.fz[fu < 0.5f ? (fv < 0.5f ? p : q) : (fv < 0.5f ? p + 1 : q + 1)];
        o[3] = bil(r.sinter, p, q, fu, fv);
        o[4] = bil(r.forest, p, q, fu, fv);
    }

    /** Wald 0..1 und Abstand zum Waldrand in Metern (nur in den feinen Rastern, sonst −1). */
    public float forestEdge(double x, double z) {
        Raster r = rFine.contains(x, z) ? rFine : null;
        if (r == null) return -1;
        int i = (int) Math.round((x - r.x0) / r.cell), j = (int) Math.round((z - r.z0) / r.cell);
        i = Math.max(0, Math.min(r.nx - 1, i)); j = Math.max(0, Math.min(r.nz - 1, j));
        return r.edge[j * r.nx + i];
    }

    private static float bil(float[] a, int p, int q, float fu, float fv) {
        float ab = a[p] + (a[p + 1] - a[p]) * fu, cd = a[q] + (a[q + 1] - a[q]) * fu;
        return ab + (cd - ab) * fv;
    }

    /** Nur Abstand zum Ufer (für den Dunst über dem Fluss); 9999 außerhalb der Raster. */
    public float riverDist(float x, float z) {
        Raster r = rasterAt(x, z);
        if (r == null) return 9999;
        float u = (float) ((x - r.x0) / r.cell), v = (float) ((z - r.z0) / r.cell);
        int i = Math.min(r.nx - 2, (int) u), j = Math.min(r.nz - 2, (int) v);
        int p = j * r.nx + i;
        return bil(r.dist, p, p + r.nx, Math.min(1, u - i), Math.min(1, v - j));
    }

    /**
     * Farbe des Bodens (linear) am Punkt mit Normale n; foot ist die Größe eines Pixels in Metern.
     * Wiese in Grüntönen mit Schlägen, Mischwald als dunkle Kronendecke mit Lichtungen, nasser Kies am
     * Ufer, Grauwacke an steilen Stellen, Schnee nach {@link Thermal#snow}.
     */
    public void albedo(float x, float y, float z, float nx, float ny, float nz, float foot, float[] gm, float[] o) {
        ground(x, z, gm);
        float bank = gm[0], rockP = gm[3], fo = gm[4];
        float big = Noise.tex(x * 0.011f, z * 0.011f), mid = Noise.tex(x * 0.07f + 3.1f, z * 0.07f + 8.7f);
        float fineVis = 1 - smooth(0.15f, 0.9f, foot);
        float fn = fineVis > 0 ? Noise.tex(x * 0.9f + 17.7f, z * 0.9f + 1.3f) - 0.5f : 0;
        // Wiese: frisches Grün, dazwischen hellere, abgeerntete Schläge
        float g = smooth(0.35f, 0.7f, Noise.tex(x * 0.02f + 3.1f, z * 0.02f + 8.7f));
        float r0 = 0.052f + 0.035f * g + 0.02f * (big - 0.5f) + 0.012f * (mid - 0.5f),
                g0 = 0.100f + 0.030f * g + 0.03f * (big - 0.5f) + 0.015f * (mid - 0.5f),
                b0 = 0.030f + 0.010f * g;
        r0 += fn * 0.03f * fineVis; g0 += fn * 0.045f * fineVis; b0 += fn * 0.015f * fineVis;
        // Mischwald: Buchen und Fichten, dunkle Kronendecke mit Lichtungen und helleren Laubflecken
        if (fo > 0.01f) {
            float bl = Noise.tex(x * 0.05f + 1.7f, z * 0.05f + 4.4f);
            float gap = smooth(0.66f, 0.74f, Noise.tex(x * 0.12f + 5.3f, z * 0.12f + 2.1f)) * 0.55f;
            float beech = smooth(0.45f, 0.65f, Noise.tex(x * 0.008f + 9.9f, z * 0.008f + 4.2f));
            float cr = 0.020f + 0.016f * bl + 0.030f * beech, cg = 0.040f + 0.020f * bl + 0.028f * beech, cb = 0.018f + 0.008f * bl;
            cr += (0.09f - cr) * gap * 0.5f; cg += (0.10f - cg) * gap * 0.5f; cb += (0.05f - cb) * gap * 0.5f;
            r0 += (cr - r0) * fo; g0 += (cg - g0) * fo; b0 += (cb - b0) * fo;
        }
        // Ufer: nasser Kies und dunkle Erde bis ein paar Meter über dem Wasser
        float wet = 1 - smooth(0, 7, bank);
        if (wet > 0) {
            float kr = 0.085f + 0.03f * mid, kg = 0.080f + 0.025f * mid, kb = 0.065f + 0.02f * mid;
            r0 += (kr - r0) * wet; g0 += (kg - g0) * wet; b0 += (kb - b0) * wet;
        }
        // Fels: Grauwacke, graubraun, an steilen Hängen und auf Felsflächen
        float rock = Math.max(1 - smooth(0.70f, 0.86f, ny), rockP);
        if (rock > 0) {
            float rr = 0.150f + 0.07f * mid, rg = 0.140f + 0.06f * mid, rb = 0.120f + 0.05f * mid;
            r0 += (rr - r0) * rock; g0 += (rg - g0) * rock; b0 += (rb - b0) * rock;
        }
        o[0] = r0; o[1] = g0; o[2] = b0;
        // Schnee: auf flachem Boden geschlossen, im Wald fleckig
        float snow = Thermal.snow;
        if (snow > 0.01f) {
            float flat = smooth(0.62f, 0.9f, ny);
            float patch = Noise.tex(x * 0.04f + 13.1f, z * 0.04f + 2.7f);
            float cover = flat * smooth(0.0f, 0.35f, snow + (patch - 0.5f) * 0.5f);
            cover *= 1 - 0.55f * fo;
            cover *= smooth(1.5f, 4, bank);
            if (cover > 0.001f) {
                float drift = 0.93f + 0.07f * mid + (fineVis > 0 ? fn * 0.04f : 0);
                float sr = 0.80f * drift, sg = 0.82f * drift, sb = 0.86f * drift;
                o[0] += (sr - o[0]) * cover; o[1] += (sg - o[1]) * cover; o[2] += (sb - o[2]) * cover;
            }
        }
        Shaper sh = shaper;
        if (sh != null) sh.paint(x, z, foot, o);
        o[0] = Math.max(0.005f, o[0]); o[1] = Math.max(0.005f, o[1]); o[2] = Math.max(0.005f, o[2]);
    }
}
