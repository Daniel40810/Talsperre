package com.dan.talsperre.core;

import com.dan.talsperre.effects.LightingEngine;
import com.dan.talsperre.effects.Sky;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.stream.IntStream;

/**
 * Software-Renderer mit verzögerter Schattierung, aus Semiramis übernommen (dort nach dem Vorbild
 * von Caracalla) und für das Geysirbecken umgebaut: kein analytischer Boden mehr, das Gelände ist ein
 * Netz mit Bodenkarte.
 * <ol>
 * <li>Rasterung der Dreiecke in Streifen auf allen Kernen: Sichtfeld-Test je Block,
 * Rückseiten weg, Schnitt an der Nahebene, Tiefenpuffer mit linearer Sichttiefe (float),
 * perspektivisch richtige Normalen in einen G-Puffer.</li>
 * <li>Beleuchtung je Pixel: Sonne mit Schattenkarte, Himmelslicht nach Neigung, Glanz,
 * Spiegelung des Himmels; der Boden holt seine Farbe aus der Bodenkarte.</li>
 * <li>Wasser in einem zweiten Durchgang: Wellen in Fließrichtung, Spiegelung im Bild, Glitzern.</li>
 * <li>Lichtstrahlen und Dunst über Fluss und Becken, Sterne, Überstrahlen.</li>
 * <li>Luftperspektive, Belichtungsautomatik, Filmkurve.</li>
 * </ol>
 * Zwei Bilder wechseln sich ab, damit die Oberfläche immer ein fertiges Bild zeigt.
 */
public final class Engine3D {
    private static final int VA = 7; // Werte je Ecke im Aufbau: x y z nx ny nz sky

    private Mesh mesh;
    private Terrain terrain;
    private int W, H;
    private float[] gz = new float[0], gnx, gny, gnz, gsk;
    /**
     * Zwischenspeicher fürs Licht bei ruhender Kamera: Bodenfarbe, Wasserfilm und Sonnenanteil je
     * Bildpunkt. Solange Kamera, Bildgröße, Beleuchtung und Klima gleich bleiben und am Bildpunkt
     * dieselbe Fläche in derselben Tiefe liegt, werden sie übernommen statt neu gerechnet; was sich
     * bewegt (Bäume im Wind, Gischt, Wasser, Dampf), wird weiter jedes Bild gerechnet.
     */
    private float[] kz = new float[0], kr, kg, kb, kfilm, klit, klt, khr, khg, khb;
    /** km: Material am Bildpunkt (−1 keins); kk: was in khr..khb liegt (0 nichts, 1 Dunstfarbe, 2 Himmel). */
    private byte[] km, kk;
    private boolean cacheOk, keep;
    private final double[] cacheKey = new double[21];
    private LightingEngine cacheL;
    private int cacheLightGen = -1, lightGen;
    /** Zwischenspeicher ein- oder ausschalten (zum Messen). */
    public volatile boolean shadeCache = true;
    /** Anteil der Bildhöhe, der gerechnet wird (1 = alles; im Kinomodus das Band zwischen den Balken). */
    public volatile double cropY = 1;
    /** Anteil der Bildpunkte (ohne Wasser), die das letzte Bild aus dem Zwischenspeicher übernommen hat (0..1). */
    public volatile double cacheHit;
    private byte[] gm;
    private float[] hr, hg, hb, colA = new float[0];
    private final BufferedImage[] images = new BufferedImage[2];
    private int cur;
    private final int strips;

    private float[] vx = new float[0], vy = new float[0], vz = new float[0];
    /** Dreiecke nach dem Aufbau, in Teilen je Kern; dazu die Zuordnung zu den Bildstreifen. */
    private static final class Part {
        float[] st = new float[3 * VA * 4096];
        byte[] sm = new byte[4096];
        int[] lo = new int[4096], hi = new int[4096];
        int n;
        final float[] cv = new float[VA * 3], poly = new float[VA * 5];
    }
    private final Part[] parts;
    private int[] binStart = new int[1], binData = new int[0];
    private int nst, rowsPerStrip;
    private static final int PART_BITS = 26;
    /** Dreiecke im letzten Bild (nach Sichtfeld, Rückseiten und Nahebene). */
    public volatile int drawnTris;

    private double exposure = 1;
    private boolean exposureValid;
    private static final float[] GAMMA = new float[4097];

    static {
        for (int i = 0; i <= 4096; i++) {
            double c = i / 4096.0;
            GAMMA[i] = (float) (c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055);
        }
    }

    // Beleuchtung: aktuell, wartend, frei
    private LightingEngine light, pending, spare;
    private LightingEngine L;
    private Sky sky;

    // ------------------------------------------------------------ Effekte
    /** Schalter: Lichtstrahlen und Dunst, Überstrahlen. */
    public volatile boolean rays = true, bloom = true;
    /** Lichtstrahlen: Verstärkung der Sonnenstrahlen im Dunst. */
    public volatile float rayGain = Float.parseFloat(System.getProperty("tal.raygain", "1.5"));
    /** Punktlichter der Szene; lampOn 0..1 folgt der Dämmerung. */
    private volatile com.dan.talsperre.effects.Lamps lamps;
    public volatile boolean lampsEnabled = true;
    private float lampOn;
    private final Sprites lampSprites = new Sprites();
    /** Dunstmenge (Faktor auf den Tagesgang, 1 = normal). */
    public volatile double fogScale = 1;
    /** Tag im Jahr und Uhrzeit für den Dunst; Sternzeit in Grad für die Sterne. */
    public volatile int day = 269;
    public volatile double hour = 9, sidereal = 0;
    private final com.dan.talsperre.effects.NightSky stars = new com.dan.talsperre.effects.NightSky();

    /** Der Sternhimmel (Richtungen gelten für das letzte Nachtbild). */
    public com.dan.talsperre.effects.NightSky nightSky() { return stars; }

    /** Nachtanteil des Himmels im letzten Bild, 0..1. */
    public float night() { return sky == null ? 0 : sky.night; }
    private float mistNow;
    /** Nässe am Boden (Gischt), heiße Quellen, Säulen fürs Streulicht, Teilchen der Ausbrüche. */
    public volatile Wetness.Set wetness;
    private Thermal thermal;
    public volatile float[][] plumes = new float[0][];
    public volatile com.dan.talsperre.effects.ParticleSystem particles;
    /** Teilchen im letzten Bild. */
    public volatile int drawnParticles;
    // Viertelauflösung für Streulicht und Überstrahlen
    private int QW, QH;
    private float[] qr = new float[0], qg, qb, qt, qd, tr, tg, tb;
    private float[] cr, cg, cb;
    private int frame;
    private static final int STEPS = 16, MF = 6;
    // Raster für den Nebelmarsch (1/MF der Auflösung)
    private int MW, MH;
    private float[] mr = new float[0], mg, mb, mt, md, mtr, mtg, mtb;
    private static final float MAX_DIST = 2500;

    /** Wind: Richtung (normiert, waagrecht) und Stärke 0..1. */
    public volatile double windX = 0.8, windZ = 0.6, wind = 0.35;
    /** Überlaufwasser sichtbar (Gruppe 2) und Wirbel im Tosbecken 0..1. */
    public volatile boolean cascadeOn;
    /** Stärke des Seenebels (Vielfaches); der Nebel über dem Wasser steigt mit dem Pegel (Mesh.lakeDy). */
    public volatile double lakeMist = 1;
    public volatile double basinFoam;
    // gedrehte Normalen der Drehkörper im laufenden Bild
    private float[] rnx = new float[0], rny = new float[0], rnz = new float[0];

    // Kamera für das laufende Bild
    private double ex, ey, ez, cfx, cfy, cfz, crx, cry, crz, cux, cuy, cuz, pfx, pfy;
    private float time;

    public Engine3D(Scene scene, int shadowSize) {
        useScene(scene);
        this.light = new LightingEngine(shadowSize);
        this.spare = new LightingEngine(shadowSize);
        this.strips = Math.max(8, Runtime.getRuntime().availableProcessors() * 3);
        int np = Math.min(48, Math.max(4, Runtime.getRuntime().availableProcessors() * 2));
        this.parts = new Part[np];
        for (int i = 0; i < np; i++) parts[i] = new Part();
    }

    public int width() { return W; }
    public int height() { return H; }

    public void setSize(int w, int h) {
        if (w == W && h == H) return;
        W = w; H = h;
        int n = w * h;
        // Puffer wachsen nur: Qualität Auto ändert die Größe oft, neu anlegen würde den Speicher fluten
        if (gz.length < n) {
            int c = n + n / 8;
            gz = new float[c]; gnx = new float[c]; gny = new float[c]; gnz = new float[c]; gsk = new float[c];
            gm = new byte[c];
            hr = new float[c]; hg = new float[c]; hb = new float[c];
            cr = new float[c]; cg = new float[c]; cb = new float[c];
        }
        if (colA.length < w) colA = new float[w + w / 8];
        QW = (w + 3) / 4; QH = (h + 3) / 4;
        int q = QW * QH;
        if (qr.length < q) {
            int c = q + q / 8;
            qr = new float[c]; qg = new float[c]; qb = new float[c]; qt = new float[c]; qd = new float[c];
            tr = new float[c]; tg = new float[c]; tb = new float[c];
        }
        MW = (w + MF - 1) / MF; MH = (h + MF - 1) / MF;
        int mm = MW * MH;
        if (mr.length < mm) {
            int c = mm + mm / 8;
            mr = new float[c]; mg = new float[c]; mb = new float[c]; mt = new float[c]; md = new float[c];
            mtr = new float[c]; mtg = new float[c]; mtb = new float[c];
            mlit = new float[c * STEPS]; mA = new float[c * STEPS]; mB = new float[c * STEPS]; mkz = new float[c]; mji = new byte[c];
        }
        images[0] = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        images[1] = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    }

    // ------------------------------------------------------------ Beleuchtung

    /**
     * Wechselt die Szene. Danach muss die Beleuchtung neu gerechnet werden
     * ({@link #setSky}); Puffer und Schattenkarten bleiben.
     */
    public synchronized void setScene(Scene scene) {
        useScene(scene);
    }

    private void useScene(Scene scene) {
        lightGen++;
        this.mesh = scene.mesh;
        this.terrain = scene.terrain;
        this.thermal = scene.thermal;
        this.lamps = scene.lamps;
        Materials.terrain = scene.terrain;
    }

    /** Sonne und Mond sofort übernehmen. */
    public synchronized void setSky(com.dan.talsperre.effects.DayNightCycle c, double haze) {
        light.compute(mesh, c.dir, c.moonDir, c.moonLit, haze);
        lightGen++;
    }

    /** Für den Licht-Thread: freies Beleuchtungsobjekt holen (oder null, wenn gerade keins frei ist). */
    public synchronized LightingEngine takeSpare() {
        LightingEngine s = spare;
        spare = null;
        return s;
    }

    /** Fertig berechnete Beleuchtung übergeben; sie gilt ab dem nächsten Bild. */
    public synchronized void offer(LightingEngine l) { pending = l; }

    private synchronized LightingEngine acquire() {
        if (pending != null) {
            lightGen++;
            spare = light;
            light = pending;
            pending = null;
        }
        return light;
    }

    public Mesh mesh() { return mesh; }
    public void resetExposure() { exposureValid = false; }
    public double exposure() { return exposure; }

    // ------------------------------------------------------------ Bild

    /** Rechnet ein Bild und gibt es zurück (abwechselnd einer von zwei Puffern). */
    public BufferedImage render(Camera cam, double t, double dt) {
        long t0 = System.nanoTime();
        L = acquire();
        sky = L.sky;
        time = (float) t;
        Materials.time = time;
        {
            com.dan.talsperre.effects.Lamps lp = lamps;
            float dark = 1 - (float) smoothD(-0.14, 0.06, sky.sun[1]);
            lampOn = lp != null && lampsEnabled ? dark : 0;
            if (lp != null) lp.on = lampOn;
        }
        cam.update();
        ex = cam.ex; ey = cam.ey; ez = cam.ez;
        cfx = cam.fx; cfy = cam.fy; cfz = cam.fz;
        crx = cam.rx; cry = cam.ry; crz = cam.rz;
        cux = cam.ux; cuy = cam.uy; cuz = cam.uz;
        // Kinomodus: nur das Breitbild-Band rechnen; der Bildwinkel in der Breite bleibt gleich
        double tanY = Math.tan(cam.fovY / 2) * cropY, tanX = tanY * W / (double) H;
        pfx = (W / 2.0) / tanX;
        pfy = (H / 2.0) / tanY;
        for (int px = 0; px < W; px++) colA[px] = (float) ((px + 0.5 - W / 2.0) / pfx);
        int rowsPer = (H + strips - 1) / strips;
        rowsPerStrip = rowsPer;
        upTables();
        nst = 0;
        if (mesh != null && mesh.nt > 0) {
            transform();
            setup(cam.near, tanX, tanY);
            bin();
        }
        drawnTris = nst;
        checkCache();
        long t1 = System.nanoTime();
        IntStream.range(0, strips).parallel().forEach(s -> clearStrip(s * rowsPer, Math.min(H, (s + 1) * rowsPer)));
        if (nst > 0) IntStream.range(0, strips).parallel().forEach(s -> rasterStrip(s, s * rowsPer, Math.min(H, (s + 1) * rowsPer)));
        long t2 = System.nanoTime();
        frame++;
        // Dunst nach Tageszeit
        mistNow = (float) (mistAmount(hour) * fogScale);
        hitAcc.set(0); eligAcc.set(0);
        IntStream.range(0, strips).parallel().forEach(s -> shadeStrip(s * rowsPer, Math.min(H, (s + 1) * rowsPer)));
        cacheHit = hitAcc.get() / (double) Math.max(1, eligAcc.get());
        IntStream.range(0, strips).parallel().forEach(s -> shadeWaterStrip(s * rowsPer, Math.min(H, (s + 1) * rowsPer)));
        com.dan.road.Roads rds = roads;
        if (rds != null && rds.batch.nt > 0 && !thermo) drawWays(rds, rowsPer);
        com.dan.ground.Batch fb = foliage;
        if (fb != null && fb.nt > 0 && !thermo) drawFoliage(fb, rowsPer);
        long t3 = System.nanoTime();
        if (sky.night > 0.05f && sky.overcast < 0.7f) drawStars();
        int qPer = (QH + strips - 1) / strips;
        long f0 = System.nanoTime();
        if (rays && !thermo) {
            int mPer = (MH + strips - 1) / strips;
            IntStream.range(0, strips).parallel().forEach(s -> marchStrip(s * mPer, Math.min(MH, (s + 1) * mPer)));
            blurQuarter();
            IntStream.range(0, strips).parallel().forEach(s -> applyScatter(s * rowsPer, Math.min(H, (s + 1) * rowsPer)));
        }
        long f1 = System.nanoTime();
        Animals an = animals;
        if (an != null && an.n > 0) drawAnimals(an);
        LeafQuads lq = leaves;
        if (lq != null && lq.n > 0) drawLeaves(lq);
        LeafQuads fq = floats;
        if (fq != null && fq.n > 0) drawLeaves(fq);
        com.dan.talsperre.effects.ParticleSystem pss = particles;
        if (pss != null && pss.n > 0) drawParticles(pss, rowsPer); else drawnParticles = 0;
        final boolean th = thermo;
        if (th) {
            final boolean hasP = pss != null && pss.n > 0 && drawnParticles > 0;
            IntStream.range(0, strips).parallel().forEach(st -> thermoStrip(st * rowsPer, Math.min(H, (st + 1) * rowsPer), hasP));
        }
        if (lamps != null && lampOn > 0.01f) { fillLampSprites(); drawSprites(lampSprites); }
        if (sprites.n > 0) drawSprites(sprites);
        float[] bl = bolt;
        if (bl != null && !th) drawBolt(bl);
        float fl = flash;
        if (fl > 0.01f && !th) {
            // Blitz: der Himmel leuchtet auf, der Boden weniger; bezogen auf die jetzige Belichtung
            final float k = (float) (fl * 0.9 / Math.max(1e-3, exposure));
            IntStream.range(0, strips).parallel().forEach(st -> {
                for (int p = st * rowsPer * W; p < Math.min(H, (st + 1) * rowsPer) * W; p++) {
                    float a = gm[p] == 0 ? k : k * 0.3f;
                    hr[p] += a * 0.85f; hg[p] += a * 0.9f; hb[p] += a;
                }
            });
        }
        long f2 = System.nanoTime();
        long f3 = System.nanoTime();
        if (bloom && !th) doBloom(rowsPer, qPer);
        long t3b = System.nanoTime();
        msFx = (t3b - t3) / 1e6;
        msRays = (f1 - f0) / 1e6; msParticles = (f2 - f1) / 1e6; msShimmer = (f3 - f2) / 1e6; msBloom = (t3b - f3) / 1e6;
        adaptExposure(dt);
        cur ^= 1;
        BufferedImage img = images[cur];
        int[] out = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        if (th) IntStream.range(0, strips).parallel().forEach(s -> thermoOut(out, s * rowsPer, Math.min(H, (s + 1) * rowsPer)));
        else IntStream.range(0, strips).parallel().forEach(s -> tonemap(out, s * rowsPer, Math.min(H, (s + 1) * rowsPer)));
        long t4 = System.nanoTime();
        msSetup = (t1 - t0) / 1e6; msRaster = (t2 - t1) / 1e6; msShade = (t3 - t2) / 1e6; msPost = (t4 - t3) / 1e6;
        return img;
    }

    /** Zeiten des letzten Bildes in Millisekunden: Aufbau, Rasterung, Licht, Belichtung und Filmkurve. */
    public volatile double msSetup, msRaster, msShade, msPost, msFx, msRays, msParticles, msShimmer, msBloom;

    // ------------------------------------------------------------ Geometrie

    private final double[] tKey = new double[12];
    private Mesh tMesh;
    private int tVersion;

    private void transform() {
        int nv = mesh.nv;
        if (vx.length < nv) { vx = new float[nv]; vy = new float[nv]; vz = new float[nv]; }
        if (rnx.length < nv && mesh.spinners.length > 0) { rnx = new float[nv]; rny = new float[nv]; rnz = new float[nv]; }
        final float[] p = mesh.pos, sw = mesh.sway, nr = mesh.nrm, fl = mesh.flutter;
        final int[] sp = mesh.spin;
        final byte[] lkv = mesh.lake;
        final byte[] rsv = mesh.rise;
        final float ldy = mesh.lakeDy;
        final float t = time, wx = (float) windX, wz = (float) windZ, wk = (float) wind;
        // Drehkörper: Winkel je Achse für dieses Bild
        final double[][] sps = mesh.spinners;
        final double[] ca = new double[sps.length], sa = new double[sps.length];
        for (int k = 0; k < sps.length; k++) { double a = sps[k][6] * t; ca[k] = Math.cos(a); sa[k] = Math.sin(a); }
        final int TK = 32;   // genug Stücke auch für viele Kerne
        // Steht die Kamera und ist das Netz unverändert, liegen die starren Ecken schon richtig
        double[] tk = {ex, ey, ez, cfx, cfy, cfz, crx, cry, crz, cux, cuy, cuz};
        boolean same = mesh == tMesh && mesh.version == tVersion && vx.length >= nv;
        for (int i = 0; same && i < 12; i++) if (Math.abs(tk[i] - tKey[i]) > 1e-5) same = false;
        if (!same) System.arraycopy(tk, 0, tKey, 0, 12);
        tMesh = mesh; tVersion = mesh.version;
        final int[] only = same ? mesh.moving() : null;
        final int cnt = same ? only.length : nv;
        IntStream.range(0, TK).parallel().forEach(k -> {
            int a = (int) ((long) cnt * k / TK), b = (int) ((long) cnt * (k + 1) / TK);
            for (int j = a; j < b; j++) {
                int v = only != null ? only[j] : j;
                double px = p[3 * v], py = p[3 * v + 1], pz = p[3 * v + 2];
                if (rsv[v] != 0) py = mesh.deformY(v, (float) py);
                float s = sw[v];
                if (s != 0 && wk > 0) {
                    // Böen: zwei Wellen, die mit dem Wind über das Becken laufen
                    float ph = (float) (px * wx + pz * wz) * 0.09f + (float) (px * 0.37 + pz * 0.23);
                    float g = ((float) Math.sin(t * 1.3f - ph * 0.6f) * 0.55f + (float) Math.sin(t * 3.1f + ph * 1.7f) * 0.25f + 0.45f) * wk;
                    px += wx * s * g; pz += wz * s * g; py -= s * g * g * 0.2f;
                }
                float f = fl[v];
                if (f != 0 && wk > 0) {
                    // Blätter zittern um ihren Stiel: schnell, je Blatt in eigener Phase, stärker in Böen
                    float fph = (float) (p[3 * v] * 3.7 + p[3 * v + 2] * 2.9 + p[3 * v + 1] * 1.3);
                    float fa = f * wk * ((float) Math.sin(t * 9.0f + fph) * 0.7f + (float) Math.sin(t * 14.3f + fph * 1.9f) * 0.3f);
                    px += nr[3 * v] * fa; py += nr[3 * v + 1] * fa; pz += nr[3 * v + 2] * fa;
                }
                if (lkv[v] != 0) py += ldy;
                int q = sp[v];
                if (q >= 0) {
                    double[] ax = sps[q];
                    double ox = px - ax[0], oy = py - ax[1], oz = pz - ax[2];
                    double[] r = rot(ox, oy, oz, ax[3], ax[4], ax[5], ca[q], sa[q]);
                    px = ax[0] + r[0]; py = ax[1] + r[1]; pz = ax[2] + r[2];
                    double[] n = rot(nr[3 * v], nr[3 * v + 1], nr[3 * v + 2], ax[3], ax[4], ax[5], ca[q], sa[q]);
                    rnx[v] = (float) n[0]; rny[v] = (float) n[1]; rnz[v] = (float) n[2];
                }
                double dx = px - ex, dy = py - ey, dz = pz - ez;
                vx[v] = (float) (dx * crx + dy * cry + dz * crz);
                vy[v] = (float) (dx * cux + dy * cuy + dz * cuz);
                vz[v] = (float) (dx * cfx + dy * cfy + dz * cfz);
            }
        });
    }

