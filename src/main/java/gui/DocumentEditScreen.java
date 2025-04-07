package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class DocumentEditScreen extends JPanel {
    private JButton btnSave;
    private JPanel toolBar;
    private JTextArea textArea;

    public DocumentEditScreen() {
        setLayout(new BorderLayout());

        // 상단 네비게이션 바 (문서 저장 버튼)
        JPanel navBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        btnSave = new JButton("Save");
        navBar.add(btnSave);

        // 네비게이션 바로 하단의 도구 모음 (임의의 편집 기능 버튼들)
        toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JButton btnBold = new JButton("B");
        JButton btnItalic = new JButton("I");
        JButton btnUnderline = new JButton("U");
        toolBar.add(btnBold);
        toolBar.add(btnItalic);
        toolBar.add(btnUnderline);

        // 상단 영역 전체를 합치는 패널 (네비게이션 바 + 도구 모음)
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(navBar, BorderLayout.NORTH);
        topPanel.add(toolBar, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        // 나머지 영역: 문서 편집 영역 (텍스트 에디터)
        textArea = new JTextArea();
        JScrollPane scrollPane = new JScrollPane(textArea);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void addSaveListener(ActionListener listener) {
        btnSave.addActionListener(listener);
    }

    public String getDocumentText() {
        return textArea.getText();
    }

    public void setDocumentText(String text) {
        textArea.setText(text);
    }
}