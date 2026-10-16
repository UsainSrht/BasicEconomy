package me.usainsrht.basiceconomy.api.transaction;

import java.util.UUID;

public record TransactionFilter(
        UUID playerUuid,
        TransactionDirection direction,
        String currency,
        Long sinceMillis,
        Long untilMillis
) {
    public static TransactionFilter of(UUID playerUuid) {
        return new TransactionFilter(playerUuid, TransactionDirection.ALL, null, null, null);
    }

    public static TransactionFilter of(UUID playerUuid, TransactionDirection direction) {
        return new TransactionFilter(playerUuid, direction, null, null, null);
    }

    public static TransactionFilter of(UUID playerUuid, TransactionDirection direction, String currency) {
        return new TransactionFilter(playerUuid, direction, currency, null, null);
    }
}
