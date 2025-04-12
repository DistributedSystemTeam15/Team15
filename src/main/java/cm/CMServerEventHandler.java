package cm;

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

public class CMServerEventHandler implements CMAppEventHandler {
	private CMServerStub m_serverStub;
	// 서버에서 관리하는 문서 목록: 문서 이름 -> 문서 내용
	private Map<String, String> documents;
	// 문서별 참여자(클라이언트) 목록: 문서 이름 -> 해당 문서를 열고 있는 사용자 이름 집합
	private Map<String, Set<String>> docUsers;
	// 사용자별 현재 접속 문서: 사용자 이름 -> 문서 이름 (없으면 null 또는 미존재)
	private Map<String, String> userCurrentDoc;

	public CMServerEventHandler(CMServerStub serverStub) {
		m_serverStub = serverStub;
		documents = new HashMap<>();
		docUsers = new HashMap<>();
		userCurrentDoc = new HashMap<>();
	}

	@Override
	public void processEvent(CMEvent cme) {
		// 이벤트 타입에 따라 분기 처리
		int nType = cme.getType();
		if(nType == CMInfo.CM_USER_EVENT) {
			// 사용자 정의 이벤트인 경우
			CMUserEvent ue = (CMUserEvent) cme;
			String eventID = ue.getStringID();  // 이벤트 식별자 (문자열)
			String user = ue.getSender();       // 이벤트 보낸 사용자 이름 (클라이언트 ID)
			switch(eventID) {
				case "CREATE_DOC":
					String docNameToCreate = ue.getEventField(CMInfo.CM_STR, "name");
					if(user == null || user.trim().isEmpty()) {
						System.err.println("문서 생성 실패: sender 정보 없음 (로그인 안 됐거나 이벤트 너무 빠름)");
						break;
					}
					if(documents.size() >= 10) {
						System.out.println("문서 생성 실패: 최대 문서 개수(10) 초과 시도 by [" + user + "]");
						break;
					}
					if(documents.containsKey(docNameToCreate)) {
						// 이미 존재하는 이름의 문서
						System.out.println("문서 생성 실패: 동일한 이름의 문서 [" + docNameToCreate + "] 이미 존재");
						// 클라이언트에게 실패 알림 이벤트를 보낼 수 있음 (구현 생략)
						break;
					}
					// 새로운 문서 생성
					documents.put(docNameToCreate, "");  // 내용은 빈 문자열로 초기화
					docUsers.put(docNameToCreate, new HashSet<>());
					docUsers.get(docNameToCreate).add(user);
					userCurrentDoc.put(user, docNameToCreate);
					System.out.println("새 문서 생성: [" + docNameToCreate + "], 생성자: " + user);
					// 생성되었으므로 해당 문서를 선택한 것과 동일하게 초기 내용 전송
					sendTextUpdateToClient(user, docNameToCreate);
					break;

				case "SELECT_DOC":
					// 기존 문서 선택(열기) 요청 처리
					String docNameToSelect = ue.getEventField(CMInfo.CM_STR, "name");
					if(!documents.containsKey(docNameToSelect)) {
						// 존재하지 않는 문서 이름인 경우
						System.out.println("문서 선택 실패: [" + docNameToSelect + "] 존재하지 않음 (요청자: " + user + ")");
						// 필요시 클라이언트에 오류 이벤트 전송 가능 (구현 생략)
						break;
					}
					// 사용자가 이미 다른 문서를 열고 있었다면 이전 문서의 참가자 목록에서 제거
					if(userCurrentDoc.containsKey(user)) {
						String prevDoc = userCurrentDoc.get(user);
						if(prevDoc != null) {
							docUsers.get(prevDoc).remove(user);
							System.out.println("사용자 [" + user + "] 기존 문서 [" + prevDoc + "] 편집 종료");
						}
					}
					// 새로운 문서에 참가자 추가 및 현재 문서 매핑
					docUsers.get(docNameToSelect).add(user);
					userCurrentDoc.put(user, docNameToSelect);
					System.out.println("문서 선택: 사용자 [" + user + "] -> 문서 [" + docNameToSelect + "]");
					// 선택한 문서의 현재 전체 텍스트 내용을 해당 사용자에게 전송
					sendTextUpdateToClient(user, docNameToSelect);

					CMUserEvent userListEvent = new CMUserEvent();
					userListEvent.setStringID("USER_LIST");
					userListEvent.setEventField(CMInfo.CM_STR, "doc", docNameToSelect);

					Set<String> users = docUsers.get(docNameToSelect);
					String userList = String.join(",", users);
					userListEvent.setEventField(CMInfo.CM_STR, "users", userList);

					m_serverStub.send(userListEvent, user);
					break;

				case "EDIT_DOC":
					// 클라이언트로부터 편집된 문서 내용 수신
					String newContent = ue.getEventField(CMInfo.CM_STR, "content");
					// 이 편집 이벤트의 대상 문서 이름은 userCurrentDoc 맵으로 확인
					String docName = userCurrentDoc.get(user);
					if(docName == null) {
						System.out.println("편집 오류: [" + user + "] 사용자가 어떤 문서도 열지 않은 상태에서 EDIT_DOC 이벤트 수신");
						break;
					}
					// 서버 측 문서 내용 업데이트
					documents.put(docName, newContent);
					System.out.println("문서 [" + docName + "] 내용 업데이트 by [" + user + "]: 길이=" + newContent.length());
					// 동일 문서를 열고 있는 다른 사용자들에게 변경 내용 전파 (broadcast)
					// 비동기 버전: 편집한 사용자는 제외하고 다른 사용자들에게만 전송
					Set<String> participants = docUsers.get(docName);
					if(participants != null) {
						for(String other : participants) {
							if(other.equals(user)) continue;  // 자기 자신에게는 보내지 않음
							sendTextUpdateToClient(other, docName);
						}
					}
					break;

				case "SAVE_DOC":
					// 문서 저장 요청 처리
					String saveDocName = userCurrentDoc.get(user);
					if(saveDocName == null) {
						System.out.println("저장 실패: [" + user + "] 어떤 문서도 열지 않은 상태");
						break;
					}
					String contentToSave = documents.get(saveDocName);
					boolean saveSuccess = saveDocumentToFile(saveDocName, contentToSave);
					if(saveSuccess) {
						System.out.println("문서 [" + saveDocName + "] 디스크에 저장 성공 (요청자: " + user + ")");
					} else {
						System.err.println("문서 [" + saveDocName + "] 저장 실패! (요청자: " + user + ")");
					}
					// 저장 성공/실패 여부를 클라이언트에게 알려줄 수도 있음 (예: SAVE_ACK 이벤트, 구현 생략)
					break;
				case "LIST_DOCS":
					if(user == null) break;
					CMUserEvent listReply = new CMUserEvent();
					listReply.setStringID("LIST_REPLY");

					String docList = String.join(",", documents.keySet());
					listReply.setEventField(CMInfo.CM_STR, "docs", docList);

					m_serverStub.send(listReply, user);
					System.out.println("문서 목록 요청 처리됨 (" + user + ")");
					break;

				case "LIST_DOCS_FOR_DELETE":
					// 문서 이름들을 클라이언트에 전송
					StringBuilder sb = new StringBuilder();
					for(String doc : documents.keySet()) {
						sb.append(doc).append(",");
					}
					if(sb.length() > 0) sb.setLength(sb.length() - 1);  // 마지막 , 제거

					CMUserEvent listEvent = new CMUserEvent();
					listEvent.setStringID("LIST_DOCS_FOR_DELETE");
					listEvent.setEventField(CMInfo.CM_STR, "docs", sb.toString());
					m_serverStub.send(listEvent, ue.getSender());
					break;

				case "DELETE_DOC":
					String toDelete = ue.getEventField(CMInfo.CM_STR, "name");
					if(toDelete == null || !documents.containsKey(toDelete)) {
						System.out.println("삭제 실패: 존재하지 않는 문서 [" + toDelete + "]");
						break;
					}
					documents.remove(toDelete);
					docUsers.remove(toDelete);
					for(String u : userCurrentDoc.keySet()) {
						if(toDelete.equals(userCurrentDoc.get(u))) {
							userCurrentDoc.put(u, null);
						}
					}
					System.out.println("문서 [" + toDelete + "] 삭제 완료");
					break;


			}
		}
		// (필요시 다른 타입의 이벤트(CMSessionEvent 등) 처리 가능. 본 과제에서는 생략)
	}

	// 특정 사용자에게 해당 문서의 현재 텍스트 내용을 전송하는 헬퍼 메서드
	private void sendTextUpdateToClient(String targetUser, String docName) {
		String content = documents.get(docName);
		if(content == null) content = "";  // 문서가 존재하지 않으면 빈 문자열
		// CMUserEvent 객체 생성 및 필드 설정
		CMUserEvent updateEvent = new CMUserEvent();
		updateEvent.setStringID("TEXT_UPDATE");
		updateEvent.setEventField(CMInfo.CM_STR, "content", content);
		// 서버에서 클라이언트로 전송 (targetUser에게 TCP로 전송)
		m_serverStub.send(updateEvent, targetUser);
	}

	// 문서 내용을 파일로 저장하는 헬퍼 메서드
	private boolean saveDocumentToFile(String docName, String content) {
		String filename = docName + ".txt";
		try(FileWriter writer = new FileWriter(filename)) {
			writer.write(content);
		} catch(IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
}
