package com.dan.ground;

/**
 * Was ein Zeichner je Bild bekommt: bewegte Ecken (Lage, Normale, Grundfarbe linear) und Dreiecke.
 * Alle Flächen sind dünn (Halme, Blüten) und von beiden Seiten zu beleuchten; Steine und Erde sind
 * geschlossen.
 */
public final class Batch {
    public int nv, nt;
    public float[] xyz = new float[3 * 4096], nrm = new float[3 * 4096], rgb = new float[3 * 4096];
    /** Je Ecke: 1 = fester Körper (Stein, Erde; nur von vorn beleuchtet), 0 = dünn (Halm, Blüte). */
    public byte[] solid = new byte[4096];
    /**
     * Je Ecke: wie weit sie in den Boden dahinter übergeht (0 = eigene Farbe, 1 = unsichtbar): am Rand
     * der Reichweite und am Saum offener Erde. Zeichner mischen die Farbe mit dem, was schon im Bild steht.
     */
    public float[] fade = new float[4096];
    public int[] tri = new int[3 * 4096];

    void ensure(int v, int t) {
        if (3 * v > xyz.length) {
            int c = 3 * Math.max(v, xyz.length / 2);
            xyz = java.util.Arrays.copyOf(xyz, c); nrm = java.util.Arrays.copyOf(nrm, c); rgb = java.util.Arrays.copyOf(rgb, c);
            solid = java.util.Arrays.copyOf(solid, c / 3);
            fade = java.util.Arrays.copyOf(fade, c / 3);
        }
        if (3 * t > tri.length) tri = java.util.Arrays.copyOf(tri, Math.max(3 * t, tri.length * 3 / 2));
    }
}
