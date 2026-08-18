package me.usainsrht.basiceconomy.impl.integration;

import io.github.miniplaceholders.api.MiniPlaceholders;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class MiniPlaceholdersHook {

    public static boolean isAvailable() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MiniPlaceholders");
        return plugin != null && plugin.isEnabled();
    }

    public static void refreshAvailability() {
        // dynamic check via isAvailable()
    }

    public static TagResolver getGlobalPlaceholders() {
        if (isAvailable()) {
            try {
                return MiniPlaceholders.globalPlaceholders();
            } catch (Throwable ignored) {}
        }
        return TagResolver.empty();
    }

    public static TagResolver getAudiencePlaceholders() {
        if (isAvailable()) {
            try {
                return MiniPlaceholders.audiencePlaceholders();
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

    public static TagResolver getRelationalPlaceholders() {
        if (isAvailable()) {
            try {
                return MiniPlaceholders.relationalPlaceholders();
            } catch (Throwable ignored) {}
        }
        return TagResolver.empty();
    }

    public static TagResolver getRelationalGlobalPlaceholders() {
        if (isAvailable()) {
            try {
                return MiniPlaceholders.relationalGlobalPlaceholders();
            } catch (Throwable ignored) {}
        }
        return TagResolver.empty();
    }
}


