package com.dan.road;

/**
 * Baut die feste Geometrie eines Wegstücks von {@link #LEN} Metern in einer Detailstufe: Fahrbahn,
 * Bankett, Böschungsschürze, Markierung, Leitpfosten, Schneestangen, Schutzplanken, Schilder,
 * Schneewälle und Brücken. Die Farben sind die Grundfarben ohne Wetter; das Wetter kommt je Bild in
 * {@link Roads} dazu. Ein Baumeister gehört einem Thread (er hat Rechenpuffer).
 */
final class Deck {
    static final float LEN = 32;
    /** Schrittweite längs je Stufe (m). */
    static final float[] STEP = {0.5f, 2, 8};

    // Zonen
    static final byte ASPHALT = 0, SHOULDER = 1, GRAVEL = 2, DIRT = 3, GRASS = 4, EARTH = 5, WHITE = 6, YELLOW = 7,
            CONCRETE = 8, METAL = 9, POST = 10, REFLECT = 11, POLE = 12, SIGN = 13, RED = 14, WOOD = 15, BANK = 16, DARK = 17;

    /** Ein fertiges Stück. */
    static final class Piece {
        int nv, nt;
        float[] xyz = new float[3 * 1024], nrm = new float[3 * 1024], rgb = new float[3 * 1024];
        float[] wheel = new float[1024], low = new float[1024], lift = new float[1024], fade = new float[1024];
        byte[] zone = new byte[1024], kind = new byte[1024];
        int[] tri = new int[3 * 1024];
        /** Dreiecksbereiche: Schneestangen und Schneewälle (nur je nach Wetter gezeichnet). */
        int pole0, pole1, bank0, bank1;
        float cx, cz;
        long used;

        int v(float x, float y, float z, float nx, float ny, float nz, byte zn, float r, float g, float b) {
            if (3 * (nv + 1) > xyz.length) {
                int c = xyz.length * 2;
                xyz = java.util.Arrays.copyOf(xyz, c); nrm = java.util.Arrays.copyOf(nrm, c); rgb = java.util.Arrays.copyOf(rgb, c);
                wheel = java.util.Arrays.copyOf(wheel, c / 3); low = java.util.Arrays.copyOf(low, c / 3); lift = java.util.Arrays.copyOf(lift, c / 3);
                fade = java.util.Arrays.copyOf(fade, c / 3); zone = java.util.Arrays.copyOf(zone, c / 3); kind = java.util.Arrays.copyOf(kind, c / 3);
            }
            int i = nv++;
            xyz[3 * i] = x; xyz[3 * i + 1] = y; xyz[3 * i + 2] = z;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9f;
            nrm[3 * i] = nx / l; nrm[3 * i + 1] = ny / l; nrm[3 * i + 2] = nz / l;
            rgb[3 * i] = r; rgb[3 * i + 1] = g; rgb[3 * i + 2] = b;
            zone[i] = zn;
            kind[i] = zn >= POST && zn <= RED || zn == METAL ? Batch.THIN : Batch.SOLID;
            return i;
        }

        void t(int a, int b, int c) {
            if (3 * (nt + 1) > tri.length) tri = java.util.Arrays.copyOf(tri, tri.length * 2);
            tri[3 * nt] = a; tri[3 * nt + 1] = b; tri[3 * nt + 2] = c;
            nt++;
        }

        void quad(int a, int b, int c, int d) { t(a, b, c); t(a, c, d); }
    }

    private final Way w;
    private final WayType ty;
    private final Ground site;
    private final float[] f = new float[6], g2 = new float[6];

    Deck(Way w, Ground site) { this.w = w; this.ty = w.type; this.site = site; }

    // ------------------------------------------------------------ Querschnitt

    /** Ein Streifen des Querschnitts. */
    private record Strip(byte zone, float u0, float u1, int div, float fade0, float fade1) { }

