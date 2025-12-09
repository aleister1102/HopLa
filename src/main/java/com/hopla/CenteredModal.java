package com.hopla;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import burp.api.montoya.ui.Theme;

public final class CenteredModal {

    private CenteredModal() {
    }

    public static JDialog showDialog(JComponent content, String title) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(new EmptyBorder(16, 20, 16, 20));

        Theme theme = HopLa.montoyaApi.userInterface().currentTheme();
        boolean dark = theme == Theme.DARK;
        Color bg = dark ? new Color(40, 40, 40) : Color.WHITE;
        Color fg = dark ? new Color(245, 245, 245) : new Color(26, 26, 26);

        wrapper.setBackground(bg);
        content.setBackground(bg);
        content.setForeground(fg);
        wrapper.getAccessibleContext().setAccessibleName(title);
        content.getAccessibleContext().setAccessibleName(title + " content");

        wrapper.add(content, BorderLayout.CENTER);

        Dimension pref = content.getPreferredSize();
        int padW = 40;
        int padH = 40;
        Dimension size = new Dimension(pref.width + padW, pref.height + padH);

        java.awt.Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int maxW = (int) (screen.width * 0.8);
        int maxH = (int) (screen.height * 0.8);
        size.width = Math.min(size.width, maxW);
        size.height = Math.min(size.height, maxH);

        JDialog dlg = new JDialog();
        dlg.setModal(true);
        dlg.setTitle(title);
        dlg.setUndecorated(false);
        dlg.setAlwaysOnTop(true);
        dlg.setContentPane(wrapper);
        dlg.setMinimumSize(size);
        dlg.setPreferredSize(size);
        dlg.pack();

        java.awt.Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        Point center = new Point(scr.width / 2 - dlg.getWidth() / 2, scr.height / 2 - dlg.getHeight() / 2);
        dlg.setLocation(center);

        dlg.getRootPane().registerKeyboardAction(e -> closeWithAnimation(dlg), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();

        dlg.setOpacity(0f);
        Timer t = new Timer(10, null);
        t.addActionListener((ActionEvent e) -> {
            float o = dlg.getOpacity();
            float next = Math.min(1f, o + 0.08f);
            dlg.setOpacity(next);
            if (next >= 1f) {
                t.stop();
            }
        });
        SwingUtilities.invokeLater(() -> {
            dlg.setVisible(true);
            t.start();
            content.requestFocusInWindow();
        });

        return dlg;
    }

    public static void closeWithAnimation(JDialog dlg) {
        Timer t = new Timer(10, null);
        t.addActionListener((ActionEvent e) -> {
            float o = dlg.getOpacity();
            float next = Math.max(0f, o - 0.08f);
            dlg.setOpacity(next);
            if (next <= 0f) {
                t.stop();
                dlg.dispose();
            }
        });
        t.start();
    }
}
