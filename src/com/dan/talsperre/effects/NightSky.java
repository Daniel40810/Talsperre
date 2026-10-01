package com.dan.talsperre.effects;

import java.util.Random;

/**
 * Sternhimmel über Yellowstone: 3600 schwache Sterne zufällig verteilt (Helligkeiten nach einer groben
 * Größenklassenverteilung), dazu die hellen Sterne aus {@link Stars} an ihren echten Örtern. Alles
 * dreht sich mit der Ortssternzeit um den Himmelspol, 44,5° über dem Nordhorizont. Die Lage der
 * Milchstraße (galaktischer Pol und Zentrum) gibt der Sternhimmel an den Himmel weiter.
 */
public final class NightSky {
    public static final int REAL = 3600, N = REAL + Stars.STARS.length;
    private final float[] bx = new float[N], by = new float[N], bz = new float[N];
    /** Richtungen im aktuellen Bild (nach Drehung) und Helligkeit, Farbe 0..1 (bläulich bis rötlich). */
    public final float[] dx = new float[N], dy = new float[N], dz = new float[N], mag = new float[N], tint = new float[N], ph = new float[N];

    private double lastSid = Double.NaN;

    public NightSky() {
        Random r = new Random(1872);   // Gründungsjahr des Parks
        for (int i = REAL; i < N; i++) {
            mag[i] = (float) Math.min(1.6, 0.4 * Math.pow(10, -0.4 * Stars.mag(i - REAL)));
            tint[i] = (float) r.nextDouble();
            ph[i] = (float) (r.nextDouble() * 6.3);
        }
        for (int i = 0; i < REAL; i++) {
            double u = r.nextDouble() * 2 - 1, a = r.nextDouble() * 2 * Math.PI, s = Math.sqrt(1 - u * u);
            bx[i] = (float) (s * Math.cos(a)); by[i] = (float) u; bz[i] = (float) (s * Math.sin(a));
            double m = Math.pow(r.nextDouble(), 7);
            mag[i] = (float) (0.004 + 0.14 * m);
            tint[i] = (float) r.nextDouble();
            ph[i] = (float) (r.nextDouble() * 6.3);
        }
    }

    /** Dreht den Himmel um die Polachse zur Sternzeit (Grad). */
    public void update(double siderealDeg) {
        if (siderealDeg == lastSid) return;
        lastSid = siderealDeg;
        double lat = Math.toRadians(DayNightCycle.LAT);
        double px = 0, py = Math.sin(lat), pz = -Math.cos(lat);
        double ang = Math.toRadians(siderealDeg);
        double c = Math.cos(ang), s = Math.sin(ang);
        double[] q = new double[3];
        for (int i = REAL; i < N; i++) {
            Stars.direction(Stars.ra(i - REAL) * 15, Stars.dec(i - REAL), siderealDeg, q);
            dx[i] = (float) q[0]; dy[i] = (float) q[1]; dz[i] = (float) q[2];
        }
        Stars.direction(Stars.GNP_RA, Stars.GNP_DEC, siderealDeg, q);
        Sky.galPole = new double[]{q[0], q[1], q[2]};
        Stars.direction(Stars.GC_RA, Stars.GC_DEC, siderealDeg, q);
        Sky.galCenter = new double[]{q[0], q[1], q[2]};
        for (int i = 0; i < REAL; i++) {
            double x = bx[i], y = by[i], z = bz[i];
            double d = px * x + py * y + pz * z;
            double cx = py * z - pz * y, cy = pz * x - px * z, cz = px * y - py * x;
            dx[i] = (float) (x * c + cx * s + px * d * (1 - c));
            dy[i] = (float) (y * c + cy * s + py * d * (1 - c));
            dz[i] = (float) (z * c + cz * s + pz * d * (1 - c));
        }
    }
}
