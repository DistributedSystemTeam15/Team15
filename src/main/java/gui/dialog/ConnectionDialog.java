package gui.dialog;

import gui.util.DialogUtil;
import gui.util.ServerConfig;

import javax.swing.*;
import java.awt.GridLayout;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class ConnectionDialog {
    public static ServerConfig getServerConfig() {
        while (true) {
            JPanel panel = new JPanel(new GridLayout(2, 2));
            panel.add(new JLabel("Server IP / Hostname:"));
            JTextField ipField = new JTextField("localhost");
            panel.add(ipField);
            panel.add(new JLabel("Port (default 7777):"));
            JTextField portField = new JTextField("7777");
            panel.add(portField);

            int result = JOptionPane.showConfirmDialog(
                    null, panel, "Server Connection", JOptionPane.OK_CANCEL_OPTION);
            if (result != JOptionPane.OK_OPTION) return null;

            String ip = ipField.getText().trim();
            String portStr = portField.getText().trim();
            if (ip.isEmpty()) {
                DialogUtil.showErrorMessage("IP / Hostname must not be empty.");
                return null;
            }
            try {                                // DNS·IP 해석 시도
                InetAddress.getByName(ip);
            } catch (UnknownHostException ex) {
                DialogUtil.showErrorMessage("Invalid address: " + ip);
                continue;                        // 재입력
            }

            int port = 7777;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException ignore) {
            }

            return new ServerConfig(ip, port);   // 유효 ⇒ 반환
        }
    }
}
