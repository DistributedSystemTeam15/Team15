package cm;

import cm.core.ClientCallback;
import cm.model.ClientState;

import gui.adapter.GuiCallback;
import gui.controller.ClientUIController;
import gui.dialog.ConnectionDialog;
import gui.dialog.LoginDialog;
import gui.util.DialogUtil;
import gui.util.ServerConfig;
import gui.view.MainFrame;
import kr.ac.konkuk.ccslab.cm.event.CMUserEvent;
import kr.ac.konkuk.ccslab.cm.info.CMInfo;
import kr.ac.konkuk.ccslab.cm.stub.CMClientStub;

import java.util.List;
import java.util.Set;


/**
 * * CM 라이브러리 초기화 / 서버 연결
 * * 비즈니스 API (문서 생성·선택·편집·저장·삭제)
 * * 이벤트 처리 어댑터(CMClientEventHandler) 포함
 * * 상태는 cm.model.ClientState 로 관리
 * * GUI 와는 ClientCallback 인터페이스로만 통신
 */
public class CMClientApp {

    /* ---------- Core Fields ---------- */
    private final ClientState state = new ClientState();
    private final CMClientStub stub;
    private final CMClientEventHandler handler;

    /* ---------- Constructor ---------- */
    public CMClientApp() {
        /* 임시 콜백 (로그인 결과만 반영하면 충분) */
        ClientCallback temp = new ClientCallback() {
            @Override public void onLoginResult(boolean ok) {
                state.setLoggedIn(ok);          // ★ 로그인 상태 저장
            }
            @Override public void onOnlineUsersUpdated(Set<String> u) {}
            @Override public void onDocumentListReceived(String j) {}
            @Override public void onDocumentContentReceived(String d, String c) {}
            @Override public void onDocumentClosed(String d) {}
            @Override public void onDocumentUserList(String doc, List<String> users) {}
        };

        /* 실제 Stub/Handler 한 번만 생성 */
        this.stub    = new CMClientStub();
        this.handler = new CMClientEventHandler(temp);
        stub.setAppEventHandler(handler);
    }

    /* 로그인 후 GUI를 만든 뒤 호출해서 콜백을 주입 */
    public void attachCallback(ClientCallback guiCb) {
        /* ▶ 핸들러 교체 대신 콜백만 바꾼다 */
        handler.setCallback(guiCb);

        /* ▶ 이미 받은 온라인 사용자 목록을 GUI 에 즉시 전달 */
        guiCb.onOnlineUsersUpdated(handler.getOnlineUsers());

        /* ▶ 문서 목록도 다시 받아 오면 UI 초기화 완벽 */
        requestDocumentList();
    }

    /* ---------- Connection ---------- */
    public boolean connect(String serverIp, int port) {
        stub.setServerAddress(serverIp);
        stub.setServerPort(port);
        return stub.startCM();
    }

    public void disconnect() {
        stub.terminateCM();
        state.setLoggedIn(false);
    }

    /* ---------- Login ---------- */
    public void loginAsync(String userId, String password) {
        state.setLoggedIn(false);
        stub.loginCM(userId, password);     // 결과는 이벤트 → callback.onLoginResult
    }

    /* ---------- Business API ---------- */
    public void createDocument(String name) {
        sendUserEvent("CREATE_DOC", "name", name);
        state.setCurrentDoc(name);
    }

    public void selectDocument(String name) {
        sendUserEvent("SELECT_DOC", "name", name);
        state.setCurrentDoc(name);
    }

    public void editCurrentDocument(String newContent) {
        sendUserEvent("EDIT_DOC", "content", newContent);
    }

    public void saveCurrentDocument() {
        sendUserEvent("SAVE_DOC", null, null);
    }

    public void deleteDocument(String name) {
        sendUserEvent("DELETE_DOC", "name", name);
        if (name.equals(state.getCurrentDoc())) {
            state.setCurrentDoc(null);
        }
    }

    public void requestDocumentList() {
        sendUserEvent("LIST_DOCS", null, null);
    }

    /* ---------- Helper ---------- */
    private void sendUserEvent(String id, String key, String value) {
        CMUserEvent ev = new CMUserEvent();
        ev.setStringID(id);
        if (key != null && value != null) {
            ev.setEventField(CMInfo.CM_STR, key, value);
        }
        stub.send(ev, "SERVER");
    }

    /* ---------- Getters ---------- */
    public ClientState getState() {
        return state;
    }

    public CMClientStub getStub() {
        return stub;
    }

    public String getCurrentDocName() {
        return state.getCurrentDoc();
    }

    public void setCurrentDocName(String name) {
        state.setCurrentDoc(name);
    }

    public boolean isDocOpen() {
        return state.isDocOpen();
    }

    public void setLoginResult(boolean success) {
        state.setLoggedIn(success);
    }

    public void setDocOpen(boolean open) {
        state.setDocOpen(open);
    }

    public static void main(String[] args) {

        /* ---------- 코어 & 서버 접속 ---------- */
        CMClientApp core = new CMClientApp();

        // 서버 주소 입력
        ServerConfig cfg = ConnectionDialog.getServerConfig();
        if (cfg == null) return;
        if (!core.connect(cfg.getServerIP(), cfg.getPort())) {
            DialogUtil.showErrorMessage("Cannot connect to the server.");
            return;
        }

        /* ---------- 로그인 ---------- */
        LoginDialog ld = new LoginDialog(core);
        if (!ld.showLoginDialog()) return;                // 로그인 요청 실패(전송 오류)

        // 서버 응답 대기 – polling (3000 ms 한도)
        int waited = 0;
        while (!core.getState().isLoggedIn() && waited < 3000) {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            waited += 100;
        }
        if (!core.getState().isLoggedIn()) {
            DialogUtil.showErrorMessage("Login failed (duplicate ID or authentication error)");
            core.disconnect();
            return;
        }

        /* ---------- GUI <-> Core 연결 ---------- */
        MainFrame ui = new MainFrame(core);          // GUI
        GuiCallback guiCb = new GuiCallback(core, ui);
        core.attachCallback(guiCb);                  // 콜백 주입

        new ClientUIController(ui, core);            // 액션 바인딩
        ui.show();                                   // GUI 표시
    }
}
