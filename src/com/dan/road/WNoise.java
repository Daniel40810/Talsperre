package com.dan.road;

/** Glattes Wertrauschen in 2D (Tabelle 256 × 256), Werte 0..1; ohne Abhängigkeiten. */
public final class WNoise {
    private WNoise() { }

    private static final float[] T = new float[256 * 256];
    static {
        for (int y = 0; y < 256; y++)
            for (int x = 0; x < 256; x++) {
                int h = x * 83492791 ^ y * 29765729;
                h = (h ^ (h >>> 13)) * 1274126177;
                T[y * 256 + x] = ((h ^ (h >>> 16)) & 0xffffff) / (float) 0xffffff;
            }
    }

    private static int fl(float v) { int i = (int) v; return v < i ? i - 1 : i; }

    public static float value(float x, float y) {
        int ix = fl(x), iy = fl(y);
        float fx = x - ix, fy = y - iy;
        float u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy);
        int x0 = ix & 255, y0 = (iy & 255) << 8, x1 = (ix + 1) & 255, y1 = ((iy + 1) & 255) << 8;
        float a = T[y0 | x0], b = T[y0 | x1], c = T[y1 | x0], d = T[y1 | x1];
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    public static float fbm(float x, float y, int oct) {
        float s = 0, a = 0.5f, w = 0;
        for (int i = 0; i < oct; i++) { s += a * value(x, y); w += a; x = x * 2.03f + 17.1f; y = y * 2.03f + 5.3f; a *= 0.5f; }
        return s / w;
    }

    /** Ganzzahliger Hash, gleichverteilt 0..1. */
    public static float hash01(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32;
        return (h >>> 40) / (float) (1L << 24);
    }

    public static float smooth(float a, float b, float x) {
        float t = (x - a) / (b - a);
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return t * t * (3 - 2 * t);
    }
}
