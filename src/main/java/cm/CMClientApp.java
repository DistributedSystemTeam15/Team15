package cm;

import gui.dialog.ConnectionDialog;
import gui.util.DialogUtil;
import gui.dialog.LoginDialog;
import gui.view.MainFrame;
import gui.util.ServerConfig;
import gui.controller.ClientUIController;

import kr.ac.konkuk.ccslab.cm.event.CMUserEvent;
import kr.ac.konkuk.ccslab.cm.info.CMInfo;
import kr.ac.konkuk.ccslab.cm.stub.CMClientStub;

public class CMClientApp {
    // CM 클라이언트 스텁: 서버와 통신하는 핵심 객체
    private CMClientStub m_clientStub;
    // 이벤트 핸들러 객체: 서버로부터 전달받은 이벤트를 처리
    private CMClientEventHandler m_eventHandler;

    // 문서 관리 관련 필드들
    private boolean docOpen;                // 현재 문서가 열려 있는지 여부 (초기 false)
    private String currentDocName = "";     // 현재 열려 있는 문서의 이름



    // 생성자: 클라이언트 스텁 및 이벤트 핸들러를 초기화하고, 상태를 초기화함
    public CMClientApp() {
        m_clientStub = new CMClientStub();
        m_eventHandler = new CMClientEventHandler(m_clientStub);
        // 이벤트 핸들러에 현재 CMClientApp 인스턴스 참조를 설정하여 클라이언트 상태에 접근할 수 있도록 함
        m_eventHandler.setClientApp(this);
        docOpen = false;
    }

    // 로그인 승인 결과를 저장하는 변수
    private boolean loginResult = false;

    public void setLoginResult(boolean result) {
        this.loginResult = result;
    }

    public boolean getLoginResult() {
        return this.loginResult;
    }

    public CMClientStub getClientStub() {
        return m_clientStub;
    }

    public CMClientEventHandler getClientEventHandler() {
        return m_eventHandler;
    }

    public boolean isDocOpen() {
        return docOpen;
    }

    public void setDocOpen(boolean open) {
        docOpen = open;
    }

    public void setCurrentDocName(String name) {
        currentDocName = name;
    }

    public String getCurrentDocName() {
        return currentDocName;
    }

    // 새 문서 생성 시, 서버로 CREATE_DOC 이벤트를 전송하는 메서드
    public void createNewDocument(String docName) {
        setCurrentDocName(docName);
        // 문서 생성 요청 보낸 뒤, 현재 선택 문서명 저장
        CMUserEvent createEvt = new CMUserEvent();
        createEvt.setStringID("CREATE_DOC");
        createEvt.setEventField(CMInfo.CM_STR, "name", docName);
        m_clientStub.send(createEvt, "SERVER");

// 현재 작업중 문서 이름 설정
        setCurrentDocName(docName);
        System.out.println("New document creation event sent: " + docName);

        // ✅ 문서 생성 직후 목록 재요청
        CMUserEvent listReq = new CMUserEvent();
        listReq.setStringID("LIST_DOCS");
        m_clientStub.send(listReq, "SERVER");
        System.out.println("LIST_DOCS 요청 (문서 생성 직후)");
    }


    // 문서 편집 시 텍스트 변경 이벤트를 서버에 전송하는 메서드
    public void sendTextUpdate(String content) {
        CMUserEvent event = new CMUserEvent();
        event.setStringID("EDIT_DOC");
        event.setEventField(CMInfo.CM_STR, "content", content);
        m_clientStub.send(event, "SERVER");
        System.out.println("텍스트 변경 이벤트 전송됨. 길이: " + content.length());
    }

    // 문서 저장 시 SAVE_DOC 이벤트를 서버에 전송하는 메서드
    public void saveDocument() {
        CMUserEvent saveEvent = new CMUserEvent();
        saveEvent.setStringID("SAVE_DOC");
        m_clientStub.send(saveEvent, "SERVER");
        System.out.println("SAVE_DOC 이벤트 전송됨.");

        // ✅ 저장 후 표시 업데이트
        getClientEventHandler().getMainFrame().getDocumentEditScreen().markSaved();
    }


    // 문서 삭제 시 DELETE_DOC 이벤트를 서버에 전송하는 메서드
    public void deleteDocument(String docName) {
        CMUserEvent delEvent = new CMUserEvent();
        delEvent.setStringID("DELETE_DOC");
        delEvent.setEventField(CMInfo.CM_STR, "name", docName);
        m_clientStub.send(delEvent, "SERVER");
        System.out.println("DELETE_DOC event sent for document: " + docName);
    }

    // Open Document 기능: 현재 문서가 열려 있다면 상태를 리셋한 후 서버에 문서 목록 요청 이벤트를 전송함
    public void openDocument() {
        if (isDocOpen()) {
            setDocOpen(false);
        }
        CMUserEvent listDocsEvent = new CMUserEvent();
        listDocsEvent.setStringID("LIST_DOCS");
        m_clientStub.send(listDocsEvent, "SERVER");
        System.out.println("LIST_DOCS 이벤트 전송됨.");
    }
    public void notifyModified(boolean modified) {
        if (modified) {
            getClientEventHandler().getMainFrame().markDocumentModified();
        } else {
            getClientEventHandler().getMainFrame().markDocumentSaved();
        }
    }

    public static void main(String[] args) {
        CMClientApp clientApp = new CMClientApp();
        CMClientStub cmStub = clientApp.getClientStub();
        cmStub.setAppEventHandler(clientApp.getClientEventHandler());

        // 서버 연결 설정
        ServerConfig config = ConnectionDialog.getServerConfig();
        if (config == null) {
            DialogUtil.showErrorMessage("Server connection settings have been canceled. Exit the program.");
            System.exit(0);
        }
        cmStub.setServerAddress(config.getServerIP());
        cmStub.setServerPort(config.getPort());

        // 서버 연결 및 로그인 반복 루프
        while (true) {
            boolean started = cmStub.startCM();
            if (!started) {
                DialogUtil.showErrorMessage("Server connection failed. IP: " + config.getServerIP());
                System.exit(0);
            }

            // 서버 응답 기준 로그인 확인을 위해 초기화
            clientApp.setLoginResult(false);

            LoginDialog loginDialog = new LoginDialog(clientApp);
            loginDialog.showLoginDialog();  // 여기서 loginCM만 호출되고, 성공 여부는 아래에서 서버 응답으로 판단

            // 서버 응답 기다리기 (최대 3초)
            int waitMs = 0;
            while (!clientApp.getLoginResult() && waitMs < 3000) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                waitMs += 100;
            }

            if (clientApp.getLoginResult()) break;

            // 실패 시 종료 또는 재시도
            cmStub.terminateCM();
            boolean retry = DialogUtil.confirm("Login failed. Do you want to try again?", "Retry Login");
            if (!retry) System.exit(0);
        }


        // 로그인 성공 시 메인 UI 띄우기
        MainFrame mainFrame = new MainFrame(clientApp);
        clientApp.getClientEventHandler().setClientUI(mainFrame);
        new ClientUIController(mainFrame, clientApp);
        mainFrame.show();
    }


}
