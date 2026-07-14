package net.gravijet.fastbuilder.nick;

import net.gravijet.fastbuilder.FastBuilder;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Resolves nicked (disguised) identities via the Phoenix API.
 *
 * <p>Every gameplay-facing surface — NPC skin, session top, island occupant names,
 * holograms, replays, stats — shows the disguised name and skin. Leaderboards are the
 * one exception: they always use the real name so records stay attributable.</p>
 *
 * <p>Phoenix is a soft dependency. When it is absent (or an older/newer API is loaded)
 * every lookup fails closed and the player's real identity is used.</p>
 */
public class NickManager {

    private final FastBuilder plugin;

    /**
     * Whether the pxAPI classes are on the classpath at all. This cannot change at runtime, so it
     * is the only thing worth caching — deliberately keyed off the class, not the plugin's name,
     * which we'd otherwise have to guess.
     */
    private final boolean apiPresent;

    public NickManager(FastBuilder plugin) {
        this.plugin = plugin;

        boolean present;
        try {
            Class.forName("xyz.refinedev.phoenix.Phoenix");
            present = true;
        } catch (Throwable ignored) {
            present = false;
        }
        this.apiPresent = present;

        if (apiPresent) {
            plugin.getLogger().info("Phoenix API found — nicked players will show their disguise "
                    + "everywhere except leaderboards.");
        } else {
            plugin.getLogger().info("Phoenix not installed — nick support disabled.");
        }
    }

    /**
     * Resolved per call rather than snapshotted at startup: if Phoenix enables after us, or its
     * singleton isn't set yet while we're enabling, a cached "unavailable" would silently disable
     * nick support for the whole session.
     */
    private xyz.refinedev.phoenix.Phoenix phoenix() {
        if (!apiPresent) return null;
        try {
            return xyz.refinedev.phoenix.Phoenix.getInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean isAvailable() {
        return phoenix() != null;
    }

    /**
     * Returns the player's active disguise, or null when Phoenix is absent, the profile
     * isn't cached, or the player isn't nicked.
     *
     * <p>Deliberately uses {@code getCachedProfile} rather than {@code getProfile}: this is
     * called from the per-tick scoreboard task, and {@code getProfile} can fall through to a
     * blocking database fetch. Online players are always in the cache.</p>
     */
    private xyz.refinedev.phoenix.profile.disguise.IDisguiseData getDisguise(UUID uuid) {
        if (uuid == null) return null;
        try {
            xyz.refinedev.phoenix.Phoenix phoenix = phoenix();
            if (phoenix == null) return null;
            xyz.refinedev.phoenix.handler.IProfileHandler handler = phoenix.getProfileHandler();
            if (handler == null) return null;
            xyz.refinedev.phoenix.profile.IProfile profile = handler.getCachedProfile(uuid);
            if (profile == null) return null;
            xyz.refinedev.phoenix.profile.disguise.IDisguiseData disguise = profile.getDisguiseData();
            return (disguise != null && disguise.isDisguised()) ? disguise : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** True when the player is currently nicked. */
    public boolean isNicked(UUID uuid) {
        return getDisguise(uuid) != null;
    }

    /** The name to show publicly: the disguise name when nicked, otherwise {@code realName}. */
    public String getDisplayName(UUID uuid, String realName) {
        xyz.refinedev.phoenix.profile.disguise.IDisguiseData disguise = getDisguise(uuid);
        if (disguise == null) return realName;
        String nick = disguise.getDisguiseName();
        return (nick != null && !nick.isEmpty()) ? nick : realName;
    }

    public String getDisplayName(Player player) {
        if (player == null) return null;
        return getDisplayName(player.getUniqueId(), player.getName());
    }

    /**
     * The disguise skin as {@code {value, signature}}, or null when the player isn't nicked
     * or Phoenix has no skin recorded for the disguise.
     */
    public String[] getNickSkin(UUID uuid) {
        xyz.refinedev.phoenix.profile.disguise.IDisguiseData disguise = getDisguise(uuid);
        if (disguise == null) return null;
        try {
            xyz.refinedev.phoenix.profile.disguise.ISkin skin = disguise.getDisguiseSkin();
            if (skin == null) return null;
            String value = skin.getValue();
            if (value == null || value.isEmpty()) return null;
            String signature = skin.getSignature();
            return new String[]{value, signature != null ? signature : ""};
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * The skin to render for this player as {@code {value, signature}}: the disguise skin when
     * nicked, otherwise the real skin from {@link net.gravijet.fastbuilder.skin.SkinManager}'s
     * cache. Null when neither is known.
     */
    public String[] getEffectiveSkin(UUID uuid) {
        String[] nickSkin = getNickSkin(uuid);
        if (nickSkin != null) return nickSkin;

        net.gravijet.fastbuilder.skin.SkinManager skins = plugin.getSkinManager();
        if (skins == null) return null;
        String value = skins.getSkinValue(uuid);
        if (value == null || value.isEmpty()) return null;
        String signature = skins.getSkinSignature(uuid);
        return new String[]{value, signature != null ? signature : ""};
    }
}
