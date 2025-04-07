package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class DocumentListScreen extends JPanel {
    private JButton btnCreate;
    private JButton btnDelete;
    private JPanel documentsPanel;

    public DocumentListScreen() {
        setLayout(new BorderLayout());

        // 상단 네비게이션 바
        JPanel navBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        btnCreate = new JButton("Create");
        btnDelete = new JButton("Delete");
        navBar.add(btnCreate);
        navBar.add(btnDelete);
        add(navBar, BorderLayout.NORTH);

        // 문서 확인 영역 (윈도우의 '아주 큰 아이콘' 보기 방식 유사)
        documentsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 20));
        // 예시 문서 버튼 추가 (실제 구현 시 문서 목록을 동적으로 로드)
        for (int i = 1; i <= 10; i++) {
            JButton docButton = new JButton("<html><center>Document " + i + "</center></html>");
            docButton.setPreferredSize(new Dimension(120, 120));
            // 아이콘을 사용하려면 setIcon() 메서드로 추가 가능
            documentsPanel.add(docButton);
        }
        JScrollPane scrollPane = new JScrollPane(documentsPanel);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void addCreateListener(ActionListener listener) {
        btnCreate.addActionListener(listener);
    }

    public void addDeleteListener(ActionListener listener) {
        btnDelete.addActionListener(listener);
    }

    // 추후 문서 목록 갱신 등 추가 메서드 구현 가능
}