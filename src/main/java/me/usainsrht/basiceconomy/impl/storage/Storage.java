package me.usainsrht.basiceconomy.impl.storage;

import me.usainsrht.basiceconomy.api.Currency;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Storage {

    void connect() throws Exception;

    void disconnect() throws Exception;

    CompletableFuture<Map<Currency, BigDecimal>> loadBalances(UUID uuid);

    CompletableFuture<Void> saveBalance(UUID uuid, Currency currency, BigDecimal amount);
    
    CompletableFuture<List<Map.Entry<UUID, BigDecimal>>> getTopBalances(Currency currency, int limit);
}
