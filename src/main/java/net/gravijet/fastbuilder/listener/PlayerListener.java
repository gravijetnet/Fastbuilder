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

        // Cache skin texture early — used by NPCs, leaderboard skulls, and replay NPCs.
        if (plugin.getSkinManager() != null) {
            plugin.getSkinManager().cacheSkin(player);
        }

        PlayerData data = pm.getPlayerData(player.getUniqueId(), player.getName());

        // 1. Try the default map first
        String defaultMapName = plugin.getConfigManager().getDefaultMap();
        if (defaultMapName != null && !defaultMapName.isEmpty()) {
            MapData defMap = mm.getMap(defaultMapName);
            // Skip the default map while it is being scaled — fall through to other maps
            // instead of leaving the player stranded with no island and no session.
            if (defMap != null && defMap.isEnabled() && !mm.isMapScaling(defMap.getName())) {
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
            if (mm.isMapScaling(map.getName())) continue; // skip maps being scaled
            int island = mm.assignFreeIsland(map.getName(), player.getUniqueId(), player.getName());
            if (island >= 0) {
                finalizeJoin(player, data, mm, map, island);
                return;
            }
        }

        // 3. No free island found anywhere
        if (player.hasPermission("fastbuilder.admin") || player.hasPermission("fastbuilder.setup")) {
            if (!Bukkit.getWorlds().isEmpty()) player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            player.setGameMode(GameMode.CREATIVE);
            player.setAllowFlight(true);
            player.setFlying(true);

            // If no maps exist at all, prompt the admin to run /map setup
            boolean noMapsExist = mm.getAllMaps().isEmpty();
            if (noMapsExist) {
                net.gravijet.fastbuilder.util.Messages.send(player, "no-maps-configured");
                sendSetupPrompt(player);
            } else {
                net.gravijet.fastbuilder.util.Messages.send(player, "no-free-islands");
            }
            return;
        }

        // Non-admins: if the only reason there's no island is that a map is mid-scale,
        // keep the player connected and retry shortly rather than kicking them.
        boolean anyEnabledScaling = false;
        for (MapData map : mm.getAllMaps()) {
            if (map.isEnabled() && mm.isMapScaling(map.getName())) { anyEnabledScaling = true; break; }
        }
        if (anyEnabledScaling) {
            if (!Bukkit.getWorlds().isEmpty()) player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            net.gravijet.fastbuilder.util.Messages.send(player, "island-scaling");
            retryJoinAfterScaling(player, 0);
            return;
        }

        String noIslands = net.gravijet.fastbuilder.util.Messages.get("no-free-islands");
        player.kickPlayer(ColorUtil.translate(noIslands
                .replace("%prefix%", plugin.getConfigManager().getPrefix())));
    }

    /**
     * Retry assigning a free island for a player who joined while a map was mid-scale.
     * Retries up to 10 times (1 s apart). Gives up silently if still no island —
     * the player remains at world spawn and can use /fb join manually.
     */
    private void retryJoinAfterScaling(Player player, int attempt) {
        if (attempt >= 10) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            MapManager mm = plugin.getMapManager();
            // If the player already got placed (e.g. used /fb join in the meantime), stop retrying.
            if (plugin.getGameplayManager() != null
                    && plugin.getGameplayManager().getSession(player.getUniqueId()) != null) {
                return;
            }
            PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId(), player.getName());
            for (MapData map : mm.getAllMaps()) {
                if (!map.isEnabled() || mm.isMapScaling(map.getName())) continue;
                int island = mm.assignFreeIsland(map.getName(), player.getUniqueId(), player.getName());
                if (island >= 0) {
                    finalizeJoin(player, data, mm, map, island);
                    return;
                }
            }
            retryJoinAfterScaling(player, attempt + 1);
        }, 20L);
    }

    private void finalizeJoin(Player player, PlayerData data, MapManager mm, MapData map, int island) {
        org.bukkit.Location spawn = (plugin.getGameplayManager() != null)
                ? plugin.getGameplayManager().getEffectiveSpawn(player.getUniqueId(), map, island)
                : map.getIslandSpawn(island);
        player.teleport(spawn);
        data.setLastMap(map.getName());
        data.setLastIsland(island);

        net.gravijet.fastbuilder.util.Messages.send(player, "joined-mode", "map", map.getName());

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
            plugin.getGameplayManager().removeGlobalSessionBest(player.getUniqueId());
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

        // Clean up protection listener state (fall cooldowns, switching flags)
        if (plugin.getProtectionListener() != null) {
            plugin.getProtectionListener().cleanupPlayer(player.getUniqueId());
        }

        // Free all islands
        plugin.getMapManager().freeAllIslands(player.getUniqueId());

        // Save and unload player data
        plugin.getPlayerManager().unload(player.getUniqueId());
    }

    private void sendSetupPrompt(Player player) {
        try {
            net.md_5.bungee.api.chat.TextComponent line =
                    new net.md_5.bungee.api.chat.TextComponent(
                            ColorUtil.translate("&7Click to begin: "));
            net.md_5.bungee.api.chat.TextComponent btn =
                    new net.md_5.bungee.api.chat.TextComponent(
                            ColorUtil.translate("&c[/map setup]"));
            btn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/map setup"));
            btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.BaseComponent[]{
                            new net.md_5.bungee.api.chat.TextComponent(
                                    ColorUtil.translate("&7Click to start the map setup wizard"))}));
            line.addExtra(btn);
            player.spigot().sendMessage(line);
        } catch (Exception e) {
            player.sendMessage(ColorUtil.translate("&7Type &c/map setup &7to create your first map."));
        }
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
            plugin.getGameplayManager().removeGlobalSessionBest(player.getUniqueId());
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
            // Place end island / platform on join, honoring any saved custom length.
            // Legacy (no end-island) maps also receive a platform at the player's preferred
            // length, or at the legacy min length when no preference is saved — without this
            // the player has no visible finish target on first join.
            if (map.hasEndIsland()) {
                int desiredLen = pJoinData != null ? pJoinData.getCustomLength(map.getName()) : 0;
                if (desiredLen <= 0) desiredLen = map.getBaseCustomLength();
                plugin.getGameplayManager().placeEndPlatform(
                        player, map, sess, desiredLen);
            } else if (map.hasCustomLength()) {
                int desiredLen = pJoinData != null ? pJoinData.getCustomLength(map.getName()) : 0;
                if (desiredLen <= 0) desiredLen = map.getEffectiveMinCustomLength();
                if (desiredLen > 0) {
                    plugin.getGameplayManager().placeEndPlatform(player, map, sess, desiredLen);
                }
            }
            // Apply the player's selected island design (does nothing if default)
            plugin.getGameplayManager().applyPlayerDesign(player, map, island);
        }

        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().giveItems(player);
        }

        plugin.getScoreboardManager().createScoreboard(player);

        if (plugin.getNpcManager() != null) {
            // Skip default NPC spawn when applyPlayerDesign() already placed it at a custom position
            boolean designHasNpc = false;
            if (plugin.getGameplayManager() != null) {
                PlayerData pd = plugin.getPlayerManager().getCachedData(player.getUniqueId());
                if (pd != null) {
                    String design = pd.getSelectedDesign(map.getName());
                    if (design != null && !design.equals(map.getTemplateFile())) {
                        net.gravijet.fastbuilder.map.MapData.DesignProfile prof = map.getDesignProfile(design);
                        if (prof != null && prof.hasNpcPosition()) designHasNpc = true;
                    }
                }
            }
            if (!designHasNpc) {
                plugin.getNpcManager().spawnNpc(player, map.getIslandNpcLocation(island));
            }
        }

        if (plugin.getHologramManager() != null) {
            org.bukkit.Location holoLoc = plugin.getGameplayManager() != null
                    ? plugin.getGameplayManager().getEffectiveHologramLocation(player.getUniqueId(), map, island)
                    : map.getIslandHologramLocation(island);
            plugin.getHologramManager().updateHologramAt(map.getName(), island, player, holoLoc);
        }
    }
}
