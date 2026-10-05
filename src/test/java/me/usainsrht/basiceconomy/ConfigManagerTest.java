package me.usainsrht.basiceconomy;

import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {

    @Test
    void testConfigCompactFormattingParsing() {
        String yaml = """
                default-currency: "coins"
                compact_format: "#.## {suffix}"
                compact_suffixes: "K, M, B, T, Q"
                currencies:
                  coins:
                    name: "coins"
                    displayname: "Coins"
                    compact_formatting: true
                  gems:
                    name: "gems"
                    displayname: "Gems"
                    compact_formatting: true
                    compact_format: "#.0{suffix}"
                    compact_suffixes:
                      - "k"
                      - "m"
                      - "b"
                """;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        ConfigManager manager = new ConfigManager(config);

        Currency coins = manager.getCurrencies().get("coins");
        assertNotNull(coins);
        assertEquals("#.## {suffix}", coins.compactFormat());
        assertEquals(List.of("K", "M", "B", "T", "Q"), coins.compactSuffixes());
        assertEquals("1.5 K", coins.format(BigDecimal.valueOf(1500)));

        Currency gems = manager.getCurrencies().get("gems");
        assertNotNull(gems);
        assertEquals("#.0{suffix}", gems.compactFormat());
        assertEquals(List.of("k", "m", "b"), gems.compactSuffixes());
        assertEquals("1.5k", gems.format(BigDecimal.valueOf(1500)));
    }

    @Test
    void testMessageCurrencyPlaceholders() {
        String yaml = """
                currencies:
                  coins:
                    name: "coins"
                    displayname: "<gold>Coins</gold>"
                    displayname_plural: "<gold>Coins</gold>"
                    symbol: "$"
                messages:
                  prefix: ""
                  balance_self: "Balance: <amount> <currency> (<currency_symbol>)"
                  test_display: "Currency: <currency_display>"
                """;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        ConfigManager manager = new ConfigManager(config);
        Currency coins = manager.getCurrencies().get("coins");

        net.kyori.adventure.text.Component msg = manager.getMessage("balance_self", "amount", "100.00", "currency", coins);
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(msg);
        assertEquals("Balance: 100.00 coins ($)", plain);

        net.kyori.adventure.text.Component msgDisplay = manager.getMessage("test_display", "currency", "coins");
        String plainDisplay = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(msgDisplay);
        assertEquals("Currency: Coins", plainDisplay);
    }
}
