package com.dan.road;

/**
 * Wetter über den Wegen. Eingaben: Regen, Schnee, Dunkelheit, Wind. Daraus folgt der Zustand der
 * Fahrbahn mit Trägheit: Sie wird im Regen in wenigen Minuten nass und trocknet danach langsam, in den
 * Radspuren schneller als daneben; Pfützen laufen langsamer voll und bleiben länger.
 * {@link #timeScale} beschleunigt das für Vorschauen.
 */
public final class Weather {
    /** Regen 0..1, Schneehöhe 0..1 (1 ≈ 30 cm), Dunkelheit 0 Tag .. 1 Nacht. */
    public volatile float rain, snow, dark;
    /** Wind (m/s) und Richtung (Grad, 0 = +x, 90 = +z): treibt Staub und Gischt. */
    public volatile float windSpeed = 3, windDirection = 30;
    /** Zeitraffer für Nässe und Pfützen (1 = echte Zeit). */
    public volatile float timeScale = 1;
    /** Schneestangen an den Straßen (im Winterhalbjahr). */
    public volatile boolean poles = true;

    /** Zustand: Nässe der Fläche, Nässe in den Radspuren, Füllung der Pfützen (je 0..1). */
    public float wet, wetTracks, puddles;

    /** Den Zustand gleich auf das Wetter setzen (ohne Übergang). */
    public void settle() {
        wet = rain > 0.05f ? 1 : 0;
        wetTracks = wet;
        puddles = Math.min(1, rain * 1.3f);
    }

    /** Ein Zeitschritt dt (s). */
    public void step(float dt) {
        float t = dt * timeScale;
        float wind = 1 + windSpeed * 0.15f;
        if (rain > 0.02f) {
            wet += (1 - wet) * (1 - (float) Math.exp(-t * (0.004f + 0.03f * rain)));
            wetTracks += (wet - wetTracks) * (1 - (float) Math.exp(-t * 0.02f));
            wetTracks = Math.max(wetTracks, wet * Math.min(1, rain * 3));
            puddles += (Math.min(1, rain * 1.3f) - puddles) * (1 - (float) Math.exp(-t * 0.0015f * (0.3f + rain)));
        } else {
            float dry = 0.0008f * wind * (1 - dark * 0.6f);
            wet -= wet * (1 - (float) Math.exp(-t * dry));
            wetTracks -= wetTracks * (1 - (float) Math.exp(-t * dry * 3));
            puddles -= puddles * (1 - (float) Math.exp(-t * dry * 0.35f));
        }
        wet = clamp(wet); wetTracks = Math.min(clamp(wetTracks), wet); puddles = clamp(puddles);
    }

    /** Sind Scheinwerfer an? Nachts, in der Dämmerung und bei starkem Regen oder Schneefall. */
    public boolean lights() { return dark > 0.3f || rain > 0.5f; }

    private static float clamp(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}
