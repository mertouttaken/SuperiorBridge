package com.bgsoftware.ssbbridge.common.messaging.packets;

import com.bgsoftware.ssbbridge.common.messaging.Packet;

public class DataRequestPacket extends Packet {

    private final String dataType; // ISLAND veya PLAYER
    private final String key;

    public DataRequestPacket(String senderId, String dataType, String key) {
        super(senderId, "DATA_REQUEST");
        this.dataType = dataType;
        this.key = key;
    }

    public String getDataType() {
        return dataType;
    }

    public String getKey() {
        return key;
    }
}