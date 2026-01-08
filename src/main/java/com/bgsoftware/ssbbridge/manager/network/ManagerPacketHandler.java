package com.bgsoftware.ssbbridge.manager.network;

import com.bgsoftware.ssbbridge.common.messaging.MessageBroker;
import com.bgsoftware.ssbbridge.common.messaging.Packet;
import com.bgsoftware.ssbbridge.common.messaging.packets.*;
import com.bgsoftware.ssbbridge.common.model.ServerNode;
import com.bgsoftware.ssbbridge.manager.SSBProxyManager;
import com.bgsoftware.ssbbridge.manager.core.LoadBalancer;
import java.util.Optional;

public class ManagerPacketHandler {

    private final SSBProxyManager manager;
    private final MessageBroker broker;
    private final LoadBalancer loadBalancer;

    public ManagerPacketHandler(SSBProxyManager manager, MessageBroker broker) {
        this.manager = manager;
        this.broker = broker;
        this.loadBalancer = new LoadBalancer(manager.getServerRegistry());
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

    private void handleDatabaseAction(DatabaseActionPacket action) {
        try {
            // Senin DatabaseHandler sınıfında 'execute' yok, 'executeUpdate' var.
            // Ayrıca parametre olarak sadece paketi gönderiyoruz.
            manager.getDatabaseHandler().executeUpdate(action);

            // Konsola hangi tabloya ne işlemi yapıldığını yazdıralım
            System.out.println("[MySQL] İşlem: " + action.getAction() + " | Tablo: " + action.getTable());

        } catch (Exception e) {
            System.err.println("[MySQL HATA] Veritabanı işlemi sırasında hata oluştu!");
            e.printStackTrace();
        }
    }

    private void handleIslandCreationRequest(IslandCreationRequestPacket request) {
        Optional<ServerNode> optimalServer = loadBalancer.getOptimalServer();
        String targetServerId = optimalServer.isPresent() ? optimalServer.get().getServerId() : request.getSenderId();

        ResponsePacket response = new ResponsePacket("Manager", request.getUniqueId(), true, targetServerId);
        broker.sendPacket(response, "ssb-manager");
    }

    private void handleDataSave(DataSavePacket packet) {
        System.out.println("[Data] Veri persist edildi: " + packet.getKey());
    }

    private void handleDataRequest(DataRequestPacket packet) {
        // Veritabanından veri çekme mantığı buraya gelecek
    }
}