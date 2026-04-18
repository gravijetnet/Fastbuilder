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
 *
 * <h3>Completion rewards</h3>
 * <p>Coin value is determined by how the player's run time compares to the
 * server-wide average for that map.  No randomness — identical ratios always
 * produce identical coins.  The five tiers are:</p>
 * <ul>
 *   <li>Elite (&lt; 70 % of average)  → <b>20 coins</b></li>
 *   <li>Very fast (70–85 %)           → 17–19 coins</li>
 *   <li>Normal (85–105 %)             → 10–16 coins</li>
 *   <li>Slow (105–140 %)              → 5–9 coins</li>
 *   <li>Very slow (&gt; 140 %)        → 1–4 coins</li>
 * </ul>
 * <p>The server-wide average is tracked in memory.  Until enough samples have
 * been collected ({@code coins-average-min-samples} in config), a configurable
 * fallback average is used so new servers start with sensible payouts.</p>
 *
 * <h3>Playtime rewards</h3>
 * <p>Fires on a configurable interval (default 900 s / 15 minutes).  Amount is
 * proportional to the actual elapsed interval so the long-run hourly average
 * stays constant regardless of window size.</p>
 *
 * <h3>Hourly XP award</h3>
 * <p>Once per hour of active play, dispatches
 * {@code /adminexp give <player> 10 Fastbuilder} via console so the external
 * rank/XP plugin handles the grant.  The AdminExp command is intentionally
 * absent from this plugin.</p>
 */
public class CoinManager {

    private final FastBuilder plugin;

    private int playtimeTaskId = -1;

    // Playtime coin-drop tracking (per player)
    private final Map<UUID, Integer> elapsedSeconds  = new HashMap<>();
    private final Map<UUID, Integer> targetIntervals = new HashMap<>();

    // Hourly XP tracking — seconds of active play since last adminexp dispatch
    private final Map<UUID, Integer> expElapsedSeconds = new HashMap<>();

    // Server-wide average completion time per map (accumulated in memory).
    // long[0] = total milliseconds, long[1] = sample count.
    private final Map<String, long[]> mapAverageData = new HashMap<>();

    public CoinManager(FastBuilder plugin) {
        this.plugin = plugin;
        startPlaytimeTask();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public int getCoins(UUID uuid) {
        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);
        return data != null ? data.getCoins() : 0;
    }

