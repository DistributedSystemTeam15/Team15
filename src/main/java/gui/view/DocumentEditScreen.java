package gui.view;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.undo.UndoManager;

import cm.CMClientApp;

public class DocumentEditScreen extends JPanel {
    private final CMClientApp core;
    private final JTextArea textArea = new JTextArea(25, 50);
    private final UndoManager undo = new UndoManager();

    // 플래그: 프로그램에 의한 텍스트 업데이트 시 이벤트 무시
    private boolean ignore = false;

    public DocumentEditScreen(CMClientApp core) {
        this.core = core;
        buildUI();
    }

    public JTextArea getTextArea() {
        return textArea;
    }

    private void buildUI() {
        setLayout(new BorderLayout());
        textArea.getDocument().addUndoableEditListener(e -> undo.addEdit(e.getEdit()));

        /* Ctrl + Z */
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control Z"), "undo");
        textArea.getActionMap().put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undo.canUndo()) undo.undo();
            }
        });

        /* ---- 문서 편집 → Core 전달 ---- */
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                if (ignore) return;
                core.editCurrentDocument(textArea.getText());

                /* ✅ 로컬에서 문서가 변경됐음을 알림 */
                firePropertyChange("localEdit", false, true);
            }

            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            public void changedUpdate(DocumentEvent e) {
                changed();
            }
        });
        add(new JScrollPane(textArea), BorderLayout.CENTER);
    }

    /* ---------------------------------------------------------------- */
    public void updateTextContent(String content) {
        SwingUtilities.invokeLater(() -> {
            ignore = true;
            textArea.setText(content);
            if (!core.isDocOpen()) {
                textArea.setEditable(true);
                core.setDocOpen(true);
            }
            ignore = false;
        });
    }

    public void resetDocumentView() {
        resetDocumentView(false);
    }

    public void resetDocumentView(boolean editable) {
        SwingUtilities.invokeLater(() -> {
            ignore = true;
            textArea.setText("");
            textArea.setEditable(editable);
            core.setDocOpen(editable);
            ignore = false;
        });
    }
}
