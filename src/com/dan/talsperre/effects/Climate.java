package com.dan.talsperre.effects;

/**
 * Klima am Old Faithful nach den Normalwerten 1991–2020 (mittlere Höchst- und Tiefstwerte je Monat,
 * Station im Park bei Old Faithful, NOAA über Current Results). Daraus:
 * <ul>
 * <li>die Lufttemperatur zu Tag und Stunde: Tiefstwert um 6:30, Höchstwert um 15:30, dazwischen
 * ein Kosinusverlauf;</li>
 * <li>wie gut man Dampf sieht: kalte Luft kann wenig Wasser halten, der Dampf kondensiert zu
 * dichten Wolken; an warmen Nachmittagen verdunstet er schnell;</li>
 * <li>die Schneedecke: ein einfaches Tagesmodell über die Monatsmittel. Unter 0 °C fällt Schnee und
 * bleibt liegen, darüber schmilzt er nach Gradtagen. So liegt Schnee von November bis in den Mai.</li>
 * </ul>
 */
public final class Climate {
    /** Mittlere Höchst- und Tiefstwerte in °F, Januar bis Dezember. */
    public static final double[] HI_F = {28, 31, 39, 46, 55, 65, 76, 75, 65, 50, 35, 27};
    public static final double[] LO_F = {1, 2, 10, 19, 29, 35, 39, 37, 30, 22, 10, 1};
    static final int[] MID = {15, 45, 74, 105, 135, 166, 196, 227, 258, 288, 319, 349};
    private static final double[] SNOW = new double[366];

    static { snowTable(); }

    /** Normalwerte aus der Datenbank (GEY_CLIMATE) übernehmen und die Schneedecke neu rechnen. */
    public static synchronized void set(double[] hiF, double[] loF) {
        System.arraycopy(hiF, 0, HI_F, 0, 12);
        System.arraycopy(loF, 0, LO_F, 0, 12);
        snowTable();
    }

    private static void snowTable() {
        // Schneedecke: zwei Jahre durchrechnen, damit der Winter über den Jahreswechsel eingeschwungen ist
        double pack = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int d = 1; d <= 365; d++) {
                double mean = (hi(d) + lo(d)) / 2;
                if (mean < 0.5) pack += 1.0;               // Schneetage
                else pack -= mean * 1.6;                  // Schmelze nach Gradtagen
                if (lo(d) > 2) pack -= 3;                 // milde Nächte: schneller weg
                pack = Math.max(0, Math.min(120, pack));
                SNOW[d] = pack;
            }
        }
    }

    private Climate() { }

    private static double f2c(double f) { return (f - 32) / 1.8; }

    /** Normalwert (Höchst- oder Tiefstwert) für Tag d, zwischen den Monatsmitten linear. */
    private static double interp(double[] tab, int d) {
        int m = 0;
        while (m < 11 && d > MID[m + 1]) m++;
        int a = d < MID[0] ? 11 : m, b = d < MID[0] ? 0 : (m + 1) % 12;
        double da = MID[a], db = MID[b];
        if (d < MID[0]) da -= 365;
        if (b == 0 && d >= MID[11]) db += 365;
        double t = (d - da) / (db - da);
        return f2c(tab[a] + (tab[b] - tab[a]) * t);
    }

    public static double hi(int d) { return interp(HI_F, d); }
    public static double lo(int d) { return interp(LO_F, d); }

    /** Lufttemperatur in °C am Tag d zur Stunde h (Ortszeit auf der Uhr). */
    public static double air(int d, double h) {
        double lo = lo(d), hi = hi(d);
        double u;
        if (h >= 6.5 && h <= 15.5) u = (1 - Math.cos(Math.PI * (h - 6.5) / 9)) / 2;
        else {
            double s = h > 15.5 ? h - 15.5 : h + 24 - 15.5;          // 0..15 Stunden nach dem Höchstwert
            u = (1 + Math.cos(Math.PI * s / 15)) / 2;
        }
        return lo + (hi - lo) * u;
    }

    /** Sichtbarkeit von Dampf 0,15..1: je kälter, desto dichter. */
    public static double steam(double t) {
        return Math.max(0.15, Math.min(1, (22 - t) / 28));
    }

    /** Schneedecke 0..1 am Tag d. */
    public static double snow(int d) {
        return Math.max(0, Math.min(1, SNOW[Math.max(1, Math.min(365, d))] / 15));
    }
}
