package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.RunSession;
import net.gravijet.fastbuilder.map.GridCalculator;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.map.MapManager;
import net.gravijet.fastbuilder.replay.ReplaySession;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Prevents players from building or interacting outside their assigned island.
 */
public class ProtectionListener implements Listener {

    private final FastBuilder plugin;
    private final java.util.Map<java.util.UUID, Long> fallCooldown = new java.util.HashMap<>();
    private final java.util.Set<java.util.UUID> switchingPlayers = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    public ProtectionListener(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("fastbuilder.admin") && plugin.getMapManager().hasSetupSession(player.getUniqueId())) {
            return;
        }

        // Block ALL placement during a reset animation or replay — prevents phantom blocks.
        if (plugin.getGameplayManager() != null) {
            RunSession sess = plugin.getGameplayManager().getSession(player.getUniqueId());
            if (sess != null && sess.isResetting()) {
                event.setCancelled(true);
                return;
            }
            // Also block if the island itself is being reset (another player's reset cleared it)
            if (sess != null && plugin.getGameplayManager().isIslandResetting(sess.getMapName(), sess.getIslandIndex())) {
                event.setCancelled(true);
                return;
            }
        }
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Use Z-corridor check for building: allows placing blocks along the X axis
        // (the build direction) beyond the island template's defined width.
        if (!canBuildAtLocation(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
            // Silent cancel — no message spam while building near the edge
        }
    }

