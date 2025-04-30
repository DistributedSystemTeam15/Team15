// ✅ MainFrame.java 수정본 - 문서 제목 및 참여자 표시 (상단 프로필 스타일)
package gui.view;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.util.Collection;
import java.util.List;

import cm.CMClientApp;
import gui.util.DocumentMeta;
import gui.util.DialogUtil;


public class MainFrame {
    private CMClientApp clientApp;
    private JFrame frame;
    private DocumentEditScreen editScreen;
    private JMenuItem newItem, saveItem;
    private DefaultListModel<String> onlineModel;
    private JList<String> onlineList;
    private DefaultListModel<DocumentMeta> docListModel;
    private JList<DocumentMeta> docList;

    // 상단 현재 문서 정보 패널
    private JLabel currentDocLabel;
    private JLabel currentUsersLabel;

    private JLabel onlineUserCountLabel;  // ✅ 접속자 수 레이블 추가

    public MainFrame(CMClientApp app) {
        this.clientApp = app;
        initUI();
    }

    private void initUI() {
        frame = new JFrame("Shared Editing Client (Asynchronous Version)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 600);
        frame.setLocationRelativeTo(null);

        // 편집 화면 중앙
        editScreen = new DocumentEditScreen(clientApp);

        // 좌측 문서 목록
        docListModel = new DefaultListModel<>();
        docList = new JList<>(docListModel);
        docList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        docList.setBorder(new TitledBorder("Documents"));
        docList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof DocumentMeta meta) {
                    label.setText(meta.getName() + "   👥 " + String.join(", ", meta.getActiveUsers()));
                }
                return label;
            }
        });

        /* --- 문서 더블 클릭 → 선택 --- */
        docList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    DocumentMeta sel = docList.getSelectedValue();
                    if (sel == null) return;
                    clientApp.selectDocument(sel.getName());          // ★ Core API 호출
                    setSaveEnabled(true);
                }
                // ✅ 우클릭 → 속성 보기 팝업
                // ✅ 우클릭 → 속성 보기/삭제 팝업
                if (SwingUtilities.isRightMouseButton(e) && !docList.isSelectionEmpty()) {
                    DocumentMeta selected = docList.getSelectedValue();
                    if (selected == null) return;

                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem info = new JMenuItem("Document Information");

                    info.addActionListener(ev -> {
                        JOptionPane.showMessageDialog(frame,
                                selected.getDetailedInfo(),
                                "Document Information",
                                JOptionPane.INFORMATION_MESSAGE);
                    });

                    JMenuItem del = new JMenuItem("Delete Document");

                    del.addActionListener(ev -> deleteSelectedDocument());

                    menu.add(info);
                    menu.add(del);  // ✅ 이 줄 추가!
                    menu.show(docList, e.getX(), e.getY());
                }
            }
        });
        JScrollPane docScroll = new JScrollPane(docList);
        docScroll.setPreferredSize(new Dimension(250, 0));

        // 우측 온라인 유저
        onlineModel = new DefaultListModel<>();
        onlineList = new JList<>(onlineModel);
        JScrollPane onlineScroll = new JScrollPane(onlineList);
        onlineScroll.setBorder(new TitledBorder("Online Users"));
        onlineUserCountLabel = new JLabel("Total: 0");
        onlineScroll.setColumnHeaderView(onlineUserCountLabel);

        // 상단 문서 제목 + 사용자 패널
        JPanel topPanel = new JPanel(new BorderLayout());
        currentDocLabel = new JLabel("문서 없음");
        currentUsersLabel = new JLabel("접속자 없음");
        currentUsersLabel.setPreferredSize(new Dimension(300, 20));
        topPanel.add(currentDocLabel, BorderLayout.WEST);
        topPanel.add(currentUsersLabel, BorderLayout.EAST);

        // 상단 메뉴
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        newItem = new JMenuItem("New Document");
        saveItem = new JMenuItem("Save");
        saveItem.setEnabled(false);

        fileMenu.add(newItem);
        fileMenu.add(saveItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        /* 툴바(저장 버튼) */
        JToolBar toolBar = new JToolBar();
        JButton saveBtn = new JButton("💾"); // 아이콘 없으면 텍스트로 대체 (임시)
        saveBtn.setToolTipText("Save");
        saveBtn.addActionListener(ev -> {
            if (saveItem.isEnabled()) {
                saveItem.doClick();
            }
        });
        toolBar.add(saveBtn);
        frame.add(toolBar, BorderLayout.NORTH);

        /* 중앙 레이아웃 -------------------------------------------------- */
        JPanel center = new JPanel(new BorderLayout());
        center.add(topPanel, BorderLayout.NORTH); // ✅ topPanel은 centerPanel에 추가

        // 오른쪽: 에디터와 접속자 패널을 나누는 스플릿
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editScreen, onlineScroll);
        rightSplit.setDividerLocation(700);
        rightSplit.setResizeWeight(1.0);

        // 문서 목록 + 오른쪽을 나누는 스플릿
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, docScroll, rightSplit);
        split.setDividerLocation(250);

        // ✅ 중앙 전체 패널 구성 완료
        center.add(split, BorderLayout.CENTER);
        frame.add(center, BorderLayout.CENTER);  // ✅ frame에는 centerPanel만 넣기

        /* 초기 진입 시 기본 안내 화면을 즉시 표시 */
        showWelcomeScreen();

        // ✅ Ctrl+S 누르면 저장
        InputMap im = frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = frame.getRootPane().getActionMap();

        im.put(KeyStroke.getKeyStroke("control S"), "save");
        am.put("save", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (saveItem.isEnabled()) {  // 저장 활성화된 경우만
                    saveItem.doClick();
                }
            }
        });
    }

    public void show() {
        frame.setVisible(true);
    }

    private void updateOnlineUserCount() {
        onlineUserCountLabel.setText("Total: " + onlineModel.size());
    }

    public void setOnlineUsers(Collection<String> users) {
        onlineModel.clear();
        users.stream().distinct().forEach(onlineModel::addElement);
        updateOnlineUserCount();  // ✅ 접속자 수 갱신
    }

    public void addOnlineUser(String user) {
        if (user == null || user.isBlank()) return;
        if (!onlineModel.contains(user)) {
            onlineModel.addElement(user);
            updateOnlineUserCount();  // ✅ 접속자 수 갱신
        }
    }

    public void removeOnlineUser(String user) {
        onlineModel.removeElement(user);
        updateOnlineUserCount();  // ✅ 접속자 수 갱신
    }


    public void updateTextContent(String content) {
        editScreen.updateTextContent(content);
    }

    public void showWelcomeScreen() {
        setCurrentDocument("📄 Shared Text Editor");
        setCurrentDocumentUsers(List.of());

        // 텍스트 영역에 기본 설명 넣기
        JTextArea textArea = editScreen.getTextArea();
        textArea.setEditable(false);  // 안내문일 때는 수정 비활성화
        textArea.setText("""
                📄 공유 텍스트 편집 프로그램
                개발자: 25년 분산시스템 7팀
                
                - 이 프로그램은 여러 사용자가 동시에 문서를 편집할 수 있도록 설계되었습니다.
                - 문서를 열거나 생성하여 자유롭게 편집할 수 있습니다.
                - 저장은 Ctrl+S 또는 저장 버튼을 사용하세요.
                - 실시간으로 다른 사용자의 편집 상황을 볼 수 있습니다.
                
                ✨ 최신 패치 노트:
                - 문서 리스트 실시간 반영
                - 저장시 수정(*) 표시 초기화
                - 사용자 접속 수 표시
                """);
    }

    public JTextArea getTextArea() {
        return editScreen.getTextArea();
    }

    public JFrame getFrame() {
        return frame;
    }

    public DocumentEditScreen getDocumentEditScreen() {
        return editScreen;
    }

    public void addNewDocumentAction(ActionListener listener) {
        newItem.addActionListener(listener);
    }

    public void addSaveDocumentAction(ActionListener listener) {
        saveItem.addActionListener(listener);
    }

    public void setSaveEnabled(boolean enabled) {
        saveItem.setEnabled(enabled);
    }

    public void setDocumentList(List<DocumentMeta> docs) {
        SwingUtilities.invokeLater(() -> {
            docListModel.clear();
            for (DocumentMeta doc : docs) docListModel.addElement(doc);
        });
    }

    // ✅ 상단 문서 제목 갱신
    public void setCurrentDocument(String name) {
        currentDocLabel.setText("📄 " + name);
    }

    public void updateDocumentUsers(String docName, List<String> users) {
        for (int i = 0; i < docListModel.getSize(); i++) {
            DocumentMeta meta = docListModel.get(i);
            if (meta.getName().equals(docName)) {
                meta.setActiveUsers(users);
                docListModel.set(i, meta); // JList 업데이트 트리거
                docList.repaint(); // ✅ 강제로 리스트 새로고침
                break;
            }
        }
    }

    // ✅ 상단 접속 사용자 리스트 갱신
    public void setCurrentDocumentUsers(List<String> users) {
        if (users == null || users.isEmpty()) {
            currentUsersLabel.setText("Please select the document to edit");
        } else {
            currentUsersLabel.setText("👥 " + String.join(", ", users));
        }
    }

    public void markDocumentModified() {
        String title = currentDocLabel.getText();
        if (!title.endsWith("*")) {
            currentDocLabel.setText(title + "*");
        }
    }

    public void markDocumentSaved() {
        String title = currentDocLabel.getText();
        if (title.endsWith("*")) {
            currentDocLabel.setText(title.substring(0, title.length() - 1));
        }
    }

    public void deleteSelectedDocument() {
        DocumentMeta sel = docList.getSelectedValue();
        if (sel == null) {
            JOptionPane.showMessageDialog(frame, "Select the document you want to delete.", "Delete document", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (DialogUtil.confirm("Delete the document \"" + sel.getName() + "\"?", "Confirm document deletion")) {
            clientApp.deleteDocument(sel.getName());            // ★ Core API 호출
        }
    }
}
