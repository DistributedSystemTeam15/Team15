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

        // Open Document 액션 리스너 등록
        mainFrame.addOpenDocumentAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 로그인 직후 즉시 Open Document 호출 시 세션 가입 및 그룹 구성 이벤트가 완료될 때까지
                // 약간의 지연(500ms)을 두고 Open Document 기능을 실행합니다.
                Timer timer = new Timer(500, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent evt) {
                        clientApp.openDocument();
                        System.out.println("Open Document action triggered.");
                    }
                });
                timer.setRepeats(false);
                timer.start();
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
        mainFrame.addDeleteDocumentAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String currentDoc = clientApp.getCurrentDocName();
                if (currentDoc == null || currentDoc.isEmpty()) {
                    DialogUtil.showErrorMessage("No document is currently open for deletion.");
                    return;
                }
                boolean confirm = DialogUtil.confirm("Do you really want to delete the current document: " + currentDoc + "?", "Delete Document");
                if (confirm) {
                    clientApp.deleteDocument(currentDoc);
                    // Clear the document view to remove the deleted document's text
                    mainFrame.getDocumentEditScreen().resetDocumentView();
                    // Optionally, disable delete button after deletion
                    mainFrame.setSaveEnabled(false);
                    mainFrame.setDeleteEnabled(false);
                    System.out.println("Delete Document action triggered for: " + currentDoc);
                }
            }
        });
    }
}
