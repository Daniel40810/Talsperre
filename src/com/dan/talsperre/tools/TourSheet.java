package com.dan.talsperre.tools;

import com.dan.talsperre.camera.Director;
import com.dan.talsperre.camera.Tours;
import com.dan.talsperre.core.Camera;
import com.dan.talsperre.core.Engine3D;
import com.dan.talsperre.effects.DayNightCycle;
import com.dan.talsperre.water.WaterWorks;
import com.dan.talsperre.world.Valley;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/** Kontaktbogen einer Kamerafahrt: {@code TourSheet datei.png tour bilder [spalten]}. Prüfwerkzeug der Regie. */
public final class TourSheet {
    public static void main(String[] a) throws Exception {
        String file = a[0];
        int tour = Integer.parseInt(a[1]), n = Integer.parseInt(a[2]), cols = a.length > 3 ? Integer.parseInt(a[3]) : 3;
        int w = 640, h = 360;
        Valley v = Valley.build();
        Engine3D r = new Engine3D(v.scene, 2048);
        com.dan.talsperre.core.Animals an = new com.dan.talsperre.core.Animals();
        r.animals = an;
        r.setSize(w, h);
        r.fogScale = 0.6;
        DayNightCycle c = new DayNightCycle();
        WaterWorks ww = new WaterWorks();
        ww.setAllGates(0.7); ww.wind = 0.3;
        Director d = new Director(v.terrain);
        d.play(Tours.get(tour, v.terrain));
        double total = d.totalSeconds();
        System.out.printf("Fahrt %s: %.0f s%n", d.title(), total);
        Camera cam = new Camera();
        int rows = (n + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * w, rows * h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        double hour = 10;
        for (int i = 0; i < n; i++) {
            double want = total * (i + 0.5) / n;
            double step = (want - d.elapsedSeconds());
            d.update(Math.max(0.001, step), cam);
            double dh = d.hour();
            if (!Double.isNaN(dh)) hour = dh;
            c.set(274, hour);
            r.day = 274; r.hour = hour; r.sidereal = c.siderealDeg;
            r.setSky(c, 0.12);
            for (int k = 0; k < 40; k++) ww.update(0.05, r, v.terrain);
            if (v.scene.life != null) v.scene.life.update(i * 3.0 + 20, ww.level, r.lampLevel(), an, r.sprites);
            BufferedImage im = r.render(cam, i, 0.1);
            im = r.render(cam, i + 0.1, 0.1);
            g.drawImage(im, (i % cols) * w, (i / cols) * h, null);
            System.out.printf("%d: t=%.0f s h=%.1f eye %.0f %.0f %.0f%n", i, want, hour, cam.ex, cam.ey, cam.ez);
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(file));
    }
}
