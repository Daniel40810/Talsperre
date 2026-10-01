package com.dan.forest;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Wald aus vielen Bäumen: je Art einige vorgerechnete Varianten mit ihren Detailstufen, die sich
 * alle Bäume teilen (Instanzen mit Ort, Drehung, Größe). Nach der Entfernung zur Kamera wählt der
 * Wald die Stufe; nahe Bäume bewegen sich im Wind (je eigener Animator, mit Böen an ihrem Ort) und
 * werfen im Herbst und bei Böen Blätter ab, die als {@link LeafFall} weiterfliegen.
 * <p>
 * Zeichnen: {@link #visit} liefert je Baum Netz, Lagen (bewegt oder Ruhelage), Normalen und Farben
 * (nach der Jahreszeit) und die Instanz für Ort, Drehung und Größe.
 */
public final class Forest {
    /** Arten des Waldes (Index = speciesIndex der Instanzen). */
    public final List<Species> species = new ArrayList<>();
    public final List<TreeInstance> trees = new ArrayList<>();
    public final WindField wind = new WindField();
    public final LeafFall leaves = new LeafFall(4000);
    /** Varianten je Art; Grenzen der Stufen 0/1/2 in Metern (dahinter Stufe 3). */
    public int variants = 5;
    public float[] lodDistance = {25, 60, 110};
    /** Feste Detailstufe für alle Bäume (0..3) oder −1 für nach der Entfernung. */
    public int forceLod = -1;
        /** Wie viele Bäume höchstens bewegt werden (die nächsten). */
    public int maxAnimated = 40;
    /** Tag im Jahr und Schnee für die Jahreszeit. */
    private int day = 200;
    private float snow;
    private final Ground ground;

    private TreeModel[][] models;
    private TreeMesh[][][] meshes;
    private float[][][][] colors;
    private Season[] seasons;
    private boolean colorsDirty = true;

    public Forest(Ground ground) { this.ground = ground == null ? Ground.FLAT : ground; }

    /** Eine Art hinzufügen; liefert ihren Index. */
    public int addSpecies(Species s) { species.add(s); models = null; return species.size() - 1; }

    TreeInstance instance(int si, int variant, double x, double y, double z, double yaw, float age) {
        prepare();
        TreeInstance t = new TreeInstance(species.get(si), si, variant, x, y, z, yaw, 0.45 + 0.55 * age, age);
        trees.add(t);
        return t;
    }

    /** Alle Bäume und fallenden Blätter entfernen (die Modelle bleiben). */
    public void clear() { trees.clear(); leaves.n = 0; }

    /** Einen Baum von Hand setzen. */
    public TreeInstance place(int si, int variant, double x, double z, double yaw, float age) {
        return instance(si, variant % variants, x, ground.height(x, z), z, yaw, age);
    }

    /** Modelle und Netze aller Arten und Varianten anlegen (einmal, beim ersten Baum). */
    public void prepare() {
        if (models != null && models.length == species.size()) return;
        int ns = species.size();
        models = new TreeModel[ns][variants];
        meshes = new TreeMesh[ns][variants][4];
        colors = new float[ns][variants][4][];
        for (int s = 0; s < ns; s++) {
            for (int v = 0; v < variants; v++) {
                models[s][v] = TreeGenerator.grow(species.get(s), 1000L * s + v * 7919L + 17, 1);
                for (int l = 0; l < 4; l++) meshes[s][v][l] = TreeMesh.build(models[s][v], l);
            }
        }
        colorsDirty = true;
    }

    public TreeMesh mesh(TreeInstance t, int lod) { prepare(); return meshes[t.speciesIndex][t.variant][lod]; }

    /** Jahreszeit setzen (Tag 1..365, Schnee 0..1); die Farben werden beim nächsten Bild neu gerechnet. */
    public void setSeason(int day, float snow) {
        if (day != this.day || Math.abs(snow - this.snow) > 0.02f) colorsDirty = true;
        this.day = day; this.snow = snow;
    }

    public Season season(int si) { return seasons[si]; }

    private void recolor() {
        int ns = species.size();
        seasons = new Season[ns];
        for (int s = 0; s < ns; s++) {
            seasons[s] = Season.of(species.get(s), day, snow);
            for (int v = 0; v < variants; v++) for (int l = 0; l < 4; l++) {
                TreeMesh m = meshes[s][v][l];
                float[] c = colors[s][v][l];
                if (c == null || c.length < 3 * m.nv) c = colors[s][v][l] = new float[3 * m.nv];
                seasons[s].colorize(m, c);
                // ferne Stufen (und die Nadelballen der Lärche): Laub ohne Blätter (kahl) wird zur Farbe der Äste
                if ((l >= 2 || species.get(s).conifer) && seasons[s].foliage < 0.3f && !seasons[s].evergreen) {
                    for (int i = 0; i < m.nv; i++) if (m.part[i] == TreeMesh.CLUMP) {
                        float k = 0.35f + 0.65f * seasons[s].foliage;
                        c[3 * i] = c[3 * i] * k + species.get(s).bark[0] * 0.6f * (1 - k);
                        c[3 * i + 1] = c[3 * i + 1] * k + species.get(s).bark[1] * 0.6f * (1 - k);
                        c[3 * i + 2] = c[3 * i + 2] * k + species.get(s).bark[2] * 0.6f * (1 - k);
                    }
                }
            }
        }
        colorsDirty = false;
    }

    /**
     * Ein Zeitschritt: Stufen nach der Entfernung zu (cx, cy, cz), die nahen Bäume im Wind bewegen, Blätter
     * abwerfen und die fallenden Blätter weiterrechnen.
     */
    public void update(double t, float dt, double cx, double cy, double cz) {
        prepare();
        if (colorsDirty) recolor();
        // die nächsten maxAnimated Bäume in den Stufen 0 und 1 bewegen
        List<TreeInstance> near = new ArrayList<>();
        for (TreeInstance tr : trees) {
            double d = Math.sqrt((tr.x - cx) * (tr.x - cx) + (tr.y - cy) * (tr.y - cy) + (tr.z - cz) * (tr.z - cz)) / Math.max(0.3, tr.scale);
            tr.lod = d < lodDistance[0] ? 0 : d < lodDistance[1] ? 1 : d < lodDistance[2] ? 2 : 3;
            if (forceLod >= 0) tr.lod = forceLod;
            if (tr.lod <= 1) near.add(tr);
        }
        near.sort((a, b) -> Double.compare(dist2(a, cx, cz), dist2(b, cx, cz)));
        float[] xyz = new float[3], c3 = new float[3];
        int na = Math.min(maxAnimated, near.size());
        for (int i = na; i < near.size(); i++) near.get(i).anim = null;
        float[] before0 = new float[na];
        WindField.Sample[] local = new WindField.Sample[na];
        // Wind und Laubfall je Baum, dann alle Posen parallel
        for (int i = 0; i < na; i++) {
            TreeInstance tr = near.get(i);
            TreeMesh m = mesh(tr, tr.lod);
            if (tr.anim == null || tr.anim.mesh != m) { tr.anim = new TreeAnimator(m); tr.leafScale = new float[m.leafIds.length]; }
            Season se = seasons[tr.speciesIndex];
            WindField.Sample ws = wind.sample(tr.x, tr.z, t);
            local[i] = ws.local(tr.yaw);
            // Laubfall: der gefallene Anteil steigt mit der Jahreszeit und reißt bei Böen weiter
            float tear = se.autumn > 0.25f && se.foliage > 0 ? 0.12f * Math.max(0, ws.gust - 1.15f) * se.autumn : 0;
            float target = Math.max(se.drop, se.drop + tear);
            float before = tr.dropped;
            if (before < 0) before = tr.dropped = target;
            if (target > tr.dropped) tr.dropped = target;
            for (int li = 0; li < m.leafIds.length; li++) tr.leafScale[li] = se.leafScale(m, li, tr.dropped - se.drop);
            before0[i] = before;
        }
        java.util.stream.IntStream.range(0, na).parallel().forEach(i -> near.get(i).anim.pose(t, local[i], near.get(i).leafScale));
        for (int i = 0; i < na; i++) {
            TreeInstance tr = near.get(i);
            TreeMesh m = tr.anim.mesh;
            Season se = seasons[tr.speciesIndex];
            float before = before0[i];
            // neu gefallene Blätter fliegen weiter (je Büschel ein paar)
            if (tr.dropped > before && !se.evergreen) {
                for (int li = 0; li < m.leafIds.length; li++) {
                    TreeModel.LeafSpot l = m.model.leaves.get(m.leafIds[li]);
                    if (l.drop < before || l.drop >= tr.dropped) continue;
                    int fv = m.leafVertex[li];
                    tr.anim.place(m.bone[fv], m.param[fv], l.x, l.y, l.z, xyz);
                    se.leafColor(tr.species, l.tone, c3);
                    for (int k = 0; k < 3; k++) {
                        double jx = (Math.random() - 0.5) * tr.species.cluster, jy = (Math.random() - 0.5) * tr.species.cluster * 0.5, jz = (Math.random() - 0.5) * tr.species.cluster;
                        double lx = xyz[0] + jx, ly = xyz[1] + jy, lz = xyz[2] + jz;
                        double cs = Math.cos(tr.yaw), sn = Math.sin(tr.yaw);
                        leaves.add((float) (tr.x + (lx * cs - lz * sn) * tr.scale), (float) (tr.y + ly * tr.scale), (float) (tr.z + (lx * sn + lz * cs) * tr.scale),
                                c3[0], c3[1], c3[2], tr.species.leafSize * 1.4f);
                    }
                }
            }
        }
        for (TreeInstance tr : trees) if (tr.lod > 1) tr.anim = null;
        leaves.step(dt, wind, t, ground);
    }

    private static double dist2(TreeInstance t, double x, double z) { return (t.x - x) * (t.x - x) + (t.z - z) * (t.z - z); }

    /** Was ein Zeichner je Baum bekommt. */
    @FunctionalInterface
    public interface Visitor {
        /** Netz, Lagen und Normalen (bewegt oder Ruhelage), Farben (nach der Jahreszeit), Instanz (Ort, Drehung yaw, Größe scale). */
        void tree(TreeMesh mesh, float[] pos, float[] nrm, float[] col, TreeInstance t);
    }

    public void visit(Visitor v) {
        prepare();
        if (colorsDirty) recolor();
        for (TreeInstance t : trees) {
            TreeMesh m = meshes[t.speciesIndex][t.variant][t.lod];
            float[] p = t.anim != null && t.anim.mesh == m ? t.anim.pos : m.pos, n = t.anim != null && t.anim.mesh == m ? t.anim.nrm : m.nrm;
            v.tree(m, p, n, colors[t.speciesIndex][t.variant][t.lod], t);
        }
    }

    /** Wie viele Bäume im letzten Bild in jeder Stufe standen (für Statuszeilen). */
    public int[] lodCounts() {
        int[] c = new int[4];
        for (TreeInstance t : trees) c[t.lod]++;
        return c;
    }
}
