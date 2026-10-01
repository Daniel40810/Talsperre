package com.dan.talsperre.ui;

import com.dan.talsperre.db.TalDb;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Abschnitt "Datenbank" im Bedienfeld: Verbindung auf Knopfdruck, Voreinstellungen speichern und laden, Blickpunkte merken
 * und das Fenster mit Steckbrief, Pegelreihe und Fahrten. Beim Start passiert nichts: Ohne Datenbank bleibt die App
 * wie sie ist, die Knöpfe melden dann den Fehler in dieser Zeile.
 */
final class DbSection extends JPanel {
    private final ControlPanel panel;
    private final ViewPanel view;
    private final TalDb db = new TalDb();
    private final JLabel state = ControlPanel.hint("Nicht verbunden. Die App läuft ohne Datenbank; verbunden wird nur auf Knopfdruck.");
    private final JComboBox<String> presetBox = new JComboBox<>(), spotBox = new JComboBox<>();
    private final List<TalDb.Spot> spots = new ArrayList<>();
    private DbDialog dialog;

    DbSection(ControlPanel panel, ViewPanel view) {
        this.panel = panel;
        this.view = view;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        add(state);
        add(Box.createVerticalStrut(6));

        JButton connect = button("Verbinden und prüfen");
        connect.addActionListener(e -> connect());
        add(connect);

        add(Box.createVerticalStrut(8));
        add(ControlPanel.label("Voreinstellung (Name eintippen)"));
        presetBox.setEditable(true);
        tame(presetBox);
        add(presetBox);
        add(row(button("Laden", e -> loadPreset()), button("Speichern", e -> savePreset()), button("Löschen", e -> deletePreset())));

        add(Box.createVerticalStrut(8));
        add(ControlPanel.label("Eigene Blickpunkte"));
        spotBox.setEditable(true);
        tame(spotBox);
        add(spotBox);
        add(row(button("Merken", e -> saveSpot()), button("Hinfahren", e -> goSpot()), button("Löschen", e -> deleteSpot())));

        add(Box.createVerticalStrut(8));
        JButton info = button("Steckbrief, Pegel, Fahrten …");
        info.addActionListener(e -> openDialog());
        add(info);
    }

    // ------------------------------------------------------------ Hilfen

    private JButton button(String text) {
        JButton b = new JButton(text);
        ControlPanel.style(b);
        return b;
    }

    private JButton button(String text, java.awt.event.ActionListener l) {
        JButton b = button(text);
        b.setHorizontalAlignment(JButton.CENTER);
        b.setBorder(BorderFactory.createLineBorder(new java.awt.Color(43, 59, 68)));
        b.addActionListener(l);
        return b;
    }

