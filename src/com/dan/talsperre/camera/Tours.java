package com.dan.talsperre.camera;

import com.dan.talsperre.camera.Director.Program;
import com.dan.talsperre.camera.Director.Shot;
import com.dan.talsperre.core.Terrain;
import com.dan.talsperre.world.Dam;

/**
 * Drehbücher der Regie: Rundumblick, Zoom von der Totale bis zur Fuge, Flug über den See, Gang
 * über die Krone, Fahrt durch das Tosbecken und „Ein Tag an der Edertalsperre“ mit Uhrzeit,
 * Blenden und Tafeln. Alles in Metern: x Ost, y hoch, z Süd, Ursprung Kronenmitte.
 */
public final class Tours {
    private Tours() { }

    public static final String[] NAMES = {"Rundumblick", "Zoom bis zur Fuge", "Flug über den See", "Gang über die Krone",
            "Durch das Tosbecken", "Ein Tag an der Edertalsperre", "Bauzeitraffer 1908–1914", "Die Nacht des 17. Mai 1943", "Der Schnitt durch die Mauer"};

    public static Program get(int i, Terrain t) {
        switch (i) {
            case 0: return program("Rundumblick", orbitShot(t, 70, 0));
            case 1: return program("Zoom bis zur Fuge", zoomShot(t, 36));
            case 2: return program("Flug über den See", lakeShot(t, 40));
            case 3: return program("Gang über die Krone", crestShot(t, 46));
            case 4: return program("Durch das Tosbecken", basinShot(t, 40));
            case 6: return build(t);
            case 7: return breach(t);
            case 8: return section(t);
            default: return day(t);
        }
    }

    private static Program program(String title, CameraPath p) {
        Program pr = new Program(title);
        pr.add(new Shot(p));
        return pr;
    }

    private static Program program(String title, Shot s) {
        Program pr = new Program(title);
        pr.add(s);
        return pr;
    }

    /** Eye so hoch über dem Gelände, wie verlangt. */
    private static double up(Terrain t, double x, double z, double minAbove, double y) {
        return Math.max(y, t.sample(x, z) + minAbove);
    }

    // ------------------------------------------------------------ Einstellungen

    /** Einmal um die Mauer, steigend und wieder sinkend; startet über dem See. */
    static CameraPath orbitPath(Terrain t, double sec, double startDeg) {
        CameraPath p = new CameraPath();
        int n = 9;
        for (int i = 0; i <= n; i++) {
            double u = i / (double) n;
            double a = Math.toRadians(startDeg + 360 * u);
            double r = 400 - 130 * Math.sin(Math.PI * u);
            double h = 55 + 110 * Math.sin(Math.PI * u) * Math.sin(Math.PI * u) + 30 * u;
            double x = r * Math.sin(a), z = -30 + r * Math.cos(a);
            p.add(sec * u, x, up(t, x, z, 22, h), z, 0, -14 + 8 * Math.sin(Math.PI * u), -30);
        }
        return p;
    }

    static Shot orbitShot(Terrain t, double sec, double startOffset) {
        return new Shot(orbitPath(t, sec, 168 + startOffset), Double.NaN, Double.NaN, "Rundumblick",
                "Einmal um die Sperrmauer: Die Mauer schwingt im Bogen mit 300 m Radius über das Tal, davor der See, dahinter die Eder.", null);
    }

