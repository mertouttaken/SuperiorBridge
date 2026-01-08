package com.bgsoftware.ssbbridge.common.messaging.redis;

import com.bgsoftware.ssbbridge.common.messaging.MessageBroker;
import com.bgsoftware.ssbbridge.common.messaging.Packet;
import com.google.gson.Gson;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class RedisBroker implements MessageBroker {

    private final JedisPool jedisPool;
    private final Gson gson;
    private final ExecutorService subscriptionExecutor;
    private final ScheduledExecutorService timeoutScheduler;

    // Gelen cevapları bekleyen istekler (CorrelationID -> Future)
    private final Map<String, CompletableFuture<Packet>> pendingRequests = new ConcurrentHashMap<>();

    // Kanal dinleyicileri
    private final Map<String, Consumer<Packet>> channelListeners = new ConcurrentHashMap<>();

    private JedisPubSub pubSubTask;
    private volatile boolean active = false;

    public RedisBroker(String host, int port, String password) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(16); // Production için pool ayarı
        config.setMaxIdle(8);
        config.setMinIdle(2);

        if (password != null && !password.isEmpty()) {
            this.jedisPool = new JedisPool(config, host, port, 2000, password);
        } else {
            this.jedisPool = new JedisPool(config, host, port);
        }

        this.gson = new Gson();
        this.subscriptionExecutor = Executors.newCachedThreadPool(); // PubSub blokladığı için ayrı thread
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void connect() {
        this.active = true;
        // Global dinleme thread'ini başlat
        startSubscriptionLoop();
        System.out.println("[RedisBroker] Bağlantı kuruldu.");
    }

    @Override
    public void disconnect() {
        this.active = false;
        if (pubSubTask != null && pubSubTask.isSubscribed()) {
            pubSubTask.unsubscribe();
        }
        subscriptionExecutor.shutdownNow();
        timeoutScheduler.shutdownNow();
        jedisPool.close();
    }

    @Override
    public void sendPacket(Packet packet, String channel) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = gson.toJson(packet);
            jedis.publish(channel, packet.getClass().getName() + "|" + json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public CompletableFuture<Packet> sendRequest(Packet packet, String targetChannel, long timeoutMs) {
        CompletableFuture<Packet> future = new CompletableFuture<>();

        // İsteği haritaya kaydet
        pendingRequests.put(packet.getUniqueId(), future);

        // Redis'e gönder
        sendPacket(packet, targetChannel);

        // Timeout mekanizması
        timeoutScheduler.schedule(() -> {
            if (pendingRequests.remove(packet.getUniqueId()) != null) {
                future.completeExceptionally(new TimeoutException("Redis request timed out: " + packet.getUniqueId()));
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        return future;
    }

    @Override
    public void subscribe(String channel, Consumer<Packet> handler) {
        channelListeners.put(channel, handler);
        // Not: Redis PubSub dinamik subscribe destekler, ancak burada basitlik için
        // startSubscriptionLoop içinde pattern subscribe kullanacağız veya
        // bu metodun çağrıldığı yerde subscribe eklenebilir.
        // Production için "pattern matching" (psubscribe) en iyisidir.
    }

    private void startSubscriptionLoop() {
        this.pubSubTask = new JedisPubSub() {
            @Override
            public void onPMessage(String pattern, String channel, String message) {
                handleIncomingMessage(channel, message);
            }
        };

        subscriptionExecutor.submit(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                // Tüm ssb-bridge kanallarını dinle
                jedis.psubscribe(pubSubTask, "ssb-*");
            } catch (Exception e) {
                if (active) {
                    System.err.println("[RedisBroker] PubSub hatası, 5sn sonra tekrar deneniyor...");
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    startSubscriptionLoop(); // Reconnect logic
                }
            }
        });
    }

    private void handleIncomingMessage(String channel, String rawMessage) {
        try {
            // Format: "com.package.PacketClass|{json_data}"
            int splitIndex = rawMessage.indexOf("|");
            if (splitIndex == -1) return;

            String className = rawMessage.substring(0, splitIndex);
            String jsonData = rawMessage.substring(splitIndex + 1);

            // Class tipine göre deserialize et
            Class<?> clazz = Class.forName(className);
            if (!Packet.class.isAssignableFrom(clazz)) return;

            Packet packet = (Packet) gson.fromJson(jsonData, clazz);

            // 1. Eğer bu bir cevapsa (Response), bekleyen future'ı tamamla
            if (pendingRequests.containsKey(packet.getUniqueId())) {
                CompletableFuture<Packet> future = pendingRequests.remove(packet.getUniqueId());
                if (future != null) {
                    future.complete(packet);
                }
                return; // Cevap işlendi, başka listener'a gerek yok
            }

            // 2. Normal bir mesajsa ilgili listener'a ilet
            if (channelListeners.containsKey(channel)) {
                channelListeners.get(channel).accept(packet);
            }

        } catch (Exception e) {
            System.err.println("[RedisBroker] Mesaj işleme hatası: " + e.getMessage());
        }
    }
}