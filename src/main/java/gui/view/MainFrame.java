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
import java.util.stream.Collectors;

import cm.CMClientApp;
import gui.util.DocumentMeta;
import kr.ac.konkuk.ccslab.cm.event.CMUserEvent;

public class MainFrame {
    private CMClientApp clientApp;
    private JFrame frame;
    private DocumentEditScreen editScreen;
    private JMenuItem newItem, openItem, saveItem, deleteItem;
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
        docList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    DocumentMeta selected = docList.getSelectedValue();
                    if (selected != null) {
                        CMUserEvent selectEvent = new CMUserEvent();
                        selectEvent.setStringID("SELECT_DOC");
                        selectEvent.setEventField(kr.ac.konkuk.ccslab.cm.info.CMInfo.CM_STR, "name", selected.getName());
                        clientApp.getClientStub().send(selectEvent, "SERVER");
                        clientApp.setCurrentDocName(selected.getName());
                        //editScreen.resetDocumentView();
                        setSaveEnabled(true);
                        // 현재 문서 제목 상단에 표시
                        setCurrentDocument(selected.getName());
                    }
                }
                // ✅ 우클릭 → 속성 보기 팝업
                // ✅ 우클릭 → 속성 보기/삭제 팝업
                if (SwingUtilities.isRightMouseButton(e) && !docList.isSelectionEmpty()) {
                    DocumentMeta selected = docList.getSelectedValue();
                    if (selected == null) return;

                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem attrItem = new JMenuItem("Document Information");

                    attrItem.addActionListener(ev -> {
                        JOptionPane.showMessageDialog(frame,
                                selected.getDetailedInfo(),
                                "Document Information",
                                JOptionPane.INFORMATION_MESSAGE);
                    });

                    JMenuItem deleteItem = new JMenuItem("Delete Document");

                    deleteItem.addActionListener(ev -> deleteSelectedDocument());

                    menu.add(attrItem);
                    menu.add(deleteItem);  // ✅ 이 줄 추가!
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

        onlineUserCountLabel = new JLabel("Total: 0명");
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
        //openItem = new JMenuItem("Open Document");
        saveItem = new JMenuItem("Save");
        //deleteItem = new JMenuItem("Delete Document");
        newItem.setEnabled(true);
        saveItem.setEnabled(false);
        fileMenu.add(newItem);
        //fileMenu.add(openItem);
        fileMenu.add(saveItem);
        //fileMenu.add(deleteItem);
        menuBar.add(fileMenu);
// (1) 상단 메뉴바 추가
        frame.setJMenuBar(menuBar);

// (2) ✅ 여기 다음에 툴바 추가
        JToolBar toolBar = new JToolBar();
        JButton saveButton = new JButton("💾"); // 아이콘 없으면 텍스트로 대체 (임시)
        saveButton.setToolTipText("저장");
        saveButton.addActionListener(ev -> {
            if (saveItem.isEnabled()) {
                clientApp.saveDocument();
            }
        });
        toolBar.add(saveButton);
        frame.add(toolBar, BorderLayout.NORTH);

        // 중앙 패널 구성
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(topPanel, BorderLayout.NORTH); // ✅ topPanel은 centerPanel에 추가

// 오른쪽: 에디터와 접속자 패널을 나누는 스플릿
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editScreen, onlineScroll);
        rightSplit.setDividerLocation(700);
        rightSplit.setResizeWeight(1.0);

// 문서 목록 + 오른쪽을 나누는 스플릿
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, docScroll, rightSplit);
        splitPane.setDividerLocation(250);
        splitPane.setResizeWeight(0);

// ✅ 중앙 전체 패널 구성 완료
        centerPanel.add(splitPane, BorderLayout.CENTER);
        frame.add(centerPanel, BorderLayout.CENTER);  // ✅ frame에는 centerPanel만 넣기


        // ✅ Ctrl+S 누르면 저장
        InputMap im = frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = frame.getRootPane().getActionMap();

        im.put(KeyStroke.getKeyStroke("control S"), "saveDocument");
        am.put("saveDocument", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (saveItem.isEnabled()) {  // 저장 활성화된 경우만
                    clientApp.saveDocument();
                }
            }
        });

    }

    public void show() {
        frame.setVisible(true);
    }

    private void updateOnlineUserCount() {
        onlineUserCountLabel.setText("접속자: " + onlineModel.size() + "명");
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

    public void setOnlineUsers(Collection<String> users) {
        onlineModel.clear();
        users.stream().distinct().forEach(onlineModel::addElement);
        updateOnlineUserCount();  // ✅ 접속자 수 갱신
    }

    public List<String> getOnlineUsers() {
        return java.util.Collections.list(onlineModel.elements());
    }

    public void updateTextContent(String content) {
        editScreen.updateTextContent(content);
    }

    public void resetDocumentView() {
        setCurrentDocument("📄 공유 텍스트 에디터");
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

    public void setDeleteEnabled(boolean enabled) {
        deleteItem.setEnabled(enabled);
    }

    public void setDocumentList(List<DocumentMeta> docs) {
        SwingUtilities.invokeLater(() -> {
            docListModel.clear();
            for (DocumentMeta doc : docs) docListModel.addElement(doc);
        });
    }

    public String promptDocumentSelection(String[] docs) {
        return DocumentListScreen.promptDocumentSelection(docs, frame);
    }

    public void showNoDocumentsAvailable() {
        DocumentListScreen.showNoDocumentsAvailable(frame);
    }

    public void showUserList(String doc, String users) {
        DocumentListScreen.showUserList(doc, users, frame);
    }

    public void showNoDocumentsForDeletion() {
        DocumentListScreen.showNoDocumentsForDeletion(frame);
    }

    public String promptDocumentDeletion(String[] docNames) {
        return DocumentListScreen.promptDocumentDeletion(docNames, frame);
    }

    public boolean confirmDocumentDeletion(String docName) {
        return DocumentListScreen.confirmDocumentDeletion(docName, frame);
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
            currentUsersLabel.setText("👥 접속자 없음");
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
        DocumentMeta selected = docList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(frame, "삭제할 문서를 선택하세요.", "문서 삭제", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<String> activeUsers = selected.getActiveUsers();
        String message;
        if (activeUsers != null && !activeUsers.isEmpty()) {
            message = "다음 사용자들이 문서를 편집 중입니다:\n" +
                    String.join(", ", activeUsers) +
                    "\n\n정말 삭제하시겠습니까?";
        } else {
            message = "정말 이 문서를 삭제하시겠습니까?";
        }

        int confirm = JOptionPane.showConfirmDialog(frame,
                message,
                "문서 삭제 확인",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            CMUserEvent delEvt = new CMUserEvent();
            delEvt.setStringID("DELETE_DOC");
            delEvt.setEventField(kr.ac.konkuk.ccslab.cm.info.CMInfo.CM_STR, "name", selected.getName());
            clientApp.getClientStub().send(delEvt, "SERVER");

            // 현재 열려 있던 문서를 삭제한 경우 편집창 초기화
            if (selected.getName().equals(clientApp.getCurrentDocName())) {
                clientApp.setCurrentDocName(null);
                editScreen.resetDocumentView();
                setCurrentDocument("문서 없음");
                setCurrentDocumentUsers(List.of());
                setSaveEnabled(false);
                setDeleteEnabled(false);
            }
        }
    }

}