    /** Von der Totale bis zur Fuge im Mauerwerk. */
    static Shot zoomShot(Terrain t, double sec) {
        double phi = Math.toRadians(8), yw = -8;
        double rd = Dam.R - Dam.width(yw);
        double sx = Dam.x(phi, rd), sz = Dam.z(phi, rd);
        // Normale der Luftseite zeigt zu kleinerem r
        double nx = -Math.sin(phi), nz = -Math.cos(phi);
        CameraPath p = new CameraPath();
        p.add(0, 0, 1700, -420, 0, 0, -40);
        p.then(sec * 0.26, 140, 760, -520, 0, -10, -60);
        p.then(sec * 0.22, sx + nx * 160 + 60, 70, sz + nz * 160, sx, yw, sz);
        p.then(sec * 0.24, sx + nx * 26 + 8, yw + 4, sz + nz * 26, sx, yw, sz);
        p.then(sec * 0.28, sx + nx * 1.1 + 0.9, yw + 0.35, sz + nz * 1.1, sx - 0.3, yw - 0.25, sz);
        return new Shot(p, Double.NaN, Double.NaN, "Zoom bis zur Fuge",
                "Vom Satellitenblick über das Tal bis auf das Bruchsteinmauerwerk: Grauwacke in Lagen, Marmor nur als Zierstein.", null);
    }

    /** Flug tief über den See (Seeachse nach Südwesten) auf die Mauer zu, über die Krone ins Edertal. */
    static Shot lakeShot(Terrain t, double sec) {
        CameraPath p = new CameraPath();
        p.add(0, -260, 12, 750, -170, 6, 520);
        p.then(sec * 0.28, -200, 10, 560, -110, 5, 330);
        p.then(sec * 0.25, -120, 10, 330, -60, 5, 110);
        p.then(sec * 0.17, -45, 12, 130, 0, 6, -30);
        p.then(sec * 0.15, -8, 26, 22, 0, 0, -250);
        p.then(sec * 0.15, 0, 70, -80, 0, -20, -420);
        return new Shot(p, Double.NaN, Double.NaN, "Flug über den See",
                "Tief über das Wasser auf die Mauer zu, über die Krone hinweg ins Edertal.", null);
    }

    /** Zu Fuß über die Krone von West nach Ost. */
    static Shot crestShot(Terrain t, double sec) {
        CameraPath p = new CameraPath();
        int n = 6;
        for (int i = 0; i <= n; i++) {
            double u = i / (double) n;
            double phi = Math.toRadians(-38 + 76 * u), ph2 = phi + Math.toRadians(5);
            double r = Dam.R - 3.0;
            double look = (u < 0.5 ? 1 : 0) * 0 + Math.sin(u * Math.PI * 2) * 0.5;   // Blick pendelt zum See und zur Luftseite
            double tr = Dam.R - 3.0 - 6 * look;
            p.add(sec * u, Dam.x(phi, r), Dam.TOP + 1.7, Dam.z(phi, r), Dam.x(ph2, tr), Dam.TOP + 1.0 - 2 * Math.max(0, look), Dam.z(ph2, tr));
        }
        return new Shot(p, Double.NaN, Double.NaN, "Gang über die Krone",
                "Fahrbahn 6 m breit zwischen Brüstungen mit Marmorkappen, die beiden Tortürme am Rand.", null);
    }

    /** Durch das Tosbecken gegen die Strahlen, dann an der Mauer hinauf. */
    static Shot basinShot(Terrain t, double sec) {
        CameraPath p = new CameraPath();
        p.add(0, 6, up(t, 6, -420, 5, -20), -420, 0, -40, -60);
        p.then(sec * 0.28, 4, -38, -130, 0, -36, -50);
        p.then(sec * 0.25, 2, -38.5, -78, 0, -34, -45);
        p.then(sec * 0.25, 0, -22, -50, 0, -10, -30);
        p.then(sec * 0.22, 0, 14, -22, 0, -2, 60);
        return new Shot(p, Double.NaN, Double.NaN, "Durch das Tosbecken",
                "Gegen die Strahlen der fünf Grundablässe, dann an der Luftseite der Mauer hinauf.", null);
    }

    // ------------------------------------------------------------ Zugaben

