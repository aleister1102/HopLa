package com.hopla.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.hopla.Constants;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.settings.SettingsPanelWithData;

public class AIConfiguration {

    private final MontoyaApi api;
    private final SettingsPanelWithData settingsPanel;
    public boolean isAIConfigured = true;

    public AIConfiguration(MontoyaApi api, SettingsPanelWithData settingsPanel) {
        this.api = api;
        this.settingsPanel = settingsPanel;
    }

    public List<AIProviderType> getEnabledProviders() {
        List<AIProviderType> enabled = new ArrayList<>();
        if (!Constants.EXTERNAL_AI) {
            enabled.add(AIProviderType.OLLAMA);
            return enabled;
        }
        for (AIProviderType type : AIProviderType.values()) {
            String prefix = type.name() + " - ";
            if (settingsPanel.getBoolean(prefix + "Enabled")) {
                enabled.add(type);
            }
        }
        return enabled;
    }

    public int getAutocompletionMinChars() {
        if (!Constants.EXTERNAL_AI) {
            return 3;
        }
        Integer val = settingsPanel.getInteger("autocompletion_min_chars");
        return val != null ? val : 3;
    }

    public String getChatShortcut() {
        if (!Constants.EXTERNAL_AI) {
            return "Ctrl+J";
        }
        String val = settingsPanel.getString("Chat Shortcut");
        return val != null ? val : "Ctrl+J";
    }

    public String getQuickActionShortcut() {
        if (!Constants.EXTERNAL_AI) {
            return "Ctrl+Shift+Q";
        }
        String val = settingsPanel.getString("Quick Action Shortcut");
        return val != null ? val : "Ctrl+Shift+Q";
    }

    public List<LLMConfig.QuickAction> getQuickActions() {
        List<LLMConfig.QuickAction> actions = new ArrayList<>();
        actions.add(new LLMConfig.QuickAction("Summarize", "Summarize this HTTP request/response."));
        actions.add(new LLMConfig.QuickAction("Explain", "Explain what this request does."));
        actions.add(new LLMConfig.QuickAction("Find Vulns", "Analyze this for potential security vulnerabilities."));
        return actions;
    }

    public List<LLMConfig.Prompt> getPrompts() {
        List<LLMConfig.Prompt> prompts = new ArrayList<>();
        prompts.add(new LLMConfig.Prompt("Fix Grammar", "Fix Grammar", "Fix grammar in the following text:"));
        prompts.add(new LLMConfig.Prompt("Analyze", "Analyze", "Analyze the security impact of this request:"));
        return prompts;
    }

    public AIProvider getChatProvider() {
        if (!Constants.EXTERNAL_AI) {
            return getProvider(AIProviderType.OLLAMA);
        }
        String providerName = settingsPanel.getString("Default Chat Provider");
        if (providerName == null) {
            return null;
        }
        try {
            return getProvider(AIProviderType.valueOf(providerName));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public AIProvider getCompletionProvider() {
        if (!Constants.EXTERNAL_AI) {
            return getProvider(AIProviderType.OLLAMA);
        }
        String providerName = settingsPanel.getString("Default Completion Provider");
        if (providerName == null) {
            return null;
        }
        try {
            return getProvider(AIProviderType.valueOf(providerName));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public AIProvider getQuickActionProvider() {
        if (!Constants.EXTERNAL_AI) {
            return getProvider(AIProviderType.OLLAMA);
        }
        String providerName = settingsPanel.getString("Default Quick Action Provider");
        if (providerName == null) {
            return null;
        }
        try {
            return getProvider(AIProviderType.valueOf(providerName));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public AIProvider getProvider(AIProviderType type) {
        // Construct LLMConfig on the fly from SettingsPanel
        LLMConfig config = new LLMConfig();
        config.defaults = new LLMConfig.Defaults(); // defaults not strictly needed here if we pass specific provider config
        config.providers = new HashMap<>();

        LLMConfig.Provider providerConfig = new LLMConfig.Provider();
        String prefix = type.name() + " - ";
        providerConfig.enabled = settingsPanel.getBoolean(prefix + "Enabled");
        providerConfig.api_key = settingsPanel.getString(prefix + "API Key");
        providerConfig.chat_model = settingsPanel.getString(prefix + "Model");
        providerConfig.chat_endpoint = settingsPanel.getString(prefix + "Endpoint");

        config.providers.put(type, providerConfig);

        return AIProviderFactory.createProvider(type, config, providerConfig);
    }

    public String getCompletionProviderName() {
        AIProvider p = getCompletionProvider();
        return p != null ? p.providerName : "Not configured";
    }

    public String getQuickActionProviderName() {
        AIProvider p = getQuickActionProvider();
        return p != null ? p.providerName : "Not configured";
    }
}
