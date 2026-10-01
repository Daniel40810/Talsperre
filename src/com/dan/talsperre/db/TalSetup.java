package com.dan.talsperre.db;

import com.dan.talsperre.camera.CameraPath;
import com.dan.talsperre.camera.Director.Program;
import com.dan.talsperre.camera.Director.Shot;
import com.dan.talsperre.camera.Tours;
import com.dan.talsperre.camera.Viewpoint;
import com.dan.talsperre.core.Terrain;
import com.dan.talsperre.world.Valley;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Einrichtung der Datenbank (Schema DEMO, Präfix TAL_) mit Durchstich. Aufruf in NetBeans über "Run File" auf dieser Klasse
 * oder auf der Kommandozeile: {@code java -cp ... com.dan.talsperre.db.TalSetup [anlegen|neu|entfernen|pruefen]}.
 * <ul>
 * <li><b>anlegen</b> (Standard): legt Tabellen, Stammdaten, Spatial-Metadaten und -Indizes an, füllt Blickpunkte und Fahrten aus
 *     dem Modell und führt den Durchstich aus. Bricht ab, wenn schon TAL_-Tabellen da sind.</li>
 * <li><b>neu</b>: entfernt zuerst alle TAL_-Tabellen (auch eigene Voreinstellungen und Blickpunkte!), dann wie anlegen.</li>
 * <li><b>entfernen</b>: nur aufräumen.</li>
 * <li><b>pruefen</b>: nur der Durchstich.</li>
 * </ul>
 * Die Verbindung steht in {@link TalDb} und lässt sich mit {@code -Dtal.db.url}, {@code -Dtal.db.user}, {@code -Dtal.db.pass} ändern.
 */
public final class TalSetup {
    private static final String[] TABLES = {"TAL_BAUWERK", "TAL_EREIGNIS", "TAL_PEGEL_STATION", "TAL_PEGEL_MESSUNG", "TAL_BLICKPUNKT",
            "TAL_FAHRT", "TAL_FAHRT_SCHRITT", "TAL_FAHRT_POSE", "TAL_VOREINSTELLUNG", "TAL_VOREINSTELLUNG_WERT"};

