package gui.view;

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import javax.swing.*;

import cm.CMClientApp;

public class MainFrame {
    private CMClientApp clientApp;
    private JFrame frame;
    private DocumentEditScreen editScreen;
    private JMenuItem newItem, openItem, saveItem, deleteItem;

    public MainFrame(CMClientApp app) {
        this.clientApp = app;
        initUI();
    }

    private void initUI() {
        frame = new JFrame("Shared Editing Client (Asynchronous Version)");
        frame.setLayout(new BorderLayout());

        // 중앙에 문서 편집 화면 추가
        editScreen = new DocumentEditScreen(clientApp);
        frame.add(editScreen, BorderLayout.CENTER);

        // 메뉴 바 생성
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        newItem = new JMenuItem("New Document");
        openItem = new JMenuItem("Open Document");
        saveItem = new JMenuItem("Save");
        deleteItem = new JMenuItem("Delete Document");

        // 초기 상태: 새, 열기는 활성화 / 저장, 삭제는 문서 열림 시 활성화
        newItem.setEnabled(true);
        openItem.setEnabled(true);
        saveItem.setEnabled(false);
        deleteItem.setEnabled(false);

        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(deleteItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
    }

    public void show() {
        frame.setVisible(true);
    }

    // ----- 어댑터(Delegation) 메서드: DocumentEditScreen 관련 -----
    public void updateTextContent(String content) {
        editScreen.updateTextContent(content);
    }

    public void resetDocumentView() {
        editScreen.resetDocumentView();
    }

    public JTextArea getTextArea() {
        return editScreen.getTextArea();
    }

    // ----- 어댑터 메서드: DocumentListScreen 관련 -----
    public String promptDocumentSelection(String[] docs) {
        return DocumentListScreen.promptDocumentSelection(docs, frame);
    }

    public void showNoDocumentsAvailable() {
        DocumentListScreen.showNoDocumentsAvailable(frame);
    }

    public String promptDocumentDeletion(String[] docs) {
        return DocumentListScreen.promptDocumentDeletion(docs, frame);
    }

    public boolean confirmDocumentDeletion(String doc) {
        return DocumentListScreen.confirmDocumentDeletion(doc, frame);
    }

    public void showNoDocumentsForDeletion() {
        DocumentListScreen.showNoDocumentsForDeletion(frame);
    }

    public void showUserList(String doc, String users) {
        DocumentListScreen.showUserList(doc, users, frame);
    }

    // ----- 액션 리스너 등록용 메서드 -----
    public void addNewDocumentAction(ActionListener listener) {
        newItem.addActionListener(listener);
    }

    public void addOpenDocumentAction(ActionListener listener) {
        openItem.addActionListener(listener);
    }

    public void addSaveDocumentAction(ActionListener listener) {
        saveItem.addActionListener(listener);
    }

    public void addDeleteDocumentAction(ActionListener listener) {
        deleteItem.addActionListener(listener);
    }

    public void setSaveEnabled(boolean enabled) {
        saveItem.setEnabled(enabled);
    }

    public void setDeleteEnabled(boolean enabled) {
        deleteItem.setEnabled(enabled);
    }

    // Getter: 메인 프레임(다이얼로그 부모 창)
    public JFrame getFrame() {
        return frame;
    }

    // Getter: 내부의 DocumentEditScreen
    public DocumentEditScreen getDocumentEditScreen() {
        return editScreen;
    }
}
