package cm.core;

import java.util.List;
import java.util.Set;

public interface ClientCallback {
    /* ---------- 로그인 ---------- */
    /** 로그인 결과(true: 성공, false: 실패) */
    void onLoginResult(boolean success);

    /* ---------- 문서 목록 ---------- */
    /** JSON·DTO 등 구현체가 이해할 수 있는 형식으로 문서 리스트 전달 */
    void onDocumentListReceived(String docsJson);

    /* ---------- 문서 내용 ---------- */
    /** 특정 문서의 전체 콘텐츠 수신 */
    void onDocumentContentReceived(String docName, String content);

    /* ---------- 온라인 사용자 ---------- */
    /** 전체 온라인 사용자 세트 갱신 */
    void onOnlineUsersUpdated(Set<String> users);

    /* ---------- 문서별 편집자 목록 ---------- */
    /** 한 문서에 참여 중인 유저 리스트(선택적) */
    default void onDocumentUserList(String docName, List<String> users) {}

    /* ---------- 문서 삭제 통지 ---------- */
    /** 편집 중인 문서가 서버에서 삭제됐을 때 */
    void onDocumentClosed(String docName);

    /* ---------- 잠금 이벤트 ---------- */
    /** 잠금 요청 ACK */
    default void onLockAck(String doc, int start, int end, boolean ok) {}

    /** 잠금/해제 브로드캐스트 */
    default void onLockUpdate(String doc, int start, int end, String owner) {}
}
