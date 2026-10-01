package com.dan.talsperre.core;

/**
 * Prozedurale Materialien: Grundfarbe (linear) aus Weltposition und Normale, dazu Glanz.
 * Feine Zeichnung wird nur gerechnet, solange ein Pixel kleiner ist als sie; in der Ferne
 * gehen die Flächen in einen gemittelten Farbton über, damit nichts flimmert. Keine Bilddateien.
 */
public final class Materials {
    public static final float[] SPEC = new float[Mat.COUNT];
    public static final float[] SHIN = new float[Mat.COUNT];
    /** Durchscheinen im Gegenlicht (Nadeln). */
    public static final float[] TRANS = new float[Mat.COUNT];

    static {
        SPEC[Mat.SINTER] = 0.04f; SHIN[Mat.SINTER] = 24;
        SPEC[Mat.MARK] = 0.06f; SHIN[Mat.MARK] = 30;
        SPEC[Mat.ROCK] = 0.02f; SHIN[Mat.ROCK] = 16;
        SPEC[Mat.STONE] = 0.03f; SHIN[Mat.STONE] = 14;
        SPEC[Mat.MARBLE] = 0.28f; SHIN[Mat.MARBLE] = 90;
        SPEC[Mat.SLATE] = 0.12f; SHIN[Mat.SLATE] = 40;
        SPEC[Mat.METAL] = 0.35f; SHIN[Mat.METAL] = 60;
        SPEC[Mat.GLASS] = 0.6f; SHIN[Mat.GLASS] = 140;
        SPEC[Mat.CUT] = 0.01f; SHIN[Mat.CUT] = 6; SPEC[Mat.CONCRETE] = 0.02f; SHIN[Mat.CONCRETE] = 10;
        SPEC[Mat.CASCADE] = 0.35f; SHIN[Mat.CASCADE] = 50;
        SPEC[Mat.RIM] = 0.10f; SHIN[Mat.RIM] = 40;
        SPEC[Mat.CONE] = 0.05f; SHIN[Mat.CONE] = 30;
        SPEC[Mat.NEEDLES] = 0.03f; SHIN[Mat.NEEDLES] = 14;
        TRANS[Mat.NEEDLES] = 0.18f;
        SPEC[Mat.SPRUCE] = 0.03f; SHIN[Mat.SPRUCE] = 14;
        TRANS[Mat.SPRUCE] = 0.12f;
        SPEC[Mat.LEAVES] = 0.05f; SHIN[Mat.LEAVES] = 20;
        TRANS[Mat.LEAVES] = 0.35f;
        SPEC[Mat.ASPHALT] = 0.06f; SHIN[Mat.ASPHALT] = 18;
        SPEC[Mat.PAINT] = 0.04f; SHIN[Mat.PAINT] = 12;
    }

    /**
     * Farben des Espenlaubs nach der Jahreszeit (linear, 3 je Ton, Töne von 0 bis 1 gleichmäßig
     * verteilt); gesetzt von Grove. Der Ton kommt aus dem Ort: ganze Haine
     * (in der Natur ein Klon aus einer Wurzel) färben sich gemeinsam, darin jedes Büschel etwas anders.
     */
    public static volatile float[] leafLut = {0.08f, 0.22f, 0.04f, 0.08f, 0.22f, 0.04f};

    /** Das Gelände liefert Bodenkarte und Quellen; gesetzt, sobald die Szene steht. */
    public static volatile Terrain terrain;

    /** Laufzeit in Sekunden für bewegte Stoffe (Überlaufwasser); der Bildrechner setzt sie je Bild. */
    public static volatile float time;

    private Materials() { }

    /** Zufall 0..1 aus drei ganzen Zahlen. */
    private static float rnd(int a, int b, int c) {
        return (Noise.hash(a, b, c) & 0xFFFF) / 65535f;
    }

    private static float smooth(float a, float b, float x) {
        float t = (x - a) / (b - a);
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return t * t * (3 - 2 * t);
    }

    /** Normale mit Rauschen verkippen (Perlen, Nadelbüschel). */
    private static void bump(float[] n, float x, float y, float z, float amp, float freq) {
        float a = Noise.tex(x * freq + z * 0.7f, y * freq) - 0.5f, b = Noise.tex(z * freq + 17.3f, y * freq + x * 0.6f) - 0.5f;
        float c = Noise.tex(y * freq + 5.5f, x * freq + 9.1f) - 0.5f;
        n[0] += a * amp * 2; n[1] += c * amp * 2; n[2] += b * amp * 2;
        float l = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        n[0] /= l; n[1] /= l; n[2] /= l;
    }