    /** Bauzeitraffer: die Mauer wächst von 1908 bis 1914, danach füllt sich der See (72 s, Takt der Zugaben). */
    private static Program build(Terrain t) {
        Program pr = new Program("Bauzeitraffer 1908–1914");
        pr.clean = false;
        CameraPath a = new CameraPath();
        a.add(0, -170, up(t, -170, -190, 20, 42), -190, 0, -22, -20);
        a.then(24, 120, up(t, 120, -210, 20, 34), -210, 0, -8, -20);
        Shot s1 = new Shot(a, 9.0, 12.0, "Bauzeitraffer 1908–1914",
                "Lage für Lage wächst die Mauer aus Grauwacke-Bruchstein. Hunderte Arbeiter, ein Kabelkran über dem Tal und Gerüste an der Luftseite.", null);
        s1.fadeIn = true;
        s1.trigger("build", 0);
        pr.add(s1);
        CameraPath b = new CameraPath();
        b.add(0, 90, 30, -75, -10, 2, -20);
        b.then(22, -70, 26, -85, 20, 2, -20);
        pr.add(new Shot(b, 12.0, 15.0, "Krone und Türme", "Zuletzt kommen Krone, Brüstungen, Torfenster und die beiden Türme; daneben das Kraftwerk.", null));
        CameraPath c = new CameraPath();
        c.add(0, 120, 50, 260, 0, 0, 0);
        c.then(26, -40, 120, 470, 0, -8, -10);
        Shot s3 = new Shot(c, 15.0, 17.0, "Der See füllt sich",
                "1914 ist die Mauer fertig; der Einstau beginnt. Der Edersee wird zum größten Stausee Deutschlands seiner Zeit.", null);
        s3.fadeOut = true;
        pr.add(s3);
        return pr;
    }

    /** Die Nacht des 17. Mai 1943 (96 s, Takt der Zugaben): Anflug, Bruch der Mauer, Flutwelle, leerer See. */
    private static Program breach(Terrain t) {
        Program pr = new Program("Die Nacht des 17. Mai 1943");
        pr.clean = false;
        CameraPath a = new CameraPath();
        a.add(0, 210, 62, 400, -25, 12, 40);
        a.then(13, 140, 44, 290, 5, 8, 0);
        Shot s1 = new Shot(a, 0.4, 0.5, "Nacht auf den 17. Mai 1943",
                "Nachstellung: Britische Bomber greifen im Tiefflug über dem See die Sperrmauer an, im Mondlicht.", null);
        s1.fadeIn = true;
        s1.trigger("breach", 0);
        pr.add(s1);
        CameraPath b = new CameraPath();
        b.add(0, 100, -4, -150, 20, -14, -20);
        b.then(22, 38, 8, -118, 14, -12, -16);
        pr.add(new Shot(b, 0.5, 0.9, "Die Mauer bricht",
                "Eine Lücke von rund 70 m Breite und 22 m Tiefe: Der See strömt aus, mit mehr Wasser, als das Tosbecken fassen kann.", null));
        CameraPath c = new CameraPath();
        c.add(0, 20, up(t, 20, -170, 30, 30), -170, 0, -36, -420);
        c.then(14, -10, up(t, -10, -400, 40, 40), -400, -22, -40, -760);
        c.then(13, -40, up(t, -40, -700, 40, 44), -700, -50, -40, -1100);
        pr.add(new Shot(c, 0.9, 1.5, "Die Flutwelle",
                "Die Welle läuft durch das Edertal und reißt Brücken und Häuser mit. Bei der Flut starben mehr als 70 Menschen.", null));
        CameraPath d = new CameraPath();
        d.add(0, 260, 170, -330, 0, -30, 120);
        d.then(18, -260, 300, 420, 0, -30, 40);
        d.then(16, -520, 360, 900, 0, -30, 60);
        Shot s4 = new Shot(d, 1.5, 2.2, "Der See ist fast leer",
                "Rund 160 Millionen m³ flossen ab. Die Mauer wurde noch im Jahr 1943 wieder geschlossen.", null);
        s4.fadeOut = true;
        pr.add(s4);
        return pr;
    }

