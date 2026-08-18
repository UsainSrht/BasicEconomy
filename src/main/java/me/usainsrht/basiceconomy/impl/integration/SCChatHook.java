package me.usainsrht.basiceconomy.impl.integration;

import me.usainsrht.scchat.SCChat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;

public class SCChatHook {

    private static Boolean available = null;

    public static boolean isAvailable() {
        if (available == null) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("SCChat");
            available = plugin != null && plugin.isEnabled();
        }
        return available;
    }

    public static void refreshAvailability() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("SCChat");
        available = plugin != null && plugin.isEnabled();
    }

    /**
     * Asynchronously fetches the SCChat display name for an offline player.
     * Returns a future that completes with the Component, or null if SCChat is
     * unavailable or an error occurs.
     */
    public static CompletableFuture<Component> getDisplayNameAsync(OfflinePlayer player) {
        if (!isAvailable() || player == null) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            return SCChat.getInstance().getChatManager().getDisplayName(player)
                    .exceptionally(ex -> null);
        } catch (Throwable e) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
