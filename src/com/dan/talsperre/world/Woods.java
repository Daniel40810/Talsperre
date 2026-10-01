package com.dan.talsperre.world;

import com.dan.forest.Forest;
import com.dan.forest.ForestPlanter;
import com.dan.forest.Species;
import com.dan.forest.TreeInstance;
import com.dan.forest.TreeMesh;
import com.dan.talsperre.core.Mat;
import com.dan.talsperre.core.MeshBuilder;
import com.dan.talsperre.core.Terrain;

import java.util.List;

/**
 * Der Wald am Edersee: Buchen, Eichen, Fichten, Tannen und Birken aus dem Wald-Paket, fest in das
 * Szenennetz gebacken. Nahe der Mauer stehen Laubbäume in der Stufe 2 (Äste und Laubbüschel), sonst
 * die Silhouette der Stufe 3; Nadelbäume immer als Silhouette. Der Ausschlag im Wind wächst mit der
 * Höhe über dem Boden. Die Bodenkarte des Geländes (Wald 0..1) bestimmt die Dichte.
 */
public final class Woods {
    private Woods() { }

    /** Zahl der Bäume des letzten Aufbaus (für Statuszeilen und Prüfung). */
    public static int trees, near, triangles;

    private static final double X0 = -1300, X1 = 1300, Z0 = -1500, Z1 = 1200;

    public static void build(MeshBuilder mb, Terrain t) {
        long t0 = System.nanoTime();
        Forest forest = new Forest((x, z) -> t.sample(x, z));
        forest.variants = 4;
        int beech = forest.addSpecies(Species.beech());
        int oak = forest.addSpecies(Species.oak());
        int spruce = forest.addSpecies(Species.spruce());
        int fir = forest.addSpecies(Species.silverFir());
        int birch = forest.addSpecies(Species.birch());
        forest.prepare();
        final float[] gm = new float[5];
        ForestPlanter.Density dens = (x, z) -> {
            double h = t.sample(x, z);
            if (low(x, z, h) || blocked(x, z)) return 0;
            float[] g = new float[5];
            t.ground((float) x, (float) z, g);
            float fo = g[4];
            if (g[0] < 4) return 0;                       // Uferstreifen frei
            double sx = t.sample(x + 6, z) - t.sample(x - 6, z), sz = t.sample(x, z + 6) - t.sample(x, z - 6);
            double slope = Math.hypot(sx, sz) / 12;
            if (slope > 1.25) return 0;                   // Felswand
            float d = fo;
            if (fo < 0.15f) {                              // auf der Wiese Baumgruppen und einzelne Bäume
                float n = com.dan.forest.Noise2.value((float) (x / 95) + 4.1f, (float) (z / 95) - 7.3f);
                d = Math.max(fo * 0.25f, n > 0.52f ? 0.5f * Math.min(1, (n - 0.52f) * 8) : 0f);
            }
            return d * (float) (1 - 0.5 * Math.max(0, slope - 0.7));
        };
        List<TreeInstance> all = ForestPlanter.plant(forest, X0, Z0, X1, Z1, 11.5f, dens,
                new int[]{beech, oak, spruce, fir, birch}, new float[]{5, 2, 3.5f, 1, 0.6f}, 140f, (x, z) -> t.sample(x, z), 20251001L);
        TreeMesh[][] lows = new TreeMesh[5][forest.variants];
        // Ufergehölz entlang der Eder und am See: lockere Reihen aus Birken und Eichen
        ForestPlanter.Density bank = (x, z) -> {
            double h = t.sample(x, z);
            if (low(x, z, h) || blocked(x, z)) return 0;
            float[] g = new float[5];
            t.ground((float) x, (float) z, g);
            float d = g[0];
            if (d < 5 || d > 28) return 0;
            return 0.55f * (float) Math.sin((d - 5) / 23 * Math.PI) * (g[4] > 0.3f ? 0.3f : 1f);
        };
        all.addAll(ForestPlanter.plant(forest, X0, Z0, X1, Z1, 9f, bank, new int[]{birch, oak}, new float[]{3, 1.5f}, 60f, (x, z) -> t.sample(x, z), 777L));
        int v0 = mb.vertexCount(), t0c = mb.triCount();
        double me = mb.maxEdge, keepSway = mb.swayValue;
        MeshBuilder.SkyFn keepSky = mb.skyFn;
        mb.maxEdge = 1e6;
        near = 0;
        final double[] ctx = new double[3];   // Fußhöhe, Baumhöhe, Boden-Himmelssicht
        mb.skyFn = (x, y, z, nx, ny, nz) -> {
            double f = Math.max(0, Math.min(1, (y - ctx[0]) / Math.max(1, ctx[1])));
            return (float) ((0.42 + 0.58 * Math.min(1, f * 1.4)) * ctx[2]);
        };
        for (TreeInstance tr : all) {
            double d = Math.hypot(tr.x - 0, tr.z + 160);
            boolean deciduous = !tr.species.conifer;
            int lod = deciduous && d < 260 ? 2 : 3;
            if (lod == 2) near++;
            TreeMesh m = lod == 2 ? forest.mesh(tr, 2) : low(lows, forest, tr);
            bake(mb, m, tr, deciduous, d < 400, ctx, t);
        }
        mb.maxEdge = me;
        mb.skyFn = keepSky;
        mb.swayValue = keepSway;
        mb.swayFn = null;
        trees = all.size();
        triangles = mb.triCount() - t0c;
        System.out.println("Wald: " + trees + " Bäume (" + near + " nah), " + triangles + " Dreiecke, " + (mb.vertexCount() - v0) + " Punkte, "
                + (System.nanoTime() - t0) / 1000000 + " ms");
    }

