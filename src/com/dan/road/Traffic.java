package com.dan.road;

/**
 * Verkehr auf dem Netz. Jeder Weg bekommt Fahrstreifen je Richtung; Fahrzeuge folgen einander nach dem
 * Intelligent Driver Model (Abstand, Zeitlücke, sanftes Bremsen), nehmen Kurven nur so schnell, wie es
 * die Querbeschleunigung erlaubt, und bremsen rechtzeitig davor. Auf mehrstreifigen Fahrbahnen
 * wechseln sie den Streifen nach MOBIL (lohnt es sich, ist es sicher, rechts fahren), mit Blinker.
 * Auf dem Feldweg weichen sich Entgegenkommende aus, auf der Straße überholen Autos Radfahrer mit
 * Abstand, wenn kein Gegenverkehr kommt, sonst fahren sie hinterher. Hindernisse (etwa Bisons auf der
 * Fahrbahn) halten den Verkehr an; das erste Fahrzeug schaltet die Warnblinker ein.
 * <p>
 * Offene Wege: Fahrzeuge kommen an den Enden herein und fahren am anderen Ende hinaus. Geschlossene
 * Wege (Rundkurse) werden einmal gefüllt.
 */
public final class Traffic {
    /** Ein Fahrstreifen (oder die Spur der Wanderer) in einer Richtung. */
    public static final class Lane {
        public final Way way;
        public final int dir;
        /** Querlage der Mitte (rechts von wachsendem s positiv). */
        public final float u;
        /** 0 = rechter Streifen in Fahrtrichtung, dann nach innen. */
        public final int k;
        /** Nur für Wanderer oder Radfahrer. */
        public final boolean slow;
        final java.util.ArrayList<Mover> list = new java.util.ArrayList<>();
        Lane inner, outer, opposite, slowLane;
        float rate;
        final java.util.ArrayList<float[]> stops = new java.util.ArrayList<>();

        Lane(Way w, int dir, float u, int k, boolean slow) { way = w; this.dir = dir; this.u = u; this.k = k; this.slow = slow; }

        public int count() { return list.size(); }
    }

    public final Network net;
    public final java.util.List<Lane> lanes = new java.util.ArrayList<>();
    public final java.util.List<Mover> movers = new java.util.ArrayList<>();
    /** Zeitlücke (s), Mindestabstand (m), Höflichkeit und Schwelle beim Spurwechsel. */
    public float headway = 1.4f, minGap = 2.2f, politeness = 0.3f, threshold = 0.25f;
    /** Zähler der Spurwechsel seit dem Start. */
    public int laneChanges;
    /** Fahrzeuge je Stunde und Richtung auf Weg i (Wanderer und Radfahrer extra in {@link #slowFlow}). */
    public final float[] flow, slowFlow;
    private final java.util.Random rnd;
    private int seedCounter;
    private final float[] F = new float[6];

    public Traffic(Network net, long seed) {
        this.net = net;
        rnd = new java.util.Random(seed);
        flow = new float[net.ways.size()];
        slowFlow = new float[net.ways.size()];
        for (Way w : net.ways) {
            WayType t = w.type;
            switch (t.kind) {
                case PATH -> { flow[w.index] = 0; slowFlow[w.index] = 40; }
                case TRACK -> { flow[w.index] = 6; slowFlow[w.index] = 20; }
                case ROAD -> { flow[w.index] = 220; slowFlow[w.index] = 8; }
                case MOTORWAY -> { flow[w.index] = 1800; slowFlow[w.index] = 0; }
            }
            for (int dir = -1; dir <= 1; dir += 2) {
                java.util.List<Lane> group = new java.util.ArrayList<>();
                switch (t.kind) {
                    case PATH -> group.add(new Lane(w, dir, dir * t.laneCenter(0), 0, true));
                    case TRACK -> {
                        group.add(new Lane(w, dir, 0, 0, false));
                        lanes.add(new Lane(w, dir, dir * (t.rutSpacing / 2 + 0.2f), 0, true));
                    }
                    default -> {
                        for (int k = 0; k < t.lanes; k++) group.add(new Lane(w, dir, dir * t.laneCenter(k), k, false));
                        if (t.kind == WayType.Kind.ROAD) lanes.add(new Lane(w, dir, dir * (t.median / 2 + t.lanes * t.laneWidth - 0.45f), 0, true));
                    }
                }
                for (int k = 0; k < group.size(); k++) {
                    if (k > 0) { group.get(k).outer = group.get(k - 1); group.get(k - 1).inner = group.get(k); }
                }
                lanes.addAll(group);
            }
        }
        for (Lane a : lanes) for (Lane b : lanes)
            if (a.way == b.way && a.dir == -b.dir && a.k == b.k && a.slow == b.slow) a.opposite = b;
        for (Lane a : lanes) for (Lane b : lanes)
            if (a.way == b.way && a.dir == b.dir && !a.slow && b.slow) a.slowLane = b;
        rates();
    }

