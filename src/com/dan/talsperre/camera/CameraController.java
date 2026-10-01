package com.dan.talsperre.camera;

import com.dan.talsperre.core.Camera;
import com.dan.talsperre.core.Terrain;

/**
 * Kamerasteuerung: Die Kamera kreist um einen Drehpunkt (Orbit mit Zoom). Maus dreht und
 * verschiebt, das Rad zoomt von der Totale bis nah heran, W A S D schieben den Drehpunkt
 * über das Gelände, Q und E heben und senken ihn. Alle Werte gleiten weich nach
 * (Ease-out), damit keine Bewegung ruckt. Aus Semiramis übernommen; neu ist der Boden: Kamera und
 * Drehpunkt bleiben über dem Gelände, das hier nicht eben ist. Mit {@link #adopt} übernimmt die
 * Steuerung die Kamera dort, wo eine Fahrt sie gelassen hat (Regie ab Phase 6).
 */
public final class CameraController {
    public static final int K_W = 0, K_A = 1, K_S = 2, K_D = 3, K_Q = 4, K_E = 5, K_SHIFT = 6;

    /** Übersicht: von Südosten über das ganze Becken nach Nordwesten, Old Faithful vorn. */
    static final double HOME_X = -520, HOME_Z = -700, HOME_YAW = Math.toRadians(62), HOME_PITCH = Math.toRadians(19), HOME_DIST = 1750;
    public static final double MAX_DIST = 14000;

    private final Terrain terrain;
    // Ziel- und aktuelle Werte (weich nachgeführt)
    private double gtx = HOME_X, gty = 0, gtz = HOME_Z, gyaw = HOME_YAW, gpitch = HOME_PITCH, gdist = HOME_DIST;
    private double tx = gtx, ty = gty, tz = gtz, yaw = gyaw - 0.6, pitch = gpitch + 0.3, dist = 6500;

    public CameraController(Terrain t) {
        terrain = t;
        gty = ty = t.sample(HOME_X, HOME_Z) + 8;
    }
    private final boolean[] keys = new boolean[7];

    public volatile boolean autoOrbit;

    public synchronized void setKey(int k, boolean down) { if (k >= 0 && k < keys.length) keys[k] = down; }

    public synchronized void releaseKeys() { java.util.Arrays.fill(keys, false); }

    public synchronized void drag(double dx, double dy, boolean pan) {
        if (pan) {
            double k = gdist * 0.0016;
            double sx = Math.cos(gyaw), sz = -Math.sin(gyaw);
            double fx = -Math.sin(gyaw), fz = -Math.cos(gyaw);
            gtx += (-dx * sx + dy * fx) * k;
            gtz += (-dx * sz + dy * fz) * k;
        } else {
            gyaw -= dx * 0.006;
            gpitch = clamp(gpitch + dy * 0.0045, Math.toRadians(-35), Math.toRadians(89));
        }
    }

    public synchronized void wheel(double notches) {
        gdist = clamp(gdist * Math.pow(1.13, notches), 3, MAX_DIST);
    }

    /** Doppelklick: neuer Drehpunkt, etwas näher heran. */
    public synchronized void focus(double[] p) {
        if (p == null) return;
        gtx = p[0]; gty = p[1] + 1; gtz = p[2];
        gdist = Math.max(12, gdist * 0.6);
    }

    /** Zurück zur Übersicht über das Becken. */
    public synchronized void goOverview() {
        gtx = HOME_X; gty = terrain.sample(HOME_X, HOME_Z) + 8; gtz = HOME_Z; gyaw = nearestYaw(HOME_YAW); gpitch = HOME_PITCH; gdist = HOME_DIST;
    }

