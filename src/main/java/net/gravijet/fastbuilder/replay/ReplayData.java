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
    private final String mapName;
    private final int islandIndex;
    private final long timestamp; // when the run happened
    private final boolean successful;
    private final long runTimeMillis;
    private final List<ReplayFrame> frames;

    public ReplayData(UUID playerUuid, String playerName, String mapName, int islandIndex,
                      long timestamp, boolean successful, long runTimeMillis, List<ReplayFrame> frames) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.mapName = mapName;
        this.islandIndex = islandIndex;
        this.timestamp = timestamp;
        this.successful = successful;
        this.runTimeMillis = runTimeMillis;
        this.frames = frames;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public String getMapName() { return mapName; }
    public int getIslandIndex() { return islandIndex; }
    public long getTimestamp() { return timestamp; }
    public boolean isSuccessful() { return successful; }
    public long getRunTimeMillis() { return runTimeMillis; }
    public List<ReplayFrame> getFrames() { return frames; }
    public int getTotalTicks() { return frames.isEmpty() ? 0 : frames.get(frames.size() - 1).getTick(); }

    /**
     * Generate a unique filename for this replay.
     */
    public String getFileName() {
        return playerUuid.toString() + "_" + mapName.toLowerCase() + "_" + timestamp + ".replay";
    }
}
