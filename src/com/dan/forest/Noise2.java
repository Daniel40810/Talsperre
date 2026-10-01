package com.dan.forest;

/** Glattes Wertrauschen in 2D (0..1) und fraktale Summe; klein und ohne Abhängigkeiten. */
public final class Noise2 {
    private Noise2() { }

    static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static float rnd(int x, int y) { return (hash(x, y) & 0xFFFFFF) / (float) 0xFFFFFF; }

    /** Wertrauschen 0..1 mit weicher Interpolation. */
    public static float value(float x, float y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        float a = rnd(ix, iy), b = rnd(ix + 1, iy), c = rnd(ix, iy + 1), d = rnd(ix + 1, iy + 1);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    /** Summe von oct Oktaven, 0..1. */
    public static float fbm(float x, float y, int oct) {
        float s = 0, amp = 0.5f, norm = 0;
        for (int i = 0; i < oct; i++) { s += amp * value(x, y); norm += amp; x = x * 2.03f + 17.1f; y = y * 2.03f + 5.3f; amp *= 0.5f; }
        return s / norm;
    }
}
