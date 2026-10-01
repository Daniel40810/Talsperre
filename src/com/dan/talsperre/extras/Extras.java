package com.dan.talsperre.extras;

import com.dan.talsperre.core.Animals;
import com.dan.talsperre.core.Camera;
import com.dan.talsperre.core.Engine3D;
import com.dan.talsperre.core.Sprites;
import com.dan.talsperre.core.Terrain;
import com.dan.talsperre.effects.ParticleSystem;
import com.dan.talsperre.water.WaterWorks;
import com.dan.talsperre.world.Dam;
import com.dan.talsperre.world.Flood;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.Random;

/**
 * Die Zugaben der Phase 9 als Zustandsmaschine: Mauerschnitt, Bauzeitraffer 1908–14 und die Nacht des 17. Mai 1943
 * (Bresche mit Flutwelle). Der Bildrechner wird nur über Mesh-Zustände (Gruppen, Bauhöhe, Flutfront), den Pegel und
 * Teilchen gesteuert; alle Bewegung läuft hier aus der Uhr des Ablaufs. Das Ganze ist eine geschichtliche Darstellung
 * ohne technische Einzelheiten.
 */
public final class Extras {
    public static final int NONE = 0, SECTION = 1, BREACH = 2, BUILD = 3;
    /** Dauer der Abläufe in Sekunden. */
    public static final double BUILD_SECONDS = 72, BREACH_SECONDS = 96;
    /** Bauhöhe, ab der die Mauer samt Türmen fertig ist, und Anteil des Ablaufs, in dem sie wächst. */
    private static final double B0 = Dam.BASE - 0.5, B1 = 31, WALL_END = 0.62, DONE = 0.68;

    private volatile int scene = NONE;
    private double t;
    private boolean saved;
    private double savedLevel;
    private int lastMask = -1;
    private double lightClock;
    private final Random rnd = new Random(1943);
    private double acc, acc2, accFront;
    private final boolean[] fired = new boolean[24];

    /** Zeit und Tag, die der Ablauf verlangt (−1 = keine Änderung); die Ansicht holt sie ab. */
    public volatile int wantDay = -1;
    public volatile double wantHour;
    /** Die Ansicht soll Schatten und Himmel neu rechnen. */
    public volatile boolean lightDirty;
    /** Zustände für den Ton: Bomber (0..1, Nähe), Wassermassen (0..1) und Detonationen (klingen ab). */
    public volatile double sndBomber, sndBreach, sndBlast;

    public int scene() { return scene; }
    public boolean running() { return scene == BUILD || scene == BREACH; }
    public boolean suppressLife() { return scene == BUILD || scene == BREACH; }
    public double clock() { return t; }

    // ------------------------------------------------------------ Steuerung

    public void setSection(boolean on) {
        if (running()) return;
        scene = on ? SECTION : NONE;
    }

    public void startBuild() {
        scene = BUILD; t = 0; saved = false;
        wantDay = 200; wantHour = 10.5;
        rememberLevel(null);
    }

    public void startBreach() {
        scene = BREACH; t = 0; saved = false;
        java.util.Arrays.fill(fired, false);
        wantDay = 137; wantHour = 0.4;
        sndBlast = 0;
    }

    /** Zurück zum Normalbetrieb: alles sichtbar wie vorher, Pegel wie vorher. */
    public void reset() { scene = NONE; t = 0; resetPending = true; }
    private volatile boolean resetPending;

    private double[] savedGates;

    /** Merkt sich Pegel und Ablässe und schließt die Ablässe (die Abläufe haben ihr eigenes Wasser). */
    private void rememberLevel(WaterWorks w) {
        if (w == null || saved) return;
        savedLevel = w.level;
        savedGates = w.gate.clone();
        w.setAllGates(0);
        saved = true;
    }

    private void restore(WaterWorks w) {
        if (!saved) return;
        w.level = savedLevel;
        if (savedGates != null) System.arraycopy(savedGates, 0, w.gate, 0, Math.min(5, savedGates.length));
        saved = false;
    }

    // ------------------------------------------------------------ Takt

