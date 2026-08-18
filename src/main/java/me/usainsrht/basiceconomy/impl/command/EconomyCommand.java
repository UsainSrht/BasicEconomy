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
import me.usainsrht.basiceconomy.impl.BasicEconomyPlugin;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import me.usainsrht.basiceconomy.impl.util.PlayerFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

public class EconomyCommand {

    private final BasicEconomyPlugin plugin;
    private final AccountManagerImpl accountManager;
    private final ConfigManager config;
    private final PlayerFormatter playerFormatter;
    private final PayCommand payCommand;

    public EconomyCommand(BasicEconomyPlugin plugin, AccountManagerImpl accountManager, ConfigManager config, PlayerFormatter playerFormatter) {
        this(plugin, accountManager, config, playerFormatter, new PayCommand(plugin, accountManager, config, playerFormatter));
    }

    public EconomyCommand(BasicEconomyPlugin plugin, AccountManagerImpl accountManager, ConfigManager config, PlayerFormatter playerFormatter, PayCommand payCommand) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.config = config;
        this.playerFormatter = playerFormatter;
        this.payCommand = payCommand;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        String basePermission = config.getCommandPermission("money");

        LiteralArgumentBuilder<CommandSourceStack> cmd = CommandHelper.literal(name)
                .requires(src -> src.getSender().hasPermission(basePermission));

        boolean singleCurrency = config.getCurrencies().size() <= 1;

        // Base command (/money)
        cmd.executes(this::executeSelf);

        // /money [currency]
        if (!singleCurrency) {
            for (String cur : config.getCurrencies().keySet()) {
                cmd.then(CommandHelper.literal(cur)
                        .executes(ctx -> executeSelf(ctx, accountManager.getCurrency(cur))));
            }
        }

        // Subcommands
        registerSendSubcommand(cmd);
        registerReloadSubcommand(cmd);
        registerHelpSubcommand(cmd);
        registerInfoSubcommand(cmd);
        registerAdminSubcommands(cmd);
        registerSeeSubcommand(cmd);
        registerOthersArgument(cmd);

