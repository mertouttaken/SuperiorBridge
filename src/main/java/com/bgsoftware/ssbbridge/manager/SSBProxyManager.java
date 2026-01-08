package com.bgsoftware.ssbbridge.manager;

import com.bgsoftware.ssbbridge.manager.core.IslandLocationRegistry;
import com.bgsoftware.ssbbridge.manager.core.ServerRegistry;
import com.bgsoftware.ssbbridge.manager.db.DatabaseHandler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SSBProxyManager {

    private final ServerRegistry serverRegistry;
    private final IslandLocationRegistry islandRegistry;
    private final ScheduledExecutorService scheduler;
    private final DatabaseHandler databaseHandler;

    // Singleton instance
    private static SSBProxyManager instance;

    public SSBProxyManager(DatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
        this.serverRegistry = new ServerRegistry();
        this.islandRegistry = new IslandLocationRegistry();
        this.scheduler = Executors.newScheduledThreadPool(4);

        instance = this;
    }

    public void start() {
        System.out.println("SSBProxyBridge Manager başlatılıyor...");

        scheduler.scheduleAtFixedRate(
                serverRegistry::cleanupDeadServers,
                10, 10, TimeUnit.SECONDS
        );

        System.out.println("SSBProxyBridge Manager aktif.");
    }

    public void shutdown() {
        System.out.println("Kapatılıyor...");
        scheduler.shutdown();
    }

    // Parametresiz getInstance: Nesne zaten Main'de oluşturulduğu için sadece döndürür
    public static SSBProxyManager getInstance() {
        return instance;
    }

    public ServerRegistry getServerRegistry() {
        return serverRegistry;
    }

    public IslandLocationRegistry getIslandRegistry() {
        return islandRegistry;
    }

    public DatabaseHandler getDatabaseHandler() {
        return databaseHandler;
    }
}