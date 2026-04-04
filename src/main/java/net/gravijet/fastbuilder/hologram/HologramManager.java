package net.gravijet.fastbuilder.hologram;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.TimeUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages DecentHolograms for per-island player stats display.
 * Each island has a hologram showing the occupant's stats.
 */
public class HologramManager {

    private final FastBuilder plugin;
    // Key: "mapName:islandIndex" -> hologram ID currently tracked
    private final Map<String, String> holograms = new HashMap<>();

    public HologramManager(FastBuilder plugin) {
        this.plugin = plugin;
    }

    public void updateHologram(String mapName, int islandIndex, Player player) {
        if (!plugin.getConfigManager().isHologramsEnabled()) return;

        MapData map = plugin.getMapManager().getMap(mapName);
        if (map == null) return;

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        updateHologram(map, islandIndex, data);
    }

    public void updateHologram(MapData map, int islandIndex, PlayerData playerData) {
        if (!plugin.getConfigManager().isHologramsEnabled()) return;

        String key = map.getName().toLowerCase() + ":" + islandIndex;
        String holoId = "fb_" + map.getName().toLowerCase() + "_" + islandIndex;

        try {
            // Always attempt to remove by the deterministic ID first to prevent duplication.
            // This handles cases where a previous session left a stale hologram.
            try {
                eu.decentsoftware.holograms.api.DHAPI.removeHologram(holoId);
            } catch (Exception ignored) {}

            // Also remove any previously tracked hologram under a different key (safety net)
            String existingId = holograms.get(key);
            if (existingId != null && !existingId.equals(holoId)) {
                try {
                    eu.decentsoftware.holograms.api.DHAPI.removeHologram(existingId);
                } catch (Exception ignored) {}
            }

            // Build content lines
            List<String> configLines = plugin.getConfigManager().getHologramLines();
            List<String> lines = new ArrayList<>();

            PlayerData.MapStats stats = playerData.getStats(map.getName());
            String pb = (stats != null && stats.hasBestTime()) ? TimeUtil.formatTime(stats.bestTime) : "N/A";
            int successful = stats != null ? stats.successfulAttempts : 0;
            int total = stats != null ? stats.totalAttempts : 0;

            // Calculate top percentile across all online players on this map
            String topPercent = calculateTopPercent(map.getName(),
                    stats != null && stats.hasBestTime() ? stats.bestTime : -1);

            for (String line : configLines) {
                line = line.replace("%player%", playerData.getName())
                        .replace("%pb%", pb)
                        .replace("%successful_attempts%", String.valueOf(successful))
                        .replace("%total_attempts%", String.valueOf(total))
                        .replace("%top_percent%", topPercent);
                lines.add(ColorUtil.translate(line));
            }

            // Use the configured hologram location for this island
            Location holoLoc = map.getIslandHologramLocation(islandIndex);

            eu.decentsoftware.holograms.api.DHAPI.createHologram(holoId, holoLoc, false, lines);
            holograms.put(key, holoId);

        } catch (NoClassDefFoundError | Exception e) {
            plugin.getLogger().warning("DecentHolograms API not available: " + e.getMessage());
        }
    }

    /**
     * Calculate the player's rank percentile among ALL players who have ever completed this map
     * (loaded from disk, with 60-second cache).
     * "Top X%" = the player is in the top X% fastest players (lower = better rank).
     * Returns "" if fewer than 2 players have a recorded time.
     */
    private String calculateTopPercent(String mapName, long playerBestTime) {
        if (playerBestTime <= 0) return "";

        long[] allTimes = plugin.getPlayerManager().getGlobalBestTimesForMap(mapName);
        int total = allTimes.length;
        if (total < 2) return "";

        int betterCount = 0; // players with a strictly faster (lower) time
        for (long t : allTimes) {
            if (t < playerBestTime) betterCount++;
        }

        // rank = betterCount + 1  (1 = best player, no one faster)
        // topPct = rank / total * 100  →  smaller value = better rank
        double topPct = ((double) (betterCount + 1) / total) * 100.0;
        if (topPct > 100.0) topPct = 100.0;
        return String.format("[Top %.1f%%]", topPct);
    }

    public void removeHologram(String mapName, int islandIndex) {
        String key = mapName.toLowerCase() + ":" + islandIndex;
        String holoId = "fb_" + mapName.toLowerCase() + "_" + islandIndex;

        // Remove by deterministic ID
        try {
            eu.decentsoftware.holograms.api.DHAPI.removeHologram(holoId);
        } catch (NoClassDefFoundError | Exception ignored) {}

        // Also remove any tracked ID
        String tracked = holograms.remove(key);
        if (tracked != null && !tracked.equals(holoId)) {
            try {
                eu.decentsoftware.holograms.api.DHAPI.removeHologram(tracked);
            } catch (NoClassDefFoundError | Exception ignored) {}
        }
    }

    public void removeAll() {
        for (Map.Entry<String, String> entry : holograms.entrySet()) {
            try {
                eu.decentsoftware.holograms.api.DHAPI.removeHologram(entry.getValue());
            } catch (NoClassDefFoundError | Exception ignored) {}
        }
        holograms.clear();
    }
}
