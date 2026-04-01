package net.gravijet.fastbuilder.gameplay;

import org.bukkit.Location;

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

    // All blocks placed during this run
    private final List<Location> placedBlocks = new ArrayList<>();

    // Practice blocks (lime STAINED_CLAY:5) placed while in practice mode
    private final List<Location> practiceBlocks = new ArrayList<>();

    // Session best times (top 3 for scoreboard)
    private final List<Long> sessionBests = new ArrayList<>();

    // Practice mode - no stats recorded, practice blocks persist across resets
    private boolean practiceMode = false;

    public RunSession(UUID playerUuid, String mapName, int islandIndex) {
        this.playerUuid = playerUuid;
        this.mapName = mapName;
        this.islandIndex = islandIndex;
    }

    public void start() {
        if (!running && !finished) {
            startTime = System.currentTimeMillis();
            running = true;
        }
    }

    public long finish() {
        if (!running) return -1;
        finishTimeMs = System.currentTimeMillis() - startTime;
        running = false;
        finished = true;
        return finishTimeMs;
    }

    public void reset() {
        startTime = -1;
        running = false;
        finished = false;
        placedBlocks.clear();
        // Note: practiceBlocks is intentionally NOT cleared here.
        // It is cleared in GameplayManager.resetRun() selectively
        // and in handleSettingsClick when practice mode is toggled off.
    }

    public long getElapsed() {
        if (startTime < 0) return 0;
        if (finished) return finishTimeMs;
        if (running) return System.currentTimeMillis() - startTime;
        return 0;
    }

    public long getFinishTime() {
        return finishTimeMs > 0 ? finishTimeMs : getElapsed();
    }

    /**
     * Add a placed block to tracking.
     * @param loc         The block location
     * @param isPractice  True if this is a practice block (should persist across normal resets)
     */
    public void addPlacedBlock(Location loc, boolean isPractice) {
        placedBlocks.add(loc.clone());
        if (isPractice) {
            practiceBlocks.add(loc.clone());
        }
    }

    /** Legacy overload - always marks as non-practice. */
    public void addPlacedBlock(Location loc) {
        addPlacedBlock(loc, false);
    }

    public List<Location> getPlacedBlocks() { return placedBlocks; }
    public List<Location> getPracticeBlocks() { return practiceBlocks; }

    public boolean hasPracticeBlocks() { return !practiceBlocks.isEmpty(); }

    public void addSessionBest(long time) {
        sessionBests.add(time);
        java.util.Collections.sort(sessionBests);
        while (sessionBests.size() > 3) sessionBests.remove(sessionBests.size() - 1);
    }

    public List<Long> getSessionBests() { return sessionBests; }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getMapName() { return mapName; }
    public int getIslandIndex() { return islandIndex; }
    public long getStartTime() { return startTime; }
    public boolean isRunning() { return running; }
    public boolean isFinished() { return finished; }
    public boolean isPracticeMode() { return practiceMode; }
    public void setPracticeMode(boolean practiceMode) { this.practiceMode = practiceMode; }
}
