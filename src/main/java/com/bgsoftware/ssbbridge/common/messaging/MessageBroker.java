package com.bgsoftware.ssbbridge.common.messaging;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface MessageBroker {

    /**
     * Broker bağlantısını başlatır.
     */
    void connect() throws Exception;

    /**
     * Bağlantıyı güvenli şekilde kapatır.
     */
    void disconnect();

    /**
     * Ateşle ve Unut (Fire-and-Forget). Cevap beklenmez.
     * Örnek: "Island Created Event"
     */
    void sendPacket(Packet packet, String channel);

    /**
     * İstek gönderir ve cevap bekler (Request-Response).
     * Örnek: "Get Island Server" -> "Server-A"
     *
     * @param packet Gönderilecek istek paketi
     * @param targetChannel Hedef kanal (örn: "manager-channel")
     * @param timeoutMs Zaman aşımı süresi
     * @return Cevap paketi (Promise)
     */
    CompletableFuture<Packet> sendRequest(Packet packet, String targetChannel, long timeoutMs);

    /**
     * Belirli bir kanalı dinlemeye başlar.
     */
    void subscribe(String channel, Consumer<Packet> handler);
}