    /**
     * One-Click Pick: if the player has the cosmetic enabled and left-clicks one of their own
     * placed blocks, remove it instantly on first damage (no dig-time required).
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (plugin.getGameplayManager() == null) return;

        // Must have One-Click Pick cosmetic AND be holding the OCP item (Diamond Axe)
        net.gravijet.fastbuilder.player.PlayerData pData =
                plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (pData == null || !pData.hasOneClickPick()) return;
        ItemStack heldItem = player.getItemInHand();
        if (heldItem == null || heldItem.getType() != org.bukkit.Material.DIAMOND_AXE) return;

        // Must be on own island with an active session
        RunSession session = plugin.getGameplayManager().getSession(player.getUniqueId());
        if (session == null) return;

        Location blockLoc = event.getBlock().getLocation();
        boolean playerPlaced = false;
        for (Location placed : session.getPlacedBlocks()) {
            if (placed.getBlockX() == blockLoc.getBlockX()
                    && placed.getBlockY() == blockLoc.getBlockY()
                    && placed.getBlockZ() == blockLoc.getBlockZ()) {
                playerPlaced = true;
                break;
            }
        }
        if (!playerPlaced) return;

        // Instantly break the block: cancel the damage event (no crack animation),
        // play the break effect, then set the block to its original state.
        event.setCancelled(true);

        org.bukkit.block.Block block = event.getBlock();
        try {
            block.getWorld().playEffect(blockLoc, org.bukkit.Effect.STEP_SOUND, block.getTypeId());
        } catch (Exception ignored) {}

        String key = blockLoc.getBlockX() + "," + blockLoc.getBlockY() + "," + blockLoc.getBlockZ();
        int[] orig = session.getOriginalBlockStates().get(key);
        if (orig != null && orig[0] != 0) {
            block.setTypeIdAndData(orig[0], (byte) orig[1], false);
        } else {
            block.setType(org.bukkit.Material.AIR);
        }
        // Remove from session tracking so it doesn't get cleared again on reset
        session.getPlacedBlocks().removeIf(loc ->
                loc.getBlockX() == blockLoc.getBlockX()
                        && loc.getBlockY() == blockLoc.getBlockY()
                        && loc.getBlockZ() == blockLoc.getBlockZ());
        session.getPracticeBlocks().removeIf(loc ->
                loc.getBlockX() == blockLoc.getBlockX()
                        && loc.getBlockY() == blockLoc.getBlockY()
                        && loc.getBlockZ() == blockLoc.getBlockZ());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("fastbuilder.admin") && plugin.getMapManager().hasSetupSession(player.getUniqueId())) {
            return;
        }

        // Block breaking during reset animation or replay — no phantom interactions.
        if (plugin.getGameplayManager() != null) {
            RunSession breakSess = plugin.getGameplayManager().getSession(player.getUniqueId());
            if (breakSess != null && breakSess.isResetting()) {
                event.setCancelled(true);
                return;
            }
        }
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Build mode: allow breaking any block from the map or other players
        if (plugin.getGameplayManager() != null && plugin.getGameplayManager().isInBuildMode(player.getUniqueId())) {
            return;
        }

        if (!isOnOwnIsland(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }

        RunSession session = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
        if (session != null) {
            Location blockLoc = event.getBlock().getLocation();
            boolean playerPlaced = false;
            for (Location placed : session.getPlacedBlocks()) {
                if (placed.getBlockX() == blockLoc.getBlockX()
                        && placed.getBlockY() == blockLoc.getBlockY()
                        && placed.getBlockZ() == blockLoc.getBlockZ()) {
                    playerPlaced = true;
                    break;
                }
            }
            if (!playerPlaced) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        if (player.hasPermission("fastbuilder.admin") && plugin.getMapManager().hasSetupSession(player.getUniqueId())) {
            return;
        }

        // Build mode: unrestricted interaction anywhere in the world
        if (plugin.getGameplayManager() != null && plugin.getGameplayManager().isInBuildMode(player.getUniqueId())) {
            return;
        }

        if (!isOnOwnIsland(player, event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        if (player.hasPermission("fastbuilder.admin") && plugin.getMapManager().hasSetupSession(player.getUniqueId())) {
            return;
        }

        if (plugin.getGameplayManager() != null && plugin.getGameplayManager().getSession(player.getUniqueId()) != null) {
            event.setCancelled(true);
        }
        // Also protect replay viewers
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
            ((Player) event.getEntity()).setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();

        if (player.hasPermission("fastbuilder.admin") && plugin.getMapManager().hasSetupSession(player.getUniqueId())) {
            return;
        }

        // Build mode players move freely — no boundary enforcement
        if (plugin.getGameplayManager() != null && plugin.getGameplayManager().isInBuildMode(player.getUniqueId())) {
            return;
        }

        // Replay viewer boundary enforcement
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            ReplaySession replaySession = plugin.getReplayManager().getPlaybackSession(player.getUniqueId());
            if (replaySession != null && replaySession.isOutsideReplayBounds(event.getTo())) {
                Location safeSpot = replaySession.getViewerWatchLocation();
                if (safeSpot != null) {
                    player.teleport(safeSpot);
                } else {
                    plugin.getReplayManager().stopPlayback(player.getUniqueId());
                }
            }
            return; // Skip normal boundary checks for replay viewers
        }

        RunSession session = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
        if (session == null) return;

        MapData map = plugin.getMapManager().getMap(session.getMapName());
        if (map == null) return;

        Location to = event.getTo();
        int[] bounds = GridCalculator.getIslandBounds(map, session.getIslandIndex());
        int maxDist = plugin.getConfigManager().getMaxDistance();

        // Z strictly identifies the player's island slot — when crossed, either switch
        // to the neighboring island (if island-hopping is enabled and target is empty)
        // or trigger a full reset.
        boolean zOutOfBounds = to.getBlockZ() < bounds[2] || to.getBlockZ() > bounds[5];
        if (zOutOfBounds) {
            long now = System.currentTimeMillis();
            Long lastZ = fallCooldown.get(player.getUniqueId());
            if (lastZ == null || now - lastZ > 1000) {
                fallCooldown.put(player.getUniqueId(), now);

                // Island hopping: detect which island the player is moving into
                if (plugin.getConfigManager().isIslandHoppingEnabled()) {
                    int targetIsland = GridCalculator.getIslandIndex(map, to);
                    int currentIsland = session.getIslandIndex();
                    if (targetIsland >= 0 && targetIsland != currentIsland) {
                        // Adjacent-only: skip non-adjacent hops (fall instead)
                        if (Math.abs(targetIsland - currentIsland) <= 1
                                && !switchingPlayers.contains(player.getUniqueId())) {
                            java.util.List<net.gravijet.fastbuilder.map.IslandInstance> islandList =
                                    plugin.getMapManager().getIslands(map.getName());
                            if (islandList != null && targetIsland < islandList.size()) {
                                net.gravijet.fastbuilder.map.IslandInstance targetInstance = islandList.get(targetIsland);
                                if (!targetInstance.isOccupied()) {
                                    // Empty adjacent target → auto-switch session
                                    switchingPlayers.add(player.getUniqueId());
                                    java.util.UUID switchUuid = player.getUniqueId();
                                    plugin.getGameplayManager().switchIsland(player, map, session, targetIsland);
                                    Bukkit.getScheduler().runTaskLater(plugin,
                                            () -> switchingPlayers.remove(switchUuid), 20L);
                                    return;
                                }
                            }
                        }
                    }
                }

                event.setCancelled(true);
                plugin.getGameplayManager().onFall(player);
            } else {
                event.setTo(event.getFrom());
            }
            return;
        }

        boolean outOfBounds = to.getBlockX() < bounds[0] - maxDist;

        boolean inVoid;
        MapData mapForVoid = plugin.getMapManager().getMap(session.getMapName());
        if (mapForVoid != null && mapForVoid.hasDeathY()) {
            int absoluteDeathY = mapForVoid.getDeathY();
            inVoid = to.getY() < absoluteDeathY;
        } else {
            inVoid = to.getBlockY() < bounds[1] - maxDist;
        }

        if (outOfBounds || inVoid) {
            // When deathY is explicitly configured, bypass the 2 s cooldown so the player
            // is reset the moment they cross the defined threshold — not several blocks later.
            boolean skipCooldown = inVoid && mapForVoid != null && mapForVoid.hasDeathY();
            if (skipCooldown) {
                plugin.getGameplayManager().onFall(player);
            } else {
                long now = System.currentTimeMillis();
                Long lastFall = fallCooldown.get(player.getUniqueId());
                if (lastFall == null || now - lastFall > 2000) {
                    fallCooldown.put(player.getUniqueId(), now);
                    plugin.getGameplayManager().onFall(player);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.getGameplayManager() != null && plugin.getGameplayManager().getSession(player.getUniqueId()) != null) {
            event.setCancelled(true);
        }
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Check if a player can place a block at a given location.
     * Z-bounds strictly identify the island slot. X and Y use generous tolerances
     * so players can bridge along the full schematic length and build above it.
     */
    private boolean canBuildAtLocation(Player player, Location blockLoc) {
        if (plugin.getGameplayManager() != null && plugin.getGameplayManager().isInBuildMode(player.getUniqueId())) {
            return true;
        }

        MapManager mm = plugin.getMapManager();

        for (MapData map : mm.getAllMaps()) {
            if (!map.isEnabled()) continue;
            if (!blockLoc.getWorld().getName().equals(map.getWorldName())) continue;

            int playerIsland = mm.getPlayerIsland(map.getName(), player.getUniqueId());
            if (playerIsland < 0) continue;

            Location islandMin = map.getIslandMin(playerIsland);
            Location islandMax = map.getIslandMax(playerIsland);

            int bx = blockLoc.getBlockX();
            int by = blockLoc.getBlockY();
            int bz = blockLoc.getBlockZ();

            // Z strictly identifies the player's island slot (not affected by distance setting)
            boolean zInBounds = bz >= islandMin.getBlockZ() && bz <= islandMax.getBlockZ();

            // Y: 2 blocks below the schematic base to 64 blocks above the schematic top
            boolean yInBounds = by >= islandMin.getBlockY() - 2
                    && by <= islandMax.getBlockY() + 64;

            // X (build direction): schematic start to schematic end + 10 block buffer
            boolean xInBounds = bx >= islandMin.getBlockX()
                    && bx <= islandMax.getBlockX() + 10;

            if (xInBounds && yInBounds && zInBounds) {
                return true;
            }
        }

        return player.hasPermission("fastbuilder.admin");
    }