    private java.util.List<Strip> strips(int lod) {
        java.util.List<Strip> l = new java.util.ArrayList<>();
        float e = ty.half(), p = ty.paved();
        switch (ty.kind) {
            case PATH -> {
                l.add(new Strip(GRASS, -e, -p, 1, 1, 0.3f));
                l.add(new Strip(DIRT, -p, p, lod == 0 ? 4 : 2, 0, 0));
                l.add(new Strip(GRASS, p, e, 1, 0.3f, 1));
            }
            case TRACK -> {
                float a = ty.rutSpacing / 2 + ty.rutWidth / 2, b = ty.rutSpacing / 2 - ty.rutWidth / 2;
                l.add(new Strip(GRASS, -e, -p, 1, 1, 0));
                l.add(new Strip(GRAVEL, -p, -a, 1, 0, 0));
                l.add(new Strip(GRAVEL, -a, -b, lod == 0 ? 2 : 1, 0, 0));
                l.add(new Strip(GRASS, -b, b, lod == 0 ? 2 : 1, 0, 0));
                l.add(new Strip(GRAVEL, b, a, lod == 0 ? 2 : 1, 0, 0));
                l.add(new Strip(GRAVEL, a, p, 1, 0, 0));
                l.add(new Strip(GRASS, p, e, 1, 0, 1));
            }
            default -> {
                float lanes = ty.median / 2 + ty.lanes * ty.laneWidth;
                l.add(new Strip(GRAVEL, -e, -p, 1, 0.5f, 0));
                l.add(new Strip(SHOULDER, -p, -lanes, 1, 0, 0));
                for (int k = 0; k < ty.lanes; k++) {
                    float a = -lanes + k * ty.laneWidth;
                    l.add(new Strip(ASPHALT, a, a + ty.laneWidth, lod == 0 ? 4 : lod == 1 ? 2 : 1, 0, 0));
                }
                if (ty.median > 0) {
                    float m = ty.median / 2, in = Math.min(0.75f, m);
                    l.add(new Strip(SHOULDER, -m, -m + in, 1, 0, 0));
                    if (m > in) l.add(new Strip(GRASS, -m + in, m - in, 2, 0, 0));
                    l.add(new Strip(SHOULDER, m - in, m, 1, 0, 0));
                }
                for (int k = ty.lanes - 1; k >= 0; k--) {
                    float a = lanes - (k + 1) * ty.laneWidth;
                    l.add(new Strip(ASPHALT, a, a + ty.laneWidth, lod == 0 ? 4 : lod == 1 ? 2 : 1, 0, 0));
                }
                l.add(new Strip(SHOULDER, lanes, p, 1, 0, 0));
                l.add(new Strip(GRAVEL, p, e, 1, 0, 0.5f));
            }
        }
        return l;
    }

    /** Radspuren 0..1 an der Querlage u (in beiden Richtungen). */
    private float wheel(float u) {
        float au = Math.abs(u);
        switch (ty.kind) {
            case PATH: return 1 - WNoise.smooth(0.1f, 0.35f, au);
            case TRACK: return 1 - WNoise.smooth(ty.rutWidth * 0.3f, ty.rutWidth * 0.6f, Math.abs(au - ty.rutSpacing / 2));
            default:
                float best = 0;
                for (int k = 0; k < ty.lanes; k++) {
                    float c = ty.laneCenter(k);
                    float d = Math.min(Math.abs(au - c - 0.85f), Math.abs(au - c + 0.85f));
                    best = Math.max(best, 1 - WNoise.smooth(0.15f, 0.45f, d));
                }
                return best;
        }
    }

    // ------------------------------------------------------------ Bau eines Stücks

