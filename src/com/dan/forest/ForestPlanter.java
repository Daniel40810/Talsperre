package com.dan.forest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Setzt Bäume in eine Fläche: mit Mindestabstand (wie Bäume in einem Bestand einander Platz lassen),
 * nach einer Dichte (0 = kein Baum, 1 = voller Bestand), mit Arten in Gruppen (Espen stehen in Klonen
 * beisammen, Buchen in Horsten) und gemischtem Alter; am Rand und wo die Dichte klein ist, stehen eher
 * junge Bäume.
 */
public final class ForestPlanter {
    private ForestPlanter() { }

    /** Dichte 0..1 an einer Stelle. */
    @FunctionalInterface
    public interface Density { float at(double x, double z); }

    /**
     * Bäume im Rechteck (x0, z0)–(x1, z1). species mit Gewichten weights; spacing ist der mittlere
     * Abstand im vollen Bestand (m); patch ist die Größe der Artengruppen (m, 0 = gemischt).
     */
    public static List<TreeInstance> plant(Forest forest, double x0, double z0, double x1, double z1, float spacing, Density density,
                                           int[] species, float[] weights, float patch, Ground ground, long seed) {
        Random rnd = new Random(seed);
        List<TreeInstance> out = new ArrayList<>();
        double cell = spacing / Math.sqrt(2);
        int nx = (int) Math.ceil((x1 - x0) / cell), nz = (int) Math.ceil((z1 - z0) / cell);
        TreeInstance[] grid = new TreeInstance[Math.max(1, nx * nz)];
        float wsum = 0;
        for (float w : weights) wsum += w;
        for (int k = 0; k < nx * nz * 3; k++) {
            double x = x0 + rnd.nextDouble() * (x1 - x0), z = z0 + rnd.nextDouble() * (z1 - z0);
            float d = density == null ? 1 : density.at(x, z);
            if (rnd.nextFloat() > d) continue;
            int gi = (int) ((x - x0) / cell), gj = (int) ((z - z0) / cell);
            // Mindestabstand: in dünnen Beständen größer
            double minD = spacing * (0.75 + 0.5 * (1 - d));
            boolean ok = true;
            for (int j = Math.max(0, gj - 2); ok && j <= Math.min(nz - 1, gj + 2); j++)
                for (int i = Math.max(0, gi - 2); ok && i <= Math.min(nx - 1, gi + 2); i++) {
                    TreeInstance o = grid[j * nx + i];
                    if (o != null && Math.hypot(o.x - x, o.z - z) < minD * (0.5 + 0.5 * Math.min(o.scale, 1))) ok = false;
                }
            if (!ok || grid[gj * nx + gi] != null) continue;
            // Art: in Gruppen, über Rauschen je Art gewichtet
            int si = 0;
            float best = -1;
            for (int s = 0; s < species.length; s++) {
                float n = patch > 0 ? Noise2.value((float) (x / patch) + s * 17.3f, (float) (z / patch) - s * 9.1f) : rnd.nextFloat();
                float score = n * weights[s] / wsum + (patch > 0 ? 0.15f * rnd.nextFloat() : 0);
                if (score > best) { best = score; si = s; }
            }
            float age = Math.max(0.2f, Math.min(1, (0.45f + 0.55f * d) * (0.6f + 0.55f * rnd.nextFloat())));
            TreeInstance t = forest.instance(species[si], rnd.nextInt(forest.variants), x, ground.height(x, z), z, rnd.nextDouble() * 2 * Math.PI, age);
            grid[gj * nx + gi] = t;
            out.add(t);
        }
        return out;
    }
}
