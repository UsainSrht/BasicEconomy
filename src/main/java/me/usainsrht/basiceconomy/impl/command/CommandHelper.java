package me.usainsrht.basiceconomy.impl.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import me.usainsrht.basiceconomy.impl.util.PlayerVisibility;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class CommandHelper {

    private CommandHelper() {}

    /**
     * Creates a LiteralArgumentBuilder whose built LiteralCommandNode checks requirement (canUse)
     * before suggesting itself during server-side tab completion.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return new LiteralArgumentBuilder<>(name) {
            @Override
            public LiteralCommandNode<CommandSourceStack> build() {
                LiteralCommandNode<CommandSourceStack> node = new LiteralCommandNode<>(
                        getLiteral(),
                        getCommand(),
                        getRequirement(),
                        getRedirect(),
                        getRedirectModifier(),
                        isFork()
                ) {
                    @Override
                    public CompletableFuture<Suggestions> listSuggestions(
                            CommandContext<CommandSourceStack> context,
                            SuggestionsBuilder builder
                    ) {
                        if (canUse(context.getSource())) {
                            return super.listSuggestions(context, builder);
                        }
                        return Suggestions.empty();
                    }
                };

                for (CommandNode<CommandSourceStack> child : getArguments()) {
                    node.addChild(child);
                }

                return node;
            }
        };
    }

    /**
     * Suggests configured currency names matching the current builder input.
     */
    public static CompletableFuture<Suggestions> suggestCurrencies(ConfigManager config, SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase();
        for (String cur : config.getCurrencies().keySet()) {
            if (cur.startsWith(input)) {
                builder.suggest(cur);
            }
        }
        return builder.buildFuture();
    }

    /**
     * Suggests player names matching the current builder input, respecting visibility and offline permission.
     */
    public static CompletableFuture<Suggestions> suggestPlayers(
            CommandSender sender,
            AccountManagerImpl accountManager,
            SuggestionsBuilder builder,
            boolean includeOffline
    ) {
        String input = builder.getRemaining().toLowerCase();

        if (!includeOffline) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (PlayerVisibility.canSeePlayer(sender, p) && p.getName().toLowerCase().startsWith(input)) {
                    builder.suggest(p.getName());
                }
            }
            return builder.buildFuture();
        }

        return CompletableFuture.supplyAsync(() -> {
            Set<String> names = new HashSet<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (PlayerVisibility.canSeePlayer(sender, p)) {
                    names.add(p.getName());
                }
            }
            names.addAll(accountManager.getCachedOfflinePlayerNames());
            for (String name : names) {
                if (name.toLowerCase().startsWith(input)) {
                    builder.suggest(name);
                }
            }
            return builder.build();
        });
    }

    /**
     * Resolves a player asynchronously. If online and visible to sender, resolves immediately.
     * If offline and allowed, checks offline player data asynchronously.
     */
    public static CompletableFuture<OfflinePlayer> resolvePlayerAsync(
            CommandSender sender,
            String targetName,
            boolean allowOffline
    ) {
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget == null) {
            onlineTarget = Bukkit.getPlayer(targetName);
        }

        if (onlineTarget != null && PlayerVisibility.canSeePlayer(sender, onlineTarget)) {
            return CompletableFuture.completedFuture(onlineTarget);
        }

        if (!allowOffline) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
                if (offlineTarget.hasPlayedBefore() || offlineTarget.isOnline()) {
                    return offlineTarget;
                }
            } catch (Exception ignored) {
            }
            return null;
        });
    }
}
