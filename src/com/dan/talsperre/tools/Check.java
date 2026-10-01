package com.dan.talsperre.tools;

import com.dan.talsperre.camera.CameraController;
import com.dan.talsperre.camera.Viewpoint;
import com.dan.talsperre.core.Camera;
import com.dan.talsperre.core.Engine3D;
import com.dan.talsperre.core.Terrain;
import com.dan.talsperre.effects.DayNightCycle;
import com.dan.talsperre.water.WaterWorks;
import com.dan.talsperre.world.Dam;
import com.dan.talsperre.world.Valley;

import java.awt.image.BufferedImage;

/** Selbsttest ohne Fenster: Gelände, Sonnenstand und Zeit, Kamera, ein Bild. Endet mit „Alles in Ordnung.“. */
public final class Check {
    private static int fails;

    private static void ok(boolean c, String what) {
        System.out.println((c ? "ok      " : "FEHLER  ") + what);
        if (!c) fails++;
    }

    public static void main(String[] args) {
        Valley v = Valley.build();
        Terrain t = v.terrain;

        // Gelände
        ok(v.scene.triangles() > 100_000, "Szene hat Dreiecke: " + v.scene.triangles());
        double worst = 0;
        for (int i = 0; i < 200; i++) {
            double x = -1400 + 2800 * ((i * 37) % 200) / 200.0, z = -1400 + 2800 * ((i * 91) % 200) / 200.0;
            worst = Math.max(worst, Math.abs(t.sample(x, z) - t.exact(x, z)));
        }
        ok(worst < 6, String.format("Gitterhöhe weicht von der Formel um höchstens %.2f m ab", worst));
        ok(Math.abs(t.riverLevel(0)) < 100 && t.riverPoints() > 100, "Lauf der Eder: " + t.riverPoints() + " Punkte");
        double sky = t.skyView(0, -400, t.sample(0, -400));
        ok(sky >= 0.35 && sky <= 1, String.format("Himmelssicht im Tal %.2f", sky));
        float[] g = new float[5];
        t.ground(0, 0, g);
        ok(g[4] >= 0 && g[4] <= 1, "Bodenkarte liefert Wald 0..1");

        // Sperrmauer: Maße nach dem Vorbild (Krone 6 m, Fuß 36 m, Höhe 48 m, Kronenlänge um 400 m)
        ok(Math.abs(Dam.width(Dam.TOP) - 6) < 1e-9 && Math.abs(Dam.width(Dam.BASE) - 36) < 1e-9, "Mauer: Krone 6 m, Fuß 36 m breit");
        ok(Math.abs(Dam.TOP - Dam.BASE - 48) < 1e-9, "Mauer: 48 m hoch");
        double arc = 0;
        for (double ph = -Dam.PHI_MAX; ph < Dam.PHI_MAX; ph += 0.002) {
            if (t.sample(Dam.x(ph, Dam.R - 3), Dam.z(ph, Dam.R - 3)) < Dam.TOP - 0.3) arc += 0.002 * (Dam.R - 3);
        }
        ok(arc > 340 && arc < 460, String.format("Mauer: sichtbare Kronenlänge %.0f m", arc));
        double toe = t.sample(Dam.x(0, Dam.R - Dam.width(Dam.BASE)), Dam.z(0, Dam.R - Dam.width(Dam.BASE)));
        ok(toe < -40, String.format("Talsohle am Mauerfuß %.1f m unter Stauziel", -toe));
        float[] col = new float[3];
        boolean mats = true;
        for (int m : new int[]{com.dan.talsperre.core.Mat.STONE, com.dan.talsperre.core.Mat.MARBLE, com.dan.talsperre.core.Mat.SLATE,
                com.dan.talsperre.core.Mat.METAL, com.dan.talsperre.core.Mat.GLASS, com.dan.talsperre.core.Mat.CONCRETE}) {
            for (int i = 0; i < 400; i++) {
                float[] n = {0.6f, 0.3f, 0.74f};
                float f = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
                n[0] /= f; n[1] /= f; n[2] /= f;
                com.dan.talsperre.core.Materials.surface(m, -90 + i * 0.37f, -30 + i * 0.11f, -20 + i * 0.23f, n, 0.05f + 0.01f * i, new float[5], col);
                for (float cc : col) if (!(cc >= 0 && cc < 1.5f)) mats = false;
            }
        }
        ok(mats, "Stein, Marmor, Schiefer, Eisen, Glas, Beton liefern gültige Farben");

        // Wasser: Pegel, Ausfluss nach Torricelli, Überfall nach Poleni, Teilchen
        ok(Math.abs(WaterWorks.nhn(0) - 244.97) < 1e-9, "Stauziel 244,97 m ü. NHN");
        double js = WaterWorks.jetSpeed(0);
        ok(js > 14 && js < 20, String.format("Strahl am Ablass bei Vollstau %.1f m/s", js));
        ok(WaterWorks.jetSpeed(-20) < WaterWorks.jetSpeed(0), "Tieferer Pegel gibt schwächeren Strahl");
        ok(WaterWorks.spillFlow(0) == 0 && WaterWorks.spillFlow(Dam.SILL + 1) > 10, String.format("Überlauf erst über der Schwelle (bei +1,3 m: %.0f m³/s)", WaterWorks.spillFlow(Dam.SILL + 1)));
        WaterWorks ww = new WaterWorks();
        Engine3D wr = new Engine3D(v.scene, 1024);
        ww.level = -7; ww.setAllGates(1);
        for (int i = 0; i < 80; i++) ww.update(0.05, wr, t);
        ok(Math.abs(wr.mesh().lakeDy + 7) < 1e-6, "Seefläche folgt dem Pegel (−7 m)");
        ok(ww.particles.n > 3000 && !wr.cascadeOn, "Strahlen erzeugen Teilchen: " + ww.particles.n + ", Überlauf aus");
        int steam = 0;
        for (int i = 0; i < ww.particles.n; i++) if (ww.particles.kind[i] == com.dan.talsperre.effects.ParticleSystem.STEAM) steam++;
        ok(steam > 20, "Gischtnebel: Dampfteilchen am Tosbecken: " + steam);
        ok(Engine3D.mistAmount(6.5) > Engine3D.mistAmount(13.5), "Morgens mehr Nebel als mittags");
        ww.level = 1.2;
        for (int i = 0; i < 60; i++) ww.update(0.05, wr, t);
        ok(wr.cascadeOn && wr.basinFoam > 0.3, String.format("Überlauf an, Brause im Becken %.2f", wr.basinFoam));
        ww.setAllGates(0); ww.level = 0;
        for (int i = 0; i < 200; i++) ww.update(0.05, wr, t);
        ok(ww.particles.n < 200 && !wr.cascadeOn, "Ohne Ablass und Überlauf versiegen die Teilchen");

        // Licht: Lampen, Fensterlicht, Spiegelung
        com.dan.talsperre.effects.Lamps lps = Dam.lamps();
        ok(lps.n > 20, "Lampen der Mauer: " + lps.n);
        lps.on = 1;
        float[] il = new float[3];
        lps.illuminate((float) Dam.x(0, Dam.R - 3), (float) Dam.TOP, (float) Dam.z(0, Dam.R - 3), 0, 1, 0, il);
        ok(il[0] > 0 && il[0] > il[2], String.format("Kronenlampe beleuchtet den Boden warm (%.2f / %.2f)", il[0], il[2]));
        int lit = 0;
        for (int i = 0; i < 400; i++) if (com.dan.talsperre.core.Materials.glassGlow(i * 2.3f, -8, 10 + i * 0.7f) > 0) lit++;
        ok(lit > 160 && lit < 340, "Rund zwei Drittel der Fenster brennen: " + lit + " von 400");
        float[] gl = new float[3];
        lps.glint((float) lps.x[0], -5, (float) lps.z[0] + 60, 0, 0.0f, -1, gl);
        ok(gl[0] >= 0, "Spiegelkeule ohne Fehler");

        // Regie: Drehbücher laufen durch, Kamera bleibt endlich und über dem Gelände
        for (int ti = 0; ti < com.dan.talsperre.camera.Tours.NAMES.length; ti++) {
            com.dan.talsperre.camera.Director dr = new com.dan.talsperre.camera.Director(t);
            dr.play(com.dan.talsperre.camera.Tours.get(ti, t));
            double tot = dr.totalSeconds();
            com.dan.talsperre.core.Camera cm = new com.dan.talsperre.core.Camera();
            boolean fin = true, above = true;
            int steps = 0;
            while (dr.update(0.5, cm) && steps < 2000) {
                steps++;
                if (Double.isNaN(cm.ex + cm.ey + cm.ez + cm.fx + cm.fy + cm.fz)) fin = false;
                if (cm.ey < t.sample(cm.ex, cm.ez) + 1.0) above = false;
            }
            ok(tot > 20 && fin && above && !dr.active(), String.format("Fahrt „%s“: %.0f s, endlich, über dem Gelände", com.dan.talsperre.camera.Tours.NAMES[ti], tot));
        }
        com.dan.talsperre.camera.Director dd = new com.dan.talsperre.camera.Director(t);
        dd.play(com.dan.talsperre.camera.Tours.get(5, t));
        dd.update(1, new com.dan.talsperre.core.Camera());
        ok(!Double.isNaN(dd.hour()) && dd.hour() > 6 && dd.hour() < 8 && dd.marks().length == 6, "Tagesfilm: Uhrzeit läuft mit, sieben Einstellungen");
        dd.stop();
        ok(!dd.active(), "Regie lässt sich beenden");

        // Zeit: Mitteleuropäische Zeit, Sommerzeit 2026 vom 29. März bis 25. Oktober
        ok(DayNightCycle.utcOffset(274, 12) == 2, "1. Oktober: MESZ (UTC+2)");
        ok(DayNightCycle.utcOffset(300, 12) == 1, "27. Oktober: MEZ (UTC+1)");
        ok(DayNightCycle.utcOffset(60, 12) == 1, "1. März: MEZ");
        ok(DayNightCycle.utcOffset(88, 1) == 1 && DayNightCycle.utcOffset(88, 3) == 2, "29. März: Umstellung um 2 Uhr");

        // Sonnenstand: 1. Oktober, Mittag gegen 13:15 MESZ, Höhe rund 36°, Süden
        DayNightCycle c = new DayNightCycle();
        c.set(274, 13.25);
        ok(c.elevationDeg > 33 && c.elevationDeg < 39, String.format("Sonnenhöhe um 13:15 am 1. Oktober %.1f°", c.elevationDeg));
        ok(Math.abs(c.azimuthDeg - 180) < 8, String.format("Sonne steht im Süden (%.0f°)", c.azimuthDeg));
        c.set(274, 1.0);
        ok(c.elevationDeg < -10, "Nachts steht die Sonne unter dem Horizont");
        c.set(172, 12.0);
        ok(c.elevationDeg > 55, String.format("Sommer: Mittagshöhe %.1f°", c.elevationDeg));

        // Kamera
        Camera cam = new Camera();
        cam.ex = 10; cam.ey = 20; cam.ez = 30;
        cam.lookAt(10, 20, -70);
        ok(Math.abs(cam.fz + 1) < 1e-9 && Math.abs(cam.fx) < 1e-9, "Kamera blickt nach Norden (-z)");
        CameraController ctl = new CameraController(t);
        for (int i = 0; i < 120; i++) ctl.update(0.05, cam);
        ok(cam.ey >= t.sample(cam.ex, cam.ez) + 1.69, "Kamera bleibt über dem Gelände");
        ctl.wheel(-50);
        for (int i = 0; i < 120; i++) ctl.update(0.05, cam);
        ok(cam.ey >= t.sample(cam.ex, cam.ez) + 1.69, "Kamera bleibt beim Heranzoomen über dem Gelände");
        ok(Viewpoint.all(t).length == Viewpoint.NAMES.length, "Blickpunkte und Namen passen zusammen");

        // Bild
        Engine3D r = new Engine3D(v.scene, 1024);
        r.setSize(480, 270);
        c.set(274, 13.25);
        r.setSky(c, 0.12);
        double[] p = Viewpoint.all(t)[0].pose;
        cam.ex = p[0]; cam.ey = p[1]; cam.ez = p[2];
        cam.lookAt(p[3], p[4], p[5]);
        BufferedImage img = null;
        long t0 = System.nanoTime();
        for (int i = 0; i < 3; i++) img = r.render(cam, i * 0.1, 0.1);
        double ms = (System.nanoTime() - t0) / 3e6;
        double mean = 0, var = 0;
        int n = 480 * 270;
        for (int i = 0; i < n; i++) {
            int px = img.getRGB(i % 480, i / 480);
            mean += ((px >> 16 & 255) + (px >> 8 & 255) + (px & 255)) / 3.0;
        }
        mean /= n;
        for (int i = 0; i < n; i++) {
            int px = img.getRGB(i % 480, i / 480);
            double d = ((px >> 16 & 255) + (px >> 8 & 255) + (px & 255)) / 3.0 - mean;
            var += d * d;
        }
        double sd = Math.sqrt(var / n);
        ok(mean > 30 && mean < 235, String.format("Bild nicht schwarz oder weiß (Mittel %.0f)", mean));
        ok(sd > 12, String.format("Bild hat Kontrast (Streuung %.0f)", sd));
        ok(r.drawnTris > 1000, "Dreiecke gezeichnet: " + r.drawnTris);
        ok(ms < 3000, String.format("Rechenzeit %.0f ms je Bild bei 480 × 270", ms));


        // Phase 8: Landschaft und Leben
        ok(com.dan.talsperre.world.Woods.trees > 20000, "Wald: " + com.dan.talsperre.world.Woods.trees + " Bäume");
        ok(v.scene.triangles() < 4_000_000, "Dreiecke unter der Blockgrenze (4,19 Mio.): " + v.scene.triangles());
        double rl = com.dan.talsperre.world.Roads.rs.length > 0 ? com.dan.talsperre.world.Roads.rs[com.dan.talsperre.world.Roads.rs.length - 1] : 0;
        ok(rl > 2000, String.format("Straße Tal – Krone – Seeufer: %.0f m", rl));
        boolean roadOk = true;
        for (int i = 0; i < com.dan.talsperre.world.Roads.rx.length; i++) {
            double gy = t.sample(com.dan.talsperre.world.Roads.rx[i], com.dan.talsperre.world.Roads.rz[i]);
            if (Math.abs(Math.hypot(com.dan.talsperre.world.Roads.rx[i], com.dan.talsperre.world.Roads.rz[i] - Dam.CZ) - (Dam.R - 3)) < 0.5) continue;   // Krone
            if (Double.isNaN(com.dan.talsperre.world.Roads.ry[i]) || com.dan.talsperre.world.Roads.ry[i] < gy - 2.5 || com.dan.talsperre.world.Roads.ry[i] > gy + 6.5) roadOk = false;
        }
        ok(roadOk, "Straße liegt auf dem Gelände (höchstens 2,5 m unter / 6,5 m über dem Boden)");
        com.dan.talsperre.core.Animals an = new com.dan.talsperre.core.Animals();
        com.dan.talsperre.core.Sprites spr = new com.dan.talsperre.core.Sprites();
        boolean lifeOk = true; int cars = 0, ships = 0, people = 0, birds = 0;
        for (double tt = 0; tt < 600 && lifeOk; tt += 37) {
            v.scene.life.update(tt, 0, 0f, an, spr);
            for (int i = 0; i < an.n; i++) {
                if (Float.isNaN(an.x[i]) || Float.isNaN(an.y[i]) || Float.isNaN(an.z[i]) || Float.isNaN(an.hx[i])) lifeOk = false;
                if (an.kind[i] == com.dan.talsperre.core.Animals.CAR || an.kind[i] == com.dan.talsperre.core.Animals.BUS) cars++;
                if (an.kind[i] == com.dan.talsperre.core.Animals.SHIP || an.kind[i] == com.dan.talsperre.core.Animals.SAIL) {
                    ships++;
                    if (t.sample(an.x[i], an.z[i]) > 0) lifeOk = false;       // Schiffe nur auf dem Wasser
                }
                if (an.kind[i] == com.dan.talsperre.core.Animals.PERSON) people++;
            }
            for (int i = 0; i < spr.n; i++) if (spr.kind[i] == com.dan.talsperre.core.Sprites.BIRD) birds++;
        }
        ok(lifeOk, "Leben: keine ungültigen Orte, Schiffe nur auf dem Wasser");
        ok(cars > 20 && ships > 20 && people > 20 && birds > 20, "Leben: Autos " + cars + ", Schiffe " + ships + ", Menschen " + people + ", Vögel " + birds + " (über 17 Zeitpunkte)");
        v.scene.life.traffic = false; v.scene.life.ships = false; v.scene.life.birds = false; v.scene.life.walkers = false;
        v.scene.life.update(10, 0, 0f, an, spr);
        ok(an.n == 0 && spr.n == 0, "Leben lässt sich ganz ausschalten");
        v.scene.life.traffic = true; v.scene.life.ships = true; v.scene.life.birds = true; v.scene.life.walkers = true;

        // ---- Phase 9: Zugaben
        com.dan.talsperre.core.Mesh me = wr.mesh();
        int[] gcount = new int[8];
        for (int ci = 0; ci < me.nChunks; ci++) gcount[me.chunkGrp[ci] & 7] += me.chunkCount[ci];
        ok(gcount[4] > 500 && gcount[5] > 500 && gcount[6] > 20 && gcount[7] > 20,
                "Mauerkeil oben " + gcount[4] + ", unten " + gcount[5] + ", Schnittflächen " + gcount[6] + ", Bruchflächen " + gcount[7] + " Dreiecke");
        int hs = Dam.hideMask(Dam.MODE_SECTION), hb = Dam.hideMask(Dam.MODE_BREACH), hn = Dam.hideMask(Dam.MODE_NORMAL);
        ok((hs >> 4 & 1) == 1 && (hs >> 5 & 1) == 1 && (hs >> 6 & 1) == 0 && (hb >> 4 & 1) == 1 && (hb >> 7 & 1) == 0 && (hn >> 6 & 1) == 1 && (hn >> 4 & 1) == 0,
                "Gruppen: Schnitt blendet den Keil ein und die Schnittflächen aus, Bresche zeigt die Bruchflächen");
        // Keil: Dreiecke der Gruppen 4/5 liegen im Winkelbereich, die Gruppe 6 auf den Schnittebenen
        boolean wedgeOk = true;
        for (int tri = 0; tri < me.nt && wedgeOk; tri += 7) {
            int g0 = me.grp[tri];
            if (g0 != 4 && g0 != 5) continue;
            int a = 3 * me.idx[3 * tri];
            double ph = Math.atan2(me.pos[a], me.pos[a + 2] - Dam.CZ);
            if (ph < Dam.WPA - 1e-3 || ph > Dam.WPB + 1e-3) wedgeOk = false;
        }
        ok(wedgeOk, "Keil: alle Ecken der Keildreiecke liegen zwischen den Schnittebenen");
        // Bauzustand
        int wallV = -1;
        for (int i = 0; i < me.nv && wallV < 0; i++) if (me.rise[i] == 1 && me.pos[3 * i + 1] > 1.5f) wallV = i;
        me.setBuild(-20f);
        float yb = me.deformY(wallV, me.pos[3 * wallV + 1]);
        me.setBuild(1e6f);
        ok(wallV >= 0 && yb <= -20f && me.deformY(wallV, me.pos[3 * wallV + 1]) == me.pos[3 * wallV + 1], String.format("Bauzustand: Ecke der Krone liegt bei Bauhöhe −20 m auf %.2f m, fertig an ihrem Platz", yb));
        int floodV = -1;
        for (int i = 0; i < me.nv && floodV < 0; i++) if (me.rise[i] == 3 && me.riseThr[i] > 100) floodV = i;
        me.setFlood(-1f);
        float yh = me.deformY(floodV, me.pos[3 * floodV + 1]);
        me.setFlood(1e5f);
        float ys = me.deformY(floodV, me.pos[3 * floodV + 1]);
        me.setFlood(-1f);
        ok(floodV >= 0 && yh < me.pos[3 * floodV + 1] - 500 && ys == me.pos[3 * floodV + 1] && com.dan.talsperre.world.Flood.length() > 300,
                String.format("Flutband: %.0f m lang, ohne Welle unsichtbar, danach da", com.dan.talsperre.world.Flood.length()));
        ok(t.sample(com.dan.talsperre.world.Ruins.BRIDGE_X, com.dan.talsperre.world.Ruins.BRIDGE_Z) < com.dan.talsperre.world.Ruins.DECK - 1
                        && Math.abs(t.sample(com.dan.talsperre.world.Ruins.BERICH_X, com.dan.talsperre.world.Ruins.BERICH_Z) - com.dan.talsperre.world.Ruins.BENCH) < 1.5,
                "Ruinen: Brücke über dem Flussbett, Dorf auf der Terrasse");
        // Ablauf Bresche 1943
        com.dan.talsperre.extras.Extras ex = new com.dan.talsperre.extras.Extras();
        WaterWorks w9 = new WaterWorks();
        com.dan.talsperre.core.Animals an9 = new com.dan.talsperre.core.Animals();
        com.dan.talsperre.core.Sprites sp9 = new com.dan.talsperre.core.Sprites();
        ex.startBreach();
        int bombers = 0; double maxFlow = 0, maxBomb = 0, maxBlast = 0; boolean mode2 = false;
        double front = -1;
        for (double tt = 0; tt < 70; tt += 0.05) {
            an9.clear(); sp9.clear();
            ex.update(0.05, wr, w9, an9, sp9, t);
            w9.update(0.05, wr, t);
            for (int i = 0; i < an9.n; i++) if (an9.kind[i] == com.dan.talsperre.core.Animals.BOMBER) bombers++;
            maxFlow = Math.max(maxFlow, ex.sndBreach); maxBomb = Math.max(maxBomb, ex.sndBomber); maxBlast = Math.max(maxBlast, ex.sndBlast);
            if ((wr.mesh().hide >> 4 & 1) == 1 && (wr.mesh().hide >> 7 & 1) == 0) mode2 = true;
            front = wr.mesh().flood;
        }
        ok(ex.wantDay == 137 && bombers > 100 && maxBomb > 0.5 && maxBlast > 0.9 && maxFlow > 0.5 && mode2 && w9.level < -15 && front > 300,
                String.format("Bresche 1943: Nacht 17. Mai, Bomber %d, Detonation, Mauer offen, Pegel %.1f m, Front %.0f m", bombers, w9.level, front));
        ex.reset();
        ex.update(0.05, wr, w9, an9, sp9, t);
        ok(wr.mesh().hide == hn && wr.mesh().flood < 0 && Math.abs(w9.level) < 1e-9, "Normalbetrieb stellt Mauer, Welle und Pegel wieder her");
        // Ablauf Bauzeitraffer
        ex.startBuild();
        double prevB = -1e9; boolean mono = true, sawLow = false, lakeHidden = false; double lastLevel = 99;
        for (double tt = 0; tt < 73; tt += 0.05) {
            an9.clear(); sp9.clear();
            ex.update(0.05, wr, w9, an9, sp9, t);
            float b = wr.mesh().buildY;
            if (b < 1e5f) { if (b < prevB - 1e-3) mono = false; prevB = b; if (b < -30) sawLow = true; }
            if ((wr.mesh().hide >> 3 & 1) == 1) lakeHidden = true;
            lastLevel = w9.level;
        }
        ok(mono && sawLow && prevB > 25 && lakeHidden && ex.scene() == com.dan.talsperre.extras.Extras.NONE && wr.mesh().buildY > 1e5f && Math.abs(lastLevel) < 1e-6,
                String.format("Bauzeitraffer: Bauhöhe wächst bis %.0f m, See erst danach, am Ende alles fertig", prevB));
        ex.setSection(true);
        ex.update(0.05, wr, w9, an9, sp9, t);
        ok(wr.mesh().hide == hs, "Mauerschnitt schaltet die Gruppen um");
        ex.setSection(false);
        ex.update(0.05, wr, w9, an9, sp9, t);
        // Ton
        com.dan.talsperre.audio.Soundscape so = new com.dan.talsperre.audio.Soundscape();
        so.volume = 0.8;
        boolean soundOk = true; double rmsAll = 0;
        for (int sc = 0; sc < 3; sc++) {
            so.flow = sc == 0 ? 0 : 1; so.dist = sc == 2 ? 15 : 200; so.night = sc == 1 ? 1 : 0; so.wind = 0.6;
            so.bomber = sc == 2 ? 1 : 0; so.flood = sc == 2 ? 1 : 0; so.blast = sc == 2 ? 1 : 0;
            float[] buf = new float[com.dan.talsperre.audio.Soundscape.RATE * 2];
            so.render(buf, buf.length);
            double sum = 0, mx = 0;
            for (float f : buf) { if (Float.isNaN(f)) soundOk = false; sum += f * f; mx = Math.max(mx, Math.abs(f)); }
            double rms = Math.sqrt(sum / buf.length);
            rmsAll += rms;
            if (mx > 0.8 + 1e-6 || rms < 0.0005) soundOk = false;
        }
        ok(soundOk, String.format("Ton: Tag, Nacht und Bresche erzeugen Signale ohne Zahlenfehler und ohne Übersteuern (RMS-Summe %.3f)", rmsAll));
        // Lupe
        com.dan.talsperre.extras.Lupe lu = new com.dan.talsperre.extras.Lupe();
        lu.setVisible(true);
        boolean lupeOk = true;
        for (int st = 0; st < 4; st++) {
            BufferedImage im = new BufferedImage(900, 640, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2 = im.createGraphics();
            lu.setStage(st);
            lu.paint(g2, 900, 640, 0, 640, 2.0);
            g2.dispose();
            int c0 = im.getRGB(450, 250), diff = 0;
            for (int x = 300; x < 600; x += 7) for (int y = 120; y < 380; y += 7) if (im.getRGB(x, y) != c0) diff++;
            if (diff < 100) lupeOk = false;
        }
        ok(lupeOk, "Mineral-Lupe zeichnet alle vier Stufen mit Inhalt");

        // Datenbank: Skripte und Zuschnitt (ohne Verbindung)
        try {
            java.util.List<String> setup = com.dan.talsperre.db.TalSetup.parse("tal_setup.sql");
            java.util.List<String> drop = com.dan.talsperre.db.TalSetup.parse("tal_drop.sql");
            ok(setup.size() == 38 && drop.size() == 1, "tal_setup.sql und tal_drop.sql lassen sich zerlegen: " + setup.size() + " und " + drop.size() + " Anweisungen");
            boolean prefix = true, semi = true;
            java.util.regex.Matcher mm = java.util.regex.Pattern.compile("(?i)^CREATE\\s+(?:TABLE|INDEX|VIEW)\\s+(\\w+)").matcher("");
            int created = 0;
            for (String st : setup) {
                mm.reset(st);
                if (mm.find()) { created++; if (!mm.group(1).toUpperCase().startsWith("TAL_")) prefix = false; }
                if (st.endsWith(";")) semi = false;
            }
            ok(prefix && created == 12, "Alle " + created + " angelegten Objekte tragen das Präfix TAL_");
            ok(semi, "Keine Anweisung endet mit Semikolon (JDBC)");
            // Geometrien aus dem Skript: Ringe geschlossen und gegen den Uhrzeigersinn, Koordinaten im Edertal
            boolean geoOk = true; int rings = 0, lines = 0;
            java.util.regex.Matcher gm = java.util.regex.Pattern.compile("SDO_GEOMETRY\\((200[23]), 8307, NULL, SDO_ELEM_INFO_ARRAY\\(([^)]*)\\), SDO_ORDINATE_ARRAY\\(([^)]*)\\)").matcher("");
            for (String st : setup) {
                gm.reset(st);
                while (gm.find()) {
                    String[] nums = gm.group(3).trim().split("\\s*,\\s*");
                    double[] co = new double[nums.length];
                    for (int i = 0; i < co.length; i++) co[i] = Double.parseDouble(nums[i]);
                    for (int i = 0; i < co.length; i += 2) if (co[i] < 8.9 || co[i] > 9.2 || co[i + 1] < 51.1 || co[i + 1] > 51.25) geoOk = false;
                    if (gm.group(1).equals("2003")) {
                        rings++;
                        int nn = co.length;
                        if (co[0] != co[nn - 2] || co[1] != co[nn - 1]) geoOk = false;
                        double area = 0;
                        for (int i = 0; i + 3 < nn; i += 2) area += co[i] * co[i + 3] - co[i + 2] * co[i + 1];
                        if (area <= 0) geoOk = false;
                    } else lines++;
                }
            }
            ok(geoOk && rings == 1 && lines == 1, "Geometrien im Skript: Seefläche geschlossen und gegen den Uhrzeigersinn, Mauerlinie, alle im Edertal");
            // Voreinstellungen: Schlüssel gegen das Bedienfeld
            java.util.Set<String> keys = new java.util.HashSet<>(java.util.Arrays.asList(com.dan.talsperre.ui.ControlPanel.KEYS));
            java.util.regex.Matcher km = java.util.regex.Pattern.compile("(?:SELECT|UNION ALL SELECT) '(\\w+)'(?: \\w)?(?:, '[^']*'(?: \\w)?)? FROM dual").matcher("");
            int seen = 0; boolean allKnown = true; String unknown = "";
            for (String st : setup) {
                if (!st.contains("tal_voreinstellung_wert")) continue;
                km.reset(st);
                while (km.find()) { seen++; if (!keys.contains(km.group(1))) { allKnown = false; unknown += km.group(1) + " "; } }
            }
            ok(allKnown && seen >= 35, "Schlüssel der Voreinstellungen im Skript sind alle im Bedienfeld bekannt (" + seen + " Werte)" + (unknown.isEmpty() ? "" : ", unbekannt: " + unknown));
        } catch (Exception e) {
            ok(false, "Datenbankskripte: " + e);
        }
        // Fahrten und Blickpunkte so, wie das Setup sie ablegt
        int poses = 0, shots = 0;
        for (int i = 0; i < com.dan.talsperre.camera.Tours.NAMES.length; i++) {
            com.dan.talsperre.camera.Director.Program pg = com.dan.talsperre.camera.Tours.get(i, t);
            shots += pg.shots.size();
            for (com.dan.talsperre.camera.Director.Shot sh : pg.shots) poses += sh.path.size();
        }
        ok(shots >= 9 && poses >= 50 && Viewpoint.all(t).length == 17, "Setup-Daten aus dem Modell: 17 Blickpunkte, " + com.dan.talsperre.camera.Tours.NAMES.length + " Fahrten, " + shots + " Einstellungen, " + poses + " Posen");
        // Kraftwerksdampf: mit und ohne, sonst gleich (Dampfteilchen im Umkreis der Maschinenhalle)
        {
            int[] cnt = new int[2];
            for (int run = 0; run < 2; run++) {
                WaterWorks w10 = new WaterWorks();
                w10.wisps = false; w10.sprayMist = 0; w10.lakeMist = 0;
                w10.plant = run == 0 ? 0 : 2;
                for (int i = 0; i < 160; i++) w10.update(0.05, wr, t);
                for (int i = 0; i < w10.particles.n; i++) {
                    double x = w10.particles.x[i], z = w10.particles.z[i];
                    if (Math.hypot(x - 73, z + 62) < 45 && w10.particles.kind[i] == com.dan.talsperre.effects.ParticleSystem.STEAM) cnt[run]++;
                }
            }
            ok(cnt[1] > cnt[0] + 10, "Kraftwerksdampf entsteht an der Maschinenhalle (" + cnt[1] + " Dampfteilchen dort, ohne " + cnt[0] + ")");
        }

        System.out.println(fails == 0 ? "Alles in Ordnung." : fails + " Prüfung(en) fehlgeschlagen.");
        System.exit(fails == 0 ? 0 : 1);
    }
}
