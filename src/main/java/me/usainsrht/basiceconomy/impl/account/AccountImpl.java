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
    private CompletableFuture<Void> writeQueue = CompletableFuture.completedFuture(null);

    public AccountImpl(UUID uuid, Map<Currency, BigDecimal> balances, AccountManagerImpl manager) {
        this.uuid = uuid;
        this.balances = new java.util.concurrent.ConcurrentHashMap<>(balances);
        this.manager = manager;
    }

    @Override
    public UUID getUniqueId() {
        return uuid;
    }

    @Override
    public synchronized BigDecimal getBalance(Currency currency) {
        return balances.getOrDefault(currency, currency.startValue());
    }

    @Override
    public synchronized CompletableFuture<Boolean> setBalance(Currency currency, BigDecimal amount) {
        if (amount.compareTo(currency.minValue()) < 0 || amount.compareTo(currency.maxValue()) > 0) {
            return CompletableFuture.completedFuture(false);
        }
        balances.put(currency, amount);
        
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        writeQueue = writeQueue.thenCompose(v -> 
            manager.saveBalance(uuid, currency, amount)
        ).thenAccept(v -> {
            manager.notifyBalanceUpdate(uuid, currency, amount);
            resultFuture.complete(true);
        }).exceptionally(ex -> {
            ex.printStackTrace();
            resultFuture.complete(false);
            return null;
        });
        
        return resultFuture;
    }

    public synchronized void updateBalanceInMemory(Currency currency, BigDecimal amount) {
        balances.put(currency, amount);
    }

    @Override
    public synchronized CompletableFuture<Boolean> addBalance(Currency currency, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return CompletableFuture.completedFuture(false);
        BigDecimal newBalance = getBalance(currency).add(amount);
        return setBalance(currency, newBalance);
    }

    @Override
    public synchronized CompletableFuture<Boolean> removeBalance(Currency currency, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return CompletableFuture.completedFuture(false);
        BigDecimal newBalance = getBalance(currency).subtract(amount);
        return setBalance(currency, newBalance);
    }
}
