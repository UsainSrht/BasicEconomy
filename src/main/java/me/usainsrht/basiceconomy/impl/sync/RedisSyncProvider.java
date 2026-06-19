package me.usainsrht.basiceconomy.impl.sync;

import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.impl.BasicEconomyPlugin;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.math.BigDecimal;
import java.util.UUID;
import org.bukkit.Bukkit;

public class RedisSyncProvider implements SyncProvider {

    private final BasicEconomyPlugin plugin;
    private final AccountManagerImpl accountManager;
    private JedisPool jedisPool;
    private JedisPubSub pubSub;
    private Thread subscribeThread;
    
    private final String host;
    private final int port;
    private final String password;
    private final String channel;

    public RedisSyncProvider(BasicEconomyPlugin plugin, AccountManagerImpl accountManager, String host, int port, String password, String channel) {
        this.plugin = plugin;
        this.accountManager = accountManager;
        this.host = host;
        this.port = port;
        this.password = (password == null || password.isEmpty()) ? null : password;
        this.channel = channel;
    }

    @Override
    public void init() throws Exception {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8);
        poolConfig.setMinIdle(2);
        
        if (password != null) {
            jedisPool = new JedisPool(poolConfig, host, port, 2000, password);
        } else {
            jedisPool = new JedisPool(poolConfig, host, port, 2000);
        }

        // Test connection
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.ping();
        }

        pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channelName, String message) {
                try {
                    // Message format: uuid:currency:amount
                    String[] parts = message.split(":", 3);
                    if (parts.length == 3) {
                        UUID uuid = UUID.fromString(parts[0]);
                        String currencyName = parts[1];
                        BigDecimal amount = new BigDecimal(parts[2]);

                        Currency currency = accountManager.getCurrency(currencyName);
                        if (currency != null) {
                            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                                accountManager.handleRemoteBalanceUpdate(uuid, currency, amount);
                            });
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse Redis sync message: " + e.getMessage());
                }
            }
        };

        subscribeThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && !jedisPool.isClosed()) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.subscribe(pubSub, channel);
                } catch (Exception e) {
                    if (!jedisPool.isClosed()) {
                        plugin.getLogger().warning("Redis connection lost. Retrying in 5 seconds...");
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }, "BasicEconomy-Redis-Subscriber");
        subscribeThread.start();
    }

    @Override
    public void shutdown() {
        if (pubSub != null) {
            pubSub.unsubscribe();
        }
        if (subscribeThread != null) {
            subscribeThread.interrupt();
        }
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    @Override
    public void sendUpdate(UUID uuid, Currency currency, BigDecimal amount) {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String message = uuid.toString() + ":" + currency.name() + ":" + amount.toPlainString();
                jedis.publish(channel, message);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to publish update to Redis: " + e.getMessage());
            }
        });
    }
}
