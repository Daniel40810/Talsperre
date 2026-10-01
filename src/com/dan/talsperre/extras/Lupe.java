package com.dan.talsperre.extras;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Die Mineral-Lupe: ein Linsenbild über der Ansicht, das vom Gestein der Mauer (Grauwacke) bis zum Atom hinabführt.
 * Vier Stufen: Handstück, Dünnschliff im polarisierten Licht, Kristallgitter des Quarzes (SiO₂) und ein Siliziumatom
 * nach Bohr. Alles wird hier mit Java2D gezeichnet; die Stufe wechselt mit dem Mausrad über der Linse oder im Bedienfeld.
 */
public final class Lupe {
    public static final String[] NAMES = {"Handstück", "Dünnschliff", "Kristallgitter", "Atom"};
    private static final String[][] TEXT = {
            {"Grauwacke, Handstück", "Dunkelgrauer „schmutziger“ Sandstein: eckige Körner von Quarz, Feldspat und Gesteinsbruchstücken in einer feinen Grundmasse aus Ton und Chlorit. Sie entstand im Devon und Karbon aus Trübeströmen im Meer, wurde später gefaltet und verfestigt und bildet das Rheinische Schiefergebirge. Sehr hart und druckfest: gut für eine Staumauer.", "Bildbreite etwa 4 cm"},
            {"Dünnschliff im polarisierten Licht", "Ein 0,03 mm dünnes Plättchen zwischen gekreuzten Polarisatoren, langsam gedreht. Quarz wechselt zwischen hell und dunkel, Feldspat zeigt die Zwillingsstreifen, Glimmer leuchtet in Interferenzfarben, die Grundmasse bleibt fast schwarz.", "Bildbreite etwa 2 mm"},
            {"Kristallgitter des Quarzes (SiO₂)", "Jedes Siliziumatom (grau) sitzt in der Mitte eines Tetraeders aus vier Sauerstoffatomen (rot); jedes Sauerstoffatom verbindet zwei Tetraeder. Alpha-Quarz ist trigonal, die Zelle misst a = 0,491 nm und c = 0,540 nm; die Atome winden sich schraubenförmig um die c-Achse.", "Bildbreite etwa 2 nm"},
            {"Silizium: Z = 14", "14 Protonen und meist 14 Neutronen im Kern, 14 Elektronen auf drei Schalen: 2 – 8 – 4 (Konfiguration [Ne] 3s² 3p²). Die vier Außenelektronen bilden die Bindungen zum Sauerstoff. Der Kern ist rund 100 000-mal kleiner als das Atom; hier ist alles stark vergrößert und nicht maßstäblich.", "Atomdurchmesser etwa 0,2 nm"}};

    private volatile boolean visible;
    private volatile int stage;
    private double shown = 0;      // Bild, das gerade gezeigt wird (für das Überblenden)
    private double fade = 1;
    private final int N = 340;
    private final BufferedImage tex = new BufferedImage(N, N, BufferedImage.TYPE_INT_RGB);
    private final int[] cell = new int[N * N];
    private final float[] edge = new float[N * N];
    private final int[] kind;          // 0 Quarz, 1 Feldspat, 2 Gesteinsbruchstück, 3 Glimmer
    private final double[] theta, stripe, sx, sy;
    private final int[] pix = new int[N * N];
    private final List<double[]> atoms = new ArrayList<>();   // x, y, z, Art (0 Si, 1 O)
    private final List<int[]> bonds = new ArrayList<>();

