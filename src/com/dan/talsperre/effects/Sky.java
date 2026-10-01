package com.dan.talsperre.effects;

/**
 * Analytisches Himmelsmodell über dem Yellowstone-Plateau (2240 m): Sonnenlicht nach Luftmasse,
 * Farbverlauf vom Zenit zum Horizont, Morgen- und Abendrot, Mond und Milchstraße. Aus Semiramis
 * übernommen und an die Höhe angepasst: dünnere Luft, also kräftigere Sonne, ein tieferes Blau im
 * Zenit und statt Wüstenstaub ein bläulich-grauer Dunst (Waldbrandrauch im Spätsommer).
 */
public final class Sky {
    public final double[] sun = {0, 1, 0};
    /** Beleuchtungsstärke der Sonne (linear, RGB). */
    public float sunR, sunG, sunB;
    float zenR, zenG, zenB, horR, horG, horB;
    /** Umgebungslicht für nach oben, seitlich und nach unten gerichtete Flächen. */
    public float upR, upG, upB, sideR, sideG, sideB, downR, downG, downB;
    /** 0 Nacht bis 1 Tag, Abendrot 0..1. */
    public float day, sunset;
    /** Staub in der Luft 0 (klar) bis 1 (diesig). */
    public float haze;
    /** Nachts ist der Mond die Lichtquelle: sun[] zeigt dann zum Mond, sunR/G/B ist Mondlicht. */
    public boolean moonLight;
    /** Wahre Richtung zur Sonne und zum Mond, beleuchteter Anteil des Mondes, Nacht 0..1. */
    public final double[] trueSun = {0, 1, 0}, moon = {0, -1, 0};
    public float moonLit, night;
    /** Bewölkung 0..1 aus dem Wetter; gilt ab dem nächsten {@link #update}. */
    public static volatile float overcastNext;
    /** Bewölkung dieses Himmels. */
    public float overcast;
    /** Galaktischer Nordpol und Zentrum am Himmel (setzt der Sternhimmel zur Sternzeit). */
    public static volatile double[] galPole = {0.28, 0.55, 0.79}, galCenter = {0, -1, 0};

    /** dy^0.42 als Tabelle: spart die teure Potenz je Pixel. */
    private static final float[] POW = new float[4097];

    static {
        for (int i = 0; i <= 4096; i++) POW[i] = (float) Math.pow(i / 4096.0, 0.42);
    }

    private static float pow042(float x) {
        float f = x * 4096;
        int i = (int) f;
        if (i >= 4096) return 1;
        float t = f - i;
        return POW[i] + (POW[i + 1] - POW[i]) * t;
    }

    public void update(double[] s, double hz) { update(s, new double[]{-s[0], -s[1], -s[2]}, 1, hz); }