    public void update(double dt, Engine3D r, WaterWorks ww, Animals an, Sprites sp, Terrain terrain) {
        dt = Math.min(dt, 0.1);
        com.dan.talsperre.core.Mesh m = r.mesh();
        int sc = scene;
        if (resetPending) {
            resetPending = false;
            m.setBuild(1e6f); m.setFlood(-1f);
            restore(ww);
            sndBomber = sndBreach = 0;
            lightDirty = true;
        }
        if (sc == BUILD || sc == BREACH) rememberLevel(ww);
        int want = Dam.hideMask(sc == SECTION ? Dam.MODE_SECTION : sc == BREACH && t >= breachAt() ? Dam.MODE_BREACH : Dam.MODE_NORMAL);
        if (sc == BUILD && t < BUILD_SECONDS * DONE) want |= 1 << 3;
        if (want != lastMask) { lastMask = want; m.setHide(want); lightDirty = true; }
        if (sc == BUILD) { build(dt, m, ww, an, sp, terrain); }
        else if (sc == BREACH) { breach(dt, r, m, ww, an, sp, terrain); }
        else if (m.buildY < 1e5f && !resetPending) { m.setBuild(1e6f); }
    }

    // ------------------------------------------------------------ Bauzeitraffer

    /** Bauhöhe zum Ablaufanteil p. */
    private static double buildHeight(double p) {
        double u = Math.max(0, Math.min(1, p / WALL_END));
        return B0 + (B1 - B0) * u;
    }

    private void build(double dt, com.dan.talsperre.core.Mesh m, WaterWorks ww, Animals an, Sprites sp, Terrain terrain) {
        t += dt;
        double p = t / BUILD_SECONDS;
        lastLevel = ww.level;
        if (p >= 1) { scene = NONE; restore(ww); m.setBuild(1e6f); ww.level = 0; lightDirty = true; return; }
        double by = buildHeight(p);
        if (p >= DONE) { by = 1e6; ww.level = WaterWorks.LEVEL_MIN + (0 - WaterWorks.LEVEL_MIN) * Math.min(1, (p - DONE) / (1 - DONE) * 1.05); }
        else ww.level = WaterWorks.LEVEL_MIN;
        if (Math.abs(m.buildY - by) > 0.03 || (by > 1e5 && m.buildY < 1e5f)) m.setBuild((float) by);
        lightClock += dt;
        if (lightClock > 0.6) { lightClock = 0; lightDirty = true; }
        // Arbeiter auf der Mauerkrone, Kübel am Kabelkran
        double top = Math.min(by, Dam.TOP + 0.1);
        if (by < 1e5) {
            for (int i = 0; i < 14; i++) {
                double u = (i * 0.61803 + 0.13) % 1.0;
                double phi = Math.toRadians(-36 + 72 * u) + Math.sin(t * 0.15 + i) * 0.02;
                double r0 = Dam.R - 1.2 - 3.4 * ((i * 0.37) % 1.0);
                double y = by < Dam.TOP ? by : Dam.TOP;
                int k = an.add(Animals.PERSON, Dam.x(phi, r0), y, Dam.z(phi, r0), Math.cos(phi) * (i % 2 == 0 ? 1 : -1), -Math.sin(phi) * (i % 2 == 0 ? 1 : -1), 1);
                if (k >= 0) {
                    an.step[k] = (float) (t * 5 + i); an.gait[k] = 0.8f;
                    float c = 0.03f + 0.04f * ((i * 7) % 5) / 4f;
                    an.cr[k] = c * 1.6f; an.cg[k] = c * 1.3f; an.cb[k] = c; an.pr[k] = 0.02f; an.pg[k] = 0.02f; an.pb[k] = 0.025f;
                }
            }
            // Kübel pendelt zwischen den Masten
            double[] c0 = cable(0.5 + 0.5 * Math.sin(t * 0.35));
            int k = an.add(Animals.BUCKET, c0[0], c0[1] - 72, c0[2], 1, 0, 1);
            if (k >= 0) { an.cr[k] = an.cg[k] = an.cb[k] = 0.05f; }
        }
    }

    /** Punkt auf dem Tragseil des Kabelkrans (0 West .. 1 Ost). */
    public static double[] cable(double u) {
        double x = -170 + 340 * u;
        return new double[]{x, 74 - 5.5 * 4 * u * (1 - u), -48};
    }

