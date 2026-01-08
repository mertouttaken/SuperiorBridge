package com.bgsoftware.ssbbridge.common.messaging.packets;

import com.bgsoftware.ssbbridge.common.messaging.Packet;
import java.util.HashMap;
import java.util.Map;

/**
 * SQL benzeri işlemleri ağ üzerinden taşır.
 * Örn: INSERT INTO "islands" VALUES ("owner": "Mert", "worth": 100)
 */
public class DatabaseActionPacket extends Packet {

    public enum ActionType {
        INSERT,
        UPDATE,
        DELETE,
        LOAD_SINGLE,
        LOAD_ALL
    }

    private final ActionType action;
    private final String table;
    private final Map<String, String> filters; // WHERE conditions (col -> val)
    private final Map<String, String> values;  // SET values (col -> val)

    public DatabaseActionPacket(String senderId, ActionType action, String table, Map<String, String> filters, Map<String, String> values) {
        super(senderId, "DB_ACTION");
        this.action = action;
        this.table = table;
        this.filters = filters != null ? filters : new HashMap<>();
        this.values = values != null ? values : new HashMap<>();
    }

    // Getters
    public ActionType getAction() { return action; }
    public String getTable() { return table; }
    public Map<String, String> getFilters() { return filters; }
    public Map<String, String> getValues() { return values; }
}