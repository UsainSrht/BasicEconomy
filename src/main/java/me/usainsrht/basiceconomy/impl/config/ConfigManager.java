package me.usainsrht.basiceconomy.impl.config;

import me.usainsrht.basiceconomy.api.Currency;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;

public class ConfigManager {

    private FileConfiguration config;
    private final Map<String, Currency> currencies = new ConcurrentHashMap<>();
    private final Map<String, Component> messages = new ConcurrentHashMap<>();
    private String defaultCurrencyName = null;

    public ConfigManager(FileConfiguration config) {
        this.config = config;
        load();
    }

    public void setConfig(FileConfiguration config) {
        this.config = config;
    }

    public void load() {
        currencies.clear();
        messages.clear();
        defaultCurrencyName = null;

        String firstLoadedCurrency = null;
        ConfigurationSection currencySection = config.getConfigurationSection("currencies");
        if (currencySection != null) {
            for (String key : currencySection.getKeys(false)) {
                ConfigurationSection sec = currencySection.getConfigurationSection(key);
                if (sec == null) continue;

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
                // For limits we just use Double.MAX_VALUE effectively, but BigDecimal can go higher.
                // Sticking to large limits.
                BigDecimal max = BigDecimal.valueOf(Double.MAX_VALUE);

                Currency currency = new Currency(
                        name, displayName, displayNamePlural, symbol, defaultFormat,
                        compactFormatting, payEnabled, baltopEnabled, min, max, start
                );
                
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
                messages.put(key, parse(messagesSection.getString(key, "")));
            }
        }
    }

    private Component parse(String text) {
        return MiniMessage.miniMessage().deserialize(text);
    }

    public Map<String, Currency> getCurrencies() {
        return currencies;
    }

    public Currency getDefaultCurrency() {
        return defaultCurrencyName != null ? currencies.get(defaultCurrencyName.toLowerCase()) : null;
    }
    
    public Component getMessage(String key, String... placeholders) {
        Component message = messages.getOrDefault(key, Component.text(key));
        Component prefix = messages.getOrDefault("prefix", Component.empty());
        
        if (key.equals("prefix")) {
            return prefix; // Don't prefix the prefix
        }
        
        boolean showPrefix = !key.equals("money_help") && !key.equals("money_info");
        Component result = showPrefix ? prefix.append(message) : message;
        
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                String pKey = placeholders[i];
                String pVal = placeholders[i + 1];
                result = result.replaceText(builder -> builder.matchLiteral("<" + pKey + ">").replacement(pVal));
            }
        }
        
        return result;
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
}
