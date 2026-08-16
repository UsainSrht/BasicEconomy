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
    private final ConcurrentHashMap<UUID, Object> loadLocks = new ConcurrentHashMap<>();
    private volatile SyncProvider syncProvider;

    public AccountManagerImpl(BasicEconomyPlugin plugin, ConfigManager config, Storage storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        startTasks();
        initSync();
        repairOnStartup();
    }

    private void startTasks() {
        int baltopInterval = plugin.getConfig().getInt("tasks.baltop-update-interval", 300);
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> updateBaltopCache(), 1, baltopInterval, java.util.concurrent.TimeUnit.SECONDS);

        int cacheInterval = plugin.getConfig().getInt("tasks.cache-cleanup-interval", 600);
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> cleanupCache(), cacheInterval, cacheInterval, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void refreshBaltopCache() {
        updateBaltopCache();
    }

    private void updateBaltopCache() {
        int fetchLimit = 100 + config.getBaltopHiddenPlayers().size();
        for (Currency currency : config.getCurrencies().values()) {
            if (currency.baltopEnabled()) {
                storage.getTopBalances(currency, fetchLimit).thenAccept(top -> {
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
        invalidateAccount(uuid);
        getAccount(uuid);
    }

    public void handleQuit(UUID uuid) {
        // Remove immediately on quit so other servers can safely own the online cache
        invalidateAccount(uuid);
    }

    public void invalidateAccount(UUID uuid) {
        loadedAccounts.remove(uuid);
        offlineCache.remove(uuid);
        CompletableFuture<AccountImpl> loading = loadingAccounts.remove(uuid);
        if (loading != null && !loading.isDone()) {
            loading.cancel(false);
        }
    }

    /**
     * Clears any stale in-memory state left by failed loads and reloads online players from storage.
     */
    public void repairOnStartup() {
        loadedAccounts.clear();
        offlineCache.clear();
        loadingAccounts.clear();

        refreshBaltopCache();

        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            getAccount(player.getUniqueId()).whenComplete((account, error) -> {
                if (error != null) {
                    plugin.getLogger().warning("Failed to repair account for " + player.getName() + ": " + error.getMessage());
                } else if (account instanceof AccountImpl accountImpl) {
                    verifyAccountAgainstStorage(player.getUniqueId(), accountImpl);
                }
            });
        }

        plugin.getLogger().info("Rebuilt account cache from storage for " + Bukkit.getOnlinePlayers().size() + " online player(s).");
    }

    private void verifyAccountAgainstStorage(UUID uuid, AccountImpl account) {
        storage.loadBalances(uuid).whenComplete((dbBalances, error) -> {
            if (error != null || dbBalances == null) {
                return;
            }
            if (dbBalances.isEmpty()) {
                return;
            }

            boolean desynced = false;
            for (Map.Entry<Currency, BigDecimal> entry : dbBalances.entrySet()) {
                if (account.getBalance(entry.getKey()).compareTo(entry.getValue()) != 0) {
                    desynced = true;
                    break;
                }
            }

            if (desynced) {
                plugin.getLogger().warning("Repairing desynced in-memory balances for " + uuid);
                account.reloadBalances(dbBalances);
            }
        });
    }

    @Override
    public CompletableFuture<Account> getAccount(UUID uuid) {
        if (Bukkit.getPlayer(uuid) != null) {
            // Online player: cache permanently in loadedAccounts while online
            AccountImpl cached = loadedAccounts.get(uuid);
            if (cached != null) {
                return CompletableFuture.completedFuture(cached);
            }
            return loadAccount(uuid, true);
        } else {
            // Offline player: cache with a short TTL to prevent spam
            OfflineCacheEntry entry = offlineCache.get(uuid);
            if (entry != null && !entry.isExpired()) {
                return CompletableFuture.completedFuture(entry.account);
            }
            return loadAccount(uuid, false);
        }
    }

    private CompletableFuture<Account> loadAccount(UUID uuid, boolean online) {
        CompletableFuture<AccountImpl> inFlight = loadingAccounts.get(uuid);
        if (inFlight != null) {
            if (inFlight.isCompletedExceptionally()) {
                loadingAccounts.remove(uuid, inFlight);
            } else {
                return inFlight.thenApply(account -> account);
            }
        }

        Object lock = loadLocks.computeIfAbsent(uuid, ignored -> new Object());
        synchronized (lock) {
            try {
                if (online) {
                    AccountImpl cached = loadedAccounts.get(uuid);
                    if (cached != null) {
                        return CompletableFuture.completedFuture(cached);
                    }
                } else {
                    OfflineCacheEntry entry = offlineCache.get(uuid);
                    if (entry != null && !entry.isExpired()) {
                        return CompletableFuture.completedFuture(entry.account);
                    }
                }

                inFlight = loadingAccounts.get(uuid);
                if (inFlight != null) {
                    if (inFlight.isCompletedExceptionally()) {
                        loadingAccounts.remove(uuid, inFlight);
                    } else {
                        return inFlight.thenApply(account -> account);
                    }
                }

                CompletableFuture<AccountImpl> result = new CompletableFuture<>();
                loadingAccounts.put(uuid, result);

                storage.loadBalances(uuid).whenComplete((balances, error) -> {
                    loadingAccounts.remove(uuid);
                    if (error != null) {
                        result.completeExceptionally(error);
                        return;
                    }

                    AccountImpl account = new AccountImpl(uuid, balances, this);
                    if (Bukkit.getPlayer(uuid) != null) {
                        loadedAccounts.put(uuid, account);
                        offlineCache.remove(uuid);
                    } else {
                        offlineCache.put(uuid, new OfflineCacheEntry(account));
                    }
                    result.complete(account);
                });

                return result.thenApply(account -> account);
            } finally {
                loadLocks.remove(uuid, lock);
            }
        }
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    @Override
    public CompletableFuture<Account> getAccountSync(UUID uuid) {
        AccountImpl cached = loadedAccounts.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        OfflineCacheEntry entry = offlineCache.get(uuid);
        if (entry != null && !entry.isExpired()) {
            return CompletableFuture.completedFuture(entry.account);
        }

        try {
            return CompletableFuture.completedFuture(getAccount(uuid).join());
        } catch (Exception e) {
            plugin.getLogger().warning("Account load failed for " + uuid + ", retrying from storage.");
            invalidateAccount(uuid);
            try {
                return CompletableFuture.completedFuture(getAccount(uuid).join());
            } catch (Exception retryError) {
                retryError.printStackTrace();
                return CompletableFuture.completedFuture(null);
            }
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
            return CompletableFuture.completedFuture(filterBaltop(cached, limit));
        }
        int fetchLimit = Math.max(limit + config.getBaltopHiddenPlayers().size(), 100);
        return storage.getTopBalances(currency, fetchLimit).thenApply(top -> filterBaltop(top, limit));
    }

    private List<Map.Entry<UUID, BigDecimal>> filterBaltop(List<Map.Entry<UUID, BigDecimal>> top, int limit) {
        Set<UUID> hidden = config.getBaltopHiddenPlayers();
        return top.stream()
                .filter(entry -> !hidden.contains(entry.getKey()))
                .limit(limit)
                .collect(Collectors.toList());
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