    Piece build(int c, int lod) {
        Piece P = new Piece();
        float s0 = c * LEN, s1 = Math.min(w.closed ? (c + 1) * LEN : w.length, (c + 1) * LEN);
        if (w.closed) s1 = Math.min(s1, w.length);
        float st = STEP[lod];
        int rows = Math.max(1, (int) Math.ceil((s1 - s0) / st));
        java.util.List<Strip> ss = strips(lod);
        w.at((s0 + s1) / 2, 0, f);
        P.cx = f[0]; P.cz = f[2];
        // Fahrbahn: je Streifen ein Gitter
        for (Strip sp : ss) {
            int cols = sp.div + 1;
            int base = P.nv;
            for (int r = 0; r <= rows; r++) {
                float s = s0 + (s1 - s0) * r / rows;
                boolean br = w.bridgeAt(s);
                for (int k = 0; k < cols; k++) {
                    float u = sp.u0 + (sp.u1 - sp.u0) * k / sp.div;
                    float uu = pathWander(s, u);
                    vertexDeck(P, s, uu, sp, k == 0 ? sp.fade0 : k == sp.div ? sp.fade1 : lerp(sp.fade0, sp.fade1, k / (float) sp.div), br, lod);
                }
            }
            for (int r = 0; r < rows; r++)
                for (int k = 0; k < sp.div; k++) {
                    int a = base + r * cols + k, b = a + 1, d = a + cols, e2 = d + 1;
                    P.quad(a, d, e2, b);
                }
        }
        if (ty.kind != WayType.Kind.PATH) skirts(P, s0, s1, rows);
        if (lod < 2) markings(P, s0, s1, st);
        if (lod < 2) props(P, s0, s1, lod);
        bridges(P, s0, s1, lod);
        P.bank0 = P.nt;
        if (lod < 2 && ty.kind != WayType.Kind.PATH && ty.kind != WayType.Kind.TRACK) snowBanks(P, s0, s1, rows);
        P.bank1 = P.nt;
        return P;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** Pfad: Breite und Lage schwanken mit der Bogenlänge. */
    private float pathWander(float s, float u) {
        if (ty.kind != WayType.Kind.PATH) return u;
        float k = 1 + ty.wander * (WNoise.value(s * 0.13f + w.index * 7.7f, 3.3f) - 0.5f) * 2;
        float shift = ty.wander * 0.5f * (WNoise.value(s * 0.05f + 1.7f, w.index * 3.1f) - 0.5f);
        return u * k + shift;
    }

    private void vertexDeck(Piece P, float s, float u, Strip sp, float fade, boolean br, int lod) {
        w.at(s, u, f);
        float x = f[0], y = f[1], z = f[2];
        // Normale aus Nachbarn längs und quer
        w.at(s + 0.5f, u, g2);
        float ax = g2[0] - x, ay = g2[1] - y, az = g2[2] - z;
        w.at(s, u + 0.25f, g2);
        float bx = g2[0] - x, by = g2[1] - y, bz = g2[2] - z;
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        if (ny < 0) { nx = -nx; ny = -ny; nz = -nz; }
        byte zn = sp.zone;
        float au = Math.abs(u), e = ty.half();
        // Bankett fällt am Rand auf den Boden ab
        if (!br && au > ty.paved() && ty.kind != WayType.Kind.PATH) y -= ty.thickness * WNoise.smooth(ty.paved(), e, au);
        if (br) zn = ty.kind == WayType.Kind.PATH || ty.kind == WayType.Kind.TRACK ? WOOD : zn == GRAVEL ? CONCRETE : zn;
        float wh = wheel(u);
        float low = 0;
        float[] c = new float[3];
        color(zn, x, z, s, u, wh, c);
        // Mulden, in denen Pfützen stehen: Spurrinnen, Schlaglöcher am Rand alter Straßen, Senken im Pfad
        switch (zn) {
            case GRAVEL, DIRT -> {
                float n = WNoise.fbm(x * 0.35f + 3.1f, z * 0.35f + 7.2f, 2);
                low = WNoise.smooth(0.5f, 0.75f, n) * (0.4f + 0.6f * wh);
                if (lod == 0) y -= 0.05f * low * wh;
            }
            case ASPHALT, SHOULDER -> {
                float n = WNoise.fbm(x * 0.6f + 9.1f, z * 0.6f + 2.2f, 2);
                float edge = WNoise.smooth(ty.paved() - 1.5f, ty.paved(), au);
                low = WNoise.smooth(0.62f, 0.78f, n) * ty.age * (0.3f + 0.7f * Math.max(edge, wh * 0.6f));
            }
            default -> { }
        }
        if (ty.kind == WayType.Kind.TRACK && zn == GRASS && au < ty.rutSpacing / 2) y += 0.04f;
        int i = P.v(x, y, z, nx, ny, nz, zn, c[0], c[1], c[2]);
        P.wheel[i] = wh; P.low[i] = low; P.fade[i] = fade;
    }

    /** Grundfarbe einer Zone an einer Stelle (linear, ohne Wetter). */
    void color(byte zn, float x, float z, float s, float u, float wh, float[] o) {
        float n1 = WNoise.value(x * 0.9f + 1.3f, z * 0.9f + 4.1f), n2 = WNoise.fbm(x * 0.08f + 5.5f, z * 0.08f + 2.5f, 3);
        float[] a = ty.surface, b = ty.worn;
        switch (zn) {
            case ASPHALT, SHOULDER -> {
                float wear = Math.min(1, ty.age * 0.8f + 0.35f * (n2 - 0.5f) + (zn == SHOULDER ? 0.15f : 0));
                float r = lerp(a[0], b[0], wear), g = lerp(a[1], b[1], wear), bl = lerp(a[2], b[2], wear);
                // Flicken: dunkle, frischere Rechtecke
                float pn = WNoise.value((float) Math.floor(s / 7) * 0.71f + w.index, (float) Math.floor(u / 1.6f) * 0.93f);
                if (pn > 1 - 0.18f * ty.age) { r = lerp(r, a[0], 0.8f); g = lerp(g, a[1], 0.8f); bl = lerp(bl, a[2], 0.8f); }
                // Risse und Bitumenstreifen
                float cr = WNoise.value(x * 1.7f + 2.2f, z * 1.7f + 8.8f);
                float crack = WNoise.smooth(0.47f, 0.5f, cr) * (1 - WNoise.smooth(0.5f, 0.53f, cr)) * ty.age;
                r *= 1 - 0.45f * crack; g *= 1 - 0.45f * crack; bl *= 1 - 0.4f * crack;
                // Ölspur in der Mitte der Fahrstreifen, polierte Radspuren
                r *= 1 + 0.08f * wh; g *= 1 + 0.08f * wh; bl *= 1 + 0.08f * wh;
                float k = 1 + 0.12f * (n1 - 0.5f);
                o[0] = r * k; o[1] = g * k; o[2] = bl * k;
            }
            case GRAVEL -> {
                float k = 0.85f + 0.3f * n1 + 0.2f * (n2 - 0.5f);
                float wear = wh * 0.5f;
                o[0] = lerp(a[0], b[0], wear) * k; o[1] = lerp(a[1], b[1], wear) * k; o[2] = lerp(a[2], b[2], wear) * k;
                if (ty.kind != WayType.Kind.TRACK) { o[0] = ty.vergeColor[0] * k; o[1] = ty.vergeColor[1] * k; o[2] = ty.vergeColor[2] * k; }
            }
            case DIRT -> {
                float k = 0.85f + 0.3f * n1;
                float wear = wh * 0.7f + 0.3f * (n2 - 0.5f);
                o[0] = lerp(a[0], b[0], wear) * k; o[1] = lerp(a[1], b[1], wear) * k; o[2] = lerp(a[2], b[2], wear) * k;
            }
            case GRASS -> {
                float k = 0.8f + 0.4f * n1;
                float[] v = ty.vergeColor;
                o[0] = v[0] * k * (0.9f + 0.3f * n2); o[1] = v[1] * k; o[2] = v[2] * k;
            }
            case EARTH -> {
                float k = 0.8f + 0.4f * n1;
                o[0] = 0.12f * k; o[1] = 0.11f * k; o[2] = 0.06f * k;
            }
            case CONCRETE -> { float k = 0.9f + 0.2f * n1; o[0] = 0.30f * k; o[1] = 0.29f * k; o[2] = 0.27f * k; }
            case WOOD -> { float k = 0.8f + 0.3f * n1; o[0] = 0.20f * k; o[1] = 0.13f * k; o[2] = 0.07f * k; }
            case WHITE -> { float[] m = ty.lineWhite; float k = 1 - 0.5f * ty.age * n2; mix(m, a, 1 - k, o); }
            case YELLOW -> { float[] m = ty.lineYellow; float k = 1 - 0.5f * ty.age * n2; mix(m, a, 1 - k, o); }
            case METAL -> { o[0] = 0.32f; o[1] = 0.33f; o[2] = 0.34f; }
            case POST, SIGN -> { o[0] = 0.62f; o[1] = 0.62f; o[2] = 0.60f; }
            case REFLECT -> { o[0] = 0.35f; o[1] = 0.22f; o[2] = 0.04f; }
            case POLE -> { o[0] = 0.60f; o[1] = 0.16f; o[2] = 0.02f; }
            case RED -> { o[0] = 0.45f; o[1] = 0.03f; o[2] = 0.03f; }
            case BANK -> { o[0] = 0.62f; o[1] = 0.64f; o[2] = 0.68f; }
            default -> { o[0] = 0.03f; o[1] = 0.03f; o[2] = 0.03f; }
        }
    }

    private static void mix(float[] a, float[] b, float t, float[] o) {
        o[0] = lerp(a[0], b[0], t); o[1] = lerp(a[1], b[1], t); o[2] = lerp(a[2], b[2], t);
    }

    // ------------------------------------------------------------ Böschungsschürze

    /** Vom Rand des Banketts bis zum Boden ein paar Meter weiter außen: deckt Stufen im groben Gelände. */
    private void skirts(Piece P, float s0, float s1, int rows) {
        float e = ty.half();
        float[] c = new float[3];
        for (int side = -1; side <= 1; side += 2) {
            int base = P.nv;
            for (int r = 0; r <= rows; r++) {
                float s = s0 + (s1 - s0) * r / rows;
                w.at(s, side * e, f);
                float x = f[0], y = f[1] - ty.thickness, z = f[2];
                float rx = -f[4], rz = f[3];
                float out = 3.5f;
                float ox = x + rx * side * out, oz = z + rz * side * out;
                float gy = site.height(ox, oz);
                if (w.bridgeAt(s)) gy = y - 0.3f;
                color(EARTH, x, z, s, 0, 0, c);
                float dy = gy - y;
                float nx = -rx * side * dy, ny = out, nz = -rz * side * dy;
                int a = P.v(x, y - 0.02f, z, nx, ny, nz, EARTH, c[0], c[1], c[2]);
                P.fade[a] = 0.3f;
                color(EARTH, ox, oz, s, 0, 0, c);
                int b = P.v(ox, gy - 0.05f, oz, nx, ny, nz, EARTH, c[0], c[1], c[2]);
                P.fade[b] = 1;
            }
            for (int r = 0; r < rows; r++) {
                int a = base + 2 * r, b = a + 1, d = a + 2, e2 = a + 3;
                if (side > 0) P.quad(a, d, e2, b); else P.quad(a, b, e2, d);
            }
        }
    }

    // ------------------------------------------------------------ Markierung

    private void markings(Piece P, float s0, float s1, float st) {
        if (ty.marking == WayType.Marking.NONE) return;
        float lanes = ty.median / 2 + ty.lanes * ty.laneWidth;
        boolean us = ty.marking == WayType.Marking.US;
        if (ty.kind == WayType.Kind.MOTORWAY) {
            for (int sd = -1; sd <= 1; sd += 2) {
                line(P, s0, s1, st, sd * (lanes - 0.2f), 0.25f, WHITE, 0, 0);
                line(P, s0, s1, st, sd * (ty.median / 2 + 0.15f), 0.15f, WHITE, 0, 0);
                for (int k = 1; k < ty.lanes; k++) line(P, s0, s1, st, sd * (ty.median / 2 + k * ty.laneWidth), 0.15f, WHITE, 6, 12);
            }
            return;
        }
        for (int sd = -1; sd <= 1; sd += 2) line(P, s0, s1, st, sd * (lanes - 0.12f), 0.12f, WHITE, 0, 0);
        if (us) {
            // Überholverbot in Kurven: doppelt durchgezogen, auf Geraden gestrichelt
            line(P, s0, s1, st, -0.1f, 0.1f, YELLOW, -1, 0);
            line(P, s0, s1, st, 0.1f, 0.1f, YELLOW, -1, 0);
        } else line(P, s0, s1, st, 0, 0.12f, WHITE, 3, 6);
    }

    /**
     * Linie bei Querlage u, Breite wd; Strich/Lücke in Metern (0: durchgezogen; −1: in Kurven
     * durchgezogen, sonst Strich 3 m, Lücke 9 m).
     */
    private void line(Piece P, float s0, float s1, float st, float u, float wd, byte zn, float dash, float gap) {
        float[] c = new float[3];
        if (dash == 0) { strip(P, s0, s1, st, u, wd, zn, c); return; }
        float per = dash < 0 ? 12 : dash + gap, on = dash < 0 ? 3 : dash;
        float k0 = (float) Math.floor(s0 / per) * per;
        for (float a = k0; a < s1; a += per) {
            if (dash < 0 && straight(a)) {
                float lo = Math.max(s0, a), hi = Math.min(s1, a + on);
                if (hi > lo) strip(P, lo, hi, st, u, wd, zn, c);
            } else if (dash < 0) {
                float lo = Math.max(s0, a), hi = Math.min(s1, a + per);
                if (hi > lo) strip(P, lo, hi, st, u, wd, zn, c);
            } else {
                float lo = Math.max(s0, a), hi = Math.min(s1, a + on);
                if (hi > lo) strip(P, lo, hi, st, u, wd, zn, c);
            }
        }
    }

    /** Gerade genug zum Überholen: über ±120 m kaum Krümmung. */
    private boolean straight(float s) {
        for (float d = -120; d <= 120; d += 20) if (Math.abs(w.curv[idx(s + d)]) > 1 / 700f) return false;
        return !w.bridgeAt(s);
    }

    private int idx(float s) {
        int i = (int) Math.round(w.wrap(s) / w.step());
        return w.closed ? Math.floorMod(i, w.n) : Math.min(w.n - 1, i);
    }

    private void strip(Piece P, float s0, float s1, float st, float u, float wd, byte zn, float[] c) {
        int n = Math.max(1, (int) Math.ceil((s1 - s0) / st));
        int base = P.nv;
        for (int r = 0; r <= n; r++) {
            float s = s0 + (s1 - s0) * r / n;
            for (int k = 0; k < 2; k++) {
                float uu = u + (k == 0 ? -wd / 2 : wd / 2);
                w.at(s, uu, f);
                color(zn, f[0], f[2], s, uu, 0, c);
                int i = P.v(f[0], f[1] + 0.012f, f[2], -f[5] * f[3] * 0, 1, 0, zn, c[0], c[1], c[2]);
                P.wheel[i] = wheel(uu);
            }
        }
        for (int r = 0; r < n; r++) { int a = base + 2 * r; P.quad(a, a + 2, a + 3, a + 1); }
    }

    // ------------------------------------------------------------ Ausstattung

    /** Quader von p (Fußpunkt) in Richtung Achse (ax, az), quer (bx, bz), Größen l, b, h. */
    private void box(Piece P, float px, float py, float pz, float ax, float az, float l, float b, float h, byte zn) {
        float bx = -az, bz = ax;
        float[] c = new float[3];
        color(zn, px, pz, 0, 0, 0, c);
        float[][] C = new float[8][];
        for (int k = 0; k < 8; k++) {
            float sl = (k & 1) == 0 ? -l / 2 : l / 2, sb = (k & 2) == 0 ? -b / 2 : b / 2, sh = (k & 4) == 0 ? 0 : h;
            C[k] = new float[]{px + ax * sl + bx * sb, py + sh, pz + az * sl + bz * sb};
        }
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {4, 6, 7, 5}};
        float[][] nn = {{-ax, 0, -az}, {ax, 0, az}, {-bx, 0, -bz}, {bx, 0, bz}, {0, 1, 0}};
        byte keep = zn;
        for (int q = 0; q < faces.length; q++) {
            int[] fc = faces[q];
            int i0 = -1;
            int[] id = new int[4];
            for (int k = 0; k < 4; k++) {
                float[] p = C[fc[k]];
                id[k] = P.v(p[0], p[1], p[2], nn[q][0], nn[q][1], nn[q][2], keep, c[0], c[1], c[2]);
                P.kind[id[k]] = Batch.SOLID;
            }
            P.quad(id[0], id[1], id[2], id[3]);
        }
    }

