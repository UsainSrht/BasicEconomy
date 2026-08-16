package me.usainsrht.basiceconomy.impl.integration;

import me.usainsrht.scchat.api.SCChatAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

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

    public static Component getOfflineDisplayName(OfflinePlayer player) {
        if (!isAvailable() || player == null) {
            return null;
        }
        try {
            return SCChatAPI.getDisplayName(player);
        } catch (Throwable e) {
            return null;
        }
    }
}
