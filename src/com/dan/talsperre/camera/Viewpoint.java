package com.dan.talsperre.camera;

import com.dan.talsperre.core.Terrain;

/**
 * Feste Blickpunkte mit Namen: Augpunkt und Blickziel. Die Liste wächst mit den Phasen; in Phase 7
 * kommen die Kamerafahrten der Regie dazu.
 */
public final class Viewpoint {
    public final String name, note;
    /** ex, ey, ez, tx, ty, tz */
    public final double[] pose;

    public Viewpoint(String name, String note, double[] pose) {
        this.name = name; this.note = note; this.pose = pose;
    }

    /** Namen in der Reihenfolge von {@link #all}, schon vor dem Bau bekannt (für das Bedienfeld). */
    public static final String[] NAMES = {"Übersicht", "Mauerfuß", "Eder-Aue", "Luftbild", "Seearm", "Hang über der Mauer",
            "Mauerkrone", "Kraftwerk", "Tosbecken", "Torturm", "Überlauf", "Morgenlicht", "Regenbogen", "Nachtlichter", "Mauerschnitt", "Alte Brücke", "Berich"};

    private static Viewpoint[] all;

    /** Die Blickpunkte (einmal nach dem Bau des Geländes angelegt). */
    public static synchronized Viewpoint[] all(Terrain t) {
        if (all != null) return all;
        all = new Viewpoint[]{
                new Viewpoint("Übersicht", "Von Norden aus dem Edertal auf Mauer und See",
                        orbit(0, -12, -30, 168, 11, 640)),
                new Viewpoint("Mauerfuß", "Aus dem Tal unterhalb der Mauer nach Süden",
                        new double[]{-70, t.sample(-70, -230) + 4, -230, 20, -18, -60}),
                new Viewpoint("Eder-Aue", "Am Ufer der Eder, Blick flussaufwärts zur Mauer",
                        new double[]{110, t.sample(110, -1300) + 2.6, -1300, 0, -20, -250}),
                new Viewpoint("Luftbild", "Das ganze Tal aus 600 m Höhe, von Nordosten",
                        new double[]{1500, 560, -1700, -1000, -40, 1100}),
                new Viewpoint("Seearm", "Über dem See nach Westen, die Arme des Edersees",
                        new double[]{-800, 120, 1100, -2800, -30, 2400}),
                new Viewpoint("Hang über der Mauer", "Vom Westhang über die Krone auf See und Tal",
                        new double[]{-260, t.sample(-260, 40) + 40, 40, 80, -10, 600}),
                new Viewpoint("Mauerkrone", "Auf der Krone, Blick entlang der Mauer nach Osten",
                        new double[]{-116, 4.4, -27, 59, 3.4, -9}),
                new Viewpoint("Kraftwerk", "Vom Ostufer auf das Maschinenhaus am Mauerfuß",
                        new double[]{150, t.sample(150, -175) + 4, -175, 74, -30, -62}),
                new Viewpoint("Tosbecken", "Unter den fünf Ablässen, Blick flussauf",
                        new double[]{8, t.sample(8, -190) + 4, -190, 0, -33, -45}),
                new Viewpoint("Torturm", "Aus der Luft auf den Torturm der Ostseite",
                        new double[]{52, 24, 70, 90.7, 14, -14}),
                new Viewpoint("Überlauf", "Von unten auf die Überläufe, wenn der See überläuft",
                        new double[]{-20, t.sample(-20, -165) + 5, -165, -34, -18, -45}),
                new Viewpoint("Morgenlicht", "Im Tal gegen die aufgehende Sonne, Licht im Morgendunst",
                        new double[]{-60, -20, -170, 222, 40, -80}),
                new Viewpoint("Regenbogen", "Von der Mauer schräg auf die Gischt im Tosbecken, Mittagssonne im Sommer",
                        new double[]{-70, 30, -30, 15, -34, -80}),
                new Viewpoint("Nachtlichter", "Über dem See nachts: Kronenlampen und ihre Spiegelung",
                        new double[]{40, 12, 60, -10, 2, -80}),
                new Viewpoint("Mauerschnitt", "Aus der Lücke auf die Schnittfläche der Mauer (der Keil wird herausgenommen)",
                        new double[]{40, -12, -62, -12.7, -22, -20}),
                new Viewpoint("Alte Brücke", "Niedrigwasser im Seearm, 7,5 km westlich der Mauer: die Aseler Brücke (60 m, vier Bögen) taucht auf (Pegel wird auf −14 m gesetzt)",
                        new double[]{-7215, -7, 2015, -7150, -12, 1970}),
                new Viewpoint("Berich", "Niedrigwasser, 5,9 km westlich der Mauer: die Grundmauern von Berich auf dem Nordufer (Pegel wird auf −19 m gesetzt)",
                        new double[]{-5620, -9, 1760, -5550, -16, 1665})};
        return all;
    }

    /** Pose im Orbit-Maß (Drehpunkt, Gier, Nick, Abstand) als Augpunkt und Blickziel. */
    static double[] orbit(double tx, double ty, double tz, double yawDeg, double pitchDeg, double d) {
        double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
        return new double[]{tx + d * Math.cos(p) * Math.sin(y), ty + d * Math.sin(p), tz + d * Math.cos(p) * Math.cos(y), tx, ty, tz};
    }
}
