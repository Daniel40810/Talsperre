package com.dan.talsperre.ui;

import com.dan.talsperre.camera.CameraController;
import com.dan.talsperre.camera.Director;
import com.dan.talsperre.camera.Tours;
import com.dan.talsperre.camera.Viewpoint;
import com.dan.talsperre.core.Camera;
import com.dan.talsperre.core.Engine3D;
import com.dan.talsperre.core.Scene;
import com.dan.talsperre.effects.DayNightCycle;
import com.dan.talsperre.effects.LightingEngine;
import com.dan.talsperre.world.Valley;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Die Szene im Fenster: baut das Tal im Hintergrund, rechnet Bilder auf einem eigenen Faden und
 * zeigt sie. Maus dreht (links) und verschiebt (rechts oder Mitte), das Rad zoomt, W A S D Q E
 * bewegen den Drehpunkt. Himmel und Schatten rechnet ein zweiter Faden neu, sobald Uhrzeit oder
 * Dunst sich ändern. Gerüst aus Phase 1; Bedienfeld, Kinomodus und Regie folgen.
 */
public final class ViewPanel extends JPanel {
    private static final Color BG = new Color(14, 20, 24), INK = new Color(222, 232, 232), MUTED = new Color(150, 165, 168);

    private volatile Engine3D engine;
    private volatile CameraController ctl;
    private volatile Director director;
    private volatile boolean cinema, wasDirected;
    private static final double CINEMA = 0.78;
    private volatile Scene scene;
    private final Camera cam = new Camera();
    private final com.dan.talsperre.water.WaterWorks water = new com.dan.talsperre.water.WaterWorks();
    private final DayNightCycle cycle = new DayNightCycle();
    private final com.dan.talsperre.core.Animals animals = new com.dan.talsperre.core.Animals();
    private final com.dan.talsperre.extras.Extras extras = new com.dan.talsperre.extras.Extras();
    private final com.dan.talsperre.extras.Lupe lupe = new com.dan.talsperre.extras.Lupe();
    private final com.dan.talsperre.audio.Soundscape sound = new com.dan.talsperre.audio.Soundscape();
    private volatile double clockT;
    private volatile BufferedImage shown;
    private volatile String loading = "Das Edertal entsteht …";
    private volatile boolean running = true, sunDirty = true, autoQuality = true;
    private volatile int day = 274;
    private volatile double hour = 10, haze = 0.12, timeLapse = 0;
    private volatile double fps, renderMs, buildMs;
    private volatile int tris;
    private volatile int viewpoint = -1;
    private double sinceMove = 1;
    private Consumer<String> status = s -> { };
    private Runnable onReady = () -> { };
    private int lastX, lastY;

