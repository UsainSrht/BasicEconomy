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
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import me.usainsrht.basiceconomy.impl.util.PlayerVisibility;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BaltopCommand {

    private final JavaPlugin plugin;
    private final AccountManagerImpl accountManager;
    private final ConfigManager config;
    private final me.usainsrht.basiceconomy.impl.util.PlayerFormatter playerFormatter;

    public BaltopCommand(JavaPlugin plugin, AccountManagerImpl accountManager, ConfigManager config, me.usainsrht.basiceconomy.impl.util.PlayerFormatter playerFormatter) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.config = config;
        this.playerFormatter = playerFormatter;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(config.getCommandPermission("baltop")));

        cmd.executes(ctx -> execute(ctx, null, 1));

        registerPlayerSubcommand(cmd, "hide", this::executeHide);
        registerPlayerSubcommand(cmd, "unhide", this::executeUnhide);

        cmd.then(Commands.argument("arg1", StringArgumentType.word())
                .suggests(this::suggestCurrencies)
                .executes(ctx -> executeWithOneArg(ctx, StringArgumentType.getString(ctx, "arg1")))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> execute(ctx, StringArgumentType.getString(ctx, "arg1"), IntegerArgumentType.getInteger(ctx, "page")))));

        return cmd;
    }

    private void registerPlayerSubcommand(
            LiteralArgumentBuilder<CommandSourceStack> cmd,
            String subKey,
            java.util.function.Function<CommandContext<CommandSourceStack>, Integer> executor
    ) {
        String subName = config.getSubcommandName("baltop", subKey);
        String permission = config.getSubcommandPermission(
                "baltop", subKey, config.getCommandPermission("baltop") + "." + subKey);
        List<String> subNames = new ArrayList<>();
        subNames.add(subName);
        subNames.addAll(config.getSubcommandAliases("baltop", subKey));

        for (String name : subNames) {
            cmd.then(Commands.literal(name)
                    .requires(src -> src.getSender().hasPermission(permission))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests(this::suggestPlayers)
                            .executes(executor::apply)));
        }
    }

    private CompletableFuture<Suggestions> suggestCurrencies(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase();
        for (String cur : config.getCurrencies().keySet()) {
            if (cur.startsWith(input)) {
                builder.suggest(cur);
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CommandSender sender = context.getSource().getSender();
        String input = builder.getRemaining().toLowerCase();

        String hidePerm = config.getSubcommandPermission("baltop", "hide", config.getCommandPermission("baltop") + ".hide");
        if (sender.hasPermission(hidePerm)) {
            String hideName = config.getSubcommandName("baltop", "hide");
            if (hideName.toLowerCase().startsWith(input)) {
                builder.suggest(hideName);
            }
        }

        String unhidePerm = config.getSubcommandPermission("baltop", "unhide", config.getCommandPermission("baltop") + ".unhide");
        if (sender.hasPermission(unhidePerm)) {
            String unhideName = config.getSubcommandName("baltop", "unhide");
            if (unhideName.toLowerCase().startsWith(input)) {
                builder.suggest(unhideName);
            }
        }

        boolean hasOffline = sender.hasPermission(config.getOthersOfflinePermission())
                || sender.hasPermission(hidePerm)
                || sender.hasPermission(unhidePerm);

        if (!hasOffline) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (PlayerVisibility.canSeePlayer(sender, player)) {
                    String name = player.getName();
                    if (name.toLowerCase().startsWith(input)) {
                        builder.suggest(name);
                    }
                }
            }
            return builder.buildFuture();
        }

        return CompletableFuture.supplyAsync(() -> {
            java.util.Set<String> names = new java.util.HashSet<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (PlayerVisibility.canSeePlayer(sender, player)) {
                    names.add(player.getName());
                }
            }
            names.addAll(accountManager.getCachedOfflinePlayerNames());
            for (String name : names) {
                if (name.toLowerCase().startsWith(input)) {
                    builder.suggest(name);
                }
            }
            return builder.build();
        });
    }

    private int executeWithOneArg(CommandContext<CommandSourceStack> ctx, String arg1) {
        Currency currency = accountManager.getCurrency(arg1);
        if (currency != null) {
            return execute(ctx, currency.name(), 1);
        }
        try {
            int page = Integer.parseInt(arg1);
            if (page >= 1) {
                return execute(ctx, null, page);
            }
        } catch (NumberFormatException ignored) {
        }
        return execute(ctx, arg1, 1);
    }

    private int execute(CommandContext<CommandSourceStack> ctx, String currName, int page) {
        CommandSender sender = ctx.getSource().getSender();
        Currency currency = currName != null ? accountManager.getCurrency(currName) : accountManager.getDefaultCurrency();

        if (currency == null) {
            sender.sendMessage(config.getMessage(sender, "currency_not_found"));
            return 0;
        }

        if (!currency.baltopEnabled()) {
            sender.sendMessage(config.getMessage(sender, "baltop_disabled"));
            return 0;
        }

        int displayTop = Math.max(1, config.getBaltopDisplayTop());

        List<AccountManagerImpl.BaltopEntry> cachedTop = accountManager.getCachedBaltop(currency);
        if (cachedTop != null) {
            sendBaltopPage(sender, currency, cachedTop, page, displayTop);
        } else {
            int cacheLimit = config.getBaltopCacheLimit();
            accountManager.getTopAccounts(currency, cacheLimit).thenAccept(top -> {
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    List<AccountManagerImpl.BaltopEntry> entries = new ArrayList<>();
                    for (var entry : top) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                        net.kyori.adventure.text.Component display = playerFormatter.formatPlayer(op);
                        String rawName = op.getName() != null ? op.getName() : "Unknown";
                        entries.add(new AccountManagerImpl.BaltopEntry(entry.getKey(), entry.getValue(), display, rawName));
                    }
                    sendBaltopPage(sender, currency, entries, page, displayTop);
                });
            });
        }

        return Command.SINGLE_SUCCESS;
    }

    private void sendBaltopPage(CommandSender sender, Currency currency, List<AccountManagerImpl.BaltopEntry> entries, int page, int displayTop) {
        int totalItems = entries.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / displayTop));
        int targetPage = Math.min(Math.max(1, page), totalPages);

        int startIndex = (targetPage - 1) * displayTop;
        int endIndex = Math.min(startIndex + displayTop, totalItems);

        sender.sendMessage(config.getMessage(sender, "baltop_header",
                "currency", currency.name(),
                "page", String.valueOf(targetPage),
                "max_pages", String.valueOf(totalPages)));

        for (int i = startIndex; i < endIndex; i++) {
            AccountManagerImpl.BaltopEntry entry = entries.get(i);
            int pos = i + 1;
            sender.sendMessage(config.getMessage(sender, "baltop_entry",
                    "position", String.valueOf(pos),
                    "player", entry.getPlayerDisplay(),
                    "amount", currency.format(entry.getBalance())));
        }

        if (sender instanceof Player player) {
            String playerPos = accountManager.getPlayerPosition(player.getUniqueId(), currency);
            sender.sendMessage(config.getMessage(sender, "baltop_footer",
                    "position", playerPos,
                    "currency", currency.name()));
        }
    }

    private int executeHide(CommandContext<CommandSourceStack> ctx) {
        return executeHideToggle(ctx, true);
    }

    private int executeUnhide(CommandContext<CommandSourceStack> ctx) {
        return executeHideToggle(ctx, false);
    }

    private int executeHideToggle(CommandContext<CommandSourceStack> ctx, boolean hide) {
        CommandSender sender = ctx.getSource().getSender();

        CompletableFuture.runAsync(() -> {
            OfflinePlayer target = resolvePlayer(StringArgumentType.getString(ctx, "player"));
            if (target == null) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                        sender.sendMessage(config.getMessage("player_not_found")));
                return;
            }

            String playerName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
            boolean changed = hide
                    ? config.addBaltopHiddenPlayer(target.getUniqueId())
                    : config.removeBaltopHiddenPlayer(target.getUniqueId());

            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                if (!changed) {
                    sender.sendMessage(config.getMessage(
                            hide ? "baltop_already_hidden" : "baltop_not_hidden",
                            "player", playerName));
                    return;
                }

                plugin.saveConfig();
                accountManager.refreshBaltopCache();
                sender.sendMessage(config.getMessage(
                        hide ? "baltop_hide_success" : "baltop_unhide_success",
                        "player", playerName));
            });
        });

        return Command.SINGLE_SUCCESS;
    }

    private OfflinePlayer resolvePlayer(String targetName) {
        OfflinePlayer target = Bukkit.getPlayer(targetName);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(targetName);
            if (!target.hasPlayedBefore()) {
                return null;
            }
        }
        return target;
    }
}
