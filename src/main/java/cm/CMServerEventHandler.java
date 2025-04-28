package cm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import kr.ac.konkuk.ccslab.cm.event.CMEvent;
import kr.ac.konkuk.ccslab.cm.event.CMUserEvent;
import kr.ac.konkuk.ccslab.cm.event.handler.CMAppEventHandler;
import kr.ac.konkuk.ccslab.cm.info.CMInfo;
import kr.ac.konkuk.ccslab.cm.stub.CMServerStub;
import kr.ac.konkuk.ccslab.cm.event.CMSessionEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Date;


public class CMServerEventHandler implements CMAppEventHandler {
    private CMServerStub m_serverStub;                       // 서버 스텁 객체 (클라이언트와 통신)
    private Map<String, String> documents;                   // in-memory에서 문서 내용 관리 (문서명 -> 문서 내용)
    private Map<String, Set<String>> docUsers;

    private static class MetaInfo {
        String creatorId;
        String lastEditorId;
        long createdTime;
        long lastModifiedTime;
    }

    private Map<String, MetaInfo> docMeta = new HashMap<>();

    // 각 문서에 접속한 사용자 집합 (문서명 -> 사용자 집합)
    private Map<String, String> userCurrentDoc;              // 사용자가 현재 열어둔 문서 정보 (사용자 -> 문서명)
    private final Set<String> onlineUsers = new HashSet<>(); // 현재 로그인 중인 전체 사용자

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

    // 서버 스텁을 전달받아 내부 데이터 구조를 초기화한다.
    public CMServerEventHandler(CMServerStub serverStub) {
        m_serverStub = serverStub;
        documents = new HashMap<>();
        docUsers = new HashMap<>();
        userCurrentDoc = new HashMap<>();
    }

    // 문서 파일들이 저장되는 폴더 경로 (상대 경로)
    private final String DOC_FOLDER = "documents";

