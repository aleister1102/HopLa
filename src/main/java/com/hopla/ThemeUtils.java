package com.hopla;

import java.awt.Color;

import javax.swing.UIManager;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.Theme;

public class ThemeUtils {

    public static boolean isDarkMode(MontoyaApi api) {
        return api.userInterface().currentTheme() == Theme.DARK;
    }

    public static Color getBackgroundColor(MontoyaApi api) {
        Color c = UIManager.getColor("List.background");
        return c != null ? c : (isDarkMode(api) ? new Color(31, 41, 55) : new Color(255, 255, 255));
    }

    public static Color getForegroundColor(MontoyaApi api) {
        Color c = UIManager.getColor("List.foreground");
        return c != null ? c : (isDarkMode(api) ? new Color(229, 231, 235) : new Color(33, 33, 33));
    }

    public static Color getSelectionBackgroundColor(MontoyaApi api) {
        Color c = UIManager.getColor("List.selectionBackground");
        return c != null ? c : (isDarkMode(api) ? new Color(59, 68, 85) : new Color(200, 200, 200));
    }

    public static Color getSelectionForegroundColor(MontoyaApi api) {
        Color c = UIManager.getColor("List.selectionForeground");
        return c != null ? c : (isDarkMode(api) ? new Color(229, 231, 235) : new Color(0, 0, 0));
    }

    public static Color getBorderColor(MontoyaApi api) {
        Color c = UIManager.getColor("Component.borderColor");
        if (c == null) {
            c = UIManager.getColor("TextField.borderColor");
        }
        return c != null ? c : (isDarkMode(api) ? new Color(55, 65, 81) : Color.LIGHT_GRAY);
    }

    public static Color getButtonBackgroundColor(MontoyaApi api) {
        Color c = UIManager.getColor("Button.background");
        return c != null ? c : (isDarkMode(api) ? new Color(59, 89, 152) : new Color(59, 89, 152));
    }

    public static Color getButtonForegroundColor(MontoyaApi api) {
        Color c = UIManager.getColor("Button.foreground");
        return c != null ? c : Color.WHITE;
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    // Chat specific colors
    public static String getCss(MontoyaApi api) {
        Color bg = getBackgroundColor(api);
        Color fg = getForegroundColor(api);
        Color border = getBorderColor(api);

        // We might want slightly different colors for chat bubbles
        Color userBg = isDarkMode(api) ? new Color(43, 53, 69) : new Color(243, 244, 246);
        Color assistBg = isDarkMode(api) ? new Color(31, 41, 55) : new Color(255, 255, 255);

        // Try to derive from UIManager if possible, or stick to safe defaults that match the theme
        if (UIManager.getColor("Panel.background") != null) {
            assistBg = UIManager.getColor("Panel.background");
            // Make user bg slightly different
            userBg = isDarkMode(api) ? assistBg.brighter() : assistBg.darker();
        }

        String bodyColor = toHex(fg);
        String borderColor = toHex(border);
        String userBgColor = toHex(userBg);
        String assistBgColor = toHex(assistBg);

        return String.format("""
            body * { font-family: sans-serif; font-size: 12px; word-wrap: break-word; overflow-wrap: break-word; margin: 0; }
            .chat-container { padding: 12px; color: %s; }
            .user, .assistant { margin-bottom: 12px; padding: 10px 12px; border-radius: 8px; border: 1px solid %s; box-shadow: none; }
            .user .role { color: #93c5fd; font-weight: bold; }
            .assistant .role { color: #a78bfa; font-weight: bold; }
            .user { background: %s; }
            .assistant { background: %s; }
            * { white-space: pre-wrap; word-break: break-word; }
            pre { color: %s; background: %s; border: 1px solid %s; border-radius: 6px; padding: 8px; white-space: pre-wrap; word-break: break-word; }
            code { background: %s; border: 1px solid %s; border-radius: 4px; padding: 2px 4px; color: %s; }
            pre code { background: transparent; border: none; padding: 0; }
            blockquote { margin: 10px 0; padding: 8px 12px; border-left: 4px solid #475569; background: %s; }
            table { border-collapse: collapse; width: 100%%; margin: 8px 0; }
            th, td { border: 1px solid %s; padding: 6px 8px; text-align: left; color: %s; }
            th { background: %s; color: #93c5fd; }
            .intro { color: %s; font-style: italic; margin-bottom: 12px; }
            .code-block { margin: 8px 0; border: 1px solid %s; border-radius: 6px; overflow: hidden; }
            .code-header { background: %s; padding: 4px 8px; font-size: 10px; color: %s; font-weight: bold; }
            .code-scroll { overflow-x: auto; margin: 0; border: none; border-radius: 0; }
        """,
                bodyColor, borderColor, userBgColor, assistBgColor,
                bodyColor, assistBgColor, borderColor,
                assistBgColor, borderColor, bodyColor,
                assistBgColor,
                borderColor, bodyColor,
                userBgColor,
                // .intro color
                toHex(isDarkMode(api) ? new Color(156, 163, 175) : new Color(107, 114, 128)),
                borderColor,
                borderColor, bodyColor
        );
    }
}
