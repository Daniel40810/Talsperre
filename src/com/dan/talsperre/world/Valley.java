package com.dan.talsperre.world;

import com.dan.talsperre.core.Mat;
import com.dan.talsperre.core.MeshBuilder;
import com.dan.talsperre.core.Scene;
import com.dan.talsperre.core.Terrain;
import com.dan.talsperre.core.Thermal;

/**
 * Baut die Szene des Edertals: Gelände aus den drei Gittern, dazu der Lauf der Eder als Wasserband.
 * Phase 2: Tal nach Maß, dazu eine flache Platzhalter-Wasserfläche als See und ein Platzhalter-Block
 * als Mauer. Phase 3 ersetzt den Block durch die Sperrmauer, Phase 4 die Fläche durch den See.
 */
public final class Valley {
    public final Scene scene;
    public final Terrain terrain;

    private Valley(Scene scene, Terrain terrain) { this.scene = scene; this.terrain = terrain; }

    public static Valley build() {
        Terrain t = new Terrain();
        Dam.shapeGround(t);
        t.build();
        Thermal th = new Thermal();
        t.thermal = th;
        MeshBuilder mb = new MeshBuilder();
        mb.nearX0 = -1500; mb.nearX1 = 1500; mb.nearZ0 = -1500; mb.nearZ1 = 1500;
        grid(mb, t, t.fine, true);
        grid(mb, t, t.mid, true);
        grid(mb, t, t.far, false);
        river(mb, t);
        Flood.build(mb, t);
        lake(mb, t, t.fine);
        lake(mb, t, t.mid);
        Dam.build(mb, t);
        Dam.lakeStrip(mb);
        Dam.construction(mb, t);
        Ruins.build(mb, t);
        Roads.build(mb, t);
        if (!Boolean.getBoolean("tal.nowoods")) Woods.build(mb, t);
        Scene sc = new Scene("Edertal", mb.build(64), t, th);
        sc.lamps = Dam.lamps();
        sc.life = new Life(t);
        return new Valley(sc, t);
    }

    // ------------------------------------------------------------ Gelände und Fluss

    /** Ein Höhengitter als Dreiecke, ohne die Löcher; mit skirt hängt am Außenrand ein Saum gegen Ritzen. */
    private static void grid(MeshBuilder mb, Terrain t, Terrain.Grid g, boolean skirt) {
        int w = g.nx + 1;
        float[] sky = skyOf(t, g);
        mb.skyFn = (x, y, z, nx, ny, nz) -> {
            int i = (int) Math.round((x - g.x0) / g.cell), j = (int) Math.round((z - g.z0) / g.cell);
            return sky[Math.max(0, Math.min(g.nz, j)) * w + Math.max(0, Math.min(g.nx, i))];
        };
        int[] id = new int[w * (g.nz + 1)];
        java.util.Arrays.fill(id, -1);
        for (int j = 0; j < g.nz; j++) {
            for (int i = 0; i < g.nx; i++) {
                if (g.inHole(i, j)) continue;
                int a = node(mb, g, id, i, j), b = node(mb, g, id, i + 1, j), c = node(mb, g, id, i + 1, j + 1), d = node(mb, g, id, i, j + 1);
                mb.tri(a, b, c, Mat.TERRAIN);
                mb.tri(a, c, d, Mat.TERRAIN);
            }
        }
        // Säume an den Lochrändern: wo das feinere Gitter tiefer liegt, schließt dieser Saum den Spalt
        int keep0 = mb.group;
        mb.group = 1;
        for (int j = 0; j < g.nz; j++) {
            for (int i = 0; i < g.nx; i++) {
                if (g.inHole(i, j)) continue;
                if (i + 1 < g.nx && g.inHole(i + 1, j)) holeSkirt(mb, g, id, i + 1, j, i + 1, j + 1);
                if (i > 0 && g.inHole(i - 1, j)) holeSkirt(mb, g, id, i, j, i, j + 1);
                if (j + 1 < g.nz && g.inHole(i, j + 1)) holeSkirt(mb, g, id, i, j + 1, i + 1, j + 1);
                if (j > 0 && g.inHole(i, j - 1)) holeSkirt(mb, g, id, i, j, i + 1, j);
            }
        }
        mb.group = keep0;
        if (!skirt) { mb.skyFn = null; return; }
        // Saum: am Außenrand senkrecht 12 m hinunter, von beiden Seiten sichtbar
        int keep = mb.group;
        mb.group = 1;
        for (int side = 0; side < 4; side++) {
            int n = side < 2 ? g.nx : g.nz;
            for (int k = 0; k < n; k++) {
                int i0, j0, i1, j1;
                switch (side) {
                    case 0: i0 = k; j0 = 0; i1 = k + 1; j1 = 0; break;
                    case 1: i0 = k; j0 = g.nz; i1 = k + 1; j1 = g.nz; break;
                    case 2: i0 = 0; j0 = k; i1 = 0; j1 = k + 1; break;
                    default: i0 = g.nx; j0 = k; i1 = g.nx; j1 = k + 1;
                }
                int a = node(mb, g, id, i0, j0), b = node(mb, g, id, i1, j1);
                int p = j0 * w + i0, q = j1 * w + i1;
                int c = mb.v(g.x0 + i1 * g.cell, g.h[q] - 12, g.z0 + j1 * g.cell, g.nxs[q], g.nys[q], g.nzs[q]);
                int d = mb.v(g.x0 + i0 * g.cell, g.h[p] - 12, g.z0 + j0 * g.cell, g.nxs[p], g.nys[p], g.nzs[p]);
                mb.tri(a, b, c, Mat.TERRAIN);
                mb.tri(a, c, d, Mat.TERRAIN);
            }
        }
        mb.group = keep;
        mb.skyFn = null;
    }

