package gui.view;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
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

    private final Map<Integer,Object> myLineTags      = new HashMap<>();
    private final Map<Integer,Object> foreignLineTags = new HashMap<>();
    private final Set<Integer> myLines = new HashSet<>();
    private final Set<Integer> lockedLines = new HashSet<>();

    private static final Highlighter.HighlightPainter PAINT_MY =
            new DefaultHighlighter.DefaultHighlightPainter(new Color(0,180,0,80));
    private static final Highlighter.HighlightPainter PAINT_FOREIGN =
            new DefaultHighlighter.DefaultHighlightPainter(new Color(200,0,0,60));

    // 마지막으로 요청했던 락 범위
    private int lastStart = -1, lastEnd = -1;
    private int lastCaretPosition = 0;

    /* selection 변경 → 라인 계산 */
    private void handleSelectionChange() {
        if (ignore) return;

        // 1) 원시 선택 오프셋
        int rawStart = textArea.getSelectionStart();
        int rawEnd   = textArea.getSelectionEnd();

        // sLine 계산 (예외 시 0으로)
        int sLine;
        try {
            sLine = textArea.getLineOfOffset(rawStart);
        } catch (BadLocationException ex) {
            sLine = 0;
        }

        // eLine 계산 (끝 offset -1 → 예외 시 sLine로)
        int endPos = rawEnd > 0 ? rawEnd - 1 : 0;
        int eLine;
        try {
            eLine = textArea.getLineOfOffset(endPos);
        } catch (BadLocationException ex) {
            eLine = sLine;
        }

        // 라인 번호를 [0, 마지막라인] 범위로 클램핑
        int maxLine = textArea.getLineCount() - 1;
        sLine = Math.max(0, Math.min(sLine, maxLine));
        eLine = Math.max(0, Math.min(eLine, maxLine));
        if (eLine < sLine) eLine = sLine;


        // 이미 잠금된 타인 라인 포함되면 선택 불가 (UI Feedback)
        for (int ln = sLine; ln <= eLine; ln++) {
            if (lockedLines.contains(ln) && !myLines.contains(ln)) {
                // 타인 락된 라인 포함 → 선택 불가
                Toolkit.getDefaultToolkit().beep();
                // 선택을 직전 내 락 영역으로 복원 (있으면)
                if (!myLines.isEmpty()) {
                    int prevStart = Collections.min(myLines);
                    int prevEnd   = Collections.max(myLines);
                    try {
                        int selLo = textArea.getLineStartOffset(prevStart);
                        int selHi = textArea.getLineEndOffset(prevEnd);
                        textArea.select(selLo, selHi);
                    } catch (BadLocationException ignored) {}
                }
                return;
            }
        }

        // 지난번과 같으면 무시
        if (sLine == lastStart && eLine == lastEnd) {
            return;
        }

        // 기존 내 락 해제
        if (!myLines.isEmpty()) {
            int relStart = Collections.min(myLines);
            int relEnd   = Collections.max(myLines);
            if (relStart != sLine || relEnd != eLine) {
                core.releaseLineLock(relStart, relEnd);
            }
        }

        // 새 라인 락 요청
        core.requestLineLock(sLine, eLine);

        // 요청 범위 기록
        lastStart = sLine;
        lastEnd   = eLine;
    }

    /* ----- 서버 ACK 수신 ----- */
    public void handleLineLockAck(int s,int e,boolean ok){
        // stale ACK 방지: 마지막 요청 범위와 다르면,
        // 서버에 쌓인 “out-of-order” 락을 즉시 해제하고 리턴
        if (s != lastStart || e != lastEnd) {
            if (ok) core.releaseLineLock(s, e);
            return;
        }

        if(!ok){ Toolkit.getDefaultToolkit().beep(); return; }

        Highlighter hl = textArea.getHighlighter();
        Set<Integer> previous = new HashSet<>(myLines);

        // 기존 녹색 태그 모두 제거
        for (int ln : previous) {
            Object oldTag = myLineTags.remove(ln);
            if (oldTag != null) hl.removeHighlight(oldTag);
            lockedLines.remove(ln);
        }
        myLines.clear();

        // 새로운 녹색 태그 추가
        for (int ln = s; ln <= e; ln++) {
            try {
                int lo = textArea.getLineStartOffset(ln);
                int hi = textArea.getLineEndOffset(ln);
                Object tag = hl.addHighlight(lo, hi, PAINT_MY);
                myLineTags.put(ln, tag);
                myLines.add(ln);
                lockedLines.add(ln);
            } catch (BadLocationException ignored) {}
        }
        // ack 성공 기준으로 lastStart/lastEnd 확정
        lastStart = s;
        lastEnd   = e;

        restartIdleTimer();
    }

    /* ----- NOTIFY 수신 ----- */
    public void handleLineLockNotify(int s,int e,String owner){
        SwingUtilities.invokeLater(() -> {
            Highlighter hl = textArea.getHighlighter();
            for(int ln=s; ln<=e; ln++){
                if(owner.isEmpty()){
                    // (1) 빨간 태그 제거
                    Object redTag = foreignLineTags.remove(ln);
                    if(redTag != null) hl.removeHighlight(redTag);
                    // (2) 초록 태그 제거
                    Object greenTag = myLineTags.remove(ln);
                    if(greenTag != null) hl.removeHighlight(greenTag);
                    // (3) 집합에서도 제거
                    myLines.remove(ln);
                    lockedLines.remove(ln);
                }else if(!owner.equals(core.getStub().getMyself().getName())){
                    // 갱신 전, 기존 빨간 태그 제거
                    Object oldTag = foreignLineTags.remove(ln);
                    if (oldTag != null) hl.removeHighlight(oldTag);
                    try{
                        int lo=textArea.getLineStartOffset(ln);
                        int hi=textArea.getLineEndOffset(ln);
                        Object tag=textArea.getHighlighter().addHighlight(lo,hi,PAINT_FOREIGN);
                        foreignLineTags.put(ln, tag);
                        lockedLines.add(ln);
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

    public int getLineAtCaret(JTextArea textArea) {
        int caretPosition = textArea.getCaretPosition();
        try {
            return textArea.getLineOfOffset(caretPosition);
        } catch (BadLocationException ex) {
            ex.printStackTrace();
            return -1; // 예외 발생 시
        }
    }

    private void buildUI() {
        setLayout(new BorderLayout());

        /* Ctrl + Z */
        textArea.getDocument().addUndoableEditListener(e -> undo.addEdit(e.getEdit()));
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control Z"), "undo");
        textArea.getActionMap().put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undo.canUndo()) undo.undo();
            }
        });

        // 캐럿(커서) 이동 시 락 요청
        //textArea.addCaretListener(e -> handleSelectionChange());
        textArea.addCaretListener(e -> {
            int caretPos = e.getDot();
            int line = getLineAtCaret(textArea);

            // 락된 라인일 경우 커서 위치 복원
            if (lockedLines.contains(line) && !myLines.contains(line)) {
                /*SwingUtilities.invokeLater(() -> {
                    textArea.setCaretPosition(lastCaretPosition); // 즉시 복원
                }*/
                textArea.setCaretPosition(lastCaretPosition); // 즉시 복원);
            } else {
                lastCaretPosition = caretPos; // 락 안된 라인일 경우에만 저장
                handleSelectionChange();
            }
        });


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
                    if(lockedLines.contains(curLn) && !myLines.contains(curLn)){
                        Toolkit.getDefaultToolkit().beep(); return;
                    }
                }catch(BadLocationException ignored){}
                System.out.println("[DEBUG] Local edit detected; text length=" + textArea.getText().length());

                core.editCurrentDocument(textArea.getText());
                firePropertyChange("localEdit", false, true);
                restartIdleTimer();
            }

            public void insertUpdate(DocumentEvent e){
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
//            clearAllLocks();

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

            // 녹색 락 재적용
            for (var entry : myLineTags.entrySet()) {
                int ln = entry.getKey();
                try {
                    int lo = textArea.getLineStartOffset(ln);
                    int hi = textArea.getLineEndOffset(ln);
                    Object newTag = hl.addHighlight(lo, hi, PAINT_MY);
                    entry.setValue(newTag);
                } catch (BadLocationException ignored) {}
            }

            // 빨간 락 재적용
            for (var entry : foreignLineTags.entrySet()) {
                int ln = entry.getKey();
                try {
                    int lo = textArea.getLineStartOffset(ln);
                    int hi = textArea.getLineEndOffset(ln);
                    Object newTag = hl.addHighlight(lo, hi, PAINT_FOREIGN);
                    entry.setValue(newTag);
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

    public void clearAllLocks() {
        Highlighter hl = textArea.getHighlighter();

        // 서버에 내 락 전부 해제 요청 (연속 구간 단위로)
        if (!myLines.isEmpty()) {
            List<Integer> lines = new ArrayList<>(myLines);
            Collections.sort(lines);
            int prev = lines.get(0), start = prev, end = prev;
            for (int i = 1; i < lines.size(); i++) {
                int cur = lines.get(i);
                if (cur == end + 1) {
                    end = cur;  // 연속
                } else {
                    // 연속 끝났으면 release
                    core.releaseLineLock(start, end);
                    start = end = cur;
                }
            }
            // 마지막 구간 해제
            core.releaseLineLock(start, end);
        }

        // 녹색(내 락) 태그 전부 제거
        for (Object tag : myLineTags.values()) {
            hl.removeHighlight(tag);
        }
        myLineTags.clear();
        myLines.clear();

        // 빨간색(타인 락) 태그 전부 제거
        for (Object tag : foreignLineTags.values()) {
            hl.removeHighlight(tag);
        }
        foreignLineTags.clear();

        // lockedLines, lastStart/lastEnd 초기화
        lockedLines.clear();
        lastStart = lastEnd = -1;
    }

    public void resetDocumentView() {
        resetDocumentView(false);
    }

    public void resetDocumentView(boolean editable) {
        SwingUtilities.invokeLater(() -> {
            ignore = true;

            // 잠금 해제: 서버에도 내 락이 있으면 release 요청 (모든 내 락 해제)
            if (!myLines.isEmpty()) {
                int relStart = Collections.min(myLines);
                int relEnd = Collections.max(myLines);
                core.releaseLineLock(relStart, relEnd);
            }

            textArea.setText("");
            textArea.setEditable(editable);
            core.setDocOpen(editable);

            // 잠금 상태 초기화
            myLineTags.clear();
            foreignLineTags.clear();
            myLines.clear();
            lockedLines.clear();
            lastStart = lastEnd = -1;

            ignore = false;
        });
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
