package com.dan.river;

/**
 * Farbe des Wassers: Was vom Grund durchkommt, hängt vom Weg durchs Wasser ab (Rot wird zuerst
 * geschluckt, darum wirkt tiefes Wasser grün bis blau); Schwebstoffe streuen Licht zurück (trübes
 * Wasser wirkt grünbraun und man sieht den Grund nicht). Oben spiegelt es nach Fresnel. Am Grund
 * bündeln die Wellen das Sonnenlicht zu hellen Netzen (Kaustik).
 * <p>
 * Farben linear (nicht sRGB), je Kanal r, g, b.
 */
public final class WaterOptics {
    /** Absorption je Meter (klares Bergwasser). */
    public float[] absorb = {0.42f, 0.085f, 0.055f};
    /** Streuung je Meter bei Trübe 1 und ihre Farbe (Schwebstoffe). */
    public float[] scatterColor = {0.10f, 0.13f, 0.08f};
    /** Trübe 0 (klar) bis 1 (nach Regen). */
    public volatile float turbidity = 0.15f;

    /** Durchlässigkeit für einen Weg von d Metern durchs Wasser: out = r, g, b. */
    public void transmit(float d, float[] out) {
        float k = 1 + 3 * turbidity;
        for (int c = 0; c < 3; c++) out[c] = (float) Math.exp(-absorb[c] * k * d);
    }

    /**
     * Licht, das aus dem Wasser zurückkommt, ohne den Grund (Farbe der unendlich tiefen Säule mal Licht
     * light), für einen Weg von d Metern: out = r, g, b.
     */
    public void inscatter(float d, float light, float[] out) {
        float sc = 0.04f + 1.2f * turbidity;
        float k = 1 + 3 * turbidity;
        for (int c = 0; c < 3; c++) {
            float a = absorb[c] * k, s = sc;
            float col = scatterColor[c] * s / (a + s);
            out[c] = col * (1 - (float) Math.exp(-(a + s) * d)) * light;
        }
    }

    /** Anteil des gespiegelten Lichts nach Schlick (Wasser: 2 % senkrecht). cosV: Kosinus Blick zur Normale. */
    public static float fresnel(float cosV) {
        float m = 1 - Math.max(0, Math.min(1, cosV));
        float m2 = m * m;
        return 0.02f + 0.98f * m2 * m2 * m;
    }

    /**
     * Kaustik am Grund bei (x, z), Tiefe d, zur Zeit t: Faktor um 1 (0.5 in den Lücken, bis 3 auf den
     * Linien). Mit der Tiefe verschwimmt das Netz.
     */
    public static float caustics(float x, float z, float t, float d, float vx, float vz) {
        if (d <= 0.02f) return 1;
        float blur = Math.min(1, d / 2.5f);
        float s = 1.4f;
        float ax = x * s - vx * t * 0.3f, az = z * s - vz * t * 0.3f;
        float n1 = RNoise.value(ax + t * 0.35f, az - t * 0.2f), n2 = RNoise.value(ax * 1.7f - t * 0.25f + 5.2f, az * 1.7f + t * 0.3f);
        float l1 = 1 - Math.abs(2 * n1 - 1), l2 = 1 - Math.abs(2 * n2 - 1);
        float ll = l1 * l2, net = ll * ll * ll * 6;
        return 0.55f + net * (1 - 0.7f * blur) + 0.45f * blur;
    }
}