    /**
     * 파일 시스템에서 지정된 문서의 내용을 읽어서 반환한다.
     *
     * @param docName 문서 이름
     * @return 파일에서 읽은 문서 내용, 파일이 없으면 빈 문자열 반환
     */
    private String loadDocumentFromFile(String docName) {
        // "documents" 폴더 생성 (없으면)
        File folder = new File(DOC_FOLDER);
        if (!folder.exists()) {
            folder.mkdir();
        }
        // 문서 파일 경로: 예) documents/<docName>.txt
        String filename = DOC_FOLDER + File.separator + docName + ".txt";
        File file = new File(filename);
        if (!file.exists()) {
            System.out.println("파일 [" + filename + "] 가 존재하지 않음. 빈 내용 반환.");
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            // 파일의 각 줄을 읽어 StringBuilder에 추가
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
        return sb.toString().trim();
    }

    private boolean saveDocumentToFile(String docName, String content) {
        // documents 폴더가 없으면 생성
        File folder = new File(DOC_FOLDER);
        if (!folder.exists()) {
            folder.mkdir();
        }
        // 파일 경로 생성
        String filename = DOC_FOLDER + File.separator + docName + ".txt";
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(content);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 실제 파일 시스템에서 지정된 문서 파일을 삭제한다.
     *
     * @param docName 삭제할 문서 이름
     * @return 삭제 성공 시 true, 실패 시 false 반환
     */
    private boolean deleteDocumentFile(String docName) {
        File folder = new File(DOC_FOLDER);
        if (!folder.exists()) {
            // Folder가 없으면 삭제할 문서도 존재하지 않는다고 볼 수 있음.
            return false;
        }
        String filename = DOC_FOLDER + File.separator + docName + ".txt";
        File file = new File(filename);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                System.err.println("Failed to delete file: " + filename);
            }
            return deleted;
        }
        return false;
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


    /**
     * processEvent() 메서드는 CM 사용자 이벤트를 처리한다.
     * 각 이벤트 타입에 따라 문서 생성, 선택, 편집, 저장, 삭제, 목록 조회 등의 작업을 수행한다.
     */
    @Override
    public void processEvent(CMEvent cme) {
        int nType = cme.getType();

        /* ---------- A. 세션 이벤트: 로그인/로그아웃 ---------- */
        if (nType == CMInfo.CM_SESSION_EVENT) {
            CMSessionEvent se = (CMSessionEvent) cme;
            String user = se.getUserName();

            switch (se.getID()) {
                case CMSessionEvent.LOGIN -> {
                    if (onlineUsers.contains(user)) {
                        // 중복 로그인 거부 메시지 전송
                        CMUserEvent rejectEvent = new CMUserEvent();
                        rejectEvent.setStringID("LOGIN_REJECTED_DUPLICATE");
                        m_serverStub.send(rejectEvent, user);
                        System.out.println("[SERVER] 중복 로그인 시도 거부: " + user);
                        return;  // 더 이상 처리하지 않음
                    }

                    onlineUsers.add(user);
                    sendOnlineListToClient(user);
                    System.out.println("[SERVER] " + user + " logged in. (online=" + onlineUsers.size() + ")");

                    CMUserEvent successEvent = new CMUserEvent();
                    successEvent.setStringID("LOGIN_ACCEPTED");
                    m_serverStub.send(successEvent, user);


                }
                case CMSessionEvent.LOGOUT, CMSessionEvent.SESSION_REMOVE_USER -> {
                    onlineUsers.remove(user);
                    String doc = userCurrentDoc.get(user);
                    if (doc != null && docUsers.containsKey(doc)) {
                        docUsers.get(doc).remove(user);
                        broadcastUserList(doc);
                    }
                    userCurrentDoc.remove(user);

                    System.out.println("[SERVER] " + user + " logged out. (online=" + onlineUsers.size() + ")");
                }
            }
            return;  // 세션 이벤트 처리 끝
        }

        if (nType == CMInfo.CM_USER_EVENT) {
            CMUserEvent ue = (CMUserEvent) cme;
            String eventID = ue.getStringID();
            String user = ue.getSender();

            switch (eventID) {

                // 새 문서 생성 요청 처리
                case "CREATE_DOC":
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

                    // 새 문서를 in-memory에 추가 (초기 내용은 빈 문자열)
                    documents.put(docNameToCreate, "");
                    // 문서 생성 직후
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

                    // 생성된 문서를 해당 사용자에게 전송하여 편집 화면 업데이트 요청
                    sendTextUpdateToClient(user, docNameToCreate);
                    broadcastDocumentList();
                    sendDocContentToClient(user, docNameToCreate);  // 생성 직후 문서 내용 보내기
                    break;

                // 문서 선택 요청 처리
                case "SELECT_DOC":
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

                // 문서 편집 이벤트 처리
                case "EDIT_DOC":
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

                // 문서 저장 이벤트 처리
                case "SAVE_DOC":
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

                // 서버가 요청한 문서 목록 조회 이벤트 처리: documents 폴더 내의 모든 .txt 파일 목록 반환
                case "LIST_DOCS":
                    File[] files = new File(DOC_FOLDER).listFiles((d, n) -> n.endsWith(".txt"));
                    CMUserEvent listReply = new CMUserEvent();
                    listReply.setStringID("LIST_REPLY");

                    JSONArray jsonDocs = new JSONArray();
                    if (files != null) {
                        for (File file : files) {
                            String name = file.getName().replaceAll("\\.txt$", "");
                            JSONObject obj = new JSONObject();
                            MetaInfo m = docMeta.get(name);
                            obj.put("name", name);
                            obj.put("creatorId", m != null ? m.creatorId : "unknown");
                            obj.put("lastEditorId", m != null ? m.lastEditorId : "unknown");
                            obj.put("createdTime", m != null ? new Date(m.createdTime).toString() : "unknown");
                            obj.put("lastModifiedTime", m != null ? new Date(m.lastModifiedTime).toString() : new Date(file.lastModified()).toString());
                            jsonDocs.put(obj);
                        }
                    }
                    listReply.setEventField(CMInfo.CM_STR, "docs_json", jsonDocs.toString());
                    m_serverStub.send(listReply, user);

                    System.out.println("문서 목록(JSON) 전송 완료 (" + user + ")");
                    break;


                // 삭제 가능한 문서 목록 조회 이벤트 처리: in-memory의 문서 목록 전달
                case "LIST_DOCS_FOR_DELETE":
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

                // 문서 삭제 이벤트 처리
                case "DELETE_DOC":
                    String toDelete = ue.getEventField(CMInfo.CM_STR, "name");
                    if (toDelete == null || !documents.containsKey(toDelete)) {
                        System.out.println("Delete failed: Document [" + toDelete + "] does not exist.");
                        break;
                    }
                    // 삭제 전, 해당 문서의 사용자들에게 알림 전송
                    String requester = ue.getSender();
                    Set<String> usersToNotify = docUsers.getOrDefault(toDelete, Set.of());
                    for (String u : usersToNotify) {
                        if (!u.equals(requester)) {
                            CMUserEvent kickEvt = new CMUserEvent();
                            kickEvt.setStringID("DOC_CLOSED");
                            kickEvt.setEventField(CMInfo.CM_STR, "name", toDelete);
                            m_serverStub.send(kickEvt, u);
                        }
                    }

                    // in-memory 데이터에서 해당 문서를 삭제
                    documents.remove(toDelete);
                    docUsers.remove(toDelete);
                    for (String u : userCurrentDoc.keySet()) {
                        if (toDelete.equals(userCurrentDoc.get(u))) {
                            userCurrentDoc.put(u, null);
                        }
                    }
                    // 실제 파일 시스템에서 문서 파일 삭제
                    if (deleteDocumentFile(toDelete)) {
                        System.out.println("Document [" + toDelete + "] successfully deleted from disk and memory.");
                    } else {
                        System.err.println("Document [" + toDelete + "] removed from memory, but file deletion failed.");
                    }
                    broadcastDocumentList();
                    break;

                default:
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
        updateEvent.setEventField(CMInfo.CM_STR, "name", docName);      // ✅ 추가!
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
