package net.gravijet.fastbuilder.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents one player's practice island.
 *
 * Layout (top-down, X increases to the right = bridge direction):
 *
 *   [START 5×3]  [GAP 5]  [BRIDGE]  [TARGET 5×3]
 *
 *   NPC is placed 2 blocks in front of (negative Z) the start platform center.
 *   Hologram floats 4.5 blocks above the start platform center.
 */
public class Island {

    public static final int PLATFORM_WIDTH   = 5;
    public static final int PLATFORM_DEPTH   = 3;
    public static final int PLATFORM_GAP     = 5;
    public static final int VOID_DEPTH       = 5;
    public static final Material PLATFORM_MAT     = Material.STONE;
    public static final Material PRESSURE_PLATE   = Material.STONE_PLATE;

    private final UUID     playerUuid;
    private final World    world;
    private final Location base;      // NW corner of start platform, at Y=islandY

    private Location       spawnLocation;
    private BridgeDistance currentDistance;
    private BridgeDistance lastBuiltDistance;

    private final List<Location> placedBlocks = new ArrayList<>();

    public Island(UUID playerUuid, Location base) {
        this.playerUuid = playerUuid;
        this.world      = base.getWorld();
        this.base       = base.clone();

        this.spawnLocation = base.clone()
                .add(PLATFORM_WIDTH / 2.0, 1.0, PLATFORM_DEPTH / 2.0);
        this.spawnLocation.setYaw(90f); // face east (positive X = bridge direction)
    }

    // ── Generation ────────────────────────────────────────────────

    public void generateStartPlatform() {
        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();
        for (int x = 0; x < PLATFORM_WIDTH; x++) {
            for (int z = 0; z < PLATFORM_DEPTH; z++) {
                world.getBlockAt(bx + x, by,     bz + z).setType(PLATFORM_MAT);
                world.getBlockAt(bx + x, by + 1, bz + z).setType(Material.AIR);
            }
        }
    }

    public void generateTargetPlatform(BridgeDistance distance) {
        if (lastBuiltDistance != null) clearTargetPlatform(lastBuiltDistance);
        this.currentDistance  = distance;
        this.lastBuiltDistance = distance;

        int tx = targetStartX(distance);
        int by = base.getBlockY();
        int bz = base.getBlockZ();

        for (int x = 0; x < PLATFORM_WIDTH; x++) {
            for (int z = 0; z < PLATFORM_DEPTH; z++) {
                world.getBlockAt(tx + x, by,     bz + z).setType(PLATFORM_MAT);
                world.getBlockAt(tx + x, by + 1, bz + z).setType(Material.AIR);
            }
        }
        // Pressure plate in the center of the target platform
        world.getBlockAt(tx + PLATFORM_WIDTH / 2, by + 1, bz + PLATFORM_DEPTH / 2)
             .setType(PRESSURE_PLATE);
    }

    private void clearTargetPlatform(BridgeDistance distance) {
        int tx = targetStartX(distance);
        int by = base.getBlockY();
        int bz = base.getBlockZ();
        for (int x = 0; x < PLATFORM_WIDTH; x++) {
            for (int z = 0; z < PLATFORM_DEPTH; z++) {
                world.getBlockAt(tx + x, by,     bz + z).setType(Material.AIR);
                world.getBlockAt(tx + x, by + 1, bz + z).setType(Material.AIR);
            }
        }
    }

    private int targetStartX(BridgeDistance dist) {
        return base.getBlockX() + PLATFORM_WIDTH + PLATFORM_GAP + dist.getDistance();
    }

    // ── Block tracking ────────────────────────────────────────────

    public void trackPlacedBlock(Block block) {
        placedBlocks.add(block.getLocation());
    }

    public void clearPlacedBlocks() {
        for (Location loc : placedBlocks) {
            if (!isFixedStructure(loc)) {
                world.getBlockAt(loc).setType(Material.AIR);
            }
        }
        placedBlocks.clear();
    }

    private boolean isFixedStructure(Location loc) {
        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();

        // Start platform
        if (loc.getBlockY() == by
                && loc.getBlockX() >= bx && loc.getBlockX() < bx + PLATFORM_WIDTH
                && loc.getBlockZ() >= bz && loc.getBlockZ() < bz + PLATFORM_DEPTH) return true;

        // Target platform
        if (currentDistance != null) {
            int tx = targetStartX(currentDistance);
            if (loc.getBlockY() == by
                    && loc.getBlockX() >= tx && loc.getBlockX() < tx + PLATFORM_WIDTH
                    && loc.getBlockZ() >= bz && loc.getBlockZ() < bz + PLATFORM_DEPTH) return true;
        }
        return false;
    }

    // ── Checks ────────────────────────────────────────────────────

    public boolean isInBridgeArea(Location loc) {
        if (!loc.getWorld().equals(world)) return false;
        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();

        int bridgeStart = bx + PLATFORM_WIDTH;
        int bridgeEnd   = currentDistance == null
                ? bx + 300
                : targetStartX(currentDistance) - 1;

        return loc.getBlockX() >= bridgeStart && loc.getBlockX() <= bridgeEnd
                && loc.getBlockZ() >= bz - 3   && loc.getBlockZ() <= bz + PLATFORM_DEPTH + 2
                && loc.getBlockY() >= by - 3    && loc.getBlockY() <= by + 5;
    }

    public boolean isOnThisIsland(Location loc) {
        if (!loc.getWorld().equals(world)) return false;
        int bx = base.getBlockX();
        int bz = base.getBlockZ();
        int maxX = bx + PLATFORM_WIDTH + PLATFORM_GAP
                + (currentDistance != null ? currentDistance.getDistance() : 64)
                + PLATFORM_WIDTH + 10;
        return loc.getBlockX() >= bx - 10 && loc.getBlockX() <= maxX
                && loc.getBlockZ() >= bz - 10 && loc.getBlockZ() <= bz + PLATFORM_DEPTH + 10;
    }

    public boolean isInVoid(Location loc) {
        return loc.getWorld().equals(world)
                && loc.getY() < (base.getBlockY() - VOID_DEPTH);
    }

    public boolean isPressurePlate(Location loc) {
        if (currentDistance == null || !loc.getWorld().equals(world)) return false;
        int tx = targetStartX(currentDistance);
        int ppX = tx + PLATFORM_WIDTH / 2;
        int ppZ = base.getBlockZ() + PLATFORM_DEPTH / 2;
        int ppY = base.getBlockY() + 1;
        return loc.getBlockX() == ppX && loc.getBlockZ() == ppZ && loc.getBlockY() == ppY;
    }

    // ── Positions for NPC / Hologram ──────────────────────────────

    public Location getNpcLocation() {
        return base.clone().add(PLATFORM_WIDTH / 2.0, 1.0, -2.0);
    }

    public Location getHologramLocation() {
        return base.clone().add(PLATFORM_WIDTH / 2.0, 5.0, PLATFORM_DEPTH / 2.0);
    }

    // ── Getters / Setters ─────────────────────────────────────────

    public UUID           getPlayerUuid()      { return playerUuid;      }
    public Location       getSpawnLocation()   { return spawnLocation.clone(); }
    public BridgeDistance getCurrentDistance() { return currentDistance; }
    public int            getPlacedBlockCount(){ return placedBlocks.size(); }

    public void setSpawnLocation(Location loc) { this.spawnLocation = loc.clone(); }
}
