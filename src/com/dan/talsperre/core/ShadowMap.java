package com.dan.talsperre.core;

import java.util.stream.IntStream;

/**
 * Orthografische Schattenkarte der Sonne: Tiefe aus Sicht der Sonne je Kartenpunkt, gerastert
 * in Streifen auf allen Kernen. Abfrage mit Neigungskorrektur gegen Schattenakne und vier
 * gefilterten Abgriffen für einen weichen Rand. Übernommen aus Caracalla und Semiramis.
 */
public final class ShadowMap {
    public final int N;
    final float[] depth;
    double lxx, lxy, lxz, lyx, lyy, lyz, lzx, lzy, lzz;
    double ox, oy, texel;
    public boolean valid;

    private float[] psx = new float[0], psy, psd;

    public ShadowMap(int n) {
        N = n;
        depth = new float[n * n];
    }

    /** Karte um den Mittelpunkt (cx,cy,cz) mit halber Kantenlänge half, Sonne aus Richtung sun. */
    public void render(Mesh m, double[] sun, double cx, double cy, double cz, double half) {
        lzx = -sun[0]; lzy = -sun[1]; lzz = -sun[2];
        double ax = lzz, ay = 0, az = -lzx;
        double l = Math.sqrt(ax * ax + ay * ay + az * az);
        if (l < 1e-6) { ax = 1; ay = 0; az = 0; l = 1; }
        lxx = ax / l; lxy = ay / l; lxz = az / l;
        lyx = lzy * lxz - lzz * lxy; lyy = lzz * lxx - lzx * lxz; lyz = lzx * lxy - lzy * lxx;
        texel = 2 * half / N;
        ox = (cx * lxx + cy * lxy + cz * lxz) - half;
        oy = (cx * lyx + cy * lyy + cz * lyz) - half;

        int nv = m.nv;
        // Projektion der Ecken: Puffer bleiben liegen, die Karte wird im Zeitraffer ständig neu gerechnet
        if (psx.length < nv) { psx = new float[nv]; psy = new float[nv]; psd = new float[nv]; }
        final float[] sx = psx, sy = psy, sd = psd;
        final float[] P = m.pos;
        for (int v = 0; v < nv; v++) {
            double x = P[3 * v], y = m.deformY(v, P[3 * v + 1]), z = P[3 * v + 2];
            sx[v] = (float) ((x * lxx + y * lxy + z * lxz - ox) / texel);
            sy[v] = (float) ((x * lyx + y * lyy + z * lyz - oy) / texel);
            sd[v] = (float) (x * lzx + y * lzy + z * lzz);
        }
        java.util.Arrays.fill(depth, Float.MAX_VALUE);
        int strips = Math.max(8, Runtime.getRuntime().availableProcessors() * 4);
        int rowsPer = (N + strips - 1) / strips;
        IntStream.range(0, strips).parallel().forEach(s -> {
            int y0 = s * rowsPer, y1 = Math.min(N, y0 + rowsPer);
            for (int t = 0; t < m.nt; t++) {
                int g = m.grp[t];
                if (g == 2 || g == 3 || !m.shown(g)) continue;   // See und Überlaufwasser werfen keinen Schatten, versteckte Gruppen auch nicht
                int a = m.idx[3 * t], b = m.idx[3 * t + 1], c = m.idx[3 * t + 2];
                float minY = Math.min(sy[a], Math.min(sy[b], sy[c])), maxY = Math.max(sy[a], Math.max(sy[b], sy[c]));
                if (maxY < y0 - 1 || minY > y1 + 1) continue;
                raster(sx[a], sy[a], sd[a], sx[b], sy[b], sd[b], sx[c], sy[c], sd[c], y0, y1);
            }
        });
        valid = true;
    }

