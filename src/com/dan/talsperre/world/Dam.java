package com.dan.talsperre.world;

import com.dan.talsperre.core.Mat;
import com.dan.talsperre.core.MeshBuilder;
import com.dan.talsperre.core.Terrain;

/**
 * Die Sperrmauer der Edertalsperre als Bauwerk: Gewichtsmauer aus Grauwacke-Bruchstein, im Grundriss
 * ein Kreisbogen (Radius 300 m, Mitte stromab), Krone 6 m breit auf +2,5 m über Stauziel, Fuß 36 m breit
 * auf -45,5 m. Dazu Brüstungen mit Marmorkappen, zwei Torürme, fünf Grundablässe, Tosbecken und das
 * Kraftwerk am Mauerfuß.
 *
 * <p>Koordinaten: Der Bogenmittelpunkt C liegt bei (0, -300). Ein Punkt der Mauer ist durch den Winkel
 * phi (0 = Kronenmitte, positiv nach Osten), den Abstand r von C und die Höhe y gegeben; r = 300 ist die
 * Seeseite, kleinere r liegen stromab. So bleibt jede Fläche ein Streifen entlang des Bogens.
 */
public final class Dam {
    /** Bogenradius der Seeseite. */
    public static final double R = 300;
    /** Mittelpunkt des Bogens (z). */
    public static final double CZ = -300;
    /** Kronenhöhe und Gründungssohle (y = 0 ist Stauziel). */
    public static final double TOP = 2.5, BASE = -45.5;
    /** Breite der Krone. */
    public static final double CREST = 6;
    /** Die Mauer reicht bis zu diesem Winkel (Bogenmaß) nach beiden Seiten in die Hänge. */
    public static final double PHI_MAX = Math.toRadians(50);

    /** Überlaufschwelle (Meter über Stauziel), Mitte und halbe Breite der beiden Überläufe, Wasserspiegel im Tosbecken. */
    public static final double SILL = 0.30, SPILL_S = 36, SPILL_HALF = 4.5, BASIN_Y = -41.9;
    /** Mitte der fünf Grundablässe: Höhe, Abstand der Strahlaustritte von C und Teilung in Metern. */
    public static final double GATE_Y = -37.4, GATE_R = 258.4, GATE_PITCH = 12;

    private Dam() { }

    private static double spillPhi() { return SPILL_S / (R - 3); }
    private static double spillHalf() { return SPILL_HALF / (R - 3); }
    /** Die Winkelbereiche der Mauer ohne die beiden Überläufe. */
    private static double[][] solid(double p0, double p1) {
        double a = spillPhi(), h = spillHalf();
        return new double[][]{{p0, -a - h}, {-a + h, a - h}, {a + h, p1}};
    }

    /** Dicke der Mauer (von der Seeseite aus) in der Höhe y. */
    public static double width(double y) {
        double t = Math.max(0, Math.min(1, (TOP - y) / (TOP - BASE)));
        return CREST + 30 * Math.pow(t, 1.2);
    }

    public static double x(double phi, double r) { return r * Math.sin(phi); }
    public static double z(double phi, double r) { return CZ + r * Math.cos(phi); }

    // ------------------------------------------------------------ Gelände vorbereiten

    /** Ebnet Kraftwerksplatz und Tosbecken; muss vor {@link Terrain#build} laufen. */
    public static void shapeGround(Terrain t) {
        Ruins.shape(t);
        for (double deg = 12; deg <= 23; deg += 3.7) {
            double p = Math.toRadians(deg);
            t.pad(x(p, 249), z(p, 249), 17, FL - 0.4, 20);
        }
        for (double zz = -45; zz >= -78; zz -= 16.5) {
            for (double xx = -24; xx <= 24; xx += 24) t.pad(xx, zz, 21, -46.2, 8);
        }
    }

    // ------------------------------------------------------------ Hilfen

    /** Streifen entlang des Bogens: Linienzug (r, y) im Uhrzeigersinn, damit die Normalen nach außen zeigen. */
    private static void sweep(MeshBuilder mb, double[][] pts, double p0, double p1, double cell, int mat) {
        double rm = 0;
        for (double[] q : pts) rm = Math.max(rm, q[0]);
        int nu = Math.max(1, (int) Math.ceil(rm * Math.abs(p1 - p0) / cell));
        for (int i = 0; i + 1 < pts.length; i++) {
            final double r0 = pts[i][0], y0 = pts[i][1], r1 = pts[i + 1][0], y1 = pts[i + 1][1];
            double len = Math.hypot(r1 - r0, y1 - y0);
            if (len < 1e-6) continue;
            int nv = Math.max(1, (int) Math.ceil(len / cell));
            final double dr = r1 - r0, dy = y1 - y0;
            mb.patch((u, v, p, n) -> {
                double r = r0 + dr * v, y = y0 + dy * v;
                double s = Math.sin(u), c = Math.cos(u);
                p[0] = r * s; p[1] = y; p[2] = CZ + r * c;
                // n = dr*up - dy*e_r
                n[0] = -dy * s; n[1] = dr; n[2] = -dy * c;
            }, p0, p1, nu, 0, 1, nv, mat);
        }
    }

    /** Geschlossener Ring (Balken) entlang des Bogens: rl < rr, yb < yt. */
    private static void ring(MeshBuilder mb, double rl, double rr, double yb, double yt, double p0, double p1, double cell, int mat) {
        sweep(mb, new double[][]{{rl, yt}, {rr, yt}, {rr, yb}, {rl, yb}, {rl, yt}}, p0, p1, cell, mat);
    }

