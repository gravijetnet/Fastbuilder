package net.gravijet.fastbuilder.map;

import org.bukkit.Location;

import java.util.UUID;

/**
 * State machine tracking an admin's map setup progress.
 *
 * Flow:
 * 1. SELECTING_ISLAND  - Left/right-click blaze rod to set island pos1/pos2
 * 2. SELECTING_SPAWN   - Right-click blaze rod to set spawn point
 * 3. SELECTING_NPC     - Right-click blaze rod to set NPC location
 * 4. SELECTING_HOLOGRAM - Right-click blaze rod to set hologram location
 * 5. SELECTING_FINISH  - Left/right-click blaze rod to set finish zone
 * 6. AWAITING_NAME     - Type /map setup name <name> to complete
 */
public class SetupSession {

    public enum State {
        SELECTING_ISLAND,
        SELECTING_DIAGONAL,     // diagonal mode: admin right-clicks where island-1 min-X should be
        SELECTING_SPAWN,
        SELECTING_NPC,
        SELECTING_HOLOGRAM,
        SELECTING_FINISH,
        SELECTING_END_ISLAND,   // custom-length mode: select the end island region
        AWAITING_NAME
    }

    private final UUID playerUuid;
    private State state;
    private boolean forceSpawnLocation = false;
    // Set during setup if the admin flags this as an infinite map (no finish zone)
    private boolean infinite = false;
    // Set during setup if the admin uses --customlength (separate end island, real-time movement)
    private boolean customLengthMode = false;
    // Set during setup if the admin uses --diagonal; diagonalStepX computed from selection
    private boolean diagonalMode = false;
    private int diagonalStepX = 0;

    // Map origin (where the admin was teleported)
    private Location setupOrigin;

    // Island area selection (absolute world coordinates)
    private Location islandPos1;
    private Location islandPos2;

    // Spawn point (absolute world coordinates)
    private Location spawnPoint;

    // NPC point (absolute world coordinates)
    private Location npcPoint;

    // Hologram point (absolute world coordinates)
    private Location hologramPoint;

    // Finish zone (absolute world coordinates)
    private Location finishPos1;
    private Location finishPos2;

    // End island selection for custom-length mode (absolute world coordinates)
    private Location endIslandPos1;
    private Location endIslandPos2;

    public SetupSession(UUID playerUuid, Location setupOrigin) {
        this.playerUuid = playerUuid;
        this.setupOrigin = setupOrigin;
        this.state = State.SELECTING_ISLAND;
    }

    // --- State transitions ---

    /**
     * After island selection, advance to the diagonal-direction step (diagonal mode only).
     * The admin right-clicks where island-slot 1's min X corner should be to set the step.
     */
    public boolean advanceToDiagonal() {
        if (state != State.SELECTING_ISLAND) return false;
        if (islandPos1 == null || islandPos2 == null) return false;
        state = State.SELECTING_DIAGONAL;
        return true;
    }

    /**
     * Record the diagonal X step from a block click location and advance to spawn selection.
     * stepX = clickedX - islandMinX.
     */
    public boolean finalizeDiagonal(int clickedX) {
        if (state != State.SELECTING_DIAGONAL) return false;
        Location islandMin = getIslandMin();
        if (islandMin == null) return false;
        diagonalStepX = clickedX - islandMin.getBlockX();
        state = State.SELECTING_SPAWN;
        return true;
    }

    public boolean advanceToSpawn() {
        if (state != State.SELECTING_ISLAND) return false;
        if (islandPos1 == null || islandPos2 == null) return false;
        state = State.SELECTING_SPAWN;
        return true;
    }

    public boolean advanceToNpc() {
        if (state != State.SELECTING_SPAWN) return false;
        if (spawnPoint == null) return false;
        state = State.SELECTING_NPC;
        return true;
    }

    public boolean advanceToHologram() {
        if (state != State.SELECTING_NPC) return false;
        if (npcPoint == null) return false;
        state = State.SELECTING_HOLOGRAM;
        return true;
    }

    public boolean advanceToFinish() {
        if (state != State.SELECTING_HOLOGRAM) return false;
        if (hologramPoint == null) return false;
        state = State.SELECTING_FINISH;
        return true;
    }

    /**
     * Skips the finish zone selection step and advances directly to AWAITING_NAME.
     * Used for infinite maps that have no end island.
     */
    public boolean advanceToName() {
        if (state != State.SELECTING_HOLOGRAM) return false;
        if (hologramPoint == null) return false;
        state = State.AWAITING_NAME;
        return true;
    }

