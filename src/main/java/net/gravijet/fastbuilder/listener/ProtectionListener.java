package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.RunSession;
import net.gravijet.fastbuilder.map.GridCalculator;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.map.MapManager;
import net.gravijet.fastbuilder.replay.ReplaySession;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
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

    public void cleanupPlayer(java.util.UUID uuid) {
        fallCooldown.remove(uuid);
        switchingPlayers.remove(uuid);
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
        // Practice blocks persist after death/reset (placedBlocks is cleared but practiceBlocks is not)
        if (!playerPlaced) {
            for (Location placed : session.getPracticeBlocks()) {
                if (placed.getBlockX() == blockLoc.getBlockX()
                        && placed.getBlockY() == blockLoc.getBlockY()
                        && placed.getBlockZ() == blockLoc.getBlockZ()) {
                    playerPlaced = true;
                    break;
                }
            }
        }
        if (!playerPlaced) return;

        // Instantly break the block: cancel the damage event (no crack animation),
        // play the break effect, restore the original block, and return the placed block to inventory.
        event.setCancelled(true);

        org.bukkit.block.Block block = event.getBlock();
        int brokenTypeId = block.getTypeId();
        byte brokenData  = block.getData();
        try {
            block.getWorld().playEffect(blockLoc, org.bukkit.Effect.STEP_SOUND, brokenTypeId);
        } catch (Exception ignored) {}

        String key = blockLoc.getBlockX() + "," + blockLoc.getBlockY() + "," + blockLoc.getBlockZ();
        int[] orig = session.getOriginalBlockStates().get(key);
        if (orig != null && orig[0] != 0) {
            block.setTypeIdAndData(orig[0], (byte) orig[1], false);
        } else {
            block.setType(org.bukkit.Material.AIR);
        }

        // Return the broken block to the player's inventory immediately
        @SuppressWarnings("deprecation")
        org.bukkit.material.MaterialData md = new org.bukkit.material.MaterialData(brokenTypeId, brokenData);
        org.bukkit.Material mat = md.getItemType();
        if (mat != null && mat != org.bukkit.Material.AIR) {
            org.bukkit.inventory.ItemStack ret = new org.bukkit.inventory.ItemStack(mat, 1, (short) 0, brokenData);
            player.getInventory().addItem(ret);
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
            // Also allow breaking practice blocks with tools (not only via reset/toggle)
            if (!playerPlaced) {
                for (Location placed : session.getPracticeBlocks()) {
                    if (placed.getBlockX() == blockLoc.getBlockX()
                            && placed.getBlockY() == blockLoc.getBlockY()
                            && placed.getBlockZ() == blockLoc.getBlockZ()) {
                        playerPlaced = true;
                        break;
                    }
                }
            }
            if (!playerPlaced) {
                event.setCancelled(true);
                return;
            }
            // Infinite blocks: the player's hotbar stack never depletes, so a dropped
            // item from the broken block would just clutter the world. Suppress drops
            // by cancelling the event and clearing the block manually.
            net.gravijet.fastbuilder.player.PlayerData pd =
                    plugin.getPlayerManager().getCachedData(player.getUniqueId());
            if (pd != null && pd.hasInfiniteBlocks()) {
                org.bukkit.block.Block broken = event.getBlock();
                int brokenTypeId = broken.getTypeId();
                event.setCancelled(true);
                broken.setType(org.bukkit.Material.AIR);
                try {
                    broken.getWorld().playEffect(blockLoc, org.bukkit.Effect.STEP_SOUND, brokenTypeId);
                } catch (Exception ignored) {}
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

        // Dragon egg: cancel teleport and place held block instead
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock().getType() == org.bukkit.Material.DRAGON_EGG) {
            event.setCancelled(true);
            placeBlockAgainstDragonEgg(player, event.getClickedBlock(), event.getBlockFace());
            return;
        }

        // Custom-length click control: left-click on end island = closer, right-click = further
        if (plugin.getGameplayManager() != null
                && (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK
                    || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {
            if (handleCustomLengthClick(player, event)) return;
        }

        // The end island lies outside the island's own X/Y box, so isOnOwnIsland() rejects it.
        // Allow it explicitly — otherwise every interaction with the finish platform (including
        // placing a block against it) is cancelled here.
        if (!isOnOwnIsland(player, event.getClickedBlock().getLocation())
                && !isInOwnEndIsland(player, event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** True when the location is inside the end-island region currently placed for this player. */
    private boolean isInOwnEndIsland(Player player, Location loc) {
        if (plugin.getGameplayManager() == null) return false;
        int[] region = plugin.getGameplayManager().getEndIslandRegion(player.getUniqueId());
        if (region == null) return false;

        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        return bx >= region[0] && bx <= region[0] + region[3] - 1
                && by >= region[1] && by <= region[1] + region[4] - 1
                && bz >= region[2] && bz <= region[2] + region[5] - 1;
    }

    @SuppressWarnings("deprecation")
    private void placeBlockAgainstDragonEgg(Player player, org.bukkit.block.Block egg,
                                             org.bukkit.block.BlockFace face) {
        ItemStack held = player.getItemInHand();
        if (held == null || held.getType() == org.bukkit.Material.AIR || !held.getType().isBlock()) return;

        org.bukkit.block.Block target = egg.getRelative(face);
        if (target.getType() != org.bukkit.Material.AIR) return;

        org.bukkit.block.BlockState replacedState = target.getState();
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(target, replacedState, egg, held, player, true);
        org.bukkit.Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) return;

        target.setTypeIdAndData(held.getTypeId(), held.getData().getData(), true);

        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            if (held.getAmount() <= 1) {
                player.setItemInHand(null);
            } else {
                held.setAmount(held.getAmount() - 1);
            }
        }
    }

    /**
     * If the player clicks on a block inside their end-island region <em>with an empty hand or a
     * non-block item</em>, adjust the custom length.
     * Left-click = closer (-1 / -10 with shift), right-click = further (+1 / +10 with shift).
     * Returns true if the click was consumed (event should not continue to other handlers).
     *
     * <p>Clicks made while holding a block are NOT consumed: the player is building, not adjusting.
     * Players bridge with a block in hand and usually while sneaking, so consuming those clicks
     * both cancelled every placement onto the end island and silently shifted the distance by
     * ±1 (±10 sneaking) on each attempt — the distance appeared to change on its own mid-run.</p>
     */
    private boolean handleCustomLengthClick(Player player, PlayerInteractEvent event) {
        net.gravijet.fastbuilder.gameplay.RunSession session =
                plugin.getGameplayManager().getSession(player.getUniqueId());
        if (session == null) return false;

        MapData map = plugin.getMapManager().getMap(session.getMapName());
        if (map == null || !map.hasEndIsland()) return false;

        int[] region = plugin.getGameplayManager().getEndIslandRegion(player.getUniqueId());
        if (region == null) return false;

        org.bukkit.block.Block clicked = event.getClickedBlock();
        int bx = clicked.getX(), by = clicked.getY(), bz = clicked.getZ();
        if (bx < region[0] || bx > region[0] + region[3] - 1) return false;
        if (by < region[1] || by > region[1] + region[4] - 1) return false;
        if (bz < region[2] || bz > region[2] + region[5] - 1) return false;

        // Holding a block = building. Leave the click alone so the placement goes through.
        ItemStack held = player.getItemInHand();
        if (held != null && held.getType() != org.bukkit.Material.AIR && held.getType().isBlock()) {
            return false;
        }

        if (!player.hasPermission("fastbuilder.feature.custom_length")) return false;

        event.setCancelled(true);

        net.gravijet.fastbuilder.player.PlayerData pData =
                plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (pData == null) return true;

        boolean isLeft  = event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK;
        boolean isShift = player.isSneaking();
        int step = isShift ? 10 : 1;
        // Matches the Custom Length menu (slot 11): left-click = closer (decrease customLength),
        // right-click = further (increase). Shift multiplies the step by 10.
        int delta = isLeft ? -step : step;

        int current = pData.getCustomLength(map.getName());
        if (current <= 0) current = map.getBaseCustomLength() > 0
                ? map.getBaseCustomLength() : map.getEffectiveMinCustomLength();

        int newVal = Math.max(map.getEffectiveMinCustomLength(),
                Math.min(map.getEffectiveMaxCustomLength(), current + delta));
        if (newVal == current) return true;

        pData.setCustomLength(map.getName(), newVal);
        plugin.getGameplayManager().placeEndPlatform(player, map, session, newVal);
        return true;
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
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        boolean inSession = plugin.getGameplayManager() != null
                && plugin.getGameplayManager().getSession(player.getUniqueId()) != null;
        boolean inReplay = plugin.getReplayManager() != null
                && plugin.getReplayManager().isInPlayback(player.getUniqueId());
        if (inSession || inReplay) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
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
                // Check hopping BEFORE setting fallCooldown — a successful hop must not arm
                // the cooldown, or the player cannot hop back within the same second.
                if (plugin.getConfigManager().isIslandHoppingEnabled()
                        && plugin.getConfigManager().isIslandJumpSwitchEnabled()) {
                    int currentIsland = session.getIslandIndex();
                    // When the Z boundary is crossed the player is in the void gap
                    // between slots, so getIslandIndex(to) returns -1. Derive the
                    // target from the crossing direction instead of the gap position
                    // (which is what made jump-switching never trigger).
                    int targetIsland = GridCalculator.getIslandIndex(map, to);
                    if (targetIsland < 0) {
                        if (to.getBlockZ() > bounds[5]) {
                            targetIsland = currentIsland + 1;
                        } else if (to.getBlockZ() < bounds[2]) {
                            targetIsland = currentIsland - 1;
                        }
                    }
                    if (targetIsland >= 0 && targetIsland != currentIsland
                            && targetIsland < map.getScale()
                            && Math.abs(targetIsland - currentIsland) <= 1
                            && !switchingPlayers.contains(player.getUniqueId())) {
                        java.util.List<net.gravijet.fastbuilder.map.IslandInstance> islandList =
                                plugin.getMapManager().getIslands(map.getName());
                        if (islandList != null && targetIsland < islandList.size()) {
                            net.gravijet.fastbuilder.map.IslandInstance targetInstance =
                                    islandList.get(targetIsland);
                            if (!targetInstance.isOccupied()) {
                                java.util.UUID switchUuid = player.getUniqueId();
                                switchingPlayers.add(switchUuid);
                                // Suppress death-check task during the switch frame
                                plugin.getGameplayManager().markIslandHopping(switchUuid);
                                player.teleport(plugin.getGameplayManager().getEffectiveSpawn(
                                        switchUuid, map, targetIsland));
                                plugin.getGameplayManager().switchIsland(player, map, session, targetIsland);
                                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                    switchingPlayers.remove(switchUuid);
                                    plugin.getGameplayManager().unmarkIslandHopping(switchUuid);
                                }, 20L);
                                return;
                            }
                        }
                    }
                }

                // No hop — arm cooldown and trigger fall/reset
                fallCooldown.put(player.getUniqueId(), now);
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
            // Detect at deathY+1.8 so the player is reset before visually falling below deathY.
            // Effective deathY drops with a lowered end island so the player isn't failed on the
            // way down to a finish platform they set below the island floor.
            int effectiveDeathY = plugin.getGameplayManager()
                    .getEffectiveDeathY(player.getUniqueId(), mapForVoid);
            inVoid = to.getY() < effectiveDeathY + 1.8;
        } else {
            // Same rule as the deathY branch: a lowered end island drops the void floor with it.
            int yFloorDrop = 0;
            if (mapForVoid != null && mapForVoid.hasCustomLength()) {
                net.gravijet.fastbuilder.player.PlayerData voidData =
                        plugin.getPlayerManager().getCachedData(player.getUniqueId());
                if (voidData != null) {
                    yFloorDrop = Math.min(0, voidData.getCustomLengthY(mapForVoid.getName()));
                }
            }
            inVoid = to.getBlockY() < bounds[1] - maxDist + yFloorDrop;
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
     * Cancel all explosions (TNT, creepers, etc.) within any active island region.
     * This prevents TNT placed at the very end of a run from exploding during/after reset.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Location epicenter = event.getLocation();
        String explosionWorld = epicenter.getWorld().getName();
        for (MapData map : plugin.getMapManager().getAllMaps()) {
            if (!map.isEnabled()) continue;
            if (!explosionWorld.equals(map.getWorldName())) continue;
            java.util.List<net.gravijet.fastbuilder.map.IslandInstance> islands =
                    plugin.getMapManager().getIslands(map.getName());
            if (islands == null) continue;
            int maxDist = plugin.getConfigManager().getMaxDistance();
            for (int i = 0; i < islands.size(); i++) {
                if (!islands.get(i).isOccupied()) continue;
                int[] bounds = GridCalculator.getIslandBounds(map, i);
                // Check if epicenter is within the buildable region of this island
                if (epicenter.getBlockX() >= bounds[0] - maxDist
                        && epicenter.getBlockX() <= bounds[3] + 10
                        && epicenter.getBlockY() >= bounds[1] - 5
                        && epicenter.getBlockY() <= bounds[4] + 64
                        && epicenter.getBlockZ() >= bounds[2]
                        && epicenter.getBlockZ() <= bounds[5]) {
                    event.setCancelled(true);
                    return;
                }
            }
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

        // Building on and against the player's own end island is always allowed. Its region can sit
        // outside the island's X/Y/Z box (custom distance, custom Y offset, its own footprint), so
        // the corridor check below would reject it and the finish platform would be unbuildable.
        // The region is padded by 1 so blocks can be placed against its faces, not just on top.
        if (plugin.getGameplayManager() != null) {
            int[] end = plugin.getGameplayManager().getEndIslandRegion(player.getUniqueId());
            if (end != null) {
                int bx = blockLoc.getBlockX(), by = blockLoc.getBlockY(), bz = blockLoc.getBlockZ();
                if (bx >= end[0] - 1 && bx <= end[0] + end[3]
                        && by >= end[1] - 1 && by <= end[1] + end[4] + 64
                        && bz >= end[2] - 1 && bz <= end[2] + end[5]) {
                    return true;
                }
            }
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

            // Y: 2 blocks below the schematic base to 64 blocks above the schematic top.
            // On custom-length maps the player can drop the end island below the island floor
            // with the Y offset; the buildable band has to follow it down, or the whole approach
            // to a lowered finish platform (and the platform itself) is unbuildable.
            int yFloorDrop = 0;
            if (map.hasCustomLength()) {
                net.gravijet.fastbuilder.player.PlayerData pData =
                        plugin.getPlayerManager().getCachedData(player.getUniqueId());
                if (pData != null) {
                    yFloorDrop = Math.min(0, pData.getCustomLengthY(map.getName()));
                }
            }
            boolean yInBounds = by >= islandMin.getBlockY() - 2 + yFloorDrop
                    && by <= islandMax.getBlockY() + 64;

            // X (build direction): the block must be within 30 blocks of the furthest
            // already-placed block (or the island's own far X edge if none placed yet).
            // This means each block extends the reachable zone by up to 30 blocks.
            int xFrontier = islandMax.getBlockX();
            RunSession buildSession = plugin.getGameplayManager() != null
                    ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
            if (buildSession != null && buildSession.getMaxPlacedX() > xFrontier) {
                xFrontier = buildSession.getMaxPlacedX();
            }
            boolean xInBounds = bx >= islandMin.getBlockX()
                    && bx <= xFrontier + 30;

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
            if (blockIsland == playerIsland) return true;
        }

        return player.hasPermission("fastbuilder.admin");
    }
}
