package com.dan.ground;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Die Bodendecke um einen Betrachter: Gräser, Blumen, Steine und offene Erde, im Wind bewegt, nach der
 * Jahreszeit gefärbt und von Gehenden niedergetreten.
 * <p>
 * Der Boden ist in Kacheln geteilt (8 m); jede Kachel wird beim ersten Bedarf aus einem Startwert
 * erzeugt (immer gleich) und behalten, solange sie in Reichweite liegt. Mit der Entfernung wird
 * ausgedünnt: Jedes Stück hat einen Rang, nur die mit kleinem Rang bleiben; die übrigen Halme werden
 * dafür breiter, sodass die Wiese gleich dicht wirkt.
 * <p>
 * Je Bild: {@link #update} bewegt alles und füllt {@link #batch}.
 */
public final class Meadow {
    public final Biome biome;
    public final Site site;
    public final Breeze wind = new Breeze();
    public final Batch batch = new Batch();
    /** Reichweite (m) und bis wohin alles gezeigt wird (danach dünner). */
    public float radius = 32, near = 9;
    /** Kleinster Anteil am Rand der Reichweite. */
    public float minKeep = 0.08f;
    public long seed = 1;
    static final float TILE = 8;

    private final Map<Long, Tile> tiles = new HashMap<>();
    private int day = -1;
    private float snow = -1;
    private volatile int seasonKey;

    /** Gehende, die das Gras niedertreten: x, z, Radius, Stärke (je 4 Werte); vor {@link #update} setzen. */
    public float[] pushers = new float[0];
    public int pusherCount;

    public Meadow(Biome biome, Site site) { this.biome = biome; this.site = site; }

    /** Jahreszeit: Tag 1..365, Schnee 0..1. */
    public void setSeason(int day, float snow) {
        if (day == this.day && Math.abs(snow - this.snow) < 0.02f) return;
        this.day = day; this.snow = snow;
        seasonKey++;
    }

    /** Alle Kacheln verwerfen (etwa nach Wechsel von Biom oder Startwert). */
    public void clear() { synchronized (tiles) { tiles.clear(); } }

    public int tileCount() { return tiles.size(); }

    // ------------------------------------------------------------ Kachel

    /** Ruhelage einer Kachel. */
    static final class Tile {
        final int ti, tj;
        int nv, nt, ni;
        float[] pos = new float[3 * 1024], side = new float[3 * 1024], nrm = new float[3 * 1024], w = new float[1024], rgb = new float[3 * 1024];
        byte[] part = new byte[1024];          // 0 Halm/Stängel/Blatt, 1 Blüte, 2 Mitte, 3 Stein, 4 Erde
        short[] vplant = new short[1024];
        int[] tri = new int[3 * 2048];
        // je Stück: erste Ecke, erstes Dreieck, Mitte, Höhe, Rang, Art, Steifheit, Niedertreten (Richtung, Stärke)
        int[] v0 = new int[256], t0 = new int[256], plant = new int[256];
        float[] cx = new float[256], cz = new float[256], hgt = new float[256], rank = new float[256], stiff = new float[256];
        float[] tdx = new float[256], tdz = new float[256], tval = new float[256];
        int colorKey = -1;

        Tile(int ti, int tj) { this.ti = ti; this.tj = tj; }

        int vertex(float x, float y, float z, float sx, float sy, float sz, float nx, float ny, float nz, float wv, int pt, int pl) {
            if (nv == w.length) {
                int c = nv * 2;
                pos = java.util.Arrays.copyOf(pos, 3 * c); side = java.util.Arrays.copyOf(side, 3 * c); nrm = java.util.Arrays.copyOf(nrm, 3 * c);
                w = java.util.Arrays.copyOf(w, c); rgb = java.util.Arrays.copyOf(rgb, 3 * c); part = java.util.Arrays.copyOf(part, c); vplant = java.util.Arrays.copyOf(vplant, c);
            }
            int i = nv++;
            pos[3 * i] = x; pos[3 * i + 1] = y; pos[3 * i + 2] = z;
            side[3 * i] = sx; side[3 * i + 1] = sy; side[3 * i + 2] = sz;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9f;
            nrm[3 * i] = nx / l; nrm[3 * i + 1] = ny / l; nrm[3 * i + 2] = nz / l;
            w[i] = wv; part[i] = (byte) pt; vplant[i] = (short) pl;
            return i;
        }

        void tri(int a, int b, int c) {
            if (3 * nt + 3 > tri.length) tri = java.util.Arrays.copyOf(tri, tri.length * 2);
            tri[3 * nt] = a; tri[3 * nt + 1] = b; tri[3 * nt + 2] = c; nt++;
        }

        void begin(int pl, float x, float z, float h, float rk, float st) {
            if (ni == v0.length) {
                int c = ni * 2;
                v0 = java.util.Arrays.copyOf(v0, c); t0 = java.util.Arrays.copyOf(t0, c); plant = java.util.Arrays.copyOf(plant, c);
                cx = java.util.Arrays.copyOf(cx, c); cz = java.util.Arrays.copyOf(cz, c); hgt = java.util.Arrays.copyOf(hgt, c); rank = java.util.Arrays.copyOf(rank, c);
                stiff = java.util.Arrays.copyOf(stiff, c); tdx = java.util.Arrays.copyOf(tdx, c); tdz = java.util.Arrays.copyOf(tdz, c); tval = java.util.Arrays.copyOf(tval, c);
            }
            v0[ni] = nv; t0[ni] = nt; plant[ni] = pl; cx[ni] = x; cz[ni] = z; hgt[ni] = h; rank[ni] = rk; stiff[ni] = st;
            ni++;
        }

        int vEnd(int i) { return i + 1 < ni ? v0[i + 1] : nv; }
        int tEnd(int i) { return i + 1 < ni ? t0[i + 1] : nt; }
    }

    private Tile build(int ti, int tj) {
        Tile t = new Tile(ti, tj);
        java.util.Random rnd = new java.util.Random(GNoise.hash(ti, tj, seed));
        float[] cv = new float[5];
        float x0 = ti * TILE, z0 = tj * TILE;
        List<Biome.Entry> es = biome.entries;
        for (int e = 0; e < es.size(); e++) {
            Biome.Entry en = es.get(e);
            float expect = en.perM2 * TILE * TILE;
            int n = (int) expect + (rnd.nextFloat() < expect - (int) expect ? 1 : 0);
            for (int k = 0; k < n; k++) {
                float x = x0 + rnd.nextFloat() * TILE, z = z0 + rnd.nextFloat() * TILE;
                float r1 = rnd.nextFloat(), r2 = rnd.nextFloat(), rank = rnd.nextFloat();
                site.cover(x, z, cv);
                float moist = 1 + en.moisture * (cv[4] - 0.5f) * 1.6f;
                float p = cv[en.layer] * Math.max(0, Math.min(1.5f, moist));
                if (r1 >= p) continue;
                float y = site.height(x, z);
                Plant pl = en.plant;
                float sz = pl.size * (1 + pl.sizeVar * (r2 * 2 - 1));
                switch (pl.kind) {
                    case GRASS: grass(t, e, pl, x, y, z, sz, rank, rnd); break;
                    case FLOWER: flower(t, e, pl, x, y, z, sz, rank, rnd); break;
                    case STONE: stone(t, e, pl, x, y, z, sz, rank, rnd); break;
                    default: soil(t, e, pl, x, y, z, sz, rank, rnd);
                }
            }
        }
        return t;
    }

    /** Horst: Halme aus einem Punkt, nach außen geneigt, gebogen; je Halm 5 Ecken, 3 Dreiecke. */
    private void grass(Tile t, int e, Plant pl, float x, float y, float z, float H, float rank, java.util.Random rnd) {
        t.begin(e, x, z, H, rank, pl.stiffness);
        for (int b = 0; b < pl.blades; b++) {
            float a = rnd.nextFloat() * 6.2832f, rr = pl.spread * (float) Math.sqrt(rnd.nextFloat());
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            float bx = x + ca * rr, bz = z + sa * rr;
            float h = H * (0.6f + 0.4f * rnd.nextFloat());
            float out = pl.lean * h * (0.4f + 0.8f * rnd.nextFloat());
            // Blattfläche quer zur Neigung, etwas verdreht
            float tw = (rnd.nextFloat() - 0.5f) * 1.2f;
            float qx = -(sa * (float) Math.cos(tw) + ca * (float) Math.sin(tw)), qz = ca * (float) Math.cos(tw) - sa * (float) Math.sin(tw);
            float wd = pl.bladeWidth * (0.7f + 0.6f * rnd.nextFloat());
            float[] lv = {0, 0.5f, 1};
            int[] id = new int[5];
            for (int k = 0; k < 2; k++) {
                float s = lv[k], o = out * s * s, yy = y + h * s * (1 - 0.25f * s * pl.lean);
                float hw = wd * 0.5f * (1 - 0.55f * s);
                float px = bx + ca * o, pz = bz + sa * o;
                id[2 * k] = t.vertex(px, yy, pz, -qx * hw, 0, -qz * hw, ca * 0.4f, 1, sa * 0.4f, s, 0, e);
                id[2 * k + 1] = t.vertex(px, yy, pz, qx * hw, 0, qz * hw, ca * 0.4f, 1, sa * 0.4f, s, 0, e);
            }
            float o = out;
            id[4] = t.vertex(bx + ca * o, y + h * (1 - 0.25f * pl.lean), bz + sa * o, 0, 0, 0, ca * 0.4f, 1, sa * 0.4f, 1, 0, e);
            t.tri(id[0], id[1], id[3]); t.tri(id[0], id[3], id[2]); t.tri(id[2], id[3], id[4]);
        }
    }

    /** Blume: Stängel, zwei Blätter, Blüte als Scheibe mit Blütenblättern oder als Ähre. */
    private void flower(Tile t, int e, Plant pl, float x, float y, float z, float H, float rank, java.util.Random rnd) {
        t.begin(e, x, z, H, rank, pl.stiffness);
        float a = rnd.nextFloat() * 6.2832f, ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        float bend = 0.06f * H * (rnd.nextFloat() - 0.3f);
        float sw = 0.004f;
        int prev0 = -1, prev1 = -1;
        for (int k = 0; k <= 2; k++) {
            float s = k / 2f, o = bend * s * s, yy = y + H * s;
            int i0 = t.vertex(x + ca * o, yy, z + sa * o, -sa * sw, 0, ca * sw, ca, 0.3f, sa, s, 0, e);
            int i1 = t.vertex(x + ca * o, yy, z + sa * o, sa * sw, 0, -ca * sw, ca, 0.3f, sa, s, 0, e);
            if (prev0 >= 0) { t.tri(prev0, prev1, i1); t.tri(prev0, i1, i0); }
            prev0 = i0; prev1 = i1;
        }
        // zwei Blätter unten
        for (int k = 0; k < 2; k++) {
            float la = a + (k == 0 ? 1.4f : -1.7f), lx = (float) Math.cos(la), lz = (float) Math.sin(la);
            float L = H * 0.3f, W = H * 0.06f;
            float yb = y + H * 0.08f;
            int p0 = t.vertex(x, yb, z, 0, 0, 0, 0, 1, 0, 0.1f, 0, e);
            int p1 = t.vertex(x + lx * L * 0.5f - lz * W, yb + L * 0.35f, z + lz * L * 0.5f + lx * W, 0, 0, 0, 0, 1, 0, 0.3f, 0, e);
            int p2 = t.vertex(x + lx * L, yb + L * 0.2f, z + lz * L, 0, 0, 0, 0, 1, 0, 0.35f, 0, e);
            int p3 = t.vertex(x + lx * L * 0.5f + lz * W, yb + L * 0.35f, z + lz * L * 0.5f - lx * W, 0, 0, 0, 0, 1, 0, 0.3f, 0, e);
            t.tri(p0, p1, p2); t.tri(p0, p2, p3);
        }
        float hx = x + ca * bend, hy = y + H, hz = z + sa * bend;
        if (pl.spike) {
            // Ähre: Blütchen an der Spitze entlang, oben kleiner
            int nf = 7;
            for (int k = 0; k < nf; k++) {
                float s = k / (float) (nf - 1);
                float fy = hy - pl.head * 2.2f * (1 - s);
                float fa = a + k * 2.4f, fr = pl.head * 0.5f * (1 - 0.6f * s);
                float fx = (float) Math.cos(fa) * fr, fz = (float) Math.sin(fa) * fr;
                int q0 = t.vertex(hx, fy - fr * 0.6f, hz, 0, 0, 0, fx, 0.5f, fz, 1, 1, e);
                int q1 = t.vertex(hx + fx - fz * 0.6f, fy, hz + fz + fx * 0.6f, 0, 0, 0, fx, 0.5f, fz, 1, 1, e);
                int q2 = t.vertex(hx + fx * 1.4f, fy + fr * 0.8f, hz + fz * 1.4f, 0, 0, 0, fx, 0.5f, fz, 1, 1, e);
                int q3 = t.vertex(hx + fx + fz * 0.6f, fy, hz + fz - fx * 0.6f, 0, 0, 0, fx, 0.5f, fz, 1, 1, e);
                t.tri(q0, q1, q2); t.tri(q0, q2, q3);
            }
        } else {
            // Scheibe: zur Seite geneigt, Blütenblätter als Dreiecke, Mitte als kleiner Fächer
            float tilt = 0.5f + 0.4f * rnd.nextFloat();
            float nx = ca * tilt, ny = 1, nz = sa * tilt, nl = (float) Math.sqrt(nx * nx + 1 + nz * nz);
            nx /= nl; ny /= nl; nz /= nl;
            // Achsen in der Blütenebene
            float ux = -sa, uz = ca, uy = 0;
            float vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
            float R = pl.head, rc = R * 0.3f;
            int c = t.vertex(hx + nx * rc * 0.3f, hy + ny * rc * 0.3f, hz + nz * rc * 0.3f, 0, 0, 0, nx, ny, nz, 1, 2, e);
            int[] ring = new int[6];
            for (int k = 0; k < 6; k++) {
                float an = k * 6.2832f / 6, cu = (float) Math.cos(an) * rc, cvv = (float) Math.sin(an) * rc;
                ring[k] = t.vertex(hx + ux * cu + vx * cvv, hy + uy * cu + vy * cvv, hz + uz * cu + vz * cvv, 0, 0, 0, nx, ny, nz, 1, 2, e);
            }
            for (int k = 0; k < 6; k++) t.tri(c, ring[k], ring[(k + 1) % 6]);
            for (int k = 0; k < pl.petals; k++) {
                float an = k * 6.2832f / pl.petals + rnd.nextFloat() * 0.2f, cu = (float) Math.cos(an), cvv = (float) Math.sin(an);
                float wdt = Math.min(0.6f, 2.6f / pl.petals);
                float c2 = (float) Math.cos(an + wdt), s2 = (float) Math.sin(an + wdt), c3 = (float) Math.cos(an - wdt), s3 = (float) Math.sin(an - wdt);
                float droop = -0.15f * R;
                int p0 = t.vertex(hx + (ux * c2 + vx * s2) * rc, hy + (uy * c2 + vy * s2) * rc, hz + (uz * c2 + vz * s2) * rc, 0, 0, 0, nx, ny, nz, 1, 1, e);
                int p1 = t.vertex(hx + (ux * cu + vx * cvv) * R + nx * droop, hy + (uy * cu + vy * cvv) * R + ny * droop, hz + (uz * cu + vz * cvv) * R + nz * droop, 0, 0, 0, nx, ny, nz, 1, 1, e);
                int p2 = t.vertex(hx + (ux * c3 + vx * s3) * rc, hy + (uy * c3 + vy * s3) * rc, hz + (uz * c3 + vz * s3) * rc, 0, 0, 0, nx, ny, nz, 1, 1, e);
                t.tri(p0, p1, p2);
            }
        }
    }

    /** Stein: halb im Boden, unregelmäßig; Kiesel mit fünf, Steine mit sechs Seiten. */
    private void stone(Tile t, int e, Plant pl, float x, float y, float z, float D, float rank, java.util.Random rnd) {
        t.begin(e, x, z, D, rank, 99);
        int n = D < 0.12f ? 5 : 6;
        float R = D * 0.5f, Hh = D * (0.3f + 0.25f * rnd.nextFloat());
        float rot = rnd.nextFloat() * 6.28f, sq = 0.7f + 0.5f * rnd.nextFloat();
        int top = t.vertex(x + (rnd.nextFloat() - 0.5f) * R * 0.3f, y + Hh, z + (rnd.nextFloat() - 0.5f) * R * 0.3f, 0, 0, 0, 0, 1, 0, 0, 3, e);
        int[] mid = new int[n], bot = new int[n];
        for (int k = 0; k < n; k++) {
            float an = rot + k * 6.2832f / n + (rnd.nextFloat() - 0.5f) * 0.4f, jag = 0.8f + 0.4f * rnd.nextFloat();
            float cx = (float) Math.cos(an) * R * jag, cz = (float) Math.sin(an) * R * jag * sq;
            mid[k] = t.vertex(x + cx * 0.85f, y + Hh * 0.55f, z + cz * 0.85f, 0, 0, 0, cx, R * 0.8f, cz, 0, 3, e);
            bot[k] = t.vertex(x + cx, y - 0.02f, z + cz, 0, 0, 0, cx, 0.1f * R, cz, 0, 3, e);
        }
        for (int k = 0; k < n; k++) {
            int k1 = (k + 1) % n;
            t.tri(top, mid[k1], mid[k]);
            t.tri(mid[k], mid[k1], bot[k1]); t.tri(mid[k], bot[k1], bot[k]);
        }
    }

    /** Offene Erde: flacher, unregelmäßiger Fleck knapp über dem Boden. */
    private void soil(Tile t, int e, Plant pl, float x, float y, float z, float D, float rank, java.util.Random rnd) {
        t.begin(e, x, z, D, rank, 99);
        int n = 10;
        int c = t.vertex(x, site.height(x, z) + 0.012f, z, 0, 0, 0, 0, 1, 0, 0, 4, e);
        int[] ring = new int[n];
        float ph = rnd.nextFloat() * 10;
        for (int k = 0; k < n; k++) {
            float an = k * 6.2832f / n;
            float r = D * 0.5f * (0.65f + 0.5f * GNoise.value(an * 1.3f + ph, ph));
            float px = x + (float) Math.cos(an) * r, pz = z + (float) Math.sin(an) * r;
            ring[k] = t.vertex(px, site.height(px, pz) + 0.008f, pz, 0, 0, 0, 0, 1, 0, 1, 4, e);
        }
        for (int k = 0; k < n; k++) t.tri(c, ring[k], ring[(k + 1) % n]);
    }

    // ------------------------------------------------------------ Jahreszeit

    /** Farben einer Kachel nach Tag und Schnee. */
    private void color(Tile t) {
        float[] c = new float[3];
        List<Biome.Entry> es = biome.entries;
        for (int i = 0; i < t.nv; i++) {
            Plant pl = es.get(t.vplant[i]).plant;
            float wv = t.w[i];
            float jitter = 0.85f + 0.3f * GNoise.value(t.pos[3 * i] * 3.1f, t.pos[3 * i + 2] * 3.1f);
            switch (t.part[i]) {
                case 0: {
                    grassColor(pl, c);
                    float k = (0.55f + 0.6f * wv) * jitter;           // unten dunkel, oben heller
                    c[0] *= k; c[1] *= k; c[2] *= k;
                    break;
                }
                case 1: c[0] = pl.petal[0] * jitter; c[1] = pl.petal[1] * jitter; c[2] = pl.petal[2] * jitter; break;
                case 2: c[0] = pl.center[0]; c[1] = pl.center[1]; c[2] = pl.center[2]; break;
                default: {
                    float k = jitter * (t.part[i] == 3 ? 0.8f + 0.4f * GNoise.value(t.pos[3 * i] * 9, t.pos[3 * i + 2] * 9) : 1);
                    c[0] = pl.color[0] * k; c[1] = pl.color[1] * k; c[2] = pl.color[2] * k;
                }
            }
            // Schnee auf Steinen und Erde (Pflanzen liegen darunter, siehe update)
            if (snow > 0.05f && t.part[i] >= 3) {
                float up = t.nrm[3 * i + 1];
                float s = Math.min(1, snow * 1.3f) * Math.max(0, Math.min(1, (up - 0.2f) / 0.5f));
                c[0] += (0.8f - c[0]) * s; c[1] += (0.82f - c[1]) * s; c[2] += (0.86f - c[2]) * s;
            }
            t.rgb[3 * i] = c[0]; t.rgb[3 * i + 1] = c[1]; t.rgb[3 * i + 2] = c[2];
        }
    }

    /** Farbe eines Grases (oder der grünen Teile einer Blume) am eingestellten Tag. */
    void grassColor(Plant pl, float[] out) {
        int d = day < 0 ? 180 : day;
        int gu = pl.kind == Plant.Kind.FLOWER ? 110 : pl.greenUp;
        int ds = pl.kind == Plant.Kind.FLOWER ? pl.bloomEnd + 5 : pl.dryStart, df = pl.kind == Plant.Kind.FLOWER ? pl.bloomEnd + 40 : pl.dryFull;
        float[] g = pl.kind == Plant.Kind.FLOWER ? new float[]{0.05f, 0.12f, 0.03f} : pl.green;
        float[] dr = pl.kind == Plant.Kind.FLOWER ? new float[]{0.20f, 0.15f, 0.07f} : pl.dry;
        float spring = smooth(gu - 25, gu + 10, d);             // Winter → grün
        float dry = smooth(ds, df, d);                           // grün → trocken
        float late = smooth(290, 330, d);                        // trocken → Winterfarbe
        float r = pl.winter[0] + (g[0] - pl.winter[0]) * spring, gg = pl.winter[1] + (g[1] - pl.winter[1]) * spring, b = pl.winter[2] + (g[2] - pl.winter[2]) * spring;
        r += (dr[0] - r) * dry; gg += (dr[1] - gg) * dry; b += (dr[2] - b) * dry;
        r += (pl.winter[0] - r) * late; gg += (pl.winter[1] - gg) * late; b += (pl.winter[2] - b) * late;
        out[0] = r; out[1] = gg; out[2] = b;
    }

    /**
     * Farbe des Bodens zwischen den Pflanzen an (x, z) (Grasnarbe, Streu, offene Erde, Kies) zum
     * eingestellten Tag: damit Lücken nicht wie nackter Boden wirken. out = r, g, b (linear).
     */
    public void carpet(double x, double z, float[] out) {
        float[] cv = new float[5], c = new float[3];
        site.cover(x, z, cv);
        float r = 0, g = 0, b = 0, wsum = 0;
        for (Biome.Entry en : biome.entries) {
            if (en.plant.kind != Plant.Kind.GRASS) continue;
            grassColor(en.plant, c);
            float w = en.perM2 * Math.max(0.05f, 1 + en.moisture * (cv[4] - 0.5f) * 1.6f);
            r += c[0] * w; g += c[1] * w; b += c[2] * w; wsum += w;
        }
        if (wsum > 0) { r /= wsum; g /= wsum; b /= wsum; } else { r = 0.12f; g = 0.11f; b = 0.07f; }
        float n = GNoise.value((float) x * 1.3f, (float) z * 1.3f);
        // Narbe: dunkler als die Halme (Schatten zwischen ihnen), Streu dazwischen
        float k = 0.55f + 0.25f * n;
        r *= k; g *= k; b *= k;
        float grass = Math.min(1, cv[0] * 1.2f);
        float sr = 0.12f, sg = 0.095f, sb = 0.065f;
        r = sr + (r - sr) * grass; g = sg + (g - sg) * grass; b = sb + (b - sb) * grass;
        float soil = Math.min(1, cv[3] * 1.4f);
        r += (0.11f - r) * soil; g += (0.085f - g) * soil; b += (0.06f - b) * soil;
        float gravel = Math.min(1, cv[2] * 0.6f) * (1 - soil);
        r += (0.17f - r) * gravel; g += (0.16f - g) * gravel; b += (0.145f - b) * gravel;
        if (snow > 0.05f) { float s = Math.min(1, snow * 1.4f); r += (0.8f - r) * s; g += (0.82f - g) * s; b += (0.86f - b) * s; }
        out[0] = r; out[1] = g; out[2] = b;
    }

    /** Zählt jede Änderung der Jahreszeit hoch (für Zeichner, die {@link #carpet} zwischenspeichern). */
    public int seasonKey() { return seasonKey; }

    /** Wie weit eine Blume blüht, 0..1. */
    public float bloom(Plant pl, int d) {
        if (d < pl.bloomStart || d > pl.bloomEnd) return 0;
        if (d < pl.bloomPeak) return smooth(pl.bloomStart, pl.bloomPeak, d);
        return 1 - smooth(pl.bloomEnd - 12, pl.bloomEnd, d);
    }

    static float smooth(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------ Bild

    private float lastT = Float.NaN;

    /**
     * Ein Bild zur Zeit t mit Betrachter bei (cx, cz): Kacheln nachladen und verwerfen, alles in
     * Reichweite bewegen und in {@link #batch} ablegen.
     */
    public void update(float t, double cx, double cz) {
        float dt = Float.isNaN(lastT) ? 0 : Math.max(0, Math.min(0.5f, t - lastT));
        lastT = t;
        int i0 = (int) Math.floor((cx - radius) / TILE), i1 = (int) Math.floor((cx + radius) / TILE);
        int j0 = (int) Math.floor((cz - radius) / TILE), j1 = (int) Math.floor((cz + radius) / TILE);
        List<long[]> missing = new ArrayList<>();
        List<Tile> act = new ArrayList<>();
        synchronized (tiles) {
            tiles.entrySet().removeIf(en -> {
                Tile tl = en.getValue();
                return tl.ti < i0 - 1 || tl.ti > i1 + 1 || tl.tj < j0 - 1 || tl.tj > j1 + 1;
            });
            for (int j = j0; j <= j1; j++)
                for (int i = i0; i <= i1; i++) {
                    // nur Kacheln, die den Kreis berühren
                    double nx = Math.max(i * TILE, Math.min(cx, (i + 1) * TILE)), nz = Math.max(j * TILE, Math.min(cz, (j + 1) * TILE));
                    if ((nx - cx) * (nx - cx) + (nz - cz) * (nz - cz) > radius * radius) continue;
                    Tile tl = tiles.get(key(i, j));
                    if (tl == null) missing.add(new long[]{i, j});
                    else act.add(tl);
                }
        }
        if (!missing.isEmpty()) {
            Tile[] built = new Tile[missing.size()];
            IntStream.range(0, built.length).parallel().forEach(k -> built[k] = build((int) missing.get(k)[0], (int) missing.get(k)[1]));
            synchronized (tiles) { for (Tile tl : built) { tiles.put(key(tl.ti, tl.tj), tl); act.add(tl); } }
        }
        int sk = seasonKey;
        act.parallelStream().forEach(tl -> { if (tl.colorKey != sk) { color(tl); tl.colorKey = sk; } });
        // Welche Stücke bleiben? Erst zählen, dann parallel füllen
        int nTiles = act.size();
        int[] vOff = new int[nTiles + 1], tOff = new int[nTiles + 1];
        boolean[][] keep = new boolean[nTiles][];
        float[][] widen = new float[nTiles][];
        final int d = day < 0 ? 180 : day;
        final float sn = Math.max(0, snow);
        IntStream.range(0, nTiles).parallel().forEach(k -> {
            Tile tl = act.get(k);
            keep[k] = new boolean[tl.ni];
            widen[k] = new float[tl.ni];
            int vc = 0, tc = 0;
            for (int i = 0; i < tl.ni; i++) {
                float dx = (float) (tl.cx[i] - cx), dz = (float) (tl.cz[i] - cz);
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                if (dist > radius) continue;
                Plant pl = biome.entries.get(tl.plant[i]).plant;
                float kf = keepFraction(pl, dist);
                if (tl.rank[i] >= kf) continue;
                boolean alive = pl.kind == Plant.Kind.STONE || pl.kind == Plant.Kind.SOIL;
                if (!alive && sn > 0.55f) continue;                    // unter dem Schnee
                keep[k][i] = true;
                widen[k][i] = (float) Math.min(3, 1 / Math.sqrt(Math.max(kf, 0.05f)));
                vc += tl.vEnd(i) - tl.v0[i];
                tc += tl.tEnd(i) - tl.t0[i];
            }
            vOff[k + 1] = vc; tOff[k + 1] = tc;
        });
        for (int k = 0; k < nTiles; k++) { vOff[k + 1] += vOff[k]; tOff[k + 1] += tOff[k]; }
        Batch b = batch;
        b.ensure(vOff[nTiles], tOff[nTiles]);
        final float fdt = dt;
        final double camX = cx, camZ = cz;
        IntStream.range(0, nTiles).parallel().forEach(k -> animate(act.get(k), keep[k], widen[k], vOff[k], tOff[k], t, fdt, d, sn, b, camX, camZ));
        b.nv = vOff[nTiles];
        b.nt = tOff[nTiles];
    }

    /** Anteil, der in der Entfernung dist bleibt. Große Steine und Erde bleiben lange, Kiesel verschwinden bald. */
    float keepFraction(Plant pl, float dist) {
        if (dist <= near) return 1;
        float k = (near / dist) * (near / dist);
        if (pl.kind == Plant.Kind.SOIL || (pl.kind == Plant.Kind.STONE && pl.size > 0.15f)) return Math.max(k, 0.6f);
        if (pl.kind == Plant.Kind.STONE) return k * k;
        return Math.max(minKeep, k);
    }

    private void animate(Tile tl, boolean[] keep, float[] widen, int vo, int to, float t, float dt, int day, float snow, Batch b, double camX, double camZ) {
        float[] wv = new float[3];
        int vOut = vo, tOut = to;
        float[] P = tl.pos, S = tl.side, N = tl.nrm, C = tl.rgb, W = tl.w;
        float[] pp = pushers;
        int pc = pusherCount;
        for (int i = 0; i < tl.ni; i++) {
            if (!keep[i]) continue;
            Plant pl = biome.entries.get(tl.plant[i]).plant;
            int a = tl.v0[i], e = tl.vEnd(i);
            boolean moves = pl.kind == Plant.Kind.GRASS || pl.kind == Plant.Kind.FLOWER;
            float bx = 0, bz = 0, turb = 0, H = tl.hgt[i];
            float bloom = pl.kind == Plant.Kind.FLOWER ? bloom(pl, day) : 1;
            float under = snow > 0.05f && moves ? 1 - Math.min(1, snow / 0.55f) * 0.8f : 1;   // unter Schnee niedergedrückt
            if (moves) {
                wind.bend(tl.cx[i], tl.cz[i], t, wv);
                float k = 1 / tl.stiff[i];
                bx = wv[0] * k; bz = wv[1] * k; turb = wv[2];
                // Niedertreten: stark, zur Seite weg; erholt sich in einigen Sekunden
                float tv = tl.tval[i] * (float) Math.exp(-dt / 7f);
                float tdx = tl.tdx[i], tdz = tl.tdz[i];
                for (int q = 0; q < pc; q++) {
                    float dx = tl.cx[i] - pp[4 * q], dz = tl.cz[i] - pp[4 * q + 1], r = pp[4 * q + 2];
                    float d2 = dx * dx + dz * dz;
                    if (d2 >= r * r) continue;
                    float dd = (float) Math.sqrt(d2) + 1e-4f;
                    float f = (float) Math.pow(1 - dd / r, 0.6) * pp[4 * q + 3];
                    if (f > tv) { tv = f; tdx = dx / dd; tdz = dz / dd; }
                }
                tl.tval[i] = tv; tl.tdx[i] = tdx; tl.tdz[i] = tdz;
                bx += tdx * tv * 1.4f; bz += tdz * tv * 1.4f;
                float bl = (float) Math.sqrt(bx * bx + bz * bz);
                if (bl > 1.1f) { bx *= 1.1f / bl; bz *= 1.1f / bl; }
            }
            float wide = widen[i];
            float dcx = (float) (tl.cx[i] - camX), dcz = (float) (tl.cz[i] - camZ);
            float edge = smooth(radius * 0.55f, radius, (float) Math.sqrt(dcx * dcx + dcz * dcz));
            float ph = (tl.cx[i] * 1.7f + tl.cz[i] * 2.3f);
            float ax = 0, ay = 0, az = 0;                              // Spitze des Stängels (Blumen)
            float bs = 0.3f + 0.7f * bloom;
            for (int v = a; v < e; v++) {
                float x = P[3 * v], y = P[3 * v + 1], z = P[3 * v + 2];
                float ww = W[v];
                byte part = tl.part[v];
                float sc = 1;
                if (part == 1 || part == 2) sc = bloom;                  // Blüte nur in der Blütezeit
                if (moves && ww > 0) {
                    float hw = H * ww;
                    float ox = bx * hw * 0.9f * ww, oz = bz * hw * 0.9f * ww;
                    // Zittern
                    float fl = (float) Math.sin(t * (6.5f + (v & 7) * 0.4f) + ph + v * 0.37f) * turb * 0.06f * hw;
                    ox += fl * 0.7f; oz -= fl * 0.5f;
                    float ol = (float) Math.sqrt(ox * ox + oz * oz);
                    float r = ol / (hw + 1e-4f);
                    float drop = hw * (1 - (float) Math.sqrt(Math.max(0.05f, 1 - Math.min(0.95f, r * r))));
                    x += ox; z += oz; y -= drop;
                    if (under < 1) y -= (1 - under) * hw * 0.9f;
                }
                if (pl.kind == Plant.Kind.FLOWER && v == a + 4) { ax = x; ay = y; az = z; }
                if (part == 1 || part == 2) {
                    // Blüte wächst mit der Blütezeit auf und fällt danach zusammen
                    x = ax + (x - ax) * bs; y = ay + (y - ay) * bs; z = az + (z - az) * bs;
                }
                float sx = S[3 * v] * wide, sy = S[3 * v + 1] * wide, sz = S[3 * v + 2] * wide;
                int o = 3 * vOut;
                b.xyz[o] = x + sx; b.xyz[o + 1] = y + sy; b.xyz[o + 2] = z + sz;
                b.nrm[o] = N[3 * v]; b.nrm[o + 1] = N[3 * v + 1]; b.nrm[o + 2] = N[3 * v + 2];
                float cr = C[3 * v], cg = C[3 * v + 1], cb = C[3 * v + 2];
                if ((part == 1 || part == 2) && sc < 1) {
                    // verblüht: Blütenfarbe geht in trockenes Braun über und fällt zusammen
                    cr += (0.16f - cr) * (1 - sc); cg += (0.12f - cg) * (1 - sc); cb += (0.06f - cb) * (1 - sc);
                }
                b.rgb[o] = cr; b.rgb[o + 1] = cg; b.rgb[o + 2] = cb;
                b.solid[vOut] = (byte) (part >= 3 ? 1 : 0);
                // Erde: Saum läuft in den Boden aus; alles: am Rand der Reichweite
                b.fade[vOut] = Math.max(edge, part == 4 ? 0.25f + 0.75f * ww : 0);
                vOut++;
            }
            // Dreiecke; Blüten außerhalb der Blütezeit fallen weg
            int shift = vOut - (e - a) - a;
            int ta = tl.t0[i], te = tl.tEnd(i);
            for (int q = ta; q < te; q++) {
                int i0 = tl.tri[3 * q], i1 = tl.tri[3 * q + 1], i2 = tl.tri[3 * q + 2];
                if (bloom < 0.15f && (tl.part[i0] == 1 || tl.part[i0] == 2)) {
                    // leeres Dreieck (alle Ecken gleich), damit die Zählung stimmt
                    b.tri[3 * tOut] = i0 + shift; b.tri[3 * tOut + 1] = i0 + shift; b.tri[3 * tOut + 2] = i0 + shift;
                } else {
                    b.tri[3 * tOut] = i0 + shift; b.tri[3 * tOut + 1] = i1 + shift; b.tri[3 * tOut + 2] = i2 + shift;
                }
                tOut++;
            }
        }
    }

    private static long key(int i, int j) { return ((long) i << 32) ^ (j & 0xffffffffL); }
}
