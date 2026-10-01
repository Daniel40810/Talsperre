package com.dan.road;

/**
 * Arten von Verkehrsteilnehmern mit Maßen (m), Fahrverhalten und Form. Die Form ist ein kleines
 * Dreiecksnetz im eigenen Raum (x nach vorn, y nach oben, z nach rechts, Ursprung am Boden in der
 * Mitte) mit Teilen, die je Bild ihre Farbe bekommen: Lack, Glas, Leuchten, Blinker, Räder.
 */
public enum Vehicle {
    CAR(4.5f, 1.8f, 1.45f, 1.6f, 3, 1.0f),
    SUV(4.9f, 1.95f, 1.8f, 1.5f, 3, 1.0f),
    PICKUP(5.6f, 2.0f, 1.9f, 1.4f, 3, 0.95f),
    CAMPER(8.5f, 2.5f, 3.3f, 0.8f, 2.5f, 0.85f),
    BUS(12f, 2.55f, 3.2f, 0.8f, 2.5f, 0.85f),
    TRUCK(16.5f, 2.55f, 4.0f, 0.6f, 2.5f, 0.8f),
    MOTORBIKE(2.2f, 0.8f, 1.4f, 2.5f, 4, 1.05f),
    BICYCLE(1.8f, 0.6f, 1.75f, 0.8f, 2, 1),
    TRACTOR(4.2f, 2.3f, 2.8f, 0.8f, 2.5f, 1),
    HIKER(0.6f, 0.5f, 1.75f, 0.6f, 1.5f, 1);

    public final float length, width, height;
    /** Anfahren und angenehmes Bremsen (m/s²), Faktor auf die Wunschgeschwindigkeit. */
    public final float accel, decel, eager;

    Vehicle(float l, float w, float h, float a, float b, float e) {
        length = l; width = w; height = h; accel = a; decel = b; eager = e;
    }

    public boolean walks() { return this == HIKER; }
    public boolean pedals() { return this == BICYCLE; }
    /** Höchstgeschwindigkeit der Art (m/s). */
    public float top() {
        return switch (this) {
            case HIKER -> 1.35f;
            case BICYCLE -> 5.5f;
            case TRACTOR -> 8;
            case TRUCK -> 24;
            case BUS, CAMPER -> 27;
            default -> 45;
        };
    }

    // ------------------------------------------------------------ Form

    /** Teile der Form. */
    static final byte BODY = 0, GLASS = 1, TRIM = 2, HEAD = 3, TAIL = 4, IND_L = 5, IND_R = 6, STRIPE = 7, SKIN = 8, CLOTH = 9, PACK = 10, LEG_L = 11, LEG_R = 12, ARM_L = 13, ARM_R = 14;

    /** Netz einer Art: je Dreieck drei Ecken (xyz), eine Normale und ein Teil; dazu die Räder (x, Radius, z, Breite). */
    static final class Shape {
        float[] p = new float[0], n = new float[0];
        byte[] part = new byte[0];
        int nt;
        float[] wheels = new float[0];
        /** Leuchten für Lichthöfe: x, y, z, Teil. */
        float[] lamps = new float[0];

        void tri(float[] a, float[] b, float[] c, float nx, float ny, float nz, byte pt) {
            if (9 * (nt + 1) > p.length) {
                int cap = Math.max(64, nt * 2);
                p = java.util.Arrays.copyOf(p, 9 * cap); n = java.util.Arrays.copyOf(n, 3 * cap); part = java.util.Arrays.copyOf(part, cap);
            }
            System.arraycopy(a, 0, p, 9 * nt, 3); System.arraycopy(b, 0, p, 9 * nt + 3, 3); System.arraycopy(c, 0, p, 9 * nt + 6, 3);
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9f;
            n[3 * nt] = nx / l; n[3 * nt + 1] = ny / l; n[3 * nt + 2] = nz / l;
            part[nt] = pt;
            nt++;
        }

