package com.hopla;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;

import static com.hopla.Constants.DEFAULT_PAYLOAD_RESOURCE_PATH;
import static com.hopla.Constants.PREFERENCE_AI;
import static com.hopla.Constants.PREFERENCE_AI_CHATS;
import static com.hopla.Constants.PREFERENCE_AI_CONFIGURATION;
import static com.hopla.Constants.PREFERENCE_AUTOCOMPLETION;
import static com.hopla.Constants.PREFERENCE_CUSTOM_PATH;
import static com.hopla.Constants.PREFERENCE_LOCAL_DICT;
import static com.hopla.Constants.PREFERENCE_SHORTCUTS;
import static com.hopla.Utils.success;

import burp.api.montoya.MontoyaApi;

public class MenuBar {

    private final MontoyaApi api;
    private final PayloadManager payloadManager;
    private final HopLa hopla;

    public MenuBar(MontoyaApi api, HopLa hopla, PayloadManager payloadManager) {
        this.api = api;
        this.hopla = hopla;
        this.payloadManager = payloadManager;
        buildAndRegisterMenu();
    }

    private String getPayloadsFilename() {
        return payloadManager.getCurrentPath().equals(DEFAULT_PAYLOAD_RESOURCE_PATH) ? "Built-in default payloads" : payloadManager.getCurrentPath();
    }

    private void buildAndRegisterMenu() {
        JMenu menu = new JMenu(Constants.EXTENSION_NAME);
        JMenuItem pathItem = new JMenuItem("Loaded payloads: " + getPayloadsFilename());
        pathItem.setEnabled(false);

        JMenuItem chooseItem = new JMenuItem(Constants.MENU_ITEM_CHOOSE_PAYLOAD);
        chooseItem.addActionListener(e -> {
            payloadManager.choosePayloadFile();
            pathItem.setText("Loaded payloads: " + getPayloadsFilename());
            reloadShortcuts();
        });

        JMenuItem reloadItem = new JMenuItem(Constants.MENU_ITEM_RELOAD_PAYLOADS);
        reloadItem.addActionListener(e -> {
            payloadManager.loadPayloads();
            api.logging().logToOutput("Payloads file reloaded: " + payloadManager.getCurrentPath());
            success(getPayloadsFilename() + " reloaded");
            api.logging().logToOutput("Reloading shortcuts");
            reloadShortcuts();
        });

        JMenuItem exportDefaultPayloadsItem = new JMenuItem(Constants.MENU_ITEM_EXPORT_DEFAULT_PAYLOADS);
        exportDefaultPayloadsItem.addActionListener(e -> {
            payloadManager.export();
        });

        JMenuItem clearPreferencesItem = new JMenuItem(Constants.MENU_ITEM_CLEAR_PREFERENCES);
        clearPreferencesItem.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(menu, "Clear preferences ?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                api.persistence().preferences().deleteBoolean(PREFERENCE_SHORTCUTS);
                api.persistence().preferences().deleteBoolean(PREFERENCE_AUTOCOMPLETION);
                api.persistence().preferences().deleteString(PREFERENCE_CUSTOM_PATH);
                api.persistence().preferences().deleteBoolean(PREFERENCE_AI);
                api.persistence().preferences().deleteString(PREFERENCE_LOCAL_DICT);
                api.persistence().preferences().deleteString(PREFERENCE_AI_CONFIGURATION);
                api.persistence().preferences().deleteString(PREFERENCE_AI_CHATS);
                success("Preferences cleared. Please reload the extension");
            }

        });

        menu.add(pathItem);
        menu.add(chooseItem);
        menu.add(reloadItem);
        menu.add(exportDefaultPayloadsItem);
        menu.add(new JSeparator());
        menu.add(clearPreferencesItem);
        api.userInterface().menuBar().registerMenu(menu);
    }

    private void reloadShortcuts() {
        hopla.disableShortcuts();
        hopla.enableShortcuts();
    }

}