    /**
     * Grundfarbe am Punkt nach o, Normale n darf verkippt werden; liefert die Verschattung 0..1
     * für das Umgebungslicht. foot = Pixelgröße in Metern; gm ist ein Hilfspuffer (5 Werte).
     */
    public static float surface(int m, float x, float y, float z, float[] n, float foot, float[] gm, float[] o) {
        float fineVis = 1 - smooth(0.05f, 0.4f, foot);
        switch (m) {
            case Mat.TERRAIN: {
                Terrain t = terrain;
                if (t == null) { o[0] = 0.2f; o[1] = 0.18f; o[2] = 0.1f; return 1; }
                t.albedo(x, y, z, n[0], n[1], n[2], foot, gm, o);
                return 1;
            }
            case Mat.SINTER: {
                float a = Noise.tex(x * 0.8f + z * 0.3f, y * 1.4f), b = Noise.tex(x * 0.13f, z * 0.13f + y * 0.2f);
                float k = 0.82f + 0.3f * (a - 0.5f) * (1 - smooth(0.1f, 0.6f, foot)) + 0.12f * (b - 0.5f);
                o[0] = 0.53f * k; o[1] = 0.51f * k; o[2] = 0.46f * k;
                return 1;
            }
            case Mat.RIM: {
                // Geyserit-Perlen: rundliche Knollen, zum Wasser hin heller und feucht
                if (fineVis > 0) bump(n, x, y, z, 0.35f * fineVis, 3.2f);
                float a = Noise.tex(x * 3.1f + y, z * 3.1f);
                float k = 0.85f + 0.25f * (a - 0.5f) * fineVis;
                o[0] = 0.56f * k; o[1] = 0.54f * k; o[2] = 0.50f * k;
                tint(x, z, o);
                return 0.85f + 0.15f * a;
            }
            case Mat.CONE: {
                // Sinterkegel: waagrechte Wachstumslagen, perlige Oberfläche, braune Läufe vom Abfluss
                if (fineVis > 0) bump(n, x, y, z, 0.28f * fineVis, 2.1f);
                float band = Noise.tex(y * 2.6f + Noise.tex(x * 0.4f, z * 0.4f) * 1.5f, 0.37f);
                float streak = smooth(0.55f, 0.75f, Noise.tex(x * 0.9f + z * 0.9f, y * 0.12f + 3.3f));
                float k = 0.78f + 0.3f * (band - 0.5f);
                float r = 0.54f * k, g = 0.51f * k, b = 0.46f * k;
                float low = 1 - smooth(0.2f, 2.5f, y - baseY(x, z));
                float br = streak * (0.35f + 0.65f * low);
                r += (0.36f - r) * br; g += (0.22f - g) * br; b += (0.12f - b) * br;
                o[0] = r; o[1] = g; o[2] = b;
                tint(x, z, o);
                float cav = Noise.tex(x * 1.3f + 7, y * 1.3f + z * 0.2f);
                return 0.75f + 0.25f * cav;
            }
            case Mat.WOOD: {
                float gr = Noise.tex(x * 0.4f + z * 0.4f, y * 6f);
                float k = 0.8f + 0.35f * (gr - 0.5f);
                o[0] = 0.20f * k; o[1] = 0.13f * k; o[2] = 0.075f * k;
                return 1;
            }
            case Mat.BOARD: {
                // verwitterte Bretter: graubraun, Maserung, dunkle Fugen quer (alle 0,15 m) nur nah
                float gr = Noise.tex(x * 0.9f + z * 0.2f, z * 0.9f + y * 5f);
                float k = 0.8f + 0.3f * (gr - 0.5f);
                float r = 0.24f * k, g = 0.19f * k, b = 0.14f * k;
                if (fineVis > 0 && n[1] > 0.7f) {
                    float u = (x * 0.7071f + z * 0.7071f) / 0.15f;
                    float f = u - (float) Math.floor(u);
                    if (f < 0.1f) { float d = 1 - 0.55f * fineVis; r *= d; g *= d; b *= d; }
                }
                o[0] = r; o[1] = g; o[2] = b;
                // festgetretener Schnee auf den Brettern (nicht an heißen Stellen)
                if (Thermal.snow > 0.01f && n[1] > 0.7f) {
                    float c = Thermal.snow * 0.8f * (0.7f + 0.3f * gr) * warmFree(x, z);
                    o[0] += (0.66f - o[0]) * c; o[1] += (0.68f - o[1]) * c; o[2] += (0.72f - o[2]) * c;
                }
                return 1;
            }
            case Mat.MARK: {
                float band = y - (float) Math.floor(y / 0.3f) * 0.3f;
                if (band < 0.08f) { o[0] = 0.75f; o[1] = 0.74f; o[2] = 0.70f; }
                else { o[0] = 0.80f; o[1] = 0.17f; o[2] = 0.025f; }
                return 1;
            }
            case Mat.ROCK: {
                float a = Noise.tex(x * 0.3f + y * 0.2f, z * 0.3f);
                float k = 0.85f + 0.3f * (a - 0.5f);
                o[0] = 0.28f * k; o[1] = 0.235f * k; o[2] = 0.21f * k;
                return 1;
            }
            case Mat.STONE: {
                // Grauwacke-Bruchstein in Lagen: Lagenhöhe 0,55..0,95 m, Steinlänge 0,8..2,2 m, dunkle Fugen
                // Längsrichtung der Mauer: Bogenlänge um den Mittelpunkt (0, -300), an Stirnflächen der Abstand
                float qz = z + 300f, qr = (float) Math.sqrt(x * x + qz * qz) + 1e-3f;
                float radial = Math.abs(n[0] * x + n[2] * qz) / qr;
                float tang = Math.abs(n[0] * qz - n[2] * x) / qr;
                float nxz = radial >= tang ? (float) Math.atan2(x, qz) * 300f : qr * (n[0] * qz - n[2] * x > 0 ? 1 : -1);
                float course = y / 0.72f;
                int ci = (int) Math.floor(course);
                float cf = course - ci;
                float rh = rnd(ci, 3, 7);
                float len = 0.9f + 1.2f * rnd(ci, 1, 5);
                float u = nxz / len + 7.3f * rh;
                int ui = (int) Math.floor(u);
                float uf = u - ui;
                float rb = rnd(ui, ci, 11);
                float avg = 1 - 0.85f * smooth(0.25f, 2.2f, foot);
                float tone = 0.98f + avg * (0.4f * (rb - 0.5f)) + 0.14f * (Noise.tex(x * 0.07f + z * 0.05f, y * 0.09f) - 0.5f);
                float hue = rnd(ui + 5, ci, 13);
                float a = Noise.tex(x * 2.3f + y * 1.7f, z * 2.3f + y * 0.9f);
                float k = tone * (1 + 0.35f * (a - 0.5f) * fineVis);
                float r = 0.225f, g = 0.205f, b = 0.18f;
                r += 0.045f * (hue - 0.5f); b -= 0.02f * (hue - 0.5f);
                // Fuge: je ferner, desto weicher, in der Ferne nur noch ein Grauschleier
                float jv = Math.min(cf, 1 - cf) * 0.72f, ju = Math.min(uf, 1 - uf) * len;
                float j = Math.min(jv, ju);
                float jw = 0.035f + 0.05f * smooth(0.05f, 0.5f, foot);
                float joint = 1 - smooth(jw * 0.5f, jw, j);
                float vis = 1 - smooth(0.25f, 1.8f, foot);
                float dk = 1 - 0.55f * joint * vis;
                // Steinoberfläche steht leicht aus der Fuge hervor
                if (fineVis > 0) {
                    bump(n, x, y, z, 0.22f * fineVis, 6.5f);
                }
                // Auswaschung und Sinterstreifen von oben nach unten, am Fuß feucht-dunkel
                float streak = Noise.tex(nxz * 0.9f, y * 0.04f);
                float st = 0.9f + 0.2f * (streak - 0.5f);
                o[0] = r * k * dk * st; o[1] = g * k * dk * st; o[2] = b * k * dk * st;
                // Moos am untersten Bereich über der Wasserlinie
                float moss = smooth(-2.5f, -0.3f, y) * (1 - smooth(0.5f, 2.5f, y)) * (0.4f + 0.6f * a) * 0.5f;
                o[0] *= 1 - 0.3f * moss; o[1] *= 1 + 0.05f * moss; o[2] *= 1 - 0.35f * moss;
                return 0.75f + 0.25f * (1 - joint * vis);
            }
            case Mat.MARBLE: {
                // weißer Carrara-Ton, graue Adern aus verzerrtem Rauschen
                float w = Noise.tex(x * 0.35f + y * 0.2f, z * 0.35f + y * 0.15f) * 2.4f;
                float v1 = Math.abs(Noise.tex(x * 0.9f + w, z * 0.9f + y * 0.7f + w) - 0.5f);
                float v2 = Math.abs(Noise.tex(x * 2.6f - w, y * 2.2f + z * 2.6f) - 0.5f);
                float vein = (1 - smooth(0.0f, 0.035f, v1)) * 0.55f + (1 - smooth(0.0f, 0.02f, v2)) * 0.25f * fineVis;
                float k = 0.80f + 0.1f * (Noise.tex(x * 0.5f, z * 0.5f + y * 0.5f) - 0.5f);
                o[0] = 0.78f * k - 0.38f * vein; o[1] = 0.77f * k - 0.37f * vein; o[2] = 0.74f * k - 0.34f * vein;
                return 1;
            }
            case Mat.SLATE: {
                // Schiefer: blaugraue Deckung in Reihen von 0,3 m, Platten verschieden hell
                float row = y / 0.30f;
                int ri = (int) Math.floor(row);
                float along = (Math.abs(n[0]) > Math.abs(n[2]) ? z : x) / 0.22f + 0.5f * (ri & 1);
                float pl = rnd((int) Math.floor(along), ri, 17);
                float k = 0.7f + 0.55f * pl + 0.15f * (Noise.tex(x * 0.15f, z * 0.15f) - 0.5f);
                float rf = row - ri;
                float edge = 1 - 0.35f * (1 - smooth(0.0f, 0.12f, rf)) * (1 - smooth(0.6f, 4f, foot));
                o[0] = 0.085f * k * edge; o[1] = 0.095f * k * edge; o[2] = 0.115f * k * edge;
                return 1;
            }
            case Mat.CASCADE: {
                // weißes, belüftetes Wasser, das die Luftseite hinabläuft: Bahnen, die mit der Zeit nach unten ziehen
                float qz = z + 300f, u = (float) Math.atan2(x, qz) * 300f;
                float fl = y + time * 5.5f;
                float a = Noise.tex(u * 1.3f, fl * 0.22f), b = Noise.tex(u * 4.1f + 3f, fl * 0.8f), c = Noise.tex(u * 9f + 7f, fl * 2.2f + 3f);
                float w = 0.62f + 0.38f * (0.5f * a + 0.35f * b + 0.15f * c) * (0.4f + 0.6f * fineVis + 0.6f * (1 - fineVis));
                bump(n, x, y + time * 5.5f, z, 0.25f * fineVis, 2.6f);
                // am Rand und in Wellentälern leicht bläulich, sonst fast weiß
                float tint = 1 - smooth(0.55f, 0.95f, w);
                o[0] = 0.62f * w - 0.12f * tint; o[1] = 0.68f * w - 0.06f * tint; o[2] = 0.72f * w;
                return 1;
            }
            case Mat.METAL: {
                float a = Noise.tex(x * 3.1f + y, z * 3.1f);
                o[0] = 0.045f + 0.02f * a; o[1] = 0.048f + 0.02f * a; o[2] = 0.05f + 0.02f * a;
                return 1;
            }
            case Mat.GLASS: {
                float a = Noise.tex(x * 0.4f + y * 0.3f, z * 0.4f);
                o[0] = 0.015f + 0.01f * a; o[1] = 0.025f + 0.015f * a; o[2] = 0.04f + 0.02f * a;
                return 1;
            }
            case Mat.CONCRETE: {
                float a = Noise.tex(x * 0.6f + y * 0.4f, z * 0.6f + y * 0.2f);
                float b = Noise.tex(x * 0.11f, z * 0.11f + y * 0.09f);
                float k = 0.78f + 0.25f * (a - 0.5f) * (1 - smooth(0.1f, 1.0f, foot)) + 0.25f * (b - 0.5f);
                // Schalungsfugen alle 2,4 m waagrecht, 3 m senkrecht
                float fy = y / 2.4f; fy -= (float) Math.floor(fy);
                float fx = (Math.abs(n[0]) > Math.abs(n[2]) ? z : x) / 3f; fx -= (float) Math.floor(fx);
                if (n[1] < 0.7f && (fy < 0.015f || fx < 0.01f)) k *= 0.7f;
                o[0] = 0.34f * k; o[1] = 0.34f * k; o[2] = 0.33f * k;
                return 1;
            }
            case Mat.CUT: {
                // Anschnitt gemauerter Quader: Lagen 1,2 m, Steine 2,4 m versetzt, helle Fugen, Farbe je Stein
                float qz = z + 300f, r = (float) Math.sqrt(x * x + qz * qz);
                float row = y / 1.2f; int ri = (int) Math.floor(row); float fy = row - ri;
                float col = r / 2.4f + ((ri * 37) % 11) / 11f; int ci = (int) Math.floor(col); float fx = col - ci;
                float st = Noise.tex(ci * 7.13f + 3f, ri * 3.71f + 1f);
                float k = 0.82f + 0.3f * (st - 0.5f) + 0.12f * (Noise.tex(r * 1.9f, y * 1.9f) - 0.5f);
                if (fy < 0.07f || fx < 0.05f) k *= 0.55f;
                o[0] = 0.46f * k; o[1] = 0.40f * k; o[2] = 0.30f * k;
                return 1;
            }
            case Mat.ASPHALT: {
                float a = Noise.tex(x * 1.7f + z * 0.9f, z * 1.7f);
                float b = Noise.tex(x * 0.09f + 3.3f, z * 0.09f + 1.1f);
                float k = 0.8f + 0.3f * (a - 0.5f) * (1 - smooth(0.1f, 1.0f, foot)) + 0.35f * (b - 0.5f);
                o[0] = 0.060f * k; o[1] = 0.060f * k; o[2] = 0.064f * k;
                frost(x, z, n[1], a, 0.6f, o);
                return 1;
            }
            case Mat.PAINT: {
                o[0] = 0.55f; o[1] = 0.55f; o[2] = 0.5f;
                return 1;
            }
            case Mat.BARK: {
                float a = Noise.tex(x * 3 + z * 3, y * 0.8f);
                float k = 0.8f + 0.35f * (a - 0.5f);
                o[0] = 0.085f * k; o[1] = 0.066f * k; o[2] = 0.052f * k;
                return 0.8f;
            }
            case Mat.NEEDLES: {
                // Drehkiefer: dunkles Blaugrün, je Baum etwas anders, Büschel als verkippte Normalen
                bump(n, x, y, z, 0.45f, 1.3f);
                float tree = Noise.tex(x * 0.21f + 11, z * 0.21f + 5);
                float a = Noise.tex(x * 1.9f + y * 0.7f, z * 1.9f + y * 0.5f);
                float k = 0.75f + 0.5f * (a - 0.5f);
                o[0] = (0.030f + 0.012f * tree) * k; o[1] = (0.058f + 0.02f * tree) * k; o[2] = (0.030f + 0.006f * tree) * k;
                frost(x, z, n[1], a, 0.75f, o);
                // Tiefe in der Krone: innen dunkler
                return 0.55f + 0.45f * a;
            }
            case Mat.LEAVES: {
                bump(n, x, y, z, 0.35f, 2.2f);
                float clone = Noise.tex(x * 0.035f + 3.1f, z * 0.035f + 7.7f);
                float a = Noise.tex(x * 2.3f + y * 0.9f, z * 2.3f + y * 0.7f);
                float tone = Math.max(0, Math.min(1, 1.6f * (clone - 0.5f) + 0.5f + 0.5f * (a - 0.5f)));
                float[] lut = leafLut;
                int nt = lut.length / 3;
                float f = tone * (nt - 1);
                int i0 = Math.min(nt - 2, (int) f);
                float w = f - i0;
                float k = 0.8f + 0.4f * (a - 0.5f);
                for (int c = 0; c < 3; c++) o[c] = (lut[3 * i0 + c] + (lut[3 * i0 + 3 + c] - lut[3 * i0 + c]) * w) * k;
                frost(x, z, n[1], a, 0.8f, o);
                return 0.6f + 0.4f * a;
            }
            case Mat.WHITEBARK: {
                // Espe: kalkweiß mit Grünstich, dunkle Narben unter den Ästen
                float a = Noise.tex(x * 4 + z * 4, y * 1.2f);
                float scar = Noise.tex((x + z) * 9, y * 5.5f);
                float k = 0.85f + 0.3f * (a - 0.5f);
                if (scar > 0.78f) { o[0] = 0.05f; o[1] = 0.05f; o[2] = 0.04f; }
                else { o[0] = 0.42f * k; o[1] = 0.45f * k; o[2] = 0.36f * k; }
                frost(x, z, n[1], a, 0.7f, o);
                return 0.9f;
            }
            case Mat.SPRUCE: {
                // Fichte und Tanne: dunkel, blaugrün, dichte Büschel
                bump(n, x, y, z, 0.4f, 1.6f);
                float tree = Noise.tex(x * 0.21f + 3, z * 0.21f + 9);
                float a = Noise.tex(x * 2.1f + y * 0.7f, z * 2.1f + y * 0.5f);
                float k = 0.72f + 0.5f * (a - 0.5f);
                o[0] = (0.020f + 0.008f * tree) * k; o[1] = (0.046f + 0.014f * tree) * k; o[2] = (0.036f + 0.012f * tree) * k;
                frost(x, z, n[1], a, 0.85f, o);
                return 0.5f + 0.45f * a;
            }
            case Mat.SNAG: {
                // abgestorbene Kiefer: silbergrau, unten weiß von aufgesogener Kieselsäure
                float a = Noise.tex(x * 2 + z * 2, y * 0.9f);
                float k = 0.85f + 0.3f * (a - 0.5f);
                float r = 0.34f * k, g = 0.32f * k, b = 0.29f * k;
                float white = 1 - smooth(0.45f, 0.75f, y - baseY(x, z));
                r += (0.66f - r) * white; g += (0.65f - g) * white; b += (0.61f - b) * white;
                o[0] = r; o[1] = g; o[2] = b;
                frost(x, z, n[1], a, 1, o);
                return 1;
            }
            default:
                o[0] = 0.3f; o[1] = 0.3f; o[2] = 0.3f;
                return 1;
        }
    }

