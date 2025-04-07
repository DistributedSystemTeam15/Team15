package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class LoginScreen extends JPanel {
    private JTextField txtUserId;
    private JPasswordField txtPassword;
    private JButton btnLogin;

    public LoginScreen() {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);

        JLabel lblUserId = new JLabel("User ID:");
        txtUserId = new JTextField(15);
        JLabel lblPassword = new JLabel("Password:");
        txtPassword = new JPasswordField(15);
        btnLogin = new JButton("LOG IN");

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        add(lblUserId, gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        add(txtUserId, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        add(lblPassword, gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        add(txtPassword, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        add(btnLogin, gbc);
    }

    public void addLoginListener(ActionListener listener) {
        btnLogin.addActionListener(listener);
    }

    public String getUserId() {
        return txtUserId.getText();
    }

    public String getPassword() {
        return new String(txtPassword.getPassword());
    }
}