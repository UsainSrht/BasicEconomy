package me.usainsrht.basiceconomy;

import me.usainsrht.basiceconomy.api.Currency;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencyFormattingTest {

    @Test
    void testDefaultFormatting() {
        Currency currency = new Currency(
                "coins", Component.empty(), Component.empty(), Component.empty(),
                "#,##0.00", true, true, true,
                BigDecimal.ZERO, BigDecimal.valueOf(Double.MAX_VALUE), BigDecimal.ZERO
        );

        assertEquals("500.00", currency.format(BigDecimal.valueOf(500)));
        assertEquals("1k", currency.format(BigDecimal.valueOf(1000)));
        assertEquals("1.5k", currency.format(BigDecimal.valueOf(1500)));
        assertEquals("2.5m", currency.format(BigDecimal.valueOf(2500000)));
    }

    @Test
    void testCapitalizedSuffixes() {
        Currency currency = new Currency(
                "coins", Component.empty(), Component.empty(), Component.empty(),
                "#,##0.00", true, "#.##{suffix}", List.of("K", "M", "B", "T", "Q"),
                true, true, BigDecimal.ZERO, BigDecimal.valueOf(Double.MAX_VALUE), BigDecimal.ZERO
        );

        assertEquals("1.5K", currency.format(BigDecimal.valueOf(1500)));
        assertEquals("2.5M", currency.format(BigDecimal.valueOf(2500000)));
        assertEquals("10B", currency.format(BigDecimal.valueOf(10000000000L)));
    }

    @Test
    void testSpacingInFormat() {
        Currency currency = new Currency(
                "coins", Component.empty(), Component.empty(), Component.empty(),
                "#,##0.00", true, "#.## {suffix}", List.of("K", "M", "B", "T", "Q"),
                true, true, BigDecimal.ZERO, BigDecimal.valueOf(Double.MAX_VALUE), BigDecimal.ZERO
        );

        assertEquals("1.5 K", currency.format(BigDecimal.valueOf(1500)));
        assertEquals("2.5 M", currency.format(BigDecimal.valueOf(2500000)));
    }

    @Test
    void testTemplateWithAmountAndSuffix() {
        Currency currency = new Currency(
                "coins", Component.empty(), Component.empty(), Component.empty(),
                "#,##0.00", true, "{amount} {suffix}", List.of("K", "M", "B", "T", "Q"),
                true, true, BigDecimal.ZERO, BigDecimal.valueOf(Double.MAX_VALUE), BigDecimal.ZERO
        );

        assertEquals("1.5 K", currency.format(BigDecimal.valueOf(1500)));
        assertEquals("2.5 M", currency.format(BigDecimal.valueOf(2500000)));
    }

    @Test
    void testSuffixesWithBuiltinSpaces() {
        Currency currency = new Currency(
                "coins", Component.empty(), Component.empty(), Component.empty(),
                "#,##0.00", true, "#.##{suffix}", List.of(" K", " M", " B", " T", " Q"),
                true, true, BigDecimal.ZERO, BigDecimal.valueOf(Double.MAX_VALUE), BigDecimal.ZERO
        );

        assertEquals("1.5 K", currency.format(BigDecimal.valueOf(1500)));
        assertEquals("2.5 M", currency.format(BigDecimal.valueOf(2500000)));
    }

    @Test
    void testCustomPatternWithDecimal() {
        Currency currency = new Currency(
                "coins", Component.empty(), Component.empty(), Component.empty(),
                "#,##0.00", true, "0.0 {suffix}", List.of("K", "M", "B", "T", "Q"),
                true, true, BigDecimal.ZERO, BigDecimal.valueOf(Double.MAX_VALUE), BigDecimal.ZERO
        );

        assertEquals("1.0 M", currency.format(BigDecimal.valueOf(1000000)));
        assertEquals("1.5 K", currency.format(BigDecimal.valueOf(1500)));
    }

    @Test
    void testCompactDisabled() {
        Currency currency = new Currency(
                "coins", Component.empty(), Component.empty(), Component.empty(),
                "#,##0.00", false, "#.##{suffix}", List.of("K", "M", "B"),
                true, true, BigDecimal.ZERO, BigDecimal.valueOf(Double.MAX_VALUE), BigDecimal.ZERO
        );

        assertEquals("1,500.00", currency.format(BigDecimal.valueOf(1500)));
    }
}