    public Lupe() {
        Random rnd = new Random(12);
        int n = 120;
        sx = new double[n]; sy = new double[n];
        kind = new int[n]; theta = new double[n]; stripe = new double[n];
        for (int i = 0; i < n; i++) {
            sx[i] = rnd.nextDouble() * N; sy[i] = rnd.nextDouble() * N;
            double u = rnd.nextDouble();
            kind[i] = u < 0.46 ? 0 : u < 0.70 ? 1 : u < 0.94 ? 2 : 3;
            theta[i] = rnd.nextDouble() * Math.PI;
            stripe[i] = rnd.nextDouble() * Math.PI;
        }
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                double d1 = 1e18, d2 = 1e18;
                int id = 0;
                for (int i = 0; i < n; i++) {
                    double dx = x - sx[i], dy = y - sy[i];
                    double d = dx * dx + dy * dy;
                    if (d < d1) { d2 = d1; d1 = d; id = i; } else if (d < d2) d2 = d;
                }
                cell[y * N + x] = id;
                edge[y * N + x] = (float) ((Math.sqrt(d2) - Math.sqrt(d1)) * 0.5);   // Abstand zur Kornkante
            }
        }
        buildLattice();
    }

    public boolean visible() { return visible; }
    public void setVisible(boolean v) { visible = v; }
    public int stage() { return stage; }
    public void setStage(int s) {
        s = Math.max(0, Math.min(NAMES.length - 1, s));
        if (s != stage) { stage = s; fade = 0; }
    }
    public void step(int d) { setStage(stage + d); }

    // ------------------------------------------------------------ Gitter

    private void buildLattice() {
        double a = 4.913, c = 5.405;
        double[][] si = {{0.4697, 0, 0}};
        double[][] ox = {{0.4135, 0.2669, 0.1191}};
        List<double[]> base = new ArrayList<>();
        for (double[] s : si) for (double[] p : sym(s)) base.add(new double[]{p[0], p[1], p[2], 0});
        for (double[] s : ox) for (double[] p : sym(s)) base.add(new double[]{p[0], p[1], p[2], 1});
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) for (int k = 0; k <= 1; k++) {
            for (double[] b : base) {
                double fx = b[0] + i, fy = b[1] + j, fz = b[2] + k;
                // hexagonale Zelle in kartesische Koordinaten (Å)
                double x = a * (fx + fy * Math.cos(Math.toRadians(120))), y = a * fy * Math.sin(Math.toRadians(120)), z = c * fz;
                atoms.add(new double[]{x, y, z, b[3]});
            }
        }
        // Mitte auf den Schwerpunkt, nur Atome in Reichweite behalten
        double cx = 0, cy = 0, cz = 0;
        for (double[] p : atoms) { cx += p[0]; cy += p[1]; cz += p[2]; }
        cx /= atoms.size(); cy /= atoms.size(); cz /= atoms.size();
        List<double[]> keep = new ArrayList<>();
        for (double[] p : atoms) {
            p[0] -= cx; p[1] -= cy; p[2] -= cz;
            if (Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]) < 6.8) keep.add(p);
        }
        atoms.clear(); atoms.addAll(keep);
        for (int i = 0; i < atoms.size(); i++) for (int j = i + 1; j < atoms.size(); j++) {
            double[] p = atoms.get(i), q = atoms.get(j);
            if (p[3] == q[3]) continue;
            double d = Math.sqrt(Math.pow(p[0] - q[0], 2) + Math.pow(p[1] - q[1], 2) + Math.pow(p[2] - q[2], 2));
            if (d < 1.75) bonds.add(new int[]{i, j});
        }
    }

    /** Die sechs Lagen der Raumgruppe P3₂21 zu einem Punkt. */
    private static double[][] sym(double[] p) {
        double x = p[0], y = p[1], z = p[2];
        return new double[][]{{x, y, z}, {-y, x - y, z + 1.0 / 3}, {-x + y, -x, z + 2.0 / 3}, {y, x, -z}, {x - y, -y, -z + 2.0 / 3}, {-x, -x + y, -z + 1.0 / 3}};
    }

    // ------------------------------------------------------------ Zeichnen

    /** Zeichnet die Lupe in die Bildfläche (iy, ih); t ist die Zeit in Sekunden. */
    public void paint(Graphics2D g, int pw, int ph, int iy, int ih, double t) {
        if (!visible) return;
        fade = Math.min(1, fade + 0.05);
        int d = (int) Math.min(ih * 0.62, pw * 0.42);
        int cx = pw - d / 2 - 36, cy = iy + ih / 2 - 24;
        if (cx - d / 2 < pw * 0.35) cx = pw / 2;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        Shape oldClip = g.getClip();
        // Rahmen und Schatten
        g.setColor(new Color(0, 0, 0, 110));
        g.fill(new Ellipse2D.Double(cx - d / 2 - 14, cy - d / 2 - 8, d + 28, d + 28));
        Shape lens = new Ellipse2D.Double(cx - d / 2, cy - d / 2, d, d);
        g.setClip(lens);
        int st = stage;
        double zoom = 0.82 + 0.18 * fade;
        g.setColor(new Color(6, 9, 12));
        g.fillRect(cx - d / 2, cy - d / 2, d, d);
        if (st <= 1) {
            renderRock(st, t);
            int dd = (int) (d * zoom);
            g.drawImage(tex, cx - dd / 2, cy - dd / 2, dd, dd, null);
        } else if (st == 2) {
            drawLattice(g, cx, cy, d, t);
        } else {
            drawAtom(g, cx, cy, d, t);
        }
        // Glasreflex, Fadenkreuz
        g.setPaint(new RadialGradientPaint((float) cx, (float) cy, d / 2f, new float[]{0.6f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 150)}));
        g.fillRect(cx - d / 2, cy - d / 2, d, d);
        g.setColor(new Color(255, 255, 255, 40));
        g.drawLine(cx - d / 2, cy, cx + d / 2, cy);
        g.drawLine(cx, cy - d / 2, cx, cy + d / 2);
        g.setClip(oldClip);
        g.setStroke(new BasicStroke(5f));
        g.setColor(new Color(70, 80, 86));
        g.draw(lens);
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(150, 170, 176));
        g.draw(new Ellipse2D.Double(cx - d / 2 - 3, cy - d / 2 - 3, d + 6, d + 6));
        // Beschriftung
        String[] tx = TEXT[st];
        int bx = cx - Math.max(d / 2, 190), bw = Math.max(d, 380);
        bx = Math.max(10, Math.min(pw - bw - 10, bx));
        Font ft = new Font("SansSerif", Font.PLAIN, 12);
        g.setFont(ft);
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : tx[1].split(" ")) {
            if (cur.length() > 0 && fm.stringWidth(cur + " " + w) > bw - 24) { lines.add(cur.toString()); cur.setLength(0); }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        int bh = 34 + lines.size() * 16 + 24;
        int by = cy + d / 2 + 16;
        if (by + bh > iy + ih - 6) by = iy + ih - bh - 6;
        g.setColor(new Color(8, 14, 18, 190));
        g.fillRoundRect(bx, by, bw, bh, 12, 12);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(new Color(240, 246, 246));
        g.drawString((st + 1) + "/4 · " + tx[0], bx + 12, by + 22);
        g.setFont(ft);
        g.setColor(new Color(190, 205, 208));
        int y = by + 22;
        for (String l : lines) { y += 16; g.drawString(l, bx + 12, y); }
        g.setColor(new Color(63, 176, 198));
        g.drawString(tx[2] + "  ·  Mausrad: nächste Stufe", bx + 12, y + 20);
    }

    /** Liegt (x, y) auf der Linse? (zum Mausrad) */
    public boolean hit(int x, int y, int pw, int ph, int iy, int ih) {
        if (!visible) return false;
        int d = (int) Math.min(ih * 0.62, pw * 0.42);
        int cx = pw - d / 2 - 36, cy = iy + ih / 2 - 24;
        if (cx - d / 2 < pw * 0.35) cx = pw / 2;
        return Math.hypot(x - cx, y - cy) <= d / 2.0 + 8;
    }

    // ------------------------------------------------------------ Gestein

    private static double hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }

    private void renderRock(int st, double t) {
        double ang = t * 0.28;
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                int id = cell[y * N + x];
                double e = edge[y * N + x];
                int k = kind[id];
                double r, gr, b;
                double nz = hash(x, y);
                boolean matrix = e < 3.2 + 2.4 * hash(id, 3);
                if (st == 0) {
                    if (matrix) { r = 0.16 + 0.04 * nz; gr = 0.17 + 0.04 * nz; b = 0.18 + 0.04 * nz; }
                    else if (k == 0) { r = 0.52 + 0.1 * nz; gr = 0.53 + 0.1 * nz; b = 0.55 + 0.1 * nz; }
                    else if (k == 1) { r = 0.78 + 0.08 * nz; gr = 0.68 + 0.08 * nz; b = 0.58 + 0.08 * nz; }
                    else if (k == 2) { double v = 0.17 + 0.12 * hash(id, 9); r = v + 0.05 * nz; gr = v + 0.05 * nz; b = v + 0.07 * nz; }
                    else { r = 0.68 + 0.25 * nz; gr = 0.66 + 0.25 * nz; b = 0.58 + 0.25 * nz; }
                    double shade = 0.86 + 0.14 * hash(id, 77);
                    r *= shade; gr *= shade; b *= shade;
                } else {
                    double th = theta[id] + ang;
                    double I;
                    if (matrix) { r = 0.05 + 0.02 * nz; gr = 0.05 + 0.02 * nz; b = 0.06 + 0.02 * nz; }
                    else if (k == 0) {
                        I = Math.pow(Math.sin(2 * th), 2);
                        r = 0.86 * I + 0.03; gr = 0.84 * I + 0.03; b = 0.78 * I + 0.04;
                    } else if (k == 1) {
                        double s = Math.sin(stripe[id]) * x + Math.cos(stripe[id]) * y;
                        boolean twin = ((int) Math.floor(s / 5.0) & 1) == 0;
                        I = Math.pow(Math.sin(2 * (th + (twin ? 0 : Math.PI / 4))), 2);
                        r = 0.74 * I + 0.03; gr = 0.72 * I + 0.03; b = 0.7 * I + 0.04;
                    } else if (k == 2) {
                        I = Math.pow(Math.sin(2 * th), 2) * 0.22;
                        r = 0.07 + I * 0.6; gr = 0.06 + I * 0.5; b = 0.06 + I * 0.4;
                    } else {
                        double I2 = 0.35 + 0.65 * Math.pow(Math.sin(2 * th), 2);
                        double hue = (theta[id] * 1.7 + 0.4 * Math.sin(th * 2)) % 1.0;
                        Color c = Color.getHSBColor((float) Math.abs(hue), 0.75f, 1f);
                        r = c.getRed() / 255.0 * I2; gr = c.getGreen() / 255.0 * I2; b = c.getBlue() / 255.0 * I2;
                    }
                }
                int ri = (int) Math.max(0, Math.min(255, Math.pow(Math.max(0, r), 0.8) * 255));
                int gi = (int) Math.max(0, Math.min(255, Math.pow(Math.max(0, gr), 0.8) * 255));
                int bi = (int) Math.max(0, Math.min(255, Math.pow(Math.max(0, b), 0.8) * 255));
                pix[y * N + x] = (ri << 16) | (gi << 8) | bi;
            }
        }
        tex.setRGB(0, 0, N, N, pix, 0, N);
    }

    // ------------------------------------------------------------ Gitter und Atom

    private void drawLattice(Graphics2D g, int cx, int cy, int d, double t) {
        double rot = t * 0.35, tilt = 0.5 + 0.12 * Math.sin(t * 0.21);
        double cr = Math.cos(rot), sr = Math.sin(rot), ct = Math.cos(tilt), st = Math.sin(tilt);
        double scale = d / 15.5;
        int n = atoms.size();
        double[][] pr = new double[n][3];
        for (int i = 0; i < n; i++) {
            double[] p = atoms.get(i);
            double x = p[0] * cr - p[1] * sr, y = p[0] * sr + p[1] * cr, z = p[2];
            double y2 = y * ct - z * st, z2 = y * st + z * ct;
            pr[i][0] = cx + x * scale; pr[i][1] = cy - y2 * scale; pr[i][2] = z2;
        }
        // Hintergrundleuchten
        g.setPaint(new RadialGradientPaint((float) cx, (float) cy, d / 2f, new float[]{0f, 1f}, new Color[]{new Color(24, 34, 46), new Color(5, 8, 12)}));
        g.fillRect(cx - d / 2, cy - d / 2, d, d);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        order.sort((a, b) -> Double.compare(pr[a][2], pr[b][2]));
        // Bindungen zuerst (hinten nach vorn über die Atome gemischt: einfach alle Bindungen vor den Atomen)
        g.setStroke(new BasicStroke(Math.max(2f, d / 150f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int[] bd : bonds) {
            double dep = 0.5 * (pr[bd[0]][2] + pr[bd[1]][2]);
            int al = (int) Math.max(60, Math.min(230, 150 + dep * 14));
            g.setColor(new Color(150, 165, 175, al));
            g.drawLine((int) pr[bd[0]][0], (int) pr[bd[0]][1], (int) pr[bd[1]][0], (int) pr[bd[1]][1]);
        }
        for (int i : order) {
            double[] p = atoms.get(i);
            boolean si = p[3] == 0;
            double rad = (si ? 0.46 : 0.30) * scale * (1 + pr[i][2] * 0.02);
            Color c0 = si ? new Color(200, 210, 224) : new Color(235, 70, 60);
            Color c1 = si ? new Color(70, 84, 104) : new Color(110, 18, 16);
            g.setPaint(new RadialGradientPaint((float) (pr[i][0] - rad * 0.3), (float) (pr[i][1] - rad * 0.3), (float) rad, new float[]{0f, 1f}, new Color[]{c0, c1}));
            g.fill(new Ellipse2D.Double(pr[i][0] - rad, pr[i][1] - rad, 2 * rad, 2 * rad));
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(new Color(200, 210, 224));
        g.drawString("● Si", cx - 30, cy + d / 2 - 40);
        g.setColor(new Color(235, 90, 80));
        g.drawString("● O", cx + 8, cy + d / 2 - 40);
    }

    private void drawAtom(Graphics2D g, int cx, int cy, int d, double t) {
        g.setPaint(new RadialGradientPaint((float) cx, (float) cy, d / 2f, new float[]{0f, 1f}, new Color[]{new Color(20, 30, 46), new Color(4, 6, 10)}));
        g.fillRect(cx - d / 2, cy - d / 2, d, d);
        double R = d / 2.0;
        int[] occ = {2, 8, 4};
        double[] rad = {R * 0.30, R * 0.58, R * 0.86};
        // Schalen
        g.setStroke(new BasicStroke(1f));
        for (int s = 0; s < 3; s++) {
            g.setColor(new Color(90, 120, 150, 120));
            g.draw(new Ellipse2D.Double(cx - rad[s], cy - rad[s], 2 * rad[s], 2 * rad[s]));
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(150, 180, 205, 200));
            g.drawString((s == 0 ? "K " : s == 1 ? "L " : "M ") + occ[s], (float) (cx + rad[s] * 0.71 + 4), (float) (cy - rad[s] * 0.71));
        }
        // Elektronen mit Schweif
        for (int s = 0; s < 3; s++) {
            double w = 1.4 / Math.pow(1 + s, 0.8);
            for (int e = 0; e < occ[s]; e++) {
                double a = t * w + e * 2 * Math.PI / occ[s] + s * 0.7;
                for (int tr = 0; tr < 7; tr++) {
                    double aa = a - tr * 0.045 * w;
                    double x = cx + rad[s] * Math.cos(aa), y = cy + rad[s] * Math.sin(aa);
                    int al = 230 - tr * 32;
                    double r = (s == 2 ? 6.5 : 5.5) - tr * 0.5;
                    g.setColor(new Color(90, 190, 255, Math.max(0, al)));
                    g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
                }
            }
        }
        // Kern: 14 Protonen (rot), 14 Neutronen (grau)
        Random rnd = new Random(14);
        for (int i = 0; i < 28; i++) {
            double a = rnd.nextDouble() * 2 * Math.PI, rr = Math.sqrt(rnd.nextDouble()) * R * 0.085;
            double x = cx + rr * Math.cos(a), y = cy + rr * Math.sin(a), r = R * 0.034;
            Color c = i % 2 == 0 ? new Color(230, 70, 60) : new Color(150, 160, 170);
            g.setPaint(new RadialGradientPaint((float) (x - r * 0.3), (float) (y - r * 0.3), (float) r, new float[]{0f, 1f}, new Color[]{c.brighter(), c.darker()}));
            g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.setColor(new Color(235, 245, 245));
        g.drawString("Si", cx - 9, cy + d / 2 - 52);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(190, 205, 208));
        String lab = "14 p⁺ · 14 n · 14 e⁻";
        g.drawString(lab, cx - g.getFontMetrics().stringWidth(lab) / 2, cy + d / 2 - 34);
    }
}