    /** Übernimmt {@link #flow} und {@link #slowFlow} in die Streifen (nach einer Änderung aufrufen). */
    public void rates() {
        for (Lane l : lanes) {
            int nl = 0;
            for (Lane o : lanes) if (o.way == l.way && o.dir == l.dir && o.slow == l.slow) nl++;
            float f = (l.slow ? slowFlow : flow)[l.way.index] / 3600f;
            // rechts mehr Verkehr als innen
            float share = nl <= 1 ? 1 : (l.k == 0 ? 0.6f : 0.4f / (nl - 1));
            l.rate = f * share;
        }
    }

    // ------------------------------------------------------------ Arten und Farben

    private Vehicle pick(Lane l) {
        float r = rnd.nextFloat();
        if (l.slow) return l.way.type.kind == WayType.Kind.ROAD ? Vehicle.BICYCLE : r < 0.8f ? Vehicle.HIKER : Vehicle.BICYCLE;
        switch (l.way.type.kind) {
            case TRACK: return r < 0.45f ? Vehicle.PICKUP : r < 0.75f ? Vehicle.TRACTOR : Vehicle.SUV;
            case MOTORWAY: return r < 0.52f ? Vehicle.CAR : r < 0.66f ? Vehicle.SUV : r < 0.88f ? Vehicle.TRUCK : r < 0.92f ? Vehicle.BUS : r < 0.97f ? Vehicle.CAMPER : Vehicle.MOTORBIKE;
            default: return r < 0.42f ? Vehicle.CAR : r < 0.66f ? Vehicle.SUV : r < 0.78f ? Vehicle.PICKUP : r < 0.9f ? Vehicle.CAMPER : r < 0.95f ? Vehicle.BUS : Vehicle.MOTORBIKE;
        }
    }

    private static final float[][] PAINT = {
            {0.62f, 0.62f, 0.60f}, {0.40f, 0.41f, 0.42f}, {0.03f, 0.03f, 0.035f}, {0.42f, 0.03f, 0.03f}, {0.04f, 0.10f, 0.32f},
            {0.05f, 0.14f, 0.07f}, {0.20f, 0.20f, 0.21f}, {0.55f, 0.50f, 0.40f}, {0.30f, 0.12f, 0.03f}, {0.12f, 0.24f, 0.40f}};
    private static final float[][] CLOTHES = {
            {0.45f, 0.05f, 0.03f}, {0.05f, 0.12f, 0.40f}, {0.55f, 0.25f, 0.02f}, {0.08f, 0.25f, 0.10f}, {0.35f, 0.33f, 0.28f}, {0.25f, 0.05f, 0.25f}};

    private Mover make(Lane l) {
        Vehicle k = pick(l);
        Mover m = new Mover(k, seedCounter++ * 7919 + 13);
        float[] c = k.walks() || k.pedals() ? CLOTHES[rnd.nextInt(CLOTHES.length)] : PAINT[rnd.nextInt(PAINT.length)];
        if (k == Vehicle.CAMPER) c = new float[]{0.62f, 0.60f, 0.56f};
        if (k == Vehicle.BUS) c = rnd.nextBoolean() ? new float[]{0.55f, 0.36f, 0.04f} : new float[]{0.45f, 0.05f, 0.04f};
        if (k == Vehicle.TRACTOR) c = rnd.nextBoolean() ? new float[]{0.05f, 0.22f, 0.05f} : new float[]{0.45f, 0.06f, 0.03f};
        System.arraycopy(c, 0, m.color, 0, 3);
        m.lane = l;
        m.temper = 0.88f + 0.24f * rnd.nextFloat();
        m.u = l.u; m.uFrom = l.u; m.uTo = l.u;
        m.think = rnd.nextFloat() * 2;
        m.step = rnd.nextFloat() * 10;
        return m;
    }

