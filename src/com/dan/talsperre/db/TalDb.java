package com.dan.talsperre.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Zugriff auf die TAL_-Tabellen im Schema DEMO (Oracle 21c, Dienst PDBORCL). Die App kommt ohne Datenbank aus:
 * Es gibt keinen Verbindungsaufbau beim Start, jede Aktion öffnet kurz eine Verbindung mit kurzen Zeitgrenzen
 * und meldet Fehler als {@link SQLException}, die das Bedienfeld in der Statuszeile zeigt.
 * <p>
 * Die Verbindung lässt sich mit Systemeigenschaften ändern: {@code tal.db.url}, {@code tal.db.user},
 * {@code tal.db.pass}. Der Treiber (ojdbc11.jar in lib) wird nur über {@link DriverManager} geladen, der Code
 * übersetzt also auch ohne ihn.
 */
public final class TalDb {
    public static final String URL = "jdbc:oracle:thin:@//localhost:1521/PDBORCL";

    /** Eine gespeicherte Voreinstellung des Bedienfelds. */
    public record Preset(String name, String note) { }

    /** Ein gemerkter Blickpunkt: Augpunkt und Blickziel in Metern. */
    public record Spot(String name, String note, double[] pose) { }

    /** Steckbrief eines Bauwerks mit Spatial-Auswertung (Entfernung zur Mauer, Fläche, Bogenlänge, Gültigkeit). */
    public record Bauwerk(String kennung, String name, String art, Integer baujahr, Double hoeheM, Double laengeM, Double stauinhalt,
                          String bemerkung, int gtyp, Double distKm, Double flaecheKm2, Double bogenM, String gueltig) { }

    public record Station(long id, String kennung, String name) { }

    public record Pegel(LocalDate tag, double meter) { }

    public record Fahrt(int nr, String name, double dauerS, String beschreibung, int schritte, int posen) { }

    private final String url, user, pass;

    public TalDb() {
        url = System.getProperty("tal.db.url", URL);
        user = System.getProperty("tal.db.user", "DEMO");
        pass = System.getProperty("tal.db.pass", "de");
    }

    public String describe() { return user + "@" + url.substring(url.lastIndexOf('/') + 1); }

