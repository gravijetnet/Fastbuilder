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
    // True while the reset animation is playing — blocks double-resets and phantom block placement.
    private boolean resetting = false;

    // All blocks placed during this run
    private final List<Location> placedBlocks = new ArrayList<>();
    // Tracks the highest X coordinate of any placed block (updated in addPlacedBlock)
    private int maxPlacedX = Integer.MIN_VALUE;

    // Practice blocks (lime STAINED_CLAY:5) placed while in practice mode
    private final List<Location> practiceBlocks = new ArrayList<>();

    // Original block states before placement (for map environment restoration)
    private final java.util.HashMap<String, int[]> originalBlockStates = new java.util.HashMap<>();

    // Session best times (top 3 for scoreboard)
    private final List<Long> sessionBests = new ArrayList<>();

    // Practice mode - no stats recorded, practice blocks persist across resets
    private boolean practiceMode = false;

    // Sticky flag: set to true the first time a practice block is placed during this run.
    // Cleared only by reset(). Prevents the "toggle practice off mid-run" loophole that
    // would otherwise let a practice-aided run count as a real PB.
    private boolean practiceUsedThisRun = false;

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
        long rawMs = System.currentTimeMillis() - startTime;
        if (rawMs < 0) rawMs = 0; // guard against NTP clock adjustment
        finishTimeMs = net.gravijet.fastbuilder.util.TimeUtil.roundTo50(rawMs);
        running = false;
        finished = true;
        return finishTimeMs;
    }

    public void reset() {
        startTime = -1;
        running = false;
        finished = false;
        resetting = false;
        practiceUsedThisRun = false;
        placedBlocks.clear();
        originalBlockStates.clear();
        maxPlacedX = Integer.MIN_VALUE;
        // Note: practiceBlocks is intentionally NOT cleared here.
        // It is cleared in GameplayManager.resetRun() selectively
        // and in handleSettingsClick when practice mode is toggled off.
    }

    /** Mark that the reset animation is in progress. Prevents double-resets and phantom block placement. */
    public void setResetting(boolean resetting) { this.resetting = resetting; }
    public boolean isResetting() { return resetting; }

    public long getElapsed() {
        if (startTime < 0) return 0;
        if (finished) return finishTimeMs;
        if (running) return System.currentTimeMillis() - startTime;
        return 0;
    }

    /** Return the rounded elapsed time without committing the finished state. */
    public long peekElapsed() {
        if (startTime < 0 || !running) return 0;
        long rawMs = System.currentTimeMillis() - startTime;
        if (rawMs < 0) rawMs = 0;
        return net.gravijet.fastbuilder.util.TimeUtil.roundTo50(rawMs);
    }

    public long getFinishTime() {
        if (finishTimeMs > 0) return finishTimeMs;
        if (running) return getElapsed();
        return -1;
    }

    /**
     * Add a placed block to tracking, saving original block state for later restoration.
     * @param loc         The block location
     * @param isPractice  True if this is a practice block (should persist across normal resets)
     * @param origTypeId  The original block type ID before placement
     * @param origData    The original block data byte before placement
     */
    public void addPlacedBlock(Location loc, boolean isPractice, int origTypeId, byte origData) {
        Location clone = loc.clone();
        placedBlocks.add(clone);
        if (isPractice) {
            practiceBlocks.add(clone);
            practiceUsedThisRun = true;
        }
        if (loc.getBlockX() > maxPlacedX) maxPlacedX = loc.getBlockX();
        // Only record original block state on first placement — subsequent placements at the same
        // location (player replacing their own block) must not overwrite the true pre-run state.
        String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        originalBlockStates.putIfAbsent(key, new int[]{origTypeId, origData});
    }

    /**
     * Add a placed block to tracking.
     * @param loc         The block location
     * @param isPractice  True if this is a practice block (should persist across normal resets)
     */
    public void addPlacedBlock(Location loc, boolean isPractice) {
        addPlacedBlock(loc, isPractice, 0, (byte) 0);
    }

    /** Legacy overload - always marks as non-practice. */
    public void addPlacedBlock(Location loc) {
        addPlacedBlock(loc, false, 0, (byte) 0);
    }

    /**
     * Get the original block state at a location before this run placed a block there.
     * @return int[]{typeId, data} or null if not tracked
     */
    public int[] getOriginalBlockState(Location loc) {
        String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        return originalBlockStates.get(key);
    }

    /** Returns the highest X coordinate of any placed block, or Integer.MIN_VALUE if none placed. */
    public int getMaxPlacedX() { return maxPlacedX; }

    public List<Location> getPlacedBlocks() { return placedBlocks; }
    public List<Location> getPracticeBlocks() { return practiceBlocks; }
    public java.util.Map<String, int[]> getOriginalBlockStates() { return java.util.Collections.unmodifiableMap(originalBlockStates); }

    public boolean hasPracticeBlocks() { return !practiceBlocks.isEmpty(); }

    /**
     * Sticky: returns true if any practice block was placed during the current run,
     * even if those blocks were later cleared (e.g. by toggling practice mode off).
     * Used to disqualify the run from personal-best ranking.
     */
    public boolean practiceUsedThisRun() { return practiceUsedThisRun; }

    public void addSessionBest(long time) {
        sessionBests.add(time);
        java.util.Collections.sort(sessionBests);
        while (sessionBests.size() > 3) sessionBests.remove(sessionBests.size() - 1);
    }

    public List<Long> getSessionBests() { return sessionBests; }

    /** Restore the timer state from a previous session (used on island switch to avoid resetting the clock). */
    public void resumeTimerFrom(long startTime, boolean running) {
        this.startTime = startTime;
        this.running = running;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getMapName() { return mapName; }
    public int getIslandIndex() { return islandIndex; }
    public long getStartTime() { return startTime; }
    public boolean isRunning() { return running; }
    public boolean isFinished() { return finished; }
    public boolean isPracticeMode() { return practiceMode; }
    public void setPracticeMode(boolean practiceMode) { this.practiceMode = practiceMode; }
}
