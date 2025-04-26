package gui.view;

import javax.swing.*;
import java.awt.*;

public class DocumentListScreen {

    public static String promptDocumentSelection(String[] docNames, Component parent) {
        return (String) JOptionPane.showInputDialog(
                parent,
                "열 문서를 선택하세요:",
                "문서 선택",
                JOptionPane.PLAIN_MESSAGE,
                null,
                docNames,
                docNames.length > 0 ? docNames[0] : null);
    }

    public static void showNoDocumentsAvailable(Component parent) {
        JOptionPane.showMessageDialog(parent, "사용 가능한 문서가 없습니다.", "알림", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void showUserList(String docName, String users, Component parent) {
        JOptionPane.showMessageDialog(parent,
                "문서: " + docName + "\n참여자: " + users,
                "문서 참여자 목록",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public static void showNoDocumentsForDeletion(Component parent) {
        JOptionPane.showMessageDialog(parent, "삭제할 수 있는 문서가 없습니다.", "문서 삭제", JOptionPane.INFORMATION_MESSAGE);
    }

    public static String promptDocumentDeletion(String[] docNames, Component parent) {
        return (String) JOptionPane.showInputDialog(
                parent,
                "삭제할 문서를 선택하세요:",
                "문서 삭제",
                JOptionPane.PLAIN_MESSAGE,
                null,
                docNames,
                docNames.length > 0 ? docNames[0] : null);
    }

    public static boolean confirmDocumentDeletion(String docName, Component parent) {
        int result = JOptionPane.showConfirmDialog(
                parent,
                "문서 [" + docName + "]를 정말 삭제하시겠습니까?",
                "문서 삭제 확인",
                JOptionPane.YES_NO_OPTION);
        return result == JOptionPane.YES_OPTION;
    }
}
