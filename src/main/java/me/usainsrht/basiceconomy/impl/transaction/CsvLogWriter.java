package me.usainsrht.basiceconomy.impl.transaction;

import me.usainsrht.basiceconomy.api.transaction.TransactionRecord;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CsvLogWriter {

    public static final String CSV_HEADER = "timestamp,datetime_utc,type,currency,amount,sender_uuid,sender_name,receiver_uuid,receiver_name,server";

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final ExecutorService executor;

    public CsvLogWriter(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BasicEconomy-CsvLogger");
            t.setDaemon(true);
            return t;
        });
    }

    public void logTransaction(TransactionRecord record) {
        executor.submit(() -> {
            try {
                String row = formatCsvRow(record);
                Instant instant = Instant.ofEpochMilli(record.timestamp());

                // 1. Global Daily Log
                if (config.isGlobalLoggingEnabled()) {
                    String globalPath = resolvePath(
                            config.getGlobalLogPattern(),
                            config.getGlobalLogDateFormat(),
                            instant,
                            null,
                            null,
                            config.getServerId()
                    );
                    appendRow(globalPath, row);
                }

                // 2. Per-Player Log
                if (config.isPlayerLoggingEnabled()) {
                    Set<UUID> loggedUuids = new HashSet<>();

                    // Log for sender
                    if (record.senderUuid() != null) {
                        loggedUuids.add(record.senderUuid());
                        String senderPath = resolvePath(
                                config.getPlayerLogPattern(),
                                config.getPlayerLogDateFormat(),
                                instant,
                                record.senderName(),
                                record.senderUuid(),
                                config.getServerId()
                        );
                        appendRow(senderPath, row);
                    }

                    // Log for receiver
                    if (record.receiverUuid() != null && !loggedUuids.contains(record.receiverUuid())) {
                        String receiverPath = resolvePath(
                                config.getPlayerLogPattern(),
                                config.getPlayerLogDateFormat(),
                                instant,
                                record.receiverName(),
                                record.receiverUuid(),
                                config.getServerId()
                        );
                        appendRow(receiverPath, row);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to write transaction CSV log: " + e.getMessage());
            }
        });
    }

    private void appendRow(String relativePath, String row) {
        File dataFolder = plugin.getDataFolder();
        File file = new File(dataFolder, relativePath);

        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            boolean isNew = !file.exists() || file.length() == 0;
            try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
                if (isNew) {
                    writer.println(CSV_HEADER);
                }
                writer.println(row);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write to CSV log file (" + file.getPath() + "): " + e.getMessage());
        }
    }

    private String resolvePath(String pattern, String dateFormatStr, Instant instant, String playerName, UUID playerUuid, String serverId) {
        DateTimeFormatter dateFormatter;
        try {
            dateFormatter = DateTimeFormatter.ofPattern(dateFormatStr).withZone(ZoneOffset.UTC);
        } catch (Exception e) {
            dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
        }
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH-mm-ss").withZone(ZoneOffset.UTC);

        String dateStr = dateFormatter.format(instant);
        String timeStr = timeFormatter.format(instant);
        String safeName = playerName != null ? sanitizeFilename(playerName) : "unknown";
        String uuidStr = playerUuid != null ? playerUuid.toString() : "none";
        String serverStr = serverId != null ? sanitizeFilename(serverId) : "default";

        return pattern
                .replace("{date}", dateStr)
                .replace("{time}", timeStr)
                .replace("{player}", safeName)
                .replace("{uuid}", uuidStr)
                .replace("{server}", serverStr);
    }

    private String sanitizeFilename(String input) {
        if (input == null) return "unknown";
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String formatCsvRow(TransactionRecord record) {
        Instant instant = Instant.ofEpochMilli(record.timestamp());
        String isoTime = DateTimeFormatter.ISO_INSTANT.format(instant);

        return String.join(",",
                String.valueOf(record.timestamp()),
                escapeCsv(isoTime),
                escapeCsv(record.type().name()),
                escapeCsv(record.currency()),
                record.amount().toPlainString(),
                record.senderUuid() != null ? record.senderUuid().toString() : "",
                escapeCsv(record.senderName() != null ? record.senderName() : "Unknown"),
                record.receiverUuid().toString(),
                escapeCsv(record.receiverName() != null ? record.receiverName() : "Unknown"),
                escapeCsv(record.server() != null ? record.server() : "default")
        );
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
