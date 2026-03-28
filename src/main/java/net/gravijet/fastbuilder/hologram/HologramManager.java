package net.gravijet.fastbuilder.hologram;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.player.PlayerData;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages DecentHolograms for per-island player stats display.
 * Each island has a hologram showing the occupant's stats.
 */
public class HologramManager {

    private final FastBuilder plugin;
    // Key: "mapName:islandIndex"
    private final Map<String, String> holograms = new HashMap<>();

    public HologramManager(FastBuilder plugin) {
        this.plugin = plugin;
    }

    /**
     * Create or update a hologram for an island.
     */
    public void updateHologram(MapData map, int islandIndex, PlayerData playerData) {
        if (!plugin.getConfigManager().isHologramsEnabled()) return;

        try {
            // DecentHolograms API integration will be implemented in the gameplay phase
            plugin.getLogger().fine("Hologram update for " + map.getName() + " island " + islandIndex);
        } catch (NoClassDefFoundError | Exception e) {
            plugin.getLogger().warning("DecentHolograms API not available.");
        }
    }

    /**
     * Remove the hologram for an island.
     */
    public void removeHologram(MapData map, int islandIndex) {
        String key = map.getName().toLowerCase() + ":" + islandIndex;
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
