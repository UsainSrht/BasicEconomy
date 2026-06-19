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
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public class BaltopCommand {

    private final JavaPlugin plugin;
    private final AccountManagerImpl accountManager;
    private final ConfigManager config;

    public BaltopCommand(JavaPlugin plugin, AccountManagerImpl accountManager, ConfigManager config) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.config = config;
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
        CommandSender sender = ctx.getSource().getSender();
        Currency currency = currName != null ? accountManager.getCurrency(currName) : accountManager.getDefaultCurrency();

        if (currency == null) {
            sender.sendMessage(config.getMessage("currency_not_found"));
            return 0;
        }

        if (!currency.baltopEnabled()) {
            sender.sendMessage(config.getMessage("baltop_disabled"));
            return 0;
        }

        sender.sendMessage(config.getMessage("baltop_header", "currency", currency.name()));
        
        accountManager.getTopAccounts(currency, 10).thenAccept(top -> {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                int pos = 1;
                for (var entry : top) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                    String pName = op.getName() != null ? op.getName() : "Unknown";
                    sender.sendMessage(config.getMessage("baltop_entry", 
                            "position", String.valueOf(pos),
                            "player", pName,
                            "amount", currency.format(entry.getValue())));
                    pos++;
                }
            });
        });

        return Command.SINGLE_SUCCESS;
    }
}
