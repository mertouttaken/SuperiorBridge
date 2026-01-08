package com.bgsoftware.ssbbridge.common.messaging.packets;

import com.bgsoftware.ssbbridge.common.messaging.Packet;

public class IslandSavePacket extends Packet {
    private final String islandUuid;
    private final byte[] schematicData;

    public IslandSavePacket(String senderId, String islandUuid, byte[] schematicData) {
        super(senderId, "ISLAND_SAVE");
        this.islandUuid = islandUuid;
        this.schematicData = schematicData;
    }

    public String getIslandUuid() { return islandUuid; }
    public byte[] getSchematicData() { return schematicData; }
}