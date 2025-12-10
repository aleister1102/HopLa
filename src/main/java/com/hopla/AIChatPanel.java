package com.hopla;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.JDialog;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;

import static com.hopla.Constants.DEBUG_AI;
import static com.hopla.Constants.EXTERNAL_AI;
import static com.hopla.Utils.alert;
import static com.hopla.Utils.generateJFrame;
import static com.hopla.Utils.getRequest;
import static com.hopla.Utils.getResponse;
import com.hopla.ai.AIChats;
import com.hopla.ai.AIConfiguration;
import com.hopla.ai.AIProvider;
import com.hopla.ai.AIProviderType;
import com.hopla.ai.LLMConfig;

import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;

public class AIChatPanel {

    private final static String REQUEST_PLACEHOLDER = "@request@";
    private final static String RESPONSE_PLACEHOLDER = "@response@";
    private final static String BUTTON_TEXT_SEND = "Ask";
    private final static String BUTTON_CANCEL_SEND = "Cancel";
    private final static String NOTES_PLACEHOLDER = "@notes@";

    private final JLabel statusLabel = new JLabel(" ");
    private final AIConfiguration aiConfiguration;
    private final AIChats chats;
    private final HTMLEditorKit kit = new HTMLEditorKit();
    private final StyleSheet styleSheet = new StyleSheet();
    private Parser parser;
    private HtmlRenderer renderer;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private JTextArea inputField;
    private JFrame frame;
    private JTextArea source;
    private JList<String> chatsList;
    private JTextPane editorPane;
    private AIProviderType currentProvider;
    private AIProvider aiProvider;
    private JScrollPane scrollPane;
    private long lastUiUpdate = 0;