        void quad(float[] a, float[] b, float[] c, float[] d, byte pt) {
            float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = d[0] - a[0], vy = d[1] - a[1], vz = d[2] - a[2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            tri(a, b, c, nx, ny, nz, pt);
            tri(a, c, d, nx, ny, nz, pt);
        }

        /**
         * Seitenriss (x, y) als konvexes Vieleck, quer von −hw bis +hw ausgezogen; edge[k] ist das Teil
         * der Kante k → k+1, cap das Teil der beiden Seitenflächen.
         */
        void prism(float[] xy, float hw, byte[] edge, byte cap) {
            int m = xy.length / 2;
            for (int k = 0; k < m; k++) {
                int j = (k + 1) % m;
                float[] a = {xy[2 * k], xy[2 * k + 1], -hw}, b = {xy[2 * j], xy[2 * j + 1], -hw}, c = {xy[2 * j], xy[2 * j + 1], hw}, d = {xy[2 * k], xy[2 * k + 1], hw};
                float ex = b[0] - a[0], ey = b[1] - a[1];
                // Umlauf gegen den Uhrzeigersinn: außen ist (ey, −ex)
                tri(a, b, c, ey, -ex, 0, edge[k]);
                tri(a, c, d, ey, -ex, 0, edge[k]);
            }
            for (int k = 1; k + 1 < m; k++) {
                tri(new float[]{xy[0], xy[1], hw}, new float[]{xy[2 * k], xy[2 * k + 1], hw}, new float[]{xy[2 * k + 2], xy[2 * k + 3], hw}, 0, 0, 1, cap);
                tri(new float[]{xy[0], xy[1], -hw}, new float[]{xy[2 * k], xy[2 * k + 1], -hw}, new float[]{xy[2 * k + 2], xy[2 * k + 3], -hw}, 0, 0, -1, cap);
            }
        }

        void box(float x0, float y0, float z0, float x1, float y1, float z1, byte pt) {
            float[][] c = new float[8][];
            for (int k = 0; k < 8; k++) c[k] = new float[]{(k & 1) == 0 ? x0 : x1, (k & 2) == 0 ? y0 : y1, (k & 4) == 0 ? z0 : z1};
            quad(c[1], c[3], c[7], c[5], pt);   // vorn
            quad(c[0], c[4], c[6], c[2], pt);   // hinten
            quad(c[2], c[6], c[7], c[3], pt);   // oben
            quad(c[0], c[1], c[5], c[4], pt);   // unten
            quad(c[4], c[5], c[7], c[6], pt);   // rechts
            quad(c[0], c[2], c[3], c[1], pt);   // links
        }

        /** Leuchte als flache Tafel quer zur Achse bei x (vorn: dir = 1, hinten: −1). */
        void lamp(float x, float y0, float y1, float z0, float z1, int dir, byte pt) {
            float xx = x + dir * 0.01f;
            float[] a = {xx, y0, z0}, b = {xx, y0, z1}, c = {xx, y1, z1}, d = {xx, y1, z0};
            tri(a, b, c, dir, 0, 0, pt);
            tri(a, c, d, dir, 0, 0, pt);
            lamps = java.util.Arrays.copyOf(lamps, lamps.length + 4);
            int o = lamps.length - 4;
            lamps[o] = xx; lamps[o + 1] = (y0 + y1) / 2; lamps[o + 2] = (z0 + z1) / 2; lamps[o + 3] = pt;
        }

        void wheel(float x, float r, float z, float w) {
            wheels = java.util.Arrays.copyOf(wheels, wheels.length + 4);
            int o = wheels.length - 4;
            wheels[o] = x; wheels[o + 1] = r; wheels[o + 2] = z; wheels[o + 3] = w;
        }
    }

    private Shape shape;

    synchronized Shape shape() {
        if (shape == null) shape = build(this);
        return shape;
    }

    private static byte[] parts(byte... b) { return b; }

