package com.hopla;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;

public class PayloadMenu {

    private static final int MARGIN_PAYLOAD_MENU = 20;
    private static final int PAYLOAD_MENU_WIDTH = 300;
    private static final int PAYLOAD_MENU_HEIGHT = 400;
    private final PayloadManager payloadManager;
    private final CommonMenu commonMenu;
    private JDialog dialog;

    public PayloadMenu(PayloadManager payloadManager, MontoyaApi api) {
        this.payloadManager = payloadManager;
        this.commonMenu = new CommonMenu(api);
    }

    public void show(MessageEditorHttpRequestResponse messageEditor, InputEvent event) {
        if (dialog != null && dialog.isVisible()) {
            dialog.dispose();
            dialog = null;
            return;
        }

        // Create the dialog
        Component source = (Component) event.getSource();
        dialog = new JDialog(SwingUtilities.getWindowAncestor(source), "Payloads", JDialog.ModalityType.MODELESS);
        dialog.setUndecorated(true);
        dialog.setAlwaysOnTop(true);
        dialog.setSize(PAYLOAD_MENU_WIDTH, PAYLOAD_MENU_HEIGHT);
        dialog.setLayout(new BorderLayout());

        // Styling
        Color bg = ThemeUtils.getBackgroundColor(HopLa.montoyaApi);
        Color fg = ThemeUtils.getForegroundColor(HopLa.montoyaApi);
        Color selBg = ThemeUtils.getSelectionBackgroundColor(HopLa.montoyaApi);
        Color selFg = ThemeUtils.getSelectionForegroundColor(HopLa.montoyaApi);

        dialog.getContentPane().setBackground(bg);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(selBg, 1));

        // Build the Tree Model
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");

        // 1. Payloads
        PayloadDefinition payloads = payloadManager.getPayloads();
        if (payloads.categories != null) {
            for (PayloadDefinition.Category cat : payloads.categories) {
                root.add(buildCategoryNode(cat));
            }
        }

        // 2. Custom Keywords
        JMenu customKeywordsMenu = HopLa.localPayloadsManager.buildMenu((payload) -> {
            insertPayloadAndClose(messageEditor, payload, event);
        });
        root.add(convertMenuToNode(customKeywordsMenu));

        // 3. Common Menu (Tools)
        DefaultMutableTreeNode toolsNode = new DefaultMutableTreeNode("Tools");
        for (Component c : this.commonMenu.buildMenu(messageEditor, event, () -> {
            if (dialog != null) {
                dialog.dispose();
            }
        })) {
            if (c instanceof JMenu) {
                toolsNode.add(convertMenuToNode((JMenu) c));
            } else if (c instanceof JMenuItem) {
                toolsNode.add(new DefaultMutableTreeNode(new MenuItemWrapper((JMenuItem) c)));
            }
        }
        root.add(toolsNode);

        // Create JTree
        JTree tree = new JTree(root);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setBackground(bg);
        tree.setForeground(fg);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        // Custom Renderer
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setBackgroundNonSelectionColor(bg);
        renderer.setTextNonSelectionColor(fg);
        renderer.setBackgroundSelectionColor(selBg);
        renderer.setTextSelectionColor(selFg);
        renderer.setBorderSelectionColor(selBg);
        tree.setCellRenderer(renderer);

        // Interaction
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = tree.getRowForLocation(e.getX(), e.getY());
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());

                if (row != -1 && path != null) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    if (node.isLeaf()) {
                        // Single click execution for leaves (Payloads / Actions)
                        activateNode(node, messageEditor, event);
                    } else {
                        // Single click toggle expansion for folders/categories
                        if (tree.isExpanded(path)) {
                            tree.collapsePath(path);
                        } else {
                            tree.expandPath(path);
                        }
                    }
                }
            }
        });

        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    TreePath path = tree.getSelectionPath();
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node.isLeaf()) {
                            activateNode(node, messageEditor, event);
                        } else {
                            if (tree.isExpanded(path)) {
                                tree.collapsePath(path);
                            } else {
                                tree.expandPath(path);
                            }
                        }
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dialog.dispose();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(bg);
        dialog.add(scrollPane, BorderLayout.CENTER);

        // Positioning
        Point mousePos = MouseInfo.getPointerInfo().getLocation();
        mousePos.x -= MARGIN_PAYLOAD_MENU;
        mousePos.y -= PAYLOAD_MENU_HEIGHT / 2;
        dialog.setLocation(mousePos);

        // Focus handling
        dialog.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                dialog.dispose();
            }
        });

        dialog.setVisible(true);
        tree.requestFocusInWindow();
    }

    private void activateNode(DefaultMutableTreeNode node, MessageEditorHttpRequestResponse messageEditor, InputEvent event) {
        Object userObject = node.getUserObject();

        if (userObject instanceof PayloadWrapper) {
            PayloadWrapper wrapper = (PayloadWrapper) userObject;
            insertPayloadAndClose(messageEditor, wrapper.payload.value, event);
        } else if (userObject instanceof MenuItemWrapper) {
            MenuItemWrapper wrapper = (MenuItemWrapper) userObject;
            wrapper.item.doClick(); // This triggers the action listener which handles closing
            if (dialog != null) {
                dialog.dispose();
            }
        }
    }

    private void insertPayloadAndClose(MessageEditorHttpRequestResponse messageEditor, String value, InputEvent event) {
        Utils.insertPayload(messageEditor, value, event);
        if (dialog != null) {
            dialog.dispose();
        }
    }

    private DefaultMutableTreeNode buildCategoryNode(PayloadDefinition.Category category) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(category.name);

        if (category.categories != null) {
            for (PayloadDefinition.Category sub : category.categories) {
                node.add(buildCategoryNode(sub));
            }
        }

        if (category.payloads != null) {
            for (PayloadDefinition.Payload payload : category.payloads) {
                node.add(new DefaultMutableTreeNode(new PayloadWrapper(payload)));
            }
        }
        return node;
    }

    private DefaultMutableTreeNode convertMenuToNode(JMenu menu) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(menu.getText());
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item instanceof JMenu) {
                node.add(convertMenuToNode((JMenu) item));
            } else if (item != null) {
                node.add(new DefaultMutableTreeNode(new MenuItemWrapper(item)));
            }
        }
        return node;
    }

    public void dispose() {
        if (dialog != null) {
            dialog.dispose();
        }
    }

    // Wrapper classes for Tree Nodes
    private static class PayloadWrapper {

        PayloadDefinition.Payload payload;

        public PayloadWrapper(PayloadDefinition.Payload payload) {
            this.payload = payload;
        }

        @Override
        public String toString() {
            String label = payload.value;
            if (payload.name != null && !payload.name.isEmpty()) {
                label = payload.name + ": " + payload.value;
            }
            if (label.length() > 80) {
                label = label.substring(0, 77) + "...";
            }
            return label;
        }
    }

    private static class MenuItemWrapper {

        JMenuItem item;

        public MenuItemWrapper(JMenuItem item) {
            this.item = item;
        }

        @Override
        public String toString() {
            return item.getText();
        }
    }
}