    /** Senkrechter Saum 12 m hinunter entlang der Kante (i0,j0)–(i1,j1). */
    private static void holeSkirt(MeshBuilder mb, Terrain.Grid g, int[] id, int i0, int j0, int i1, int j1) {
        int w = g.nx + 1;
        int a = node(mb, g, id, i0, j0), b = node(mb, g, id, i1, j1);
        int p = j0 * w + i0, q = j1 * w + i1;
        int c = mb.v(g.x0 + i1 * g.cell, g.h[q] - 12, g.z0 + j1 * g.cell, g.nxs[q], g.nys[q], g.nzs[q]);
        int d = mb.v(g.x0 + i0 * g.cell, g.h[p] - 12, g.z0 + j0 * g.cell, g.nxs[p], g.nys[p], g.nzs[p]);
        mb.tri(a, b, c, Mat.TERRAIN);
        mb.tri(a, c, d, Mat.TERRAIN);
    }

    private static int node(MeshBuilder mb, Terrain.Grid g, int[] id, int i, int j) {
        int p = j * (g.nx + 1) + i;
        if (id[p] < 0) id[p] = mb.v(g.x0 + i * g.cell, g.h[p], g.z0 + j * g.cell, g.nxs[p], g.nys[p], g.nzs[p]);
        return id[p];
    }

    /** Himmelssicht der Geländeknoten; das Raster wird parallel vorgerechnet. */
    private static float[] skyOf(Terrain t, Terrain.Grid g) {
        int w = g.nx + 1;
        float[] s = new float[w * (g.nz + 1)];
        java.util.stream.IntStream.range(0, g.nz + 1).parallel().forEach(j -> {
            for (int i = 0; i < w; i++) s[j * w + i] = t.skyView(g.x0 + i * g.cell, g.z0 + j * g.cell, g.h[j * w + i]);
        });
        return s;
    }

    /**
     * Seefläche: ein Raster aus Zellen auf Stauziel (Gruppe 3, wird vom Pegel gehoben und gesenkt) über allen
     * Zellen oberhalb der Mauer, in denen der Grund niedriger liegt als der höchste Pegel. Das Gelände verdeckt
     * die Fläche dort, wo es höher ist; so entsteht die Uferlinie bei jedem Pegel von selbst.
     */
    private static void lake(MeshBuilder mb, Terrain t, Terrain.Grid g) {
        int w = g.nx + 1;
        int keep = mb.group;
        mb.group = 3;
        double me = mb.maxEdge;
        mb.maxEdge = 1e6;
        for (int j = 0; j < g.nz; j++) {
            double z0 = g.z0 + j * g.cell;
            if (z0 + g.cell < -300 + Dam.R * 0.5) continue;
            for (int i = 0; i < g.nx; i++) {
                if (g.inHole(i, j)) continue;
                double x0 = g.x0 + i * g.cell;
                // ganz oberhalb der Mauer (Abstand vom Bogenmittelpunkt über Radius + 12 m)
                double xa = Math.min(Math.abs(x0), Math.abs(x0 + g.cell)), za = z0 + g.cell > -300 && z0 < -300 ? 0 : Math.min(Math.abs(z0 + 300), Math.abs(z0 + g.cell + 300));
                if (z0 < -300 || Math.hypot(xa, za) < Dam.R + 12) continue;
                int a = j * w + i;
                float lo = Math.min(Math.min(g.h[a], g.h[a + 1]), Math.min(g.h[a + w], g.h[a + w + 1]));
                if (lo >= 3.0f) continue;
                mb.rectH(x0, z0, x0 + g.cell, z0 + g.cell, 0, true, Mat.WATER);
            }
        }
        mb.maxEdge = me;
        mb.group = keep;
    }

    /** Die Eder als Wasserband auf Höhe des Wasserspiegels; wo der Boden höher liegt, hebt sich das Band. */
    private static void river(MeshBuilder mb, Terrain t) {
        int n = t.riverPoints();
        int[] prev = null;
        for (int i = 0; i < n; i++) {
            double x = t.riverX(i), z = t.riverZ(i);
            if (x < t.mid.x0 + 10 || x > t.mid.x1() - 10 || z < t.mid.z0 + 10 || z > t.mid.z1() - 10) { prev = null; continue; }
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            double dx = t.riverX(b) - t.riverX(a), dz = t.riverZ(b) - t.riverZ(a);
            double l = Math.hypot(dx, dz);
            double px = -dz / l, pz = dx / l;
            double half = t.riverHalf(i) * 1.25;
            double y = t.riverLevel(i);
            double lift = Math.max(0, t.sample(x, z) + 0.3 - y);
            y += lift;
            int[] cur = {
                    mb.v(x + px * half, y, z + pz * half, 0, 1, 0),
                    mb.v(x, y, z, 0, 1, 0),
                    mb.v(x - px * half, y, z - pz * half, 0, 1, 0)};
            if (prev != null) {
                for (int k = 0; k < 2; k++) {
                    mb.tri(prev[k], cur[k], cur[k + 1], Mat.RIVER);
                    mb.tri(prev[k], cur[k + 1], prev[k + 1], Mat.RIVER);
                }
            }
            prev = cur;
        }
    }
}
