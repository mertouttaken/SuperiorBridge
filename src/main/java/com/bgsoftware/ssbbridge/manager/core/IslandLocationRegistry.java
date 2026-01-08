package com.bgsoftware.ssbbridge.manager.core;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * IslandUUID -> ServerID eşleşmesini yönetir.
 * Bu sınıf ada oluşturma ve taşıma işlemlerinde otoritedir.
 */
public class IslandLocationRegistry {

    // IslandUUID -> ServerID
    private final Map<UUID, String> islandLocations = new ConcurrentHashMap<>();

    // PlayerUUID -> IslandUUID (Hızlı lookup için önbellek)
    private final Map<UUID, UUID> playerIslands = new ConcurrentHashMap<>();

    /**
     * Bir adanın konumunu günceller.
     */
    public void setIslandLocation(UUID islandId, String serverId) {
        islandLocations.put(islandId, serverId);
    }

    /**
     * Bir adanın hangi sunucuda olduğunu döner.
     */
    public Optional<String> getIslandServer(UUID islandId) {
        return Optional.ofNullable(islandLocations.get(islandId));
    }

    /**
     * Adanın kaydını siler (Disband durumunda).
     */
    public void removeIsland(UUID islandId) {
        islandLocations.remove(islandId);
        // Player cache'ini temizlemek daha kompleks bir logic gerektirebilir,
        // burada basit tutuyoruz.
    }

    /**
     * Oyuncuyu bir adaya mapler.
     */
    public void cachePlayerIsland(UUID player, UUID island) {
        playerIslands.put(player, island);
    }

    public Optional<UUID> getPlayerIsland(UUID player) {
        return Optional.ofNullable(playerIslands.get(player));
    }
}