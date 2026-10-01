package com.dan.river;

/**
 * Treibgut auf dem Wasser: Schaumflocken, Blätter und Zweige. Es zieht mit der Strömung, kreiselt im
 * Kehrwasser, sammelt sich an langsamen Stellen und bleibt am Ufer hängen. Neue Stücke tauchen in einem
 * Kreis um einen Mittelpunkt (etwa die Kamera) auf, wo das Wasser sie hertragen würde; was den Kreis
 * verlässt oder lange am Ufer liegt, verschwindet.
 * <p>
 * Ein Zeichner holt die Stücke mit {@link #quads} als Vierecke auf dem Wasserspiegel.
 */
public final class Drift {
    public static final byte FOAM = 0, LEAF = 1, TWIG = 2;
    public final int capacity;
    public int n;
    public final float[] x, z, y, ang, spin, size, r, g, b, stuck;
    public final byte[] kind;
    /** Stücke je 100 m² Wasser, die um den Mittelpunkt gehalten werden (Schaum, Blätter, Zweige). */
    public float foamDensity = 3, leafDensity = 0.15f, twigDensity = 0.05f;
    /** Mittlere Größe der Schaumflocken (m). */
    public float foamSize = 0.25f;
    /** Farbe der Blätter (linear), wenn sie nicht von außen kommen. */
    public float[] leafColor = {0.35f, 0.22f, 0.05f};
    private final java.util.Random rnd = new java.util.Random(4242);
    private final FlowField.Flow f = new FlowField.Flow();

    public Drift(int capacity) {
        this.capacity = capacity;
        x = new float[capacity]; z = new float[capacity]; y = new float[capacity]; ang = new float[capacity];
        spin = new float[capacity]; size = new float[capacity]; r = new float[capacity]; g = new float[capacity];
        b = new float[capacity]; stuck = new float[capacity]; kind = new byte[capacity];
    }

    /** Ein Stück an (px, pz) aufs Wasser legen (etwa ein Blatt, das hineinfällt). */
    public boolean add(float px, float pz, byte k, float cr, float cg, float cb, float sz) {
        if (n >= capacity) return false;
        int i = n++;
        x[i] = px; z[i] = pz; y[i] = 0; ang[i] = rnd.nextFloat() * 6.28f; spin[i] = (rnd.nextFloat() - 0.5f) * 0.6f;
        size[i] = sz; r[i] = cr; g[i] = cg; b[i] = cb; stuck[i] = 0; kind[i] = k;
        return true;
    }

