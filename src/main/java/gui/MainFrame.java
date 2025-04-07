package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class MainFrame extends JFrame {
    private CardLayout cardLayout;
    private JPanel mainPanel;

    private LoginScreen loginScreen;
    private DocumentListScreen documentListScreen;
    private DocumentEditScreen documentEditScreen;

    public MainFrame() {
        setTitle("Distributed System Computing");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // 각 화면 초기화
        loginScreen = new LoginScreen();
        documentListScreen = new DocumentListScreen();
        documentEditScreen = new DocumentEditScreen();

        // CardLayout에 화면 추가 (카드 이름: "login", "list", "edit")
        mainPanel.add(loginScreen, "login");
        mainPanel.add(documentListScreen, "list");
        mainPanel.add(documentEditScreen, "edit");
        add(mainPanel);

        // 로그인 버튼 클릭 시 -> 문서 목록 화면으로 전환
        loginScreen.addLoginListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String userId = loginScreen.getUserId();
                String password = loginScreen.getPassword();
                // 여기에 실제 로그인 인증 로직을 추가할 수 있음.
                System.out.println("로그인 시도: " + userId);
                // 성공 시 문서 목록 화면 전환
                showScreen("list");
            }
        });

        // 문서 생성 버튼 클릭 시 -> 문서 편집 화면으로 전환 (새 문서 생성)
        documentListScreen.addCreateListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("문서 생성 버튼 클릭");
                // 새 문서 생성 로직 추가 후 편집 화면 전환
                documentEditScreen.setDocumentText(""); // 새 문서 초기화
                showScreen("edit");
            }
        });

        // 문서 삭제 버튼 클릭 시 (예시로 콘솔 출력)
        documentListScreen.addDeleteListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("문서 삭제 버튼 클릭");
                // 문서 삭제 로직 추가 가능
            }
        });

        // 문서 편집 화면의 저장 버튼 클릭 시 -> 문서 저장 후 문서 목록 화면으로 전환
        documentEditScreen.addSaveListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String content = documentEditScreen.getDocumentText();
                System.out.println("문서 저장: " + content);
                // 저장 로직 추가 후 목록 화면으로 전환 (또는 다른 동작)
                showScreen("list");
            }
        });
    }

    public void showScreen(String name) {
        cardLayout.show(mainPanel, name);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}