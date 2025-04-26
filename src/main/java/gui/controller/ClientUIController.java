package gui.controller;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JOptionPane;
import javax.swing.Timer;

import cm.CMClientApp;
import gui.util.DialogUtil;
import gui.view.MainFrame;

public class ClientUIController {
    private MainFrame mainFrame;
    private CMClientApp clientApp;

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
                if (docName != null && !docName.trim().isEmpty()) {
                    String trimmedDocName = docName.trim();
                    // 클라이언트 내부 문서 이름 설정
                    clientApp.setCurrentDocName(trimmedDocName);

                    // CMClientApp의 새 문서 생성 이벤트 전송
                    clientApp.createNewDocument(trimmedDocName);

                    // 문서 편집 화면을 리셋하고 편집 가능 상태로 전환
                    mainFrame.getDocumentEditScreen().resetDocumentView(true);
                    mainFrame.getDocumentEditScreen().getTextArea().requestFocusInWindow();

                    mainFrame.setSaveEnabled(true);
                    mainFrame.setDeleteEnabled(false);

                    System.out.println("New document created: " + trimmedDocName);
                }
            }
        });

        // Save Document 액션 리스너 등록
        mainFrame.addSaveDocumentAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clientApp.saveDocument();
                mainFrame.setDeleteEnabled(true);
                System.out.println("Save Document action triggered.");
            }
        });

        // Delete Document 액션 리스너 등록

    }
}
