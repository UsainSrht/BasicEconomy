package me.usainsrht.basiceconomy.api;

import net.kyori.adventure.text.Component;
import java.math.BigDecimal;
import java.util.List;

public record Currency(
        String name,
        Component displayName,
        Component displayNamePlural,
        Component symbol,
        String defaultFormat,
        boolean compactFormatting,
        String compactFormat,
        List<String> compactSuffixes,
        boolean payEnabled,
        boolean baltopEnabled,
        BigDecimal minValue,
        BigDecimal maxValue,
        BigDecimal startValue
) {
    public Currency {
        if (compactFormat == null || compactFormat.isBlank()) {
            compactFormat = "#.##{suffix}";
        }
        if (compactSuffixes == null || compactSuffixes.isEmpty()) {
            compactSuffixes = List.of("k", "m", "b", "t", "q");
        } else {
            compactSuffixes = List.copyOf(compactSuffixes);
        }
    }

    public Currency(
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
        this(name, displayName, displayNamePlural, symbol, defaultFormat,
                compactFormatting, "#.##{suffix}", List.of("k", "m", "b", "t", "q"),
                payEnabled, baltopEnabled, minValue, maxValue, startValue);
    }

    public String format(BigDecimal amount) {
        if (compactFormatting && amount.doubleValue() >= 1000) {
            return formatCompact(amount.doubleValue());
        }
        java.text.DecimalFormat df;
        try {
            df = new java.text.DecimalFormat(defaultFormat);
        } catch (IllegalArgumentException e) {
            df = new java.text.DecimalFormat("#,##0.00");
        }
        return df.format(amount);
    }

    private String formatCompact(double amount) {
        if (compactSuffixes.isEmpty()) {
            java.text.DecimalFormat df;
            try {
                df = new java.text.DecimalFormat(defaultFormat);
            } catch (IllegalArgumentException e) {
                df = new java.text.DecimalFormat("#,##0.00");
            }
            return df.format(amount);
        }

        int suffixIndex = 0;
        double value = amount;

        while (value >= 1000.0 && suffixIndex < compactSuffixes.size()) {
            value /= 1000.0;
            suffixIndex++;
        }

        String suffix = suffixIndex > 0 ? compactSuffixes.get(suffixIndex - 1) : "";
        String format = (compactFormat != null && !compactFormat.isBlank()) ? compactFormat : "#.##{suffix}";

        String pattern = "#.##";
        String template = format;

        if (template.contains("{amount}") || template.contains("{value}")) {
            java.text.DecimalFormat df;
            try {
                df = new java.text.DecimalFormat(pattern);
            } catch (IllegalArgumentException e) {
                df = new java.text.DecimalFormat("#.##");
            }
            String formattedNumber = df.format(value);
            if (suffix.isEmpty()) {
                return template.replace("{suffix}", "")
                        .replace("{amount}", formattedNumber)
                        .replace("{value}", formattedNumber)
                        .trim();
            }
            return template.replace("{amount}", formattedNumber)
                    .replace("{value}", formattedNumber)
                    .replace("{suffix}", suffix);
        }

        if (template.contains("{suffix}")) {
            int suffixIndexInFormat = template.indexOf("{suffix}");
            String beforeSuffix = template.substring(0, suffixIndexInFormat);
            String afterSuffix = template.substring(suffixIndexInFormat + "{suffix}".length());

            String trimmedPattern = beforeSuffix.stripTrailing();
            String spacing = beforeSuffix.substring(trimmedPattern.length());

            if (!trimmedPattern.isEmpty()) {
                pattern = trimmedPattern;
            }

            java.text.DecimalFormat df;
            try {
                df = new java.text.DecimalFormat(pattern);
            } catch (IllegalArgumentException e) {
                df = new java.text.DecimalFormat("#.##");
            }

            String effectiveSpacing = suffix.isEmpty() ? "" : spacing;
            return df.format(value) + effectiveSpacing + suffix + afterSuffix;
        }

        java.text.DecimalFormat df;
        try {
            df = new java.text.DecimalFormat(template);
        } catch (IllegalArgumentException e) {
            df = new java.text.DecimalFormat("#.##");
        }
        return df.format(value) + suffix;
    }
}
