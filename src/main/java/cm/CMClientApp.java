package cm;

import javax.swing.JOptionPane;
import kr.ac.konkuk.ccslab.cm.stub.CMClientStub;
import gui.LoginScreen;
import gui.MainFrame;

public class CMClientApp {
    private CMClientStub m_clientStub;
    private CMClientEventHandler m_eventHandler;

    // 문서 관리 관련 필드
    private boolean docOpen;  // 현재 문서가 열려 있는지 여부 (초기 false)
    private String currentDocName = "";

    public CMClientApp() {
        // 클라이언트 스텁과 이벤트 핸들러 생성
        m_clientStub = new CMClientStub();
        m_eventHandler = new CMClientEventHandler(m_clientStub);
        docOpen = false;
    }

    public CMClientStub getClientStub() {
        return m_clientStub;
    }

    public CMClientEventHandler getClientEventHandler() {
        return m_eventHandler;
    }

    public boolean isDocOpen() {
        return docOpen;
    }

    public void setDocOpen(boolean open) {
        docOpen = open;
    }

    public void setCurrentDocName(String name) {
        currentDocName = name;
    }

    public String getCurrentDocName() {
        return currentDocName;
    }

    public static void main(String[] args) {
        // 클라이언트 애플리케이션 실행
        CMClientApp clientApp = new CMClientApp();
        CMClientStub cmStub = clientApp.getClientStub();
        // 이벤트 핸들러 등록 및 CM 클라이언트 시작
        cmStub.setAppEventHandler(clientApp.getClientEventHandler());

        // 서버 연결 설정
        String serverIP = JOptionPane.showInputDialog(null, "Enter the server IP address:", "Server Connection", JOptionPane.PLAIN_MESSAGE);
        if (serverIP == null || serverIP.trim().isEmpty()) {
            System.out.println("서버 IP 입력 취소됨. 프로그램 종료.");
            System.exit(0);
        }
        serverIP = serverIP.trim();

        // 포트 입력 받기
        String portStr = JOptionPane.showInputDialog(null, "Enter the server port number (default 7777):", "Port Input", JOptionPane.PLAIN_MESSAGE);
        int port = 7777;  // 기본 포트
        if (portStr != null && !portStr.trim().isEmpty()) {
            try {
                port = Integer.parseInt(portStr.trim());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Invalid port number. Using default 7777.");
            }
        }

        // 동적으로 서버 주소 및 포트 설정
        cmStub.setServerAddress(serverIP);
        cmStub.setServerPort(port);

        // 서버에 연결
        boolean start = cmStub.startCM();
        if (!start) {
            JOptionPane.showMessageDialog(null, "Server connection failed! IP: " + serverIP);
            System.exit(0);
        }
        System.out.println("CM 클라이언트가 서버에 연결되었습니다.");

        // 로그인 화면 실행
        LoginScreen loginScreen = new LoginScreen(clientApp);
        boolean loggedIn = loginScreen.showLoginDialog();
        if (!loggedIn) {
            JOptionPane.showMessageDialog(null, "로그인 실패로 인해 프로그램을 종료합니다.");
            System.exit(0);
        }

        // 로그인 성공 시 MainFrame 생성 및 UI 객체 등록
        MainFrame mainFrame = new MainFrame(clientApp);
        clientApp.getClientEventHandler().setClientUI(mainFrame);
        mainFrame.show();
    }
}