    /**
     * Advances from SELECTING_HOLOGRAM to SELECTING_END_ISLAND.
     * Used in custom-length mode: the admin separately selects the end island region.
     */
    public boolean advanceToEndIsland() {
        if (state != State.SELECTING_HOLOGRAM) return false;
        if (hologramPoint == null) return false;
        state = State.SELECTING_END_ISLAND;
        return true;
    }

    /**
     * Finalizes end island selection and advances to AWAITING_NAME.
     */
    public boolean finalizeEndIsland() {
        if (state != State.SELECTING_END_ISLAND) return false;
        if (endIslandPos1 == null || endIslandPos2 == null) return false;
        state = State.AWAITING_NAME;
        return true;
    }

    public boolean finalize_() {
        if (state != State.SELECTING_FINISH) return false;
        if (finishPos1 == null || finishPos2 == null) return false;
        state = State.AWAITING_NAME;
        return true;
    }

    public boolean canContinue() {
        switch (state) {
            case SELECTING_ISLAND:
                return islandPos1 != null && islandPos2 != null;
            case SELECTING_DIAGONAL:
                return false; // diagonal step is set via right-click, not /map setup continue
            case SELECTING_SPAWN:
                return spawnPoint != null;
            case SELECTING_NPC:
                return npcPoint != null;
            case SELECTING_HOLOGRAM:
                return hologramPoint != null;
            case SELECTING_END_ISLAND:
                return endIslandPos1 != null && endIslandPos2 != null;
            default:
                return false;
        }
    }

    public boolean canFinalize() {
        return state == State.SELECTING_FINISH && finishPos1 != null && finishPos2 != null;
    }

    // --- Computed values ---

    public Location getIslandMin() {
        if (islandPos1 == null || islandPos2 == null) return null;
        return new Location(islandPos1.getWorld(),
                Math.min(islandPos1.getBlockX(), islandPos2.getBlockX()),
                Math.min(islandPos1.getBlockY(), islandPos2.getBlockY()),
                Math.min(islandPos1.getBlockZ(), islandPos2.getBlockZ()));
    }

    public Location getIslandMax() {
        if (islandPos1 == null || islandPos2 == null) return null;
        return new Location(islandPos1.getWorld(),
                Math.max(islandPos1.getBlockX(), islandPos2.getBlockX()),
                Math.max(islandPos1.getBlockY(), islandPos2.getBlockY()),
                Math.max(islandPos1.getBlockZ(), islandPos2.getBlockZ()));
    }

    public int getIslandWidth() {
        Location min = getIslandMin(); Location max = getIslandMax();
        if (min == null || max == null) return 0;
        return max.getBlockX() - min.getBlockX() + 1;
    }

    public int getIslandHeight() {
        Location min = getIslandMin(); Location max = getIslandMax();
        if (min == null || max == null) return 0;
        return max.getBlockY() - min.getBlockY() + 1;
    }

    public int getIslandLength() {
        Location min = getIslandMin(); Location max = getIslandMax();
        if (min == null || max == null) return 0;
        return max.getBlockZ() - min.getBlockZ() + 1;
    }

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

    public double getNpcOffsetX() {
        if (npcPoint == null || getIslandMin() == null) return 0;
        return npcPoint.getX() - getIslandMin().getBlockX();
    }

    public double getNpcOffsetY() {
        if (npcPoint == null || getIslandMin() == null) return 0;
        return npcPoint.getY() - getIslandMin().getBlockY();
    }

    public double getNpcOffsetZ() {
        if (npcPoint == null || getIslandMin() == null) return 0;
        return npcPoint.getZ() - getIslandMin().getBlockZ();
    }

    public double getHologramOffsetX() {
        if (hologramPoint == null || getIslandMin() == null) return 0;
        return hologramPoint.getX() - getIslandMin().getBlockX();
    }

    public double getHologramOffsetY() {
        if (hologramPoint == null || getIslandMin() == null) return 0;
        return hologramPoint.getY() - getIslandMin().getBlockY();
    }

    public double getHologramOffsetZ() {
        if (hologramPoint == null || getIslandMin() == null) return 0;
        return hologramPoint.getZ() - getIslandMin().getBlockZ();
    }

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

    public boolean isForceSpawnLocation() { return forceSpawnLocation; }
    public void setForceSpawnLocation(boolean f) { this.forceSpawnLocation = f; }

    public boolean isInfinite() { return infinite; }
    public void setInfinite(boolean infinite) { this.infinite = infinite; }

    public boolean isSpawnFacingEast() {
        if (spawnPoint == null) return false;
        float y = spawnPoint.getYaw();
        while (y > 180) y -= 360;
        while (y < -180) y += 360;
        return Math.abs(y - (-90f)) < 45f;
    }

