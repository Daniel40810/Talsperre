package com.dan.road;

/** Der Boden unter den Wegen und, wenn vorhanden, Wasser, über das eine Brücke führen muss. */
public interface Ground {
    /** Bodenhöhe an (x, z). */
    float height(double x, double z);

    /** Wasserspiegel an (x, z) oder NaN, wenn dort kein Wasser ist. */
    default float water(double x, double z) { return Float.NaN; }

    /** Ebener Boden auf y = 0 ohne Wasser. */
    Ground FLAT = (x, z) -> 0;
}
