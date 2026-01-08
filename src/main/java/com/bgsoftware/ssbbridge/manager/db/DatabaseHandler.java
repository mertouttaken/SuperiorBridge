package com.bgsoftware.ssbbridge.manager.db;

import com.bgsoftware.ssbbridge.common.messaging.packets.DatabaseActionPacket;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class DatabaseHandler {

    private final HikariDataSource dataSource;

    public DatabaseHandler(String host, int port, String db, String user, String pass) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db);
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(10);
        config.addDataSourceProperty("cachePrepStmts", "true");
        this.dataSource = new HikariDataSource(config);
    }

    /**
     * Gelen DatabaseActionPacket'i gerçek SQL sorgusuna dönüştürür.
     */
    public void executeUpdate(DatabaseActionPacket packet) {
        String sql = "";
        Map<String, String> values = packet.getValues();
        Map<String, String> filters = packet.getFilters();

        try (Connection conn = dataSource.getConnection()) {
            if (packet.getAction() == DatabaseActionPacket.ActionType.INSERT) {
                // Sütun isimlerini ` ` içine alıyoruz
                String cols = values.keySet().stream().map(k -> "`" + k + "`").collect(Collectors.joining(", "));
                String placeholders = values.keySet().stream().map(k -> "?").collect(Collectors.joining(", "));
                sql = "INSERT INTO `" + packet.getTable() + "` (" + cols + ") VALUES (" + placeholders + ")";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    int i = 1;
                    for (String val : values.values()) ps.setString(i++, val);
                    ps.executeUpdate();
                }

            } else if (packet.getAction() == DatabaseActionPacket.ActionType.UPDATE) {
                String sets = values.keySet().stream().map(k -> "`" + k + "` = ?").collect(Collectors.joining(", "));

                // Filtre (WHERE) kontrolü
                String wheres = "";
                if (!filters.isEmpty()) {
                    wheres = " WHERE " + filters.keySet().stream().map(k -> "`" + k + "` = ?").collect(Collectors.joining(" AND "));
                }

                sql = "UPDATE `" + packet.getTable() + "` SET " + sets + wheres;

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    int i = 1;
                    for (String val : values.values()) ps.setString(i++, val);
                    for (String val : filters.values()) ps.setString(i++, val);
                    ps.executeUpdate();
                }
            }

            // Hata ayıklama için sorguyu konsola yazdır (İsteğe bağlı)
            // System.out.println("[SQL-DEBUG] Çalıştırılan Sorgu: " + sql);

        } catch (SQLException e) {
            System.err.println("[MySQL HATA] Sorgu Hatası: " + sql);
            e.printStackTrace();
        }
    }

    /**
     * Veritabanından veri okur (SELECT).
     */
    public List<Map<String, Object>> executeQuery(DatabaseActionPacket packet) {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT * FROM " + packet.getTable();
        Map<String, String> filters = packet.getFilters();

        if (!filters.isEmpty()) {
            sql += " WHERE " + filters.keySet().stream().map(k -> k + " = ?").collect(Collectors.joining(" AND "));
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int i = 1;
            for (String val : filters.values()) ps.setString(i++, val);

            ResultSet rs = ps.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int col = 1; col <= meta.getColumnCount(); col++) {
                    row.put(meta.getColumnName(col), rs.getObject(col));
                }
                results.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }
}