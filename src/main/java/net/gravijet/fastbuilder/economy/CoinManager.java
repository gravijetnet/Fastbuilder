package net.gravijet.fastbuilder.economy;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Economy system for coins.
 * Coins are earned via playtime (150 coins/hour) and successful run completions.
 */
public class CoinManager {

    private final FastBuilder plugin;
    private int playtimeTaskId = -1;

    // Tracks seconds of active playtime per player this session
    private final Map<UUID, Integer> playtimeSeconds = new HashMap<>();

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
     * Faster completions earn a higher multiplier (up to 2x for very fast runs).
     */
    public int awardCompletionCoins(Player player, long timeMillis) {
        double base = plugin.getConfigManager().getCoinsPerCompletion();
        double timeSeconds = timeMillis / 1000.0;
        double multiplier = Math.max(1.0, 2.0 - (timeSeconds / 30.0));
        int coins = (int) Math.ceil(base * multiplier);

        addCoins(player.getUniqueId(), coins);
        return coins;
    }

    /**
     * Start the playtime coin reward task.
     * Awards 150 coins per hour of active playtime (player must have a gameplay session).
     * Sends a chat message when the reward is given.
     */
    private void startPlaytimeTask() {
        int coinsPerHour = plugin.getConfigManager().getCoinsPerHour();
        if (coinsPerHour <= 0) return;

        // Run every 20 ticks (1 second), track seconds per player
        playtimeTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (plugin.getGameplayManager() == null) continue;
                    if (plugin.getGameplayManager().getSession(player.getUniqueId()) == null) continue;

                    UUID uuid = player.getUniqueId();
                    int seconds = playtimeSeconds.getOrDefault(uuid, 0) + 1;

                    if (seconds >= 3600) {
                        // One full hour elapsed — award coins
                        addCoins(uuid, coinsPerHour);
                        playtimeSeconds.put(uuid, 0);

                        String msg = plugin.getConfigManager().getMessage("playtime-reward");
                        if (msg == null || msg.isEmpty()) {
                            msg = "%prefix%&a+%coins% coins &7(hourly playtime reward).";
                        }
                        msg = msg.replace("%coins%", String.valueOf(coinsPerHour))
                                .replace("%prefix%", plugin.getConfigManager().getPrefix());
                        player.sendMessage(ColorUtil.translate(msg));
                    } else {
                        playtimeSeconds.put(uuid, seconds);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();
    }

    /** Reset playtime counter when a player disconnects. */
    public void resetPlaytime(UUID uuid) {
        playtimeSeconds.remove(uuid);
    }

    public void shutdown() {
        if (playtimeTaskId != -1) {
            Bukkit.getScheduler().cancelTask(playtimeTaskId);
        }
        playtimeSeconds.clear();
    }
}
