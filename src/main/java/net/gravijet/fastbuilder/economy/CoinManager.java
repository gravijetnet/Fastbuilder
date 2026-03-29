package net.gravijet.fastbuilder.economy;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Economy system for coins.
 * Coins are earned via playtime and successful run completions.
 */
public class CoinManager {

    private final FastBuilder plugin;
    private int playtimeTaskId = -1;

    public CoinManager(FastBuilder plugin) {
        this.plugin = plugin;
        startPlaytimeTask();
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

    /**
     * Start the playtime coin reward task.
     * Awards coins-per-second to all online players with an active gameplay session.
     */
    private void startPlaytimeTask() {
        double coinsPerSecond = plugin.getConfigManager().getCoinsPerSecond();
        if (coinsPerSecond <= 0) return;

        // Run every 20 ticks (1 second)
        playtimeTaskId = new BukkitRunnable() {
            private double accumulator = 0;

            @Override
            public void run() {
                accumulator += coinsPerSecond;
                if (accumulator >= 1.0) {
                    int coinsToAdd = (int) accumulator;
                    accumulator -= coinsToAdd;

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        // Only award if the player has a gameplay session
                        if (plugin.getGameplayManager() != null
                                && plugin.getGameplayManager().getSession(player.getUniqueId()) != null) {
                            addCoins(player.getUniqueId(), coinsToAdd);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();
    }

    public void shutdown() {
        if (playtimeTaskId != -1) {
            Bukkit.getScheduler().cancelTask(playtimeTaskId);
        }
    }
}
