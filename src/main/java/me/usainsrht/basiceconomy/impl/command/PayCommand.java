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

    public PayCommand(JavaPlugin plugin, AccountManagerImpl accountManager, ConfigManager config) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.config = config;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(config.getCommandPermission("pay")) && src.getSender() instanceof Player);

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
        return CompletableFuture.supplyAsync(() -> {
            String input = builder.getRemaining().toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (PlayerVisibility.canSeePlayer(sender, p)) {
                    String name = p.getName();
                    if (name.toLowerCase().startsWith(input)) {
                        builder.suggest(name);
                    }
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
        Player sender = (Player) ctx.getSource().getSender();
        Currency currency = currName != null ? accountManager.getCurrency(currName) : accountManager.getDefaultCurrency();

        if (currency == null) {
            sender.sendMessage(config.getMessage("currency_not_found"));
            return 0;
        }

        if (!currency.payEnabled()) {
            sender.sendMessage(config.getMessage("pay_disabled"));
            return 0;
        }

        String targetName = StringArgumentType.getString(ctx, "target");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        BigDecimal bdAmount = BigDecimal.valueOf(amount);

        CompletableFuture.runAsync(() -> {
            try {
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    target = Bukkit.getPlayer(targetName);
                }

                if (target == null || !PlayerVisibility.canSeePlayer(sender, target)) {
                    Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                            sender.sendMessage(config.getMessage("player_not_found")));
                    return;
                }

                if (sender.getUniqueId().equals(target.getUniqueId())) {
                    Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                            sender.sendMessage(config.getMessage("cannot_pay_self")));
                    return;
                }

                final Player finalTarget = target;
                accountManager.getAccount(sender.getUniqueId()).thenAccept(senderAcc -> {
                    if (senderAcc.getBalance(currency).compareTo(bdAmount) < 0) {
                        Bukkit.getGlobalRegionScheduler().run(plugin, task -> sender.sendMessage(config.getMessage("not_enough_money")));
                        return;
                    }

                    senderAcc.removeBalance(currency, bdAmount).thenAccept(removed -> {
                        if (removed) {
                            accountManager.getAccount(finalTarget.getUniqueId()).thenAccept(targetAcc -> {
                                targetAcc.addBalance(currency, bdAmount).thenAccept(added -> {
                                    if (added) {
                                        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                                            sender.sendMessage(config.getMessage("pay_success",
                                                    "player", finalTarget.getName(),
                                                    "amount", currency.format(bdAmount)));
                                            finalTarget.sendMessage(config.getMessage("pay_received",
                                                    "player", sender.getName(),
                                                    "amount", currency.format(bdAmount)));
                                        });
                                    } else {
                                        // Refund on failure
                                        senderAcc.addBalance(currency, bdAmount);
                                    }
                                });
                            });
                        } else {
                            Bukkit.getGlobalRegionScheduler().run(plugin, task -> sender.sendMessage(config.getMessage("invalid_amount")));
                        }
                    });
                });
            } catch (Exception e) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> sender.sendMessage(config.getMessage("player_not_found")));
            }
        });

        return Command.SINGLE_SUCCESS;
    }
}