    /** Gleitet zu einem Punkt: Drehpunkt dort, Blick aus Richtung yawDeg (NaN: Richtung bleibt), Neigung und Abstand. */
    public synchronized void flyTo(double x, double y, double z, double yawDeg, double pitchDeg, double d) {
        gtx = x; gty = y; gtz = z;
        if (!Double.isNaN(yawDeg)) gyaw = nearestYaw(Math.toRadians(yawDeg));
        gpitch = Math.toRadians(pitchDeg);
        gdist = clamp(d, 3, MAX_DIST);
    }

    private double nearestYaw(double target) {
        double d = target - gyaw;
        d -= Math.round(d / (2 * Math.PI)) * 2 * Math.PI;
        return gyaw + d;
    }

    public synchronized double distance() { return dist; }

    /** Zielpose zum Merken: Drehpunkt x, y, z, Gier und Nick in Grad, Abstand. */
    public synchronized double[] pose() {
        return new double[]{gtx, gty, gtz, Math.toDegrees(gyaw), Math.toDegrees(gpitch), gdist};
    }

    /** Setzt die Zielpose; mit jump sofort, sonst gleitet die Kamera hin. */
    public synchronized void setPose(double[] p, boolean jump) {
        gtx = p[0]; gty = p[1]; gtz = p[2];
        double y = Math.toRadians(p[3]);
        gyaw = jump ? y : nearestYaw(y);
        gpitch = clamp(Math.toRadians(p[4]), Math.toRadians(-35), Math.toRadians(89));
        gdist = clamp(p[5], 3, MAX_DIST);
        if (jump) { tx = gtx; ty = gty; tz = gtz; yaw = gyaw; pitch = gpitch; dist = gdist; }
    }

    /**
     * Übernimmt die Kamera an Ort und Stelle: Drehpunkt im Abstand d vor dem Auge, Richtung und
     * Abstand so, dass das nächste Bild genau dasselbe zeigt. Kein Sprung, kein Nachgleiten.
     */
    public synchronized void adopt(Camera c, double d) {
        d = clamp(d, 3, MAX_DIST);
        c.update();
        tx = gtx = c.ex + c.fx * d;
        ty = gty = c.ey + c.fy * d;
        tz = gtz = c.ez + c.fz * d;
        yaw = gyaw = c.yaw;
        pitch = gpitch = -c.pitch;
        dist = gdist = d;
    }

    public synchronized void update(double dt, Camera cam) {
        double k = 1 - Math.exp(-dt * 6);
        if (autoOrbit) gyaw += dt * 0.08;
        // Drehpunkt mit der Tastatur verschieben, Tempo wächst mit dem Abstand
        double sp = (keys[K_SHIFT] ? 3.5 : 1) * Math.max(6, gdist * 0.35) * dt;
        double fx = -Math.sin(gyaw), fz = -Math.cos(gyaw), rx = -fz, rz = fx;
        double mf = (keys[K_W] ? 1 : 0) - (keys[K_S] ? 1 : 0), ms = (keys[K_D] ? 1 : 0) - (keys[K_A] ? 1 : 0);
        double mu = (keys[K_E] ? 1 : 0) - (keys[K_Q] ? 1 : 0);
        gtx += (fx * mf + rx * ms) * sp;
        gtz += (fz * mf + rz * ms) * sp;
        double floor = terrain.sample(gtx, gtz);
        gty = clamp(gty + mu * sp * 0.5, floor + 0.5, floor + 1500);
        tx += (gtx - tx) * k; ty += (gty - ty) * k; tz += (gtz - tz) * k;
        yaw += (gyaw - yaw) * k; pitch += (gpitch - pitch) * k;
        dist = Math.exp(Math.log(dist) + (Math.log(gdist) - Math.log(dist)) * k);
        cam.ex = tx + dist * Math.cos(pitch) * Math.sin(yaw);
        cam.ey = ty + dist * Math.sin(pitch);
        cam.ez = tz + dist * Math.cos(pitch) * Math.cos(yaw);
        double ground = terrain.sample(cam.ex, cam.ez) + 1.7;
        if (cam.ey < ground) cam.ey = ground;
        cam.lookAt(tx, ty, tz);
    }

    private static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
}
