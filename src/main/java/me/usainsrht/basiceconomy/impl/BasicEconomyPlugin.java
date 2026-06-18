package me.usainsrht.basiceconomy.impl;

import me.usainsrht.basiceconomy.api.BasicEconomyAPI;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import me.usainsrht.basiceconomy.impl.command.CommandRegistry;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import me.usainsrht.basiceconomy.impl.integration.PlaceholderAPIExpansion;
import me.usainsrht.basiceconomy.impl.integration.VaultEconomyImpl;
import me.usainsrht.basiceconomy.impl.storage.MongoStorage;
import me.usainsrht.basiceconomy.impl.storage.SqlStorage;
import me.usainsrht.basiceconomy.impl.storage.Storage;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class BasicEconomyPlugin extends JavaPlugin implements Listener {

    private ConfigManager configManager;
    private Storage storage;
    private AccountManagerImpl accountManager;
    private VaultEconomyImpl vaultEconomy;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(getConfig());

        try {
            if (configManager.getStorageType().equals("MONGODB")) {
                storage = new MongoStorage(configManager);
            } else {
                storage = new SqlStorage(configManager);
            }
            storage.connect();
        } catch (Exception e) {
            getLogger().severe("Failed to connect to storage!");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        accountManager = new AccountManagerImpl(this, configManager, storage);
        BasicEconomyAPI.setEconomyManager(accountManager);

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Vault hook
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            vaultEconomy = new VaultEconomyImpl(this, accountManager);
            getServer().getServicesManager().register(Economy.class, vaultEconomy, this, ServicePriority.Highest);
            getLogger().info("Hooked into Vault!");
        }

        // PlaceholderAPI hook
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIExpansion(this, accountManager, configManager).register();
            getLogger().info("Hooked into PlaceholderAPI!");
        }

        // Commands
        CommandRegistry registry = new CommandRegistry(this, accountManager, configManager);
        registry.register();

        // bStats
        int pluginId = 32082; // Placeholder ID
        new Metrics(this, pluginId);

        getLogger().info("BasicEconomy has been enabled.");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            try {
                storage.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        getLogger().info("BasicEconomy has been disabled.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        accountManager.handleJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        accountManager.handleQuit(event.getPlayer().getUniqueId());
    }

    public void reload() {
        reloadConfig();
        configManager.load();
        try {
            storage.disconnect();
            if (configManager.getStorageType().equals("MONGODB")) {
                storage = new MongoStorage(configManager);
            } else {
                storage = new SqlStorage(configManager);
            }
            storage.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
