package com.hopla;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.EnhancedCapability;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Registration;
import burp.api.montoya.extension.ExtensionUnloadingHandler;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import burp.api.montoya.ui.hotkey.HotKeyContext;
import burp.api.montoya.ui.hotkey.HotKeyHandler;
import burp.api.montoya.ui.settings.SettingsPanel;
import burp.api.montoya.ui.settings.SettingsPanelBuilder;
import burp.api.montoya.ui.settings.SettingsPanelPersistence;
import burp.api.montoya.ui.settings.SettingsPanelSetting;
import burp.api.montoya.ui.settings.SettingsPanelWithData;
import com.hopla.ai.AIChats;
import com.hopla.ai.AIConfiguration;
import com.hopla.ai.AIProviderType;
import com.hopla.ai.AIQuickAction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hopla.Constants.*;
import static com.hopla.Utils.alert;
import static com.hopla.Utils.getSelectedText;

public class HopLa implements BurpExtension, ExtensionUnloadingHandler, AWTEventListener {

    public static MontoyaApi montoyaApi;
    public static LocalPayloadsManager localPayloadsManager;
    public static SearchReplaceWindow searchReplaceWindow;
    public static AIChatPanel aiChatPanel;
    public static AIConfiguration aiConfiguration;
    public static AIChats aiChats;
    public static AIQuickAction aiQuickAction;
    private static String extensionName;
    private final ArrayList<Completer> listeners = new ArrayList<>();
    private final ArrayList<Registration> registrations = new ArrayList<Registration>();
    private final Set<String> registeredShortcuts = new HashSet<>();
    public Boolean autocompletionEnabled;
    public Boolean shortcutsEnabled;
    public Boolean aiAutocompletionEnabled;
    private PayloadManager payloadManager;
    private AutoCompleteMenu autoCompleteMenu;
    private PayloadMenu payloadMenu;
    private SettingsPanelWithData settingsPanel;

    @Override
    public void initialize(MontoyaApi montoyaApi) {
        HopLa.montoyaApi = montoyaApi;
        HopLa.extensionName = Constants.EXTENSION_NAME;

        montoyaApi.extension().setName(Constants.EXTENSION_NAME);
        montoyaApi.extension().registerUnloadingHandler(this);

        // Build Settings Panel
        List<String> providerNames = Arrays.stream(AIProviderType.values()).map(Enum::name).collect(Collectors.toList());

        // General Settings
        List<SettingsPanelSetting> settings = new ArrayList<>();
        settings.add(SettingsPanelSetting.booleanSetting("Enable Shortcuts", true));
        settings.add(SettingsPanelSetting.booleanSetting("Enable Autocompletion", true));
        settings.add(SettingsPanelSetting.booleanSetting("Enable AI Autocompletion", false));
        settings.add(SettingsPanelSetting.stringSetting("Chat Shortcut", "Ctrl+J"));
        settings.add(SettingsPanelSetting.stringSetting("Quick Action Shortcut", "Ctrl+Shift+Q"));
        settings.add(SettingsPanelSetting.integerSetting("Autocompletion Min Chars", 1));

        settings.add(SettingsPanelSetting.listSetting("Default Chat Provider", providerNames, AIProviderType.OLLAMA.name()));
        settings.add(SettingsPanelSetting.listSetting("Default Completion Provider", providerNames, AIProviderType.OLLAMA.name()));
        settings.add(SettingsPanelSetting.listSetting("Default Quick Action Provider", providerNames, AIProviderType.OLLAMA.name()));

        // Provider Settings
        for (AIProviderType type : AIProviderType.values()) {
            String prefix = type.name() + " - ";
            settings.add(SettingsPanelSetting.booleanSetting(prefix + "Enabled", true));
            settings.add(SettingsPanelSetting.stringSetting(prefix + "API Key", ""));
            settings.add(SettingsPanelSetting.stringSetting(prefix + "Model", ""));

            String defaultEndpoint = "";
            if (type == AIProviderType.OLLAMA) {
                defaultEndpoint = "http://localhost:11434";
            }
            settings.add(SettingsPanelSetting.stringSetting(prefix + "Endpoint", defaultEndpoint));
        }

        settingsPanel = SettingsPanelBuilder.settingsPanel()
                .withPersistence(SettingsPanelPersistence.USER_SETTINGS)
                .withTitle("HopLa Settings")
                .withDescription("Configure HopLa extension settings.")
                .withSettings(settings.toArray(new SettingsPanelSetting[0]))
                .build();

        montoyaApi.userInterface().registerSettingsPanel(settingsPanel);

        // Initialize Configuration with SettingsPanel
        aiConfiguration = new AIConfiguration(montoyaApi, settingsPanel);
        aiChats = new AIChats();

        aiQuickAction = new AIQuickAction(aiConfiguration);

        // Load initial states from settings
        aiAutocompletionEnabled = settingsPanel.getBoolean("Enable AI Autocompletion");
        shortcutsEnabled = settingsPanel.getBoolean("Enable Shortcuts");
        autocompletionEnabled = settingsPanel.getBoolean("Enable Autocompletion");

        montoyaApi.logging().logToOutput("AI configured: " + aiConfiguration.isAIConfigured);

        if (Constants.EXTERNAL_AI) {
            montoyaApi.logging().logToOutput("AI Autocompletion enabled: " + aiAutocompletionEnabled);
        }

        montoyaApi.logging().logToOutput("Shortcuts enabled: " + shortcutsEnabled);
        montoyaApi.logging().logToOutput("Autocompletion enabled: " + autocompletionEnabled);

        localPayloadsManager = new LocalPayloadsManager(montoyaApi);
        payloadManager = new PayloadManager(montoyaApi, localPayloadsManager);
        autoCompleteMenu = new AutoCompleteMenu(this, montoyaApi, payloadManager, aiConfiguration);
        searchReplaceWindow = new SearchReplaceWindow(montoyaApi);
        payloadMenu = new PayloadMenu(payloadManager, montoyaApi);
        aiChatPanel = new AIChatPanel(aiConfiguration, aiChats);
        montoyaApi.userInterface().registerContextMenuItemsProvider(new ContextMenu(montoyaApi, payloadManager));
        new MenuBar(montoyaApi, this, payloadManager);

        if (shortcutsEnabled) {
            enableShortcuts();
        }
        if (autocompletionEnabled) {
            enableAutocompletion();
        }

        if (Constants.DEBUG) {
            montoyaApi.logging().logToOutput("Debug enabled");
        }
        montoyaApi.logging().logToOutput(Constants.INIT_MESSAGE);

    }