    /** Quader in örtlichen Koordinaten (x tangential, z radial von C aus), danach an den Winkel phi gedreht. */
    private static void block(MeshBuilder mb, double phi, double sOff, double x0, double x1, double y0, double y1, double r0, double r1, int mat) {
        int v0 = mb.vertexCount();
        mb.box(x0, y0, r0, x1, y1, r1, mat, true);
        // sOff: Verschiebung entlang der Tangente in Metern (vor der Drehung)
        mb.transform(v0, phi, 0, 0, CZ);
        if (sOff != 0) shift(mb, v0, phi, sOff);
    }

    private static void shift(MeshBuilder mb, int v0, double phi, double s) {
        // Verschiebung entlang der Tangente (cos, -sin)
        mb.transform(v0, 0, s * Math.cos(phi), 0, -s * Math.sin(phi));
    }

    /** Kappe an einem Ringende: Fächer aus dem Linienzug, Normale nach +/-e_phi. */
    private static void cap(MeshBuilder mb, double phi, double[][] poly, boolean forward, int mat) {
        double s = Math.sin(phi), c = Math.cos(phi);
        double nx = forward ? c : -c, nz = forward ? -s : s;
        int[] ids = new int[poly.length];
        for (int i = 0; i < poly.length; i++) ids[i] = mb.v(poly[i][0] * s, poly[i][1], CZ + poly[i][0] * c, nx, 0, nz);
        for (int i = 1; i + 1 < poly.length; i++) mb.tri(ids[0], ids[i], ids[i + 1], mat);
    }

    // ------------------------------------------------------------ Aufbau

    public static void build(MeshBuilder mb, Terrain t) {
        mb.skyFn = (x, y, z, nx, ny, nz) -> ny > 0.5 ? 1f : (float) (0.78 + 0.22 * Math.max(0, Math.min(1, (y + 46) / 16)));
        double p0 = -PHI_MAX, p1 = PHI_MAX;
        int t0 = mb.triCount();
        mb.riseMode = 1;
        body(mb, p0, p1);
        crest(mb, p0, p1);
        outlets(mb);
        windows(mb);
        mb.riseMode = 0;
        wedge(mb, t0);
        mb.riseMode = 1;
        gateTower(mb, Math.toRadians(-17.6));
        gateTower(mb, Math.toRadians(17.6));
        mb.riseMode = 2; mb.riseThr = -30;
        stillingBasin(mb);
        mb.riseMode = 0;
        cascades(mb);
        mb.riseMode = 2; mb.riseThr = TOP + 24;
        powerHouse(mb);
        mb.riseMode = 0;
        mb.skyFn = null;
    }

