package com.dan.forest;

/**
 * Bewegt einen Baum im Wind, in drei Stufen wie bei SpeedTree und Unreal: der Stamm biegt sich
 * langsam mit dem Wind und pendelt, die Äste schwingen je nach Länge und Ordnung schneller und wippen
 * auf und ab, die Blätter flattern um ihren Stiel. Jede Bewegung wird vom Elternast an seine Kinder
 * weitergegeben (hierarchisch, wie „Pivot Painter“). Abgefallene Blätter fallen auf ihren Ansatzpunkt
 * zusammen (entartete Dreiecke, die ein Zeichner überspringt).
 * <p>
 * Ein Animator gehört zu einer Stufe eines Baums (die Ergebnisfelder werden wiederverwendet); für
 * mehrere Bäume mit demselben Netz je einen Animator anlegen.
 */
public final class TreeAnimator {
    public final TreeMesh mesh;
    /** Bewegte Lagen und Normalen (3 je Punkt), nach {@link #pose}. */
    public final float[] pos, nrm;
    private final int nb;
    private final float[] M, T, W, P;  // je Ast: Drehung (9), Verschiebung (3), Drehvektor (3), Fußpunkt (3)
    private final float[] dir;          // Richtung am Fuß (3)
    private final float[] leafRot;      // je Blatt: Drehvektor des Flatterns (3)

    public TreeAnimator(TreeMesh mesh) {
        this.mesh = mesh;
        pos = new float[3 * mesh.nv];
        nrm = new float[3 * mesh.nv];
        nb = mesh.model.branches.size();
        M = new float[9 * nb]; T = new float[3 * nb]; W = new float[3 * nb]; P = new float[3 * nb]; dir = new float[3 * nb];
        float[] q = new float[7];
        for (TreeModel.Branch b : mesh.model.branches) {
            b.at(0, q);
            P[3 * b.id] = b.x[0]; P[3 * b.id + 1] = b.y[0]; P[3 * b.id + 2] = b.z[0];
            dir[3 * b.id] = q[4]; dir[3 * b.id + 1] = q[5]; dir[3 * b.id + 2] = q[6];
        }
        leafRot = new float[3 * Math.max(1, mesh.leafIds.length)];
        System.arraycopy(mesh.pos, 0, pos, 0, 3 * mesh.nv);
        System.arraycopy(mesh.nrm, 0, nrm, 0, 3 * mesh.nv);
    }

