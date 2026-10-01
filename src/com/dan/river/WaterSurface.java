package com.dan.river;

/**
 * Die bewegte Oberfläche: Wellen und Schaum, die mit der Strömung treiben.
 * <p>
 * Muster werden nach der Flow-Map-Technik mit dem Wasser verschoben: zwei Phasen, die um eine halbe
 * Periode versetzt laufen und überblendet werden, sodass das Muster nie verzerrt und nie springt. Das
 * Muster ist längs der Strömung gestreckt (Schlieren); dazu kommen kleine Riffel, bei Unruhe kurze,
 * steile Wellen, an Steinen und Schnellen stehende Wellen (die bleiben am Ort, während das Wasser durch
 * sie hindurchläuft) und Kräuselung durch Wind.
 * <p>
 * Ergebnis je Ort: Neigung der Oberfläche (dh/dx, dh/dz), Hebung (m) und Schaum 0..1. Die Normale ist
 * (−dh/dx, 1, −dh/dz), normiert.
 */
public final class WaterSurface {
    /** Länge einer Phase (s); kürzer = weniger Streckung, länger = weniger Pulsieren. */
    public float period = 1.8f;
    /** Stärke der Wellen insgesamt. */
    public float amplitude = 1;
    /** Wind über dem Wasser (m/s) und Richtung (Grad, 0 = +x, 90 = +z). */
    public volatile float wind = 2, windDir = 30;
    /** Schaum insgesamt (0 = keiner). */
    public float foamAmount = 1;

    /** Ergebnis einer Abfrage. */
    public static final class Surf {
        public float dhdx, dhdz, height, foam;
        final float[] g = new float[2];
    }