    // ------------------------------------------------------------ Hindernisse

    private final Network.Hit hit = new Network.Hit();

    /** Hindernisse auf der Fahrbahn: je x, z, Radius. Gelten bis zum nächsten Aufruf. */
    public void obstacles(float[] xzr, int n) {
        for (Lane l : lanes) l.stops.clear();
        for (int i = 0; i < n; i++) {
            float x = xzr[3 * i], z = xzr[3 * i + 1], r = xzr[3 * i + 2];
            if (!net.locate(x, z, hit)) continue;
            for (Lane l : lanes) {
                if (l.way != hit.way) continue;
                if (Math.abs(hit.u - l.u) > r + 1.3f) continue;
                float s = (float) hit.s;
                float p = l.dir > 0 ? s : l.way.length - s;
                l.stops.add(new float[]{p - r, 0});
            }
        }
    }

    // ------------------------------------------------------------ Füllen

    /** Verteilt Fahrzeuge über alle Streifen nach Verkehrsstärke und Geschwindigkeit. */
    public void populate() {
        for (Lane l : lanes) { movers.removeAll(l.list); l.list.clear(); }
        for (Lane l : lanes) {
            if (l.rate <= 0) continue;
            float vt = l.slow ? 1.3f : Math.max(3, l.way.type.speedLimit * 0.85f);
            float dens = l.rate / vt;
            float p = rnd.nextFloat() * 30;
            while (p < l.way.length - 10) {
                Mover m = make(l);
                m.p = p;
                m.v = Math.min(vt, m.kind.top()) * 0.9f;
                l.list.add(m);
                movers.add(m);
                float gap = (float) (-Math.log(1 - rnd.nextFloat()) / dens);
                p += Math.max(m.kind.length + (l.slow ? 3 : 20), gap);
            }
        }
    }

    // ------------------------------------------------------------ Schritt

    /** Ein Zeitschritt dt (s); lange Schritte werden geteilt. */
    public void step(float dt) {
        int sub = Math.max(1, (int) Math.ceil(dt / 0.1f));
        for (int k = 0; k < sub; k++) sub(dt / sub);
        for (Mover m : movers) pose(m, dt);
    }

