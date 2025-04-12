package cm;

import kr.ac.konkuk.ccslab.cm.stub.CMServerStub;

public class CMServerApp {
	private CMServerStub m_serverStub;
	private CMServerEventHandler m_eventHandler;

	public CMServerApp() {
		// CM 서버 스텁과 이벤트 핸들러 생성
		m_serverStub = new CMServerStub();
		m_eventHandler = new CMServerEventHandler(m_serverStub);
		// 서버 스텁에 이벤트 핸들러 등록
		m_serverStub.setAppEventHandler(m_eventHandler);
	}

	public void startServer() {
		// CM 서버를 기동 (기본 cm-server.conf 설정 사용)
		m_serverStub.startCM();
		System.out.println("CM 서버가 실행되었습니다.");
	}

	public static void main(String[] args) {
		// 서버 애플리케이션 객체 생성 및 서버 시작
		CMServerApp serverApp = new CMServerApp();
		serverApp.startServer();
	}
}
