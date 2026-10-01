package com.dan.talsperre.world;

import com.dan.talsperre.core.Mat;
import com.dan.talsperre.core.MeshBuilder;
import com.dan.talsperre.core.Terrain;
import java.util.Random;

/**
 * Was der See im Normalfall verbirgt: die Aseler Brücke aus Stein und die Grundmauern von Berich auf einer Terrasse
 * am Nordufer des oberen Seearms. Nach den Angaben zur Lage liegen Berich rund 5,9 km und die Aseler Brücke rund 7,5 km von der Mauer
 * entfernt (Luftlinie). Die Brücke (60 m, vier Bögen) ist unter etwa 235 m ü. NHN sichtbar, also ab −10 m gegenüber Stauziel; die Grundmauern
 * von Berich liegen tiefer. Die Lage im Gelände ist nachempfunden.
 */
public final class Ruins {
    private Ruins() { }

    /** Brücke und Dorf im Seearm: Mitte, Brückenoberkante, Terrassenhöhe. */
    public static final double BRIDGE_X = -7150, BRIDGE_Z = 1970, DECK = -10.0;
    public static final double BERICH_X = -5550, BERICH_Z = 1665, BENCH = -16.0;

    /** Terrasse für Berich; muss vor {@link Terrain#build} laufen. */
    public static void shape(Terrain t) {
        t.pad(BERICH_X, BERICH_Z, 70, BENCH, 40);
    }

    public static void build(MeshBuilder mb, Terrain t) {
        int keep = mb.group;
        bridge(mb, t);
        village(mb, t);
        mb.group = keep;
    }

    private static void bridge(MeshBuilder mb, Terrain t) {
        double x = BRIDGE_X, zc = BRIDGE_Z, hw = 3.0;
        double[] pz = {-24, -12, 0, 12, 24};
        double deckBot = DECK - 0.9, spring = deckBot - 4.6;
        // Fahrbahn und Brüstungen
        mb.box(x - hw, deckBot, zc - 30, x + hw, DECK, zc + 30, Mat.STONE, true);
        mb.box(x - hw, DECK, zc - 30, x - hw + 0.55, DECK + 0.9, zc + 30, Mat.STONE, false);
        mb.box(x + hw - 0.55, DECK, zc - 30, x + hw, DECK + 0.9, zc + 30, Mat.STONE, false);
        // Pfeiler bis auf den Grund
        for (double p : pz) {
            double g = Math.min(t.sample(x, zc + p), spring) - 3;
            mb.box(x - hw + 0.2, g, zc + p - 1.5, x + hw - 0.2, spring, zc + p + 1.5, Mat.STONE, false);
        }
        // Bogen zwischen den Pfeilern: Unterseite und Stirnflächen über dem Bogen
        mb.maxEdge = 1.5;
        for (int i = 0; i + 1 < pz.length; i++) {
            final double za = zc + pz[i] + 1.5, zb = zc + pz[i + 1] - 1.5, mid = (za + zb) / 2, half = (zb - za) / 2;
            final double rise = deckBot - spring;
            mb.patch((u, v, p, n) -> {
                p[0] = x - hw + 2 * hw * v; p[2] = mid + half * Math.cos(u); p[1] = spring + rise * Math.sin(u);
                n[0] = 0; n[1] = -Math.sin(u) * rise; n[2] = -Math.cos(u) * half;
            }, 0, Math.PI, 12, 0, 1, 2, Mat.STONE);
            for (int sd = -1; sd <= 1; sd += 2) {
                final double xs = x + sd * hw; final int sdf = sd;
                mb.patch((u, v, p, n) -> {
                    double zz = za + (zb - za) * u;
                    double ya = spring + rise * Math.sqrt(Math.max(0, 1 - Math.pow((zz - mid) / half, 2)));
                    p[0] = xs; p[2] = zz; p[1] = ya + (deckBot - ya) * v;
                    n[0] = sdf; n[1] = 0; n[2] = 0;
                }, 0, 1, 12, 0, 1, 2, Mat.STONE);
            }
        }
        mb.maxEdge = 2.5;
    }

    private static void village(MeshBuilder mb, Terrain t) {
        Random rnd = new Random(1900);
        // Dorfstraße entlang der Terrasse, Häuser links und rechts
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 6; i++) {
                double x = BERICH_X - 52 + i * 19 + rnd.nextDouble() * 4, z = BERICH_Z + side * (13 + rnd.nextDouble() * 3);
                double w = 9 + rnd.nextDouble() * 4, d = 7 + rnd.nextDouble() * 3, h = 1.2 + rnd.nextDouble() * 2.2;
                house(mb, t, x, z, w, d, h, rnd);
            }
        }
        // Kirche: Turmstumpf und Schiff ohne Dach
        double cx = BERICH_X + 4, cz = BERICH_Z - 38;
        mb.box(cx - 3, BENCH - 0.5, cz - 3, cx + 3, BENCH + 9, cz + 3, Mat.STONE, false);
        mb.box(cx + 3, BENCH - 0.5, cz - 4.5, cx + 3.8, BENCH + 3.8, cz + 12, Mat.STONE, false);
        mb.box(cx - 3.8, BENCH - 0.5, cz - 4.5, cx - 3, BENCH + 3.0, cz + 12, Mat.STONE, false);
        mb.box(cx - 3, BENCH - 0.5, cz + 11.2, cx + 3, BENCH + 2.2, cz + 12, Mat.STONE, false);
    }

    private static void house(MeshBuilder mb, Terrain t, double x, double z, double w, double d, double h, Random rnd) {
        double th = 0.6, y0 = BENCH - 0.5;
        mb.box(x - w / 2, y0, z - d / 2, x + w / 2, y0 + h * (0.6 + 0.4 * rnd.nextDouble()), z - d / 2 + th, Mat.STONE, false);
        mb.box(x - w / 2, y0, z + d / 2 - th, x + w / 2, y0 + h * (0.6 + 0.4 * rnd.nextDouble()), z + d / 2, Mat.STONE, false);
        mb.box(x - w / 2, y0, z - d / 2 + th, x - w / 2 + th, y0 + h * (0.5 + 0.5 * rnd.nextDouble()), z + d / 2 - th, Mat.STONE, false);
        if (rnd.nextBoolean()) mb.box(x + w / 2 - th, y0, z - d / 2 + th, x + w / 2, y0 + h * 0.7, z - 0.7, Mat.STONE, false);
        mb.box(x + w / 2 - th, y0, z + 0.7, x + w / 2, y0 + h * 0.5, z + d / 2 - th, Mat.STONE, false);
    }
}
