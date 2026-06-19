package me.usainsrht.basiceconomy.impl.sync;

import java.math.BigDecimal;
import java.util.UUID;
import me.usainsrht.basiceconomy.api.Currency;

public interface SyncProvider {
    void init() throws Exception;
    void shutdown();
    void sendUpdate(UUID uuid, Currency currency, BigDecimal amount);
}
