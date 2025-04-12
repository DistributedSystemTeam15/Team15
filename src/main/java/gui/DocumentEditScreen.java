package gui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import javax.swing.*;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.UndoManager;

import cm.CMClientApp;

public class DocumentEditScreen extends JPanel {
    private CMClientApp clientApp;
    private JTextArea textArea;
    private UndoManager undoManager;

    public DocumentEditScreen(CMClientApp app) {
        this.clientApp = app;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        textArea = new JTextArea(25, 50);
        undoManager = new UndoManager();
        textArea.getDocument().addUndoableEditListener(new UndoableEditListener() {
            @Override
            public void undoableEditHappened(UndoableEditEvent e) {
                undoManager.addEdit(e.getEdit());
            }
        });
        // Ctrl+Z 단축키 등록
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control Z"), "Undo");
        textArea.getActionMap().put("Undo", new AbstractAction("Undo") {
            public void actionPerformed(ActionEvent evt) {
                if (undoManager.canUndo()) {
                    undoManager.undo();
                }
            }
        });
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        add(scrollPane, BorderLayout.CENTER);
    }

    // 수신한 문서 내용 업데이트
    public void updateTextContent(String content) {
        SwingUtilities.invokeLater(() -> {
            textArea.setText(content);
            if (!clientApp.isDocOpen()) {
                textArea.setEditable(true);
                clientApp.setDocOpen(true);
            }
        });
    }

    // 문서 내용 리셋(예: 새 문서 또는 문서 선택 후)
    public void resetDocumentView() {
        SwingUtilities.invokeLater(() -> {
            textArea.setText("");
            textArea.setEditable(false);
            clientApp.setDocOpen(false);
        });
    }

    public JTextArea getTextArea() {
        return textArea;
    }
}
