package com.dan.forest;

/**
 * Dreiecke eines Baums in einer Detailstufe, in Ruhelage und in Baumkoordinaten (Fuß bei 0, y nach
 * oben). Jeder Punkt kennt den Ast, an dem er hängt, und die Stelle darauf (für den Wind), Blattpunkte
 * zusätzlich ihr Blatt (fürs Flattern und Abfallen). Die Farbe je Punkt ist linear und schon mit dem
 * Umgebungslicht in der Krone abgedunkelt; {@link Season} färbt die Blätter nach dem Tag im Jahr.
 * <p>
 * Detailstufen: 0 alles (nah), 1 ohne die feinsten Triebe und mit weniger, größeren Blättern (mittel),
 * 2 Stamm, Hauptäste und je Ast ein Laubballen (fern), 3 nur die Silhouette: Stamm und die Krone als
 * Drehkörper nach ihrem Umriss (sehr fern, für Tausende Bäume). Blätter und Ballen sind zweiseitig: ein
 * Zeichner sollte sie nicht nach der Rückseite verwerfen ({@link #doubleSided}).
 */
public final class TreeMesh {
    public static final byte BARK = 0, LEAF = 1, NEEDLES = 2, CLUMP = 3, CONE = 4;

    public final TreeModel model;
    public final int lod;
    public int nv, nt;
    /** Je Punkt: Lage, Normale, Farbe (je 3), Ast, Stelle auf dem Ast, Blatt (−1: keins), Art der Fläche. */
    public float[] pos = new float[3 * 1024], nrm = new float[3 * 1024], col = new float[3 * 1024], param = new float[1024];
    public int[] bone = new int[1024], leaf = new int[1024];
    public byte[] part = new byte[1024];
    /** Je Dreieck drei Punkte und ob es zweiseitig ist. */
    public int[] tri = new int[3 * 2048];
    public boolean[] doubleSided = new boolean[2048];
    /** Blätter dieser Stufe (Index in model.leaves) und ihre Größe in dieser Stufe. */
    public int[] leafIds = new int[0];
    public float[] leafScale = new float[0];
    /** Je Blatt der Stufe: sein erster Punkt (Ast und Stelle, an denen es in dieser Stufe hängt). */
    public int[] leafVertex = new int[0];

    private TreeMesh(TreeModel model, int lod) { this.model = model; this.lod = lod; }

    /** Baut die Geometrie der Stufe lod (0 bis 3). */
    public static TreeMesh build(TreeModel m, int lod) { return build(m, lod, lod); }

    /**
     * Wie {@link #build(TreeModel, int)}, aber Stamm und Äste in der Stufe barkLod (0 bis 2, nicht
     * feiner als lod): etwa Blätter der Stufe 1 an Ästen der Stufe 2 für viele Laubbäume in mittlerer
     * Entfernung. Blätter an weggelassenen Zweigen bleiben an ihrem Ort.
     */
    public static TreeMesh build(TreeModel m, int lod, int barkLod) {
        TreeMesh t = new TreeMesh(m, lod);
        if (lod >= 3) return silhouette(m, 6, 7);
        barkLod = Math.max(lod, Math.min(2, barkLod));
        Species sp = m.species;
        int[][] sides = {{10, 6, 4, 3}, {6, 4, 3, 3}, {5, 3, 3, 3}};
        int skipBark = barkLod == 0 ? 99 : barkLod == 1 ? 3 : 2;
        for (TreeModel.Branch b : m.branches) {
            if (b.level >= skipBark) continue;
            if (barkLod == 2 && b.level == 1 && b.rel < 0.35f) continue;
            t.tube(b, sides[barkLod][Math.min(3, b.level)]);
        }
        int skipLevel = lod == 0 ? 99 : lod == 1 ? 3 : 2;
        java.util.Random rnd = new java.util.Random(m.seed ^ 0x5DEECE66DL);
        if (lod < 2) {
            java.util.List<Integer> ids = new java.util.ArrayList<>(), first = new java.util.ArrayList<>();
            java.util.List<Float> sc = new java.util.ArrayList<>();
            for (int i = 0; i < m.leaves.size(); i++) {
                TreeModel.LeafSpot l = m.leaves.get(i);
                float scale = 1;
                if (lod == 1) {
                    if (rnd.nextFloat() > (sp.conifer ? 0.55f : 0.4f)) continue;
                    scale = sp.conifer ? 1.35f : 1.55f;
                }
                int bone = l.branch;
                float s = l.s;
                // Blätter an weggelassenen Trieben hängen am nächsten gezeigten Elternast
                while (m.branches.get(bone).level >= skipLevel) { TreeModel.Branch bb = m.branches.get(bone); s = bb.attach; bone = bb.parent; }
                first.add(t.nv);
                t.leafGeom(l, ids.size(), bone, s, scale);
                ids.add(i);
                sc.add(scale);
            }
            // Nadelbäume: dunkle Ballen innen an den Ästen füllen die Lücken zwischen den Bürsten
            if (sp.conifer) t.clumps(0.6f, 0.45f);
            for (TreeModel.Cone c : m.cones) t.cone(c);
            t.leafIds = new int[ids.size()];
            t.leafScale = new float[ids.size()];
            t.leafVertex = new int[ids.size()];
            for (int i = 0; i < ids.size(); i++) { t.leafIds[i] = ids.get(i); t.leafScale[i] = sc.get(i); t.leafVertex[i] = first.get(i); }
        } else {
            t.clumps(1, 1);
        }
        t.ambient();
        return t;
    }

