package com.dan.road;

/** Ein Verkehrsteilnehmer: Art, Lage auf dem Weg, Geschwindigkeit, Lichter und Haltung. Nur lesen. */
public final class Mover {
    public final Vehicle kind;
    public final int seed;
    /** Lack oder Kleidung (linear). */
    public final float[] color = new float[3];
    Traffic.Lane lane;
    /** Fortschritt in Fahrtrichtung (m), Geschwindigkeit (m/s), Beschleunigung (m/s²). */
    public float p, v, a;
    /** Wunschgeschwindigkeit (m/s) und Faktor des Fahrers. */
    float v0, temper, curveBrake;
    /** Querlage jetzt, Ausweichen nach rechts (m), Spurwechsel: Start, Ziel, Fortschritt 0..1. */
    public float u;
    float yieldU, uFrom, uTo, change = 1;
    /** Blinker: −1 links, 1 rechts, 0 aus; Warnblinker; Bremslicht. */
    public int blink;
    public boolean hazard, brake;
    /** Zeit bis zur nächsten Prüfung eines Spurwechsels. */
    float think;
    /** Welt: Lage des Fußpunkts, Richtung (x, z), Neigung längs (rad), Rollen (rad), Federn (m). */
    public float x, y, z, hx, hz, pitch, roll, bounce;
    /** Drehwinkel der Räder, Phase der Schritte oder Tritte. */
    public float wheel, step;
    /** Staub hinter dem Fahrzeug (Zähler für das Ausstoßen). */
    float dustAcc;

    Mover(Vehicle kind, int seed) { this.kind = kind; this.seed = seed; }

    public Way way() { return lane.way; }
    /** Fahrtrichtung auf dem Weg: 1 mit wachsender Bogenlänge, −1 dagegen. */
    public int dir() { return lane.dir; }
    /** Bogenlänge auf dem Weg. */
    public float s() { return lane.dir > 0 ? p : lane.way.length - p; }
}