    /**
     * Stellt den Baum zur Zeit t in den Wind w (im Rahmen des Baums, siehe {@link WindField.Sample#local});
     * leafScale je Blatt der Stufe (0 = gefallen) oder null für alle voll.
     */
    public void pose(double t, WindField.Sample w, float[] leafScale) {
        TreeModel m = mesh.model;
        Species sp = m.species;
        float v = w.speed / 10f;
        float wx = w.dx, wz = w.dz;
        float H = Math.max(3, m.height);
        // ------------------------------------------------ Äste: Drehvektor je Ast
        for (TreeModel.Branch b : m.branches) {
            int i = b.id;
            float wX, wY, wZ;
            if (b.level == 0) {
                // Stamm: neigt sich mit dem Wind (Achse waagrecht quer zum Wind) und pendelt langsam
                float f0 = 0.28f * (float) Math.sqrt(20 / H);
                float lean = sp.flexTrunk * 0.010f * v * v * (H / 20);
                float osc = sp.flexTrunk * 0.006f * v * (0.4f + v) * (float) Math.sin(2 * Math.PI * f0 * t + b.phase);
                float side = 0.35f * sp.flexTrunk * 0.004f * v * (float) Math.sin(2 * Math.PI * f0 * 0.77 * t + b.phase * 1.7f);
                float th = (lean + osc) * (0.7f + 0.3f * w.gust);
                wX = wz * th + wx * side; wY = 0; wZ = -wx * th + wz * side;
            } else {
                float dx = dir[3 * i], dy = dir[3 * i + 1], dz = dir[3 * i + 2];
                float len = Math.max(0.1f, b.length), r0 = Math.max(0.003f, b.r[0]);
                float lv = b.level == 1 ? 0.05f : b.level == 2 ? 0.08f : 0.12f;
                float f = sp.flexBranch * lv * Math.max(0.3f, Math.min(3f, len / (r0 * 70)));
                float freq = (b.level == 1 ? 0.55f : b.level == 2 ? 1.1f : 1.9f) / (float) Math.sqrt(Math.max(0.4f, len / 2));
                // zum Wind hin: Achse d × w
                float ax = dy * wz - dz * 0, ay = dz * wx - dx * wz, az = dx * 0 - dy * wx;
                float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
                float thW = f * (0.45f * v * v + 0.6f * v * (0.5f + 0.5f * (float) Math.sin(2 * Math.PI * freq * t + b.phase)) * (0.6f + 0.4f * w.gust));
                // auf und ab: Achse d × oben
                float bx = -dz, bz = dx;
                float bl = (float) Math.sqrt(bx * bx + bz * bz);
                float thU = f * v * 0.45f * (float) Math.sin(2 * Math.PI * freq * 1.31 * t + b.phase * 2.1f) * (0.5f + w.turb);
                wX = 0; wY = 0; wZ = 0;
                if (al > 1e-4f) { wX += ax / al * thW; wY += ay / al * thW; wZ += az / al * thW; }
                if (bl > 1e-4f) { wX += bx / bl * thU; wZ += bz / bl * thU; }
            }
            W[3 * i] = wX; W[3 * i + 1] = wY; W[3 * i + 2] = wZ;
        }
        // ------------------------------------------------ Rahmen je Ast (Eltern vor Kindern)
        float[] R = new float[9];
        for (TreeModel.Branch b : m.branches) {
            int i = b.id;
            if (b.parent < 0) { ident(M, i); T[3 * i] = T[3 * i + 1] = T[3 * i + 2] = 0; continue; }
            int p = b.parent;
            TreeModel.Branch pb = m.branches.get(p);
            float k = prof(pb.level, b.attach);
            rodrigues(W[3 * p] * k, W[3 * p + 1] * k, W[3 * p + 2] * k, R);
            // M_i = M_p · R ; T_i = M_p · (P_p − R·P_p) + T_p
            mul(M, p, R, M, i);
            float px = P[3 * p], py = P[3 * p + 1], pz = P[3 * p + 2];
            float rx = px - (R[0] * px + R[1] * py + R[2] * pz), ry = py - (R[3] * px + R[4] * py + R[5] * pz), rz = pz - (R[6] * px + R[7] * py + R[8] * pz);
            int o = 9 * p;
            T[3 * i] = M[o] * rx + M[o + 1] * ry + M[o + 2] * rz + T[3 * p];
            T[3 * i + 1] = M[o + 3] * rx + M[o + 4] * ry + M[o + 5] * rz + T[3 * p + 1];
            T[3 * i + 2] = M[o + 6] * rx + M[o + 7] * ry + M[o + 8] * rz + T[3 * p + 2];
        }
        // ------------------------------------------------ Blätter: Flattern um den Stiel
        int nl = mesh.leafIds.length;
        // Flattern: schon bei leichtem Wind (Espe), bei Windstille nicht
        float amp = sp.flutter * (0.06f * Math.min(1, v * 5) + 0.22f * v) * (0.5f + w.turb);
        for (int li = 0; li < nl; li++) {
            TreeModel.LeafSpot l = m.leaves.get(mesh.leafIds[li]);
            float fr = (2.5f + 3.5f * l.tone) * (sp.flutter > 2 ? 1.6f : 1);
            float a1 = amp * (float) Math.sin(2 * Math.PI * fr * t + l.phase), a2 = amp * 0.6f * (float) Math.sin(2 * Math.PI * fr * 1.37 * t + l.phase * 1.3f);
            // um die Blattachse drehen (Espe) und um die Querachse klappen
            float qx = l.dy * l.nz - l.dz * l.ny, qy = l.dz * l.nx - l.dx * l.nz, qz = l.dx * l.ny - l.dy * l.nx;
            leafRot[3 * li] = l.dx * a1 + qx * a2; leafRot[3 * li + 1] = l.dy * a1 + qy * a2; leafRot[3 * li + 2] = l.dz * a1 + qz * a2;
        }
        // ------------------------------------------------ Punkte
        float[] src = mesh.pos, sn = mesh.nrm;
        java.util.List<TreeModel.LeafSpot> leaves = m.leaves;
        for (int v0 = 0; v0 < mesh.nv; v0++) {
            float x = src[3 * v0], y = src[3 * v0 + 1], z = src[3 * v0 + 2];
            float nx = sn[3 * v0], ny = sn[3 * v0 + 1], nz = sn[3 * v0 + 2];
            int li = mesh.leaf[v0];
            if (li >= 0) {
                TreeModel.LeafSpot l = leaves.get(mesh.leafIds[li]);
                float sc = leafScale == null ? 1 : leafScale[li];
                float ux = (x - l.x) * sc, uy = (y - l.y) * sc, uz = (z - l.z) * sc;
                float fx = leafRot[3 * li], fy = leafRot[3 * li + 1], fz = leafRot[3 * li + 2];
                float[] r = rot(fx, fy, fz, ux, uy, uz);
                x = l.x + r[0]; y = l.y + r[1]; z = l.z + r[2];
                float[] rn = rot(fx, fy, fz, nx, ny, nz);
                nx = rn[0]; ny = rn[1]; nz = rn[2];
            }
            int b = mesh.bone[v0];
            TreeModel.Branch br = m.branches.get(b);
            float k = prof(br.level, mesh.param[v0]);
            float fx = W[3 * b] * k, fy = W[3 * b + 1] * k, fz = W[3 * b + 2] * k;
            float px = P[3 * b], py = P[3 * b + 1], pz = P[3 * b + 2];
            float[] r = rot(fx, fy, fz, x - px, y - py, z - pz);
            x = px + r[0]; y = py + r[1]; z = pz + r[2];
            float[] rn = rot(fx, fy, fz, nx, ny, nz);
            int o = 9 * b;
            pos[3 * v0] = M[o] * x + M[o + 1] * y + M[o + 2] * z + T[3 * b];
            pos[3 * v0 + 1] = M[o + 3] * x + M[o + 4] * y + M[o + 5] * z + T[3 * b + 1];
            pos[3 * v0 + 2] = M[o + 6] * x + M[o + 7] * y + M[o + 8] * z + T[3 * b + 2];
            nrm[3 * v0] = M[o] * rn[0] + M[o + 1] * rn[1] + M[o + 2] * rn[2];
            nrm[3 * v0 + 1] = M[o + 3] * rn[0] + M[o + 4] * rn[1] + M[o + 5] * rn[2];
            nrm[3 * v0 + 2] = M[o + 6] * rn[0] + M[o + 7] * rn[1] + M[o + 8] * rn[2];
        }
    }

