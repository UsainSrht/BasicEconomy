package me.usainsrht.basiceconomy.impl.integration;

import me.usainsrht.basiceconomy.impl.BasicEconomyPlugin;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.AccountPermission;
import net.milkbowl.vault2.economy.EconomyResponse;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.concurrent.ExecutionException;

public class Vault2EconomyImpl implements Economy {
    private final BasicEconomyPlugin plugin;
    private final AccountManagerImpl accountManager;

    public Vault2EconomyImpl(BasicEconomyPlugin plugin, AccountManagerImpl accountManager) {
        this.plugin = plugin;
        this.accountManager = accountManager;
    }

    private me.usainsrht.basiceconomy.api.Currency getDefaultCurrency() {
        return accountManager.getDefaultCurrency();
    }

    private me.usainsrht.basiceconomy.api.Account getAccountSync(UUID uuid) {
        try {
            return accountManager.getAccountSync(uuid).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean updateAccountPermission(String pluginName, UUID accountID, UUID targetID, AccountPermission permission, boolean grant) {
        return false;
    }

    @Override
    public boolean hasAccountPermission(String pluginName, UUID accountID, UUID targetID, AccountPermission permission) {
        return false;
    }

    @Override
    public boolean removeAccountMember(String pluginName, UUID accountID, UUID targetID) {
        return false;
    }

    @Override
    public boolean addAccountMember(String pluginName, UUID accountID, UUID targetID, AccountPermission... permissions) {
        return false;
    }

    @Override
    public boolean addAccountMember(String pluginName, UUID accountID, UUID targetID) {
        return false;
    }

    @Override
    public boolean isAccountMember(String pluginName, UUID accountID, UUID targetID) {
        return false;
    }

    @Override
    public boolean setOwner(String pluginName, UUID accountID, UUID targetID) {
        return false;
    }

    @Override
    public boolean isAccountOwner(String pluginName, UUID accountID, UUID targetID) {
        return false;
    }

    @Override
    public boolean createSharedAccount(String pluginName, UUID accountID, String name, UUID ownerID) {
        return false;
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
    public boolean hasAccount(UUID accountID) {
        return true;
    }

    @Override
    public boolean hasAccount(UUID accountID, String world) {
        return true;
    }

    @Override
    public boolean createAccount(UUID accountID, String name, String world, boolean active) {
        return true;
    }

    @Override
    public boolean createAccount(UUID accountID, String name, String world) {
        return true;
    }

    @Override
    public boolean createAccount(UUID accountID, String name, boolean active) {
        return true;
    }

    @Override
    public boolean createAccount(UUID accountID, String name) {
        return true;
    }

    @Override
    public java.util.List<String> currencies() {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (me.usainsrht.basiceconomy.api.Currency currency : accountManager.getCurrencies()) {
            list.add(currency.name());
        }
        return list;
    }

    @Override
    public String defaultCurrencyNameSingular(String string) {
        me.usainsrht.basiceconomy.api.Currency def = getDefaultCurrency();
        return def == null ? "" : def.name();
    }

    @Override
    public String defaultCurrencyNamePlural(String string) {
        me.usainsrht.basiceconomy.api.Currency def = getDefaultCurrency();
        return def == null ? "" : def.name();
    }

    @Override
    public String getDefaultCurrency(String string) {
        me.usainsrht.basiceconomy.api.Currency def = getDefaultCurrency();
        return def == null ? "" : def.name();
    }

    @Override
    public boolean hasCurrency(String currency) {
        return accountManager.getCurrency(currency) != null;
    }

    @Override
    public java.util.Optional<String> getAccountName(UUID accountID) {
        org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(accountID);
        return java.util.Optional.ofNullable(player.getName());
    }

    @Override
    public java.util.Map<UUID, String> getUUIDNameMap() {
        return java.util.Map.of();
    }

    @Override
    public boolean deleteAccount(String pluginName, UUID accountID) {
        return false;
    }

    @Override
    public boolean renameAccount(String pluginName, UUID accountID, String name) {
        return false;
    }

    @Override
    public boolean renameAccount(UUID accountID, String name) {
        return false;
    }

    @Override
    public boolean accountSupportsCurrency(String pluginName, UUID accountID, String currency) {
        return accountManager.getCurrency(currency) != null;
    }

    @Override
    public boolean accountSupportsCurrency(String pluginName, UUID accountID, String currency, String world) {
        return accountSupportsCurrency(pluginName, accountID, currency);
    }

    @Override
    public BigDecimal getBalance(String pluginName, UUID accountID) {
        me.usainsrht.basiceconomy.api.Account acc = getAccountSync(accountID);
        if (acc == null) return BigDecimal.ZERO;
        return acc.getBalance(getDefaultCurrency());
    }

    @Override
    public BigDecimal getBalance(String pluginName, UUID accountID, String currency) {
        me.usainsrht.basiceconomy.api.Account acc = getAccountSync(accountID);
        if (acc == null) return BigDecimal.ZERO;
        me.usainsrht.basiceconomy.api.Currency cur = accountManager.getCurrency(currency);
        if (cur == null) return BigDecimal.ZERO;
        return acc.getBalance(cur);
    }

    @Override
    public BigDecimal getBalance(String pluginName, UUID accountID, String currency, String world) {
        return getBalance(pluginName, accountID, currency);
    }

    @Override
    public boolean has(String pluginName, UUID accountID, BigDecimal amount) {
        return getBalance(pluginName, accountID).compareTo(amount) >= 0;
    }

    @Override
    public boolean has(String pluginName, UUID accountID, String currency, BigDecimal amount) {
        return getBalance(pluginName, accountID, currency).compareTo(amount) >= 0;
    }

    @Override
    public boolean has(String pluginName, UUID accountID, String currency, String world, BigDecimal amount) {
        return has(pluginName, accountID, currency, amount);
    }

    @Override
    public EconomyResponse withdraw(String pluginName, UUID accountID, BigDecimal amount) {
        me.usainsrht.basiceconomy.api.Currency def = getDefaultCurrency();
        if (def == null) {
            return new EconomyResponse(amount, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Default currency not found");
        }
        return withdraw(pluginName, accountID, def.name(), amount);
    }

    @Override
    public EconomyResponse withdraw(String pluginName, UUID accountID, String currency, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative amount");
        }
        me.usainsrht.basiceconomy.api.Account acc = getAccountSync(accountID);
        if (acc == null) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Account not found");
        }
        me.usainsrht.basiceconomy.api.Currency cur = accountManager.getCurrency(currency);
        if (cur == null) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Currency not found");
        }
        BigDecimal currentBal = acc.getBalance(cur);
        if (currentBal.compareTo(amount) < 0) {
            return new EconomyResponse(BigDecimal.ZERO, currentBal, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }
        try {
            acc.removeBalance(cur, amount).get();
            return new EconomyResponse(amount, acc.getBalance(cur), EconomyResponse.ResponseType.SUCCESS, null);
        } catch (InterruptedException | ExecutionException e) {
            return new EconomyResponse(BigDecimal.ZERO, currentBal, EconomyResponse.ResponseType.FAILURE, "Database error");
        }
    }

    @Override
    public EconomyResponse withdraw(String pluginName, UUID accountID, String currency, String world, BigDecimal amount) {
        return withdraw(pluginName, accountID, currency, amount);
    }

    @Override
    public EconomyResponse deposit(String pluginName, UUID accountID, BigDecimal amount) {
        me.usainsrht.basiceconomy.api.Currency def = getDefaultCurrency();
        if (def == null) {
            return new EconomyResponse(amount, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Default currency not found");
        }
        return deposit(pluginName, accountID, def.name(), amount);
    }

    @Override
    public EconomyResponse deposit(String pluginName, UUID accountID, String currency, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative amount");
        }
        me.usainsrht.basiceconomy.api.Account acc = getAccountSync(accountID);
        if (acc == null) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Account not found");
        }
        me.usainsrht.basiceconomy.api.Currency cur = accountManager.getCurrency(currency);
        if (cur == null) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "Currency not found");
        }
        try {
            acc.addBalance(cur, amount).get();
            return new EconomyResponse(amount, acc.getBalance(cur), EconomyResponse.ResponseType.SUCCESS, null);
        } catch (InterruptedException | ExecutionException e) {
            return new EconomyResponse(BigDecimal.ZERO, acc.getBalance(cur), EconomyResponse.ResponseType.FAILURE, "Database error");
        }
    }

    @Override
    public EconomyResponse deposit(String pluginName, UUID accountID, String currency, String world, BigDecimal amount) {
        return deposit(pluginName, accountID, currency, amount);
    }

    @Override
    public String format(String currency, BigDecimal amount) {
        me.usainsrht.basiceconomy.api.Currency cur = accountManager.getCurrency(currency);
        if (cur == null) return amount.toString();
        return cur.format(amount);
    }

    @Override
    public String format(String currency, BigDecimal amount, String world) {
        return format(currency, amount);
    }

    @Override
    public String format(BigDecimal amount, String worldOrCurrency) {
        if (accountManager.getCurrency(worldOrCurrency) != null) {
            return format(worldOrCurrency, amount);
        }
        me.usainsrht.basiceconomy.api.Currency def = getDefaultCurrency();
        if (def == null) return amount.toString();
        return def.format(amount);
    }

    @Override
    public String format(BigDecimal amount) {
        me.usainsrht.basiceconomy.api.Currency def = getDefaultCurrency();
        if (def == null) return amount.toString();
        return def.format(amount);
    }

    @Override
    public boolean hasMultiCurrencySupport() {
        return true;
    }

    @Override
    public boolean hasSharedAccountSupport() {
        return false;
    }

    @Override
    public int fractionalDigits(String currency) {
        return 2;
    }

    public String currencyNamePlural(String currency) {
        me.usainsrht.basiceconomy.api.Currency cur = accountManager.getCurrency(currency);
        return cur == null ? "" : cur.name();
    }

    public String currencyNameSingular(String currency) {
        me.usainsrht.basiceconomy.api.Currency cur = accountManager.getCurrency(currency);
        return cur == null ? "" : cur.name();
    }
}