    private static TreeMesh low(TreeMesh[][] c, Forest f, TreeInstance tr) {
        TreeMesh m = c[tr.speciesIndex][tr.variant];
        if (m == null) m = c[tr.speciesIndex][tr.variant] = TreeMesh.silhouette(f.mesh(tr, 3).model, 5, 4);
        return m;
    }

    /** Wo kein Wald steht: Mauer, Krone, Tosbecken, Kraftwerk, Fluss unterhalb. */
    /** Unterhalb des höchsten Pegels im Seebecken (oberhalb der Mauer). */
    static boolean low(double x, double z, double h) {
        return h < 3.5 && z > Dam.CZ + 5 && Math.hypot(x, z - Dam.CZ) > Dam.R - 25;
    }

    static boolean blocked(double x, double z) {
        if (Roads.near(x, z)) return true;
        double dx = x, dz = z - Dam.CZ;
        double r = Math.hypot(dx, dz);
        double phi = Math.atan2(dx, dz);
        if (Math.abs(r - Dam.R) < 80 && Math.abs(phi) < Math.toRadians(58) && z < 120) return true;
        if (Math.abs(x) < 130 && z > -640 && z < -280) return true;      // Tosbecken und Kraftwerk
        return false;
    }

    private static void bake(MeshBuilder mb, TreeMesh m, TreeInstance tr, boolean deciduous, boolean sway, double[] ctx, Terrain t) {
        double s = tr.scale, cy = Math.cos(tr.yaw), sy = Math.sin(tr.yaw);
        double h = m.model.height * s;
        ctx[0] = tr.y; ctx[1] = h;
        ctx[2] = 0.55 + 0.45 * t.skyView(tr.x, tr.z, tr.y);
        int base = mb.vertexCount();
        boolean birch = tr.species.name.startsWith("Birke");
        for (int i = 0; i < m.nv; i++) {
            double px = m.pos[3 * i] * s, py = m.pos[3 * i + 1] * s, pz = m.pos[3 * i + 2] * s;
            double nx = m.nrm[3 * i], ny = m.nrm[3 * i + 1], nz = m.nrm[3 * i + 2];
            double wx = tr.x + cy * px + sy * pz, wz = tr.z - sy * px + cy * pz;
            double rx = cy * nx + sy * nz, rz = -sy * nx + cy * nz;
            double f = Math.max(0, py / Math.max(1, h));
            mb.swayValue = sway ? 0.5 * f * f * (deciduous ? 1.0 : 0.6) : 0;
            mb.v(wx, tr.y + py - 0.15, wz, rx, ny, rz);
        }
        mb.swayValue = 0;
        for (int k = 0; k < m.nt; k++) {
            int a = m.tri[3 * k], b = m.tri[3 * k + 1], c = m.tri[3 * k + 2];
            byte part = m.part[a];
            int mat;
            if (part == TreeMesh.BARK) mat = birch ? Mat.WHITEBARK : Mat.BARK;
            else if (tr.species.conifer) mat = Mat.SPRUCE;
            else mat = Mat.LEAVES;
            mb.tri(base + a, base + b, base + c, mat);
        }
    }
}