    /** Drehung von (x,y,z) um die Achse (ax,ay,az) mit cos c und sin s (Rodrigues). */
    private static double[] rot(double x, double y, double z, double ax, double ay, double az, double c, double s) {
        double d = ax * x + ay * y + az * z;
        double cx = ay * z - az * y, cy = az * x - ax * z, cz = ax * y - ay * x;
        return new double[]{x * c + cx * s + ax * d * (1 - c), y * c + cy * s + ay * d * (1 - c), z * c + cz * s + az * d * (1 - c)};
    }

    private boolean chunkVisible(int c, double[][] planes) {
        float[] b = mesh.chunkBox;
        int o = 6 * c;
        for (double[] n : planes) {
            double px = n[0] >= 0 ? b[o + 3] : b[o], py = n[1] >= 0 ? b[o + 4] : b[o + 1], pz = n[2] >= 0 ? b[o + 5] : b[o + 2];
            if ((px - ex) * n[0] + (py - ey) * n[1] + (pz - ez) * n[2] < 0) return false;
        }
        return true;
    }

    private void setup(double near, double tanX, double tanY) {
        float nr = (float) near;
        double[][] planes = {
                {cfx, cfy, cfz},
                {cfx * tanX - crx, cfy * tanX - cry, cfz * tanX - crz},
                {cfx * tanX + crx, cfy * tanX + cry, cfz * tanX + crz},
                {cfx * tanY - cux, cfy * tanY - cuy, cfz * tanY - cuz},
                {cfx * tanY + cux, cfy * tanY + cuy, cfz * tanY + cuz}};
        // sichtbare Blöcke, nach Dreiecken gleichmäßig auf die Teile verteilt
        int nc = mesh.nChunks, total = 0;
        int[] vis = new int[nc];
        int nv = 0;
        final boolean casc = cascadeOn;
        final int hideMask = mesh.hide;
        for (int c = 0; c < nc; c++) if ((casc || mesh.chunkGrp[c] != 2) && (hideMask >> mesh.chunkGrp[c] & 1) == 0 && chunkVisible(c, planes)) { vis[nv++] = c; total += mesh.chunkCount[c]; }
        int P = parts.length;
        int[] from = new int[P + 1];
        int acc = 0, pi = 1;
        for (int k = 0; k < nv && pi < P; k++) {
            acc += mesh.chunkCount[vis[k]];
            if (acc >= (long) total * pi / P) from[pi++] = k + 1;
        }
        while (pi <= P) from[pi++] = nv;
        final int nvis = nv;
        IntStream.range(0, P).parallel().forEach(q -> {
            Part pt = parts[q];
            pt.n = 0;
            for (int k = from[q]; k < from[q + 1] && k < nvis; k++) {
                int c = vis[k];
                int t0 = mesh.chunkStart[c], t1 = t0 + mesh.chunkCount[c];
                for (int t = t0; t < t1; t++) {
                    int a = mesh.idx[3 * t], b = mesh.idx[3 * t + 1], cc = mesh.idx[3 * t + 2];
                    int pa = 3 * a;
                    double dot = (ex - mesh.pos[pa]) * mesh.fn[3 * t] + (ey - (mesh.grp[t] == 3 ? mesh.lakeDy : 0) - mesh.pos[pa + 1]) * mesh.fn[3 * t + 1]
                            + (ez - mesh.pos[pa + 2]) * mesh.fn[3 * t + 2];
                    int m = mesh.mat[t];
                    if (dot <= 0 && mesh.grp[t] != 1) continue;
                    float za = vz[a], zb = vz[b], zc = vz[cc];
                    if (za < nr && zb < nr && zc < nr) continue;
                    float[] cv = pt.cv;
                    load(cv, 0, a); load(cv, VA, b); load(cv, 2 * VA, cc);
                    if (za >= nr && zb >= nr && zc >= nr) {
                        emit(pt, cv, 0, VA, 2 * VA, m);
                    } else {
                        int n = clipNear(cv, pt.poly, nr);
                        for (int j = 1; j + 1 < n; j++) emit(pt, pt.poly, 0, VA * j, VA * (j + 1), m);
                    }
                }
            }
        });
        for (Part pt : parts) nst += pt.n;
    }

    /** Zähler je Teil und Streifen fürs parallele Einsortieren. */
    private int[][] binCount = new int[0][];

    /**
     * Ordnet jedes Dreieck den Bildstreifen zu, die es berührt: jeder Teil zählt für sich
     * (parallel), dann Aufsummieren über Streifen und Teile, dann füllt jeder Teil seine Plätze
     * (wieder parallel). Die Reihenfolge je Streifen bleibt dieselbe wie seriell.
     */
    private void bin() {
        final int S = strips, P = parts.length;
        if (binStart.length < S + 1) binStart = new int[S + 1];
        if (binCount.length != P || (P > 0 && binCount[0].length < S)) binCount = new int[P][S];
        IntStream.range(0, P).parallel().forEach(p -> {
            int[] c = binCount[p];
            java.util.Arrays.fill(c, 0, S, 0);
            Part pt = parts[p];
            for (int i = 0; i < pt.n; i++) for (int q = pt.lo[i]; q <= pt.hi[i]; q++) c[q]++;
        });
        // Startplatz je (Teil, Streifen): Streifen außen, Teile innen, wie beim seriellen Füllen
        int total = 0;
        for (int q = 0; q < S; q++) {
            binStart[q] = total;
            for (int p = 0; p < P; p++) {
                int n = binCount[p][q];
                binCount[p][q] = total;
                total += n;
            }
        }
        binStart[S] = total;
        if (binData.length < total) binData = new int[total + total / 4 + 16];
        IntStream.range(0, P).parallel().forEach(p -> {
            int[] fill = binCount[p];
            Part pt = parts[p];
            for (int i = 0; i < pt.n; i++) {
                int code = (p << PART_BITS) | i;
                for (int q = pt.lo[i]; q <= pt.hi[i]; q++) binData[fill[q]++] = code;
            }
        });
    }

    private void load(float[] d, int o, int v) {
        d[o] = vx[v]; d[o + 1] = vy[v]; d[o + 2] = vz[v];
        if (mesh.spin[v] >= 0) { d[o + 3] = rnx[v]; d[o + 4] = rny[v]; d[o + 5] = rnz[v]; }
        else { d[o + 3] = mesh.nrm[3 * v]; d[o + 4] = mesh.nrm[3 * v + 1]; d[o + 5] = mesh.nrm[3 * v + 2]; }
        d[o + 6] = mesh.sky[v];
    }

    /** Schneidet das Dreieck an der Nahebene; liefert die Eckenzahl in poly. */
    private static int clipNear(float[] tri, float[] poly, float nr) {
        int n = 0;
        for (int i = 0; i < 3; i++) {
            int a = VA * i, b = VA * ((i + 1) % 3);
            boolean ia = tri[a + 2] >= nr, ib = tri[b + 2] >= nr;
            if (ia) { System.arraycopy(tri, a, poly, VA * n, VA); n++; }
            if (ia != ib) {
                float t = (nr - tri[a + 2]) / (tri[b + 2] - tri[a + 2]);
                for (int k = 0; k < VA; k++) poly[VA * n + k] = tri[a + k] + (tri[b + k] - tri[a + k]) * t;
                n++;
            }
        }
        return n;
    }

