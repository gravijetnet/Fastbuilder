package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.GameplayManager;
import net.gravijet.fastbuilder.gameplay.RunSession;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.player.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Handles gameplay events: block placement (timer start), finish zone detection,
 * and player movement (fall detection handled in ProtectionListener).
 */
public class GameplayListener implements Listener {

    private final FastBuilder plugin;

    public GameplayListener(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        if (plugin.getReplayManager() == null) return;
        if (plugin.getReplayManager().isInPlayback(player.getUniqueId())) return;
        plugin.getReplayManager().recordArmSwing(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        GameplayManager gm = plugin.getGameplayManager();
        if (gm == null) return;

        // Skip admins in setup mode
        if (player.hasPermission("fastbuilder.admin") && plugin.getMapManager().hasSetupSession(player.getUniqueId())) {
            return;
        }

        // Skip players in replay
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            return;
        }

        gm.onBlockPlace(player, event.getBlock(), event.getBlockReplacedState());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if player actually moved blocks (not just head rotation)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        GameplayManager gm = plugin.getGameplayManager();
        if (gm == null) return;

        // Skip players in replay
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            return;
        }

        RunSession session = gm.getSession(player.getUniqueId());
        if (session == null) return;

        MapData map = plugin.getMapManager().getMap(session.getMapName());
        if (map == null) return;

        Location to = event.getTo();

        // Finish zone detection (only when timer is running)
        if (session.isRunning()) {
            if (isInFinishZone(map, session.getIslandIndex(), to, player)) {
                // In "touch" mode the player must be standing on a pressure plate inside the zone.
                // In "zone" mode (default) any block — including regular blocks — triggers the finish.
                if (plugin.getConfigManager().isFinishTouchMode()) {
                    org.bukkit.block.Block below = to.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
                    // Allow both stone plates (70), wood plates (72), AND any solid non-air block
                    if (below.getType() == org.bukkit.Material.AIR) return;
                }
                gm.onFinish(player);
                return;
            }
        }
    }

    /**
     * Cancel animation FallingBlocks from landing and placing a block.
     * This is the failsafe alongside the NMS dontSetBlock flag.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.FallingBlock)) return;
        GameplayManager gm = plugin.getGameplayManager();
        if (gm == null) return;

        java.util.UUID entityId = event.getEntity().getUniqueId();
        if (gm.isAnimationEntity(entityId)) {
            event.setCancelled(true);
            // Do NOT remove from animationEntities here — FallingBlocks can fire this event
            // more than once (bounce on the same surface or hit multiple blocks). Keeping the
            // UUID in the set ensures every subsequent landing attempt is also cancelled.
            // The 60L cleanup task in GameplayManager.spawnAnimationFallingBlock handles removal.
            event.getEntity().remove();
        }
    }

    /**
     * Check if a location is within the finish zone of a specific island.
     *
     * <ul>
     *   <li>Returns false immediately for infinite-mode maps (no finish zone).</li>
     *   <li>For end-island maps: derives the zone from the placed end island's bounds
     *       at the player's current custom length (X) and height offset (Y).</li>
     *   <li>For legacy/standard maps: checks the static finish zone, applying any
     *       active design-profile finish-zone override first.</li>
     *   <li>Triggers if the player is within X/Z bounds and within Y range up to
     *       finishMaxY + configurable height tolerance.</li>
     * </ul>
     */
    private boolean isInFinishZone(MapData map, int islandIndex, Location loc, Player player) {
        // Infinite maps have no finish condition
        if (map.isInfinite()) return false;

        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        int heightTolerance = plugin.getConfigManager().getFinishHeightTolerance();

        PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());

        // End-island mode: derive finish zone from the live end-island position
        if (map.hasEndIsland()) {
            int customLength = pData != null ? pData.getCustomLength(map.getName()) : 0;
            if (customLength <= 0) customLength = map.getBaseCustomLength();
            customLength = Math.max(map.getEffectiveMinCustomLength(),
                    Math.min(map.getEffectiveMaxCustomLength(), customLength));

            // Y adjustment from the Custom Length sub-menu
            int yAdjust = pData != null ? pData.getCustomLengthY(map.getName()) : 0;

            // End island is placed to the +X (east) side of the start island (1 block offset applied)
            int endX = map.getOriginX() + map.getIslandWidth() + customLength - 2;
            int endY = map.getOriginY() + map.getEndIslandYOffset() + yAdjust;
            int endZ = map.getOriginZ() + islandIndex * map.getActualZStep()
                    + map.getEndIslandZOffset();

            return bx >= endX && bx < endX + map.getEndIslandWidth()
                    && by >= endY && by <= endY + map.getEndIslandHeight() - 1 + heightTolerance
                    && bz >= endZ && bz < endZ + map.getEndIslandLength();
        }

        // Standard / legacy maps — check for active design-profile finish-zone override
        int offsetZ = islandIndex * map.getActualZStep();
        int fMinX, fMinY, fMinZ, fMaxX, fMaxY, fMaxZ;

        int[] finishOverride = pData != null ? pData.getActiveFinishZone(map.getName()) : null;
        if (finishOverride != null) {
            fMinX = map.getOriginX() + finishOverride[0];
            fMinY = map.getOriginY() + finishOverride[1];
            fMinZ = map.getOriginZ() + offsetZ + finishOverride[2];
            fMaxX = map.getOriginX() + finishOverride[3];
            fMaxY = map.getOriginY() + finishOverride[4];
            fMaxZ = map.getOriginZ() + offsetZ + finishOverride[5];
        } else {
            fMinX = map.getOriginX() + map.getFinishMinX();
            fMinY = map.getOriginY() + map.getFinishMinY();
            fMinZ = map.getOriginZ() + offsetZ + map.getFinishMinZ();
            fMaxX = map.getOriginX() + map.getFinishMaxX();
            fMaxY = map.getOriginY() + map.getFinishMaxY();
            fMaxZ = map.getOriginZ() + offsetZ + map.getFinishMaxZ();
        }

        // Legacy custom-length: reposition finish zone X based on player preference
        if (map.hasCustomLength()) {
            int customLength = pData != null ? pData.getCustomLength(map.getName()) : 0;
            if (customLength > 0) {
                customLength = Math.max(map.getMinCustomLength(),
                        Math.min(map.getMaxCustomLength(), customLength));
                int finishZoneWidth = fMaxX - fMinX;
                fMinX = map.getOriginX() + (int) map.getSpawnOffsetX() + customLength;
                fMaxX = fMinX + finishZoneWidth;
            }
        }

        return bx >= fMinX && bx <= fMaxX
                && by >= fMinY && by <= fMaxY + heightTolerance
                && bz >= fMinZ && bz <= fMaxZ;
    }
}
