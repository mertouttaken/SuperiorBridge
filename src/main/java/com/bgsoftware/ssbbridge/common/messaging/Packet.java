package com.bgsoftware.ssbbridge.common.messaging;

import com.google.gson.Gson;
import java.util.UUID;

/**
 * Ağdaki tüm veri transferi bu sınıf üzerinden yapılır.
 * Her paketin benzersiz bir ID'si ve gönderen bilgisi vardır.
 */
public abstract class Packet {

    // Request-Response eşleşmesi için unique ID
    private final String uniqueId;
    private final String senderId;
    private final long timestamp;
    private final String packetType; // JSON Deserialization için gerekli

    public Packet(String senderId, String packetType) {
        this.uniqueId = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.timestamp = System.currentTimeMillis();
        this.packetType = packetType;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public String getSenderId() {
        return senderId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getPacketType() {
        return packetType;
    }

    // Alt sınıflar kendi verilerini JSON'a çevirmek için bunu kullanabilir
    public String toJson() {
        return new Gson().toJson(this);
    }
}