    // ------------------------------------------------------------ Bauteile

    private int vertex(float x, float y, float z, float nx, float ny, float nz, float r, float g, float b, int bone, float s, int leafIdx, byte kind) {
        if (nv == bone().length) grow();
        int i = nv++;
        pos[3 * i] = x; pos[3 * i + 1] = y; pos[3 * i + 2] = z;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9f;
        nrm[3 * i] = nx / l; nrm[3 * i + 1] = ny / l; nrm[3 * i + 2] = nz / l;
        col[3 * i] = r; col[3 * i + 1] = g; col[3 * i + 2] = b;
        this.bone[i] = bone; param[i] = s; leaf[i] = leafIdx; part[i] = kind;
        return i;
    }

    private int[] bone() { return bone; }

    private void triangle(int a, int b, int c, boolean two) {
        if (3 * nt + 3 > tri.length) { tri = java.util.Arrays.copyOf(tri, tri.length * 2); doubleSided = java.util.Arrays.copyOf(doubleSided, doubleSided.length * 2); }
        tri[3 * nt] = a; tri[3 * nt + 1] = b; tri[3 * nt + 2] = c;
        doubleSided[nt] = two;
        nt++;
    }

    private void grow() {
        int c = bone.length * 2;
        pos = java.util.Arrays.copyOf(pos, 3 * c); nrm = java.util.Arrays.copyOf(nrm, 3 * c); col = java.util.Arrays.copyOf(col, 3 * c);
        param = java.util.Arrays.copyOf(param, c); bone = java.util.Arrays.copyOf(bone, c); leaf = java.util.Arrays.copyOf(leaf, c);
        part = java.util.Arrays.copyOf(part, c);
    }

