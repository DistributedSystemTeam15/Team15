package gui.util;

public class ServerConfig {
    private String serverIP;
    private int port;

    public ServerConfig(String serverIP, int port) {
        this.serverIP = serverIP;
        this.port = port;
    }

    public String getServerIP() {
        return serverIP;
    }

    public int getPort() {
        return port;
    }
}
