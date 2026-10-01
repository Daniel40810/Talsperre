package com.dan.talsperre.effects;

/**
 * Datum und Uhrzeit an der Edertalsperre (51,186° N, 9,062° O) im Jahr 2026 und daraus der Sonnenstand
 * im Koordinatensystem der Szene (x Osten, y oben, z Süden). Die Uhrzeit ist die Zeit auf der Uhr:
 * Mitteleuropäische Zeit, im Sommer MESZ (UTC+2), sonst MEZ (UTC+1). Sonnenstand nach den Formeln der
 * NOAA (Deklination und Zeitgleichung aus dem Bruchteil des Jahres), der Mond aus seiner Phase,
 * gerechnet vom Neumond am 6. Januar 2000. Aus Geyser übernommen.
 */
public final class DayNightCycle {
    public static final double LAT = 51.186, LON = 9.062;
    public static final int YEAR = 2026;
    /** Sommerzeit 2026: 29. März bis 25. Oktober (Tag im Jahr; Beginn 2 Uhr MEZ, Ende 3 Uhr MESZ). */
    public static final int DST_START = 88, DST_END = 298;

    private int day = 274;         // 1. Oktober
    private double hour = 9.0;     // Uhrzeit (MEZ/MESZ)

    public double elevationDeg, azimuthDeg;
    /** Richtung zur Sonne, normiert. */
    public final double[] dir = new double[3];
    /** Richtung zum Mond, Mondphase 0..1 (0 Neumond, 0,5 Vollmond) und beleuchteter Anteil 0..1. */
    public final double[] moonDir = new double[3];
    public double moonPhase, moonLit, moonElevationDeg;
    /** Wahre Ortszeit in Stunden (was eine Sonnenuhr zeigt) und Sternzeit in Grad. */
    public double solarHour, siderealDeg;
    public static final double SYNODIC = 29.530588853;

    public DayNightCycle() { update(); }

    public int day() { return day; }
    public double hour() { return hour; }

    public void set(int day, double hour) {
        this.day = Math.max(1, Math.min(365, day));
        this.hour = ((hour % 24) + 24) % 24;
        update();
    }

    /** Zeitraffer: Stunden weiterzählen, um Mitternacht beginnt der nächste Tag. */
    public void advance(double hours) {
        double h = hour + hours;
        while (h >= 24) { h -= 24; day = day % 365 + 1; }
        hour = h;
        update();
    }

    /** Gilt an diesem Tag und zu dieser Uhrzeit die Sommerzeit? */
    public static boolean dst(int day, double hour) {
        if (day > DST_START && day < DST_END) return true;
        if (day == DST_START) return hour >= 2;
        if (day == DST_END) return hour < 3;
        return false;
    }

    /** Abstand zur Weltzeit in Stunden (+2 im Sommer, +1 im Winter). */
    public static int utcOffset(int day, double hour) { return dst(day, hour) ? 2 : 1; }

