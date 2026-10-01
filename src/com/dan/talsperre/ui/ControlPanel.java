package com.dan.talsperre.ui;

import com.dan.talsperre.camera.Viewpoint;
import com.dan.talsperre.effects.DayNightCycle;
import com.dan.talsperre.water.WaterWorks;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/** Bedienfeld rechts: Blickpunkte, Tageszeit, Dunst und Schalter. Wächst mit den Phasen. */
public final class ControlPanel extends JPanel implements javax.swing.Scrollable {
    static final Color BG = new Color(14, 20, 24), CARD = new Color(22, 31, 37), INK = new Color(222, 232, 232),
            MUTED = new Color(150, 165, 168), ACCENT = new Color(63, 176, 198);

    private final ViewPanel view;
    private final JLabel timeLabel = label("");

    public ControlPanel(ViewPanel view) {
        this.view = view;
        setBackground(BG);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(title("Blickpunkte"));
        for (int i = 0; i < Viewpoint.NAMES.length; i++) {
            final int k = i;
            JButton b = new JButton(Viewpoint.NAMES[i]);
            style(b);
            b.addActionListener(e -> { view.goTo(k); view.requestFocusInWindow(); });
            add(b);
            add(Box.createVerticalStrut(4));
        }
        JCheckBox orbit = check("Kreisen (Orbit)", false);
        orbit.addActionListener(e -> view.setAutoOrbit(orbit.isSelected()));
        add(orbit);

        add(Box.createVerticalStrut(14));
        add(title("Regie"));
        for (int i = 0; i < com.dan.talsperre.camera.Tours.NAMES.length; i++) {
            final int k = i;
            JButton b = new JButton(com.dan.talsperre.camera.Tours.NAMES[i]);
            style(b);
            b.addActionListener(e -> { view.playTour(k); view.requestFocusInWindow(); });
            add(b);
            add(Box.createVerticalStrut(4));
        }
        JButton stop = new JButton("Stopp (Esc)");
        style(stop);
        stop.addActionListener(e -> view.interrupt());
        add(stop);
        JCheckBox cine = check("Kinomodus (Breitbild)", false);
        cine.addActionListener(e -> view.setCinema(cine.isSelected()));
        add(cine);

        add(Box.createVerticalStrut(14));
        add(title("Tageszeit"));
        add(timeLabel);
        JSlider hour = reg("hour", slider(0, 240, (int) Math.round(view.hour() * 10)), 10);
        hour.addChangeListener(e -> { view.setTime(view.day(), hour.getValue() / 10.0); showTime(); });
        add(hour);
        JSlider day = reg("day", slider(1, 365, view.day()), 1);
        day.addChangeListener(e -> { view.setTime(day.getValue(), view.hour()); showTime(); });
        add(label("Tag im Jahr"));
        add(day);
        showTime();
        // Tageszeiten auf Knopfdruck
        JPanel presets = new JPanel(new java.awt.GridLayout(1, 4, 4, 0));
        presets.setBackground(BG);
        presets.setAlignmentX(Component.LEFT_ALIGNMENT);
        presets.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        final String[] pn = {"Morgen", "Mittag", "Abend", "Nacht"};
        final double[] ph = {8.3, 12.5, 18.4, 22.5};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            JButton b = new JButton(pn[i]);
            style(b);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            b.setBorder(BorderFactory.createLineBorder(new Color(43, 59, 68)));
            b.setHorizontalAlignment(JButton.CENTER);
            b.addActionListener(e -> { view.setTime(view.day(), ph[k]); hour.setValue((int) Math.round(ph[k] * 10)); showTime(); });
            presets.add(b);
        }
        add(Box.createVerticalStrut(4));
        add(presets);
        JButton bow = new JButton("Regenbogen-Szene (Sommer)");
        style(bow);
        add(Box.createVerticalStrut(4));
        add(bow);
        final JCheckBox lapse = check("Zeitraffer", false);
        final JSlider lapseSpeed = slider(1, 100, 25);
        lapse.addActionListener(e -> view.setTimeLapse(lapse.isSelected() ? lapseSpeed.getValue() / 100.0 : 0));
        lapseSpeed.addChangeListener(e -> { if (lapse.isSelected()) view.setTimeLapse(lapseSpeed.getValue() / 100.0); });
        add(lapse);
        add(lapseSpeed);
        javax.swing.Timer clock = new javax.swing.Timer(200, e -> {
            if (view.timeLapse() != 0 || view.directing()) {
                int v = (int) Math.round(view.hour() * 10);
                if (v != hour.getValue()) hour.setValue(Math.min(240, v));
                if (day.getValue() != view.day()) day.setValue(view.day());
                showTime();
            }
        });
        clock.start();

