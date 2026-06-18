package me.usainsrht.basiceconomy.impl.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.usainsrht.basiceconomy.api.Currency;
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
                hc.setJdbcUrl("jdbc:h2:./plugins/BasicEconomy/" + config.getStorageH2File() + ";mode=MySQL");
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
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS basiceconomy_balances (" +
                             "uuid VARCHAR(36) NOT NULL," +
                             "currency VARCHAR(64) NOT NULL," +
                             "balance DECIMAL(38, 2) NOT NULL," + // Allows extremely large values
                             "PRIMARY KEY (uuid, currency)" +
                             ");"
             )) {
            ps.execute();
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
                        Currency currency = config.getCurrencies().get(currName);
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
}
