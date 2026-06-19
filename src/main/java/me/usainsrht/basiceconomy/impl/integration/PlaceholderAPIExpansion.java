package me.usainsrht.basiceconomy.impl.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.usainsrht.basiceconomy.api.Account;
import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.impl.BasicEconomyPlugin;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class PlaceholderAPIExpansion extends PlaceholderExpansion {

    private final BasicEconomyPlugin plugin;
    private final AccountManagerImpl accountManager;
    private final ConfigManager config;

    public PlaceholderAPIExpansion(BasicEconomyPlugin plugin, AccountManagerImpl accountManager, ConfigManager config) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.config = config;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "basiceconomy";
    }

    @Override
    public @NotNull String getAuthor() {
        List<String> authors = plugin.getDescription().getAuthors();
        return (authors == null || authors.isEmpty()) ? "Usain" : authors.get(0);
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Needed since we register it internally
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String[] args = params.split("_");
        if (args.length < 2) return null;

        if (args[0].equalsIgnoreCase("balance")) {
            // %basiceconomy_balance_<currency>%
            String currName = args[1];
            Currency currency = accountManager.getCurrency(currName);
            if (currency == null) return "Invalid Currency";

            if (player == null) return "";
            
            // Try to resolve using non-blocking getNow to see if it is cached.
            CompletableFuture<Account> future = accountManager.getAccount(player.getUniqueId());
            Account account = future.getNow(null);
            
            if (account == null) {
                // If not cached, return the start value instead of blocking the main thread.
                return currency.format(currency.startValue());
            }
            return currency.format(account.getBalance(currency));
        }

        if (args[0].equalsIgnoreCase("baltop")) {
            // %basiceconomy_baltop_<position>_<currency>%
            if (args.length < 3) return null;
            int position;
            try {
                position = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                return null;
            }
            
            String currName = args[2];
            Currency currency = accountManager.getCurrency(currName);
            if (currency == null) return "Invalid Currency";
            
            try {
                // Since getTopAccounts returns a cached value if available, we can get it without lag.
                List<Map.Entry<UUID, BigDecimal>> top = accountManager.getTopAccounts(currency, position).getNow(null);
                if (top == null) {
                    return "Loading...";
                }
                if (position > top.size() || position < 1) {
                    return "None";
                }
                Map.Entry<UUID, BigDecimal> entry = top.get(position - 1);
                OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getKey());
                String name = target.getName() != null ? target.getName() : "Unknown";
                return name + " - " + currency.format(entry.getValue());
            } catch (Exception e) {
                return "Error";
            }
        }

        return null;
    }
}
