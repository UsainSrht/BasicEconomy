package me.usainsrht.basiceconomy.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.math.BigDecimal;

public interface EconomyManager {

    CompletableFuture<Account> getAccount(UUID uuid);

    CompletableFuture<Account> getAccountSync(UUID uuid); // Internal or when blocking is absolutely needed

    Collection<Currency> getCurrencies();

    Currency getCurrency(String name);

    Currency getDefaultCurrency();

    CompletableFuture<List<Map.Entry<UUID, BigDecimal>>> getTopAccounts(Currency currency, int limit);
}
