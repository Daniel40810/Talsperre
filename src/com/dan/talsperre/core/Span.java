package com.dan.talsperre.core;

/**
 * Pixelbereich einer Dreieckszeile: alle x mit w_i(x) = A_i + B_i·x ≥ 0 für i = 0..2.
 * Ergebnis als gepackte long-Zahl (erstes und letztes Pixel), um Speicheranlagen zu sparen.
 */
final class Span {
    static final long EMPTY = Long.MIN_VALUE;

    private Span() { }

    static long of(float A0, float B0, float A1, float B1, float A2, float B2, float lo, float hi) {
        final float eps = 1e-5f;
        if (Math.abs(B0) < 1e-12f) { if (A0 < -eps) return EMPTY; }
        else { float x = -A0 / B0; if (B0 > 0) { if (x > lo) lo = x; } else if (x < hi) hi = x; }
        if (Math.abs(B1) < 1e-12f) { if (A1 < -eps) return EMPTY; }
        else { float x = -A1 / B1; if (B1 > 0) { if (x > lo) lo = x; } else if (x < hi) hi = x; }
        if (Math.abs(B2) < 1e-12f) { if (A2 < -eps) return EMPTY; }
        else { float x = -A2 / B2; if (B2 > 0) { if (x > lo) lo = x; } else if (x < hi) hi = x; }
        if (lo > hi) return EMPTY;
        int a = (int) Math.ceil(lo - 0.5f), b = (int) Math.floor(hi - 0.5f);
        if (a > b) return EMPTY;
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }

    static int lo(long s) { return (int) (s >> 32); }

    static int hi(long s) { return (int) s; }
}