    /** Senkrechte Tafel (beidseitig) zwischen zwei Fußpunkten, von Höhe y0 bis y1 darüber. */
    private void panel(Piece P, float[] a, float[] b, float y0, float y1, byte zn, float gloss) {
        float[] c = new float[3];
        color(zn, a[0], a[2], 0, 0, 0, c);
        float dx = b[0] - a[0], dz = b[2] - a[2];
        float nx = -dz, nz = dx;
        int i0 = P.v(a[0], a[1] + y0, a[2], nx, 0.2f, nz, zn, c[0], c[1], c[2]);
        int i1 = P.v(b[0], b[1] + y0, b[2], nx, 0.2f, nz, zn, c[0], c[1], c[2]);
        int i2 = P.v(b[0], b[1] + y1, b[2], nx, 0.2f, nz, zn, c[0], c[1], c[2]);
        int i3 = P.v(a[0], a[1] + y1, a[2], nx, 0.2f, nz, zn, c[0], c[1], c[2]);
        P.kind[i0] = P.kind[i1] = P.kind[i2] = P.kind[i3] = Batch.THIN;
        P.quad(i0, i1, i2, i3);
    }

    private void props(Piece P, float s0, float s1, int lod) {
        float e = ty.half(), pv = ty.paved();
        float[] a = new float[6], b = new float[6];
        // Leitpfosten alle 50 m, weiß mit schwarzem Band und Rückstrahler
        if (ty.posts) {
            for (float s = (float) Math.ceil(s0 / 50) * 50; s < s1; s += 50) {
                if (w.bridgeAt(s)) continue;
                for (int sd = -1; sd <= 1; sd += 2) {
                    w.at(s, sd * (pv + 0.5f), a);
                    float gy = a[1] - ty.thickness;
                    box(P, a[0], gy, a[2], a[3], a[4], 0.12f, 0.1f, 1.0f, POST);
                    box(P, a[0], gy + 0.72f, a[2], a[3], a[4], 0.125f, 0.105f, 0.18f, DARK);
                    // Rückstrahler: kleine Tafel auf beiden Seiten längs der Achse
                    for (int dir = -1; dir <= 1; dir += 2) {
                        float cx = a[0] + a[3] * dir * 0.065f, cz = a[2] + a[4] * dir * 0.065f;
                        float bx = -a[4] * 0.04f, bz = a[3] * 0.04f;
                        float[] c = new float[3];
                        color(REFLECT, cx, cz, 0, 0, 0, c);
                        int i0 = P.v(cx - bx, gy + 0.76f, cz - bz, a[3] * dir, 0, a[4] * dir, REFLECT, c[0], c[1], c[2]);
                        int i1 = P.v(cx + bx, gy + 0.76f, cz + bz, a[3] * dir, 0, a[4] * dir, REFLECT, c[0], c[1], c[2]);
                        int i2 = P.v(cx + bx, gy + 0.86f, cz + bz, a[3] * dir, 0, a[4] * dir, REFLECT, c[0], c[1], c[2]);
                        int i3 = P.v(cx - bx, gy + 0.86f, cz - bz, a[3] * dir, 0, a[4] * dir, REFLECT, c[0], c[1], c[2]);
                        P.quad(i0, i1, i2, i3);
                    }
                }
            }
        }
        // Schutzplanken: außen und in der Mitte, Pfosten alle 4 m
        if (ty.guardrail || ty.medianBarrier) {
            java.util.List<Float> us = new java.util.ArrayList<>();
            if (ty.guardrail) { us.add(-(pv + 0.3f)); us.add(pv + 0.3f); }
            if (ty.medianBarrier) us.add(0f);
            float st = lod == 0 ? 2 : 4;
            for (float u : us) {
                for (float s = s0; s < s1 - 1e-3f; s += st) {
                    float se = Math.min(s1, s + st);
                    if (w.bridgeAt(s)) continue;
                    w.at(s, u, a); w.at(se, u, b);
                    float ga = a[1] - (Math.abs(u) > 0 ? ty.thickness : 0), gb = b[1] - (Math.abs(u) > 0 ? ty.thickness : 0);
                    float[] pa = {a[0], ga, a[2]}, pb = {b[0], gb, b[2]};
                    panel(P, pa, pb, 0.5f, 0.82f, METAL, 0.3f);
                    if (Math.floorMod(Math.round(s / st), lod == 0 ? 2 : 1) == 0)
                        box(P, a[0], ga, a[2], a[3], a[4], 0.1f, 0.12f, 0.75f, DARK);
                }
            }
        }
        // Schilder alle 900 m auf der rechten Seite je Richtung
        if (ty.signs) {
            for (float s = (float) Math.ceil(s0 / 900) * 900 + 300; s < s1; s += 900) {
                for (int sd = -1; sd <= 1; sd += 2) {
                    if (w.bridgeAt(s)) continue;
                    w.at(s, sd * (e + 0.6f), a);
                    float gy = site.height(a[0], a[2]);
                    box(P, a[0], gy, a[2], a[3], a[4], 0.08f, 0.08f, 2.1f, METAL);
                    float bx = -a[4], bz = a[3];
                    float[] l = {a[0] - bx * 0.4f, gy, a[2] - bz * 0.4f}, r = {a[0] + bx * 0.4f, gy, a[2] + bz * 0.4f};
                    float off = -sd * 0.05f;
                    l[0] += a[3] * off; l[2] += a[4] * off; r[0] += a[3] * off; r[2] += a[4] * off;
                    panel(P, l, r, 1.4f, 2.4f, SIGN, 0);
                    if (ty.marking == WayType.Marking.EU) panel(P, l, r, 2.25f, 2.4f, RED, 0);
                    else panel(P, l, r, 1.4f, 1.5f, DARK, 0);
                }
            }
        }
        // Schneestangen (orange, 2,4 m) alle 30 m
        P.pole0 = P.nt;
        if (ty.snowPoles) {
            for (float s = (float) Math.ceil(s0 / 30) * 30; s < s1; s += 30) {
                if (w.bridgeAt(s)) continue;
                for (int sd = -1; sd <= 1; sd += 2) {
                    w.at(s, sd * (e + 0.3f), a);
                    float gy = site.height(a[0], a[2]);
                    box(P, a[0], Math.min(gy, a[1] - ty.thickness), a[2], a[3], a[4], 0.035f, 0.035f, 2.4f, POLE);
                }
            }
        }
        P.pole1 = P.nt;
    }

