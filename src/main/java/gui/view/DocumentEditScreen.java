package gui.view;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.Timer;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.undo.UndoManager;

import cm.CMClientApp;

public class DocumentEditScreen extends JPanel {
    private final CMClientApp core;
    private final JTextArea textArea = new JTextArea(25, 50);
    private final UndoManager undo = new UndoManager();

    private final Map<Integer,Object> foreignLines = new HashMap<>();
    private final Set<Integer> myLines = new HashSet<>();
    private static final Highlighter.HighlightPainter PAINT_MY =
            new DefaultHighlighter.DefaultHighlightPainter(new Color(0,180,0,80));
    private static final Highlighter.HighlightPainter PAINT_FOREIGN =
            new DefaultHighlighter.DefaultHighlightPainter(new Color(200,0,0,60));

    /* selection 변경 → 라인 계산 */
    private void handleSelectionChange() {
        try {
            int sLine = textArea.getLineOfOffset(textArea.getSelectionStart());
            int rawEnd = textArea.getSelectionEnd();
            int eLine  = textArea.getLineOfOffset(rawEnd > 0 ? rawEnd - 1 : 0);
            if (sLine==eLine && myLines.contains(sLine)) return; // 그대로
            /* release 기존 */
            if (!myLines.isEmpty())
                core.releaseLineLock(Collections.min(myLines), Collections.max(myLines));
            core.requestLineLock(sLine,eLine);
        } catch (BadLocationException ignored) {}
    }

    /* ----- 서버 ACK 수신 ----- */
    public void handleLineLockAck(int s,int e,boolean ok){
        if(!ok){ Toolkit.getDefaultToolkit().beep(); return; }
        clearMyLines(false);
        for(int ln=s; ln<=e; ln++){
            try{
                int lo=textArea.getLineStartOffset(ln);
                int hi=textArea.getLineEndOffset(ln);
                Object tag=textArea.getHighlighter().addHighlight(lo,hi,PAINT_MY);
                foreignLines.put(ln,tag);   // 같은 맵 재사용
                myLines.add(ln);
            }catch(BadLocationException ignored){}
        }
        restartIdleTimer();
    }

    /* ----- NOTIFY 수신 ----- */
    public void handleLineLockNotify(int s,int e,String owner){
        SwingUtilities.invokeLater(() -> {
            for(int ln=s; ln<=e; ln++){
                if(owner.isEmpty()){                // 해제
                    removeLineHighlight(ln);
                }else if(!owner.equals(core.getStub().getMyself().getName())){
                    try{
                        int lo=textArea.getLineStartOffset(ln);
                        int hi=textArea.getLineEndOffset(ln);
                        Object tag=textArea.getHighlighter().addHighlight(lo,hi,PAINT_FOREIGN);
                        replaceHighlight(ln,tag);
                    }catch(BadLocationException ignored){}
                }
            }
        });
    }

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

        // 캐럿(커서) 이동 시 락 요청
        textArea.addCaretListener(e -> handleSelectionChange());

        // 마우스 드래그 뒤 버튼 해제 시 락 요청
        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                handleSelectionChange();
            }
        });

        /* ---- 문서 편집 → Core 전달 ---- */
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                if (ignore) return;

                try{
                    int curLn=textArea.getLineOfOffset(textArea.getCaretPosition());
                    if(!myLines.contains(curLn)){
                        Toolkit.getDefaultToolkit().beep(); return;
                    }
                }catch(BadLocationException ignored){}
                System.out.println("[DEBUG] Local edit detected; text length=" + textArea.getText().length());

                core.editCurrentDocument(textArea.getText());
                firePropertyChange("localEdit", false, true);
                restartIdleTimer();
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
            System.out.println("[DEBUG] updating UI textArea to new content (oldText len=" +
                    oldText.length() + ", newText len=" + newText.length() + ")");
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

            // 다시 모든 하이라이트를 지우고
            Highlighter hl = textArea.getHighlighter();
            hl.removeAllHighlights();

            // 내 라인 락(초록) 재적용
            for (int ln : myLines) {
                try {
                    int lo = textArea.getLineStartOffset(ln);
                    int hi = textArea.getLineEndOffset(ln);
                    Object tag = hl.addHighlight(lo, hi, PAINT_MY);
                    // replace the old tag so clearMyLines / removeLineHighlight still works
                    foreignLines.put(ln, tag);
                } catch (BadLocationException ignored) {}
            }

            // 타인 라인 락(빨강) 재적용
            // note: foreignLines.keySet() still holds all previously‐locked lines
            for (int ln : new ArrayList<>(foreignLines.keySet())) {
                if (myLines.contains(ln)) continue;
                try {
                    int lo = textArea.getLineStartOffset(ln);
                    int hi = textArea.getLineEndOffset(ln);
                    Object tag = hl.addHighlight(lo, hi, PAINT_FOREIGN);
                    foreignLines.put(ln, tag);
                } catch (BadLocationException ignored) {}
            }

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

    /* 유틸 */
    private void replaceHighlight(int ln,Object tag){
        Object old=foreignLines.put(ln,tag);
        if(old!=null) textArea.getHighlighter().removeHighlight(old);
    }
    private void removeLineHighlight(int ln){
        Object tag=foreignLines.remove(ln);
        if(tag!=null) textArea.getHighlighter().removeHighlight(tag);
        myLines.remove(ln);
    }
    private void clearMyLines(boolean repaint){
        for(int ln:myLines) removeLineHighlight(ln);
        myLines.clear();
        if(repaint) textArea.repaint();
    }

    /* 5 초 idle-timer (필드) */
    private final Timer idleTimer=new Timer(true);
    private TimerTask idleTask;
    private void restartIdleTimer(){
        if(idleTask!=null) idleTask.cancel();
        idleTask=new TimerTask(){
            @Override public void run(){
                if(!myLines.isEmpty())
                    core.releaseLineLock(Collections.min(myLines),Collections.max(myLines));
            }
        };
        idleTimer.schedule(idleTask,5000);
    }
}
