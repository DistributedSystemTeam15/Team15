package gui.adapter;

import cm.CMClientApp;
import cm.core.ClientCallback;
import gui.dialog.LoginDialog;
import gui.util.DialogUtil;
import gui.util.DocumentMeta;
import gui.view.MainFrame;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * CMCore → GUI(MainFrame) 로 이벤트를 전달-반영하는 클래스.
 * 모든 메서드는 EDT(Swing 이벤트 디스패치 스레드)에서 실행되도록 보장한다.
 */
public final class GuiCallback implements ClientCallback {
    private final CMClientApp clientCore;   // 상태 업데이트용
    private final MainFrame ui;           // 실제 화면

    public GuiCallback(CMClientApp core, MainFrame ui) {
        this.clientCore = Objects.requireNonNull(core);
        this.ui = Objects.requireNonNull(ui);
    }

    /* ------------------------------------------------------------------
       1) 로그인 결과
       ------------------------------------------------------------------ */
    @Override
    public void onLoginResult(boolean success) {
        clientCore.setLoginResult(success);

        /* 실패했을 때: 알림 → 연결 해제 → 로그인 창 재호출 */
        if (!success) {
            runEdt(() -> {
                DialogUtil.showErrorMessage("로그인에 실패했습니다. 다시 시도해 주세요.");

                /* ① 서버 연결 해제 */
                clientCore.disconnect();
                /* ② 새로운 로그인 다이얼로그 호출 (비동기로 재시도) */
                LoginDialog dlg = new LoginDialog(clientCore);
                boolean requested = dlg.showLoginDialog();
                /* 사용자가 취소하면 애플리케이션 종료 */
                if (!requested) System.exit(0);
            });
        }
    }

    /* ------------------------------------------------------------------
       2) 온라인 사용자 / 문서 사용자
       ------------------------------------------------------------------ */
    @Override
    public void onOnlineUsersUpdated(Set<String> users) {
        runEdt(() -> ui.setOnlineUsers(users));
    }

    @Override
    public void onDocumentUserList(String doc, List<String> users) {
        runEdt(() -> {
            ui.updateDocumentUsers(doc, users);
            if (doc.equals(clientCore.getCurrentDocName()))
                ui.setCurrentDocumentUsers(users);
        });
    }

    /* ------------------------------------------------------------------
       3) 문서 메타 리스트
       ------------------------------------------------------------------ */
    @Override
    public void onDocumentListReceived(String json) {
        runEdt(() -> {
            if (json.isBlank()) {
                ui.showNoDocumentsAvailable();
                return;
            }
            try {
                JSONArray arr = new JSONArray(json);
                List<DocumentMeta> list = new ArrayList<>();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    DocumentMeta meta = new DocumentMeta(
                            o.getString("name"),
                            o.getString("creatorId"),
                            o.getString("lastEditorId"),
                            o.getString("createdTime"),
                            o.getString("lastModifiedTime")
                    );
                    String users = o.optString("activeUsers", "");
                    if (!users.isBlank())
                        meta.setActiveUsers(List.of(users.split(",")));
                    list.add(meta);
                }
                ui.setDocumentList(list);
            } catch (Exception e) {
                e.printStackTrace();
                ui.showNoDocumentsAvailable();
            }
        });
    }

    /* ------------------------------------------------------------------
       4) 문서 내용
       ------------------------------------------------------------------ */
    @Override
    public void onDocumentContentReceived(String name, String content) {
        runEdt(() -> {
            clientCore.setCurrentDocName(name);
            ui.setCurrentDocument(name);
            ui.getDocumentEditScreen().resetDocumentView();
            ui.updateTextContent(content);
            ui.getTextArea().setEditable(true);
            ui.setSaveEnabled(true);
        });
    }

    /* ------------------------------------------------------------------
       5) 문서 삭제 알림
       ------------------------------------------------------------------ */
    @Override
    public void onDocumentClosed(String name) {
        runEdt(() -> {
            if (name.equals(clientCore.getCurrentDocName())) {
                clientCore.setCurrentDocName(null);
                ui.resetDocumentView();
                ui.setSaveEnabled(false);
                DialogUtil.showInfoMessage(
                        "The document \"" + name + "\" you were editing was deleted.");
            } else {
                DialogUtil.showErrorMessage(
                        "Document \"" + name + "\" has been deleted.");
            }
        });
    }

    /* ========== EDT 헬퍼 ========== */
    private static void runEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }
}
