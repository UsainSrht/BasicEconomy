package me.usainsrht.basiceconomy.api.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record TransactionStats(
        BigDecimal totalSent,
        int countSent,
        BigDecimal totalReceived,
        int countReceived
) {
    public static final TransactionStats EMPTY = new TransactionStats(BigDecimal.ZERO, 0, BigDecimal.ZERO, 0);

    public int totalCount() {
        return countSent + countReceived;
    }

    public BigDecimal netFlow() {
        return totalReceived.subtract(totalSent);
    }

    public BigDecimal averageAmount() {
        int total = totalCount();
        if (total == 0) return BigDecimal.ZERO;
        BigDecimal sum = totalSent.add(totalReceived);
        return sum.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
