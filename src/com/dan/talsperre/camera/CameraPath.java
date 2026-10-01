package com.dan.talsperre.camera;

import java.util.ArrayList;
import java.util.List;

/**
 * Kamerafahrt als Folge von Schlüsselposen (Augpunkt und Blickziel) mit Zeitpunkten.
 * Dazwischen wird mit Hermite-Kurven nach Catmull-Rom interpoliert, die Tangenten richten sich
 * nach den Zeitabständen. Am Anfang und am Ende steht die Kamera still, die Fahrt beginnt und
 * endet also weich.
 */
public final class CameraPath {
    private final List<double[]> keys = new ArrayList<>();   // t, ex, ey, ez, tx, ty, tz

    public CameraPath add(double t, double ex, double ey, double ez, double tx, double ty, double tz) {
        keys.add(new double[]{t, ex, ey, ez, tx, ty, tz});
        return this;
    }

    /** Hängt eine Pose dt Sekunden nach der letzten an. */
    public CameraPath then(double dt, double ex, double ey, double ez, double tx, double ty, double tz) {
        double t = keys.isEmpty() ? 0 : keys.get(keys.size() - 1)[0] + dt;
        return add(t, ex, ey, ez, tx, ty, tz);
    }

    public CameraPath then(double dt, double[] pose) {
        return then(dt, pose[0], pose[1], pose[2], pose[3], pose[4], pose[5]);
    }

    public int size() { return keys.size(); }

    public double duration() { return keys.isEmpty() ? 0 : keys.get(keys.size() - 1)[0]; }

    public double[] key(int i) { return keys.get(i); }

    /**
     * Tangente an der mittleren Pose nach Catmull-Rom, aber ohne Überschwingen: Wechselt die
     * Richtung, steht die Kamera in dieser Achse kurz still, sonst höchstens dreimal die kleinere
     * Nachbarsteigung (nach Fritsch und Carlson). So taucht eine Fahrt nie unter ihre Posen.
     */
    private static double tangent(double[] p, double[] q, double[] r, int c) {
        if (p == null || r == null) return 0;
        double sl = (q[c] - p[c]) / (q[0] - p[0]), sr = (r[c] - q[c]) / (r[0] - q[0]);
        if (sl * sr <= 0) return 0;
        double m = (r[c] - p[c]) / (r[0] - p[0]);
        double lim = 3 * Math.min(Math.abs(sl), Math.abs(sr));
        return Math.max(-lim, Math.min(lim, m));
    }

    /** Pose zur Zeit t (auf die Fahrt begrenzt) in out[0..5]: Augpunkt, Blickziel. */
    public void sample(double t, double[] out) {
        int n = keys.size();
        if (n == 0) return;
        if (n == 1 || t <= keys.get(0)[0]) { System.arraycopy(keys.get(0), 1, out, 0, 6); return; }
        if (t >= keys.get(n - 1)[0]) { System.arraycopy(keys.get(n - 1), 1, out, 0, 6); return; }
        int i = 0;
        while (i < n - 2 && keys.get(i + 1)[0] <= t) i++;
        double[] a = keys.get(i), b = keys.get(i + 1);
        double[] a0 = i > 0 ? keys.get(i - 1) : null, b1 = i + 2 < n ? keys.get(i + 2) : null;
        double h = b[0] - a[0], u = (t - a[0]) / h;
        double u2 = u * u, u3 = u2 * u;
        double h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
        for (int c = 1; c <= 6; c++) {
            double ma = tangent(a0, a, b, c), mb = tangent(a, b, b1, c);
            out[c - 1] = h00 * a[c] + h10 * h * ma + h01 * b[c] + h11 * h * mb;
        }
    }
}
