package me.usainsrht.basiceconomy;

import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.api.transaction.*;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import me.usainsrht.basiceconomy.impl.storage.SqlStorage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentHistoryTest {

    private SqlStorage storage;
    private ConfigManager configManager;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "mem:testdb_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        String yaml = String.format("""
                server-id: "test-server"
                storage:
                  type: H2
                  h2-file: "%s"
                currencies:
                  coins:
                    name: "coins"
                    displayname: "Coins"
                  gems:
                    name: "gems"
                    displayname: "Gems"
                history:
                  enabled: true
                  page-size: 5
                  retention-days: 14
                  prune-interval: 3600
                  logging:
                    global:
                      enabled: true
                      file-pattern: "logs/global/{date}.csv"
                      date-format: "yyyy-MM-dd"
                    players:
                      enabled: true
                      file-pattern: "logs/players/{player}.csv"
                      date-format: "yyyy-MM-dd"
                    log-to-console: false
                  high-value-alert:
                    enabled: true
                    threshold: 10000.0
                    permission: "basiceconomy.alert.highvalue"
                messages:
                  prefix: ""
                  history_empty: "No payment history found."
                  history_entry_received: "+<amount> from <player>"
                  history_entry_sent: "-<amount> to <player>"
                """, dbName);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        configManager = new ConfigManager(config);
        storage = new SqlStorage(configManager);
        storage.connect();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (storage != null) {
            storage.disconnect();
        }
    }

    @Test
    void testConfigProperties() {
        assertEquals("test-server", configManager.getServerId());
        assertTrue(configManager.isHistoryEnabled());
        assertEquals(5, configManager.getHistoryPageSize());
        assertEquals(14, configManager.getHistoryRetentionDays());
        assertEquals(3600, configManager.getHistoryPruneInterval());
        assertTrue(configManager.isGlobalLoggingEnabled());
        assertEquals("logs/global/{date}.csv", configManager.getGlobalLogPattern());
        assertEquals("logs/players/{player}.csv", configManager.getPlayerLogPattern());
        assertTrue(configManager.isHighValueAlertEnabled());
        assertEquals(BigDecimal.valueOf(10000.0), configManager.getHighValueAlertThreshold());
    }

    @Test
    void testTransactionStatsCalculations() {
        TransactionStats stats = new TransactionStats(
                BigDecimal.valueOf(150.00),
                2,
                BigDecimal.valueOf(400.00),
                3
        );

        assertEquals(5, stats.totalCount());
        assertEquals(BigDecimal.valueOf(250.00), stats.netFlow());
        // average: (150 + 400) / 5 = 550 / 5 = 110.00
        assertEquals(new BigDecimal("110.00"), stats.averageAmount());
    }

    @Test
    void testSqlStorageTransactions() {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        UUID playerC = UUID.randomUUID();

        long now = System.currentTimeMillis();

        TransactionRecord tx1 = new TransactionRecord(
                0, now - 5000, playerA, "Alice", playerB, "Bob", "coins",
                BigDecimal.valueOf(100.00), TransactionType.PAY, "test-server"
        );
        TransactionRecord tx2 = new TransactionRecord(
                0, now - 3000, playerB, "Bob", playerA, "Alice", "coins",
                BigDecimal.valueOf(30.00), TransactionType.PAY, "test-server"
        );
        TransactionRecord tx3 = new TransactionRecord(
                0, now - 1000, playerA, "Alice", playerC, "Charlie", "gems",
                BigDecimal.valueOf(50.00), TransactionType.PAY, "test-server"
        );

        storage.recordTransaction(tx1).join();
        storage.recordTransaction(tx2).join();
        storage.recordTransaction(tx3).join();

        // 1. Count all transactions for Alice
        TransactionFilter allAlice = TransactionFilter.of(playerA, TransactionDirection.ALL);
        int aliceCount = storage.countTransactions(allAlice).join();
        assertEquals(3, aliceCount);

        // 2. Sent by Alice
        TransactionFilter sentAlice = TransactionFilter.of(playerA, TransactionDirection.SENT);
        List<TransactionRecord> sentList = storage.getTransactions(sentAlice, 0, 10).join();
        assertEquals(2, sentList.size());
        assertEquals("gems", sentList.get(0).currency()); // Most recent first
        assertEquals("coins", sentList.get(1).currency());

        // 3. Received by Alice
        TransactionFilter recvAlice = TransactionFilter.of(playerA, TransactionDirection.RECEIVED);
        List<TransactionRecord> recvList = storage.getTransactions(recvAlice, 0, 10).join();
        assertEquals(1, recvList.size());
        assertEquals("Bob", recvList.get(0).senderName());
        assertEquals(new BigDecimal("30.00"), recvList.get(0).amount());

        // 4. Currency filter: Alice + coins
        TransactionFilter coinsFilter = TransactionFilter.of(playerA, TransactionDirection.ALL, "coins");
        List<TransactionRecord> coinsList = storage.getTransactions(coinsFilter, 0, 10).join();
        assertEquals(2, coinsList.size());

        // 5. Pagination: limit 1, offset 1
        List<TransactionRecord> pageList = storage.getTransactions(allAlice, 1, 1).join();
        assertEquals(1, pageList.size());
        assertEquals(new BigDecimal("30.00"), pageList.get(0).amount()); // middle transaction

        // 6. Transaction Stats for Alice (coins)
        Currency coinsCurrency = configManager.getCurrencies().get("coins");
        TransactionStats stats = storage.getTransactionStats(playerA, coinsCurrency, 0).join();
        assertEquals(new BigDecimal("100.00"), stats.totalSent());
        assertEquals(1, stats.countSent());
        assertEquals(new BigDecimal("30.00"), stats.totalReceived());
        assertEquals(1, stats.countReceived());
        assertEquals(new BigDecimal("-70.00"), stats.netFlow());

        // 7. Purge transactions older than now - 2000 ms
        // tx1 (-5000) and tx2 (-3000) should be purged, tx3 (-1000) should remain
        int purged = storage.purgeTransactions(now - 2000).join();
        assertEquals(2, purged);

        int remaining = storage.countTransactions(TransactionFilter.of(playerA, TransactionDirection.ALL)).join();
        assertEquals(1, remaining);
    }

    @Test
    void testFormatTimeAgo() {
        long now = System.currentTimeMillis();

        assertEquals("just now", me.usainsrht.basiceconomy.impl.command.PaymentHistoryCommand.formatTimeAgo(now - 10_000));
        assertEquals("5m ago", me.usainsrht.basiceconomy.impl.command.PaymentHistoryCommand.formatTimeAgo(now - (5 * 60_000)));
        assertEquals("3h ago", me.usainsrht.basiceconomy.impl.command.PaymentHistoryCommand.formatTimeAgo(now - (3 * 3600_000)));
        assertEquals("yesterday", me.usainsrht.basiceconomy.impl.command.PaymentHistoryCommand.formatTimeAgo(now - (24 * 3600_000)));
    }

    @Test
    void testCsvHeaderConstant() {
        assertEquals(
                "timestamp,datetime_utc,type,currency,amount,sender_uuid,sender_name,receiver_uuid,receiver_name,server",
                me.usainsrht.basiceconomy.impl.transaction.CsvLogWriter.CSV_HEADER
        );
    }

    @Test
    void testCustomTypeAndTimeAgo() {
        String yaml = """
                messages:
                  prefix: ""
                  history_types:
                    pay: "Transfer"
                    admin_add: "Grant"
                  history_time:
                    just_now: "right now"
                    minutes_ago: "<minutes> mins ago"
                    hours_ago: "<hours> hours ago"
                    yesterday: "prior day"
                    days_ago: "<days> days ago"
                    date: "on <date>"
                    date_format: "dd/MM/yyyy"
                """;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        ConfigManager cm = new ConfigManager(config);

        // Test custom types
        net.kyori.adventure.text.Component payComp = cm.getTransactionTypeDisplay(TransactionType.PAY);
        assertEquals("Transfer", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(payComp));

        net.kyori.adventure.text.Component addComp = cm.getTransactionTypeDisplay(TransactionType.ADMIN_ADD);
        assertEquals("Grant", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(addComp));

        // Test custom time ago
        long now = System.currentTimeMillis();
        net.kyori.adventure.text.Component justNow = cm.formatTimeAgo(now - 5_000);
        assertEquals("right now", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(justNow));

        net.kyori.adventure.text.Component minsAgo = cm.formatTimeAgo(now - (12 * 60_000));
        assertEquals("12 mins ago", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(minsAgo));

        net.kyori.adventure.text.Component hoursAgo = cm.formatTimeAgo(now - (5 * 3600_000));
        assertEquals("5 hours ago", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(hoursAgo));

        net.kyori.adventure.text.Component yesterday = cm.formatTimeAgo(now - (24 * 3600_000));
        assertEquals("prior day", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(yesterday));

        net.kyori.adventure.text.Component daysAgo = cm.formatTimeAgo(now - (3 * 24 * 3600_000));
        assertEquals("3 days ago", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(daysAgo));
    }
}

