package cm.model;

/**
 * 클라이언트의 로그인·문서 상태를 보관하는 순수 DTO
 * GUI·CM 어느 쪽에도 의존하지 않음
 */
public class ClientState {
    // --- 로그인 & 문서 상태 ---
    private boolean loggedIn   = false;   // (= 기존 loginResult)
    private boolean docOpen    = false;
    private String  currentDoc = "";

    /* ---------- getters / setters ---------- */
    public boolean isLoggedIn()            { return loggedIn; }
    public void    setLoggedIn(boolean v)  { this.loggedIn = v; }

    public boolean isDocOpen()             { return docOpen; }
    public void    setDocOpen(boolean v)   { this.docOpen = v; }

    public String  getCurrentDoc()         { return currentDoc; }
    public void    setCurrentDoc(String n) { this.currentDoc = n == null ? "" : n; }

    /** 초기화(로그아웃) method */
    public void reset() {
        loggedIn   = false;
        docOpen    = false;
        currentDoc = "";
    }
}
