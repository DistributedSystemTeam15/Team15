package gui.dialog;

import gui.util.ServerConfig;

import javax.swing.*;
import java.awt.GridLayout;

public class ConnectionDialog {
    public static ServerConfig getServerConfig() {
        while (true) {
            JPanel panel = new JPanel(new GridLayout(2, 2));

            panel.add(new JLabel("Server IP:"));
            JTextField ipField = new JTextField("localhost");
            panel.add(ipField);

            panel.add(new JLabel("Port (default 7777):"));
            JTextField portField = new JTextField("7777");
            panel.add(portField);

            int result = JOptionPane.showConfirmDialog(null, panel, "Server Connection", JOptionPane.OK_CANCEL_OPTION);
            if (result != JOptionPane.OK_OPTION) return null;

            String ip = ipField.getText().trim();
            String portStr = portField.getText().trim();

            if (ip.isBlank()) {
                JOptionPane.showMessageDialog(
                        null, "IP / Hostname must not be empty!",
                        "Error", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            int port;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                port = 7777;
            }
            return new ServerConfig(ip, port);
        }
    }
}
