package com.dan.forest;

import java.util.Random;

/**
 * Lässt einen Baum nach den Regeln seiner Art wachsen: erst der Stamm mit leichter Krümmung, dann Äste
 * in Quirlen (Nadelbäume) oder schraubig im Goldenen Winkel, jeweils mit Abgangswinkel, Länge nach der
 * Form der Krone und einer Neigung nach oben oder unten; darauf Zweige und Triebe, an den letzten
 * Ordnungen die Blätter oder Nadelbüschel. Derselbe Startwert ergibt immer denselben Baum.
 */
public final class TreeGenerator {
    private TreeGenerator() { }

    /** Ein Baum der Art sp aus dem Startwert seed; age 0..1 skaliert die Größe (junge Bäume sind kleiner und haben weniger Äste). */
    public static TreeModel grow(Species sp, long seed, float age) {
        Random rnd = new Random(seed * 6364136223846793005L + 1442695040888963407L);
        TreeModel m = new TreeModel(sp, seed);
        float a = Math.max(0.15f, Math.min(1, age));
        float H = lerp(sp.heightMin, sp.heightMax, rnd.nextFloat()) * (0.3f + 0.7f * a);
        m.height = H;
        // ------------------------------------------------ Stamm
        int n = sp.trunkSegments + 1;
        float trunkLen = H * sp.trunkEnd;
        float[] x = new float[n], y = new float[n], z = new float[n], r = new float[n];
        float r0 = H * sp.trunkRatio * 0.5f * (0.85f + 0.3f * rnd.nextFloat());
        double dx = (rnd.nextDouble() - 0.5) * sp.trunkWobble, dz = (rnd.nextDouble() - 0.5) * sp.trunkWobble, dy = 1;
        double px = 0, py = 0, pz = 0, step = trunkLen / (n - 1);
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            x[i] = (float) px; y[i] = (float) py; z[i] = (float) pz;
            // Wurzelanlauf unten, zur Spitze schlanker
            r[i] = (float) (r0 * (1 - 0.8 * Math.pow(t, 0.9)) * (1 + 0.5 * Math.exp(-t * 25)));
            if (sp.trunkEnd < 1) r[i] = (float) (r0 * (1 - 0.45 * t) * (1 + 0.5 * Math.exp(-t * 25)));
            dx += (rnd.nextDouble() - 0.5) * sp.trunkWobble; dz += (rnd.nextDouble() - 0.5) * sp.trunkWobble;
            double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            px += dx / l * step; py += dy / l * step; pz += dz / l * step;
        }
        TreeModel.Branch trunk = m.add(-1, 0, 0, x, y, z, r);
        trunk.phase = rnd.nextFloat() * 6.2832f;
        children(m, sp, trunk, 1, rnd, H, a);
        leaves(m, sp, rnd);
        cones(m, sp, rnd);
        // Kronenradius aus den Blättern (für Umgebungslicht und Detailstufen)
        float cr = 0;
        for (TreeModel.LeafSpot l : m.leaves) cr = Math.max(cr, (float) Math.hypot(l.x, l.z));
        for (TreeModel.Branch b : m.branches) for (int i = 0; i < b.nodes(); i++) cr = Math.max(cr, (float) Math.hypot(b.x[i], b.z[i]));
        m.crownRadius = Math.max(0.5f, cr);
        return m;
    }

    /** Kinder der Ordnung level am Elternast p. */
    private static void children(TreeModel m, Species sp, TreeModel.Branch p, int level, Random rnd, float H, float age) {
        if (level > sp.levels) return;
        float[] q = new float[7];
        int count = Math.round(sp.count[level] * (level == 1 ? (0.6f + 0.4f * age) : (0.45f + 0.55f * p.rel)) * (0.85f + 0.3f * rnd.nextFloat()));
        if (count <= 0) return;
        // Ansatzbereich auf dem Elternast
        float t0 = level == 1 ? Math.max(sp.start[1], Math.min(0.95f, sp.crownBase * H / p.length)) : sp.start[level];
        float t1 = level == 1 ? (sp.trunkEnd < 1 ? 1f : 0.97f) : 0.95f;
        float az = rnd.nextFloat() * 6.2832f;
        int whorls = sp.whorl > 0 && level == 1 ? Math.max(1, count / sp.whorl) : count;
        int made = 0;
        for (int k = 0; k < whorls && made < count; k++) {
            float t = t0 + (t1 - t0) * (whorls == 1 ? 1 : k / (float) (whorls - 1));
            if (level > 1 || sp.whorl == 0) t += (rnd.nextFloat() - 0.5f) * (t1 - t0) / whorls * 0.6f;
            t = Math.max(t0, Math.min(t1, t));
            int inWhorl = sp.whorl > 0 && level == 1 ? sp.whorl : 1;
            float wAz = rnd.nextFloat() * 6.2832f;
            for (int j = 0; j < inWhorl && made < count; j++, made++) {
                float azi = inWhorl > 1 ? wAz + j * 6.2832f / inWhorl + (rnd.nextFloat() - 0.5f) * 0.5f : (az += 2.39996f);
                p.at(t, q);
                grow(m, sp, p, level, t, azi, q, rnd, H);
            }
        }
    }

    /** Ein Kind an der Stelle t des Elternasts (q = Punkt, Radius, Richtung), Azimut azi. */
    private static void grow(TreeModel m, Species sp, TreeModel.Branch p, int level, float t, float azi, float[] q, Random rnd, float H) {
        float tx = q[4], ty = q[5], tz = q[6];
        // Basis senkrecht zur Richtung des Elternasts; u möglichst waagrecht
        float ux = -tz, uy = 0, uz = tx;
        float ul = (float) Math.sqrt(ux * ux + uz * uz);
        if (ul < 1e-3f) { ux = 1; uz = 0; ul = 1; }
        ux /= ul; uz /= ul;
        float vx = ty * uz - tz * uy, vy = tz * ux - tx * uz, vz = tx * uy - ty * ux;
        float ang = (float) Math.toRadians(sp.angle[level] + (rnd.nextFloat() * 2 - 1) * sp.angleVar[level]);
        float sa = (float) Math.sin(ang), ca = (float) Math.cos(ang);
        float cz = (float) Math.cos(azi), sz = (float) Math.sin(azi);
        if (sp.conifer && level == 2) { cz = rnd.nextBoolean() ? 1 : -1; sz = (rnd.nextFloat() - 0.5f) * 0.4f; }   // flache Fächer
        float dx = ca * tx + sa * (cz * ux + sz * vx), dy = ca * ty + sa * (cz * uy + sz * vy), dz = ca * tz + sa * (cz * uz + sz * vz);
        // Länge: am Stamm nach der Form der Krone, weiter außen kürzer zur Spitze des Elternasts
        float len;
        if (level == 1) {
            float yb = sp.crownBase * H;
            float h = Math.max(0, Math.min(1, (q[1] - yb) / Math.max(1, H - yb)));
            len = H * sp.lengthRatio[1] * envelope(sp.crown, h);
        } else {
            len = p.length * sp.lengthRatio[level] * (1 - 0.55f * t);
        }
        len *= 0.8f + 0.4f * rnd.nextFloat();
        if (len < 0.05f) return;
        int segs = level == 1 ? 6 : level == 2 ? 4 : 3;
        float[] x = new float[segs + 1], y = new float[segs + 1], z = new float[segs + 1], r = new float[segs + 1];
        float rb = Math.min(q[3] * 0.9f, Math.max(0.004f, q[3] * sp.radiusRatio[level] * (0.7f + 0.3f * (float) Math.sqrt(len / Math.max(0.1f, p.length)))));
        float px = q[0], py = q[1], pz = q[2], step = len / segs;
        for (int i = 0; i <= segs; i++) {
            float u = i / (float) segs;
            x[i] = px; y[i] = py; z[i] = pz;
            r[i] = Math.max(0.003f, rb * (1 - 0.75f * u));
            // Neigung nach oben oder unten, dazu etwas Zufall
            dy += sp.tropism[level] * step;
            dx += (rnd.nextFloat() - 0.5f) * 0.12f; dy += (rnd.nextFloat() - 0.5f) * 0.08f; dz += (rnd.nextFloat() - 0.5f) * 0.12f;
            float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= l; dy /= l; dz /= l;
            px += dx * step; py += dy * step; pz += dz * step;
        }
        TreeModel.Branch b = m.add(p.id, level, t, x, y, z, r);
        b.phase = rnd.nextFloat() * 6.2832f;
        b.rel = Math.min(1, len / Math.max(0.1f, (level == 1 ? H * sp.lengthRatio[1] : p.length * sp.lengthRatio[level])));
        children(m, sp, b, level + 1, rnd, H, 1);
    }

    /** Länge der Äste erster Ordnung nach der Höhe h in der Krone (0 unten, 1 oben). */
    static float envelope(Species.Crown c, float h) {
        switch (c) {
            case CONICAL: return 0.12f + 0.88f * (1 - h);
            case NARROW: return 0.25f + 0.75f * (float) Math.pow(1 - h, 0.8) * (0.6f + 0.4f * (float) Math.sin(Math.PI * Math.min(1, h * 2.5)));
            case ROUND: return (float) Math.sin(Math.PI * (0.12 + 0.82 * h)) * 0.95f + 0.05f;
            case SPREADING: return (float) Math.pow(Math.sin(Math.PI * (0.08 + 0.8 * h)), 0.6) * (1.15f - 0.35f * h);
            case SPIRE: return 0.22f + 0.78f * (float) Math.pow(Math.max(0, 1 - h), 0.55) * (1 - 0.25f * h);
            case UMBRELLA: {
                float up = h < 0.75f ? 0.35f + 0.65f * h / 0.75f : 1;
                return up * (h > 0.85f ? 1 - (h - 0.85f) / 0.15f * 0.55f : 1);
            }
            default: return (float) Math.pow(Math.sin(Math.PI * (0.1 + 0.85 * h)), 0.8) * (1 - 0.2f * h) + 0.08f;
        }
    }

    /** Blätter und Nadelbüschel an den Ästen der Blatt-Ordnungen. */
    private static void leaves(TreeModel m, Species sp, Random rnd) {
        float[] q = new float[7];
        for (TreeModel.Branch b : m.branches) {
            if (b.level < sp.leafLevel) continue;
            // Nadelbäume: Büschel an Ästen und Zweigen; Laubbäume: auf den letzten Ordnungen, an den äußeren Stücken
            float from = sp.conifer ? (b.level == 1 ? 0.3f : 0.1f) : (b.level < sp.levels ? 0.55f : 0.15f);
            int n = Math.max(1, Math.round(sp.leafDensity * b.length * (1 - from) * (b.level < sp.levels && !sp.conifer ? 0.5f : 1)));
            for (int i = 0; i < n; i++) {
                float s = from + (1 - from) * (i + rnd.nextFloat()) / n;
                b.at(s, q);
                TreeModel.LeafSpot l = new TreeModel.LeafSpot();
                l.branch = b.id; l.s = s; l.x = q[0]; l.y = q[1]; l.z = q[2];
                float tx = q[4], ty = q[5], tz = q[6];
                // Blatt steht seitlich und etwas nach oben ab, Fläche zum Licht
                float ax = (float) (rnd.nextGaussian()), ay = 0.4f + 0.3f * (float) rnd.nextGaussian(), azv = (float) (rnd.nextGaussian());
                float dot = ax * tx + ay * ty + azv * tz;
                ax -= dot * tx; ay -= dot * ty; azv -= dot * tz;
                if (sp.conifer) { ax = tx; ay = ty; azv = tz; }
                float al = (float) Math.sqrt(ax * ax + ay * ay + azv * azv) + 1e-6f;
                l.dx = ax / al; l.dy = ay / al; l.dz = azv / al;
                // Normale: senkrecht zum Blatt, möglichst nach oben
                float nx = l.dy * tz - l.dz * ty, ny = l.dz * tx - l.dx * tz, nz = l.dx * ty - l.dy * tx;
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nl < 1e-3f) { nx = 0; ny = 1; nz = 0; nl = 1; }
                nx /= nl; ny /= nl; nz /= nl;
                if (ny < 0) { nx = -nx; ny = -ny; nz = -nz; }
                // etwas zur Sonne gedreht
                ny += 0.6f; nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                l.nx = nx / nl; l.ny = ny / nl; l.nz = nz / nl;
                l.size = sp.leafSize * (0.75f + 0.5f * rnd.nextFloat()) * (sp.conifer ? 1 : (0.8f + 0.4f * s));
                l.tone = rnd.nextFloat(); l.drop = rnd.nextFloat(); l.phase = rnd.nextFloat() * 6.2832f;
                m.leaves.add(l);
            }
        }
    }

    /** Zapfen nahe den Spitzen der Äste erster Ordnung im oberen Teil der Krone; aufrecht oder hängend. */
    private static void cones(TreeModel m, Species sp, Random rnd) {
        if (sp.cones <= 0) return;
        float[] q = new float[7];
        float yMin = m.height * (1 - sp.coneZone);
        for (TreeModel.Branch b : m.branches) {
            if (b.level != 1) continue;
            b.at(0.8f, q);
            if (q[1] < yMin) continue;
            float expect = sp.cones * b.rel;
            int n = (int) expect + (rnd.nextFloat() < expect - (int) expect ? 1 : 0);
            for (int i = 0; i < n; i++) {
                float s = 0.55f + 0.4f * rnd.nextFloat();
                b.at(s, q);
                TreeModel.Cone c = new TreeModel.Cone();
                c.branch = b.id; c.s = s;
                float side = (rnd.nextFloat() - 0.5f) * 0.15f;
                c.x = q[0] + side * q[6]; c.y = q[1] + (sp.coneUpright ? q[3] : -q[3]); c.z = q[2] - side * q[4];
                float tilt = (rnd.nextFloat() - 0.5f) * 0.4f;
                c.dx = q[4] * 0.2f + tilt; c.dy = sp.coneUpright ? 1 : -1; c.dz = q[6] * 0.2f - tilt;
                float l = (float) Math.sqrt(c.dx * c.dx + c.dy * c.dy + c.dz * c.dz);
                c.dx /= l; c.dy /= l; c.dz /= l;
                c.size = sp.coneSize * (0.8f + 0.4f * rnd.nextFloat());
                m.cones.add(c);
            }
        }
    }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
