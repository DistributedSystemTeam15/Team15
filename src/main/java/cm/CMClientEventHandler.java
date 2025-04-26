package cm;

import gui.util.DialogUtil;
import gui.view.MainFrame;
import kr.ac.konkuk.ccslab.cm.event.CMEvent;
import kr.ac.konkuk.ccslab.cm.event.CMUserEvent;
import kr.ac.konkuk.ccslab.cm.event.handler.CMAppEventHandler;
import kr.ac.konkuk.ccslab.cm.info.CMInfo;
import kr.ac.konkuk.ccslab.cm.stub.CMClientStub;
import kr.ac.konkuk.ccslab.cm.event.CMSessionEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import gui.util.DocumentMeta;
import java.util.List;
import java.util.ArrayList;

public class CMClientEventHandler implements CMAppEventHandler {
    private CMClientStub m_clientStub;  // CM 클라이언트 스텁: 서버와의 통신을 담당하는 객체
    private CMClientApp m_clientApp;    // 클라이언트 상태(문서, 사용자 등)에 접근하기 위한 참조
    private MainFrame mainFrame;        // UI 업데이트를 담당하는 객체 (gui.view.MainFrame)
    private boolean m_bRemoteUpdating;  // 원격 업데이트 진행 중 여부를 나타내는 플래그

    // UI 초기화 이전에 쌓이는 온라인 사용자 버퍼
    private final Set<String> pendingOnline = new HashSet<>();


    // 생성자: 클라이언트 스텁을 초기화하고, 원격 업데이트 플래그를 false로 설정
    public CMClientEventHandler(CMClientStub clientStub) {
        m_clientStub = clientStub;
        m_bRemoteUpdating = false;
    }

    // 클라이언트 애플리케이션 인스턴스를 설정 (이벤트 핸들러에서 클라이언트 상태 접근 시 사용)
    public void setClientApp(CMClientApp app) {
        m_clientApp = app;
    }

    // MainFrame 객체를 주입 (UI 업데이트 시 사용하며, null 체크 포함)
    public void setClientUI(MainFrame ui) {
        this.mainFrame = ui;

        if (!pendingOnline.isEmpty()) {
            mainFrame.setOnlineUsers(pendingOnline);
            pendingOnline.clear();
        }

        // ✅ 여기서 문서 목록 요청
        CMUserEvent listReq = new CMUserEvent();
        listReq.setStringID("LIST_DOCS");
        m_clientStub.send(listReq, "SERVER");
    }


    // 원격 업데이트 플래그 설정 메서드
    public void setRemoteUpdating(boolean b) {
        m_bRemoteUpdating = b;
    }

    // 원격 업데이트 플래그 반환 메서드
    public boolean isRemoteUpdating() {
        return m_bRemoteUpdating;
    }

