package com.dan.talsperre.core;

/**
 * Tiere für den Bildrechner: Bison und Wapiti als Schattenrisse in einer senkrechten Ebene entlang
 * der Laufrichtung, die sich so weit zur Kamera dreht, dass sie nie ganz zur Linie wird. Die Beine
 * schwingen im Schritt. Wer sie bewegt (Fauna), füllt die Felder vor
 * jedem Bild; der Bildrechner liest sie nur.
 * <p>
 * Die Umrisse sind in Metern gezeichnet (x nach vorn, y nach oben, Boden bei 0).
 */
public final class Animals {
    public static final byte BISON = 0, ELK_COW = 1, ELK_BULL = 2, BISON_CALF = 3, PERSON = 4,
            /** Fahrzeuge und Schiffe: Auto, Bus, Fahrgastschiff, Segelboot (Schattenriss von der Seite, x nach vorn). */
            CAR = 5, BUS = 6, SHIP = 7, SAIL = 8,
            /** Lancaster-Bomber (Schattenriss von der Seite) und Kübel des Kabelkrans mit Seil. */
            BOMBER = 9, BUCKET = 10;
    static final int CAP = 320;

    public int n;
    public final float[] x = new float[CAP], y = new float[CAP], z = new float[CAP], hx = new float[CAP], hz = new float[CAP];
    /** Schrittphase (Bogenmaß), Gangstärke 0 (steht, grast) bis 1 (geht), Kopf gesenkt 0..1, Größe. */
    public final float[] step = new float[CAP], gait = new float[CAP], graze = new float[CAP], scale = new float[CAP];
    public final byte[] kind = new byte[CAP];
    /** Besucher: Farbe der Jacke (linear) und der Hose. */
    public final float[] cr = new float[CAP], cg = new float[CAP], cb = new float[CAP], pr = new float[CAP], pg = new float[CAP], pb = new float[CAP];

    /** Körper: Umriss als Punktfolge (x, y) und Farbe (linear, Albedo). */
    static final float[] BISON_BODY = {
            -1.45f, 1.05f, -1.40f, 1.38f, -1.10f, 1.52f, -0.50f, 1.60f, 0.05f, 1.78f, 0.35f, 1.86f, 0.62f, 1.80f,
            0.95f, 1.55f, 1.20f, 1.28f, 1.38f, 1.02f, 1.47f, 0.70f, 1.42f, 0.52f, 1.22f, 0.46f, 1.05f, 0.30f,
            0.82f, 0.40f, 0.62f, 0.55f, 0.20f, 0.66f, -0.55f, 0.72f, -1.05f, 0.80f, -1.35f, 0.88f};
    /** Mähne und Kopf dunkler, über den Körper gelegt. */
    static final float[] BISON_MANE = {0.05f, 1.78f, 0.35f, 1.86f, 0.62f, 1.80f, 0.95f, 1.55f, 1.20f, 1.28f, 1.38f, 1.02f,
            1.47f, 0.70f, 1.42f, 0.52f, 1.22f, 0.46f, 1.05f, 0.30f, 0.82f, 0.40f, 0.62f, 0.55f, 0.25f, 0.70f, -0.05f, 1.10f};
    static final float[] ELK_BODY = {
            -1.08f, 1.30f, -1.00f, 1.43f, -0.55f, 1.47f, 0.00f, 1.45f, 0.40f, 1.52f, 0.62f, 1.50f, 0.80f, 1.30f,
            0.74f, 1.08f, 0.62f, 0.98f, 0.20f, 0.95f, -0.30f, 0.97f, -0.70f, 1.02f, -1.02f, 1.08f};
    /** Hals und Kopf mit Ohr, um den Widerrist drehbar (Grasen). */
    static final float[] ELK_NECK = {0.35f, 1.50f, 0.62f, 1.72f, 0.92f, 2.00f, 0.96f, 2.28f, 1.02f, 2.14f, 1.20f, 2.10f,
            1.55f, 1.88f, 1.50f, 1.78f, 1.22f, 1.82f, 0.98f, 1.64f, 0.80f, 1.36f, 0.62f, 1.16f};
    static final float[] ELK_RUMP = {-1.08f, 1.10f, -1.07f, 1.40f, -0.82f, 1.45f, -0.72f, 1.22f, -0.86f, 1.04f};
    /** Geweih des Bullen: Stange und Enden als Linien (x0, y0, x1, y1), vom Kopf aus. */
    static final float[] ANTLER = {1.12f, 2.15f, 0.70f, 2.85f, 0.70f, 2.85f, 0.25f, 3.20f, 1.05f, 2.30f, 1.40f, 2.55f,
            0.92f, 2.52f, 1.22f, 2.85f, 0.80f, 2.70f, 1.00f, 3.05f, 0.55f, 2.98f, 0.62f, 3.30f};

    /** Mensch, von der Seite: Rumpf mit Jacke, Kopf; Beine eigens (Hüfte bei 0,9 m). */
    static final float[] PERSON_BODY = {-0.14f, 0.88f, -0.16f, 1.20f, -0.13f, 1.42f, -0.05f, 1.48f, 0.08f, 1.47f, 0.14f, 1.38f, 0.15f, 1.15f, 0.12f, 0.88f};
    static final float[] PERSON_HEAD = {-0.07f, 1.50f, -0.09f, 1.60f, -0.06f, 1.70f, 0.02f, 1.73f, 0.09f, 1.68f, 0.10f, 1.58f, 0.06f, 1.50f};

