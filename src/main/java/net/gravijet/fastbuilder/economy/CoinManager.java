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
 * <p>Coin value uses a smooth hyperbolic formula based on how the player's run
 * compares to the blended average (65% personal + 35% server-wide):
 * {@code coins = clamp(5, 30, round(15 / ratio))} where
 * {@code ratio = playerTimeMs / averageMs}.</p>
 * <ul>
 *   <li>At average speed      → 15 coins</li>
 *   <li>2× faster than avg    → 30 coins (cap)</li>
 *   <li>2× slower than avg    →  8 coins</li>
 *   <li>3× slower than avg    →  5 coins (floor)</li>
 * </ul>
 * <p>No randomness — identical ratios always produce identical coins.
 * The booster multiplier is applied on top of the base amount.</p>
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
     * <p>The reward uses a smooth hyperbolic formula against a blended average
     * (65% personal + 35% server-wide when enough personal data exists):
     * {@code coins = clamp(5, 30, round(15 / ratio))} where
     * {@code ratio = playerTimeMs / averageMs}.  At average speed the payout is
     * ~15 coins; twice as fast gives the 30-coin cap; three times slower hits the
     * 5-coin floor.  The booster multiplier is applied on top.</p>
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
     * The booster multiplier is applied so all coin sources scale consistently.
     *
     * @return the final coins awarded (after booster), or 0 if not configured
     */
    public int awardFailedRunCoins(Player player) {
        if (!plugin.getConfigManager().isCoinsOnFailed()) return 0;
        // Always 1 base coin; booster can push it to 2 (hard cap).
        double boost = plugin.getBoosterManager().getMultiplier(player);
        int coins = Math.min(2, Math.max(1, (int) Math.round(boost)));
        addCoins(player.getUniqueId(), coins);
        return coins;
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
     * Map a performance ratio (playerTime / blendedAverage) onto a coin reward.
     *
     * <p>Formula: {@code coins = clamp(5, 30, round(15 / ratio))}</p>
     * <ul>
     *   <li>ratio = 0.5 (2× faster) → 30 coins (cap)</li>
     *   <li>ratio = 1.0 (at average) → 15 coins</li>
     *   <li>ratio = 3.0 (3× slower) →  5 coins (floor)</li>
     * </ul>
     * The curve is smooth and continuous — no discrete tiers.
     */
    private int computeTierCoins(long timeMs, double averageMs) {
        double ratio = timeMs / averageMs;
        int raw = (int) Math.round(15.0 / ratio);
        return Math.max(5, Math.min(30, raw));
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