    /** Mauerkörper: Seeseite, Luftseite mit Marmorgurt; über der Überlaufschwelle ohne die beiden Überläufe. */
    private static void body(MeshBuilder mb, double p0, double p1) {
        // unterhalb der Schwelle durchgehend
        sweep(mb, new double[][]{{R, SILL}, {R, -12}, {R, BASE}}, p0, p1, 3.0, Mat.STONE);
        int n = 11;
        double[][] face = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double y = BASE + (SILL - BASE) * i / n;
            face[i] = new double[]{R - width(y), y};
        }
        sweep(mb, face, p0, p1, 3.0, Mat.STONE);
        // oberhalb der Schwelle nur zwischen den Überläufen
        for (double[] iv : solid(p0, p1)) {
            sweep(mb, new double[][]{{R, TOP}, {R, SILL}}, iv[0], iv[1], 3.0, Mat.STONE);
            sweep(mb, new double[][]{{R - width(SILL), SILL}, {R - width(1.4), 1.4}, {R - CREST, TOP}}, iv[0], iv[1], 3.0, Mat.STONE);
        }
        // Überläufe: Schwelle aus Marmor, Wangen aus Marmor
        for (int sg = -1; sg <= 1; sg += 2) {
            double a = sg * spillPhi() - spillHalf(), b = sg * spillPhi() + spillHalf();
            sweep(mb, new double[][]{{R - width(SILL), SILL}, {R, SILL}}, a, b, 3.0, Mat.MARBLE);
            double[][] wall = {{R - width(SILL), SILL}, {R, SILL}, {R, TOP}, {R - CREST, TOP}, {R - width(1.4), 1.4}};
            cap(mb, a, wall, false, Mat.MARBLE);
            cap(mb, b, wall, true, Mat.MARBLE);
            // Trägerbalken der Brücke unter der Fahrbahn
            ring(mb, R - CREST, R, TOP - 0.7, TOP, a - 0.004, b + 0.004, 2.0, Mat.CONCRETE);
        }
        // Gurtgesims aus Marmor auf der Luftseite in halber Höhe
        double ya = -19.2, yb = -20.8;
        sweep(mb, new double[][]{{R - width(yb) - 0.2, yb}, {R - width(ya) - 0.2, ya}}, p0, p1, 3.0, Mat.MARBLE);
        sweep(mb, new double[][]{{R - width(ya) - 0.2, ya}, {R - width(ya) + 0.3, ya}}, p0, p1, 3.0, Mat.MARBLE);
        // Seeseite: Marmorband an der Stauziel-Marke (Hochwassermarke), knapp über dem Wasser
        for (double[] iv : solid(p0, p1)) ring(mb, R - 0.1, R + 0.18, 0.55, 1.15, iv[0], iv[1], 3.0, Mat.MARBLE);
    }

    /** Das Wasser, das über die Überläufe läuft: weiße Bahnen auf Schwelle und Luftseite (Gruppe 2, ein- und ausblendbar). */
    private static void cascades(MeshBuilder mb) {
        int keep = mb.group;
        mb.group = 2;
        int n = 11;
        double[][] face = new double[n + 2][];
        face[0] = new double[]{R - 0.2, SILL + 0.06};
        face[1] = new double[]{R - width(SILL) - 0.12, SILL + 0.06};
        for (int i = 1; i <= n; i++) {
            double y = SILL - (SILL - BASE) * i / n;
            face[i + 1] = new double[]{R - width(y) - 0.12, y};
        }
        // von der Schwelle nach unten: Reihenfolge top→bottom, Normalen nach außen (stromab, oben)
        for (int sg = -1; sg <= 1; sg += 2) {
            double a = sg * spillPhi() - spillHalf() * 0.92, b = sg * spillPhi() + spillHalf() * 0.92;
            double[][] rev = new double[face.length][];
            for (int i = 0; i < face.length; i++) rev[i] = face[face.length - 1 - i];
            sweep(mb, rev, a, b, 2.5, Mat.CASCADE);
        }
        mb.group = keep;
    }

    /** Seefläche (Gruppe 3, folgt dem Pegel): Streifen an der Mauer; die Zellen des Geländes ergänzt Valley. */
    public static void lakeStrip(MeshBuilder mb) {
        int keep = mb.group;
        mb.group = 3;
        double me = mb.maxEdge;
        mb.maxEdge = 1e6;
        sweep(mb, new double[][]{{R - 5.5, 0}, {R + 32, 0}}, -PHI_MAX, PHI_MAX, 12.0, Mat.WATER);
        mb.maxEdge = me;
        mb.group = keep;
    }

    /** Krone: Fahrbahn, Brüstungen mit Marmorkappen, Gesimse, Pfosten. */
    private static void crest(MeshBuilder mb, double p0, double p1) {
        // Fahrbahn
        sweep(mb, new double[][]{{R - CREST, TOP}, {R, TOP}}, p0, p1, 3.0, Mat.CONCRETE);
        // Brüstungen
        ring(mb, R - 0.55, R, TOP, TOP + 1.05, p0, p1, 3.0, Mat.STONE);
        ring(mb, R - CREST, R - CREST + 0.55, TOP, TOP + 1.05, p0, p1, 3.0, Mat.STONE);
        ring(mb, R - 0.85, R + 0.15, TOP + 1.05, TOP + 1.25, p0, p1, 3.0, Mat.MARBLE);
        ring(mb, R - CREST - 0.15, R - CREST + 0.85, TOP + 1.05, TOP + 1.25, p0, p1, 3.0, Mat.MARBLE);
        // Kranzgesimse unter der Krone
        ring(mb, R - 0.1, R + 0.5, TOP - 1.0, TOP, p0, p1, 3.0, Mat.MARBLE);
        ring(mb, R - CREST - 0.55, R - CREST + 0.1, TOP - 1.0, TOP, p0, p1, 3.0, Mat.MARBLE);
        // Pfosten mit Marmorkugel alle 12 m auf der Seeseite
        double step = 12 / R;
        for (double p = -Math.toRadians(41); p <= Math.toRadians(41) + 1e-9; p += step) {
            if (Math.abs(Math.abs(p) - Math.toRadians(17.6)) < Math.toRadians(1.6)) continue;
            mb.riseMode = 2; mb.riseThr = TOP;
            block(mb, p, 0, -0.32, 0.32, TOP + 1.25, TOP + 2.15, R - 0.82, R - 0.18, Mat.STONE);
            int v0 = mb.vertexCount();
            mb.ellipsoid(0, TOP + 2.55, R - 0.5, 0.34, 0.34, 0.34, 8, 6, Mat.MARBLE);
            mb.transform(v0, p, 0, 0, CZ);
            mb.riseMode = 1;
        }
    }

    /** Torturm über der Krone: zwei Pylone, Sturz, Turmschaft, Marmorgesims, Schieferhaube. */
    private static void gateTower(MeshBuilder mb, double phi) {
        double hw = 3.8;          // halbe Breite tangential
        double rl = R - CREST - 0.4, rr = R + 0.4;
        double yTop = 19.5;
        // Pylone links und rechts der Durchfahrt (Durchfahrt 3,8 m breit, 4,6 m hoch)
        block(mb, phi, 0, -hw, hw, TOP, yTop - 0.0 * 1, rl, rl + 1.3, Mat.STONE);
        block(mb, phi, 0, -hw, hw, TOP, yTop, rr - 1.3, rr, Mat.STONE);
        // Sturz und Schaft über der Durchfahrt
        block(mb, phi, 0, -hw, hw, TOP + 4.6, yTop, rl + 1.3, rr - 1.3, Mat.STONE);
        // seitliche Wangen der Durchfahrt schließen (tangential): Pylonenseiten sind Teil der Quader.
        // Marmor: Bogenstein über der Durchfahrt, Gesimse
        block(mb, phi, 0, -hw - 0.3, hw + 0.3, yTop, yTop + 0.7, rl - 0.4, rr + 0.4, Mat.MARBLE);
        block(mb, phi, 0, -hw - 0.3, hw + 0.3, TOP + 4.6, TOP + 5.0, rl - 0.1, rr + 0.1, Mat.MARBLE);
        block(mb, phi, 0, -hw - 0.2, hw + 0.2, 9.6, 10.0, rl - 0.2, rr + 0.2, Mat.MARBLE);
        // Fenster auf beiden Seiten
        for (double rf : new double[]{rl, rr}) {
            double sgn = rf == rl ? -1 : 1;
            block(mb, phi, 0, -0.8, 0.8, 12.2, 16.2, rf + (sgn < 0 ? -0.12 : -0.05), rf + (sgn < 0 ? 0.05 : 0.12), Mat.GLASS);
            block(mb, phi, 0, -1.1, 1.1, 16.2, 16.55, rf - (sgn < 0 ? 0.25 : 0.05), rf + (sgn < 0 ? 0.05 : 0.25), Mat.MARBLE);
            block(mb, phi, 0, -1.1, 1.1, 11.85, 12.2, rf - (sgn < 0 ? 0.25 : 0.05), rf + (sgn < 0 ? 0.05 : 0.25), Mat.MARBLE);
        }
        // Gesims- und Schieferhaube: vierseitiger Pyramidenstumpf, um 45 Grad gedreht
        int v0 = mb.vertexCount();
        double rc = (rl + rr) / 2;
        mb.cylinder(0, rc, yTop + 0.7, yTop + 9.5, 5.6, 0.25, 4, Mat.SLATE, false);
        // Pyramide ausrichten: Ecken auf die Achsen des Turms, daher um 45 Grad drehen (um die Achse)
        rotateAround(mb, v0, 0, rc, Math.PI / 4);
        mb.transform(v0, phi, 0, 0, CZ);
        // Turmspitze und Kugel aus Marmor
        v0 = mb.vertexCount();
        mb.ellipsoid(0, yTop + 9.9, rc, 0.45, 0.45, 0.45, 8, 6, Mat.MARBLE);
        mb.transform(v0, phi, 0, 0, CZ);
        // Wappentafel aus Marmor über dem Bogen (Seeseite und Luftseite)
        block(mb, phi, 0, -1.4, 1.4, 7.0, 9.0, rr - 0.1, rr + 0.15, Mat.MARBLE);
        block(mb, phi, 0, -1.4, 1.4, 7.0, 9.0, rl - 0.15, rl + 0.1, Mat.MARBLE);
    }

    /** Dreht die Ecken ab v0 um die senkrechte Achse (cx, cz). */
    private static void rotateAround(MeshBuilder mb, int v0, double cx, double cz, double ang) {
        mb.transform(v0, 0, -cx, 0, -cz);
        mb.transform(v0, ang, 0, 0, 0);
        mb.transform(v0, 0, cx, 0, cz);
    }

    /** Fünf Grundablässe am Mauerfuß (hier die Bauwerke; die Strahlen kommen in Phase 4). */
    private static void outlets(MeshBuilder mb) {
        for (int k = -2; k <= 2; k++) {
            double s = k * 12.0;
            double phi = s / 264.0;
            double rf = 258.4;   // Vorderkante des Auslassbauwerks
            block(mb, phi, 0, -3.3, 3.3, BASE - 0.5, -30.6, rf, 277, Mat.CONCRETE);
            block(mb, phi, 0, -3.6, 3.6, -30.6, -30.25, rf - 0.3, 277, Mat.MARBLE);
            // Marmorrahmen um die Öffnung (3,4 x 3,4 m)
            double cy = -37.4, hw = 1.7, hh = 1.7, th = 0.5;
            block(mb, phi, 0, -hw - th, -hw, cy - hh - th, cy + hh + th, rf - 0.35, rf + 0.02, Mat.MARBLE);
            block(mb, phi, 0, hw, hw + th, cy - hh - th, cy + hh + th, rf - 0.35, rf + 0.02, Mat.MARBLE);
            block(mb, phi, 0, -hw, hw, cy + hh, cy + hh + th, rf - 0.35, rf + 0.02, Mat.MARBLE);
            block(mb, phi, 0, -hw, hw, cy - hh - th, cy - hh, rf - 0.35, rf + 0.02, Mat.MARBLE);
            // Öffnung mit eisernem Schieber, leicht zurückgesetzt
            block(mb, phi, 0, -hw, hw, cy - hh, cy + hh, rf - 0.02, rf + 0.12, Mat.METAL);
            // Schieberspindel und Handrad oben auf dem Block
            block(mb, phi, 0, -0.12, 0.12, -30.25, -28.4, rf + 2.0, rf + 2.24, Mat.METAL);
            block(mb, phi, 0, -0.55, 0.55, -28.4, -28.28, rf + 1.7, rf + 2.54, Mat.METAL);
        }
    }

    /** Kontrollgang-Fenster in zwei Reihen auf der Luftseite. */
    private static void windows(MeshBuilder mb) {
        double[] rows = {-8, -26};
        for (double yc : rows) {
            double rd = R - width(yc);
            for (double s = -170; s <= 170; s += 17) {
                if (Math.abs(s) < 33 && yc < -20) continue;  // Platz für die Ablassbauwerke
                double phi = s / rd;
                mb.riseMode = 2; mb.riseThr = yc + 0.9;
                block(mb, phi, 0, -0.45, 0.45, yc - 0.6, yc + 0.6, rd - 0.45, rd + 0.6, Mat.GLASS);
                block(mb, phi, 0, -0.65, 0.65, yc + 0.6, yc + 0.85, rd - 0.5, rd + 0.6, Mat.MARBLE);
            }
        }
    }

    /** Tosbecken unter den Ablässen: Betonplatte, Seitenwände, Endschwelle. */
    private static void stillingBasin(MeshBuilder mb) {
        double zA = -30, zE = -96;
        mb.box(-39.5, -46.5, zE, 39.5, -45.0, zA, Mat.CONCRETE, false);
        mb.box(-42.5, -46.5, zE, -39.5, -36.2, zA, Mat.CONCRETE, false);
        mb.box(39.5, -46.5, zE, 42.5, -36.2, zA, Mat.CONCRETE, false);
        // Marmorkappe auf den Wänden
        mb.box(-42.8, -36.2, zE, -39.2, -35.9, zA, Mat.MARBLE, false);
        mb.box(39.2, -36.2, zE, 42.8, -35.9, zA, Mat.MARBLE, false);
        // Wasser im Becken: ruhiger Spiegel, den der Bildrechner mit Brausen und Schaum belebt
        double me = mb.maxEdge;
        mb.maxEdge = 1e6;
        mb.rectH(-39.5, zE, 39.5, zA, BASIN_Y, true, Mat.WATER);
        mb.maxEdge = me;
        // Endschwelle mit Zähnen
        mb.box(-42.5, -46.5, zE - 3.5, 42.5, -42.6, zE, Mat.CONCRETE, false);
        for (double x = -38; x <= 38; x += 7) mb.box(x - 1.4, -45.0, zE + 0.0, x + 1.4, -43.4, zE + 7, Mat.CONCRETE, false);
    }

    /** Punktlichter der Mauer: Kronenlampen, Torturmfenster, Kraftwerk, Flutlicht am Tosbecken. */
    public static com.dan.talsperre.effects.Lamps lamps() {
        com.dan.talsperre.effects.Lamps L = new com.dan.talsperre.effects.Lamps();
        // Kronenlampen auf jedem zweiten Pfosten
        double step = 24 / R;
        for (double p = -Math.toRadians(41); p <= Math.toRadians(41) + 1e-9; p += step) {
            if (Math.abs(Math.abs(p) - Math.toRadians(17.6)) < Math.toRadians(2.2)) continue;
            L.add(x(p, R - 0.5), TOP + 3.3, z(p, R - 0.5), 1.0, 0.68, 0.36, 26);
        }
        // Torturmfenster beiderseits
        for (double sg : new double[]{-1, 1}) {
            double phi = sg * Math.toRadians(17.6);
            L.add(x(phi, R - CREST - 1.6), 14.2, z(phi, R - CREST - 1.6), 1.0, 0.7, 0.38, 22);
            L.add(x(phi, R + 1.4), 14.2, z(phi, R + 1.4), 1.0, 0.7, 0.38, 22);
        }
        // Kraftwerk: Licht vor der Talseite und vor dem Tor
        double a0 = Math.toRadians(11.6), a1 = Math.toRadians(22.8), rl = 239;
        for (double a = a0 + 0.03; a < a1 - 0.02; a += 12.4 / 249) L.add(x(a, rl - 2.0), FL + 5.0, z(a, rl - 2.0), 1.0, 0.72, 0.4, 24);
        // Flutlicht auf den Wänden des Tosbeckens, kühles Weiß
        for (double sx : new double[]{-41, 41})
            for (double zz : new double[]{-42, -62, -82}) L.add(sx, -32.8, zz, 0.72, 0.88, 1.12, 48);
        L.finish();
        return L;
    }

    /** Sohle des Kraftwerks. */
    public static final double FL = -38.6;

    /** Kraftwerk am Fuß der Mauer: gebogene Maschinenhalle mit Schieferdach, Fensterreihen, Turm. */
    private static void powerHouse(MeshBuilder mb) {
        double a0 = Math.toRadians(11.6), a1 = Math.toRadians(22.8);
        double rl = 239, rr = 259, yb = FL - 1.4, yw = FL + 11.4;
        ring(mb, rl, rr, yb, yw, a0, a1, 3.0, Mat.STONE);
        ring(mb, rl - 0.3, rr + 0.3, yb, FL + 0.4, a0, a1, 3.0, Mat.CONCRETE);
        ring(mb, rl - 0.5, rr + 0.5, yw - 0.35, yw, a0, a1, 3.0, Mat.MARBLE);
        double rm = (rl + rr) / 2;
        double[][] roof = {{rl - 1.3, yw}, {rm, yw + 6.2}, {rr + 1.3, yw}};
        sweep(mb, roof, a0, a1, 3.0, Mat.SLATE);
        sweep(mb, new double[][]{{rr + 1.3, yw}, {rl - 1.3, yw}}, a0, a1, 3.0, Mat.CONCRETE);
        double[][] gable = {{rl, yw}, {rr, yw}, {rm, yw + 5.4}};
        cap(mb, a0, gable, false, Mat.STONE);
        cap(mb, a1, gable, true, Mat.STONE);
        double[][] end = {{rl, yb}, {rr, yb}, {rr, yw}, {rl, yw}};
        cap(mb, a0, end, false, Mat.STONE);
        cap(mb, a1, end, true, Mat.STONE);
        for (double a = a0 + 0.012; a < a1 - 0.012; a += 6.2 / rm) {
            for (int side = 0; side < 2; side++) {
                double rf = side == 0 ? rl : rr;
                double o = side == 0 ? -1 : 1;
                double f0 = o < 0 ? rf - 0.2 : rf - 0.05, f1 = o < 0 ? rf + 0.05 : rf + 0.2;
                block(mb, a, 0, -0.95, 0.95, FL + 1.6, FL + 7.8, f0, f1, Mat.GLASS);
                double m0 = o < 0 ? rf - 0.4 : rf - 0.05, m1 = o < 0 ? rf + 0.05 : rf + 0.4;
                block(mb, a, 0, -1.25, 1.25, FL + 7.8, FL + 8.2, m0, m1, Mat.MARBLE);
                block(mb, a, 0, -1.25, 1.25, FL + 1.2, FL + 1.6, m0, m1, Mat.MARBLE);
            }
        }
        // Uhrenturm über der Mitte
        double am = (a0 + a1) / 2;
        block(mb, am, 0, -3.2, 3.2, yw, yw + 14.5, rm - 3.2, rm + 3.2, Mat.STONE);
        block(mb, am, 0, -3.6, 3.6, yw + 14.5, yw + 14.9, rm - 3.6, rm + 3.6, Mat.MARBLE);
        block(mb, am, 0, -1.2, 1.2, yw + 8, yw + 12, rm - 3.3, rm - 3.1, Mat.GLASS);
        block(mb, am, 0, -1.2, 1.2, yw + 8, yw + 12, rm + 3.1, rm + 3.3, Mat.GLASS);
        int v0 = mb.vertexCount();
        mb.cylinder(0, rm, yw + 14.9, yw + 22.5, 4.7, 0.2, 4, Mat.SLATE, false);
        rotateAround(mb, v0, 0, rm, Math.PI / 4);
        mb.transform(v0, am, 0, 0, CZ);
        // Haupttor auf der Talseite
        block(mb, am + 0.06, 0, -1.6, 1.6, FL + 0.4, FL + 5.4, rl - 0.3, rl + 0.1, Mat.METAL);
        block(mb, am + 0.06, 0, -2.2, 2.2, FL + 5.4, FL + 5.8, rl - 0.5, rl + 0.1, Mat.MARBLE);
    }

    // ------------------------------------------------------------ Mauerschnitt und Bresche

    /** Der herausnehmbare Keil: von WPA (durch die Achse des zweiten Grundablasses) bis WPB, geteilt bei y = WY. */
    public static final double WPA = -12.0 / 264.0, WPB = Math.toRadians(11), WY = -20;
    /** Betriebsarten der Mauer: normal, Schnitt, Bresche. */
    public static final int MODE_NORMAL = 0, MODE_SECTION = 1, MODE_BREACH = 2;

    /** Welche Gruppen im jeweiligen Zustand nicht gezeichnet werden. */
    public static int hideMask(int mode) {
        switch (mode) {
            case MODE_SECTION: return (1 << 4) | (1 << 5) | (1 << 2) | (1 << 7);
            case MODE_BREACH: return (1 << 4) | (1 << 6);
            default: return (1 << 6) | (1 << 7);
        }
    }

    /** Punkt der Schnittfläche bei Winkel phi, Abstand r, Höhe y. */
    public static double[] sectionPoint(double phi, double r, double y) { return pt(phi, r, y, 0); }

    private static double[] pt(double phi, double r, double y, double s) {
        return new double[]{r * Math.sin(phi) + s * Math.cos(phi), y, CZ + r * Math.cos(phi) - s * Math.sin(phi)};
    }

    /** Teilt alles ab t0 an den Ebenen des Keils, ordnet die Stücke den Gruppen 4/5 zu und baut Schnitt- und Bruchflächen. */
    private static void wedge(MeshBuilder mb, int t0) {
        for (double a : new double[]{WPA, WPB}) {
            mb.splitPlane(t0, Math.cos(a), 0, -Math.sin(a), 300 * Math.sin(a));
        }
        mb.splitPlane(t0, 0, 1, 0, WY);
        mb.regroup(t0, (cx, cy, cz, old) -> {
            double ph = Math.atan2(cx, cz - CZ);
            if (ph > WPA && ph < WPB) return cy >= WY ? 4 : 5;
            return old;
        });
        int keep = mb.group;
        double me = mb.maxEdge;
        mb.maxEdge = 1e6;
        mb.group = 6;
        face(mb, WPA, 1, true, BASE - 10, true);
        face(mb, WPB, -1, false, BASE - 10, true);
        tubes(mb, WPA, 1, true);
        tubes(mb, WPB, -1, false);
        mb.group = 7;
        face(mb, WPA, 1, false, WY, false);
        face(mb, WPB, -1, false, WY, false);
        mb.maxEdge = me;
        breachFloor(mb);
        debris(mb);
        mb.group = keep;
    }

    private static double rlAt(double y, boolean outlet) {
        double rl = R - width(Math.min(y, TOP));
        if (outlet) {
            if (y < -30.6) rl = Math.min(rl, 258.4);
            else if (y < -30.25) rl = Math.min(rl, 258.1);
        }
        return rl;
    }

    /** Schnittfläche bei Winkel phi; sg = +1: Normale +e_phi (Mauer liegt bei kleineren Winkeln). */
    private static void face(MeshBuilder mb, double phi, int sg, boolean outlet, double yLow, boolean rock) {
        double nx = sg * Math.cos(phi), nz = -sg * Math.sin(phi);
        double yb = rock ? BASE - 0.5 : yLow;
        // Hohlräume: {y0, y1, r0, r1}
        java.util.List<double[]> voids = new java.util.ArrayList<>();
        for (double yc : new double[]{-8, -26}) {
            double rd = R - width(yc);
            voids.add(new double[]{yc - 1.3, yc + 1.3, rd + 0.6, rd + 3.4});
        }
        if (outlet) voids.add(new double[]{GATE_Y - 1.7, GATE_Y + 1.7, 258.4, R});
        java.util.TreeSet<Double> ys = new java.util.TreeSet<>();
        for (double y = yb; y < TOP; y += 1.2) ys.add(y);
        ys.add(yb); ys.add(TOP); ys.add(BASE); ys.add(-30.6); ys.add(-30.25);
        for (double[] v : voids) { ys.add(v[0]); ys.add(v[1]); }
        java.util.List<Double> yl = new java.util.ArrayList<>();
        for (double y : ys) if (y >= yb - 1e-9 && y <= TOP + 1e-9 && (yl.isEmpty() || y - yl.get(yl.size() - 1) > 0.02)) yl.add(y);
        for (int i = 0; i + 1 < yl.size(); i++) {
            double y0 = yl.get(i), y1 = yl.get(i + 1), ym = (y0 + y1) / 2;
            double l0 = rlAt(y0 + 1e-6, outlet), l1 = rlAt(y1 - 1e-6, outlet);
            double[] vd = null;
            for (double[] v : voids) if (ym > v[0] && ym < v[1]) vd = v;
            if (vd == null) {
                mb.quad(pt(phi, l0, y0, 0), pt(phi, R, y0, 0), pt(phi, R, y1, 0), pt(phi, l1, y1, 0), nx, 0, nz, Mat.CUT);
            } else {
                if (vd[2] > l0 + 1e-6) mb.quad(pt(phi, l0, y0, 0), pt(phi, vd[2], y0, 0), pt(phi, vd[2], y1, 0), pt(phi, l1, y1, 0), nx, 0, nz, Mat.CUT);
                if (vd[3] < R - 1e-6) mb.quad(pt(phi, vd[3], y0, 0), pt(phi, R, y0, 0), pt(phi, R, y1, 0), pt(phi, vd[3], y1, 0), nx, 0, nz, Mat.CUT);
            }
        }
        // Brüstungen und Marmorkappen
        mb.quad(pt(phi, R - 0.55, TOP, 0), pt(phi, R, TOP, 0), pt(phi, R, TOP + 1.05, 0), pt(phi, R - 0.55, TOP + 1.05, 0), nx, 0, nz, Mat.STONE);
        mb.quad(pt(phi, R - CREST, TOP, 0), pt(phi, R - CREST + 0.55, TOP, 0), pt(phi, R - CREST + 0.55, TOP + 1.05, 0), pt(phi, R - CREST, TOP + 1.05, 0), nx, 0, nz, Mat.STONE);
        mb.quad(pt(phi, R - 0.85, TOP + 1.05, 0), pt(phi, R + 0.15, TOP + 1.05, 0), pt(phi, R + 0.15, TOP + 1.25, 0), pt(phi, R - 0.85, TOP + 1.25, 0), nx, 0, nz, Mat.MARBLE);
        mb.quad(pt(phi, R - CREST - 0.15, TOP + 1.05, 0), pt(phi, R - CREST + 0.85, TOP + 1.05, 0), pt(phi, R - CREST + 0.85, TOP + 1.25, 0), pt(phi, R - CREST - 0.15, TOP + 1.25, 0), nx, 0, nz, Mat.MARBLE);
        if (rock) {
            double rl = rlAt(BASE, outlet) - 14;
            mb.quad(pt(phi, rl, BASE - 10, 0), pt(phi, R + 14, BASE - 10, 0), pt(phi, R + 14, yb, 0), pt(phi, rl, yb, 0), nx, 0, nz, Mat.ROCK);
        }
    }

    /** Innenwände der beiden Kontrollgänge (und am Westschnitt des Grundablass-Rohrs), die man durch die Öffnungen sieht. */
    private static void tubes(MeshBuilder mb, double phi, int sg, boolean outlet) {
        double len = 26;
        for (double yc : new double[]{-8, -26}) {
            double rd = R - width(yc), a = rd + 0.6, b = rd + 3.4, yb = yc - 1.3, yt = yc + 1.3;
            double pe = phi - sg * len / ((a + b) / 2);
            double p0 = phi, p1 = pe;
            sweep(mb, new double[][]{{a, yt}, {a, yb}, {b, yb}, {b, yt}, {a, yt}}, p0, p1, 3.0, Mat.CONCRETE);
            cap(mb, pe, new double[][]{{a, yb}, {b, yb}, {b, yt}, {a, yt}}, sg > 0, Mat.CONCRETE);
        }
        if (outlet) {
            double hw = 1.7, y0 = GATE_Y - hw, y1 = GATE_Y + hw;
            mb.quad(pt(phi, 258.4, y0, -hw), pt(phi, R, y0, -hw), pt(phi, R, y0, 0), pt(phi, 258.4, y0, 0), 0, 1, 0, Mat.CONCRETE);
            mb.quad(pt(phi, 258.4, y1, -hw), pt(phi, R, y1, -hw), pt(phi, R, y1, 0), pt(phi, 258.4, y1, 0), 0, -1, 0, Mat.CONCRETE);
            mb.quad(pt(phi, 258.4, y0, -hw), pt(phi, R, y0, -hw), pt(phi, R, y1, -hw), pt(phi, 258.4, y1, -hw), Math.cos(phi), 0, -Math.sin(phi), Mat.CONCRETE);
        }
    }

    /** Unregelmäßige Oberkante der Restmauer bei y = WY (Gruppe 7: nur bei der Bresche zu sehen). */
    private static void breachFloor(MeshBuilder mb) {
        double rl = R - width(WY);
        mb.maxEdge = 2.5;
        mb.patch((u, v, p, n) -> {
            double r = rl + (R - rl) * v;
            double edge = Math.sin(Math.PI * Math.min(1, Math.max(0, (u - WPA) / (WPB - WPA)))) * Math.sin(Math.PI * v);
            double rag = edge * (0.8 + 0.9 * Math.sin(u * 311 + v * 7.3) * Math.cos(u * 173 - v * 11.1) + 0.5 * Math.sin(u * 997 + v * 31));
            p[0] = r * Math.sin(u); p[1] = WY + Math.max(0, rag); p[2] = CZ + r * Math.cos(u);
            n[0] = 0; n[1] = 1; n[2] = 0;
        }, WPA, WPB, Math.max(1, (int) Math.ceil(R * (WPB - WPA) / 3.0)), 0, 1, Math.max(1, (int) Math.ceil((R - rl) / 3.0)), Mat.CONCRETE);
    }

    /** Trümmer der Mauer: Betonbrocken auf der Bruchkante und im Tosbecken. */
    private static void debris(MeshBuilder mb) {
        java.util.Random rnd = new java.util.Random(1943);
        for (int i = 0; i < 16; i++) {
            double sx = 1.5 + rnd.nextDouble() * 4, sy = 1 + rnd.nextDouble() * 2.5, sz = 1.5 + rnd.nextDouble() * 4;
            double x, z, y0;
            if (i < 4) {
                double ph = WPA + (WPB - WPA) * (0.15 + 0.7 * rnd.nextDouble()), r = R - 4 - rnd.nextDouble() * 10;
                x = r * Math.sin(ph); z = CZ + r * Math.cos(ph); y0 = WY + 0.6;
            } else {
                x = -36 + rnd.nextDouble() * 72; z = -50 - rnd.nextDouble() * 45; y0 = -46.4;
            }
            int v0 = mb.vertexCount();
            mb.box(-sx / 2, 0, -sz / 2, sx / 2, sy, sz / 2, Mat.CONCRETE, true);
            mb.transform(v0, rnd.nextDouble() * Math.PI, x, y0, z);
        }
    }

    // ------------------------------------------------------------ Baustelle (nur im Bauzeitraffer sichtbar)

    /** Gerüste an der Luftseite (steigen mit der Mauer) und der Kabelkran über dem Tal. */
    public static void construction(MeshBuilder mb, Terrain t) {
        double me = mb.maxEdge;
        mb.riseMode = 6;
        for (double y = BASE + 5; y <= TOP; y += 6) {
            double rd = R - width(y);
            ring(mb, rd - 2.8, rd - 1.0, y - 0.12, y + 0.12, -Math.toRadians(44), Math.toRadians(44), 4.0, Mat.BOARD);
            for (double a = -Math.toRadians(44); a <= Math.toRadians(44); a += 10.0 / rd) {
                block(mb, a, 0, -0.1, 0.1, y, y + 6.0, rd - 1.1, rd - 0.9, Mat.BOARD);
            }
        }
        mb.riseMode = 5;
        for (int side = 0; side < 2; side++) {
            double x = side == 0 ? -172 : 172, z = -48;
            double g = t.sample(x, z);
            mb.box(x - 1.8, g - 2, z - 1.8, x + 1.8, 77, z + 1.8, Mat.METAL, false);
            mb.box(x - 2.4, 74, z - 0.5, x + 2.4, 77.4, z + 0.5, Mat.METAL, false);
        }
        mb.maxEdge = 1e6;
        int n = 28;
        for (int i = 0; i < n; i++) {
            double[] a = com.dan.talsperre.extras.Extras.cable(i / (double) n), b = com.dan.talsperre.extras.Extras.cable((i + 1) / (double) n);
            mb.quad(new double[]{a[0], a[1], a[2] - 0.4}, new double[]{b[0], b[1], b[2] - 0.4}, new double[]{b[0], b[1], b[2] + 0.4}, new double[]{a[0], a[1], a[2] + 0.4}, 0, 1, 0, Mat.METAL);
            mb.quad(new double[]{a[0], a[1] - 0.2, a[2]}, new double[]{b[0], b[1] - 0.2, b[2]}, new double[]{b[0], b[1] + 0.2, b[2]}, new double[]{a[0], a[1] + 0.2, a[2]}, 0, 0, 1, Mat.METAL);
        }
        mb.maxEdge = me;
        mb.riseMode = 0;
    }
}
