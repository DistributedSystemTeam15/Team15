package gui.view;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.undo.UndoManager;

import cm.CMClientApp;
import cm.lock.Interval;

public class DocumentEditScreen extends JPanel {

    /* 필드 */
    private final CMClientApp core;
    private final JTextArea textArea = new JTextArea(25, 50);
    private final UndoManager undo = new UndoManager();

    /* 잠금 관리 */
    private final Map<Interval, Object> foreignLocks = new HashMap<>();      // 서버에서 통보받은 잠금 + 하이라이트 태그
    private Interval myLock = null;                 // 내가 보유한 잠금(단일)

    /* 내부 상태 플래그 */
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
        add(new JScrollPane(textArea), BorderLayout.CENTER);

        /* Ctrl + Z */
        textArea.getDocument().addUndoableEditListener(e -> undo.addEdit(e.getEdit()));
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control Z"), "undo");
        textArea.getActionMap().put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undo.canUndo()) undo.undo();
            }
        });

        /* ---- 문서 편집 → Core 전달 ---- */
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            private void handleChange() {
                if (ignore) return;

                Interval edit = currentEditInterval();

                /* 1) 다른 사람의 잠금 영역인지 검사 */
                if (foreignLocks.keySet().stream().anyMatch(iv -> iv.overlaps(edit))) {
                    Toolkit.getDefaultToolkit().beep();
                    return;
                }

                /* 2) 잠금이 없거나 영역이 달라지면 새로 요청 (optimistic) */
                if (myLock == null || !myLock.equals(edit)) {
                    myLock = edit;
                    core.requestIntervalLock(edit.start(), edit.end());
                }

                /* 3) 실내용 편집 내용 서버 전송 */
                core.editCurrentDocument(textArea.getText());
                firePropertyChange("localEdit", false, true);
            }

            public void insertUpdate(DocumentEvent e) {
                handleChange();
            }

            public void removeUpdate(DocumentEvent e) {
                handleChange();
            }

            public void changedUpdate(DocumentEvent e) {
                handleChange();
            }
        });
    }

    /**
     * 서버에서 내려온 전체 문서 내용 갱신 + caret 보정
     */
    public void updateTextContent(String newText) {
        SwingUtilities.invokeLater(() -> {
            ignore = true;

            /* 0) 기존 상태 보관 */
            String oldText = textArea.getText();
            int caretPos = textArea.getCaretPosition();
            int selStart = textArea.getSelectionStart();
            int selEnd = textArea.getSelectionEnd();

            /* 1) old ↔ new 첫 번째 차이 지점과 길이 차이 계산 */
            int diffIdx = 0;
            int minLen = Math.min(oldText.length(), newText.length());
            while (diffIdx < minLen && oldText.charAt(diffIdx) == newText.charAt(diffIdx))
                diffIdx++;

            // 변경이 caret 앞쪽에서 일어난 경우에만 보정
            int delta = newText.length() - oldText.length();
            if (diffIdx <= caretPos) caretPos += delta;
            if (diffIdx <= selStart) selStart += delta;
            if (diffIdx <= selEnd) selEnd += delta;

            /* 2) 텍스트 치환 */
            textArea.setText(newText);

            /* 3) 보정된 위치로 복원 (경계를 넘어가면 마지막 글자에 맞춤) */
            int max = newText.length();
            caretPos = Math.max(0, Math.min(caretPos, max));
            selStart = Math.max(0, Math.min(selStart, max));
            selEnd = Math.max(0, Math.min(selEnd, max));

            textArea.setCaretPosition(caretPos);
            textArea.select(selStart, selEnd);

            if (!core.isDocOpen()) {
                textArea.setEditable(true);
                core.setDocOpen(true);
            }
            ignore = false;
        });
    }

    public void resetDocumentView(boolean editable) {
        SwingUtilities.invokeLater(() -> {
            ignore = true;
            textArea.setText("");
            textArea.setEditable(editable);
            clearAllLocks();
            core.setDocOpen(editable);
            ignore = false;
        });
    }

    public void resetDocumentView() {
        resetDocumentView(false);
    }

    /* interval-lock  콜백 (GuiCallback → 여기로 전달) */
    /**
     * LOCK_ACK
     */
    public void handleLockAck(int start, int end, boolean ok) {
        Interval target = new Interval(start, end, "");
        if (!target.equals(myLock)) return;            // 다른 요청이면 무시
        if (!ok) {                                     // 거절 → 내 잠금 해제
            Toolkit.getDefaultToolkit().beep();
            myLock = null;
        } /* 성공이면 별도 처리 필요 없음 – 이미 편집 중 */
    }

    /**
     * LOCK_NOTIFY : owner=="" 이면 해제, 아니면 설정
     */
    public void handleLockNotify(int start, int end, String owner) {
        if (owner.isEmpty()) {           // 🔓 해제
            removeForeignLock(start, end);
            return;
        }
        /* 내 잠금은 foreign 아님 */
        String me = core.getStub().getMyself().getName();
        if (owner.equals(me)) return;

        Interval iv = new Interval(start, end, owner);
        if (foreignLocks.containsKey(iv)) return;        // 이미 있음

        Object tag = addHighlight(iv, new Color(244, 244, 244));
        foreignLocks.put(iv, tag);
    }

    /* 내부 유틸 */
    private Interval currentEditInterval() {
        int s = textArea.getSelectionStart();
        int e = textArea.getSelectionEnd();
        if (s == e) {                    // 드래그 선택 없으면 현재 커서 문자
            s = e = textArea.getCaretPosition();
        }
        return new Interval(s, e);
    }

    /* -------- 하이라이트 유틸 -------- */
    private final Highlighter hl = textArea.getHighlighter();

    private Object addHighlight(Interval iv, Color c) {
        try {
            int docLen = textArea.getDocument().getLength();
            int st = Math.min(iv.start(), docLen);
            int ed = Math.min(iv.end() + 1, docLen);      // end 포함 → +1
            return hl.addHighlight(st, ed,
                    new DefaultHighlighter.DefaultHighlightPainter(c));
        } catch (BadLocationException ex) {
            return null;
        }
    }

    private void removeForeignLock(int start, int end) {
        foreignLocks.entrySet().removeIf(ent -> {
            Interval iv = ent.getKey();
            if (iv.start() == start && iv.end() == end) {
                hl.removeHighlight(ent.getValue());
                return true;
            }
            return false;
        });
    }

    public void clearAllLocks() {
        hl.removeAllHighlights();
        foreignLocks.clear();
        myLock = null;
    }
}