    private static Shape build(Vehicle v) {
        Shape s = new Shape();
        float L = v.length / 2, hw = v.width / 2;
        byte B = BODY, G = GLASS, T = TRIM;
        switch (v) {
            case CAR -> {
                s.prism(new float[]{-L, 0.3f, L - 0.1f, 0.3f, L, 0.55f, L - 0.12f, 0.8f, 0.85f, 0.9f, -L + 0.6f, 0.95f, -L, 0.88f, -L - 0.05f, 0.55f}, hw,
                        parts(T, T, B, B, B, B, B, T), B);
                s.prism(new float[]{-L + 0.35f, 0.92f, 0.85f, 0.9f, 0.1f, 1.42f, -L + 0.95f, 1.44f}, hw * 0.88f, parts(B, G, B, G), G);
                lights(s, v, 0.55f, 0.72f, 0.62f, 0.8f);
                s.wheel(L - 1.1f, 0.32f, hw - 0.12f, 0.22f); s.wheel(-L + 1.1f, 0.32f, hw - 0.12f, 0.22f);
            }
            case SUV -> {
                s.prism(new float[]{-L, 0.4f, L - 0.1f, 0.4f, L, 0.7f, L - 0.15f, 1.0f, 1.0f, 1.08f, -L + 0.1f, 1.1f, -L - 0.05f, 0.7f}, hw,
                        parts(T, T, B, B, B, B, T), B);
                s.prism(new float[]{-L + 0.1f, 1.08f, 1.0f, 1.06f, 0.35f, 1.72f, -L + 0.2f, 1.78f}, hw * 0.9f, parts(B, G, B, G), G);
                lights(s, v, 0.72f, 0.9f, 0.8f, 1.0f);
                s.wheel(L - 1.2f, 0.38f, hw - 0.14f, 0.26f); s.wheel(-L + 1.2f, 0.38f, hw - 0.14f, 0.26f);
            }
            case PICKUP -> {
                s.prism(new float[]{-L, 0.45f, L - 0.1f, 0.45f, L, 0.75f, L - 0.15f, 1.05f, 1.2f, 1.12f, -L, 1.12f}, hw, parts(T, T, B, B, B, B), B);
                s.prism(new float[]{-0.5f, 1.1f, 1.2f, 1.1f, 0.55f, 1.85f, -0.45f, 1.88f}, hw * 0.92f, parts(B, G, B, G), G);
                s.box(-L + 0.05f, 1.0f, -hw + 0.08f, -0.55f, 1.12f, hw - 0.08f, TRIM);     // Ladefläche innen dunkel
                lights(s, v, 0.78f, 0.95f, 0.85f, 1.05f);
                s.wheel(L - 1.2f, 0.4f, hw - 0.15f, 0.28f); s.wheel(-L + 1.3f, 0.4f, hw - 0.15f, 0.28f);
            }
            case CAMPER -> {
                s.prism(new float[]{-L, 0.5f, L - 0.3f, 0.5f, L, 0.8f, L - 0.05f, 1.35f, L - 0.8f, 2.0f, -L, 2.0f}, hw, parts(T, T, B, G, B, B), B);
                s.prism(new float[]{-L, 1.95f, L - 0.8f, 1.95f, L - 0.3f, 2.9f, L - 1.2f, 3.3f, -L, 3.3f}, hw, parts(B, B, B, B, B), B);
                s.box(-L + 0.6f, 1.55f, -hw - 0.01f, L - 3.0f, 1.62f, hw + 0.01f, STRIPE);
                s.box(-L + 1.5f, 2.2f, -hw - 0.012f, -L + 3.0f, 2.8f, hw + 0.012f, GLASS);
                lights(s, v, 0.85f, 1.05f, 0.9f, 1.2f);
                s.wheel(L - 1.3f, 0.45f, hw - 0.2f, 0.3f); s.wheel(-L + 2.0f, 0.45f, hw - 0.2f, 0.3f);
            }
            case BUS -> {
                s.prism(new float[]{-L, 0.4f, L, 0.4f, L + 0.02f, 3.0f, L - 0.15f, 3.2f, -L + 0.1f, 3.2f, -L, 3.0f}, hw, parts(T, B, B, B, B, B), B);
                s.box(-L + 0.8f, 1.6f, -hw - 0.012f, L - 1.4f, 2.6f, hw + 0.012f, GLASS);
                s.box(L - 0.02f, 1.3f, -hw + 0.1f, L + 0.03f, 2.8f, hw - 0.1f, GLASS);
                s.box(-L + 0.3f, 1.15f, -hw - 0.014f, L - 0.3f, 1.3f, hw + 0.014f, STRIPE);
                lights(s, v, 0.6f, 0.8f, 0.8f, 1.0f);
                s.wheel(L - 2.6f, 0.5f, hw - 0.25f, 0.3f); s.wheel(-L + 3.0f, 0.5f, hw - 0.25f, 0.3f);
            }
            case TRUCK -> {
                float cab = L - 2.3f;
                s.prism(new float[]{cab, 0.6f, L, 0.6f, L + 0.02f, 2.2f, L - 0.2f, 3.0f, cab, 3.0f}, hw, parts(T, B, G, B, B), B);
                s.box(-L, 1.2f, -hw, cab - 0.5f, 4.0f, hw, STRIPE);
                s.box(-L, 0.9f, -hw + 0.3f, cab, 1.2f, hw - 0.3f, TRIM);
                lights(s, v, 0.6f, 0.85f, 0.9f, 1.1f);
                s.wheel(L - 1.2f, 0.52f, hw - 0.25f, 0.32f); s.wheel(cab - 1.2f, 0.52f, hw - 0.25f, 0.32f);
                s.wheel(-L + 1.5f, 0.52f, hw - 0.25f, 0.32f); s.wheel(-L + 2.8f, 0.52f, hw - 0.25f, 0.32f);
            }
            case MOTORBIKE -> {
                s.prism(new float[]{-0.7f, 0.45f, 0.7f, 0.45f, 0.75f, 0.85f, 0.2f, 0.95f, -0.6f, 0.85f}, 0.18f, parts(T, B, B, B, T), B);
                s.box(-0.35f, 0.9f, -0.2f, 0.1f, 1.45f, 0.2f, CLOTH);
                s.box(-0.1f, 1.45f, -0.13f, 0.15f, 1.72f, 0.13f, BODY);
                s.lamp(0.78f, 0.75f, 0.85f, -0.07f, 0.07f, 1, HEAD);
                s.lamp(-0.72f, 0.75f, 0.83f, -0.06f, 0.06f, -1, TAIL);
                s.wheel(0.75f, 0.32f, 0, 0.12f); s.wheel(-0.75f, 0.32f, 0, 0.14f);
            }
            case BICYCLE -> {
                s.box(-0.55f, 0.55f, -0.02f, 0.55f, 0.6f, 0.02f, TRIM);
                s.box(-0.1f, 0.6f, -0.02f, -0.05f, 0.95f, 0.02f, TRIM);
                s.box(-0.35f, 0.95f, -0.16f, 0.05f, 1.45f, 0.16f, CLOTH);
                s.box(-0.1f, 1.45f, -0.1f, 0.12f, 1.7f, 0.1f, SKIN);
                s.box(0.0f, 1.1f, -0.2f, 0.45f, 1.18f, -0.14f, ARM_L); s.box(0.0f, 1.1f, 0.14f, 0.45f, 1.18f, 0.2f, ARM_R);
                s.box(-0.12f, 0.35f, -0.12f, -0.02f, 0.95f, -0.05f, LEG_L); s.box(-0.12f, 0.35f, 0.05f, -0.02f, 0.95f, 0.12f, LEG_R);
                s.wheel(0.55f, 0.34f, 0, 0.04f); s.wheel(-0.55f, 0.34f, 0, 0.04f);
            }
            case TRACTOR -> {
                s.box(-0.3f, 0.7f, -0.45f, 2.0f, 1.6f, 0.45f, BODY);
                s.box(-1.3f, 0.8f, -0.6f, -0.2f, 2.7f, 0.6f, GLASS);
                s.box(-1.35f, 2.7f, -0.7f, -0.15f, 2.8f, 0.7f, BODY);
                s.lamp(2.02f, 1.3f, 1.45f, -0.35f, 0.35f, 1, HEAD);
                s.lamp(-1.37f, 1.0f, 1.1f, -0.6f, 0.6f, -1, TAIL);
                s.wheel(1.5f, 0.5f, 0.85f, 0.3f); s.wheel(-0.8f, 0.8f, 0.95f, 0.5f);
            }
            case HIKER -> {
                s.box(-0.08f, 0.85f, -0.17f, 0.08f, 1.45f, 0.17f, CLOTH);
                s.box(-0.24f, 0.95f, -0.15f, -0.08f, 1.5f, 0.15f, PACK);
                s.box(-0.09f, 1.47f, -0.09f, 0.09f, 1.7f, 0.09f, SKIN);
                s.box(-0.1f, 1.68f, -0.13f, 0.1f, 1.76f, 0.13f, PACK);
                s.box(-0.05f, 0.0f, -0.13f, 0.05f, 0.87f, -0.03f, LEG_L);
                s.box(-0.05f, 0.0f, 0.03f, 0.05f, 0.87f, 0.13f, LEG_R);
                s.box(-0.04f, 0.8f, -0.24f, 0.04f, 1.42f, -0.17f, ARM_L);
                s.box(-0.04f, 0.8f, 0.17f, 0.04f, 1.42f, 0.24f, ARM_R);
            }
        }
        return s;
    }

