package cm;

import cm.core.ClientCallback;
import cm.lock.Interval;
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

import java.io.IOException;
import java.nio.channels.UnresolvedAddressException;
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
            @Override
            public void onLoginResult(boolean ok) {
                state.setLoggedIn(ok);          // ★ 로그인 상태 저장
            }

            @Override
            public void onOnlineUsersUpdated(Set<String> u) {
            }

            @Override
            public void onDocumentListReceived(String j) {
            }

            @Override
            public void onDocumentContentReceived(String d, String c) {
            }

            @Override
            public void onDocumentClosed(String d) {
            }

            @Override
            public void onDocumentUserList(String doc, List<String> users) {
            }
        };

        /* 실제 Stub/Handler 한 번만 생성 */
        this.stub = new CMClientStub();
        this.handler = new CMClientEventHandler(temp);
        stub.setAppEventHandler(handler);
    }

    /* Internal helper */
    private void send(String id, String key, String val) {
        CMUserEvent ev = new CMUserEvent();
        ev.setStringID(id);
        if (key != null && val != null) ev.setEventField(CMInfo.CM_STR, key, val);
        stub.send(ev, "SERVER");
    }

    /* callback hookup */
    public void attachCallback(ClientCallback cb) {
        handler.setCallback(cb);
        cb.onOnlineUsersUpdated(handler.getOnlineUsers());
        requestDocumentList();
    }

    /* life-cycle */
    public boolean connect(String serverIp, int port) {
        stub.setServerAddress(serverIp);
        stub.setServerPort(port);
        try {
            return stub.startCM();                 // 내부 FutureTask 실행
        } catch (UnresolvedAddressException ex) {
            DialogUtil.showErrorMessage(
                    "Cannot resolve or reach the server address: " + serverIp + ":" + port);
            return false;
        } catch (Exception ex) {                   // 기타 예외
            DialogUtil.showErrorMessage("Failed to start CM: " + ex.getMessage());
            return false;
        }
    }

    public void disconnect() {
        stub.terminateCM();
        state.setLoggedIn(false);
    }

    /* Login */
    public void loginAsync(String id, String pw) {
        state.setLoggedIn(false);
        stub.loginCM(id, pw);     // 결과는 이벤트 → callback.onLoginResult
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

    public void editCurrentDocument(String txt) {
        send("EDIT_DOC", "content", txt);
    }

    public void saveCurrentDocument() {
        send("SAVE_DOC", null, null);
    }

    public void deleteDocument(String name) {
        send("DELETE_DOC", "name", name);
    }

    public void requestDocumentList() {
        send("LIST_DOCS", null, null);
    }

    /* interval-lock 요청 / 해제 */
    public void requestIntervalLock(int start, int end) {
        Interval iv = new Interval(start, end);
        CMUserEvent ev = new CMUserEvent();
        ev.setStringID("LOCK_REQ");
        ev.setEventField(CMInfo.CM_STR, "doc", state.getCurrentDoc());
        ev.setEventField(CMInfo.CM_INT, "start", "" + iv.start());
        ev.setEventField(CMInfo.CM_INT, "end", "" + iv.end());
        stub.send(ev, "SERVER");
    }

    public void releaseIntervalLock(int start, int end) {
        Interval iv = new Interval(start, end);
        CMUserEvent ev = new CMUserEvent();
        ev.setStringID("LOCK_RELEASE");
        ev.setEventField(CMInfo.CM_STR, "doc", state.getCurrentDoc());
        ev.setEventField(CMInfo.CM_INT, "start", "" + iv.start());
        ev.setEventField(CMInfo.CM_INT, "end", "" + iv.end());
        stub.send(ev, "SERVER");
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
        if (cfg == null || !core.connect(cfg.getServerIP(), cfg.getPort())) {
            DialogUtil.showErrorMessage("Cannot connect to server.");
            return;
        }

        /* ---------- 로그인 ---------- */
        if (!new LoginDialog(core).showLoginDialog()) return;

        // 서버 응답 대기 – polling (3000 ms 한도)
        int waited = 0;
        while (!core.getState().isLoggedIn() && waited < 3000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            waited += 100;
        }
        if (!core.getState().isLoggedIn()) {
            DialogUtil.showErrorMessage("Login failed (duplicate ID or authentication error)");
            core.disconnect();
            return;
        }

        /* ---------- GUI <-> Core 연결 ---------- */
        MainFrame ui = new MainFrame(core);             // GUI
        core.attachCallback(new GuiCallback(core, ui)); // 콜백 주입
        new ClientUIController(ui, core);               // 액션 바인딩
        ui.show();                                      // GUI 표시
    }
}
