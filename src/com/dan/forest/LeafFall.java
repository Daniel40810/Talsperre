package com.dan.forest;

/**
 * Fallende Blätter: Sie lösen sich (vor allem im Herbst und bei Böen), segeln im Wind, pendeln und
 * trudeln dabei – ein flach liegendes Blatt gleitet seitlich und fällt langsam, ein kippendes stürzt
 * ab – und bleiben am Boden liegen, bis sie verrotten. Jedes Blatt ist ein Rhombus mit Farbe; ein
 * Zeichner holt die Ecken mit {@link #quads}.
 */
public final class LeafFall {
    public final int capacity;
    public int n;
    final float[] x, y, z, vx, vy, vz, ax, ay, az, wx, wy, wz, size, r, g, b, age, ground, phase;
    /** Wie lange ein Blatt am Boden liegt, bis es weg ist (s). */
    public float lieTime = 45;

    /** Wasser, auf dem Blätter landen können (etwa ein Fluss, der sie weiterträgt). */
    public interface Water {
        /** Wasserspiegel an (x, z) oder NaN, wo kein Wasser ist. */
        float level(float x, float z);

        /** Ein Blatt landet auf dem Wasser; true = übernommen (hier verschwindet es dann). */
        boolean take(float x, float y, float z, float r, float g, float b, float size);
    }

    /** Wasser unter den Bäumen (null = keins). */
    public volatile Water water;
    private final java.util.Random rnd = new java.util.Random(1776);

    public LeafFall(int capacity) {
        this.capacity = capacity;
        x = new float[capacity]; y = new float[capacity]; z = new float[capacity];
        vx = new float[capacity]; vy = new float[capacity]; vz = new float[capacity];
        ax = new float[capacity]; ay = new float[capacity]; az = new float[capacity];
        wx = new float[capacity]; wy = new float[capacity]; wz = new float[capacity];
        size = new float[capacity]; r = new float[capacity]; g = new float[capacity]; b = new float[capacity];
        age = new float[capacity]; ground = new float[capacity]; phase = new float[capacity];
    }

    /** Ein Blatt löst sich bei (px, py, pz) mit Farbe (linear) und Größe (m); ist alles voll, ersetzt es das älteste liegende. */
    public void add(float px, float py, float pz, float cr, float cg, float cb, float sz) {
        int i;
        if (n < capacity) i = n++;
        else {
            i = 0;
            float best = -1;
            for (int k = 0; k < n; k++) if (ground[k] > best) { best = ground[k]; i = k; }
        }
        x[i] = px; y[i] = py; z[i] = pz; vx[i] = 0; vy[i] = 0; vz[i] = 0;
        ax[i] = rnd.nextFloat() * 6.28f; ay[i] = rnd.nextFloat() * 6.28f; az[i] = rnd.nextFloat() * 6.28f;
        wx[i] = (rnd.nextFloat() - 0.5f) * 6; wy[i] = (rnd.nextFloat() - 0.5f) * 4; wz[i] = (rnd.nextFloat() - 0.5f) * 6;
        size[i] = sz; r[i] = cr; g[i] = cg; b[i] = cb; age[i] = 0; ground[i] = -1; phase[i] = rnd.nextFloat() * 6.28f;
    }

    /** Ein Zeitschritt: Wind aus wind (zur Zeit t), Boden aus gr. */
    public void step(float dt, WindField wind, double t, Ground gr) {
        WindField.Sample s = new WindField.Sample();
        for (int i = 0; i < n; i++) {
            if (ground[i] >= 0) {
                ground[i] += dt;
                if (ground[i] > lieTime) { remove(i); i--; }
                continue;
            }
            age[i] += dt;
            wind.sample(x[i], z[i], t, s);
            // Wie flach das Blatt liegt: flach gleitet es und fällt langsam, gekippt stürzt es
            float flat = Math.abs((float) Math.cos(ax[i]) * (float) Math.cos(az[i]));
            float vt = 0.7f + 1.3f * (1 - flat);
            // Pendeln: seitliches Gleiten quer zur Fallrichtung, wechselnd
            float sw = (float) Math.sin(age[i] * (2.2f + phase[i] * 0.3f) + phase[i]) * 1.4f * flat;
            float tx = s.dx * s.speed * 0.85f + (float) Math.cos(phase[i] + age[i] * 0.4f) * sw;
            float tz = s.dz * s.speed * 0.85f + (float) Math.sin(phase[i] + age[i] * 0.4f) * sw;
            float ty = -vt + 0.25f * s.turb * (float) Math.sin(age[i] * 5 + phase[i]);
            float k = Math.min(1, dt * 2.5f);
            vx[i] += (tx - vx[i]) * k; vy[i] += (ty - vy[i]) * k; vz[i] += (tz - vz[i]) * k;
            x[i] += vx[i] * dt; y[i] += vy[i] * dt; z[i] += vz[i] * dt;
            // Trudeln, schneller bei Wind
            float spin = 1 + 0.2f * s.speed;
            ax[i] += wx[i] * dt * spin; ay[i] += wy[i] * dt * spin; az[i] += wz[i] * dt * spin;
            Water wa = water;
            if (wa != null) {
                float wl = wa.level(x[i], z[i]);
                if (!Float.isNaN(wl) && y[i] <= wl + 0.02f && wa.take(x[i], wl, z[i], r[i], g[i], b[i], size[i])) { remove(i); i--; continue; }
            }
            float h = gr.height(x[i], z[i]);
            if (y[i] <= h + 0.02f) {
                y[i] = h + 0.02f + 0.01f * rnd.nextFloat();
                ax[i] = 0; az[i] = 0;          // liegt flach
                ground[i] = 0;
            }
        }
    }

