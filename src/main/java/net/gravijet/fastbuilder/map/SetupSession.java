package net.gravijet.fastbuilder.map;

import org.bukkit.Location;

import java.util.UUID;

/**
 * State machine tracking an admin's map setup progress.
 *
 * Flow:
 * 1. SELECTING_ISLAND  - Left/right-click blaze rod to set island pos1/pos2
 * 2. SELECTING_SPAWN   - Right-click blaze rod to set spawn point
 * 3. SELECTING_FINISH  - Left/right-click blaze rod to set finish zone
 * 4. AWAITING_FINALIZE - Type /map setup finish to save template
 * 5. AWAITING_NAME     - Type /map setup name <name> to complete
 */
public class SetupSession {

    public enum State {
        SELECTING_ISLAND,
        SELECTING_SPAWN,
        SELECTING_FINISH,
        AWAITING_NAME
    }

    private final UUID playerUuid;
    private State state;

    // Map origin (where the admin was teleported)
    private Location setupOrigin;

    // Island area selection (absolute world coordinates)
    private Location islandPos1;
    private Location islandPos2;

    // Spawn point (absolute world coordinates)
    private Location spawnPoint;

    // Finish zone (absolute world coordinates)
    private Location finishPos1;
    private Location finishPos2;

    public SetupSession(UUID playerUuid, Location setupOrigin) {
        this.playerUuid = playerUuid;
        this.setupOrigin = setupOrigin;
        this.state = State.SELECTING_ISLAND;
    }

    // --- State transitions ---

    /**
     * Attempt to advance from SELECTING_ISLAND to SELECTING_SPAWN.
     * Requires both island positions to be set.
     */
    public boolean advanceToSpawn() {
        if (state != State.SELECTING_ISLAND) return false;
        if (islandPos1 == null || islandPos2 == null) return false;
        state = State.SELECTING_SPAWN;
        return true;
    }

    /**
     * Attempt to advance from SELECTING_SPAWN to SELECTING_FINISH.
     * Requires spawn point to be set.
     */
    public boolean advanceToFinish() {
        if (state != State.SELECTING_SPAWN) return false;
        if (spawnPoint == null) return false;
        state = State.SELECTING_FINISH;
        return true;
    }

    /**
     * Attempt to finalize: advance from SELECTING_FINISH to AWAITING_NAME.
     * Requires both finish zone positions to be set.
     */
    public boolean finalize_() {
        if (state != State.SELECTING_FINISH) return false;
        if (finishPos1 == null || finishPos2 == null) return false;
        state = State.AWAITING_NAME;
        return true;
    }

    /**
     * Check if the current step can advance via /map setup continue.
     */
    public boolean canContinue() {
        switch (state) {
            case SELECTING_ISLAND:
                return islandPos1 != null && islandPos2 != null;
            case SELECTING_SPAWN:
                return spawnPoint != null;
            default:
                return false;
        }
    }

    /**
     * Check if the setup can be finalized via /map setup finish.
     */
    public boolean canFinalize() {
        return state == State.SELECTING_FINISH && finishPos1 != null && finishPos2 != null;
    }

    // --- Computed values ---

    /**
     * Get the normalized min corner of the island selection.
     */
    public Location getIslandMin() {
        if (islandPos1 == null || islandPos2 == null) return null;
        return new Location(islandPos1.getWorld(),
                Math.min(islandPos1.getBlockX(), islandPos2.getBlockX()),
                Math.min(islandPos1.getBlockY(), islandPos2.getBlockY()),
                Math.min(islandPos1.getBlockZ(), islandPos2.getBlockZ()));
    }

    /**
     * Get the normalized max corner of the island selection.
     */
    public Location getIslandMax() {
        if (islandPos1 == null || islandPos2 == null) return null;
        return new Location(islandPos1.getWorld(),
                Math.max(islandPos1.getBlockX(), islandPos2.getBlockX()),
                Math.max(islandPos1.getBlockY(), islandPos2.getBlockY()),
                Math.max(islandPos1.getBlockZ(), islandPos2.getBlockZ()));
    }

