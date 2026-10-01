package com.dan.talsperre.core;

/** Schnelles Wert-Rauschen für prozedurale Materialien (aus Semiramis). */
public final class Noise {
    private Noise() { }

    public static int hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    /** Zufallswert 0..1 je Gitterzelle. */
    public static float cell(int x, int y, int z) {
        return (hash(x, y, z) & 0xFFFFFF) / 16777215f;
    }

    private static int fl(float v) { int i = (int) v; return v < i ? i - 1 : i; }

    /** Glattes Rauschen 0..1. */
    public static float value(float x, float y, float z) {
        int xi = fl(x), yi = fl(y), zi = fl(z);
        float fx = x - xi, fy = y - yi, fz = z - zi;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz);
        float a = cell(xi, yi, zi), b = cell(xi + 1, yi, zi), c = cell(xi, yi + 1, zi), d = cell(xi + 1, yi + 1, zi);
        float e = cell(xi, yi, zi + 1), f = cell(xi + 1, yi, zi + 1), g = cell(xi, yi + 1, zi + 1), h = cell(xi + 1, yi + 1, zi + 1);
        float ab = a + (b - a) * fx, cd = c + (d - c) * fx, ef = e + (f - e) * fx, gh = g + (h - g) * fx;
        float abcd = ab + (cd - ab) * fy, efgh = ef + (gh - ef) * fy;
        return abcd + (efgh - abcd) * fz;
    }

    // ------------------------------------------------------------ Tabellen für die Bildschleife

    private static final int TN = 512, TM = TN - 1, CELLS = 64, PER = TN / CELLS;
    /** Kachelbares fraktales Rauschen (4 Oktaven) auf 64 × 64 Gitterzellen, 8 Tabellenpunkte je Zelle. */
    private static final float[] TEX = new float[TN * TN];

    static {
        for (int j = 0; j < TN; j++) {
            for (int i = 0; i < TN; i++) {
                float s = 0, a = 0.5f, n = 0;
                int period = CELLS;
                float fx = i / (float) PER, fy = j / (float) PER;
                for (int o = 0; o < 4; o++) {
                    s += a * periodic(fx, fy, period, o);
                    n += a;
                    fx *= 2; fy *= 2; period *= 2;
                    a *= 0.5f;
                }
                TEX[j * TN + i] = s / n;
            }
        }
    }

    private static float periodic(float x, float y, int period, int layer) {
        int xi = fl(x), yi = fl(y);
        float fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        int x0 = Math.floorMod(xi, period), x1 = Math.floorMod(xi + 1, period);
        int y0 = Math.floorMod(yi, period), y1 = Math.floorMod(yi + 1, period);
        float a = cell(x0, y0, layer), b = cell(x1, y0, layer), c = cell(x0, y1, layer), d = cell(x1, y1, layer);
        float ab = a + (b - a) * fx, cd = c + (d - c) * fx;
        return ab + (cd - ab) * fy;
    }

    /**
     * Fraktales Rauschen 0..1 in der Ebene aus der Tabelle, bilinear gelesen; eine Einheit entspricht
     * einer Gitterzelle wie bei {@link #fbm}. Wiederholt sich alle 64 Einheiten.
     */
    public static float tex(float x, float y) {
        float u = x * PER, v = y * PER;
        int iu = fl(u), iv = fl(v);
        float fu = u - iu, fv = v - iv;
        int u0 = iu & TM, u1 = (iu + 1) & TM, v0 = (iv & TM) * TN, v1 = ((iv + 1) & TM) * TN;
        float a = TEX[v0 + u0], b = TEX[v0 + u1], c = TEX[v1 + u0], d = TEX[v1 + u1];
        float ab = a + (b - a) * fu, cd = c + (d - c) * fu;
        return ab + (cd - ab) * fv;
    }

    private static final float[] EXP = new float[4097];

    static {
        for (int i = 0; i <= 4096; i++) EXP[i] = (float) Math.exp(-i / 256.0);
    }

    /** e^(−x) für x ≥ 0 aus einer Tabelle (Fehler unter 0,1 %), 0 ab x = 16. */
    public static float expNeg(float x) {
        float f = x * 256;
        if (f >= 4096) return 0;
        if (f <= 0) return 1;
        int i = (int) f;
        float t = f - i;
        return EXP[i] + (EXP[i + 1] - EXP[i]) * t;
    }

    /** Fraktales Rauschen 0..1. */
    public static float fbm(float x, float y, float z, int oct) {
        float s = 0, a = 0.5f, n = 0;
        for (int i = 0; i < oct; i++) {
            s += a * value(x, y, z);
            n += a;
            x = x * 2.03f + 17.1f; y = y * 2.03f + 3.7f; z = z * 2.03f + 9.2f;
            a *= 0.5f;
        }
        return s / n;
    }
}
