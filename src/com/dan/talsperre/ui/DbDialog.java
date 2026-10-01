package com.dan.talsperre.ui;

import com.dan.talsperre.db.TalDb;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fenster zur Datenbank: Steckbrief der Bauwerke mit Spatial-Auswertung (Entfernung zur Mauer, Fläche, Bogenlänge),
 * Pegelreihe mit Übernahme eines Messtags in die Szene und die Liste der Kamerafahrten.
 */
final class DbDialog extends JDialog {
    private static final Color BG = ControlPanel.BG, CARD = ControlPanel.CARD, INK = ControlPanel.INK, MUTED = ControlPanel.MUTED, ACCENT = ControlPanel.ACCENT;

    private final TalDb db;
    private final ViewPanel view;
    private final JLabel info = new JLabel(" ");
    private final DefaultTableModel bauModel = new DefaultTableModel(new String[]{"Name", "Art", "Baujahr", "Höhe m", "Länge m", "Stauinhalt Mio. m³",
            "Abstand zur Mauer km", "Fläche km²", "Bogen m", "Geometrie"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final DefaultTableModel fahrtModel = new DefaultTableModel(new String[]{"Nr", "Fahrt", "Dauer s", "Einstellungen", "Posen"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable bauTable = new JTable(bauModel), fahrtTable = new JTable(fahrtModel);
    private final JTextArea bauText = new JTextArea(3, 40);
    private final JComboBox<String> stationBox = new JComboBox<>();
    private final Chart chart = new Chart();
    private final JSlider day = new JSlider(0, 364, 0);
    private final JLabel dayLabel = new JLabel(" ");
    private final List<TalDb.Bauwerk> bauwerke = new ArrayList<>();
    private List<TalDb.Station> stations = new ArrayList<>();
    private List<TalDb.Fahrt> fahrten = new ArrayList<>();

    DbDialog(Window owner, TalDb db, ViewPanel view) {
        super(owner, "Talsperre · Datenbank", ModalityType.MODELESS);
        this.db = db;
        this.view = view;
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());
        info.setForeground(MUTED);
        info.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        add(info, BorderLayout.SOUTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(new Color(196, 214, 220));
        tabs.setForeground(new Color(14, 20, 24));
        tabs.addTab("Steckbrief", steckbrief());
        tabs.addTab("Pegelreihe", pegelTab());
        tabs.addTab("Kamerafahrten", fahrtTab());
        add(tabs, BorderLayout.CENTER);
        setSize(980, 560);
        setLocationRelativeTo(owner);
    }

    // ------------------------------------------------------------ Aufbau

    private static void dark(JTable t) {
        t.setBackground(CARD);
        t.setForeground(INK);
        t.setGridColor(new Color(43, 59, 68));
        t.setSelectionBackground(new Color(34, 84, 98));
        t.setSelectionForeground(Color.WHITE);
        t.setRowHeight(22);
        t.getTableHeader().setBackground(new Color(30, 42, 49));
        t.getTableHeader().setForeground(ACCENT);
        t.setFillsViewportHeight(true);
    }

    private static JScrollPane scroll(java.awt.Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.getViewport().setBackground(CARD);
        sp.setBorder(BorderFactory.createLineBorder(new Color(43, 59, 68)));
        return sp;
    }

    private JPanel steckbrief() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dark(bauTable);
        bauTable.getColumnModel().getColumn(0).setPreferredWidth(230);
        bauTable.getColumnModel().getColumn(5).setPreferredWidth(140);
        bauTable.getColumnModel().getColumn(6).setPreferredWidth(150);
        p.add(scroll(bauTable), BorderLayout.CENTER);
        bauText.setEditable(false);
        bauText.setLineWrap(true);
        bauText.setWrapStyleWord(true);
        bauText.setBackground(CARD);
        bauText.setForeground(INK);
        bauText.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        p.add(bauText, BorderLayout.SOUTH);
        bauTable.getSelectionModel().addListSelectionListener(e -> {
            int r = bauTable.getSelectedRow();
            bauText.setText(r >= 0 && r < bauwerke.size() ? nz(bauwerke.get(r).bemerkung()) : "");
        });
        return p;
    }

    private JPanel pegelTab() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.setBackground(BG);
        JLabel l = new JLabel("Messstelle:");
        l.setForeground(INK);
        top.add(l);
        stationBox.setPrototypeDisplayValue("Beispiel: Seepegel im Seearm bei Asel      ");
        top.add(stationBox);
        stationBox.addActionListener(e -> loadPegel());
        p.add(top, BorderLayout.NORTH);
        p.add(chart, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setBackground(BG);
        day.setBackground(BG);
        day.setForeground(ACCENT);
        day.addChangeListener(e -> { chart.sel = day.getValue(); showDay(); chart.repaint(); });
        dayLabel.setForeground(INK);
        dayLabel.setPreferredSize(new Dimension(300, 24));
        JButton apply = new JButton("Pegel an die Szene übergeben");
        ControlPanel.style(apply);
        apply.setMaximumSize(new Dimension(260, 30));
        apply.addActionListener(e -> {
            if (chart.data.isEmpty()) return;
            double m = chart.data.get(Math.min(chart.data.size() - 1, day.getValue())).meter();
            view.water().level = m;
            info.setText(String.format(Locale.GERMAN, "Pegel %+.2f m an die Szene übergeben.", m));
        });
        bottom.add(day, BorderLayout.CENTER);
        bottom.add(dayLabel, BorderLayout.WEST);
        bottom.add(apply, BorderLayout.EAST);
        p.add(bottom, BorderLayout.SOUTH);
        chart.onPick = i -> day.setValue(i);
        return p;
    }

    private JPanel fahrtTab() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dark(fahrtTable);
        p.add(scroll(fahrtTable), BorderLayout.CENTER);
        JButton play = new JButton("Fahrt abspielen");
        ControlPanel.style(play);
        play.setMaximumSize(new Dimension(200, 30));
        play.addActionListener(e -> {
            int r = fahrtTable.getSelectedRow();
            if (r < 0 || r >= fahrten.size()) { info.setText("Erst eine Fahrt wählen."); return; }
            view.playTour(fahrten.get(r).nr() - 1);
            info.setText("Spiele „" + fahrten.get(r).name() + "“.");
        });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.setBackground(BG);
        south.add(play);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    // ------------------------------------------------------------ Laden

    void reload() {
        info.setText("Lade aus " + db.describe() + " …");
        DbSection.async(() -> new Object[]{db.bauwerke(), db.stations(), db.fahrten()}, r -> {
            bauwerke.clear();
            bauModel.setRowCount(0);
            for (TalDb.Bauwerk b : cast(r[0], TalDb.Bauwerk.class)) {
                bauwerke.add(b);
                bauModel.addRow(new Object[]{b.name(), b.art(), b.baujahr(), num(b.hoeheM(), 0), num(b.laengeM(), 0), num(b.stauinhalt(), 1),
                        num(b.distKm(), 2), num(b.flaecheKm2(), 2), num(b.bogenM(), 0), geomName(b.gtyp()) + ("TRUE".equals(b.gueltig()) ? "" : " (" + b.gueltig() + ")")});
            }
            stations = cast(r[1], TalDb.Station.class);
            stationBox.removeAllItems();
            for (TalDb.Station s : stations) stationBox.addItem(s.name());
            fahrten = cast(r[2], TalDb.Fahrt.class);
            fahrtModel.setRowCount(0);
            for (TalDb.Fahrt f : fahrten) fahrtModel.addRow(new Object[]{f.nr(), f.name(), num(f.dauerS(), 0), f.schritte(), f.posen()});
            info.setText(bauwerke.size() + " Bauwerke, " + stations.size() + " Messstellen, " + fahrten.size() + " Fahrten aus " + db.describe() + ".");
        }, m -> info.setText("Keine Datenbank: " + m));
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> cast(Object o, Class<T> type) { return (List<T>) o; }

    private void loadPegel() {
        int i = stationBox.getSelectedIndex();
        if (i < 0 || i >= stations.size()) return;
        long id = stations.get(i).id();
        DbSection.async(() -> db.pegel(id), l -> {
            chart.data = l;
            day.setMaximum(Math.max(0, l.size() - 1));
            day.setValue(0);
            chart.sel = 0;
            showDay();
            chart.repaint();
        }, m -> info.setText("Pegelreihe: " + m));
    }

    private void showDay() {
        if (chart.data.isEmpty()) { dayLabel.setText(" "); return; }
        TalDb.Pegel p = chart.data.get(Math.min(chart.data.size() - 1, day.getValue()));
        dayLabel.setText(p.tag().format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)) + String.format(Locale.GERMAN, " · Pegel %+.2f m", p.meter()));
    }

    private static String num(Double v, int digits) { return v == null ? "" : String.format(Locale.GERMAN, "%." + digits + "f", v); }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String geomName(int gtyp) {
        switch (gtyp) {
            case 2001: return "Punkt";
            case 2002: return "Linie";
            case 2003: return "Fläche";
            default: return "Typ " + gtyp;
        }
    }

    /** Linienbild der Pegelreihe; Klick oder Ziehen wählt den Messtag. */
    private static final class Chart extends JPanel {
        List<TalDb.Pegel> data = new ArrayList<>();
        int sel;
        java.util.function.IntConsumer onPick = i -> { };

        Chart() {
            setBackground(CARD);
            setBorder(BorderFactory.createLineBorder(new Color(43, 59, 68)));
            java.awt.event.MouseAdapter m = new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) { pick(e.getX()); }
                @Override public void mouseDragged(java.awt.event.MouseEvent e) { pick(e.getX()); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private void pick(int x) {
            if (data.size() < 2) return;
            int l = 56, r = getWidth() - 16;
            int i = (int) Math.round((x - l) / (double) Math.max(1, r - l) * (data.size() - 1));
            onPick.accept(Math.max(0, Math.min(data.size() - 1, i)));
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            if (data.size() < 2) { g.setColor(MUTED); g.drawString("Keine Pegelreihe geladen.", 20, 24); g.dispose(); return; }
            double lo = 0, hi = 0;
            for (TalDb.Pegel p : data) { lo = Math.min(lo, p.meter()); hi = Math.max(hi, p.meter()); }
            lo = Math.floor(lo / 5) * 5 - 2; hi = Math.ceil(hi / 5) * 5 + 1;
            int l = 56, r = getWidth() - 16, t = 16, b = getHeight() - 28;
            for (double v = Math.ceil(lo / 5) * 5; v <= hi; v += 5) {
                int y = (int) (b - (v - lo) / (hi - lo) * (b - t));
                g.setColor(v == 0 ? new Color(63, 176, 198, 140) : new Color(43, 59, 68));
                g.drawLine(l, y, r, y);
                g.setColor(MUTED);
                g.drawString(String.format(Locale.GERMAN, "%+.0f m", v) + (v == 0 ? " Stauziel" : ""), 4, y + 4);
            }
            g.setColor(ACCENT);
            g.setStroke(new BasicStroke(1.8f));
            int px = 0, py = 0;
            for (int i = 0; i < data.size(); i++) {
                int x = l + (int) ((r - l) * i / (double) (data.size() - 1));
                int y = (int) (b - (data.get(i).meter() - lo) / (hi - lo) * (b - t));
                if (i > 0) g.drawLine(px, py, x, y);
                px = x; py = y;
            }
            int s = Math.max(0, Math.min(data.size() - 1, sel));
            int sx = l + (int) ((r - l) * s / (double) (data.size() - 1));
            int sy = (int) (b - (data.get(s).meter() - lo) / (hi - lo) * (b - t));
            g.setColor(new Color(255, 200, 80));
            g.drawLine(sx, t, sx, b);
            g.fillOval(sx - 4, sy - 4, 8, 8);
            g.setColor(MUTED);
            String[] mon = {"Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"};
            for (int i = 0; i < data.size(); i++) {
                if (data.get(i).tag().getDayOfMonth() == 1) {
                    int x = l + (int) ((r - l) * i / (double) (data.size() - 1));
                    g.drawString(mon[data.get(i).tag().getMonthValue() - 1], x - 8, b + 16);
                }
            }
            g.dispose();
        }
    }
}