    private int ok, bad;

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0].toLowerCase() : "anlegen";
        TalSetup s = new TalSetup();
        int code;
        try {
            code = s.run(mode);
        } catch (SQLException e) {
            System.out.println("FEHLER: " + e.getMessage());
            code = 2;
        } catch (IOException e) {
            System.out.println("FEHLER beim Lesen des Skripts: " + e.getMessage());
            code = 2;
        }
        System.exit(code);
    }

    private int run(String mode) throws SQLException, IOException {
        TalDb db = new TalDb();
        System.out.println("Talsperre · Datenbank " + db.describe() + " · Modus " + mode);
        switch (mode) {
            case "entfernen":
                try (Connection c = db.open()) { runScript(c, "tal_drop.sql"); }
                System.out.println("Alle TAL_-Tabellen entfernt.");
                return 0;
            case "pruefen":
                return verify(db);
            case "neu":
                try (Connection c = db.open()) { runScript(c, "tal_drop.sql"); }
                System.out.println("Alte TAL_-Tabellen entfernt.");
                return create(db);
            case "anlegen":
                return create(db);
            default:
                System.out.println("Unbekannter Modus. Erlaubt: anlegen, neu, entfernen, pruefen");
                return 2;
        }
    }

    private int create(TalDb db) throws SQLException, IOException {
        try (Connection c = db.open()) {
            if (count(c, "SELECT COUNT(*) FROM user_tables WHERE table_name LIKE 'TAL\\_%' ESCAPE '\\'") > 0) {
                System.out.println("Es gibt schon TAL_-Tabellen. Mit dem Modus 'neu' werden sie entfernt und neu angelegt (eigene Voreinstellungen gehen dabei verloren).");
                return 2;
            }
            int n = runScript(c, "tal_setup.sql");
            System.out.println("tal_setup.sql: " + n + " Anweisungen ausgeführt.");
            System.out.println("Baue das Modell für Blickpunkte und Fahrten …");
            Terrain t = Valley.build().terrain;
            int vp = seedViewpoints(c, t);
            int[] f = seedTours(c, t);
            System.out.println("Blickpunkte: " + vp + " · Fahrten: " + f[0] + " mit " + f[1] + " Einstellungen und " + f[2] + " Posen");
        }
        return verify(db);
    }

    // ------------------------------------------------------------ Skript

    /** Zerlegt ein Skript aus dem Klassenpfad: Anweisungen enden mit einer Zeile "/", Zeilen mit "--" sind Kommentar. */
    public static List<String> parse(String resource) throws IOException {
        List<String> stmts = new ArrayList<>();
        try (InputStream in = TalSetup.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException(resource + " nicht gefunden (liegt neben TalSetup im Klassenpfad)");
            StringBuilder cur = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String t = line.trim();
                    if (t.startsWith("--")) continue;
                    if (t.equals("/")) {
                        add(stmts, cur);
                        cur.setLength(0);
                    } else if (!t.isEmpty()) cur.append(line).append('\n');
                }
                add(stmts, cur);
            }
        }
        return stmts;
    }

    static int runScript(Connection c, String resource) throws SQLException, IOException {
        List<String> stmts = parse(resource);
        int n = 0;
        try (Statement st = c.createStatement()) {
            for (String s : stmts) {
                try {
                    st.execute(s);
                } catch (SQLException e) {
                    String head = s.length() > 90 ? s.substring(0, 90) + " …" : s;
                    throw new SQLException(e.getMessage().trim() + "\n  bei: " + head.replace('\n', ' '), e.getSQLState(), e.getErrorCode(), e);
                }
                n++;
            }
        }
        return n;
    }

    private static void add(List<String> out, StringBuilder cur) {
        String s = cur.toString().trim();
        if (s.isEmpty()) return;
        String up = s.toUpperCase();
        // PL/SQL-Blöcke behalten das Semikolon, alle anderen Anweisungen dürfen keins haben
        if (!(up.startsWith("BEGIN") || up.startsWith("DECLARE"))) while (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();
        out.add(s);
    }

    // ------------------------------------------------------------ Füllen aus dem Modell

    private static int seedViewpoints(Connection c, Terrain t) throws SQLException {
        Viewpoint[] vp = Viewpoint.all(t);
        String sql = "INSERT INTO tal_blickpunkt (nr, name, art, notiz, ex, ey, ez, tx, ty, tz) VALUES (?, ?, 'STANDARD', ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < vp.length; i++) {
                ps.setInt(1, i + 1);
                ps.setString(2, vp[i].name);
                ps.setString(3, vp[i].note);
                for (int k = 0; k < 6; k++) ps.setDouble(4 + k, round3(vp[i].pose[k]));
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return vp.length;
    }

    /** Fahrten, Einstellungen und Schlüsselposen; liefert die Anzahlen. */
    private static int[] seedTours(Connection c, Terrain t) throws SQLException {
        int tours = 0, shots = 0, poses = 0;
        String fSql = "INSERT INTO tal_fahrt (nr, name, dauer_s, beschreibung) VALUES (?, ?, ?, ?)";
        String sSql = "INSERT INTO tal_fahrt_schritt (fahrt_id, nr, dauer_s, kopf, beschreibung, uhr_von, uhr_bis) VALUES (?, ?, ?, ?, ?, ?, ?)";
        String pSql = "INSERT INTO tal_fahrt_pose (fahrt_id, schritt_nr, nr, t_s, ex, ey, ez, tx, ty, tz) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        for (int i = 0; i < Tours.NAMES.length; i++) {
            Program p = Tours.get(i, t);
            long id;
            try (PreparedStatement ps = c.prepareStatement(fSql, new String[]{"ID"})) {
                ps.setInt(1, i + 1);
                ps.setString(2, Tours.NAMES[i]);
                ps.setDouble(3, Math.round(p.duration() * 10) / 10.0);
                String d = null;
                for (Shot s : p.shots) if (s.text != null) { d = s.text; break; }
                ps.setString(4, cut(d, 600));
                ps.executeUpdate();
                try (ResultSet k = ps.getGeneratedKeys()) { k.next(); id = k.getLong(1); }
            }
            tours++;
            try (PreparedStatement sp = c.prepareStatement(sSql); PreparedStatement pp = c.prepareStatement(pSql)) {
                int sn = 0;
                for (Shot s : p.shots) {
                    sn++;
                    sp.setLong(1, id);
                    sp.setInt(2, sn);
                    sp.setDouble(3, Math.round(s.path.duration() * 10) / 10.0);
                    sp.setString(4, cut(s.head, 120));
                    sp.setString(5, cut(s.text, 800));
                    if (Double.isNaN(s.h0)) sp.setNull(6, Types.NUMERIC); else sp.setDouble(6, s.h0);
                    if (Double.isNaN(s.h1)) sp.setNull(7, Types.NUMERIC); else sp.setDouble(7, s.h1);
                    sp.addBatch();
                    CameraPath path = s.path;
                    for (int k = 0; k < path.size(); k++) {
                        double[] key = path.key(k);
                        pp.setLong(1, id);
                        pp.setInt(2, sn);
                        pp.setInt(3, k + 1);
                        pp.setDouble(4, Math.round(key[0] * 100) / 100.0);
                        for (int m = 0; m < 6; m++) pp.setDouble(5 + m, round3(key[1 + m]));
                        pp.addBatch();
                        poses++;
                    }
                    shots++;
                }
                sp.executeBatch();   // Einstellungen zuerst (Fremdschlüssel der Posen)
                pp.executeBatch();
            }
        }
        return new int[]{tours, shots, poses};
    }

    private static double round3(double v) { return Math.round(v * 1000) / 1000.0; }

    private static String cut(String s, int n) { return s == null ? null : s.length() <= n ? s : s.substring(0, n - 1) + "…"; }

    // ------------------------------------------------------------ Durchstich

    private void check(String what, boolean good, String detail) {
        if (good) ok++; else bad++;
        System.out.println((good ? "  ok     " : "  FEHLER ") + what + (detail == null || detail.isEmpty() ? "" : " · " + detail));
    }

    private static int count(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(sql)) { r.next(); return r.getInt(1); }
    }

    /** Prüft Tabellen, Zeilenzahlen, Spatial (Gültigkeit, Indizes, Abfrage), Pegelbereich und einen Hin-und-zurück-Lauf der App-Zugriffe. */
    private int verify(TalDb db) throws SQLException {
        System.out.println("Durchstich:");
        ok = 0; bad = 0;
        try (Connection c = db.open()) {
            check("Verbindung " + db.describe(), true, null);
            Map<String, Integer> min = new LinkedHashMap<>();
            min.put("TAL_BAUWERK", 6); min.put("TAL_EREIGNIS", 4); min.put("TAL_PEGEL_STATION", 2); min.put("TAL_PEGEL_MESSUNG", 730);
            min.put("TAL_BLICKPUNKT", 17); min.put("TAL_FAHRT", 9); min.put("TAL_FAHRT_SCHRITT", 9); min.put("TAL_FAHRT_POSE", 50);
            min.put("TAL_VOREINSTELLUNG", 5); min.put("TAL_VOREINSTELLUNG_WERT", 30);
            for (String tb : TABLES) {
                int want = min.get(tb);
                try {
                    int n = count(c, "SELECT COUNT(*) FROM " + tb);
                    check(tb, n >= want, n + " Zeilen (erwartet mindestens " + want + ")");
                } catch (SQLException e) {
                    check(tb, false, e.getMessage().trim());
                }
            }
            // Spatial
            try (Statement st = c.createStatement()) {
                int bad0 = 0;
                StringBuilder why = new StringBuilder();
                for (String[] g : new String[][]{{"TAL_BAUWERK", "kennung"}, {"TAL_PEGEL_STATION", "kennung"}}) {
                    try (ResultSet r = st.executeQuery("SELECT " + g[1] + ", SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(geom, 0.05), t.geom.SDO_SRID FROM " + g[0] + " t")) {
                        while (r.next()) {
                            if (!"TRUE".equals(r.getString(2)) || r.getInt(3) != 8307) { bad0++; why.append(r.getString(1)).append(": ").append(r.getString(2)).append("; "); }
                        }
                    }
                }
                check("Geometrien gültig, SRID 8307", bad0 == 0, why.toString());
                int idx = 0;
                StringBuilder st2 = new StringBuilder();
                try (ResultSet r = st.executeQuery("SELECT index_name, status, domidx_status, domidx_opstatus FROM user_indexes WHERE index_name IN ('TAL_BAUWERK_SIDX','TAL_PEGEL_STATION_SIDX')")) {
                    while (r.next()) {
                        idx++;
                        boolean good = "VALID".equals(r.getString(3)) && "VALID".equals(r.getString(4));
                        st2.append(r.getString(1)).append(good ? " valid; " : " " + r.getString(3) + "/" + r.getString(4) + "; ");
                        if (!good) bad0++;
                    }
                }
                check("Spatial-Indizes", idx == 2 && bad0 == 0, st2.toString());
                int near = count(c, "SELECT COUNT(*) FROM tal_bauwerk b, tal_bauwerk m WHERE m.kennung = 'EDERTALSPERRE' AND b.kennung <> m.kennung "
                        + "AND SDO_WITHIN_DISTANCE(b.geom, m.geom, 'distance=6 unit=KM') = 'TRUE'");
                check("Räumliche Abfrage: Bauwerke im Umkreis von 6 km der Mauer", near >= 3, near + " gefunden");
            } catch (SQLException e) {
                check("Spatial", false, e.getMessage().trim());
            }
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT MIN(pegel_m), MAX(pegel_m) FROM tal_pegel_messung")) {
                r.next();
                double lo = r.getDouble(1), hi = r.getDouble(2);
                check("Pegelreihe im Bereich der Szene (−30 m bis +1,5 m)", lo >= -30 && hi <= 1.5, String.format(java.util.Locale.GERMAN, "%.2f m bis %.2f m", lo, hi));
            } catch (SQLException e) {
                check("Pegelreihe", false, e.getMessage().trim());
            }
        }
        // Hin und zurück mit denselben Methoden wie die App
        try {
            Map<String, String> v = new LinkedHashMap<>();
            v.put("hour", "9.5"); v.put("rays", "1"); v.put("level", "-3.5");
            db.savePreset("__DURCHSTICH__", "Test, wird gleich gelöscht", v);
            Map<String, String> back = db.loadPreset("__DURCHSTICH__");
            check("Voreinstellung speichern und laden", back.equals(new java.util.TreeMap<>(v)) || back.equals(v), back.toString());
            v.put("hour", "10.5");
            db.savePreset("__DURCHSTICH__", "Test", v);
            check("Voreinstellung überschreiben", "10.5".equals(db.loadPreset("__DURCHSTICH__").get("hour")), null);
            db.deletePreset("__DURCHSTICH__");
            boolean gone = true;
            for (TalDb.Preset p : db.presets()) if (p.name().equals("__DURCHSTICH__")) gone = false;
            check("Voreinstellung löschen", gone, null);
            db.saveSpot("__DURCHSTICH__", "Test", new double[]{1, 2, 3, 4, 5, 6});
            boolean found = false;
            for (TalDb.Spot s : db.spots()) if (s.name().equals("__DURCHSTICH__") && s.pose()[5] == 6) found = true;
            db.deleteSpot("__DURCHSTICH__");
            check("Blickpunkt merken und löschen", found, null);
            check("Steckbrief (Spatial-Auswertung)", db.bauwerke().size() >= 6, db.bauwerke().size() + " Bauwerke");
            List<TalDb.Station> st = db.stations();
            check("Pegelreihe lesen", !st.isEmpty() && db.pegel(st.get(0).id()).size() == 365, null);
            check("Fahrtenliste", db.fahrten().size() >= 9, null);
            check("Statuszeile der App", db.check().startsWith("verbunden"), db.check());
        } catch (SQLException e) {
            check("Zugriffe der App", false, e.getMessage().trim());
        }
        System.out.println(bad == 0 ? "Durchstich bestanden: " + ok + " von " + ok + " Prüfungen ok." : "Durchstich NICHT bestanden: " + bad + " von " + (ok + bad) + " Prüfungen fehlgeschlagen.");
        return bad == 0 ? 0 : 1;
    }
}
