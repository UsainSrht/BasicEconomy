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
import me.usainsrht.basiceconomy.api.Account;
import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.impl.BasicEconomyPlugin;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class EconomyCommand {

    private final BasicEconomyPlugin plugin;
    private final AccountManagerImpl accountManager;
    private final ConfigManager config;

    public EconomyCommand(BasicEconomyPlugin plugin, AccountManagerImpl accountManager, ConfigManager config) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.config = config;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(config.getCommandPermission("money")));

        boolean singleCurrency = config.getCurrencies().size() <= 1;

        // Base command (/money)
        cmd.executes(this::executeSelf);

        // /money [currency]
        if (!singleCurrency) {
            cmd.then(Commands.argument("currency", StringArgumentType.word())
                    .suggests(this::suggestCurrencies)
                    .executes(this::executeSelfCurrency));
        }

        // /money reload
        cmd.then(Commands.literal("reload")
                .requires(src -> src.getSender().hasPermission(config.getCommandPermission("money") + ".reload"))
                .executes(this::executeReload));

        // Subcommands (set, add, remove)
        String[] actions = {"set", "add", "remove"};
        for (String action : actions) {
            LiteralArgumentBuilder<CommandSourceStack> actionNode = Commands.literal(action)
                    .requires(src -> src.getSender().hasPermission(config.getCommandPermission("money") + ".admin"));

            RequiredArgumentBuilder<CommandSourceStack, PlayerSelectorArgumentResolver> targetNode = Commands.argument("target", ArgumentTypes.player());
            RequiredArgumentBuilder<CommandSourceStack, Double> amountNode = Commands.argument("amount", DoubleArgumentType.doubleArg(0));

            amountNode.executes(ctx -> executeAdmin(ctx, action, null));

            if (!singleCurrency) {
                amountNode.then(Commands.argument("currency", StringArgumentType.word())
                        .suggests(this::suggestCurrencies)
                        .executes(ctx -> executeAdmin(ctx, action, StringArgumentType.getString(ctx, "currency"))));
            }

            targetNode.then(amountNode);
            actionNode.then(targetNode);
            cmd.then(actionNode);
        }

        // /money <player>
        RequiredArgumentBuilder<CommandSourceStack, PlayerSelectorArgumentResolver> otherTarget = Commands.argument("player", ArgumentTypes.player());
        otherTarget.executes(this::executeOther);

        if (!singleCurrency) {
            otherTarget.then(Commands.argument("currency", StringArgumentType.word())
                    .suggests(this::suggestCurrencies)
                    .executes(this::executeOtherCurrency));
        }
        cmd.then(otherTarget);

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

    private int executeSelf(CommandContext<CommandSourceStack> ctx) {
        return executeSelf(ctx, accountManager.getDefaultCurrency());
    }

    private int executeSelfCurrency(CommandContext<CommandSourceStack> ctx) {
        String currName = StringArgumentType.getString(ctx, "currency");
        Currency currency = accountManager.getCurrency(currName);
        if (currency == null) {
            ctx.getSource().getSender().sendMessage(config.getMessage("currency_not_found"));
            return 0;
        }
        return executeSelf(ctx, currency);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx, Currency currency) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(config.getMessage("player_only"));
            return 0;
        }
        accountManager.getAccount(player.getUniqueId()).thenAccept(account -> {
            BigDecimal bal = account.getBalance(currency);
            player.sendMessage(config.getMessage("balance_self", "amount", currency.format(bal)));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx) {
        return executeOther(ctx, accountManager.getDefaultCurrency());
    }

    private int executeOtherCurrency(CommandContext<CommandSourceStack> ctx) {
        String currName = StringArgumentType.getString(ctx, "currency");
        Currency currency = accountManager.getCurrency(currName);
        if (currency == null) {
            ctx.getSource().getSender().sendMessage(config.getMessage("currency_not_found"));
            return 0;
        }
        return executeOther(ctx, currency);
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx, Currency currency) {
        try {
            Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).get(0);
            accountManager.getAccount(target.getUniqueId()).thenAccept(account -> {
                BigDecimal bal = account.getBalance(currency);
                ctx.getSource().getSender().sendMessage(config.getMessage("balance_other", 
                        "player", target.getName(),
                        "amount", currency.format(bal)));
            });
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            ctx.getSource().getSender().sendMessage(config.getMessage("player_not_found"));
            return 0;
        }
    }

    private int executeAdmin(CommandContext<CommandSourceStack> ctx, String action, String currName) {
        Currency currency = currName != null ? accountManager.getCurrency(currName) : accountManager.getDefaultCurrency();
        if (currency == null) {
            ctx.getSource().getSender().sendMessage(config.getMessage("currency_not_found"));
            return 0;
        }
        
        try {
            Player target = ctx.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).get(0);
            double amount = DoubleArgumentType.getDouble(ctx, "amount");
            BigDecimal bdAmount = BigDecimal.valueOf(amount);
            
            accountManager.getAccount(target.getUniqueId()).thenAccept(account -> {
                CompletableFuture<Boolean> future;
                String msgKey;
                if (action.equals("set")) {
                    future = account.setBalance(currency, bdAmount);
                    msgKey = "set_success";
                } else if (action.equals("add")) {
                    future = account.addBalance(currency, bdAmount);
                    msgKey = "add_success";
                } else {
                    future = account.removeBalance(currency, bdAmount);
                    msgKey = "remove_success";
                }
                
                future.thenAccept(success -> {
                    if (success) {
                        ctx.getSource().getSender().sendMessage(config.getMessage(msgKey, 
                                "player", target.getName(),
                                "amount", currency.format(bdAmount)));
                    } else {
                        ctx.getSource().getSender().sendMessage(config.getMessage("invalid_amount"));
                    }
                });
            });
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            ctx.getSource().getSender().sendMessage(config.getMessage("player_not_found"));
            return 0;
        }
    }

    private int executeReload(CommandContext<CommandSourceStack> ctx) {
        plugin.reload();
        ctx.getSource().getSender().sendMessage(config.getMessage("reloaded"));
        return Command.SINGLE_SUCCESS;
    }
}
