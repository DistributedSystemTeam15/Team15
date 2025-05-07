package cm;

import kr.ac.konkuk.ccslab.cm.stub.CMServerStub;

public class CMServerApp {
    private CMServerStub m_serverStub;              // 서버와 클라이언트 간 통신을 담당하는 CM 서버 스텁
    private CMServerEventHandler m_eventHandler;    // 서버 이벤트를 처리하는 이벤트 핸들러

    public CMServerApp() {
        // CM 서버 스텁 생성 (서버 설정 파일(cm-server.conf)을 기반으로 동작)
        m_serverStub = new CMServerStub();
        // 서버 이벤트 핸들러 생성 (클라이언트로부터 도착하는 이벤트를 처리)
        m_eventHandler = new CMServerEventHandler(m_serverStub);
        // 생성한 이벤트 핸들러를 CM 서버 스텁에 등록하여 이벤트가 전달되도록 함
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
