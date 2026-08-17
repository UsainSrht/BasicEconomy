package me.usainsrht.basiceconomy.impl.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

public class PayCommand {

    private final JavaPlugin plugin;
    private final AccountManagerImpl accountManager;
    private final ConfigManager config;

    private final me.usainsrht.basiceconomy.impl.util.PlayerFormatter playerFormatter;

    public PayCommand(JavaPlugin plugin, AccountManagerImpl accountManager, ConfigManager config, me.usainsrht.basiceconomy.impl.util.PlayerFormatter playerFormatter) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.config = config;
        this.playerFormatter = playerFormatter;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return buildSubcommand(name, config.getCommandPermission("pay"));
    }

    public LiteralArgumentBuilder<CommandSourceStack> buildSubcommand(String name, String permission) {
        LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(permission));

        boolean singleCurrency = config.getCurrencies().size() <= 1;

        RequiredArgumentBuilder<CommandSourceStack, String> targetNode = Commands.argument("target", StringArgumentType.word())
                .suggests(this::suggestPayPlayers);
        RequiredArgumentBuilder<CommandSourceStack, Double> amountNode = Commands.argument("amount", DoubleArgumentType.doubleArg(0.01));

        amountNode.executes(ctx -> execute(ctx, null));

        if (!singleCurrency) {
            amountNode.then(Commands.argument("currency", StringArgumentType.word())
                    .suggests(this::suggestCurrencies)
                    .executes(ctx -> execute(ctx, StringArgumentType.getString(ctx, "currency"))));
        }

        targetNode.then(amountNode);
        cmd.then(targetNode);

        return cmd;
    }

    private CompletableFuture<Suggestions> suggestPayPlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CommandSender sender = context.getSource().getSender();
        boolean hasOfflinePay = sender.hasPermission(config.getPayOfflinePermission())
                || sender.hasPermission(config.getOthersOfflinePermission());

        String input = builder.getRemaining().toLowerCase();

        if (!hasOfflinePay) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (PlayerVisibility.canSeePlayer(sender, p)) {
                    String name = p.getName();
                    if (name.toLowerCase().startsWith(input)) {
                        builder.suggest(name);
                    }
                }
            }
            return builder.buildFuture();
        }

        return CompletableFuture.supplyAsync(() -> {
            java.util.Set<String> names = new java.util.HashSet<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (PlayerVisibility.canSeePlayer(sender, p)) {
                    names.add(p.getName());
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

    private CompletableFuture<Suggestions> suggestCurrencies(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase();
        for (String cur : config.getCurrencies().keySet()) {
            if (cur.startsWith(input)) {
                builder.suggest(cur);
            }
        }
        return builder.buildFuture();
    }

    private int execute(CommandContext<CommandSourceStack> ctx, String currName) {
        CommandSender rawSender = ctx.getSource().getSender();
        if (!(rawSender instanceof Player sender)) {
            rawSender.sendMessage(config.getMessage(rawSender, "player_only"));
            return 0;
        }

        Currency currency = currName != null ? accountManager.getCurrency(currName) : accountManager.getDefaultCurrency();

        if (currency == null) {
            sender.sendMessage(config.getMessage(sender, "currency_not_found"));
            return 0;
        }

        if (!currency.payEnabled()) {
            sender.sendMessage(config.getMessage(sender, "pay_disabled"));
            return 0;
        }

        String targetName = StringArgumentType.getString(ctx, "target");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        BigDecimal bdAmount = BigDecimal.valueOf(amount);

        boolean hasOfflinePay = sender.hasPermission(config.getPayOfflinePermission())
                || sender.hasPermission(config.getOthersOfflinePermission());

        if (!hasOfflinePay) {
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                target = Bukkit.getPlayer(targetName);
            }

            if (target == null || !PlayerVisibility.canSeePlayer(sender, target)) {
                sender.sendMessage(config.getMessage(sender, "player_not_found"));
                return Command.SINGLE_SUCCESS;
            }

            if (sender.getUniqueId().equals(target.getUniqueId())) {
                sender.sendMessage(config.getMessage(sender, "cannot_pay_self"));
                return Command.SINGLE_SUCCESS;
            }

            final Player finalTarget = target;
            accountManager.getAccount(sender.getUniqueId()).thenAccept(senderAcc -> {
                if (senderAcc.getBalance(currency).compareTo(bdAmount) < 0) {
                    Bukkit.getGlobalRegionScheduler().run(plugin, task -> sender.sendMessage(config.getMessage(sender, "not_enough_money")));
                    return;
                }

                senderAcc.removeBalance(currency, bdAmount).thenAccept(removed -> {
                    if (removed) {
                        accountManager.getAccount(finalTarget.getUniqueId()).thenAccept(targetAcc -> {
                            targetAcc.addBalance(currency, bdAmount).thenAccept(added -> {
                                if (added) {
                                    net.kyori.adventure.text.Component targetDisplay = playerFormatter.formatPlayer(finalTarget);
                                    net.kyori.adventure.text.Component senderDisplay = playerFormatter.formatPlayer(sender);
                                    Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                                        sender.sendMessage(config.getMessage(sender, "pay_success",
                                                "player", targetDisplay,
                                                "amount", currency.format(bdAmount)));
                                        finalTarget.sendMessage(config.getMessage(finalTarget, "pay_received",
                                                "player", senderDisplay,
                                                "amount", currency.format(bdAmount)));
                                    });
                                } else {
                                    senderAcc.addBalance(currency, bdAmount);
                                }
                            });
                        });
                    } else {
                        Bukkit.getGlobalRegionScheduler().run(plugin, task -> sender.sendMessage(config.getMessage(sender, "invalid_amount")));
                    }
                });
            });
            return Command.SINGLE_SUCCESS;
        }

        CompletableFuture.runAsync(() -> {
            try {
                Player onlineTarget = Bukkit.getPlayerExact(targetName);
                if (onlineTarget == null) {
                    onlineTarget = Bukkit.getPlayer(targetName);
                }

                org.bukkit.OfflinePlayer target = null;
                if (onlineTarget != null && PlayerVisibility.canSeePlayer(sender, onlineTarget)) {
                    target = onlineTarget;
                } else {
                    org.bukkit.OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
                    if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
                        Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                                sender.sendMessage(config.getMessage(sender, "player_not_found")));
                        return;
                    }
                    target = offlineTarget;
                }

                if (sender.getUniqueId().equals(target.getUniqueId())) {
                    Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                            sender.sendMessage(config.getMessage(sender, "cannot_pay_self")));
                    return;
                }

                final org.bukkit.OfflinePlayer finalTarget = target;
                accountManager.getAccount(sender.getUniqueId()).thenAccept(senderAcc -> {
                    if (senderAcc.getBalance(currency).compareTo(bdAmount) < 0) {
                        Bukkit.getGlobalRegionScheduler().run(plugin, task -> sender.sendMessage(config.getMessage(sender, "not_enough_money")));
                        return;
                    }

                    senderAcc.removeBalance(currency, bdAmount).thenAccept(removed -> {
                        if (removed) {
                            accountManager.getAccount(finalTarget.getUniqueId()).thenAccept(targetAcc -> {
                                targetAcc.addBalance(currency, bdAmount).thenAccept(added -> {
                                    if (added) {
                                        net.kyori.adventure.text.Component targetDisplay = playerFormatter.formatPlayer(finalTarget);
                                        net.kyori.adventure.text.Component senderDisplay = playerFormatter.formatPlayer(sender);
                                        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                                            sender.sendMessage(config.getMessage(sender, "pay_success",
                                                    "player", targetDisplay,
                                                    "amount", currency.format(bdAmount)));
                                            if (finalTarget.isOnline() && finalTarget.getPlayer() != null) {
                                                finalTarget.getPlayer().sendMessage(config.getMessage(finalTarget.getPlayer(), "pay_received",
                                                        "player", senderDisplay,
                                                        "amount", currency.format(bdAmount)));
                                            }
                                        });
                                    } else {
                                        senderAcc.addBalance(currency, bdAmount);
                                    }
                                });
                            });
                        } else {
                            Bukkit.getGlobalRegionScheduler().run(plugin, task -> sender.sendMessage(config.getMessage(sender, "invalid_amount")));
                        }
                    });
                });
            } catch (Exception e) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> sender.sendMessage(config.getMessage(sender, "player_not_found")));
            }
        });

        return Command.SINGLE_SUCCESS;
    }
}
