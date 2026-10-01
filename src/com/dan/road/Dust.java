package com.dan.road;

/**
 * Staub hinter Fahrzeugen auf Kies und Erde (nur wenn es trocken ist), Gischt auf nasser Fahrbahn bei
 * schneller Fahrt und aufgewirbelter Schnee. Teilchen steigen, wachsen, treiben mit dem Wind und
 * verblassen.
 */
final class Dust {
    static final byte DUST = 0, SPRAY = 1, SNOW = 2;
    int n;
    final int cap = 4000;
    final float[] x = new float[cap], y = new float[cap], z = new float[cap], vx = new float[cap], vy = new float[cap], vz = new float[cap];
    final float[] age = new float[cap], life = new float[cap], size = new float[cap], dense = new float[cap];
    final float[] r = new float[cap], g = new float[cap], b = new float[cap];
    final byte[] kind = new byte[cap];
    private final java.util.Random rnd = new java.util.Random(5);

    void step(float dt, Traffic tr, Weather w) {
        double wa = Math.toRadians(w.windDirection);
        float wx = (float) Math.cos(wa) * w.windSpeed, wz = (float) Math.sin(wa) * w.windSpeed;
        for (Mover m : tr.movers) {
            if (m.kind.walks() || m.v < 2) continue;
            WayType t = m.lane.way.type;
            boolean loose = t.paving != WayType.Paving.ASPHALT;
            float rate = 0;
            byte k = DUST;
            if (w.snow > 0.2f && (loose || m.v > 10)) { rate = m.v * 1.2f * w.snow; k = SNOW; }
            else if (loose && w.wet < 0.35f) rate = m.v * 2.5f * (1 - w.wet / 0.35f) * (m.kind.pedals() ? 0.1f : 1);
            else if (!loose && w.wet > 0.5f && m.v > 12) { rate = (m.v - 12) * 1.5f * w.wet; k = SPRAY; }
            if (m.kind.pedals() && k != DUST) rate = 0;
            m.dustAcc += rate * dt;
            while (m.dustAcc >= 1 && n < cap) {
                m.dustAcc -= 1;
                int i = n++;
                float back = m.kind.length * 0.5f;
                float side = (rnd.nextFloat() - 0.5f) * m.kind.width;
                x[i] = m.x - m.hx * back - m.hz * side; z[i] = m.z - m.hz * back + m.hx * side;
                y[i] = m.y + 0.2f;
                vx[i] = m.hx * m.v * 0.25f + (rnd.nextFloat() - 0.5f) * 1.5f;
                vz[i] = m.hz * m.v * 0.25f + (rnd.nextFloat() - 0.5f) * 1.5f;
                vy[i] = 0.4f + rnd.nextFloat() * 0.6f;
                age[i] = 0;
                kind[i] = k;
                life[i] = k == DUST ? 5 + rnd.nextFloat() * 5 : k == SPRAY ? 1.2f + rnd.nextFloat() : 2.5f + rnd.nextFloat() * 2;
                size[i] = k == DUST ? 0.6f : 0.4f;
                dense[i] = k == DUST ? 0.3f : k == SPRAY ? 0.22f : 0.4f;
                float[] c = t.worn;
                if (k == DUST) { r[i] = c[0] * 1.5f; g[i] = c[1] * 1.5f; b[i] = c[2] * 1.5f; }
                else if (k == SPRAY) { r[i] = 0.42f; g[i] = 0.44f; b[i] = 0.47f; }
                else { r[i] = 0.75f; g[i] = 0.77f; b[i] = 0.8f; }
            }
            if (m.dustAcc > 1) m.dustAcc = 1;
        }
        for (int i = 0; i < n; i++) {
            age[i] += dt;
            if (age[i] >= life[i]) { copy(--n, i); i--; continue; }
            float k = 1 - (float) Math.exp(-dt * 0.8f);
            vx[i] += (wx - vx[i]) * k; vz[i] += (wz - vz[i]) * k;
            vy[i] += ((kind[i] == DUST ? 0.15f : -0.3f) - vy[i]) * k;
            x[i] += vx[i] * dt; y[i] += vy[i] * dt; z[i] += vz[i] * dt;
            size[i] += dt * (kind[i] == DUST ? 0.8f : 0.6f);
        }
    }

    private void copy(int from, int to) {
        x[to] = x[from]; y[to] = y[from]; z[to] = z[from]; vx[to] = vx[from]; vy[to] = vy[from]; vz[to] = vz[from];
        age[to] = age[from]; life[to] = life[from]; size[to] = size[from]; dense[to] = dense[from]; kind[to] = kind[from];
        r[to] = r[from]; g[to] = g[from]; b[to] = b[from];
    }

    /** Deckkraft jetzt: blendet ein und langsam aus. */
    float alpha(int i) {
        float t = age[i] / life[i];
        return dense[i] * Math.min(1, t * 8) * (1 - t) * (1 - t);
    }
}
