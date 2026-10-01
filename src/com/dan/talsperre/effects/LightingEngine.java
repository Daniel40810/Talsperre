package com.dan.talsperre.effects;

import com.dan.talsperre.core.Mesh;
import com.dan.talsperre.core.ShadowMap;

/**
 * Alles, was vom Stand von Sonne und Mond abhängt: Himmel und zwei Schattenkarten, eine feine über
 * dem gewählten Ort, Upper Geyser Basin oder Midway (0,6 m je Kartenpunkt) und eine weite über das ganze Tal (knapp 7 m), die
 * die langen Schatten der Hänge am Abend fängt. Zwei solcher Objekte wechseln sich ab, damit ein
 * Hintergrund-Thread das nächste berechnen kann, während das aktuelle gezeichnet wird (Zeitraffer).
 * Aus Semiramis und Geyser übernommen.
 */
public final class LightingEngine {
    /** Mitte und halbe Kantenlängen der beiden Karten in Metern. */
    public static final double FCX = 0, FCY = -20, FCZ = 0, FINE_HALF = 1300;
    /** Mitte der feinen Karte: die Sperrmauer. */
    public static volatile double centerX = FCX, centerZ = FCZ;
    public static final double WCX = 0, WCY = 0, WCZ = 0, WIDE_HALF = 9000;

    public final Sky sky = new Sky();
    public final ShadowMap fine, wide;
    public final double[] sun = new double[3];

    public LightingEngine(int shadowSize) {
        fine = new ShadowMap(shadowSize);
        wide = new ShadowMap(shadowSize);
    }

    /** Himmel und Schatten zu Sonne und Mond; nachts werfen die Dinge Mondschatten. */
    public void compute(Mesh m, double[] sunDir, double[] moonDir, double moonLit, double haze) {
        sky.update(sunDir, moonDir, moonLit, haze);
        double[] dir = sky.sun;
        sun[0] = dir[0]; sun[1] = dir[1]; sun[2] = dir[2];
        boolean lightUp = sky.sunR + sky.sunG + sky.sunB > 1e-4 && dir[1] > -0.02;
        if (lightUp && m != null && m.nt > 0) {
            fine.render(m, dir, centerX, FCY, centerZ, FINE_HALF);
            wide.render(m, dir, WCX, WCY, WCZ, WIDE_HALF);
        } else {
            fine.valid = false;
            wide.valid = false;
        }
    }

    /** Sonnenanteil 0..1; soft = false nimmt einen einzelnen gefilterten Abgriff (für Fernes). */
    public float lit(double x, double y, double z, double slope, boolean soft) {
        float v = fine.litOrMiss(x, y, z, slope, soft, 0.55);
        if (v >= 0) return v;
        // Die weite Karte ist grob: immer weich und breiter gefiltert, sonst werden lange Schatten treppig
        v = wide.litOrMiss(x, y, z, slope, true, 1.4);
        return v >= 0 ? v : 1;
    }
}
