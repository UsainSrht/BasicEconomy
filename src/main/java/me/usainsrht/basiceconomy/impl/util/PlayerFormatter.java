package me.usainsrht.basiceconomy.impl.util;

import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import me.usainsrht.basiceconomy.impl.integration.MiniPlaceholdersHook;
import me.usainsrht.basiceconomy.impl.integration.SCChatHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public class PlayerFormatter {

    private final ConfigManager config;

    public PlayerFormatter(ConfigManager config) {
        this.config = config;
    }

    private void debug(String message) {
        if (config.isDebug()) {
            Bukkit.getLogger().info("[BasicEconomy Debug] " + message);
        }
    }

    /**
     * Formats a player asynchronously.
     * <ul>
     *   <li>Online players resolve MiniPlaceholders with audience and SCChat async display name.</li>
     *   <li>Offline players fetch the SCChat display-name via its async API before
     *       building the Component, so the result is always accurate even when the
     *       player has never joined during this server session.</li>
     * </ul>
     */
    public CompletableFuture<Component> formatPlayerAsync(OfflinePlayer offlinePlayer) {
        if (offlinePlayer == null) {
            debug("formatPlayerAsync called with null OfflinePlayer");
            return CompletableFuture.completedFuture(Component.text("Unknown"));
        }

        boolean isOnline = offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null;
        String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : offlinePlayer.getUniqueId().toString();
        debug("formatPlayerAsync called for " + playerName + " (UUID: " + offlinePlayer.getUniqueId() + ", online=" + isOnline + ")");

        CompletableFuture<Component> future;
        if (isOnline) {
            Player player = offlinePlayer.getPlayer();
            if (SCChatHook.isAvailable()) {
                debug("SCChat is available, fetching async display name for online player " + player.getName());
                future = SCChatHook.getDisplayNameAsync(player)
                        .thenApply(scchatDisplay -> {
                            debug("SCChat async display name for online " + player.getName() + ": " + (scchatDisplay != null ? PlainTextComponentSerializer.plainText().serialize(scchatDisplay) : "null"));
                            return buildOnlineComponent(player, scchatDisplay);
                        });
            } else {
                debug("SCChat is not available for online player " + player.getName());
                future = CompletableFuture.completedFuture(buildOnlineComponent(player, null));
            }
        } else {
            if (SCChatHook.isAvailable()) {
                debug("SCChat is available, fetching async display name for offline player " + playerName);
                future = SCChatHook.getDisplayNameAsync(offlinePlayer)
                        .thenApply(scchatDisplay -> {
                            debug("SCChat async display name for offline " + playerName + ": " + (scchatDisplay != null ? PlainTextComponentSerializer.plainText().serialize(scchatDisplay) : "null"));
                            return buildOfflineComponent(offlinePlayer, scchatDisplay);
                        });
            } else {
                debug("SCChat is not available for offline player " + playerName);
                future = CompletableFuture.completedFuture(buildOfflineComponent(offlinePlayer, null));
            }
        }

        return future.exceptionally(ex -> {
            debug("Formatting failed for " + playerName + ": " + ex.getMessage());
            return Component.text(playerName);
        });
    }

    /**
     * Synchronous convenience variant.
     */
    public Component formatPlayer(OfflinePlayer offlinePlayer) {
        if (offlinePlayer == null) {
            return Component.text("Unknown");
        }

        if (offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
            return formatOnlinePlayer(offlinePlayer.getPlayer());
        }

        return buildOfflineComponent(offlinePlayer, null);
    }

    public Component formatOnlinePlayer(Player player) {
        return buildOnlineComponent(player, null);
    }

    public Component buildOnlineComponent(Player player, Component scchatDisplayName) {
        String format = config.getPlayerFormat();
        String fallbackName = player.getName() != null ? player.getName() : "Unknown";
        debug("buildOnlineComponent for " + fallbackName + " with raw format: '" + format + "'");

        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                try {
                    String papiParsed = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, format);
                    debug("PlaceholderAPI parsed format for " + fallbackName + ": '" + papiParsed + "'");
                    format = papiParsed;
                } catch (Throwable t) {
                    debug("PlaceholderAPI error: " + t.getMessage());
                }
            }

            TagResolver.Builder resolverBuilder = TagResolver.builder();

            // 1. Fallbacks (lowest priority)
            resolverBuilder.resolver(Placeholder.parsed("player_name", fallbackName));
            resolverBuilder.resolver(Placeholder.parsed("player", fallbackName));
            resolverBuilder.resolver(Placeholder.parsed("username", fallbackName));
            resolverBuilder.resolver(Placeholder.component("player_displayname", player.displayName()));
            resolverBuilder.resolver(Placeholder.component("displayname", player.displayName()));

            // 2. MiniPlaceholders audience & global placeholders for this player
            boolean miniAvailable = MiniPlaceholdersHook.isAvailable();
            debug("MiniPlaceholders available: " + miniAvailable);
            if (miniAvailable) {
                resolverBuilder.resolver(MiniPlaceholdersHook.getAudienceGlobalPlaceholders());
            }

            // 3. Direct SCChat display name if available (highest priority)
            if (scchatDisplayName != null) {
                resolverBuilder.resolver(Placeholder.component("scchatuser_displayname", scchatDisplayName));
                resolverBuilder.resolver(Placeholder.component("displayname", scchatDisplayName));
                resolverBuilder.resolver(Placeholder.component("player_displayname", scchatDisplayName));
            } else if (!miniAvailable) {
                resolverBuilder.resolver(Placeholder.parsed("scchatuser_displayname", fallbackName));
            }

            TagResolver resolver = resolverBuilder.build();
            Component result = MiniMessage.miniMessage().deserialize(format, player, resolver);
            debug("buildOnlineComponent result for " + fallbackName + ": '" + PlainTextComponentSerializer.plainText().serialize(result) + "' (MiniMessage serialized: " + MiniMessage.miniMessage().serialize(result) + ")");
            return result;
        } catch (Throwable t) {
            debug("buildOnlineComponent error for " + fallbackName + ": " + t.getMessage());
            return Component.text(fallbackName);
        }
    }

    public Component buildOfflineComponent(OfflinePlayer offlinePlayer, Component scchatDisplayName) {
        String format = config.getPlayerFormat();
        String fallbackName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
        debug("buildOfflineComponent for " + fallbackName + " with raw format: '" + format + "'");

        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                try {
                    String papiParsed = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(offlinePlayer, format);
                    debug("PlaceholderAPI parsed format for offline " + fallbackName + ": '" + papiParsed + "'");
                    format = papiParsed;
                } catch (Throwable t) {
                    debug("PlaceholderAPI error: " + t.getMessage());
                }
            }

            TagResolver.Builder resolverBuilder = TagResolver.builder();

            // 1. Fallbacks (lowest priority)
            resolverBuilder.resolver(Placeholder.parsed("player_name", fallbackName));
            resolverBuilder.resolver(Placeholder.parsed("player", fallbackName));
            resolverBuilder.resolver(Placeholder.parsed("username", fallbackName));
            resolverBuilder.resolver(Placeholder.parsed("displayname", fallbackName));
            resolverBuilder.resolver(Placeholder.parsed("player_displayname", fallbackName));

            // 2. MiniPlaceholders global placeholders
            if (MiniPlaceholdersHook.isAvailable()) {
                resolverBuilder.resolver(MiniPlaceholdersHook.getGlobalPlaceholders());
            }

            // 3. Direct SCChat display name if available (highest priority)
            if (scchatDisplayName != null) {
                resolverBuilder.resolver(Placeholder.component("scchatuser_displayname", scchatDisplayName));
                resolverBuilder.resolver(Placeholder.component("displayname", scchatDisplayName));
                resolverBuilder.resolver(Placeholder.component("player_displayname", scchatDisplayName));
            } else {
                resolverBuilder.resolver(Placeholder.parsed("scchatuser_displayname", fallbackName));
            }

            TagResolver resolver = resolverBuilder.build();
            Component result = MiniMessage.miniMessage().deserialize(format, resolver);
            debug("buildOfflineComponent result for " + fallbackName + ": '" + PlainTextComponentSerializer.plainText().serialize(result) + "' (MiniMessage serialized: " + MiniMessage.miniMessage().serialize(result) + ")");
            return result;
        } catch (Throwable t) {
            debug("buildOfflineComponent error for " + fallbackName + ": " + t.getMessage());
            return Component.text(fallbackName);
        }
    }
}

