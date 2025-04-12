package cm;

import gui.MainFrame;
import kr.ac.konkuk.ccslab.cm.event.CMEvent;
import kr.ac.konkuk.ccslab.cm.event.CMUserEvent;
import kr.ac.konkuk.ccslab.cm.event.handler.CMAppEventHandler;
import kr.ac.konkuk.ccslab.cm.info.CMInfo;
import kr.ac.konkuk.ccslab.cm.stub.CMClientStub;

public class CMClientEventHandler implements CMAppEventHandler {
    private CMClientStub m_clientStub;
    private CMClientApp m_clientApp;    // 클라이언트 앱의 상태(문서, 사용자 등)에 접근하기 위한 참조
    private MainFrame mainFrame;        // UI 업데이트를 담당하는 객체 (gui.MainFrame)
    private boolean m_bRemoteUpdating;  // 원격 업데이트 진행 중 플래그

    public CMClientEventHandler(CMClientStub clientStub) {
        m_clientStub = clientStub;
        m_bRemoteUpdating = false;
    }

    public void setClientApp(CMClientApp app) {
        m_clientApp = app;
    }

    // MainFrame 객체는 반드시 주입되어야 함
    public void setClientUI(MainFrame ui) {
        if (ui == null) {
            throw new IllegalArgumentException("MainFrame 객체는 null일 수 없습니다.");
        }
        this.mainFrame = ui;
    }

    // 원격 업데이트 플래그 setter/getter
    public void setRemoteUpdating(boolean b) {
        m_bRemoteUpdating = b;
    }

    public boolean isRemoteUpdating() {
        return m_bRemoteUpdating;
    }

    @Override
    public void processEvent(CMEvent cme) {
        System.out.println("📥 이벤트 수신됨! 타입: " + cme.getType());
        int nType = cme.getType();

        if (nType == CMInfo.CM_USER_EVENT) {
            CMUserEvent ue = (CMUserEvent) cme;
            String eventID = ue.getStringID();
            switch (eventID) {
                case "TEXT_UPDATE":
                    // 서버로부터 수신한 문서 내용 업데이트 이벤트 처리
                    String newContent = ue.getEventField(CMInfo.CM_STR, "content");
                    mainFrame.updateTextContent(newContent);
                    System.out.println("문서 내용 동기화 수신 (TEXT_UPDATE) - 길이: "
                            + (newContent != null ? newContent.length() : 0));
                    break;

                case "LIST_REPLY":
                    // 문서 목록 응답 이벤트 처리
                    String docListStr = ue.getEventField(CMInfo.CM_STR, "docs");
                    if (docListStr == null || docListStr.isEmpty()) {
                        mainFrame.showNoDocumentsAvailable();
                        break;
                    }
                    String[] docNames = docListStr.split(",");
                    String selected = mainFrame.promptDocumentSelection(docNames);
                    if (selected != null && !selected.trim().isEmpty()) {
                        CMUserEvent selectEvent = new CMUserEvent();
                        selectEvent.setStringID("SELECT_DOC");
                        selectEvent.setEventField(CMInfo.CM_STR, "name", selected.trim());
                        m_clientStub.send(selectEvent, "SERVER");

                        m_clientApp.setCurrentDocName(selected.trim());
                        mainFrame.resetDocumentView();
                        System.out.println("문서 선택 요청 전송됨: " + selected.trim());
                    }
                    break;

                case "USER_LIST":
                    // 사용자 목록 응답 이벤트 처리
                    String doc = ue.getEventField(CMInfo.CM_STR, "doc");
                    String users = ue.getEventField(CMInfo.CM_STR, "users");
                    mainFrame.showUserList(doc, users);
                    break;

                case "LIST_DOCS_FOR_DELETE":
                    // 문서 삭제 가능한 목록 응답 이벤트 처리
                    String docsStr = ue.getEventField(CMInfo.CM_STR, "docs");
                    if (docsStr == null || docsStr.isEmpty()) {
                        mainFrame.showNoDocumentsForDeletion();
                        break;
                    }
                    String[] docs = docsStr.split(",");
                    String selectedDoc = mainFrame.promptDocumentDeletion(docs);
                    if (selectedDoc != null && !selectedDoc.trim().isEmpty()
                            && mainFrame.confirmDocumentDeletion(selectedDoc)) {
                        CMUserEvent delEvent = new CMUserEvent();
                        delEvent.setStringID("DELETE_DOC");
                        delEvent.setEventField(CMInfo.CM_STR, "name", selectedDoc.trim());
                        m_clientStub.send(delEvent, "SERVER");
                        System.out.println("문서 삭제 요청 전송됨: " + selectedDoc.trim());
                    }
                    break;

                default:
                    System.out.println("알 수 없는 이벤트 타입: " + eventID);
                    break;
            }
        }
        // 로그인 응답 등 다른 이벤트 타입은 기본 CM 동작에 의해 처리됨
    }
}
