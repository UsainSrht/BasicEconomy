package me.usainsrht.basiceconomy.impl.integration;

import me.usainsrht.basiceconomy.impl.BasicEconomyPlugin;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import org.bukkit.plugin.ServicePriority;

public class Vault2Hook {
    public static void register(BasicEconomyPlugin plugin, AccountManagerImpl accountManager) {
        Vault2EconomyImpl vault2Economy = new Vault2EconomyImpl(plugin, accountManager);
        plugin.getServer().getServicesManager().register(net.milkbowl.vault2.economy.Economy.class, vault2Economy, plugin, ServicePriority.Highest);
        plugin.getLogger().info("Hooked into VaultUnlocked (Vault2)!");
    }
}