    public AIChatPanel(AIConfiguration aiConfiguration, AIChats chats) {
        this.aiConfiguration = aiConfiguration;
        this.chats = chats;
        if (aiConfiguration.isAIConfigured) {
            AIProvider provider = aiConfiguration.getChatProvider();
            if (provider != null) {
                this.currentProvider = provider.type;
            }
        }

        String css = ThemeUtils.getCss(HopLa.montoyaApi);
        styleSheet.addRule(css);
        kit.setStyleSheet(styleSheet);
        java.util.List<Extension> exts = java.util.Arrays.asList(
                TablesExtension.create(),
                AutolinkExtension.create(),
                StrikethroughExtension.create()
        );
        parser = Parser.builder().extensions(exts).build();
        renderer = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).extensions(exts).build();
    }

    public void show(MessageEditorHttpRequestResponse messageEditor, InputEvent event, String input) {
        if (frame != null) {
            frame.dispose();
        }
        if (!aiConfiguration.isAIConfigured) {
            alert("AI is not configured");
            return;
        }

        if (currentProvider == null) {
            AIProvider p = aiConfiguration.getChatProvider();
            if (p != null) {
                this.currentProvider = p.type;
            }
        }

        this.source = (JTextArea) event.getSource();
        frame = generateJFrame();
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setAlwaysOnTop(false);

        // ESC closes the panel and cancels any running AI requests
        frame.getRootPane().registerKeyboardAction(e -> {
            if (aiProvider != null) {
                aiProvider.cancelCurrentChatRequest();
            }
            frame.dispose();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (aiProvider != null) {
                    aiProvider.cancelCurrentChatRequest();
                }
            }
        });

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        editorPane = new JTextPane();
        editorPane.setEditable(false);
        editorPane.setOpaque(false);
        editorPane.setContentType("text/html");
        editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        editorPane.setEditorKit(kit);
        Document doc = kit.createDefaultDocument();
        editorPane.setDocument(doc);
        editorPane.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPopupMenu contextMenu = new JPopupMenu();
        contextMenu.add(new JMenuItem(new DefaultEditorKit.CopyAction()));
        contextMenu.add(new JMenuItem(new AbstractAction("Insert selection in editor") {
            public void actionPerformed(ActionEvent e) {
                if (editorPane.getSelectedText() != null) {
                    source.insert(editorPane.getSelectedText(), source.getCaretPosition());
                }

            }
        }));

        editorPane.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    show(e);
                }
            }

            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    show(e);
                }
            }

            private void show(MouseEvent e) {
                if (editorPane.getSelectedText() != null && !editorPane.getSelectedText().isEmpty()) {
                    contextMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int width = panel.getWidth() - 50;
                editorPane.setMaximumSize(new Dimension(width, Short.MAX_VALUE));
                panel.repaint();
                panel.invalidate();
            }
        });

        scrollPane = new JScrollPane(editorPane,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(4);

        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(statusLabel, BorderLayout.NORTH);

        inputField = new JTextArea(5, 20);
        inputField.setLineWrap(true);
        inputField.setWrapStyleWord(true);
        inputField.setBorder(new EmptyBorder(5, 5, 5, 5));

        JScrollPane inputScrollPane = new JScrollPane(inputField);
        inputScrollPane.setBorder(new RoundedBorder(10, ThemeUtils.getBorderColor(HopLa.montoyaApi)));

        JButton sendButton = new JButton(BUTTON_TEXT_SEND);
        sendButton.setBackground(ThemeUtils.getButtonBackgroundColor(HopLa.montoyaApi));
        sendButton.setForeground(ThemeUtils.getButtonForegroundColor(HopLa.montoyaApi));
        sendButton.setFocusPainted(false);
        sendButton.setBorder(new RoundedBorder(10, ThemeUtils.getButtonBackgroundColor(HopLa.montoyaApi)));
        sendButton.setOpaque(true);

        inputPanel.add(inputScrollPane, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown()) {
                    inputField.insert("\n", inputField.getCaretPosition());
                    e.consume();
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (sendButton.getText().equals(BUTTON_TEXT_SEND)) {
                        sendButton.doClick();
                    }
                    e.consume();
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    AIChats.Chat chat = getCurrentChat();
                    inputField.setText(chat.getLastUserMessage().getContent());
                    e.consume();
                }
            }
        });

        panel.add(inputPanel, BorderLayout.SOUTH);

        JPanel historyPanel = new JPanel();
        historyPanel.setLayout(new BoxLayout(historyPanel, BoxLayout.Y_AXIS));
        historyPanel.setPreferredSize(new Dimension(200, 500));
        historyPanel.setMinimumSize(new Dimension(100, 0));
        historyPanel.setMaximumSize(new Dimension(200, Integer.MAX_VALUE));

        JButton buttonNewChat = new JButton("New chat");
        buttonNewChat.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonNewChat.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttonNewChat.getPreferredSize().height));
        buttonNewChat.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chats.getChats().add(new AIChats.Chat(LocalDateTime.now().format(dateFormatter), new ArrayList<>()));
                loadChatList();
                sendButton.setText(BUTTON_TEXT_SEND);
                statusLabel.setText("");
            }
        });
        historyPanel.add(buttonNewChat);

        DefaultListModel<String> listModel = new DefaultListModel<>();

        chatsList = new JList<>(listModel);
        chatsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chatsList.setLayoutOrientation(JList.VERTICAL);
        chatsList.setFixedCellWidth(200);
        historyPanel.add(chatsList);
        loadChatList();

        final int[] clickedItem = new int[1];
        JPopupMenu chatContextMenu = new JPopupMenu();
        chatContextMenu.add(new JMenuItem(new AbstractAction("Delete chat") {
            public void actionPerformed(ActionEvent e) {
                int confirm = JOptionPane.showConfirmDialog(
                        frame,
                        "Delete ?",
                        null,
                        JOptionPane.YES_NO_OPTION
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    chats.getChats().remove(chatsList.getModel().getSize() - 1 - clickedItem[0]);
                    loadChatList();
                }

            }
        }));

        chatsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    int index = chatsList.locationToIndex(e.getPoint());
                    if (index != -1) {
                        int chatIndex = chatsList.getModel().getSize() - 1 - index;
                        loadChat(chats.getChats().get(chatIndex));

                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    show(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    show(e);
                }
            }

            private void show(MouseEvent e) {
                clickedItem[0] = chatsList.getSelectedIndex();
                chatContextMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        JScrollPane historyScroll = new JScrollPane(historyPanel);
        historyScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        panel.setPreferredSize(new Dimension(1100, 700));
        frame.add(historyPanel, BorderLayout.WEST);
        frame.add(panel, BorderLayout.CENTER);
        inputField.setText(input);

        ActionListener sendAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (sendButton.getText().equals(BUTTON_CANCEL_SEND) && aiProvider != null) {
                    aiProvider.cancelCurrentChatRequest();
                    sendButton.setText(BUTTON_TEXT_SEND);
                    statusLabel.setText("Cancelled");
                    return;
                }

                String userInput = inputField.getText().trim();
                if (!userInput.isEmpty()) {

                    userInput = userInput.replace(REQUEST_PLACEHOLDER, getRequest(messageEditor));
                    userInput = userInput.replace(RESPONSE_PLACEHOLDER, getResponse(messageEditor));
                    AIChats.Chat current = getCurrentChat();
                    String notes = current.getNotes() == null ? "" : current.getNotes();
                    userInput = userInput.replace(NOTES_PLACEHOLDER, notes);

                    inputField.setText("");
                    statusLabel.setText("Thinking...");

                    AIChats.Chat chat = getCurrentChat();
                    AIChats.Message message = new AIChats.Message(
                            AIChats.MessageRole.USER,
                            userInput
                    );
                    chat.addMessage(message);
                    loadChat(chat);
                    chats.save();
                    sendButton.setText(BUTTON_CANCEL_SEND);

                    try {
                        aiProvider = aiConfiguration.getProvider(currentProvider);
                        AIChats.Message answer = new AIChats.Message(
                                AIChats.MessageRole.ASSISTANT,
                                ""
                        );
                        chat.addMessage(answer);

                        aiProvider.chat(chat, new AIProvider.StreamingCallback() {
                            @Override
                            public void onData(String chunk) {
                                if (!chunk.isEmpty()) {
                                    chat.getLastMessage().appendContent(chunk);
                                    long currentTime = System.currentTimeMillis();
                                    if (currentTime - lastUiUpdate > 100) {
                                        lastUiUpdate = currentTime;
                                        CompletableFuture.supplyAsync(() -> buildChatHtml(chat))
                                                .thenAccept(html -> SwingUtilities.invokeLater(() -> {
                                            JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
                                            boolean atBottom = verticalBar.getValue() + verticalBar.getVisibleAmount() >= verticalBar.getMaximum() + 70;

                                            editorPane.setText(html);
                                            if (!atBottom) {
                                                editorPane.setCaretPosition(editorPane.getDocument().getLength());
                                            }
                                        }));
                                    }

                                }
                            }

                            @Override
                            public void onDone() {
                                chats.save();
                                SwingUtilities.invokeLater(() -> {
                                    loadChat(chat);
                                    statusLabel.setText("");
                                    sendButton.setText(BUTTON_TEXT_SEND);
                                    generateTitleAsync(chat);
                                });
                            }

                            @Override
                            public void onError(String error) {
                                SwingUtilities.invokeLater(() -> {
                                    statusLabel.setText(error);
                                    sendButton.setText(BUTTON_TEXT_SEND);
                                });
                            }
                        });
                    } catch (Exception exc) {
                        alert("AI chat error: " + exc.getMessage());
                        HopLa.montoyaApi.logging().logToError("AI chat error: " + exc.getMessage());
                    }

                }
            }
        };

        sendButton.addActionListener(sendAction);

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton buttonRequest = new JButton(REQUEST_PLACEHOLDER);
        buttonRequest.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                insertTextTextArea(REQUEST_PLACEHOLDER);
            }
        });
        buttonPanel.add(buttonRequest);

        JButton buttonResponse = new JButton(RESPONSE_PLACEHOLDER);
        buttonResponse.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                insertTextTextArea(RESPONSE_PLACEHOLDER);

            }
        });
        buttonPanel.add(buttonResponse);

        JButton buttonNotesToken = new JButton(NOTES_PLACEHOLDER);
        buttonNotesToken.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                insertTextTextArea(NOTES_PLACEHOLDER);
            }
        });
        buttonPanel.add(buttonNotesToken);

        JComboBox<LLMConfig.Prompt> selectBox = new JComboBox<>(aiConfiguration.getPrompts().toArray(new LLMConfig.Prompt[0]));
        selectBox.addActionListener(e -> {
            int idx = selectBox.getSelectedIndex();
            if (idx != -1) {
                insertTextTextArea(aiConfiguration.getPrompts().get(idx).content);

            }
        });

        buttonPanel.add(selectBox);

        JButton notesButton = new JButton("Notes");
        notesButton.addActionListener(e -> {
            AIChats.Chat chat = getCurrentChat();
            JTextArea notesArea = new JTextArea(chat.getNotes() == null ? "" : chat.getNotes(), 12, 40);
            notesArea.setLineWrap(true);
            notesArea.setWrapStyleWord(true);
            JPanel content = new JPanel(new BorderLayout());
            content.add(new JScrollPane(notesArea), BorderLayout.CENTER);
            JButton saveButton = new JButton("Save");
            saveButton.addActionListener(ev -> {
                chat.setNotes(notesArea.getText());
                chats.save();
                JDialog dlg = (JDialog) SwingUtilities.getWindowAncestor(saveButton);
                if (dlg != null) {
                    CenteredModal.closeWithAnimation(dlg);
                }
            });
            content.add(saveButton, BorderLayout.SOUTH);
            CenteredModal.showDialog(content, "Chat Notes");
        });
        buttonPanel.add(notesButton);

        if (Constants.EXTERNAL_AI) {
            java.util.List<AIProviderType> enabledProviders = aiConfiguration.getEnabledProviders();

            JComboBox<AIProviderType> aiProviderSelectBox = new JComboBox<>(enabledProviders.toArray(new AIProviderType[0]));

            aiProviderSelectBox.addActionListener(e -> {
                AIProviderType selectedProvider = (AIProviderType) aiProviderSelectBox.getSelectedItem();
                if (selectedProvider != null) {
                    currentProvider = selectedProvider;
                    if (DEBUG_AI) {
                        HopLa.montoyaApi.logging().logToOutput("Provider selected:" + selectedProvider);
                    }
                }
            });

            AIProvider p = aiConfiguration.getChatProvider();
            if (p != null) {
                aiProviderSelectBox.setSelectedItem(p.type);
            }
            buttonPanel.add(aiProviderSelectBox);
        }

        bottomBar.add(buttonPanel, BorderLayout.WEST);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton hideButton = new JButton("Hide");
        hideButton.addActionListener(e -> frame.setVisible(false));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> {
            if (aiProvider != null) {
                aiProvider.cancelCurrentChatRequest();
            }
            frame.dispose();
        });
        rightPanel.add(hideButton);
        rightPanel.add(closeButton);
        bottomBar.add(rightPanel, BorderLayout.EAST);
        inputPanel.add(bottomBar, BorderLayout.SOUTH);

        frame.pack();
        frame.setVisible(true);
        SwingUtilities.invokeLater(() -> inputField.requestFocusInWindow());
    }

    private AIChats.Chat getCurrentChat() {
        return chats.getChats().get(chatsList.getModel().getSize() - 1 - chatsList.getSelectedIndex());
    }

    private void loadChatList() {
        if (chats.getChats().isEmpty()) {
            chats.getChats().add(new AIChats.Chat(LocalDateTime.now().format(dateFormatter), new ArrayList<>()));
            chats.save();
        }
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (AIChats.Chat item : chats.getChats().reversed()) {
            String name = (item.getTitle() != null && !item.getTitle().isBlank()) ? item.getTitle() : item.timestamp;
            listModel.addElement(name);
        }

        chatsList.setModel(listModel);

        if (!listModel.isEmpty()) {
            chatsList.setSelectedIndex(0);
            loadChat(chats.getChats().getLast());
        }
    }

    private void loadChat(AIChats.Chat chat) {
        String m = buildChatHtml(chat);

        if (DEBUG_AI) {
            HopLa.montoyaApi.logging().logToOutput("AI chat html: " + m);

        }

        JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
        boolean atBottom = verticalBar.getValue() + verticalBar.getVisibleAmount() >= verticalBar.getMaximum() + 70;

        editorPane.setText(m);
        if (!atBottom) {
            editorPane.setCaretPosition(editorPane.getDocument().getLength());
        }

    }

    private String buildChatHtml(AIChats.Chat chat) {
        StringBuilder m = new StringBuilder();
        m.append("<div class=\"chat-container\">");
        if (chat.getMessages().isEmpty()) {
            m.append("<div class=\"intro\">Hello! 👋 How’s your day going? Let me know if there’s anything I can help you with.</div>");
        }
        for (AIChats.Message message : chat.getMessages()) {
            String html = renderMarkdownToHtml(message.getContent());
            m.append("<div class=\"").append(message.getRole().toString().toLowerCase()).append("\">")
                    .append("<span class=\"role\">").append(message.getRole().toString()).append("</span>")
                    .append(html)
                    .append("</div>");
        }
        m.append("</div>");
        return m.toString();
    }

    private void generateTitleAsync(AIChats.Chat chat) {
        if (chat.getMessages().isEmpty()) {
            return;
        }

        // Use up to the last 6 messages to generate the title
        java.util.List<AIChats.Message> messages = chat.getMessages();
        int start = Math.max(0, messages.size() - 6);
        StringBuilder conversation = new StringBuilder();
        for (int i = start; i < messages.size(); i++) {
            AIChats.Message msg = messages.get(i);
            conversation.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }
        String base = conversation.toString();

        String fallback = "New Chat";
        AIChats.Message lastUser = chat.getLastUserMessage();
        if (lastUser != null && lastUser.getContent() != null && !lastUser.getContent().isBlank()) {
            fallback = sanitizeTitle(lastUser.getContent());
        }

        if (!EXTERNAL_AI) {
            chat.setTitle(fallback);
            chats.save();
            loadChatList();
            return;
        }
        try {
            AIProvider p = aiConfiguration.getChatProvider();
            String prompt = "Generate a concise, meaningful chat title (3-6 words) based on this conversation summary. Return only the title without punctuation: \n" + base;
            StringBuilder sb = new StringBuilder();
            String finalFallback = fallback;
            p.instruct(prompt, new AIProvider.StreamingCallback() {
                @Override
                public void onData(String chunk) {
                    sb.append(chunk);
                }

                @Override
                public void onDone() {
                    String title = sanitizeTitle(sb.toString());
                    chat.setTitle(title.isBlank() ? finalFallback : title);
                    chats.save();
                    SwingUtilities.invokeLater(() -> loadChatList());
                }

                @Override
                public void onError(String error) {
                    chat.setTitle(finalFallback);
                    chats.save();
                    SwingUtilities.invokeLater(() -> loadChatList());
                }
            });
        } catch (Exception e) {
            chat.setTitle(fallback);
            chats.save();
            loadChatList();
        }
    }

    private String sanitizeTitle(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\n", " ").replaceAll("[\"'`]+", "").trim();
        if (t.length() > 60) {
            t = t.substring(0, 60).trim();
        }
        return t;
    }

    private String renderMarkdownToHtml(String markdown) {
        Node document = parser.parse(markdown);
        String body = renderer.render(document);
        if (DEBUG_AI) {
            HopLa.montoyaApi.logging().logToError("AI chat html: " + body);
        }
        return enhanceCodeBlocks(body);
    }

    private String enhanceCodeBlocks(String html) {
        String patternWithLang = "<pre><code\\s+class=\\\"language-([a-zA-Z0-9_\\-]+)\\\">([\\s\\S]*?)</code></pre>";
        String patternNoLang = "<pre><code>([\\s\\S]*?)</code></pre>";

        java.util.regex.Pattern pWith = java.util.regex.Pattern.compile(patternWithLang, java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher mWith = pWith.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (mWith.find()) {
            String lang = mWith.group(1);
            String content = mWith.group(2);
            String replacement = "<div class=\"code-block\"><div class=\"code-header\"><span class=\"lang-label\">" + lang
                    + "</span></div><pre class=\"code-scroll\"><code class=\"language-" + lang + "\">" + content
                    + "</code></pre></div>";
            mWith.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        mWith.appendTail(sb);
        html = sb.toString();

        java.util.regex.Pattern pNo = java.util.regex.Pattern.compile(patternNoLang, java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher mNo = pNo.matcher(html);
        sb = new StringBuffer();
        while (mNo.find()) {
            String content = mNo.group(1);
            String replacement = "<div class=\"code-block\"><div class=\"code-header\"><span class=\"lang-label\">code</span></div>"
                    + "<pre class=\"code-scroll\"><code>" + content + "</code></pre></div>";
            mNo.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        mNo.appendTail(sb);
        return sb.toString();
    }

    private void insertTextTextArea(String text) {
        int start = inputField.getCaretPosition();
        int end = start;

        if (inputField.getSelectedText() != null && !inputField.getSelectedText().isEmpty()) {
            start = inputField.getSelectionStart();
            end = inputField.getSelectionEnd();
        }
        Document doc = inputField.getDocument();
        try {
            doc.remove(start, end - start);
            doc.insertString(start, text, null);
        } catch (BadLocationException exc) {
            HopLa.montoyaApi.logging().logToError("AI chat insertion error: " + exc.getMessage());
        }
        inputField.setCaretPosition(start + text.length());
    }

    public void dispose() {
        if (frame != null) {
            frame.dispose();
        }
    }

    private static class RoundedBorder extends AbstractBorder {

        private final int radius;
        private final Color color;

        RoundedBorder(int radius, Color color) {
            this.radius = radius;
            this.color = color;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(this.radius + 1, this.radius + 1, this.radius + 2, this.radius);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.left = insets.top = this.radius + 1;
            insets.right = insets.bottom = this.radius + 2;
            return insets;
        }
    }

}
