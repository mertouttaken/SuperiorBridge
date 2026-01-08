package com.bgsoftware.ssbbridge.common.messaging.packets;

import com.bgsoftware.ssbbridge.common.messaging.Packet;

/**
 * Manager -> Plugin: "Bu oyuncu gelince ada kur" talimatı.
 */
public class PrepareIslandCreationPacket extends Packet {

    private final String playerUuid;
    private final String schematicName;

    public PrepareIslandCreationPacket(String senderId, String playerUuid, String schematicName) {
        super(senderId, "PREPARE_ISLAND_CREATION");
        this.playerUuid = playerUuid;
        this.schematicName = schematicName;
    }

    public String getPlayerUuid() { return playerUuid; }
    public String getSchematicName() { return schematicName; }
}