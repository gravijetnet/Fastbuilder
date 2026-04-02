package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.RunSession;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.map.MapManager;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.player.PlayerManager;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player join (route to map, setup gameplay) and quit (cleanup + block removal).
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

        PlayerData data = pm.getPlayerData(player.getUniqueId(), player.getName());

        // Route to last played map or default map
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
                        raw = raw.replace("%map%", map.getName())
                                .replace("%prefix%", plugin.getConfigManager().getPrefix());
                        player.sendMessage(ColorUtil.translate(raw));
                    }

                    setupPlayerOnIsland(player, map, island);
                    mm.checkAutoscale(map);
                    return;
                }
            }
        }

        // Fallback: relocate to any available map
        mm.relocatePlayer(player, "");

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

        // Stop active replay
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            plugin.getReplayManager().stopPlayback(player.getUniqueId());
        }

        // Stop any active recording
        if (plugin.getReplayManager() != null) {
            plugin.getReplayManager().stopRecording(player.getUniqueId(), false);
        }

        // STRICT BLOCK CLEANUP: remove all placed blocks before session is removed
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());
            plugin.getGameplayManager().removeSession(player.getUniqueId());
        }

        // Despawn NPC
        if (plugin.getNpcManager() != null) {
            plugin.getNpcManager().despawnNpc(player.getUniqueId());
        }

        // Remove hologram from the player's last island
        if (plugin.getHologramManager() != null) {
            PlayerData qData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            if (qData != null && qData.getLastMap() != null) {
                plugin.getHologramManager().removeHologram(qData.getLastMap(), qData.getLastIsland());
            }
        }

        // Remove scoreboard
        plugin.getScoreboardManager().removeScoreboard(player);

        // Reset playtime counter
        if (plugin.getCoinManager() != null) {
            plugin.getCoinManager().resetPlaytime(player.getUniqueId());
        }

        // Clean up CPS hologram
        if (plugin.getCpsListener() != null) {
            plugin.getCpsListener().cleanupPlayer(player.getUniqueId());
        }

        // Free all islands
        plugin.getMapManager().freeAllIslands(player.getUniqueId());

        // Save and unload player data
        plugin.getPlayerManager().unload(player.getUniqueId());
    }

    /**
     * Clean up a player's current island: despawn NPC, remove hologram, clear placed blocks.
     * Call this before switching the player to a new island or map.
     */
    public void cleanupCurrentIsland(Player player) {
        if (plugin.getNpcManager() != null) {
            plugin.getNpcManager().despawnNpc(player.getUniqueId());
        }
        if (plugin.getHologramManager() != null) {
            PlayerData d = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            if (d != null && d.getLastMap() != null) {
                plugin.getHologramManager().removeHologram(d.getLastMap(), d.getLastIsland());
            }
        }
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());
            plugin.getGameplayManager().removeSession(player.getUniqueId());
        }
    }

    private void setupPlayerOnIsland(Player player, MapData map, int island) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setFoodLevel(20);
        player.setHealth(player.getMaxHealth());

        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().createSession(player.getUniqueId(), map.getName(), island);
        }

        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().giveItems(player);
        }

        plugin.getScoreboardManager().createScoreboard(player);

        if (plugin.getNpcManager() != null) {
            plugin.getNpcManager().spawnNpc(player, map.getIslandNpcLocation(island));
        }

        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().updateHologram(map.getName(), island, player);
        }
    }
}
