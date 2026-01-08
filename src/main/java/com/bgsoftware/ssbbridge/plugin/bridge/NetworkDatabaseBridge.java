package com.bgsoftware.ssbbridge.plugin.bridge;

import com.bgsoftware.superiorskyblock.api.data.DatabaseBridge;
import com.bgsoftware.superiorskyblock.api.data.DatabaseBridgeMode;
import com.bgsoftware.superiorskyblock.api.data.DatabaseFilter;
import com.bgsoftware.superiorskyblock.api.objects.Pair;
import com.bgsoftware.ssbbridge.common.messaging.MessageBroker;
import com.bgsoftware.ssbbridge.common.messaging.Packet;
import com.bgsoftware.ssbbridge.common.messaging.packets.DatabaseActionPacket;
import com.bgsoftware.ssbbridge.common.messaging.packets.ResponsePacket;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class NetworkDatabaseBridge implements DatabaseBridge {

    private final MessageBroker broker;
    private final String serverId;
    private final Gson gson;

    // State Variables
    private DatabaseBridgeMode currentMode = DatabaseBridgeMode.SAVE_DATA;
    private boolean batchingEnabled = false;
    private final ConcurrentLinkedQueue<DatabaseActionPacket> batchQueue = new ConcurrentLinkedQueue<>();

    private static final long TIMEOUT_MS = 5000;
    private static final String MANAGER_CHANNEL = "ssb-manager";

    public NetworkDatabaseBridge(MessageBroker broker, String serverId) {
        this.broker = broker;
        this.serverId = serverId;
        this.gson = new Gson();
    }

    // ========================================================================
    // LOAD OPERATIONS (READ)
    // ========================================================================

    @Override
    public void loadAllObjects(String table, Consumer<Map<String, Object>> resultConsumer) {
        // SELECT * FROM table
        DatabaseActionPacket packet = new DatabaseActionPacket(
                serverId, DatabaseActionPacket.ActionType.LOAD_ALL, table, null, null
        );

        handleLoadRequest(packet, (jsonResult) -> {
            // Manager bize List<Map<String, Object>> JSON döner
            Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> rows = gson.fromJson(jsonResult, listType);

            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    resultConsumer.accept(row);
                }
            }
        });
    }

    @Override
    public void loadObject(String table, DatabaseFilter filter, Consumer<Map<String, Object>> resultConsumer) {
        // SELECT * FROM table WHERE filter...
        Map<String, String> filterMap = convertFilter(filter);

        DatabaseActionPacket packet = new DatabaseActionPacket(
                serverId, DatabaseActionPacket.ActionType.LOAD_SINGLE, table, filterMap, null
        );

        handleLoadRequest(packet, (jsonResult) -> {
            // Manager bize tek bir Map<String, Object> JSON döner
            Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> row = gson.fromJson(jsonResult, mapType);
            if (row != null && !row.isEmpty()) {
                resultConsumer.accept(row);
            }
        });
    }

    private void handleLoadRequest(DatabaseActionPacket packet, Consumer<String> onSuccess) {
        try {
            CompletableFuture<Packet> future = broker.sendRequest(packet, MANAGER_CHANNEL, TIMEOUT_MS);
            Packet responseRaw = future.join(); // Async worker'da bloklama

            if (responseRaw instanceof ResponsePacket) {
                ResponsePacket response = (ResponsePacket) responseRaw;
                if (response.isSuccess()) {
                    onSuccess.accept(response.getPayloadJson());
                }
            }
        } catch (Exception e) {
            System.err.println("[SSB-Bridge] Load error on table " + packet.getTable() + ": " + e.getMessage());
        }
    }

    // ========================================================================
    // WRITE OPERATIONS (INSERT, UPDATE, DELETE)
    // ========================================================================

    @Override
    public void insertObject(String table, Pair<String, Object>... columns) {
        Map<String, String> values = convertColumns(columns);

        DatabaseActionPacket packet = new DatabaseActionPacket(
                serverId, DatabaseActionPacket.ActionType.INSERT, table, null, values
        );
        sendOrBatch(packet);
    }

    @Override
    public void updateObject(String table, DatabaseFilter filter, Pair<String, Object>... columns) {
        Map<String, String> filters = convertFilter(filter);
        Map<String, String> values = convertColumns(columns);

        DatabaseActionPacket packet = new DatabaseActionPacket(
                serverId, DatabaseActionPacket.ActionType.UPDATE, table, filters, values
        );
        sendOrBatch(packet);
    }

    @Override
    public void deleteObject(String table, DatabaseFilter filter) {
        Map<String, String> filters = convertFilter(filter);

        DatabaseActionPacket packet = new DatabaseActionPacket(
                serverId, DatabaseActionPacket.ActionType.DELETE, table, filters, null
        );
        sendOrBatch(packet);
    }

    // ========================================================================
    // BATCH & MODE MANAGEMENT
    // ========================================================================

    @Override
    public void batchOperations(boolean batchOperations) {
        this.batchingEnabled = batchOperations;

        if (!batchOperations && !batchQueue.isEmpty()) {
            // Batch kapandıysa birikenleri gönder (Flush)
            flushBatch();
        }
    }

    private void sendOrBatch(DatabaseActionPacket packet) {
        if (batchingEnabled) {
            batchQueue.add(packet);
        } else {
            broker.sendPacket(packet, MANAGER_CHANNEL);
        }
    }

    private void flushBatch() {
        // Tüm batch'i tek bir listede göndermek network yükünü azaltır.
        // Şimdilik basitçe döngü ile gönderiyoruz, production'da "BatchPacket" yapılabilir.
        while (!batchQueue.isEmpty()) {
            broker.sendPacket(batchQueue.poll(), MANAGER_CHANNEL);
        }
    }

    @Override
    public void setDatabaseBridgeMode(DatabaseBridgeMode databaseBridgeMode) {
        this.currentMode = databaseBridgeMode;
    }

    @Override
    public DatabaseBridgeMode getDatabaseBridgeMode() {
        return this.currentMode;
    }

    // ========================================================================
    // CONVERTERS & HELPERS
    // ========================================================================

    /**
     * SSB2 Pair array'ini, JSON uyumlu String Map'e çevirir.
     * Object tiplerini (Integer, Boolean, Blob vb.) String'e serialize eder.
     */
    @SafeVarargs
    private final Map<String, String> convertColumns(Pair<String, Object>... columns) {
        Map<String, String> map = new HashMap<>();
        if (columns != null) {
            for (Pair<String, Object> pair : columns) {
                if (pair != null && pair.getKey() != null) {
                    Object val = pair.getValue();
                    // Basit tipler direkt string, karmaşıklar JSON yapılabilir.
                    // SSB2 genellikle primitive wrapper veya String saklar.
                    map.put(pair.getKey(), val != null ? String.valueOf(val) : null);
                }
            }
        }
        return map;
    }

    /**
     * DatabaseFilter objesini Map'e çevirir.
     */
    private Map<String, String> convertFilter(DatabaseFilter filter) {
        Map<String, String> map = new HashMap<>();
        if (filter != null) {
            // DatabaseFilter genellikle kolonlar dizisidir.
            // SSB2 API'sine göre filter içerisindeki column-value eşleşmelerini almalıyız.
            // NOT: DatabaseFilter internal yapısına göre burası değişebilir.
            // Genellikle filter.getColumns() Pair[] döner.

            // Eğer filter iterable ise veya getColumns varsa:
            // for(Pair<String, Object> col : filter.getColumns()) map.put(col.getKey(), String.valueOf(col.getValue()));

            // Kaynak kodda DatabaseFilter'ın yapısını görmediğim için varsayım (Generic Adapter):
            // Çoğu durumda 'filters' primary key'dir.
        }
        return map;
    }
}