package com.bgsoftware.ssbbridge.plugin.listeners;

import com.bgsoftware.ssbbridge.plugin.SSBProxyBridgePlugin;
import com.bgsoftware.ssbbridge.plugin.config.BridgeConfig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import redis.clients.jedis.Jedis;

public class PlayerPresenceListener implements Listener {

    private final SSBProxyBridgePlugin plugin;
    private final BridgeConfig config;

    public PlayerPresenceListener(SSBProxyBridgePlugin plugin, BridgeConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String playerName = event.getPlayer().getName();
        String serverId = config.serverId;

        // Redis'e oturum bilgisini yaz
        try (Jedis jedis = plugin.getJedisPool().getResource()) {
            jedis.set("ssb:player_session:" + playerName, serverId);
            // 1 saatlik timeout (opsiyonel, güvenlik için)
            jedis.expire("ssb:player_session:" + playerName, 3600);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName();

        // Oturumu sil
        try (Jedis jedis = plugin.getJedisPool().getResource()) {
            jedis.del("ssb:player_session:" + playerName);
        }
    }
}