package gui.dialog;

import java.awt.GridLayout;
import javax.swing.*;

import cm.CMClientApp;

public class LoginDialog {
    private CMClientApp clientApp;

    public LoginDialog(CMClientApp app) {
        this.clientApp = app;
    }

    // 로그인 다이얼로그 표시 후 로그인 성공 여부 반환
    public boolean showLoginDialog() {
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
                boolean success = clientApp.getClientStub().loginCM(id, pw);
                if (success) {
                    System.out.println("로그인 요청 전송 완료: ID=" + id);
                    return true;
                } else {
                    System.err.println("로그인 요청 실패!");
                    return false;
                }
            }
        }
        return false;
    }
}