    private void remove(int i) {
        int j = --n;
        if (i == j) return;
        x[i] = x[j]; y[i] = y[j]; z[i] = z[j]; vx[i] = vx[j]; vy[i] = vy[j]; vz[i] = vz[j];
        ax[i] = ax[j]; ay[i] = ay[j]; az[i] = az[j]; wx[i] = wx[j]; wy[i] = wy[j]; wz[i] = wz[j];
        size[i] = size[j]; r[i] = r[j]; g[i] = g[j]; b[i] = b[j]; age[i] = age[j]; ground[i] = ground[j]; phase[i] = phase[j];
    }

    /** Wie viele gerade in der Luft sind. */
    public int flying() {
        int c = 0;
        for (int i = 0; i < n; i++) if (ground[i] < 0) c++;
        return c;
    }

    /**
     * Ecken aller Blätter als Rhomben: xyz bekommt 12 Werte je Blatt (4 Ecken), rgb 3 je Blatt (am Boden
     * dunkler und brauner), nrm 3 je Blatt. Liefert die Anzahl.
     */
    public int quads(float[] xyz, float[] rgb, float[] nrm) {
        for (int i = 0; i < n; i++) {
            float ca = (float) Math.cos(ax[i]), sa = (float) Math.sin(ax[i]), cb = (float) Math.cos(ay[i]), sb = (float) Math.sin(ay[i]),
                    cc = (float) Math.cos(az[i]), sc = (float) Math.sin(az[i]);
            // Drehung Gier (ay), Nick (ax), Roll (az): Blattachse u, Querachse v, Normale
            float ux = cb * cc + sb * sa * sc, uy = ca * sc, uz = -sb * cc + cb * sa * sc;
            float vx0 = -cb * sc + sb * sa * cc, vy0 = ca * cc, vz0 = sb * sc + cb * sa * cc;
            float nx = uy * vz0 - uz * vy0, ny = uz * vx0 - ux * vz0, nz = ux * vy0 - uy * vx0;
            if (ground[i] >= 0) { ux = cb; uy = 0; uz = -sb; vx0 = sb; vy0 = 0; vz0 = cb; nx = 0; ny = 1; nz = 0; }
            float L = size[i] * 0.5f, Wd = size[i] * 0.3f;
            int o = 12 * i;
            xyz[o] = x[i] - ux * L; xyz[o + 1] = y[i] - uy * L; xyz[o + 2] = z[i] - uz * L;
            xyz[o + 3] = x[i] + vx0 * Wd; xyz[o + 4] = y[i] + vy0 * Wd; xyz[o + 5] = z[i] + vz0 * Wd;
            xyz[o + 6] = x[i] + ux * L; xyz[o + 7] = y[i] + uy * L; xyz[o + 8] = z[i] + uz * L;
            xyz[o + 9] = x[i] - vx0 * Wd; xyz[o + 10] = y[i] - vy0 * Wd; xyz[o + 11] = z[i] - vz0 * Wd;
            // am Boden: dunkler und brauner, bis es verrottet
            float k = ground[i] < 0 ? 0 : Math.min(1, 0.3f + 0.7f * ground[i] / lieTime);
            rgb[3 * i] = r[i] + (0.07f - r[i]) * k; rgb[3 * i + 1] = g[i] + (0.05f - g[i]) * k; rgb[3 * i + 2] = b[i] + (0.03f - b[i]) * k;
            nrm[3 * i] = nx; nrm[3 * i + 1] = ny; nrm[3 * i + 2] = nz;
        }
        return n;
    }
}