    /** Biegung entlang des Asts: der Fuß steht, zur Spitze hin mehr (der Stamm krümmt sich stärker). */
    static float prof(int level, float s) {
        s = Math.max(0, Math.min(1, s));
        return level == 0 ? s * (float) Math.sqrt(s) : s;
    }

    private final float[] tmp = new float[3];

    /** Kleine Drehung um den Drehvektor f (Näherung zweiter Ordnung) von u. */
    private float[] rot(float fx, float fy, float fz, float ux, float uy, float uz) {
        float cx = fy * uz - fz * uy, cy = fz * ux - fx * uz, cz = fx * uy - fy * ux;
        float ddx = fy * cz - fz * cy, ddy = fz * cx - fx * cz, ddz = fx * cy - fy * cx;
        tmp[0] = ux + cx + 0.5f * ddx; tmp[1] = uy + cy + 0.5f * ddy; tmp[2] = uz + cz + 0.5f * ddz;
        return tmp;
    }

    private static void ident(float[] M, int i) {
        int o = 9 * i;
        for (int k = 0; k < 9; k++) M[o + k] = (k % 4 == 0) ? 1 : 0;
    }

    /** Drehmatrix zum Drehvektor (x, y, z) nach Rodrigues. */
    static void rodrigues(float x, float y, float z, float[] R) {
        float a = (float) Math.sqrt(x * x + y * y + z * z);
        if (a < 1e-7f) { for (int k = 0; k < 9; k++) R[k] = (k % 4 == 0) ? 1 : 0; return; }
        float kx = x / a, ky = y / a, kz = z / a, c = (float) Math.cos(a), s = (float) Math.sin(a), t = 1 - c;
        R[0] = c + kx * kx * t; R[1] = kx * ky * t - kz * s; R[2] = kx * kz * t + ky * s;
        R[3] = ky * kx * t + kz * s; R[4] = c + ky * ky * t; R[5] = ky * kz * t - kx * s;
        R[6] = kz * kx * t - ky * s; R[7] = kz * ky * t + kx * s; R[8] = c + kz * kz * t;
    }

    /** M[i] = M[p] · R. */
    private static void mul(float[] A, int p, float[] R, float[] out, int i) {
        int o = 9 * p, q = 9 * i;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++)
            out[q + 3 * r + c] = A[o + 3 * r] * R[c] + A[o + 3 * r + 1] * R[3 + c] + A[o + 3 * r + 2] * R[6 + c];
    }

    /** Wo ein Punkt der Ruhelage (Ast b, Stelle s) jetzt ist (für abfallende Blätter); out = x, y, z. */
    public void place(int b, float s, float x, float y, float z, float[] out) {
        TreeModel.Branch br = mesh.model.branches.get(b);
        float k = prof(br.level, s);
        float px = P[3 * b], py = P[3 * b + 1], pz = P[3 * b + 2];
        float[] r = rot(W[3 * b] * k, W[3 * b + 1] * k, W[3 * b + 2] * k, x - px, y - py, z - pz);
        x = px + r[0]; y = py + r[1]; z = pz + r[2];
        int o = 9 * b;
        out[0] = M[o] * x + M[o + 1] * y + M[o + 2] * z + T[3 * b];
        out[1] = M[o + 3] * x + M[o + 4] * y + M[o + 5] * z + T[3 * b + 1];
        out[2] = M[o + 6] * x + M[o + 7] * y + M[o + 8] * z + T[3 * b + 2];
    }
}
