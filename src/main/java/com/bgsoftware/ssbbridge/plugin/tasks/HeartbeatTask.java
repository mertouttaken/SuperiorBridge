package com.bgsoftware.ssbbridge.plugin.tasks;

import com.bgsoftware.ssbbridge.common.messaging.MessageBroker;
import com.bgsoftware.ssbbridge.common.messaging.packets.ServerHeartbeatPacket;
import com.bgsoftware.ssbbridge.plugin.config.BridgeConfig;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import org.bukkit.Bukkit;

public class HeartbeatTask implements Runnable {

    private final MessageBroker broker;
    private final BridgeConfig config;

    public HeartbeatTask(MessageBroker broker, BridgeConfig config) {
        this.broker = broker;
        this.config = config;
    }

    @Override
    public void run() {
        try {
            // Basit TPS hesabı (veya NMS'den çekilebilir, burada basit tutuyoruz)
            double tps = 20.0;
            int players = Bukkit.getOnlinePlayers().size();

            // SSB2 API'den aktif ada sayısını al
            // Not: Grid boyutu yerine yüklenen ada sayısını almak daha iyi olabilir.
            int activeIslands = SuperiorSkyblockAPI.getGrid().getIslands().size();

            ServerHeartbeatPacket packet = new ServerHeartbeatPacket(
                    config.serverId,
                    Bukkit.getIp(),
                    Bukkit.getPort(),
                    players,
                    tps,
                    activeIslands
            );

            broker.sendPacket(packet, config.managerChannel);

        } catch (Exception e) {
            // Heartbeat hatası logları spamlamamalı
            System.err.println("[SSB-Bridge] Heartbeat failed: " + e.getMessage());
        }
    }
}