package me.usainsrht.basiceconomy.impl.transaction;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.api.transaction.TransactionFilter;
import me.usainsrht.basiceconomy.api.transaction.TransactionRecord;
import me.usainsrht.basiceconomy.api.transaction.TransactionStats;
import me.usainsrht.basiceconomy.impl.BasicEconomyPlugin;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import me.usainsrht.basiceconomy.impl.storage.Storage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TransactionManager {

    private final BasicEconomyPlugin plugin;
    private final ConfigManager config;
    private volatile Storage storage;
    private final CsvLogWriter csvLogWriter;
    private ScheduledTask pruneTask;

    public TransactionManager(BasicEconomyPlugin plugin, ConfigManager config, Storage storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.csvLogWriter = new CsvLogWriter(plugin, config);
        startPruningTask();
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public void record(TransactionRecord record) {
        if (!config.isHistoryEnabled()) {
            return;
        }

        // Async storage write
        storage.recordTransaction(record);

        // Async CSV logging
        csvLogWriter.logTransaction(record);

        // Console log
        if (config.isLogToConsole()) {
            plugin.getLogger().info("[Transaction] " + record.type() + ": " +
                    (record.senderName() != null ? record.senderName() : "System") + " -> " +
                    record.receiverName() + " (" + record.amount() + " " + record.currency() + ")");
        }

        // High-value transfer alert
        if (config.isHighValueAlertEnabled() && record.amount().compareTo(config.getHighValueAlertThreshold()) >= 0) {
            Currency currency = config.getCurrencies().get(record.currency().toLowerCase());
            String formattedAmount = currency != null ? currency.format(record.amount()) : record.amount().toPlainString();
            String permission = config.getHighValueAlertPermission();

            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission(permission)) {
                        player.sendMessage(config.getMessage(player, "history_alert_high_value",
                                "sender", record.senderName() != null ? record.senderName() : "System",
                                "receiver", record.receiverName() != null ? record.receiverName() : "Unknown",
                                "amount", formattedAmount,
                                "currency", currency != null ? currency : record.currency(),
                                "id", String.valueOf(record.id())));
                    }
                }
            });
        }
    }

    public CompletableFuture<List<TransactionRecord>> getTransactions(TransactionFilter filter, int offset, int limit) {
        return storage.getTransactions(filter, offset, limit);
    }

    public CompletableFuture<Integer> countTransactions(TransactionFilter filter) {
        return storage.countTransactions(filter);
    }

    public CompletableFuture<TransactionStats> getTransactionStats(UUID playerUuid, Currency currency, long sinceMillis) {
        return storage.getTransactionStats(playerUuid, currency, sinceMillis);
    }

    public void startPruningTask() {
        if (pruneTask != null) {
            pruneTask.cancel();
            pruneTask = null;
        }

        int intervalSeconds = Math.max(300, config.getHistoryPruneInterval());
        pruneTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> {
            int retentionDays = config.getHistoryRetentionDays();
            if (retentionDays > 0) {
                long cutoffMillis = System.currentTimeMillis() - (retentionDays * 86_400_000L);
                storage.purgeTransactions(cutoffMillis).thenAccept(pruned -> {
                    if (pruned > 0 && config.isDebug()) {
                        plugin.getLogger().info("Pruned " + pruned + " old transaction records older than " + retentionDays + " days.");
                    }
                });
            }
        }, 60, intervalSeconds, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (pruneTask != null) {
            pruneTask.cancel();
            pruneTask = null;
        }
        if (csvLogWriter != null) {
            csvLogWriter.shutdown();
        }
    }
}
