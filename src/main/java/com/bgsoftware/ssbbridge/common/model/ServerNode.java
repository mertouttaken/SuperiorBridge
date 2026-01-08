package com.bgsoftware.ssbbridge.common.model;

import java.util.Objects;

/**
 * Bir Skyblock sunucusunun anlık durumunu (Snapshot) temsil eder.
 * Thread-safe kullanım için tasarlanmıştır.
 */
public class ServerNode {

    private final String serverId;
    private final String host;
    private final int port;

    // Değişken veriler volatile ile thread-safe yapılır (AtomicInteger da kullanılabilir)
    private volatile int onlinePlayers;
    private volatile int activeIslands;
    public volatile long lastHeartbeat;
    private volatile boolean maintenance;

    public ServerNode(String serverId, String host, int port) {
        this.serverId = serverId;
        this.host = host;
        this.port = port;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public String getServerId() { return serverId; }
    public String getHost() { return host; }
    public int getPort() { return port; }

    public int getOnlinePlayers() { return onlinePlayers; }
    public void setOnlinePlayers(int onlinePlayers) { this.onlinePlayers = onlinePlayers; }

    public int getActiveIslands() { return activeIslands; }
    public void setActiveIslands(int activeIslands) { this.activeIslands = activeIslands; }

    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public boolean isAlive(long timeoutThreshold) {
        return (System.currentTimeMillis() - lastHeartbeat) < timeoutThreshold;
    }

    public boolean isMaintenance() { return maintenance; }
    public void setMaintenance(boolean maintenance) { this.maintenance = maintenance; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServerNode that = (ServerNode) o;
        return Objects.equals(serverId, that.serverId);
    }

    @Override
    public int hashCode() { return Objects.hash(serverId); }
}