package cm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import cm.lock.Interval;
import kr.ac.konkuk.ccslab.cm.event.CMEvent;
import kr.ac.konkuk.ccslab.cm.event.CMUserEvent;
import kr.ac.konkuk.ccslab.cm.event.handler.CMAppEventHandler;
import kr.ac.konkuk.ccslab.cm.info.CMInfo;
import kr.ac.konkuk.ccslab.cm.stub.CMServerStub;
import kr.ac.konkuk.ccslab.cm.event.CMSessionEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class CMServerEventHandler implements CMAppEventHandler {
    /**
     * 기본 필드
     */
    private final CMServerStub m_serverStub;
    private final Map<String, String> documents = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> docUsers = new ConcurrentHashMap<>();
    private final Map<String, MetaInfo> docMeta = new ConcurrentHashMap<>();
    private final Map<String, String> userCurrentDoc = new ConcurrentHashMap<>();
    private final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();

    /**
     * 잠금 테이블
     */
    private final Map<String, List<Interval>> lockTable = new ConcurrentHashMap<>();
    private final Map<Interval, String> intervalDoc = new ConcurrentHashMap<>();
    private final Map<Interval, Long> lastTouch = new ConcurrentHashMap<>();

    /**
     * Idle 해제용 타이머
     */
    private final ScheduledExecutorService idleEvictor =
            Executors.newSingleThreadScheduledExecutor();

    /**
     * 문서 메타 클래스
     */
    private static class MetaInfo {
        String creatorId;
        String lastEditorId;
        long createdTime;
        long lastModifiedTime;
    }

    /**
     * 생성자
     */
    public CMServerEventHandler(CMServerStub serverStub) {
        m_serverStub = serverStub;
        /* idle 잠금 해제 타이머 기동 */
        idleEvictor.scheduleAtFixedRate(this::evictIdleLocks,
                5, 5, TimeUnit.SECONDS);
    }

    /**
     * 파일 시스템 유틸
     */
    private final String DOC_FOLDER = "documents";

    private String loadDocumentFromFile(String docName) {
        File f = new File(DOC_FOLDER, docName + ".txt");
        if (!f.exists()) return "";
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private boolean saveDocumentToFile(String docName, String content) {
        try {
            File dir = new File(DOC_FOLDER);
            if (!dir.exists()) dir.mkdir();
            try (FileWriter w = new FileWriter(new File(dir, docName + ".txt"))) {
                w.write(content);
                return true;
            }
        } catch (IOException e) {
            return false;
        }
    }

    private boolean deleteDocumentFile(String docName) {
        return new File(DOC_FOLDER, docName + ".txt").delete();
    }

    /**
     * 잠금 로직
     */
    private boolean tryLock(String doc, int s, int e, String user) {
        Interval want = new Interval(s, e, user);
        List<Interval> list = lockTable.computeIfAbsent(doc,
                k -> Collections.synchronizedList(new ArrayList<>()));

        synchronized (list) {
            if (list.stream().anyMatch(want::overlaps)) return false;
            list.add(want);
            intervalDoc.put(want, doc);                 // ★ 매핑 저장
            lastTouch.put(want, System.currentTimeMillis());
            return true;
        }
    }

    private void releaseLock(String doc, int s, int e, String user) {
        List<Interval> list = lockTable.get(doc);
        if (list == null) return;

        synchronized (list) {
            // 대상 Interval 객체를 찾아서
            Interval target = null;
            for (Interval iv : list) {
                if (iv.start() == s && iv.end() == e && iv.owner().equals(user)) {
                    target = iv;
                    break;
                }
            }
            // 리스트에서 제거하고, 보조 맵에서도 정리
            if (target != null) {
                list.remove(target);
                intervalDoc.remove(target);
                lastTouch.remove(target);
            }
            // 리스트가 비어 있으면 전체 엔트리 삭제
            if (list.isEmpty()) {
                lockTable.remove(doc);
            }
        }
    }

    /**
     * 주기적으로 idle(5s) 잠금 해제
     */
    private void evictIdleLocks() {
        long now = System.currentTimeMillis();

        lastTouch.entrySet().removeIf(ent -> {
            if (now - ent.getValue() < 5_000) return false;   // 5s 이내면 유지
            Interval iv = ent.getKey();
            String doc = intervalDoc.get(iv);
            releaseLock(doc, iv.start(), iv.end(), iv.owner());
            broadcastLockNotify(doc, iv, "");                 // owner="" → 해제
            intervalDoc.remove(iv);
            return true;
        });
    }

    private void broadcastLockNotify(String doc, Interval iv, String owner) {
        CMUserEvent m = new CMUserEvent();
        m.setStringID("LOCK_NOTIFY");
        m.setEventField(CMInfo.CM_STR, "doc", doc);
        m.setEventField(CMInfo.CM_INT, "start", "" + iv.start());
        m.setEventField(CMInfo.CM_INT, "end", "" + iv.end());
        m.setEventField(CMInfo.CM_STR, "owner", owner);   // "" 이면 해제
        for (String u : onlineUsers) m_serverStub.send(m, u);
    }

    /**
     *
     */
    private void broadcastUserList(String docName) {
        Set<String> users = docUsers.get(docName);
        if (users == null) return;

        CMUserEvent evt = new CMUserEvent();
        evt.setStringID("USER_LIST");
        evt.setEventField(CMInfo.CM_STR, "doc", docName);
        evt.setEventField(CMInfo.CM_STR, "users", String.join(",", users));

        for (String u : users) {
            m_serverStub.send(evt, u);
        }
    }

    private void sendDocContentToClient(String user, String docName) {
        String content = documents.get(docName);
        if (content == null) content = "";
        CMUserEvent docEvt = new CMUserEvent();
        docEvt.setStringID("DOC_CONTENT");
        docEvt.setEventField(CMInfo.CM_STR, "name", docName);
        docEvt.setEventField(CMInfo.CM_STR, "content", content);
        m_serverStub.send(docEvt, user);
    }

    /* 승인 + 개인 목록 전송 */
    private void sendLoginAcceptedAndLists(String user) {
        CMUserEvent ok = new CMUserEvent();
        ok.setStringID("LOGIN_ACCEPTED");
        m_serverStub.send(ok, user);

        sendOnlineListToClient(user);   // 로그인한 본인에게 현재 목록
        broadcastOnlineList();          // 기존 사용자들에게도 업데이트
    }

    /* 모든 온라인 사용자에게 ONLINE_LIST 브로드캐스트 */
    private void broadcastOnlineList() {
        for (String u : onlineUsers) {
            sendOnlineListToClient(u);
        }
    }

    /**
     * 세션 이벤트 보조
     */
    private void handleSession(CMSessionEvent se) {
        int id = se.getID();
        String user = se.getUserName();
        System.out.println("[SERVER DEBUG] handleSession(): ID=" + id + ", user=" + user);

        // --- 로그인 관련 이벤트 (중복 검사/승인) ---
        if (id == CMSessionEvent.LOGIN
                || id == CMSessionEvent.SESSION_ADD_USER) {
            if (onlineUsers.contains(user)) {
                System.out.println("[SERVER DEBUG] 중복 로그인 거부: user=" + user);
                CMUserEvent rej = new CMUserEvent();
                rej.setStringID("LOGIN_REJECTED_DUPLICATE");
                m_serverStub.send(rej, user);
            } else {
                onlineUsers.add(user);
                System.out.println("[SERVER DEBUG] 로그인 허용: user=" + user);
                sendLoginAcceptedAndLists(user);
                broadcastOnlineList();
            }
        }

        // --- 로그아웃/세션 제거 이벤트 ---
        if (id == CMSessionEvent.LOGOUT
                || id == CMSessionEvent.SESSION_REMOVE_USER) {
            if (onlineUsers.remove(user)) {
                System.out.println("[SERVER DEBUG] 로그아웃 처리: user=" + user);
                broadcastOnlineList();
            }
        }

    }

    /**
     * processEvent() 메서드는 CM 사용자 이벤트를 처리한다.
     * 각 이벤트 타입에 따라 문서 생성, 선택, 편집, 저장, 삭제, 목록 조회 등의 작업을 수행한다.
     */
    @Override
    public void processEvent(CMEvent cme) {
        System.out.println("[SERVER DEBUG] processEvent() type=" + cme.getType() +
                ", class=" + cme.getClass().getSimpleName());
        int nType = cme.getType();

        /* ---------- A. 세션 이벤트: 로그인/로그아웃 ---------- */
        if (nType == CMInfo.CM_SESSION_EVENT) {
            handleSession((CMSessionEvent) cme);
            return;
        }

        if (nType != CMInfo.CM_USER_EVENT) return;
        CMUserEvent ue = (CMUserEvent) cme;
        String eventID = ue.getStringID();
        String user = ue.getSender();

        switch (eventID) {

            // 새 문서 생성 요청 처리
            case "CREATE_DOC": {
                String docNameToCreate = ue.getEventField(CMInfo.CM_STR, "name");
                if (user == null || user.trim().isEmpty()) {
                    System.err.println("문서 생성 실패: sender 정보 없음");
                    break;
                }
                if (documents.size() >= 10) {
                    System.out.println("문서 생성 실패: 최대 문서 개수(10) 초과 시도 by [" + user + "]");
                    break;
                }
                if (documents.containsKey(docNameToCreate)) {
                    System.out.println("문서 생성 실패: 동일한 이름의 문서 [" + docNameToCreate + "] 이미 존재");
                    break;
                }

                /* 사용자가 이미 편집 중이던 문서에서 제거 */
                if (userCurrentDoc.containsKey(user)) {
                    String prevDoc = userCurrentDoc.get(user);
                    if (prevDoc != null && docUsers.containsKey(prevDoc)) {
                        docUsers.get(prevDoc).remove(user);
                        broadcastUserList(prevDoc);           // 이전 문서 참여자 갱신
                    }
                }

                /* 새 문서를 생성하고 사용자 등록 */
                documents.put(docNameToCreate, "");

                MetaInfo meta = new MetaInfo();
                meta.creatorId = user;
                meta.lastEditorId = user;
                meta.createdTime = System.currentTimeMillis();
                meta.lastModifiedTime = meta.createdTime;
                docMeta.put(docNameToCreate, meta);

                // 해당 문서에 대한 사용자 집합 초기화 후 생성자(user)를 추가
                docUsers.put(docNameToCreate, new HashSet<>());
                docUsers.get(docNameToCreate).add(user);

                // 사용자가 현재 열어 둔 문서 정보 업데이트
                userCurrentDoc.put(user, docNameToCreate);
                System.out.println("새 문서 생성: [" + docNameToCreate + "], 생성자: " + user);

                /* 클라이언트에 반영 */
                sendTextUpdateToClient(user, docNameToCreate);
                broadcastDocumentList();
                sendDocContentToClient(user, docNameToCreate);  // 생성 직후 문서 내용 보내기
                broadcastUserList(docNameToCreate);
                break;
            }

            // 문서 선택 요청 처리
            case "SELECT_DOC": {
                String docNameToSelect = ue.getEventField(CMInfo.CM_STR, "name");

                // 만약 문서가 in-memory에 존재하지 않으면 파일 시스템에서 내용을 로드하여 추가
                if (!documents.containsKey(docNameToSelect)) {
                    String loadedContent = loadDocumentFromFile(docNameToSelect);
                    documents.put(docNameToSelect, loadedContent);
                    System.out.println("파일 시스템에서 문서 [" + docNameToSelect + "] 내용 로드 완료.");
                }

                // 만약 문서에 대한 사용자 집합이 없으면 새로 생성
                if (!docUsers.containsKey(docNameToSelect)) {
                    docUsers.put(docNameToSelect, new HashSet<>());
                }

                // 이전에 사용자가 열었던 문서가 있다면 그 문서에서 해당 사용자를 제거
                if (userCurrentDoc.containsKey(user)) {
                    String prevDoc = userCurrentDoc.get(user);
                    if (prevDoc != null && docUsers.containsKey(prevDoc)) {
                        docUsers.get(prevDoc).remove(user);
                        broadcastUserList(prevDoc); // ✅ 추가!
                        System.out.println("사용자 [" + user + "] 기존 문서 [" + prevDoc + "] 편집 종료");
                    }
                }

                docUsers.get(docNameToSelect).add(user);
                userCurrentDoc.put(user, docNameToSelect);

                // ✅ 현재 문서 사용자 리스트만 전송 (중복 제거)
                broadcastUserList(docNameToSelect);

                sendDocContentToClient(user, docNameToSelect);
                broadcastDocumentList();

                break;
            }

            // 문서 편집 이벤트 처리
            case "EDIT_DOC": {
                String newContent = ue.getEventField(CMInfo.CM_STR, "content");
                String docName = userCurrentDoc.get(user);
                if (docName == null) {
                    System.out.println("편집 오류: [" + user + "] 문서를 열지 않은 상태에서 EDIT_DOC 이벤트 수신");
                    break;
                }

                // in-memory에 문서 내용을 업데이트
                documents.put(docName, newContent);
                MetaInfo metaEdit = docMeta.get(docName);
                if (metaEdit != null) {
                    metaEdit.lastEditorId = user;
                    metaEdit.lastModifiedTime = System.currentTimeMillis();
                }

                System.out.println("문서 [" + docName + "] 업데이트 by [" + user + "]: 길이=" + newContent.length());

                // 해당 문서에 참여 중인 다른 사용자들에게 업데이트 전송
                Set<String> participants = docUsers.get(docName);
                if (participants != null) {
                    for (String other : participants) {
                        if (other.equals(user)) continue;
                        sendTextUpdateToClient(other, docName);
                    }
                }
                break;
            }

            // 문서 저장 이벤트 처리
            case "SAVE_DOC": {
                String saveDocName = userCurrentDoc.get(user);
                if (saveDocName == null) {
                    System.out.println("저장 실패: [" + user + "] 문서를 열지 않은 상태");
                    break;
                }
                String contentToSave = documents.get(saveDocName);
                boolean saveSuccess = saveDocumentToFile(saveDocName, contentToSave);
                if (saveSuccess) {
                    System.out.println("문서 [" + saveDocName + "] 디스크 저장 성공 (요청자: " + user + ")");
                } else {
                    System.err.println("문서 [" + saveDocName + "] 저장 실패! (요청자: " + user + ")");
                }
                broadcastDocumentList();
                broadcastUserList(saveDocName);
                break;
            }

            // 서버가 요청한 문서 목록 조회 이벤트 처리: documents 폴더 내의 모든 .txt 파일 목록 반환
            case "LIST_DOCS": {
                File[] files = new File(DOC_FOLDER).listFiles((d, n) -> n.endsWith(".txt"));
                CMUserEvent listReply = new CMUserEvent();
                listReply.setStringID("LIST_REPLY");

                JSONArray jsonDocs = new JSONArray();
                if (files != null) {
                    for (File file : files) {
                        String name = file.getName().replaceAll("\\.txt$", "");
                        MetaInfo m = docMeta.get(name);

                        JSONObject obj = new JSONObject();
                        obj.put("name", name);
                        obj.put("creatorId", m != null ? m.creatorId : "unknown");
                        obj.put("lastEditorId", m != null ? m.lastEditorId : "unknown");
                        obj.put("createdTime", m != null ? new Date(m.createdTime).toString() : "unknown");
                        obj.put("lastModifiedTime", m != null ? new Date(m.lastModifiedTime).toString() : new Date(file.lastModified()).toString());
                        obj.put("activeUsers", String.join(",", docUsers.getOrDefault(name, Set.of())));
                        jsonDocs.put(obj);
                    }
                }
                listReply.setEventField(CMInfo.CM_STR, "docs_json", jsonDocs.toString());
                m_serverStub.send(listReply, user);

                System.out.println("문서 목록(JSON) 전송 완료 (" + user + ")");
                break;
            }

            // 삭제 가능한 문서 목록 조회 이벤트 처리: in-memory의 문서 목록 전달
            case "LIST_DOCS_FOR_DELETE": {
                StringBuilder sb = new StringBuilder();
                for (String doc : documents.keySet()) {
                    sb.append(doc).append(",");
                }
                if (!sb.isEmpty()) sb.setLength(sb.length() - 1);
                CMUserEvent listEvent = new CMUserEvent();
                listEvent.setStringID("LIST_DOCS_FOR_DELETE");
                listEvent.setEventField(CMInfo.CM_STR, "docs", sb.toString());
                m_serverStub.send(listEvent, ue.getSender());
                break;
            }

            // 문서 삭제 이벤트 처리
            case "DELETE_DOC": {
                String toDelete = ue.getEventField(CMInfo.CM_STR, "name");
                if (toDelete == null || !documents.containsKey(toDelete)) {
                    System.out.println("Delete failed: Document [" + toDelete + "] does not exist.");
                    break;
                }

                String requester = ue.getSender();

                /* 삭제되기 전에 모든 참여자(요청자 포함)에게 문서-종료 알림 */
                Set<String> participants = new HashSet<>(docUsers.getOrDefault(toDelete, Set.of()));
                participants.add(requester);                       // ← 요청자 자신도 포함
                for (String u : participants) {
                    CMUserEvent closed = new CMUserEvent();
                    closed.setStringID("DOC_CLOSED");
                    closed.setEventField(CMInfo.CM_STR, "name", toDelete);
                    m_serverStub.send(closed, u);
                }

                /* in-memory 구조 업데이트 */
                documents.remove(toDelete);
                docUsers.remove(toDelete);
                userCurrentDoc.entrySet().removeIf(e -> toDelete.equals(e.getValue()));

                /* 락 테이블 및 관련 매핑 정리 */
                List<Interval> locks = lockTable.remove(toDelete);
                if (locks != null) {
                    for (Interval iv : locks) {
                        intervalDoc.remove(iv);
                        lastTouch.remove(iv);
                    }
                }

                /* 파일 삭제 */
                if (deleteDocumentFile(toDelete)) {
                    System.out.println("Document [" + toDelete + "] deleted by " + requester);
                } else {
                    System.err.println("Document [" + toDelete + "] removed from memory, but file deletion failed.");
                }

                /* 문서 목록 갱신 브로드캐스트 */
                broadcastDocumentList();
                break;
            }

            // 잠금 요청
            case "LOCK_REQ": {
                String doc = ue.getEventField(CMInfo.CM_STR, "doc");
                int s = Integer.parseInt(ue.getEventField(CMInfo.CM_STR, "start"));
                int e = Integer.parseInt(ue.getEventField(CMInfo.CM_STR, "end"));

                boolean ok = tryLock(doc, s, e, user);

                CMUserEvent ack = new CMUserEvent();
                ack.setStringID("LOCK_ACK");
                ack.setEventField(CMInfo.CM_STR, "doc", doc);
                ack.setEventField(CMInfo.CM_INT, "start", "" + s);
                ack.setEventField(CMInfo.CM_INT, "end", "" + e);
                ack.setEventField(CMInfo.CM_INT, "ok", ok ? "1" : "0");
                m_serverStub.send(ack, user);

                if (ok) broadcastLockNotify(doc, new Interval(s, e, user), user);
            }

            // 잠금 해제 요청
            case "LOCK_RELEASE": {
                String doc = ue.getEventField(CMInfo.CM_STR, "doc");
                int s = Integer.parseInt(ue.getEventField(CMInfo.CM_STR, "start"));
                int e = Integer.parseInt(ue.getEventField(CMInfo.CM_STR, "end"));

                releaseLock(doc, s, e, user);
                broadcastLockNotify(doc, new Interval(s, e, user), "");   // 해제 통보
            }

            default: {
                System.out.println("알 수 없는 이벤트 타입: " + eventID);
                break;
            }
        }
    }

    /**
     * 특정 사용자에게 지정된 문서의 내용을 전송한다.
     *
     * @param targetUser 전송할 대상 사용자
     * @param docName    문서 이름
     */
    private void sendTextUpdateToClient(String targetUser, String docName) {
        String content = documents.get(docName);
        if (content == null) content = "";
        CMUserEvent updateEvent = new CMUserEvent();
        updateEvent.setStringID("DOC_CONTENT");
        updateEvent.setEventField(CMInfo.CM_STR, "name", docName);
        updateEvent.setEventField(CMInfo.CM_STR, "content", content);
        m_serverStub.send(updateEvent, targetUser);
    }

    /**
     * 특정 사용자에게 온라인 목록 전송
     *
     * @param targetUser 전송할 대상 사용자
     */
    private void sendOnlineListToClient(String targetUser) {
        CMUserEvent listEvt = new CMUserEvent();
        listEvt.setStringID("ONLINE_LIST");
        String userStr = String.join(",", onlineUsers);
        listEvt.setEventField(CMInfo.CM_STR, "users", userStr);
        m_serverStub.send(listEvt, targetUser);
    }

    private void broadcastDocumentList() {
        File[] files = new File(DOC_FOLDER).listFiles((d, n) -> n.endsWith(".txt"));
        JSONArray jsonDocs = new JSONArray();

        if (files != null) {
            for (File file : files) {
                String name = file.getName().replaceAll("\\.txt$", "");
                MetaInfo m = docMeta.get(name);
                JSONObject obj = new JSONObject();
                obj.put("name", name);
                obj.put("creatorId", m != null ? m.creatorId : "unknown");
                obj.put("lastEditorId", m != null ? m.lastEditorId : "unknown");
                obj.put("createdTime", m != null ? new Date(m.createdTime).toString() : "unknown");
                obj.put("lastModifiedTime", m != null ? new Date(m.lastModifiedTime).toString() : new Date(file.lastModified()).toString());
                obj.put("activeUsers", String.join(",", docUsers.getOrDefault(name, Set.of())));

                jsonDocs.put(obj);
            }
        }

        for (String user : onlineUsers) {
            CMUserEvent evt = new CMUserEvent();
            evt.setStringID("LIST_REPLY");
            evt.setEventField(CMInfo.CM_STR, "docs_json", jsonDocs.toString());
            m_serverStub.send(evt, user);
        }
    }
}
