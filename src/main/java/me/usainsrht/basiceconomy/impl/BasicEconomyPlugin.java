package me.usainsrht.basiceconomy.impl;

import me.usainsrht.basiceconomy.api.BasicEconomyAPI;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import me.usainsrht.basiceconomy.impl.command.CommandRegistry;
import me.usainsrht.basiceconomy.impl.command.CommandSuggestionListener;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import me.usainsrht.basiceconomy.impl.integration.MiniPlaceholdersExpansion;
import me.usainsrht.basiceconomy.impl.integration.MiniPlaceholdersHook;
import me.usainsrht.basiceconomy.impl.integration.PlaceholderAPIExpansion;
import me.usainsrht.basiceconomy.impl.integration.SCChatHook;
import me.usainsrht.basiceconomy.impl.integration.Vault2Hook;
import me.usainsrht.basiceconomy.impl.integration.VaultEconomyImpl;
import me.usainsrht.basiceconomy.impl.storage.MongoStorage;
import me.usainsrht.basiceconomy.impl.storage.SqlStorage;
import me.usainsrht.basiceconomy.impl.storage.Storage;
import me.usainsrht.basiceconomy.impl.util.PlayerFormatter;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class BasicEconomyPlugin extends JavaPlugin implements Listener {

    private ConfigManager configManager;
    private Storage storage;
    private PlayerFormatter playerFormatter;
    private AccountManagerImpl accountManager;
    private VaultEconomyImpl vaultEconomy;
    private MiniPlaceholdersExpansion miniPlaceholdersExpansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(getConfig());
        playerFormatter = new PlayerFormatter(configManager);

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

        accountManager = new AccountManagerImpl(this, configManager, storage, playerFormatter);
        BasicEconomyAPI.setEconomyManager(accountManager);

        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new CommandSuggestionListener(configManager), this);

        // Setup integration hooks
        setupHooks();

        // Commands
        CommandRegistry registry = new CommandRegistry(this, accountManager, configManager, playerFormatter);
        registry.register();

        // bStats
        int pluginId = 32082; // Placeholder ID
        new Metrics(this, pluginId);

        getLogger().info("BasicEconomy has been enabled.");
    }

    @EventHandler
    public void onServerLoad(org.bukkit.event.server.ServerLoadEvent event) {
        setupHooks();
        if (accountManager != null) {
            accountManager.refreshBaltopCache();
        }
    }

    public void setupHooks() {
        // Vault hook
        if (getServer().getPluginManager().isPluginEnabled("Vault") && vaultEconomy == null) {
            vaultEconomy = new VaultEconomyImpl(this, accountManager);
            getServer().getServicesManager().register(Economy.class, vaultEconomy, this, ServicePriority.Highest);
            getLogger().info("Hooked into Vault!");
        }

        // VaultUnlocked hook
        try {
            Class.forName("net.milkbowl.vault2.economy.Economy");
            Vault2Hook.register(this, accountManager);
        } catch (Throwable ignored) {
            // VaultUnlocked not present
        }

        // PlaceholderAPI hook
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderAPIExpansion(this, accountManager, configManager).register();
            getLogger().info("Hooked into PlaceholderAPI!");
        }

        // MiniPlaceholders hook
        if (MiniPlaceholdersHook.isAvailable() && miniPlaceholdersExpansion == null) {
            miniPlaceholdersExpansion = new MiniPlaceholdersExpansion(this, accountManager, configManager);
            miniPlaceholdersExpansion.register();
        }

        getLogger().info("[BasicEconomy] Active hooks: MiniPlaceholders=" + MiniPlaceholdersHook.isAvailable()
                + ", SCChat=" + SCChatHook.isAvailable()
                + ", PlaceholderAPI=" + getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")
                + ", Vault=" + (vaultEconomy != null));
    }

    @Override
    public void onDisable() {
        if (miniPlaceholdersExpansion != null) {
            miniPlaceholdersExpansion.unregister();
        }
        if (accountManager != null) {
            accountManager.shutdownSync();
        }
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
        if (configManager != null) {
            configManager.setConfig(getConfig());
            configManager.load();
        }
        setupHooks();
        try {
            storage.disconnect();
            if (configManager.getStorageType().equals("MONGODB")) {
                storage = new MongoStorage(configManager);
            } else {
                storage = new SqlStorage(configManager);
            }
            storage.connect();
            if (accountManager != null) {
                accountManager.setStorage(storage);
                accountManager.initSync();
                accountManager.repairOnStartup();
                accountManager.refreshBaltopCache();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
