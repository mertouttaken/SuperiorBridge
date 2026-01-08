package com.bgsoftware.ssbbridge.manager.core;

import com.bgsoftware.ssbbridge.common.model.ServerNode;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.List;

/**
 * Bağlı olan tüm Skyblock sunucularını yönetir.
 * Tamamen Thread-Safe'dir.
 */
public class ServerRegistry {

    // ServerID -> ServerNode eşleşmesi
    private final Map<String, ServerNode> servers = new ConcurrentHashMap<>();

    // Heartbeat timeout süresi (varsayılan 30sn)
    private static final long HEARTBEAT_TIMEOUT = 30_000L;

    /**
     * Yeni bir sunucu kaydeder veya varsa günceller.
     */
    public void registerOrUpdateServer(String serverId, String host, int port, int players, int islands) {
        servers.compute(serverId, (key, existing) -> {
            if (existing == null) {
                ServerNode newNode = new ServerNode(serverId, host, port);
                newNode.setOnlinePlayers(players);
                newNode.setActiveIslands(islands);
                System.out.println("[Registry] Yeni sunucu kaydedildi: " + serverId);
                return newNode;
            } else {
                existing.updateHeartbeat();
                existing.setOnlinePlayers(players);
                existing.setActiveIslands(islands);
                return existing;
            }
        });
    }

    /**
     * Belirli bir sunucuyu siler (Graceful shutdown durumunda).
     */
    public void unregisterServer(String serverId) {
        if (servers.remove(serverId) != null) {
            System.out.println("[Registry] Sunucu kaydı silindi: " + serverId);
        }
    }

    public Optional<ServerNode> getServer(String serverId) {
        return Optional.ofNullable(servers.get(serverId));
    }

    /**
     * Sadece canlı ve bakımda olmayan sunucuları döner.
     */
    public List<ServerNode> getAvailableServers() {
        long now = System.currentTimeMillis();
        return servers.values().stream()
                .filter(node -> !node.isMaintenance())
                .filter(node -> (now - node.lastHeartbeat) < HEARTBEAT_TIMEOUT) // Reflekşın yerine direkt field access (internal package)
                .collect(Collectors.toList());
    }

    /**
     * Ölü sunucuları temizler (Zamanlanmış görev ile çağrılmalı).
     */
    public void cleanupDeadServers() {
        long now = System.currentTimeMillis();
        servers.entrySet().removeIf(entry -> {
            boolean dead = (now - entry.getValue().lastHeartbeat) > HEARTBEAT_TIMEOUT;
            if (dead) {
                System.out.println("[Registry] Timeout nedeniyle sunucu siliniyor: " + entry.getKey());
            }
            return dead;
        });
    }
}