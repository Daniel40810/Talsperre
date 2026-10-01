package com.dan.talsperre.core;

/**
 * Fallende und liegende Blätter für den Bildrechner: je Blatt ein Rhombus aus vier Ecken, Farbe
 * (linear) und Normale. Wer sie bewegt (Grove), füllt die Felder vor
 * jedem Bild; der Bildrechner liest sie nur.
 */
public final class LeafQuads {
    public static final int CAP = 3000;
    public int n;
    public final float[] xyz = new float[12 * CAP], rgb = new float[3 * CAP], nrm = new float[3 * CAP];
}