    /** Schnitt durch die Mauer: die Kamera fährt von der Lücke aus an der Schnittfläche entlang. */
    private static Program section(Terrain t) {
        Program pr = new Program("Der Schnitt durch die Mauer");
        pr.clean = false;
        CameraPath p = new CameraPath();
        p.add(0, 60, -8, -75, -12.7, -24, -20);
        p.then(10, 38, -14, -62, -12.7, -24, -20);
        p.then(10, 8, 22, -60, -12.7, -22, -20);
        p.then(10, -34, 18, 10, -12.7, -20, -20);
        Shot s = new Shot(p, 11.0, 12.0, "Der Schnitt durch die Mauer",
                "Bruchsteinmauerwerk aus Grauwacke, darin zwei Kontrollgänge und ein Grundablass; unten sitzt die Mauer im Fels.", null);
        s.fadeIn = true; s.fadeOut = true;
        s.trigger("section", 0);
        pr.add(s);
        return pr;
    }

    // ------------------------------------------------------------ Ein Tag

    private static Program day(Terrain t) {
        Program pr = new Program("Ein Tag an der Edertalsperre");
        pr.clean = false;
        pr.add(shot(lakeShotMorning(), 6.7, 7.4, "Morgen am Edersee",
                "Kalte Luft über warmem Wasser: Nebel steigt aus dem See und fließt an der Mauer ab.", true, false));
        pr.add(shot(morningLight(), 7.7, 8.5, "Die Sonne kommt über den Kamm",
                "Das erste Licht fällt durch den Dunst im Tal.", false, false));
        pr.add(shot(basinShot(t, 26).path, 10.0, 11.0, "Die Ablässe",
                "Fünf Grundablässe schießen mit rund 17 m/s ins Tosbecken, aus dem Gischt aufsteigt.", false, false));
        pr.add(shot(orbitPath(t, 30, 168), 12.6, 14.0, "Mittag: Rundumblick",
                "48 m hoch, 400 m lang und 6 m breit an der Krone: Die Sperrmauer wurde 1908 bis 1914 gebaut und staut rund 200 Mio. m³.", false, false));
        pr.add(shot(zoomPath(t), 14.5, 15.0, "Aus der Nähe",
                "Bruchstein in Lagen mit Marmor an den Kappen.", false, false));
        pr.add(shot(crestShotPath(t, 28), 17.4, 18.7, "Abend auf der Krone",
                "Die Sonne steht flach, die Lampen auf den Pfosten gehen bald an.", false, false));
        pr.add(shot(nightPath(), 19.4, 22.8, "Nacht am Edersee",
                "Kronenlampen, Fensterlicht und Flutlicht am Tosbecken spiegeln sich im See.", false, true));
        return pr;
    }

    private static Shot shot(CameraPath p, double h0, double h1, String head, String text, boolean fadeIn, boolean fadeOut) {
        Shot s = new Shot(p, h0, h1, head, text, null);
        s.fadeIn = true; s.fadeOut = true;
        if (fadeIn) s.fadeIn = true;
        return s;
    }

    private static CameraPath zoomPath(Terrain t) { return zoomShot(t, 24).path; }
    private static CameraPath crestShotPath(Terrain t, double sec) { return crestShot(t, sec).path; }

    private static CameraPath lakeShotMorning() {
        CameraPath p = new CameraPath();
        p.add(0, -200, 7, 560, -110, 5, 330);
        p.then(11, -120, 6, 330, -60, 6, 110);
        p.then(10, -45, 8, 130, 0, 8, -30);
        return p;
    }

    private static CameraPath morningLight() {
        CameraPath p = new CameraPath();
        p.add(0, -60, -20, -170, 222, 40, -80);
        p.then(18, -30, -22, -175, 222, 38, -60);
        return p;
    }

    private static CameraPath nightPath() {
        CameraPath p = new CameraPath();
        p.add(0, 40, 12, 60, -10, 2, -80);
        p.then(16, 0, 14, 120, 0, 6, -40);
        p.then(16, -60, 22, 220, 40, 4, -30);
        return p;
    }
}
