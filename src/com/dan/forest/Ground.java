package com.dan.forest;

/** Bodenhöhe (y) an einer Stelle; für Waldverteilung und liegende Blätter. */
@FunctionalInterface
public interface Ground {
    float height(double x, double z);

    Ground FLAT = (x, z) -> 0;
}