    public void addCoins(UUID uuid, int amount) {
        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);
        if (data != null) data.addCoins(amount);
    }

    public boolean removeCoins(UUID uuid, int amount) {
        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);
        return data != null && data.removeCoins(amount);
    }

    public boolean hasEnough(UUID uuid, int amount) {
        return getCoins(uuid) >= amount;
    }

    /**
     * Award coins for a successfully finished run.
     *
     * <p>The tier is determined by comparing the player's time against a blended
     * average: 65% personal average (when enough data exists) + 35% server-wide
     * average. This rewards players relative to their own skill level while still
     * anchoring to the server norm.</p>
     *
     * <p>Coin tiers (based on ratio = playerTime / blendedAverage):</p>
     * <ul>
     *   <li>ratio &lt; 0.70 → 20 (elite)</li>
     *   <li>0.70–0.85       → 17–19 (very fast)</li>
     *   <li>0.85–1.05       → 10–16 (normal)</li>
     *   <li>1.05–1.40       → 5–9   (slow)</li>
     *   <li>&gt; 1.40       → 1–4   (very slow)</li>
     * </ul>
     *
     * <p>The booster multiplier is applied after the tier is computed.
     * Base coins are capped at 20; boosters can push the total above that cap.</p>
     *
     * @param player     the player who finished
     * @param timeMillis run duration in milliseconds
     * @param mapName    name of the map (used for average tracking)
     * @return the final coins awarded (after booster)
     */
    public int awardCompletionCoins(Player player, long timeMillis, String mapName) {
        // Update the server-wide rolling average for this map
        long[] mapStats = mapAverageData.computeIfAbsent(mapName, k -> new long[]{0L, 0L});
        mapStats[0] += timeMillis;
        mapStats[1]++;

        // Blend server-wide and personal averages so coin rewards are relative to the
        // player's own performance history. Personal average is weighted more heavily
        // when enough samples exist (threshold: coins-average-min-samples in config).
        double serverMs   = resolveServerAverage(mapName);
        double personalMs = resolvePersonalAverage(player.getUniqueId(), mapName);
        double averageMs  = personalMs > 0
                ? personalMs * 0.65 + serverMs * 0.35
                : serverMs;

        int base = computeTierCoins(timeMillis, averageMs); // base is at most 20

        double boost = plugin.getBoosterManager().getMultiplier(player);
        int coins = (int) Math.round(base * boost);

        addCoins(player.getUniqueId(), coins);
        return coins;
    }

    /**
     * Returns the base (pre-booster) coin amount for the given run time against the
     * blended average.  Used by callers that need to display the breakdown.
     */
    public int computeBaseCoins(java.util.UUID uuid, long timeMillis, String mapName) {
        double serverMs   = resolveServerAverage(mapName);
        double personalMs = resolvePersonalAverage(uuid, mapName);
        double averageMs  = personalMs > 0
                ? personalMs * 0.65 + serverMs * 0.35
                : serverMs;
        return computeTierCoins(timeMillis, averageMs);
    }

    /**
     * Award a small consolation amount for a failed run (fall/out-of-bounds reset).
     * Only called when {@code coins-on-failed} is true in config.
     *
     * @return the coins awarded, or 0 if not configured
     */
    public int awardFailedRunCoins(Player player) {
        int amount = plugin.getConfigManager().getCoinsOnFailedAmount();
        if (amount <= 0) return 0;
        addCoins(player.getUniqueId(), amount);
        return amount;
    }

    /** Reset playtime and EXP counters on disconnect. */
    public void resetPlaytime(UUID uuid) {
        elapsedSeconds.remove(uuid);
        targetIntervals.remove(uuid);
        expElapsedSeconds.remove(uuid);
    }

    public void shutdown() {
        if (playtimeTaskId != -1) {
            Bukkit.getScheduler().cancelTask(playtimeTaskId);
        }
        elapsedSeconds.clear();
        targetIntervals.clear();
        expElapsedSeconds.clear();
    }

    // -------------------------------------------------------------------------
    // Coin tier logic
    // -------------------------------------------------------------------------

    /**
     * Resolve the server-wide average for a map.
     * Falls back to the configured default until enough samples accumulate.
     */
    private double resolveServerAverage(String mapName) {
        long[] stats = mapAverageData.get(mapName);
        int minSamples = plugin.getConfigManager().getCoinsAverageMinSamples();
        if (stats == null || stats[1] < minSamples) {
            return plugin.getConfigManager().getCoinsFallbackAverageSeconds() * 1000.0;
        }
        return (double) stats[0] / stats[1];
    }

    /**
     * Resolve the player's personal average for a map.
     * Returns 0 if fewer than {@code coins-average-min-samples} successful runs exist.
     */
    private double resolvePersonalAverage(java.util.UUID uuid, String mapName) {
        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);
        if (data == null) return 0;
        PlayerData.MapStats pStats = data.getAllStats().get(mapName);
        int minSamples = plugin.getConfigManager().getCoinsAverageMinSamples();
        if (pStats == null || pStats.successfulAttempts < minSamples) return 0;
        return (double) pStats.totalSuccessTime / pStats.successfulAttempts;
    }

    /**
     * Map a performance ratio (player time / server average) onto a coin reward.
     *
     * <pre>
     * ratio &lt; 0.70  → 20 (elite)
     * 0.70–0.85     → 17–19 (very fast, linear interpolation)
     * 0.85–1.05     → 10–16 (normal, linear interpolation)
     * 1.05–1.40     → 5–9  (slow, linear interpolation)
     * &gt; 1.40       → 1–4  (very slow, linear interpolation, floor at 1)
     * </pre>
     */
    private int computeTierCoins(long timeMs, double averageMs) {
        double ratio = timeMs / averageMs;

        if (ratio < 0.70) {
            return 20;
        } else if (ratio < 0.85) {
            double t = (ratio - 0.70) / (0.85 - 0.70); // 0→1 across this band
            return (int) Math.round(19 - t * 2);         // 19 → 17
        } else if (ratio < 1.05) {
            double t = (ratio - 0.85) / (1.05 - 0.85);
            return (int) Math.round(16 - t * 6);          // 16 → 10
        } else if (ratio < 1.40) {
            double t = (ratio - 1.05) / (1.40 - 1.05);
            return (int) Math.round(9 - t * 4);           // 9 → 5
        } else {
            double t = Math.min(1.0, (ratio - 1.40) / (1.80 - 1.40));
            return Math.max(1, (int) Math.round(4 - t * 3)); // 4 → 1
        }
    }

    // -------------------------------------------------------------------------
    // Playtime task
    // -------------------------------------------------------------------------

    private int pickRandomInterval() {
        int min = plugin.getConfigManager().getCoinsDropIntervalMin();
        int max = plugin.getConfigManager().getCoinsDropIntervalMax();
        if (min >= max) return Math.max(1, min);
        // Simple deterministic spread — no randomness needed here since the
        // interval is internal and doesn't affect balance meaningfully
        return min + (int) ((System.currentTimeMillis() % (max - min + 1)));
    }

    private int computeDropAmount(int intervalSeconds) {
        double coinsPerHour = plugin.getConfigManager().getCoinsPerHour();
        if (coinsPerHour <= 0) return 0;
        return Math.max(1, (int) Math.round(coinsPerHour * intervalSeconds / 3600.0));
    }

    /**
     * Per-second ticker.  Handles both the playtime coin drop and the hourly
     * XP dispatch (via console {@code /adminexp give}).
     *
     * <p>Only ticks players who have an active gameplay session so AFK players
     * parked in the lobby do not accumulate rewards.</p>
     */
    private void startPlaytimeTask() {
        if (plugin.getConfigManager().getCoinsPerHour() <= 0) return;

        playtimeTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (plugin.getGameplayManager() == null) continue;
                    if (plugin.getGameplayManager().getSession(player.getUniqueId()) == null) continue;

                    UUID uuid = player.getUniqueId();

                    // --- Coin drop ---
                    int elapsed = elapsedSeconds.getOrDefault(uuid, 0) + 1;
                    int target  = targetIntervals.computeIfAbsent(uuid, k -> pickRandomInterval());

                    if (elapsed >= target) {
                        int base  = computeDropAmount(target);
                        double mult = plugin.getBoosterManager().getMultiplier(player);
                        int coins = (int) Math.round(base * mult);

                        addCoins(uuid, coins);
                        elapsedSeconds.put(uuid, 0);
                        targetIntervals.put(uuid, pickRandomInterval());

                        String msg = plugin.getConfigManager().getMessage("playtime-reward");
                        if (msg == null || msg.isEmpty()) {
                            msg = "%prefix%&a+%coins% coins &7(playtime reward).";
                        }
                        if (mult > 1.0) {
                            msg = msg + " &6[" + formatMult(mult) + " booster]";
                        }
                        msg = msg.replace("%coins%", String.valueOf(coins))
                                 .replace("%prefix%", plugin.getConfigManager().getPrefix());
                        player.sendMessage(ColorUtil.translate(msg));
                    } else {
                        elapsedSeconds.put(uuid, elapsed);
                    }

                    // --- Hourly XP dispatch (delegates to external /adminexp plugin) ---
                    int expElapsed = expElapsedSeconds.getOrDefault(uuid, 0) + 1;
                    if (expElapsed >= 3600) {
                        expElapsedSeconds.put(uuid, 0);
                        final String name = player.getName();
                        // Run on next tick so we're not blocking the scheduler body
                        Bukkit.getScheduler().runTask(plugin, () ->
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                "adminexp give " + name + " 10 Fastbuilder"
                            )
                        );
                    } else {
                        expElapsedSeconds.put(uuid, expElapsed);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();
    }

    private static String formatMult(double mult) {
        if (mult == Math.floor(mult)) return (int) mult + "x";
        return String.format("%.1fx", mult);
    }
}
