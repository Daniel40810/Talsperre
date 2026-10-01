package com.dan.forest;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Skelett eines Baums: Äste als Linienzüge mit Dicke (Eltern stehen vor ihren Kindern) und die
 * Stellen, an denen Blätter oder Nadelbüschel sitzen. Koordinaten in Metern, der Fuß des Stamms bei
 * (0, 0, 0), y nach oben. Aus dem Skelett macht {@link TreeMesh} die Geometrie je Detailstufe.
 */
public final class TreeModel {
    /** Ein Ast (Stamm = Ordnung 0): Knoten, Radien, Ansatz am Elternast. */
    public static final class Branch {
        public final int id, parent, level;
        /** Ansatzstelle auf dem Elternast (0 Fuß .. 1 Spitze). */
        public final float attach;
        public final float[] x, y, z, r;
        /** Länge bis zu jedem Knoten und gesamt. */
        public final float[] cum;
        public final float length;
        /** Zufallsphase fürs Schwingen, relative Länge (0..1 zur längsten ihrer Ordnung). */
        public float phase, rel = 1;

        Branch(int id, int parent, int level, float attach, float[] x, float[] y, float[] z, float[] r) {
            this.id = id; this.parent = parent; this.level = level; this.attach = attach;
            this.x = x; this.y = y; this.z = z; this.r = r;
            cum = new float[x.length];
            for (int i = 1; i < x.length; i++) cum[i] = cum[i - 1] + (float) Math.sqrt(sq(x[i] - x[i - 1]) + sq(y[i] - y[i - 1]) + sq(z[i] - z[i - 1]));
            length = cum[x.length - 1];
        }

        public int nodes() { return x.length; }

        /** Punkt, Radius und Richtung bei Parameter s (0..1 der Länge); out = x, y, z, r, dx, dy, dz. */
        public void at(float s, float[] out) {
            float d = Math.max(0, Math.min(1, s)) * length;
            int i = 0;
            while (i < x.length - 2 && cum[i + 1] < d) i++;
            float seg = Math.max(1e-6f, cum[i + 1] - cum[i]), u = Math.max(0, Math.min(1, (d - cum[i]) / seg));
            out[0] = x[i] + (x[i + 1] - x[i]) * u; out[1] = y[i] + (y[i + 1] - y[i]) * u; out[2] = z[i] + (z[i + 1] - z[i]) * u;
            out[3] = r[i] + (r[i + 1] - r[i]) * u;
            float dx = x[i + 1] - x[i], dy = y[i + 1] - y[i], dz = z[i + 1] - z[i], l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            out[4] = dx / l; out[5] = dy / l; out[6] = dz / l;
        }

        private static float sq(float v) { return v * v; }
    }

    /** Eine Blattstelle: Ast und Parameter, Ansatzpunkt, Richtung des Blatts, Blattfläche zeigt nach n. */
    public static final class LeafSpot {
        public int branch;
        public float s, x, y, z, dx, dy, dz, nx, ny, nz, size;
        /** Zufall 0..1: Farbton, Reihenfolge des Abfallens, Phase des Flatterns. */
        public float tone, drop, phase;
    }

    public final Species species;
    public final long seed;
    public final List<Branch> branches = new ArrayList<>();
    public final List<LeafSpot> leaves = new ArrayList<>();

    /** Ein Zapfen: Ast, Stelle, Mitte, Richtung (nach oben oder hängend), Länge. */
    public static final class Cone {
        public int branch;
        public float s, x, y, z, dx, dy, dz, size;
    }

    public final List<Cone> cones = new ArrayList<>();
    /** Höhe und größter Radius der Krone (m). */
    public float height, crownRadius;

    TreeModel(Species species, long seed) { this.species = species; this.seed = seed; }

    Branch add(int parent, int level, float attach, float[] x, float[] y, float[] z, float[] r) {
        Branch b = new Branch(branches.size(), parent, level, attach, x, y, z, r);
        branches.add(b);
        return b;
    }
}