    private void raster(float x0, float y0, float d0, float x1, float y1, float d1, float x2, float y2, float d2, int ry0, int ry1) {
        float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
        if (Math.abs(area) < 1e-8f) return;
        float inv = 1f / area;
        int ya = Math.max(ry0, (int) Math.ceil(Math.min(y0, Math.min(y1, y2)) - 0.5f));
        int yb = Math.min(ry1 - 1, (int) Math.floor(Math.max(y0, Math.max(y1, y2)) - 0.5f));
        int xa0 = Math.max(0, (int) Math.ceil(Math.min(x0, Math.min(x1, x2)) - 0.5f));
        int xb0 = Math.min(N - 1, (int) Math.floor(Math.max(x0, Math.max(x1, x2)) - 0.5f));
        if (xa0 > xb0) return;
        float B0 = -(y2 - y1) * inv, B1 = -(y0 - y2) * inv, B2 = -(y1 - y0) * inv;
        for (int py = ya; py <= yb; py++) {
            float cy = py + 0.5f;
            float A0 = ((x2 - x1) * (cy - y1) + (y2 - y1) * x1) * inv;
            float A1 = ((x0 - x2) * (cy - y2) + (y0 - y2) * x2) * inv;
            float A2 = ((x1 - x0) * (cy - y0) + (y1 - y0) * x0) * inv;
            long span = Span.of(A0, B0, A1, B1, A2, B2, xa0 + 0.5f, xb0 + 0.5f);
            if (span == Span.EMPTY) continue;
            int xa = Math.max(xa0, Span.lo(span)), xb = Math.min(xb0, Span.hi(span));
            int row = py * N;
            for (int px = xa; px <= xb; px++) {
                float cx = px + 0.5f;
                float w0 = A0 + B0 * cx, w1 = A1 + B1 * cx, w2 = 1 - w0 - w1;
                float d = w0 * d0 + w1 * d1 + w2 * d2;
                if (d < depth[row + px]) depth[row + px] = d;
            }
        }
    }

    /** Liegt der Punkt sicher innerhalb der Karte? */
    public boolean covers(double x, double y, double z) {
        if (!valid) return false;
        double sx = (x * lxx + y * lxy + z * lxz - ox) / texel, sy = (x * lyx + y * lyy + z * lyz - oy) / texel;
        return sx > 4 && sy > 4 && sx < N - 5 && sy < N - 5;
    }

    /**
     * Wie {@link #lit}, aber −1, wenn der Punkt nicht sicher in der Karte liegt; projiziert nur einmal.
     * soft = false nimmt nur einen gefilterten Abgriff (für ferne Flächen); radius in Kartenpunkten.
     */
    public float litOrMiss(double x, double y, double z, double slope, boolean soft, double radius) {
        if (!valid) return -1;
        double sx = (x * lxx + y * lxy + z * lxz - ox) / texel - 0.5;
        double sy = (x * lyx + y * lyy + z * lyz - oy) / texel - 0.5;
        if (sx < 4 || sy < 4 || sx >= N - 5 || sy >= N - 5) return -1;
        double bias = texel * (1.0 + 1.6 * Math.min(slope, 6));
        float d = (float) (x * lzx + y * lzy + z * lzz - bias);
        if (!soft) return tap(sx, sy, d);
        double a = radius, b = radius * 0.36;
        return (tap(sx - a, sy - b, d) + tap(sx + a, sy + b, d) + tap(sx - b, sy + a, d) + tap(sx + b, sy - a, d)) * 0.25f;
    }

    /** Anteil Sonnenlicht 0..1 am Punkt; slope = tan des Einfallswinkels für die Neigungskorrektur. */
    public float lit(double x, double y, double z, double slope) {
        if (!valid) return 1;
        double bias = texel * (1.0 + 1.6 * Math.min(slope, 6));
        double sx = (x * lxx + y * lxy + z * lxz - ox) / texel - 0.5;
        double sy = (x * lyx + y * lyy + z * lyz - oy) / texel - 0.5;
        float d = (float) (x * lzx + y * lzy + z * lzz - bias);
        if (sx < 1 || sy < 1 || sx >= N - 2 || sy >= N - 2) return 1;
        return (tap(sx - 0.55, sy - 0.2, d) + tap(sx + 0.55, sy + 0.2, d) + tap(sx - 0.2, sy + 0.55, d) + tap(sx + 0.2, sy - 0.55, d)) * 0.25f;
    }

    private float tap(double sx, double sy, float d) {
        int ix = (int) Math.floor(sx), iy = (int) Math.floor(sy);
        float fx = (float) (sx - ix), fy = (float) (sy - iy);
        int o = iy * N + ix;
        float a = d <= depth[o] ? 1 : 0, b = d <= depth[o + 1] ? 1 : 0;
        float c = d <= depth[o + N] ? 1 : 0, e = d <= depth[o + N + 1] ? 1 : 0;
        return (a + (b - a) * fx) * (1 - fy) + (c + (e - c) * fx) * fy;
    }
}
