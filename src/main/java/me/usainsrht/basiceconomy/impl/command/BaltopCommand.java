package me.usainsrht.basiceconomy.impl.command;

import com.mojang.brigadier.Command;
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

        boolean singleCurrency = config.getCurrencies().size() <= 1;

        cmd.executes(ctx -> execute(ctx, null));

        if (!singleCurrency) {
            cmd.then(Commands.argument("currency", StringArgumentType.word())
                    .suggests(this::suggestCurrencies)
                    .executes(ctx -> execute(ctx, StringArgumentType.getString(ctx, "currency"))));
        }

        registerPlayerSubcommand(cmd, "hide", this::executeHide);
        registerPlayerSubcommand(cmd, "unhide", this::executeUnhide);

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
        return CompletableFuture.supplyAsync(() -> {
            String input = builder.getRemaining().toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (PlayerVisibility.canSeePlayer(sender, player)) {
                    String name = player.getName();
                    if (name.toLowerCase().startsWith(input)) {
                        builder.suggest(name);
                    }
                }
            }
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() != null && op.getName().toLowerCase().startsWith(input)) {
                    builder.suggest(op.getName());
                }
            }
            return builder.build();
        });
    }

    private int execute(CommandContext<CommandSourceStack> ctx, String currName) {
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

        sender.sendMessage(config.getMessage(sender, "baltop_header", "currency", currency.name()));
        
        List<AccountManagerImpl.BaltopEntry> cachedTop = accountManager.getCachedBaltop(currency);
        if (cachedTop != null) {
            int pos = 1;
            for (AccountManagerImpl.BaltopEntry entry : cachedTop) {
                sender.sendMessage(config.getMessage(sender, "baltop_entry",
                        "position", String.valueOf(pos),
                        "player", entry.getPlayerDisplay(),
                        "amount", currency.format(entry.getBalance())));
                pos++;
            }
        } else {
            accountManager.getTopAccounts(currency, 10).thenAccept(top -> {
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    int pos = 1;
                    for (var entry : top) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                        net.kyori.adventure.text.Component display = playerFormatter.formatPlayer(op);
                        sender.sendMessage(config.getMessage(sender, "baltop_entry", 
                                "position", String.valueOf(pos),
                                "player", display,
                                "amount", currency.format(entry.getValue())));
                        pos++;
                    }
                });
            });
        }

        return Command.SINGLE_SUCCESS;
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
