package com.dan.talsperre.tools;

import com.dan.talsperre.extras.Lupe;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Zeichnet die vier Stufen der Mineral-Lupe in ein Blatt (Prüfbild): {@code LupeSheet datei.png}. */
public final class LupeSheet {
    public static void main(String[] a) throws Exception {
        int w = 900, h = 640;
        BufferedImage out = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_RGB);
        Lupe l = new Lupe();
        l.setVisible(true);
        for (int s = 0; s < 4; s++) {
            BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            g.setColor(new Color(60, 90, 70));
            g.fillRect(0, 0, w, h);
            l.setStage(s);
            for (int i = 0; i < 30; i++) l.paint(g, w, h, 0, h, 3.0 + s);
            g.dispose();
            Graphics2D go = out.createGraphics();
            go.drawImage(im, (s % 2) * w, (s / 2) * h, null);
            go.dispose();
        }
        ImageIO.write(out, "png", new File(a.length > 0 ? a[0] : "lupe.png"));
    }
}
