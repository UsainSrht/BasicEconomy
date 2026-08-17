package me.usainsrht.basiceconomy.impl.integration;

import io.github.miniplaceholders.api.Expansion;
import me.usainsrht.basiceconomy.api.Account;
import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.impl.BasicEconomyPlugin;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl.BaltopEntry;
import me.usainsrht.basiceconomy.impl.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import org.bukkit.entity.Player;

import java.util.List;

public class MiniPlaceholdersExpansion {

    private final BasicEconomyPlugin plugin;
    private final AccountManagerImpl accountManager;
    private final ConfigManager config;
    private Expansion expansion;

    public MiniPlaceholdersExpansion(BasicEconomyPlugin plugin, AccountManagerImpl accountManager, ConfigManager config) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.config = config;
    }

    public void register() {
        if (!MiniPlaceholdersHook.isAvailable()) {
            return;
        }

        try {
            expansion = Expansion.builder("basiceconomy")
                    .author(plugin.getDescription().getAuthors().isEmpty() ? "UsainSrht" : plugin.getDescription().getAuthors().get(0))
                    .version(plugin.getDescription().getVersion())
                    // Audience placeholder: <basiceconomy_balance> or <basiceconomy_balance:currency>
                    .audiencePlaceholder(Player.class, "balance", (player, queue, ctx) -> {
                        Currency currency;
                        if (queue.hasNext()) {
                            String currName = queue.pop().value();
                            currency = accountManager.getCurrency(currName);
                        } else {
                            currency = accountManager.getDefaultCurrency();
                        }

                        if (currency == null) {
                            return Tag.inserting(Component.text("Invalid Currency"));
                        }

                        Account account = accountManager.getAccount(player.getUniqueId()).getNow(null);
                        if (account == null) {
                            return Tag.inserting(Component.text(currency.format(currency.startValue())));
                        }

                        return Tag.inserting(Component.text(currency.format(account.getBalance(currency))));
                    })
                    .audiencePlaceholder(Player.class, "position", (player, queue, ctx) -> {
                        Currency currency;
                        if (queue.hasNext()) {
                            String currName = queue.pop().value();
                            currency = accountManager.getCurrency(currName);
                        } else {
                            currency = accountManager.getDefaultCurrency();
                        }

                        if (currency == null) {
                            return Tag.inserting(Component.text("Invalid Currency"));
                        }

                        return Tag.inserting(Component.text(accountManager.getPlayerPosition(player.getUniqueId(), currency)));
                    })
                    .audiencePlaceholder(Player.class, "rank", (player, queue, ctx) -> {
                        Currency currency;
                        if (queue.hasNext()) {
                            String currName = queue.pop().value();
                            currency = accountManager.getCurrency(currName);
                        } else {
                            currency = accountManager.getDefaultCurrency();
                        }

                        if (currency == null) {
                            return Tag.inserting(Component.text("Invalid Currency"));
                        }

                        return Tag.inserting(Component.text(accountManager.getPlayerPosition(player.getUniqueId(), currency)));
                    })
                    // Global placeholder: <basiceconomy_baltop:pos> or <basiceconomy_baltop:type:pos:currency>
                    .globalPlaceholder("baltop", (queue, ctx) -> {
                        if (!queue.hasNext()) {
                            return Tag.inserting(Component.text(""));
                        }

                        String firstArg = queue.pop().value();
                        boolean hasType = firstArg.equalsIgnoreCase("name") || firstArg.equalsIgnoreCase("balance");
                        String type = hasType ? firstArg.toLowerCase() : "all";

                        String posStr = hasType ? (queue.hasNext() ? queue.pop().value() : null) : firstArg;
                        if (posStr == null) {
                            return Tag.inserting(Component.text(""));
                        }

                        int position;
                        try {
                            position = Integer.parseInt(posStr);
                        } catch (NumberFormatException e) {
                            return Tag.inserting(Component.text("Invalid Position"));
                        }

                        Currency currency;
                        if (queue.hasNext()) {
                            String currName = queue.pop().value();
                            currency = accountManager.getCurrency(currName);
                        } else {
                            currency = accountManager.getDefaultCurrency();
                        }

                        if (currency == null) {
                            return Tag.inserting(Component.text("Invalid Currency"));
                        }

                        List<BaltopEntry> top = accountManager.getCachedBaltop(currency);
                        if (top == null) {
                            return Tag.inserting(Component.text("Loading..."));
                        }

                        if (position < 1 || position > top.size()) {
                            return Tag.inserting(Component.text("None"));
                        }

                        BaltopEntry entry = top.get(position - 1);
                        if (type.equals("name")) {
                            return Tag.inserting(entry.getPlayerDisplay());
                        } else if (type.equals("balance")) {
                            return Tag.inserting(Component.text(currency.format(entry.getBalance())));
                        } else {
                            Component formatted = entry.getPlayerDisplay()
                                    .append(Component.text(" - " + currency.format(entry.getBalance())));
                            return Tag.inserting(formatted);
                        }
                    })
                    .build();

            expansion.register();
            plugin.getLogger().info("Registered MiniPlaceholders v3 expansion!");
        } catch (Throwable e) {
            plugin.getLogger().warning("Failed to register MiniPlaceholders expansion: " + e.getMessage());
        }
    }

    public void unregister() {
        if (expansion != null && expansion.registered()) {
            try {
                expansion.unregister();
            } catch (Throwable ignored) {}
        }
    }
}
