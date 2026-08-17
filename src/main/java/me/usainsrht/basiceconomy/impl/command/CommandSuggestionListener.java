package me.usainsrht.basiceconomy.impl.command;

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

public class CommandSuggestionListener implements Listener {

    private final ConfigManager config;

    public CommandSuggestionListener(ConfigManager config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSendSuggestions(AsyncPlayerSendSuggestionsEvent event) {
        String buffer = event.getBuffer();
        if (buffer == null || buffer.isEmpty()) {
            return;
        }

        String trimmed = buffer.startsWith("/") ? buffer.substring(1) : buffer;
        String[] parts = trimmed.split("\\s+", -1);
        if (parts.length == 0) {
            return;
        }

        String commandLabel = parts[0].toLowerCase();
        List<String> moneyLabels = config.getCommandNamesWithAliases("money");
        List<String> baltopLabels = config.getCommandNamesWithAliases("baltop");

        // Only filter subcommands at the first argument position (e.g., /money <subcommand>, /baltop <subcommand>)
        if (parts.length <= 2) {
            Player player = event.getPlayer();
            Suggestions original = event.getSuggestions();
            List<Suggestion> filtered = new ArrayList<>();

            if (moneyLabels.stream().anyMatch(l -> l.equalsIgnoreCase(commandLabel))) {
                for (Suggestion suggestion : original.getList()) {
                    String text = suggestion.getText();
                    if (isDisallowedMoneySubcommand(player, text)) {
                        continue;
                    }
                    filtered.add(suggestion);
                }
                event.setSuggestions(new Suggestions(original.getRange(), filtered));
            } else if (baltopLabels.stream().anyMatch(l -> l.equalsIgnoreCase(commandLabel))) {
                for (Suggestion suggestion : original.getList()) {
                    String text = suggestion.getText();
                    if (isDisallowedBaltopSubcommand(player, text)) {
                        continue;
                    }
                    filtered.add(suggestion);
                }
                event.setSuggestions(new Suggestions(original.getRange(), filtered));
            }
        }
    }

    private boolean isDisallowedMoneySubcommand(Player player, String text) {
        String lower = text.toLowerCase();

        // Admin subcommands: set, add, remove (and their aliases)
        if (matchesAny(lower, "money", "add")) {
            String addPerm = config.getSubcommandPermission("money", "add", config.getCommandPermission("money") + ".admin");
            if (!player.hasPermission(addPerm)) return true;
        }
        if (matchesAny(lower, "money", "set")) {
            String setPerm = config.getSubcommandPermission("money", "set", config.getCommandPermission("money") + ".admin");
            if (!player.hasPermission(setPerm)) return true;
        }
        if (matchesAny(lower, "money", "remove")) {
            String removePerm = config.getSubcommandPermission("money", "remove", config.getCommandPermission("money") + ".admin");
            if (!player.hasPermission(removePerm)) return true;
        }

        // Send / Pay subcommand
        if (matchesAny(lower, "money", "send")) {
            String sendPerm = config.getSubcommandPermission("money", "send", config.getCommandPermission("pay"));
            if (!player.hasPermission(sendPerm)) {
                return true;
            }
        }

        // See subcommand
        if (matchesAny(lower, "money", "see")) {
            String seePerm = config.getSubcommandPermission("money", "see", config.getOthersOfflinePermission());
            if (!player.hasPermission(seePerm)) {
                return true;
            }
        }

        // Reload subcommand
        if (matchesAny(lower, "money", "reload")) {
            String reloadPerm = config.getSubcommandPermission("money", "reload", config.getCommandPermission("money") + ".reload");
            if (!player.hasPermission(reloadPerm)) {
                return true;
            }
        }

        // Help subcommand
        if (matchesAny(lower, "money", "help")) {
            String helpPerm = config.getSubcommandPermission("money", "help", config.getCommandPermission("money") + ".help");
            if (!player.hasPermission(helpPerm)) {
                return true;
            }
        }

        // Info subcommand
        if (matchesAny(lower, "money", "info")) {
            String infoPerm = config.getSubcommandPermission("money", "info", config.getCommandPermission("money") + ".info");
            if (!player.hasPermission(infoPerm)) {
                return true;
            }
        }

        return false;
    }

    private boolean isDisallowedBaltopSubcommand(Player player, String text) {
        String lower = text.toLowerCase();

        // Hide subcommand
        if (matchesAny(lower, "baltop", "hide")) {
            String hidePerm = config.getSubcommandPermission("baltop", "hide", config.getCommandPermission("baltop") + ".hide");
            if (!player.hasPermission(hidePerm)) {
                return true;
            }
        }

        // Unhide subcommand
        if (matchesAny(lower, "baltop", "unhide")) {
            String unhidePerm = config.getSubcommandPermission("baltop", "unhide", config.getCommandPermission("baltop") + ".unhide");
            if (!player.hasPermission(unhidePerm)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesAny(String input, String command, String subKey) {
        List<String> namesWithAliases = config.getSubcommandNamesWithAliases(command, subKey);
        for (String name : namesWithAliases) {
            if (name.equalsIgnoreCase(input)) {
                return true;
            }
        }
        return false;
    }
}
