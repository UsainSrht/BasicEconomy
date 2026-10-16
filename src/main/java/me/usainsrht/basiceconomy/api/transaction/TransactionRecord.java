package me.usainsrht.basiceconomy.api.transaction;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionRecord(
        long id,
        long timestamp,
        UUID senderUuid,
        String senderName,
        UUID receiverUuid,
        String receiverName,
        String currency,
        BigDecimal amount,
        TransactionType type,
        String server
) {
    public boolean isSender(UUID uuid) {
        return senderUuid != null && senderUuid.equals(uuid);
    }

    public boolean isReceiver(UUID uuid) {
        return receiverUuid != null && receiverUuid.equals(uuid);
    }
}
