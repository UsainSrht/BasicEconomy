package me.usainsrht.basiceconomy.impl.command;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.usainsrht.basiceconomy.impl.BasicEconomyPlugin;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import org.bukkit.plugin.Plugin;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class CommandRegistry {

    private final BasicEconomyPlugin plugin;
    private final AccountManagerImpl accountManager;
    private final ConfigManager config;

    public CommandRegistry(BasicEconomyPlugin plugin, AccountManagerImpl accountManager, ConfigManager config) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.config = config;
    }

    public void register() {
        LifecycleEventManager<Plugin> manager = plugin.getLifecycleManager();
        
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            EconomyCommand moneyCmd = new EconomyCommand(plugin, accountManager, config);
            List<String> moneyAliases = config.getCommandAliases("money");
            for (String alias : moneyAliases) {
                commands.register(moneyCmd.build(alias).build(), "BasicEconomy main command", List.of());
            }

            PayCommand payCmd = new PayCommand(plugin, accountManager, config);
            List<String> payAliases = config.getCommandAliases("pay");
            for (String alias : payAliases) {
                commands.register(payCmd.build(alias).build(), "BasicEconomy pay command", List.of());
            }

            BaltopCommand baltopCmd = new BaltopCommand(plugin, accountManager, config);
            List<String> baltopAliases = config.getCommandAliases("baltop");
            for (String alias : baltopAliases) {
                commands.register(baltopCmd.build(alias).build(), "BasicEconomy baltop command", List.of());
            }
        });
    }
}
