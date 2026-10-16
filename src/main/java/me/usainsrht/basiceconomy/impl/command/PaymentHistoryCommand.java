package me.usainsrht.basiceconomy.impl.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.api.transaction.TransactionDirection;
import me.usainsrht.basiceconomy.api.transaction.TransactionFilter;
import me.usainsrht.basiceconomy.api.transaction.TransactionRecord;
import me.usainsrht.basiceconomy.api.transaction.TransactionStats;
import me.usainsrht.basiceconomy.impl.BasicEconomyPlugin;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import me.usainsrht.basiceconomy.impl.transaction.TransactionManager;
import me.usainsrht.basiceconomy.impl.util.PlayerFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class PaymentHistoryCommand {

    private final BasicEconomyPlugin plugin;
    private final AccountManagerImpl accountManager;
    private final ConfigManager config;
    private final PlayerFormatter playerFormatter;
    private final TransactionManager transactionManager;

    private static final DateTimeFormatter UTC_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    public PaymentHistoryCommand(
            BasicEconomyPlugin plugin,
            AccountManagerImpl accountManager,
            ConfigManager config,
            PlayerFormatter playerFormatter,
            TransactionManager transactionManager
    ) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.config = config;
        this.playerFormatter = playerFormatter;
        this.transactionManager = transactionManager;
    }

    public LiteralArgumentBuilder<CommandSourceStack> buildPayHistorySubcommand(String name, String permission) {
        LiteralArgumentBuilder<CommandSourceStack> node = CommandHelper.literal(name)
                .requires(src -> src.getSender().hasPermission(permission));

        // /pay history
        node.executes(ctx -> executeSelfHistory(ctx, TransactionDirection.ALL, null, 1));

        // /pay history <page: int>
        node.then(Commands.argument("page", IntegerArgumentType.integer(1))
                .executes(ctx -> executeSelfHistory(ctx, TransactionDirection.ALL, null, IntegerArgumentType.getInteger(ctx, "page"))));

        // /pay history <arg1: word>
        node.then(Commands.argument("arg1", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestDirectionOrCurrency(config, builder))
                .executes(ctx -> executeSelfHistoryOneArg(ctx, StringArgumentType.getString(ctx, "arg1")))
                .then(Commands.argument("arg2", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestCurrencyOrPage(config, builder))
                        .executes(ctx -> executeSelfHistoryTwoArgs(ctx, StringArgumentType.getString(ctx, "arg1"), StringArgumentType.getString(ctx, "arg2")))
                        .then(Commands.argument("page3", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeSelfHistoryThreeArgs(ctx, StringArgumentType.getString(ctx, "arg1"), StringArgumentType.getString(ctx, "arg2"), IntegerArgumentType.getInteger(ctx, "page3"))))));

        return node;
    }

    public LiteralArgumentBuilder<CommandSourceStack> buildMoneyHistorySubcommand(String name, String permission) {
        LiteralArgumentBuilder<CommandSourceStack> node = CommandHelper.literal(name)
                .requires(src -> src.getSender().hasPermission(permission));

        // /money history (self)
        node.executes(ctx -> executeSelfHistory(ctx, TransactionDirection.ALL, null, 1));

        // Subcommand: /money history stats <player> [currency] [timeframe]
        String statsPerm = config.getHistoryStatsPermission();
        LiteralArgumentBuilder<CommandSourceStack> statsNode = CommandHelper.literal("stats")
                .requires(src -> src.getSender().hasPermission(statsPerm));

        statsNode.then(Commands.argument("target", StringArgumentType.word())
                .suggests((ctx, builder) -> CommandHelper.suggestPlayers(ctx.getSource().getSender(), accountManager, builder, true))
                .executes(ctx -> executeStats(ctx, StringArgumentType.getString(ctx, "target"), null, "all"))
                .then(Commands.argument("currency", StringArgumentType.word())
                        .suggests((ctx, builder) -> CommandHelper.suggestCurrencies(config, builder))
                        .executes(ctx -> executeStats(ctx, StringArgumentType.getString(ctx, "target"), StringArgumentType.getString(ctx, "currency"), "all"))
                        .then(Commands.argument("timeframe", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestTimeframes(builder))
                                .executes(ctx -> executeStats(ctx, StringArgumentType.getString(ctx, "target"), StringArgumentType.getString(ctx, "currency"), StringArgumentType.getString(ctx, "timeframe"))))));
        node.then(statsNode);

        // /money history <player> [direction|currency|page] ...
        String othersPerm = config.getHistoryOthersPermission();
        node.then(Commands.argument("player", StringArgumentType.word())
                .requires(src -> src.getSender().hasPermission(othersPerm))
                .suggests((ctx, builder) -> CommandHelper.suggestPlayers(ctx.getSource().getSender(), accountManager, builder, true))
                .executes(ctx -> executeOtherHistory(ctx, StringArgumentType.getString(ctx, "player"), TransactionDirection.ALL, null, 1))
                .then(Commands.argument("other_arg1", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestDirectionOrCurrency(config, builder))
                        .executes(ctx -> executeOtherHistoryOneArg(ctx, StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "other_arg1")))
                        .then(Commands.argument("other_arg2", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestCurrencyOrPage(config, builder))
                                .executes(ctx -> executeOtherHistoryTwoArgs(ctx, StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "other_arg1"), StringArgumentType.getString(ctx, "other_arg2")))
                                .then(Commands.argument("other_page3", IntegerArgumentType.integer(1))
                                        .executes(ctx -> executeOtherHistoryThreeArgs(ctx, StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "other_arg1"), StringArgumentType.getString(ctx, "other_arg2"), IntegerArgumentType.getInteger(ctx, "other_page3")))))));

        return node;
    }

    private int executeSelfHistory(CommandContext<CommandSourceStack> ctx, TransactionDirection direction, String currency, int page) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getMessage(sender, "player_only"));
            return 0;
        }
        showHistory(player, player, direction, currency, page, true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeSelfHistoryOneArg(CommandContext<CommandSourceStack> ctx, String arg1) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getMessage(sender, "player_only"));
            return 0;
        }

        try {
            int page = Integer.parseInt(arg1);
            showHistory(player, player, TransactionDirection.ALL, null, page, true);
            return Command.SINGLE_SUCCESS;
        } catch (NumberFormatException ignored) {}

        TransactionDirection dir = parseDirection(arg1);
        if (dir != null) {
            showHistory(player, player, dir, null, 1, true);
            return Command.SINGLE_SUCCESS;
        }

        showHistory(player, player, TransactionDirection.ALL, arg1, 1, true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeSelfHistoryTwoArgs(CommandContext<CommandSourceStack> ctx, String arg1, String arg2) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getMessage(sender, "player_only"));
            return 0;
        }

        TransactionDirection dir = parseDirection(arg1);
        if (dir != null) {
            try {
                int page = Integer.parseInt(arg2);
                showHistory(player, player, dir, null, page, true);
                return Command.SINGLE_SUCCESS;
            } catch (NumberFormatException ignored) {}

            showHistory(player, player, dir, arg2, 1, true);
            return Command.SINGLE_SUCCESS;
        }

        try {
            int page = Integer.parseInt(arg2);
            showHistory(player, player, TransactionDirection.ALL, arg1, page, true);
            return Command.SINGLE_SUCCESS;
        } catch (NumberFormatException ignored) {}

        showHistory(player, player, TransactionDirection.ALL, arg1, 1, true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeSelfHistoryThreeArgs(CommandContext<CommandSourceStack> ctx, String arg1, String arg2, int page) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getMessage(sender, "player_only"));
            return 0;
        }

        TransactionDirection dir = parseDirection(arg1);
        String curr = dir != null ? arg2 : arg1;
        showHistory(player, player, dir != null ? dir : TransactionDirection.ALL, curr, page, true);
        return Command.SINGLE_SUCCESS;
    }

    private int executeOtherHistory(CommandContext<CommandSourceStack> ctx, String targetName, TransactionDirection dir, String currency, int page) {
        CommandSender sender = ctx.getSource().getSender();
        CommandHelper.resolvePlayerAsync(sender, targetName, true).thenAccept(target -> {
            if (target == null) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                        sender.sendMessage(config.getMessage(sender, "player_not_found", "player", targetName)));
                return;
            }
            showHistory(sender, target, dir, currency, page, false);
        });
        return Command.SINGLE_SUCCESS;
    }

    private int executeOtherHistoryOneArg(CommandContext<CommandSourceStack> ctx, String targetName, String arg1) {
        try {
            int page = Integer.parseInt(arg1);
            return executeOtherHistory(ctx, targetName, TransactionDirection.ALL, null, page);
        } catch (NumberFormatException ignored) {}

        TransactionDirection dir = parseDirection(arg1);
        if (dir != null) {
            return executeOtherHistory(ctx, targetName, dir, null, 1);
        }

        return executeOtherHistory(ctx, targetName, TransactionDirection.ALL, arg1, 1);
    }

    private int executeOtherHistoryTwoArgs(CommandContext<CommandSourceStack> ctx, String targetName, String arg1, String arg2) {
        TransactionDirection dir = parseDirection(arg1);
        if (dir != null) {
            try {
                int page = Integer.parseInt(arg2);
                return executeOtherHistory(ctx, targetName, dir, null, page);
            } catch (NumberFormatException ignored) {}

            return executeOtherHistory(ctx, targetName, dir, arg2, 1);
        }

        try {
            int page = Integer.parseInt(arg2);
            return executeOtherHistory(ctx, targetName, TransactionDirection.ALL, arg1, page);
        } catch (NumberFormatException ignored) {}

        return executeOtherHistory(ctx, targetName, TransactionDirection.ALL, arg1, 1);
    }

    private int executeOtherHistoryThreeArgs(CommandContext<CommandSourceStack> ctx, String targetName, String arg1, String arg2, int page) {
        TransactionDirection dir = parseDirection(arg1);
        String curr = dir != null ? arg2 : arg1;
        return executeOtherHistory(ctx, targetName, dir != null ? dir : TransactionDirection.ALL, curr, page);
    }

    private int executeStats(CommandContext<CommandSourceStack> ctx, String targetName, String currName, String timeframe) {
        CommandSender sender = ctx.getSource().getSender();
        CommandHelper.resolvePlayerAsync(sender, targetName, true).thenAccept(target -> {
            if (target == null) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                        sender.sendMessage(config.getMessage(sender, "player_not_found", "player", targetName)));
                return;
            }
            showStats(sender, target, currName, timeframe);
        });
        return Command.SINGLE_SUCCESS;
    }

    public void showHistory(CommandSender viewer, OfflinePlayer target, TransactionDirection direction, String currName, int page, boolean isSelf) {
        if (!config.isHistoryEnabled()) {
            Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                    viewer.sendMessage(config.getMessage(viewer, "history_disabled")));
            return;
        }

        Currency currency = currName != null ? accountManager.getCurrency(currName) : null;
        String curFilterName = currency != null ? currency.name() : currName;
        TransactionFilter filter = new TransactionFilter(target.getUniqueId(), direction, curFilterName, null, null);

        int pageSize = Math.max(1, config.getHistoryPageSize());

        transactionManager.countTransactions(filter).thenAccept(totalCount -> {
            if (totalCount == 0) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                        viewer.sendMessage(config.getMessage(viewer, "history_empty")));
                return;
            }

            int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
            int targetPage = Math.min(Math.max(1, page), totalPages);
            int offset = (targetPage - 1) * pageSize;

            transactionManager.getTransactions(filter, offset, pageSize).thenAccept(records -> {
                // Collect async player formatting futures for unique UUIDs on this page
                java.util.Map<java.util.UUID, CompletableFuture<Component>> displayFutures = new java.util.HashMap<>();
                for (TransactionRecord record : records) {
                    if (record.senderUuid() != null) {
                        displayFutures.computeIfAbsent(record.senderUuid(),
                                u -> playerFormatter.formatPlayerAsync(Bukkit.getOfflinePlayer(u)));
                    }
                    if (record.receiverUuid() != null) {
                        displayFutures.computeIfAbsent(record.receiverUuid(),
                                u -> playerFormatter.formatPlayerAsync(Bukkit.getOfflinePlayer(u)));
                    }
                }

                CompletableFuture.allOf(displayFutures.values().toArray(new CompletableFuture[0])).thenAccept(ignored -> {
                    java.util.Map<java.util.UUID, Component> resolvedDisplays = new java.util.HashMap<>();
                    for (java.util.Map.Entry<java.util.UUID, CompletableFuture<Component>> entry : displayFutures.entrySet()) {
                        resolvedDisplays.put(entry.getKey(), entry.getValue().join());
                    }

                    Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                        // Header
                        viewer.sendMessage(config.getMessage(viewer, "history_header",
                                "page", String.valueOf(targetPage),
                                "max_pages", String.valueOf(totalPages),
                                "currency", currency != null ? currency : (currName != null ? currName : "all")));

                        // Entries
                        for (TransactionRecord record : records) {
                            Component senderDisplay = record.senderUuid() != null
                                    ? resolvedDisplays.getOrDefault(record.senderUuid(), Component.text(record.senderName() != null ? record.senderName() : "Unknown"))
                                    : Component.text(record.senderName() != null ? record.senderName() : "System");

                            Component receiverDisplay = resolvedDisplays.getOrDefault(record.receiverUuid(),
                                    Component.text(record.receiverName() != null ? record.receiverName() : "Unknown"));

                            boolean isReceived = record.receiverUuid().equals(target.getUniqueId());
                            Component counterpartDisplay = isReceived ? senderDisplay : receiverDisplay;

                            Currency recCurrency = accountManager.getCurrency(record.currency());
                            String formattedAmount = recCurrency != null
                                    ? recCurrency.format(record.amount())
                                    : record.amount().toPlainString() + " " + record.currency();

                            Component timeAgo = config.formatTimeAgo(viewer instanceof net.kyori.adventure.audience.Audience a ? a : null, record.timestamp());
                            Component typeDisplay = config.getTransactionTypeDisplay(viewer instanceof net.kyori.adventure.audience.Audience a ? a : null, record.type());
                            String dateUtc = UTC_DATE_FORMAT.format(Instant.ofEpochMilli(record.timestamp()));

                            String msgKey = isReceived ? "history_entry_received" : "history_entry_sent";
                            Component entryLine = config.getMessage(viewer, msgKey,
                                    "amount", formattedAmount,
                                    "player", counterpartDisplay,
                                    "time_ago", timeAgo,
                                    "type", typeDisplay,
                                    "currency", recCurrency != null ? recCurrency : record.currency());

                            // Tooltip
                            Component tooltip = config.getMessage(viewer, "history_tooltip",
                                    "id", String.valueOf(record.id()),
                                    "date", dateUtc,
                                    "type", typeDisplay,
                                    "amount", formattedAmount,
                                    "sender", senderDisplay,
                                    "receiver", receiverDisplay,
                                    "server", record.server() != null ? record.server() : "default",
                                    "currency", recCurrency != null ? recCurrency : record.currency());

                            Component hoverableEntry = entryLine.hoverEvent(HoverEvent.showText(tooltip));
                            viewer.sendMessage(hoverableEntry);
                        }

                        // Footer with clickable pagination
                        String cmdBase = buildPaginationCommand(target, isSelf, direction, currName);
                        int prevPage = Math.max(1, targetPage - 1);
                        int nextPage = Math.min(totalPages, targetPage + 1);

                        String prevCmd = cmdBase + " " + prevPage;
                        String nextCmd = cmdBase + " " + nextPage;

                        viewer.sendMessage(config.getMessage(viewer, "history_footer",
                                "prev_cmd", prevCmd,
                                "next_cmd", nextCmd,
                                "page", String.valueOf(targetPage),
                                "max_pages", String.valueOf(totalPages)));
                    });
                });
            });
        });
    }

    private String buildPaginationCommand(OfflinePlayer target, boolean isSelf, TransactionDirection direction, String currName) {
        StringBuilder sb = new StringBuilder();
        if (isSelf) {
            sb.append("/pay history");
        } else {
            sb.append("/money history ").append(target.getName() != null ? target.getName() : target.getUniqueId().toString());
        }

        if (direction != null && direction != TransactionDirection.ALL) {
            sb.append(" ").append(direction.name().toLowerCase(Locale.ROOT));
        }
        if (currName != null && !currName.isEmpty()) {
            sb.append(" ").append(currName);
        }
        return sb.toString();
    }

    public void showStats(CommandSender viewer, OfflinePlayer target, String currName, String timeframe) {
        if (!config.isHistoryEnabled()) {
            Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                    viewer.sendMessage(config.getMessage(viewer, "history_disabled")));
            return;
        }

        long sinceMillis = parseTimeframeToMillis(timeframe);
        Currency currency = currName != null ? accountManager.getCurrency(currName) : accountManager.getDefaultCurrency();
        if (currency == null) {
            Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                    viewer.sendMessage(config.getMessage(viewer, "currency_not_found", "currency", currName != null ? currName : "")));
            return;
        }

        transactionManager.getTransactionStats(target.getUniqueId(), currency, sinceMillis).thenAccept(stats -> {
            playerFormatter.formatPlayerAsync(target).thenAccept(playerDisplay -> {
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    BigDecimal netFlow = stats.netFlow();
                    String flowColor = netFlow.compareTo(BigDecimal.ZERO) >= 0 ? "<green>" : "<red>";
                    String flowSign = netFlow.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";

                    viewer.sendMessage(config.getMessage(viewer, "history_stats_header",
                            "player", playerDisplay,
                            "timeframe", timeframe != null ? timeframe.toUpperCase(Locale.ROOT) : "ALL"));

                    viewer.sendMessage(config.getMessage(viewer, "history_stats_line_currency",
                            "currency", currency));

                    viewer.sendMessage(config.getMessage(viewer, "history_stats_line_sent",
                            "amount", currency.format(stats.totalSent()),
                            "count", String.valueOf(stats.countSent()),
                            "currency", currency));

                    viewer.sendMessage(config.getMessage(viewer, "history_stats_line_received",
                            "amount", currency.format(stats.totalReceived()),
                            "count", String.valueOf(stats.countReceived()),
                            "currency", currency));

                    viewer.sendMessage(config.getMessage(viewer, "history_stats_line_net",
                            "amount", currency.format(netFlow.abs()),
                            "flow_color", flowColor,
                            "flow_sign", flowSign,
                            "currency", currency));

                    viewer.sendMessage(config.getMessage(viewer, "history_stats_line_avg",
                            "amount", currency.format(stats.averageAmount()),
                            "currency", currency));

                    viewer.sendMessage(config.getMessage(viewer, "history_stats_footer",
                            "currency", currency));
                });
            });
        });
    }

    private static long parseTimeframeToMillis(String timeframe) {
        if (timeframe == null) return 0L;
        return switch (timeframe.toLowerCase(Locale.ROOT)) {
            case "24h", "1d" -> System.currentTimeMillis() - 86_400_000L;
            case "7d", "1w" -> System.currentTimeMillis() - (7 * 86_400_000L);
            case "30d", "1m" -> System.currentTimeMillis() - (30 * 86_400_000L);
            default -> 0L;
        };
    }

    public static String formatTimeAgo(long timestamp) {
        long diff = Math.max(0, System.currentTimeMillis() - timestamp);
        long seconds = diff / 1000L;
        if (seconds < 60) {
            return "just now";
        }
        long minutes = seconds / 60L;
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60L;
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = hours / 24L;
        if (days == 1) {
            return "yesterday";
        }
        if (days < 7) {
            return days + "d ago";
        }
        Instant instant = Instant.ofEpochMilli(timestamp);
        return DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(instant);
    }

    private TransactionDirection parseDirection(String input) {
        if (input == null) return null;
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "sent", "send", "out" -> TransactionDirection.SENT;
            case "received", "recv", "in" -> TransactionDirection.RECEIVED;
            case "all" -> TransactionDirection.ALL;
            default -> null;
        };
    }

    private static CompletableFuture<Suggestions> suggestDirectionOrCurrency(ConfigManager config, SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase();
        for (String dir : List.of("all", "sent", "received")) {
            if (dir.startsWith(input)) {
                builder.suggest(dir);
            }
        }
        for (String cur : config.getCurrencies().keySet()) {
            if (cur.startsWith(input)) {
                builder.suggest(cur);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCurrencyOrPage(ConfigManager config, SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase();
        for (String cur : config.getCurrencies().keySet()) {
            if (cur.startsWith(input)) {
                builder.suggest(cur);
            }
        }
        for (String page : List.of("1", "2", "3", "4", "5")) {
            if (page.startsWith(input)) {
                builder.suggest(page);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestTimeframes(SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase();
        for (String tf : List.of("24h", "7d", "30d", "all")) {
            if (tf.startsWith(input)) {
                builder.suggest(tf);
            }
        }
        return builder.buildFuture();
    }
}