    /**
     * Oberfläche an (x, z) zur Zeit t mit der Strömung f (aus {@link FlowField#sample}); lod 0..1 dämpft
     * feine Wellen (1 = nur grobe, für ferne Pixel). Ergebnis in out.
     */
    public void sample(float x, float z, float t, FlowField.Flow f, float lod, Surf out) {
        // Muster treiben mit der glatten Grundströmung und etwas von den Wirbeln; der Versatz ist
        // begrenzt, sonst zieht starke Scherung (neben Steinen) das Muster zu Streifen auseinander
        float vx = f.bx + 0.3f * (f.vx - f.bx), vz = f.bz + 0.3f * (f.vz - f.bz);
        float sp = (float) Math.hypot(vx, vz);
        float vmax = 1.6f;
        if (sp > vmax) { vx *= vmax / sp; vz *= vmax / sp; sp = vmax; }
        float dx, dz;
        if (sp > 1e-3f) { dx = vx / sp; dz = vz / sp; } else { dx = 1; dz = 0; }
        float T = period;
        float ph0 = frac(t / T), ph1 = frac(t / T + 0.5f);
        float w0 = 1 - Math.abs(2 * ph0 - 1), w1 = 1 - w0;
        // Zyklus-Versatz: jede Phase sieht nach dem Rücksprung ein anderes Stück Muster
        float c0 = (float) Math.floor(t / T) * 7.31f, c1 = (float) Math.floor(t / T + 0.5f) * 7.31f + 3.7f;
        float o0 = (ph0 - 0.5f) * T, o1 = (ph1 - 0.5f) * T;
        float hx = 0, hz = 0, h = 0, foamTex = 0;
        float turb = f.turb, fine = 1 - lod;
        float a1 = (0.035f + 0.09f * turb) * amplitude, a2 = (0.010f + 0.035f * turb) * amplitude * fine, a3 = 0.028f * turb * amplitude * fine;
        for (int k = 0; k < 2; k++) {
            float w = k == 0 ? w0 : w1;
            if (w < 1e-3f) continue;
            float o = k == 0 ? o0 : o1, c = k == 0 ? c0 : c1;
            // mit dem Wasser verschobener Ort
            float px = x - vx * o, pz = z - vz * o;
            // Rahmen längs (a) und quer (b) zur Strömung
            float a = px * dx + pz * dz, b = -px * dz + pz * dx;
            float[] g = out.g;
            // Schlieren: längs gestreckt
            float fa = 0.28f, fb = 0.95f;
            float v1 = RNoise.grad(a * fa + c, b * fb, g);
            float ga = g[0] * fa, gb = g[1] * fb;
            float sa = ga * a1, sb = gb * a1, hh = (v1 - 0.5f) * a1;
            // Riffel
            if (a2 > 0) {
                float f2a = 1.6f, f2b = 2.3f;
                float v2 = RNoise.grad(a * f2a + 11.3f + c, b * f2b + 4.1f, g);
                sa += g[0] * f2a * a2; sb += g[1] * f2b * a2; hh += (v2 - 0.5f) * a2;
            }
            // Unruhe: kurze Wellen, die auch auf der Stelle brodeln
            if (a3 > 0) {
                float f3 = 3.4f, e = t * 1.3f;
                float v3 = RNoise.grad(a * f3 + 5.1f + c + e, b * f3 - e * 0.7f, g);
                sa += g[0] * f3 * a3; sb += g[1] * f3 * a3; hh += (v3 - 0.5f) * a3;
            }
            // zurück in x, z
            hx += w * (sa * dx - sb * dz);
            hz += w * (sa * dz + sb * dx);
            h += w * hh;
            // Schaum wie Spitze: grobe Bahnen, darin Linien (Grate des Rauschens) und Bläschen
            if (f.foam * foamAmount <= 0.01f) continue;
            float mask = RNoise.value(a * 0.35f + c * 1.3f, b * 0.9f);
            float lace = fine > 0.2f ? 1 - Math.abs(2 * RNoise.value(a * 2.2f + c, b * 3.4f + 2.2f) - 1) : 0.6f;
            float bub = fine > 0.5f ? RNoise.value(a * 6.5f + c * 2.1f, b * 6.5f + 9.1f) : 0.5f;
            foamTex += w * (mask * 0.5f + lace * lace * 0.35f + bub * 0.15f);
        }
        // stehende Wellen an Steinen und Schnellen: am Ort fest, quer zur Strömung
        float st = turb * turb * 0.06f * amplitude;
        if (st > 0.002f) {
            // Rahmen aus der Grundströmung: sonst drehte er sich um jeden Stein und zöge Ringe
            float bsp = (float) Math.hypot(f.bx, f.bz);
            float ex = bsp > 1e-3f ? f.bx / bsp : dx, ez = bsp > 1e-3f ? f.bz / bsp : dz;
            float a = x * ex + z * ez, b = -x * ez + z * ex;
            float fa = 1.1f, fb = 0.5f;
            float v = RNoise.grad(a * fa + 91.7f, b * fb + 33.1f, out.g);
            float ga = out.g[0] * fa * st, gb = out.g[1] * fb * st;
            hx += ga * ex - gb * ez; hz += ga * ez + gb * ex; h += (v - 0.5f) * st;
        }
        // Wind: feine Kräuselung, die mit dem Wind läuft
        if (wind > 0.3f && fine > 0) {
            double wa = Math.toRadians(windDir);
            float wx = (float) Math.cos(wa), wz = (float) Math.sin(wa);
            float aw = Math.min(1, wind / 8) * 0.012f * amplitude * fine, fw = 3.1f;
            float a = x * wx + z * wz - t * (0.4f + 0.1f * wind), b = -x * wz + z * wx;
            float v = RNoise.grad(a * fw, b * fw * 0.8f + 7.7f, out.g);
            float ga = out.g[0] * fw * aw, gb = out.g[1] * fw * 0.8f * aw;
            hx += ga * wx - gb * wz; hz += ga * wz + gb * wx; h += (v - 0.5f) * aw;
        }
        out.dhdx = hx; out.dhdz = hz; out.height = h;
        // Schaum: wo die Strömung Luft einträgt, bleibt ein Teil des Musters über der Schwelle
        float pot = Math.min(1, f.foam * foamAmount);
        float thr = 0.85f - pot * 0.6f;
        out.foam = pot <= 0.01f ? 0 : FlowField.smooth(thr - 0.05f, thr + 0.12f, foamTex * (0.8f + 0.4f * pot)) * Math.min(1, 0.5f + pot);
    }

    private static float frac(float v) { return v - (float) Math.floor(v); }
}
