package com.dan.road;

/**
 * Was ein Zeichner je Bild bekommt: Ecken mit Lage, Normale, Farbe (linear) und Art, dazu Dreiecke.
 * <ul>
 *   <li>{@link #SOLID}: fester Körper, nur von vorn beleuchtet (Fahrbahn, Karosserie).</li>
 *   <li>{@link #THIN}: dünne Fläche, von beiden Seiten beleuchtet (Leitpfosten, Geländer, Menschen).</li>
 *   <li>{@link #LAMP}: leuchtet selbst, die Farbe ist die Strahlung (Scheinwerfer, Rückleuchten).</li>
 *   <li>{@link #GLOW}: Lichthof, wird unbeleuchtet zum Bild addiert und verdeckt nichts.</li>
 * </ul>
 * {@code gloss} ist der Glanz 0..1 (nasse Fahrbahn, Pfützen, Glas): Zeichner spiegeln dort Himmel
 * und Sonne. {@code fade} mischt die Ecke mit dem, was schon im Bild steht (Staub, Rand eines Pfades).
 */
public final class Batch {
    public static final byte SOLID = 0, THIN = 1, LAMP = 2, GLOW = 3;
    public int nv, nt;
    public float[] xyz = new float[3 * 4096], nrm = new float[3 * 4096], rgb = new float[3 * 4096];
    public float[] gloss = new float[4096], fade = new float[4096];
    public byte[] kind = new byte[4096];
    public int[] tri = new int[3 * 4096];

    void clear() { nv = 0; nt = 0; }

    void ensure(int v, int t) {
        if (3 * v > xyz.length) {
            int c = Math.max(v, xyz.length / 2);
            xyz = java.util.Arrays.copyOf(xyz, 3 * c); nrm = java.util.Arrays.copyOf(nrm, 3 * c); rgb = java.util.Arrays.copyOf(rgb, 3 * c);
            gloss = java.util.Arrays.copyOf(gloss, c); fade = java.util.Arrays.copyOf(fade, c); kind = java.util.Arrays.copyOf(kind, c);
        }
        if (3 * t > tri.length) tri = java.util.Arrays.copyOf(tri, Math.max(3 * t, tri.length * 3 / 2));
    }

    /** Neue Ecke; gibt ihre Nummer zurück. */
    int v(float x, float y, float z, float nx, float ny, float nz, float r, float g, float b, byte k, float gl, float fd) {
        ensure(nv + 1, nt);
        int i = nv++;
        xyz[3 * i] = x; xyz[3 * i + 1] = y; xyz[3 * i + 2] = z;
        nrm[3 * i] = nx; nrm[3 * i + 1] = ny; nrm[3 * i + 2] = nz;
        rgb[3 * i] = r; rgb[3 * i + 1] = g; rgb[3 * i + 2] = b;
        kind[i] = k; gloss[i] = gl; fade[i] = fd;
        return i;
    }

    void t(int a, int b, int c) {
        ensure(nv, nt + 1);
        tri[3 * nt] = a; tri[3 * nt + 1] = b; tri[3 * nt + 2] = c;
        nt++;
    }
}
