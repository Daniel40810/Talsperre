package com.dan.ground;

/**
 * Wo und wie viel wächst: Höhe des Bodens und Bedeckung an jedem Ort. Die Bedeckung sagt je Schicht
 * 0..1, wie dicht sie dort ist (1 = so dicht, wie das Biom es vorsieht).
 */
public interface Site {
    /** Bodenhöhe y an (x, z). */
    float height(double x, double z);

    /**
     * Bedeckung an (x, z): out[0] Gras, out[1] Blumen, out[2] Steine, out[3] offene Erde, out[4] Feuchte
     * (0 trocken, 1 nass; Seggen mögen es nass, Blumen eher nicht).
     */
    void cover(double x, double z, float[] out);

    /** Flacher Boden, überall Wiese mit Flecken aus Blumen, Steinen und Erde. */
    Site MEADOW = new Site() {
        @Override public float height(double x, double z) { return 0; }

        @Override public void cover(double x, double z, float[] out) {
            float fx = (float) x, fz = (float) z;
            out[0] = 0.55f + 0.45f * GNoise.value(fx * 0.08f, fz * 0.08f);
            out[1] = Math.max(0, GNoise.value(fx * 0.05f + 9, fz * 0.05f + 3) * 1.6f - 0.4f);
            out[2] = Math.max(0, GNoise.value(fx * 0.07f + 4, fz * 0.07f + 8) * 1.8f - 0.9f);
            out[3] = Math.max(0, GNoise.value(fx * 0.04f + 2, fz * 0.04f + 5) * 2.2f - 1.5f);
            out[4] = GNoise.value(fx * 0.02f + 7, fz * 0.02f + 1);
        }
    };
}
