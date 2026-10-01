package com.dan.ground;

/**
 * Wind über der Wiese: Grundwind mit Böen, die als Wellen über die Fläche laufen (das Gras legt sich
 * in Bahnen um, die man wandern sieht), dazu Unruhe für das Zittern der Halme.
 */
public final class Breeze {
    /** Geschwindigkeit (m/s), Richtung (Grad, 0 = +x, 90 = +z), Böigkeit 0..1. */
    public volatile float speed = 4, direction = 30, gustiness = 0.6f;

    /**
     * Umbiegen an (x, z) zur Zeit t: out[0], out[1] = Richtung mal Stärke (0 windstill, 1 ≈ flach
     * gedrückt bei Sturm), out[2] = Unruhe 0..1.
     */
    public void bend(float x, float z, float t, float[] out) {
        double a = Math.toRadians(direction);
        float dx = (float) Math.cos(a), dz = (float) Math.sin(a);
        float sp = speed;
        float along = x * dx + z * dz, across = -x * dz + z * dx;
        // Böen: breite Bänder, die mit dem Wind wandern, darin feinere Wellen
        float w1 = GNoise.value((along - t * sp * 0.9f) * 0.045f, across * 0.02f);
        float w2 = GNoise.value((along - t * sp * 1.1f) * 0.16f + 5.3f, across * 0.09f + 1.7f);
        float gust = 0.55f + gustiness * (w1 - 0.5f) * 1.8f + gustiness * (w2 - 0.5f) * 0.7f;
        gust = Math.max(0.05f, gust);
        float strength = Math.min(1, sp / 16f) * gust;
        out[0] = dx * strength; out[1] = dz * strength;
        out[2] = Math.min(1, 0.15f + 0.6f * gust * Math.min(1, sp / 8f));
    }
}
