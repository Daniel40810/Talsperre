package com.dan.talsperre.core;

/**
 * Kleine Dinge, die der Bildrechner nach dem Licht in das Bild setzt, jedes mit Tiefenprüfung:
 * leuchtende Punkte (Wasserweg, Glühwürmchen), weiche Punkte (Schmetterlinge, Fische) und Vögel
 * als flatternde Winkel. Wer sie erzeugt, füllt den Puffer vor jedem Bild neu.
 */
public final class Sprites {
    public static final byte GLOW = 0, DOT = 1, BIRD = 2;

    public int n;
    public float[] x = new float[256], y = new float[256], z = new float[256], size = new float[256];
    public float[] r = new float[256], g = new float[256], b = new float[256], a = new float[256];
    /** Vögel: Flugrichtung (waagrecht, normiert) und Flügelschlag −1..1. */
    public float[] hx = new float[256], hz = new float[256], flap = new float[256];
    public byte[] kind = new byte[256];

    public void clear() { n = 0; }

    public int add(byte k, double px, double py, double pz, double sz, double cr, double cg, double cb, double al) {
        if (n == x.length) grow();
        int i = n++;
        kind[i] = k; x[i] = (float) px; y[i] = (float) py; z[i] = (float) pz; size[i] = (float) sz;
        r[i] = (float) cr; g[i] = (float) cg; b[i] = (float) cb; a[i] = (float) al;
        hx[i] = 1; hz[i] = 0; flap[i] = 0;
        return i;
    }

    private void grow() {
        int c = x.length * 2;
        x = java.util.Arrays.copyOf(x, c); y = java.util.Arrays.copyOf(y, c); z = java.util.Arrays.copyOf(z, c);
        size = java.util.Arrays.copyOf(size, c); r = java.util.Arrays.copyOf(r, c); g = java.util.Arrays.copyOf(g, c);
        b = java.util.Arrays.copyOf(b, c); a = java.util.Arrays.copyOf(a, c); hx = java.util.Arrays.copyOf(hx, c);
        hz = java.util.Arrays.copyOf(hz, c); flap = java.util.Arrays.copyOf(flap, c); kind = java.util.Arrays.copyOf(kind, c);
    }
}
