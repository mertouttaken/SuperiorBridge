package com.bgsoftware.ssbbridge.common.messaging.packets;

import com.bgsoftware.ssbbridge.common.messaging.Packet;

public class IslandInvitePacket extends Packet {
    private final String inviterName;
    private final String targetPlayerName;
    private final String islandUuid;

    public IslandInvitePacket(String senderId, String inviterName, String targetPlayerName, String islandUuid) {
        super(senderId, "ISLAND_INVITE");
        this.inviterName = inviterName;
        this.targetPlayerName = targetPlayerName;
        this.islandUuid = islandUuid;
    }

    public String getInviterName() { return inviterName; }
    public String getTargetPlayerName() { return targetPlayerName; }
    public String getIslandUuid() { return islandUuid; }
}