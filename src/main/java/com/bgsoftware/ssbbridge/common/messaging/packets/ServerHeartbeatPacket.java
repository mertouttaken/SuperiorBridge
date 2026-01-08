package com.bgsoftware.ssbbridge.common.messaging.packets;

import com.bgsoftware.ssbbridge.common.messaging.Packet;

public class ServerHeartbeatPacket extends Packet {

    private final String host;
    private final int port;
    private final int onlinePlayers;
    private final double tps;
    private final int activeIslands;

    public ServerHeartbeatPacket(String senderId, String host, int port, int onlinePlayers, double tps, int activeIslands) {
        super(senderId, "HEARTBEAT");
        this.host = host;
        this.port = port;
        this.onlinePlayers = onlinePlayers;
        this.tps = tps;
        this.activeIslands = activeIslands;
    }

    // Getters... (Lombok @Data varsayılabilir ama manuel yazıyorum)
    public int getOnlinePlayers() { return onlinePlayers; }
    public int getActiveIslands() { return activeIslands; }
}