    private void update() {
        int tz = utcOffset(day, hour);
        double utc = hour - tz;
        // Bruchteil des Jahres (NOAA), Tag und Stunde in Weltzeit
        double gamma = 2 * Math.PI / 365 * (day - 1 + (utc - 12) / 24);
        double eqt = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma) - 0.032077 * Math.sin(gamma)
                - 0.014615 * Math.cos(2 * gamma) - 0.040849 * Math.sin(2 * gamma));
        double decl = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma) - 0.006758 * Math.cos(2 * gamma)
                + 0.000907 * Math.sin(2 * gamma) - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma);
        double tst = hour * 60 + eqt + 4 * LON - 60 * tz;      // wahre Ortszeit in Minuten
        solarHour = ((tst / 60) % 24 + 24) % 24;
        double H = Math.toRadians(tst / 4 - 180);
        elevationDeg = toDir(decl, H, dir);
        azimuthDeg = (Math.toDegrees(Math.atan2(dir[0], -dir[2])) + 360) % 360;
        // Tage seit 1.1.2000 12 Uhr Weltzeit (für Mond und Sternzeit)
        double jd2000 = daysSince2000(day, utc);
        siderealDeg = ((280.46061837 + 360.98564736629 * jd2000 + LON) % 360 + 360) % 360;
        // Mond: Phase vom Neumond am 6.1.2000, 18:14 UT; Länge um die Phase gegen die Sonne versetzt,
        // Bahnneigung (5,1°) grob genähert
        double age = jd2000 - 5.2597;
        moonPhase = ((age / SYNODIC) % 1 + 1) % 1;
        moonLit = 0.5 * (1 - Math.cos(2 * Math.PI * moonPhase));
        double eps = Math.toRadians(23.44);
        double lamS = Math.toRadians(280.46 + 0.9856474 * jd2000) + Math.toRadians(1.915) * Math.sin(Math.toRadians(357.528 + 0.9856003 * jd2000));
        double lamM = lamS + 2 * Math.PI * moonPhase;
        double betaM = Math.toRadians(5.1) * Math.sin(Math.toRadians(93.27 + 13.22935 * jd2000));
        double declM = Math.asin(Math.sin(betaM) * Math.cos(eps) + Math.cos(betaM) * Math.sin(eps) * Math.sin(lamM));
        double raS = Math.atan2(Math.cos(eps) * Math.sin(lamS), Math.cos(lamS));
        double raM = Math.atan2(Math.sin(lamM) * Math.cos(eps) - Math.tan(betaM) * Math.sin(eps), Math.cos(lamM));
        double HM = H + raS - raM;
        moonElevationDeg = toDir(declM, HM, moonDir);
    }

    /** Tage seit dem 1. Januar 2000, 12 Uhr Weltzeit, für Tag day im Jahr 2026 und Stunde utc. */
    static double daysSince2000(int day, double utc) {
        int y = YEAR;
        int days = 0;
        for (int k = 2000; k < y; k++) days += (k % 4 == 0 && (k % 100 != 0 || k % 400 == 0)) ? 366 : 365;
        return days + (day - 1) + (utc - 12) / 24.0;
    }

    /** Richtung zu einem Gestirn mit Deklination decl und Stundenwinkel H; liefert die Höhe in Grad. */
    static double toDir(double decl, double H, double[] out) {
        double phi = Math.toRadians(LAT);
        double e = -Math.cos(decl) * Math.sin(H);
        double n = Math.sin(decl) * Math.cos(phi) - Math.cos(decl) * Math.cos(H) * Math.sin(phi);
        double u = Math.sin(decl) * Math.sin(phi) + Math.cos(decl) * Math.cos(H) * Math.cos(phi);
        double l = Math.sqrt(e * e + n * n + u * u);
        out[0] = e / l;
        out[1] = u / l;
        out[2] = -n / l;
        return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, u / l))));
    }

    /** Mondphase in Worten. */
    public String moonLabel() {
        double p = moonPhase;
        if (p < 0.03 || p > 0.97) return "Neumond";
        if (p < 0.22) return "zunehmende Sichel";
        if (p < 0.28) return "erstes Viertel";
        if (p < 0.47) return "zunehmender Mond";
        if (p < 0.53) return "Vollmond";
        if (p < 0.72) return "abnehmender Mond";
        if (p < 0.78) return "letztes Viertel";
        return "abnehmende Sichel";
    }

    public static String dateLabel(int day) {
        int[] len = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        String[] mon = {"Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez."};
        int d = day, m = 0;
        while (m < 11 && d > len[m]) { d -= len[m]; m++; }
        return d + ". " + mon[m];
    }

    public static String timeLabel(double h) {
        int hh = (int) Math.floor(h), mm = (int) Math.round((h - hh) * 60);
        if (mm == 60) { hh++; mm = 0; }
        return String.format("%02d:%02d", hh % 24, mm);
    }

    /** Zeitzone als Kürzel zum Tag und zur Uhrzeit. */
    public static String zone(int day, double hour) { return dst(day, hour) ? "MESZ" : "MEZ"; }

    /** Heutiger Tag im Jahr (für den Start), im Schaltjahr ohne den 29. Februar. */
    public static int today() {
        java.time.LocalDate d = java.time.LocalDate.now();
        int doy = d.getDayOfYear();
        if (d.isLeapYear() && doy > 59) doy--;
        return Math.max(1, Math.min(365, doy));
    }
}
