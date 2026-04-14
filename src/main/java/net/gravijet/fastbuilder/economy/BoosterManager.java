package net.gravijet.fastbuilder.economy;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.player.PlayerData;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Manages coin boosters — permanent (permission-based) and temporary (purchased/granted).
 *
 * <p><b>Active booster rule:</b> a player may only have one active temporary booster at a
 * time.  Activating another while one is running is blocked; the player must wait for the
 * current booster to expire before activating a new one.</p>
 *
 * <p><b>Multiplier resolution:</b> at award time the higher of the permanent permission
 * multiplier and the active temporary multiplier is used.</p>
 */
public class BoosterManager {

    private final FastBuilder plugin;

    private static final String PERM_1_5X = "fastbuilder.booster.1_5x";
    private static final String PERM_2X   = "fastbuilder.booster.2x";
    private static final String PERM_3X   = "fastbuilder.booster.3x";

    public BoosterManager(FastBuilder plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Multiplier resolution
    // -------------------------------------------------------------------------

    /**
     * Returns the effective coin multiplier for a player.
     * Takes the higher of their permanent (permission) and temporary (timed) booster.
     * Always at least 1.0.
     */
    public double getMultiplier(Player player) {
        double perm = getPermanentMultiplier(player);
        double temp = getTemporaryMultiplier(player.getUniqueId());
        return Math.max(1.0, Math.max(perm, temp));
    }

    // -------------------------------------------------------------------------
    // Temporary booster activation
    // -------------------------------------------------------------------------

    /**
     * Attempt to activate a booster from the player's inventory.
     * Fails silently if the player already has an active booster — callers should
     * check {@link #canActivate(UUID)} first and show the appropriate message.
     *
     * @return true if the booster was successfully activated
     */
    public boolean activateBooster(Player player, String typeId) {
        UUID uuid = player.getUniqueId();

        if (hasActiveTemporaryBooster(uuid)) return false;

        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);
        if (data == null) return false;

        BoosterType type = plugin.getConfigManager().getBoosterType(typeId);
        if (type == null) return false;

        if (!data.consumeBooster(typeId)) return false;

        long durationMs = (long) type.durationMinutes * 60_000L;
        data.setBoosterExpiry(System.currentTimeMillis() + durationMs);
        data.setBoosterMultiplier(type.multiplier);

        plugin.getPlayerManager().savePlayerData(uuid);
        return true;
    }

    /**
     * Whether the player currently has no active temporary booster and may activate one.
     */
    public boolean canActivate(UUID uuid) {
        return !hasActiveTemporaryBooster(uuid);
    }

    /** True if the player has a temporary booster that has not yet expired. */
    public boolean hasActiveTemporaryBooster(UUID uuid) {
        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);
        if (data == null) return false;
        return data.getBoosterExpiry() > System.currentTimeMillis();
    }

    // -------------------------------------------------------------------------
    // Admin inventory management
    // -------------------------------------------------------------------------

    /**
     * Add boosters directly to a player's inventory without charging coins.
     * Safe to call even when the player has an active booster (the new items just sit in inventory).
     */
    public void giveBooster(UUID uuid, String typeId, int amount) {
        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);
        if (data == null) return;
        data.addBooster(typeId, amount);
        plugin.getPlayerManager().savePlayerData(uuid);
    }

    /**
     * Remove boosters from the player's inventory (admin correction).
     *
     * @return how many were actually removed (may be less than {@code amount} if not enough owned)
     */
    public int takeBooster(UUID uuid, String typeId, int amount) {
        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);
        if (data == null) return 0;
        int owned = data.getBoosterCount(typeId);
        int removing = Math.min(owned, amount);
        for (int i = 0; i < removing; i++) data.consumeBooster(typeId);
        if (removing > 0) plugin.getPlayerManager().savePlayerData(uuid);
        return removing;
    }

    /**
     * Forcibly clear the player's active temporary booster immediately.
     */
    public void clearActiveBooster(UUID uuid) {
        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);
        if (data == null) return;
        data.setBoosterExpiry(0);
        data.setBoosterMultiplier(1.0);
        plugin.getPlayerManager().savePlayerData(uuid);
    }

    // -------------------------------------------------------------------------
    // Remaining time helpers
    // -------------------------------------------------------------------------

    /** Remaining milliseconds on the active temporary booster, or 0 if none. */
    public long getRemainingMs(UUID uuid) {
        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);
        if (data == null) return 0;
        return Math.max(0, data.getBoosterExpiry() - System.currentTimeMillis());
    }

    /** Human-readable remaining time, e.g. "4m 32s" or "Inactive". */
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

    /** Whether the player currently has any multiplier active (perm or temp). */
    public boolean hasActiveBooster(Player player) {
        return getMultiplier(player) > 1.0;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
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