    // -------------------------------------------------------------------------
    // Absolute build mode overrides (run at HIGHEST so they fire last and win)
    // -------------------------------------------------------------------------

    /**
     * Force-allow ALL block placements for build-mode players, regardless of what
     * lower-priority listeners (including other plugins) decided.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlaceBuildModeOverride(BlockPlaceEvent event) {
        if (plugin.getGameplayManager() != null
                && plugin.getGameplayManager().isInBuildMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(false);
        }
    }

    /**
     * Force-allow ALL block breaks for build-mode players.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreakBuildModeOverride(BlockBreakEvent event) {
        if (plugin.getGameplayManager() != null
                && plugin.getGameplayManager().isInBuildMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(false);
        }
    }

    /**
     * Force-allow ALL block interactions for build-mode players.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractBuildModeOverride(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (plugin.getGameplayManager() != null
                && plugin.getGameplayManager().isInBuildMode(event.getPlayer().getUniqueId())) {
            event.setCancelled(false);
        }
    }

    /**
     * Standard island bounds check for block breaking and interaction.
     */
    private boolean isOnOwnIsland(Player player, Location blockLoc) {
        MapManager mm = plugin.getMapManager();

        for (MapData map : mm.getAllMaps()) {
            if (!map.isEnabled()) continue;
            if (!blockLoc.getWorld().getName().equals(map.getWorldName())) continue;

            int blockIsland = GridCalculator.getIslandIndex(map, blockLoc);
            if (blockIsland < 0) continue;

            int playerIsland = mm.getPlayerIsland(map.getName(), player.getUniqueId());
            return blockIsland == playerIsland;
        }

        return player.hasPermission("fastbuilder.admin");
    }
}
