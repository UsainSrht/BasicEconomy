package me.usainsrht.basiceconomy.impl.integration;

import me.usainsrht.basiceconomy.api.Account;
import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.impl.BasicEconomyPlugin;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class VaultEconomyImpl implements Economy {

    private final BasicEconomyPlugin plugin;
    private final AccountManagerImpl accountManager;

    public VaultEconomyImpl(BasicEconomyPlugin plugin, AccountManagerImpl accountManager) {
        this.plugin = plugin;
        this.accountManager = accountManager;
    }

    private Currency getDefaultCurrency() {
        return accountManager.getDefaultCurrency();
    }

    private Account getAccountSync(UUID uuid) {
        try {
            return accountManager.getAccountSync(uuid).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "BasicEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        Currency def = getDefaultCurrency();
        if (def == null) return String.valueOf(amount);
        return def.format(BigDecimal.valueOf(amount));
    }

    @Override
    public String currencyNamePlural() {
        Currency def = getDefaultCurrency();
        return def == null ? "" : def.name(); // We don't have a string method for plural without component parsing easily, return name
    }

    @Override
    public String currencyNameSingular() {
        Currency def = getDefaultCurrency();
        return def == null ? "" : def.name();
    }

    @Override
    public boolean hasAccount(String playerName) {
        return false; // Vault recommends ignoring string names if possible, but we don't have UUID lookup from string sync.
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        return 0; // Not supported by name
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        Account acc = getAccountSync(player.getUniqueId());
        if (acc == null) return 0;
        return acc.getBalance(getDefaultCurrency()).doubleValue();
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not supported by name.");
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative amount");
        }
        Account acc = getAccountSync(player.getUniqueId());
        if (acc == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        }
        Currency def = getDefaultCurrency();
        if (acc.getBalance(def).doubleValue() < amount) {
            return new EconomyResponse(0, acc.getBalance(def).doubleValue(), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }
        
        try {
            acc.removeBalance(def, BigDecimal.valueOf(amount)).get();
            return new EconomyResponse(amount, acc.getBalance(def).doubleValue(), EconomyResponse.ResponseType.SUCCESS, null);
        } catch (InterruptedException | ExecutionException e) {
            return new EconomyResponse(0, acc.getBalance(def).doubleValue(), EconomyResponse.ResponseType.FAILURE, "Database error");
        }
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not supported by name.");
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative amount");
        }
        Account acc = getAccountSync(player.getUniqueId());
        if (acc == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Account not found");
        }
        Currency def = getDefaultCurrency();
        
        try {
            acc.addBalance(def, BigDecimal.valueOf(amount)).get();
            return new EconomyResponse(amount, acc.getBalance(def).doubleValue(), EconomyResponse.ResponseType.SUCCESS, null);
        } catch (InterruptedException | ExecutionException e) {
            return new EconomyResponse(0, acc.getBalance(def).doubleValue(), EconomyResponse.ResponseType.FAILURE, "Database error");
        }
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "BasicEconomy does not support Vault banks.");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "BasicEconomy does not support Vault banks.");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "BasicEconomy does not support Vault banks.");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "BasicEconomy does not support Vault banks.");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "BasicEconomy does not support Vault banks.");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "BasicEconomy does not support Vault banks.");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "BasicEconomy does not support Vault banks.");
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "BasicEconomy does not support Vault banks.");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "BasicEconomy does not support Vault banks.");
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "BasicEconomy does not support Vault banks.");
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "BasicEconomy does not support Vault banks.");
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return false;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return false;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return true;
    }
}
