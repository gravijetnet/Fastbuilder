package net.gravijet.fastbuilder.gameplay;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tracks an active bridging run for a single player.
 */
public class RunSession {

    private final UUID playerUuid;
    private final String mapName;
    private final int islandIndex;

    private long startTime = -1;
    private boolean running = false;
    private boolean finished = false;

    // Blocks placed during this run (for reset/replay)
    private final List<Location> placedBlocks = new ArrayList<>();

    // Session best times (for scoreboard top 3)
    private final List<Long> sessionBests = new ArrayList<>();

    // Practice mode - no stats recorded
    private boolean practiceMode = false;

    public RunSession(UUID playerUuid, String mapName, int islandIndex) {
        this.playerUuid = playerUuid;
        this.mapName = mapName;
        this.islandIndex = islandIndex;
    }

    /**
     * Start the timer. Called on first block placement.
     */
    public void start() {
        if (!running && !finished) {
            startTime = System.currentTimeMillis();
            running = true;
        }
    }

    /**
     * Finish the run. Returns elapsed time in millis.
     */
    public long finish() {
        if (!running) return -1;
        running = false;
        finished = true;
        return getElapsed();
    }

    /**
     * Reset the run state for a new attempt.
     */
    public void reset() {
        startTime = -1;
        running = false;
        finished = false;
        placedBlocks.clear();
    }

    /**
     * Get elapsed time in milliseconds.
     */
    public long getElapsed() {
        if (startTime < 0) return 0;
        if (running) return System.currentTimeMillis() - startTime;
        return System.currentTimeMillis() - startTime; // snapshot at finish
    }

    /**
     * Get elapsed time at finish (frozen).
     */
    public long getFinishTime() {
        if (startTime < 0) return 0;
        return finished ? (System.currentTimeMillis() - startTime) : getElapsed();
    }

    public void addPlacedBlock(Location loc) {
        placedBlocks.add(loc.clone());
    }

    public List<Location> getPlacedBlocks() {
        return placedBlocks;
    }

    public void addSessionBest(long time) {
        sessionBests.add(time);
        // Sort ascending
        java.util.Collections.sort(sessionBests);
        // Keep only top 3
        while (sessionBests.size() > 3) {
            sessionBests.remove(sessionBests.size() - 1);
        }
    }

    public List<Long> getSessionBests() {
        return sessionBests;
    }

    // --- Getters/Setters ---

    public UUID getPlayerUuid() { return playerUuid; }
    public String getMapName() { return mapName; }
    public int getIslandIndex() { return islandIndex; }
    public long getStartTime() { return startTime; }
    public boolean isRunning() { return running; }
    public boolean isFinished() { return finished; }
    public boolean isPracticeMode() { return practiceMode; }
    public void setPracticeMode(boolean practiceMode) { this.practiceMode = practiceMode; }
}
