package com.bgsoftware.ssbbridge.plugin.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class BridgeConfig {

    private final JavaPlugin plugin;

    public String serverId;
    public String redisHost;
    public int redisPort;
    public String redisPassword;
    public String managerChannel;
    public int heartbeatInterval;

    public BridgeConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.serverId = config.getString("server-id", "skyblock-1");
        this.redisHost = config.getString("redis.host", "127.0.0.1");
        this.redisPort = config.getInt("redis.port", 6379);
        this.redisPassword = config.getString("redis.password", "");
        this.managerChannel = config.getString("redis.channels.manager", "ssb-manager");
        this.heartbeatInterval = config.getInt("heartbeat-interval", 5);
    }
}