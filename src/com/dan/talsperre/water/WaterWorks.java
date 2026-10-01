package com.dan.talsperre.water;

import com.dan.talsperre.core.Engine3D;
import com.dan.talsperre.core.Terrain;
import com.dan.talsperre.effects.ParticleSystem;
import com.dan.talsperre.world.Dam;

/**
 * Das Wasser der Talsperre als Ganzes: Pegel des Sees, die fünf Grundablässe, die beiden Überläufe und
 * der Wind über dem See. Rechnet aus dem Pegel die Wassermengen (Ausfluss nach Torricelli, Überfall nach
 * Poleni) und erzeugt daraus die Strahlen der Ablässe, die Brause im Tosbecken und die Gischt am Fuß der
 * Überläufe als Teilchen. Den See hebt oder senkt es über {@code Mesh.lakeDy}.
 *
 * <p>Pegel in Metern gegenüber Stauziel (244,97 m ü. NHN): 0 = Vollstau, negativ = abgesenkt.
 */
public final class WaterWorks {
    public static final double STAUZIEL_NHN = 244.97;
    public static final double LEVEL_MIN = -30, LEVEL_MAX = 1.5;
    /** Beiwert der Ablässe (Strahlgeschwindigkeit gegenüber Torricelli, grob wegen Reibung und Strahlaufweitung). */
    private static final double CV = 0.62;

    /** Pegel in Metern gegenüber Stauziel. */
    public volatile double level = 0;
    /** Öffnung der fünf Ablässe 0..1. */
    public final double[] gate = new double[5];
    /** Wind über dem See 0..1. */
    public volatile double wind = 0.35;
    /** Windrichtung (Einheitsvektor, Richtung, in die der Wind weht): von Südwest nach Nordost. */
    public volatile double windX = 0.8, windZ = -0.6;

    /** Gischtnebel über dem Tosbecken und Seenebel (Vielfache der Grundstärke), Nebelschwaden aus Teilchen an/aus. */
    public volatile double sprayMist = 1, lakeMist = 1;
    public volatile boolean wisps = true;
    /** Kraftwerksdampf an der Maschinenhalle (Vielfaches der Standardmenge, 0 = aus). */
    public volatile double plant = 1;

    public final ParticleSystem particles = new ParticleSystem();
    private final java.util.Random rnd = new java.util.Random(4711);
    private double accJet, accSpill, accDrop;
    /** Rechenwerte des letzten Schrittes (für Anzeige und Selbsttest). */
    public volatile double outflow, spillFlow, jetSpeed;

    public static double nhn(double level) { return STAUZIEL_NHN + level; }

    /** Druckhöhe über der Mitte der Ablässe. */
    public static double head(double level) { return level - Dam.GATE_Y; }

    /** Austrittsgeschwindigkeit des Strahls bei Öffnung 1 in m/s. */
    public static double jetSpeed(double level) { return CV * Math.sqrt(2 * 9.81 * Math.max(0.1, head(level))); }

    /** Wassermenge eines Ablasses in m³/s: Öffnungsfläche 3,4 × 3,4 m, Strahl mit Einschnürung. */
    public static double gateFlow(double level, double open) {
        double area = 3.4 * 3.4 * open;
        return 0.62 * area * Math.sqrt(2 * 9.81 * Math.max(0.1, head(level)));
    }

    /** Überfall je Überlauf in m³/s nach Poleni: Q = 2/3 · μ · b · √(2g) · h^1,5, b = 9 m. */
    public static double spillFlow(double level) {
        double h = level - Dam.SILL;
        return h <= 0 ? 0 : 2.0 / 3.0 * 0.62 * 9.0 * Math.sqrt(2 * 9.81) * Math.pow(h, 1.5);
    }

    public void setAllGates(double open) { for (int i = 0; i < 5; i++) gate[i] = open; }

