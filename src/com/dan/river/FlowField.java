package com.dan.river;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Strömung eines Flusses an jedem Ort, ohne Gitter und ohne Simulation, aus einfachen Regeln der
 * Gerinnehydraulik:
 * <ul>
 *   <li><b>Geschwindigkeit</b> nach Manning aus Tiefe und Gefälle (v = R^⅔ · S^½ / n), mit dem Abfluss
 *       skaliert; in der Mitte am schnellsten, zu den Ufern hin langsamer.</li>
 *   <li><b>Krümmung:</b> In der Außenkurve ist das Wasser schneller und tiefer (Kolk), innen flach
 *       (Gleithang); an der Oberfläche drängt es nach außen (Sekundärströmung).</li>
 *   <li><b>Steine:</b> Das Wasser teilt sich um den Stein (Potentialströmung um einen Zylinder), davor
 *       staut es sich, dahinter liegt ein Kehrwasser, in dem es zurückströmt; Schaum und Unruhe im
 *       Nachlauf.</li>
 *   <li><b>Schnellen:</b> Wo es steil und flach ist, wird das Wasser unruhig und schäumt.</li>
 * </ul>
 * Richtungen in der Ebene (x, z); alle Größen in Metern und Sekunden.
 */
public final class FlowField {
    public final RiverPath path;
    /** Abfluss relativ zum Mittel (0.2 Niedrigwasser … 3 Hochwasser); hebt Tiefe und Geschwindigkeit. */
    public volatile float discharge = 1;
    /** Größte Tiefe (m) bei 10 m halber Breite und mittlerem Abfluss; mit der Breite wurzelförmig. */
    public float depth = 1.2f;
    /** Rauheit nach Manning (0.03 Kies, 0.05 Geröll). */
    public float manning = 0.035f;
    /** Mindestgefälle, damit auch ein ebener Lauf fließt. */
    public float minSlope = 0.0006f;

    private final List<Rock> rocks = new ArrayList<>();
    private final float rcell = 24;
    private java.util.Map<Long, Rock[]> rgrid = new java.util.HashMap<>();

    public FlowField(RiverPath path) { this.path = path; }

    /** Stein hinzufügen. */
    public void add(Rock r) {
        rocks.add(r);
        java.util.Map<Long, List<Rock>> m = new java.util.HashMap<>();
        for (Rock k : rocks) {
            float reach = reach(k);
            for (int j = cellOf(k.z - reach); j <= cellOf(k.z + reach); j++)
                for (int i = cellOf(k.x - reach); i <= cellOf(k.x + reach); i++) m.computeIfAbsent(key(i, j), q -> new ArrayList<>()).add(k);
        }
        java.util.Map<Long, Rock[]> g = new java.util.HashMap<>();
        for (java.util.Map.Entry<Long, List<Rock>> e : m.entrySet()) g.put(e.getKey(), e.getValue().toArray(new Rock[0]));
        rgrid = g;
    }

    /** Wie weit ein Stein wirkt (m): der Nachlauf reicht etwa zwanzig Radien flussab. */
    static float reach(Rock k) { return k.r * 20 + 4; }

    public List<Rock> rocks() { return java.util.Collections.unmodifiableList(rocks); }

    private int cellOf(float v) { return (int) Math.floor(v / rcell); }
    private static long key(int i, int j) { return ((long) i << 32) ^ (j & 0xffffffffL); }

    /** Die Strömung an einem Ort. */
    public static final class Flow {
        /** Im Wasser? (sonst sind die übrigen Werte die des nächsten Ufers) */
        public boolean wet;
        /** Geschwindigkeit an der Oberfläche (m/s) als Vektor und Betrag. */
        public float vx, vz, speed;
        /** Grundströmung ohne die Wirbel an den Steinen (glatt; zum Verschieben von Mustern). */
        public float bx, bz;
        /** Tiefe (m), Wasserspiegel (y), Querlage −1..1, Bogenlänge. */
        public float depth, level, u, s;
        /** Unruhe 0..1 (kleine, schnelle Wellen) und Schaum 0..1 (wie viel Luft eingetragen wird). */
        public float turb, foam;
        /** Kehrwasser 0..1 (strömt zurück oder kreist). */
        public float eddy;
        /** Im Stein. */
        public boolean solid;
        public final RiverPath.Loc loc = new RiverPath.Loc();
    }

