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
        prompts.add(new LLMConfig.Prompt("General Assistant", "General Pentest Assistant", "You are a helpful penetration testing assistant. Answer questions concisely and focus on security implications. If you provide code or payloads, explain how they work and why they are effective."));
        prompts.add(new LLMConfig.Prompt("Analyze Request", "Analyze Request", "Analyze the provided HTTP request for potential security vulnerabilities. Focus on the following areas:\n"
                + "1. **Input Validation**: Check for missing or weak validation on parameters, headers, and cookies.\n"
                + "2. **Injection Attacks**: Identify potential injection points for SQLi, XSS, Command Injection, etc.\n"
                + "3. **Authentication & Authorization**: Look for IDOR, broken access control, or weak authentication mechanisms.\n"
                + "4. **Sensitive Data Exposure**: Check if sensitive information (PII, credentials, tokens) is being transmitted insecurely.\n"
                + "5. **Logic Flaws**: Identify any business logic errors or race conditions.\n"
                + "Provide a summary of findings and rate the severity of each issue."));
        prompts.add(new LLMConfig.Prompt("Suggest SQLi", "Suggest SQLi", "Analyze the request and suggest specific SQL injection payloads. Consider the following:\n"
                + "- **Database Type**: If known or inferable (MySQL, PostgreSQL, Oracle, MSSQL), tailor payloads accordingly.\n"
                + "- **Context**: Is the injection in a WHERE clause, ORDER BY, or INSERT statement?\n"
                + "- **WAF Bypass**: Include techniques to bypass common WAF filters (e.g., encoding, whitespace manipulation, SQL comments).\n"
                + "- **Blind vs. Error-Based**: Suggest payloads for both blind and error-based injection scenarios.\n"
                + "Provide a list of payloads and explain why each one might work."));
        prompts.add(new LLMConfig.Prompt("Suggest CMDi", "Suggest CMDi", "Analyze the request and suggest command injection payloads. Focus on:\n"
                + "- **OS Context**: Linux vs. Windows payloads.\n"
                + "- **Separators**: Use various separators like `;`, `|`, `&&`, `||`, `$(...)`, `` `...` ``.\n"
                + "- **Filter Bypass**: Techniques to bypass blacklists (e.g., using `$IFS`, concatenation, base64 encoding).\n"
                + "- **OOB Extraction**: Payloads for out-of-band data exfiltration (DNS, HTTP).\n"
                + "List the payloads and describe the expected behavior."));
        prompts.add(new LLMConfig.Prompt("Suggest LFI/Path Traversal", "Suggest LFI", "Analyze the request and suggest LFI and Path Traversal payloads. Consider:\n"
                + "- **Path Manipulation**: `../`, `..\\`, absolute paths.\n"
                + "- **Encoding**: URL encoding, double URL encoding, Unicode variations.\n"
                + "- **Null Byte**: `%00` injection for older PHP versions.\n"
                + "- **Wrappers**: PHP wrappers (`php://filter`, `php://input`, `expect://`, `zip://`).\n"
                + "- **OS Specifics**: Common sensitive files for Linux (`/etc/passwd`) and Windows (`C:\\Windows\\win.ini`).\n"
                + "Provide a diverse set of payloads."));
        prompts.add(new LLMConfig.Prompt("Suggest LDAP Injection", "Suggest LDAP", "Analyze the request and suggest LDAP injection payloads. Focus on:\n"
                + "- **Filter Manipulation**: Using `*`, `(`, `)`, `&`, `|` to alter LDAP filters.\n"
                + "- **Authentication Bypass**: Payloads to bypass login forms.\n"
                + "- **Data Extraction**: Techniques to extract attributes or valid usernames.\n"
                + "- **Blind Injection**: Payloads for inferring data character by character.\n"
                + "List the payloads and explain the logic behind them."));
        prompts.add(new LLMConfig.Prompt("Suggest RFI", "Suggest RFI", "Analyze the request and suggest Remote File Inclusion (RFI) payloads. Include:\n"
                + "- **Remote URL Schemes**: `http://`, `https://`, `ftp://`, `smb://`.\n"
                + "- **Bypass Techniques**: Null bytes, question marks to terminate strings.\n"
                + "- **Verification**: Simple payloads to verify RFI (e.g., pinging a collaborator URL).\n"
                + "Provide a list of payloads and how to verify execution."));
        prompts.add(new LLMConfig.Prompt("Suggest SSTI", "Suggest SSTI", "Analyze the request and suggest Server-Side Template Injection (SSTI) payloads. Consider:\n"
                + "- **Template Engines**: Detect the engine (Jinja2, Twig, FreeMarker, Velocity, Thymeleaf, etc.).\n"
                + "- **Context**: Payloads for variable interpolation vs. code execution.\n"
                + "- **Polyglots**: Payloads that work across multiple engines.\n"
                + "Provide payloads sorted by likely template engine."));
        prompts.add(new LLMConfig.Prompt("Suggest XSS", "Suggest XSS", "Analyze the request and suggest Cross-Site Scripting (XSS) payloads. Focus on:\n"
                + "- **Context**: Reflected, Stored, or DOM-based XSS.\n"
                + "- **Injection Point**: HTML body, attribute, script tag, or event handler.\n"
                + "- **WAF Bypass**: Encoding, obfuscation, and polyglots.\n"
                + "- **Payload Types**: `alert(1)`, `document.cookie` exfiltration, and redirection.\n"
                + "List the payloads and the specific context they are designed for."));
        prompts.add(new LLMConfig.Prompt("Fix Grammar", "Fix Grammar", "Correct the grammar and spelling in the following text. Ensure the tone remains professional and clear. Do not change the meaning of the text."));
        prompts.add(new LLMConfig.Prompt("Explain Vulnerability", "Explain Vulnerability", "Explain the selected vulnerability in detail. Include:\n"
                + "1. **Description**: What the vulnerability is and how it works.\n"
                + "2. **Impact**: The potential consequences (e.g., data loss, RCE).\n"
                + "3. **Example**: A simple request/response example demonstrating the issue.\n"
                + "4. **Exploitation**: How an attacker might exploit it in a real-world scenario."));
        prompts.add(new LLMConfig.Prompt("Remediation", "Remediation", "Provide detailed remediation steps for the selected vulnerability. Include:\n"
                + "1. **Short-term Fix**: Immediate actions to mitigate the risk.\n"
                + "2. **Long-term Fix**: Code changes or architectural improvements to prevent recurrence.\n"
                + "3. **Code Example**: Secure code snippets in relevant languages (Java, Python, etc.).\n"
                + "4. **References**: Links to OWASP or CWE for further reading."));
        prompts.add(new LLMConfig.Prompt("Code Review", "Code Review", "Perform a security code review on the provided snippet. Look for:\n"
                + "- **Injection Flaws**: SQLi, XSS, CMDi.\n"
                + "- **Insecure Deserialization**.\n"
                + "- **Hardcoded Secrets**: API keys, passwords.\n"
                + "- **Broken Access Control**.\n"
                + "- **Use of Vulnerable Components**.\n"
                + "Explain each finding with line numbers and suggest a secure replacement."));
        prompts.add(new LLMConfig.Prompt("Agent Scan", "Agent Scan", "Analyze the following HTTP request and response for security vulnerabilities. If you find any issues, return them in the following JSON format:\n"
                + "[\n"
                + "  {\n"
                + "    \"name\": \"Issue Name\",\n"
                + "    \"detail\": \"Detailed description of the issue...\",\n"
                + "    \"severity\": \"High|Medium|Low|Information\",\n"
                + "    \"confidence\": \"Certain|Firm|Tentative\",\n"
                + "    \"remediation\": \"Remediation steps...\"\n"
                + "  }\n"
                + "]\n"
                + "If no issues are found, return an empty list []."));
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
