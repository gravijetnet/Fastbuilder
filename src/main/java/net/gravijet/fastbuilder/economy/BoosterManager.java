package net.gravijet.fastbuilder.economy;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.player.PlayerData;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Tracks coin boosters — permanent (permission-based) and
 * temporary (purchased in the shop or granted via command).
 *
 * Temporary boosters are stored in PlayerData so they survive restarts.
 * Permanent boosters are read live from the player's permission set.
 *
 * Multiplier resolution: the highest active value wins.
 */
public class BoosterManager {

    private final FastBuilder plugin;

    private static final String PERM_1_5X = "fastbuilder.booster.1_5x";
    private static final String PERM_2X   = "fastbuilder.booster.2x";
    private static final String PERM_3X   = "fastbuilder.booster.3x";

    public BoosterManager(FastBuilder plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the active coin multiplier for a player.
     * Takes the maximum of their permanent (permission) and temporary (timed) booster.
     */
    public double getMultiplier(Player player) {
        double perm = getPermanentMultiplier(player);
        double temp = getTemporaryMultiplier(player.getUniqueId());
        return Math.max(1.0, Math.max(perm, temp));
    }

    /**
     * Activate a temporary booster for a player.
     * If the player already has an active booster of the same tier, the new
     * duration stacks on top.  A stronger booster always replaces a weaker one.
     *
     * @param uuid       target player UUID
     * @param multiplier coin multiplier (e.g. 1.5, 2.0, 3.0)
     * @param durationMs duration in milliseconds
     */
    public void activateBooster(UUID uuid, double multiplier, long durationMs) {
        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);
        if (data == null) return;

        long now           = System.currentTimeMillis();
        long currentExpiry = data.getBoosterExpiry();
        double currentMult = data.getBoosterMultiplier();
        boolean hasActive  = currentExpiry > now;

        if (hasActive && Math.abs(currentMult - multiplier) < 0.01) {
            // Same tier — stack the duration
            data.setBoosterExpiry(currentExpiry + durationMs);
        } else if (hasActive && currentMult > multiplier) {
            // Existing booster is stronger — only extend it slightly as a courtesy
            data.setBoosterExpiry(currentExpiry + durationMs);
        } else {
            // New booster is stronger, or nothing active
            data.setBoosterExpiry(now + durationMs);
            data.setBoosterMultiplier(multiplier);
        }

        plugin.getPlayerManager().savePlayerData(uuid);
    }

    /** Remaining milliseconds on the player's temporary booster, or 0 if none. */
    public long getRemainingMs(UUID uuid) {
        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);
        if (data == null) return 0;
        return Math.max(0, data.getBoosterExpiry() - System.currentTimeMillis());
    }

    /**
     * Human-readable remaining time string, e.g. "4m 32s" or "Inactive".
     */
    public String formatRemaining(UUID uuid) {
        long ms = getRemainingMs(uuid);
        if (ms <= 0) return "Inactive";
        long secs = ms / 1000;
        long hrs  = secs / 3600;
        long mins = (secs % 3600) / 60;
        secs = secs % 60;
        if (hrs > 0)  return hrs  + "h " + mins + "m";
        if (mins > 0) return mins + "m " + secs + "s";
        return secs + "s";
    }

    /** Whether the player currently has any active booster (perm or temp). */
    public boolean hasActiveBooster(Player player) {
        return getMultiplier(player) > 1.0;
    }

    // -------------------------------------------------------------------------

    private double getPermanentMultiplier(Player player) {
        if (player.hasPermission(PERM_3X))   return 3.0;
        if (player.hasPermission(PERM_2X))   return 2.0;
        if (player.hasPermission(PERM_1_5X)) return 1.5;
        return 1.0;
    }

    private double getTemporaryMultiplier(UUID uuid) {
        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);
        if (data == null) return 1.0;
        if (data.getBoosterExpiry() > System.currentTimeMillis()) {
            return Math.max(1.0, data.getBoosterMultiplier());
        }
        return 1.0;
    }
}
