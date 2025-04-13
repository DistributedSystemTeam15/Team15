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
        CMUserEvent createDocEvent = new CMUserEvent();
        createDocEvent.setStringID("CREATE_DOC");
        createDocEvent.setEventField(CMInfo.CM_STR, "name", docName);
        m_clientStub.send(createDocEvent, "SERVER");
        System.out.println("New document creation event sent: " + docName);
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

    public static void main(String[] args) {
        CMClientApp clientApp = new CMClientApp();
        CMClientStub cmStub = clientApp.getClientStub();
        // 이벤트 핸들러 등록 및 CM 클라이언트 시작
        cmStub.setAppEventHandler(clientApp.getClientEventHandler());

        // 서버 연결 설정을 위해 ConnectionDialog를 사용 (Swing 관련 코드는 gui.dialog에 있음)
        ServerConfig config = ConnectionDialog.getServerConfig();
        if (config == null) {
            System.out.println("서버 연결 설정 취소됨. 프로그램 종료.");
            System.exit(0);
        }
        cmStub.setServerAddress(config.getServerIP());
        cmStub.setServerPort(config.getPort());

        // 서버에 연결 시도
        boolean start = cmStub.startCM();
        if (!start) {
            DialogUtil.showErrorMessage("Server connection failed! IP: " + config.getServerIP());
            System.exit(0);
        }
        System.out.println("CM 클라이언트가 서버에 연결되었습니다.");

        // 로그인 다이얼로그를 통해 로그인 진행 (LoginDialog는 gui.dialog에 위치)
        LoginDialog loginDialog = new LoginDialog(clientApp);
        boolean loggedIn = loginDialog.showLoginDialog();
        if (!loggedIn) {
            DialogUtil.showErrorMessage("로그인 실패로 인해 프로그램을 종료합니다.");
            System.exit(0);
        }

        // 로그인 성공 후 메인 창(MainFrame) 생성 및 UI 컨트롤러 초기화
        MainFrame mainFrame = new MainFrame(clientApp);
        clientApp.getClientEventHandler().setClientUI(mainFrame);
        new ClientUIController(mainFrame, clientApp);
        mainFrame.show();
    }
}
