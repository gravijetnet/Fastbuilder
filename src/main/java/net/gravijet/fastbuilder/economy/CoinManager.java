package net.gravijet.fastbuilder.economy;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Economy system for coins.
 *
 * <p><b>Completion rewards</b> use exponential decay: the faster the run, the
 * more coins — up to a hard cap of 20 per run (before boosters).  A ±30 % jitter
 * makes two identical times feel different.</p>
 *
 * <p><b>Playtime rewards</b> fire on a configurable interval (default 900 s /
 * 15 minutes).  Amount is proportional to the actual elapsed interval so the
 * long-run hourly average stays constant regardless of window size.</p>
 *
 * <p>All coin awards pass through {@link BoosterManager} so permanent and
 * temporary boosters are always applied consistently.</p>
 */
public class CoinManager {

    private final FastBuilder plugin;
    private static final Random RANDOM = new Random();

    /** Hard ceiling on base completion coins before the booster is applied. */
    private static final int COMPLETION_BASE_CAP = 20;

    /** Half-life (seconds) for the completion-reward decay curve. */
    private static final double DECAY_HALF_LIFE = 20.0;

    /** Jitter fraction applied to every coin award (±30 %). */
    private static final double JITTER = 0.30;

    private int playtimeTaskId = -1;

    private final Map<UUID, Integer> elapsedSeconds   = new HashMap<>();
    private final Map<UUID, Integer> targetIntervals  = new HashMap<>();

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
     * Award coins for finishing a run.
     *
     * <p>Base coins use an exponential decay over run time with a ±30 % jitter,
     * capped at {@value #COMPLETION_BASE_CAP} before the booster multiplier.
     * The booster can push the final amount beyond the cap — that's intentional.</p>
     *
     * @param player     the player who finished
     * @param timeMillis run time in milliseconds
     * @return the actual coins awarded (after booster)
     */
    public int awardCompletionCoins(Player player, long timeMillis) {
        double timeSeconds = timeMillis / 1000.0;

        // Exponential decay: at t=0 → baseMax coins; halves every DECAY_HALF_LIFE seconds.
        double baseMax = plugin.getConfigManager().getCoinsPerCompletion() * 2.0;
        double decayFactor = Math.exp(-timeSeconds * Math.log(2.0) / DECAY_HALF_LIFE);
        double mean = Math.max(1.0, baseMax * decayFactor);

        // ±JITTER variance so repeated runs don't always give the same number
        double lo = mean * (1.0 - JITTER);
        double hi = mean * (1.0 + JITTER);
        int base = (int) Math.round(lo + RANDOM.nextDouble() * (hi - lo));
        int capped = Math.max(1, Math.min(COMPLETION_BASE_CAP, base));

        // Apply booster multiplier (can exceed the cap — that's the point of boosters)
        double boost = plugin.getBoosterManager().getMultiplier(player);
        int coins = (int) Math.round(capped * boost);

        addCoins(player.getUniqueId(), coins);
        return coins;
    }

    /** Reset playtime counters on disconnect so expired sessions don't carry over. */
    public void resetPlaytime(UUID uuid) {
        elapsedSeconds.remove(uuid);
        targetIntervals.remove(uuid);
    }

    public void shutdown() {
        if (playtimeTaskId != -1) {
            Bukkit.getScheduler().cancelTask(playtimeTaskId);
        }
        elapsedSeconds.clear();
        targetIntervals.clear();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Pick the next drop interval for a player using the configured min/max range.
     * When min == max the drop is perfectly predictable; a spread adds surprise.
     */
    private int pickRandomInterval() {
        int min = plugin.getConfigManager().getCoinsDropIntervalMin();
        int max = plugin.getConfigManager().getCoinsDropIntervalMax();
        if (min >= max) return Math.max(1, min);
        return min + RANDOM.nextInt(max - min + 1);
    }

    /**
     * Coins for a playtime drop that fired after {@code intervalSeconds}.
     *
     * <p>Scaled proportionally to the actual interval so the long-run hourly
     * average converges to {@code coins-per-hour} regardless of window size.
     * A ±{@value #JITTER} jitter is applied on top.</p>
     */
    private int computeDropAmount(int intervalSeconds) {
        double coinsPerHour = plugin.getConfigManager().getCoinsPerHour();
        if (coinsPerHour <= 0) return 0;
        double base   = coinsPerHour * intervalSeconds / 3600.0;
        double jitter = 1.0 - JITTER + RANDOM.nextDouble() * JITTER * 2.0;
        return Math.max(1, (int) Math.round(base * jitter));
    }

    /**
     * Per-second ticker that accumulates playtime and fires a coin drop once the
     * player's random target interval elapses.  Only ticks players who have an
     * active gameplay session.
     */
    private void startPlaytimeTask() {
        if (plugin.getConfigManager().getCoinsPerHour() <= 0) return;

        playtimeTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (plugin.getGameplayManager() == null) continue;
                    if (plugin.getGameplayManager().getSession(player.getUniqueId()) == null) continue;

                    UUID uuid    = player.getUniqueId();
                    int elapsed  = elapsedSeconds.getOrDefault(uuid, 0) + 1;
                    int target   = targetIntervals.computeIfAbsent(uuid, k -> pickRandomInterval());

                    if (elapsed >= target) {
                        int base   = computeDropAmount(target);
                        double mult = plugin.getBoosterManager().getMultiplier(player);
                        int coins  = (int) Math.round(base * mult);

                        addCoins(uuid, coins);
                        elapsedSeconds.put(uuid, 0);
                        targetIntervals.put(uuid, pickRandomInterval());

                        String msg = plugin.getConfigManager().getMessage("playtime-reward");
                        if (msg == null || msg.isEmpty()) {
                            msg = "%prefix%&a+%coins% coins &7(playtime reward).";
                        }
                        if (mult > 1.0) {
                            // Show the multiplier to reinforce that the booster is working
                            msg = msg + " &6[" + formatMultiplier(mult) + " booster]";
                        }
                        msg = msg.replace("%coins%", String.valueOf(coins))
                                 .replace("%prefix%", plugin.getConfigManager().getPrefix());
                        player.sendMessage(ColorUtil.translate(msg));
                    } else {
                        elapsedSeconds.put(uuid, elapsed);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();
    }

    /** Format a multiplier like 1.5 → "1.5x", 2.0 → "2x". */
    private static String formatMultiplier(double mult) {
        if (mult == Math.floor(mult)) return (int) mult + "x";
        return String.format("%.1fx", mult);
    }
}
