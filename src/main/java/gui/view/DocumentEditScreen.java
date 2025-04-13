package gui.view;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.UndoManager;

import cm.CMClientApp;

public class DocumentEditScreen extends JPanel {
    private CMClientApp clientApp;
    private JTextArea textArea;
    private UndoManager undoManager;
    // 플래그: 프로그램에 의한 텍스트 업데이트 시 이벤트 무시
    private boolean ignoreDocEvents = false;

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

        // 문서 입력 이벤트 감지를 위한 DocumentListener 추가
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                sendEvent();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                sendEvent();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                sendEvent();
            }

            private void sendEvent() {
                if (ignoreDocEvents) return;
                String content = textArea.getText();
                clientApp.sendTextUpdate(content);
            }
        });

        JScrollPane scrollPane = new JScrollPane(textArea);
        add(scrollPane, BorderLayout.CENTER);
    }

    // 수신한 문서 내용 업데이트 (원격 업데이트에 의한 이벤트라면 이벤트 전송 방지)
    public void updateTextContent(String content) {
        SwingUtilities.invokeLater(() -> {
            ignoreDocEvents = true;
            textArea.setText(content);
            if (!clientApp.isDocOpen()) {
                textArea.setEditable(true);
                clientApp.setDocOpen(true);
            }
            ignoreDocEvents = false;
        });
    }

    // 기본 버전: 편집 불가능한 상태로 초기화
    public void resetDocumentView() {
        resetDocumentView(false);
    }

    // 편집 가능 여부를 지정하여 텍스트 영역을 초기화
    public void resetDocumentView(boolean enableEditing) {
        SwingUtilities.invokeLater(() -> {
            ignoreDocEvents = true;
            textArea.setText("");
            textArea.setEditable(enableEditing);
            clientApp.setDocOpen(enableEditing);
            ignoreDocEvents = false;
        });
    }

    public JTextArea getTextArea() {
        return textArea;
    }
}
