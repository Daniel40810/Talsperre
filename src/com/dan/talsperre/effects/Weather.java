package com.dan.talsperre.effects;

import com.dan.talsperre.core.Terrain;

/**
 * Wetter über dem Becken: Bewölkung, Regen, Gewitter mit Blitz und Donner, Schneefall. Von Hand
 * wählbar oder „nach Jahreszeit“: Im Juli und August kommen nachmittags oft kurze, heftige Gewitter,
 * Juli ist der trockenste Monat; Winter und Frühjahr bringen den meisten Niederschlag, bei Frost als
 * Schnee (Yellowstone Forever, Klimabeschreibungen des Parks). Welcher Tag welches Wetter hat, ist
 * ein Modell: aus Tag und Jahreszeit gewürfelt, jeden Tag gleich.
 * <p>
 * Regen und Schnee fallen als Teilchen um die Kamera, der Boden wird nass, Donner kommt mit der
 * Laufzeit des Schalls (rund 3 s je km).
 */
public final class Weather {
    public static final String[] MODES = {"nach Jahreszeit", "klar", "bewölkt", "Regen", "Gewitter", "Schneefall"};
    public static final int AUTO = 0, CLEAR = 1, CLOUDY = 2, RAIN = 3, STORM = 4, SNOW = 5;

    public volatile int mode = AUTO;
    /** Jetziger Zustand, weich nachgeführt: Bewölkung, Regen, Schnee 0..1; Gewitter an. */
    public float overcast, rain, snow;
    public boolean storm;
    /** Was gerade gilt (für Tafel und Statuszeile). */
    public String label = "klar";

    /** Blitz: Helligkeit 0..1, Einschlag x, z, Punkte des Kanals (x, y, z …), Zeit bis zum nächsten. */
    public float flash;
    public volatile float[] bolt;
    public double boltX, boltZ;
    private double nextStrike = 8, flashT = -1;
    private final java.util.Random rnd = new java.util.Random(1988);

    /** Ziel für Tag d, Stunde h und Luft t (°C), ohne Übergänge. */
    public float[] target(int d, double h, double t) {
        int m = mode;
        if (m == AUTO) m = auto(d, h, t);
        if (m == RAIN && t < 0.5) m = SNOW;
        if (m == SNOW && t > 3) m = RAIN;
        switch (m) {
            case CLOUDY: return new float[]{0.75f, 0, 0, 0};
            case RAIN: return new float[]{0.95f, 0.7f, 0, 0};
            case STORM: return new float[]{1f, 1f, 0, 1};
            case SNOW: return new float[]{0.9f, 0, 0.8f, 0};
            default: return new float[]{0, 0, 0, 0};
        }
    }

    /**
     * Wetter nach Jahreszeit (Modell): Sommergewitter an etwa jedem dritten Tag im Juli und August
     * zwischen 13 und 19 Uhr für gut eine Stunde; sonst an manchen Tagen Regen oder Schnee für einige
     * Stunden, im Juli selten, im Winter und Frühjahr häufiger.
     */
    static int auto(int d, double h, double t) {
        long seed = d * 2654435761L + 97;
        java.util.Random r = new java.util.Random(seed);
        boolean summer = d >= 182 && d <= 243;
        if (summer && r.nextDouble() < 0.35) {
            double s = 13 + 5 * r.nextDouble(), len = 0.8 + 0.9 * r.nextDouble();
            if (h >= s - 0.7 && h < s) return CLOUDY;
            if (h >= s && h < s + len) return STORM;
            if (h >= s + len && h < s + len + 0.5) return CLOUDY;
            return CLEAR;
        }
        // Anteil der Tage mit Niederschlag je Monat (Modell nach der Beschreibung: Juli trocken, Winter/Frühjahr nass)
        double[] wet = {0.45, 0.4, 0.4, 0.4, 0.4, 0.35, 0.12, 0.15, 0.2, 0.3, 0.4, 0.45};
        int mo = Math.min(11, (int) ((d - 1) / 30.5));
        if (r.nextDouble() < wet[mo]) {
            double s = 24 * r.nextDouble(), len = 2 + 6 * r.nextDouble();
            double hh = h < s ? h + 24 : h;
            if (hh >= s && hh < s + len) return t < 0.5 ? SNOW : RAIN;
            if (hh >= s - 1.5 && hh < s + len + 1.5) return CLOUDY;
        }
        return r.nextDouble() < 0.25 ? CLOUDY : CLEAR;
    }

