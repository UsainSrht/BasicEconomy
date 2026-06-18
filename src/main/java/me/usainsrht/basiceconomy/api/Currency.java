package me.usainsrht.basiceconomy.api;

import net.kyori.adventure.text.Component;
import java.math.BigDecimal;

public record Currency(
        String name,
        Component displayName,
        Component displayNamePlural,
        Component symbol,
        String defaultFormat,
        boolean compactFormatting,
        boolean payEnabled,
        boolean baltopEnabled,
        BigDecimal minValue,
        BigDecimal maxValue,
        BigDecimal startValue
) {
    public String format(BigDecimal amount) {
        if (compactFormatting && amount.doubleValue() >= 1000) {
            return formatCompact(amount.doubleValue());
        }
        java.text.DecimalFormat df = new java.text.DecimalFormat(defaultFormat);
        return df.format(amount);
    }

    private String formatCompact(double amount) {
        char[] suffixes = new char[]{'k', 'm', 'b', 't', 'q'};
        int suffixIndex = 0;
        double value = amount;

        while (value >= 1000.0 && suffixIndex < suffixes.length) {
            value /= 1000.0;
            suffixIndex++;
        }

        java.text.DecimalFormat df = new java.text.DecimalFormat("#.##");
        return df.format(value) + (suffixIndex > 0 ? suffixes[suffixIndex - 1] : "");
    }
}
