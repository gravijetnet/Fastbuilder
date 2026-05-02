package net.gravijet.fastbuilder.replay;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Complete replay data for one run.
 */
public class ReplayData {

    private final UUID playerUuid;
    private final String playerName;
    // Full display tag (rank prefix + name) captured at recording time. Empty on old replays.
    private final String playerDisplayTag;
    private final String mapName;
    private final int islandIndex;
    private final long timestamp; // when the run happened
    private final boolean successful;
    private final long runTimeMillis;
    private final List<ReplayFrame> frames;
    // Blocks that were already placed on the island when recording started
    // (e.g. practice blocks, admin build-mode blocks)
    private final List<ReplayFrame.BlockPlacement> initialBlocks;
    // Custom length (blocks) active during this run. 0 = normal mode (no custom length).
    private final int customLength;

    public ReplayData(UUID playerUuid, String playerName, String mapName, int islandIndex,
                      long timestamp, boolean successful, long runTimeMillis, List<ReplayFrame> frames) {
        this(playerUuid, playerName, "", mapName, islandIndex, timestamp, successful, runTimeMillis,
                frames, new ArrayList<>(), 0);
    }

    public ReplayData(UUID playerUuid, String playerName, String mapName, int islandIndex,
                      long timestamp, boolean successful, long runTimeMillis, List<ReplayFrame> frames,
                      List<ReplayFrame.BlockPlacement> initialBlocks) {
        this(playerUuid, playerName, "", mapName, islandIndex, timestamp, successful, runTimeMillis,
                frames, initialBlocks, 0);
    }

    public ReplayData(UUID playerUuid, String playerName, String playerDisplayTag, String mapName,
                      int islandIndex, long timestamp, boolean successful, long runTimeMillis,
                      List<ReplayFrame> frames, List<ReplayFrame.BlockPlacement> initialBlocks) {
        this(playerUuid, playerName, playerDisplayTag, mapName, islandIndex, timestamp, successful,
                runTimeMillis, frames, initialBlocks, 0);
    }

    public ReplayData(UUID playerUuid, String playerName, String playerDisplayTag, String mapName,
                      int islandIndex, long timestamp, boolean successful, long runTimeMillis,
                      List<ReplayFrame> frames, List<ReplayFrame.BlockPlacement> initialBlocks,
                      int customLength) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.playerDisplayTag = playerDisplayTag != null ? playerDisplayTag : "";
        this.mapName = mapName;
        this.islandIndex = islandIndex;
        this.timestamp = timestamp;
        this.successful = successful;
        this.runTimeMillis = runTimeMillis;
        this.frames = frames;
        this.initialBlocks = initialBlocks != null ? initialBlocks : new ArrayList<>();
        this.customLength = customLength;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public String getPlayerDisplayTag() { return playerDisplayTag; }
    public String getMapName() { return mapName; }
    public int getIslandIndex() { return islandIndex; }
    public long getTimestamp() { return timestamp; }
    public boolean isSuccessful() { return successful; }
    public long getRunTimeMillis() { return runTimeMillis; }
    public List<ReplayFrame> getFrames() { return frames; }
    public List<ReplayFrame.BlockPlacement> getInitialBlocks() { return initialBlocks; }
    public int getCustomLength() { return customLength; }
    public int getTotalTicks() { return frames.isEmpty() ? 0 : frames.get(frames.size() - 1).getTick(); }

    public int getBlocksPlaced() {
        int count = 0;
        for (ReplayFrame frame : frames) {
            if (frame.hasBlockPlacement()) count++;
        }
        return count;
    }

    /**
     * Generate a unique filename for this replay.
     */
    public String getFileName() {
        return playerUuid.toString() + "_" + mapName.toLowerCase() + "_" + timestamp + ".replay";
    }
}
