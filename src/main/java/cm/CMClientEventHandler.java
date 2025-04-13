package cm;

import gui.util.DialogUtil;
import gui.view.MainFrame;
import kr.ac.konkuk.ccslab.cm.event.CMEvent;
import kr.ac.konkuk.ccslab.cm.event.CMUserEvent;
import kr.ac.konkuk.ccslab.cm.event.handler.CMAppEventHandler;
import kr.ac.konkuk.ccslab.cm.info.CMInfo;
import kr.ac.konkuk.ccslab.cm.stub.CMClientStub;

public class CMClientEventHandler implements CMAppEventHandler {
    private CMClientStub m_clientStub;  // CM 클라이언트 스텁: 서버와의 통신을 담당하는 객체
    private CMClientApp m_clientApp;    // 클라이언트 상태(문서, 사용자 등)에 접근하기 위한 참조
    private MainFrame mainFrame;        // UI 업데이트를 담당하는 객체 (gui.view.MainFrame)
    private boolean m_bRemoteUpdating;  // 원격 업데이트 진행 중 여부를 나타내는 플래그

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
        if (ui == null) {
            throw new IllegalArgumentException("MainFrame 객체는 null일 수 없습니다.");
        }
        this.mainFrame = ui;
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

        // CM 사용자 이벤트인 경우 처리
        if (nType == CMInfo.CM_USER_EVENT) {
            CMUserEvent ue = (CMUserEvent) cme;
            String eventID = ue.getStringID();

            switch (eventID) {

                // 서버로부터 수신한 문서 내용 업데이트 이벤트 처리
                case "TEXT_UPDATE":
                    String newContent = ue.getEventField(CMInfo.CM_STR, "content");
                    mainFrame.updateTextContent(newContent);
                    System.out.println("문서 내용 동기화 수신 (TEXT_UPDATE) - 길이: "
                            + (newContent != null ? newContent.length() : 0));
                    break;

                // 서버로부터 문서 목록 응답 이벤트 처리
                case "LIST_REPLY":
                    String docListStr = ue.getEventField(CMInfo.CM_STR, "docs");
                    if (docListStr == null || docListStr.isEmpty()) {
                        mainFrame.showNoDocumentsAvailable();
                        break;
                    }
                    // 서버가 보낸 목록 문자열을 콤마 기준으로 분리
                    String[] docNames = docListStr.split(",");
                    // 문서 선택 다이얼로그를 통해 사용자가 문서를 선택하도록 함
                    String selected = mainFrame.promptDocumentSelection(docNames);
                    if (selected != null && !selected.trim().isEmpty()) {
                        // 만약 선택한 문서가 현재 열려 있는 문서와 동일하다면 재로드 여부 확인
                        if (selected.trim().equals(m_clientApp.getCurrentDocName())) {
                            boolean confirm = DialogUtil.confirm(
                                    "Document [" + selected.trim() + "] is already open.\nDo you want to reload?",
                                    "Reload Document");
                            if (!confirm) {
                                break;
                            }
                        }
                        // 선택한 문서를 열기 위해 SELECT_DOC 이벤트 전송
                        CMUserEvent selectEvent = new CMUserEvent();
                        selectEvent.setStringID("SELECT_DOC");
                        selectEvent.setEventField(CMInfo.CM_STR, "name", selected.trim());
                        m_clientStub.send(selectEvent, "SERVER");

                        // 클라이언트 상태 업데이트: 현재 문서 이름 설정
                        m_clientApp.setCurrentDocName(selected.trim());
                        // 문서 선택 시, 편집 준비를 위해 화면을 리셋함 (편집 불가능 상태에서 업데이트 대기)
                        mainFrame.resetDocumentView();

                        // 저장, 삭제 버튼 활성화 (문서가 열렸으므로)
                        mainFrame.setSaveEnabled(true);
                        mainFrame.setDeleteEnabled(true);
                        System.out.println("문서 선택 요청 전송됨: " + selected.trim());
                    }
                    break;

                // 사용자 목록 응답 이벤트 처리
                case "USER_LIST":
                    String doc = ue.getEventField(CMInfo.CM_STR, "doc");
                    String users = ue.getEventField(CMInfo.CM_STR, "users");
                    mainFrame.showUserList(doc, users);
                    break;

                // 서버로부터 삭제 가능한 문서 목록 응답 이벤트 처리
                case "LIST_DOCS_FOR_DELETE":
                    String docsStr = ue.getEventField(CMInfo.CM_STR, "docs");
                    if (docsStr == null || docsStr.isEmpty()) {
                        mainFrame.showNoDocumentsForDeletion();
                        break;
                    }
                    String[] docs = docsStr.split(",");
                    String selectedDoc = mainFrame.promptDocumentDeletion(docs);
                    if (selectedDoc != null && !selectedDoc.trim().isEmpty()
                            && mainFrame.confirmDocumentDeletion(selectedDoc)) {
                        // 선택한 문서를 삭제하기 위한 DELETE_DOC 이벤트 전송
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
        // 로그인 응답 등 다른 이벤트는 기본 CM 동작에 의해 처리됨
    }
}
