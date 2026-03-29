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
    // Key: "mapName:islandIndex" -> hologram ID
    private final Map<String, String> holograms = new HashMap<>();

    public HologramManager(FastBuilder plugin) {
        this.plugin = plugin;
    }

    /**
     * Create or update a hologram for an island with a Player reference.
     */
    public void updateHologram(String mapName, int islandIndex, Player player) {
        if (!plugin.getConfigManager().isHologramsEnabled()) return;

        MapData map = plugin.getMapManager().getMap(mapName);
        if (map == null) return;

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        updateHologram(map, islandIndex, data);
    }

    /**
     * Create or update a hologram for an island.
     */
    public void updateHologram(MapData map, int islandIndex, PlayerData playerData) {
        if (!plugin.getConfigManager().isHologramsEnabled()) return;

        String key = map.getName().toLowerCase() + ":" + islandIndex;
        String holoId = "fb_" + key.replace(":", "_");

        try {
            // Build hologram lines from config
            List<String> configLines = plugin.getConfigManager().getHologramLines();
            List<String> lines = new ArrayList<>();

            PlayerData.MapStats stats = playerData.getStats(map.getName());
            String pb = (stats != null && stats.hasBestTime())
                    ? TimeUtil.formatTime(stats.bestTime) : "N/A";
            int successful = stats != null ? stats.successfulAttempts : 0;
            int total = stats != null ? stats.totalAttempts : 0;

            for (String line : configLines) {
                line = line.replace("%player%", playerData.getName())
                        .replace("%pb%", pb)
                        .replace("%successful_attempts%", String.valueOf(successful))
                        .replace("%total_attempts%", String.valueOf(total));
                lines.add(ColorUtil.translate(line));
            }

            // Position: above the island spawn point
            Location spawn = map.getIslandSpawn(islandIndex);
            Location holoLoc = spawn.clone().add(0, 3, 0);

            // Remove existing hologram if present
            String existingId = holograms.get(key);
            if (existingId != null) {
                try {
                    eu.decentsoftware.holograms.api.DHAPI.removeHologram(existingId);
                } catch (Exception ignored) {}
            }

            // Create new hologram
            eu.decentsoftware.holograms.api.holograms.Hologram hologram =
                    eu.decentsoftware.holograms.api.DHAPI.createHologram(holoId, holoLoc, false, lines);

            holograms.put(key, holoId);
        } catch (NoClassDefFoundError | Exception e) {
            plugin.getLogger().warning("DecentHolograms API not available: " + e.getMessage());
        }
    }

    /**
     * Remove the hologram for an island.
     */
    public void removeHologram(String mapName, int islandIndex) {
        String key = mapName.toLowerCase() + ":" + islandIndex;
        String holoId = holograms.remove(key);
        if (holoId == null) return;

        try {
            eu.decentsoftware.holograms.api.DHAPI.removeHologram(holoId);
        } catch (NoClassDefFoundError | Exception ignored) {}
    }

    /**
     * Remove all managed holograms.
     */
    public void removeAll() {
        for (String holoId : holograms.values()) {
            try {
                eu.decentsoftware.holograms.api.DHAPI.removeHologram(holoId);
            } catch (NoClassDefFoundError | Exception ignored) {}
        }
        holograms.clear();
    }
}