    @Override
    public Set<EnhancedCapability> enhancedCapabilities() {
        return Set.of(EnhancedCapability.AI_FEATURES);
    }

    public void enableAutocompletion() {
        montoyaApi.persistence()
                .preferences().setBoolean(PREFERENCE_AUTOCOMPLETION, true);
        autocompletionEnabled = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
    }

    public void disableAutocompletion() {
        montoyaApi.persistence()
                .preferences().setBoolean(PREFERENCE_AUTOCOMPLETION, false);
        autocompletionEnabled = false;
        removeListeners();
    }

    public void enableShortcuts() {
        montoyaApi.persistence()
                .preferences().setBoolean(PREFERENCE_SHORTCUTS, true);
        shortcutsEnabled = true;
        registerShortcuts();
    }

    public void disableShortcuts() {
        montoyaApi.persistence()
                .preferences().setBoolean(PREFERENCE_SHORTCUTS, false);
        shortcutsEnabled = false;
        unregisterShortcuts();
    }

    private void unregisterShortcuts() {
        for (Registration registration : registrations) {
            registration.deregister();
        }
        registrations.clear();
        registeredShortcuts.clear();
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        if (event.getSource() instanceof JTextArea source) {
            if (source.getClientProperty("hasListener") != null && ((Boolean) source.getClientProperty("hasListener"))) {
                return;
            }

            // enable to debug awt frame
            if (AWT_DEBUG) {
                Container comp = source;
                while (comp != null) {
                    montoyaApi.logging().logToOutput("Ancestor: " + comp.getClass().getName() + " name: " + comp.getName());
                    comp = comp.getParent();
                }
            }

            Container is_editor = SwingUtilities.getAncestorNamed("messageEditor", source);

            if (is_editor == null) {
                return;
            }
            if (AWT_DEBUG) {
                montoyaApi.logging().logToOutput("Message editor detected: " + source.getName());
            }
            if (!source.isEditable()) {
                return;
            }
            if (Constants.DEBUG) {
                montoyaApi.logging().logToOutput("Message editor is editable: " + source.getName());
            }
            if (autocompletionEnabled) {
                Completer t = new Completer(montoyaApi, source, autoCompleteMenu);
                source.putClientProperty("hasListener", true);
                this.listeners.add(t);
                if (Constants.DEBUG) {
                    montoyaApi.logging().logToOutput("Add completer: " + source.getName());
                }
            }
        }
    }

    private void removeListeners() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(this);

