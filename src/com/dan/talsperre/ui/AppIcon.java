package com.dan.talsperre.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Das Programmsymbol der Edertalsperre, gemalt statt geladen:
 * Die geschwungene Bruchsteinmauer mit Kronengang, den beiden Tortürmen und Überlauf-Bögen,
 * davor das tiefblaue Wasser des Edersees und die bewaldeten Hänge des Kellerwalds,
 * darunter die schäumende Kaskade im Tosbecken.
 * Liefert alle üblichen Größen für Taskleiste, Fenstertitel und Alt+Tab sowie eine .ico-Datei.
 */
public final class AppIcon {
    public static final int[] SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};
    public static final int[] ICO_SIZES = {16, 24, 32, 48, 64, 128, 256};

    private AppIcon() { }

    public static List<Image> images() {
        List<Image> l = new ArrayList<>();
        for (int s : SIZES) l.add(paint(s));
        return l;
    }

    /** Setzt das Symbol am Fenster und, wo unterstützt, in der Taskleiste. */
    public static void install(java.awt.Window w) {
        List<Image> l = images();
        w.setIconImages(l);
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar tb = java.awt.Taskbar.getTaskbar();
                if (tb.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                    tb.setIconImage(l.get(l.size() - 1));
                }
            }
        } catch (Exception | Error ignored) {
            // Windows übernimmt die Fenstersymbole
        }
    }

    /** Malt das Symbol in der Kantenlänge s (quadratisch, mit Alpha). */
    public static BufferedImage paint(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(s / 256.0, s / 256.0);
        boolean small = s <= 24;

        RoundRectangle2D tile = new RoundRectangle2D.Double(8, 8, 240, 240, 56, 56);

        // 1. Abend-/Morgenhimmel über dem Edersee
        g.setPaint(new GradientPaint(0, 8, new Color(14, 32, 54), 0, 135, new Color(82, 138, 178)));
        g.fill(tile);
        g.setClip(tile);

        // Zarte Bergkette im Hintergrund
        Path2D bgHills = new Path2D.Double();
        bgHills.moveTo(8, 120);
        bgHills.curveTo(60, 105, 110, 116, 160, 102);
        bgHills.curveTo(195, 92, 225, 106, 248, 114);
        bgHills.lineTo(248, 150);
        bgHills.lineTo(8, 150);
        bgHills.closePath();
        g.setColor(new Color(42, 80, 102, 190));
        g.fill(bgHills);

        // 2. Bewaldete Hänge (Kellerwald) links und rechts
        Path2D forestLeft = new Path2D.Double();
        forestLeft.moveTo(8, 85);
        forestLeft.curveTo(45, 92, 70, 120, 85, 148);
        forestLeft.lineTo(8, 160);
        forestLeft.closePath();
        g.setColor(new Color(24, 62, 45));
        g.fill(forestLeft);

        Path2D forestRight = new Path2D.Double();
        forestRight.moveTo(248, 82);
        forestRight.curveTo(210, 90, 185, 122, 171, 148);
        forestRight.lineTo(248, 160);
        forestRight.closePath();
        g.setColor(new Color(20, 54, 38));
        g.fill(forestRight);

        // Baumkronen-Akzente auf den Hängen
        if (!small) {
            g.setColor(new Color(32, 82, 56));
            for (int i = 0; i < 5; i++) {
                double bx = 16 + i * 12, by = 100 + i * 9;
                g.fill(new Ellipse2D.Double(bx, by, 10, 10));
            }
            for (int i = 0; i < 5; i++) {
                double bx = 224 - i * 11, by = 98 + i * 9;
                g.fill(new Ellipse2D.Double(bx, by, 10, 10));
            }
        }

        // 3. Stausee (Edersee) vor der Mauer
        Path2D lake = new Path2D.Double();
        lake.moveTo(8, 126);
        lake.curveTo(80, 134, 176, 134, 248, 126);
        lake.lineTo(248, 148);
        lake.lineTo(8, 148);
        lake.closePath();
        g.setPaint(new GradientPaint(0, 120, new Color(18, 62, 104), 0, 148, new Color(48, 135, 175)));
        g.fill(lake);

        // Wasser-Reflexe
        if (!small) {
            g.setColor(new Color(180, 230, 255, 95));
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(85, 135, 171, 135);
            g.drawLine(105, 139, 151, 139);
        }

        // 4. Die Edertalsperre Staumauer (Gewichtsmauer aus Bruchstein)
        Path2D dam = new Path2D.Double();
        // Kronenbogen
        dam.moveTo(28, 145);
        dam.curveTo(88, 153, 168, 153, 228, 145);
        // Rechte Kante
        dam.lineTo(234, 218);
        // Mauerfußbogen
        dam.curveTo(172, 228, 84, 228, 22, 218);
        dam.closePath();

        // Mauerfüllung Grauwacke / Bruchstein
        g.setPaint(new GradientPaint(0, 144, new Color(182, 185, 178), 0, 222, new Color(102, 108, 106)));
        g.fill(dam);

        // Kronengang (Heller Abschlussstein oben)
        Path2D crest = new Path2D.Double();
        crest.moveTo(28, 144);
        crest.curveTo(88, 152, 168, 152, 228, 144);
        crest.lineTo(228, 148);
        crest.curveTo(168, 156, 88, 156, 28, 148);
        crest.closePath();
        g.setColor(new Color(222, 226, 220));
        g.fill(crest);

        // 5. Überlauf-Bögen (die markante Bogenreihe der Edertalsperre)
        int arches = small ? 7 : 13;
        double aStart = 48, aEnd = 208, aStep = (aEnd - aStart) / (arches - 1);
        g.setColor(new Color(45, 48, 50));
        for (int i = 0; i < arches; i++) {
            double ax = aStart + i * aStep;
            double t = (ax - 128.0) / 100.0;
            double ay = 153 + (1.0 - t * t) * 3.5;
            double aw = small ? 4.5 : 5.5, ah = small ? 7.0 : 8.5;
            Path2D arch = new Path2D.Double();
            arch.moveTo(ax - aw / 2, ay + ah);
            arch.lineTo(ax - aw / 2, ay + ah * 0.45);
            arch.quadTo(ax, ay - 1.5, ax + aw / 2, ay + ah * 0.45);
            arch.lineTo(ax + aw / 2, ay + ah);
            arch.closePath();
            g.fill(arch);
        }

        // 6. Wasserablass / Kaskadensturz in der Mauermitte
        Path2D spill = new Path2D.Double();
        spill.moveTo(98, 160);
        spill.lineTo(158, 160);
        spill.lineTo(168, 225);
        spill.lineTo(88, 225);
        spill.closePath();
        g.setPaint(new GradientPaint(0, 160, new Color(245, 252, 255, 225), 0, 225, new Color(175, 222, 245, 175)));
        g.fill(spill);

        // Gischt- und Strömungslinien
        if (!small) {
            g.setColor(new Color(255, 255, 255, 210));
            g.setStroke(new BasicStroke(1.2f));
            for (int i = 0; i < 5; i++) {
                int sx = 108 + i * 10;
                g.drawLine(sx, 164, sx + (i - 2) * 2, 222);
            }
        }

        // 7. Die beiden Tortürme auf der Mauerkrone
        double[] towerX = {84, 172};
        for (double tx : towerX) {
            double ty = 136;
            // Turmkörper
            g.setColor(new Color(205, 208, 202));
            g.fillRect((int) (tx - 5), (int) ty, 10, 12);
            g.setColor(new Color(130, 134, 132));
            g.drawRect((int) (tx - 5), (int) ty, 10, 12);
            // Walmdach / Spitzdach
            Path2D roof = new Path2D.Double();
            roof.moveTo(tx - 6.5, ty);
            roof.lineTo(tx, ty - 8);
            roof.lineTo(tx + 6.5, ty);
            roof.closePath();
            g.setColor(new Color(68, 76, 86));
            g.fill(roof);
            // Fenster
            if (!small) {
                g.setColor(new Color(38, 42, 46));
                g.fillRect((int) tx - 1, (int) ty + 3, 3, 4);
            }
        }

        // 8. Tosbecken am Mauerfuß
        Path2D basin = new Path2D.Double();
        basin.moveTo(8, 216);
        basin.curveTo(88, 228, 168, 228, 248, 216);
        basin.lineTo(248, 248);
        basin.lineTo(8, 248);
        basin.closePath();
        g.setPaint(new GradientPaint(0, 216, new Color(26, 74, 88), 0, 248, new Color(10, 28, 38)));
        g.fill(basin);

        // Schaumkrone / Gischt am Aufprall des Wassers
        RadialGradientPaint foam = new RadialGradientPaint(128f, 224f, small ? 24f : 38f,
                new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(255, 255, 255, 240), new Color(210, 242, 252, 170), new Color(210, 242, 252, 0)});
        g.setPaint(foam);
        g.fill(new Ellipse2D.Double(88, 214, 80, 22));

        g.setClip(null);

        // 9. Eleganter goldener Zierrand
        if (!small) {
            g.setColor(new Color(218, 176, 72));
            g.setStroke(new BasicStroke(5f));
            g.draw(new RoundRectangle2D.Double(10.5, 10.5, 235, 235, 53, 53));
        }

        g.dispose();
        return img;
    }

    /**
     * Schreibt eine standardkonforme Multiauflösungs-.ico-Datei mit PNG-kodierten Bildern.
     * Enthält die 7 Standardgrößen: 16, 24, 32, 48, 64, 128, 256.
     */
    public static void writeIco(File target) throws IOException {
        List<byte[]> pngBytesList = new ArrayList<>();
        for (int s : ICO_SIZES) {
            BufferedImage bi = paint(s);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", baos);
            pngBytesList.add(baos.toByteArray());
        }

        int count = ICO_SIZES.length;
        // Header: 6 Bytes (reserved 2B=0, type 2B=1, count 2B)
        // Directory Entries: 16 Bytes pro Bild
        int headerSize = 6 + count * 16;
        int currentOffset = headerSize;

        ByteBuffer dir = ByteBuffer.allocate(headerSize);
        dir.order(ByteOrder.LITTLE_ENDIAN);
        dir.putShort((short) 0); // reserved
        dir.putShort((short) 1); // type 1 = ICO
        dir.putShort((short) count);

        for (int i = 0; i < count; i++) {
            int s = ICO_SIZES[i];
            byte[] png = pngBytesList.get(i);
            dir.put((byte) (s == 256 ? 0 : s)); // Width
            dir.put((byte) (s == 256 ? 0 : s)); // Height
            dir.put((byte) 0);                   // Color count
            dir.put((byte) 0);                   // Reserved
            dir.putShort((short) 1);             // Color planes
            dir.putShort((short) 32);            // Bits per pixel
            dir.putInt(png.length);              // Data length
            dir.putInt(currentOffset);           // Data offset
            currentOffset += png.length;
        }

        try (FileOutputStream fos = new FileOutputStream(target)) {
            fos.write(dir.array());
            for (byte[] png : pngBytesList) {
                fos.write(png);
            }
        }
    }

    /** Erzeugt beim Aufruf direkt die Datei talsperre.ico im Projektverzeichnis. */
    public static void main(String[] args) {
        try {
            File f = new File("talsperre.ico");
            writeIco(f);
            System.out.println("Erfolgreich erstellt: " + f.getAbsolutePath() + " (" + f.length() + " Bytes)");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
