package com.bgsoftware.ssbbridge.common.messaging.packets;

import com.bgsoftware.ssbbridge.common.messaging.Packet;

public class ResponsePacket extends Packet {

    private final String originalPacketId; // Hangi isteğe cevap verildiği
    private final boolean success;
    private final String payloadJson; // Cevap verisi (Generics ile uğraşmamak için JSON string)

    public ResponsePacket(String senderId, String originalPacketId, boolean success, String payloadJson) {
        super(senderId, "RESPONSE");
        this.originalPacketId = originalPacketId;
        this.success = success;
        this.payloadJson = payloadJson;
    }

    // Request-Response mantığı için ID'yi override ediyoruz.
    // Böylece Request ID = Response ID olur ve Pending map'te eşleşir.
    @Override
    public String getUniqueId() {
        return originalPacketId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getPayloadJson() {
        return payloadJson;
    }
}