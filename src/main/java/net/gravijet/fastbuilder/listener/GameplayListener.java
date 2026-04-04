package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.GameplayManager;
import net.gravijet.fastbuilder.gameplay.RunSession;
import net.gravijet.fastbuilder.map.MapData;
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
        if (session == null || !session.isRunning()) return;

        MapData map = plugin.getMapManager().getMap(session.getMapName());
        if (map == null) return;

        Location to = event.getTo();
        if (isInFinishZone(map, session.getIslandIndex(), to)) {
            // In touch mode, also require the player to be standing on a pressure plate
            if (plugin.getConfigManager().isFinishTouchMode()) {
                org.bukkit.block.Block below = to.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
                if (below.getTypeId() != 70 && below.getTypeId() != 72) return; // 70=stone plate, 72=wood plate
            }
            gm.onFinish(player);
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
     * Triggers if the player is within X/Z bounds and within Y range up to
     * finishMaxY + configurable height tolerance (for aerial/jumping detection).
     */
    private boolean isInFinishZone(MapData map, int islandIndex, Location loc) {
        int offsetZ = islandIndex * map.getDistance();

        int fMinX = map.getOriginX() + map.getFinishMinX();
        int fMinY = map.getOriginY() + map.getFinishMinY();
        int fMinZ = map.getOriginZ() + offsetZ + map.getFinishMinZ();
        int fMaxX = map.getOriginX() + map.getFinishMaxX();
        int fMaxY = map.getOriginY() + map.getFinishMaxY();
        int fMaxZ = map.getOriginZ() + offsetZ + map.getFinishMaxZ();

        int heightTolerance = plugin.getConfigManager().getFinishHeightTolerance();

        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();

        return bx >= fMinX && bx <= fMaxX
                && by >= fMinY && by <= fMaxY + heightTolerance
                && bz >= fMinZ && bz <= fMaxZ;
    }
}
