package com.hopla.ai;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hopla.HopLa;
import com.hopla.Utils;

import javax.swing.*;
import java.lang.reflect.Type;
import java.util.List;

public class AIAgent {
    private final AIConfiguration aiConfiguration;
    private final Gson gson = new Gson();

    public AIAgent(AIConfiguration aiConfiguration) {
        this.aiConfiguration = aiConfiguration;
    }

    public void run(HttpRequest request) {
        if (!aiConfiguration.isAIConfigured) {
            Utils.alert("AI is not configured");
            return;
        }

        // Send request in a separate thread to avoid blocking UI
        new Thread(() -> {
            try {
                HttpRequestResponse reqRes = HopLa.montoyaApi.http().sendRequest(request);
                analyze(reqRes);
            } catch (Exception e) {
                HopLa.montoyaApi.logging().logToError("Agent error sending request: " + e.getMessage());
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Agent failed to send request: " + e.getMessage()));
            }
        }).start();
    }

    private void analyze(HttpRequestResponse reqRes) {
        AIProvider provider = aiConfiguration.getChatProvider();
        if (provider == null) {
            return;
        }

        String promptTemplate = aiConfiguration.getPrompts().stream()
                .filter(p -> p.name.equals("Agent Scan"))
                .findFirst()
                .map(p -> p.content)
                .orElse("");
        
        if (promptTemplate.isEmpty()) {
             HopLa.montoyaApi.logging().logToError("Agent Scan prompt not found");
             return;
        }

        String prompt = promptTemplate + "\n\nRequest:\n" + reqRes.request().toString() + "\n\nResponse:\n" + reqRes.response().toString();

        try {
            provider.instruct(prompt, new AIProvider.StreamingCallback() {
                StringBuilder sb = new StringBuilder();

                @Override
                public void onData(String chunk) {
                    sb.append(chunk);
                }

                @Override
                public void onDone() {
                    processAiResponse(sb.toString(), reqRes);
                }

                @Override
                public void onError(String error) {
                    HopLa.montoyaApi.logging().logToError("Agent AI error: " + error);
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Agent AI error: " + error));
                }
            });
        } catch (Exception e) {
            HopLa.montoyaApi.logging().logToError("Agent instruct error: " + e.getMessage());
        }
    }

    private void processAiResponse(String jsonResponse, HttpRequestResponse reqRes) {
        try {
            // Extract JSON from response (in case of markdown code blocks)
            String json = jsonResponse;
            if (json.contains("```json")) {
                json = json.substring(json.indexOf("```json") + 7);
                if (json.contains("```")) {
                    json = json.substring(0, json.indexOf("```"));
                }
            } else if (json.contains("```")) {
                 json = json.substring(json.indexOf("```") + 3);
                if (json.contains("```")) {
                    json = json.substring(0, json.indexOf("```"));
                }
            }
            json = json.trim();

            Type listType = new TypeToken<List<IssueData>>(){}.getType();
            List<IssueData> issues = gson.fromJson(json, listType);

            if (issues != null && !issues.isEmpty()) {
                for (IssueData issue : issues) {
                    AuditIssue auditIssue = AuditIssue.auditIssue(
                            issue.name,
                            issue.detail,
                            issue.remediation,
                            reqRes.request().url(),
                            getSeverity(issue.severity),
                            getConfidence(issue.confidence),
                            null,
                            null,
                            getSeverity(issue.severity), // typical severity
                            reqRes
                    );
                    HopLa.montoyaApi.siteMap().add(auditIssue);
                }
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Agent finished. " + issues.size() + " issues found and added to Site Map."));
            } else {
                 SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Agent finished. No issues found."));
            }

        } catch (Exception e) {
            HopLa.montoyaApi.logging().logToError("Agent parsing error: " + e.getMessage() + "\nJSON: " + jsonResponse);
             SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Agent finished but failed to parse response. Check extension errors."));
        }
    }

    private AuditIssueSeverity getSeverity(String s) {
        if (s == null) return AuditIssueSeverity.INFORMATION;
        switch (s.toLowerCase()) {
            case "high": return AuditIssueSeverity.HIGH;
            case "medium": return AuditIssueSeverity.MEDIUM;
            case "low": return AuditIssueSeverity.LOW;
            default: return AuditIssueSeverity.INFORMATION;
        }
    }

    private AuditIssueConfidence getConfidence(String s) {
        if (s == null) return AuditIssueConfidence.TENTATIVE;
        switch (s.toLowerCase()) {
            case "certain": return AuditIssueConfidence.CERTAIN;
            case "firm": return AuditIssueConfidence.FIRM;
            default: return AuditIssueConfidence.TENTATIVE;
        }
    }

    private static class IssueData {
        String name;
        String detail;
        String severity;
        String confidence;
        String remediation;
    }
}
