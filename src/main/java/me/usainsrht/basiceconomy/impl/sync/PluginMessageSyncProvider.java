package me.usainsrht.basiceconomy.impl.sync;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.usainsrht.basiceconomy.api.Currency;
import me.usainsrht.basiceconomy.impl.BasicEconomyPlugin;
import me.usainsrht.basiceconomy.impl.account.AccountManagerImpl;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.math.BigDecimal;
import java.util.UUID;

public class PluginMessageSyncProvider implements SyncProvider, PluginMessageListener {

    private final BasicEconomyPlugin plugin;
    private final AccountManagerImpl accountManager;
    private static final String CHANNEL = "basiceconomy:sync";

    public PluginMessageSyncProvider(BasicEconomyPlugin plugin, AccountManagerImpl accountManager) {
        this.plugin = plugin;
        this.accountManager = accountManager;
    }

    @Override
    public void init() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    @Override
    public void shutdown() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
    }

    @Override
    public void sendUpdate(UUID uuid, Currency currency, BigDecimal amount) {
        Player player = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (player == null) {
            return; // No player online to relay the message. Offline players don't have caches on other servers to invalidate.
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(uuid.toString());
        out.writeUTF(currency.name());
        out.writeUTF(amount.toPlainString());

        player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL)) {
            return;
        }

        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            UUID uuid = UUID.fromString(in.readUTF());
            String currencyName = in.readUTF();
            BigDecimal amount = new BigDecimal(in.readUTF());

            Currency currency = accountManager.getCurrency(currencyName);
            if (currency != null) {
                // Ensure we run this sync or async properly.
                // It's safe to delegate to the scheduler.
                Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                    accountManager.handleRemoteBalanceUpdate(uuid, currency, amount);
                });
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse plugin message for balance sync: " + e.getMessage());
        }
    }
}