    /** Beine: Hüft-x, Hüfthöhe, Länge bis zum Huf, Phasenversatz. */
    static final float[][] BISON_LEGS = {{0.75f, 0.70f, 0.70f, 0}, {0.55f, 0.70f, 0.70f, 3.14f}, {-0.95f, 0.85f, 0.85f, 1.57f}, {-1.15f, 0.85f, 0.85f, 4.71f}};
    static final float[][] ELK_LEGS = {{0.70f, 1.05f, 1.05f, 0}, {0.52f, 1.05f, 1.05f, 3.14f}, {-0.75f, 1.05f, 1.05f, 1.57f}, {-0.92f, 1.05f, 1.05f, 4.71f}};

    /** Wagen: Karosserie, Fenster, Räder als Achtecke (Mitte x, Radius). */
    static final float[] CAR_BODY = {-2.1f, 0.30f, -2.12f, 0.80f, -1.5f, 0.95f, -0.85f, 1.0f, -0.45f, 1.40f, 0.55f, 1.42f, 1.05f, 1.0f, 1.95f, 0.88f, 2.1f, 0.55f, 2.05f, 0.30f};
    static final float[] CAR_WIN = {-0.35f, 1.04f, -0.15f, 1.34f, 0.45f, 1.34f, 0.85f, 1.04f};
    static final float[] BUS_BODY = {-6f, 0.40f, -6f, 3.1f, 5.4f, 3.1f, 6f, 2.7f, 6f, 0.40f};
    static final float[] BUS_WIN = {-5.4f, 1.5f, -5.4f, 2.6f, 5.0f, 2.6f, 5.4f, 2.2f, 5.4f, 1.5f};
    static final float[] SHIP_HULL = {-14f, 0f, -14.5f, 1.8f, -12f, 2.5f, 12f, 2.5f, 15.5f, 2.3f, 13f, 0f};
    static final float[] SHIP_DECK = {-11f, 2.5f, -11f, 4.8f, 9f, 4.8f, 9f, 2.5f};
    static final float[] SHIP_WIN = {-10.4f, 3.2f, -10.4f, 4.2f, 8.4f, 4.2f, 8.4f, 3.2f};
    static final float[] SHIP_UP = {-8f, 4.8f, -8f, 6.6f, 5f, 6.6f, 5f, 4.8f};
    static final float[] SHIP_UPWIN = {-7.4f, 5.3f, -7.4f, 6.2f, 4.4f, 6.2f, 4.4f, 5.3f};
    static final float[] SHIP_FUNNEL = {-1.5f, 6.6f, -1.5f, 8.6f, 0.8f, 8.6f, 0.8f, 6.6f};
    static final float[] SAIL_HULL = {-2.6f, 0f, -2.8f, 0.55f, 2.6f, 0.65f, 3.0f, 0.45f, 2.2f, 0f};
    static final float[] SAIL_MAIN = {-0.2f, 0.7f, -0.2f, 8.2f, -2.4f, 0.9f};
    static final float[] SAIL_JIB = {0.2f, 0.7f, 0.2f, 7.2f, 2.8f, 0.7f};

    static final float[] BOMBER_BODY = {-10.5f, 1.2f, -10.5f, 2.7f, -6.5f, 2.4f, 3.5f, 2.8f, 7.5f, 2.5f, 10.8f, 1.5f, 9.5f, 0.4f, 3.0f, 0f, -5f, 0.5f};
    static final float[] BOMBER_FIN = {-10.5f, 2.5f, -9.9f, 5.6f, -8.0f, 5.6f, -7.2f, 2.4f};
    static final float[] BOMBER_WING = {-1.8f, 1.0f, -1.2f, 1.6f, 2.4f, 1.6f, 2.8f, 1.0f};
    static final float[] BOMBER_ENG = {-0.6f, 0.2f, -0.6f, 1.2f, 2.6f, 1.2f, 2.6f, 0.2f};
    static final float[] BUCKET_BODY = {-0.9f, 0f, 0.9f, 0f, 1.2f, 1.4f, -1.2f, 1.4f};
    static final float[] BUCKET_ROPE = {-0.04f, 1.4f, 0.04f, 1.4f, 0.04f, 70f, -0.04f, 70f};

    /** Rad als Achteck um (cx, r) mit Radius r. */
    static float[] wheel(float cx, float r) {
        float[] w = new float[16];
        for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; w[2 * i] = cx + (float) Math.cos(a) * r; w[2 * i + 1] = r + (float) Math.sin(a) * r; }
        return w;
    }
    static final float[][] CAR_WHEELS = {wheel(-1.3f, 0.34f), wheel(1.3f, 0.34f)};
    static final float[][] BUS_WHEELS = {wheel(-3.8f, 0.5f), wheel(3.8f, 0.5f)};

    public void clear() { n = 0; }

    public int add(byte k, double px, double py, double pz, double hdx, double hdz, double sc) {
        if (n == x.length) return -1;
        int i = n++;
        kind[i] = k; x[i] = (float) px; y[i] = (float) py; z[i] = (float) pz;
        double l = Math.hypot(hdx, hdz);
        hx[i] = (float) (hdx / l); hz[i] = (float) (hdz / l);
        scale[i] = (float) sc; step[i] = 0; gait[i] = 0; graze[i] = 0;
        return i;
    }
}
