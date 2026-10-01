package com.dan.talsperre.world;

import com.dan.talsperre.core.Mat;
import com.dan.talsperre.core.MeshBuilder;
import com.dan.talsperre.core.Terrain;
import java.util.ArrayList;
import java.util.List;

/**
 * Die Flutwelle in der Eder: ein breites Wasserband auf den Flusspunkten unterhalb des Tosbeckens. Jede Ecke
 * trägt den Weg s (Meter vom ersten Punkt) als Schwelle; die Welle erscheint mit {@link com.dan.talsperre.core.Mesh#setFlood}
 * Stück für Stück. Die Tiefe nimmt mit dem Weg ab, die Breite zu.
 */
public final class Flood {
    private Flood() { }

    /** Wegpunkte der Welle: Weg, x, y (Wasserspiegel im Fluss), z. */
    public static float[] ps = new float[0], px = new float[0], py = new float[0], pz = new float[0];

    public static double length() { return ps.length == 0 ? 0 : ps[ps.length - 1]; }

    /** Tiefe der Welle (m) am Weg s. */
    public static double depth(double s) { return 2.2 + 8.0 * Math.exp(-s / 320.0); }

    /** Ort am Weg s (linear zwischen den Punkten): x, y (Flussspiegel), z. */
    public static void at(double s, double[] o) {
        int n = ps.length;
        if (n == 0) { o[0] = 0; o[1] = -42; o[2] = -110; return; }
        if (s <= 0) { o[0] = px[0]; o[1] = py[0]; o[2] = pz[0]; return; }
        for (int i = 1; i < n; i++) {
            if (s <= ps[i]) {
                double u = (s - ps[i - 1]) / Math.max(1e-6, ps[i] - ps[i - 1]);
                o[0] = px[i - 1] + (px[i] - px[i - 1]) * u; o[1] = py[i - 1] + (py[i] - py[i - 1]) * u; o[2] = pz[i - 1] + (pz[i] - pz[i - 1]) * u;
                return;
            }
        }
        o[0] = px[n - 1]; o[1] = py[n - 1]; o[2] = pz[n - 1];
    }

    public static void build(MeshBuilder mb, Terrain t) {
        int n = t.riverPoints();
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double x = t.riverX(i), z = t.riverZ(i);
            if (z > -104) continue;
            if (x < t.mid.x0 + 10 || x > t.mid.x1() - 10 || z < t.mid.z0 + 10 || z > t.mid.z1() - 10) continue;
            ids.add(i);
        }
        ids.sort((a, b) -> Float.compare(t.riverZ(b), t.riverZ(a)));
        int m = ids.size();
        ps = new float[m]; px = new float[m]; py = new float[m]; pz = new float[m];
        double s = 0;
        for (int k = 0; k < m; k++) {
            int i = ids.get(k);
            if (k > 0) {
                int j = ids.get(k - 1);
                s += Math.hypot(t.riverX(i) - t.riverX(j), t.riverZ(i) - t.riverZ(j));
            }
            ps[k] = (float) s; px[k] = t.riverX(i); pz[k] = t.riverZ(i); py[k] = t.riverLevel(i);
        }
        int[] prev = null;
        final int C = 9;
        mb.riseMode = 3;
        for (int k = 0; k < m; k++) {
            int i = ids.get(k);
            int a = ids.get(Math.max(0, k - 1)), b = ids.get(Math.min(m - 1, k + 1));
            double dx = t.riverX(b) - t.riverX(a), dz = t.riverZ(b) - t.riverZ(a);
            double l = Math.hypot(dx, dz);
            if (l < 1e-6) continue;
            double qx = -dz / l, qz = dx / l;
            double half = 70 + 0.12 * ps[k];
            mb.riseThr = ps[k];
            int[] cur = new int[C];
            for (int c = 0; c < C; c++) {
                double e = (c - (C - 1) / 2.0) / ((C - 1) / 2.0), o = e * half;
                double x = t.riverX(i) + qx * o, z = t.riverZ(i) + qz * o;
                double dl = depth(ps[k]) * (1 - 0.9 * Math.abs(e) * Math.abs(e));
                cur[c] = mb.v(x, Math.max(t.sample(x, z), t.riverLevel(i)) + dl, z, 0, 1, 0);
            }
            if (prev != null) {
                for (int c = 0; c + 1 < C; c++) {
                    mb.tri(prev[c], cur[c], cur[c + 1], Mat.RIVER);
                    mb.tri(prev[c], cur[c + 1], prev[c + 1], Mat.RIVER);
                }
            }
            prev = cur;
        }
        mb.riseMode = 0;
    }
}
