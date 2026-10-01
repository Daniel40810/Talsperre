package com.dan.talsperre.effects;

import com.dan.talsperre.core.Terrain;
import com.dan.talsperre.core.Wetness;

/**
 * Teilchen der Ausbrüche und Quellen: Tropfen (einzeln, fliegen ballistisch), Gischt (Tropfenwolken,
 * die den Körper der Säule bilden), Dampf (steigt, wächst, treibt im Wind) und Spritzer beim Aufprall.
 * Tropfen und Gischt spüren Schwerkraft und quadratischen Luftwiderstand gegen den Wind; landen sie,
 * nässen sie den Boden und werfen Spritzer. Bis zu {@link #CAP} Teilchen, in flachen Feldern.
 */
public final class ParticleSystem {
    public static final byte DROP = 0, SPRAY = 1, STEAM = 2, SPLASH = 3, RAIN = 4, SNOW = 5, MUD = 6;
    public static final int CAP = 90000;
    public static final float G = 9.81f;
    /** Luftwiderstand k (1/m) von Tropfen und Gischt: a = −k·|v−w|·(v−w). */
    public static final float K_DROP = 0.018f, K_SPRAY = 0.024f;

    public int n;
    public final float[] x = new float[CAP], y = new float[CAP], z = new float[CAP];
    public final float[] vx = new float[CAP], vy = new float[CAP], vz = new float[CAP];
    public final float[] size = new float[CAP], age = new float[CAP], life = new float[CAP], grow = new float[CAP];
    /** Deckkraft des Teilchens (vor dem Ein- und Ausblenden) und die Höhe, unter der es landet. */
    public final float[] alpha = new float[CAP], floor = new float[CAP];
    /** Luftwiderstand als Vielfaches des Standardwerts (Strahlen aus den Ablässen fliegen weiter). */
    public final float[] drag = new float[CAP];
    /** Auftrieb des Dampfs als Vielfaches des Standardwerts (Nebelschwaden steigen kaum). */
    public final float[] lift = new float[CAP];
    public final byte[] kind = new byte[CAP];
    /** Nummer des Nässerasters, in das es beim Landen tropft (−1: keins). */
    public final short[] wet = new short[CAP];

    private final java.util.Random rnd = new java.util.Random(1870);
    private int seed = 7;

    /** Zähler der letzten Sekunde, für die Statuszeile. */
    public int spawnedDrops;

