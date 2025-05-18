package cm;

import cm.core.ClientCallback;
import kr.ac.konkuk.ccslab.cm.event.CMEvent;
import kr.ac.konkuk.ccslab.cm.event.CMUserEvent;
import kr.ac.konkuk.ccslab.cm.event.handler.CMAppEventHandler;
import kr.ac.konkuk.ccslab.cm.info.CMInfo;
import kr.ac.konkuk.ccslab.cm.event.CMSessionEvent;

import java.util.*;
import java.util.stream.Collectors;


/**
 * CM 이벤트를 수신해 ClientCallback 으로 전달하는 어댑터.
 * Swing 등 GUI 코드와 전혀 연결되지 않는다.
 */
public class CMClientEventHandler implements CMAppEventHandler {
    private volatile ClientCallback callback;

    /* 온라인 사용자 캐싱 */
    private final Set<String> pendingOnline = new HashSet<>();

    public CMClientEventHandler(ClientCallback cb) {
        this.callback = Objects.requireNonNull(cb);
    }

    /* 콜백 교체용 setter */
    public void setCallback(ClientCallback cb) {
        this.callback = Objects.requireNonNull(cb);
    }

    /* 현재 캐시를 읽어 갈 수 있도록 getter */
    public Set<String> getOnlineUsers() {
        return Set.copyOf(pendingOnline);
    }

    /* ------------ CMAppEventHandler ------------ */
    @Override
    public void processEvent(CMEvent cme) {
        switch (cme.getType()) {

            /* 1) 세션 이벤트 (로그인 / 로그아웃) */
            case CMInfo.CM_SESSION_EVENT -> handleSession((CMSessionEvent) cme);

            /* 2) 사용자 정의 이벤트 */
            case CMInfo.CM_USER_EVENT -> handleUser((CMUserEvent) cme);

            /* 기타는 무시 */
            default -> {
            }
        }
    }

    /* ===== 세션 이벤트 ===== */
    private void handleSession(CMSessionEvent se) {
        String user = se.getUserName();

        switch (se.getID()) {
            case CMSessionEvent.SESSION_ADD_USER -> {
                pendingOnline.add(user);
                callback.onOnlineUsersUpdated(Set.copyOf(pendingOnline));
            }
            case CMSessionEvent.SESSION_REMOVE_USER -> {
                pendingOnline.remove(user);
                callback.onOnlineUsersUpdated(Set.copyOf(pendingOnline));
            }
        }
    }

    /* ===== 사용자 정의 이벤트 ===== */
    private void handleUser(CMUserEvent ue) {

        String id = ue.getStringID();

        switch (id) {

            /* 로그인 결과 */
            case "LOGIN_ACCEPTED" -> callback.onLoginResult(true);
            case "LOGIN_REJECTED_DUPLICATE" -> callback.onLoginResult(false);

            /* 온라인 리스트 일괄 전송 */
            case "ONLINE_LIST" -> {
                String s = ue.getEventField(CMInfo.CM_STR, "users");
                pendingOnline.clear();
                if (s != null && !s.isBlank())
                    pendingOnline.addAll(Arrays.asList(s.split(",")));
                callback.onOnlineUsersUpdated(Set.copyOf(pendingOnline));
            }

            /* 문서 목록 */
            case "LIST_REPLY" -> {
                String json = ue.getEventField(CMInfo.CM_STR, "docs_json");
                callback.onDocumentListReceived(json == null ? "" : json);
            }

            /* 문서 내용 */
            case "DOC_CONTENT" -> {
                System.out.println("[DEBUG] CMClientEventHandler received DOC_CONTENT for " +
                        ue.getEventField(CMInfo.CM_STR, "name") +
                        ", length=" + ue.getEventField(CMInfo.CM_STR, "content").length());
                String name = ue.getEventField(CMInfo.CM_STR, "name");
                String content = ue.getEventField(CMInfo.CM_STR, "content");
                callback.onDocumentContentReceived(name, content);
            }

            /* 문서별 사용자 리스트 */
            case "USER_LIST" -> {
                String doc = ue.getEventField(CMInfo.CM_STR, "doc");
                String list = ue.getEventField(CMInfo.CM_STR, "users");
                List<String> users = list == null || list.isBlank()
                        ? List.of()
                        : Arrays.stream(list.split(",")).collect(Collectors.toList());
                callback.onDocumentUserList(doc, users);
            }

            /* 문서가 서버에서 삭제됨 */
            case "DOC_CLOSED" -> {
                String name = ue.getEventField(CMInfo.CM_STR, "name");
                callback.onDocumentClosed(name);
            }

            case "LOCK_LINE_ACK" -> {
                String doc   = ue.getEventField(CMInfo.CM_STR,"doc");
                int sLine    = Integer.parseInt(ue.getEventField(CMInfo.CM_INT,"startLine"));
                int eLine    = Integer.parseInt(ue.getEventField(CMInfo.CM_INT,"endLine"));
                boolean ok   = "1".equals(ue.getEventField(CMInfo.CM_INT,"ok"));
                callback.onLineLockAck(doc,sLine,eLine,ok);
            }

            case "LOCK_LINE_NOTIFY" -> {
                String doc   = ue.getEventField(CMInfo.CM_STR,"doc");
                int sLine    = Integer.parseInt(ue.getEventField(CMInfo.CM_INT,"startLine"));
                int eLine    = Integer.parseInt(ue.getEventField(CMInfo.CM_INT,"endLine"));
                String owner = ue.getEventField(CMInfo.CM_STR,"owner");    // "" → 해제
                callback.onLineLockUpdate(doc,sLine,eLine,owner==null?"":owner);
            }

            case "EDIT_REJECT" -> {
                String rsn = ue.getEventField(CMInfo.CM_STR,"reason");
                callback.onEditReject(rsn==null?"":rsn);
            }

            default -> { /* 알 수 없는 이벤트 무시 */ }
        }
    }
}