    private void emit(Part pt, float[] v, int a, int b, int c, int m) {
        int S = 3 * VA;
        int nst = pt.n;
        if (S * nst + S > pt.st.length) {
            pt.st = java.util.Arrays.copyOf(pt.st, pt.st.length * 2);
            pt.sm = java.util.Arrays.copyOf(pt.sm, pt.sm.length * 2);
            pt.lo = java.util.Arrays.copyOf(pt.lo, pt.lo.length * 2);
            pt.hi = java.util.Arrays.copyOf(pt.hi, pt.hi.length * 2);
        }
        float[] st = pt.st;
        int o = S * nst;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int k = 0; k < 3; k++) {
            int src = k == 0 ? a : (k == 1 ? b : c);
            float iz = 1f / v[src + 2];
            float sx = (float) (W / 2.0 + v[src] * iz * pfx);
            float sy = (float) (H / 2.0 - v[src + 1] * iz * pfy);
            st[o] = sx; st[o + 1] = sy; st[o + 2] = iz;
            for (int q = 3; q < VA; q++) st[o + q] = v[src + q];
            minX = Math.min(minX, sx); maxX = Math.max(maxX, sx); minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
            o += VA;
        }
        if (maxX < 0 || minX > W || maxY < 0 || minY > H) return;
        // Kleiner als ein Pixel und keinen Pixelmittelpunkt getroffen: gar nicht erst eintragen
        if (maxX - minX < 1 && maxY - minY < 1
                && Math.floor(minX - 0.5f) == Math.floor(maxX - 0.5f) && Math.floor(minY - 0.5f) == Math.floor(maxY - 0.5f)) return;
        pt.sm[nst] = (byte) m;
        int rp = rowsPerStrip;
        pt.lo[nst] = Math.max(0, Math.min(strips - 1, (int) Math.floor((minY - 0.5f) / rp)));
        pt.hi[nst] = Math.max(0, Math.min(strips - 1, (int) Math.floor((maxY + 0.5f) / rp)));
        pt.n = nst + 1;
    }

    // ------------------------------------------------------------ Rasterung

    /** Starttiefe je Pixel: unendlich (Himmel); das Gelände ist Teil des Netzes. */
    private void clearStrip(int y0, int y1) {
        // Bei vielen Kernen gibt es mehr Streifen als Zeilen: die letzten sind leer
        if (y0 >= y1) return;
        java.util.Arrays.fill(gz, y0 * W, y1 * W, Float.MAX_VALUE);
        java.util.Arrays.fill(gm, y0 * W, y1 * W, (byte) 0);
    }

    private void rasterStrip(int strip, int y0, int y1) {
        final int S = 3 * VA;
        final int mask = (1 << PART_BITS) - 1;
        for (int k = binStart[strip], ke = binStart[strip + 1]; k < ke; k++) {
            int code = binData[k];
            Part pt = parts[code >>> PART_BITS];
            int t = code & mask;
            final float[] st = pt.st;
            final byte[] sm = pt.sm;
            int o = S * t;
            int o1 = o + VA, o2 = o + 2 * VA;
            float x0 = st[o], yy0 = st[o + 1], x1 = st[o1], yy1 = st[o1 + 1], x2 = st[o2], yy2 = st[o2 + 1];
            float minY = Math.min(yy0, Math.min(yy1, yy2)), maxY = Math.max(yy0, Math.max(yy1, yy2));
            if (maxY < y0 - 0.5f || minY > y1 + 0.5f) continue;
            float area = (x1 - x0) * (yy2 - yy0) - (x2 - x0) * (yy1 - yy0);
            if (Math.abs(area) < 1e-9f) continue;
            float inv = 1f / area;
            int ya = Math.max(y0, (int) Math.ceil(minY - 0.5f)), yb = Math.min(y1 - 1, (int) Math.floor(maxY - 0.5f));
            int xa0 = Math.max(0, (int) Math.ceil(Math.min(x0, Math.min(x1, x2)) - 0.5f));
            int xb0 = Math.min(W - 1, (int) Math.floor(Math.max(x0, Math.max(x1, x2)) - 0.5f));
            if (xa0 > xb0 || ya > yb) continue;
            float iz0 = st[o + 2], iz1 = st[o1 + 2], iz2 = st[o2 + 2];
            byte m = (byte) (sm[t] + 1);
            float B0 = -(yy2 - yy1) * inv, B1 = -(yy0 - yy2) * inv, B2 = -(yy1 - yy0) * inv;
            for (int py = ya; py <= yb; py++) {
                float cy = py + 0.5f;
                float A0 = ((x2 - x1) * (cy - yy1) + (yy2 - yy1) * x1) * inv;
                float A1 = ((x0 - x2) * (cy - yy2) + (yy0 - yy2) * x2) * inv;
                float A2 = ((x1 - x0) * (cy - yy0) + (yy1 - yy0) * x0) * inv;
                long span = Span.of(A0, B0, A1, B1, A2, B2, xa0 + 0.5f, xb0 + 0.5f);
                if (span == Span.EMPTY) continue;
                int xa = Math.max(xa0, Span.lo(span)), xb = Math.min(xb0, Span.hi(span));
                int row = py * W;
                for (int px = xa; px <= xb; px++) {
                    float cx = px + 0.5f;
                    float w0 = A0 + B0 * cx, w1 = A1 + B1 * cx, w2 = A2 + B2 * cx;
                    float iz = w0 * iz0 + w1 * iz1 + w2 * iz2;
                    if (iz <= 0) continue;
                    float z = 1f / iz;
                    int p = row + px;
                    if (z >= gz[p]) continue;
                    gz[p] = z;
                    float q0 = w0 * iz0 * z, q1 = w1 * iz1 * z, q2 = w2 * iz2 * z;
                    gnx[p] = q0 * st[o + 3] + q1 * st[o1 + 3] + q2 * st[o2 + 3];
                    gny[p] = q0 * st[o + 4] + q1 * st[o1 + 4] + q2 * st[o2 + 4];
                    gnz[p] = q0 * st[o + 5] + q1 * st[o1 + 5] + q2 * st[o2 + 5];
                    gsk[p] = q0 * st[o + 6] + q1 * st[o1 + 6] + q2 * st[o2 + 6];
                    gm[p] = m;
                }
            }
        }
    }

    // ------------------------------------------------------------ Beleuchtung je Pixel

    /** Prüft, ob der Zwischenspeicher fürs Licht noch gilt, und merkt sich den Stand. */
    private void checkCache() {
        double[] k = {W, H, ex, ey, ez, cfx, cfy, cfz, crx, cry, crz, cux, cuy, cuz, pfx, pfy,
                Thermal.snow * 50, Thermal.season * 50, Thermal.rime * 50, Thermal.ambient * 2, Thermal.generation};
        // Zwischenspeicher nur bis gut 4 Millionen Bildpunkte (Standbilder in 4K rechnen ohne ihn)
        int n = W * H;
        keep = shadeCache && n <= 4_500_000;
        if (keep && kz.length < n) {
            int c = n + n / 8;
            kz = new float[c]; km = new byte[c]; kr = new float[c]; kg = new float[c]; kb = new float[c];
            kfilm = new float[c]; klit = new float[c]; klt = new float[c];
            khr = new float[c]; khg = new float[c]; khb = new float[c]; kk = new byte[c];
            cacheL = null;
        }
        boolean same = keep && L == cacheL && lightGen == cacheLightGen;
        if (k[0] != cacheKey[0] || k[1] != cacheKey[1] || k[14] != cacheKey[14] || k[15] != cacheKey[15] || k[20] != cacheKey[20]) same = false;
        // Kamera: winzige Reste der Dämpfung zählen nicht als Bewegung
        for (int i = 2; same && i < 14; i++) if (Math.abs(k[i] - cacheKey[i]) > 1e-5) same = false;
        // Klima: kleine Schritte (die Luft wird mit der Uhr langsam wärmer) lösen nichts aus
        for (int i = 16; same && i < 20; i++) if (Math.abs(k[i] - cacheKey[i]) > 0.5) same = false;
        if (!same) {
            System.arraycopy(k, 0, cacheKey, 0, 21);
            cacheL = L;
            cacheLightGen = lightGen;
        }
        cacheOk = same;
    }

    private void shadeStrip(int y0, int y1) {
        float[] o = new float[3], hz = new float[3], alb = new float[3], nb = new float[3], gmb = new float[5], il3 = new float[3];
        final com.dan.talsperre.effects.Lamps lp = lamps;
        final boolean lampLit = lp != null && lampOn > 0.01f;
        final LightingEngine li = L;
        final Sky s = sky;
        float lx = (float) s.sun[0], ly = (float) s.sun[1], lz = (float) s.sun[2];
        boolean sunUp = s.sunR + s.sunG + s.sunB > 0.001f;
        float air = AIR0 + AIR1 * s.haze;
        final boolean useCache = cacheOk, kp = keep;
        int hits = 0, elig = 0;
        for (int py = y0; py < y1; py++) {
            double b = (H / 2.0 - py - 0.5) / pfy;
            for (int px = 0; px < W; px++) {
                int p = py * W + px;
                double a = colA[px];
                float dx = (float) (cfx + crx * a + cux * b), dy = (float) (cfy + cry * a + cuy * b), dz = (float) (cfz + crz * a + cuz * b);
                float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                float ndx = dx / dl, ndy = dy / dl, ndz = dz / dl;
                int code = gm[p] - 1;
                float z = gz[p];
                int kv = useCache ? kk[p] : 0;
                if (code < 0) {
                    if (kp) km[p] = -1;
                    elig++;
                    if (kv == 2) { hr[p] = khr[p]; hg[p] = khg[p]; hb[p] = khb[p]; hits++; continue; }
                    s.radiance(ndx, ndy, ndz, o);
                    hr[p] = o[0]; hg[p] = o[1]; hb[p] = o[2];
                    if (kp) { khr[p] = o[0]; khg[p] = o[1]; khb[p] = o[2]; kk[p] = 2; }
                    continue;
                }
                int m = code;
                if (Mat.water(m)) { if (kp) { km[p] = -1; kk[p] = 0; } continue; }
                boolean hit = useCache && km[p] == code && Math.abs(kz[p] - z) <= z * 2e-4f;
                elig++;
                if (hit) hits++;
                float wx = (float) (ex + dx * z), wy = (float) (ey + dy * z), wz = (float) (ez + dz * z);
                float nx = gnx[p], ny = gny[p], nz = gnz[p];
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nl < 1e-9f) { nx = 0; ny = 1; nz = 0; } else { nx /= nl; ny /= nl; nz /= nl; }
                float vxv = -ndx, vyv = -ndy, vzv = -ndz;
                if (nx * vxv + ny * vyv + nz * vzv < 0) { nx = -nx; ny = -ny; nz = -nz; }
                float dist = z * dl;
                nb[0] = nx; nb[1] = ny; nb[2] = nz;
                // Pixelgröße auf der Fläche: unter flachem Blick deutlich größer (sonst Moiré)
                float facing = Math.max(0.12f, nx * vxv + ny * vyv + nz * vzv);
                float ao;
                if (hit && m == Mat.TERRAIN) {
                    alb[0] = kr[p]; alb[1] = kg[p]; alb[2] = kb[p];
                    ao = 1;
                } else {
                    ao = Materials.surface(m, wx, wy, wz, nb, (float) (dist / pfy) / facing, gmb, alb);
                    if (kp && m == Mat.TERRAIN) { kr[p] = alb[0]; kg[p] = alb[1]; kb[p] = alb[2]; }
                }
                // Nässe: Gischt der Ausbrüche und der warme Film im Abfluss machen dunkler und glänzend
                float wet = 0;
                if (Mat.wettable(m)) {
                    Wetness.Set ws = wetness;
                    if (ws != null) wet = ws.at(wx, wz);
                    wet = Math.max(wet, rainWet);
                    Thermal th = thermal;
                    if (th != null && m != Mat.BOARD) {
                        float fl;
                        if (hit) fl = kfilm[p];
                        else { fl = th.film(wx, wz); if (kp) kfilm[p] = fl; }
                        wet = Math.max(wet, fl * 0.75f);
                    }
                    if (wet > 0) {
                        float k = 1 - 0.42f * wet;
                        alb[0] *= k; alb[1] *= k; alb[2] *= k;
                        // der Film glättet die Oberfläche
                        nb[0] *= 1 - 0.6f * wet; nb[2] *= 1 - 0.6f * wet; nb[1] += (1 - nb[1]) * 0.6f * wet * Math.max(0, nb[1]);
                        float l2 = (float) Math.sqrt(nb[0] * nb[0] + nb[1] * nb[1] + nb[2] * nb[2]);
                        nb[0] /= l2; nb[1] /= l2; nb[2] /= l2;
                    }
                }
                nx = nb[0]; ny = nb[1]; nz = nb[2];
                float skyv = Math.max(0, Math.min(1, gsk[p]));
                float ndl = nx * lx + ny * ly + nz * lz;
                float lit = 0;
                if (sunUp && ndl > 0) {
                    if (hit) lit = klit[p];
                    else {
                        float sinT = (float) Math.sqrt(Math.max(0, 1 - ndl * ndl));
                        lit = li.lit(wx + nx * 0.04, wy + ny * 0.04, wz + nz * 0.04, sinT / Math.max(ndl, 0.05f), dist < 350);
                    }
                }
                float dirK = Math.max(0, ndl) * lit;
                float tr = Materials.TRANS[m];
                float lt = 0;
                if (tr > 0 && sunUp && ndl < 0) {
                    // Gegenlicht durch Nadeln und Gras
                    lt = hit ? klt[p] : li.lit(wx - nx * 0.05, wy - ny * 0.05, wz - nz * 0.05, 1.0, false);
                    dirK += -ndl * lt * tr;
                }
                if (!hit && kp) { klit[p] = lit; klt[p] = lt; kz[p] = z; km[p] = (byte) code; }
                float ar, ag, ab;
                if (ny >= 0) { ar = s.sideR + (s.upR - s.sideR) * ny; ag = s.sideG + (s.upG - s.sideG) * ny; ab = s.sideB + (s.upB - s.sideB) * ny; }
                else { ar = s.sideR + (s.downR - s.sideR) * -ny; ag = s.sideG + (s.downG - s.sideG) * -ny; ab = s.sideB + (s.downB - s.sideB) * -ny; }
                // Lichtrückwurf vom sonnigen Sinter auf senkrechte und überhängende Flächen
                float gb = (1 - Math.max(0, ny)) * 0.5f * Math.max(0, ly) * 0.3f;
                float occ = skyv * skyv * (3 - 2 * skyv);
                float gbo = 0.25f + 0.75f * occ;
                ar = (ar * occ + s.sunR * gb * 0.5f * gbo) * ao; ag = (ag * occ + s.sunG * gb * 0.45f * gbo) * ao; ab = (ab * occ + s.sunB * gb * 0.38f * gbo) * ao;
                float r = alb[0] * (s.sunR * dirK + ar);
                float g = alb[1] * (s.sunG * dirK + ag);
                float bl = alb[2] * (s.sunB * dirK + ab);
                if (lampLit && dist < 420) {
                    il3[0] = il3[1] = il3[2] = 0;
                    lp.illuminate(wx, wy, wz, nx, ny, nz, il3);
                    r += alb[0] * il3[0]; g += alb[1] * il3[1]; bl += alb[2] * il3[2];
                }
                if (m == Mat.GLASS && lampLit) {
                    float ge = Materials.glassGlow(wx, wy, wz) * lampOn;
                    r += 1.5f * ge; g += 1.05f * ge; bl += 0.5f * ge;
                }
                float spec = Math.max(Materials.SPEC[m], 0.32f * wet);
                if (spec > 0) {
                    float hx = lx + vxv, hy = ly + vyv, hzz = lz + vzv;
                    float hl = (float) Math.sqrt(hx * hx + hy * hy + hzz * hzz);
                    float ndh = (nx * hx + ny * hy + nz * hzz) / hl;
                    float shin = Math.max(Materials.SHIN[m], wet > 0.05f ? 120 : 0);
                    if (dirK > 0 && ndh > 0) {
                        float sp = (float) Math.pow(ndh, shin) * (shin + 8) / 25.13f * spec * dirK;
                        r += s.sunR * sp; g += s.sunG * sp; bl += s.sunB * sp;
                    }
                    // Spiegelung des Himmels auf glatten, nassen Flächen
                    float nv = nx * vxv + ny * vyv + nz * vzv;
                    float cosV = Math.max(0, nv), f1 = 1 - cosV, f5 = f1 * f1; f5 = f5 * f5 * f1;
                    float fr = spec * (0.25f + 0.75f * f5);
                    float rdx = 2 * nv * nx - vxv, rdy = 2 * nv * ny - vyv, rdz = 2 * nv * nz - vzv;
                    s.radiance(rdx, Math.max(rdy, 0.02f), rdz, hz);
                    r += hz[0] * fr * occ; g += hz[1] * fr * occ; bl += hz[2] * fr * occ;
                }
                // Luftperspektive
                float f = 1 - Noise.expNeg(dist * air);
                if (f > 0.002f) {
                    if (kv == 1) { hz[0] = khr[p]; hz[1] = khg[p]; hz[2] = khb[p]; }
                    else {
                        s.haze(ndx, ndy, ndz, hz);
                        if (kp) { khr[p] = hz[0]; khg[p] = hz[1]; khb[p] = hz[2]; kk[p] = 1; }
                    }
                    r += (hz[0] - r) * f; g += (hz[1] - g) * f; bl += (hz[2] - bl) * f;
                } else if (kp) kk[p] = 0;
                hr[p] = r; hg[p] = g; hb[p] = bl;
            }
        }
        if (hits > 0) hitAcc.addAndGet(hits);
        eligAcc.addAndGet(elig);
    }

    private final java.util.concurrent.atomic.AtomicLong hitAcc = new java.util.concurrent.atomic.AtomicLong(), eligAcc = new java.util.concurrent.atomic.AtomicLong();

    /** Luftperspektive: Dämpfung je Meter bei klarer Luft und zusätzlich je Anteil Dunst. */
    static final float AIR0 = 0.00004f, AIR1 = 0.0004f;

    // ------------------------------------------------------------ Effekte

    private static double smoothD(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /**
     * Dunst nach Tageszeit: im Becken dampft es an kalten Morgen am stärksten, bis die Sonne die
     * Luft erwärmt; am Abend und nachts wieder etwas.
     */
    public static double mistAmount(double h) {
        double morning = smoothD(4.0, 6.5, h) * (1 - smoothD(7.5, 10.5, h));
        double evening = smoothD(18.5, 21.0, h) * 0.4;
        double night = (h < 4.5 || h > 21) ? 0.35 : 0;
        return Math.max(morning, Math.max(evening, night));
    }

    /** Dichte der streuenden Luft (je Meter) über dem Grundwert: Bodendunst und Dampf über dem Fluss. */
    private float density(float x, float y, float z, float t, float base) {
        float[] ab = new float[2];
        groundMist(x, y, z, ab);
        return density(x, y, z, t, base, ab[0], ab[1]);
    }

    /**
     * Der Teil des Bodendunsts, der nur vom Ort abhängt (Höhe über Boden, Nähe zum Fluss): ab[0] für
     * den Dunst, ab[1] für den Dampf über dem Firehole. Bei ruhender Kamera wird er je Schritt behalten.
     */
    private void groundMist(float x, float y, float z, float[] ab) {
        ab[0] = 0; ab[1] = 0;
        float gnd = terrain.sample(x, z);
        // Über dem See liegt der Nebel auf dem Wasser (Pegel), nicht auf dem Grund des Sees
        float lv = mesh.lakeDy;
        float qz = z + 300f;
        boolean lake = gnd < lv && qz > 0 && x * x + qz * qz >= 298f * 298f;
        float gy = y - (lake ? lv : gnd);
        float lm = lake ? 2.6f * (float) lakeMist : 1;
        if (gy < 70) {
            ab[0] = 0.0012f * lm * (float) Math.exp(-gy / (lake ? 10.0 : 8.0));
            if (gy < 25) {
                // Die Eder kommt aus der Tiefe des Sees und ist im Herbst wärmer als die Morgenluft: sie dampft
                float rd = terrain.riverDist(x, z);
                if (rd < 70) ab[1] = 0.0055f * (float) Math.exp(-gy / 5.0) * (1 - smooth(-5, 70, rd));
            }
        }
    }

    private float density(float x, float y, float z, float t, float base, float mA, float mB) {
        float d = base * Math.max(0.2f, 1 - y / 800f);
        float m = mistNow;
        if (m > 0.01f && (mA > 0 || mB > 0)) {
            float n = Noise.tex(x * 0.012f + t * 0.015f, z * 0.012f - t * 0.01f);
            float n2 = Noise.tex(x * 0.0035f - t * 0.012f * (float) windX, z * 0.0035f + t * 0.012f * (float) windZ);
            d += m * mA * (0.45f + 0.7f * n) * (0.4f + 1.2f * n2);
            if (mB > 0) d += (0.03f + m) * mB * (0.5f + n);
        }
        // Säulen der Ausbrüche: dichte Gischt, in der sich die Sonne fängt
        float[][] pl = plumes;
        for (int k = 0; k < pl.length; k++) {
            float[] q = pl[k];
            if (y < q[2] - 2 || y > q[3] + 12) continue;
            float hh = y - q[2];
            float ax = q[0] + q[6] * hh, az = q[1] + q[7] * hh;
            float ddx = x - ax, ddz = z - az;
            float r = q[4] * (0.6f + 0.9f * hh / Math.max(1, q[3] - q[2]));
            float d2 = (ddx * ddx + ddz * ddz) / (r * r);
            if (d2 < 5) d += q[5] * Noise.expNeg(d2) * (1 - smooth(q[3] - 4, q[3] + 12, y));
        }
        return d;
    }

    /** Lichtstrahlen und Nebel: Marsch entlang des Sehstrahls in Viertelauflösung, mit Schattenkarte. */
    /** Sonnenanteil je Schritt der Lichtstrahlen, bei ruhender Kamera übernommen (wie beim Licht). */
    private float[] mlit = new float[0], mkz = new float[0], mA = new float[0], mB = new float[0];
    private byte[] mji = new byte[0];

    private void marchStrip(int y0, int y1) {
        final LightingEngine li = L;
        final boolean useCache = cacheOk;
        final Sky s = sky;
        final float[] mab = new float[2], lc = new float[3];
        final com.dan.talsperre.effects.Lamps lp = lamps;
        final boolean lampLit = lp != null && lampOn > 0.01f;
        final float rg = rayGain;
        float lx = (float) s.sun[0], ly = (float) s.sun[1], lz = (float) s.sun[2];
        boolean lightUp = s.sunR + s.sunG + s.sunB > 1e-4f && li.fine.valid;
        float g = 0.62f, g2 = g * g;
        float base = 0.00007f * (1 + 2.5f * s.haze);
        float t = time;
        for (int qy = y0; qy < y1; qy++) {
            int py = Math.min(H - 1, qy * MF + MF / 2);
            double b = (H / 2.0 - py - 0.5) / pfy;
            for (int qx = 0; qx < MW; qx++) {
                int px = Math.min(W - 1, qx * MF + MF / 2);
                int p = py * W + px, q = qy * MW + qx;
                double a = colA[px];
                float dx = (float) (cfx + crx * a + cux * b), dy = (float) (cfy + cry * a + cuy * b), dz = (float) (cfz + crz * a + cuz * b);
                float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                float ndx = dx / dl, ndy = dy / dl, ndz = dz / dl;
                boolean skyPix = gz[p] >= Float.MAX_VALUE;
                float depth = skyPix ? MAX_DIST : Math.min(MAX_DIST, gz[p] * dl);
                md[q] = skyPix ? Float.MAX_VALUE : gz[p];
                float mu = ndx * lx + ndy * ly + ndz * lz;
                float phase = (1 - g2) / (float) Math.pow(1 + g2 - 2 * g * mu, 1.5) * 0.35f + 0.3f;
                // Bei ruhender Kamera fester Versatz, damit der Sonnenanteil je Schritt gleich bleibt
                int ji = useCache ? 8 : frame & 7;
                float jit = ((Noise.hash(qx, qy, ji) & 1023) / 1024f);
                boolean hit = useCache && mkz[q] == md[q] && mji[q] == ji;
                mkz[q] = md[q]; mji[q] = (byte) ji;
                int mo = q * STEPS;
                float T = 1, sr = 0, sg = 0, sbb = 0, prev = 0;
                for (int i = 0; i < STEPS; i++) {
                    float u = (i + jit) / STEPS;
                    float tpos = depth * u * u;
                    float ds = tpos - prev;
                    prev = tpos;
                    float wx = (float) (ex + ndx * tpos), wy = (float) (ey + ndy * tpos), wz = (float) (ez + ndz * tpos);
                    if (wy < -60) { if (!hit) for (int k2 = i; k2 < STEPS; k2++) mlit[mo + k2] = 0; break; }
                    float lit;
                    if (hit) lit = mlit[mo + i];
                    else {
                        mlit[mo + i] = lit = lightUp ? li.lit(wx, wy, wz, 0.2, false) : 0;
                        groundMist(wx, wy, wz, mab);
                        mA[mo + i] = mab[0]; mB[mo + i] = mab[1];
                    }
                    float d = density(wx, wy, wz, t, base, mA[mo + i], mB[mo + i]);
                    float k = T * d * ds;
                    float sl = lit * phase * rg;
                    sr += k * (s.sunR * sl + (s.upR + s.sideR) * 0.35f);
                    sg += k * (s.sunG * sl + (s.upG + s.sideG) * 0.35f);
                    sbb += k * (s.sunB * sl + (s.upB + s.sideB) * 0.35f);
                    if (lampLit) {
                        lc[0] = lc[1] = lc[2] = 0;
                        lp.scatter(wx, wy, wz, lc);
                        sr += k * lc[0]; sg += k * lc[1]; sbb += k * lc[2];
                    }
                    T *= Noise.expNeg(d * ds);
                }
                mr[q] = sr; mg[q] = sg; mb[q] = sbb; mt[q] = T;
            }
        }
    }

    /** Glättet das Streulicht, tiefenbewusst (aus Caracalla). */
    private void blurQuarter() {
        for (int pass = 0; pass < 2; pass++) {
            final int ps = pass;
            float[] ir = pass == 0 ? mr : mtr, ig = pass == 0 ? mg : mtg, ib = pass == 0 ? mb : mtb;
            float[] or = pass == 0 ? mtr : mr, og = pass == 0 ? mtg : mg, ob = pass == 0 ? mtb : mb;
            IntStream.range(0, MH).parallel().forEach(y -> {
                for (int x = 0; x < MW; x++) {
                    float r = 0, g = 0, b = 0, w = 0;
                    float d0 = md[y * MW + x];
                    for (int k = -1; k <= 1; k++) {
                        int xx = ps == 0 ? x + k : x, yy = ps == 0 ? y : y + k;
                        if (xx < 0 || yy < 0 || xx >= MW || yy >= MH) continue;
                        int i = yy * MW + xx;
                        float dd = md[i];
                        float wk = (k == 0 ? 2 : 1) * (Math.abs(dd - d0) < 0.1f * Math.min(dd, d0) + 0.5f || (dd > 1e30f && d0 > 1e30f) ? 1f : 0.05f);
                        r += ir[i] * wk; g += ig[i] * wk; b += ib[i] * wk; w += wk;
                    }
                    int o = y * MW + x;
                    or[o] = r / w; og[o] = g / w; ob[o] = b / w;
                }
            });
        }
    }

    /** Spaltentabellen fürs Hochskalieren (Streulicht 1/MF, Überstrahlen 1/4); je Bild einmal gefüllt. */
    private int[] upX0 = new int[0], upX1 = new int[0], bqX0 = new int[0], bqX1 = new int[0];
    private float[] upTX = new float[0], bqTX = new float[0];

    private void upTables() {
        if (upX0.length < W) {
            upX0 = new int[W]; upX1 = new int[W]; upTX = new float[W];
            bqX0 = new int[W]; bqX1 = new int[W]; bqTX = new float[W];
        }
        for (int px = 0; px < W; px++) {
            float fx = (px + 0.5f) / MF - 0.5f;
            int q0 = Math.max(0, Math.min(MW - 1, (int) Math.floor(fx)));
            upX0[px] = q0; upX1[px] = Math.min(MW - 1, q0 + 1); upTX[px] = Math.max(0, Math.min(1, fx - q0));
            float bx = (px + 0.5f) / 4f - 0.5f;
            int b0 = Math.max(0, Math.min(QW - 1, (int) Math.floor(bx)));
            bqX0[px] = b0; bqX1[px] = Math.min(QW - 1, b0 + 1); bqTX[px] = Math.max(0, Math.min(1, bx - b0));
        }
    }

    /**
     * Streulicht tiefenbewusst hochskalieren und ins Bild geben (aus Caracalla). Schneller Weg:
     * liegen alle vier Nachbarn in derselben Tiefe wie das Pixel (der Normalfall), reicht die
     * einfache bilineare Mischung ohne Gewichte je Nachbar.
     */
    private void applyScatter(int y0, int y1) {
        final int[] X0 = upX0, X1 = upX1;
        final float[] TX = upTX;
        for (int py = y0; py < y1; py++) {
            float fy = (py + 0.5f) / MF - 0.5f;
            int qy0 = Math.max(0, Math.min(MH - 1, (int) Math.floor(fy))), qy1 = Math.min(MH - 1, qy0 + 1);
            float ty = Math.max(0, Math.min(1, fy - qy0));
            int r0 = qy0 * MW, r1 = qy1 * MW;
            for (int px = 0; px < W; px++) {
                int p = py * W + px;
                float tx = TX[px];
                int ia = r0 + X0[px], ib = r0 + X1[px], ic = r1 + X0[px], id = r1 + X1[px];
                float z = gz[p];
                float da = md[ia], db = md[ib], dc = md[ic], dd = md[id];
                float r, g, b, T;
                boolean same;
                if (z > 1e30f) same = da > 1e30f && db > 1e30f && dc > 1e30f && dd > 1e30f;
                else {
                    float tol = 0.02f * z;
                    same = Math.abs(da - z) < tol && Math.abs(db - z) < tol && Math.abs(dc - z) < tol && Math.abs(dd - z) < tol;
                }
                if (same) {
                    float w0 = (1 - tx) * (1 - ty), w1 = tx * (1 - ty), w2 = (1 - tx) * ty, w3 = tx * ty;
                    r = mr[ia] * w0 + mr[ib] * w1 + mr[ic] * w2 + mr[id] * w3;
                    g = mg[ia] * w0 + mg[ib] * w1 + mg[ic] * w2 + mg[id] * w3;
                    b = mb[ia] * w0 + mb[ib] * w1 + mb[ic] * w2 + mb[id] * w3;
                    T = mt[ia] * w0 + mt[ib] * w1 + mt[ic] * w2 + mt[id] * w3;
                } else {
                    float w0 = (1 - tx) * (1 - ty) * sim(da, z) + 1e-4f, w1 = tx * (1 - ty) * sim(db, z) + 1e-4f;
                    float w2 = (1 - tx) * ty * sim(dc, z) + 1e-4f, w3 = tx * ty * sim(dd, z) + 1e-4f;
                    float iw = 1 / (w0 + w1 + w2 + w3);
                    r = (mr[ia] * w0 + mr[ib] * w1 + mr[ic] * w2 + mr[id] * w3) * iw;
                    g = (mg[ia] * w0 + mg[ib] * w1 + mg[ic] * w2 + mg[id] * w3) * iw;
                    b = (mb[ia] * w0 + mb[ib] * w1 + mb[ic] * w2 + mb[id] * w3) * iw;
                    T = (mt[ia] * w0 + mt[ib] * w1 + mt[ic] * w2 + mt[id] * w3) * iw;
                }
                hr[p] = hr[p] * T + r; hg[p] = hg[p] * T + g; hb[p] = hb[p] * T + b;
            }
        }
    }

    private static float sim(float dd, float z) {
        if (dd > 1e30f && z > 1e30f) return 1;
        if (dd > 1e30f || z > 1e30f) return 0.02f;
        return 1f / (1 + 20 * Math.abs(dd - z) / Math.min(dd, z));
    }

    /** Sterne als Lichtpunkte auf Himmelspixeln, mit Funkeln und Dämpfung am Horizont. */
    private void drawStars() {
        com.dan.talsperre.effects.NightSky ns = stars;
        ns.update(sidereal);
        float nk = sky.night * sky.night * (1 - 0.75f * sky.haze) * (1 - 0.8f * sky.moonLit * (sky.moon[1] > 0 ? 1 : 0) * 0.5f);
        for (int i = 0; i < com.dan.talsperre.effects.NightSky.N; i++) {
            float dx = ns.dx[i], dy = ns.dy[i], dz = ns.dz[i];
            if (dy < 0.01f) continue;
            double vz = dx * cfx + dy * cfy + dz * cfz;
            if (vz < 0.05) continue;
            double sx = W / 2.0 + (dx * crx + dy * cry + dz * crz) / vz * pfx - 0.5;
            double sy = H / 2.0 - (dx * cux + dy * cuy + dz * cuz) / vz * pfy - 0.5;
            if (sx < 0 || sy < 0 || sx >= W - 1 || sy >= H - 1) continue;
            int ix = (int) sx, iy = (int) sy;
            float fx = (float) (sx - ix), fy = (float) (sy - iy);
            float tw = 0.75f + 0.25f * (float) Math.sin(time * (3 + ns.ph[i]) + ns.ph[i] * 7);
            float br = ns.mag[i] * nk * tw * smooth(0.01f, 0.2f, dy) * 1.6f;
            float tint = ns.tint[i];
            float cr0 = br * (0.85f + 0.3f * tint), cg0 = br * 0.95f, cb0 = br * (1.15f - 0.3f * tint);
            for (int k = 0; k < 4; k++) {
                int xx = ix + (k & 1), yy = iy + (k >> 1);
                int p = yy * W + xx;
                if (gz[p] < Float.MAX_VALUE) continue;
                float w = ((k & 1) == 0 ? 1 - fx : fx) * ((k >> 1) == 0 ? 1 - fy : fy);
                hr[p] += cr0 * w; hg[p] += cg0 * w; hb[p] += cb0 * w;
            }
        }
    }

    /** Kleine Dinge, die vor jedem Bild neu gefüllt werden (später Tiere, Leuchtpunkte). */
    public final Sprites sprites = new Sprites();

    /** Projektion eines Weltpunkts mit der Kamera des letzten Bildes: x, y in Bildpixeln und Tiefe; null hinter der Kamera. */
    public double[] project(double x, double y, double z) {
        double ddx = x - ex, ddy = y - ey, ddz = z - ez;
        double vz = ddx * cfx + ddy * cfy + ddz * cfz;
        if (vz < 0.3) return null;
        return new double[]{W / 2.0 + (ddx * crx + ddy * cry + ddz * crz) / vz * pfx, H / 2.0 - (ddx * cux + ddy * cuy + ddz * cuz) / vz * pfy, vz};
    }

    /** Projektion einer Richtung (unendlich fern, etwa ein Stern); null hinter der Kamera. */
    public double[] projectDir(double dx, double dy, double dz) {
        double vz = dx * cfx + dy * cfy + dz * cfz;
        if (vz < 0.05) return null;
        return new double[]{W / 2.0 + (dx * crx + dy * cry + dz * crz) / vz * pfx, H / 2.0 - (dx * cux + dy * cuy + dz * cuz) / vz * pfy};
    }

    /** Tiefe im letzten Bild an einem Pixel (Float.MAX_VALUE = Himmel). */
    public float depthAt(int px, int py) {
        if (px < 0 || py < 0 || px >= W || py >= H) return Float.MAX_VALUE;
        return gz[py * W + px];
    }

    /** Leuchtpunkte und Lichthöfe der Lampen; der Hof wächst mit dem Dunst. */
    private void fillLampSprites() {
        final com.dan.talsperre.effects.Lamps lp = lamps;
        lampSprites.clear();
        float mist = 1 + 2.5f * mistNow + 3 * sky.haze;
        for (int i = 0; i < lp.n; i++) {
            double dx = lp.x[i] - ex, dy = lp.y[i] - ey, dz = lp.z[i] - ez;
            if (dx * dx + dy * dy + dz * dz > 1500.0 * 1500.0) continue;
            lampSprites.add(Sprites.GLOW, lp.x[i], lp.y[i], lp.z[i], 0.45, lp.r[i] * 5, lp.g[i] * 5, lp.b[i] * 5, lampOn);
            lampSprites.add(Sprites.GLOW, lp.x[i], lp.y[i], lp.z[i], 3.2, lp.r[i] * 0.35, lp.g[i] * 0.35, lp.b[i] * 0.35, 0.10f * mist * lampOn);
        }
    }

    private void drawSprites(final Sprites sp) {
        for (int i = 0; i < sp.n; i++) {
            double ddx = sp.x[i] - ex, ddy = sp.y[i] - ey, ddz = sp.z[i] - ez;
            double vz = ddx * cfx + ddy * cfy + ddz * cfz;
            if (vz < 0.3 || vz > 3000) continue;
            double sx = W / 2.0 + (ddx * crx + ddy * cry + ddz * crz) / vz * pfx;
            double sy = H / 2.0 - (ddx * cux + ddy * cuy + ddz * cuz) / vz * pfy;
            float rad = (float) (sp.size[i] * pfy / vz);
            byte k = sp.kind[i];
            if (k == Sprites.BIRD) {
                if (sx < -20 || sy < -20 || sx > W + 20 || sy > H + 20) continue;
                double span = sp.size[i] * 0.5, fl = sp.flap[i];
                double sxw = -sp.hz[i] * span, szw = sp.hx[i] * span, up = fl * span * 0.55;
                double[] l = project(sp.x[i] + sxw, sp.y[i] + up, sp.z[i] + szw), rr = project(sp.x[i] - sxw, sp.y[i] + up, sp.z[i] - szw);
                if (l == null || rr == null) continue;
                float al = sp.a[i];
                line(l[0], l[1], sx, sy, (float) vz, sp.r[i], sp.g[i], sp.b[i], al);
                line(sx, sy, rr[0], rr[1], (float) vz, sp.r[i], sp.g[i], sp.b[i], al);
                continue;
            }
            float glowMin = k == Sprites.GLOW ? 0.9f : 0.6f;
            if (rad < glowMin) rad = glowMin;
            if (rad > 60) rad = 60;
            if (sx < -rad || sy < -rad || sx > W + rad || sy > H + rad) continue;
            int xa = Math.max(0, (int) Math.floor(sx - rad)), xb = Math.min(W - 1, (int) Math.ceil(sx + rad));
            int ya = Math.max(0, (int) Math.floor(sy - rad)), yb = Math.min(H - 1, (int) Math.ceil(sy + rad));
            float r2 = rad * rad, fz = (float) vz, soft = Math.max(0.2f, fz * 0.01f);
            float cr0 = sp.r[i], cg0 = sp.g[i], cb0 = sp.b[i], al = sp.a[i];
            for (int yy = ya; yy <= yb; yy++) {
                float eyy = yy + 0.5f - (float) sy;
                for (int xx = xa; xx <= xb; xx++) {
                    float exx = xx + 0.5f - (float) sx;
                    float qd2 = (exx * exx + eyy * eyy) / r2;
                    if (qd2 >= 1) continue;
                    int p = yy * W + xx;
                    float zz = gz[p];
                    float sf = zz >= Float.MAX_VALUE ? 1 : Math.min(1, (zz - fz) / soft);
                    if (sf <= 0) continue;
                    float fall = (1 - qd2) * (1 - qd2);
                    if (k == Sprites.GLOW) {
                        float a = fall * sf * al;
                        hr[p] += cr0 * a; hg[p] += cg0 * a; hb[p] += cb0 * a;
                    } else {
                        float a = Math.min(1, fall * 1.6f) * sf * al;
                        hr[p] += (cr0 - hr[p]) * a; hg[p] += (cg0 - hg[p]) * a; hb[p] += (cb0 - hb[p]) * a;
                    }
                }
            }
        }
    }

    /** Blitz: Punkte des Kanals (x, y, z …), gerade im Bild; null = keiner. Helligkeit 0..1 für das Aufleuchten. */
    public volatile float[] bolt;
    public volatile float flash;
    /** Nässe vom Regen 0..1 überall am Boden. */
    public volatile float rainWet;

    /** Der Blitzkanal als helle, doppelte Linie mit Tiefenprüfung. */
    private void drawBolt(float[] b) {
        float k = (float) (6 / Math.max(1e-3, exposure));
        for (int i = 0; i + 5 < b.length; i += 3) {
            double[] p0 = project(b[i], b[i + 1], b[i + 2]), p1 = project(b[i + 3], b[i + 4], b[i + 5]);
            if (p0 == null || p1 == null) continue;
            float z = (float) Math.min(p0[2], p1[2]);
            for (int o = 0; o < 2; o++) line(p0[0] + o, p0[1], p1[0] + o, p1[1], z, k * 0.8f, k * 0.85f, k, 1);
        }
    }

    /** Dünne Linie mit Tiefenprüfung (für Vögel), deckend mit al. */
    private void line(double x0, double y0, double x1, double y1, float z, float cr, float cg, float cb, float al) {
        double dx = x1 - x0, dy = y1 - y0;
        int n = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))) + 1;
        if (n > 2000) return;
        for (int k = 0; k <= n; k++) {
            double t = k / (double) n;
            int px = (int) Math.floor(x0 + dx * t), py = (int) Math.floor(y0 + dy * t);
            if (px < 0 || py < 0 || px >= W || py >= H) continue;
            int p = py * W + px;
            if (gz[p] < z) continue;
            hr[p] += (cr - hr[p]) * al; hg[p] += (cg - hg[p]) * al; hb[p] += (cb - hb[p]) * al;
        }
    }

    /** Überstrahlen: helle Anteile in Viertelauflösung sammeln, breit weichzeichnen, dazugeben (aus Caracalla). */
    private void doBloom(int rowsPer, int qPer) {
        float e = (float) exposure;
        float thr = 1.0f;
        IntStream.range(0, strips).parallel().forEach(st -> {
            for (int qy = st * qPer; qy < Math.min(QH, (st + 1) * qPer); qy++) {
                for (int qx = 0; qx < QW; qx++) {
                    float r = 0, g = 0, b = 0;
                    int n = 0;
                    for (int yy = qy * 4; yy < Math.min(H, qy * 4 + 4); yy += 2) {
                        for (int xx = qx * 4 + ((yy >> 1) & 1); xx < Math.min(W, qx * 4 + 4); xx += 2) {
                            int p = yy * W + xx;
                            float l = (0.2126f * hr[p] + 0.7152f * hg[p] + 0.0722f * hb[p]) * e;
                            if (l > thr) {
                                float k = Math.min(l - thr, 30) / l;
                                r += hr[p] * k; g += hg[p] * k; b += hb[p] * k;
                            }
                            n++;
                        }
                    }
                    int q = qy * QW + qx;
                    tr[q] = r / n; tg[q] = g / n; tb[q] = b / n;
                }
            }
        });
        float[] kern = new float[13];
        float ks = 0;
        for (int i = -6; i <= 6; i++) { kern[i + 6] = (float) Math.exp(-i * i / 12.0); ks += kern[i + 6]; }
        for (int i = 0; i < 13; i++) kern[i] /= ks;
        gauss(tr, tg, tb, qr, qg, qb, kern, true);
        gauss(qr, qg, qb, tr, tg, tb, kern, false);
        float strength = 0.24f;
        IntStream.range(0, strips).parallel().forEach(st -> {
            for (int py = st * rowsPer; py < Math.min(H, (st + 1) * rowsPer); py++) {
                float fy = (py + 0.5f) / 4f - 0.5f;
                int y0 = Math.max(0, Math.min(QH - 1, (int) Math.floor(fy))), y1 = Math.min(QH - 1, y0 + 1);
                float ty = Math.max(0, Math.min(1, fy - y0));
                for (int px = 0; px < W; px++) {
                    int x0 = bqX0[px], x1 = bqX1[px];
                    float tx = bqTX[px];
                    int a = y0 * QW + x0, b = y0 * QW + x1, c = y1 * QW + x0, d = y1 * QW + x1;
                    float w0 = (1 - tx) * (1 - ty), w1 = tx * (1 - ty), w2 = (1 - tx) * ty, w3 = tx * ty;
                    int p = py * W + px;
                    hr[p] += (tr[a] * w0 + tr[b] * w1 + tr[c] * w2 + tr[d] * w3) * strength;
                    hg[p] += (tg[a] * w0 + tg[b] * w1 + tg[c] * w2 + tg[d] * w3) * strength;
                    hb[p] += (tb[a] * w0 + tb[b] * w1 + tb[c] * w2 + tb[d] * w3) * strength;
                }
            }
        });
    }

    private void gauss(float[] ir, float[] ig, float[] ib, float[] or, float[] og, float[] ob, float[] k, boolean horiz) {
        IntStream.range(0, QH).parallel().forEach(y -> {
            for (int x = 0; x < QW; x++) {
                float r = 0, g = 0, b = 0;
                for (int i = -6; i <= 6; i++) {
                    int xx = horiz ? Math.max(0, Math.min(QW - 1, x + i)) : x;
                    int yy = horiz ? y : Math.max(0, Math.min(QH - 1, y + i));
                    int sidx = yy * QW + xx;
                    float w = k[i + 6];
                    r += ir[sidx] * w; g += ig[sidx] * w; b += ib[sidx] * w;
                }
                int o = y * QW + x;
                or[o] = r; og[o] = g; ob[o] = b;
            }
        });
    }

    // ------------------------------------------------------------ Wasser

    /**
     * Zweiter Durchgang für Wasser: der Firehole River und (ab Phase 4) die Quellen. Wellen aus der
     * Rauschtabelle, die im Fluss mit der Strömung wandern; Spiegelung zuerst im fertigen Bild gesucht,
     * sonst der Himmel; Sonnenglitzern mit Schattenkarte. Der Firehole ist klar und flach: über hellem
     * Kies scheint der Grund grünlich braun durch.
     */
    private void shadeWaterStrip(int y0, int y1) {
        float[] sk = new float[3], ref = new float[3], hz = new float[3], gmb = new float[5], pc = new float[3], lw = new float[3], lc = new float[3];
        final com.dan.talsperre.effects.Lamps lp = lamps;
        final boolean lampLit = lp != null && lampOn > 0.01f;
        final com.dan.river.FlowField rfl = riverFlow;
        final com.dan.river.WaterSurface rsu = riverSurface;
        final com.dan.river.FlowField.Flow rf = new com.dan.river.FlowField.Flow();
        final com.dan.river.WaterSurface.Surf rsf = new com.dan.river.WaterSurface.Surf();
        final LightingEngine li = L;
        final Sky s = sky;
        float lx = (float) s.sun[0], ly = (float) s.sun[1], lz = (float) s.sun[2];
        boolean sunUp = s.sunR + s.sunG + s.sunB > 0.001f;
        float air = AIR0 + AIR1 * s.haze;
        float t = time;
        for (int py = y0; py < y1; py++) {
            double b = (H / 2.0 - py - 0.5) / pfy;
            int lastPx = -9;
            float lastWx = 0, lastWz = 0;
            boolean lastOk = false;
            for (int px = 0; px < W; px++) {
                int p = py * W + px;
                int code = gm[p] - 1;
                if (!Mat.water(code)) continue;
                boolean river = code == Mat.RIVER;
                double a = colA[px];
                float dx = (float) (cfx + crx * a + cux * b), dy = (float) (cfy + cry * a + cuy * b), dz = (float) (cfz + crz * a + cuz * b);
                float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                float vx = -dx / dl, vy = -dy / dl, vz = -dz / dl;
                float z = gz[p];
                float wx = (float) (ex + dx * z), wy = (float) (ey + dy * z), wz = (float) (ez + dz * z);
                float dist = z * dl;
                if (code == Mat.POOL) { shadePool(p, wx, wy, wz, vx, vy, vz, dist, sk, ref, hz, pc); continue; }
                // Wellen: im Fluss aus dem Fluss-Paket (Strömung, Steine, Schaum), sonst aus der Rauschtabelle
                float fx = 0, fz = 0, spd = 0, amp, f;
                float bank = 99, foam = 0, depthW = -1, lakeDepth = 99;
                boolean basin = false;
                float gx, gzz;
                // Strömung: vom Nachbarpixel übernehmen, wenn es keine 30 cm entfernt lag (sie ändert sich kaum)
                boolean flowOk = false;
                if (river && rfl != null && rsu != null) {
                    if (px == lastPx + 1 && Math.abs(wx - lastWx) + Math.abs(wz - lastWz) < 0.3f) flowOk = lastOk;
                    else { flowOk = rfl.sample(wx, wz, rf); lastWx = wx; lastWz = wz; lastOk = flowOk; }
                    lastPx = px;
                }
                if (flowOk) {
                    float lod = smooth(40, 400, dist);
                    rsu.sample(wx, wz, t, rf, lod, rsf);
                    float k = dist > 250 ? 250 / dist : 1;
                    gx = rsf.dhdx * k; gzz = rsf.dhdz * k;
                    foam = rsf.foam * (1 - smooth(300, 900, dist));
                    depthW = rf.depth;
                } else {
                    if (river) {
                        terrain.ground(wx, wz, gmb);
                        fx = gmb[1]; fz = gmb[2]; bank = -gmb[0];
                        spd = 1.1f; amp = 0.7f; f = 0.35f;
                        if (dist > 250) amp *= 250 / dist;
                        float e = 0.3f / f;
                        gx = (wave(wx + e, wz, t, f, fx, fz, spd) - wave(wx - e, wz, t, f, fx, fz, spd)) / (2 * e) * amp;
                        gzz = (wave(wx, wz + e, t, f, fx, fz, spd) - wave(wx, wz - e, t, f, fx, fz, spd)) / (2 * e) * amp;
                    } else {
                        // See: Windwellen aus fünf Wellenzügen; im Tosbecken kommt das Brausen der Strahlen dazu
                        lakeWaves(wx, wz, t, dist, lw);
                        gx = lw[0]; gzz = lw[1]; foam = lw[2];
                        lakeDepth = wy - terrain.sample(wx, wz);
                        if (wz < -29 && wz > -97 && Math.abs(wx) < 39.5f && wy < -35) {
                            float bf = (float) basinFoam;
                            // Zone des Aufpralls um z = -58, die Brause läuft nach Norden aus
                            float zone = 0.3f + 0.7f * (float) Math.exp(-((wz + 58) * (wz + 58)) / 260f);
                            float tu = Noise.tex(wx * 0.9f + t * 0.7f, wz * 0.9f - t * 1.6f) - 0.5f, tv = Noise.tex(wz * 0.8f - t * 1.1f + 4f, wx * 0.8f + t * 0.5f) - 0.5f;
                            gx += tu * 0.9f * bf * zone; gzz += tv * 0.9f * bf * zone;
                            float fm = Noise.tex(wx * 1.3f - t * 0.4f, wz * 1.3f - t * 1.2f) * 0.55f + Noise.tex(wx * 3.7f + t, wz * 3.7f - t * 2f) * 0.45f;
                            foam = Math.max(foam, 0.9f * Math.min(1, smooth(0.5f, 0.78f, fm + 0.3f * zone * bf) * bf * (0.3f + 0.7f * zone)));
                            basin = true;
                        }
                    }
                }
                float nx = -gx, ny = 1, nz = -gzz;
                float nl = (float) Math.sqrt(nx * nx + 1 + nz * nz);
                nx /= nl; ny /= nl; nz /= nl;
                float cosV = Math.max(0.02f, nx * vx + ny * vy + nz * vz);
                float F = 0.02f + 0.98f * (float) Math.pow(1 - cosV, 5);
                float rx = 2 * cosV * nx - vx, ry = 2 * cosV * ny - vy, rz = 2 * cosV * nz - vz;
                float wgt = ssr(wx, wy + 0.02f, wz, rx, ry, rz, ref);
                s.radiance(rx, Math.max(ry, 0.01f), rz, sk);
                float rr = ref[0] * wgt + sk[0] * (1 - wgt), rg = ref[1] * wgt + sk[1] * (1 - wgt), rb = ref[2] * wgt + sk[2] * (1 - wgt);
                float skyv = Math.max(0.2f, Math.min(1, gsk[p]));
                float sunV = sunUp ? li.lit(wx, wy + 0.03, wz, 0.5, dist < 300) : 0;
                float amR = s.upR * skyv, amG = s.upG * skyv, amB = s.upB * skyv;
                float sl = Math.max(0, ly) * sunV;
                // Farbe im Wasser: am Ufer flach über Kies (heller), in der Mitte tiefer und grüner
                float shallow = !river ? 0 : depthW >= 0 ? 1 - smooth(0.1f, 1.3f, depthW) : 1 - smooth(0, 6, bank);
                float br, bg, bb;
                if (!river) {
                    // See: Licht fällt durchs Wasser zum Grund und zurück (doppelter Weg), Rot wird zuerst geschluckt;
                    // was nicht zum Grund kommt, streut als tiefes Blaugrün zurück
                    float dd = Math.max(0.05f, lakeDepth) * 2f;
                    float tR = Noise.expNeg(0.60f * dd), tG = Noise.expNeg(0.17f * dd), tB = Noise.expNeg(0.11f * dd);
                    float lr = amR + s.sunR * sl * 0.5f, lg = amG + s.sunG * sl * 0.5f, lb = amB + s.sunB * sl * 0.5f;
                    float turb = basin ? 0.4f : 0;
                    br = (0.075f * tR + 0.013f * (1 - tR)) * lr;
                    bg = (0.070f * tG + 0.058f * (1 - tG)) * lg;
                    bb = (0.055f * tB + 0.066f * (1 - tB)) * lb;
                    // Ufersaum: ganz flaches Wasser, helle Schaumkante mit Spritzern
                    float edge = 1 - smooth(0.0f, 0.22f, lakeDepth);
                    float en = Noise.tex(wx * 1.6f + t * 0.3f, wz * 1.6f - t * 0.25f);
                    foam = Math.max(foam, edge * (0.35f + 0.65f * en) * (1 - smooth(60, 500, dist)));
                    br += turb * 0.02f * lr; bg += turb * 0.03f * lg; bb += turb * 0.03f * lb;
                } else {
                    br = (0.030f + 0.05f * shallow) * (amR + s.sunR * sl * 0.6f);
                    bg = (0.050f + 0.045f * shallow) * (amG + s.sunG * sl * 0.6f);
                    bb = (0.042f + 0.025f * shallow) * (amB + s.sunB * sl * 0.6f);
                }
                float r = br * (1 - F) + rr * F, g = bg * (1 - F) + rg * F, bl = bb * (1 - F) + rb * F;
                if (sunV > 0) {
                    float rs = Math.max(0, rx * lx + ry * ly + rz * lz);
                    float sp = Math.min(14f, (float) Math.pow(rs, 900) * 70) + (float) Math.pow(rs, 90) * 1.0f;
                    sp *= sunV * (0.3f + F);
                    r += s.sunR * sp; g += s.sunG * sp; bl += s.sunB * sp;
                }
                if (foam > 0) {
                    // Schaum: weiß, von Sonne und Himmel beleuchtet
                    float fr = 0.75f * (amR + s.sunR * sl * 0.9f), fg = 0.77f * (amG + s.sunG * sl * 0.9f), fb2 = 0.78f * (amB + s.sunB * sl * 0.9f);
                    r += (fr - r) * foam; g += (fg - g) * foam; bl += (fb2 - bl) * foam;
                }
                if (lampLit) {
                    lc[0] = lc[1] = lc[2] = 0;
                    if (dist < 500) {
                        lp.illuminate(wx, wy + 0.1f, wz, 0, 1, 0, lc);
                        float k = 0.07f + 0.7f * foam;
                        r += lc[0] * k; g += lc[1] * k; bl += lc[2] * k;
                    }
                    lc[0] = lc[1] = lc[2] = 0;
                    lp.glint(wx, wy, wz, rx, ry, rz, lc);
                    float fk = 0.25f + F;
                    r += lc[0] * fk; g += lc[1] * fk; bl += lc[2] * fk;
                }
                float fa = 1 - Noise.expNeg(dist * air);
                if (fa > 0.002f) {
                    s.haze(-vx, -vy, -vz, hz);
                    r += (hz[0] - r) * fa; g += (hz[1] - g) * fa; bl += (hz[2] - bl) * fa;
                }
                hr[p] = r; hg[p] = g; hb[p] = bl;
            }
        }
    }

    /** Wasser der Quellen: Absorption (Rot stark, Blau schwach) und Streuung an Kieselsäure (blau) je Meter. */
    static final float SA_R = 0.45f, SA_G = 0.065f, SA_B = 0.016f, SS_R = 0.004f, SS_G = 0.011f, SS_B = 0.032f;

    /**
     * Heiße Quelle: Der Sehstrahl wird an der Oberfläche gebrochen und läuft durch das klare Wasser zum
     * Grund. Der Grund hat die Farbe seiner Temperatur (Matten am flachen Rand, heller Sinter in der
     * heißen Tiefe). Unterwegs schluckt das Wasser Rot, und Kieselsäure streut Blau: daher das tiefe
     * Blau in der Mitte, Türkis und Grün über dem gelben Schelf. Oben Spiegelung nach Fresnel.
     */
    private void shadePool(int p, float wx, float wy, float wz, float vx, float vy, float vz, float dist, float[] sk, float[] ref, float[] hz, float[] c3) {
        final Sky s = sky;
        final LightingEngine li = L;
        Thermal th = thermal;
        Thermal.Spring sp = th == null ? null : th.poolAt(wx, wz);
        if (sp != null && sp.kind == Thermal.Kind.MUD) { shadeMud(p, sp, wx, wy, wz, vx, vy, vz, dist, sk, hz); return; }
        float t = time;
        float lx = (float) s.sun[0], ly = (float) s.sun[1], lz = (float) s.sun[2];
        boolean sunUp = s.sunR + s.sunG + s.sunB > 0.001f;
        // Oberfläche: ruhig; über dem heißen Schlot wallt das Wasser
        float boil = sp == null ? 0 : (float) smoothD(86, 93, sp.t0) * (1 - (float) smoothD(0.15, 0.5, sp.u(wx, wz)));
        float amp = 0.05f + 0.18f * boil;
        if (dist > 150) amp *= 150 / dist;
        float f = 0.8f + 1.6f * boil, e = 0.3f / f;
        float tt = t * (1 + 3 * boil);
        float gx = (wave(wx + e, wz, tt, f, 0, 0, 0) - wave(wx - e, wz, tt, f, 0, 0, 0)) / (2 * e);
        float gzz = (wave(wx, wz + e, tt, f, 0, 0, 0) - wave(wx, wz - e, tt, f, 0, 0, 0)) / (2 * e);
        float nx = -gx * amp, ny = 1, nz = -gzz * amp;
        float nl = (float) Math.sqrt(nx * nx + 1 + nz * nz);
        nx /= nl; ny /= nl; nz /= nl;
        float cosV = Math.max(0.02f, nx * vx + ny * vy + nz * vz);
        float F = 0.02f + 0.98f * (float) Math.pow(1 - cosV, 5);
        float rx = 2 * cosV * nx - vx, ry = 2 * cosV * ny - vy, rz = 2 * cosV * nz - vz;
        float wgt = ssr(wx, wy + 0.02f, wz, rx, ry, rz, ref);
        s.radiance(rx, Math.max(ry, 0.01f), rz, sk);
        float rr = ref[0] * wgt + sk[0] * (1 - wgt), rg = ref[1] * wgt + sk[1] * (1 - wgt), rb = ref[2] * wgt + sk[2] * (1 - wgt);
        float sunV = sunUp ? li.lit(wx, wy + 0.03, wz, 0.5, dist < 300) : 0;
        float skyv = Math.max(0.3f, Math.min(1, gsk[p]));
        float amR = s.upR * skyv, amG = s.upG * skyv, amB = s.upB * skyv;
        // Brechung (n = 1,333): Richtung des Strahls im Wasser
        float eta = 0.75f, cosT = (float) Math.sqrt(Math.max(0, 1 - eta * eta * (1 - cosV * cosV)));
        float tx = -vx * eta + (eta * cosV - cosT) * nx, ty = -vy * eta + (eta * cosV - cosT) * ny, tzz = -vz * eta + (eta * cosV - cosT) * nz;
        float down = Math.max(0.12f, -ty);
        float d0 = sp == null ? 2 : Thermal.depth(sp, wx, wz);
        float L0 = d0 / down;
        float bx = wx + tx * L0, bz = wz + tzz * L0;
        float d1 = sp == null ? d0 : Thermal.depth(sp, bx, bz);
        float path = Math.min(80, d1 / down);
        bx = wx + tx * path; bz = wz + tzz * path;
        // Farbe des Grundes nach seiner Temperatur
        float tb = sp == null ? 60 : th.tempOf(sp, bx, bz);
        Thermal.color(tb, c3);
        // Licht am Grund: Sonne durchs Wasser (gebrochen), Himmel
        float sinS = (float) Math.sqrt(Math.max(0, 1 - ly * ly)) * eta, cosS = (float) Math.sqrt(Math.max(0.05, 1 - sinS * sinS));
        float sd = d1 / cosS, ad = d1 * 1.25f;
        float sl = Math.max(0, ly) * sunV;
        float er = s.sunR * sl * Noise.expNeg((SA_R + SS_R) * sd) + amR * Noise.expNeg((SA_R + SS_R) * ad);
        float eg = s.sunG * sl * Noise.expNeg((SA_G + SS_G) * sd) + amG * Noise.expNeg((SA_G + SS_G) * ad);
        float eb = s.sunB * sl * Noise.expNeg((SA_B + SS_B) * sd) + amB * Noise.expNeg((SA_B + SS_B) * ad);
        // Kaustik: wandernde helle Netze auf flachem Grund
        if (d1 < 3 && sl > 0) {
            float ca = Noise.tex(bx * 1.3f + t * 0.4f, bz * 1.3f - t * 0.3f), cb = Noise.tex(bx * 1.9f - t * 0.35f + 5, bz * 1.9f + t * 0.25f);
            float c = Math.max(0, 1 - Math.abs(ca - cb) * 7);
            float kk = 1 + 1.2f * c * c * (1 - d1 / 3);
            er *= kk; eg *= kk; eb *= kk;
        }
        float tvR = Noise.expNeg((SA_R + SS_R) * path), tvG = Noise.expNeg((SA_G + SS_G) * path), tvB = Noise.expNeg((SA_B + SS_B) * path);
        // Streuung im Wasser: Sonne (vorwärts etwas mehr) und Himmel
        float mu = -(tx * lx + ty * ly + tzz * lz);
        float ph = 0.6f + 0.5f * Math.max(0, mu);
        float inR = s.sunR * sl * ph * 0.5f + amR * 0.6f, inG = s.sunG * sl * ph * 0.5f + amG * 0.6f, inB = s.sunB * sl * ph * 0.5f + amB * 0.6f;
        float ur = c3[0] * er * tvR + SS_R / (SA_R + SS_R) * (1 - tvR) * inR;
        float ug = c3[1] * eg * tvG + SS_G / (SA_G + SS_G) * (1 - tvG) * inG;
        float ub = c3[2] * eb * tvB + SS_B / (SA_B + SS_B) * (1 - tvB) * inB;
        float r = ur * (1 - F) + rr * F, g = ug * (1 - F) + rg * F, bl = ub * (1 - F) + rb * F;
        if (sunV > 0) {
            float rs = Math.max(0, rx * lx + ry * ly + rz * lz);
            float spk = (float) Math.pow(rs, 900) * 10 + (float) Math.pow(rs, 350) * 0.3f;
            spk = Math.min(4, spk * sunV * (0.3f + F));
            r += s.sunR * spk; g += s.sunG * spk; bl += s.sunB * spk;
        }
        float air = AIR0 + AIR1 * s.haze;
        float fa = 1 - Noise.expNeg(dist * air);
        if (fa > 0.002f) {
            s.haze(-vx, -vy, -vz, hz);
            r += (hz[0] - r) * fa; g += (hz[1] - g) * fa; bl += (hz[2] - bl) * fa;
        }
        hr[p] = r; hg[p] = g; hb[p] = bl;
    }

    /**
     * Schlammtopf: undurchsichtiger, nasser Ton (Kaolinit, von Eisenoxiden rosa bis orange getönt).
     * Blasen wachsen in Zellen von knapp einem Meter, platzen und werfen einen Ring, der verläuft.
     * Licht wie am Boden, dazu Glanz und Himmelsspiegelung der nassen Oberfläche.
     */
    private void shadeMud(int p, Thermal.Spring sp, float wx, float wy, float wz, float vx, float vy, float vz, float dist, float[] sk, float[] hz) {
        final Sky s = sky;
        final LightingEngine li = L;
        float t = time;
        float lx = (float) s.sun[0], ly = (float) s.sun[1], lz = (float) s.sun[2];
        boolean sunUp = s.sunR + s.sunG + s.sunB > 0.001f;
        // Höhe der Oberfläche aus den Blasen der Nachbarzellen, Normale aus der Ableitung
        float e = 0.05f;
        float h0 = mudHeight(wx, wz, t), hx = mudHeight(wx + e, wz, t), hzz = mudHeight(wx, wz + e, t);
        float nx = -(hx - h0) / e, nz = -(hzz - h0) / e, ny = 1;
        float nl = (float) Math.sqrt(nx * nx + 1 + nz * nz);
        nx /= nl; ny /= nl; nz /= nl;
        // Farbe: grau-rosa Ton, zum Rand hin wärmer und trockener
        float u = (float) Math.min(1, sp.u(wx, wz));
        float n1 = Noise.tex(wx * 0.9f + 3.3f, wz * 0.9f), n2 = Noise.tex(wx * 3.1f, wz * 3.1f + 7);
        float ar = 0.46f + 0.10f * u + 0.06f * n1, ag = 0.40f + 0.04f * u + 0.04f * n1, ab = 0.39f - 0.03f * u + 0.03f * n2;
        float dark = 0.85f + 0.15f * n2;
        ar *= dark; ag *= dark; ab *= dark;
        float sunV = sunUp ? li.lit(wx, wy + 0.05, wz, 0.5, dist < 300) : 0;
        float ndl = Math.max(0, nx * lx + ny * ly + nz * lz);
        float skyv = Math.max(0.3f, Math.min(1, gsk[p]));
        float r = ar * (s.sunR * ndl * sunV + s.upR * skyv), g = ag * (s.sunG * ndl * sunV + s.upG * skyv), bl = ab * (s.sunB * ndl * sunV + s.upB * skyv);
        // nasser Glanz
        float cosV = Math.max(0.02f, nx * vx + ny * vy + nz * vz);
        float F = 0.03f + 0.5f * (float) Math.pow(1 - cosV, 5);
        float rx = 2 * cosV * nx - vx, ry = 2 * cosV * ny - vy, rz = 2 * cosV * nz - vz;
        s.radiance(rx, Math.max(ry, 0.01f), rz, sk);
        r += (sk[0] - r) * F; g += (sk[1] - g) * F; bl += (sk[2] - bl) * F;
        if (sunV > 0) {
            float rs = Math.max(0, rx * lx + ry * ly + rz * lz);
            float spk = (float) Math.pow(rs, 80) * 1.2f * sunV;
            r += s.sunR * spk; g += s.sunG * spk; bl += s.sunB * spk;
        }
        float air = AIR0 + AIR1 * s.haze;
        float fa = 1 - Noise.expNeg(dist * air);
        if (fa > 0.002f) {
            s.haze(-vx, -vy, -vz, hz);
            r += (hz[0] - r) * fa; g += (hz[1] - g) * fa; bl += (hz[2] - bl) * fa;
        }
        hr[p] = r; hg[p] = g; hb[p] = bl;
    }

    /** Oberfläche des Schlamms (m über dem Spiegel): Blasen je Zelle von 0,9 m, die wachsen, platzen und Ringe werfen. */
    static float mudHeight(float x, float z, float t) {
        final float C = 0.9f;
        int ci = (int) Math.floor(x / C), cj = (int) Math.floor(z / C);
        float h = 0;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                int i = ci + di, j = cj + dj;
                int hs = Noise.hash(i, j, 77);
                float ox = ((hs & 255) / 255f) * C, oz = (((hs >> 8) & 255) / 255f) * C;
                float period = 1.6f + 3.4f * (((hs >> 16) & 255) / 255f), ph = ((hs >> 24) & 255) / 255f;
                float tau = (t / period + ph) % 1;
                float dx = x - (i * C + ox), dz = z - (j * C + oz), d = (float) Math.sqrt(dx * dx + dz * dz);
                float rMax = 0.18f + 0.22f * (((hs >> 4) & 255) / 255f);
                if (tau < 0.7f) {
                    // Blase wächst als flache Kuppel
                    float rr = rMax * (float) Math.sqrt(tau / 0.7f);
                    if (d < rr) { float q = 1 - d * d / (rr * rr); h += 0.08f * rr / rMax * q * q; }
                } else {
                    // geplatzt: ein Ring läuft nach außen und verebbt
                    float k = (tau - 0.7f) / 0.3f;
                    float ring = rMax * (0.6f + 2.4f * k), w = 0.06f + 0.05f * k;
                    float q = (d - ring) / w;
                    h += 0.03f * (1 - k) * (float) Math.exp(-q * q);
                    if (d < rMax * 0.6f) h -= 0.03f * (1 - k) * (1 - d / (rMax * 0.6f));
                }
            }
        }
        return h;
    }

    // ------------------------------------------------------------ Teilchen

    private float[] qx = new float[0], qy, qrad, qz, qcr, qcg, qcb, qal, qx2, qy2;
    private byte[] qline;
    /** Teilchenpuffer in halber Auflösung: Farbe (vormultipliziert), Durchlässigkeit, nächste Tiefe. */
    private int PW, PH;
    private float[] pr = new float[0], pg, pb, pt, pz, pT;
    private float[] qtm = new float[0];
    private final SteamGrid steamGrid = new SteamGrid();
    /** Schalter: Regenbogen, Selbstschatten im Dampf. */
    public volatile boolean rainbow = true, steamShadow = true;

    /**
     * Regenbogen als Faktor auf das gestreute Sonnenlicht, für den Winkel th (Grad) zwischen
     * Blickrichtung und Gegenpunkt der Sonne. Hauptbogen: Rot außen bei 42,5°, Violett innen bei 40,6°;
     * Nebenbogen bei 50–53° mit umgekehrter Farbfolge und schwächer; innen heller, dazwischen das
     * dunkle Band nach Alexander (Werte nach Wikipedia, „Rainbow“).
     */
    static void bow(float th, float[] o) {
        float r = gss(th, 42.2f, 0.55f) + 0.43f * gss(th, 50.5f, 0.7f);
        float g = gss(th, 41.5f, 0.55f) + 0.43f * gss(th, 51.6f, 0.7f);
        float b = gss(th, 40.8f, 0.55f) + 0.43f * gss(th, 52.8f, 0.7f);
        float inside = th < 40.5f ? 0.35f * smooth(40.5f, 30f, th) + 0.12f : 0;
        float band = th > 42.6f && th < 50f ? -0.25f * smooth(42.6f, 44f, th) * smooth(50f, 48.6f, th) : 0;
        float base = 1 + inside + band;
        o[0] = base + 2.6f * r; o[1] = base + 2.6f * g; o[2] = base + 2.6f * b;
    }

    private static float gss(float x, float m, float sd) { float d = (x - m) / sd; return (float) Math.exp(-0.5 * d * d); }

    /**
     * Tropfen, Gischt, Dampf und Spritzer: in halber Auflösung als weiche Scheiben mit Tiefenprüfung
     * gesammelt und tiefengerecht ins volle Bild gerechnet (etwa ein Viertel der Arbeit). Beleuchtet mit
     * Schattenkarte, Streufunktion und dem Schatten, den der Dampf auf sich selbst wirft; Wassertropfen
     * zeigen im Gegenpunkt der Sonne den Regenbogen. Nahe Tropfen als Striche in voller Auflösung.
     */
    private void drawParticles(com.dan.talsperre.effects.ParticleSystem ps, int rowsPer) {
        final int n = ps.n;
        if (qx.length < n) {
            int c = n + n / 4 + 64;
            qx = new float[c]; qy = new float[c]; qrad = new float[c]; qz = new float[c]; qcr = new float[c]; qcg = new float[c];
            qcb = new float[c]; qal = new float[c]; qx2 = new float[c]; qy2 = new float[c]; qline = new byte[c]; qtm = new float[c];
        }
        final boolean thermoOn = thermo;
        final float tAmb = com.dan.talsperre.core.Thermal.ambient;
        final Sky s = sky;
        final LightingEngine li = L;
        final float lx = (float) s.sun[0], ly = (float) s.sun[1], lz = (float) s.sun[2];
        final boolean sunUp = s.sunR + s.sunG + s.sunB > 1e-4f;
        final float air = AIR0 + AIR1 * s.haze;
        final boolean bowOn = rainbow;
        // Dichteraster des Dampfs für den Selbstschatten
        final boolean shadowOn = steamShadow && sunUp;
        if (shadowOn) steamGrid.build(ps);
        final int CH = 64;
        final int chunks = (n + CH - 1) / CH;
        final float[] hzc = new float[3];
        s.haze((float) cfx, (float) cfy, (float) cfz, hzc);
        IntStream.range(0, chunks).parallel().forEach(ch -> {
            float[] bw = new float[3];
            for (int i = ch * CH; i < Math.min(n, (ch + 1) * CH); i++) {
                qal[i] = 0;
                float x = ps.x[i], y = ps.y[i], z = ps.z[i];
                double ddx = x - ex, ddy = y - ey, ddz = z - ez;
                double vz = ddx * cfx + ddy * cfy + ddz * cfz;
                if (vz < 0.3 || vz > 4000) continue;
                byte k = ps.kind[i];
                boolean steam = k == com.dan.talsperre.effects.ParticleSystem.STEAM;
                double rad = ps.size[i] * pfy / vz;
                double sx = W / 2.0 + (ddx * crx + ddy * cry + ddz * crz) / vz * pfx;
                double sy = H / 2.0 - (ddx * cux + ddy * cuy + ddz * cuz) / vz * pfy;
                double cap = steam ? 160 : 80;
                if (rad > cap) rad = cap;
                float fadeIn, fadeOut, a01 = ps.age[i] / ps.life[i];
                float al = ps.alpha[i];
                if (steam) {
                    fadeIn = Math.min(1, ps.age[i] * 0.8f);
                    fadeOut = (float) Math.pow(1 - a01, 1.3);
                } else {
                    fadeIn = Math.min(1, ps.age[i] * 6);
                    fadeOut = 1 - smooth(0.75f, 1, a01);
                }
                al *= fadeIn * fadeOut;
                if (rad < 0.7) { al *= (float) (rad / 0.7) * (float) (rad / 0.7); rad = 0.7; }
                if (al < 0.003f) continue;
                if (sx < -rad || sy < -rad || sx > W + rad || sy > H + rad) continue;
                float dl = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                float mu = (float) ((ddx * lx + ddy * ly + ddz * lz) / dl);
                float g = steam ? 0.45f : 0.65f;
                float hg = (1 - g * g) / (float) Math.pow(1 + g * g - 2 * g * mu, 1.5);
                // Gegenlicht hell, aber gedeckelt: eine dünne Wolke streut nicht mehr, als durch sie hindurchgeht
                float phase = Math.min(2.2f, 0.35f + 0.65f * hg * 0.5f);
                float lit = sunUp ? li.lit(x, y, z, 0.3, false) : 0;
                float occ = 1;
                if (shadowOn && (steam || k == com.dan.talsperre.effects.ParticleSystem.SPRAY)) {
                    float od = steamGrid.toward(x, y, z, lx, ly, lz);
                    lit *= 0.3f + 0.7f * Noise.expNeg(od * 0.45f);
                    occ = 1 / (1 + 0.25f * steamGrid.at(x, y, z));
                }
                boolean mud = k == com.dan.talsperre.effects.ParticleSystem.MUD;
                float alb = steam ? 0.95f : mud ? 0.45f : 0.9f;
                float amb = (steam ? 0.55f : 0.5f) * (0.55f + 0.45f * occ);
                float br = 1, bg = 1, bb = 1;
                if (mud) { br = 1.1f; bg = 0.95f; bb = 0.9f; }
                else if (bowOn && !steam && lit > 0) {
                    float th = (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, -mu))));
                    if (th < 58) { bow(th, bw); br = bw[0]; bg = bw[1]; bb = bw[2]; }
                }
                float cr = alb * (s.sunR * lit * phase * br + (s.upR + s.sideR) * amb);
                float cg = alb * (s.sunG * lit * phase * bg + (s.upG + s.sideG) * amb);
                float cb = alb * (s.sunB * lit * phase * bb + (s.upB + s.sideB) * amb);
                if (lampOn > 0.01f && lamps != null) {
                    float[] lq = new float[3];
                    lamps.scatter(x, y, z, lq);
                    float kq = alb * (steam ? 4.5f : 3.0f);
                    cr += lq[0] * kq; cg += lq[1] * kq; cb += lq[2] * kq;
                }
                float fa = 1 - Noise.expNeg(dl * air);
                cr += (hzc[0] - cr) * fa; cg += (hzc[1] - cg) * fa; cb += (hzc[2] - cb) * fa;
                qx[i] = (float) sx; qy[i] = (float) sy; qrad[i] = (float) rad; qz[i] = (float) vz;
                qcr[i] = cr; qcg[i] = cg; qcb[i] = cb; qal[i] = Math.min(1, al);
                if (thermoOn) {
                    // Temperatur des Teilchens: Dampf kühlt rasch auf die Luft ab, Tropfen langsamer
                    float ag = ps.age[i];
                    qtm[i] = steam ? tAmb + (90 - tAmb) * Noise.expNeg(ag * 0.35f) : Math.max(tAmb, 88 - ag * 4);
                }
                qline[i] = 0;
                boolean rainK = k == com.dan.talsperre.effects.ParticleSystem.RAIN;
                if ((k == com.dan.talsperre.effects.ParticleSystem.DROP || rainK) && rad < 2.5 && vz < 220) {
                    double st = rainK ? 0.05 : 0.025;
                    double bx = x - ps.vx[i] * st - ex, by = y - ps.vy[i] * st - ey, bz = z - ps.vz[i] * st - ez;
                    double bvz = bx * cfx + by * cfy + bz * cfz;
                    if (bvz > 0.3) {
                        qx2[i] = (float) (W / 2.0 + (bx * crx + by * cry + bz * crz) / bvz * pfx);
                        qy2[i] = (float) (H / 2.0 - (bx * cux + by * cuy + bz * cuz) / bvz * pfy);
                        float len = Math.abs(qx2[i] - qx[i]) + Math.abs(qy2[i] - qy[i]);
                        if (len > 1.5f && len < 120) qline[i] = 1;
                    }
                }
            }
        });
        int cnt = 0;
        for (int i = 0; i < n; i++) if (qal[i] > 0) cnt++;
        drawnParticles = cnt;
        // Puffer in halber Auflösung: Tiefe = nächste Fläche im 2 × 2-Block
        int pw = (W + 1) / 2, ph = (H + 1) / 2;
        if (pr.length < pw * ph) {
            int c = pw * ph + pw * ph / 8;
            pr = new float[c]; pg = new float[c]; pb = new float[c]; pt = new float[c]; pz = new float[c]; pT = new float[c];
        }
        PW = pw; PH = ph;
        final int hPer = (ph + strips - 1) / strips;
        IntStream.range(0, strips).parallel().forEach(st -> {
            for (int yy = st * hPer; yy < Math.min(PH, (st + 1) * hPer); yy++) {
                for (int xx = 0; xx < PW; xx++) {
                    int q = yy * PW + xx;
                    int x0 = xx * 2, y0 = yy * 2, x1 = Math.min(W - 1, x0 + 1), y1 = Math.min(H - 1, y0 + 1);
                    pz[q] = Math.min(Math.min(gz[y0 * W + x0], gz[y0 * W + x1]), Math.min(gz[y1 * W + x0], gz[y1 * W + x1]));
                    pr[q] = 0; pg[q] = 0; pb[q] = 0; pt[q] = 1; pT[q] = 0;
                }
            }
        });
        IntStream.range(0, strips).parallel().forEach(st -> {
            int y0 = st * hPer, y1 = Math.min(PH, (st + 1) * hPer);
            if (y0 >= y1) return;
            for (int j = 0; j < n; j++) {
                float al = qal[j];
                if (al <= 0 || qline[j] != 0) continue;
                float rad = Math.max(0.5f, qrad[j] * 0.5f), sx = qx[j] * 0.5f, sy = qy[j] * 0.5f;
                if (sy + rad < y0 || sy - rad >= y1) continue;
                if (rad < 0.75f) al *= (rad / 0.75f) * (rad / 0.75f) * 1.4f;
                int xa = Math.max(0, (int) Math.floor(sx - rad)), xb = Math.min(PW - 1, (int) Math.ceil(sx + rad));
                int ya = Math.max(y0, (int) Math.floor(sy - rad)), yb = Math.min(y1 - 1, (int) Math.ceil(sy + rad));
                float r2 = Math.max(0.3f, rad * rad), vz = qz[j], soft = Math.max(0.3f, vz * 0.02f + qrad[j] * vz / (float) pfy * 0.5f);
                float cr0 = qcr[j], cg0 = qcg[j], cb0 = qcb[j], tm0 = thermoOn ? qtm[j] : 0;
                float ir2 = 1 / r2, isoft = 1 / soft;
                for (int yy = ya; yy <= yb; yy++) {
                    float eyy = yy + 0.5f - sy;
                    float rem = r2 - eyy * eyy;
                    if (rem <= 0) continue;
                    // nur die Spalten innerhalb des Kreises
                    float hw = (float) Math.sqrt(rem);
                    int xs = Math.max(xa, (int) (sx - hw - 0.5f)), xe = Math.min(xb, (int) (sx + hw - 0.5f) + 1);
                    int row = yy * PW;
                    float ey2 = eyy * eyy;
                    for (int xx = xs; xx <= xe; xx++) {
                        float exx = xx + 0.5f - sx;
                        float qd2 = (exx * exx + ey2) * ir2;
                        if (qd2 >= 1) continue;
                        int p = row + xx;
                        float zz = pz[p];
                        float sf = zz >= Float.MAX_VALUE ? 1 : Math.min(1, (zz - vz) * isoft);
                        if (sf <= 0) continue;
                        float fall = (1 - qd2) * (1 - qd2);
                        float a = Math.min(1, al * fall * sf);
                        pr[p] += (cr0 - pr[p]) * a; pg[p] += (cg0 - pg[p]) * a; pb[p] += (cb0 - pb[p]) * a;
                        if (thermoOn) pT[p] += (tm0 - pT[p]) * a;
                        pt[p] *= 1 - a;
                    }
                }
            }
        });
        // Ins volle Bild: bilinear, Nachbarn anderer Tiefe zählen kaum (wie beim Streulicht)
        IntStream.range(0, strips).parallel().forEach(st -> {
            for (int py = st * rowsPer; py < Math.min(H, (st + 1) * rowsPer); py++) {
                float fy = (py + 0.5f) / 2f - 0.5f;
                int ya = Math.max(0, Math.min(PH - 1, (int) Math.floor(fy))), yb = Math.min(PH - 1, ya + 1);
                float ty = Math.max(0, Math.min(1, fy - ya));
                for (int px = 0; px < W; px++) {
                    float fx = (px + 0.5f) / 2f - 0.5f;
                    int xa = Math.max(0, Math.min(PW - 1, (int) Math.floor(fx))), xb = Math.min(PW - 1, xa + 1);
                    float tx = Math.max(0, Math.min(1, fx - xa));
                    int a = ya * PW + xa, b = ya * PW + xb, c = yb * PW + xa, d = yb * PW + xb;
                    if (pt[a] > 0.999f && pt[b] > 0.999f && pt[c] > 0.999f && pt[d] > 0.999f) continue;
                    int p = py * W + px;
                    float z = gz[p];
                    float w0 = (1 - tx) * (1 - ty) * sim(pz[a], z) + 1e-4f, w1 = tx * (1 - ty) * sim(pz[b], z) + 1e-4f;
                    float w2 = (1 - tx) * ty * sim(pz[c], z) + 1e-4f, w3 = tx * ty * sim(pz[d], z) + 1e-4f;
                    float iw = 1 / (w0 + w1 + w2 + w3);
                    float T = (pt[a] * w0 + pt[b] * w1 + pt[c] * w2 + pt[d] * w3) * iw;
                    float cr = (pr[a] * (1 - pt[a]) * w0 + pr[b] * (1 - pt[b]) * w1 + pr[c] * (1 - pt[c]) * w2 + pr[d] * (1 - pt[d]) * w3) * iw;
                    float cg = (pg[a] * (1 - pt[a]) * w0 + pg[b] * (1 - pt[b]) * w1 + pg[c] * (1 - pt[c]) * w2 + pg[d] * (1 - pt[d]) * w3) * iw;
                    float cb = (pb[a] * (1 - pt[a]) * w0 + pb[b] * (1 - pt[b]) * w1 + pb[c] * (1 - pt[c]) * w2 + pb[d] * (1 - pt[d]) * w3) * iw;
                    hr[p] = hr[p] * T + cr; hg[p] = hg[p] * T + cg; hb[p] = hb[p] * T + cb;
                }
            }
        });
        for (int j = 0; j < n; j++) {
            if (qal[j] <= 0 || qline[j] == 0) continue;
            line(qx[j], qy[j], qx2[j], qy2[j], qz[j], qcr[j] * 1.1f, qcg[j] * 1.1f, qcb[j] * 1.1f, Math.min(0.85f, qal[j] * 1.3f));
        }
    }

    /** Wellenhöhe 0..1 aus zwei Lagen der Rauschtabelle, in Fließrichtung verschoben und gestreckt. */
    /** Wellenzüge des Sees: Längen und Höhen je Zug; ihre Höhe wächst mit dem Wind. */
    private static final float[] LW_LEN = {1.7f, 3.9f, 8.5f, 19f, 42f}, LW_AMP = {0.012f, 0.03f, 0.07f, 0.13f, 0.2f},
            LW_ANG = {-0.45f, 0.18f, -0.14f, 0.52f, -0.7f};

    /**
     * Neigung der Seeoberfläche am Punkt (x, z): out[0], out[1] = Steigung in x und z, out[2] = Schaumkronen 0..1.
     * Fünf Wellenzüge laufen mit dem Wind (Tiefwasser: Geschwindigkeit nach der Wellenlänge); je ferner der
     * Punkt, desto mehr verschwinden die kurzen Züge, damit nichts flimmert.
     */
    private void lakeWaves(float x, float z, float t, float dist, float[] out) {
        float wl = (float) Math.sqrt(windX * windX + windZ * windZ) + 1e-6f;
        float ux = (float) windX / wl, uz = (float) windZ / wl;
        float w = 0.2f + 0.8f * (float) Math.max(0, Math.min(1, wind));
        float gx = 0, gz = 0, h = 0;
        for (int i = 0; i < 5; i++) {
            float len = LW_LEN[i], k = 6.2831853f / len;
            float fade = 1 - smooth(len * 12, len * 70, dist);
            if (fade <= 0) continue;
            float ca = (float) Math.cos(LW_ANG[i]), sa = (float) Math.sin(LW_ANG[i]);
            float dx = ux * ca - uz * sa, dz = ux * sa + uz * ca;
            float ph = k * (dx * x + dz * z) - (float) Math.sqrt(9.81f * k) * t + i * 1.7f;
            float a = LW_AMP[i] * w * fade;
            float sl = a * k * (float) Math.cos(ph);
            gx += dx * sl; gz += dz * sl;
            if (i >= 2) h += a * (float) Math.sin(ph);
        }
        // feines Kräuseln: Rauschen, das mit dem Wind treibt
        float rip = (0.015f + 0.04f * w) * (1 - smooth(40, 300, dist));
        if (rip > 0.0005f) {
            gx += (Noise.tex(x * 1.9f + t * 0.6f * ux, z * 1.9f + t * 0.6f * uz) - 0.5f) * rip * 2;
            gz += (Noise.tex(z * 1.9f + 7f + t * 0.6f * uz, x * 1.9f + 3f + t * 0.6f * ux) - 0.5f) * rip * 2;
        }
        out[0] = gx; out[1] = gz;
        float cn = Noise.tex(x * 1.1f + t * 0.5f * ux, z * 1.1f + t * 0.5f * uz);
        out[2] = 0.85f * smooth(0.6f, 0.98f, w) * smooth(0.24f, 0.34f, h + 0.07f * (cn - 0.5f)) * smooth(0.35f, 0.65f, cn) * (1 - smooth(200, 900, dist));
    }

    private static float wave(float x, float z, float t, float f, float fx, float fz, float spd) {
        if (spd == 0) {
            return Noise.tex(x * f + t * 0.11f, z * f - t * 0.07f) * 0.6f + Noise.tex(z * f * 2.1f - t * 0.13f + 9.3f, x * f * 2.1f + t * 0.05f) * 0.4f;
        }
        float along = x * fx + z * fz, across = x * fz - z * fx;
        float a = along * f * 0.45f - t * spd * f * 0.45f, c = across * f * 1.3f;
        return Noise.tex(a, c) * 0.65f + Noise.tex(a * 2.3f - t * spd * f * 0.4f + 3.1f, c * 1.7f) * 0.35f;
    }

    /**
     * Spiegelung im Bildraum (aus Caracalla): der gespiegelte Strahl wird schrittweise verfolgt, bis er
     * hinter einer sichtbaren Fläche verschwindet. Liefert das Gewicht (0 = nichts gefunden), Farbe in out.
     */
    private float ssr(float ox, float oy, float oz, float rx, float ry, float rz, float[] out) {
        float tPrev = 0, t = 0.2f;
        for (int i = 0; i < 50; i++) {
            float qx = ox + rx * t, qy = oy + ry * t, qz = oz + rz * t;
            double ddx = qx - ex, ddy = qy - ey, ddz = qz - ez;
            double vz = ddx * cfx + ddy * cfy + ddz * cfz;
            if (vz < 0.3) return 0;
            double sx = W / 2.0 + (ddx * crx + ddy * cry + ddz * crz) / vz * pfx;
            double sy = H / 2.0 - (ddx * cux + ddy * cuy + ddz * cuz) / vz * pfy;
            if (sx < 0 || sy < 0 || sx >= W || sy >= H) return 0;
            int pix = (int) sy * W + (int) sx;
            float d = gz[pix];
            if (d < Float.MAX_VALUE && vz > d + 0.05f) {
                if (vz - d < 1.0f + 0.06f * t) {
                    float lo = tPrev, hi = t;
                    for (int k = 0; k < 5; k++) {
                        float m = (lo + hi) / 2;
                        double mdx = ox + rx * m - ex, mdy = oy + ry * m - ey, mdz = oz + rz * m - ez;
                        double mvz = mdx * cfx + mdy * cfy + mdz * cfz;
                        if (mvz < 0.3) break;
                        double msx = W / 2.0 + (mdx * crx + mdy * cry + mdz * crz) / mvz * pfx;
                        double msy = H / 2.0 - (mdx * cux + mdy * cuy + mdz * cuz) / mvz * pfy;
                        int mp = Math.max(0, Math.min(H - 1, (int) msy)) * W + Math.max(0, Math.min(W - 1, (int) msx));
                        if (mvz > gz[mp] + 0.05f) { hi = m; pix = mp; sx = msx; sy = msy; } else lo = m;
                    }
                    int c = gm[pix] - 1;
                    if (Mat.water(c)) return 0;
                    out[0] = hr[pix]; out[1] = hg[pix]; out[2] = hb[pix];
                    float edge = (float) Math.min(Math.min(sx, W - sx) / (0.08 * W), Math.min(sy, H - sy) / (0.08 * H));
                    return Math.max(0, Math.min(1, edge)) * Math.max(0, 1 - t / 1500f);
                }
            }
            tPrev = t;
            t = t * 1.16f + 0.1f;
        }
        return 0;
    }

    private static float smooth(float a, float b, float x) {
        float t = (x - a) / (b - a);
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return t * t * (3 - 2 * t);
    }

    /** Punkt unter dem Pixel des letzten Bildes (Bau oder Boden), null für Himmel. */
    public double[] pick(int px, int py) {
        if (W == 0 || px < 0 || py < 0 || px >= W || py >= H) return null;
        float z = gz[py * W + px];
        if (z >= Float.MAX_VALUE || z * 1.0 > 4000) return null;
        double a = (px + 0.5 - W / 2.0) / pfx, b = (H / 2.0 - py - 0.5) / pfy;
        double dx = cfx + crx * a + cux * b, dy = cfy + cry * a + cuy * b, dz = cfz + crz * a + cuz * b;
        return new double[]{ex + dx * z, ey + dy * z, ez + dz * z};
    }

    // ------------------------------------------------------------ Belichtung und Filmkurve

    private double[] expSum = new double[0];
    private int[] expN = new int[0];

    private void adaptExposure(double dt) {
        // Mittlere Helligkeit (geometrisch) aus jedem siebten Pixel, parallel über die Streifen
        final int S = strips, total = W * H, per = (total + S - 1) / S;
        if (expSum.length < S) { expSum = new double[S]; expN = new int[S]; }
        IntStream.range(0, S).parallel().forEach(st -> {
            int a = st * per, b = Math.min(total, a + per);
            int p0 = a + ((3 - a) % 7 + 7) % 7;
            double sm = 0;
            int nn = 0;
            for (int p = p0; p < b; p += 7) {
                double l = 0.2126 * hr[p] + 0.7152 * hg[p] + 0.0722 * hb[p];
                if (!(l >= 0)) continue;   // NaN oder negativ: nicht mitzählen
                sm += Math.log(1e-4 + Math.min(l, 50));
                nn++;
            }
            expSum[st] = sm; expN[st] = nn;
        });
        double sum = 0;
        int n = 0;
        for (int st = 0; st < S; st++) { sum += expSum[st]; n += expN[st]; }
        double avg = Math.exp(sum / Math.max(1, n));
        double target = 0.20 / Math.pow(avg, 0.82) * Math.pow(0.35, 0.18);
        // Nachts nicht zum Tag aufhellen: das Auge passt sich an, aber die Nacht bleibt dunkel
        target = Math.max(0.15, Math.min(3.5, target));
        if (!exposureValid || dt <= 0) { exposure = target; exposureValid = true; }
        else {
            double k = 1 - Math.exp(-dt * 1.8);
            exposure = Math.exp(Math.log(exposure) + (Math.log(target) - Math.log(exposure)) * k);
        }
    }

    // ------------------------------------------------------------ Tiere

    /** Tiere der Szene (Bisons, Wapitis) oder null; die Fauna füllt sie vor jedem Bild. */
    public volatile Animals animals;
    /** Fallendes Laub (null = keins). */
    public volatile LeafQuads leaves;
    /** Gras, Blumen, Steine und Erde um die Kamera (com.dan.ground); null = keine. */
    public volatile com.dan.ground.Batch foliage;
    /** Zeit fürs Zeichnen der Bodendecke im letzten Bild (ms). */
    public volatile double msFoliage;
    private float[] fpx = new float[0], fpy, fpz, fcr, fcg, fcb, ffd;
    private int[] fBinCount = new int[0];
    private short[] fLo = new short[0], fHi = new short[0];

    /**
     * Bodendecke als Dreiecke mit Farbe je Ecke, nach dem Licht der Szene: Sonne mit Schattenkarte (dünne
     * Halme und Blüten von beiden Seiten, Steine nur von vorn), Himmel, Dunst. Schreibt Tiefe und
     * Material, damit Lichtstrahlen, Dampf und Tiere sie berücksichtigen.
     */
    private void drawFoliage(com.dan.ground.Batch b, int rowsPer) {
        long f0 = System.nanoTime();
        final int n = b.nv;
        if (fpx.length < n) { int c = n + n / 4; fpx = new float[c]; fpy = new float[c]; fpz = new float[c]; fcr = new float[c]; fcg = new float[c]; fcb = new float[c]; ffd = new float[c]; }
        final Sky s = sky;
        final LightingEngine li = L;
        final boolean sunUp = s.sunR + s.sunG + s.sunB > 1e-4f;
        final float lx = (float) s.sun[0], ly = (float) s.sun[1], lz = (float) s.sun[2];
        final float air = AIR0 + AIR1 * s.haze;
        final float[] hzc = new float[3];
        s.haze((float) cfx, (float) cfy, (float) cfz, hzc);
        final float[] P = b.xyz, N = b.nrm, C = b.rgb;
        final byte[] solid = b.solid;
        System.arraycopy(b.fade, 0, ffd, 0, n);
        IntStream.range(0, 64).parallel().forEach(k -> {
            int a = n * k / 64, e = n * (k + 1) / 64;
            for (int i = a; i < e; i++) {
                double x = P[3 * i], y = P[3 * i + 1], z = P[3 * i + 2];
                double dx = x - ex, dy = y - ey, dz = z - ez;
                double vz = dx * cfx + dy * cfy + dz * cfz;
                fpz[i] = (float) vz;
                if (vz < 0.15) continue;
                fpx[i] = (float) (W / 2.0 + (dx * crx + dy * cry + dz * crz) / vz * pfx);
                fpy[i] = (float) (H / 2.0 - (dx * cux + dy * cuy + dz * cuz) / vz * pfy);
                float nx = N[3 * i], ny = N[3 * i + 1], nz = N[3 * i + 2];
                float d = nx * lx + ny * ly + nz * lz;
                boolean thin = solid[i] == 0;
                float lam = thin ? 0.35f + 0.65f * Math.abs(d) : Math.max(0, d);
                float lit = sunUp ? li.lit(x, y + 0.03, z, 0.3, false) : 0;
                float kSun = lit * lam * (float) Math.max(0, ly + 0.1);
                float amb = 0.5f + 0.4f * Math.abs(ny);
                float r = C[3 * i], g = C[3 * i + 1], bl = C[3 * i + 2];
                float cr = r * (s.sunR * kSun + (s.upR + s.sideR) * amb), cg = g * (s.sunG * kSun + (s.upG + s.sideG) * amb), cb = bl * (s.sunB * kSun + (s.upB + s.sideB) * amb);
                float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                float fa = 1 - Noise.expNeg(dl * air);
                fcr[i] = cr + (hzc[0] - cr) * fa; fcg[i] = cg + (hzc[1] - cg) * fa; fcb[i] = cb + (hzc[2] - cb) * fa;
            }
        });
        final int nt = b.nt;
        final int[] T = b.tri;
        final int ns = strips;
        if (fBinCount.length != ns) fBinCount = new int[ns];
        java.util.Arrays.fill(fBinCount, 0);
        if (fLo.length < nt) { fLo = new short[nt + nt / 4]; fHi = new short[nt + nt / 4]; }
        for (int t = 0; t < nt; t++) {
            int a = T[3 * t], c1 = T[3 * t + 1], c2 = T[3 * t + 2];
            fLo[t] = -1;
            if (fpz[a] < 0.15f || fpz[c1] < 0.15f || fpz[c2] < 0.15f) continue;
            float mnY = Math.min(fpy[a], Math.min(fpy[c1], fpy[c2])), mxY = Math.max(fpy[a], Math.max(fpy[c1], fpy[c2]));
            float mnX = Math.min(fpx[a], Math.min(fpx[c1], fpx[c2])), mxX = Math.max(fpx[a], Math.max(fpx[c1], fpx[c2]));
            if (mxY < 0 || mnY >= H || mxX < 0 || mnX >= W) continue;
            int s0 = Math.max(0, (int) Math.floor(Math.max(0, mnY)) / rowsPer), s1 = Math.min(ns - 1, (int) Math.floor(Math.min(H - 1, mxY)) / rowsPer);
            fLo[t] = (short) s0; fHi[t] = (short) s1;
            for (int q = s0; q <= s1; q++) fBinCount[q]++;
        }
        final int[][] bins = new int[ns][];
        for (int q = 0; q < ns; q++) bins[q] = new int[fBinCount[q]];
        int[] fill = new int[ns];
        for (int t = 0; t < nt; t++) { if (fLo[t] < 0) continue; for (int q = fLo[t]; q <= fHi[t]; q++) bins[q][fill[q]++] = t; }
        final byte terrainCode = (byte) (Mat.TERRAIN + 1);
        IntStream.range(0, ns).parallel().forEach(q -> {
            int y0 = q * rowsPer, y1 = Math.min(H, (q + 1) * rowsPer);
            for (int t : bins[q]) foliageTri(T[3 * t], T[3 * t + 1], T[3 * t + 2], y0, y1, terrainCode);
        });
        msFoliage = (System.nanoTime() - f0) / 1e6;
    }

    private void foliageTri(int a, int b, int c, int y0, int y1, byte code) {
        float xa = fpx[a], ya = fpy[a], xb = fpx[b], yb = fpy[b], xc = fpx[c], yc = fpy[c];
        float area = (xb - xa) * (yc - ya) - (xc - xa) * (yb - ya);
        if (Math.abs(area) < 1e-7f) return;
        float minX = Math.min(xa, Math.min(xb, xc)), maxX = Math.max(xa, Math.max(xb, xc));
        float minY = Math.min(ya, Math.min(yb, yc)), maxY = Math.max(ya, Math.max(yb, yc));
        int ix0 = Math.max(0, (int) Math.floor(minX)), ix1 = Math.min(W - 1, (int) Math.ceil(maxX));
        int iy0 = Math.max(y0, (int) Math.floor(minY)), iy1 = Math.min(y1 - 1, (int) Math.ceil(maxY));
        if (ix0 > ix1 || iy0 > iy1) return;
        float za = fpz[a], zb = fpz[b], zc = fpz[c];
        if (ix1 - ix0 <= 1 && iy1 - iy0 <= 1) {
            // kleiner als ein Bildpunkt: anteilig in die Farbe mischen
            int x = (int) ((xa + xb + xc) / 3), y = (int) ((ya + yb + yc) / 3);
            if (x < 0 || y < y0 || x >= W || y >= y1) return;
            float z = (za + zb + zc) / 3;
            int p = y * W + x;
            if (z >= gz[p]) return;
            float cov = Math.min(1, Math.abs(area) * 0.5f + 0.2f) * (1 - (ffd[a] + ffd[b] + ffd[c]) / 3);
            hr[p] += ((fcr[a] + fcr[b] + fcr[c]) / 3 - hr[p]) * cov;
            hg[p] += ((fcg[a] + fcg[b] + fcg[c]) / 3 - hg[p]) * cov;
            hb[p] += ((fcb[a] + fcb[b] + fcb[c]) / 3 - hb[p]) * cov;
            return;
        }
        float inv = 1 / area, ia = 1 / za, ib = 1 / zb, ic = 1 / zc;
        for (int y = iy0; y <= iy1; y++) {
            float sy = y + 0.5f;
            for (int x = ix0; x <= ix1; x++) {
                float sx = x + 0.5f;
                float w0 = ((xb - sx) * (yc - sy) - (xc - sx) * (yb - sy)) * inv;
                float w1 = ((xc - sx) * (ya - sy) - (xa - sx) * (yc - sy)) * inv;
                float w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float iz = w0 * ia + w1 * ib + w2 * ic, z = 1 / iz;
                int p = y * W + x;
                if (z >= gz[p]) continue;
                float k0 = w0 * ia * z, k1 = w1 * ib * z, k2 = w2 * ic * z;
                // Übergang in den Boden dahinter (Rand der Reichweite, Saum offener Erde)
                float f = ffd[a] * k0 + ffd[b] * k1 + ffd[c] * k2, o = 1 - f;
                if (f < 0.6f) { gz[p] = z; gm[p] = code; }
                hr[p] = (fcr[a] * k0 + fcr[b] * k1 + fcr[c] * k2) * o + hr[p] * f;
                hg[p] = (fcg[a] * k0 + fcg[b] * k1 + fcg[c] * k2) * o + hg[p] * f;
                hb[p] = (fcb[a] * k0 + fcb[b] * k1 + fcb[c] * k2) * o + hb[p] * f;
            }
        }
    }

    // ------------------------------------------------------------ Straßen und Wege

    /** Straßen, Wege und Verkehr (com.dan.road); null = keine. */
    public volatile com.dan.road.Roads roads;
    /** Zeit fürs Zeichnen der Wege im letzten Bild (ms). */
    public volatile double msWays;
    private float[] wpx = new float[0], wpy, wpz, wcr, wcg, wcb, wfd;
    private byte[] wkd = new byte[0];
    private short[] wLo = new short[0], wHi = new short[0];

    /**
     * Wege und Fahrzeuge als Dreiecke mit Farbe je Ecke: Sonne mit Schattenkarte, Himmel, nachts die
     * Scheinwerfer; glänzende Flächen (nasse Fahrbahn, Pfützen, Glas, Lack) spiegeln den Himmel nach
     * Fresnel und die Sonne. Leuchten strahlen selbst, Lichthöfe werden addiert und verdecken nichts.
     */
    private void drawWays(com.dan.road.Roads rd, int rowsPer) {
        long f0 = System.nanoTime();
        final com.dan.road.Batch b = rd.batch;
        final int n = b.nv;
        if (wpx.length < n) {
            int c = n + n / 4;
            wpx = new float[c]; wpy = new float[c]; wpz = new float[c]; wcr = new float[c]; wcg = new float[c]; wcb = new float[c]; wfd = new float[c]; wkd = new byte[c];
        }
        final Sky s = sky;
        final LightingEngine li = L;
        final boolean sunUp = s.sunR + s.sunG + s.sunB > 1e-4f;
        final float lx = (float) s.sun[0], ly = (float) s.sun[1], lz = (float) s.sun[2];
        final float air = AIR0 + AIR1 * s.haze;
        final float[] hzc = new float[3];
        s.haze((float) cfx, (float) cfy, (float) cfz, hzc);
        final float[] P = b.xyz, N = b.nrm, C = b.rgb, G = b.gloss;
        final byte[] K = b.kind;
        final boolean lamps = rd.lightCount > 0;
        System.arraycopy(b.fade, 0, wfd, 0, n);
        System.arraycopy(K, 0, wkd, 0, n);
        IntStream.range(0, 64).parallel().forEach(k -> {
            int a = n * k / 64, e = n * (k + 1) / 64;
            float[] sk = new float[3], il = new float[3];
            for (int i = a; i < e; i++) {
                double x = P[3 * i], y = P[3 * i + 1], z = P[3 * i + 2];
                double dx = x - ex, dy = y - ey, dz = z - ez;
                double vz = dx * cfx + dy * cfy + dz * cfz;
                wpz[i] = (float) vz;
                if (vz < 0.15) continue;
                wpx[i] = (float) (W / 2.0 + (dx * crx + dy * cry + dz * crz) / vz * pfx);
                wpy[i] = (float) (H / 2.0 - (dx * cux + dy * cuy + dz * cuz) / vz * pfy);
                float r = C[3 * i], g = C[3 * i + 1], bl = C[3 * i + 2];
                float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                byte kind = K[i];
                float cr, cg, cb;
                if (kind == com.dan.road.Batch.LAMP || kind == com.dan.road.Batch.GLOW) { cr = r; cg = g; cb = bl; }
                else {
                    float nx = N[3 * i], ny = N[3 * i + 1], nz = N[3 * i + 2];
                    float d = nx * lx + ny * ly + nz * lz;
                    boolean thin = kind == com.dan.road.Batch.THIN;
                    float lam = thin ? 0.35f + 0.65f * Math.abs(d) : Math.max(0, d);
                    float lit = sunUp && lam > 0 ? li.lit(x, y + 0.05, z, 0.3, false) : 0;
                    float kSun = lit * lam * (float) Math.max(0, ly + 0.1);
                    float amb = 0.5f + 0.4f * Math.abs(ny);
                    float eR = s.sunR * kSun + (s.upR + s.sideR) * amb, eG = s.sunG * kSun + (s.upG + s.sideG) * amb, eB = s.sunB * kSun + (s.upB + s.sideB) * amb;
                    if (lamps && dl < 300) {
                        il[0] = il[1] = il[2] = 0;
                        rd.illuminate((float) x, (float) y, (float) z, nx, ny, nz, il);
                        eR += il[0]; eG += il[1]; eB += il[2];
                    }
                    float gl = G[i], dif = 1 - 0.6f * gl;
                    cr = r * eR * dif; cg = g * eG * dif; cb = bl * eB * dif;
                    if (gl > 0.01f) {
                        float vx = (float) (dx / dl), vy = (float) (dy / dl), vw = (float) (dz / dl);
                        float vn = vx * nx + vy * ny + vw * nz;
                        float rx = vx - 2 * vn * nx, ry = vy - 2 * vn * ny, rw = vw - 2 * vn * nz;
                        float cos = Math.max(0, -vn), f5 = (1 - cos) * (1 - cos);
                        f5 = f5 * f5 * (1 - cos);
                        float F = (0.03f + 0.97f * f5) * gl;
                        s.radiance(rx, Math.max(0.02f, ry), rw, sk);
                        float sp = (float) (rx * s.sun[0] + ry * s.sun[1] + rw * s.sun[2]);
                        float glint = sp > 0.9f ? (float) Math.pow(sp, 300 * gl + 20) * 30 * gl * lit : 0;
                        cr += (sk[0] + s.sunR * glint) * F; cg += (sk[1] + s.sunG * glint) * F; cb += (sk[2] + s.sunB * glint) * F;
                    }
                }
                float fa = 1 - Noise.expNeg(dl * air);
                if (kind == com.dan.road.Batch.GLOW) { cr *= 1 - fa; cg *= 1 - fa; cb *= 1 - fa; }
                else { cr += (hzc[0] - cr) * fa; cg += (hzc[1] - cg) * fa; cb += (hzc[2] - cb) * fa; }
                wcr[i] = cr; wcg[i] = cg; wcb[i] = cb;
            }
        });
        final int nt = b.nt;
        final int[] T = b.tri;
        final int ns = strips;
        int[] cnt = new int[ns];
        if (wLo.length < nt) { wLo = new short[nt + nt / 4]; wHi = new short[nt + nt / 4]; }
        for (int t = 0; t < nt; t++) {
            int a = T[3 * t], c1 = T[3 * t + 1], c2 = T[3 * t + 2];
            wLo[t] = -1;
            if (wpz[a] < 0.15f || wpz[c1] < 0.15f || wpz[c2] < 0.15f) continue;
            float mnY = Math.min(wpy[a], Math.min(wpy[c1], wpy[c2])), mxY = Math.max(wpy[a], Math.max(wpy[c1], wpy[c2]));
            float mnX = Math.min(wpx[a], Math.min(wpx[c1], wpx[c2])), mxX = Math.max(wpx[a], Math.max(wpx[c1], wpx[c2]));
            if (mxY < 0 || mnY >= H || mxX < 0 || mnX >= W) continue;
            int s0 = Math.max(0, (int) Math.floor(Math.max(0, mnY)) / rowsPer), s1 = Math.min(ns - 1, (int) Math.floor(Math.min(H - 1, mxY)) / rowsPer);
            wLo[t] = (short) s0; wHi[t] = (short) s1;
            for (int q = s0; q <= s1; q++) cnt[q]++;
        }
        final int[][] bins = new int[ns][];
        for (int q = 0; q < ns; q++) bins[q] = new int[cnt[q]];
        int[] fill = new int[ns];
        for (int t = 0; t < nt; t++) { if (wLo[t] < 0) continue; for (int q = wLo[t]; q <= wHi[t]; q++) bins[q][fill[q]++] = t; }
        final byte code = (byte) (Mat.TERRAIN + 1);
        IntStream.range(0, ns).parallel().forEach(q -> {
            int y0 = q * rowsPer, y1 = Math.min(H, (q + 1) * rowsPer);
            for (int t : bins[q]) if (wkd[T[3 * t]] != com.dan.road.Batch.GLOW) wayTri(T[3 * t], T[3 * t + 1], T[3 * t + 2], y0, y1, code, false);
            for (int t : bins[q]) if (wkd[T[3 * t]] == com.dan.road.Batch.GLOW) wayTri(T[3 * t], T[3 * t + 1], T[3 * t + 2], y0, y1, code, true);
        });
        msWays = (System.nanoTime() - f0) / 1e6;
    }

    private void wayTri(int a, int b, int c, int y0, int y1, byte code, boolean add) {
        float xa = wpx[a], ya = wpy[a], xb = wpx[b], yb = wpy[b], xc = wpx[c], yc = wpy[c];
        float area = (xb - xa) * (yc - ya) - (xc - xa) * (yb - ya);
        if (Math.abs(area) < 1e-7f) return;
        float minX = Math.min(xa, Math.min(xb, xc)), maxX = Math.max(xa, Math.max(xb, xc));
        float minY = Math.min(ya, Math.min(yb, yc)), maxY = Math.max(ya, Math.max(yb, yc));
        int ix0 = Math.max(0, (int) Math.floor(minX)), ix1 = Math.min(W - 1, (int) Math.ceil(maxX));
        int iy0 = Math.max(y0, (int) Math.floor(minY)), iy1 = Math.min(y1 - 1, (int) Math.ceil(maxY));
        if (ix0 > ix1 || iy0 > iy1) return;
        float za = wpz[a], zb = wpz[b], zc = wpz[c];
        if (ix1 - ix0 <= 1 && iy1 - iy0 <= 1) {
            int x = (int) ((xa + xb + xc) / 3), y = (int) ((ya + yb + yc) / 3);
            if (x < 0 || y < y0 || x >= W || y >= y1) return;
            float z = (za + zb + zc) / 3;
            int p = y * W + x;
            if (z >= gz[p]) return;
            float cov = Math.min(1, Math.abs(area) * 0.5f + 0.2f) * (1 - (wfd[a] + wfd[b] + wfd[c]) / 3);
            float r = (wcr[a] + wcr[b] + wcr[c]) / 3, g = (wcg[a] + wcg[b] + wcg[c]) / 3, bl = (wcb[a] + wcb[b] + wcb[c]) / 3;
            if (add) { hr[p] += r * cov; hg[p] += g * cov; hb[p] += bl * cov; return; }
            hr[p] += (r - hr[p]) * cov; hg[p] += (g - hg[p]) * cov; hb[p] += (bl - hb[p]) * cov;
            return;
        }
        float inv = 1 / area, ia = 1 / za, ib = 1 / zb, ic = 1 / zc;
        for (int y = iy0; y <= iy1; y++) {
            float sy = y + 0.5f;
            for (int x = ix0; x <= ix1; x++) {
                float sx = x + 0.5f;
                float w0 = ((xb - sx) * (yc - sy) - (xc - sx) * (yb - sy)) * inv;
                float w1 = ((xc - sx) * (ya - sy) - (xa - sx) * (yc - sy)) * inv;
                float w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float iz = w0 * ia + w1 * ib + w2 * ic, z = 1 / iz;
                int p = y * W + x;
                if (z >= gz[p]) continue;
                float k0 = w0 * ia * z, k1 = w1 * ib * z, k2 = w2 * ic * z;
                float r = wcr[a] * k0 + wcr[b] * k1 + wcr[c] * k2, g = wcg[a] * k0 + wcg[b] * k1 + wcg[c] * k2, bl = wcb[a] * k0 + wcb[b] * k1 + wcb[c] * k2;
                if (add) { hr[p] += r; hg[p] += g; hb[p] += bl; continue; }
                float f = wfd[a] * k0 + wfd[b] * k1 + wfd[c] * k2, o = 1 - f;
                if (f < 0.6f) { gz[p] = z; gm[p] = code; }
                hr[p] = r * o + hr[p] * f; hg[p] = g * o + hg[p] * f; hb[p] = bl * o + hb[p] * f;
            }
        }
    }

    /** Treibgut auf dem Fluss (null = keins). */
    public volatile LeafQuads floats;
    /** Strömung und Oberfläche des Flusses (com.dan.river); null = die einfachen Wellen. */
    public volatile com.dan.river.FlowField riverFlow;
    public volatile com.dan.river.WaterSurface riverSurface;

    /**
     * Fallende und liegende Blätter als kleine Rhomben mit Tiefenprüfung. Licht: Sonne nach der
     * Schattenkarte, von beiden Seiten (dünne Blätter scheinen durch), dazu Himmel; Dunst wie bei den
     * Tieren. Nur bis 160 m, weiter weg wären sie kleiner als ein Bildpunkt.
     */
    private void drawLeaves(LeafQuads lq) {
        final Sky s = sky;
        final LightingEngine li = L;
        final float air = AIR0 + AIR1 * s.haze;
        float[] hzc = new float[3];
        s.haze((float) cfx, (float) cfy, (float) cfz, hzc);
        boolean sunUp = s.sunR + s.sunG + s.sunB > 1e-4f;
        for (int i = 0; i < lq.n; i++) {
            int o = 12 * i;
            double cx = (lq.xyz[o] + lq.xyz[o + 6]) * 0.5, cy = (lq.xyz[o + 1] + lq.xyz[o + 7]) * 0.5, cz = (lq.xyz[o + 2] + lq.xyz[o + 8]) * 0.5;
            double ddx = cx - ex, ddy = cy - ey, ddz = cz - ez;
            double vz = ddx * cfx + ddy * cfy + ddz * cfz;
            if (vz < 0.5 || vz > 160) continue;
            boolean ok = true;
            for (int j = 0; j < 4 && ok; j++) {
                double[] q = project(lq.xyz[o + 3 * j], lq.xyz[o + 3 * j + 1], lq.xyz[o + 3 * j + 2]);
                if (q == null) ok = false;
                else { polyX[j] = (float) q[0]; polyY[j] = (float) q[1]; }
            }
            if (!ok) continue;
            float nx = lq.nrm[3 * i], ny = lq.nrm[3 * i + 1], nz = lq.nrm[3 * i + 2];
            float lit = sunUp ? li.lit(cx, cy + 0.05, cz, 0.3, false) : 0;
            float cosS = (float) Math.abs(nx * s.sun[0] + ny * s.sun[1] + nz * s.sun[2]);
            float kSun = lit * (0.35f + 0.65f * cosS) * (float) Math.max(0, s.sun[1] + 0.1);
            float amb = 0.5f + 0.3f * Math.abs(ny);
            float r = lq.rgb[3 * i], g = lq.rgb[3 * i + 1], b = lq.rgb[3 * i + 2];
            float cr = r * (s.sunR * kSun + (s.upR + s.sideR) * amb), cg = g * (s.sunG * kSun + (s.upG + s.sideG) * amb),
                    cb = b * (s.sunB * kSun + (s.upB + s.sideB) * amb);
            float dl = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
            float fa = 1 - Noise.expNeg(dl * air);
            cr += (hzc[0] - cr) * fa; cg += (hzc[1] - cg) * fa; cb += (hzc[2] - cb) * fa;
            fillPoly(4, (float) vz - 0.02f, cr, cg, cb, false, 0);
        }
    }
    private final float[] polyX = new float[64], polyY = new float[64];

    /**
     * Bisons und Wapitis als Schattenrisse: eine senkrechte Ebene durch die Laufrichtung, zur Kamera
     * gedreht, sodass sie mindestens 35° gegen den Blick steht. Licht aus Schattenkarte und Himmel,
     * Dunst wie bei den Teilchen, darunter ein weicher Schattenfleck. Schreibt Tiefe und Material.
     */
    private void drawAnimals(Animals an) {
        final Sky s = sky;
        final LightingEngine li = L;
        final float air = AIR0 + AIR1 * s.haze;
        float[] hzc = new float[3];
        s.haze((float) cfx, (float) cfy, (float) cfz, hzc);
        boolean sunUp = s.sunR + s.sunG + s.sunB > 1e-4f;
        for (int i = 0; i < an.n; i++) {
            double ax = an.x[i], ay = an.y[i], az = an.z[i];
            double ddx = ax - ex, ddy = ay + 1 - ey, ddz = az - ez;
            double vz = ddx * cfx + ddy * cfy + ddz * cfz;
            if (vz < 1 || vz > 2500) continue;
            float sc = an.scale[i];
            // Ebene: Laufrichtung, notfalls zur Kamera gedreht
            double vx = ddx, vzz = ddz, vl = Math.hypot(vx, vzz);
            vx /= vl; vzz /= vl;
            double hx = an.hx[i], hz = an.hz[i];
            double c = hx * vx + hz * vzz;
            double lim = Math.cos(Math.toRadians(55));
            if (Math.abs(c) > lim) {
                // Anteil quer zum Blick auf das Mindestmaß heben
                double px = -vzz, pz = vx;
                double side = hx * px + hz * pz;
                double sg = side >= 0 ? 1 : -1;
                double cc = Math.signum(c) * lim, ss = sg * Math.sqrt(1 - lim * lim);
                hx = vx * cc + px * ss; hz = vzz * cc + pz * ss;
            }
            float lit = sunUp ? li.lit(ax, ay + 1.2 * sc, az, 0.3, true) : 0;
            // seitliche Beleuchtung: Sonne quer zur Ebene heller
            float side = (float) Math.abs(-hz * s.sun[0] + hx * s.sun[2]);
            float kSun = lit * (0.35f + 0.65f * side) * (float) Math.max(0, s.sun[1] + 0.1);
            float dl = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
            float fa = 1 - Noise.expNeg(dl * air);
            byte k = an.kind[i];
            boolean bison = k == Animals.BISON || k == Animals.BISON_CALF;
            float dep = (float) vz;
            if (k >= Animals.CAR) { drawCraft(an, i, k, ax, ay, az, hx, hz, sc, kSun, s, hzc, fa, dep, lit); continue; }
            if (k == Animals.PERSON) {
                if (vz > 900) continue;
                shadowBlob(ax, ay, az, hx, hz, sc * 0.3f, sc * 0.2f, 0.3f + 0.3f * lit, dep);
                float st = an.step[i], gt = an.gait[i];
                for (int L0 = 0; L0 < 2; L0++) {
                    float sw = (float) Math.sin(st + L0 * Math.PI) * 0.28f * gt;
                    float[] q = {-0.07f, 0.9f, 0.07f, 0.9f, sw + 0.06f, 0, sw - 0.06f, 0};
                    fillShape(q, ax, ay, az, hx, hz, sc, an.pr[i], an.pg[i], an.pb[i], kSun, s, hzc, fa, dep - 0.01f * L0, 0, 0, 0);
                }
                fillShape(Animals.PERSON_BODY, ax, ay, az, hx, hz, sc, an.cr[i], an.cg[i], an.cb[i], kSun, s, hzc, fa, dep - 0.03f, 0, 0, 0);
                fillShape(Animals.PERSON_HEAD, ax, ay, az, hx, hz, sc, 0.30f, 0.20f, 0.14f, kSun, s, hzc, fa, dep - 0.04f, 0, 0, 0);
                continue;
            }
            // Schattenfleck am Boden
            shadowBlob(ax, ay, az, hx, hz, sc * (bison ? 1.5f : 1.1f), sc * 0.55f, 0.35f + 0.35f * lit, dep);
            float[][] legs = bison ? Animals.BISON_LEGS : Animals.ELK_LEGS;
            float st = an.step[i], gt = an.gait[i];
            float lc = bison ? 0.028f : 0.05f, lcg = bison ? 0.020f : 0.035f, lcb = bison ? 0.014f : 0.022f;
            for (int L0 = 0; L0 < 4; L0++) {
                float[] lg = legs[L0];
                float sw = (float) Math.sin(st + lg[3]) * 0.22f * gt;
                float lift = Math.max(0, (float) Math.cos(st + lg[3])) * 0.08f * gt;
                float w = bison ? 0.11f : 0.06f;
                float[] q = {lg[0] - w, lg[1], lg[0] + w, lg[1], lg[0] + sw + w * 0.7f, lift, lg[0] + sw - w * 0.7f, lift};
                fillShape(q, ax, ay, az, hx, hz, sc, lc, lcg, lcb, kSun, s, hzc, fa, dep, 0, 0, 0);
            }
            float gr = an.graze[i];
            if (bison) {
                fillShape(Animals.BISON_BODY, ax, ay, az, hx, hz, sc, 0.075f, 0.050f, 0.032f, kSun, s, hzc, fa, dep, 0, 0, 0);
                fillShape(Animals.BISON_MANE, ax, ay, az, hx, hz, sc, 0.040f, 0.028f, 0.018f, kSun, s, hzc, fa, dep - 0.05f, 0, 0, 0);
            } else {
                fillShape(Animals.ELK_BODY, ax, ay, az, hx, hz, sc, 0.30f, 0.20f, 0.11f, kSun, s, hzc, fa, dep, 0, 0, 0);
                // Hals und Kopf senken sich beim Grasen um den Widerrist
                float rot = gr * 1.55f;
                fillShape(Animals.ELK_NECK, ax, ay, az, hx, hz, sc, 0.11f, 0.070f, 0.040f, kSun, s, hzc, fa, dep - 0.05f, rot, 0.55f, 1.30f);
                fillShape(Animals.ELK_RUMP, ax, ay, az, hx, hz, sc, 0.55f, 0.47f, 0.34f, kSun, s, hzc, fa, dep - 0.05f, 0, 0, 0);
                if (k == Animals.ELK_BULL) {
                    float[] A = Animals.ANTLER;
                    for (int j = 0; j < A.length; j += 4) {
                        double[] p0 = localToScreen(A[j], A[j + 1], ax, ay, az, hx, hz, sc, rot, 0.55f, 1.30f);
                        double[] p1 = localToScreen(A[j + 2], A[j + 3], ax, ay, az, hx, hz, sc, rot, 0.55f, 1.30f);
                        if (p0 == null || p1 == null) continue;
                        float br = 0.30f * (kSun * s.sunR + (s.upR + s.sideR) * 0.6f);
                        line(p0[0], p0[1], p1[0], p1[1], dep - 0.1f, br * 1.0f, br * 0.85f, br * 0.65f, 0.95f);
                    }
                }
            }
        }
    }

    /** Wagen, Bus, Fahrgastschiff und Segelboot: Schattenrisse aus Teilflächen; Fenster dunkel, nachts mit warmem Licht. */
    private void drawCraft(Animals an, int i, byte k, double ax, double ay, double az, double hx, double hz, float sc,
                           float kSun, Sky s, float[] hzc, float fa, float dep, float lit) {
        float r = an.cr[i], g = an.cg[i], b = an.cb[i], r2 = an.pr[i], g2 = an.pg[i], b2 = an.pb[i];
        float win = 0.012f;
        if (k == Animals.CAR || k == Animals.BUS) {
            boolean bus = k == Animals.BUS;
            shadowBlob(ax, ay, az, hx, hz, sc * (bus ? 6.5f : 2.3f), sc * (bus ? 1.5f : 1.0f), 0.35f + 0.3f * lit, dep);
            for (float[] w : bus ? Animals.BUS_WHEELS : Animals.CAR_WHEELS)
                fillShape(w, ax, ay, az, hx, hz, sc, 0.01f, 0.01f, 0.012f, kSun, s, hzc, fa, dep + 0.01f, 0, 0, 0);
            fillShape(bus ? Animals.BUS_BODY : Animals.CAR_BODY, ax, ay, az, hx, hz, sc, r, g, b, kSun, s, hzc, fa, dep, 0, 0, 0);
            fillShape(bus ? Animals.BUS_WIN : Animals.CAR_WIN, ax, ay, az, hx, hz, sc, win, win * 1.3f, win * 1.8f, kSun, s, hzc, fa, dep - 0.03f, 0, 0, 0);
        } else if (k == Animals.SHIP) {
            fillShape(Animals.SHIP_HULL, ax, ay, az, hx, hz, sc, r, g, b, kSun, s, hzc, fa, dep, 0, 0, 0);
            fillShape(Animals.SHIP_DECK, ax, ay, az, hx, hz, sc, r2, g2, b2, kSun, s, hzc, fa, dep - 0.02f, 0, 0, 0);
            fillShape(Animals.SHIP_UP, ax, ay, az, hx, hz, sc, r2, g2, b2, kSun, s, hzc, fa, dep - 0.02f, 0, 0, 0);
            fillShape(Animals.SHIP_FUNNEL, ax, ay, az, hx, hz, sc, 0.45f, 0.03f, 0.02f, kSun, s, hzc, fa, dep - 0.02f, 0, 0, 0);
            fillShape(Animals.SHIP_WIN, ax, ay, az, hx, hz, sc, win, win * 1.3f, win * 1.8f, kSun, s, hzc, fa, dep - 0.05f, 0, 0, 0);
            fillShape(Animals.SHIP_UPWIN, ax, ay, az, hx, hz, sc, win, win * 1.3f, win * 1.8f, kSun, s, hzc, fa, dep - 0.05f, 0, 0, 0);
        } else if (k == Animals.BOMBER) {
            fillShape(Animals.BOMBER_ENG, ax, ay, az, hx, hz, sc, 0.012f, 0.012f, 0.014f, kSun, s, hzc, fa, dep + 0.01f, 0, 0, 0);
            fillShape(Animals.BOMBER_WING, ax, ay, az, hx, hz, sc, 0.012f, 0.013f, 0.015f, kSun, s, hzc, fa, dep, 0, 0, 0);
            fillShape(Animals.BOMBER_BODY, ax, ay, az, hx, hz, sc, 0.014f, 0.015f, 0.017f, kSun, s, hzc, fa, dep - 0.01f, 0, 0, 0);
            fillShape(Animals.BOMBER_FIN, ax, ay, az, hx, hz, sc, 0.014f, 0.015f, 0.017f, kSun, s, hzc, fa, dep - 0.01f, 0, 0, 0);
        } else if (k == Animals.BUCKET) {
            fillShape(Animals.BUCKET_ROPE, ax, ay, az, hx, hz, sc, 0.02f, 0.02f, 0.022f, kSun, s, hzc, fa, dep + 0.01f, 0, 0, 0);
            fillShape(Animals.BUCKET_BODY, ax, ay, az, hx, hz, sc, 0.07f, 0.07f, 0.075f, kSun, s, hzc, fa, dep, 0, 0, 0);
        } else {
            fillShape(Animals.SAIL_HULL, ax, ay, az, hx, hz, sc, r, g, b, kSun, s, hzc, fa, dep, 0, 0, 0);
            fillShape(Animals.SAIL_MAIN, ax, ay, az, hx, hz, sc, r2, g2, b2, kSun, s, hzc, fa, dep - 0.02f, 0, 0, 0);
            fillShape(Animals.SAIL_JIB, ax, ay, az, hx, hz, sc, r2 * 0.9f, g2 * 0.9f, b2 * 0.9f, kSun, s, hzc, fa, dep - 0.02f, 0, 0, 0);
        }
    }

    /** Wie hell die Lampen brennen (0 Tag, 1 Nacht); für Schiffs- und Autolichter. */
    public float lampLevel() { return lampOn; }

    private double[] localToScreen(float u, float w, double ax, double ay, double az, double hx, double hz, float sc,
                                   float rot, float pvx, float pvy) {
        if (rot != 0) {
            float du = u - pvx, dw = w - pvy;
            float cs = (float) Math.cos(-rot), sn = (float) Math.sin(-rot);
            u = pvx + du * cs - dw * sn; w = pvy + du * sn + dw * cs;
        }
        return project(ax + hx * u * sc, ay + w * sc, az + hz * u * sc);
    }

    /** Füllt einen Umriss (x, y in Metern) in der Ebene des Tiers; rot dreht um (pvx, pvy). */
    private void fillShape(float[] pts, double ax, double ay, double az, double hx, double hz, float sc,
                           float r, float g, float b, float kSun, Sky s, float[] hzc, float fa, float dep,
                           float rot, float pvx, float pvy) {
        int m = pts.length / 2;
        for (int j = 0; j < m; j++) {
            double[] q = localToScreen(pts[2 * j], pts[2 * j + 1], ax, ay, az, hx, hz, sc, rot, pvx, pvy);
            if (q == null) return;
            polyX[j] = (float) q[0]; polyY[j] = (float) q[1];
        }
        float amb = 0.55f;
        float cr = r * (s.sunR * kSun + (s.upR + s.sideR) * amb), cg = g * (s.sunG * kSun + (s.upG + s.sideG) * amb),
                cb = b * (s.sunB * kSun + (s.upB + s.sideB) * amb);
        cr += (hzc[0] - cr) * fa; cg += (hzc[1] - cg) * fa; cb += (hzc[2] - cb) * fa;
        fillPoly(m, dep, cr, cg, cb, false, 0);
    }

    /** Gefülltes Vieleck (gerade-ungerade) mit Tiefenprüfung; shade: nur abdunkeln um den Faktor dark. */
    private void fillPoly(int m, float dep, float cr, float cg, float cb, boolean shade, float dark) {
        float y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int j = 0; j < m; j++) { y0 = Math.min(y0, polyY[j]); y1 = Math.max(y1, polyY[j]); }
        int ya = Math.max(0, (int) Math.ceil(y0 - 0.5f)), yb = Math.min(H - 1, (int) Math.floor(y1 - 0.5f));
        if (ya > yb) {
            // kleiner als ein Pixel: ein Punkt
            float cx = 0, cy = 0;
            for (int j = 0; j < m; j++) { cx += polyX[j]; cy += polyY[j]; }
            int px = (int) (cx / m), py = (int) (cy / m);
            if (!shade && px >= 0 && py >= 0 && px < W && py < H && gz[py * W + px] > dep) {
                int p = py * W + px;
                hr[p] += (cr - hr[p]) * 0.5f; hg[p] += (cg - hg[p]) * 0.5f; hb[p] += (cb - hb[p]) * 0.5f;
            }
            return;
        }
        float[] xs = new float[m];
        for (int yy = ya; yy <= yb; yy++) {
            float sy = yy + 0.5f;
            int cnt = 0;
            for (int j = 0, k = m - 1; j < m; k = j++) {
                float ya2 = polyY[k], yb2 = polyY[j];
                if ((ya2 <= sy && yb2 > sy) || (yb2 <= sy && ya2 > sy)) {
                    xs[cnt++] = polyX[k] + (sy - ya2) / (yb2 - ya2) * (polyX[j] - polyX[k]);
                }
            }
            java.util.Arrays.sort(xs, 0, cnt);
            int row = yy * W;
            for (int c2 = 0; c2 + 1 < cnt; c2 += 2) {
                int xa = Math.max(0, (int) Math.ceil(xs[c2] - 0.5f)), xb = Math.min(W - 1, (int) Math.floor(xs[c2 + 1] - 0.5f));
                for (int xx = xa; xx <= xb; xx++) {
                    int p = row + xx;
                    if (shade) {
                        float d = gz[p];
                        if (d >= Float.MAX_VALUE || Math.abs(d - dep) > 3 + dep * 0.01f || gm[p] == Mat.ANIMAL + 1) continue;
                        hr[p] *= dark; hg[p] *= dark; hb[p] *= dark;
                    } else {
                        if (gz[p] <= dep) continue;
                        hr[p] = cr; hg[p] = cg; hb[p] = cb;
                        gz[p] = dep;
                        gm[p] = (byte) (Mat.ANIMAL + 1);
                    }
                }
            }
        }
    }

    /** Weicher Schattenfleck unter einem Tier (Ellipse am Boden). */
    private void shadowBlob(double ax, double ay, double az, double hx, double hz, float len, float wid, float dark, float dep) {
        int m = 12;
        for (int j = 0; j < m; j++) {
            double a = j * 2 * Math.PI / m;
            double u = Math.cos(a) * len, v = Math.sin(a) * wid;
            double[] q = project(ax + hx * u - hz * v, ay + 0.03, az + hz * u + hx * v);
            if (q == null) return;
            polyX[j] = (float) q[0]; polyY[j] = (float) q[1];
        }
        fillPoly(m, dep, 0, 0, 0, true, 1 - dark * 0.6f);
    }

    // ------------------------------------------------------------ Thermografie

    /** Wärmebild statt Farben: Temperatur aus dem Modell, Farbskala „Ironbow“ von −20 bis 100 °C. */
    public volatile boolean thermo;
    public static final float THERMO_LO = -30, THERMO_HI = 100;
    /** Skala gestreckt im Kalten (Potenz 0,55), damit Wiese, Wald und Sinter unterscheidbar bleiben. */
    static int thermoIndex(float t) {
        float u = (t - THERMO_LO) / (THERMO_HI - THERMO_LO);
        if (u <= 0) return 0;
        if (u >= 1) return 255;
        return (int) (255 * Math.pow(u, 0.55));
    }
    private float[] tmp = new float[0];
    private static final int[] IRON = new int[256];

    static {
        // Schwarz → Violett → Rot → Orange → Gelb → Weiß
        float[][] k = {{0, 0, 0, 0}, {0.18f, 0.20f, 0.02f, 0.40f}, {0.38f, 0.55f, 0.05f, 0.55f}, {0.58f, 0.86f, 0.25f, 0.15f},
                {0.78f, 0.98f, 0.62f, 0.02f}, {0.92f, 1f, 0.90f, 0.30f}, {1f, 1f, 1f, 0.95f}};
        for (int i = 0; i < 256; i++) {
            float t = i / 255f;
            int j = 0;
            while (j < k.length - 2 && t > k[j + 1][0]) j++;
            float u = (t - k[j][0]) / (k[j + 1][0] - k[j][0]);
            int r = (int) (255 * (k[j][1] + (k[j + 1][1] - k[j][1]) * u));
            int g = (int) (255 * (k[j][2] + (k[j + 1][2] - k[j][2]) * u));
            int b = (int) (255 * (k[j][3] + (k[j + 1][3] - k[j][3]) * u));
            IRON[i] = (Math.max(0, Math.min(255, r)) << 16) | (Math.max(0, Math.min(255, g)) << 8) | Math.max(0, Math.min(255, b));
        }
    }

    /** Farbe der Skala für eine Temperatur (für die Legende). */
    public static int ironbow(float t) {
        return IRON[thermoIndex(t)];
    }

    private void thermoStrip(int y0, int y1, boolean hasP) {
        if (y0 >= y1) return;
        if (tmp.length < W * H) { synchronized (this) { if (tmp.length < W * H) tmp = new float[W * H + W]; } }
        final Thermal th = Materials.terrain == null ? null : Materials.terrain.thermal;
        final float amb = Thermal.ambient;
        final Sky s = sky;
        final LightingEngine li = L;
        final boolean sunUp = s.sun[1] > 0;
        final float sx = (float) s.sun[0], sy = (float) s.sun[1], sz = (float) s.sun[2];
        final float[] gmT = new float[5];
        for (int py = y0; py < y1; py++) {
            float b = (float) ((H / 2.0 - py - 0.5) / pfy);
            for (int px = 0; px < W; px++) {
                int p = py * W + px;
                float a = colA[px];
                float dx = (float) (cfx + crx * a + cux * b), dy = (float) (cfy + cry * a + cuy * b), dz = (float) (cfz + crz * a + cuz * b);
                float z = gz[p], t;
                if (z >= Float.MAX_VALUE) {
                    float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    float up = Math.max(0, dy / l);
                    t = amb - 8 - 32 * (float) Math.sqrt(up);          // klarer Himmel: kalt, zum Zenit kälter
                } else {
                    float wx = (float) (ex + dx * z), wy = (float) (ey + dy * z), wz = (float) (ez + dz * z);
                    int m = gm[p] - 1;
                    float sol = 0;
                    if (sunUp && m != Mat.POOL && m != Mat.WATER && m != Mat.RIVER && m != Mat.ANIMAL) {
                        float nd = Math.max(0, gnx[p] * sx + gny[p] * sy + gnz[p] * sz);
                        sol = nd > 0 ? nd * li.lit(wx + gnx[p] * 0.4f, wy + gny[p] * 0.4f, wz + gnz[p] * 0.4f, 0.6, true) : 0;
                    }
                    switch (m) {
                        case Mat.ANIMAL: t = 30; break;
                        case Mat.TERRAIN: {
                            // heller Sinter wirft die Sonne zurück und bleibt kühler als Wiese und Wald
                            Materials.terrain.ground(wx, wz, gmT);
                            float gain = 15 - 8 * gmT[3] - 5 * gmT[4];
                            t = (th == null ? amb : th.temp(wx, wz)) + gain * sol;
                            break;
                        }
                        case Mat.SINTER: case Mat.RIM: case Mat.CONE: case Mat.POOL: case Mat.WATER:
                            t = (th == null ? amb : th.temp(wx, wz)) + 7 * sol; break;
                        case Mat.RIVER: t = amb + 3; break;
                        default: t = amb + 9 * sol;
                    }
                }
                if (hasP) {
                    int q = Math.min(PH - 1, py >> 1) * PW + Math.min(PW - 1, px >> 1);
                    float T = pt[q];
                    if (T < 0.999f) t = t * T + pT[q] * (1 - T);
                }
                tmp[p] = t;
            }
        }
    }

    private void thermoOut(int[] out, int y0, int y1) {
        for (int py = y0; py < y1; py++) {
            for (int px = 0; px < W; px++) {
                int p = py * W + px;
                float t = tmp[p] + (((Noise.hash(px, py, 1) & 255) / 255f) - 0.5f) * 0.4f;
                out[p] = IRON[thermoIndex(t)];
            }
        }
    }

    /** Farbstil: Sättigung, Lebendigkeit, Wärme der Lichter gegen die Tiefen (0 = neutral). */
    public static volatile float GRADE_SAT = 1.08f, GRADE_VIB = 0.34f, GRADE_WARM = 0.12f;
    /** Schwarzweiß 0..1: Grauwert wie hinter einem Gelbfilter (Blau dunkler, der Himmel wird satt grau). */
    public static volatile float GRADE_MONO = 0;
    /** Weißabgleich für das ganze Bild: > 0 wärmer, < 0 kühler. */
    public static volatile float GRADE_TINT = 0;

    /** Farbstile zur Auswahl: Name und Sättigung, Lebendigkeit, Wärme der Lichter, Schwarzweiß, Weißabgleich. */
    public static final String[] STYLES = {"Natürlich", "Neutral", "Kodachrome", "Abendgold", "Winterblau", "Schwarzweiß (Ansel Adams)"};
    private static final float[][] STYLE_V = {
            {1.08f, 0.34f, 0.12f, 0, 0}, {1.0f, 0, 0, 0, 0}, {1.36f, 0.18f, 0.24f, 0, 0.02f},
            {1.14f, 0.30f, 0.22f, 0, 0.13f}, {0.86f, 0.18f, 0.06f, 0, -0.11f}, {1.0f, 0, 0, 1, 0}};

    public static void setStyle(int i) {
        float[] v = STYLE_V[Math.max(0, Math.min(STYLE_V.length - 1, i))];
        GRADE_SAT = v[0]; GRADE_VIB = v[1]; GRADE_WARM = v[2]; GRADE_MONO = v[3]; GRADE_TINT = v[4];
    }

    /** hl − 0,5 mit hl = L / (L + 0,35), für L = i / 512. */
    private static final float[] WARM_LUT = new float[4096];
    static {
        for (int i = 0; i < 4096; i++) { float l = i / 512f; WARM_LUT[i] = l / (l + 0.35f) - 0.5f; }
    }

    private void tonemap(int[] out, int y0, int y1) {
        float e = (float) exposure;
        final float gSat = GRADE_SAT, gVib = GRADE_VIB, gWarm = GRADE_WARM, gMono = GRADE_MONO, gTint = GRADE_TINT;
        for (int py = y0; py < y1; py++) {
            for (int px = 0; px < W; px++) {
                int p = py * W + px;
                float d = ((Noise.hash(px, py, 1) & 255) / 255f - 0.5f) / 255f;
                float cr0 = hr[p] * e, cg0 = hg[p] * e, cb0 = hb[p] * e;
                // Farbstil: Lebendigkeit (blasse Farben kräftiger, satte kaum), warme Lichter, kühle Tiefen
                float lum = 0.2126f * cr0 + 0.7152f * cg0 + 0.0722f * cb0;
                if (lum > 1e-6f) {
                    float mx = Math.max(cr0, Math.max(cg0, cb0)), mn = Math.min(cr0, Math.min(cg0, cb0));
                    float k = gSat + gVib * mn / (mx + 1e-6f);          // = SAT + VIB · (1 − Sättigung)
                    cr0 = lum + (cr0 - lum) * k; cg0 = lum + (cg0 - lum) * k; cb0 = lum + (cb0 - lum) * k;
                    int li = (int) (lum * 512);
                    float w = WARM_LUT[li < 4095 ? li : 4095] * gWarm;  // Wärme nach Helligkeit, −0,5 … +0,5
                    cr0 *= 1 + w + gTint; cg0 *= 1 + gTint * 0.35f; cb0 *= 1 - w - gTint;
                    if (cr0 < 0) cr0 = 0; if (cg0 < 0) cg0 = 0; if (cb0 < 0) cb0 = 0;
                    if (gMono > 0) {
                        // Gelbfilter: Rot und Grün tragen, Blau kaum; dazu etwas mehr Kontrast
                        float yv = 0.42f * cr0 + 0.50f * cg0 + 0.08f * cb0;
                        yv = yv * yv / (yv + 0.06f) * 1.08f;
                        cr0 += (yv - cr0) * gMono; cg0 += (yv - cg0) * gMono; cb0 += (yv - cb0) * gMono;
                    }
                }
                int r = tm(cr0, d), g = tm(cg0, d), b = tm(cb0, d);
                out[p] = (r << 16) | (g << 8) | b;
            }
        }
    }

    /** Filmkurve (ACES-Näherung), Gamma und eine Spur Rauschen gegen Stufen. */
    private static int tm(float x, float dither) {
        float g;
        if (x <= 0) g = 0;
        else if (x >= CURVE_MAX) g = curve(x);
        else {
            // Tabelle über √x: im Dunkeln fein, wo die Kurve steil ist
            float f = (float) Math.sqrt(x * (1f / CURVE_MAX)) * CURVE_N;
            int i = (int) f;
            g = CURVE[i] + (CURVE[i + 1] - CURVE[i]) * (f - i);
        }
        int v = (int) ((g + dither) * 255 + 0.5f);
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static float curve(float x) {
        float y = (x * (2.51f * x + 0.03f)) / (x * (2.43f * x + 0.59f) + 0.14f);
        if (y < 0) y = 0; else if (y > 1) y = 1;
        return GAMMA[(int) (y * 4096)];
    }

    private static final int CURVE_N = 4096;
    private static final float CURVE_MAX = 16;
    private static final float[] CURVE = new float[CURVE_N + 2];

    static {
        for (int i = 0; i <= CURVE_N + 1; i++) {
            float u = i / (float) CURVE_N;
            CURVE[i] = curve(u * u * CURVE_MAX);
        }
    }
}
