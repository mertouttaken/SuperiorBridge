package com.bgsoftware.ssbbridge.plugin.listeners;

import com.bgsoftware.ssbbridge.common.messaging.MessageBroker;
import com.bgsoftware.ssbbridge.plugin.SSBProxyBridgePlugin;
import com.bgsoftware.ssbbridge.plugin.config.BridgeConfig;
import com.bgsoftware.superiorskyblock.api.events.IslandEnterEvent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import redis.clients.jedis.Jedis;

import java.util.UUID;

public class TeamAwareListener implements Listener {

    private final SSBProxyBridgePlugin plugin;
    private final BridgeConfig config;

    public TeamAwareListener(SSBProxyBridgePlugin plugin, BridgeConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIslandTeleport(IslandEnterEvent event) {
        Player player = event.getPlayer().asPlayer();
        if (player == null) return;

        UUID islandUuid = event.getIsland().getUniqueId();

        // 🧠 KRİTİK: Redis'ten adanın authoritative (mutlak) sunucusunu kontrol et
        // Production'da bir RedisService üzerinden çekmek daha temizdir.
        String targetServerId = getIslandServerFromRedis(islandUuid);

        if (targetServerId != null && !targetServerId.equalsIgnoreCase(config.serverId)) {
            // 🚫 YANLIŞ SUNUCU! Teleportu durdur ve oyuncuyu doğru yere gönder.
            event.setCancelled(true);

            player.sendMessage(ChatColor.YELLOW + "Adanız " + targetServerId + " sunucusunda bulunuyor. Oraya aktarılıyorsunuz...");

            // BungeeCord üzerinden hedef sunucuya gönder
            plugin.getProxyService().sendPlayerToServer(player, targetServerId);
        }
    }

    private String getIslandServerFromRedis(UUID islandId) {
        // RedisBroker'daki JedisPool üzerinden sorgu yapıyoruz
        // Key formatı Manager ile aynı olmalı: ssb:island:{uuid}:server
        try (Jedis jedis = plugin.getJedisPool().getResource()) {
            return jedis.get("ssb:island:" + islandId.toString() + ":server");
        } catch (Exception e) {
            return null;
        }
    }
}