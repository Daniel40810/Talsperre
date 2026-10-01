package com.dan.road;

import java.util.stream.IntStream;

/**
 * Alles zusammen: Netz, Wetter, Verkehr, Staub. {@link #update} rechnet einen Zeitschritt und füllt
 * {@link #batch} mit den Dreiecken um die Kamera: Fahrbahn in drei Detailstufen (Stücke werden erst
 * gebaut, wenn sie gebraucht werden), darauf das Wetter (nass, Pfützen, Schnee, Schneewälle),
 * Fahrzeuge und Wanderer mit Lichtern, Staub und Gischt, Lichthöfe.
 * <p>
 * Nachts liefert {@link #lights} die Scheinwerfer als Lichtkegel; {@link #illuminate} rechnet damit das
 * Licht auf einer Fläche, damit ein Zeichner auch Fahrbahn und Gelände davon beleuchten kann.
 */
public final class Roads {
    public final Network net;
    public final Weather weather = new Weather();
    public final Traffic traffic;
    public final Batch batch = new Batch();
    /** Bis zu dieser Entfernung wird gezeichnet (m); Grenzen der Detailstufen. */
    public float radius = 1200, near = 70, mid = 380;
    /** Fahrzeuge bis zu dieser Entfernung (m). */
    public float vehicleRange = 900;
    /** Lichtstärke eines Scheinwerfers (je nach Belichtung des Zeichners anzupassen). */
    public float headlight = 260;
    /** Helligkeit der Lichthöfe um die Leuchten (1 = etwa so hell wie der Nachthimmel mal 10). */
    public float glowScale = 0.08f;
    /** Ohne Verkehr nur die Wege. */
    public volatile boolean showTraffic = true;
    private final Ground site;
    private final java.util.concurrent.ConcurrentHashMap<Long, Deck.Piece> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Dust dust = new Dust();
    private long frame;
    private float time;
    /** Scheinwerfer: je x, y, z, Richtung x, y, z, Stärke. */
    public float[] lights = new float[7 * 64];
    public int lightCount;
    /** Zeit für Aufbau und Verkehr im letzten Schritt (ms). */
    public double msBuild, msTraffic;

    /**
     * @param net  gebautes Netz
     * @param site der Boden, wie der Zeichner ihn zeigt (für Schürzen, Pfosten, Pfeiler)
     */
    public Roads(Network net, Ground site, long seed) {
        this.net = net;
        this.site = site;
        traffic = new Traffic(net, seed);
        traffic.populate();
    }

    /** Stücke vergessen (etwa nach einer Änderung an den Farben eines Typs). */
    public void clear() { cache.clear(); }

    public int pieces() { return cache.size(); }

    // ------------------------------------------------------------ Schritt

    public void update(float t, float dt, double camX, double camY, double camZ) {
        time = t;
        frame++;
        long a0 = System.nanoTime();
        weather.step(dt);
        if (showTraffic) { traffic.step(dt); dust.step(dt, traffic, weather); }
        long a1 = System.nanoTime();
        msTraffic = (a1 - a0) / 1e6;
        collectLights(camX, camZ);
        batch.clear();
        deck(camX, camY, camZ);
        if (showTraffic) {
            vehicles(camX, camY, camZ);
            dust(camX, camY, camZ);
        }
        // alte Stücke vergessen
        if (frame % 60 == 0) cache.values().removeIf(p -> frame - p.used > 240);
        msBuild = (System.nanoTime() - a1) / 1e6;
    }

    // ------------------------------------------------------------ Fahrbahn

    private record Use(Deck.Piece p, boolean poles, boolean banks) { }