    private JPanel row(JButton... bs) {
        JPanel p = new JPanel(new GridLayout(1, bs.length, 4, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        for (JButton b : bs) p.add(b);
        return p;
    }

    private static void tame(JComboBox<String> c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        c.setBackground(ControlPanel.CARD);
        c.setForeground(ControlPanel.INK);
        Component ed = c.getEditor().getEditorComponent();
        ed.setBackground(ControlPanel.CARD);
        ed.setForeground(ControlPanel.INK);
        if (ed instanceof javax.swing.text.JTextComponent) ((javax.swing.text.JTextComponent) ed).setCaretColor(ControlPanel.INK);
    }

    private void say(String s) {
        if (s.contains("No suitable driver")) s = "Kein JDBC-Treiber gefunden (ojdbc11.jar muss im Klassenpfad liegen, in NetBeans: lib).";
        // Zero-width-Leerzeichen nach Trennzeichen, damit lange Adressen umbrechen
        String safe = s.replace("&", "&amp;").replace("<", "&lt;").replaceAll("([/:@.,;])", "$1\u200B");
        state.setText("<html><body style='width:170px'>" + safe + "</body></html>");
        state.revalidate();
        view.requestFocusInWindow();
    }

    private static String firstLine(Throwable e) {
        Throwable t = e.getCause() != null && e instanceof java.util.concurrent.ExecutionException ? e.getCause() : e;
        String m = t.getMessage();
        if (m == null) m = t.getClass().getSimpleName();
        int nl = m.indexOf('\n');
        return nl > 0 ? m.substring(0, nl) : m;
    }

    /** Führt eine Datenbankaktion im Hintergrund aus und meldet das Ergebnis im Swing-Thread. */
    static <T> void async(Callable<T> job, Consumer<T> ok, Consumer<String> fail) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return job.call(); }
            @Override protected void done() {
                try {
                    ok.accept(get());
                } catch (Exception e) {
                    fail.accept(firstLine(e));
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------ Aktionen

    private record Snapshot(String state, List<TalDb.Preset> presets, List<TalDb.Spot> spots) { }

    private void connect() {
        say("Verbinde mit " + db.describe() + " …");
        async(() -> new Snapshot(db.check(), db.presets(), db.spots()),
                r -> {
                    say(r.state());
                    fill(presetBox, r.presets().stream().map(TalDb.Preset::name).toList());
                    spots.clear();
                    spots.addAll(r.spots());
                    fill(spotBox, spots.stream().map(TalDb.Spot::name).toList());
                },
                m -> say("Keine Datenbank: " + m));
    }

    private static void fill(JComboBox<String> box, List<String> names) {
        Object keep = box.getEditor().getItem();
        box.removeAllItems();
        for (String n : names) box.addItem(n);
        if (keep != null && !keep.toString().isEmpty()) box.setSelectedItem(keep);
    }

    private String name(JComboBox<String> box) {
        Object o = box.getEditor().getItem();
        return o == null ? "" : o.toString().trim();
    }

    private void loadPreset() {
        String n = name(presetBox);
        if (n.isEmpty()) { say("Bitte einen Namen wählen."); return; }
        async(() -> db.loadPreset(n),
                m -> { if (m.isEmpty()) say("Keine Voreinstellung „" + n + "“ gefunden."); else { panel.apply(m); say("Voreinstellung „" + n + "“ geladen (" + m.size() + " Werte)."); } },
                m -> say("Laden fehlgeschlagen: " + m));
    }

    private void savePreset() {
        String n = name(presetBox);
        if (n.isEmpty()) { say("Bitte einen Namen eintippen."); return; }
        final java.util.Map<String, String> values = panel.capture();
        async(() -> { db.savePreset(n, "Vom Bedienfeld gespeichert", values); return db.presets(); },
                l -> { fill(presetBox, l.stream().map(TalDb.Preset::name).toList()); say("Voreinstellung „" + n + "“ gespeichert (" + values.size() + " Werte)."); },
                m -> say("Speichern fehlgeschlagen: " + m));
    }

    private void deletePreset() {
        String n = name(presetBox);
        if (n.isEmpty()) return;
        async(() -> { db.deletePreset(n); return db.presets(); },
                l -> { presetBox.getEditor().setItem(""); fill(presetBox, l.stream().map(TalDb.Preset::name).toList()); say("Voreinstellung „" + n + "“ gelöscht."); },
                m -> say("Löschen fehlgeschlagen: " + m));
    }

    private void saveSpot() {
        String n = name(spotBox);
        if (n.isEmpty()) n = "Blickpunkt " + (spots.size() + 1);
        final String nm = n;
        final double[] pose = view.cameraPose();
        async(() -> { db.saveSpot(nm, "Vom Bedienfeld gemerkt", pose); return db.spots(); },
                l -> { spots.clear(); spots.addAll(l); fill(spotBox, l.stream().map(TalDb.Spot::name).toList()); say("Blickpunkt „" + nm + "“ gemerkt."); },
                m -> say("Merken fehlgeschlagen: " + m));
    }

    private void goSpot() {
        String n = name(spotBox);
        for (TalDb.Spot s : spots) if (s.name().equals(n)) { view.goToPose(s.name(), s.pose()); say("Fahre zu „" + n + "“."); return; }
        say("Blickpunkt „" + n + "“ nicht in der Liste (erst verbinden).");
    }

    private void deleteSpot() {
        String n = name(spotBox);
        if (n.isEmpty()) return;
        async(() -> { db.deleteSpot(n); return db.spots(); },
                l -> { spots.clear(); spots.addAll(l); spotBox.getEditor().setItem(""); fill(spotBox, l.stream().map(TalDb.Spot::name).toList()); say("Blickpunkt „" + n + "“ gelöscht."); },
                m -> say("Löschen fehlgeschlagen: " + m));
    }

    private void openDialog() {
        if (dialog == null) dialog = new DbDialog(javax.swing.SwingUtilities.getWindowAncestor(this), db, view);
        dialog.reload();
        dialog.setVisible(true);
        dialog.toFront();
    }
}
