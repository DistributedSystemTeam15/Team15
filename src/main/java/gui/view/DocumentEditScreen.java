package gui.view;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
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
    public void updateTextContent(String newText) {
        SwingUtilities.invokeLater(() -> {
            ignore = true;

            /* 0) 기존 상태 보관 */
            String oldText   = textArea.getText();
            int    caretPos  = textArea.getCaretPosition();
            int    selStart  = textArea.getSelectionStart();
            int    selEnd    = textArea.getSelectionEnd();

            /* 1) old ↔ new 첫 번째 차이 지점과 길이 차이 계산 */
            int diffIdx = 0;
            int minLen  = Math.min(oldText.length(), newText.length());
            while (diffIdx < minLen && oldText.charAt(diffIdx) == newText.charAt(diffIdx))
                diffIdx++;

            // 변경이 caret 앞쪽에서 일어난 경우에만 보정
            int delta = newText.length() - oldText.length();
            if (diffIdx <= caretPos)       caretPos += delta;
            if (diffIdx <= selStart)       selStart += delta;
            if (diffIdx <= selEnd)         selEnd   += delta;

            /* 2) 텍스트 치환 */
            textArea.setText(newText);

            /* 3) 보정된 위치로 복원 (경계를 넘어가면 마지막 글자에 맞춤) */
            int max = newText.length();
            caretPos = Math.max(0, Math.min(caretPos, max));
            selStart = Math.max(0, Math.min(selStart, max));
            selEnd   = Math.max(0, Math.min(selEnd,   max));

            textArea.setCaretPosition(caretPos);
            textArea.select(selStart, selEnd);

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