    /** Strömung an (x, z) in out; false, wenn der Ort nicht am Fluss liegt. */
    public boolean sample(double x, double z, Flow out) {
        RiverPath.Loc L = out.loc;
        if (!path.locate(x, z, L)) { out.wet = false; out.speed = 0; out.vx = out.vz = out.bx = out.bz = 0; out.depth = 0; out.foam = out.turb = out.eddy = 0; out.solid = false; return false; }
        float u = L.n / L.half;
        out.u = u; out.s = L.s; out.level = L.level + rise(L.half);
        out.solid = false; out.eddy = 0;
        float au = Math.abs(u);
        float q = discharge;
        // Kurve: Außenseite ist die, von der der Fluss wegbiegt (u · Krümmung < 0)
        float bend = -L.curv * L.half * u;                  // > 0 außen
        bend = Math.max(-0.8f, Math.min(0.8f, bend * 2.2f));
        // Tiefe: flache Mulde, außen tiefer, innen flacher; mit dem Abfluss steigt der Spiegel
        float dmax = depth * (float) Math.sqrt(L.half / 10f) * q04();
        float prof = au >= 1 ? 0 : pow34(1 - au * au);
        float d = dmax * prof * (1 + 0.6f * bend);
        out.depth = Math.max(0, d);
        out.wet = au < 1;
        // Geschwindigkeit nach Manning mit mittlerer Tiefe, dann Querprofil
        float S = Math.max(minSlope, L.slope);
        float R = dmax * 0.65f;
        float vm = (float) (Math.cbrt(R * R) * Math.sqrt(S) / manning);
        vm = Math.min(5, Math.max(0.15f, vm));
        float lat = au >= 1 ? 0 : (float) Math.sqrt(1 - au * au * (float) Math.sqrt(au));
        float v = vm * lat * (1 + 0.35f * bend);
        // Sekundärströmung: an der Oberfläche nach außen
        float side = -Math.signum(L.curv) * Math.min(0.12f, Math.abs(L.curv) * L.half * 1.5f) * lat;
        float nx = -L.tz, nz = L.tx;
        float vx = L.tx * v + nx * side * v, vz = L.tz * v + nz * side * v;
        // Unruhe: Gefälle, Geschwindigkeit, flaches Wasser
        float turb = smooth(0.0015f, 0.02f, S) * 0.7f + smooth(0.8f, 2.5f, v) * 0.4f + smooth(0.5f, 0.1f, out.depth) * 0.35f * lat;
        float foam = smooth(0.006f, 0.04f, S) * smooth(0.6f, 2.2f, v) * 0.45f;
        // Ufer: langsames Wasser, Schaum sammelt sich
        foam += smooth(0.8f, 0.98f, au) * 0.15f;
        out.bx = vx; out.bz = vz;
        // Steine
        Rock[] rs = rgrid.get(key(cellOf((float) x), cellOf((float) z)));
        if (rs != null) {
            float ux = vx, uz = vz, U = (float) Math.hypot(ux, uz);
            float dirx = U > 1e-4f ? ux / U : L.tx, dirz = U > 1e-4f ? uz / U : L.tz;
            float dvx = 0, dvz = 0;
            for (Rock k : rs) {
                float px = (float) x - k.x, pz = (float) z - k.z;
                float a = px * dirx + pz * dirz, b = -px * dirz + pz * dirx;   // längs und quer zur Strömung
                float r2 = a * a + b * b, R2 = k.r * k.r;
                float reach = reach(k);
                if (r2 > reach * reach) continue;
                // untergetaucht schwächer; zum Rand der Reichweite weich auf null
                float sub = (k.top >= 0 ? 1 : (float) Math.exp(k.top / 0.25f)) * (1 - smooth(0.6f * reach, reach, (float) Math.sqrt(r2)));
                if (r2 < R2 && k.top >= 0) { out.solid = true; out.wet = false; out.vx = out.vz = out.speed = 0; out.bx = out.bz = 0; out.foam = 0.9f; out.turb = 1; return true; }
                float r4 = r2 * r2 + 1e-6f;
                // Umströmung eines Zylinders
                float ua = U * (1 - R2 * (a * a - b * b) / r4), ub = -U * (2 * R2 * a * b / r4);
                // Kehrwasser dahinter
                float Lw = k.r * 5 + 1;
                float wake = a > 0 ? (float) Math.exp(-a / Lw) * gauss(b / (k.r * (1 + 0.25f * a / k.r))) : 0;
                ua -= U * 1.35f * wake;
                ub *= 1 - wake;
                // Stau davor und Bugwelle
                float bow = a < 0 ? gauss((float) Math.sqrt(r2) / k.r - 1.15f, 0.35f) : 0;
                float dA = (ua - U) * sub, dB = ub * sub;
                dvx += dirx * dA - dirz * dB;
                dvz += dirz * dA + dirx * dB;
                float near = gauss((float) Math.sqrt(r2) / k.r - 1, 0.6f);
                turb += (wake * 0.9f + near * 0.6f + bow * 0.5f) * sub;
                foam += (wake * 0.45f * smooth(0.3f, 1.2f, U) + bow * 0.5f + near * 0.25f) * sub * Math.min(1, 0.4f + U);
                out.eddy = Math.max(out.eddy, wake * sub * smooth(0.7f, 1.1f, 1.35f * wake));
            }
            vx += dvx; vz += dvz;
        }
        out.vx = vx; out.vz = vz;
        out.speed = (float) Math.hypot(vx, vz);
        out.turb = Math.min(1, turb);
        out.foam = 1 - (float) Math.exp(-1.3f * foam);     // viele Steine sättigen, statt alles weiß zu machen
        return true;
    }

    /**
     * Wie weit der Spiegel beim eingestellten Abfluss über dem mittleren liegt (m), bei halber Breite
     * half; negativ bei Niedrigwasser.
     */
    public float rise(float half) {
        return depth * (float) Math.sqrt(half / 10f) * (q04() - 1) * 0.6f;
    }

    private float qLast = -1, qPow = 1;

    /** Abfluss hoch 0,4 (Tiefe wächst langsamer als der Abfluss), zwischengespeichert. */
    private float q04() {
        float q = discharge;
        if (q != qLast) { qPow = (float) Math.pow(q, 0.4); qLast = q; }
        return qPow;
    }

    /** x hoch ¾ ohne pow. */
    private static float pow34(float x) { float r = (float) Math.sqrt(x); return r * (float) Math.sqrt(r); }

    static float smooth(float a, float b, float x) {
        float t = (x - a) / (b - a);
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return t * t * (3 - 2 * t);
    }

    private static float gauss(float x) { return (float) Math.exp(-x * x); }
    private static float gauss(float x, float w) { return (float) Math.exp(-(x / w) * (x / w)); }
}
