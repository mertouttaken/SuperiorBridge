package com.bgsoftware.ssbbridge.common.messaging.packets;

import com.bgsoftware.ssbbridge.common.messaging.Packet;

public class PrepareIslandCreationPacket extends Packet {
    private final String playerUuid;
    private final String schematicName;
    private final byte[] schematicData; // Yeni alan

    public PrepareIslandCreationPacket(String senderId, String playerUuid, String schematicName, byte[] schematicData) {
        super(senderId, "PREPARE_ISLAND_CREATION");
        this.playerUuid = playerUuid;
        this.schematicName = schematicName;
        this.schematicData = schematicData;
    }

    public String getPlayerUuid() { return playerUuid; }
    public String getSchematicName() { return schematicName; }
    public byte[] getSchematicData() { return schematicData; }
}