    /** Datum und Bauhöhe für die Tafel (null, wenn kein Bauzeitraffer läuft). */
    public String[] buildLabel() {
        if (scene != BUILD) return null;
        double p = t / BUILD_SECONDS;
        String[] mon = {"Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"};
        String when, what;
        if (p < DONE) {
            double months = 4 + 72 * Math.min(1, p / DONE);       // ab Mai 1908
            int mi = (int) months;
            when = mon[mi % 12] + " " + (1908 + mi / 12);
            double h = Math.min(buildHeight(p), Dam.TOP) - Dam.BASE;
            what = p < WALL_END ? String.format("Mauerhöhe %.0f m von 48 m", Math.max(0, h)) : "Kraftwerk und Türme";
        } else {
            double q = (p - DONE) / (1 - DONE);
            int mi = (int) (4 + 12 * q);
            when = mon[(mi + 7) % 12] + " " + (1914 + (mi + 7) / 12);
            what = String.format("Einstau: Pegel %.0f m unter Stauziel", -Math.min(0, lastLevel));
        }
        return new String[]{when, what};
    }
    private volatile double lastLevel;

    // ------------------------------------------------------------ Nacht des 17. Mai 1943

    private static double breachAt() { return 11.4; }

    private void breach(double dt, Engine3D r, com.dan.talsperre.core.Mesh m, WaterWorks ww, Animals an, Sprites sp, Terrain terrain) {
        t += dt;
        ParticleSystem ps = ww.particles;
        sndBlast *= Math.exp(-dt * 0.9);
        double ta = breachAt();
        // Bomber im Tiefflug über dem See: drei Maschinen, versetzt
        double near = 0;
        for (int b = 0; b < 3; b++) {
            double t0 = 1.5 + 1.5 * b, v = 78;
            double s = (t - t0) * v;                 // Weg ab 700 m südlich der Mauer
            double u = Math.min(1, s / 700.0);
            double z = 700 - s, x = (130 - 40 * b) * (1 - u) + 20 * b * u;
            if (s < 0 || z < -420) continue;
            double hdx = -(130 - 40 * b - 20 * b) / 700.0, hdz = -1;
            double y = 20 + 2 * Math.sin(t + b);
            int k = an.add(Animals.BOMBER, x, y, z, hdx, hdz, 1.0);
            if (k >= 0) {
                // Auspuffglut der vier Motoren
                double l = Math.hypot(hdx, hdz), px = -hdz / l, pz = hdx / l;
                for (int e = 0; e < 4; e++) {
                    double o = (e - 1.5) * 6.2;
                    sp.add(Sprites.GLOW, x + px * o, y + 0.6, z + pz * o, 2.2, 1.0, 0.35, 0.08, 0.7);
                }
            }
            double d = Math.hypot(x, z);
            near = Math.max(near, 1 / (1 + d / 220.0));
        }
        sndBomber = near;
        // Treffer: Wassersäulen und Stöße an der Krone
        double[] hitT = {7.4, 9.0, 10.4};
        double[] hitX = {-6, 26, 44};
        for (int h = 0; h < 3; h++) {
            if (!fired[h] && t >= hitT[h]) {
                fired[h] = true;
                sndBlast = 1;
                for (int i = 0; i < 700; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, sp0 = 3 + rnd.nextDouble() * 9;
                    ps.spawn(ParticleSystem.SPRAY, (float) (hitX[h] + rnd.nextGaussian() * 2.5), 0.4f, (float) (6 + rnd.nextGaussian() * 2),
                            (float) (Math.cos(a) * sp0 * 0.5), (float) (14 + rnd.nextDouble() * 22), (float) (Math.sin(a) * sp0 * 0.5 + 2),
                            (float) (1.6 + rnd.nextDouble() * 1.8), (float) (3.2 + rnd.nextDouble() * 1.6), 0.45f, 0f, -1);
                }
                for (int i = 0; i < 90; i++) {
                    ps.spawn(ParticleSystem.STEAM, (float) (hitX[h] + rnd.nextGaussian() * 3), 1f, (float) (6 + rnd.nextGaussian() * 3),
                            (float) (rnd.nextGaussian() * 2), (float) (3 + rnd.nextDouble() * 5), (float) (rnd.nextGaussian() * 2),
                            (float) (6 + rnd.nextDouble() * 6), (float) (6 + rnd.nextDouble() * 4), 0.16f, 0f, -1);
                }
            }
        }
        for (int h = 0; h < 3; h++) {
            for (int k = 0; k < 3; k++) {
                int fi = 3 + h * 3 + k;
                double tk = hitT[h] - (1.7 - 0.6 * k);
                if (!fired[fi] && t >= tk) {
                    fired[fi] = true;
                    double zz = 6 + (180 - 70 * k), xx = hitX[h] * (1 - (zz - 6) / 400.0) + 6 * k;
                    for (int i = 0; i < 90; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, sp0 = 1 + rnd.nextDouble() * 3;
                        ps.spawn(ParticleSystem.SPRAY, (float) (xx + rnd.nextGaussian()), 0.3f, (float) (zz + rnd.nextGaussian()),
                                (float) (Math.cos(a) * sp0), (float) (5 + rnd.nextDouble() * 6), (float) (Math.sin(a) * sp0 - 3),
                                (float) (1.0 + rnd.nextDouble()), (float) (1.8 + rnd.nextDouble()), 0.4f, 0f, -1);
                    }
                }
            }
        }
        if (t < ta) { ww.level = -0.6; m.setFlood(-1f); sndBreach = 0; return; }
        double tau = t - ta;
        double lv = -19.6 * (1 - Math.exp(-tau / 21.0));
        ww.level = lv;
        double head = Math.max(0.3, lv - Dam.WY);
        double fl = Math.min(1, head / 8.0);
        sndBreach = fl * Math.min(1, tau / 4);
        double v0 = 3 + 0.75 * Math.sqrt(2 * 9.81 * head);
        // Strahl über die Bruchkante: Gischt und Tropfen auf ganzer Breite
        double rl = Dam.R - Dam.width(Dam.WY);
        int ns = poissonRate(5500 * fl * Math.min(1, tau / 2), dt), nd = poissonRate(2600 * fl * Math.min(1, tau / 2), dt);
        for (int i = 0; i < ns + nd; i++) {
            double ph = Dam.WPA + (Dam.WPB - Dam.WPA) * (0.04 + 0.92 * rnd.nextDouble());
            double sn = Math.sin(ph), cs = Math.cos(ph);
            double r0 = rl + 1 - rnd.nextDouble() * 1.5;
            double sp0 = v0 * (0.85 + 0.3 * rnd.nextDouble());
            double yy = Dam.WY + 0.5 + Math.min(head, 3 + 0) * rnd.nextDouble();
            boolean spray = i < ns;
            ps.spawn(spray ? ParticleSystem.SPRAY : ParticleSystem.DROP, (float) (r0 * sn), (float) yy, (float) (Dam.CZ + r0 * cs),
                    (float) (-sn * sp0 + rnd.nextGaussian() * 0.8), (float) (rnd.nextGaussian() * 0.8), (float) (-cs * sp0 + rnd.nextGaussian() * 0.8),
                    spray ? (float) (2.4 + rnd.nextDouble() * 2.2) : 0.12f, (float) (2.8 + rnd.nextDouble()), spray ? 0.34f : 0.8f, (float) Dam.BASIN_Y, -1);
        }
        // Gischtnebel über dem Tosbecken
        int nm = poissonRate(60 * fl, dt);
        for (int i = 0; i < nm; i++) {
            ps.spawn(ParticleSystem.STEAM, (float) ((rnd.nextDouble() - 0.5) * 70), (float) (Dam.BASIN_Y + 1), (float) (-50 - rnd.nextDouble() * 50),
                    (float) (rnd.nextGaussian()), 2f, (float) (-2 - rnd.nextDouble() * 2), (float) (6 + rnd.nextDouble() * 5), (float) (9 + 5 * rnd.nextDouble()),
                    0.12f, (float) Dam.BASIN_Y, -1);
        }
        // Flutwelle
        double sFront = tau <= 0.5 ? -1 : 6 + 24 * tau + 0.18 * tau * tau;
        m.setFlood((float) sFront);
        if (sFront > 0 && sFront < Flood.length() + 40) {
            double[] o = new double[3];
            Flood.at(sFront, o);
            int nf = poissonRate(900, dt);
            for (int i = 0; i < nf; i++) {
                double ox = (rnd.nextDouble() - 0.5) * 70, oz = (rnd.nextDouble() - 0.5) * 24;
                double gy = Math.max(terrain.sample(o[0] + ox, o[2] + oz), o[1]) + Flood.depth(sFront) * 0.5;
                ps.spawn(ParticleSystem.SPRAY, (float) (o[0] + ox), (float) gy, (float) (o[2] + oz),
                        (float) (rnd.nextGaussian() * 2), (float) (4 + rnd.nextDouble() * 9), (float) (-8 - rnd.nextDouble() * 8),
                        (float) (2 + rnd.nextDouble() * 2.5), (float) (1.8 + rnd.nextDouble()), 0.4f, (float) (gy - 6), -1);
            }
        }
        if (tau > BREACH_SECONDS - ta) { scene = BREACH; t = BREACH_SECONDS - 0.01; }
    }

