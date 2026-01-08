package com.bgsoftware.ssbbridge.manager;

import com.bgsoftware.ssbbridge.common.messaging.redis.RedisBroker;
import com.bgsoftware.ssbbridge.manager.db.DatabaseHandler;
import com.bgsoftware.ssbbridge.manager.network.ManagerPacketHandler;

import java.util.Scanner;

import static com.bgsoftware.ssbbridge.manager.SSBProxyManager.*;

public class SSBManagerApp {

    public static void main(String[] args) {
        // 1. Önce Veritabanı Ayarlarını Yap
        DatabaseHandler db = new DatabaseHandler(
                "127.0.0.1",
                3306,
                "skyblock_database",
                "admin_user",
                "test123"
        );

        // 2. Manager Nesnesini Oluştur (MUTLAKA DB'yi parametre olarak ver)
        // Bu satır SSBProxyManager.java içindeki 'instance'ı doldurur.
        SSBProxyManager manager = new SSBProxyManager(db);

        // 3. Manager'ı Başlat (Hata aldığın satır burasıydı, artık manager null değil)
        manager.start();

        // 4. Redis Broker'ı Kur
        RedisBroker broker = new RedisBroker("127.0.0.1", 6379, "test123");
        broker.connect();

        // 5. Packet Handler'ı Kur ve Dinlemeye Başla
        ManagerPacketHandler packetHandler = new ManagerPacketHandler(manager, broker, db);

        // Kanal ismine dikkat: ssb-manager (Az önce monitor'de gördüğümüz isim)
        broker.subscribe("ssb-manager", packetHandler::handlePacket);

        System.out.println("[Main] Sistem aktif. Çıkış için 'stop' yazın.");

        // 4. Uygulamayı Ayakta Tut (Main Loop)
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String line = scanner.nextLine();
            if (line.equalsIgnoreCase("stop") || line.equalsIgnoreCase("exit")) {
                break;
            }
            if (line.equalsIgnoreCase("status")) {
                System.out.println("Aktif Sunucular: " + manager.getServerRegistry().getAvailableServers().size());
            }
        }

        // 5. Kapanış
        System.out.println("Sistem kapatılıyor...");
        broker.disconnect();
        manager.shutdown();
        System.exit(0);
    }
}