    /** Öffnet eine Verbindung mit kurzen Zeitgrenzen (Aufbau 5 s, Antwort 15 s). */
    public Connection open() throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", user);
        p.setProperty("password", pass);
        p.setProperty("oracle.net.CONNECT_TIMEOUT", "5000");
        p.setProperty("oracle.jdbc.ReadTimeout", "15000");
        DriverManager.setLoginTimeout(5);
        return DriverManager.getConnection(url, p);
    }

    /** Verbindung und Tabellen prüfen; liefert die Zeile für die Statuszeile. */
    public String check() throws SQLException {
        try (Connection c = open(); Statement st = c.createStatement()) {
            int bw = 0, pr = 0, sp = 0;
            try (ResultSet r = st.executeQuery("SELECT (SELECT COUNT(*) FROM tal_bauwerk), (SELECT COUNT(*) FROM tal_voreinstellung), (SELECT COUNT(*) FROM tal_blickpunkt WHERE art = 'EIGEN') FROM dual")) {
                r.next();
                bw = r.getInt(1); pr = r.getInt(2); sp = r.getInt(3);
            } catch (SQLException ex) {
                if (ex.getErrorCode() == 942) throw new SQLException("verbunden mit " + describe() + ", aber die TAL_-Tabellen fehlen: Setup ausführen (db.TalSetup)", ex);
                throw ex;
            }
            return "verbunden mit " + describe() + " · " + bw + " Bauwerke, " + pr + " Voreinstellungen, " + sp + " eigene Blickpunkte";
        }
    }

    // ------------------------------------------------------------ Voreinstellungen

    public List<Preset> presets() throws SQLException {
        List<Preset> out = new ArrayList<>();
        try (Connection c = open(); Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT name, notiz FROM tal_voreinstellung ORDER BY name")) {
            while (r.next()) out.add(new Preset(r.getString(1), r.getString(2)));
        }
        return out;
    }

    public Map<String, String> loadPreset(String name) throws SQLException {
        Map<String, String> m = new LinkedHashMap<>();
        String sql = "SELECT w.schluessel, w.wert FROM tal_voreinstellung_wert w JOIN tal_voreinstellung v ON v.id = w.voreinstellung_id WHERE v.name = ? ORDER BY w.schluessel";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) m.put(r.getString(1), r.getString(2));
            }
        }
        return m;
    }

    /** Legt die Voreinstellung an oder überschreibt sie (in einer Transaktion). */
    public void savePreset(String name, String note, Map<String, String> values) throws SQLException {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                long id = -1;
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM tal_voreinstellung WHERE name = ?")) {
                    ps.setString(1, name);
                    try (ResultSet r = ps.executeQuery()) { if (r.next()) id = r.getLong(1); }
                }
                if (id < 0) {
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO tal_voreinstellung (name, notiz) VALUES (?, ?)", new String[]{"ID"})) {
                        ps.setString(1, name);
                        ps.setString(2, note);
                        ps.executeUpdate();
                        try (ResultSet k = ps.getGeneratedKeys()) { k.next(); id = k.getLong(1); }
                    }
                } else {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE tal_voreinstellung SET notiz = ?, geaendert = SYSDATE WHERE id = ?")) {
                        ps.setString(1, note);
                        ps.setLong(2, id);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM tal_voreinstellung_wert WHERE voreinstellung_id = ?")) {
                        ps.setLong(1, id);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO tal_voreinstellung_wert (voreinstellung_id, schluessel, wert) VALUES (?, ?, ?)")) {
                    for (Map.Entry<String, String> e : values.entrySet()) {
                        ps.setLong(1, id);
                        ps.setString(2, e.getKey());
                        ps.setString(3, e.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    public void deletePreset(String name) throws SQLException {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement("DELETE FROM tal_voreinstellung WHERE name = ?")) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------ Eigene Blickpunkte

    public List<Spot> spots() throws SQLException {
        List<Spot> out = new ArrayList<>();
        String sql = "SELECT name, notiz, ex, ey, ez, tx, ty, tz FROM tal_blickpunkt WHERE art = 'EIGEN' ORDER BY angelegt, name";
        try (Connection c = open(); Statement st = c.createStatement(); ResultSet r = st.executeQuery(sql)) {
            while (r.next()) out.add(new Spot(r.getString(1), r.getString(2), new double[]{r.getDouble(3), r.getDouble(4), r.getDouble(5), r.getDouble(6), r.getDouble(7), r.getDouble(8)}));
        }
        return out;
    }

    /** Merkt die Pose unter dem Namen (ein vorhandener eigener Blickpunkt gleichen Namens wird überschrieben). */
    public void saveSpot(String name, String note, double[] pose) throws SQLException {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                int n;
                try (PreparedStatement ps = c.prepareStatement("UPDATE tal_blickpunkt SET notiz = ?, ex = ?, ey = ?, ez = ?, tx = ?, ty = ?, tz = ?, angelegt = SYSDATE WHERE name = ? AND art = 'EIGEN'")) {
                    ps.setString(1, note);
                    for (int i = 0; i < 6; i++) ps.setDouble(2 + i, pose[i]);
                    ps.setString(8, name);
                    n = ps.executeUpdate();
                }
                if (n == 0) {
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO tal_blickpunkt (name, art, notiz, ex, ey, ez, tx, ty, tz) VALUES (?, 'EIGEN', ?, ?, ?, ?, ?, ?, ?)")) {
                        ps.setString(1, name);
                        ps.setString(2, note);
                        for (int i = 0; i < 6; i++) ps.setDouble(3 + i, pose[i]);
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    public void deleteSpot(String name) throws SQLException {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement("DELETE FROM tal_blickpunkt WHERE name = ? AND art = 'EIGEN'")) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------ Steckbrief, Pegel, Fahrten

    private static final String STECKBRIEF = "SELECT b.kennung, b.name, b.art, b.baujahr, b.hoehe_m, b.laenge_m, b.stauinhalt_mio_m3, b.bemerkung, "
            + "b.geom.SDO_GTYPE AS gtyp, "
            + "SDO_GEOM.SDO_DISTANCE(b.geom, m.geom, 0.05, 'unit=KM') AS dist_km, "
            + "CASE WHEN b.geom.SDO_GTYPE = 2003 THEN SDO_GEOM.SDO_AREA(b.geom, 0.05, 'unit=SQ_KM') END AS flaeche_km2, "
            + "CASE WHEN b.geom.SDO_GTYPE = 2002 THEN SDO_GEOM.SDO_LENGTH(b.geom, 0.05, 'unit=M') END AS bogen_m, "
            + "SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(b.geom, 0.05) AS gueltig "
            + "FROM tal_bauwerk b, tal_bauwerk m WHERE m.kennung = 'EDERTALSPERRE' ORDER BY dist_km, b.name";

    public List<Bauwerk> bauwerke() throws SQLException {
        List<Bauwerk> out = new ArrayList<>();
        try (Connection c = open(); Statement st = c.createStatement(); ResultSet r = st.executeQuery(STECKBRIEF)) {
            while (r.next()) {
                out.add(new Bauwerk(r.getString(1), r.getString(2), r.getString(3), nInt(r, 4), nDouble(r, 5), nDouble(r, 6), nDouble(r, 7),
                        r.getString(8), r.getInt(9), nDouble(r, 10), nDouble(r, 11), nDouble(r, 12), r.getString(13)));
            }
        }
        return out;
    }

    public List<Station> stations() throws SQLException {
        List<Station> out = new ArrayList<>();
        try (Connection c = open(); Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT id, kennung, name FROM tal_pegel_station ORDER BY id")) {
            while (r.next()) out.add(new Station(r.getLong(1), r.getString(2), r.getString(3)));
        }
        return out;
    }

    public List<Pegel> pegel(long stationId) throws SQLException {
        List<Pegel> out = new ArrayList<>();
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement("SELECT tag, pegel_m FROM tal_pegel_messung WHERE station_id = ? ORDER BY tag")) {
            ps.setLong(1, stationId);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) out.add(new Pegel(r.getDate(1).toLocalDate(), r.getDouble(2)));
            }
        }
        return out;
    }

    public List<Fahrt> fahrten() throws SQLException {
        List<Fahrt> out = new ArrayList<>();
        String sql = "SELECT f.nr, f.name, f.dauer_s, f.beschreibung, "
                + "(SELECT COUNT(*) FROM tal_fahrt_schritt s WHERE s.fahrt_id = f.id), (SELECT COUNT(*) FROM tal_fahrt_pose p WHERE p.fahrt_id = f.id) "
                + "FROM tal_fahrt f ORDER BY f.nr";
        try (Connection c = open(); Statement st = c.createStatement(); ResultSet r = st.executeQuery(sql)) {
            while (r.next()) out.add(new Fahrt(r.getInt(1), r.getString(2), r.getDouble(3), r.getString(4), r.getInt(5), r.getInt(6)));
        }
        return out;
    }

    private static Integer nInt(ResultSet r, int i) throws SQLException { int v = r.getInt(i); return r.wasNull() ? null : v; }

    private static Double nDouble(ResultSet r, int i) throws SQLException { double v = r.getDouble(i); return r.wasNull() ? null : v; }
}
