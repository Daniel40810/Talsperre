package com.dan.talsperre.camera;

import com.dan.talsperre.core.Camera;
import com.dan.talsperre.core.Terrain;

import java.util.ArrayList;
import java.util.List;

/**
 * Regie (aus Semiramis): spielt Programme aus Einstellungen ab. Jede Einstellung ist eine
 * Kamerafahrt, dazu optional eine Uhrzeit, die währenddessen läuft, der Ort (Upper Basin oder
 * Midway), ein Geysir, der nach einigen Sekunden ausbricht, und eine Tafel mit Text und Quelle.
 * An den Schnitten wird ab- und aufgeblendet. Übergänge zu Blickpunkten, Fahrten, der Rundgang und
 * das Drehbuch sind alles solche Programme.
 * <p>
 * Solange ein Programm läuft, gehört die Kamera der Regie; jede Eingabe beendet es und die
 * Kamerasteuerung übernimmt an Ort und Stelle ({@link CameraController#adopt}). Das Auge bleibt
 * immer mindestens 1,2 m über dem Gelände.
 */
public final class Director {

    /** Eine Einstellung. */
    public static final class Shot {
        public final CameraPath path;
        public final double h0, h1;          // Uhrzeit am Anfang und Ende, NaN = nicht ändern
        public final String head, text, source;
        public boolean fadeIn, fadeOut;
        /** Ort: 0 Upper Geyser Basin, 1 Midway, 2 Lower Geyser Basin, −1 unverändert. */
        public int site = -1;
        /** Geysir, der triggerAt Sekunden nach Beginn der Einstellung ausbricht (oder null). */
        public String trigger;
        public double triggerAt;

        public Shot(CameraPath path, double h0, double h1, String head, String text, String source) {
            this.path = path; this.h0 = h0; this.h1 = h1; this.head = head; this.text = text; this.source = source;
        }

        public Shot(CameraPath path) { this(path, Double.NaN, Double.NaN, null, null, null); }

        public Shot site(int s) { site = s; return this; }
        public Shot trigger(String g, double at) { trigger = g; triggerAt = at; return this; }
        public Shot fades(boolean in, boolean out) { fadeIn = in; fadeOut = out; return this; }
    }

    /** Ein Programm aus Einstellungen. */
    public static final class Program {
        public final String title;
        public final List<Shot> shots = new ArrayList<>();
        /** Ohne Tafeln der Absteckung (Fahrten, Rundgang, Drehbuch); Übergänge zeigen sie. */
        public boolean clean = true;

        public Program(String title) { this.title = title; }

        public Program add(Shot s) { shots.add(s); return this; }

        public double duration() {
            double d = 0;
            for (Shot s : shots) d += s.path.duration();
            return d;
        }
    }

    public static final double FADE = 0.8;

    private final Terrain terrain;
    private Program prog;
    private int shot;
    private double t, elapsed;
    private final double[] pose = new double[6];
    /** Ereignisse für die Bildschleife: eben begonnene Einstellung, fälliger Ausbruch. */
    private Shot started;
    private String pendingTrigger;
    private boolean triggered;

    public Director(Terrain terrain) { this.terrain = terrain; }

    public synchronized boolean active() { return prog != null; }

    public synchronized void stop() { prog = null; started = null; pendingTrigger = null; }

    public synchronized void play(Program p) {
        prog = p.shots.isEmpty() ? null : p;
        shot = 0;
        t = 0;
        elapsed = 0;
        triggered = false;
        started = prog == null ? null : p.shots.get(0);
        afterCaption = null;
    }

    /** Übergang von der aktuellen Kamera zum Blickpunkt v: bei weiten Wegen im Bogen über das Becken. */
    public synchronized void goTo(Viewpoint v, Camera cam) {
        double[] a = currentPose(cam);
        CameraPath p = new CameraPath();
        p.add(0, a[0], a[1], a[2], a[3], a[4], a[5]);
        double[] b = v.pose;
        double dx = b[0] - a[0], dz = b[2] - a[2];
        double hd = Math.sqrt(dx * dx + dz * dz);
        double tt = 0;
        if (hd > 250) {
            double lift = Math.max(a[1], b[1]) + 60 + Math.min(900, hd * 0.18);
            double[] mid = {(a[0] + b[0]) / 2, lift, (a[2] + b[2]) / 2, (a[3] + b[3]) / 2, (a[4] + b[4]) / 2, (a[5] + b[5]) / 2};
            double leg = legTime(a, mid, 2.0);
            p.then(leg, mid);
            tt += leg;
            p.then(legTime(mid, b, 2.0), b);
        } else {
            p.then(legTime(a, b, 1.8), b);
        }
        Program pr = new Program(v.name);
        pr.clean = false;
        pr.add(new Shot(p, Double.NaN, Double.NaN, v.name, v.note, null));
        play(pr);
    }

    private static double legTime(double[] a, double[] b, double min) {
        double d = dist3(a[0], a[1], a[2], b);
        double ang = Math.abs(Math.atan2(b[3] - b[0], b[5] - b[2]) - Math.atan2(a[3] - a[0], a[5] - a[2]));
        if (ang > Math.PI) ang = 2 * Math.PI - ang;
        return Math.max(min, Math.max(1.0 + 1.1 * Math.log1p(d / 40), ang * 0.9));
    }