    // ------------------------------------------------------------ Brücken

    private void bridges(Piece P, float s0, float s1, int lod) {
        float e = ty.half();
        float st = lod == 2 ? 8 : 2;
        float[] a = new float[6], b = new float[6];
        boolean wood = ty.kind == WayType.Kind.PATH || ty.kind == WayType.Kind.TRACK;
        float slab = wood ? 0.3f : 0.9f;
        for (float s = s0; s < s1 - 1e-3f; s += st) {
            float se = Math.min(s1, s + st);
            if (!w.bridgeAt(s) && !w.bridgeAt(se)) continue;
            for (int sd = -1; sd <= 1; sd += 2) {
                w.at(s, sd * e, a); w.at(se, sd * e, b);
                // Seitenfläche der Platte
                float[] pa = {a[0], a[1] - slab, a[2]}, pb = {b[0], b[1] - slab, b[2]};
                panel(P, pa, pb, 0, slab, wood ? WOOD : CONCRETE, 0);
                if (wood) {
                    panel(P, new float[]{a[0], a[1], a[2]}, new float[]{b[0], b[1], b[2]}, 0.95f, 1.05f, WOOD, 0);
                    if (lod < 2 && Math.floorMod(Math.round(s / st), 1) == 0) box(P, a[0], a[1], a[2], a[3], a[4], 0.08f, 0.08f, 1.05f, WOOD);
                } else {
                    // Brüstung aus Beton mit Geländer
                    panel(P, new float[]{a[0], a[1], a[2]}, new float[]{b[0], b[1], b[2]}, 0, 0.55f, CONCRETE, 0);
                    if (lod < 2) panel(P, new float[]{a[0], a[1], a[2]}, new float[]{b[0], b[1], b[2]}, 0.9f, 1.0f, METAL, 0.3f);
                }
            }
            // Unterseite
            w.at(s, -e, a); w.at(se, -e, b);
            float[] c = new float[3];
            color(CONCRETE, a[0], a[2], 0, 0, 0, c);
            float[] a2 = new float[6], b2 = new float[6];
            w.at(s, e, a2); w.at(se, e, b2);
            int i0 = P.v(a[0], a[1] - slab, a[2], 0, -1, 0, wood ? WOOD : CONCRETE, c[0] * 0.6f, c[1] * 0.6f, c[2] * 0.6f);
            int i1 = P.v(b[0], b[1] - slab, b[2], 0, -1, 0, wood ? WOOD : CONCRETE, c[0] * 0.6f, c[1] * 0.6f, c[2] * 0.6f);
            int i2 = P.v(b2[0], b2[1] - slab, b2[2], 0, -1, 0, wood ? WOOD : CONCRETE, c[0] * 0.6f, c[1] * 0.6f, c[2] * 0.6f);
            int i3 = P.v(a2[0], a2[1] - slab, a2[2], 0, -1, 0, wood ? WOOD : CONCRETE, c[0] * 0.6f, c[1] * 0.6f, c[2] * 0.6f);
            P.quad(i0, i1, i2, i3);
            // Pfeiler
            float pier = wood ? 6 : 24;
            float sp = (float) Math.floor(s / pier) * pier;
            if (sp + pier > s && sp + pier <= se && w.bridgeAt(sp + pier) && w.bridgeAt(sp + pier - 4) && w.bridgeAt(sp + pier + 4)) {
                float ps = sp + pier;
                for (int sd = -1; sd <= 1; sd += 2) {
                    w.at(ps, sd * e * 0.6f, a);
                    float gy = site.height(a[0], a[2]) - 1;
                    float top = a[1] - slab;
                    if (top - gy > 0.3f) box(P, a[0], gy, a[2], a[3], a[4], wood ? 0.2f : 1.2f, wood ? 0.2f : 1.0f, top - gy, wood ? WOOD : CONCRETE);
                }
            }
        }
    }

