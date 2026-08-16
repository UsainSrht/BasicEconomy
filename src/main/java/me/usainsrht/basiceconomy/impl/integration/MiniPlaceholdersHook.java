package me.usainsrht.basiceconomy.impl.integration;

import io.github.miniplaceholders.api.MiniPlaceholders;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class MiniPlaceholdersHook {

    private static Boolean available = null;

    public static boolean isAvailable() {
        if (available == null) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("MiniPlaceholders");
            available = plugin != null && plugin.isEnabled();
        }
        return available;
    }

    public static void refreshAvailability() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MiniPlaceholders");
        available = plugin != null && plugin.isEnabled();
    }

    public static TagResolver getGlobalPlaceholders() {
        if (isAvailable()) {
            try {
                return MiniPlaceholders.globalPlaceholders();
            } catch (Throwable ignored) {}
        }
        return TagResolver.empty();
    }

    public static TagResolver getAudienceGlobalPlaceholders() {
        if (isAvailable()) {
            try {
                return MiniPlaceholders.audienceGlobalPlaceholders();
            } catch (Throwable ignored) {}
        }
        return TagResolver.empty();
    }
}
