package me.usainsrht.basiceconomy.api.transaction;

public enum TransactionType {
    PAY,
    ADMIN_ADD,
    ADMIN_SET,
    ADMIN_REMOVE,
    SYSTEM;

    public static TransactionType fromString(String str) {
        if (str == null) return PAY;
        try {
            return TransactionType.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PAY;
        }
    }
}
