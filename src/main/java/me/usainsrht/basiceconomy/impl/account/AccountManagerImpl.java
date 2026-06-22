package me.usainsrht.basiceconomy.impl.account;

import me.usainsrht.basiceconomy.api.Account;
import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.api.EconomyManager;
import me.usainsrht.basiceconomy.impl.BasicEconomyPlugin;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import me.usainsrht.basiceconomy.impl.storage.Storage;
import me.usainsrht.basiceconomy.impl.sync.SyncProvider;
import me.usainsrht.basiceconomy.impl.sync.PluginMessageSyncProvider;
import me.usainsrht.basiceconomy.impl.sync.RedisSyncProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AccountManagerImpl implements EconomyManager {

    private final BasicEconomyPlugin plugin;
    private final ConfigManager config;
    private volatile Storage storage;
    
    private final Map<UUID, AccountImpl> loadedAccounts = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<AccountImpl>> loadingAccounts = new ConcurrentHashMap<>();
    private final Map<Currency, List<Map.Entry<UUID, BigDecimal>>> baltopCache = new ConcurrentHashMap<>();
    
    // Cache for offline players to prevent database spam
    private static class OfflineCacheEntry {
        final AccountImpl account;
        final long loadTime;

        OfflineCacheEntry(AccountImpl account) {
            this.account = account;
            this.loadTime = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - loadTime > 5000; // 5 seconds TTL
        }
    }
    
    private final Map<UUID, OfflineCacheEntry> offlineCache = new ConcurrentHashMap<>();
    private volatile SyncProvider syncProvider;

    public AccountManagerImpl(BasicEconomyPlugin plugin, ConfigManager config, Storage storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        startTasks();
        initSync();
    }

    private void startTasks() {
        int baltopInterval = plugin.getConfig().getInt("tasks.baltop-update-interval", 300);
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> updateBaltopCache(), 1, baltopInterval, java.util.concurrent.TimeUnit.SECONDS);

        int cacheInterval = plugin.getConfig().getInt("tasks.cache-cleanup-interval", 600);
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> cleanupCache(), cacheInterval, cacheInterval, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void updateBaltopCache() {
        for (Currency currency : config.getCurrencies().values()) {
            if (currency.baltopEnabled()) {
                storage.getTopBalances(currency, 100).thenAccept(top -> {
                    baltopCache.put(currency, top);
                });
            }
        }
    }

    private void cleanupCache() {
        // Remove accounts of offline players from permanent cache
        loadedAccounts.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        // Clean up expired offline cache entries
        offlineCache.values().removeIf(OfflineCacheEntry::isExpired);
    }

    public void handleJoin(UUID uuid) {
        getAccount(uuid);
    }

    public void handleQuit(UUID uuid) {
        // Remove immediately on quit so other servers can safely own the online cache
        loadedAccounts.remove(uuid);
        offlineCache.remove(uuid);
    }

    @Override
    public CompletableFuture<Account> getAccount(UUID uuid) {
        if (Bukkit.getPlayer(uuid) != null) {
            // Online player: cache permanently in loadedAccounts while online
            AccountImpl cached = loadedAccounts.get(uuid);
            if (cached != null) {
                return CompletableFuture.completedFuture(cached);
            }
            return loadingAccounts.computeIfAbsent(uuid, k -> 
                storage.loadBalances(uuid).thenApply(balances -> {
                    AccountImpl account = new AccountImpl(uuid, balances, this);
                    loadedAccounts.put(uuid, account);
                    loadingAccounts.remove(uuid);
                    return account;
                })
            ).thenApply(account -> account);
        } else {
            // Offline player: cache with a short TTL to prevent spam
            OfflineCacheEntry entry = offlineCache.get(uuid);
            if (entry != null && !entry.isExpired()) {
                return CompletableFuture.completedFuture(entry.account);
            }
            return loadingAccounts.computeIfAbsent(uuid, k -> 
                storage.loadBalances(uuid).thenApply(balances -> {
                    AccountImpl account = new AccountImpl(uuid, balances, this);
                    offlineCache.put(uuid, new OfflineCacheEntry(account));
                    loadingAccounts.remove(uuid);
                    return account;
                })
            ).thenApply(account -> account);
        }
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    @Override
    public CompletableFuture<Account> getAccountSync(UUID uuid) {
        try {
            return CompletableFuture.completedFuture(getAccount(uuid).join());
        } catch (Exception e) {
            e.printStackTrace();
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public Collection<Currency> getCurrencies() {
        return config.getCurrencies().values();
    }

    @Override
    public Currency getCurrency(String name) {
        if (name == null) return null;
        return config.getCurrencies().get(name.toLowerCase());
    }

    @Override
    public Currency getDefaultCurrency() {
        return config.getDefaultCurrency();
    }

    @Override
    public CompletableFuture<List<Map.Entry<UUID, BigDecimal>>> getTopAccounts(Currency currency, int limit) {
        List<Map.Entry<UUID, BigDecimal>> cached = baltopCache.get(currency);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached.stream().limit(limit).collect(Collectors.toList()));
        }
        return storage.getTopBalances(currency, limit);
    }

    public CompletableFuture<Void> saveBalance(UUID uuid, Currency currency, BigDecimal amount) {
        return storage.saveBalance(uuid, currency, amount);
    }

    // Sync provider lifecycle and message triggers
    public void initSync() {
        shutdownSync();
        String type = config.getSyncType();
        if (type.equals("PLUGIN_MESSAGE")) {
            syncProvider = new PluginMessageSyncProvider(plugin, this);
        } else if (type.equals("REDIS")) {
            syncProvider = new RedisSyncProvider(
                    plugin, this,
                    config.getRedisHost(),
                    config.getRedisPort(),
                    config.getRedisPassword(),
                    config.getRedisChannel()
            );
        } else {
            syncProvider = null;
        }

        if (syncProvider != null) {
            try {
                syncProvider.init();
                plugin.getLogger().info("Initialized multi-server synchronization: " + type);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize sync provider: " + type);
                e.printStackTrace();
            }
        }
    }

    public void shutdownSync() {
        if (syncProvider != null) {
            try {
                syncProvider.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
            }
            syncProvider = null;
        }
    }

    public void notifyBalanceUpdate(UUID uuid, Currency currency, BigDecimal amount) {
        if (syncProvider != null) {
            syncProvider.sendUpdate(uuid, currency, amount);
        }
    }

    public void handleRemoteBalanceUpdate(UUID uuid, Currency currency, BigDecimal amount) {
        AccountImpl onlineAccount = loadedAccounts.get(uuid);
        if (onlineAccount != null) {
            onlineAccount.updateBalanceInMemory(currency, amount);
        }
        OfflineCacheEntry offlineEntry = offlineCache.get(uuid);
        if (offlineEntry != null) {
            offlineEntry.account.updateBalanceInMemory(currency, amount);
        }
    }
}
