package gui.view;

import javax.swing.*;

public class DocumentListScreen {

    public static String promptDocumentSelection(String[] docs, JFrame parentFrame) {
        JList<String> list = new JList<>(docs);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(list);
        int result = JOptionPane.showConfirmDialog(parentFrame, scrollPane, "Select Document", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            return list.getSelectedValue();
        }
        return null;
    }

    public static String promptDocumentDeletion(String[] docs, JFrame parentFrame) {
        JList<String> list = new JList<>(docs);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(list);
        int result = JOptionPane.showConfirmDialog(parentFrame, scrollPane, "Select Document to Delete", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            return list.getSelectedValue();
        }
        return null;
    }

    public static boolean confirmDocumentDeletion(String doc, JFrame parentFrame) {
        int choice = JOptionPane.showConfirmDialog(parentFrame, "Are you sure you want to delete document [" + doc + "]?", "Confirm Deletion", JOptionPane.YES_NO_OPTION);
        return (choice == JOptionPane.YES_OPTION);
    }

    public static void showNoDocumentsAvailable(JFrame parentFrame) {
        JOptionPane.showMessageDialog(parentFrame, "No documents available to open.", "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void showNoDocumentsForDeletion(JFrame parentFrame) {
        JOptionPane.showMessageDialog(parentFrame, "No documents available for deletion.", "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void showUserList(String doc, String users, JFrame parentFrame) {
        JOptionPane.showMessageDialog(parentFrame, "Document: " + doc + "\nParticipants: " + users, "User List", JOptionPane.INFORMATION_MESSAGE);
    }
}
