package me.usainsrht.basiceconomy.api.transaction;

public enum TransactionDirection {
    ALL,
    SENT,
    RECEIVED;

    public static TransactionDirection fromString(String str) {
        if (str == null) return ALL;
        try {
            return TransactionDirection.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }
}
