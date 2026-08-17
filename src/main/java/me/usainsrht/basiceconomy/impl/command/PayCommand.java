package me.usainsrht.basiceconomy.impl.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import me.usainsrht.basiceconomy.impl.util.PlayerFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;

public class PayCommand {

    private final JavaPlugin plugin;
    private final AccountManagerImpl accountManager;
    private final ConfigManager config;
    private final PlayerFormatter playerFormatter;

    public PayCommand(JavaPlugin plugin, AccountManagerImpl accountManager, ConfigManager config, PlayerFormatter playerFormatter) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.config = config;
        this.playerFormatter = playerFormatter;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return buildSubcommand(name, config.getCommandPermission("pay"));
    }

    public LiteralArgumentBuilder<CommandSourceStack> buildSubcommand(String name, String permission) {
        LiteralArgumentBuilder<CommandSourceStack> cmd = CommandHelper.literal(name)
                .requires(src -> src.getSender().hasPermission(permission));

        boolean singleCurrency = config.getCurrencies().size() <= 1;

        RequiredArgumentBuilder<CommandSourceStack, String> targetNode = Commands.argument("target", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    CommandSender sender = ctx.getSource().getSender();
                    boolean hasOfflinePay = sender.hasPermission(config.getPayOfflinePermission())
                            || sender.hasPermission(config.getOthersOfflinePermission());
                    return CommandHelper.suggestPlayers(sender, accountManager, builder, hasOfflinePay);
                });

        RequiredArgumentBuilder<CommandSourceStack, Double> amountNode = Commands.argument("amount", DoubleArgumentType.doubleArg(0.01));
        amountNode.executes(ctx -> execute(ctx, null));

        if (!singleCurrency) {
            amountNode.then(Commands.argument("currency", StringArgumentType.word())
                    .suggests((ctx, builder) -> CommandHelper.suggestCurrencies(config, builder))
                    .executes(ctx -> execute(ctx, StringArgumentType.getString(ctx, "currency"))));
        }

        targetNode.then(amountNode);
        cmd.then(targetNode);

        return cmd;
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

        CommandHelper.resolvePlayerAsync(sender, targetName, hasOfflinePay).thenAccept(target -> {
            if (target == null) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                        sender.sendMessage(config.getMessage(sender, "player_not_found")));
                return;
            }

            if (sender.getUniqueId().equals(target.getUniqueId())) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                        sender.sendMessage(config.getMessage(sender, "cannot_pay_self")));
                return;
            }

            accountManager.getAccount(sender.getUniqueId()).thenAccept(senderAcc -> {
                if (senderAcc.getBalance(currency).compareTo(bdAmount) < 0) {
                    Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                            sender.sendMessage(config.getMessage(sender, "not_enough_money")));
                    return;
                }

                senderAcc.removeBalance(currency, bdAmount).thenAccept(removed -> {
                    if (!removed) {
                        Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                                sender.sendMessage(config.getMessage(sender, "invalid_amount")));
                        return;
                    }

                    accountManager.getAccount(target.getUniqueId()).thenAccept(targetAcc -> {
                        targetAcc.addBalance(currency, bdAmount).thenAccept(added -> {
                            if (added) {
                                Component targetDisplay = playerFormatter.formatPlayer(target);
                                Component senderDisplay = playerFormatter.formatPlayer(sender);
                                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                                    sender.sendMessage(config.getMessage(sender, "pay_success",
                                            "player", targetDisplay,
                                            "amount", currency.format(bdAmount)));
                                    if (target.isOnline() && target.getPlayer() != null) {
                                        target.getPlayer().sendMessage(config.getMessage(target.getPlayer(), "pay_received",
                                                "player", senderDisplay,
                                                "amount", currency.format(bdAmount)));
                                    }
                                });
                            } else {
                                senderAcc.addBalance(currency, bdAmount);
                                Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                                        sender.sendMessage(config.getMessage(sender, "invalid_amount")));
                            }
                        });
                    });
                });
            });
        });

        return Command.SINGLE_SUCCESS;
    }
}