        // Remove all listeners on unload
        for (Completer listener : this.listeners) {
            listener.detach();
            listener.getSource().putClientProperty("hasListener", false);
        }
    }

    @Override
    public void extensionUnloaded() {
        removeListeners();
        autoCompleteMenu.dispose();
        payloadMenu.dispose();
        localPayloadsManager.dispose();
        searchReplaceWindow.dispose();
        aiChatPanel.dispose();
        aiQuickAction.dispose();
        unregisterShortcuts();
        montoyaApi.logging().logToOutput(extensionName + " unloaded");
    }

    private void registerShortcuts() {

        if (montoyaApi.burpSuite().version().buildNumber() < 20250300000037651L) {
            alert("Register Hotkey not supported with this Burp Version");
            return;
        }

        for (PayloadDefinition.Category category : payloadManager.getPayloads().categories) {
            this.recursiveRegisterShortcuts(category);
        }

        this.registerShortcut(payloadManager.getPayloads().shortcut_payload_menu, "Payload Menu", event -> {
            if (event.messageEditorRequestResponse().isEmpty()) {
                return;
            }
            MessageEditorHttpRequestResponse messageEditor = event.messageEditorRequestResponse().get();
            payloadMenu.show(messageEditor, event.inputEvent());
        });

        this.registerShortcut(payloadManager.getPayloads().shortcut_search_and_replace, "Search Replace", event -> {
            if (event.messageEditorRequestResponse().isEmpty()) {
                return;
            }
            MessageEditorHttpRequestResponse messageEditor = event.messageEditorRequestResponse().get();
            searchReplaceWindow.attach(messageEditor, event.inputEvent(), getSelectedText(messageEditor));
        });

        this.registerShortcut(payloadManager.getPayloads().shortcut_add_custom_keyword, "Add custom keyword", event -> {
            if (event.messageEditorRequestResponse().isEmpty()) {
                return;
            }
            MessageEditorHttpRequestResponse messageEditor = event.messageEditorRequestResponse().get();
            HopLa.localPayloadsManager.add(getSelectedText(messageEditor));
        });

        this.registerShortcut(payloadManager.getPayloads().shortcut_collaborator, "Collaborator", event -> {
            if (event.messageEditorRequestResponse().isEmpty()) {
                return;
            }
            MessageEditorHttpRequestResponse messageEditor = event.messageEditorRequestResponse().get();
            Utils.InsertCollaboratorPayload(montoyaApi, messageEditor, event.inputEvent());
        });

        if (aiConfiguration.isAIConfigured) {
            this.registerShortcut(aiConfiguration.getChatShortcut(), "AI chat", event -> {
                if (event.messageEditorRequestResponse().isEmpty()) {
                    return;
                }
                MessageEditorHttpRequestResponse messageEditor = event.messageEditorRequestResponse().get();
                aiChatPanel.show(messageEditor, event.inputEvent(), getSelectedText(messageEditor));
            });
            this.registerShortcut(aiConfiguration.getQuickActionShortcut(), "AI Quick action", event -> {
                if (event.messageEditorRequestResponse().isEmpty()) {
                    return;
                }
                MessageEditorHttpRequestResponse messageEditor = event.messageEditorRequestResponse().get();
                aiQuickAction.show(messageEditor, event.inputEvent(), getSelectedText(messageEditor));
            });

        }

    }

    private void recursiveRegisterShortcuts(PayloadDefinition.Category category) {
        if (category.payloads != null) {
            for (PayloadDefinition.Payload payload : category.payloads) {
                if (payload.shortcut == null || payload.shortcut.isBlank()) {
                    continue;
                }
                this.registerShortcut(payload.shortcut, category.name + " " + payload.name, event -> {
                    if (event.messageEditorRequestResponse().isEmpty()) {
                        return;
                    }
                    MessageEditorHttpRequestResponse messageEditor = event.messageEditorRequestResponse().get();
                    Utils.insertPayload(messageEditor, payload.value, event.inputEvent());
                });
            }
        }

        if (category.categories != null) {
            for (PayloadDefinition.Category sub : category.categories) {
                this.recursiveRegisterShortcuts(sub);
            }
        }
    }

    private void registerShortcut(String shortcut, String message, HotKeyHandler handler) {
        String normalizedShortcut = Utils.normalizeShortcut(shortcut);
        if (normalizedShortcut == null) {
            return;
        }

        if (registeredShortcuts.contains(normalizedShortcut)) {
            montoyaApi.logging().logToError("Duplicate shortcut in HopLa: " + normalizedShortcut + " - " + message);
            return;
        }

        Registration registration = montoyaApi.userInterface().registerHotKeyHandler(HotKeyContext.HTTP_MESSAGE_EDITOR, normalizedShortcut, handler);

        if (registration.isRegistered()) {
            montoyaApi.logging().logToOutput("Successfully registered hotkey handler: " + normalizedShortcut + " - " + message);
            registrations.add(registration);
            registeredShortcuts.add(normalizedShortcut);
        } else {
            montoyaApi.logging().logToError("Failed to register hotkey handler: " + normalizedShortcut + " - " + message);
            alert("Failed to register hotkey handler: " + normalizedShortcut);
        }
    }
}
