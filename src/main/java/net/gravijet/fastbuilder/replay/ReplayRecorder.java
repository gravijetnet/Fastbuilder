package net.gravijet.fastbuilder.replay;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/**
 * Records a player's movements and block placements tick by tick.
 */
public class ReplayRecorder {

    private final UUID playerUuid;
    private final String playerName;
    private final String mapName;
    private final int islandIndex;
    private final long startTimestamp;
    private final List<ReplayFrame.BlockPlacement> initialBlocks;

    private int currentTick = 0;
    private final List<ReplayFrame> frames = new ArrayList<>();

    // Queued block placements to be attached to the next tick
    private final Queue<ReplayFrame.BlockPlacement> pendingPlacements = new LinkedList<>();

    // Arm-swing flag: set by the animation event, consumed once per tick
    private boolean pendingArmSwing = false;

    public ReplayRecorder(UUID playerUuid, String playerName, String mapName, int islandIndex) {
        this(playerUuid, playerName, mapName, islandIndex, Collections.emptyList());
    }

    public ReplayRecorder(UUID playerUuid, String playerName, String mapName, int islandIndex,
                          List<ReplayFrame.BlockPlacement> initialBlocks) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.mapName = mapName;
        this.islandIndex = islandIndex;
        this.startTimestamp = System.currentTimeMillis();
        this.initialBlocks = new ArrayList<>(initialBlocks);
    }

    /**
     * Record a tick. Called every server tick while recording.
     */
    @SuppressWarnings("deprecation")
    public void recordTick(Player player) {
        Location loc = player.getLocation();
        boolean sneaking   = player.isSneaking();
        boolean sprinting  = player.isSprinting();
        boolean swingArm   = pendingArmSwing;
        pendingArmSwing = false;

        // Record hand item (v4)
        org.bukkit.inventory.ItemStack handItem = player.getItemInHand();
        int handItemId = (handItem != null && handItem.getType() != org.bukkit.Material.AIR)
                ? handItem.getTypeId() : 0;
        byte handItemData = (handItem != null && handItem.getType() != org.bukkit.Material.AIR)
                ? handItem.getData().getData() : 0;

        ReplayFrame.BlockPlacement placement = pendingPlacements.poll();

        frames.add(new ReplayFrame(
                currentTick,
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(), loc.getYaw(),
                sneaking, sprinting, swingArm,
                handItemId, handItemData,
                placement
        ));

        // If there are more pending placements, add extra frames for the same tick
        while (!pendingPlacements.isEmpty()) {
            ReplayFrame.BlockPlacement extra = pendingPlacements.poll();
            frames.add(new ReplayFrame(
                    currentTick,
                    loc.getX(), loc.getY(), loc.getZ(),
                    loc.getYaw(), loc.getPitch(), loc.getYaw(),
                    sneaking, sprinting, false,
                    handItemId, handItemData,
                    extra
            ));
        }

        currentTick++;
    }

    /**
     * Flag an arm swing for this tick. Called by PlayerAnimationEvent.
     * The flag is consumed on the next recordTick() call.
     */
    public void recordArmSwing() {
        pendingArmSwing = true;
    }

    /**
     * Queue a block placement to be recorded on the next tick.
     */
    public void recordBlockPlace(Location loc, int blockId, byte blockData) {
        pendingPlacements.add(new ReplayFrame.BlockPlacement(
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                blockId, blockData
        ));
    }

    /**
     * Build the final ReplayData.
     */
    public ReplayData build(boolean successful, long runTimeMillis) {
        return new ReplayData(
                playerUuid, playerName, mapName, islandIndex,
                startTimestamp, successful, runTimeMillis, new ArrayList<>(frames),
                new ArrayList<>(initialBlocks)
        );
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getMapName() { return mapName; }
}