    /** Ein Schritt von dt Sekunden: Pegel und Wind an den Bildrechner geben, Teilchen erzeugen und bewegen. */
    public void update(double dt, Engine3D e, Terrain t) {
        dt = Math.min(dt, 0.1);
        double lv = Math.max(LEVEL_MIN, Math.min(LEVEL_MAX, level));
        e.mesh().lakeDy = (float) lv;
        e.particles = particles;
        e.wind = wind;
        e.lakeMist = lakeMist;
        e.windX = windX; e.windZ = windZ;
        double sp = spillFlow(lv);
        e.cascadeOn = sp > 0.02;
        double q = 0, open = 0;
        for (double g : gate) { q += gateFlow(lv, g); open += g; }
        outflow = q; spillFlow = sp * 2; jetSpeed = jetSpeed(lv);
        // Brause im Becken: nach der Wassermenge, in Ablassmengen gemessen (ein Ablass voll offen rund 100 m³/s)
        double turb = Math.min(1, (q + sp * 2 * 1.5) / 260.0);
        e.basinFoam += (turb - e.basinFoam) * Math.min(1, dt * 1.5);
        emit(dt, lv, sp);
        emitMist(dt, lv, sp, turb, e);
        particles.step((float) dt, (float) (windX * 4 * wind), (float) (windZ * 4 * wind), t, null);
    }

    private void emit(double dt, double lv, double sp) {
        ParticleSystem ps = particles;
        double v0 = jetSpeed(lv);
        for (int i = 0; i < 5; i++) {
            double o = gate[i];
            if (o <= 0.005) continue;
            double s = (i - 2) * Dam.GATE_PITCH;
            double phi = s / 264.0;
            double sn = Math.sin(phi), cs = Math.cos(phi);
            // Austritt: Mitte der Öffnung, Strahl nach stromab (−e_r), leicht nach oben (die Schieber lenken ab)
            double cx = Dam.GATE_R * sn, cz = Dam.CZ + Dam.GATE_R * cs;
            double tx = cs, tz = -sn;   // Tangente e_phi
            double half = 1.5 * Math.sqrt(o);
            // Spray: Gischtwolken, die den Körper des Strahls bilden; Tropfen darum
            double rate = 1500 * o;
            int ns = poisson(rate * dt);
            for (int k = 0; k < ns; k++) {
                double a = (rnd.nextDouble() - 0.5) * 2 * half, b = (rnd.nextDouble() - 0.5) * 2 * half;
                double sp0 = v0 * 1.15 * o0(o) * (0.92 + 0.16 * rnd.nextDouble());
                double up = 0.05 + 0.10 * rnd.nextDouble();
                float vx = (float) (-sn * sp0 + tx * (rnd.nextDouble() - 0.5) * 1.6), vz = (float) (-cs * sp0 + tz * (rnd.nextDouble() - 0.5) * 1.6);
                float vy = (float) (sp0 * up + (rnd.nextDouble() - 0.5) * 1.2);
                int id = ps.spawn(ParticleSystem.SPRAY, (float) (cx + tx * a), (float) (Dam.GATE_Y + b), (float) (cz + tz * a), vx, vy, vz,
                        (float) (0.38 + 0.4 * rnd.nextDouble()), (float) (2.2 + 0.8 * rnd.nextDouble()), 0.3f, (float) Dam.BASIN_Y, -1);
                if (id >= 0) { ps.grow[id] = (float) (0.35 + 0.5 * rnd.nextDouble()); ps.drag[id] = 0.22f; }
            }
            int nd = poisson(900 * o * dt);
            for (int k = 0; k < nd; k++) {
                double a = (rnd.nextDouble() - 0.5) * 2 * half * 1.1, b = (rnd.nextDouble() - 0.5) * 2 * half * 1.1;
                double sp0 = v0 * 1.15 * o0(o) * (0.97 + 0.12 * rnd.nextDouble());
                float vx = (float) (-sn * sp0 + tx * (rnd.nextDouble() - 0.5) * 2.4), vz = (float) (-cs * sp0 + tz * (rnd.nextDouble() - 0.5) * 2.4);
                float vy = (float) (sp0 * (0.06 + 0.12 * rnd.nextDouble()) + (rnd.nextDouble() - 0.5) * 2);
                int id = ps.spawn(ParticleSystem.DROP, (float) (cx + tx * a), (float) (Dam.GATE_Y + b), (float) (cz + tz * a), vx, vy, vz,
                        0.1f, (float) (2.6 + rnd.nextDouble()), 0.8f, (float) Dam.BASIN_Y, -1);
                if (id >= 0) ps.drag[id] = 0.25f;
            }
        }
        // Überläufe: Gischt springt am Fuß der Bahn hoch
        if (sp > 0.02) {
            double rate = Math.min(1400, 130 * sp);
            for (int sg = -1; sg <= 1; sg += 2) {
                int ns = poisson(rate * dt);
                double s0 = sg * Dam.SPILL_S;
                for (int k = 0; k < ns; k++) {
                    double s = s0 + (rnd.nextDouble() - 0.5) * 2 * Dam.SPILL_HALF * 0.9;
                    double rtoe = Dam.R - Dam.width(Dam.BASE) - 1.0 - rnd.nextDouble() * 3;
                    double phi = s / rtoe;
                    double sn = Math.sin(phi), cs = Math.cos(phi);
                    double up = 3 + 7 * rnd.nextDouble() * Math.min(1, 0.3 + sp / 15);
                    ps.spawn(ParticleSystem.SPRAY, (float) (rtoe * sn), (float) (Dam.BASIN_Y + 0.3), (float) (Dam.CZ + rtoe * cs),
                            (float) (-sn * 2.5 + (rnd.nextDouble() - 0.5) * 2), (float) up, (float) (-cs * 2.5 + (rnd.nextDouble() - 0.5) * 2),
                            (float) (0.45 + 0.4 * rnd.nextDouble()), (float) (1.6 + rnd.nextDouble()), 0.16f, (float) Dam.BASIN_Y, -1);
                }
            }
        }
    }

