package net.gravijet.fastbuilder.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.RunSession;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.TimeUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/**
 * PlaceholderAPI expansion exposing FastBuilder data to other plugins
 * (scoreboards, tab lists, chat formats, holograms, ...).
 *
 * <h3>Player placeholders</h3>
 * <ul>
 *   <li>{@code %fastbuilder_coins%}              — coin balance</li>
 *   <li>{@code %fastbuilder_multiplier%}         — effective coin multiplier (e.g. {@code 1}, {@code 1.5}, {@code 2})</li>
 *   <li>{@code %fastbuilder_booster_remaining%}  — remaining temporary-booster time (e.g. {@code 4m 32s}) or {@code Inactive}</li>
 *   <li>{@code %fastbuilder_map%}                — current map name, empty when not playing</li>
 *   <li>{@code %fastbuilder_island%}             — current island slot (1-based), empty when not playing</li>
 *   <li>{@code %fastbuilder_session_best%}       — best time of the current session, formatted</li>
 * </ul>
 *
 * <h3>Per-map statistics</h3>
 * Without a suffix these use the player's current map; append {@code _<map>} to
 * query a specific map (e.g. {@code %fastbuilder_best_classic%}):
 * <ul>
 *   <li>{@code %fastbuilder_best%} / {@code %fastbuilder_best_<map>%}            — personal best, formatted ({@code 12,350})</li>
 *   <li>{@code %fastbuilder_best_ms%} / {@code %fastbuilder_best_ms_<map>%}      — personal best in raw milliseconds</li>
 *   <li>{@code %fastbuilder_attempts%} / {@code %fastbuilder_attempts_<map>%}    — total attempts</li>
 *   <li>{@code %fastbuilder_successes%} / {@code %fastbuilder_successes_<map>%}  — successful attempts</li>
 *   <li>{@code %fastbuilder_winrate%} / {@code %fastbuilder_winrate_<map>%}      — success rate in percent ({@code 87.5})</li>
 *   <li>{@code %fastbuilder_average%} / {@code %fastbuilder_average_<map>%}      — average successful time, formatted</li>
 * </ul>
 *
 * Placeholders resolve from the live cache only, so values are available for
 * online players (and recently disconnected ones still pending unload).
 */
public class FastBuilderExpansion extends PlaceholderExpansion {

    private final FastBuilder plugin;

    public FastBuilderExpansion(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "fastbuilder";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Survive PlaceholderAPI reloads — FastBuilder manages the lifecycle itself. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        if (offline == null || params == null) return "";

        String p = params.toLowerCase(Locale.ROOT);
        UUID uuid = offline.getUniqueId();
        PlayerData data = plugin.getPlayerManager().getCachedData(uuid);

        switch (p) {
            case "coins":
                return data != null ? String.valueOf(data.getCoins()) : "0";

            case "multiplier": {
                Player online = offline.getPlayer();
                if (online == null || plugin.getBoosterManager() == null) return "1";
                return formatMultiplier(plugin.getBoosterManager().getMultiplier(online));
            }

            case "booster_remaining":
                return plugin.getBoosterManager() != null
                        ? plugin.getBoosterManager().formatRemaining(uuid) : "Inactive";

            case "map": {
                RunSession session = getSession(uuid);
                return session != null ? session.getMapName() : "";
            }

            case "island": {
                RunSession session = getSession(uuid);
                return session != null ? String.valueOf(session.getIslandIndex() + 1) : "";
            }

            case "session_best": {
                RunSession session = getSession(uuid);
                if (session == null || session.getSessionBests().isEmpty()) return TimeUtil.EMPTY_RAW;
                long best = Long.MAX_VALUE;
                for (Long t : session.getSessionBests()) {
                    if (t != null && t < best) best = t;
                }
                return best == Long.MAX_VALUE ? TimeUtil.EMPTY_RAW : TimeUtil.formatTimeFull(best);
            }

            default:
                break;
        }

        // Per-map statistics: <stat> uses the current map, <stat>_<map> a specific one.
        if (p.startsWith("best_ms")) return statPlaceholder(uuid, data, p, "best_ms", StatKind.BEST_MS);
        if (p.startsWith("best"))    return statPlaceholder(uuid, data, p, "best", StatKind.BEST);
        if (p.startsWith("attempts"))  return statPlaceholder(uuid, data, p, "attempts", StatKind.ATTEMPTS);
        if (p.startsWith("successes")) return statPlaceholder(uuid, data, p, "successes", StatKind.SUCCESSES);
        if (p.startsWith("winrate"))   return statPlaceholder(uuid, data, p, "winrate", StatKind.WINRATE);
        if (p.startsWith("average"))   return statPlaceholder(uuid, data, p, "average", StatKind.AVERAGE);

        return null; // unknown placeholder — let PlaceholderAPI display it unchanged
    }

    private enum StatKind { BEST, BEST_MS, ATTEMPTS, SUCCESSES, WINRATE, AVERAGE }

    private String statPlaceholder(UUID uuid, PlayerData data, String params, String prefix, StatKind kind) {
        String mapName;
        if (params.length() == prefix.length()) {
            RunSession session = getSession(uuid);
            if (session == null) return defaultValue(kind);
            mapName = session.getMapName();
        } else if (params.charAt(prefix.length()) == '_') {
            mapName = params.substring(prefix.length() + 1);
        } else {
            return null; // e.g. "bestest" — not one of our placeholders
        }
        if (mapName.isEmpty()) return defaultValue(kind);

        PlayerData.MapStats stats = data != null ? data.getStats(mapName) : null;
        if (stats == null) return defaultValue(kind);

        switch (kind) {
            case BEST:      return stats.hasBestTime() ? TimeUtil.formatTimeFull(stats.bestTime) : TimeUtil.EMPTY_RAW;
            case BEST_MS:   return stats.hasBestTime() ? String.valueOf(stats.bestTime) : "-1";
            case ATTEMPTS:  return String.valueOf(stats.totalAttempts);
            case SUCCESSES: return String.valueOf(stats.successfulAttempts);
            case WINRATE: {
                if (stats.totalAttempts <= 0) return "0";
                double rate = stats.successfulAttempts * 100.0 / stats.totalAttempts;
                return trimTrailingZero(String.format(Locale.ROOT, "%.1f", rate));
            }
            case AVERAGE: {
                long avg = stats.getAverageTime();
                return avg > 0 ? TimeUtil.formatTimeFull(avg) : TimeUtil.EMPTY_RAW;
            }
            default: return "";
        }
    }

    private String defaultValue(StatKind kind) {
        switch (kind) {
            case BEST:
            case AVERAGE:  return TimeUtil.EMPTY_RAW;
            case BEST_MS:  return "-1";
            case WINRATE:  return "0";
            default:       return "0";
        }
    }

    private RunSession getSession(UUID uuid) {
        return plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(uuid) : null;
    }

    /** 1.0 → "1", 1.5 → "1.5", 2.0 → "2" */
    private static String formatMultiplier(double mult) {
        if (mult == Math.floor(mult)) return String.valueOf((long) mult);
        return trimTrailingZero(String.format(Locale.ROOT, "%.2f", mult));
    }

    private static String trimTrailingZero(String s) {
        if (s.indexOf('.') < 0) return s;
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