    /** Ein Zeitschritt: Übergänge (rund eine Minute), Blitze. Liefert true, wenn ein Blitz einschlägt. */
    public boolean step(double dt, int d, double h, double t, double camX, double camZ) {
        float[] tg = target(d, h, t);
        float k = (float) Math.min(1, dt / 40);
        overcast += (tg[0] - overcast) * k;
        rain += (tg[1] - rain) * k;
        snow += (tg[2] - snow) * k;
        storm = tg[3] > 0;
        label = storm ? "Gewitter" : rain > 0.2f ? "Regen" : snow > 0.2f ? "Schneefall" : overcast > 0.5f ? "bewölkt" : "klar";
        boolean strike = false;
        if (storm && rain > 0.5f) {
            nextStrike -= dt;
            if (nextStrike <= 0) {
                nextStrike = 6 + 22 * rnd.nextDouble();
                double a = rnd.nextDouble() * 2 * Math.PI, r = 400 + 2600 * rnd.nextDouble();
                boltX = camX + Math.cos(a) * r; boltZ = camZ + Math.sin(a) * r;
                flashT = 0;
                strike = true;
            }
        }
        if (flashT >= 0) {
            flashT += dt;
            // zwei, drei Nachblitze in den ersten 0,4 s
            double f = Math.exp(-flashT / 0.07) + 0.6 * Math.exp(-Math.abs(flashT - 0.18) / 0.04) + 0.4 * Math.exp(-Math.abs(flashT - 0.33) / 0.04);
            flash = (float) Math.min(1, f);
            if (flashT > 0.6) { flashT = -1; flash = 0; bolt = null; }
        } else flash = 0;
        return strike;
    }

    /** Blitzkanal vom Wolkenboden (rund 1500 m über Grund) zum Einschlag, zackig. */
    public void makeBolt(Terrain terrain) {
        int n = 28;
        float[] p = new float[3 * (n + 1)];
        double gy = terrain.sample(boltX, boltZ), top = gy + 1500;
        double x = boltX + (rnd.nextDouble() - 0.5) * 300, z = boltZ + (rnd.nextDouble() - 0.5) * 300;
        for (int i = 0; i <= n; i++) {
            double u = i / (double) n;
            double y = top + (gy - top) * u;
            if (i > 0) { x += (rnd.nextDouble() - 0.5) * 90 + (boltX - x) * 0.12; z += (rnd.nextDouble() - 0.5) * 90 + (boltZ - z) * 0.12; }
            if (i == n) { x = boltX; z = boltZ; }
            p[3 * i] = (float) x; p[3 * i + 1] = (float) y; p[3 * i + 2] = (float) z;
        }
        bolt = p;
    }

    /** Regen oder Schnee um die Kamera ausstoßen (dt Sekunden), Wind in m/s. */
    public void emit(ParticleSystem ps, Terrain t, double cx, double cy, double cz, float dt, float wx, float wz) {
        float rn = rain, sn = snow;
        if (rn < 0.02f && sn < 0.02f) return;
        double gy = t.sample(cx, cz);
        double alt = cy - gy;
        if (alt > 400) return;
        double R = 42 + Math.min(80, alt * 0.4);
        float nRain = rn * 7800 * dt * (float) (R * R / (42 * 42)), nSnow = sn * 2600 * dt * (float) (R * R / (42 * 42));
        int free = ParticleSystem.CAP - ps.n - 4000;
        spawn(ps, t, cx, cy, cz, R, Math.min(free, nRain), true, wx, wz);
        spawn(ps, t, cx, cy, cz, R, Math.min(free, nSnow), false, wx, wz);
    }

    private void spawn(ParticleSystem ps, Terrain t, double cx, double cy, double cz, double R, float mean, boolean rainKind, float wx, float wz) {
        int c = (int) mean;
        if (ps.rand() < mean - c) c++;
        for (int k = 0; k < c; k++) {
            double r = R * Math.sqrt(ps.rand()), a = ps.rand() * 6.2832;
            double x = cx + Math.cos(a) * r, z = cz + Math.sin(a) * r;
            double gy = t.sample(x, z);
            double y = Math.max(gy + 2, cy - 8) + ps.rand() * 30;
            if (rainKind) {
                float vy = -(6.5f + 2.5f * ps.rand());
                ps.spawn(ParticleSystem.RAIN, (float) x, (float) y, (float) z, wx * 0.7f, vy, wz * 0.7f, 0.012f, 9, 0.35f, -1e9f, -1);
            } else {
                int i = ps.spawn(ParticleSystem.SNOW, (float) x, (float) y, (float) z, wx * 0.5f, -(0.8f + 0.5f * ps.rand()), wz * 0.5f,
                        0.03f + 0.03f * ps.rand(), 40, 0.8f, -1e9f, -1);
                if (i >= 0) ps.grow[i] = ps.rand() * 6.28f;
            }
        }
    }
}