    public float rand() {
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 8) & 0xFFFFFF) / 16777216f;
    }

    public java.util.Random random() { return rnd; }

    /** Neues Teilchen; liefert den Index oder −1, wenn kein Platz ist. */
    public int spawn(byte k, float px, float py, float pz, float ux, float uy, float uz, float sz, float lf, float al, float fl, int wetIdx) {
        if (n >= CAP) return -1;
        int i = n++;
        kind[i] = k; x[i] = px; y[i] = py; z[i] = pz; vx[i] = ux; vy[i] = uy; vz[i] = uz;
        size[i] = sz; age[i] = 0; life[i] = lf; grow[i] = 0; alpha[i] = al; floor[i] = fl; wet[i] = (short) wetIdx; drag[i] = 1; lift[i] = 1;
        return i;
    }

    private void kill(int i) {
        int j = --n;
        if (i == j) return;
        kind[i] = kind[j]; x[i] = x[j]; y[i] = y[j]; z[i] = z[j]; vx[i] = vx[j]; vy[i] = vy[j]; vz[i] = vz[j];
        size[i] = size[j]; age[i] = age[j]; life[i] = life[j]; grow[i] = grow[j]; alpha[i] = alpha[j]; floor[i] = floor[j]; wet[i] = wet[j]; drag[i] = drag[j]; lift[i] = lift[j];
    }

    /**
     * Ein Zeitschritt. Wind wx, wz in m/s (in 10 m Höhe; am Boden halb so stark). Landende Tropfen
     * nässen das Raster wets.list.get(wet[i]) und werfen mit einiger Wahrscheinlichkeit Spritzer.
     */
    public void step(float dt, float wx, float wz, Terrain t, Wetness.Set wets) {
        if (dt <= 0) return;
        for (int i = 0; i < n; i++) {
            float a = age[i] + dt;
            if (a >= life[i]) { kill(i); i--; continue; }
            age[i] = a;
            byte k = kind[i];
            float gy = Math.max(t.sample(x[i], z[i]), floor[i]);
            float hAbove = y[i] - gy;
            float wk = 0.5f + 0.5f * Math.min(1, Math.max(0, hAbove) / 10f);
            float ax = wx * wk, az = wz * wk;
            if (k == RAIN || k == SNOW) {
                // Regen fällt mit Endgeschwindigkeit, Schnee langsam und pendelnd (grow: Phase); am Boden weg
                if (k == SNOW) {
                    float sw = (float) Math.sin(a * 1.7f + grow[i]) * 0.5f;
                    x[i] += (vx[i] + sw) * dt; z[i] += (vz[i] + sw * 0.6f) * dt;
                } else { x[i] += vx[i] * dt; z[i] += vz[i] * dt; }
                y[i] += vy[i] * dt;
                if (y[i] <= gy) {
                    if (k == RAIN && rand() < 0.04f) spawn(SPLASH, x[i], gy + 0.03f, z[i], 0, 0.8f, 0, 0.05f, 0.25f, 0.4f, -1e9f, -1);
                    kill(i); i--;
                }
                continue;
            }
            if (k == STEAM) {
                // Auftrieb klingt ab, der Wind übernimmt; die Schwade wächst
                float rise = (2.6f * (1 - a / life[i]) + 0.3f) * lift[i];
                vx[i] += (ax - vx[i]) * Math.min(1, 0.8f * dt);
                vz[i] += (az - vz[i]) * Math.min(1, 0.8f * dt);
                vy[i] += (rise - vy[i]) * Math.min(1, 0.9f * dt);
                size[i] += grow[i] * dt;
                x[i] += vx[i] * dt; y[i] += vy[i] * dt; z[i] += vz[i] * dt;
                if (y[i] < gy + size[i] * 0.3f) { y[i] = gy + size[i] * 0.3f; if (vy[i] < 0) vy[i] = 0; }
                continue;
            }
            float kd = (k == DROP ? K_DROP : (k == SPRAY ? K_SPRAY : 0.05f)) * drag[i];
            float rx = vx[i] - ax, ry = vy[i], rz = vz[i] - az;
            float sp = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
            float d = kd * sp * dt;
            if (d > 0.9f) d = 0.9f;
            vx[i] -= rx * d; vy[i] -= ry * d; vz[i] -= rz * d;
            vy[i] -= G * dt;
            x[i] += vx[i] * dt; y[i] += vy[i] * dt; z[i] += vz[i] * dt;
            if (k == SPRAY) size[i] += grow[i] * dt;
            if (y[i] <= gy && vy[i] < 0) {
                int wi = wet[i];
                if (wi >= 0 && wi < wets.list.size() && k != SPLASH) wets.list.get(wi).add(x[i], z[i], k == DROP ? 0.012f : 0.03f);
                if (k != SPLASH && k != MUD && rand() < (k == DROP ? 0.22f : 0.5f)) {
                    float s = (float) Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]);
                    int m = k == DROP ? 1 : 3;
                    for (int q = 0; q < m; q++) {
                        float up = s * (0.06f + 0.12f * rand());
                        float an = rand() * 6.2832f, hs = up * (0.5f + rand());
                        spawn(SPLASH, x[i], gy + 0.05f, z[i], (float) Math.cos(an) * hs + vx[i] * 0.1f, up, (float) Math.sin(an) * hs + vz[i] * 0.1f,
                                k == DROP ? 0.12f : 0.35f + 0.3f * rand(), 0.5f + 0.5f * rand(), 0.5f, floor[i], -1);
                    }
                }
                kill(i);
                i--;
            }
        }
    }

    public void clear() { n = 0; }
}
