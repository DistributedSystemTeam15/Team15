import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import kr.ac.konkuk.ccslab.cm.stub.CMClientStub;
import kr.ac.konkuk.ccslab.cm.event.CMUserEvent;
import kr.ac.konkuk.ccslab.cm.info.CMInfo;
import javax.swing.undo.UndoManager;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;


public class CMClientApp {
    private JMenuItem newItem, openItem, saveItem;
    private CMClientStub m_clientStub;
    private CMClientEventHandler m_eventHandler;
    private JTextArea textArea;
    private boolean docOpen;  // 현재 문서가 열려 있는지 여부 (초기 false)
    private JMenuItem deleteItem;



    public CMClientApp() {
        // 클라이언트 스텁과 이벤트 핸들러 생성
        m_clientStub = new CMClientStub();
        m_eventHandler = new CMClientEventHandler(m_clientStub);
        // 이벤트 핸들러에 GUI 구성요소 접근을 위한 참조 전달
        m_eventHandler.setClientApp(this);
        docOpen = false;
    }

    public CMClientStub getClientStub() {
        return m_clientStub;
    }
    public CMClientEventHandler getClientEventHandler() {
        return m_eventHandler;
    }
    public JTextArea getTextArea() {
        return textArea;
    }
    public void setDocOpen(boolean open) {
        docOpen = open;
    }
    public boolean isDocOpen() {
        return docOpen;
    }

    // GUI 초기화 및 텍스트 편집기 창 생성
    void createTextEditorUI() {
        JFrame frame = new JFrame("Shared Editing Client (Asynchronous Version)");
        textArea = new JTextArea(25, 50);
        UndoManager undoManager = new UndoManager();

// 편집 가능한 변경 사항을 UndoManager에 등록
        textArea.getDocument().addUndoableEditListener(new UndoableEditListener() {
            public void undoableEditHappened(UndoableEditEvent e) {
                undoManager.addEdit(e.getEdit());
            }
        });

// Ctrl+Z 단축키 등록
        textArea.getInputMap().put(KeyStroke.getKeyStroke("control Z"), "Undo");
        textArea.getActionMap().put("Undo", new AbstractAction("Undo") {
            public void actionPerformed(ActionEvent evt) {
                if (undoManager.canUndo()) {
                    undoManager.undo();
                }
            }
        });


        textArea.setEditable(false);  // 문서 열기 전까지는 편집 비활성화
        JScrollPane scrollPane = new JScrollPane(textArea);

        // 메뉴 바 및 메뉴 항목 설정 (문서 생성/열기/저장 기능)
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        deleteItem = new JMenuItem("Delete Document");
        deleteItem.setEnabled(false);  // 로그인 전에는 비활성화
        newItem = new JMenuItem("New Document");
        openItem = new JMenuItem("Open Document");
        saveItem = new JMenuItem("Save");

        newItem.setEnabled(false);
        openItem.setEnabled(false);
        saveItem.setEnabled(false);  // 초기에 저장 버튼 비활성 (문서 열리면 활성화)

        // 새 문서 만들기 메뉴 동작: 사용자에게 새 문서 이름 입력 받아 CREATE_DOC 이벤트 전송
        newItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String docName = JOptionPane.showInputDialog(frame, "New Document Name:");
                if(docName != null && !docName.trim().isEmpty()) {
                    setCurrentDocName(docName.trim());

                    CMUserEvent ue = new CMUserEvent();
                    ue.setStringID("CREATE_DOC");
                    ue.setEventField(CMInfo.CM_STR, "name", docName.trim());

                    // ✅ 내가 누군지 명시적으로 실어 보내고 싶다면 (선택 사항)
                    String myID = m_clientStub.getMyself().getName();
                    ue.setEventField(CMInfo.CM_STR, "senderID", myID);  // [선택] 로그/확인용

                    // 🔒 로그인 후에만 이벤트 전송되도록 체크
                    if(myID != null && !myID.isEmpty()) {
                        m_clientStub.send(ue, "SERVER");
                        System.out.println("서버에 새 문서 생성 요청 보냄. 보낸 사람: " + myID);
                    } else {
                        System.err.println("로그인되지 않았거나 내 ID를 확인할 수 없음");
                    }

                    textArea.setText("");
                    textArea.setEditable(false);
                    saveItem.setEnabled(false);
                    docOpen = false;
                }
            }
        });
        openItem.addActionListener(e -> {
            // 서버에 문서 목록 요청 (비동기)
            CMUserEvent ue = new CMUserEvent();
            ue.setStringID("LIST_DOCS");
            m_clientStub.send(ue, "SERVER");

            System.out.println("서버에 문서 목록 요청 보냄");
        });
// 🔥 문서 삭제 메뉴 동작 정의
        deleteItem.addActionListener(e -> {
            // 서버에 문서 목록 요청 (삭제용)
            CMUserEvent ue = new CMUserEvent();
            ue.setStringID("LIST_DOCS_FOR_DELETE");
            m_clientStub.send(ue, "SERVER");
            System.out.println("문서 삭제용 목록 요청 보냄");
        });

        // 문서 열기 메뉴 동작: 사용자에게 문서 이름 입력 받아 SELECT_DOC 이벤트 전송
