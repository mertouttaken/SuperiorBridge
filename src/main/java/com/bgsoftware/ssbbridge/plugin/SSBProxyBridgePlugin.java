package com.bgsoftware.ssbbridge.plugin;

import com.bgsoftware.ssbbridge.common.messaging.MessageBroker;
import com.bgsoftware.ssbbridge.common.messaging.redis.RedisBroker;
import com.bgsoftware.ssbbridge.plugin.bridge.NetworkDatabaseBridge;
import com.bgsoftware.ssbbridge.plugin.config.BridgeConfig;
import com.bgsoftware.ssbbridge.plugin.service.ProxyService;
import com.bgsoftware.ssbbridge.plugin.tasks.HeartbeatTask;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;

public class SSBProxyBridgePlugin extends JavaPlugin {

    private BridgeConfig config;
    private MessageBroker messageBroker;
    private ProxyService proxyService;
    private NetworkDatabaseBridge networkBridge;

    @Override
    public void onEnable() {
        // 1. Config Yükle
        this.config = new BridgeConfig(this);

        // 2. Redis Bağlantısı
        this.messageBroker = new RedisBroker(config.redisHost, config.redisPort, config.redisPassword);
        try {
            this.messageBroker.connect();
            getLogger().info("Redis bağlantısı başarılı.");
        } catch (Exception e) {
            getLogger().severe("Redis bağlantısı kurulamadı! Plugin devre dışı bırakılıyor.");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Proxy Servisi
        this.proxyService = new ProxyService(this);

        // 4. SSB2 Entegrasyonu (Injection)
        if (!injectDatabaseBridge()) {
            getLogger().severe("SSB2 DatabaseBridge enjekte edilemedi! Sürüm uyumsuzluğu olabilir.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 5. Heartbeat Başlat
        Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                new HeartbeatTask(messageBroker, config),
                20L, config.heartbeatInterval * 20L
        );

        getLogger().info("SSBProxyBridge başarıyla yüklendi! ServerID: " + config.serverId);
    }

    @Override
    public void onDisable() {
        if (messageBroker != null) {
            messageBroker.disconnect();
        }
    }

    /**
     * SuperiorSkyblock2'nin database implementasyonunu reflection ile değiştirir.
     * Bu işlem çok risklidir ama "Addon" mantığı için gereklidir.
     */
    /**
     * SuperiorSkyblock2'nin database implementasyonunu reflection ile değiştirir.
     */
    private boolean injectDatabaseBridge() {
        org.bukkit.plugin.Plugin ssbPluginInstance = getServer().getPluginManager().getPlugin("SuperiorSkyblock2");
        if (ssbPluginInstance == null) return false;

        try {
            this.networkBridge = new NetworkDatabaseBridge(messageBroker, config.serverId);

            // 1. dataHandler'ı (DataManager) al
            java.lang.reflect.Field dataHandlerField = ssbPluginInstance.getClass().getDeclaredField("dataHandler");
            dataHandlerField.setAccessible(true);
            Object dataHandlerObj = dataHandlerField.get(ssbPluginInstance);

            getLogger().info("--- DataManager Alan Taraması (List-Aware) Başlatıldı ---");

            // 2. Derinlemesine tarama ve Liste içi sızma
            boolean success = deepSearchAndInject(dataHandlerObj, 0);

            if (success) {
                getLogger().info("--- [SSBBridge] Enjeksiyon BAŞARILI! ---");
                return true;
            }

            getLogger().severe("HATA: DatabaseBridge hiyerarşide bulunamadı.");
            return false;

        } catch (Exception e) {
            getLogger().severe("Injection sırasında hata: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean deepSearchAndInject(Object target, int depth) throws IllegalAccessException {
        if (target == null || depth > 3) return false;

        Class<?> clazz = target.getClass();
        boolean foundAtLeastOne = false;

        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(target);
            if (value == null) continue;

            // KRİTİK NOKTA: Eğer field bir Liste/Koleksiyon ise içini tara
            if (value instanceof java.util.Collection) {
                java.util.Collection<?> collection = (java.util.Collection<?>) value;
                getLogger().info("[Scan-D" + depth + "] Liste bulundu: " + field.getName() + " (" + collection.size() + " eleman)");

                for (Object item : collection) {
                    if (item != null && deepSearchAndInject(item, depth + 1)) {
                        foundAtLeastOne = true;
                    }
                }
                continue;
            }

            // Normal Tip Kontrolü
            if (com.bgsoftware.superiorskyblock.api.data.DatabaseBridge.class.isAssignableFrom(field.getType())) {
                field.set(target, this.networkBridge);
                getLogger().info(">>> ENJEKTE EDİLDİ -> Sınıf: " + clazz.getSimpleName() + " | Field: " + field.getName());
                foundAtLeastOne = true;
                continue;
            }

            // SSB2 paketleri içindeyse derinleş
            if (field.getType().getName().startsWith("com.bgsoftware.superiorskyblock") && depth < 3) {
                if (deepSearchAndInject(value, depth + 1)) {
                    foundAtLeastOne = true;
                }
            }
        }
        return foundAtLeastOne;
    }

    public static SSBProxyBridgePlugin getInstance() {
        return JavaPlugin.getPlugin(SSBProxyBridgePlugin.class);
    }

    public ProxyService getProxyService() {
        return proxyService;
    }
}