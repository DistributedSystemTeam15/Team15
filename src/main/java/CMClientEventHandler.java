import javax.swing.*;

import kr.ac.konkuk.ccslab.cm.event.CMEvent;
import kr.ac.konkuk.ccslab.cm.event.CMUserEvent;
import kr.ac.konkuk.ccslab.cm.event.handler.CMAppEventHandler;
import kr.ac.konkuk.ccslab.cm.info.CMInfo;
import kr.ac.konkuk.ccslab.cm.stub.CMClientStub;
import kr.ac.konkuk.ccslab.cm.event.CMSessionEvent;

public class CMClientEventHandler implements CMAppEventHandler {
	private CMClientStub m_clientStub;
	private CMClientApp m_clientApp;    // GUI 및 상태에 접근하기 위한 클라이언트 앱 참조
	private boolean m_bRemoteUpdating;  // 원격 업데이트 진행 중 플래그

	public CMClientEventHandler(CMClientStub clientStub) {
		m_clientStub = clientStub;
		m_bRemoteUpdating = false;
	}

	public void setClientApp(CMClientApp app) {
		m_clientApp = app;
	}

	// 원격 업데이트 플래그 접근자
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

//        if(nType == CMInfo.CM_SESSION_EVENT) {
//            CMSessionEvent se = (CMSessionEvent) cme;
//            if(se.getID() == CMSessionEvent.LOGIN) {
//                System.out.println("✅ 로그인 완료됨: 사용자 = " + se.getUserName());
//
//                // 메뉴 활성화 - EDT(UI) 스레드에서 실행
////                SwingUtilities.invokeLater(() -> {
////                    m_clientApp.createTextEditorUI();
////                    m_clientApp.getNewItem().setEnabled(true);
////                    m_clientApp.getOpenItem().setEnabled(true);
////                });
//            }
//        }
		if(nType == CMInfo.CM_USER_EVENT) {
			CMUserEvent ue = (CMUserEvent) cme;
			String eventID = ue.getStringID();
			switch(eventID) {
				case "TEXT_UPDATE":
					// 서버로부터 수신한 문서 내용 업데이트 이벤트
					String newContent = ue.getEventField(CMInfo.CM_STR, "content");
					// 텍스트 영역 내용 갱신 (EDT 스레드에서 수행)
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							// 원격 업데이트 적용 시작
							m_bRemoteUpdating = true;
							m_clientApp.getTextArea().setText(newContent);
							m_bRemoteUpdating = false;
							// 문서가 아직 열려있지 않은 상태라면 (초기 전송) 편집 가능하도록 설정
							if(!m_clientApp.isDocOpen()) {
								m_clientApp.getTextArea().setEditable(true);
								// 저장 메뉴 활성화를 위해 docOpen 플래그 설정
								m_clientApp.setDocOpen(true);
								m_clientApp.getSaveItem().setEnabled(true);
							}
						}
					});
					System.out.println("문서 내용 동기화 수신 (TEXT_UPDATE) - 길이: " + (newContent != null ? newContent.length() : 0));
					break;

				case "LIST_REPLY":
					String docListStr = ue.getEventField(CMInfo.CM_STR, "docs");
					if(docListStr == null || docListStr.isEmpty()) {
						JOptionPane.showMessageDialog(null, "No documents available to open.");
						break;
					}

					String[] docNames = docListStr.split(",");
					JList<String> docListUI = new JList<>(docNames);
					JScrollPane scrollPane = new JScrollPane(docListUI);
					int result = JOptionPane.showConfirmDialog(null, scrollPane, "Select Document", JOptionPane.OK_CANCEL_OPTION);

					if(result == JOptionPane.OK_OPTION) {
						String selected = docListUI.getSelectedValue();
						if(selected != null && !selected.trim().isEmpty()) {
							CMUserEvent selectEvent = new CMUserEvent();
							selectEvent.setStringID("SELECT_DOC");
							selectEvent.setEventField(CMInfo.CM_STR, "name", selected.trim());
							m_clientStub.send(selectEvent, "SERVER");

							m_clientApp.setCurrentDocName(selected.trim());
							m_clientApp.getTextArea().setText("");
							m_clientApp.getTextArea().setEditable(false);
							m_clientApp.getSaveItem().setEnabled(false);
							m_clientApp.setDocOpen(false);

							System.out.println("문서 선택 요청 전송됨: " + selected.trim());
						}
					}
					break;
				case "USER_LIST":
					String doc = ue.getEventField(CMInfo.CM_STR, "doc");
					String users = ue.getEventField(CMInfo.CM_STR, "users");
					JOptionPane.showMessageDialog(null, "📄 Current document [" + doc + "] participants: " + users);
					break;
				case "LIST_DOCS_FOR_DELETE":
					String docsStr = ue.getEventField(CMInfo.CM_STR, "docs");
					if(docsStr == null || docsStr.isEmpty()) {
						JOptionPane.showMessageDialog(null, "No documents available for deletion.");
						break;
					}

					String[] docs = docsStr.split(",");
					JList<String> listUI = new JList<>(docs);
					scrollPane = new JScrollPane(listUI);
					result = JOptionPane.showConfirmDialog(null, scrollPane, "Select a document to delete", JOptionPane.OK_CANCEL_OPTION);

					if(result == JOptionPane.OK_OPTION) {
						String selectedDoc = listUI.getSelectedValue();
						if(selectedDoc != null && !selectedDoc.trim().isEmpty()) {
							int confirm = JOptionPane.showConfirmDialog(null, "Are you sure you want to delete document [" + selectedDoc + "]?", "Delete Confirmation", JOptionPane.YES_NO_OPTION);
							if(confirm == JOptionPane.YES_OPTION) {
								CMUserEvent delEvent = new CMUserEvent();
								delEvent.setStringID("DELETE_DOC");
								delEvent.setEventField(CMInfo.CM_STR, "name", selectedDoc.trim());
								m_clientStub.send(delEvent, "SERVER");
								System.out.println("문서 삭제 요청 전송됨: " + selectedDoc.trim());
							}
						}
					}
					break;

				// (필요 시 CREATE_DOC, SELECT_DOC에 대한 확인 응답이나 오류 처리 이벤트를 정의했다면 여기에 처리 가능)
				default:
					// 기타 이벤트 타입은 별도 처리하지 않음
					break;

			}
		}
		// (로그인 응답 등 다른 타입의 이벤트 처리 생략 - 기본 CM 동작이 처리)
	}
}
