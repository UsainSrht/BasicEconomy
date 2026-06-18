package me.usainsrht.basiceconomy.impl.account;

import me.usainsrht.basiceconomy.api.Account;
import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.api.EconomyManager;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import me.usainsrht.basiceconomy.impl.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AccountManagerImpl implements EconomyManager {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Storage storage;
    
    private final Map<UUID, AccountImpl> loadedAccounts = new ConcurrentHashMap<>();
    private final Map<Currency, List<Map.Entry<UUID, BigDecimal>>> baltopCache = new ConcurrentHashMap<>();

    public AccountManagerImpl(JavaPlugin plugin, ConfigManager config, Storage storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        startTasks();
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
        // Remove accounts of offline players
        loadedAccounts.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    public void handleJoin(UUID uuid) {
        loadAccount(uuid);
    }

    public void handleQuit(UUID uuid) {
        // We can just leave it in cache until the cleanup task removes it
        // Or remove it immediately if we want strict memory management.
    }

    private CompletableFuture<AccountImpl> loadAccount(UUID uuid) {
        return storage.loadBalances(uuid).thenApply(balances -> {
            AccountImpl account = new AccountImpl(uuid, balances, this);
            loadedAccounts.put(uuid, account);
            return account;
        });
    }

    @Override
    public CompletableFuture<Account> getAccount(UUID uuid) {
        AccountImpl cached = loadedAccounts.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return loadAccount(uuid).thenApply(account -> account);
    }

    @Override
    public CompletableFuture<Account> getAccountSync(UUID uuid) {
        // Since getAccount is async, we can just block for it.
        // This is primarily for Vault.
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
}
