package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.map.MapManager;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.player.PlayerManager;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player join (route to map, setup hotbar/scoreboard/NPC/gameplay) and quit (save + cleanup).
 */
public class PlayerListener implements Listener {

    private final FastBuilder plugin;

    public PlayerListener(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerManager pm = plugin.getPlayerManager();
        MapManager mm = plugin.getMapManager();

        // Load player data
        PlayerData data = pm.getPlayerData(player.getUniqueId(), player.getName());

        // Route to last played map, or default
        String targetMap = data.getLastMap();
        if (targetMap == null || targetMap.isEmpty() || mm.getMap(targetMap) == null) {
            targetMap = plugin.getConfigManager().getDefaultMap();
        }

        if (targetMap != null && !targetMap.isEmpty()) {
            MapData map = mm.getMap(targetMap);
            if (map != null && map.isEnabled()) {
                int island = mm.assignFreeIsland(map.getName(), player.getUniqueId(), player.getName());
                if (island >= 0) {
                    player.teleport(map.getIslandSpawn(island));
                    data.setLastMap(map.getName());
                    data.setLastIsland(island);

                    String raw = plugin.getConfigManager().getMessage("joined-mode");
                    if (raw != null && !raw.isEmpty()) {
                        raw = raw.replace("%map%", map.getName());
                        raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
                        player.sendMessage(ColorUtil.translate(raw));
                    }

                    // Setup gameplay session
                    setupPlayerOnIsland(player, map, island);

                    // Check autoscale
                    mm.checkAutoscale(map);
                    return;
                }
            }
        }

        // No valid map found, relocate to any available
        mm.relocatePlayer(player, "");

        // Try to setup on the relocated map
        data = pm.getCachedData(player.getUniqueId());
        if (data != null && data.getLastMap() != null) {
            MapData map = mm.getMap(data.getLastMap());
            if (map != null) {
                int island = mm.getPlayerIsland(map.getName(), player.getUniqueId());
                if (island >= 0) {
                    setupPlayerOnIsland(player, map, island);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Stop any active replay
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            plugin.getReplayManager().stopPlayback(player.getUniqueId());
        }

        // Remove gameplay session
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().removeSession(player.getUniqueId());
        }

        // Despawn NPC
        if (plugin.getNpcManager() != null) {
            plugin.getNpcManager().despawnNpc(player.getUniqueId());
        }

        // Remove scoreboard
        plugin.getScoreboardManager().removeScoreboard(player);

        // Free all islands held by this player
        plugin.getMapManager().freeAllIslands(player.getUniqueId());

        // Save and unload player data
        plugin.getPlayerManager().unload(player.getUniqueId());
    }

    /**
     * Setup all gameplay systems for a player on an island.
     */
    private void setupPlayerOnIsland(Player player, MapData map, int island) {
        // Create gameplay session
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().createSession(player.getUniqueId(), map.getName(), island);
        }

        // Give hotbar items
        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().giveItems(player);
        }

        // Setup scoreboard
        plugin.getScoreboardManager().createScoreboard(player);

        // Spawn NPC on the island
        if (plugin.getNpcManager() != null) {
            plugin.getNpcManager().spawnNpc(player, map.getIslandSpawn(island));
        }

        // Update hologram
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().updateHologram(map.getName(), island, player);
        }
    }
}
