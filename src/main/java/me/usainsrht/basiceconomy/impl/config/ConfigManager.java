package me.usainsrht.basiceconomy.impl.config;

import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.impl.integration.MiniPlaceholdersHook;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
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

    public boolean isDebug() {
        return config.getBoolean("debug", false);
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
        String globalCompactFormat = config.getString("compact_format", "#.##{suffix}");
        List<String> globalCompactSuffixes = parseSuffixes(config, "compact_suffixes", List.of("k", "m", "b", "t", "q"));

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
                String compactFormat = sec.getString("compact_format", globalCompactFormat);
                List<String> compactSuffixes = parseSuffixes(sec, "compact_suffixes", globalCompactSuffixes);
                boolean payEnabled = sec.getBoolean("pay_enabled", true);
                boolean baltopEnabled = sec.getBoolean("baltop_enabled", true);
                BigDecimal min = BigDecimal.valueOf(sec.getDouble("min_value", 0.0));
                BigDecimal start = BigDecimal.valueOf(sec.getDouble("start_value", 0.0));
                BigDecimal max = BigDecimal.valueOf(Double.MAX_VALUE);

                Currency currency = new Currency(
                        name, displayName, displayNamePlural, symbol, defaultFormat,
                        compactFormatting, compactFormat, compactSuffixes,
                        payEnabled, baltopEnabled, min, max, start);

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
            loadMessagesRecursively(messagesSection, "");
        }

        // Support history types configured under history.types
        ConfigurationSection historyTypesSec = config.getConfigurationSection("history.types");
        if (historyTypesSec != null) {
            for (String key : historyTypesSec.getKeys(false)) {
                String raw = historyTypesSec.getString(key, "");
                rawMessages.put("history_type_" + key.toLowerCase(java.util.Locale.ROOT), raw);
                rawMessages.put("history_types." + key.toLowerCase(java.util.Locale.ROOT), raw);
            }
        }

        // Support history time formats configured under history.time-ago or history.time-format
        ConfigurationSection historyTimeSec = config.getConfigurationSection("history.time-ago");
        if (historyTimeSec == null) {
            historyTimeSec = config.getConfigurationSection("history.time-format");
        }
        if (historyTimeSec != null) {
            for (String key : historyTimeSec.getKeys(false)) {
                String raw = historyTimeSec.getString(key, "");
                String normKey = key.replace('-', '_').toLowerCase(java.util.Locale.ROOT);
                rawMessages.put("history_time_" + normKey, raw);
                rawMessages.put("history_time." + normKey, raw);
            }
        }

        // Default history type messages
        for (me.usainsrht.basiceconomy.api.transaction.TransactionType type : me.usainsrht.basiceconomy.api.transaction.TransactionType.values()) {
            String typeName = type.name().toLowerCase(java.util.Locale.ROOT);
            String primaryKey = "history_type_" + typeName;
            if (!rawMessages.containsKey(primaryKey)) {
                String val = rawMessages.get("history_types_" + typeName);
                if (val == null) val = rawMessages.get("history_types." + typeName);
                if (val == null) {
                    val = switch (type) {
                        case PAY -> "<yellow>Payment</yellow>";
                        case ADMIN_ADD -> "<green>Admin Grant</green>";
                        case ADMIN_SET -> "<aqua>Admin Set</aqua>";
                        case ADMIN_REMOVE -> "<red>Admin Deduction</red>";
                        case SYSTEM -> "<gray>System</gray>";
                    };
                }
                rawMessages.put(primaryKey, val);
                messages.put(primaryKey, parse(val));
            }
        }

        // Default time ago messages
        ensureDefaultRawMessage("history_time_just_now", "just now");
        ensureDefaultRawMessage("history_time_minutes_ago", "<minutes>m ago");
        ensureDefaultRawMessage("history_time_hours_ago", "<hours>h ago");
        ensureDefaultRawMessage("history_time_yesterday", "yesterday");
        ensureDefaultRawMessage("history_time_days_ago", "<days>d ago");
        ensureDefaultRawMessage("history_time_date", "<date>");
        ensureDefaultRawMessage("history_time_date_format", "yyyy-MM-dd");
    }

    private void loadMessagesRecursively(ConfigurationSection sec, String prefix) {
        for (String key : sec.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "_" + key;
            String dotKey = prefix.isEmpty() ? key : prefix + "." + key;
            if (sec.isConfigurationSection(key)) {
                ConfigurationSection child = sec.getConfigurationSection(key);
                if (child != null) {
                    loadMessagesRecursively(child, fullKey);
                }
            } else {
                String raw = sec.getString(key, "");
                rawMessages.put(fullKey, raw);
                rawMessages.put(dotKey, raw);
                messages.put(fullKey, parse(raw));
                messages.put(dotKey, parse(raw));
            }
        }
    }

    private void ensureDefaultRawMessage(String key, String def) {
        if (!rawMessages.containsKey(key)) {
            String dotKey = key.replaceFirst("_", ".");
            String alt = rawMessages.get(dotKey);
            String val = alt != null ? alt : def;
            rawMessages.put(key, val);
            messages.put(key, parse(val));
        }
    }

    public Component parse(String text) {
        return parse(text, null);
    }

    public Component parse(String text, Audience audience) {
        TagResolver.Builder builder = TagResolver.builder();
        if (MiniPlaceholdersHook.isAvailable()) {
            builder.resolver(MiniPlaceholdersHook.getAudienceGlobalPlaceholders());
        }
        if (audience instanceof net.kyori.adventure.pointer.Pointered pointered) {
            return MiniMessage.miniMessage().deserialize(text, pointered, builder.build());
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
        return getMessage((Audience) null, key, placeholders);
    }

    public Component getMessage(Audience audience, String key, Object... placeholders) {
        String rawMsg = rawMessages.get(key);
        if (rawMsg == null) {
            return Component.text(key);
        }

        String rawPrefix = rawMessages.getOrDefault("prefix", "");
        boolean showPrefix = !key.equals("prefix")
                && !key.equals("money_help")
                && !key.equals("money_info")
                && !key.startsWith("history_time")
                && !key.startsWith("history_type")
                && !key.startsWith("history_types")
                && !key.equals("history_tooltip")
                && !key.startsWith("history_stats_line")
                && !key.equals("history_stats_footer");

        TagResolver.Builder builder = TagResolver.builder();

        if (MiniPlaceholdersHook.isAvailable()) {
            builder.resolver(MiniPlaceholdersHook.getAudienceGlobalPlaceholders());
        }

        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                String pKey = String.valueOf(placeholders[i]);
                Object pVal = placeholders[i + 1];
                if (pVal instanceof Currency cur) {
                    builder.resolver(Placeholder.parsed(pKey, cur.name()));
                    builder.resolver(Placeholder.parsed("currency", cur.name()));
                    builder.resolver(Placeholder.parsed("currency_name", cur.name()));
                    if (cur.displayName() != null) {
                        builder.resolver(Placeholder.component("currency_display", cur.displayName()));
                    }
                    if (cur.displayNamePlural() != null) {
                        builder.resolver(Placeholder.component("currency_plural", cur.displayNamePlural()));
                    }
                    if (cur.symbol() != null) {
                        builder.resolver(Placeholder.component("currency_symbol", cur.symbol()));
                    }
                } else if (pVal instanceof Component cVal) {
                    builder.resolver(Placeholder.component(pKey, cVal));
                } else if (pVal instanceof org.bukkit.OfflinePlayer op) {
                    String opName = op.getName() != null ? op.getName() : op.getUniqueId().toString();
                    builder.resolver(Placeholder.parsed(pKey, opName));
                } else if (pVal != null) {
                    String strVal = String.valueOf(pVal);
                    builder.resolver(Placeholder.parsed(pKey, strVal));
                    if (pKey.equals("currency")) {
                        builder.resolver(Placeholder.parsed("currency_name", strVal));
                        Currency cur = currencies.get(strVal.toLowerCase());
                        if (cur != null) {
                            if (cur.displayName() != null) {
                                builder.resolver(Placeholder.component("currency_display", cur.displayName()));
                            }
                            if (cur.displayNamePlural() != null) {
                                builder.resolver(Placeholder.component("currency_plural", cur.displayNamePlural()));
                            }
                            if (cur.symbol() != null) {
                                builder.resolver(Placeholder.component("currency_symbol", cur.symbol()));
                            }
                        }
                    }
                }
            }
        }

        TagResolver resolver = builder.build();
        Component msgComp;
        if (audience instanceof net.kyori.adventure.pointer.Pointered pointered) {
            msgComp = MiniMessage.miniMessage().deserialize(rawMsg, pointered, resolver);
        } else {
            msgComp = MiniMessage.miniMessage().deserialize(rawMsg, resolver);
        }

        if (!showPrefix) {
            return msgComp;
        }

        Component prefixComp = rawPrefix.isEmpty()
                ? Component.empty()
                : (audience instanceof net.kyori.adventure.pointer.Pointered pointered
                    ? MiniMessage.miniMessage().deserialize(rawPrefix, pointered, resolver)
                    : MiniMessage.miniMessage().deserialize(rawPrefix, resolver));

        return prefixComp.append(msgComp);
    }

    public String getCommandName(String command) {
        return config.getString("commands." + command + ".name", command);
    }

    public List<String> getCommandAliases(String command) {
        return config.getStringList("commands." + command + ".aliases");
    }

    public List<String> getCommandNamesWithAliases(String command) {
        String name = getCommandName(command);
        List<String> aliases = getCommandAliases(command);
        List<String> names = new ArrayList<>();
        if (name != null && !name.isBlank()) {
            names.add(name);
        }
        for (String alias : aliases) {
            if (alias != null && !alias.isBlank() && !names.contains(alias)) {
                names.add(alias);
            }
        }
        return names;
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

    public List<String> getSubcommandNamesWithAliases(String parent, String subKey) {
        String name = getSubcommandName(parent, subKey);
        List<String> aliases = getSubcommandAliases(parent, subKey);
        List<String> names = new ArrayList<>();
        if (name != null && !name.isBlank()) {
            names.add(name);
        }
        for (String alias : aliases) {
            if (alias != null && !alias.isBlank() && !names.contains(alias)) {
                names.add(alias);
            }
        }
        return names;
    }

    public String getSubcommandPermission(String parent, String subKey, String defaultPerm) {
        return config.getString("commands." + parent + ".subcommands." + subKey + ".permission", defaultPerm);
    }

    public String getCurrencySubcommandName(Currency currency, String subKey) {
        if (currency != null) {
            String path = "currencies." + currency.name().toLowerCase() + "." + subKey + "_subcommand.name";
            if (config.isSet(path)) {
                return config.getString(path, subKey);
            }
        }
        return getSubcommandName("money", subKey);
    }

    public List<String> getCurrencySubcommandAliases(Currency currency, String subKey) {
        if (currency != null) {
            String path = "currencies." + currency.name().toLowerCase() + "." + subKey + "_subcommand.aliases";
            if (config.isSet(path)) {
                return config.getStringList(path);
            }
        }
        return getSubcommandAliases("money", subKey);
    }

    public List<String> getCurrencySubcommandNamesWithAliases(Currency currency, String subKey) {
        String name = getCurrencySubcommandName(currency, subKey);
        List<String> aliases = getCurrencySubcommandAliases(currency, subKey);
        List<String> names = new ArrayList<>();
        if (name != null && !name.isBlank()) {
            names.add(name);
        }
        for (String alias : aliases) {
            if (alias != null && !alias.isBlank() && !names.contains(alias)) {
                names.add(alias);
            }
        }
        return names;
    }

    public String getCurrencySubcommandPermission(Currency currency, String subKey, String defaultPerm) {
        if (currency != null) {
            String path = "currencies." + currency.name().toLowerCase() + "." + subKey + "_subcommand.permission";
            if (config.isSet(path)) {
                return config.getString(path, defaultPerm);
            }
        }
        return getSubcommandPermission("money", subKey, defaultPerm);
    }

    public String getOthersOfflinePermission() {
        return config.getString("commands.money.subcommands.others.offline.permission", "basiceconomy.command.money.others.offline");
    }

    public String getPayOfflinePermission() {
        return config.getString("commands.pay.subcommands.offline.permission", "basiceconomy.command.pay.offline");
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

    public int getBaltopCacheLimit() {
        return config.getInt("baltop.cache-limit", 100);
    }

    public int getBaltopDisplayTop() {
        return config.getInt("baltop.display-top", 10);
    }

    public String getBaltopOutOfRangePosition() {
        return config.getString("baltop.out-of-range-position", "100+");
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

    private List<String> parseSuffixes(ConfigurationSection sec, String path, List<String> defaultSuffixes) {
        if (sec.isList(path)) {
            List<String> list = sec.getStringList(path);
            return list.isEmpty() ? defaultSuffixes : list;
        } else if (sec.isString(path)) {
            String str = sec.getString(path);
            if (str != null && !str.isBlank()) {
                return Arrays.stream(str.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }
        }
        return defaultSuffixes;
    }

    public String getServerId() {
        return config.getString("server-id", "default");
    }

    public boolean isHistoryEnabled() {
        return config.getBoolean("history.enabled", true);
    }

    public int getHistoryPageSize() {
        return config.getInt("history.page-size", 8);
    }

    public int getHistoryRetentionDays() {
        return config.getInt("history.retention-days", 30);
    }

    public int getHistoryPruneInterval() {
        return config.getInt("history.prune-interval", 43200);
    }

    public boolean isGlobalLoggingEnabled() {
        return config.getBoolean("history.logging.global.enabled", true);
    }

    public String getGlobalLogPattern() {
        return config.getString("history.logging.global.file-pattern", "logs/global/{date}.csv");
    }

    public String getGlobalLogDateFormat() {
        return config.getString("history.logging.global.date-format", "yyyy-MM-dd");
    }

    public boolean isPlayerLoggingEnabled() {
        return config.getBoolean("history.logging.players.enabled", true);
    }

    public String getPlayerLogPattern() {
        return config.getString("history.logging.players.file-pattern", "logs/players/{player}.csv");
    }

    public String getPlayerLogDateFormat() {
        return config.getString("history.logging.players.date-format", "yyyy-MM-dd");
    }

    public boolean isLogToConsole() {
        return config.getBoolean("history.logging.log-to-console", false);
    }

    public boolean isHighValueAlertEnabled() {
        return config.getBoolean("history.high-value-alert.enabled", false);
    }

    public BigDecimal getHighValueAlertThreshold() {
        return BigDecimal.valueOf(config.getDouble("history.high-value-alert.threshold", 50000.0));
    }

    public String getHighValueAlertPermission() {
        return config.getString("history.high-value-alert.permission", "basiceconomy.alert.highvalue");
    }

    public String getPayHistoryPermission() {
        return config.getString("commands.pay.subcommands.history.permission", "basiceconomy.command.pay.history");
    }

    public String getMoneyHistoryPermission() {
        return config.getString("commands.money.subcommands.history.permission", "basiceconomy.command.money.history");
    }

    public String getHistoryOthersPermission() {
        return config.getString("commands.money.subcommands.history.others.permission", "basiceconomy.command.money.history.others");
    }

    public String getHistoryStatsPermission() {
        return config.getString("commands.money.subcommands.history.stats.permission", "basiceconomy.command.money.history.stats");
    }

    public Component getTransactionTypeDisplay(me.usainsrht.basiceconomy.api.transaction.TransactionType type) {
        return getTransactionTypeDisplay(null, type);
    }

    public Component getTransactionTypeDisplay(Audience audience, me.usainsrht.basiceconomy.api.transaction.TransactionType type) {
        if (type == null) return Component.text("Unknown");
        String key = "history_type_" + type.name().toLowerCase(java.util.Locale.ROOT);
        return getMessage(audience, key);
    }

    public Component formatTimeAgo(long timestamp) {
        return formatTimeAgo(null, timestamp);
    }

    public Component formatTimeAgo(Audience audience, long timestamp) {
        long diff = Math.max(0, System.currentTimeMillis() - timestamp);
        long seconds = diff / 1000L;
        if (seconds < 60) {
            return getMessage(audience, "history_time_just_now");
        }
        long minutes = seconds / 60L;
        if (minutes < 60) {
            return getMessage(audience, "history_time_minutes_ago", "minutes", String.valueOf(minutes));
        }
        long hours = minutes / 60L;
        if (hours < 24) {
            return getMessage(audience, "history_time_hours_ago", "hours", String.valueOf(hours));
        }
        long days = hours / 24L;
        if (days == 1) {
            return getMessage(audience, "history_time_yesterday");
        }
        if (days < 7) {
            return getMessage(audience, "history_time_days_ago", "days", String.valueOf(days));
        }

        String pattern = rawMessages.getOrDefault("history_time_date_format", "yyyy-MM-dd");
        java.time.format.DateTimeFormatter formatter;
        try {
            formatter = java.time.format.DateTimeFormatter.ofPattern(pattern).withZone(java.time.ZoneOffset.UTC);
        } catch (Exception e) {
            formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(java.time.ZoneOffset.UTC);
        }
        String dateStr = formatter.format(java.time.Instant.ofEpochMilli(timestamp));
        return getMessage(audience, "history_time_date", "date", dateStr);
    }
}
