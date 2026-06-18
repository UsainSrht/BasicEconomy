package me.usainsrht.basiceconomy.api;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Account {

    UUID getUniqueId();

    BigDecimal getBalance(Currency currency);

    CompletableFuture<Boolean> setBalance(Currency currency, BigDecimal amount);

    CompletableFuture<Boolean> addBalance(Currency currency, BigDecimal amount);

    CompletableFuture<Boolean> removeBalance(Currency currency, BigDecimal amount);

    default CompletableFuture<Boolean> hasBalance(Currency currency, BigDecimal amount) {
        return CompletableFuture.completedFuture(getBalance(currency).compareTo(amount) >= 0);
    }
}