        return cmd;
    }

    private void registerSendSubcommand(LiteralArgumentBuilder<CommandSourceStack> root) {
        String sendPermission = config.getSubcommandPermission("money", "send", config.getCommandPermission("pay"));
        for (String sName : config.getSubcommandNamesWithAliases("money", "send")) {
            root.then(payCommand.buildSubcommand(sName, sendPermission));
        }
    }

    private void registerReloadSubcommand(LiteralArgumentBuilder<CommandSourceStack> root) {
        String reloadPermission = config.getSubcommandPermission("money", "reload", config.getCommandPermission("money") + ".reload");
        for (String rName : config.getSubcommandNamesWithAliases("money", "reload")) {
            root.then(CommandHelper.literal(rName)
                    .requires(src -> src.getSender().hasPermission(reloadPermission))
                    .executes(this::executeReload));
        }
    }

    private void registerHelpSubcommand(LiteralArgumentBuilder<CommandSourceStack> root) {
        String helpPermission = config.getSubcommandPermission("money", "help", config.getCommandPermission("money") + ".help");
        for (String hName : config.getSubcommandNamesWithAliases("money", "help")) {
            root.then(CommandHelper.literal(hName)
                    .requires(src -> src.getSender().hasPermission(helpPermission))
                    .executes(this::executeHelp));
        }
    }

    private void registerInfoSubcommand(LiteralArgumentBuilder<CommandSourceStack> root) {
        String infoPermission = config.getSubcommandPermission("money", "info", config.getCommandPermission("money") + ".info");
        for (String iName : config.getSubcommandNamesWithAliases("money", "info")) {
            root.then(CommandHelper.literal(iName)
                    .requires(src -> src.getSender().hasPermission(infoPermission))
                    .executes(this::executeInfo));
        }
    }

    private void registerAdminSubcommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        boolean singleCurrency = config.getCurrencies().size() <= 1;
        String[] actions = {"set", "add", "remove"};

        for (String action : actions) {
            String permission = config.getSubcommandPermission("money", action, config.getCommandPermission("money") + ".admin");

            for (String aName : config.getSubcommandNamesWithAliases("money", action)) {
                LiteralArgumentBuilder<CommandSourceStack> actionNode = CommandHelper.literal(aName)
                        .requires(src -> src.getSender().hasPermission(permission));

                RequiredArgumentBuilder<CommandSourceStack, String> targetNode = Commands.argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> CommandHelper.suggestPlayers(ctx.getSource().getSender(), accountManager, builder, true));

                RequiredArgumentBuilder<CommandSourceStack, Double> amountNode = Commands.argument("amount", DoubleArgumentType.doubleArg(0));
                amountNode.executes(ctx -> executeAdmin(ctx, action, null));

                if (!singleCurrency) {
                    amountNode.then(Commands.argument("currency", StringArgumentType.word())
                            .suggests((ctx, builder) -> CommandHelper.suggestCurrencies(config, builder))
                            .executes(ctx -> executeAdmin(ctx, action, StringArgumentType.getString(ctx, "currency"))));
                }

                targetNode.then(amountNode);
                actionNode.then(targetNode);
                root.then(actionNode);
            }
        }
    }

    private void registerSeeSubcommand(LiteralArgumentBuilder<CommandSourceStack> root) {
        String seePermission = config.getSubcommandPermission("money", "see", config.getOthersOfflinePermission());
        boolean singleCurrency = config.getCurrencies().size() <= 1;

        for (String sName : config.getSubcommandNamesWithAliases("money", "see")) {
            LiteralArgumentBuilder<CommandSourceStack> seeNode = CommandHelper.literal(sName)
                    .requires(src -> src.getSender().hasPermission(seePermission));

            RequiredArgumentBuilder<CommandSourceStack, String> targetNode = Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> CommandHelper.suggestPlayers(ctx.getSource().getSender(), accountManager, builder, true))
                    .executes(this::executeOther);

            if (!singleCurrency) {
                targetNode.then(Commands.argument("currency", StringArgumentType.word())
                        .suggests((ctx, builder) -> CommandHelper.suggestCurrencies(config, builder))
                        .executes(this::executeOtherCurrency));
            }

            seeNode.then(targetNode);
            root.then(seeNode);
        }
    }

    private void registerOthersArgument(LiteralArgumentBuilder<CommandSourceStack> root) {
        String othersPermission = config.getSubcommandPermission("money", "others", config.getCommandPermission("money") + ".others");
        boolean singleCurrency = config.getCurrencies().size() <= 1;

        RequiredArgumentBuilder<CommandSourceStack, String> otherTarget = Commands.argument("player", StringArgumentType.word())
                .requires(src -> src.getSender().hasPermission(othersPermission))
                .suggests((ctx, builder) -> CommandHelper.suggestPlayers(ctx.getSource().getSender(), accountManager, builder, false));

        otherTarget.executes(this::executeOther);

        if (!singleCurrency) {
            otherTarget.then(Commands.argument("currency", StringArgumentType.word())
                    .suggests((ctx, builder) -> CommandHelper.suggestCurrencies(config, builder))
                    .executes(this::executeOtherCurrency));
        }

        root.then(otherTarget);
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx) {
        return executeSelf(ctx, accountManager.getDefaultCurrency());
    }

    private int executeSelf(CommandContext<CommandSourceStack> ctx, Currency currency) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getMessage(sender, "player_only"));
            return 0;
        }
        accountManager.getAccount(player.getUniqueId()).thenAccept(account -> {
            BigDecimal bal = account.getBalance(currency);
            Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                    player.sendMessage(config.getMessage(player, "balance_self", "amount", currency.format(bal)))
            );
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
            ctx.getSource().getSender().sendMessage(config.getMessage(ctx.getSource().getSender(), "currency_not_found"));
            return 0;
        }
        return executeOther(ctx, currency);
    }

    private int executeOther(CommandContext<CommandSourceStack> ctx, Currency currency) {
        CommandSender sender = ctx.getSource().getSender();
        String othersOfflinePermission = config.getOthersOfflinePermission();
        boolean hasOthersOffline = sender.hasPermission(othersOfflinePermission);

        String targetName = StringArgumentType.getString(ctx, "player");

        CommandHelper.resolvePlayerAsync(sender, targetName, hasOthersOffline).thenAccept(target -> {
            if (target == null) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                        sender.sendMessage(config.getMessage(sender, "player_not_found"))
                );
                return;
            }

            playerFormatter.formatPlayerAsync(target).thenAccept(targetDisplay ->
                    accountManager.getAccount(target.getUniqueId()).thenAccept(account -> {
                        BigDecimal bal = account.getBalance(currency);
                        Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                                sender.sendMessage(config.getMessage(sender, "balance_other",
                                        "player", targetDisplay,
                                        "amount", currency.format(bal)))
                        );
                    }));
        });

        return Command.SINGLE_SUCCESS;
    }

    private int executeAdmin(CommandContext<CommandSourceStack> ctx, String action, String currName) {
        CommandSender sender = ctx.getSource().getSender();
        Currency currency = currName != null ? accountManager.getCurrency(currName) : accountManager.getDefaultCurrency();
        if (currency == null) {
            sender.sendMessage(config.getMessage(sender, "currency_not_found"));
            return 0;
        }

        String targetName = StringArgumentType.getString(ctx, "target");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        BigDecimal bdAmount = BigDecimal.valueOf(amount);

        CommandHelper.resolvePlayerAsync(sender, targetName, true).thenAccept(target -> {
            if (target == null) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                        sender.sendMessage(config.getMessage(sender, "player_not_found"))
                );
                return;
            }

            playerFormatter.formatPlayerAsync(target).thenAccept(targetDisplay ->
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
                            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                                if (success) {
                                    sender.sendMessage(config.getMessage(sender, msgKey,
                                            "player", targetDisplay,
                                            "amount", currency.format(bdAmount)));
                                } else {
                                    sender.sendMessage(config.getMessage(sender, "invalid_amount"));
                                }
                            });
                        });
                    }));
        });

        return Command.SINGLE_SUCCESS;
    }

    private int executeReload(CommandContext<CommandSourceStack> ctx) {
        plugin.reload();
        ctx.getSource().getSender().sendMessage(config.getMessage(ctx.getSource().getSender(), "reloaded"));
        return Command.SINGLE_SUCCESS;
    }

    private int executeHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(config.getMessage(ctx.getSource().getSender(), "money_help"));
        return Command.SINGLE_SUCCESS;
    }

    private int executeInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        String vaultHook = (vault != null && vault.isEnabled()) ? vault.getDescription().getVersion() : "Disabled";

        Plugin vaultUnlocked = Bukkit.getPluginManager().getPlugin("VaultUnlocked");
        String vault2Hook = "Disabled";
        if (vaultUnlocked != null && vaultUnlocked.isEnabled()) {
            vault2Hook = vaultUnlocked.getDescription().getVersion();
        } else {
            try {
                Class.forName("net.milkbowl.vault2.economy.Economy");
                vault2Hook = "Enabled";
            } catch (Throwable ignored) {}
        }

        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        String papiHook = (papi != null && papi.isEnabled()) ? papi.getDescription().getVersion() : "Disabled";

        Plugin miniPlaceholders = Bukkit.getPluginManager().getPlugin("MiniPlaceholders");
        String miniPlaceholdersHook = (miniPlaceholders != null && miniPlaceholders.isEnabled()) ? miniPlaceholders.getDescription().getVersion() : "Disabled";

        Plugin scchat = Bukkit.getPluginManager().getPlugin("SCChat");
        String scchatHook = (scchat != null && scchat.isEnabled()) ? scchat.getDescription().getVersion() : "Disabled";

        sender.sendMessage(config.getMessage(sender, "money_info",
                "server_version", Bukkit.getBukkitVersion(),
                "platform", Bukkit.getName(),
                "db_type", config.getStorageType(),
                "vault_hook", vaultHook,
                "vault2_hook", vault2Hook,
                "papi_hook", papiHook,
                "miniplaceholders_hook", miniPlaceholdersHook,
                "scchat_hook", scchatHook
        ));
        return Command.SINGLE_SUCCESS;
    }
}
