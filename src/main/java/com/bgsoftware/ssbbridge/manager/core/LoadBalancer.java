package com.bgsoftware.ssbbridge.manager.core;

import com.bgsoftware.ssbbridge.common.model.ServerNode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class LoadBalancer {

    private final ServerRegistry serverRegistry;

    public LoadBalancer(ServerRegistry serverRegistry) {
        this.serverRegistry = serverRegistry;
    }

    /**
     * Ada oluşturmak için en uygun sunucuyu seçer.
     * Strateji: En az aktif adaya sahip, bakımda olmayan ve canlı sunucu.
     */
    public Optional<ServerNode> getOptimalServer() {
        List<ServerNode> availableServers = serverRegistry.getAvailableServers();

        if (availableServers.isEmpty()) {
            return Optional.empty();
        }

        // Basit Algoritma: En az ada sayısı olan sunucuyu seç.
        // Production Notu: Burada CPU load veya TPS verisi de hesaba katılabilir.
        return availableServers.stream()
                .min(Comparator.comparingInt(ServerNode::getActiveIslands));
    }
}