//        openItem.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                String docName = JOptionPane.showInputDialog(frame, "열기 원하는 문서 이름:");
//                if(docName != null && !docName.trim().isEmpty()) {
//                    setCurrentDocName(docName.trim());
//
//                    CMUserEvent ue = new CMUserEvent();
//                    ue.setStringID("SELECT_DOC");
//                    ue.setEventField(CMInfo.CM_STR, "name", docName.trim());
//                    m_clientStub.send(ue, "SERVER");
//                    textArea.setText("");
//                    textArea.setEditable(false);
//                    saveItem.setEnabled(false);
//                    docOpen = false;
//                    System.out.println("서버에 문서 선택 요청: " + docName.trim());
//                }
//            }
//        });
        // 저장 메뉴 동작: 현재 열려있는 문서에 대해 SAVE_DOC 이벤트 전송
        saveItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(docOpen) {
                    String content = textArea.getText();
                    String docName = getCurrentDocName();  // 현재 열려 있는 문서 이름

                    CMUserEvent ue = new CMUserEvent();
                    ue.setStringID("SAVE_DOC");
                    ue.setEventField(CMInfo.CM_STR, "name", docName);
                    ue.setEventField(CMInfo.CM_STR, "content", content);
                    m_clientStub.send(ue, "SERVER");

                    System.out.println("서버에 문서 저장 요청 전송: " + docName);
                }
            }
        });


        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(deleteItem);  // ✅ 삭제 메뉴 추가
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        // 텍스트 영역의 DocumentListener 설정: 문서 내용 변경 시 서버로 EDIT_DOC 이벤트 전송
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            private void sendEditEvent() {
                if(docOpen && !m_eventHandler.isRemoteUpdating()) {
                    // 문서가 열려 있고, 현재 변경이 로컬 입력에 의해 발생한 경우에만 서버로 전송
                    String content = textArea.getText();
                    CMUserEvent ue = new CMUserEvent();
                    ue.setStringID("EDIT_DOC");
                    ue.setEventField(CMInfo.CM_STR, "content", content);
                    m_clientStub.send(ue, "SERVER");
                    // 비동기 버전에서는 별도의 버전 정보 전송 불필요
                }
            }
            @Override
            public void insertUpdate(DocumentEvent e) {
                sendEditEvent();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                sendEditEvent();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                // 일반 텍스트 영역에서는 attributes 변경 없음 - 처리 생략
            }
        });

        frame.add(scrollPane, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);  // 화면 중앙에 창 위치
        frame.setVisible(true);
    }
    private String currentDocName = "";

    public void setCurrentDocName(String name) {
        currentDocName = name;
    }

    public String getCurrentDocName() {
        return currentDocName;
    }

    public JMenuItem getNewItem() { return newItem; }
    public JMenuItem getOpenItem() { return openItem; }
    public JMenuItem getSaveItem() { return saveItem; }
    public JMenuItem getDeleteItem() { return deleteItem; }

    public void showLoginDialog() {
        JPanel panel = new JPanel(new GridLayout(2, 2));
        panel.add(new JLabel("ID:"));
        JTextField idField = new JTextField();
        panel.add(idField);
        panel.add(new JLabel("Password:"));
        JPasswordField pwField = new JPasswordField();
        panel.add(pwField);

        int result = JOptionPane.showConfirmDialog(null, panel, "Login", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String id = idField.getText().trim();
            String pw = new String(pwField.getPassword()).trim();

            if (!id.isEmpty()) {
                boolean success = m_clientStub.loginCM(id, pw);
                if (success) {
                    System.out.println("로그인 요청 전송 완료: ID=" + id);

                    SwingUtilities.invokeLater(() -> {
                        createTextEditorUI(); // 에디터 UI 생성
                        getNewItem().setEnabled(true); // 새 문서
                        getOpenItem().setEnabled(true); // 문서 열기
                        getDeleteItem().setEnabled(true); // ✅ 문서 삭제
                    });
                } else {
                    System.err.println("로그인 요청 실패!");
                }
            }
        } else {
            System.exit(0);  // 로그인 안 하면 종료
        }
    }

    public static void main(String[] args) {
        // 클라이언트 애플리케이션 실행
        CMClientApp clientApp = new CMClientApp();
        CMClientStub cmStub = clientApp.getClientStub();
        // 이벤트 핸들러 등록 및 CM 클라이언트 시작
        cmStub.setAppEventHandler(clientApp.getClientEventHandler());

        String serverIP = JOptionPane.showInputDialog(null, "Enter the server IP address:", "Server Connection", JOptionPane.PLAIN_MESSAGE);
        if(serverIP == null || serverIP.trim().isEmpty()) {
            System.out.println("서버 IP 입력 취소됨. 프로그램 종료.");
            System.exit(0);
        }

        // ✅ 포트 입력 받기
        String portStr = JOptionPane.showInputDialog(null, "Enter the server port number (default 7777):", "Port Input", JOptionPane.PLAIN_MESSAGE);
        int port = 7777;  // 기본 포트
        if(portStr != null && !portStr.trim().isEmpty()) {
            try {
                port = Integer.parseInt(portStr.trim());
            } catch(NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Invalid port number. Using default 7777.");
            }
        }

        serverIP = serverIP.trim();

        // ✅ 동적으로 서버 주소 설정
        cmStub.setServerAddress(serverIP);
        cmStub.setServerPort(7777);  // 기본 포트. 필요시 입력 받게 해도 됨

        // ✅ 서버에 연결
        boolean start = cmStub.startCM();
        if(!start) {
            JOptionPane.showMessageDialog(null, "Server connection failed! IP: " + serverIP);
            System.exit(0);
        }
        System.out.println("CM 클라이언트가 서버에 연결되었습니다.");
        // GUI 생성 (로그인 과정 생략, 기본 설정으로 바로 세션 참여 가정)
        clientApp.showLoginDialog();
        // clientApp.createTextEditorUI();
    }
}
