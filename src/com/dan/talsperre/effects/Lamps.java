package com.dan.talsperre.effects;

/**
 * Punktlichter der Sperrmauer: Kronenlampen, Fensterlicht, Flutlicht am Tosbecken. Sie leuchten
 * erst, wenn die Sonne weg ist ({@link #on} 0..1, vom Bildrechner gesetzt). Ein grobes Raster
 * über dem Tal findet die Lampen in der Nähe eines Punkts, damit Pixel und Strahlschritte nicht
 * alle Lampen prüfen müssen.
 */
public final class Lamps {
    public int n;
    public float[] x = new float[64], y = new float[64], z = new float[64];
    public float[] r = new float[64], g = new float[64], b = new float[64], rad = new float[64];
    /** Helligkeit der Lampen 0..1, folgt der Dämmerung. */
    public volatile float on;
    public volatile boolean enabled = true;

    private static final float CELL = 40, X0 = -900, Z0 = -1100;
    private static final int GW = 50, GH = 40;
    private int[] start = new int[GW * GH + 1], items = new int[0];

    public void add(double px, double py, double pz, double cr, double cg, double cb, double radius) {
        if (n == x.length) {
            int c = n * 2;
            x = java.util.Arrays.copyOf(x, c); y = java.util.Arrays.copyOf(y, c); z = java.util.Arrays.copyOf(z, c);
            r = java.util.Arrays.copyOf(r, c); g = java.util.Arrays.copyOf(g, c); b = java.util.Arrays.copyOf(b, c);
            rad = java.util.Arrays.copyOf(rad, c);
        }
        x[n] = (float) px; y[n] = (float) py; z[n] = (float) pz;
        r[n] = (float) cr; g[n] = (float) cg; b[n] = (float) cb; rad[n] = (float) radius;
        n++;
    }

    private static int cell(float px, float pz) {
        int cx = (int) Math.floor((px - X0) / CELL), cz = (int) Math.floor((pz - Z0) / CELL);
        if (cx < 0 || cz < 0 || cx >= GW || cz >= GH) return -1;
        return cz * GW + cx;
    }

    /** Raster füllen: jede Lampe in alle Zellen, die ihr Radius berührt. */
    public void finish() {
        int[] cnt = new int[GW * GH + 1];
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) {
                for (int i = 0; i < GW * GH; i++) cnt[i + 1] += cnt[i];
                start = cnt.clone();
                items = new int[start[GW * GH]];
                java.util.Arrays.fill(cnt, 0);
            }
            for (int i = 0; i < n; i++) {
                int a = (int) Math.floor((x[i] - rad[i] - X0) / CELL), b2 = (int) Math.floor((x[i] + rad[i] - X0) / CELL);
                int c = (int) Math.floor((z[i] - rad[i] - Z0) / CELL), d = (int) Math.floor((z[i] + rad[i] - Z0) / CELL);
                for (int cz = Math.max(0, c); cz <= Math.min(GH - 1, d); cz++)
                    for (int cx = Math.max(0, a); cx <= Math.min(GW - 1, b2); cx++) {
                        int k = cz * GW + cx;
                        if (pass == 0) cnt[k + 1]++;
                        else items[start[k] + cnt[k]++] = i;
                    }
            }
        }
    }

    /** Licht aller Lampen in der Nähe auf eine Fläche (Lambert, weiche Reichweite); addiert in out. */
    public void illuminate(float px, float py, float pz, float nx, float ny, float nz, float[] out) {
        int k = cell(px, pz);
        if (k < 0) return;
        float on = this.on;
        for (int j = start[k]; j < start[k + 1]; j++) {
            int i = items[j];
            float dx = x[i] - px, dy = y[i] - py, dz = z[i] - pz;
            float d2 = dx * dx + dy * dy + dz * dz;
            float R = rad[i];
            if (d2 >= R * R) continue;
            float d = (float) Math.sqrt(d2);
            float c = (dx * nx + dy * ny + dz * nz) / Math.max(d, 0.01f);
            if (c <= 0) continue;
            float w = 1 - d / R;
            float e = c * w * w * on * 38f / (d2 + 6f);
            out[0] += r[i] * e; out[1] += g[i] * e; out[2] += b[i] * e;
        }
    }

    /** Streulicht der Lampen in Nebel und Dunst an einem Punkt der Sichtlinie (ohne Fläche). */
    public void scatter(float px, float py, float pz, float[] out) {
        int k = cell(px, pz);
        if (k < 0) return;
        float on = this.on;
        for (int j = start[k]; j < start[k + 1]; j++) {
            int i = items[j];
            float dx = x[i] - px, dy = y[i] - py, dz = z[i] - pz;
            float d2 = dx * dx + dy * dy + dz * dz;
            float R = rad[i];
            if (d2 >= R * R) continue;
            float w = 1 - (float) Math.sqrt(d2) / R;
            float e = w * w * on * 30f / (d2 + 12f);
            out[0] += r[i] * e; out[1] += g[i] * e; out[2] += b[i] * e;
        }
    }

    /** Spiegelbild der Lampen auf Wasser: Lichtkeule um die Spiegelrichtung (rx, ry, rz), addiert in out. */
    public void glint(float px, float py, float pz, float rx, float ry, float rz, float[] out) {
        float on = this.on;
        for (int i = 0; i < n; i++) {
            float dx = x[i] - px, dy = y[i] - py, dz = z[i] - pz;
            float dot = dx * rx + dy * ry + dz * rz;
            if (dot <= 0) continue;
            float d2 = dx * dx + dy * dy + dz * dz;
            float c2 = dot * dot / d2;
            if (c2 < 0.99f) continue;
            float cos = (float) Math.sqrt(c2);
            float e = (float) Math.exp(-(1 - cos) * 1800f) * on * 2.2f;
            out[0] += r[i] * e; out[1] += g[i] * e; out[2] += b[i] * e;
        }
    }
}
