package com.dan.river;

/**
 * Glattes Wertrauschen in 2D mit Ableitungen (für Wellennormalen ohne Nachbarabfragen). Ohne
 * Abhängigkeiten; Werte 0..1.
 */
public final class RNoise {
    private RNoise() { }

    /** Zufallswerte auf einem Gitter von 256 × 256 (wiederholt sich alle 256 Einheiten). */
    private static final float[] T = new float[256 * 256];
    static {
        for (int y = 0; y < 256; y++)
            for (int x = 0; x < 256; x++) {
                int h = x * 374761393 + y * 668265263;
                h = (h ^ (h >>> 13)) * 1274126177;
                T[y * 256 + x] = ((h ^ (h >>> 16)) & 0xffffff) / (float) 0xffffff;
            }
    }

    private static float hash(int x, int y) { return T[((y & 255) << 8) | (x & 255)]; }

    private static int fl(float v) { int i = (int) v; return v < i ? i - 1 : i; }

    /** Rauschen an (x, y). */
    public static float value(float x, float y) {
        int ix = fl(x), iy = fl(y);
        float fx = x - ix, fy = y - iy;
        float u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy);
        float a = hash(ix, iy), b = hash(ix + 1, iy), c = hash(ix, iy + 1), d = hash(ix + 1, iy + 1);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    /** Rauschen an (x, y); out bekommt die Ableitungen d/dx und d/dy. Liefert den Wert. */
    public static float grad(float x, float y, float[] out) {
        int ix = fl(x), iy = fl(y);
        float fx = x - ix, fy = y - iy;
        float u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy);
        float du = 6 * fx * (1 - fx), dv = 6 * fy * (1 - fy);
        float a = hash(ix, iy), b = hash(ix + 1, iy), c = hash(ix, iy + 1), d = hash(ix + 1, iy + 1);
        float k = a - b - c + d;
        out[0] = du * ((b - a) + k * v);
        out[1] = dv * ((c - a) + k * u);
        return a + (b - a) * u + (c - a) * v + k * u * v;
    }

    /** Mehrere Oktaven, 0..1. */
    public static float fbm(float x, float y, int oct) {
        float s = 0, a = 0.5f, w = 0;
        for (int i = 0; i < oct; i++) { s += a * value(x, y); w += a; x = x * 2.03f + 17.1f; y = y * 2.03f + 5.3f; a *= 0.5f; }
        return s / w;
    }
}