        add(Box.createVerticalStrut(14));
        add(title("Wasser"));
        final WaterWorks ww = view.water();
        final JLabel levelLabel = label("");
        add(levelLabel);
        JSlider level = reg("level", slider((int) (WaterWorks.LEVEL_MIN * 10), (int) (WaterWorks.LEVEL_MAX * 10), 0), 10);
        final boolean[] syncing = {false};
        level.addChangeListener(e -> { if (!syncing[0]) ww.level = level.getValue() / 10.0; });
        add(level);
        add(label("Wind über dem See"));
        JSlider wind = reg("wind", slider(0, 100, 35), 100);
        wind.addChangeListener(e -> ww.wind = wind.getValue() / 100.0);
        add(wind);
        final JLabel gateLabel = label("");
        add(gateLabel);
        final JSlider open = reg("open", slider(0, 100, 55), 100);
        final JCheckBox[] gates = new JCheckBox[5];
        final Runnable applyGates = () -> {
            for (int i = 0; i < 5; i++) ww.gate[i] = gates[i].isSelected() ? open.getValue() / 100.0 : 0;
        };
        open.addChangeListener(e -> applyGates.run());
        add(open);
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        row.setBackground(BG);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < 5; i++) {
            gates[i] = reg("gate" + (i + 1), check("" + (i + 1), true));
            gates[i].addActionListener(e -> applyGates.run());
            row.add(gates[i]);
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        add(row);
        applyGates.run();
        bow.addActionListener(e -> {
            view.setTime(190, 13.25);
            hour.setValue(132); day.setValue(190); showTime();
            open.setValue(100);
            for (JCheckBox c : gates) c.setSelected(true);
            applyGates.run();
            view.goTo(java.util.Arrays.asList(Viewpoint.NAMES).indexOf("Regenbogen"));
            view.requestFocusInWindow();
        });
        final JLabel spillLabel = label("");
        add(spillLabel);
        javax.swing.Timer tick = new javax.swing.Timer(300, e -> {
            double lv = ww.level;
            levelLabel.setText(String.format(java.util.Locale.GERMAN, "Pegel %+.1f m · %.2f m ü. NHN", lv, WaterWorks.nhn(lv)));
            gateLabel.setText(String.format(java.util.Locale.GERMAN, "Ablässe: %.0f m³/s · Strahl %.0f m/s", ww.outflow, ww.jetSpeed));
            spillLabel.setText(ww.spillFlow > 0.05 ? String.format("Überlauf: %.0f m³/s", ww.spillFlow) : "Überlauf: aus (Schwelle +0,3 m)");
        });
        tick.start();

        add(Box.createVerticalStrut(14));
        add(title("Dampf und Nebel"));
        add(label("Seenebel"));
        JSlider lakeMist = reg("lakeMist", slider(0, 200, 100), 100);
        lakeMist.addChangeListener(e -> ww.lakeMist = lakeMist.getValue() / 100.0);
        add(lakeMist);
        add(label("Gischtnebel am Tosbecken"));
        JSlider sprayMist = reg("sprayMist", slider(0, 200, 100), 100);
        sprayMist.addChangeListener(e -> ww.sprayMist = sprayMist.getValue() / 100.0);
        add(sprayMist);
        JCheckBox wisps = reg("wisps", check("Nebelschwaden (See, Eder)", true));
        wisps.addActionListener(e -> ww.wisps = wisps.isSelected());
        add(wisps);
        add(label("Kraftwerksdampf (Maschinenhalle)"));
        JSlider plant = reg("plant", slider(0, 200, 100), 100);
        plant.addChangeListener(e -> ww.plant = plant.getValue() / 100.0);
        add(plant);

        add(Box.createVerticalStrut(14));
        add(title("Leben"));
        String[][] life = {{"Verkehr auf Krone und Straße", "traffic"}, {"Schiffe und Segelboote", "ships"}, {"Vögel", "birds"}, {"Wanderer und Kraftwerk", "walkers"}};
        for (String[] l : life) {
            final String key = l[1];
            JCheckBox cb = reg(key, check(l[0], true));
            cb.addActionListener(e -> view.setLife(key, cb.isSelected()));
            add(cb);
        }

        add(Box.createVerticalStrut(14));
        add(title("Luft"));
        add(label("Dunst"));
        JSlider haze = reg("haze", slider(0, 100, 12), 100);
        haze.addChangeListener(e -> view.setHaze(haze.getValue() / 100.0));
        add(haze);
        add(label("Nebel am Boden"));
        JSlider fog = reg("fog", slider(0, 200, 50), 100);
        fog.addChangeListener(e -> view.setFog(fog.getValue() / 100.0));
        add(fog);
        JCheckBox rays = reg("rays", check("Lichtstrahlen und Dunst", true));
        rays.addActionListener(e -> view.setRays(rays.isSelected()));
        add(rays);
        add(label("Stärke der Lichtstrahlen"));
        JSlider rayGain = reg("rayGain", slider(0, 400, 150), 100);
        rayGain.addChangeListener(e -> view.setRayGain(rayGain.getValue() / 100.0));
        add(rayGain);
        JCheckBox rainbow = reg("rainbow", check("Regenbogen in der Gischt", true));
        rainbow.addActionListener(e -> view.setRainbow(rainbow.isSelected()));
        add(rainbow);
        JCheckBox lampsOn = reg("lamps", check("Lampen und Fensterlicht (nachts)", true));
        lampsOn.addActionListener(e -> view.setLamps(lampsOn.isSelected()));
        add(lampsOn);
        JCheckBox bloom = reg("bloom", check("Überstrahlen", true));
        bloom.addActionListener(e -> view.setBloom(bloom.isSelected()));
        add(bloom);
        JCheckBox auto = check("Qualität automatisch", true);
        auto.addActionListener(e -> view.setAutoQuality(auto.isSelected()));
        add(auto);

        add(Box.createVerticalStrut(14));
        add(title("Zugaben"));
        JCheckBox cut = check("Mauerschnitt (Keil herausnehmen)", false);
        cut.addActionListener(e -> view.extras().setSection(cut.isSelected()));
        add(cut);
        add(Box.createVerticalStrut(4));
        String[] zb = {"Bauzeitraffer 1908–1914", "Nacht des 17. Mai 1943", "Niedrigwasser: Alte Brücke", "Niedrigwasser: Berich", "Normalbetrieb"};
        for (int i = 0; i < zb.length; i++) {
            final int k = i;
            JButton b = new JButton(zb[i]);
            style(b);
            b.addActionListener(e -> {
                switch (k) {
                    case 0: view.playTour(6); break;
                    case 1: view.playTour(7); break;
                    case 2: view.goTo(java.util.Arrays.asList(Viewpoint.NAMES).indexOf("Alte Brücke")); break;
                    case 3: view.goTo(java.util.Arrays.asList(Viewpoint.NAMES).indexOf("Berich")); break;
                    default: view.interrupt(); view.extras().reset(); cut.setSelected(false); break;
                }
                view.requestFocusInWindow();
            });
            add(b);
            add(Box.createVerticalStrut(4));
        }
        final JLabel lupeLabel = label("Mineral-Lupe: " + com.dan.talsperre.extras.Lupe.NAMES[0]);
        JCheckBox lupeOn = check("Mineral-Lupe (Grauwacke bis Atom)", false);
        lupeOn.addActionListener(e -> view.lupe().setVisible(lupeOn.isSelected()));
        add(lupeOn);
        final JSlider lupeStage = slider(0, 3, 0);
        lupeStage.addChangeListener(e -> view.lupe().setStage(lupeStage.getValue()));
        add(lupeLabel);
        add(lupeStage);
        JCheckBox sndOn = reg("sound", check("Ton", true));
        sndOn.addActionListener(e -> view.sound().enabled = sndOn.isSelected());
        add(sndOn);
        JSlider vol = reg("volume", slider(0, 100, 60), 100);
        vol.addChangeListener(e -> view.sound().volume = vol.getValue() / 100.0);
        add(vol);
        JButton horn = new JButton("Schiffshorn");
        style(horn);
        horn.addActionListener(e -> view.sound().horn());
        add(horn);
        javax.swing.Timer zt = new javax.swing.Timer(300, e -> {
            int want = (int) Math.round(ww.level * 10);
            if (!level.getValueIsAdjusting() && level.getValue() != want) { syncing[0] = true; level.setValue(want); syncing[0] = false; }
            int st = view.lupe().stage();
            if (lupeStage.getValue() != st && !lupeStage.getValueIsAdjusting()) lupeStage.setValue(st);
            lupeLabel.setText("Mineral-Lupe: " + com.dan.talsperre.extras.Lupe.NAMES[st]);
            boolean sec = view.extras().scene() == com.dan.talsperre.extras.Extras.SECTION;
            if (cut.isSelected() != sec && !view.extras().running()) cut.setSelected(sec);
        });
        zt.start();

        add(Box.createVerticalStrut(14));
        add(title("Datenbank"));
        add(new DbSection(this, view));

        add(Box.createVerticalStrut(14));
        add(title("Bedienung"));
        add(hint("Maus links: drehen · rechts oder Umschalt: verschieben · Rad: zoomen · Doppelklick: Drehpunkt setzen · W A S D Q E: Drehpunkt bewegen."));
        add(Box.createVerticalGlue());
    }

    // ------------------------------------------------------------ Zustand für die Datenbank

    /** Regler und Schalter, die in Voreinstellungen landen: Schlüssel → {Regler oder Schalter, Skalierung}. */
    /** Alle Schlüssel, die in Voreinstellungen vorkommen dürfen (zum Prüfen der Skripte). */
    public static final String[] KEYS = {"hour", "day", "level", "wind", "open", "gate1", "gate2", "gate3", "gate4", "gate5", "lakeMist", "sprayMist",
            "wisps", "plant", "traffic", "ships", "birds", "walkers", "haze", "fog", "rays", "rayGain", "rainbow", "lamps", "bloom", "sound", "volume"};

    private final java.util.LinkedHashMap<String, Object[]> binds = new java.util.LinkedHashMap<>();

    private JSlider reg(String key, JSlider s, double scale) { binds.put(key, new Object[]{s, scale}); return s; }

    private JCheckBox reg(String key, JCheckBox c) { binds.put(key, new Object[]{c, 1.0}); return c; }

    /** Der Zustand des Bedienfelds als Schlüssel-Wert-Paare (Zahlen mit Punkt, Schalter als 1 oder 0). */
    public java.util.Map<String, String> capture() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, Object[]> e : binds.entrySet()) {
            Object c = e.getValue()[0];
            if (c instanceof JSlider) {
                double v = ((JSlider) c).getValue() / (Double) e.getValue()[1];
                m.put(e.getKey(), String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("\\.?0+$", ""));
            } else m.put(e.getKey(), ((JCheckBox) c).isSelected() ? "1" : "0");
        }
        return m;
    }

    /** Setzt vorhandene Schlüssel; unbekannte oder unlesbare Werte bleiben unbeachtet. Nur im Swing-Thread aufrufen. */
    public void apply(java.util.Map<String, String> m) {
        for (java.util.Map.Entry<String, String> e : m.entrySet()) {
            Object[] b = binds.get(e.getKey());
            if (b == null) continue;
            try {
                if (b[0] instanceof JSlider) {
                    ((JSlider) b[0]).setValue((int) Math.round(Double.parseDouble(e.getValue()) * (Double) b[1]));
                } else {
                    boolean on = "1".equals(e.getValue());
                    JCheckBox c = (JCheckBox) b[0];
                    if (c.isSelected() != on) c.doClick(0);
                }
            } catch (NumberFormatException ex) {
                // Wert unlesbar: Regler bleibt
            }
        }
        showTime();
    }

    private void showTime() {
        timeLabel.setText(DayNightCycle.dateLabel(view.day()) + " · " + DayNightCycle.timeLabel(view.hour()) + " " + DayNightCycle.zone(view.day(), view.hour()));
    }

    private static JLabel title(String s) {
        JLabel l = new JLabel(s.toUpperCase());
        l.setForeground(ACCENT);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    static JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(INK);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    static JLabel hint(String s) {
        JLabel l = new JLabel("<html><body style='width:210px'>" + s + "</body></html>");
        l.setForeground(MUTED);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JSlider slider(int lo, int hi, int v) {
        JSlider s = new JSlider(lo, hi, v);
        s.setBackground(BG);
        s.setForeground(ACCENT);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        s.setFocusable(false);
        return s;
    }

    static JCheckBox check(String s, boolean on) {
        JCheckBox c = new JCheckBox(s, on);
        c.setBackground(BG);
        c.setForeground(INK);
        c.setFocusable(false);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    static void style(JButton b) {
        b.setBackground(CARD);
        b.setForeground(INK);
        b.setFocusPainted(false);
        b.setFocusable(false);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(43, 59, 68)), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        b.setHorizontalAlignment(JButton.LEFT);
    }

    // Die Breite folgt dem Rahmen, damit nichts nach rechts hinausläuft
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return 120; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