    public int getIslandWidth() {
        Location min = getIslandMin();
        Location max = getIslandMax();
        if (min == null || max == null) return 0;
        return max.getBlockX() - min.getBlockX() + 1;
    }

    public int getIslandHeight() {
        Location min = getIslandMin();
        Location max = getIslandMax();
        if (min == null || max == null) return 0;
        return max.getBlockY() - min.getBlockY() + 1;
    }

    public int getIslandLength() {
        Location min = getIslandMin();
        Location max = getIslandMax();
        if (min == null || max == null) return 0;
        return max.getBlockZ() - min.getBlockZ() + 1;
    }

    /**
     * Get the spawn offset relative to the island min corner.
     */
    public double getSpawnOffsetX() {
        if (spawnPoint == null || getIslandMin() == null) return 0;
        return spawnPoint.getX() - getIslandMin().getBlockX();
    }

    public double getSpawnOffsetY() {
        if (spawnPoint == null || getIslandMin() == null) return 0;
        return spawnPoint.getY() - getIslandMin().getBlockY();
    }

    public double getSpawnOffsetZ() {
        if (spawnPoint == null || getIslandMin() == null) return 0;
        return spawnPoint.getZ() - getIslandMin().getBlockZ();
    }

    /**
     * Get the normalized finish zone min, relative to island min corner.
     */
    public int getFinishMinX() {
        if (finishPos1 == null || finishPos2 == null || getIslandMin() == null) return 0;
        return Math.min(finishPos1.getBlockX(), finishPos2.getBlockX()) - getIslandMin().getBlockX();
    }

    public int getFinishMinY() {
        if (finishPos1 == null || finishPos2 == null || getIslandMin() == null) return 0;
        return Math.min(finishPos1.getBlockY(), finishPos2.getBlockY()) - getIslandMin().getBlockY();
    }

    public int getFinishMinZ() {
        if (finishPos1 == null || finishPos2 == null || getIslandMin() == null) return 0;
        return Math.min(finishPos1.getBlockZ(), finishPos2.getBlockZ()) - getIslandMin().getBlockZ();
    }

    public int getFinishMaxX() {
        if (finishPos1 == null || finishPos2 == null || getIslandMin() == null) return 0;
        return Math.max(finishPos1.getBlockX(), finishPos2.getBlockX()) - getIslandMin().getBlockX();
    }

    public int getFinishMaxY() {
        if (finishPos1 == null || finishPos2 == null || getIslandMin() == null) return 0;
        return Math.max(finishPos1.getBlockY(), finishPos2.getBlockY()) - getIslandMin().getBlockY();
    }

    public int getFinishMaxZ() {
        if (finishPos1 == null || finishPos2 == null || getIslandMin() == null) return 0;
        return Math.max(finishPos1.getBlockZ(), finishPos2.getBlockZ()) - getIslandMin().getBlockZ();
    }

    // --- Getters/Setters ---

    public UUID getPlayerUuid() { return playerUuid; }
    public State getState() { return state; }
    public Location getSetupOrigin() { return setupOrigin; }

    public Location getIslandPos1() { return islandPos1; }
    public void setIslandPos1(Location pos) { this.islandPos1 = pos; }

    public Location getIslandPos2() { return islandPos2; }
    public void setIslandPos2(Location pos) { this.islandPos2 = pos; }

    public Location getSpawnPoint() { return spawnPoint; }
    public void setSpawnPoint(Location pos) { this.spawnPoint = pos; }

    public Location getFinishPos1() { return finishPos1; }
    public void setFinishPos1(Location pos) { this.finishPos1 = pos; }

    public Location getFinishPos2() { return finishPos2; }
    public void setFinishPos2(Location pos) { this.finishPos2 = pos; }
}