    /**
     * Schnee auf den nach oben weisenden Flächen (Nadeln, Äste) und Raureif rundum in der Nähe
     * heißer Quellen, wenn die Luft friert: die „Geisterbäume“ der Geysirbecken im Winter.
     */
    private static void frost(float x, float z, float ny, float a, float amt, float[] o) {
        float snow = Thermal.snow, rime = Thermal.rime;
        if (snow < 0.01f && rime < 0.01f) return;
        Terrain t = terrain;
        float c = snow * smooth(0.45f, 0.9f, ny + (a - 0.5f) * 0.5f) * 0.6f;
        if (rime > 0.01f && t != null && t.thermal != null) {
            float r = rime * t.thermal.near(x, z, 90) * (0.6f + 0.4f * a);
            c = Math.max(c, r);
        }
        c = Math.min(1, c * amt);
        if (c <= 0) return;
        o[0] += (0.74f - o[0]) * c; o[1] += (0.77f - o[1]) * c; o[2] += (0.82f - o[2]) * c;
    }

    /** 1, wo der Boden kalt ist; 0 über warmem Abfluss. */
    private static float warmFree(float x, float z) {
        Terrain t = terrain;
        if (t == null || t.thermal == null) return 1;
        float tq = t.thermal.temp(x, z);
        return 1 - (float) smooth(Thermal.ambient + 3, Thermal.ambient + 12, tq);
    }

    private static float baseY(float x, float z) {
        Terrain t = terrain;
        return t == null ? 0 : t.sample(x, z);
    }

    /** Warmes Wasser färbt auch Kegel und Ränder: Matten nach der Temperatur, halb so stark. */
    private static void tint(float x, float z, float[] o) {
        Terrain t = terrain;
        if (t == null || t.thermal == null) return;
        float r = o[0], g = o[1], b = o[2];
        t.thermal.overlay(x, z, 1, o);
        o[0] = r + (o[0] - r) * 0.5f; o[1] = g + (o[1] - g) * 0.5f; o[2] = b + (o[2] - b) * 0.5f;
    }

    /** Fensterlicht: etwa zwei Drittel der Fenster brennen, je Fenster fest, mit leichtem Flackern des Tons. */
    public static float glassGlow(float x, float y, float z) {
        int h = Noise.hash((int) Math.floor(x / 2.2f), (int) Math.floor(y / 2.6f), (int) Math.floor(z / 2.2f));
        float v = (h & 1023) / 1024f;
        return v < 0.68f ? 0.55f + 0.45f * ((h >> 10 & 255) / 255f) : 0;
    }
}
