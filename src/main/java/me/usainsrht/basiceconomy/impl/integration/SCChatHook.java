package me.usainsrht.basiceconomy.impl.integration;

import me.usainsrht.scchat.SCChat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;

public class SCChatHook {

    public static boolean isAvailable() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("SCChat");
        return plugin != null && plugin.isEnabled();
    }

    public static void refreshAvailability() {
        // dynamic check via isAvailable()
    }

    /**
     * Asynchronously fetches the SCChat display name for any player (online or offline).
     * Returns a future that completes with the Component, or null if SCChat is
     * unavailable or an error occurs.
     */
    public static CompletableFuture<Component> getDisplayNameAsync(OfflinePlayer player) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            return SCChat.getInstance().getChatManager().getDisplayName(player)
                    .handle((comp, ex) -> {
                        if (ex != null) {
                            String name = player.getName() != null ? player.getName() : player.getUniqueId().toString();
                            Bukkit.getLogger().warning("[BasicEconomy Debug] SCChat getDisplayName failed for " + name + ": " + ex.getClass().getSimpleName() + (ex.getMessage() != null ? " (" + ex.getMessage() + ")" : ""));
                            return null;
                        }
                        return comp;
                    });
        } catch (Throwable e) {
            String name = player.getName() != null ? player.getName() : player.getUniqueId().toString();
            Bukkit.getLogger().warning("[BasicEconomy Debug] SCChat getDisplayName threw exception for " + name + ": " + e.getClass().getSimpleName() + (e.getMessage() != null ? " (" + e.getMessage() + ")" : ""));
            return CompletableFuture.completedFuture(null);
        }
    }
}

