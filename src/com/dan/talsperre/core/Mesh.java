package com.dan.talsperre.core;

/**
 * Fertiges Dreiecksnetz der Szene. Dreiecke sind nach räumlichen Blöcken (Chunks)
 * sortiert, damit der Renderer ganze Blöcke gegen das Sichtfeld prüfen kann.
 * Übernommen aus Semiramis, ohne das Wachsen.
 */
public final class Mesh {
    public final int nv, nt;
    /** Wird erhöht, wenn Ecken nachträglich verschoben werden (Sinter-Zeitraffer). */
    public volatile int version;
    /** Ecken, die sich von selbst bewegen (Wind, Drehkörper); einmal ermittelt. */
    private int[] moving;

    public int[] moving() {
        int[] m = moving;
        if (m != null) return m;
        int n = 0;
        for (int v = 0; v < nv; v++) if (sway[v] != 0 || flutter[v] != 0 || spin[v] >= 0 || lake[v] != 0) n++;
        m = new int[n];
        n = 0;
        for (int v = 0; v < nv; v++) if (sway[v] != 0 || flutter[v] != 0 || spin[v] >= 0 || lake[v] != 0) m[n++] = v;
        return moving = m;
    }
    public final float[] pos, nrm;   // 3 je Ecke
    public final float[] sky;        // Himmelssicht je Ecke, 0..1 (1 = frei)
    /** Ausschlag im Wind je Ecke in Metern bei vollem Wind (0 = starr). */
    public final float[] sway;
    /** Zittern der Blätter je Ecke in Metern bei vollem Wind, entlang der Normale (0 = keins). */
    public final float[] flutter;
    /** Drehkörper je Ecke (−1 = keiner) und die Drehachsen: Ursprung, Richtung, Winkelgeschwindigkeit. */
    public final int[] spin;
    /** Ecken der Seefläche (Gruppe 3): sie folgen dem Pegel lakeDy. */
    public final byte[] lake;
    /** Bauzustand je Ecke (siehe {@link MeshBuilder#riseMode}) und Schwelle. */
    public final byte[] rise;
    public final float[] riseThr;
    /** Bauhöhe der Mauer (m, y=0 Stauziel); ab 1e5 fertig. Flutfront: Weg in m entlang der Eder; negativ = keine Flut. */
    public volatile float buildY = 1e6f, flood = -1f;
    /** Bit g gesetzt: Gruppe g wird nicht gezeichnet (und wirft keinen Schatten). */
    public volatile int hide = (1 << 6) | (1 << 7);

    public void setHide(int mask) { hide = mask; version++; }
    public void setBuild(float y) { buildY = y; version++; }
    public void setFlood(float s) { flood = s; version++; }
    public boolean shown(int g) { return (hide >> g & 1) == 0; }

    /** Höhe der Ecke v nach dem Bauzustand. */
    public float deformY(int v, float y) {
        int m = rise[v];
        if (m == 0) return y;
        float b = buildY;
        switch (m) {
            case 1: return y > b - 0.02f ? Math.min(y, b - 0.02f) : y;
            case 2: return b >= riseThr[v] ? y : y - 600;
            case 5: return b < 1e5f ? y : y - 600;
            case 6: return b >= 1e5f ? y - 600 : Math.min(y, b + 3);
            case 3: {
                float k = (flood - riseThr[v]) / 8f;
                k = k < 0 ? 0 : (k > 1 ? 1 : k);
                return flood < 0 ? y - 600 : y - 600 * (1 - k);
            }
            default: return b >= riseThr[v] && b < riseThr[v] + 0.25f ? b : y - 600;
        }
    }
    /** Pegel gegenüber Stauziel in Metern (Seeecken werden um diesen Wert gehoben). */
    public volatile float lakeDy;
    public static final float LAKE_MIN = 40, LAKE_MAX = 4;
    public double[][] spinners = new double[0][];
    public final int[] idx;          // 3 je Dreieck
    public final byte[] mat, grp;    // je Dreieck
    public final float[] fn;         // Flächennormale, 3 je Dreieck
    public final int nChunks;
    public final int[] chunkStart, chunkCount, chunkGrp;
    public final float[] chunkBox;   // minX,minY,minZ,maxX,maxY,maxZ

    Mesh(int nv, int nt, float[] pos, float[] nrm, int[] idx, byte[] mat, byte[] grp, float[] fn,
         int nChunks, int[] cs, int[] cc, int[] cg, float[] cb) {
        this.nv = nv; this.nt = nt; this.pos = pos; this.nrm = nrm; this.idx = idx;
        this.mat = mat; this.grp = grp; this.fn = fn; this.nChunks = nChunks;
        this.chunkStart = cs; this.chunkCount = cc; this.chunkGrp = cg; this.chunkBox = cb;
        this.sky = new float[nv];
        java.util.Arrays.fill(sky, 1f);
        this.sway = new float[nv];
        this.flutter = new float[nv];
        this.spin = new int[nv];
        this.lake = new byte[nv];
        this.rise = new byte[nv];
        this.riseThr = new float[nv];
        java.util.Arrays.fill(spin, -1);
    }
}
