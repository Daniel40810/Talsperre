package com.dan.talsperre.world;

import com.dan.talsperre.core.Animals;
import com.dan.talsperre.core.Sprites;
import com.dan.talsperre.core.Terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Leben am Edersee: Autos und ein Linienbus auf der Straße über die Krone, Wanderer auf Krone und
 * Wegen, zwei Fahrgastschiffe und Segelboote mit Kielwasser auf dem See, Vögel über Wasser und Wald,
 * dazu Mitarbeiter und geparkte Wagen am Kraftwerk. Alles bewegt sich nach der Zeit t (keine
 * Zustände): {@link #update} füllt die Puffer des Bildrechners vor jedem Bild neu.
 */
public final class Life {
    public volatile boolean traffic = true, ships = true, birds = true, walkers = true;

    private final Terrain t;
    private final Random rnd = new Random(31);

    // ---- Seeweg (geschlossene Schleife aus Hin- und Rückspur um die Seemitte)
    private double[] lx, lz, ls;
    private double lakeLen;
    private static final double LANE = 55;

    private static final class Car {
        double s0, speed; int dir; float r, g, b; boolean bus;
    }

    private static final class Walker {
        double s0, s1, speed, phase, off; float jr, jg, jb, pr, pg, pb;
    }

    private static final class Craft {
        double phase, speed, lane; boolean sail; float r, g, b, r2, g2, b2;
    }

    private static final class Flock {
        double cx, cz, rad, alt, omega, phase, span; int n; boolean white;
    }

    private final List<Car> cars = new ArrayList<>();
    private final List<Walker> walks = new ArrayList<>();
    private final List<Craft> crafts = new ArrayList<>();
    private final List<Flock> flocks = new ArrayList<>();
    private double crestS0, crestS1;

    public Life(Terrain t) {
        this.t = t;
        lakeRoute();
        crest();
        double L = Roads.rs.length > 0 ? Roads.rs[Roads.rs.length - 1] : 1;
        for (int i = 0; i < 9; i++) {
            Car c = new Car();
            c.dir = i % 2 == 0 ? 1 : -1;
            c.s0 = rnd.nextDouble() * L;
            c.speed = 8 + rnd.nextDouble() * 5;
            float[][] pal = {{0.42f, 0.02f, 0.02f}, {0.02f, 0.05f, 0.30f}, {0.55f, 0.55f, 0.52f}, {0.03f, 0.03f, 0.035f}, {0.03f, 0.22f, 0.07f}, {0.50f, 0.34f, 0.04f}, {0.20f, 0.20f, 0.22f}};
            float[] p = pal[rnd.nextInt(pal.length)];
            c.r = p[0]; c.g = p[1]; c.b = p[2];
            cars.add(c);
        }
        Car bus = new Car();
        bus.bus = true; bus.dir = 1; bus.s0 = L * 0.3; bus.speed = 7.5; bus.r = 0.55f; bus.g = 0.10f; bus.b = 0.04f;
        cars.add(bus);
        // Wanderer: Gruppen auf der Krone, am Seeweg im Osten und im Tal
        float[][] jack = {{0.45f, 0.04f, 0.03f}, {0.03f, 0.10f, 0.35f}, {0.60f, 0.45f, 0.03f}, {0.05f, 0.30f, 0.08f}, {0.40f, 0.40f, 0.40f}};
        for (int i = 0; i < 8; i++) {
            Walker w = new Walker();
            w.s0 = crestS0 + 30 + rnd.nextDouble() * 20; w.s1 = crestS1 - 30;
            w.speed = 1.1 + rnd.nextDouble() * 0.4; w.phase = rnd.nextDouble() * 600; w.off = (i % 2 == 0 ? -1 : 1) * (1.0 + rnd.nextDouble() * 0.3);
            colors(w, jack);
            walks.add(w);
        }
        for (int i = 0; i < 4; i++) {
            Walker w = new Walker();
            w.s0 = crestS1 + 80; w.s1 = crestS1 + 420;
            w.speed = 1.2; w.phase = rnd.nextDouble() * 900; w.off = 2.2 + 0.7 * (i % 2);
            colors(w, jack);
            walks.add(w);
        }
        // Schiffe und Segler
        for (int i = 0; i < 2; i++) {
            Craft c = new Craft();
            c.phase = i * 0.5; c.speed = 5.0; c.lane = 0;
            c.r = 0.02f; c.g = 0.05f; c.b = 0.22f; c.r2 = 0.55f; c.g2 = 0.55f; c.b2 = 0.52f;
            crafts.add(c);
        }
        for (int i = 0; i < 5; i++) {
            Craft c = new Craft();
            c.sail = true; c.phase = rnd.nextDouble(); c.speed = 1.4 + rnd.nextDouble() * 0.8; c.lane = (i % 2 == 0 ? 1 : -1) * (30 + 20 * rnd.nextDouble());
            c.r = 0.5f; c.g = 0.5f; c.b = 0.5f;
            float[][] sails = {{0.75f, 0.75f, 0.72f}, {0.55f, 0.08f, 0.04f}, {0.70f, 0.60f, 0.12f}, {0.75f, 0.75f, 0.72f}};
            float[] sc = sails[i % sails.length];
            c.r2 = sc[0]; c.g2 = sc[1]; c.b2 = sc[2];
            crafts.add(c);
        }
        // Vögel
        addFlock(0, 420, 230, 60, 0.05, 6, false, 1.0);
        addFlock(-120, 760, 300, 90, -0.04, 7, true, 0.8);
        addFlock(-250, -520, 120, 45, 0.07, 5, false, 0.9);
        addFlock(180, 40, 200, 100, 0.03, 2, false, 1.8);     // Rotmilane
        addFlock(-420, 1050, 260, 70, 0.045, 5, true, 0.85);
    }

    private void addFlock(double cx, double cz, double rad, double alt, double omega, int n, boolean white, double span) {
        Flock f = new Flock();
        f.cx = cx; f.cz = cz; f.rad = rad; f.alt = alt; f.omega = omega; f.n = n; f.white = white; f.span = span;
        f.phase = rnd.nextDouble() * 6.28;
        flocks.add(f);
    }

    private static void colors(Walker w, float[][] jack) {
        float[] j = jack[(int) (Math.random() * jack.length)];
        w.jr = j[0]; w.jg = j[1]; w.jb = j[2];
        w.pr = 0.05f; w.pg = 0.07f; w.pb = 0.14f;
    }

    // ------------------------------------------------------------ Wege

    /** Seemitte je z (Mitte der Wasserfläche bei höchstem Pegel), daraus die Schleife aus Hin- und Rückspur. */
    private void lakeRoute() {
        List<double[]> mid = new ArrayList<>();
        for (double z = 180; z <= 1150; z += 25) {
            double bx = 0, bh = 1e9;
            for (double x = -2200; x <= 600; x += 10) { double h = t.sample(x, z); if (h < bh) { bh = h; bx = x; } }
            double e = bx, w = bx;
            while (e < 900 && t.sample(e, z) < -1.0) e += 8;
            while (w > -2600 && t.sample(w, z) < -1.0) w -= 8;
            mid.add(new double[]{(e + w) / 2, z});
        }
        // glätten
        int n = mid.size();
        double[][] m = new double[n][2];
        for (int i = 0; i < n; i++) {
            double sx = 0; int c = 0;
            for (int k = -4; k <= 4; k++) { int j = i + k; if (j < 0 || j >= n) continue; sx += mid.get(j)[0]; c++; }
            m[i][0] = sx / c; m[i][1] = mid.get(i)[1];
        }
        List<double[]> loop = new ArrayList<>();
        // Hinweg (nach Süden) auf der Spur +LANE (rechts: Westseite), Wende, Rückweg auf −LANE
        for (int i = 0; i < n; i++) loop.add(off(m, i, LANE));
        double[] a = off(m, n - 1, LANE), b = off(m, n - 1, -LANE);
        for (int k = 1; k < 8; k++) {
            double u = k / 8.0, ang = Math.PI * u;
            double cx = (a[0] + b[0]) / 2, cz = (a[1] + b[1]) / 2, r = Math.hypot(a[0] - b[0], a[1] - b[1]) / 2;
            double ux = (a[0] - cx) / r, uz = (a[1] - cz) / r;
            // Halbkreis in Fahrtrichtung (nach Süden hinaus)
            double tx = 0, tz = 1;
            loop.add(new double[]{cx + r * (ux * Math.cos(ang) + tx * Math.sin(ang)), cz + r * (uz * Math.cos(ang) + tz * Math.sin(ang))});
        }
        for (int i = n - 1; i >= 0; i--) loop.add(off(m, i, -LANE));
        a = off(m, 0, -LANE); b = off(m, 0, LANE);
        for (int k = 1; k < 8; k++) {
            double u = k / 8.0, ang = Math.PI * u;
            double cx = (a[0] + b[0]) / 2, cz = (a[1] + b[1]) / 2, r = Math.hypot(a[0] - b[0], a[1] - b[1]) / 2;
            double ux = (a[0] - cx) / r, uz = (a[1] - cz) / r;
            double tx = 0, tz = -1;
            loop.add(new double[]{cx + r * (ux * Math.cos(ang) + tx * Math.sin(ang)), cz + r * (uz * Math.cos(ang) + tz * Math.sin(ang))});
        }
        int N = loop.size();
        lx = new double[N]; lz = new double[N]; ls = new double[N + 1];
        for (int i = 0; i < N; i++) { lx[i] = loop.get(i)[0]; lz[i] = loop.get(i)[1]; }
        for (int i = 0; i < N; i++) {
            int j = (i + 1) % N;
            ls[i + 1] = ls[i] + Math.hypot(lx[j] - lx[i], lz[j] - lz[i]);
        }
        lakeLen = ls[N];
    }

    private static double[] off(double[][] m, int i, double d) {
        int a = Math.max(0, i - 1), b = Math.min(m.length - 1, i + 1);
        double dx = m[b][0] - m[a][0], dz = m[b][1] - m[a][1], l = Math.hypot(dx, dz);
        // rechts der Fahrtrichtung nach Süden (dx, dz): (−dz, dx)
        return new double[]{m[i][0] - dz / l * d, m[i][1] + dx / l * d};
    }

    private void crest() {
        // Anfang und Ende der Krone auf dem Fahrweg: die Punkte mit Radius nahe R−3
        crestS0 = Double.NaN;
        double r3 = Dam.R - 3;
        for (int i = 0; i < Roads.rs.length; i++) {
            double r = Math.hypot(Roads.rx[i], Roads.rz[i] - Dam.CZ);
            if (Math.abs(r - r3) < 0.5 && Roads.ry[i] < Dam.TOP + 0.2 && Roads.ry[i] > Dam.TOP) {
                if (Double.isNaN(crestS0)) crestS0 = Roads.rs[i];
                crestS1 = Roads.rs[i];
            }
        }
        if (Double.isNaN(crestS0)) { crestS0 = 0; crestS1 = 1; }
    }

    private int seg(double s) {
        int lo = 0, hi = Roads.rs.length - 2;
        while (lo < hi) { int mid = (lo + hi + 1) >>> 1; if (Roads.rs[mid] <= s) lo = mid; else hi = mid - 1; }
        return lo;
    }

    /** Ort auf dem Fahrweg: out = x, y, z, hx, hz. */
    private void onRoad(double s, double lateral, double[] out) {
        int i = seg(s);
        double l = Math.max(1e-6, Roads.rs[i + 1] - Roads.rs[i]);
        double u = Math.max(0, Math.min(1, (s - Roads.rs[i]) / l));
        double x = Roads.rx[i] + (Roads.rx[i + 1] - Roads.rx[i]) * u, y = Roads.ry[i] + (Roads.ry[i + 1] - Roads.ry[i]) * u,
                z = Roads.rz[i] + (Roads.rz[i + 1] - Roads.rz[i]) * u;
        double hx = Roads.rx[i + 1] - Roads.rx[i], hz = Roads.rz[i + 1] - Roads.rz[i], hl = Math.hypot(hx, hz);
        if (hl < 1e-6) {          // zwei fast gleiche Stützpunkte: Richtung aus den Nachbarn
            int a0 = Math.max(0, i - 1), b0 = Math.min(Roads.rx.length - 1, i + 2);
            hx = Roads.rx[b0] - Roads.rx[a0]; hz = Roads.rz[b0] - Roads.rz[a0]; hl = Math.hypot(hx, hz);
            if (hl < 1e-9) { hx = 1; hz = 0; hl = 1; }
        }
        hx /= hl; hz /= hl;
        out[0] = x - hz * lateral; out[1] = y; out[2] = z + hx * lateral; out[3] = hx; out[4] = hz;
    }

    private void onLake(double s, double[] out) {
        s = ((s % lakeLen) + lakeLen) % lakeLen;
        int lo = 0, hi = ls.length - 2;
        while (lo < hi) { int mid = (lo + hi + 1) >>> 1; if (ls[mid] <= s) lo = mid; else hi = mid - 1; }
        int i = lo, j = (i + 1) % lx.length;
        double u = (s - ls[i]) / Math.max(1e-6, ls[i + 1] - ls[i]);
        out[0] = lx[i] + (lx[j] - lx[i]) * u; out[2] = lz[i] + (lz[j] - lz[i]) * u;
        double hx = lx[j] - lx[i], hz = lz[j] - lz[i], l = Math.hypot(hx, hz);
        out[3] = hx / l; out[4] = hz / l;
    }

    // ------------------------------------------------------------ Bild

    /**
     * Füllt Tiere (Fahrzeuge, Schiffe, Wanderer) und Sprites (Kielwasser, Vögel, Lichter) für die Zeit t.
     * level: Pegel in m über Stauziel; night 0..1: wie dunkel (Lichter an).
     */
    public void update(double time, double level, float night, Animals an, Sprites sp) {
        an.clear();
        sp.clear();
        double[] q = new double[5];
        double L = Roads.rs.length > 0 ? Roads.rs[Roads.rs.length - 1] : 1;
        if (traffic && L > 10) {
            for (Car c : cars) {
                double s = c.s0 + c.dir * c.speed * time;
                s = ((s % L) + L) % L;
                double lat = c.dir > 0 ? 1.7 : -1.7;
                onRoad(s, lat, q);
                double hx = c.dir > 0 ? q[3] : -q[3], hz = c.dir > 0 ? q[4] : -q[4];
                int k = an.add(c.bus ? Animals.BUS : Animals.CAR, q[0], q[1], q[2], hx, hz, 1);
                if (k < 0) break;
                an.cr[k] = c.r; an.cg[k] = c.g; an.cb[k] = c.b;
                if (night > 0.05f) {
                    // Scheinwerfer vorn (weiß), Rücklichter hinten (rot)
                    double len = c.bus ? 6 : 2.1;
                    double lx0 = q[0] + hx * len, lz0 = q[2] + hz * len;
                    sp.add(Sprites.GLOW, lx0, q[1] + 0.7, lz0, 3.0, 1.0, 0.95, 0.8, 0.5 * night);
                    sp.add(Sprites.GLOW, q[0] - hx * len, q[1] + 0.8, q[2] - hz * len, 1.0, 0.8, 0.05, 0.03, 0.6 * night);
                }
            }
            // geparkte Wagen am Kraftwerk
            for (int i = 0; i < 3; i++) {
                double phi = Math.toRadians(13.5 + 3.4 * i), r = 226;
                double x = Dam.x(phi, r), z = Dam.z(phi, r);
                double hx = Math.cos(phi), hz = -Math.sin(phi);
                int k = an.add(Animals.CAR, x, t.sample(x, z) + 0.05, z, hx, hz, 1);
                if (k >= 0) { an.cr[k] = i == 1 ? 0.5f : 0.03f; an.cg[k] = i == 1 ? 0.5f : 0.03f; an.cb[k] = i == 1 ? 0.5f : 0.04f; }
            }
        }
        if (walkers && L > 10) {
            for (Walker w : walks) {
                double span = w.s1 - w.s0;
                if (span < 10) continue;
                // Hin und zurück mit Pausen an den Enden
                double cyc = 2 * span / w.speed + 8;
                double u = ((time + w.phase) % cyc + cyc) % cyc;
                double go = span / w.speed;
                double s, dir;
                boolean moving = true;
                if (u < go) { s = w.s0 + u * w.speed; dir = 1; }
                else if (u < go + 4) { s = w.s1; dir = 1; moving = false; }
                else if (u < 2 * go + 4) { s = w.s1 - (u - go - 4) * w.speed; dir = -1; }
                else { s = w.s0; dir = -1; moving = false; }
                onRoad(s, w.off, q);
                int k = an.add(Animals.PERSON, q[0], q[1], q[2], dir * q[3], dir * q[4], 1);
                if (k < 0) break;
                an.step[k] = (float) (time * 5.8 + w.phase); an.gait[k] = moving ? 1 : 0;
                an.cr[k] = w.jr; an.cg[k] = w.jg; an.cb[k] = w.jb; an.pr[k] = w.pr; an.pg[k] = w.pg; an.pb[k] = w.pb;
            }
            // Mitarbeiter am Kraftwerk
            for (int i = 0; i < 2; i++) {
                double phi = Math.toRadians(14.5 + 6.5 * i + 1.2 * Math.sin(time * 0.05 + i)), r = 231;
                double x = Dam.x(phi, r), z = Dam.z(phi, r);
                double hx = Math.cos(phi), hz = -Math.sin(phi);
                double dir = Math.cos(time * 0.05 + i) > 0 ? 1 : -1;
                int k = an.add(Animals.PERSON, x, t.sample(x, z) + 0.05, z, dir * hx, dir * hz, 1);
                if (k >= 0) {
                    an.step[k] = (float) (time * 5.8); an.gait[k] = 1;
                    an.cr[k] = 0.5f; an.cg[k] = 0.22f; an.cb[k] = 0.02f; an.pr[k] = 0.04f; an.pg[k] = 0.05f; an.pb[k] = 0.1f;
                }
            }
        }
        if (ships && lakeLen > 100 && level > -14) {
            for (Craft c : crafts) {
                double s = c.phase * lakeLen + c.speed * time;
                onLake(s, q);
                // Segler halten Abstand zur Spur (seitlich versetzt)
                double x = q[0] - q[4] * c.lane, z = q[2] + q[3] * c.lane;
                if (t.sample(x, z) > level - 4) continue;       // zu flach: im Hafen
                double hx = q[3], hz = q[4];
                int k = an.add(c.sail ? Animals.SAIL : Animals.SHIP, x, level + 0.05, z, hx, hz, 1);
                if (k < 0) break;
                an.cr[k] = c.r; an.cg[k] = c.g; an.cb[k] = c.b; an.pr[k] = c.r2; an.pg[k] = c.g2; an.pb[k] = c.b2;
                double day = 1.3 * (1 - night) + 0.12;
                double len = c.sail ? 3 : 15;
                // Kielwasser: Heckwelle als V aus weichen Punkten
                int nw = c.sail ? 8 : 16;
                double step = c.sail ? 1.6 : 3.2;
                for (int j = 1; j <= nw; j++) {
                    double u = (double) j / nw;
                    double px = x - hx * (len + j * step), pz = z - hz * (len + j * step);
                    double sp2 = (c.sail ? 0.4 : 1.1) * j * step * 0.35;
                    for (int sg = -1; sg <= 1; sg += 2) {
                        sp.add(Sprites.DOT, px - hz * sg * sp2, level + 0.1, pz + hx * sg * sp2, (c.sail ? 0.9 : 2.0) + 0.5 * j * step * 0.2, 0.9 * day, 0.95 * day, day, 0.40 * (1 - u));
                    }
                }
                if (!c.sail) sp.add(Sprites.DOT, x + hx * 14, level + 0.2, z + hz * 14, 3.5, 0.9 * day, 0.95 * day, day, 0.45);
                if (night > 0.05f) {
                    // Fensterreihen warm, Positionslichter rot und grün
                    if (!c.sail) {
                        for (int w = -9; w <= 8; w += 3)
                            sp.add(Sprites.GLOW, x + hx * w, level + 3.7, z + hz * w, 1.6, 1.0, 0.72, 0.38, 0.55 * night);
                        sp.add(Sprites.GLOW, x + hx * 12, level + 3.0, z + hz * 12, 1.2, 1.0, 0.95, 0.8, 0.7 * night);
                    } else {
                        sp.add(Sprites.GLOW, x + hx * 2.5, level + 1.2, z + hz * 2.5, 0.8, 0.9, 0.9, 0.8, 0.5 * night);
                    }
                }
            }
        }
        if (birds) {
            for (Flock f : flocks) {
                for (int i = 0; i < f.n; i++) {
                    double a = f.phase + f.omega * time + i * 0.35 + 0.3 * Math.sin(time * 0.3 + i);
                    double rr = f.rad * (1 + 0.12 * Math.sin(i * 1.7 + time * 0.07));
                    double x = f.cx + Math.cos(a) * rr, z = f.cz + Math.sin(a) * rr;
                    double y = Math.max(level, t.sample(x, z)) + f.alt + 8 * Math.sin(i * 2.3 + time * 0.11);
                    double dir = f.omega >= 0 ? 1 : -1;
                    double hx = -Math.sin(a) * dir, hz = Math.cos(a) * dir;
                    int k = sp.add(Sprites.BIRD, x, y, z, f.span * 1.5, f.white ? 0.9 : 0.04, f.white ? 0.9 : 0.04, f.white ? 0.92 : 0.045, 0.95);
                    sp.hx[k] = (float) hx; sp.hz[k] = (float) hz;
                    sp.flap[k] = (float) (f.span > 1.5 ? 0.25 * Math.sin(time * 1.5 + i) : Math.sin(time * 7 + i * 1.9));
                }
            }
        }
    }
}
