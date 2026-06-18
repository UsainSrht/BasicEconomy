package me.usainsrht.basiceconomy.impl.account;

import me.usainsrht.basiceconomy.api.Account;
import me.usainsrht.basiceconomy.api.Currency;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AccountImpl implements Account {

    private final UUID uuid;
    private final Map<Currency, BigDecimal> balances;
    private final AccountManagerImpl manager;

    public AccountImpl(UUID uuid, Map<Currency, BigDecimal> balances, AccountManagerImpl manager) {
        this.uuid = uuid;
        this.balances = balances;
        this.manager = manager;
    }

    @Override
    public UUID getUniqueId() {
        return uuid;
    }

    @Override
    public BigDecimal getBalance(Currency currency) {
        return balances.getOrDefault(currency, currency.startValue());
    }

    @Override
    public CompletableFuture<Boolean> setBalance(Currency currency, BigDecimal amount) {
        if (amount.compareTo(currency.minValue()) < 0 || amount.compareTo(currency.maxValue()) > 0) {
            return CompletableFuture.completedFuture(false);
        }
        balances.put(currency, amount);
        return manager.saveBalance(uuid, currency, amount).thenApply(v -> true);
    }

    @Override
    public CompletableFuture<Boolean> addBalance(Currency currency, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return CompletableFuture.completedFuture(false);
        BigDecimal newBalance = getBalance(currency).add(amount);
        return setBalance(currency, newBalance);
    }

    @Override
    public CompletableFuture<Boolean> removeBalance(Currency currency, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return CompletableFuture.completedFuture(false);
        BigDecimal newBalance = getBalance(currency).subtract(amount);
        return setBalance(currency, newBalance);
    }
}