    /**
     * Gischtnebel und Nebelschwaden als Dampfteilchen. Der Gischtnebel entsteht, wo die Strahlen und die
     * Überlaufbahnen aufs Wasser treffen: feine Tröpfchen, die der Wind das Tal hinabträgt. Die Schwaden sind
     * flache Streifen über See und Eder, die an kalten Morgen (Engine3D.mistAmount) dichter werden und kaum steigen.
     */
    private void emitMist(double dt, double lv, double sp, double turb, Engine3D e) {
        ParticleSystem ps = particles;
        double mist = Engine3D.mistAmount(e.hour) * e.fogScale;
        // Gischtnebel: je Wassermenge, in kühler Luft (Morgen) kondensiert mehr
        double rate = 13 * turb * sprayMist * (0.7 + 0.8 * Math.min(1.5, mist + 0.3));
        int n = poisson(rate * dt);
        for (int k = 0; k < n; k++) {
            double x = (rnd.nextDouble() - 0.5) * 60, z = -58 - rnd.nextDouble() * 36;
            double size = 3.2 + 3.0 * rnd.nextDouble();
            int id = ps.spawn(ParticleSystem.STEAM, (float) x, (float) (Dam.BASIN_Y + 0.6 + rnd.nextDouble() * 2), (float) z,
                    (float) ((rnd.nextDouble() - 0.5) * 1.5), 0.8f, (float) (-1.0 - rnd.nextDouble()),
                    (float) size, (float) (8 + 6 * rnd.nextDouble()), (float) (0.055 + 0.05 * rnd.nextDouble()), (float) Dam.BASIN_Y, -1);
            if (id >= 0) { ps.grow[id] = (float) (0.9 + 0.7 * rnd.nextDouble()); ps.lift[id] = 0.45f; }
        }
        // Gischt von den Überläufen am Fuß der Mauer
        if (sp > 0.02) {
            int m = poisson(Math.min(14, 2 * sp) * sprayMist * dt);
            for (int k = 0; k < m; k++) {
                double s = (rnd.nextBoolean() ? 1 : -1) * Dam.SPILL_S + (rnd.nextDouble() - 0.5) * 8;
                double r = Dam.R - Dam.width(Dam.BASE) - 2;
                double phi = s / r;
                int id = ps.spawn(ParticleSystem.STEAM, (float) (r * Math.sin(phi)), (float) (Dam.BASIN_Y + 1), (float) (Dam.CZ + r * Math.cos(phi)),
                        0f, 1f, -1.5f, (float) (3 + 2.5 * rnd.nextDouble()), (float) (7 + 5 * rnd.nextDouble()), 0.07f, (float) Dam.BASIN_Y, -1);
                if (id >= 0) { ps.grow[id] = (float) (0.9 + 0.6 * rnd.nextDouble()); ps.lift[id] = 0.45f; }
            }
        }
        // Kraftwerksdampf: kühle Luft am Auslaufkanal der Maschinenhalle und warme Abluft über dem Dach;
        // an kalten Morgen stärker (wie der Nebel), tagsüber nur dünn
        if (plant > 0.02) {
            double cold = 0.3 + 0.9 * Math.min(1.5, mist);
            double a0 = Math.toRadians(12.6), a1 = Math.toRadians(21.8);
            int nt = poisson(9.0 * plant * cold * dt);
            for (int k = 0; k < nt; k++) {
                double a = a0 + (a1 - a0) * rnd.nextDouble(), r = 235 + rnd.nextDouble() * 2.5;
                double sn = Math.sin(a), cs = Math.cos(a);
                double size = 2.4 + 2.0 * rnd.nextDouble();
                int id = ps.spawn(ParticleSystem.STEAM, (float) (r * sn), (float) (Dam.FL + 0.4 + rnd.nextDouble() * 1.2), (float) (Dam.CZ + r * cs),
                        (float) (-sn * 0.9 + windX * 0.4), 0.5f, (float) (-cs * 0.9 + windZ * 0.4),
                        (float) size, (float) (9 + 5 * rnd.nextDouble()), (float) (0.09 + 0.07 * rnd.nextDouble()), (float) (Dam.FL - 1.5), -1);
                if (id >= 0) { ps.grow[id] = (float) (0.7 + 0.5 * rnd.nextDouble()); ps.lift[id] = 0.3f; }
            }
            int nv = poisson(6.0 * plant * cold * dt);
            for (int k = 0; k < nv; k++) {
                double a = Math.toRadians(14.2 + 3.0 * rnd.nextInt(3)) + (rnd.nextDouble() - 0.5) * 0.004;
                double r = 249 + (rnd.nextDouble() - 0.5) * 1.5;
                int id = ps.spawn(ParticleSystem.STEAM, (float) (r * Math.sin(a)), (float) (Dam.FL + 18.2), (float) (Dam.CZ + r * Math.cos(a)),
                        (float) (windX * 0.6), 1.4f, (float) (windZ * 0.6),
                        (float) (1.1 + 0.8 * rnd.nextDouble()), (float) (6 + 4 * rnd.nextDouble()), (float) (0.10 + 0.07 * rnd.nextDouble()), (float) (Dam.FL), -1);
                if (id >= 0) { ps.grow[id] = (float) (1.0 + 0.8 * rnd.nextDouble()); ps.lift[id] = 0.5f; }
            }
        }
        // Nebelschwaden über See und Eder
        if (wisps && mist > 0.12) {
            double lakeRate = 3.2 * mist * lakeMist, riverRate = 1.3 * mist;
            int nl = poisson(lakeRate * dt);
            for (int k = 0; k < nl; k++) {
                double x = (rnd.nextDouble() - 0.5) * 520, z = 30 + rnd.nextDouble() * 650;
                if (Math.hypot(x, z + 300) < Dam.R + 15) continue;
                double size = 14 + 14 * rnd.nextDouble();
                int id = ps.spawn(ParticleSystem.STEAM, (float) x, (float) (lv + 1.5 + rnd.nextDouble() * 3), (float) z,
                        (float) (windX * 0.8), 0f, (float) (windZ * 0.8), (float) size, (float) (28 + 14 * rnd.nextDouble()),
                        (float) (0.07 + 0.07 * rnd.nextDouble()), (float) (lv + 0.2), -1);
                if (id >= 0) { ps.grow[id] = (float) (0.35 + 0.3 * rnd.nextDouble()); ps.lift[id] = 0.04f; }
            }
            int nr = poisson(riverRate * dt);
            for (int k = 0; k < nr; k++) {
                double z = -110 - rnd.nextDouble() * 520;
                double x = 10 - (z + 100) * 0.075 + (rnd.nextDouble() - 0.5) * 24;
                double size = 9 + 9 * rnd.nextDouble();
                int id = ps.spawn(ParticleSystem.STEAM, (float) x, (float) (-41 + rnd.nextDouble() * 3), (float) z,
                        0f, 0f, (float) (-1.0 - 0.5 * rnd.nextDouble()), (float) size, (float) (24 + 12 * rnd.nextDouble()),
                        (float) (0.07 + 0.06 * rnd.nextDouble()), -46f, -1);
                if (id >= 0) { ps.grow[id] = (float) (0.3 + 0.3 * rnd.nextDouble()); ps.lift[id] = 0.05f; }
            }
        }
    }

    /** Teil der vollen Geschwindigkeit bei teilgeöffnetem Schieber (Druck baut sich im Strahl nicht ganz ab). */
    private static double o0(double open) { return 0.55 + 0.45 * open; }

    private int poisson(double mean) {
        int n = (int) Math.floor(mean);
        if (rnd.nextDouble() < mean - n) n++;
        return n;
    }
}