    public Location getIslandPos1() { return islandPos1; }
    public void setIslandPos1(Location pos) { this.islandPos1 = pos; }

    public Location getIslandPos2() { return islandPos2; }
    public void setIslandPos2(Location pos) { this.islandPos2 = pos; }

    public Location getSpawnPoint() { return spawnPoint; }
    public void setSpawnPoint(Location pos) { this.spawnPoint = pos; }

    public Location getNpcPoint() { return npcPoint; }
    public void setNpcPoint(Location pos) { this.npcPoint = pos; }

    public Location getHologramPoint() { return hologramPoint; }
    public void setHologramPoint(Location pos) { this.hologramPoint = pos; }

    public Location getFinishPos1() { return finishPos1; }
    public void setFinishPos1(Location pos) { this.finishPos1 = pos; }

    public Location getFinishPos2() { return finishPos2; }
    public void setFinishPos2(Location pos) { this.finishPos2 = pos; }

    public boolean isCustomLengthMode() { return customLengthMode; }
    public void setCustomLengthMode(boolean mode) { this.customLengthMode = mode; }

    public boolean isDiagonalMode() { return diagonalMode; }
    public void setDiagonalMode(boolean mode) { this.diagonalMode = mode; }

    public int getDiagonalStepX() { return diagonalStepX; }
    public void setDiagonalStepX(int stepX) { this.diagonalStepX = stepX; }

    public Location getEndIslandPos1() { return endIslandPos1; }
    public void setEndIslandPos1(Location pos) { this.endIslandPos1 = pos; }

    public Location getEndIslandPos2() { return endIslandPos2; }
    public void setEndIslandPos2(Location pos) { this.endIslandPos2 = pos; }

    // --- End island computed values ---

    public Location getEndIslandMin() {
        if (endIslandPos1 == null || endIslandPos2 == null) return null;
        return new Location(endIslandPos1.getWorld(),
                Math.min(endIslandPos1.getBlockX(), endIslandPos2.getBlockX()),
                Math.min(endIslandPos1.getBlockY(), endIslandPos2.getBlockY()),
                Math.min(endIslandPos1.getBlockZ(), endIslandPos2.getBlockZ()));
    }

    public Location getEndIslandMax() {
        if (endIslandPos1 == null || endIslandPos2 == null) return null;
        return new Location(endIslandPos1.getWorld(),
                Math.max(endIslandPos1.getBlockX(), endIslandPos2.getBlockX()),
                Math.max(endIslandPos1.getBlockY(), endIslandPos2.getBlockY()),
                Math.max(endIslandPos1.getBlockZ(), endIslandPos2.getBlockZ()));
    }

    public int getEndIslandWidth() {
        Location min = getEndIslandMin(); Location max = getEndIslandMax();
        return (min == null || max == null) ? 0 : max.getBlockX() - min.getBlockX() + 1;
    }

    public int getEndIslandHeight() {
        Location min = getEndIslandMin(); Location max = getEndIslandMax();
        return (min == null || max == null) ? 0 : max.getBlockY() - min.getBlockY() + 1;
    }

    public int getEndIslandLength() {
        Location min = getEndIslandMin(); Location max = getEndIslandMax();
        return (min == null || max == null) ? 0 : max.getBlockZ() - min.getBlockZ() + 1;
    }

    /**
     * Base custom length = block gap from the westernmost block of the Start-Island
     * to the easternmost block of the End-Island (start west edge → end east edge, in -X direction).
     *
     * A value of 0 means the islands are directly touching. Positive when end island is west (-X).
     */
    public int getBaseCustomLength() {
        Location islandMin = getIslandMin();
        Location endMax = getEndIslandMax();
        if (islandMin == null || endMax == null) return 0;
        // Gap = start island west edge - end island east edge - 1
        return islandMin.getBlockX() - endMax.getBlockX() - 1;
    }

    /**
     * Y offset of the end island's min corner relative to the start island's min Y.
     */
    public int getEndIslandYOffset() {
        Location islandMin = getIslandMin();
        Location endMin = getEndIslandMin();
        if (islandMin == null || endMin == null) return 0;
        return endMin.getBlockY() - islandMin.getBlockY();
    }

    /**
     * Z offset of the end island's min corner relative to the start island's min Z.
     * Usually 0 (both in the same Z corridor), but captured to be safe.
     */
    public int getEndIslandZOffset() {
        Location islandMin = getIslandMin();
        Location endMin = getEndIslandMin();
        if (islandMin == null || endMin == null) return 0;
        return endMin.getBlockZ() - islandMin.getBlockZ();
    }
}