    private void deck(double cx, double cy, double cz) {
        java.util.List<long[]> want = new java.util.ArrayList<>();
        for (Way w : net.ways) {
            int nc = (int) Math.ceil(w.length / Deck.LEN);
            for (int c = 0; c < nc; c++) {
                float s = Math.min(w.length, (c + 0.5f) * Deck.LEN);
                int i = Math.min(w.n - 1, Math.round(s / w.step()));
                double dx = w.x[i] - cx, dy = w.y[i] - cy, dz = w.z[i] - cz;
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz) - Deck.LEN / 2;
                if (d > radius) continue;
                int lod = d < near ? 0 : d < mid ? 1 : 2;
                want.add(new long[]{w.index, c, lod});
            }
        }
        // fehlende Stücke parallel bauen
        // je Stück ein eigener Baumeister: seine Rechenpuffer gehören dann nur einem Thread
        want.parallelStream().forEach(k -> cache.computeIfAbsent(key(k), q -> new Deck(net.ways.get((int) k[0]), site).build((int) k[1], (int) k[2])));
        java.util.List<Use> use = new java.util.ArrayList<>();
        boolean poles = weather.poles, banks = weather.snow > 0.02f;
        for (long[] k : want) { Deck.Piece p = cache.get(key(k)); p.used = frame; use.add(new Use(p, poles, banks)); }
        // Plätze vergeben, dann parallel füllen
        int[] v0 = new int[use.size() + 1], t0 = new int[use.size() + 1];
        for (int i = 0; i < use.size(); i++) {
            Use u = use.get(i);
            v0[i + 1] = v0[i] + u.p.nv;
            int nt = u.p.nt - (u.poles ? 0 : u.p.pole1 - u.p.pole0) - (u.banks ? 0 : u.p.bank1 - u.p.bank0);
            t0[i + 1] = t0[i] + nt;
        }
        batch.ensure(v0[use.size()], t0[use.size()]);
        batch.nv = v0[use.size()];
        batch.nt = t0[use.size()];
        IntStream.range(0, use.size()).parallel().forEach(i -> fill(use.get(i), v0[i], t0[i], cx, cy, cz));
    }

    private static long key(long[] k) { return (k[0] << 40) | (k[1] << 4) | k[2]; }

    /** Ein Stück mit Wetter in den Batch schreiben. */
    private void fill(Use u, int vb, int tb, double cx, double cy, double cz) {
        Deck.Piece p = u.p;
        Weather W = weather;
        float wet = W.wet, wt = W.wetTracks, pud = W.puddles, snow = W.snow, rain = W.rain;
        Batch b = batch;
        float[] ref = new float[3];
        for (int i = 0; i < p.nv; i++) {
            int o = vb + i;
            float x = p.xyz[3 * i], y = p.xyz[3 * i + 1], z = p.xyz[3 * i + 2];
            float r = p.rgb[3 * i], g = p.rgb[3 * i + 1], bl = p.rgb[3 * i + 2];
            float nx = p.nrm[3 * i], ny = p.nrm[3 * i + 1], nz = p.nrm[3 * i + 2];
            byte zn = p.zone[i];
            byte kind = p.kind[i];
            float gloss = 0, fade = p.fade[i];
            float wh = p.wheel[i];
            float wv = wet + (wt - wet) * wh;
            switch (zn) {
                case Deck.ASPHALT, Deck.SHOULDER, Deck.GRAVEL, Deck.DIRT, Deck.GRASS, Deck.EARTH, Deck.WHITE, Deck.YELLOW, Deck.WOOD, Deck.CONCRETE -> {
                    boolean paved = zn == Deck.ASPHALT || zn == Deck.SHOULDER || zn == Deck.WHITE || zn == Deck.YELLOW;
                    boolean flat = ny > 0.8f;
                    float dark = paved ? 0.55f : zn == Deck.GRASS ? 0.25f : 0.42f;
                    if (!flat) dark *= 0.5f;
                    float k = 1 - dark * wv;
                    r *= k; g *= k; bl *= k;
                    gloss = flat ? (paved ? 0.08f + 0.5f * wv : zn == Deck.GRASS ? 0.08f * wv : 0.3f * wv) : 0.1f * wv;
                    if (zn == Deck.WHITE || zn == Deck.YELLOW) gloss *= 0.6f;
                    // Pfützen: tiefe Stellen laufen zuerst voll
                    float lo = p.low[i];
                    if (lo > 0 && pud > 0 && flat) {
                        float c = WNoise.smooth(1.02f - lo, 1.12f - lo, pud) * WNoise.smooth(0.15f, 0.3f, lo);
                        if (c > 0) {
                            r += (0.018f - r) * c; g += (0.02f - g) * c; bl += (0.024f - bl) * c;
                            gloss += (1 - gloss) * c;
                            if (rain > 0.02f && c > 0.5f) {
                                // Regentropfen: die Oberfläche zittert
                                float jx = WNoise.value(x * 3.1f + time * 7.3f, z * 3.1f) - 0.5f, jz = WNoise.value(x * 3.1f + 5.1f, z * 3.1f - time * 6.9f) - 0.5f;
                                nx += jx * 0.3f * rain; nz += jz * 0.3f * rain;
                            }
                        }
                    }
                    if (snow > 0.005f && flat) {
                        float cover;
                        if (paved && plowed(p, i)) {
                            // geräumt: festgefahrener Schnee, in den Radspuren Matsch und Asphalt
                            cover = Math.min(1, snow * 1.2f) * (1 - 0.8f * wh);
                            float c2 = cover * 0.9f;
                            float sr = 0.45f + 0.25f * (1 - wh), sg = sr + 0.01f, sb = sr + 0.03f;
                            r += (sr - r) * c2; g += (sg - g) * c2; bl += (sb - bl) * c2;
                            gloss = Math.max(gloss, 0.35f * wh * Math.min(1, snow * 3));
                        } else {
                            float patch = WNoise.value(x * 0.3f + 4.4f, z * 0.3f + 1.1f);
                            cover = WNoise.smooth(0, 0.5f, snow * 1.6f + (patch - 0.5f) * 0.4f) * (1 - 0.45f * wh);
                            r += (0.76f - r) * cover; g += (0.78f - g) * cover; bl += (0.83f - bl) * cover;
                            gloss *= 1 - cover;
                        }
                    }
                }
                case Deck.BANK -> {
                    float lift = p.lift[i] * snow * 0.9f;
                    y += lift;
                    float dirt = 0.25f * p.lift[i];
                    r = 0.72f - dirt * 0.3f; g = 0.74f - dirt * 0.3f; bl = 0.79f - dirt * 0.28f;
                }
                case Deck.REFLECT -> {
                    float gl = retro(x, y + 0.8f, z, cx, cy, cz);
                    if (gl > 0.02f) { kind = Batch.LAMP; float k = 2.5f * gl; r = 1.0f * k; g = 0.62f * k; bl = 0.18f * k; }
                }
                case Deck.METAL -> gloss = 0.3f;
                default -> { }
            }
            if (zn >= Deck.CONCRETE && zn != Deck.BANK && snow > 0.05f && ny > 0.7f) {
                float c = Math.min(1, snow * 2);
                r += (0.76f - r) * c; g += (0.78f - g) * c; bl += (0.83f - bl) * c;
            }
            b.xyz[3 * o] = x; b.xyz[3 * o + 1] = y; b.xyz[3 * o + 2] = z;
            b.nrm[3 * o] = nx; b.nrm[3 * o + 1] = ny; b.nrm[3 * o + 2] = nz;
            b.rgb[3 * o] = r; b.rgb[3 * o + 1] = g; b.rgb[3 * o + 2] = bl;
            b.kind[o] = kind; b.gloss[o] = Math.min(1, gloss); b.fade[o] = fade;
        }
        int k = 0;
        for (int t = 0; t < p.nt; t++) {
            if (!u.poles && t >= p.pole0 && t < p.pole1) continue;
            if (!u.banks && t >= p.bank0 && t < p.bank1) continue;
            int o = tb + k++;
            b.tri[3 * o] = p.tri[3 * t] + vb; b.tri[3 * o + 1] = p.tri[3 * t + 1] + vb; b.tri[3 * o + 2] = p.tri[3 * t + 2] + vb;
        }
    }

    /** Wird der Belag im Winter geräumt? Straßen und Autobahnen ja, Feldwege und Pfade nein. */
    private static boolean plowed(Deck.Piece p, int i) { return p.zone[i] == Deck.ASPHALT || p.zone[i] == Deck.SHOULDER || p.zone[i] == Deck.WHITE || p.zone[i] == Deck.YELLOW; }

    // ------------------------------------------------------------ Licht

    private void collectLights(double cx, double cz) {
        lightCount = 0;
        if (!weather.lights() || !showTraffic) return;
        for (Mover m : traffic.movers) {
            if (m.kind.walks() || m.kind.pedals()) continue;
            double dx = m.x - cx, dz = m.z - cz;
            if (dx * dx + dz * dz > (radius + 150) * (radius + 150)) continue;
            if (7 * (lightCount + 1) > lights.length) lights = java.util.Arrays.copyOf(lights, lights.length * 2);
            float fwd = m.kind.length * 0.5f;
            int o = 7 * lightCount++;
            lights[o] = m.x + m.hx * fwd; lights[o + 1] = m.y + 0.7f; lights[o + 2] = m.z + m.hz * fwd;
            float dy = -0.07f + (float) Math.sin(m.pitch);
            float l = (float) Math.sqrt(1 + dy * dy);
            lights[o + 3] = m.hx / l; lights[o + 4] = dy / l; lights[o + 5] = m.hz / l;
            lights[o + 6] = headlight * (m.kind == Vehicle.MOTORBIKE ? 0.45f : 1);
        }
    }

    /**
     * Licht der Scheinwerfer auf einer Fläche an (x, y, z) mit Normale n: addiert die Bestrahlung
     * (linear, warmweiß) zu out[0..2]. Reichweite etwa 90 m.
     */
    public void illuminate(float x, float y, float z, float nx, float ny, float nz, float[] out) {
        for (int i = 0; i < lightCount; i++) {
            int o = 7 * i;
            float dx = x - lights[o], dy = y - lights[o + 1], dz = z - lights[o + 2];
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > 8100 || d2 < 1e-4f) continue;
            float d = (float) Math.sqrt(d2);
            float c = (dx * lights[o + 3] + dy * lights[o + 4] + dz * lights[o + 5]) / d;
            if (c < 0.8f) continue;
            float cone = WNoise.smooth(0.8f, 0.97f, c);
            float lam = -(dx * nx + dy * ny + dz * nz) / d;
            if (lam <= 0) continue;
            float e = lights[o + 6] * cone * lam / (d2 + 25);
            out[0] += e; out[1] += e * 0.93f; out[2] += e * 0.8f;
        }
    }

    /** Rückstrahler: hell, wenn ein Scheinwerfer ihn trifft und die Kamera nahe hinter dem Licht steht. */
    private float retro(float x, float y, float z, double cx, double cy, double cz) {
        float best = 0;
        for (int i = 0; i < lightCount; i++) {
            int o = 7 * i;
            float dx = x - lights[o], dy = y - lights[o + 1], dz = z - lights[o + 2];
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > 40000) continue;
            float d = (float) Math.sqrt(d2) + 1e-3f;
            float c = (dx * lights[o + 3] + dy * lights[o + 4] + dz * lights[o + 5]) / d;
            if (c < 0.85f) continue;
            float ex = (float) (x - cx), ey = (float) (y - cy), ez = (float) (z - cz);
            float el = (float) Math.sqrt(ex * ex + ey * ey + ez * ez) + 1e-3f;
            float back = (ex * dx + ey * dy + ez * dz) / (el * d);
            float g = WNoise.smooth(0.85f, 0.97f, c) * WNoise.smooth(0.97f, 0.9995f, back) * Math.min(1, 900 / (d2 + 100));
            best = Math.max(best, g);
        }
        return best;
    }

    // ------------------------------------------------------------ Fahrzeuge

    private void vehicles(double cx, double cy, double cz) {
        boolean on = weather.lights();
        float blinkOn = (time * 1.5f) % 1 < 0.5f ? 1 : 0;
        for (Mover m : traffic.movers) {
            double dx = m.x - cx, dz = m.z - cz;
            double d2 = dx * dx + dz * dz;
            if (d2 > vehicleRange * vehicleRange) continue;
            draw(m, on, blinkOn > 0, cx, cy, cz, d2 < 250 * 250);
        }
    }

    private final float[] T = new float[9];

    /** Drehung (Neigung, Rollen) und Lage eines Fahrzeugs; T = vorn, oben, rechts als Spalten. */
    private void frameOf(Mover m) {
        float cp = (float) Math.cos(m.pitch), sp = (float) Math.sin(m.pitch), cr = (float) Math.cos(m.roll), sr = (float) Math.sin(m.roll);
        float fx = m.hx, fz = m.hz, rx = -m.hz, rz = m.hx;
        // vorn nach Neigung, oben/rechts nach Rollen
        float Fx = fx * cp, Fy = sp, Fz = fz * cp;
        float Ux = -fx * sp, Uy = cp, Uz = -fz * sp;
        float Rx = rx, Ry = 0, Rz = rz;
        T[0] = Fx; T[1] = Fy; T[2] = Fz;
        T[3] = Ux * cr - Rx * sr; T[4] = Uy * cr - Ry * sr; T[5] = Uz * cr - Rz * sr;
        T[6] = Rx * cr + Ux * sr; T[7] = Ry * cr + Uy * sr; T[8] = Rz * cr + Uz * sr;
    }

    private void draw(Mover m, boolean on, boolean blink, double cx, double cy, double cz, boolean near) {
        Vehicle.Shape s = m.kind.shape();
        frameOf(m);
        float bx = m.x, by = m.y + m.bounce, bz = m.z;
        Batch b = batch;
        b.ensure(b.nv + 3 * s.nt + 400, b.nt + s.nt + 200);
        float[] lp = new float[3];
        float swing = (float) Math.sin(m.step * Math.PI) * 0.5f * Math.min(1, m.v);
        for (int t = 0; t < s.nt; t++) {
            byte pt = s.part[t];
            float r, g, bl, gl = 0;
            byte kind = Batch.SOLID;
            switch (pt) {
                case Vehicle.BODY, Vehicle.CLOTH -> { r = m.color[0]; g = m.color[1]; bl = m.color[2]; gl = pt == Vehicle.BODY ? 0.35f : 0; }
                case Vehicle.GLASS -> { r = 0.02f; g = 0.025f; bl = 0.03f; gl = 0.85f; }
                case Vehicle.TRIM -> { r = 0.025f; g = 0.025f; bl = 0.027f; }
                case Vehicle.STRIPE -> { r = 0.6f; g = 0.6f; bl = 0.58f; if (m.kind == Vehicle.CAMPER) { r = 0.3f; g = 0.12f; bl = 0.05f; } gl = 0.2f; }
                case Vehicle.HEAD -> {
                    if (on) { kind = Batch.LAMP; r = 6; g = 5.7f; bl = 5; } else { r = 0.5f; g = 0.5f; bl = 0.5f; gl = 0.9f; }
                }
                case Vehicle.TAIL -> {
                    if (m.brake && m.v > 0.05f || m.brake && on) { kind = Batch.LAMP; float k = m.brake ? 5 : 1.4f; r = k; g = 0.05f * k; bl = 0.03f * k; }
                    else if (on) { kind = Batch.LAMP; r = 1.4f; g = 0.07f; bl = 0.04f; }
                    else { r = 0.22f; g = 0.02f; bl = 0.02f; gl = 0.6f; }
                }
                case Vehicle.IND_L, Vehicle.IND_R -> {
                    boolean lit = blink && (m.hazard || m.blink == (pt == Vehicle.IND_L ? -1 : 1));
                    if (lit) { kind = Batch.LAMP; r = 5; g = 2.2f; bl = 0.1f; } else { r = 0.3f; g = 0.15f; bl = 0.03f; gl = 0.6f; }
                }
                case Vehicle.SKIN -> { r = 0.42f; g = 0.28f; bl = 0.2f; }
                case Vehicle.PACK -> { r = 0.1f; g = 0.1f; bl = 0.09f; }
                case Vehicle.LEG_L, Vehicle.LEG_R -> { r = 0.08f; g = 0.08f; bl = 0.1f; }
                default -> { r = m.color[0] * 0.8f; g = m.color[1] * 0.8f; bl = m.color[2] * 0.8f; }
            }
            if (m.kind.walks() || m.kind.pedals()) kind = kind == Batch.LAMP ? kind : Batch.THIN;
            // Beine und Arme schwingen um Hüfte und Schulter
            float ang = 0, pivot = 0;
            if (m.kind.walks()) {
                if (pt == Vehicle.LEG_L) { ang = swing; pivot = 0.87f; } else if (pt == Vehicle.LEG_R) { ang = -swing; pivot = 0.87f; }
                else if (pt == Vehicle.ARM_L) { ang = -swing * 0.7f; pivot = 1.42f; } else if (pt == Vehicle.ARM_R) { ang = swing * 0.7f; pivot = 1.42f; }
            } else if (m.kind.pedals() && (pt == Vehicle.LEG_L || pt == Vehicle.LEG_R)) {
                ang = (float) Math.sin(m.step * 2 * Math.PI + (pt == Vehicle.LEG_L ? 0 : Math.PI)) * 0.5f; pivot = 0.95f;
            }
            float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
            int[] id = new int[3];
            float nx0 = s.n[3 * t], ny0 = s.n[3 * t + 1], nz0 = s.n[3 * t + 2];
            float nxa = nx0 * ca - ny0 * sa, nya = nx0 * sa + ny0 * ca;
            float wnx = T[0] * nxa + T[3] * nya + T[6] * nz0, wny = T[1] * nxa + T[4] * nya + T[7] * nz0, wnz = T[2] * nxa + T[5] * nya + T[8] * nz0;
            for (int c = 0; c < 3; c++) {
                float lx = s.p[9 * t + 3 * c], ly = s.p[9 * t + 3 * c + 1], lz = s.p[9 * t + 3 * c + 2];
                if (ang != 0) { float yy = ly - pivot; float nlx = lx * ca - yy * sa, nly = lx * sa + yy * ca; lx = nlx; ly = nly + pivot; }
                float wx = bx + T[0] * lx + T[3] * ly + T[6] * lz, wy = by + T[1] * lx + T[4] * ly + T[7] * lz, wz = bz + T[2] * lx + T[5] * ly + T[8] * lz;
                id[c] = b.v(wx, wy, wz, wnx, wny, wnz, r, g, bl, kind, gl, 0);
            }
            b.t(id[0], id[1], id[2]);
        }
        wheels(m, s, bx, by, bz, near);
        // Lichthöfe
        if (near || on) for (int k = 0; k < s.lamps.length; k += 4) {
            byte pt = (byte) s.lamps[k + 3];
            float inten = 0, rr = 0, gg = 0, bb = 0, size = 0;
            if (pt == Vehicle.HEAD && on) { inten = 1; rr = 0.9f; gg = 0.85f; bb = 0.7f; size = 0.9f; }
            else if (pt == Vehicle.TAIL && (on || m.brake && m.v > 0.05f)) { inten = m.brake ? 1 : 0.4f; rr = 0.9f; gg = 0.05f; bb = 0.02f; size = 0.55f; }
            else if ((pt == Vehicle.IND_L || pt == Vehicle.IND_R) && blink && (m.hazard || m.blink == (pt == Vehicle.IND_L ? -1 : 1))) { inten = 1; rr = 0.9f; gg = 0.4f; bb = 0.02f; size = 0.45f; }
            if (inten <= 0) continue;
            float lx = s.lamps[k], ly = s.lamps[k + 1], lz = s.lamps[k + 2];
            float wx = bx + T[0] * lx + T[3] * ly + T[6] * lz, wy = by + T[1] * lx + T[4] * ly + T[7] * lz, wz = bz + T[2] * lx + T[5] * ly + T[8] * lz;
            float ex = (float) (cx - wx), ey = (float) (cy - wy), ez = (float) (cz - wz), el = (float) Math.sqrt(ex * ex + ey * ey + ez * ez) + 1e-3f;
            float face = (T[0] * ex + T[1] * ey + T[2] * ez) / el * Math.signum(lx);
            if (face <= 0) continue;
            float k2 = glowScale * inten * (0.25f + 0.75f * face * face);
            glow(wx, wy, wz, size * (1 + el * 0.004f), rr * k2, gg * k2, bb * k2, cx, cy, cz);
        }
    }

    /** Räder als Achtecke mit Speichen (Drehung sichtbar), je beidseitig. */
    private void wheels(Mover m, Vehicle.Shape s, float bx, float by, float bz, boolean near) {
        Batch b = batch;
        int seg = near ? 8 : 6;
        for (int k = 0; k < s.wheels.length; k += 4) {
            float wx0 = s.wheels[k], r = s.wheels[k + 1], wz0 = s.wheels[k + 2], w = s.wheels[k + 3];
            for (int side = -1; side <= 1; side += 2) {
                if (wz0 == 0 && side < 0) continue;
                float zc = wz0 * side;
                float zo = zc + side * w / 2, zi = zc - side * w / 2;
                if (wz0 == 0) { zo = w / 2; zi = -w / 2; }
                int[] o = new int[seg], in = new int[seg], hub = new int[seg];
                int ctr = vtx(b, wx0, r, zo + 0.001f * side, 0, 0, side, 0.12f, 0.12f, 0.13f, bx, by, bz);
                for (int q = 0; q < seg; q++) {
                    double a = m.wheel * -1 + q * 2 * Math.PI / seg;
                    float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                    float lx = wx0 + ca * r, ly = r + sa * r;
                    o[q] = vtx(b, lx, ly, zo, ca, sa, 0, 0.02f, 0.02f, 0.022f, bx, by, bz);
                    in[q] = vtx(b, lx, ly, zi, ca, sa, 0, 0.02f, 0.02f, 0.022f, bx, by, bz);
                    float rim = (q & 1) == 0 ? 0.3f : 0.06f;
                    hub[q] = vtx(b, wx0 + ca * r * 0.62f, r + sa * r * 0.62f, zo + 0.001f * side, 0, 0, side, rim, rim, rim * 1.05f, bx, by, bz);
                }
                for (int q = 0; q < seg; q++) {
                    int n2 = (q + 1) % seg;
                    b.t(o[q], o[n2], in[n2]); b.t(o[q], in[n2], in[q]);
                    b.t(ctr, hub[q], hub[n2]);
                    b.t(hub[q], o[q], o[n2]); b.t(hub[q], o[n2], hub[n2]);
                }
            }
        }
    }

    private int vtx(Batch b, float lx, float ly, float lz, float nx, float ny, float nz, float r, float g, float bl, float bx, float by, float bz) {
        float wx = bx + T[0] * lx + T[3] * ly + T[6] * lz, wy = by + T[1] * lx + T[4] * ly + T[7] * lz, wz = bz + T[2] * lx + T[5] * ly + T[8] * lz;
        float wnx = T[0] * nx + T[3] * ny + T[6] * nz, wny = T[1] * nx + T[4] * ny + T[7] * nz, wnz = T[2] * nx + T[5] * ny + T[8] * nz;
        return b.v(wx, wy, wz, wnx, wny, wnz, r, g, bl, Batch.SOLID, 0.1f, 0);
    }

    /** Lichthof: ein zur Kamera gedrehter Fächer, innen hell, außen null. */
    private void glow(float x, float y, float z, float rad, float r, float g, float bl, double cx, double cy, double cz) {
        Batch b = batch;
        float ex = (float) (cx - x), ey = (float) (cy - y), ez = (float) (cz - z), el = (float) Math.sqrt(ex * ex + ey * ey + ez * ez) + 1e-4f;
        ex /= el; ey /= el; ez /= el;
        // zwei Richtungen quer zur Blickrichtung
        float ax = -ez, ay = 0, az = ex, al = (float) Math.sqrt(ax * ax + az * az) + 1e-6f;
        ax /= al; az /= al;
        float ux = ey * az - ez * ay, uy = ez * ax - ex * az, uz = ex * ay - ey * ax;
        // etwas zur Kamera ziehen, damit die Leuchte selbst den Hof nicht verdeckt
        x += ex * 0.15f; y += ey * 0.15f; z += ez * 0.15f;
        int c = b.v(x, y, z, ex, ey, ez, r, g, bl, Batch.GLOW, 0, 0);
        int seg = 8;
        int[] ring = new int[seg];
        for (int q = 0; q < seg; q++) {
            double a = q * 2 * Math.PI / seg;
            float ca = (float) Math.cos(a) * rad, sa = (float) Math.sin(a) * rad;
            ring[q] = b.v(x + ax * ca + ux * sa, y + ay * ca + uy * sa, z + az * ca + uz * sa, ex, ey, ez, 0, 0, 0, Batch.GLOW, 0, 0);
        }
        for (int q = 0; q < seg; q++) b.t(c, ring[q], ring[(q + 1) % seg]);
    }

    // ------------------------------------------------------------ Staub

    private void dust(double cx, double cy, double cz) {
        Dust d = dust;
        Batch b = batch;
        for (int i = 0; i < d.n; i++) {
            float a = d.alpha(i);
            if (a < 0.01f) continue;
            float x = d.x[i], y = d.y[i], z = d.z[i];
            float ex = (float) (cx - x), ey = (float) (cy - y), ez = (float) (cz - z), el = (float) Math.sqrt(ex * ex + ey * ey + ez * ez) + 1e-4f;
            if (el > radius) continue;
            ex /= el; ey /= el; ez /= el;
            float ax = -ez, az = ex, al = (float) Math.sqrt(ax * ax + az * az) + 1e-6f;
            ax /= al; az /= al;
            float ux = ey * az, uy = ez * ax - ex * az, uz = -ey * ax;
            float rad = d.size[i];
            int c = b.v(x, y, z, 0, 1, 0, d.r[i], d.g[i], d.b[i], Batch.THIN, 0, 1 - a);
            int seg = 6;
            int[] ring = new int[seg];
            for (int q = 0; q < seg; q++) {
                double an = q * 2 * Math.PI / seg + i;
                float ca = (float) Math.cos(an) * rad, sa = (float) Math.sin(an) * rad;
                ring[q] = b.v(x + ax * ca + ux * sa, y + uy * sa, z + az * ca + uz * sa, 0, 1, 0, d.r[i], d.g[i], d.b[i], Batch.THIN, 0, 1);
            }
            for (int q = 0; q < seg; q++) b.t(c, ring[q], ring[(q + 1) % seg]);
        }
    }

    /** Anzahl der Staub- und Gischtteilchen. */
    public int particles() { return dust.n; }

    /** Anzahl der Teilchen einer Art: 0 Staub, 1 Gischt, 2 Schnee. */
    public int particles(int kind) {
        int c = 0;
        for (int i = 0; i < dust.n; i++) if (dust.kind[i] == kind) c++;
        return c;
    }
}