    @Override
    public void processEvent(CMEvent cme) {
        System.out.println("📥 이벤트 수신됨! 타입: " + cme.getType());
        int nType = cme.getType();

        // 세션 이벤트 (접속 / 해제)
        if (nType == CMInfo.CM_SESSION_EVENT) {
            CMSessionEvent se = (CMSessionEvent) cme;
            String u = se.getUserName();
            switch (se.getID()) {
                case CMSessionEvent.SESSION_ADD_USER -> addOnline(u);
                case CMSessionEvent.SESSION_REMOVE_USER -> removeOnline(u);
            }
            return; // 세션 이벤트 처리 끝
        }

        // 사용자 정의 이벤트
        if (nType == CMInfo.CM_USER_EVENT) {
            CMUserEvent ue = (CMUserEvent) cme;
            String eventID = ue.getStringID();

            switch (eventID) {

                case "ONLINE_LIST":
                    String usersStr = ue.getEventField(CMInfo.CM_STR, "users");
                    if (usersStr != null && !usersStr.isBlank()) {
                        Arrays.stream(usersStr.split(",")).forEach(this::addOnline);
                    }
                    break;

                // 문서 내용 동기화
                case "TEXT_UPDATE": {
                    String newContent = ue.getEventField(CMInfo.CM_STR, "content");
                    mainFrame.updateTextContent(newContent);
                    // resetDocumentView() 호출 삭제 (지우기)
                    break;
                }

                case "DOC_CONTENT": {
                    String docName = ue.getEventField(CMInfo.CM_STR, "name");
                    String content = ue.getEventField(CMInfo.CM_STR, "content");

                    m_clientApp.setCurrentDocName(docName);
                    mainFrame.setCurrentDocument(docName);
                    mainFrame.getDocumentEditScreen().resetDocumentView();
                    mainFrame.updateTextContent(content);
                    mainFrame.getTextArea().setEditable(true); // ✅ 편집 가능 설정
                    mainFrame.setSaveEnabled(true);
                    break;
                }




                // 서버로부터 문서 목록 응답 이벤트 처리
                case "LIST_REPLY":
                    if (mainFrame == null) return;
                    String jsonStr = ue.getEventField(CMInfo.CM_STR, "docs_json");
                    if (jsonStr == null || jsonStr.isBlank()) {
                        mainFrame.showNoDocumentsAvailable();
                        break;
                    }
                    try {
                        JSONArray arr = new JSONArray(jsonStr);
                        List<DocumentMeta> metaList = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            String name = obj.getString("name");
                            String creator = obj.getString("creatorId");
                            String editor = obj.getString("lastEditorId");
                            String createdTime = obj.getString("createdTime");
                            String modifiedTime = obj.getString("lastModifiedTime");
                            metaList.add(new DocumentMeta(name, creator, editor, createdTime, modifiedTime));
                        }
                        mainFrame.setDocumentList(metaList);
                    } catch (Exception e) {
                        e.printStackTrace();
                        mainFrame.showNoDocumentsAvailable();
                    }
                    break;


                // 사용자 목록 응답 이벤트 처리
                case "USER_LIST": {
                    String docName = ue.getEventField(CMInfo.CM_STR, "doc");
                    String userStr = ue.getEventField(CMInfo.CM_STR, "users");
                    List<String> users = userStr != null && !userStr.isBlank() ? List.of(userStr.split(",")) : List.of();

                    // (1) 문서 리스트 갱신
                    mainFrame.updateDocumentUsers(docName, users);

                    // (2) 현재 열려 있는 문서에 대한 상단 표시 갱신
                    String currentDoc = m_clientApp.getCurrentDocName();
                    if (docName != null && docName.equals(currentDoc)) {
                        mainFrame.setCurrentDocumentUsers(users);
                    }

                    // (3) 만약 현재 열려 있는 문서가 삭제되었거나, 유저가 없어진다면 초기화
                    if (currentDoc != null && currentDoc.equals(docName) && users.isEmpty()) {
                        mainFrame.resetDocumentView();
                        m_clientApp.setCurrentDocName(null);
                    }
                    break;
                }



                // 서버로부터 삭제 가능한 문서 목록 응답 이벤트 처리
                case "LIST_DOCS_FOR_DELETE":
                    if (mainFrame == null) return;
                    String docsStr = ue.getEventField(CMInfo.CM_STR, "docs");
                    if (docsStr == null || docsStr.isEmpty()) {
                        mainFrame.showNoDocumentsForDeletion();
                        break;
                    }
                    String[] docs = docsStr.split(",");
                    String selectedDoc = mainFrame.promptDocumentDeletion(docs);
                    if(selectedDoc!=null && !selectedDoc.isBlank()
                            && mainFrame.confirmDocumentDeletion(selectedDoc)){
                        CMUserEvent delEvt = new CMUserEvent();
                        delEvt.setStringID("DELETE_DOC");
                        delEvt.setEventField(CMInfo.CM_STR, "name", selectedDoc.trim());
                        m_clientStub.send(delEvt, "SERVER");
                    }
                    break;
                case "LOGIN_REJECTED_DUPLICATE":
                    System.out.println("[CLIENT] 중복 로그인 거부 응답 수신");
                    DialogUtil.showErrorMessage("This ID is already logged in. Please try again with a different ID.");

                    // 연결 종료 후 클라이언트 종료 → main()에서 로그인 반복 가능
                    m_clientStub.terminateCM();
                    System.exit(1);  // main에서 다시 로그인 시도하게 할 수 있도록 종료
                    break;
                case "LOGIN_ACCEPTED":
                    m_clientApp.setLoginResult(true);

                    // ✅ 1. 안내문 먼저 보여주고
                    if (mainFrame != null) {
                        mainFrame.resetDocumentView();
                    }

                    // ✅ 2. 문서 목록 요청 (서버에 LIST_DOCS 전송)
                    CMUserEvent listReq = new CMUserEvent();
                    listReq.setStringID("LIST_DOCS");
                    m_clientStub.send(listReq, "SERVER");

                    break;



                case "DOC_CLOSED": {
                    String closedDoc = ue.getEventField(CMInfo.CM_STR, "name");

                    if (closedDoc.equals(m_clientApp.getCurrentDocName())) {
                        // ✅ 현재 열려있는 문서면 초기화
                        m_clientApp.setCurrentDocName(null);
                        mainFrame.resetDocumentView();
                        mainFrame.setSaveEnabled(false);
                        mainFrame.setDeleteEnabled(false);
                        DialogUtil.showInfoMessage("The document you were working on has been deleted. It will return to the main screen.");
                    } else {
                        // ✅ 다른 문서라면 경고만
                        DialogUtil.showErrorMessage("Document \"" + closedDoc + "\" has been deleted so can no longer be opened.");
                    }
                    break;
                }

                default:
                    System.out.println("알 수 없는 이벤트 타입: " + eventID);
                    break;
            }
        }
        // 로그인 응답 등 다른 이벤트는 기본 CM 동작에 의해 처리됨
    }
    public MainFrame getMainFrame() {
        return mainFrame;
    }

    private void addOnline(String user) {
        if (user == null || user.isBlank()) return;
        if (mainFrame != null) mainFrame.addOnlineUser(user);
        else pendingOnline.add(user);
    }

    private void removeOnline(String user) {
        if (mainFrame != null) mainFrame.removeOnlineUser(user);
        else pendingOnline.remove(user);
    }
}
