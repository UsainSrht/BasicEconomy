package me.usainsrht.basiceconomy.impl.config;

import me.usainsrht.basiceconomy.api.Currency;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ConfigManager {

    private FileConfiguration config;
    private final Map<String, Currency> currencies = new ConcurrentHashMap<>();
    private final Map<String, Component> messages = new ConcurrentHashMap<>();
    private final Set<UUID> baltopHiddenPlayers = ConcurrentHashMap.newKeySet();
    private String defaultCurrencyName = null;

    private final Map<String, String> rawMessages = new ConcurrentHashMap<>();

    public ConfigManager(FileConfiguration config) {
        this.config = config;
        load();
    }

    public void setConfig(FileConfiguration config) {
        this.config = config;
    }

    public String getPlayerFormat() {
        return config.getString("player-format", "<scchatuser_displayname>");
    }

    public void load() {
        currencies.clear();
        messages.clear();
        rawMessages.clear();
        baltopHiddenPlayers.clear();
        defaultCurrencyName = null;

        for (String uuidStr : config.getStringList("baltop-hidden-players")) {
            try {
                baltopHiddenPlayers.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) {
            }
        }

        String firstLoadedCurrency = null;
        ConfigurationSection currencySection = config.getConfigurationSection("currencies");
        if (currencySection != null) {
            for (String key : currencySection.getKeys(false)) {
                ConfigurationSection sec = currencySection.getConfigurationSection(key);
                if (sec == null)
                    continue;

                String name = sec.getString("name", key);
                Component displayName = parse(sec.getString("displayname", name));
                Component displayNamePlural = parse(sec.getString("displayname_plural", name + "s"));
                Component symbol = parse(sec.getString("symbol", "$"));
                String defaultFormat = sec.getString("default_format", "#,##0.00");
                boolean compactFormatting = sec.getBoolean("compact_formatting", true);
                boolean payEnabled = sec.getBoolean("pay_enabled", true);
                boolean baltopEnabled = sec.getBoolean("baltop_enabled", true);
                BigDecimal min = BigDecimal.valueOf(sec.getDouble("min_value", 0.0));
                BigDecimal start = BigDecimal.valueOf(sec.getDouble("start_value", 0.0));
                BigDecimal max = BigDecimal.valueOf(Double.MAX_VALUE);

                Currency currency = new Currency(
                        name, displayName, displayNamePlural, symbol, defaultFormat,
                        compactFormatting, payEnabled, baltopEnabled, min, max, start);

                if (firstLoadedCurrency == null) {
                    firstLoadedCurrency = name;
                }

                currencies.put(name.toLowerCase(), currency);
            }
        }

        String defaultCurrencyConfig = config.getString("default-currency");
        if (defaultCurrencyConfig != null && currencies.containsKey(defaultCurrencyConfig.toLowerCase())) {
            defaultCurrencyName = currencies.get(defaultCurrencyConfig.toLowerCase()).name();
        } else {
            defaultCurrencyName = firstLoadedCurrency;
        }

        ConfigurationSection messagesSection = config.getConfigurationSection("messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                String raw = messagesSection.getString(key, "");
                rawMessages.put(key, raw);
                messages.put(key, parse(raw));
            }
        }
    }

    public Component parse(String text) {
        return parse(text, (net.kyori.adventure.audience.Audience) null);
    }

    public Component parse(String text, net.kyori.adventure.audience.Audience audience) {
        net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.Builder builder = net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
                .builder();
        if (me.usainsrht.basiceconomy.impl.integration.MiniPlaceholdersHook.isAvailable()) {
            if (audience != null) {
                builder.resolver(me.usainsrht.basiceconomy.impl.integration.MiniPlaceholdersHook
                        .getAudienceGlobalPlaceholders());
            } else {
                builder.resolver(
                        me.usainsrht.basiceconomy.impl.integration.MiniPlaceholdersHook.getGlobalPlaceholders());
            }
        }
        return MiniMessage.miniMessage().deserialize(text, builder.build());
    }

    public Map<String, Currency> getCurrencies() {
        return currencies;
    }

    public Currency getDefaultCurrency() {
        return defaultCurrencyName != null ? currencies.get(defaultCurrencyName.toLowerCase()) : null;
    }

    public Component getMessage(String key, Object... placeholders) {
        return getMessage((net.kyori.adventure.audience.Audience) null, key, placeholders);
    }

    public Component getMessage(net.kyori.adventure.audience.Audience audience, String key, Object... placeholders) {
        String rawMsg = rawMessages.get(key);
        if (rawMsg == null) {
            return Component.text(key);
        }

        String rawPrefix = rawMessages.getOrDefault("prefix", "");
        boolean showPrefix = !key.equals("prefix") && !key.equals("money_help") && !key.equals("money_info");

        net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.Builder builder = net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
                .builder();

        if (me.usainsrht.basiceconomy.impl.integration.MiniPlaceholdersHook.isAvailable()) {
            if (audience != null) {
                builder.resolver(me.usainsrht.basiceconomy.impl.integration.MiniPlaceholdersHook
                        .getAudienceGlobalPlaceholders());
            } else {
                builder.resolver(
                        me.usainsrht.basiceconomy.impl.integration.MiniPlaceholdersHook.getGlobalPlaceholders());
            }
        }

        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                String pKey = String.valueOf(placeholders[i]);
                Object pVal = placeholders[i + 1];
                if (pVal instanceof Component cVal) {
                    builder.resolver(
                            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component(pKey, cVal));
                } else if (pVal != null) {
                    builder.resolver(net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed(pKey,
                            String.valueOf(pVal)));
                }
            }
        }

        net.kyori.adventure.text.minimessage.tag.resolver.TagResolver resolver = builder.build();
        Component msgComp = MiniMessage.miniMessage().deserialize(rawMsg, resolver);

        if (!showPrefix) {
            return msgComp;
        }

        Component prefixComp = rawPrefix.isEmpty()
                ? Component.empty()
                : MiniMessage.miniMessage().deserialize(rawPrefix, resolver);

        return prefixComp.append(msgComp);
    }

    public String getCommandName(String command) {
        return config.getString("commands." + command + ".name", command);
    }

    public List<String> getCommandAliases(String command) {
        return config.getStringList("commands." + command + ".aliases");
    }

    public String getCommandPermission(String command) {
        return config.getString("commands." + command + ".permission", "basiceconomy.command." + command);
    }

    public String getSubcommandName(String parent, String subKey) {
        return config.getString("commands." + parent + ".subcommands." + subKey + ".name", subKey);
    }

    public List<String> getSubcommandAliases(String parent, String subKey) {
        return config.getStringList("commands." + parent + ".subcommands." + subKey + ".aliases");
    }

    public String getSubcommandPermission(String parent, String subKey, String defaultPerm) {
        return config.getString("commands." + parent + ".subcommands." + subKey + ".permission", defaultPerm);
    }

    public String getStorageType() {
        return config.getString("storage.type", "H2").toUpperCase();
    }

    public String getStorageAddress() {
        return config.getString("storage.address", "localhost");
    }

    public int getStoragePort() {
        return config.getInt("storage.port", 3306);
    }

    public String getStorageDatabase() {
        return config.getString("storage.database", "basiceconomy");
    }

    public String getStorageUsername() {
        return config.getString("storage.username", "root");
    }

    public String getStoragePassword() {
        return config.getString("storage.password", "");
    }

    public String getStorageH2File() {
        return config.getString("storage.h2-file", "database");
    }

    public String getStorageMongoUri() {
        return config.getString("storage.mongodb-uri", "mongodb://localhost:27017");
    }

    public String getSyncType() {
        return config.getString("sync.type", "NONE").toUpperCase();
    }

    public String getRedisHost() {
        return config.getString("sync.redis.host", "localhost");
    }

    public int getRedisPort() {
        return config.getInt("sync.redis.port", 6379);
    }

    public String getRedisPassword() {
        return config.getString("sync.redis.password", "");
    }

    public String getRedisChannel() {
        return config.getString("sync.redis.channel", "basiceconomy:sync");
    }

    public Set<UUID> getBaltopHiddenPlayers() {
        return Collections.unmodifiableSet(baltopHiddenPlayers);
    }

    public boolean isBaltopHidden(UUID uuid) {
        return baltopHiddenPlayers.contains(uuid);
    }

    public boolean addBaltopHiddenPlayer(UUID uuid) {
        if (!baltopHiddenPlayers.add(uuid)) {
            return false;
        }
        saveBaltopHiddenPlayers();
        return true;
    }

    public boolean removeBaltopHiddenPlayer(UUID uuid) {
        if (!baltopHiddenPlayers.remove(uuid)) {
            return false;
        }
        saveBaltopHiddenPlayers();
        return true;
    }

    private void saveBaltopHiddenPlayers() {
        List<String> uuids = baltopHiddenPlayers.stream()
                .map(UUID::toString)
                .sorted()
                .collect(Collectors.toList());
        config.set("baltop-hidden-players", uuids);
    }
}
