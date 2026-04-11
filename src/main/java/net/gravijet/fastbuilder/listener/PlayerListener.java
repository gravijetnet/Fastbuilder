package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.RunSession;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.map.MapManager;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.player.PlayerManager;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.Bukkit;
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

        // 1. Try the default map first
        String defaultMapName = plugin.getConfigManager().getDefaultMap();
        if (defaultMapName != null && !defaultMapName.isEmpty()) {
            MapData defMap = mm.getMap(defaultMapName);
            if (defMap != null && defMap.isEnabled()) {
                int island = mm.assignFreeIsland(defMap.getName(), player.getUniqueId(), player.getName());
                if (island >= 0) {
                    finalizeJoin(player, data, mm, defMap, island);
                    return;
                }
                // Default map is full — fall through to find the next available map
            }
        }

        // 2. Try any other enabled map (skip the full default map)
        for (MapData map : mm.getAllMaps()) {
            if (!map.isEnabled()) continue;
            if (defaultMapName != null && map.getName().equalsIgnoreCase(defaultMapName)) continue; // already tried
            int island = mm.assignFreeIsland(map.getName(), player.getUniqueId(), player.getName());
            if (island >= 0) {
                finalizeJoin(player, data, mm, map, island);
                return;
            }
        }

        // 3. No free island found anywhere
        if (player.hasPermission("fastbuilder.admin") || player.hasPermission("fastbuilder.setup")) {
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            player.setGameMode(GameMode.CREATIVE);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getMessage("no-free-islands")
                    .replace("%prefix%", plugin.getConfigManager().getPrefix())));
        } else {
            player.kickPlayer(ColorUtil.translate(plugin.getConfigManager().getMessage("no-free-islands")
                    .replace("%prefix%", plugin.getConfigManager().getPrefix())));
        }
    }

    private void finalizeJoin(Player player, PlayerData data, MapManager mm, MapData map, int island) {
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

        // STRICT BLOCK CLEANUP: remove all placed blocks before session is removed.
        // Build mode: exit first, but do NOT clear blocks — they persist by design.
        if (plugin.getGameplayManager() != null) {
            if (plugin.getGameplayManager().isInBuildMode(player.getUniqueId())) {
                plugin.getGameplayManager().exitBuildMode(player.getUniqueId());
            } else {
                plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());
            }
            plugin.getGameplayManager().clearEndPlatform(player.getUniqueId());
            // Revert island to default design so the next player gets a clean slate
            PlayerData qDesignData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            if (qDesignData != null && qDesignData.getLastMap() != null) {
                net.gravijet.fastbuilder.map.MapData qMap =
                        plugin.getMapManager().getMap(qDesignData.getLastMap());
                if (qMap != null) {
                    plugin.getGameplayManager().revertIslandDesign(qMap, qDesignData.getLastIsland());
                }
                qDesignData.clearCustomLengths();
            }
            plugin.getGameplayManager().removeSession(player.getUniqueId());
            plugin.getGameplayManager().removeGlobalSessionBest(player.getName());
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
     * Clean up a player's current island: despawn NPC, remove hologram, clear placed blocks,
     * remove CPS hologram, and remove from session top 3.
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
            plugin.getGameplayManager().clearEndPlatform(player.getUniqueId());
            plugin.getGameplayManager().removeSession(player.getUniqueId());
            plugin.getGameplayManager().removeGlobalSessionBest(player.getName());
        }
        if (plugin.getCpsListener() != null) {
            plugin.getCpsListener().cleanupPlayer(player.getUniqueId());
        }
    }

    private void setupPlayerOnIsland(Player player, MapData map, int island) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setFoodLevel(20);
        player.setHealth(player.getMaxHealth());

        if (plugin.getGameplayManager() != null) {
            net.gravijet.fastbuilder.gameplay.RunSession sess =
                    plugin.getGameplayManager().createSession(player.getUniqueId(), map.getName(), island);
            PlayerData pJoinData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            // Place end island / platform on join
            if (map.hasEndIsland()) {
                // Custom lengths are session-only; always start at base distance on join
                plugin.getGameplayManager().placeEndPlatform(
                        player, map, sess, map.getBaseCustomLength());
            } else if (pJoinData != null && map.hasCustomLength()
                    && pJoinData.getCustomLength(map.getName()) > 0) {
                plugin.getGameplayManager().placeEndPlatform(
                        player, map, sess, pJoinData.getCustomLength(map.getName()));
            }
            // Apply the player's selected island design (does nothing if default)
            plugin.getGameplayManager().applyPlayerDesign(player, map, island);
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
