package com.dan.forest;

/**
 * Wind über dem Wald: Grundgeschwindigkeit und Richtung, dazu Böen, die als Wellen mit dem Wind über
 * die Fläche laufen (benachbarte Bäume biegen sich nacheinander, nicht alle zugleich), und Unruhe,
 * die die Blätter flattern lässt. Ohne Abhängigkeiten, in Metern und Sekunden.
 */
public final class WindField {
    /** Mittlere Geschwindigkeit (m/s), Richtung (Grad, 0 = +x, 90 = +z), Böigkeit 0..1, Unruhe 0..1. */
    public volatile float speed = 4, direction = 30, gustiness = 0.5f, turbulence = 0.4f;

    /** Wind an einer Stelle zu einer Zeit. */
    public static final class Sample {
        /** Richtung (waagrecht, normiert), Geschwindigkeit (m/s), Böe 0..2 (1 = mittel), Unruhe 0..1. */
        public float dx = 1, dz, speed, gust = 1, turb;

        /** Derselbe Wind im gedrehten Rahmen eines Baums (um yaw gedreht gestellt). */
        public Sample local(double yaw) {
            Sample s = new Sample();
            float c = (float) Math.cos(-yaw), sn = (float) Math.sin(-yaw);
            s.dx = dx * c - dz * sn; s.dz = dx * sn + dz * c; s.speed = speed; s.gust = gust; s.turb = turb;
            return s;
        }
    }

    public Sample sample(double x, double z, double t) {
        Sample s = new Sample();
        sample(x, z, t, s);
        return s;
    }

    public void sample(double x, double z, double t, Sample out) {
        double a = Math.toRadians(direction);
        float dx = (float) Math.cos(a), dz = (float) Math.sin(a);
        // Böen laufen mit dem Wind: Koordinate entlang (mit der Zeit verschoben) und quer
        float along = (float) ((x * dx + z * dz) - t * Math.max(1, speed)) / 45f, across = (float) (-x * dz + z * dx) / 70f;
        float g = Noise2.fbm(along, across + 13.7f, 3);
        float gust = Math.max(0.15f, 1 + gustiness * (g - 0.5f) * 3.2f);
        // die Richtung pendelt etwas
        float wob = (Noise2.value((float) t * 0.05f, 3.3f) - 0.5f) * 0.5f * gustiness;
        float c = (float) Math.cos(wob), sn = (float) Math.sin(wob);
        out.dx = dx * c - dz * sn; out.dz = dx * sn + dz * c;
        out.speed = speed * gust;
        out.gust = gust;
        out.turb = Math.max(0, Math.min(1, turbulence * (0.5f + 0.5f * Noise2.value(along * 3, across * 3 + (float) t * 0.7f)) + 0.2f * (gust - 1)));
    }
}
