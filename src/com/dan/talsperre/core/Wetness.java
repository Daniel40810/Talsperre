package com.dan.talsperre.core;

/**
 * Nässe am Boden um einen Geysir: ein Raster von 0,8 m, in das jeder landende Tropfen Wasser
 * einträgt. Das Wasser läuft bergab zur tiefsten Nachbarzelle und trocknet, in der Sonne schneller.
 * Der Bildrechner macht nassen Boden dunkler und glänzend.
 */
public final class Wetness {
    public static final int N = 160;
    public static final float CELL = 0.8f;
    final float x0, z0;
    final float[] w = new float[N * N];
    private final float[] nw = new float[N * N];
    /** Tiefste Nachbarzelle (−1 = keine tiefere). */
    private final int[] down = new int[N * N];
    private float acc;

    public Wetness(Terrain t, double cx, double cz) {
        x0 = (float) (cx - N * CELL / 2);
        z0 = (float) (cz - N * CELL / 2);
        float[] h = new float[N * N];
        for (int j = 0; j < N; j++) for (int i = 0; i < N; i++) h[j * N + i] = t.sample(x0 + (i + 0.5) * CELL, z0 + (j + 0.5) * CELL);
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                int p = j * N + i, best = -1;
                float bh = h[p] - 0.005f;
                for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) {
                    int ii = i + di, jj = j + dj;
                    if ((di == 0 && dj == 0) || ii < 0 || jj < 0 || ii >= N || jj >= N) continue;
                    int q = jj * N + ii;
                    if (h[q] < bh) { bh = h[q]; best = q; }
                }
                down[p] = best;
            }
        }
    }

    public boolean contains(float x, float z) {
        return x >= x0 && z >= z0 && x < x0 + N * CELL && z < z0 + N * CELL;
    }

    /** Wasser eintragen (Anteil einer Zelle, 1 = ganz nass). */
    public void add(float x, float z, float amount) {
        int i = (int) ((x - x0) / CELL), j = (int) ((z - z0) / CELL);
        if (i < 0 || j < 0 || i >= N || j >= N) return;
        int p = j * N + i;
        w[p] = Math.min(2.5f, w[p] + amount);
    }

    /** Abfließen und Trocknen; sun 0..1 beschleunigt das Trocknen. Rechnet höchstens fünfmal je Sekunde. */
    public void step(float dt, float sun) {
        acc += dt;
        if (acc < 0.2f) return;
        float h = acc;
        acc = 0;
        float dry = (0.004f + 0.012f * sun) * h;
        float flow = Math.min(0.9f, 0.6f * h);
        System.arraycopy(w, 0, nw, 0, w.length);
        for (int p = 0; p < w.length; p++) {
            float v = w[p];
            if (v <= 0.02f) continue;
            int q = down[p];
            if (q >= 0) {
                float m = (v - 0.02f) * flow * 0.5f;
                nw[p] -= m;
                nw[q] += m;
            }
        }
        for (int p = 0; p < w.length; p++) w[p] = Math.max(0, Math.min(2.5f, nw[p] - dry * (0.3f + Math.min(1, nw[p]))));
    }

    /** Nässe 0..1 am Punkt, bilinear. */
    public float at(float x, float z) {
        float u = (x - x0) / CELL - 0.5f, v = (z - z0) / CELL - 0.5f;
        if (u < 0 || v < 0 || u >= N - 1 || v >= N - 1) return 0;
        int i = (int) u, j = (int) v;
        float fu = u - i, fv = v - j;
        int p = j * N + i;
        float a = w[p], b = w[p + 1], c = w[p + N], d = w[p + N + 1];
        float ab = a + (b - a) * fu, cd = c + (d - c) * fu;
        return Math.min(1, ab + (cd - ab) * fv);
    }

    /** Alle Nässeraster der Szene. */
    public static final class Set {
        public final java.util.List<Wetness> list = new java.util.ArrayList<>();

        public float at(float x, float z) {
            float best = 0;
            for (int k = 0; k < list.size(); k++) {
                Wetness q = list.get(k);
                if (q.contains(x, z)) best = Math.max(best, q.at(x, z));
            }
            return best;
        }

        public void step(float dt, float sun) { for (Wetness q : list) q.step(dt, sun); }
    }
}
