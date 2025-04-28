package gui.util;

import java.util.List;
import java.util.ArrayList;

public class DocumentMeta {
    private String name;
    private String creatorId;
    private String lastEditorId;
    private String createdTime;
    private String lastModifiedTime;
    private List<String> activeUsers = new ArrayList<>();  // ✅ 추가

    public DocumentMeta(String name, String creatorId, String lastEditorId,
                        String createdTime, String lastModifiedTime) {
        this.name = name;
        this.creatorId = creatorId;
        this.lastEditorId = lastEditorId;
        this.createdTime = createdTime;
        this.lastModifiedTime = lastModifiedTime;
    }

    public String getName() {
        return name;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public String getLastEditorId() {
        return lastEditorId;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public String getLastModifiedTime() {
        return lastModifiedTime;
    }

    public List<String> getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(List<String> users) {
        this.activeUsers = new ArrayList<>(users);
    }

    @Override
    public String toString() {
        return name + " (Last Modified Time: " + lastModifiedTime + ")";
    }

    public String getDetailedInfo() {
        return "Document Name: " + name +
                "\nCreator ID: " + creatorId +
                "\nLast Editor ID: " + lastEditorId +
                "\nCreated Time: " + createdTime +
                "\nLast Modified Time: " + lastModifiedTime;
    }
}
