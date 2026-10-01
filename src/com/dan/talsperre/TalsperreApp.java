package com.dan.talsperre;

import com.dan.fframe.FFrame;
import com.dan.talsperre.ui.ControlPanel;
import com.dan.talsperre.ui.ViewPanel;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

/**
 * Talsperre — die Edertalsperre als animierte 3D-Szene.
 * Einstieg der Anwendung: FFrame, Szene in der Mitte, Bedienfeld rechts, Statuszeile unten.
 * Läuft ohne Datenbank (die kommt in Phase 10).
 */
public final class TalsperreApp {
    static final Color BG = new Color(14, 20, 24), STATUS_BG = new Color(8, 12, 15), STATUS_INK = new Color(160, 170, 170);

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        SwingUtilities.invokeLater(TalsperreApp::open);
    }

    private static void open() {
        FFrame f = new FFrame("Talsperre · Edertalsperre, Edersee");
        ViewPanel view = new ViewPanel();
        ControlPanel controls = new ControlPanel(view);
        JLabel status = new JLabel(" ");
        status.setForeground(STATUS_INK);
        status.setFont(new Font("SansSerif", Font.PLAIN, 12));
        status.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        view.setStatusListener(s -> SwingUtilities.invokeLater(() -> status.setText(s)));

        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(view, BorderLayout.CENTER);
        javax.swing.JScrollPane side = new javax.swing.JScrollPane(controls,
                javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        side.setBorder(BorderFactory.createEmptyBorder());
        side.getViewport().setBackground(BG);
        com.dan.fscrollbar.FScrollBar bar = new com.dan.fscrollbar.FScrollBar(javax.swing.JScrollBar.VERTICAL);
        bar.setUnitIncrement(16);
        bar.setBlockIncrement(120);
        side.setVerticalScrollBar(bar);
        side.setBackground(BG);
        side.setPreferredSize(new Dimension(270, 600));
        root.add(side, BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(STATUS_BG);
        south.add(status, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);

        f.setPreferredFrameSize(new Dimension(1400, 860));
        f.setSize(1400, 860);
        f.setLocationRelativeTo(null);
        f.setResizable(true);
        f.setVisible(true);
        view.start();
        Runtime.getRuntime().addShutdownHook(new Thread(view::stop, "Talsperre-Ende"));
        view.requestFocusInWindow();
    }

    private TalsperreApp() { }
}
