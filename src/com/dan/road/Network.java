package com.dan.road;

/**
 * Alle Wege einer Landschaft mit einem Gitter für schnelle Abfragen: Welcher Weg liegt an (x, z), wie
 * weit quer zur Achse, wie hoch ist die Fahrbahn dort? Dazu die Formung des Geländes: Unter der
 * Fahrbahn wird der Boden geebnet, daneben entstehen Damm und Einschnitt mit den Böschungen
 * {@link #fillSlope} und {@link #cutSlope}.
 */
public final class Network {
    public final java.util.List<Way> ways = new java.util.ArrayList<>();
    /** Böschungsneigung (Höhe je Meter) am Damm und im Einschnitt. */
    public float fillSlope = 0.5f, cutSlope = 0.67f;
    /**
     * Ebene Berme neben dem Bankett, bevor die Böschung beginnt (m), und wie tief der Boden unter der
     * Fahrbahn liegt: So stößt auch ein grobes Geländegitter nicht durch die Fahrbahn.
     */
    public float shelf = 2, sink = 0.08f;
    /** Bis zu diesem Abstand vom Rand des Banketts formt ein Weg das Gelände (m). */
    public float reach = 30;
    private final float cell = 24;
    private final java.util.Map<Long, int[]> grid = new java.util.HashMap<>();
    private boolean built;

    public Way add(Way w) {
        w.index = ways.size();
        ways.add(w);
        built = false;
        return w;
    }

    /** Rechnet Achsen, Profile und das Suchgitter. Nach dem Hinzufügen aller Wege einmal aufrufen. */
    public void build(Ground g) {
        for (Way w : ways) w.build(g);
        grid.clear();
        java.util.Map<Long, java.util.List<Integer>> m = new java.util.HashMap<>();
        for (Way w : ways) {
            float pad = w.type.half() + reach;
            for (int i = 0; i < w.segments(); i++) {
                int b = w.nb(i, 1);
                float x0 = Math.min(w.x[i], w.x[b]) - pad, x1 = Math.max(w.x[i], w.x[b]) + pad;
                float z0 = Math.min(w.z[i], w.z[b]) - pad, z1 = Math.max(w.z[i], w.z[b]) + pad;
                for (int cj = (int) Math.floor(z0 / cell); cj <= (int) Math.floor(z1 / cell); cj++)
                    for (int ci = (int) Math.floor(x0 / cell); ci <= (int) Math.floor(x1 / cell); ci++)
                        m.computeIfAbsent(key(ci, cj), k -> new java.util.ArrayList<>()).add((w.index << 20) | i);
            }
        }
        for (var e : m.entrySet()) grid.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).toArray());
        built = true;
    }

    private static long key(int i, int j) { return ((long) i << 32) ^ (j & 0xffffffffL); }

    /** Ergebnis einer Abfrage. */
    public static final class Hit {
        public Way way;
        /** Bogenlänge, Querlage (rechts positiv), Höhe der Fahrbahn dort (am Rand festgehalten). */
        public double s, u;
        public float deck;
        /** Befestigte Fläche 0..1 (1 auf der Fahrbahn, 0 jenseits des Banketts). */
        public float cover;
    }

    private static final ThreadLocal<double[]> TMP = ThreadLocal.withInitial(() -> new double[3]);
    private static final ThreadLocal<float[]> TMPF = ThreadLocal.withInitial(() -> new float[6]);

    /**
     * Nächster Weg an (x, z) innerhalb von Bankett und {@link #reach}; false, wenn keiner nah ist.
     */
    public boolean locate(double x, double z, Hit h) {
        if (!built) throw new IllegalStateException("Network.build fehlt");
        int[] c = grid.get(key((int) Math.floor(x / cell), (int) Math.floor(z / cell)));
        if (c == null) return false;
        double[] o = TMP.get();
        double best = Double.MAX_VALUE;
        Way bw = null;
        double bs = 0, bu = 0;
        for (int q : c) {
            Way w = ways.get(q >>> 20);
            w.project(q & 0xfffff, x, z, o);
            // Abstand zum Rand des Weges: so gewinnt ein breiter Weg vor einem schmalen daneben
            double d = Math.sqrt(o[2]) - w.type.half();
            if (d < best) { best = d; bw = w; bs = o[0]; bu = o[1]; }
        }
        if (bw == null || best > reach) return false;
        h.way = bw; h.s = bs; h.u = bu;
        float[] f = TMPF.get();
        float e = bw.type.half();
        bw.at(bs, Math.max(-e, Math.min(e, bu)), f);
        h.deck = f[1];
        float au = (float) Math.abs(bu), pv = bw.type.paved();
        h.cover = 1 - WNoise.smooth(pv, pv + 0.6f * bw.type.verge + 0.05f, au);
        return true;
    }

    /**
     * Gelände h an (x, z), geformt von den Wegen: unter Fahrbahn und Bankett knapp unter der Fahrbahn,
     * daneben Damm oder Einschnitt, weiter außen unverändert. Unter Brücken bleibt der Boden, wie er ist.
     */
    public double shape(double x, double z, double h) {
        Hit t = HIT.get();
        if (!locate(x, z, t)) return h;
        Way w = t.way;
        if (w.bridgeAt(t.s)) return h;
        float e = w.type.half();
        double yd = t.deck - w.type.thickness - sink;
        double dd = Math.abs(t.u) - e - shelf;
        if (dd <= 0) return yd;
        double lo = yd - dd * fillSlope, hi = yd + dd * cutSlope;
        double c = Math.max(lo, Math.min(hi, h));
        double k = WNoise.smooth(reach * 0.6f, reach, (float) dd);
        return c + (h - c) * k;
    }

    private static final ThreadLocal<Hit> HIT = ThreadLocal.withInitial(Hit::new);

    /** Wie weit (x, z) befestigt ist, 0..1; für Gras und Bodenfarbe. */
    public float cover(double x, double z) {
        Hit t = HIT.get();
        return locate(x, z, t) ? t.cover : 0;
    }

    /** Geformter Boden als {@link Ground}: für Renderer und Pflanzen, die auf dem Gelände stehen. */
    public Ground shaped(Ground g) {
        return new Ground() {
            @Override public float height(double x, double z) { return (float) shape(x, z, g.height(x, z)); }
            @Override public float water(double x, double z) { return g.water(x, z); }
        };
    }
}
