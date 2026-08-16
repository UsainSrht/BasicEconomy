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
import me.usainsrht.basiceconomy.api.Account;
import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.impl.BasicEconomyPlugin;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import me.usainsrht.basiceconomy.impl.util.PlayerVisibility;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class EconomyCommand {

    private final BasicEconomyPlugin plugin;
    private final AccountManagerImpl accountManager;
    private final ConfigManager config;

    private final me.usainsrht.basiceconomy.impl.util.PlayerFormatter playerFormatter;

    public EconomyCommand(BasicEconomyPlugin plugin, AccountManagerImpl accountManager, ConfigManager config, me.usainsrht.basiceconomy.impl.util.PlayerFormatter playerFormatter) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.config = config;
        this.playerFormatter = playerFormatter;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(config.getCommandPermission("money")));

        boolean singleCurrency = config.getCurrencies().size() <= 1;

        // Base command (/money)
        cmd.executes(this::executeSelf);

        // /money [currency]
        if (!singleCurrency) {
            for (String cur : config.getCurrencies().keySet()) {
                cmd.then(Commands.literal(cur)
                        .executes(ctx -> executeSelf(ctx, accountManager.getCurrency(cur))));
            }
        }

        // /money reload
        String reloadName = config.getSubcommandName("money", "reload");
        String reloadPermission = config.getSubcommandPermission("money", "reload", config.getCommandPermission("money") + ".reload");
        List<String> reloadAliases = config.getSubcommandAliases("money", "reload");

        List<String> reloadNames = new ArrayList<>();
        reloadNames.add(reloadName);
        reloadNames.addAll(reloadAliases);

        for (String rName : reloadNames) {
            cmd.then(Commands.literal(rName)
                    .requires(src -> src.getSender().hasPermission(reloadPermission))
                    .executes(this::executeReload));
        }

        // /money help
        String helpName = config.getSubcommandName("money", "help");
        String helpPermission = config.getSubcommandPermission("money", "help", config.getCommandPermission("money") + ".help");
        List<String> helpAliases = config.getSubcommandAliases("money", "help");

        List<String> helpNames = new ArrayList<>();
        helpNames.add(helpName);
        helpNames.addAll(helpAliases);

        for (String hName : helpNames) {
            cmd.then(Commands.literal(hName)
                    .requires(src -> src.getSender().hasPermission(helpPermission))
                    .executes(this::executeHelp));
        }

        // /money info
        String infoName = config.getSubcommandName("money", "info");
        String infoPermission = config.getSubcommandPermission("money", "info", config.getCommandPermission("money") + ".info");
        List<String> infoAliases = config.getSubcommandAliases("money", "info");

        List<String> infoNames = new ArrayList<>();
        infoNames.add(infoName);
        infoNames.addAll(infoAliases);

        for (String iName : infoNames) {
            cmd.then(Commands.literal(iName)
                    .requires(src -> src.getSender().hasPermission(infoPermission))
                    .executes(this::executeInfo));
        }

        // Subcommands (set, add, remove)
        String[] actions = {"set", "add", "remove"};
        for (String action : actions) {
            String actionName = config.getSubcommandName("money", action);
            String permission = config.getSubcommandPermission("money", action, config.getCommandPermission("money") + ".admin");
            List<String> aliases = config.getSubcommandAliases("money", action);

            List<String> actionNames = new ArrayList<>();
            actionNames.add(actionName);
            actionNames.addAll(aliases);

            for (String aName : actionNames) {
                LiteralArgumentBuilder<CommandSourceStack> actionNode = Commands.literal(aName)
                        .requires(src -> src.getSender().hasPermission(permission));

                RequiredArgumentBuilder<CommandSourceStack, String> targetNode = Commands.argument("target", StringArgumentType.word())
                        .suggests(this::suggestPlayers);
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
        }

        // /money <player>
        RequiredArgumentBuilder<CommandSourceStack, String> otherTarget = Commands.argument("player", StringArgumentType.word())
                .suggests(this::suggestPlayers);
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

    private CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CommandSender sender = context.getSource().getSender();
        String othersPermission = config.getSubcommandPermission("money", "others", config.getCommandPermission("money") + ".others");
        if (!sender.hasPermission(othersPermission)) {
            return builder.buildFuture();
        }

        return CompletableFuture.supplyAsync(() -> {
            String input = builder.getRemaining().toLowerCase();
            String adminPermission = config.getSubcommandPermission("money", "admin", config.getCommandPermission("money") + ".admin");
            boolean isAdmin = sender.hasPermission(adminPermission);

            Set<String> names = new HashSet<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (isAdmin || PlayerVisibility.canSeePlayer(sender, p)) {
                    names.add(p.getName());
                }
            }
            if (isAdmin) {
                for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                    if (op.getName() != null) {
                        names.add(op.getName());
                    }
                }
            }

            for (String name : names) {
                if (name.toLowerCase().startsWith(input)) {
                    builder.suggest(name);
                }
            }
            return builder.build();
        });
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
        String othersPermission = config.getSubcommandPermission("money", "others", config.getCommandPermission("money") + ".others");
        if (!sender.hasPermission(othersPermission)) {
            return executeSelf(ctx, currency);
        }

        CompletableFuture.runAsync(() -> {
            try {
                String targetName = StringArgumentType.getString(ctx, "player");
                String adminPermission = config.getSubcommandPermission("money", "admin", config.getCommandPermission("money") + ".admin");
                boolean isAdmin = sender.hasPermission(adminPermission);

                OfflinePlayer target = null;
                if (!isAdmin) {
                    Player onlineTarget = Bukkit.getPlayerExact(targetName);
                    if (onlineTarget == null) {
                        onlineTarget = Bukkit.getPlayer(targetName);
                    }
                    if (onlineTarget == null || !PlayerVisibility.canSeePlayer(sender, onlineTarget)) {
                        Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                                sender.sendMessage(config.getMessage(sender, "player_not_found"))
                        );
                        return;
                    }
                    target = onlineTarget;
                } else {
                    Player onlineTarget = Bukkit.getPlayerExact(targetName);
                    if (onlineTarget == null) {
                        onlineTarget = Bukkit.getPlayer(targetName);
                    }
                    if (onlineTarget != null) {
                        target = onlineTarget;
                    } else {
                        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
                        if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
                            Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                                    sender.sendMessage(config.getMessage(sender, "player_not_found"))
                            );
                            return;
                        }
                        target = offlineTarget;
                    }
                }

                final OfflinePlayer finalTarget = target;
                net.kyori.adventure.text.Component targetDisplay = playerFormatter.formatPlayer(finalTarget);
                accountManager.getAccount(finalTarget.getUniqueId()).thenAccept(account -> {
                    BigDecimal bal = account.getBalance(currency);
                    Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                            sender.sendMessage(config.getMessage(sender, "balance_other",
                                    "player", targetDisplay,
                                    "amount", currency.format(bal)))
                    );
                });
            } catch (Exception e) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                        sender.sendMessage(config.getMessage(sender, "player_not_found"))
                );
            }
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
        
        CompletableFuture.runAsync(() -> {
            try {
                String targetName = StringArgumentType.getString(ctx, "target");
                OfflinePlayer target = Bukkit.getPlayer(targetName);
                if (target == null) {
                    target = Bukkit.getOfflinePlayer(targetName);
                    if (!target.hasPlayedBefore()) {
                        Bukkit.getGlobalRegionScheduler().run(plugin, task -> 
                            sender.sendMessage(config.getMessage(sender, "player_not_found"))
                        );
                        return;
                    }
                }
                final OfflinePlayer finalTarget = target;
                net.kyori.adventure.text.Component targetDisplay = playerFormatter.formatPlayer(finalTarget);
                double amount = DoubleArgumentType.getDouble(ctx, "amount");
                BigDecimal bdAmount = BigDecimal.valueOf(amount);
                
                accountManager.getAccount(finalTarget.getUniqueId()).thenAccept(account -> {
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
                });
            } catch (Exception e) {
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> 
                    sender.sendMessage(config.getMessage(sender, "player_not_found"))
                );
            }
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
        org.bukkit.plugin.Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        String vaultHook = (vault != null && vault.isEnabled()) ? vault.getDescription().getVersion() : "Disabled";

        org.bukkit.plugin.Plugin vaultUnlocked = Bukkit.getPluginManager().getPlugin("VaultUnlocked");
        String vault2Hook = "Disabled";
        if (vaultUnlocked != null && vaultUnlocked.isEnabled()) {
            vault2Hook = vaultUnlocked.getDescription().getVersion();
        } else {
            try {
                Class.forName("net.milkbowl.vault2.economy.Economy");
                vault2Hook = "Enabled";
            } catch (Throwable ignored) {}
        }

        org.bukkit.plugin.Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        String papiHook = (papi != null && papi.isEnabled()) ? papi.getDescription().getVersion() : "Disabled";

        org.bukkit.plugin.Plugin miniPlaceholders = Bukkit.getPluginManager().getPlugin("MiniPlaceholders");
        String miniPlaceholdersHook = (miniPlaceholders != null && miniPlaceholders.isEnabled()) ? miniPlaceholders.getDescription().getVersion() : "Disabled";

        org.bukkit.plugin.Plugin scchat = Bukkit.getPluginManager().getPlugin("SCChat");
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
