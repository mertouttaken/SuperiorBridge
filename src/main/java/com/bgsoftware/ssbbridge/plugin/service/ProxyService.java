package com.bgsoftware.ssbbridge.plugin.service;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class ProxyService {

    private final JavaPlugin plugin;

    public ProxyService(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
    }

    /**
     * Oyuncuyu belirtilen sunucuya gönderir.
     */
// ProxyService.java
    public void sendPlayerToServer(Player player, String targetServer) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(targetServer);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }
}