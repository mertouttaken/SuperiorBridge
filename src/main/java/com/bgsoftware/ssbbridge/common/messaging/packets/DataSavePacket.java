package com.bgsoftware.ssbbridge.common.messaging.packets;

import com.bgsoftware.ssbbridge.common.messaging.Packet;

public class DataSavePacket extends Packet {

    public enum DataType {
        ISLAND,
        PLAYER,
        GRID
    }

    private final DataType dataType;
    private final String key; // IslandUUID veya PlayerUUID
    private final String jsonData; // Serileştirilmiş veri

    public DataSavePacket(String senderId, DataType dataType, String key, String jsonData) {
        super(senderId, "DATA_SAVE");
        this.dataType = dataType;
        this.key = key;
        this.jsonData = jsonData;
    }

    public DataType getDataType() {
        return dataType;
    }

    public String getKey() {
        return key;
    }

    public String getJsonData() {
        return jsonData;
    }
}