package com.dan.talsperre.world;

import com.dan.talsperre.core.Mat;
import com.dan.talsperre.core.MeshBuilder;
import com.dan.talsperre.core.Terrain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Die Straße über die Staumauer und ihre Anschlüsse: von der Westseite am Hang entlang und ins Edertal
 * hinunter (nach Norden), von der Ostseite hoch über dem Seeufer nach Süden. Beide folgen einer
 * Höhenlinie; das Band liegt je Kante auf dem Gelände. Dazu eine gestrichelte Mittellinie. Die
 * gesamte Strecke (Tal – Krone – Seeufer) dient dem Verkehr als Fahrweg {@link #route}.
 */
public final class Roads {
    private Roads() { }

    public static final double WIDTH = 6.4;

    /** Fahrweg: Punkte (x, y, z), Bogenlänge s; von der Talstraße im Norden über die Krone zur Seestraße im Süden. */
    public static double[] rx = new double[0], ry = new double[0], rz = new double[0], rs = new double[0];

    private static Set<Long> keep = new HashSet<>();

    /** Liegt (x, z) auf oder dicht neben der Straße (für den Wald)? */
    public static boolean near(double x, double z) {
        return keep.contains(key(x, z));
    }

    private static long key(double x, double z) {
        return ((long) Math.floor(x / 12) << 32) ^ (long) Math.floor(z / 12) & 0xFFFFFFFFL;
    }

    private static void mark(double x, double z) {
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) keep.add(key(x + 12 * i, z + 12 * j));
    }

    public static void build(MeshBuilder mb, Terrain t) {
        keep = new HashSet<>();
        double ex = Dam.x(Dam.PHI_MAX, Dam.R - 3), ez = Dam.z(Dam.PHI_MAX, Dam.R - 3);
        List<double[]> west = contour(t, true, ez, -1300, -1);
        List<double[]> east = contour(t, false, ez, 1300, 1);
        // Anschluss an die Krone: das Band beginnt am Kronenende
        west.add(0, new double[]{-ex, ez});
        east.add(0, new double[]{ex, ez});
        double me = mb.maxEdge;
        mb.maxEdge = 1e6;
        double[][] w = ribbon(mb, t, west);
        double[][] e = ribbon(mb, t, east);
        mb.maxEdge = me;
        // Fahrweg: Westband rückwärts, Krone, Ostband
        List<double[]> r = new ArrayList<>();
        for (int i = w.length - 1; i >= 0; i--) r.add(w[i]);
        for (double d = -Dam.PHI_MAX; d <= Dam.PHI_MAX + 1e-9; d += Math.toRadians(2))
            r.add(new double[]{Dam.x(d, Dam.R - 3), Dam.TOP + 0.05, Dam.z(d, Dam.R - 3)});
        for (double[] q : e) r.add(q);
        int n = r.size();
        rx = new double[n]; ry = new double[n]; rz = new double[n]; rs = new double[n];
        for (int i = 0; i < n; i++) {
            rx[i] = r.get(i)[0]; ry[i] = r.get(i)[1]; rz[i] = r.get(i)[2];
            rs[i] = i == 0 ? 0 : rs[i - 1] + Math.hypot(Math.hypot(rx[i] - rx[i - 1], rz[i] - rz[i - 1]), ry[i] - ry[i - 1]);
        }
        System.out.println("Straße: " + n + " Punkte, " + Math.round(rs[n - 1]) + " m");
    }

    /**
     * Höhenlinie am Hang: je Schritt in z die erste Stelle von der Talsohle (tiefster Punkt) aus nach
     * außen (west bzw. ost), an der das Gelände die Zielhöhe erreicht. Westseite: 6 m, ab z = −600 sinkend auf die
     * Talsohle +9; Ostseite: 12 m über dem Stauziel.
     */
    private static List<double[]> contour(Terrain t, boolean west, double z0, double z1, int dir) {
        List<double[]> raw = new ArrayList<>();
        double step = 6;
        for (double z = z0 + dir * 20; dir > 0 ? z <= z1 : z >= z1; z += dir * step) {
            // Talsohle: tiefster Punkt auf der Linie
            double bx = 0, bh = 1e9;
            for (double x = -700; x <= 700; x += 8) { double h = t.sample(x, z); if (h < bh) { bh = h; bx = x; } }
            double c;
            if (west) {
                c = 6;
                if (z < -600) c = 6 + (Math.max(-900.0, z) + 600) / 300.0 * (6 - (bh + 9));
                c = Math.max(c, bh + 9);
            } else {
                c = 12;
            }
            double sgn = west ? -1 : 1;
            double x = bx;
            double h = t.sample(x, z);
            int guard = 0;
            while (h < c && guard++ < 160) { x += sgn * 6; h = t.sample(x, z); }
            double h0 = t.sample(x - sgn * 6, z);
            if (h > h0 + 1e-6) x -= sgn * 6 * (h - c) / (h - h0);
            raw.add(new double[]{x, z});
        }
        // glätten
        int n = raw.size();
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double sx = 0; int cnt = 0;
            for (int k = -8; k <= 8; k++) { int j = i + k; if (j < 0 || j >= n) continue; sx += raw.get(j)[0]; cnt++; }
            out.add(new double[]{sx / cnt, raw.get(i)[1]});
        }
        return out;
    }

    /** Baut das Band; liefert die Mittellinie als (x, y, z). */
    private static double[][] ribbon(MeshBuilder mb, Terrain t, List<double[]> pts) {
        int n = pts.size();
        double[][] cl = new double[n][3];
        int[] left = new int[n], right = new int[n];
        double[] hy = new double[n], yl = new double[n], yr = new double[n];
        double[][] nrm = new double[n][2];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            double dx = pts.get(b)[0] - pts.get(a)[0], dz = pts.get(b)[1] - pts.get(a)[1];
            double l = Math.hypot(dx, dz);
            nrm[i][0] = -dz / l; nrm[i][1] = dx / l;
        }
        double h = WIDTH / 2;
        double[] gl = new double[n], gr = new double[n];
        for (int i = 0; i < n; i++) {
            double x = pts.get(i)[0], z = pts.get(i)[1];
            gl[i] = t.sample(x + nrm[i][0] * h, z + nrm[i][1] * h);
            gr[i] = t.sample(x - nrm[i][0] * h, z - nrm[i][1] * h);
            hy[i] = t.sample(x, z);
        }
        double acc = 0;
        for (int i = 0; i < n; i++) {
            double sl = 0, sr = 0, sc = 0; int c = 0;
            for (int k = -3; k <= 3; k++) { int j = i + k; if (j < 0 || j >= n) continue; sl += gl[j]; sr += gr[j]; sc += hy[j]; c++; }
            double fade = Math.min(1, acc / 25.0);
            double top = Dam.TOP + 0.05;
            yl[i] = Math.max(top + (sl / c + 0.22 - top) * fade, sl / c + 0.12);
            yr[i] = Math.max(top + (sr / c + 0.22 - top) * fade, sr / c + 0.12);
            if (i > 0) acc += Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0], pts.get(i)[1] - pts.get(i - 1)[1]);
            cl[i] = new double[]{pts.get(i)[0], (yl[i] + yr[i]) / 2, pts.get(i)[1]};
            mark(pts.get(i)[0], pts.get(i)[1]);
            double px = nrm[i][0], pz = nrm[i][1];
            left[i] = mb.v(cl[i][0] + px * h, yl[i], cl[i][2] + pz * h, 0, 1, 0);
            right[i] = mb.v(cl[i][0] - px * h, yr[i], cl[i][2] - pz * h, 0, 1, 0);
        }
        for (int i = 0; i + 1 < n; i++) {
            mb.tri(left[i], right[i], right[i + 1], Mat.ASPHALT);
            mb.tri(left[i], right[i + 1], left[i + 1], Mat.ASPHALT);
        }
        // Mittellinie: Striche von 4 m alle 12 m
        double run = 0;
        for (int i = 0; i + 1 < n; i++) {
            double seg = Math.hypot(cl[i + 1][0] - cl[i][0], cl[i + 1][2] - cl[i][2]);
            double u0 = run % 12, u1 = u0 + seg;
            if (u0 < 4 || u1 > 12 && (u1 % 12) < 4) {
                double dx = (cl[i + 1][0] - cl[i][0]) / seg, dz = (cl[i + 1][2] - cl[i][2]) / seg;
                double px = -dz * 0.1, pz = dx * 0.1;
                double y0 = cl[i][1] + 0.05, y1 = cl[i + 1][1] + 0.05;
                int a = mb.v(cl[i][0] + px, y0, cl[i][2] + pz, 0, 1, 0), b = mb.v(cl[i][0] - px, y0, cl[i][2] - pz, 0, 1, 0);
                int c = mb.v(cl[i + 1][0] - px, y1, cl[i + 1][2] - pz, 0, 1, 0), d = mb.v(cl[i + 1][0] + px, y1, cl[i + 1][2] + pz, 0, 1, 0);
                mb.tri(a, b, c, Mat.PAINT);
                mb.tri(a, c, d, Mat.PAINT);
            }
            run += seg;
        }
        return cl;
    }
}