    public ViewPanel() {
        setBackground(BG);
        setFocusable(true);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { lastX = e.getX(); lastY = e.getY(); requestFocusInWindow(); interrupt(); }
            @Override public void mouseDragged(MouseEvent e) {
                CameraController c = ctl;
                if (c == null) return;
                interrupt();
                boolean pan = SwingUtilities.isRightMouseButton(e) || SwingUtilities.isMiddleMouseButton(e) || e.isShiftDown();
                c.drag(e.getX() - lastX, e.getY() - lastY, pan);
                lastX = e.getX(); lastY = e.getY();
                sinceMove = 0;
            }
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                int ph0 = getHeight(), ih0 = cinema ? (int) Math.round(ph0 * CINEMA) : ph0;
                if (lupe.hit(e.getX(), e.getY(), getWidth(), ph0, (ph0 - ih0) / 2, ih0)) { lupe.step(e.getWheelRotation() > 0 ? 1 : -1); return; }
                CameraController c = ctl;
                if (c != null) { interrupt(); c.wheel(e.getPreciseWheelRotation()); sinceMove = 0; }
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
                Engine3D r = engine; CameraController c = ctl; BufferedImage im = shown;
                if (r == null || c == null || im == null) return;
                double[] p = r.pick((int) (e.getX() * (double) im.getWidth() / Math.max(1, getWidth())),
                        (int) (e.getY() * (double) im.getHeight() / Math.max(1, getHeight())));
                c.focus(p);
                sinceMove = 0;
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
        addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { key(e, true); }
            @Override public void keyReleased(KeyEvent e) { key(e, false); }
        });
    }

    private void key(KeyEvent e, boolean down) {
        CameraController c = ctl;
        if (c == null) return;
        int k;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE: if (down) interrupt(); return;
            case KeyEvent.VK_W: k = CameraController.K_W; break;
            case KeyEvent.VK_A: k = CameraController.K_A; break;
            case KeyEvent.VK_S: k = CameraController.K_S; break;
            case KeyEvent.VK_D: k = CameraController.K_D; break;
            case KeyEvent.VK_Q: k = CameraController.K_Q; break;
            case KeyEvent.VK_E: k = CameraController.K_E; break;
            case KeyEvent.VK_SHIFT: k = CameraController.K_SHIFT; break;
            default: return;
        }
        if (down) interrupt();
        c.setKey(k, down);
        sinceMove = 0;
    }

    public void setStatusListener(Consumer<String> l) { status = l; }
    public void setOnReady(Runnable r) { onReady = r; }

    // ------------------------------------------------------------ Bedienung von außen

    public void setTime(int day, double hour) { this.day = day; this.hour = hour; sunDirty = true; }
    public void setHaze(double h) { haze = h; sunDirty = true; }
    public void setFog(double f) { Engine3D r = engine; if (r != null) r.fogScale = f; sinceMove = 0; }
    public void setRays(boolean on) { Engine3D r = engine; if (r != null) r.rays = on; }
    public void setRainbow(boolean on) { Engine3D r = engine; if (r != null) r.rainbow = on; }
    /** Leben ein- und ausschalten: "traffic", "ships", "birds", "walkers". */
    public void setLife(String what, boolean on) {
        com.dan.talsperre.world.Life l = scene == null ? null : scene.life;
        if (l == null) return;
        switch (what) {
            case "traffic": l.traffic = on; break;
            case "ships": l.ships = on; break;
            case "birds": l.birds = on; break;
            default: l.walkers = on;
        }
    }
    public void setLamps(boolean on) { Engine3D r = engine; if (r != null) r.lampsEnabled = on; }
    public void setRayGain(double g) { Engine3D r = engine; if (r != null) r.rayGain = (float) g; }
    /** Zeitraffer: Stunden je Sekunde, 0 = aus. */
    public void setTimeLapse(double hoursPerSecond) { timeLapse = hoursPerSecond; }
    public double timeLapse() { return timeLapse; }
    public void setBloom(boolean on) { Engine3D r = engine; if (r != null) r.bloom = on; }
    public void setAutoOrbit(boolean on) { CameraController c = ctl; if (c != null) c.autoOrbit = on; }
    public void setAutoQuality(boolean on) { autoQuality = on; }
    public int day() { return day; }
    public double hour() { return hour; }
    public boolean ready() { return engine != null; }
    public Scene scene() { return scene; }
    public com.dan.talsperre.extras.Extras extras() { return extras; }
    public com.dan.talsperre.extras.Lupe lupe() { return lupe; }
    public com.dan.talsperre.audio.Soundscape sound() { return sound; }
    public com.dan.talsperre.water.WaterWorks water() { return water; }

    /** Fliegt weich zum Blickpunkt i (Index in {@link Viewpoint#NAMES}). */
    public void goTo(int i) {
        Scene sc = scene; Director d = director;
        if (sc == null || d == null || ctl == null) return;
        Viewpoint[] vp = Viewpoint.all(sc.terrain);
        if (i < 0 || i >= vp.length) return;
        if (vp[i].name.equals("Mauerschnitt")) extras.setSection(true);
        else if (extras.scene() == com.dan.talsperre.extras.Extras.SECTION) extras.setSection(false);
        if (vp[i].name.equals("Alte Brücke")) water.level = -14;
        else if (vp[i].name.equals("Berich")) water.level = -19;
        d.goTo(vp[i], cam);
        viewpoint = i;
        sinceMove = 0;
    }

    /** Augpunkt und ein Blickziel 60 m voraus (ex, ey, ez, tx, ty, tz) als Pose für gemerkte Blickpunkte. */
    public double[] cameraPose() {
        Camera c = cam;
        double d = 60;
        return new double[]{c.ex, c.ey, c.ez, c.ex + c.fx * d, c.ey + c.fy * d, c.ez + c.fz * d};
    }

    /** Fliegt weich zu einer gemerkten Pose (zum Beispiel aus der Datenbank). */
    public void goToPose(String name, double[] pose) {
        Director d = director;
        if (d == null || ctl == null || pose == null || pose.length < 6) return;
        if (extras.scene() == com.dan.talsperre.extras.Extras.SECTION) extras.setSection(false);
        d.goTo(new Viewpoint(name, "Gemerkter Blickpunkt", pose), cam);
        viewpoint = -1;
        sinceMove = 0;
    }

    /** Beendet die Regie; die Steuerung übernimmt die Kamera dort, wo sie steht. */
    public void interrupt() {
        Director d = director; CameraController c = ctl;
        if (d == null || c == null || !d.active()) return;
        double dist = d.targetDistance();
        d.stop();
        d.clearCaption();
        wasDirected = false;
        c.adopt(cam, Math.max(30, dist));
        sinceMove = 0;
    }

    public void playTour(int i) {
        Scene sc = scene; Director d = director;
        if (sc == null || d == null) return;
        d.play(Tours.get(i, sc.terrain));
        viewpoint = -1;
    }

    public boolean directing() { Director d = director; return d != null && d.active(); }
    public void setCinema(boolean on) { cinema = on; sinceMove = 0; }

    /** Setzt den Blickpunkt i sofort, ohne Flug (Start). */
    private void jumpTo(int i) {
        Scene sc = scene; CameraController c = ctl;
        if (sc == null || c == null) return;
        Viewpoint[] vp = Viewpoint.all(sc.terrain);
        if (i < 0 || i >= vp.length) return;
        double[] p = vp[i].pose;
        Camera t = new Camera();
        t.ex = p[0]; t.ey = p[1]; t.ez = p[2];
        t.lookAt(p[3], p[4], p[5]);
        double d = Math.max(30, Math.sqrt((p[3] - p[0]) * (p[3] - p[0]) + (p[4] - p[1]) * (p[4] - p[1]) + (p[5] - p[2]) * (p[5] - p[2])));
        c.adopt(t, d);
        viewpoint = i;
        sinceMove = 0;
    }

    // ------------------------------------------------------------ Start und Schleifen

    public void start() {
        Thread build = new Thread(this::buildAndRun, "Talsperre-Bau");
        build.setDaemon(true);
        build.start();
    }

    public void stop() { running = false; sound.stop(); }

    private void buildAndRun() {
        try {
            long t0 = System.nanoTime();
            Valley v = Valley.build();
            buildMs = (System.nanoTime() - t0) / 1e6;
            loading = "Die Sonne steht über dem Tal …";
            repaint();
            Engine3D r = new Engine3D(v.scene, 4096);
            cycle.set(day, hour);
            r.day = day; r.hour = hour; r.sidereal = cycle.siderealDeg;
            r.fogScale = 0.5;
            r.setSky(cycle, haze);
            sunDirty = false;
            CameraController c = new CameraController(v.terrain);
            scene = v.scene;
            ctl = c;
            director = new Director(v.terrain);
            r.animals = animals;
            engine = r;
            jumpTo(0);
            loading = null;
            sound.start();
            SwingUtilities.invokeLater(onReady);
        } catch (Throwable ex) {
            loading = "Fehler beim Aufbau: " + ex;
            repaint();
            ex.printStackTrace();
            return;
        }
        Thread lw = new Thread(this::lightLoop, "Talsperre-Licht");
        lw.setDaemon(true);
        lw.start();
        renderLoop();
    }

    /** Rechnet Himmel und Schattenkarten neu, sobald sich Sonne oder Dunst ändern; im Hintergrund. */
    private void lightLoop() {
        DayNightCycle dc = new DayNightCycle();
        while (running) {
            Engine3D r = engine;
            if (r == null || !sunDirty) { sleep(8); continue; }
            LightingEngine spare = r.takeSpare();
            if (spare == null) { sleep(4); continue; }
            sunDirty = false;
            dc.set(day, hour);
            spare.compute(r.mesh(), dc.dir, dc.moonDir, dc.moonLit, haze);
            r.day = dc.day(); r.hour = dc.hour(); r.sidereal = dc.siderealDeg;
            r.offer(spare);
        }
    }

    private void renderLoop() {
        Engine3D r = engine;
        CameraController c = ctl;
        long last = System.nanoTime();
        double t = 0, fpsAcc = 0;
        int frames = 0;
        while (running) {
            long now = System.nanoTime();
            double dt = Math.min(0.1, (now - last) / 1e9);
            last = now;
            t += dt;
            int pw = Math.max(64, getWidth()), ph = Math.max(64, getHeight());
            sinceMove += dt;
            double scale = autoQuality ? (sinceMove < 0.4 ? 0.6 : 1.0) : 1.0;
            double hh = cinema ? CINEMA : 1;
            r.cropY = hh;
            r.setSize(Math.max(64, (int) (pw * scale)), Math.max(64, (int) (ph * hh * scale)));
            Director dir = director;
            boolean directed = dir != null && dir.update(dt, cam);
            if (directed) {
                wasDirected = true;
                sinceMove = 0;
                double dh = dir.hour();
                if (!Double.isNaN(dh) && Math.abs(dh - hour) > 0.01) { hour = dh; sunDirty = true; }
            } else {
                if (wasDirected) { wasDirected = false; c.adopt(cam, Math.max(30, dir.targetDistance())); }
                c.update(dt, cam);
            }
            water.update(dt, r, scene.terrain);
            com.dan.talsperre.world.Life lf = scene.life;
            if (lf != null && !extras.suppressLife()) lf.update(t, water.level, r.lampLevel(), animals, r.sprites);
            else if (extras.suppressLife()) { animals.clear(); r.sprites.clear(); }
            if (dir != null) {
                String tg = dir.takeTrigger();
                if (tg != null) trigger(tg);
            }
            extras.update(dt, r, water, animals, r.sprites, scene.terrain);
            int wd = extras.wantDay;
            if (wd > 0) { extras.wantDay = -1; day = wd; hour = extras.wantHour; sunDirty = true; }
            if (extras.lightDirty) { extras.lightDirty = false; sunDirty = true; }
            clockT = t;
            sound.flow = Math.min(1, (water.outflow + water.spillFlow * 1.5) / 450.0);
            sound.dist = Math.sqrt(cam.ex * cam.ex + (cam.ey + 40) * (cam.ey + 40) + (cam.ez + 70) * (cam.ez + 70));
            sound.wind = water.wind; sound.night = r.lampLevel();
            if (lf != null) { sound.birds = lf.birds; sound.traffic = lf.traffic; sound.ships = lf.ships; }
            sound.bomber = extras.sndBomber; sound.flood = extras.sndBreach; sound.blast = extras.sndBlast;
            if (timeLapse != 0) {
                double h = hour + dt * timeLapse;
                while (h >= 24) { h -= 24; day = day % 365 + 1; }
                hour = h; sunDirty = true;
            }
            long f0 = System.nanoTime();
            shown = r.render(cam, t, dt);
            renderMs = (System.nanoTime() - f0) / 1e6;
            tris = r.drawnTris;
            repaint();
            frames++;
            fpsAcc += dt;
            if (fpsAcc >= 0.5) {
                fps = frames / fpsAcc; frames = 0; fpsAcc = 0;
                String ttl = dir != null ? dir.title() : null;
                status.accept(String.format("%s · %s %s · %.0f Bilder/s (%.0f ms) · %,d Dreiecke",
                        ttl != null ? "Regie: " + ttl : viewpoint >= 0 ? Viewpoint.NAMES[viewpoint] : "frei", DayNightCycle.dateLabel(day),
                        DayNightCycle.timeLabel(hour) + " " + DayNightCycle.zone(day, hour), fps, renderMs, tris));
            }
            long spent = (System.nanoTime() - now) / 1_000_000;
            if (spent < 16) sleep(16 - spent);
        }
    }

    /** Ereignisse aus den Drehbüchern der Regie. */
    private void trigger(String g) {
        switch (g) {
            case "build": extras.startBuild(); break;
            case "breach": extras.startBreach(); break;
            case "section": extras.setSection(true); break;
            default: break;
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        BufferedImage im = shown;
        if (im != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int pw = getWidth(), ph = getHeight();
            int ih = cinema ? (int) Math.round(ph * CINEMA) : ph, iy = (ph - ih) / 2;
            if (cinema) { g.setColor(Color.BLACK); g.fillRect(0, 0, pw, ph); }
            g.drawImage(im, 0, iy, pw, ih, null);
            overlay(g, pw, ph, iy, ih);
        }
        String l = loading;
        if (l != null) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font("SansSerif", Font.PLAIN, 18));
            g.setColor(INK);
            g.drawString("Talsperre", 40, getHeight() / 2 - 14);
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.setColor(MUTED);
            g.drawString(l, 40, getHeight() / 2 + 8);
            javax.swing.Timer tm = new javax.swing.Timer(120, e -> repaint());
            tm.setRepeats(false);
            tm.start();
        }
    }

    /** Blende, Tafel und Zeitleiste der Regie über dem Bild. */
    private void overlay(Graphics2D g, int pw, int ph, int iy, int ih) {
        Director d = director;
        if (d == null) return;
        Camera cc = new Camera();
        cc.copyFrom(cam);
        extras.paint(g, cc, pw, ph, iy, ih);
        lupe.paint(g, pw, ph, iy, ih, clockT);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double fade = d.fade();
        if (fade > 0.001) {
            g.setColor(new Color(0, 0, 0, (int) Math.round(255 * fade)));
            g.fillRect(0, iy, pw, ih);
        }
        Object[] cap = d.caption();
        if (cap != null) {
            float a = ((Number) cap[3]).floatValue();
            String head = (String) cap[0], text = (String) cap[1];
            Font fh = new Font("SansSerif", Font.BOLD, 18), ft = new Font("SansSerif", Font.PLAIN, 13);
            int maxW = Math.min(520, pw - 60);
            java.util.List<String> lines = new java.util.ArrayList<>();
            if (text != null) {
                java.awt.FontMetrics fm = g.getFontMetrics(ft);
                StringBuilder cur = new StringBuilder();
                for (String w : text.split(" ")) {
                    if (cur.length() > 0 && fm.stringWidth(cur + " " + w) > maxW) { lines.add(cur.toString()); cur.setLength(0); }
                    if (cur.length() > 0) cur.append(' ');
                    cur.append(w);
                }
                if (cur.length() > 0) lines.add(cur.toString());
            }
            int bh = (head != null ? 30 : 8) + lines.size() * 18 + 12;
            int bx = 28, by = iy + ih - bh - 36;
            g.setColor(new Color(8, 14, 18, (int) (170 * a)));
            g.fillRoundRect(bx, by, maxW + 28, bh, 12, 12);
            int y = by + 10;
            if (head != null) {
                g.setFont(fh); g.setColor(new Color(235, 245, 245, (int) (255 * a)));
                y += 18; g.drawString(head, bx + 14, y); y += 8;
            }
            g.setFont(ft); g.setColor(new Color(190, 205, 208, (int) (255 * a)));
            for (String l : lines) { y += 18; g.drawString(l, bx + 14, y); }
        }
        if (d.active()) {
            int bx = 28, bw = pw - 56, by = iy + ih - 18;
            g.setColor(new Color(255, 255, 255, 50));
            g.fillRect(bx, by, bw, 3);
            g.setColor(new Color(63, 176, 198, 230));
            g.fillRect(bx, by, (int) Math.round(bw * d.progress()), 3);
            g.setColor(new Color(255, 255, 255, 140));
            for (double m : d.marks()) g.fillRect(bx + (int) Math.round(bw * m) - 1, by - 3, 2, 9);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(190, 205, 208, 200));
            String tm = String.format("%d:%02d / %d:%02d  ·  Esc beendet", (int) d.elapsedSeconds() / 60, (int) d.elapsedSeconds() % 60,
                    (int) d.totalSeconds() / 60, (int) d.totalSeconds() % 60);
            g.drawString(tm, bx, by - 8);
        }
    }
}