    // ------------------------------------------------------------ Schneewälle

    /** Geräumter Schnee am Rand: drei Reihen, die mittlere wird je nach Schneehöhe angehoben. */
    private void snowBanks(Piece P, float s0, float s1, int rows) {
        float pv = ty.paved(), e = ty.half();
        float[] c = new float[3];
        float[] us = {pv - 0.2f, (pv + e) / 2 + 0.3f, e + 0.8f};
        float[] lift = {0, 1, 0.25f};
        for (int sd = -1; sd <= 1; sd += 2) {
            int base = P.nv;
            for (int r = 0; r <= rows; r++) {
                float s = s0 + (s1 - s0) * r / rows;
                boolean br = w.bridgeAt(s);
                for (int k = 0; k < 3; k++) {
                    w.at(s, sd * us[k], f);
                    float y = f[1] + 0.02f;
                    if (k == 2) y = Math.max(f[1] - ty.thickness, site.height(f[0], f[2])) + 0.02f;
                    color(BANK, f[0], f[2], s, 0, 0, c);
                    int i = P.v(f[0], y, f[2], 0, 1, 0, BANK, c[0], c[1], c[2]);
                    P.lift[i] = br ? lift[k] * 0.3f : lift[k] * (0.8f + 0.4f * WNoise.value(s * 0.2f, sd * 3.3f));
                    P.fade[i] = k == 2 ? 0.5f : 0;
                }
            }
            for (int r = 0; r < rows; r++)
                for (int k = 0; k < 2; k++) {
                    int a = base + 3 * r + k, b = a + 1, d = a + 3, e2 = d + 1;
                    if (sd > 0) P.quad(a, d, e2, b); else P.quad(a, b, e2, d);
                }
        }
    }
}
