package gui.controller;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JOptionPane;

import cm.CMClientApp;
import gui.view.MainFrame;

public class ClientUIController {
    private final MainFrame mainFrame;
    private final CMClientApp clientApp;

    public ClientUIController(MainFrame mainFrame, CMClientApp clientApp) {
        this.mainFrame = mainFrame;
        this.clientApp = clientApp;
        registerActions();
    }

    private void registerActions() {
        // New Document 액션 리스너 등록
        mainFrame.addNewDocumentAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String docName = JOptionPane.showInputDialog(mainFrame.getFrame(), "Enter new document name:");
                if (docName == null || docName.trim().isEmpty()) return;

                String trimmed = docName.trim();
                // 클라이언트 내부 문서 이름 설정
                clientApp.setCurrentDocName(trimmed);

                // CMClientApp의 새 문서 생성 이벤트 전송
                clientApp.createDocument(trimmed);

                // 문서 편집 화면을 리셋하고 편집 가능 상태로 전환
                mainFrame.getDocumentEditScreen().resetDocumentView(true);
                mainFrame.getDocumentEditScreen().getTextArea().requestFocusInWindow();

                mainFrame.setSaveEnabled(true);

                System.out.println("New document created: " + trimmed);

            }
        });

        // Save Document 액션 리스너 등록
        mainFrame.addSaveDocumentAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clientApp.saveCurrentDocument();
                System.out.println("Save Document action triggered.");
            }
        });
    }
}
