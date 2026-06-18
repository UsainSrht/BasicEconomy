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
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.impl.BasicEconomyPlugin;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

public class PayCommand {

    private final AccountManagerImpl accountManager;
    private final ConfigManager config;

    public PayCommand(AccountManagerImpl accountManager, ConfigManager config) {
        this.accountManager = accountManager;
        this.config = config;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(config.getCommandPermission("pay")) && src.getSender() instanceof Player);

        boolean singleCurrency = config.getCurrencies().size() <= 1;

        RequiredArgumentBuilder<CommandSourceStack, PlayerSelectorArgumentResolver> targetNode = Commands.argument("target", ArgumentTypes.player());
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

        try {
            Player target = ctx.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).get(0);
            if (sender.getUniqueId().equals(target.getUniqueId())) {
                sender.sendMessage(config.getMessage("cannot_pay_self"));
                return 0;
            }

            double amount = DoubleArgumentType.getDouble(ctx, "amount");
            BigDecimal bdAmount = BigDecimal.valueOf(amount);

            accountManager.getAccount(sender.getUniqueId()).thenAccept(senderAcc -> {
                if (senderAcc.getBalance(currency).compareTo(bdAmount) < 0) {
                    sender.sendMessage(config.getMessage("not_enough_money"));
                    return;
                }

                senderAcc.removeBalance(currency, bdAmount).thenAccept(removed -> {
                    if (removed) {
                        accountManager.getAccount(target.getUniqueId()).thenAccept(targetAcc -> {
                            targetAcc.addBalance(currency, bdAmount).thenAccept(added -> {
                                if (added) {
                                    sender.sendMessage(config.getMessage("pay_success", 
                                            "player", target.getName(),
                                            "amount", currency.format(bdAmount)));
                                    target.sendMessage(config.getMessage("pay_received", 
                                            "player", sender.getName(),
                                            "amount", currency.format(bdAmount)));
                                } else {
                                    // Refund on failure
                                    senderAcc.addBalance(currency, bdAmount);
                                }
                            });
                        });
                    } else {
                        sender.sendMessage(config.getMessage("invalid_amount"));
                    }
                });
            });

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            sender.sendMessage(config.getMessage("player_not_found"));
            return 0;
        }
    }
}
