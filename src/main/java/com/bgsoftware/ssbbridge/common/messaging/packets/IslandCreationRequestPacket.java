package com.bgsoftware.ssbbridge.common.messaging.packets;

import com.bgsoftware.ssbbridge.common.messaging.Packet;

public class IslandCreationRequestPacket extends Packet {

    private final String playerUuid;
    private final String schematicName; // Oyuncunun seçtiği schematic (varsa)
    private final String playerName;

    public IslandCreationRequestPacket(String senderId, String playerUuid, String playerName, String schematicName) {
        super(senderId, "ISLAND_CREATE_REQUEST");
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.schematicName = schematicName;
    }

    public String getPlayerUuid() { return playerUuid; }
    public String getSchematicName() { return schematicName; }
    public String getPlayerName() { return playerName; }
}