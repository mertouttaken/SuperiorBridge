package com.bgsoftware.ssbbridge.manager.network;

import com.bgsoftware.ssbbridge.common.messaging.MessageBroker;
import com.bgsoftware.ssbbridge.common.messaging.Packet;
import com.bgsoftware.ssbbridge.common.messaging.packets.*;
import com.bgsoftware.ssbbridge.common.model.ServerNode;
import com.bgsoftware.ssbbridge.manager.SSBProxyManager;
import com.bgsoftware.ssbbridge.manager.core.LoadBalancer;
import com.bgsoftware.ssbbridge.manager.db.DatabaseHandler;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.schematic.SchematicOptions;
import com.google.gson.Gson;
import org.bukkit.Location;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ManagerPacketHandler {

    private final SSBProxyManager manager;
    private final MessageBroker broker;
    private final LoadBalancer loadBalancer;
    private final DatabaseHandler databaseHandler;

    public ManagerPacketHandler(SSBProxyManager manager, MessageBroker broker, DatabaseHandler databaseHandler) {
        this.manager = manager;
        this.broker = broker;
        this.loadBalancer = new LoadBalancer(manager.getServerRegistry());
        this.databaseHandler = databaseHandler;
    }

    public void handlePacket(Packet packet) {
        // --- 1. Heartbeat ---
        if (packet instanceof ServerHeartbeatPacket) {
            ServerHeartbeatPacket hb = (ServerHeartbeatPacket) packet;
            manager.getServerRegistry().registerOrUpdateServer(
                    hb.getSenderId(),
                    "127.0.0.1",
                    25565,
                    hb.getOnlinePlayers(),
                    hb.getActiveIslands()
            );
            return;
        }
        if (packet instanceof IslandSavePacket) {
            IslandSavePacket save = (IslandSavePacket) packet;
            manager.getDatabaseHandler().saveIslandSchematic(save.getIslandUuid(), save.getSchematicData());
        }
        if (packet instanceof DataRequestPacket && ((DataRequestPacket) packet).getDataType().equals("LOAD_ISLAND_AND_TELEPORT")) {
            String playerUuid = ((DataRequestPacket) packet).getKey();
            // 1. En uygun sunucuyu seç
            ServerNode target = loadBalancer.getOptimalServer().get();

            // 2. Şematiği DB'den çek
            byte[] schem = manager.getDatabaseHandler().getIslandSchematic(playerUuid);

            // 3. Hedef sunucuya "Adayı Yapıştır" talimatı gönder
            PrepareIslandCreationPacket prepare = new PrepareIslandCreationPacket("Manager", playerUuid, "custom_load", null);
            // Not: PrepareIslandCreationPacket'e byte[] schematicData alanı eklenmeli
            broker.sendPacket(prepare, "ssb-server-" + target.getServerId());

            // 4. Lobiye sunucu bilgisini dön
            sendResponse(packet, target.getServerId());
        }
        if (packet instanceof IslandInvitePacket) {
            IslandInvitePacket invite = (IslandInvitePacket) packet;

            // 1. Hedef oyuncunun hangi sunucuda olduğunu bul
            // (Bunu Heartbeat'ten gelen oyuncu listesinden veya Redis'teki aktif oyuncu haritasından bulabiliriz)
            String targetServerId = findServerOfPlayer(invite.getTargetPlayerName());

            if (targetServerId != null) {
                // 2. Daveti sadece o sunucunun kanalına gönder
                broker.sendPacket(invite, "ssb-server-" + targetServerId);
                System.out.println("[Postacı] Davet iletildi: " + invite.getInviterName() + " -> " + invite.getTargetPlayerName() + " (" + targetServerId + ")");
            }
            return;
        }
        // --- 2. Database Action (KRİTİK EKLEME) ---
        // Oyun sunucusundan gelen ham SQL sorgularını MySQL'e yazar.
        if (packet instanceof DatabaseActionPacket) {
            handleDatabaseAction((DatabaseActionPacket) packet);
            return;
        }

        // --- 3. Ada Oluşturma İsteği ---
        if (packet instanceof IslandCreationRequestPacket) {
            handleIslandCreationRequest((IslandCreationRequestPacket) packet);
            return;
        }

        // --- 4. Veri Kaydetme ---
        if (packet instanceof DataSavePacket) {
            handleDataSave((DataSavePacket) packet);
            return;
        }

        // --- 5. Veri İsteme ---
        if (packet instanceof DataRequestPacket) {
            handleDataRequest((DataRequestPacket) packet);
        }
    }
    private void handleIslandLoadRequest(DataRequestPacket request) {
        String playerUuid = request.getKey();

        // 1. En uygun (en boş) sunucuyu seç
        ServerNode targetNode = loadBalancer.getOptimalServer().get();

        // 2. MySQL'den bu oyuncunun ada şematiğini bul
        byte[] schematicData = databaseHandler.getIslandSchematic(playerUuid);

        // 3. Hedef sunucuya "Bu şematiği yapıştır ve oyuncuyu bekle" talimatı gönder
        PrepareIslandCreationPacket preparePacket = new PrepareIslandCreationPacket(
                "Manager",
                playerUuid,
                null,
                schematicData
        );
        broker.sendPacket(preparePacket, "ssb-server-" + targetNode.getServerId());

        // 4. Lobiye "Oyuncuyu şu sunucuya gönder" cevabını dön
        sendResponse(request, targetNode.getServerId());
    }
    private void handleDatabaseAction(DatabaseActionPacket action) {
        try {
            if (action.getAction() == DatabaseActionPacket.ActionType.INSERT || action.getAction() == DatabaseActionPacket.ActionType.UPDATE || action.getAction() == DatabaseActionPacket.ActionType.DELETE) {
                // YAZMA İŞLEMİ
                manager.getDatabaseHandler().executeUpdate(action);
                System.out.println("[MySQL] Yazma: " + action.getAction() + " | Tablo: " + action.getTable());
            } else {
                // OKUMA İŞLEMİ (LOAD_SINGLE / LOAD_ALL)
                // Veritabanından veriyi çek
                List<Map<String, Object>> results = manager.getDatabaseHandler().executeQuery(action);

                // Sonucu JSON'a çevir ve ResponsePacket ile Spigot'a geri gönder
                String jsonResponse = new Gson().toJson(results);
                sendResponse(action, jsonResponse); // Önceki adımda yazdığımız metod

                System.out.println("[MySQL] Okuma: " + action.getAction() + " | Tablo: " + action.getTable() + " | Kayıt: " + results.size());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void sendResponse(Packet request, String targetServerId) {
        ResponsePacket response = new ResponsePacket(
                "Manager",            // Gönderen ID
                request.getUniqueId(), // Orijinal paketin ID'si (Correlation ID)
                true,                 // Başarı durumu
                targetServerId        // Payload: Hedef sunucu ID'si
        );

        // Cevabı manager kanalına gönderiyoruz; Spigot tarafındaki broker
        // Correlation ID sayesinde kendi isteğini tanıyacaktır.
        broker.sendPacket(response, "ssb-manager");
    }
    // ManagerPacketHandler.java içinde güncelleme
    private void handleIslandCreationRequest(IslandCreationRequestPacket request) {
        UUID playerUuid = UUID.fromString(request.getPlayerUuid());

        // 1. Önce oyuncunun zaten bir adası var mı kontrol et (Redis Otoritesi)
        Optional<UUID> existingIsland = manager.getIslandRegistry().getPlayerIsland(playerUuid);

        if (existingIsland.isPresent()) {
            // Eğer adası varsa, adanın kayıtlı olduğu sunucuyu bul
            Optional<String> islandServer = manager.getIslandRegistry().getIslandServer(existingIsland.get());
            if (islandServer.isPresent()) {
                // Oyuncuyu zorla adasının olduğu sunucuya gönder
                sendResponse(request, islandServer.get());
                return;
            }
        }

        // 2. Yeni ada için en boş sunucuyu seç (Load Balancer)
        Optional<ServerNode> optimalServer = loadBalancer.getOptimalServer();
        String targetServerId = optimalServer.isPresent() ? optimalServer.get().getServerId() : request.getSenderId();

        // 3. Redis'e bu eşleşmeyi ATOMİK olarak yaz (İleride LUA script eklenebilir)
        // Bu işlemden sonra ada artık o sunucuya aittir.
        manager.getIslandRegistry().setIslandLocation(UUID.randomUUID(), targetServerId); // Yeni ada ID üretimi

        sendResponse(request, targetServerId);
    }

    private void handleDataSave(DataSavePacket packet) {
        System.out.println("[Data] Veri persist edildi: " + packet.getKey());
    }

    private void handleDataRequest(DataRequestPacket packet) {
        // Veritabanından veri çekme mantığı buraya gelecek
    }
    public void handleIslandUnload(Island island, File tempFile) {
        // 1. Adanın sınırlarını (min ve max) alıyoruz
        Location minLocation = island.getMinimum();
        Location maxLocation = island.getMaximum();

        // 2. Şematik ayarlarını oluşturuyoruz (İsim olarak Island ID kullanabilirsin)
        // Not: SS2 şematikleri otomatik olarak kendi klasörüne kaydeder.
        SchematicOptions options = SchematicOptions.newBuilder(island.getUniqueId().toString())
                .build();

        // 3. exportSchematic yerine saveSchematic kullanıyoruz
        SuperiorSkyblockAPI.getSchematics().saveSchematic(
                minLocation,
                maxLocation,
                options,
                () -> {
                    SuperiorSkyblockAPI.deleteIsland(island);

                    System.out.println("Ada başarıyla dışa aktarıldı ve gridden kaldırıldı.");
                }
        );
    }
    private String findServerOfPlayer(String playerName) {
        // RedisBroker üzerinden JedisPool'a erişiyoruz
        if (broker instanceof com.bgsoftware.ssbbridge.common.messaging.redis.RedisBroker) {
            try (redis.clients.jedis.Jedis jedis = ((com.bgsoftware.ssbbridge.common.messaging.redis.RedisBroker) broker).getJedisPool().getResource()) {
                // "ssb:player:Mert:current_server" gibi bir key'den veriyi oku
                return jedis.get("ssb:player_session:" + playerName);
            } catch (Exception e) {
                System.err.println("[Manager] Oyuncu konumu sorgulanırken hata: " + e.getMessage());
            }
        }
        return null;
    }
}