    private static double dist3(double x, double y, double z, double[] b) {
        double dx = b[0] - x, dy = b[1] - y, dz = b[2] - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Aktuelle Pose der Kamera mit einem Blickziel in sinnvollem Abstand. */
    private double[] currentPose(Camera c) {
        double h = c.ey - terrain.sample(c.ex, c.ez);
        double d = Math.max(30, Math.min(600, h * 2 + 60));
        return new double[]{c.ex, c.ey, c.ez, c.ex + c.fx * d, c.ey + c.fy * d, c.ez + c.fz * d};
    }

    /**
     * Ein Zeitschritt. Setzt die Kamera und liefert true, solange das Programm läuft; am Ende
     * einmal false (die Kamera steht dann auf der letzten Pose).
     */
    public synchronized boolean update(double dt, Camera cam) {
        if (prog == null) return false;
        t += dt;
        elapsed += dt;
        Shot s = prog.shots.get(shot);
        while (t >= s.path.duration()) {
            if (shot + 1 >= prog.shots.size()) {
                s.path.sample(s.path.duration(), pose);
                apply(cam);
                Object[] c = captionOf(s, s.path.duration() - 1.2);
                afterCaption = prog.shots.size() == 1 ? c : null;
                afterUntil = System.nanoTime() + 5_000_000_000L;
                prog = null;
                return false;
            }
            t -= s.path.duration();
            shot++;
            s = prog.shots.get(shot);
            started = s;
            triggered = false;
        }
        if (!triggered && s.trigger != null && t >= s.triggerAt) {
            triggered = true;
            pendingTrigger = s.trigger;
        }
        s.path.sample(t, pose);
        apply(cam);
        return true;
    }

    private void apply(Camera cam) {
        double floor = terrain.sample(pose[0], pose[2]) + 1.2;
        cam.ex = pose[0]; cam.ey = Math.max(pose[1], floor); cam.ez = pose[2];
        cam.lookAt(pose[3], pose[4], pose[5]);
    }

    /** Einstellung, die seit dem letzten Aufruf begonnen hat (Ort und Tag setzen), oder null. */
    public synchronized Shot takeStarted() { Shot s = started; started = null; return s; }

    /** Geysir, der jetzt ausbrechen soll, oder null. */
    public synchronized String takeTrigger() { String g = pendingTrigger; pendingTrigger = null; return g; }

    /** Abstand zum Blickziel der laufenden Pose (für die Übernahme durch den Orbit). */
    public synchronized double targetDistance() {
        double dx = pose[3] - pose[0], dy = pose[4] - pose[1], dz = pose[5] - pose[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ------------------------------------------------------------ Anzeige

    public synchronized String title() { return prog == null ? null : prog.title; }

    /** true, solange ein Programm ohne Beschriftung läuft. */
    public synchronized boolean clean() { return prog != null && prog.clean; }

    public synchronized double progress() {
        if (prog == null) return 0;
        double d = prog.duration();
        return d <= 0 ? 1 : Math.min(1, elapsed / d);
    }

    public synchronized double totalSeconds() { return prog == null ? 0 : prog.duration(); }
    public synchronized double elapsedSeconds() { return elapsed; }

    /** Anteile, an denen eine neue Einstellung beginnt (für die Zeitleiste). */
    public synchronized double[] marks() {
        if (prog == null || prog.shots.size() < 2) return new double[0];
        double d = prog.duration(), acc = 0;
        double[] m = new double[prog.shots.size() - 1];
        for (int i = 0; i < m.length; i++) { acc += prog.shots.get(i).path.duration(); m[i] = acc / d; }
        return m;
    }

    /** Uhrzeit, die die laufende Einstellung vorgibt, oder NaN. */
    public synchronized double hour() {
        if (prog == null) return Double.NaN;
        Shot s = prog.shots.get(shot);
        if (Double.isNaN(s.h0)) return Double.NaN;
        double u = Math.min(1, t / s.path.duration());
        double h1 = s.h1 < s.h0 ? s.h1 + 24 : s.h1;
        return (s.h0 + (h1 - s.h0) * u) % 24;
    }

    /** Schwarzblende 0..1 an den Schnitten. */
    public synchronized double fade() {
        if (prog == null) return 0;
        Shot s = prog.shots.get(shot);
        double d = s.path.duration(), f = 0;
        if (s.fadeIn && t < FADE) f = 1 - t / FADE;
        if (s.fadeOut && t > d - FADE) f = Math.max(f, (t - (d - FADE)) / FADE);
        return Math.max(0, Math.min(1, f));
    }

    /** Tafel der laufenden Einstellung: Überschrift, Text, Quelle, Deckkraft; oder null. */
    public synchronized Object[] caption() {
        if (prog == null) {
            if (afterCaption == null) return null;
            double left = (afterUntil - System.nanoTime()) / 1e9;
            if (left <= 0) { afterCaption = null; return null; }
            return new Object[]{afterCaption[0], afterCaption[1], afterCaption[2], Math.min(1, left / 1.2)};
        }
        return captionOf(prog.shots.get(shot), t);
    }

    private Object[] afterCaption;
    private long afterUntil;

    public synchronized void clearCaption() { afterCaption = null; }

    private Object[] captionOf(Shot s, double t) {
        if (s.head == null && s.text == null) return null;
        double d = s.path.duration();
        double a = Math.min(1, Math.min((t - 0.8) / 1.0, (d - 0.9 - t) / 1.0));
        if (prog != null && prog.shots.size() == 1) a = Math.min(1, t / 0.4);
        if (a <= 0) return null;
        return new Object[]{s.head, s.text, s.source, a};
    }
}