    private void sub(float dt) {
        for (Lane l : lanes) l.list.sort((a, b) -> Float.compare(a.p, b.p));
        // Beschleunigungen
        for (Lane l : lanes) {
            int n = l.list.size();
            float L = l.way.length;
            for (int i = 0; i < n; i++) {
                Mover m = l.list.get(i);
                float gap = 1e6f, vl = m.v0;
                if (i + 1 < n) { Mover q = l.list.get(i + 1); gap = q.p - m.p - q.kind.length * 0.5f - m.kind.length * 0.5f; vl = q.v; }
                else if (l.way.closed && n > 1) { Mover q = l.list.get(0); gap = q.p + L - m.p - q.kind.length * 0.5f - m.kind.length * 0.5f; vl = q.v; }
                m.hazard = false;
                for (float[] st : l.stops) {
                    float g = st[0] - m.p - m.kind.length * 0.5f;
                    if (l.way.closed && g < -5) g += L;
                    if (g > -1 && g < gap) { gap = Math.max(0.05f, g); vl = 0; m.hazard = m.v < 2 && g < 12; }
                }
                desired(m);
                // Überholen von Radfahrern mit Abstand; bei Gegenverkehr dahinter bleiben
                float yieldT = 0;
                if (l.slowLane != null && l.way.type.kind == WayType.Kind.ROAD) {
                    Mover bike = ahead(l.slowLane, m.p - m.kind.length, 25);
                    if (bike != null) {
                        boolean oncoming = l.opposite != null && near(l.opposite, L - m.p, 70);
                        float g = bike.p - m.p - m.kind.length * 0.5f - 1;
                        if (oncoming && bike.p > m.p && g < gap) { gap = Math.max(0.1f, g); vl = bike.v; }
                        else if (!oncoming) yieldT = -1.3f;
                    }
                }
                // Feldweg und Pfad: Entgegenkommenden ausweichen
                if ((l.way.type.kind == WayType.Kind.TRACK || l.way.type.kind == WayType.Kind.PATH) && l.opposite != null) {
                    Mover o = aheadMirror(l.opposite, L - m.p, 35);
                    if (o != null) { yieldT = l.slow ? 0.35f : 0.9f; if (!l.slow) m.v0 = Math.min(m.v0, 3); }
                }
                m.yieldU += (yieldT - m.yieldU) * Math.min(1, dt * 0.8f);
                m.a = idm(m, gap, vl);
                if (m.curveBrake < 0) m.a = Math.min(m.a, m.curveBrake * 1.15f);
            }
        }
        // Spurwechsel
        for (Lane l : lanes) {
            if (l.inner == null && l.outer == null) continue;
            for (int i = 0; i < l.list.size(); i++) {
                Mover m = l.list.get(i);
                m.think -= dt;
                if (m.think > 0 || m.change < 1) continue;
                m.think = 1.2f + rnd.nextFloat();
                Lane best = null;
                float bestGain = threshold;
                for (Lane t : new Lane[]{l.inner, l.outer}) {
                    if (t == null) continue;
                    float g = gain(m, i, l, t);
                    if (g > bestGain) { bestGain = g; best = t; }
                }
                if (best != null) {
                    l.list.remove(i);
                    i--;
                    best.list.add(m);
                    m.uFrom = m.u; m.uTo = best.u; m.change = 0;
                    m.blink = best == l.inner ? -1 : 1;
                    m.lane = best;
                    laneChanges++;
                }
            }
        }
        // Bewegen, Ein- und Ausfahren
        for (Lane l : lanes) {
            float L = l.way.length;
            for (int i = l.list.size() - 1; i >= 0; i--) {
                Mover m = l.list.get(i);
                m.v = Math.max(0, m.v + m.a * dt);
                m.p += m.v * dt;
                m.brake = m.a < -0.6f || m.v < 0.2f;
                if (m.change < 1) {
                    m.change = Math.min(1, m.change + dt / 3.5f);
                    if (m.change >= 1) m.blink = 0;
                }
                if (l.way.closed) { if (m.p >= L) m.p -= L; }
                else if (m.p > L + 3) { l.list.remove(i); movers.remove(m); }
            }
            if (!l.way.closed && l.rate > 0 && rnd.nextFloat() < l.rate * dt) {
                float first = 1e9f;
                for (Mover m : l.list) first = Math.min(first, m.p);
                if (first > (l.slow ? 6 : 28)) {
                    Mover m = make(l);
                    m.p = -2;
                    desired(m);
                    m.v = m.v0 * 0.9f;
                    l.list.add(m);
                    movers.add(m);
                }
            }
        }
    }

    /** Wunschgeschwindigkeit: Art, Fahrer, Tempolimit und die Kurven der nächsten 120 m. */
    private void desired(Mover m) {
        Lane l = m.lane;
        WayType t = l.way.type;
        float lim = m.kind.walks() || m.kind.pedals() ? m.kind.top() : Math.min(m.kind.top(), t.speedLimit * m.kind.eager);
        float v = lim * m.temper;
        m.curveBrake = 0;
        if (!m.kind.walks()) {
            float s = m.s();
            float tm = Math.min(1.05f, m.temper);
            for (float d = 0; d <= 120; d += 5) {
                float raw = l.way.curveSpeed(s + l.dir * d), vc = raw * tm;
                v = Math.min(v, (float) Math.sqrt(vc * vc + 2 * m.kind.decel * d));
                // rechtzeitig bremsen: nötige Verzögerung bis zur Kurve
                if (raw < t.speedLimit - 0.01f && m.v > vc) m.curveBrake = Math.min(m.curveBrake, (vc * vc - m.v * m.v) / (2 * Math.max(4, d - 2)));
            }
        }
        m.v0 = Math.max(0.5f, v);
    }

    private float idm(Mover m, float gap, float vl) {
        float a = m.kind.accel, b = m.kind.decel;
        boolean slow = m.kind.walks() || m.kind.pedals();
        float T = slow ? 1.0f : headway * (1.1f - 0.2f * m.temper), s0 = slow ? 1.0f : minGap;
        float v = m.v;
        float ss = s0 + Math.max(0, v * T + v * (v - vl) / (2 * (float) Math.sqrt(a * b)));
        float r = v / m.v0;
        float acc = a * (1 - r * r * r * r - (ss / Math.max(0.1f, gap)) * (ss / Math.max(0.1f, gap)));
        return Math.max(-9, acc);
    }

