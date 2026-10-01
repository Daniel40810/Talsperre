package com.dan.talsperre.core;

import com.dan.talsperre.effects.ParticleSystem;

/**
 * Grobes Dichteraster der Dampf- und Gischtwolken (Zellen zu 3 m, als Hashtabelle), neu aufgebaut
 * in jedem Bild. Damit kann jedes Teilchen fragen, wie viel Wolke zwischen ihm und der Sonne liegt
 * (Selbstschatten: die Sonnenseite einer Dampfsäule ist hell, die abgewandte grau) und wie dicht es
 * um es herum ist (weniger Himmelslicht im Innern).
 */
public final class SteamGrid {
    static final float CELL = 3f, INV = 1 / CELL;
    private static final int SIZE = 1 << 16, MASK = SIZE - 1;
    private static final long EMPTY = Long.MIN_VALUE;
    private final long[] keys = new long[SIZE];
    private final float[] val = new float[SIZE];
    private int used;

    public SteamGrid() { java.util.Arrays.fill(keys, EMPTY); }

    private static long key(int i, int j, int k) {
        return ((long) (i & 0x1FFFFF) << 42) | ((long) (j & 0x1FFFFF) << 21) | (k & 0x1FFFFF);
    }

    private static int hash(long k) {
        long h = k * 0x9E3779B97F4A7C15L;
        return (int) (h >>> 40) & MASK;
    }

    private void add(int i, int j, int k, float v) {
        long kk = key(i, j, k);
        int h = hash(kk);
        while (true) {
            long c = keys[h];
            if (c == kk) { val[h] += v; return; }
            if (c == EMPTY) {
                if (used > SIZE * 3 / 4) return;       // voll: Rest weglassen
                keys[h] = kk; val[h] = v; used++; return;
            }
            h = (h + 1) & MASK;
        }
    }

    private float get(int i, int j, int k) {
        long kk = key(i, j, k);
        int h = hash(kk);
        while (true) {
            long c = keys[h];
            if (c == kk) return val[h];
            if (c == EMPTY) return 0;
            h = (h + 1) & MASK;
        }
    }

    /** Baut das Raster aus allen Dampf- und Gischtteilchen. Dichte in 1/m (Extinktion). */
    public void build(ParticleSystem ps) {
        if (used > 0) { java.util.Arrays.fill(keys, EMPTY); used = 0; }
        for (int p = 0; p < ps.n; p++) {
            byte k = ps.kind[p];
            if (k != ParticleSystem.STEAM && k != ParticleSystem.SPRAY) continue;
            float a01 = ps.age[p] / ps.life[p];
            float al = ps.alpha[p] * (1 - a01);
            if (al < 0.01f) continue;
            float r = Math.min(6f, ps.size[p]);
            // Querschnitt des Teilchens verteilt auf das Zellvolumen
            float v = al * r * r * 3.1416f * (k == ParticleSystem.SPRAY ? 0.6f : 0.35f) / (CELL * CELL * CELL);
            add((int) Math.floor(ps.x[p] * INV), (int) Math.floor(ps.y[p] * INV), (int) Math.floor(ps.z[p] * INV), v);
        }
    }

    /** Dichte am Ort (1/m). */
    public float at(float x, float y, float z) {
        if (used == 0) return 0;
        return get((int) Math.floor(x * INV), (int) Math.floor(y * INV), (int) Math.floor(z * INV));
    }

    /** Optische Tiefe vom Punkt in Richtung (lx,ly,lz), 8 Schritte bis 40 m. */
    public float toward(float x, float y, float z, float lx, float ly, float lz) {
        if (used == 0) return 0;
        float od = 0, t = 2f;
        for (int s = 0; s < 8; s++) {
            float st = 2f + s * 0.9f;
            od += at(x + lx * t, y + ly * t, z + lz * t) * st;
            t += st;
        }
        return od;
    }
}
