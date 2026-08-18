package me.usainsrht.basiceconomy.impl.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import me.usainsrht.basiceconomy.impl.util.PlayerFormatter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class BaltopCommand {

    private final JavaPlugin plugin;
    private final AccountManagerImpl accountManager;
    private final ConfigManager config;
    private final PlayerFormatter playerFormatter;

    public BaltopCommand(JavaPlugin plugin, AccountManagerImpl accountManager, ConfigManager config, PlayerFormatter playerFormatter) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.config = config;
        this.playerFormatter = playerFormatter;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        String basePerm = config.getCommandPermission("baltop");

        LiteralArgumentBuilder<CommandSourceStack> cmd = CommandHelper.literal(name)
                .requires(src -> src.getSender().hasPermission(basePerm));

        cmd.executes(ctx -> execute(ctx, null, 1));

        registerPlayerSubcommand(cmd, "hide", this::executeHide);
        registerPlayerSubcommand(cmd, "unhide", this::executeUnhide);

        cmd.then(Commands.argument("arg1", StringArgumentType.word())
                .suggests((ctx, builder) -> CommandHelper.suggestCurrencies(config, builder))
                .executes(ctx -> executeWithOneArg(ctx, StringArgumentType.getString(ctx, "arg1")))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> execute(ctx, StringArgumentType.getString(ctx, "arg1"), IntegerArgumentType.getInteger(ctx, "page")))));

        return cmd;
    }

    private void registerPlayerSubcommand(
            LiteralArgumentBuilder<CommandSourceStack> cmd,
            String subKey,
            Function<CommandContext<CommandSourceStack>, Integer> executor
    ) {
        String permission = config.getSubcommandPermission(
                "baltop", subKey, config.getCommandPermission("baltop") + "." + subKey);

        for (String name : config.getSubcommandNamesWithAliases("baltop", subKey)) {
            LiteralArgumentBuilder<CommandSourceStack> subNode = CommandHelper.literal(name)
                    .requires(src -> src.getSender().hasPermission(permission))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                CommandSender sender = ctx.getSource().getSender();
                                boolean hasOffline = sender.hasPermission(config.getOthersOfflinePermission())
                                        || sender.hasPermission(permission);
                                return CommandHelper.suggestPlayers(sender, accountManager, builder, hasOffline);
                            })
                            .executes(executor::apply));
            cmd.then(subNode);
        }
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
                List<CompletableFuture<AccountManagerImpl.BaltopEntry>> futures = new ArrayList<>();
                for (var entry : top) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                    String rawName = op.getName() != null ? op.getName() : "Unknown";
                    CompletableFuture<AccountManagerImpl.BaltopEntry> future =
                            playerFormatter.formatPlayerAsync(op)
                                    .thenApply(display ->
                                            new AccountManagerImpl.BaltopEntry(
                                                    entry.getKey(), entry.getValue(), display, rawName));
                    futures.add(future);
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .thenAccept(ignored -> {
                            List<AccountManagerImpl.BaltopEntry> entries = new ArrayList<>();
                            for (CompletableFuture<AccountManagerImpl.BaltopEntry> f : futures) {
                                entries.add(f.join());
                            }
                            Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                                    sendBaltopPage(sender, currency, entries, page, displayTop));
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
        String targetName = StringArgumentType.getString(ctx, "player");

        CommandHelper.resolvePlayerAsync(sender, targetName, true).thenAccept(target -> {
            if (target == null) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                        sender.sendMessage(config.getMessage(sender, "player_not_found")));
                return;
            }

            String playerName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
            boolean changed = hide
                    ? config.addBaltopHiddenPlayer(target.getUniqueId())
                    : config.removeBaltopHiddenPlayer(target.getUniqueId());

            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                if (!changed) {
                    sender.sendMessage(config.getMessage(sender,
                            hide ? "baltop_already_hidden" : "baltop_not_hidden",
                            "player", playerName));
                    return;
                }

                plugin.saveConfig();
                accountManager.refreshBaltopCache();
                sender.sendMessage(config.getMessage(sender,
                        hide ? "baltop_hide_success" : "baltop_unhide_success",
                        "player", playerName));
            });
        });

        return Command.SINGLE_SUCCESS;
    }
}