    private int poissonRate(double rate, double dt) {
        double mean = rate * dt;
        int n = (int) Math.floor(mean);
        if (rnd.nextDouble() < mean - n) n++;
        return n;
    }

    // ------------------------------------------------------------ Beschriftung

    private static final Object[][] SECTION_LABELS = {
            {Dam.R - 3.0, Dam.TOP + 0.8, "Krone, 6 m breit"},
            {Dam.R - 10.0, -2.0, "Gewichtsmauer aus Grauwacke-Bruchstein"},
            {0.0, -8.0, "Kontrollgang"},
            {0.0, -26.0, "Kontrollgang, Entwässerung"},
            {272.0, GATE_Y(), "Grundablass 3,4 × 3,4 m"},
            {Dam.R - 8.0, -52.0, "Fundament im Fels"},
            {Dam.R + 3.0, 0.3, "Stauziel"}
    };
    private static double GATE_Y() { return Dam.GATE_Y; }

    /** Zeichnet die Beschriftung des Mauerschnitts und die Tafel des Bauzeitraffers über das Bild. */
    public void paint(Graphics2D g, Camera cam, int pw, int ph, int iy, int ih) {
        int sc = scene;
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        if (sc == SECTION) {
            Font f = new Font("SansSerif", Font.PLAIN, 12);
            g.setFont(f);
            double ph0 = Dam.WPA;
            for (Object[] L : SECTION_LABELS) {
                double r = ((Number) L[0]).doubleValue(), y = ((Number) L[1]).doubleValue();
                String txt = (String) L[2];
                double rr = txt.startsWith("Kontroll") ? Dam.R - Dam.width(y) + 2 : r;
                double[] p = Dam.sectionPoint(ph0, rr, y);
                double[] s = project(cam, p[0], p[1], p[2], pw, ph, iy, ih);
                if (s == null) continue;
                int x = (int) s[0], yy = (int) s[1];
                int w = g.getFontMetrics().stringWidth(txt);
                int tx = Math.min(pw - w - 14, x + 26), ty = yy - 18;
                g.setColor(new Color(8, 14, 18, 175));
                g.fillRoundRect(tx - 6, ty - 14, w + 12, 20, 8, 8);
                g.setColor(new Color(255, 255, 255, 190));
                g.drawLine(x, yy, tx - 6, ty - 4);
                g.fillOval(x - 3, yy - 3, 7, 7);
                g.setColor(new Color(235, 245, 245));
                g.drawString(txt, tx, ty);
            }
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.setColor(new Color(235, 245, 245, 230));
            g.drawString("Mauerschnitt", 28, iy + 34);
            g.setFont(f);
            g.setColor(new Color(190, 205, 208, 220));
            g.drawString("48 m hoch · Krone 6 m · Fuß 36 m breit · Radius 300 m", 28, iy + 54);
        }
        String[] bl = buildLabel();
        if (bl != null) {
            g.setColor(new Color(8, 14, 18, 170));
            g.fillRoundRect(28, iy + 20, 300, 70, 12, 12);
            g.setFont(new Font("SansSerif", Font.BOLD, 26));
            g.setColor(new Color(240, 246, 246));
            g.drawString(bl[0], 44, iy + 56);
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.setColor(new Color(190, 205, 208));
            g.drawString(bl[1], 44, iy + 78);
        }
        if (sc == BREACH) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(new Color(190, 205, 208, 210));
            g.drawString("Nachstellung der Ereignisse vom 16./17. Mai 1943 · Zeitraffer", 28, iy + 34);
        }
    }

    /** Bildschirmpunkt (x, y) oder null, wenn hinter der Kamera. */
    static double[] project(Camera c, double x, double y, double z, int pw, int ph, int iy, int ih) {
        double dx = x - c.ex, dy = y - c.ey, dz = z - c.ez;
        double zc = dx * c.fx + dy * c.fy + dz * c.fz;
        if (zc < 0.5) return null;
        double xc = dx * c.rx + dy * c.ry + dz * c.rz, yc = dx * c.ux + dy * c.uy + dz * c.uz;
        double tanY = Math.tan(c.fovY / 2) * ih / (double) ph, tanX = tanY * pw / (double) ih;
        double sx = pw / 2.0 + (xc / zc) / tanX * pw / 2.0;
        double sy = iy + ih / 2.0 - (yc / zc) / tanY * ih / 2.0;
        return new double[]{sx, sy};
    }
}