    /**
     * Ein Zeitschritt: bewegen mit der Strömung field, Nachschub im Kreis radius um (cx, cz). Stücke
     * außerhalb des Kreises verschwinden.
     */
    public void step(float dt, FlowField field, double cx, double cz, float radius) {
        for (int i = 0; i < n; i++) {
            float px = x[i], pz = z[i];
            if (!field.sample(px, pz, f) || !f.wet) {
                // am Ufer oder am Stein hängen geblieben
                stuck[i] += dt;
                if (stuck[i] > 20 || f.solid) { remove(i--); continue; }
                continue;
            }
            stuck[i] = 0;
            // Treibgut folgt der Oberfläche, Unruhe schüttelt es
            float jig = 0.25f * f.turb + 0.03f;
            float vx = f.vx + (rnd.nextFloat() - 0.5f) * jig * 2, vz = f.vz + (rnd.nextFloat() - 0.5f) * jig * 2;
            x[i] = px + vx * dt; z[i] = pz + vz * dt;
            y[i] = f.level;
            // Drehung: im Kehrwasser und durch Scherung
            ang[i] += (spin[i] + f.eddy * 1.5f * Math.signum(spin[i] + 1e-3f)) * dt;
            float dxc = x[i] - (float) cx, dzc = z[i] - (float) cz;
            if (dxc * dxc + dzc * dzc > radius * radius * 1.1f) remove(i--);
        }
        // Nachschub: so viele, wie auf der Wasserfläche im Kreis zu erwarten sind
        int want = 0;
        int tries = 40;
        float area = 0;
        int wetHits = 0;
        for (int k = 0; k < tries; k++) {
            float a = rnd.nextFloat() * 6.2832f, rr = radius * (float) Math.sqrt(rnd.nextFloat());
            if (field.sample(cx + Math.cos(a) * rr, cz + Math.sin(a) * rr, f) && f.wet) wetHits++;
        }
        area = (float) (Math.PI * radius * radius) * wetHits / tries;
        want = (int) (area / 100 * (foamDensity + leafDensity + twigDensity));
        int spawn = Math.min(capacity - n, Math.max(0, want - n));
        spawn = Math.min(spawn, 30);
        for (int k = 0; k < spawn * 4 && spawn > 0; k++) {
            float a = rnd.nextFloat() * 6.2832f, rr = radius * (float) Math.sqrt(rnd.nextFloat());
            float px = (float) (cx + Math.cos(a) * rr), pz = (float) (cz + Math.sin(a) * rr);
            if (!field.sample(px, pz, f) || !f.wet) continue;
            float tot = foamDensity + leafDensity + twigDensity, p = rnd.nextFloat() * tot;
            byte kd = p < foamDensity ? FOAM : p < foamDensity + leafDensity ? LEAF : TWIG;
            if (kd == FOAM && f.foam < 0.05f && rnd.nextFloat() > 0.3f) continue;   // Schaum dort, wo welcher entsteht
            if (kd == FOAM) add(px, pz, kd, 0.85f, 0.87f, 0.86f, foamSize * (0.5f + rnd.nextFloat()));
            else if (kd == LEAF) add(px, pz, kd, leafColor[0] * (0.8f + 0.4f * rnd.nextFloat()), leafColor[1], leafColor[2], 0.06f + 0.04f * rnd.nextFloat());
            else add(px, pz, kd, 0.16f, 0.11f, 0.07f, 0.3f + 0.6f * rnd.nextFloat());
            y[n - 1] = f.level;
            spawn--;
        }
    }

    private void remove(int i) {
        int j = --n;
        if (i == j) return;
        x[i] = x[j]; z[i] = z[j]; y[i] = y[j]; ang[i] = ang[j]; spin[i] = spin[j]; size[i] = size[j];
        r[i] = r[j]; g[i] = g[j]; b[i] = b[j]; stuck[i] = stuck[j]; kind[i] = kind[j];
    }

    /**
     * Ecken aller Stücke als flache Vierecke knapp über dem Wasserspiegel: xyz bekommt 12 Werte je Stück,
     * rgb 3. Zweige sind lang und schmal, Blätter rautenförmig, Schaum rund. Liefert die Anzahl.
     */
    public int quads(float[] xyz, float[] rgb) {
        for (int i = 0; i < n; i++) {
            float c = (float) Math.cos(ang[i]), s = (float) Math.sin(ang[i]);
            float L = size[i] * 0.5f, W = kind[i] == TWIG ? size[i] * 0.04f : kind[i] == LEAF ? size[i] * 0.3f : size[i] * 0.45f;
            float yy = y[i] + 0.015f;
            int o = 12 * i;
            xyz[o] = x[i] - c * L; xyz[o + 1] = yy; xyz[o + 2] = z[i] - s * L;
            xyz[o + 3] = x[i] - s * W; xyz[o + 4] = yy; xyz[o + 5] = z[i] + c * W;
            xyz[o + 6] = x[i] + c * L; xyz[o + 7] = yy; xyz[o + 8] = z[i] + s * L;
            xyz[o + 9] = x[i] + s * W; xyz[o + 10] = yy; xyz[o + 11] = z[i] - c * W;
            rgb[3 * i] = r[i]; rgb[3 * i + 1] = g[i]; rgb[3 * i + 2] = b[i];
        }
        return n;
    }
}
