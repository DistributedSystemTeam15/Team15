package gui.dialog;

import java.awt.GridLayout;
import javax.swing.*;

import cm.CMClientApp;

public class LoginDialog {
    private final CMClientApp clientApp;

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

        System.out.println("[DEBUG] Enter showLoginDialog()");
        int result = JOptionPane.showConfirmDialog(null, panel, "Login", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) {
            System.out.println("[DEBUG] showLoginDialog() returns: false");
            return false;
        }

        String id = idField.getText().trim();
        String pw = new String(pwField.getPassword()).trim();
        if (id.isEmpty()) {
            System.out.println("[DEBUG] showLoginDialog() returns: false");
            return false;
        }

        clientApp.loginAsync(id, pw);                // 비동기 로그인 요청
        System.out.println("로그인 요청 전송 완료: ID=" + id);
        System.out.println("[DEBUG] showLoginDialog() returns: true");
        return true;                                 // 요청 전송 성공
//        // 1) 로그인 요청 전송
//        clientApp.loginAsync(id, pw);
//        System.out.println("[DEBUG] 로그인 요청 전송 완료: ID=" + id);
//
//        // 2) 최대 3초 동안 서버 응답을 기다림
//        long deadline = System.currentTimeMillis() + 3000;
//        while (System.currentTimeMillis() < deadline && !clientApp.getState().isLoggedIn()) {
//            try {
//                Thread.sleep(100);
//
//            } catch (InterruptedException ignored) {
//            }
//        }
//
//        // 3) 로그인 결과 반환
//        if (clientApp.getState().isLoggedIn()) {
//            System.out.println("[DEBUG] showLoginDialog(): 로그인 성공 → true 반환");
//            return true;
//
//        } else {
//            System.out.println("[DEBUG] showLoginDialog(): 로그인 실패 → false 반환");
//            // 로그인 실패하면 CM 연결 종료
//            clientApp.disconnect();
//            // 사용자에게도 에러 메시지 띄우기
//            JOptionPane.showMessageDialog(
//                    null,
//                    "Login failed (duplicate ID or authentication error)",
//                    "Error",
//                    JOptionPane.ERROR_MESSAGE
//            );
//            return false;
//        }
    }
}