    /** Ast als Röhre mit n Seiten, Ringe an den Knoten, im Parallelrahmen mitgeführt (ohne Verdrehen). */
    private void tube(TreeModel.Branch b, int n) {
        Species sp = model.species;
        int k = b.nodes();
        float ux = 1, uy = 0, uz = 0;
        int prev = -1;
        for (int i = 0; i < k; i++) {
            int a = Math.max(0, i - 1), c = Math.min(k - 1, i + 1);
            float tx = b.x[c] - b.x[a], ty = b.y[c] - b.y[a], tz = b.z[c] - b.z[a];
            float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz) + 1e-9f;
            tx /= tl; ty /= tl; tz /= tl;
            // u senkrecht zu t halten
            float d = ux * tx + uy * ty + uz * tz;
            ux -= d * tx; uy -= d * ty; uz -= d * tz;
            float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
            if (ul < 1e-4f) { ux = -tz; uy = 0; uz = tx; if (Math.abs(ux) + Math.abs(uz) < 1e-4f) { ux = 1; uz = 0; } ul = (float) Math.sqrt(ux * ux + uz * uz); }
            ux /= ul; uy /= ul; uz /= ul;
            float vx = ty * uz - tz * uy, vy = tz * ux - tx * uz, vz = tx * uy - ty * ux;
            float s = b.length > 0 ? b.cum[i] / b.length : 0;
            int first = nv;
            for (int j = 0; j < n; j++) {
                double an = 2 * Math.PI * j / n;
                float cs = (float) Math.cos(an), sn = (float) Math.sin(an);
                float nx = ux * cs + vx * sn, ny = uy * cs + vy * sn, nz = uz * cs + vz * sn;
                float[] c3 = barkColor(sp, b.x[i] + nx * b.r[i], b.y[i], b.z[i] + nz * b.r[i], j, model.height);
                vertex(b.x[i] + nx * b.r[i], b.y[i] + ny * b.r[i], b.z[i] + nz * b.r[i], nx, ny, nz, c3[0], c3[1], c3[2], b.id, s, -1, BARK);
            }
            if (prev >= 0) {
                for (int j = 0; j < n; j++) {
                    int j1 = (j + 1) % n;
                    triangle(prev + j, first + j1, prev + j1, false);
                    triangle(prev + j, first + j, first + j1, false);
                }
            }
            prev = first;
        }
    }

    /** Rinde mit etwas Streuung; Birke und Espe mit dunklen Querbändern; oben andere Farbe (Waldkiefer). */
    private static float[] barkColor(Species sp, float x, float y, float z, int j, float H) {
        float n = Noise2.value(x * 3.1f + z * 2.3f, y * 1.7f);
        float k = 0.8f + 0.4f * n;
        float r = sp.bark[0] * k, g = sp.bark[1] * k, b = sp.bark[2] * k;
        if (sp.barkTop != null) {
            float from = sp.barkTopFrom * H;
            float u = Math.max(0, Math.min(1, (y - from) / (0.15f * H) + (n - 0.5f) * 0.6f));
            r += (sp.barkTop[0] * k - r) * u; g += (sp.barkTop[1] * k - g) * u; b += (sp.barkTop[2] * k - b) * u;
        }
        if (sp.barkMarks > 0) {
            float band = Noise2.value(y * 5.5f, (x + z) * 9.0f);
            if (band > 1 - sp.barkMarks) { r = sp.barkMark[0]; g = sp.barkMark[1]; b = sp.barkMark[2]; }
        }
        return new float[]{r, g, b};
    }

    /** Ein Blatt oder Nadelbüschel. */
    private void leafGeom(TreeModel.LeafSpot l, int li, int bone, float s, float scale) {
        Species sp = model.species;
        float L = l.size * scale;
        // Achsen: d entlang des Blatts, n Normale, w quer
        float dx = l.dx, dy = l.dy, dz = l.dz, nx = l.nx, ny = l.ny, nz = l.nz;
        float wx = dy * nz - dz * ny, wy = dz * nx - dx * nz, wz = dx * ny - dy * nx;
        float wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz) + 1e-9f;
        wx /= wl; wy /= wl; wz /= wl;
        float[] c = sp.summer;
        if (sp.leaf == Species.Leaf.NEEDLES) {
            // Flaschenbürste: zwei gekreuzte Streifen entlang des Zweigs, Nadeln stehen seitlich ab
            float half = L * 0.5f, reach = L * 0.55f;
            for (int k = 0; k < 2; k++) {
                float sx = k == 0 ? wx : nx, sy = k == 0 ? wy : ny, sz = k == 0 ? wz : nz;
                int a = vertex(l.x - dx * half - sx * reach, l.y - dy * half - sy * reach, l.z - dz * half - sz * reach, nx, ny, nz, c[0], c[1], c[2], bone, s, li, NEEDLES);
                int b = vertex(l.x + dx * half - sx * reach * 0.6f, l.y + dy * half - sy * reach * 0.6f, l.z + dz * half - sz * reach * 0.6f, nx, ny, nz, c[0] * 1.3f, c[1] * 1.3f, c[2] * 1.3f, bone, s, li, NEEDLES);
                int e = vertex(l.x + dx * half + sx * reach * 0.6f, l.y + dy * half + sy * reach * 0.6f, l.z + dz * half + sz * reach * 0.6f, nx, ny, nz, c[0] * 1.3f, c[1] * 1.3f, c[2] * 1.3f, bone, s, li, NEEDLES);
                int f = vertex(l.x - dx * half + sx * reach, l.y - dy * half + sy * reach, l.z - dz * half + sz * reach, nx, ny, nz, c[0], c[1], c[2], bone, s, li, NEEDLES);
                triangle(a, b, e, true);
                triangle(a, e, f, true);
            }
            return;
        }
        // Büschel: ein flacher Fächer um die Blattstelle, der Rand gezackt nach der Blattform
        int lobes, m = 14;
        float depth;
        switch (sp.leaf) {
            case ROUND: lobes = 9; depth = 0.18f; break;
            case LOBED: lobes = 7; depth = 0.38f; break;
            case SMALL: lobes = 11; depth = 0.28f; break;
            default: lobes = 8; depth = 0.3f;
        }
        float R = sp.cluster * 0.5f * scale * (0.75f + 0.5f * l.tone);
        // Mitte etwas vor der Stelle, leicht gewölbt; Rand hängt etwas
        float mx = l.x + dx * R * 0.5f, my = l.y + dy * R * 0.5f, mz = l.z + dz * R * 0.5f;
        int center = vertex(mx + nx * R * 0.12f, my + ny * R * 0.12f, mz + nz * R * 0.12f, nx, ny, nz, c[0] * 0.8f, c[1] * 0.8f, c[2] * 0.8f, bone, s, li, LEAF);
        int first = nv;
        for (int k = 0; k < m; k++) {
            double a = 2 * Math.PI * k / m + l.phase;
            float lobe = (float) Math.pow(Math.abs(Math.sin(a * lobes / 2 + l.phase)), 0.6);
            float rr = R * (1 - depth + depth * lobe) * (0.85f + 0.3f * Noise2.value(k * 1.3f, l.phase * 3));
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            float px = mx + (dx * ca + wx * sa * sp.leafAspect * 1.3f) * rr, py = my + (dy * ca + wy * sa * sp.leafAspect * 1.3f) * rr - R * 0.12f,
                    pz = mz + (dz * ca + wz * sa * sp.leafAspect * 1.3f) * rr;
            float lit = 0.95f + 0.2f * lobe;
            vertex(px, py, pz, nx + (ca * dx + sa * wx) * 0.35f, ny + 0.2f, nz + (ca * dz + sa * wz) * 0.35f, c[0] * lit, c[1] * lit, c[2] * lit, bone, s, li, LEAF);
        }
        for (int k = 0; k < m; k++) triangle(center, first + k, first + (k + 1) % m, true);
    }

    /** Zapfen: schlanke Doppelpyramide mit sechs Seiten entlang seiner Richtung. */
    private void cone(TreeModel.Cone c) {
        Species sp = model.species;
        float L = c.size, R = L * 0.28f;
        float ux = -c.dz, uz = c.dx, ul = (float) Math.sqrt(ux * ux + uz * uz);
        if (ul < 1e-4f) { ux = 1; uz = 0; ul = 1; }
        ux /= ul; uz /= ul;
        float uy = 0;
        float vx = c.dy * uz - c.dz * uy, vy = c.dz * ux - c.dx * uz, vz = c.dx * uy - c.dy * ux;
        float[] col = sp.coneColor;
        int base = vertex(c.x - c.dx * L * 0.45f, c.y - c.dy * L * 0.45f, c.z - c.dz * L * 0.45f, -c.dx, -c.dy, -c.dz, col[0] * 0.7f, col[1] * 0.7f, col[2] * 0.7f, c.branch, c.s, -1, CONE);
        int tip = vertex(c.x + c.dx * L * 0.55f, c.y + c.dy * L * 0.55f, c.z + c.dz * L * 0.55f, c.dx, c.dy, c.dz, col[0], col[1], col[2], c.branch, c.s, -1, CONE);
        int[] ring = new int[6];
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3;
            float ca = (float) Math.cos(a) * R, sa = (float) Math.sin(a) * R;
            float nx = ux * ca + vx * sa, ny = uy * ca + vy * sa, nz = uz * ca + vz * sa;
            ring[k] = vertex(c.x + nx, c.y + ny, c.z + nz, nx, ny, nz, col[0] * 1.1f, col[1] * 1.1f, col[2] * 1.1f, c.branch, c.s, -1, CONE);
        }
        for (int k = 0; k < 6; k++) {
            int k1 = (k + 1) % 6;
            triangle(tip, ring[k], ring[k1], false);
            triangle(base, ring[k1], ring[k], false);
        }
    }

    /**
     * Je Hauptast ein Laubballen (flaches Ellipsoid aus 20 Dreiecken) um seine Blätter; size skaliert
     * den Ballen, dark die Farbe (innen in der Krone ist es dunkel).
     */
    private void clumps(float size, float dark) {
        TreeModel m = model;
        Species sp = m.species;
        int nb = m.branches.size();
        float[] sx = new float[nb], sy = new float[nb], sz = new float[nb], s2 = new float[nb];
        int[] cnt = new int[nb];
        for (TreeModel.LeafSpot l : m.leaves) {
            int b = l.branch;
            while (m.branches.get(b).level > 1) b = m.branches.get(b).parent;
            sx[b] += l.x; sy[b] += l.y; sz[b] += l.z; cnt[b]++;
        }
        for (TreeModel.LeafSpot l : m.leaves) {
            int b = l.branch;
            while (m.branches.get(b).level > 1) b = m.branches.get(b).parent;
            float cx = sx[b] / cnt[b], cy = sy[b] / cnt[b], cz = sz[b] / cnt[b];
            s2[b] += (l.x - cx) * (l.x - cx) + (l.y - cy) * (l.y - cy) + (l.z - cz) * (l.z - cz);
        }
        float[] c = sp.summer;
        for (int b = 0; b < nb; b++) {
            if (cnt[b] == 0) continue;
            float cx = sx[b] / cnt[b], cy = sy[b] / cnt[b], cz = sz[b] / cnt[b];
            float rad = ((float) Math.sqrt(s2[b] / cnt[b]) * 1.5f + sp.leafSize) * size;
            float flat = sp.conifer ? 0.45f : 0.75f;
            icosa(cx, cy, cz, rad, rad * flat, b, new float[]{c[0] * dark, c[1] * dark, c[2] * dark});
        }
    }

    /**
     * Stufe 3: Stamm als Prisma bis in die Krone, die Krone als Drehkörper mit sides Seiten und rings
     * Ringen; der Radius je Höhe ist der Umriss der Blätter (90 % liegen innerhalb). Mit wenigen Seiten
     * und Ringen (etwa 5 und 4, gut 50 Dreiecke) für sehr viele Bäume.
     */
    public static TreeMesh silhouette(TreeModel model, int sides, int rings) {
        TreeMesh t = new TreeMesh(model, 3);
        t.silhouetteGeom(sides, rings);
        t.ambient();
        return t;
    }

    private void silhouetteGeom(int sides, int rings) {
        TreeModel m = model;
        Species sp = m.species;
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (TreeModel.LeafSpot l : m.leaves) { lo = Math.min(lo, l.y); hi = Math.max(hi, l.y); }
        if (m.leaves.isEmpty()) { lo = m.height * 0.5f; hi = m.height; }
        hi = Math.max(hi, m.height * 0.98f);
        int bins = rings;
        float[] rad = new float[bins + 1];
        java.util.List<java.util.List<Float>> per = new java.util.ArrayList<>();
        for (int i = 0; i <= bins; i++) per.add(new java.util.ArrayList<>());
        for (TreeModel.LeafSpot l : m.leaves) {
            int i = Math.round((l.y - lo) / Math.max(1e-3f, hi - lo) * bins);
            per.get(Math.max(0, Math.min(bins, i))).add((float) Math.hypot(l.x, l.z) + l.size * 0.5f);
        }
        for (int i = 0; i <= bins; i++) {
            java.util.List<Float> v = per.get(i);
            if (v.isEmpty()) { rad[i] = i == bins ? 0 : -1; continue; }
            java.util.Collections.sort(v);
            rad[i] = v.get(Math.min(v.size() - 1, (int) (v.size() * 0.9f)));
        }
        for (int i = 0; i <= bins; i++) if (rad[i] < 0) rad[i] = i > 0 ? rad[i - 1] : 0.5f;
        rad[bins] = sp.conifer ? 0.05f : rad[bins] * 0.5f;
        // Stamm bis zum Kronenansatz
        TreeModel.Branch tr = m.branches.get(0);
        float r0 = tr.r[0];
        float[] c = sp.bark;
        int tb = nv;
        for (int j = 0; j < 4; j++) {
            double an = Math.PI / 2 * j;
            float cx = (float) Math.cos(an), cz = (float) Math.sin(an);
            vertex(cx * r0, 0, cz * r0, cx, 0, cz, c[0], c[1], c[2], 0, 0, -1, BARK);
            vertex(cx * r0 * 0.6f, lo + (hi - lo) * 0.3f, cz * r0 * 0.6f, cx, 0, cz, c[0], c[1], c[2], 0, (lo + (hi - lo) * 0.3f) / m.height, -1, BARK);
        }
        for (int j = 0; j < 4; j++) {
            int a = tb + 2 * j, b = tb + 2 * ((j + 1) % 4);
            triangle(a, b + 1, b, false);
            triangle(a, a + 1, b + 1, false);
        }
        // Krone: Ringe von unten (geschlossen) nach oben (Spitze)
        float[] lc = sp.summer;
        int prev = vertex(0, lo - (hi - lo) * 0.04f, 0, 0, -1, 0, lc[0], lc[1], lc[2], 0, lo / m.height, -1, CLUMP);
        boolean firstRing = true;
        for (int i = 0; i <= bins; i++) {
            float y = lo + (hi - lo) * i / bins;
            int ring = nv;
            for (int j = 0; j < sides; j++) {
                double an = 2 * Math.PI * (j + 0.5 * (i & 1)) / sides;
                float cx = (float) Math.cos(an), cz = (float) Math.sin(an);
                float jag = 0.85f + 0.3f * Noise2.value(i * 1.7f + j * 3.1f, m.seed % 97);
                float rr = rad[i] * jag;
                float ny = sp.conifer ? 0.45f : 0.2f + 0.6f * (i / (float) bins);
                vertex(cx * rr, y, cz * rr, cx, ny, cz, lc[0], lc[1], lc[2], 0, y / m.height, -1, CLUMP);
            }
            if (firstRing) {
                for (int j = 0; j < sides; j++) triangle(prev, ring + (j + 1) % sides, ring + j, true);
                firstRing = false;
            } else {
                int pr = ring - sides;
                for (int j = 0; j < sides; j++) {
                    int j1 = (j + 1) % sides;
                    triangle(pr + j, ring + j1, pr + j1, true);
                    triangle(pr + j, ring + j, ring + j1, true);
                }
            }
        }
        int top = vertex(0, hi + (sp.conifer ? m.height * 0.03f : 0), 0, 0, 1, 0, lc[0], lc[1], lc[2], 0, 1, -1, CLUMP);
        int last = nv - 1 - sides;
        for (int j = 0; j < sides; j++) triangle(last + j, top, last + (j + 1) % sides, true);
    }

    private static final float[][] ICO;
    private static final int[] ICO_T = {0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
            3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1};

    static {
        float p = (float) ((1 + Math.sqrt(5)) / 2);
        float[][] v = {{-1, p, 0}, {1, p, 0}, {-1, -p, 0}, {1, -p, 0}, {0, -1, p}, {0, 1, p}, {0, -1, -p}, {0, 1, -p}, {p, 0, -1}, {p, 0, 1}, {-p, 0, -1}, {-p, 0, 1}};
        for (float[] q : v) { float l = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]); q[0] /= l; q[1] /= l; q[2] /= l; }
        ICO = v;
    }

    private void icosa(float cx, float cy, float cz, float rh, float rv, int bone, float[] c) {
        int first = nv;
        for (float[] q : ICO) {
            float jag = 0.85f + 0.3f * Noise2.value(cx + q[0] * 3, cz + q[2] * 3 + q[1]);
            vertex(cx + q[0] * rh * jag, cy + q[1] * rv * jag, cz + q[2] * rh * jag, q[0], q[1], q[2], c[0], c[1], c[2], bone, 0.8f, -1, CLUMP);
        }
        for (int i = 0; i < ICO_T.length; i += 3) triangle(first + ICO_T[i], first + ICO_T[i + 2], first + ICO_T[i + 1], true);
    }

    /**
     * Umgebungslicht: innen in der Krone und unten am Stamm ist es dunkler. Aus dem Abstand zur
     * Stammachse im Verhältnis zum Kronenradius in dieser Höhe; gespeichert als Faktor in ao[].
     */
    public float[] ao;

    private void ambient() {
        ao = new float[nv];
        float H = model.height, R = model.crownRadius;
        for (int i = 0; i < nv; i++) {
            float x = pos[3 * i], y = pos[3 * i + 1], z = pos[3 * i + 2];
            float d = (float) Math.hypot(x, z) / R, h = Math.max(0, Math.min(1, y / H));
            float out = Math.min(1, d * 1.1f);
            float a = 0.35f + 0.65f * (0.25f + 0.75f * out) * (0.55f + 0.45f * h);
            ao[i] = Math.min(1, a);
            col[3 * i] *= ao[i]; col[3 * i + 1] *= ao[i]; col[3 * i + 2] *= ao[i];
        }
    }

    /** Anzahl Dreiecke und Punkte als Text. */
    @Override public String toString() { return model.species.name + " Stufe " + lod + ": " + nt + " Dreiecke, " + nv + " Punkte, " + leafIds.length + " Blätter"; }
}
