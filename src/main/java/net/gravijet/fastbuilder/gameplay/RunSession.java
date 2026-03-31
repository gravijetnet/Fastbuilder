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
    private long finishTimeMs = -1;
    private boolean running = false;
    private boolean finished = false;

    // Blocks placed during this run (for reset/replay)
    private final List<Location> placedBlocks = new ArrayList<>();

    // Blocks placed while in practice mode (tracked separately for anti-exploit clearing)
    private final List<Location> practiceBlocks = new ArrayList<>();

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
        finishTimeMs = System.currentTimeMillis() - startTime;
        running = false;
        finished = true;
        return finishTimeMs;
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
        if (finished) return finishTimeMs;
        if (running) return System.currentTimeMillis() - startTime;
        return 0;
    }

    /**
     * Get elapsed time at finish (frozen).
     */
    public long getFinishTime() {
        return finishTimeMs > 0 ? finishTimeMs : getElapsed();
    }

    public void addPlacedBlock(Location loc) {
        placedBlocks.add(loc.clone());
        if (practiceMode) {
            practiceBlocks.add(loc.clone());
        }
    }

    public List<Location> getPlacedBlocks() {
        return placedBlocks;
    }

    public List<Location> getPracticeBlocks() {
        return practiceBlocks;
    }

    /**
     * Check if there are practice blocks that need clearing before a real run.
     */
    public boolean hasPracticeBlocks() {
        return !practiceBlocks.isEmpty();
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
