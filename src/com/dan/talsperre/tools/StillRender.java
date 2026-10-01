package com.dan.talsperre.tools;

import com.dan.talsperre.camera.Viewpoint;
import com.dan.talsperre.core.Camera;
import com.dan.talsperre.core.Engine3D;
import com.dan.talsperre.effects.DayNightCycle;
import com.dan.talsperre.world.Valley;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Rechnet ein Standbild ohne Fenster: {@code StillRender datei.png [blick 0..3] [uhrzeit] [tag] [breite] [höhe]}.
 * Dient dem Statusbild je Phase und den Prüfbildern.
 */
public final class StillRender {
    public static void main(String[] a) throws Exception {
        String file = a.length > 0 ? a[0] : "still.png";
        int view = a.length > 1 ? Integer.parseInt(a[1]) : 0;
        double hour = a.length > 2 ? Double.parseDouble(a[2]) : 10;
        int day = a.length > 3 ? Integer.parseInt(a[3]) : 274;
        int w = a.length > 4 ? Integer.parseInt(a[4]) : 1280, h = a.length > 5 ? Integer.parseInt(a[5]) : 720;
        long t0 = System.nanoTime();
        Valley v = Valley.build();
        System.out.printf("Bau: %.0f ms, %d Dreiecke%n", (System.nanoTime() - t0) / 1e6, v.scene.triangles());
        Engine3D r = new Engine3D(v.scene, 2048);
        r.setSize(w, h);
        DayNightCycle c = new DayNightCycle();
        c.set(day, hour);
        r.day = day; r.hour = hour; r.sidereal = c.siderealDeg;
        r.fogScale = a.length > 6 ? Double.parseDouble(a[6]) : 0.35;
        r.setSky(c, 0.12);
        r.mesh().setHide(com.dan.talsperre.world.Dam.hideMask(Integer.getInteger("tal.mode", 0)));
        if (System.getProperty("tal.flood") != null) r.mesh().setFlood(Float.parseFloat(System.getProperty("tal.flood")));
        if (System.getProperty("tal.build") != null) {
            r.mesh().setBuild(Float.parseFloat(System.getProperty("tal.build")));
            r.mesh().setHide(r.mesh().hide | (1 << 3));
        }
        // Wasser: -Dtal.level=Pegel (m gegen Stauziel) -Dtal.gate=0..1 -Dtal.wind=0..1
        com.dan.talsperre.water.WaterWorks ww = new com.dan.talsperre.water.WaterWorks();
        ww.level = Double.parseDouble(System.getProperty("tal.level", "0"));
        ww.setAllGates(Double.parseDouble(System.getProperty("tal.gate", "0")));
        ww.wind = Double.parseDouble(System.getProperty("tal.wind", "0.35"));
        ww.plant = Double.parseDouble(System.getProperty("tal.plant", "1"));
        for (int i = 0; i < Integer.getInteger("tal.steps", 100); i++) ww.update(0.05, r, v.terrain);
        com.dan.talsperre.core.Animals an = new com.dan.talsperre.core.Animals();
        r.animals = an;
        String sim = System.getProperty("tal.scene");
        if (sim != null) {
            com.dan.talsperre.extras.Extras ex = new com.dan.talsperre.extras.Extras();
            if (sim.equals("build")) ex.startBuild(); else if (sim.equals("breach")) ex.startBreach(); else if (sim.equals("section")) ex.setSection(true);
            double T = Double.parseDouble(System.getProperty("tal.sim", "10"));
            for (double tt = 0; tt < T; tt += 0.05) {
                an.clear(); r.sprites.clear();
                ex.update(0.05, r, ww, an, r.sprites, v.terrain);
                ww.update(0.05, r, v.terrain);
                if (ex.wantDay > 0) {
                    day = ex.wantDay; hour = Double.parseDouble(System.getProperty("tal.hour", String.valueOf(ex.wantHour))); ex.wantDay = -1;
                    c.set(day, hour); r.day = day; r.hour = hour; r.sidereal = c.siderealDeg; r.setSky(c, 0.12);
                }
            }
            System.out.printf("Szene %s: t=%.1f Pegel %.1f Bauhöhe %.1f Partikel %d Tiere %d%n", sim, ex.clock(), ww.level, r.mesh().buildY, ww.particles.n, an.n);
        }
        Camera cam = new Camera();
        double[] p = Viewpoint.all(v.terrain)[Math.max(0, Math.min(Viewpoint.NAMES.length - 1, view))].pose;
        if (a.length > 7) { String[] q = a[7].split(","); p = new double[q.length]; for (int i = 0; i < q.length; i++) p[i] = Double.parseDouble(q[i]); }
        cam.ex = p[0]; cam.ey = p[1]; cam.ez = p[2];
        cam.lookAt(p[3], p[4], p[5]);
        double lt = Double.parseDouble(System.getProperty("tal.time", "100"));
        BufferedImage img = null;
        for (int i = 0; i < 3; i++) {
            long f0 = System.nanoTime();
            if (v.scene.life != null && !Boolean.getBoolean("tal.nolife")) v.scene.life.update(lt, ww.level, r.lampLevel(), an, r.sprites);
            if (i == 0) for (int q = 0; q < an.n; q++) if (an.kind[q] >= 4 && an.kind[q] != 8) System.out.printf("OBJ " + an.kind[q] + " %.0f %.0f h %.2f %.2f%n", an.x[q], an.z[q], an.hx[q], an.hz[q]);
            img = r.render(cam, i * 0.1, 0.1);
            System.out.printf("Bild %d: %.0f ms (%d Dreiecke gezeichnet)%n", i, (System.nanoTime() - f0) / 1e6, r.drawnTris);
        }
        ImageIO.write(img, "png", new File(file));
        System.out.println("geschrieben: " + file);
    }
}