    /** Himmel zu Sonnenstand s und Mondstand mo (Anteil beleuchtet ml). */
    public void update(double[] s, double[] mo, double ml, double hz) {
        haze = (float) hz;
        trueSun[0] = s[0]; trueSun[1] = s[1]; trueSun[2] = s[2];
        moon[0] = mo[0]; moon[1] = mo[1]; moon[2] = mo[2];
        moonLit = (float) ml;
        sun[0] = s[0]; sun[1] = s[1]; sun[2] = s[2];
        double sy = s[1];
        day = (float) smooth(-0.12, 0.12, sy);
        double el = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sy))));
        double m = sy > -0.02 ? 1.0 / (Math.max(sy, 0) + 0.15 * Math.pow(Math.max(el, 0) + 3.885, -1.253)) : 40;
        m = Math.min(m, 40);
        double vis = smooth(-0.03, 0.04, sy);
        double hk = 1 + 1.8 * hz;
        double dim = 1 - 0.3 * hz;
        // Dünne Bergluft: rund 78 % der Luftmasse auf Meereshöhe
        sunR = (float) (3.4 * dim * Math.exp(-m * 0.78 * 0.055 * hk) * vis);
        sunG = (float) (3.4 * dim * Math.exp(-m * 0.78 * 0.125 * hk) * vis);
        sunB = (float) (3.4 * dim * Math.exp(-m * 0.78 * 0.290 * hk) * vis);
        sunset = (float) (Math.exp(-Math.max(el, 0) / 9.0) * day);
        float lowZ = (float) smooth(0.0, 0.5, sy);
        zenR = lerp(0.003f, lerp(0.085f, 0.13f, lowZ), day);
        zenG = lerp(0.005f, lerp(0.18f, 0.30f, lowZ), day);
        zenB = lerp(0.016f, lerp(0.46f, 0.80f, lowZ), day);
        horR = lerp(0.010f, lerp(0.80f, 1.12f, sunset), day);
        horG = lerp(0.012f, lerp(0.80f, 0.60f, sunset), day);
        horB = lerp(0.024f, lerp(0.84f, 0.36f, sunset), day);
        // Dunst: Horizont bläulich grau, Himmel blasser (bei klarer Luft kaum)
        float hzf = 0.06f + 0.94f * (float) hz;
        zenR += (horR * 0.8f - zenR) * 0.3f * hzf; zenG += (horG * 0.8f - zenG) * 0.3f * hzf; zenB += (horB * 0.8f - zenB) * 0.3f * hzf;
        float grey = (horR + horG + horB) / 3f;
        horR += (grey * 0.97f - horR) * 0.45f * hzf; horG += (grey * 1.00f - horG) * 0.45f * hzf; horB += (grey * 1.06f - horB) * 0.45f * hzf;
        upR = (zenR * 0.6f + horR * 0.4f) * 1.5f + 0.004f;
        upG = (zenG * 0.6f + horG * 0.4f) * 1.5f + 0.005f;
        upB = (zenB * 0.6f + horB * 0.4f) * 1.5f + 0.008f;
        sideR = horR * 0.75f + zenR * 0.35f + 0.003f;
        sideG = horG * 0.75f + zenG * 0.35f + 0.004f;
        sideB = horB * 0.75f + zenB * 0.35f + 0.006f;
        // Der sichtbare Himmel leuchtet heller als sein Beitrag zum Umgebungslicht vermuten lässt
        float lift = 1 + 0.75f * day;
        zenR *= lift; zenG *= lift; zenB *= lift * 1.05f;
        horR *= lift; horG *= lift; horB *= lift;
        night = 1 - day;
        moonLight = false;
        // Nachts: Mond als Lichtquelle, der Himmel hellt sich um ihn herum etwas auf
        if (sy < -0.03 && mo[1] > -0.02) {
            double my = mo[1];
            double mm = my > -0.02 ? 1.0 / (Math.max(my, 0) + 0.15 * Math.pow(Math.max(Math.toDegrees(Math.asin(Math.max(0, my))), 0) + 3.885, -1.253)) : 40;
            mm = Math.min(mm, 40);
            float mvis = (float) (smooth(-0.02, 0.05, my) * smooth(-0.03, -0.12, sy));
            float im = (float) (0.035 * Math.pow(ml, 1.5)) * mvis;
            sunR = im * 0.78f * (float) Math.exp(-mm * 0.05); sunG = im * 0.86f * (float) Math.exp(-mm * 0.1); sunB = im * (float) Math.exp(-mm * 0.2);
            sun[0] = mo[0]; sun[1] = mo[1]; sun[2] = mo[2];
            moonLight = true;
            zenR += im * 0.05f; zenG += im * 0.08f; zenB += im * 0.16f;
            horR += im * 0.08f; horG += im * 0.1f; horB += im * 0.16f;
            upR += im * 0.15f; upG += im * 0.2f; upB += im * 0.32f;
            sideR += im * 0.1f; sideG += im * 0.13f; sideB += im * 0.2f;
        }
        // Bewölkung: die Sonne verschwindet hinter der Wolkendecke, der Himmel wird gleichmäßig grau
        float oc = overcast = overcastNext;
        if (oc > 0.001f) {
            float odim = 1 - 0.93f * oc;
            float lumSky = (upR + upG + upB) / 3f * 0.9f + (sunR + sunG + sunB) / 3f * 0.08f * oc * (moonLight ? 0 : 1);
            float gz = lumSky * 0.55f, gh = lumSky * 0.72f;
            zenR += (gz - zenR) * oc; zenG += (gz * 1.01f - zenG) * oc; zenB += (gz * 1.05f - zenB) * oc;
            horR += (gh - horR) * oc; horG += (gh * 1.01f - horG) * oc; horB += (gh * 1.04f - horB) * oc;
            float ua = (upR + upG + upB) / 3f;
            float amb = ua * (1 - 0.35f * oc) + (sunR + sunG + sunB) / 3f * 0.06f * oc * (moonLight ? 0 : 1);
            upR += (amb - upR) * oc; upG += (amb * 1.01f - upG) * oc; upB += (amb * 1.04f - upB) * oc;
            float sa = (sideR + sideG + sideB) / 3f * (1 - 0.3f * oc) + amb * 0.2f * oc;
            sideR += (sa - sideR) * oc; sideG += (sa * 1.01f - sideG) * oc; sideB += (sa * 1.04f - sideB) * oc;
            sunR *= odim; sunG *= odim; sunB *= odim;
            sunset *= odim;
        }
        float gs = (float) Math.max(0, sun[1]) * 0.30f;
        downR = sunR * gs * 0.60f + upR * 0.18f;
        downG = sunG * gs * 0.52f + upG * 0.18f;
        downB = sunB * gs * 0.38f + upB * 0.18f;
    }

    /** Himmelsleuchtdichte in Richtung d (normiert). */
    public void radiance(float dx, float dy, float dz, float[] o) { radiance(dx, dy, dz, o, true); }

    private void radiance(float dx, float dy, float dz, float[] o, boolean disc) {
        float r, g, b;
        if (dy >= 0) {
            float t = pow042(dy);
            r = horR + (zenR - horR) * t; g = horG + (zenG - horG) * t; b = horB + (zenB - horB) * t;
        } else {
            float t = Math.min(1, -dy * 4);
            float gr = 0.12f * day + 0.004f, gg = 0.10f * day + 0.004f, gb = 0.075f * day + 0.005f;
            r = horR * 0.8f + (gr - horR * 0.8f) * t; g = horG * 0.8f + (gg - horG * 0.8f) * t; b = horB * 0.8f + (gb - horB * 0.8f) * t;
        }
        if (overcast > 0.02f && dy > 0) {
            // Wolkendecke: dunklere und hellere Ballen, zum Horizont flach zusammengedrängt
            float inv = 1f / (dy + 0.08f);
            float u = dx * inv * 0.6f, v = dz * inv * 0.6f;
            float c = com.dan.talsperre.core.Noise.tex(u + 3.1f, v + 1.7f) * 0.65f + com.dan.talsperre.core.Noise.tex(u * 3.3f + 7, v * 3.3f) * 0.35f;
            float k = 1 + (c - 0.5f) * 0.9f * overcast * Math.min(1, dy * 5);
            r *= k; g *= k; b *= k;
        }
        if (night > 0.05f && dy > -0.02f && overcast < 0.9f) {
            // Milchstraße: Band entlang des galaktischen Äquators, zum Zentrum (Schütze) heller
            double[] gp = galPole, gc = galCenter;
            float gd = (float) (dx * gp[0] + dy * gp[1] + dz * gp[2]);
            float toC = (float) (dx * gc[0] + dy * gc[1] + dz * gc[2]);
            float c2 = Math.max(0, toC) * Math.max(0, toC);
            float band = (float) Math.exp(-gd * gd / (0.018f + 0.02f * c2)) * (0.55f + 1.8f * c2);
            // Sternwolken in zwei Größen, dazu der dunkle Große Riss (Staub) vom Schwan bis zum Schützen
            float cl = 0.45f + 0.7f * com.dan.talsperre.core.Noise.tex(dx * 9 + dz * 3, dy * 9 + dx * 2)
                    + 0.35f * (com.dan.talsperre.core.Noise.tex(dx * 31 + 7, dz * 31 + dy * 13) - 0.5f);
            float ro = 0.03f + 0.09f * (com.dan.talsperre.core.Noise.tex(dx * 5 + 1, dz * 5 + dy * 3) - 0.5f);
            float rw = 0.0006f + 0.0016f * com.dan.talsperre.core.Noise.tex(dx * 7 + 9, dz * 7 + dy * 4);
            float lump = com.dan.talsperre.core.Noise.tex(dx * 17 + 3, dz * 17 + dy * 6);
            float rift = (float) Math.exp(-(gd - ro) * (gd - ro) / rw) * Math.max(0, Math.min(1, toC + 0.4f))
                    * Math.max(0, Math.min(1, (lump - 0.25f) * 2.2f));
            band *= Math.max(0.2f, 1 - 0.7f * rift);
            float moonWash = 1 - 0.85f * moonLit * Math.max(0, Math.min(1, (float) moon[1] * 5 + 0.3f));
            float n2 = night * night * (1 - 0.8f * haze) * Math.min(1, dy * 6 + 0.1f) * moonWash;
            float mw = band * n2 * 0.006f * cl;
            r += mw * 0.9f; g += mw * 0.92f; b += mw;
            // Mondscheibe mit Phase, dazu ein Hof
            float mm = (float) (dx * moon[0] + dy * moon[1] + dz * moon[2]);
            if (mm > 0.9f) {
                float halo = (float) Math.pow(mm, 400) * 0.02f * moonLit * night;
                r += halo * 0.8f; g += halo * 0.85f; b += halo;
                final float sinR = 0.0105f; // etwa 0,6°: größer als echt, damit man die Phase sieht
                if (disc && mm > Math.sqrt(1 - sinR * sinR)) {
                    float ox = (float) (dx - moon[0] * mm), oy = (float) (dy - moon[1] * mm), oz = (float) (dz - moon[2] * mm);
                    float rr = (float) Math.sqrt(ox * ox + oy * oy + oz * oz) / sinR;
                    if (rr < 1) {
                        float zc = (float) Math.sqrt(1 - rr * rr);
                        float nx = ox / sinR - (float) moon[0] * zc, ny = oy / sinR - (float) moon[1] * zc, nz = oz / sinR - (float) moon[2] * zc;
                        float lit = Math.max(0, (float) (nx * trueSun[0] + ny * trueSun[1] + nz * trueSun[2]));
                        float mare = 0.7f + 0.3f * com.dan.talsperre.core.Noise.tex(nx * 3.1f + 5, ny * 3.1f + nz * 1.7f);
                        float br = (0.02f + 1.4f * lit * mare) * Math.min(1, night * 1.5f + 0.1f) * (1 - 0.6f * haze * (1 - dy));
                        r += br * 0.95f; g += br * 0.93f; b += br * 0.88f;
                    }
                }
            }
        }
        float mu = moonLight ? -1 : (float) (dx * sun[0] + dy * sun[1] + dz * sun[2]);
        if (mu > 0) {
            float m2 = mu * mu, m4 = m2 * m2, m8 = m4 * m4;
            float m32 = m8 * m8; m32 *= m32;
            float glow = (0.05f + 0.05f * haze) * m4 + 0.16f * m8 + 0.45f * m32 * m32;
            float k = 0.25f;
            r += sunR * glow * k; g += sunG * glow * k; b += sunB * glow * k;
            if (disc && mu > 0.99996f) { r += sunR * 60; g += sunG * 60; b += sunB * 60; }
        }
        o[0] = r; o[1] = g; o[2] = b;
    }

    /** Dunstfarbe für die Luftperspektive in Blickrichtung. */
    public void haze(float dx, float dy, float dz, float[] o) {
        // Richtung knapp über den Horizont gespiegelt: so geht der Dunst nahtlos in den Himmel über
        radiance(dx, Math.abs(dy) * 0.3f, dz, o, false);
        float mu = (float) Math.max(0, dx * sun[0] + dz * sun[2]);
        o[0] += sunR * mu * mu * 0.05f * haze;
        o[1] += sunG * mu * mu * 0.04f * haze;
        o[2] += sunB * mu * mu * 0.03f * haze;
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