    /** Lohnt der Wechsel von l nach t? (MOBIL) */
    private float gain(Mover m, int idx, Lane l, Lane t) {
        Mover lead = null, fol = null;
        for (Mover q : t.list) {
            if (q.p > m.p) { if (lead == null || q.p < lead.p) lead = q; }
            else if (fol == null || q.p > fol.p) fol = q;
        }
        float gapL = lead == null ? 1e6f : lead.p - m.p - (lead.kind.length + m.kind.length) * 0.5f;
        float gapF = fol == null ? 1e6f : m.p - fol.p - (fol.kind.length + m.kind.length) * 0.5f;
        if (gapL < 2 || gapF < 3) return -1e9f;
        float aNew = idm(m, gapL, lead == null ? m.v0 : lead.v);
        float fNew = fol == null ? 0 : idm(fol, gapF, m.v), fOld = fol == null ? 0 : fol.a;
        if (fNew < -4) return -1e9f;
        float bias = t == l.outer ? 0.3f : -0.3f;
        if (m.kind == Vehicle.TRUCK || m.kind == Vehicle.BUS || m.kind == Vehicle.CAMPER) bias += t == l.outer ? 0.6f : -0.6f;
        return aNew - m.a + politeness * (fNew - fOld) + bias;
    }

    private static Mover ahead(Lane l, float p, float range) {
        Mover best = null;
        for (Mover q : l.list) if (q.p > p && q.p < p + range && (best == null || q.p < best.p)) best = q;
        return best;
    }

    /** Ein Entgegenkommender (Streifen der Gegenrichtung), dessen Fortschritt sich p nähert. */
    private static Mover aheadMirror(Lane opp, float pMirror, float range) {
        for (Mover q : opp.list) if (q.p < pMirror && q.p > pMirror - range) return q;
        return null;
    }

    private static boolean near(Lane opp, float pMirror, float range) {
        for (Mover q : opp.list) if (q.p < pMirror + 5 && q.p > pMirror - range) return true;
        return false;
    }

    // ------------------------------------------------------------ Haltung in der Welt

    private void pose(Mover m, float dt) {
        Lane l = m.lane;
        Way w = l.way;
        float cu = m.change < 1 ? m.uFrom + (m.uTo - m.uFrom) * WNoise.smooth(0, 1, m.change) : l.u;
        m.u = cu + l.dir * m.yieldU;
        float s = m.s();
        w.at(s, m.u, F);
        float bank = F[5];
        m.x = F[0]; m.z = F[2];
        m.hx = F[3] * l.dir; m.hz = F[4] * l.dir;
        float half = m.kind.length * 0.4f;
        w.at(s + l.dir * half, m.u, F);
        float yf = F[1];
        w.at(s - l.dir * half, m.u, F);
        float yb = F[1];
        float y = (yf + yb) / 2;
        m.pitch = (float) Math.atan2(yf - yb, 2 * half);
        float kd = w.curv[Math.min(w.n - 1, Math.max(0, Math.round((float) w.wrap(s) / w.step())))] * l.dir;
        if (m.kind.walks()) {
            m.step += m.v * dt / 0.72f;
            m.bounce = 0.035f * Math.abs((float) Math.sin(m.step * Math.PI)) * Math.min(1, m.v);
            m.roll = 0;
        } else {
            float lat = m.v * m.v * kd;
            if (m.kind == Vehicle.MOTORBIKE || m.kind == Vehicle.BICYCLE) m.roll = -(float) Math.atan(lat / 9.81f);
            else m.roll = (float) Math.atan(bank * l.dir) + Math.max(-0.05f, Math.min(0.05f, lat * 0.008f));
            m.pitch += Math.max(-0.04f, Math.min(0.03f, m.a * 0.006f));
            float rough = w.type.roughness * Math.min(1, m.v / 4);
            m.bounce = rough * 0.06f * (WNoise.value(s * 0.9f + m.seed, m.seed * 0.37f) - 0.5f);
            m.pitch += rough * 0.03f * (WNoise.value(s * 0.7f + 3.3f + m.seed, m.seed * 0.11f) - 0.5f);
            float r = m.kind.shape().wheels.length > 0 ? m.kind.shape().wheels[1] : 0.35f;
            m.wheel += m.v * dt / r;
            if (m.kind.pedals()) m.step += m.v * dt / 2.1f;
        }
        m.y = y;
    }
}