    /** Scheinwerfer, Rückleuchten und Blinker an Bug und Heck. */
    private static void lights(Shape s, Vehicle v, float hy0, float hy1, float ty0, float ty1) {
        float L = v.length / 2, hw = v.width / 2;
        float fx = v == BUS ? L + 0.02f : v == TRUCK ? L + 0.02f : L, rx = v == CAR || v == SUV || v == PICKUP ? -L - 0.05f : -L;
        s.lamp(fx, hy0, hy1, hw - 0.45f, hw - 0.12f, 1, HEAD);
        s.lamp(fx, hy0, hy1, -hw + 0.12f, -hw + 0.45f, 1, HEAD);
        s.lamp(fx, hy0 - 0.12f, hy0 - 0.03f, hw - 0.12f, hw - 0.02f, 1, IND_R);
        s.lamp(fx, hy0 - 0.12f, hy0 - 0.03f, -hw + 0.02f, -hw + 0.12f, 1, IND_L);
        s.lamp(rx, ty0, ty1, hw - 0.35f, hw - 0.05f, -1, TAIL);
        s.lamp(rx, ty0, ty1, -hw + 0.05f, -hw + 0.35f, -1, TAIL);
        s.lamp(rx, ty0 - 0.12f, ty0 - 0.02f, hw - 0.2f, hw - 0.05f, -1, IND_R);
        s.lamp(rx, ty0 - 0.12f, ty0 - 0.02f, -hw + 0.05f, -hw + 0.2f, -1, IND_L);
    }
}
