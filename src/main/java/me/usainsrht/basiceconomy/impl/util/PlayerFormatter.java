package me.usainsrht.basiceconomy.impl.util;

import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import me.usainsrht.basiceconomy.impl.integration.MiniPlaceholdersHook;
import me.usainsrht.basiceconomy.impl.integration.SCChatHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public class PlayerFormatter {

    private final ConfigManager config;

    public PlayerFormatter(ConfigManager config) {
        this.config = config;
    }

    public CompletableFuture<Component> formatPlayerAsync(OfflinePlayer offlinePlayer) {
        if (offlinePlayer == null) {
            return CompletableFuture.completedFuture(Component.text("Unknown"));
        }

        if (offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
            return CompletableFuture.completedFuture(formatOnlinePlayer(offlinePlayer.getPlayer()));
        }

        return CompletableFuture.supplyAsync(() -> formatOfflinePlayerSync(offlinePlayer));
    }

    public Component formatPlayer(OfflinePlayer offlinePlayer) {
        if (offlinePlayer == null) {
            return Component.text("Unknown");
        }

        if (offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
            return formatOnlinePlayer(offlinePlayer.getPlayer());
        }

        return formatOfflinePlayerSync(offlinePlayer);
    }

    public Component formatOnlinePlayer(Player player) {
        String format = config.getPlayerFormat();
        String fallbackName = player.getName();

        TagResolver.Builder resolverBuilder = TagResolver.builder();

        // Include MiniPlaceholders audience & global placeholders if available
        if (MiniPlaceholdersHook.isAvailable()) {
            resolverBuilder.resolver(MiniPlaceholdersHook.getAudienceGlobalPlaceholders());
        }

        // Fallbacks for standard player placeholders
        resolverBuilder.resolver(Placeholder.parsed("player_name", fallbackName));
        resolverBuilder.resolver(Placeholder.parsed("player", fallbackName));
        resolverBuilder.resolver(Placeholder.parsed("scchatuser_displayname", fallbackName));

        return MiniMessage.miniMessage().deserialize(format, resolverBuilder.build());
    }

    private Component formatOfflinePlayerSync(OfflinePlayer offlinePlayer) {
        String format = config.getPlayerFormat();
        String fallbackName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";

        Component scchatDisplayName = null;
        if (format.toLowerCase().contains("scchatuser_displayname") && SCChatHook.isAvailable()) {
            try {
                scchatDisplayName = SCChatHook.getOfflineDisplayName(offlinePlayer);
            } catch (Throwable ignored) {}
        }

        TagResolver.Builder resolverBuilder = TagResolver.builder();

        if (MiniPlaceholdersHook.isAvailable()) {
            resolverBuilder.resolver(MiniPlaceholdersHook.getGlobalPlaceholders());
        }

        resolverBuilder.resolver(Placeholder.parsed("player_name", fallbackName));
        resolverBuilder.resolver(Placeholder.parsed("player", fallbackName));

        if (scchatDisplayName != null) {
            resolverBuilder.resolver(Placeholder.component("scchatuser_displayname", scchatDisplayName));
        } else {
            resolverBuilder.resolver(Placeholder.parsed("scchatuser_displayname", fallbackName));
        }

        return MiniMessage.miniMessage().deserialize(format, resolverBuilder.build());
    }
}
