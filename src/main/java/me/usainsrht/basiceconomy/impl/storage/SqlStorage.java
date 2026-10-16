package me.usainsrht.basiceconomy.impl.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.api.transaction.*;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SqlStorage implements Storage {

    private final ConfigManager config;
    private HikariDataSource dataSource;

    public SqlStorage(ConfigManager config) {
        this.config = config;
    }

    @Override
    public void connect() throws Exception {
        HikariConfig hc = new HikariConfig();
        String type = config.getStorageType();

        switch (type) {
            case "MYSQL":
            case "MARIADB":
                hc.setJdbcUrl("jdbc:" + type.toLowerCase() + "://" + config.getStorageAddress() + ":" + config.getStoragePort() + "/" + config.getStorageDatabase());
                hc.setUsername(config.getStorageUsername());
                hc.setPassword(config.getStoragePassword());
                hc.addDataSourceProperty("cachePrepStmts", "true");
                hc.addDataSourceProperty("prepStmtCacheSize", "250");
                hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                break;
            case "POSTGRESQL":
                hc.setJdbcUrl("jdbc:postgresql://" + config.getStorageAddress() + ":" + config.getStoragePort() + "/" + config.getStorageDatabase());
                hc.setUsername(config.getStorageUsername());
                hc.setPassword(config.getStoragePassword());
                break;
            case "H2":
            default:
                String h2File = config.getStorageH2File();
                if (h2File.startsWith("mem:") || h2File.startsWith("tcp:")) {
                    hc.setJdbcUrl("jdbc:h2:" + h2File + ";mode=MySQL");
                } else {
                    hc.setJdbcUrl("jdbc:h2:./plugins/BasicEconomy/" + h2File + ";mode=MySQL");
                }
                hc.setDriverClassName("org.h2.Driver");
                break;
        }

        hc.setMaximumPoolSize(10);
        hc.setMinimumIdle(2);
        hc.setPoolName("BasicEconomyPool");

        dataSource = new HikariDataSource(hc);
        createTables();
    }

    private void createTables() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS basiceconomy_balances (" +
                            "uuid VARCHAR(36) NOT NULL," +
                            "currency VARCHAR(64) NOT NULL," +
                            "balance DECIMAL(38, 2) NOT NULL," +
                            "PRIMARY KEY (uuid, currency)" +
                            ");"
            )) {
                ps.execute();
            }

            String idColumn = config.getStorageType().equals("POSTGRESQL")
                    ? "id BIGSERIAL PRIMARY KEY"
                    : "id BIGINT AUTO_INCREMENT PRIMARY KEY";

            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS basiceconomy_transactions (" +
                            idColumn + "," +
                            "timestamp BIGINT NOT NULL," +
                            "sender_uuid VARCHAR(36) NULL," +
                            "sender_name VARCHAR(32) NOT NULL," +
                            "receiver_uuid VARCHAR(36) NOT NULL," +
                            "receiver_name VARCHAR(32) NOT NULL," +
                            "currency VARCHAR(64) NOT NULL," +
                            "amount DECIMAL(38, 2) NOT NULL," +
                            "type VARCHAR(32) NOT NULL," +
                            "server VARCHAR(64) NOT NULL DEFAULT 'default'" +
                            ");"
            )) {
                ps.execute();
            }

            createIndexIfNotExists(conn, "idx_be_tx_sender", "basiceconomy_transactions", "sender_uuid, timestamp DESC");
            createIndexIfNotExists(conn, "idx_be_tx_receiver", "basiceconomy_transactions", "receiver_uuid, timestamp DESC");
            createIndexIfNotExists(conn, "idx_be_tx_timestamp", "basiceconomy_transactions", "timestamp DESC");
            createIndexIfNotExists(conn, "idx_be_tx_currency", "basiceconomy_transactions", "currency");
        }
    }

    private void createIndexIfNotExists(Connection conn, String indexName, String table, String columns) {
        try (PreparedStatement ps = conn.prepareStatement(
                "CREATE INDEX " + indexName + " ON " + table + " (" + columns + ");")) {
            ps.execute();
        } catch (Exception ignored) {
            // Index already exists or not supported
        }
    }

    @Override
    public void disconnect() throws Exception {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public CompletableFuture<Map<Currency, BigDecimal>> loadBalances(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Map<Currency, BigDecimal> balances = new HashMap<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT currency, balance FROM basiceconomy_balances WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String currName = rs.getString("currency");
                        BigDecimal balance = rs.getBigDecimal("balance");
                        Currency currency = config.getCurrencies().get(currName.toLowerCase());
                        if (currency != null) {
                            balances.put(currency, balance);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return balances;
        });
    }

    @Override
    public CompletableFuture<Void> saveBalance(UUID uuid, Currency currency, BigDecimal amount) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                String type = config.getStorageType();
                if (type.equals("H2") || type.equals("MYSQL") || type.equals("MARIADB")) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO basiceconomy_balances (uuid, currency, balance) VALUES (?, ?, ?) " +
                                    "ON DUPLICATE KEY UPDATE balance = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, currency.name().toLowerCase());
                        ps.setBigDecimal(3, amount);
                        ps.setBigDecimal(4, amount);
                        ps.executeUpdate();
                    }
                } else if (type.equals("POSTGRESQL")) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO basiceconomy_balances (uuid, currency, balance) VALUES (?, ?, ?) " +
                                    "ON CONFLICT (uuid, currency) DO UPDATE SET balance = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, currency.name().toLowerCase());
                        ps.setBigDecimal(3, amount);
                        ps.setBigDecimal(4, amount);
                        ps.executeUpdate();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public CompletableFuture<List<Map.Entry<UUID, BigDecimal>>> getTopBalances(Currency currency, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Map.Entry<UUID, BigDecimal>> top = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT uuid, balance FROM basiceconomy_balances WHERE currency = ? ORDER BY balance DESC LIMIT ?")) {
                ps.setString(1, currency.name().toLowerCase());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        BigDecimal balance = rs.getBigDecimal("balance");
                        top.add(new AbstractMap.SimpleEntry<>(uuid, balance));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return top;
        });
    }

    @Override
    public CompletableFuture<Void> recordTransaction(TransactionRecord record) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO basiceconomy_transactions (timestamp, sender_uuid, sender_name, receiver_uuid, receiver_name, currency, amount, type, server) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, record.timestamp());
                if (record.senderUuid() != null) {
                    ps.setString(2, record.senderUuid().toString());
                } else {
                    ps.setNull(2, java.sql.Types.VARCHAR);
                }
                ps.setString(3, record.senderName() != null ? record.senderName() : "Unknown");
                ps.setString(4, record.receiverUuid().toString());
                ps.setString(5, record.receiverName() != null ? record.receiverName() : "Unknown");
                ps.setString(6, record.currency().toLowerCase());
                ps.setBigDecimal(7, record.amount());
                ps.setString(8, record.type().name());
                ps.setString(9, record.server() != null ? record.server() : "default");
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public CompletableFuture<List<TransactionRecord>> getTransactions(TransactionFilter filter, int offset, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<TransactionRecord> list = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT id, timestamp, sender_uuid, sender_name, receiver_uuid, receiver_name, currency, amount, type, server FROM basiceconomy_transactions WHERE 1=1 ");
            List<Object> params = new ArrayList<>();

            appendFilter(sql, params, filter);

            sql.append("ORDER BY timestamp DESC, id DESC LIMIT ? OFFSET ?");
            params.add(limit);
            params.add(offset);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        long timestamp = rs.getLong("timestamp");
                        String senderStr = rs.getString("sender_uuid");
                        UUID senderUuid = senderStr != null ? UUID.fromString(senderStr) : null;
                        String senderName = rs.getString("sender_name");
                        UUID receiverUuid = UUID.fromString(rs.getString("receiver_uuid"));
                        String receiverName = rs.getString("receiver_name");
                        String currency = rs.getString("currency");
                        BigDecimal amount = rs.getBigDecimal("amount");
                        TransactionType type = TransactionType.fromString(rs.getString("type"));
                        String server = rs.getString("server");

                        list.add(new TransactionRecord(id, timestamp, senderUuid, senderName, receiverUuid, receiverName, currency, amount, type, server));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<Integer> countTransactions(TransactionFilter filter) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM basiceconomy_transactions WHERE 1=1 ");
            List<Object> params = new ArrayList<>();

            appendFilter(sql, params, filter);

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        });
    }

    private void appendFilter(StringBuilder sql, List<Object> params, TransactionFilter filter) {
        if (filter.playerUuid() != null) {
            String uuidStr = filter.playerUuid().toString();
            TransactionDirection dir = filter.direction() != null ? filter.direction() : TransactionDirection.ALL;
            switch (dir) {
                case SENT -> {
                    sql.append("AND sender_uuid = ? ");
                    params.add(uuidStr);
                }
                case RECEIVED -> {
                    sql.append("AND receiver_uuid = ? ");
                    params.add(uuidStr);
                }
                case ALL -> {
                    sql.append("AND (sender_uuid = ? OR receiver_uuid = ?) ");
                    params.add(uuidStr);
                    params.add(uuidStr);
                }
            }
        }
        if (filter.currency() != null) {
            sql.append("AND currency = ? ");
            params.add(filter.currency().toLowerCase());
        }
        if (filter.sinceMillis() != null) {
            sql.append("AND timestamp >= ? ");
            params.add(filter.sinceMillis());
        }
        if (filter.untilMillis() != null) {
            sql.append("AND timestamp <= ? ");
            params.add(filter.untilMillis());
        }
    }

    @Override
    public CompletableFuture<TransactionStats> getTransactionStats(UUID playerUuid, Currency currency, long sinceMillis) {
        return CompletableFuture.supplyAsync(() -> {
            if (playerUuid == null) return TransactionStats.EMPTY;
            String uuidStr = playerUuid.toString();
            StringBuilder sql = new StringBuilder(
                    "SELECT " +
                            "COALESCE(SUM(CASE WHEN sender_uuid = ? THEN amount ELSE 0 END), 0) AS total_sent, " +
                            "COUNT(CASE WHEN sender_uuid = ? THEN 1 END) AS count_sent, " +
                            "COALESCE(SUM(CASE WHEN receiver_uuid = ? THEN amount ELSE 0 END), 0) AS total_recv, " +
                            "COUNT(CASE WHEN receiver_uuid = ? THEN 1 END) AS count_recv " +
                            "FROM basiceconomy_transactions WHERE (sender_uuid = ? OR receiver_uuid = ?) "
            );
            List<Object> params = new ArrayList<>();
            params.add(uuidStr);
            params.add(uuidStr);
            params.add(uuidStr);
            params.add(uuidStr);
            params.add(uuidStr);
            params.add(uuidStr);

            if (currency != null) {
                sql.append("AND currency = ? ");
                params.add(currency.name().toLowerCase());
            }
            if (sinceMillis > 0) {
                sql.append("AND timestamp >= ? ");
                params.add(sinceMillis);
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        BigDecimal totalSent = rs.getBigDecimal("total_sent");
                        int countSent = rs.getInt("count_sent");
                        BigDecimal totalRecv = rs.getBigDecimal("total_recv");
                        int countRecv = rs.getInt("count_recv");
                        return new TransactionStats(
                                totalSent != null ? totalSent : BigDecimal.ZERO,
                                countSent,
                                totalRecv != null ? totalRecv : BigDecimal.ZERO,
                                countRecv
                        );
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return TransactionStats.EMPTY;
        });
    }

    @Override
    public CompletableFuture<Integer> purgeTransactions(long cutoffMillis) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM basiceconomy_transactions WHERE timestamp < ?")) {
                ps.setLong(1, cutoffMillis);
                return ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
                return 0;
            }
        });
    }
}
