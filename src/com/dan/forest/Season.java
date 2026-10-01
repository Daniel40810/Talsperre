package com.dan.forest;

/**
 * Das Laubjahr einer Art an einem Tag: wie weit die Blätter ausgetrieben sind, ob sie noch jung und
 * hellgrün sind, wie weit die Herbstfärbung ist und wie viele schon gefallen sind; dazu Schnee auf
 * Ästen und Nadeln. Nadelbäume bleiben grün, im Winter etwas dunkler und olivstichig.
 * <p>
 * Die Tage kommen aus der Art ({@link Species#leafOut} bis {@link Species#bare}); jedes Blatt färbt
 * sich und fällt nach seinem eigenen Zufallswert ein wenig früher oder später.
 */
public final class Season {
    /** Austrieb 0..1 (Größe der Blätter), junges Grün 0..1, Herbstfärbung 0..1, gefallen 0..1, Schnee 0..1. */
    public float foliage, flush, autumn, drop, snow;
    public final boolean evergreen;
    public final int day;

    private Season(boolean evergreen, int day) { this.evergreen = evergreen; this.day = day; }

    /** Laubjahr der Art sp am Tag day (1..365), mit Schnee 0..1 (etwa aus dem Wetter). */
    public static Season of(Species sp, int day, float snow) {
        Season s = new Season(!sp.deciduous, day);
        s.snow = Math.max(0, Math.min(1, snow));
        if (!sp.deciduous) {
            s.foliage = 1;
            // Maitriebe: die neuen Spitzen treiben hell aus und dunkeln im Sommer nach
            if (sp.shoots != null) s.flush = smooth(sp.leafOut - 5, sp.leafOut + 12, day) * (1 - smooth(sp.leafOut + 35, sp.leafOut + 80, day));
            return s;
        }
        float d = day;
        if (d < sp.leafOut - 12 || d >= sp.bare) { s.foliage = 0; s.drop = 1; return s; }
        s.foliage = smooth(sp.leafOut - 12, sp.leafOut + 10, d);
        s.flush = 1 - smooth(sp.leafOut + 5, sp.leafOut + 40, d);
        s.autumn = smooth(sp.colorStart, sp.colorFull, d);
        s.drop = smooth(sp.colorFull - 8, sp.bare, d);
        return s;
    }

    /**
     * Größe des Blatts li der Stufe m (0 = gefallen oder noch nicht ausgetrieben); extraDrop hebt die
     * Schwelle, etwa wenn Böen Blätter abreißen.
     */
    public float leafScale(TreeMesh m, int li, float extraDrop) {
        if (evergreen) return 1;
        TreeModel.LeafSpot l = m.model.leaves.get(m.leafIds[li]);
        if (l.drop < Math.min(1, drop + extraDrop)) return 0;
        return foliage;
    }

    /** Ist das Blatt li bei diesem Stand (und extraDrop) schon gefallen? */
    public boolean fallen(TreeMesh m, int li, float extraDrop) {
        if (evergreen) return false;
        return m.model.leaves.get(m.leafIds[li]).drop < Math.min(1, drop + extraDrop);
    }

    /** Farbe eines Blatts (linear) nach Jahreszeit und seinem Zufallswert tone; out bekommt r, g, b. */
    public void leafColor(Species sp, float tone, float[] out) {
        float[] a = sp.summer, b = sp.spring;
        float fl = flush;
        if (evergreen && sp.shoots != null) {
            // nur die Spitzen (etwa jede dritte Stelle) tragen neue, helle Triebe
            b = sp.shoots;
            fl *= Math.max(0, Math.min(1, (tone - 0.6f) / 0.15f));
        }
        float r = a[0] + (b[0] - a[0]) * fl, g = a[1] + (b[1] - a[1]) * fl, bl = a[2] + (b[2] - a[2]) * fl;
        if (evergreen) {
            // Winter: Nadeln dunkler und olivstichig
            float w = (float) Math.max(0, Math.cos(2 * Math.PI * (day - 15) / 365.0));
            r *= 1 + 0.15f * w; g *= 1 - 0.15f * w; bl *= 1 - 0.2f * w;
        } else if (autumn > 0) {
            // jedes Blatt beginnt etwas früher oder später; der Ton wählt die Herbstfarbe
            float k = Math.max(0, Math.min(1, (autumn - tone * 0.45f) / 0.55f));
            float[] c = sp.autumn[Math.min(sp.autumn.length - 1, (int) (tone * sp.autumn.length))];
            r += (c[0] - r) * k; g += (c[1] - g) * k; bl += (c[2] - bl) * k;
        }
        out[0] = r; out[1] = g; out[2] = bl;
    }

    /**
     * Farben der Stufe m nach der Jahreszeit in out (3 je Punkt): Blätter nach {@link #leafColor}, mit
     * der Abschattung aus der Ruhefarbe; Schnee auf Flächen, die nach oben zeigen.
     */
    public void colorize(TreeMesh m, float[] out) {
        Species sp = m.model.species;
        float[] c = new float[3];
        float[] base = sp.summer;
        int lastLeaf = -2;
        for (int i = 0; i < m.nv; i++) {
            float r = m.col[3 * i], g = m.col[3 * i + 1], b = m.col[3 * i + 2];
            byte p = m.part[i];
            if (p != TreeMesh.BARK && p != TreeMesh.CONE) {
                int li = m.leaf[i];
                float tone = li >= 0 ? m.model.leaves.get(m.leafIds[li]).tone : 0.5f;
                if (li != lastLeaf) { leafColor(sp, tone, c); lastLeaf = li; }
                r *= c[0] / base[0]; g *= c[1] / base[1]; b *= c[2] / base[2];
            }
            if (snow > 0) {
                float up = m.nrm[3 * i + 1];
                float k = snow * Math.max(0, Math.min(1, (up - 0.25f) / 0.55f)) * (p == TreeMesh.BARK || p == TreeMesh.CONE ? 0.7f : 0.9f);
                float ao = m.ao == null ? 1 : m.ao[i];
                float white = 0.8f * (0.5f + 0.5f * ao);
                r += (white - r) * k; g += (white - g) * k; b += (white * 1.03f - b) * k;
            }
            out[3 * i] = r; out[3 * i + 1] = g; out[3 * i + 2] = b;
        }
    }

    static float smooth(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
