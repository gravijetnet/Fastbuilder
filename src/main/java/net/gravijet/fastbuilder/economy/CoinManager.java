package net.gravijet.fastbuilder.economy;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.player.PlayerData;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Economy system for coins.
 * Coins are earned via playtime and successful run completions.
 */
public class CoinManager {

    private final FastBuilder plugin;

    public CoinManager(FastBuilder plugin) {
        this.plugin = plugin;
    }

    public int getCoins(UUID uuid) {
        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);
        return data != null ? data.getCoins() : 0;
    }

    public void addCoins(UUID uuid, int amount) {
        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);
        if (data != null) {
            data.addCoins(amount);
        }
    }

    public boolean removeCoins(UUID uuid, int amount) {
        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);
        if (data != null) {
            return data.removeCoins(amount);
        }
        return false;
    }

    public boolean hasEnough(UUID uuid, int amount) {
        return getCoins(uuid) >= amount;
    }

    /**
     * Award coins for a completion based on time.
     */
    public int awardCompletionCoins(Player player, long timeMillis) {
        double base = plugin.getConfigManager().getCoinsPerCompletion();
        // Faster times earn a small bonus (up to 2x for sub-10s runs)
        double timeSeconds = timeMillis / 1000.0;
        double multiplier = Math.max(1.0, 2.0 - (timeSeconds / 30.0));
        int coins = (int) Math.ceil(base * multiplier);

        addCoins(player.getUniqueId(), coins);
        return coins;
    }
}
