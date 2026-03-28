package net.gravijet.fastbuilder.model;

import net.gravijet.fastbuilder.manager.SchematicManager;
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
 * Supports two modes:
 *   - Template mode  : a {@link MapTemplate} schematic is pasted at the base location.
 *   - Procedural mode: classic stone start/target platforms (no template).
 *
 * Bridge direction is along the positive X-axis.
 */
public class Island {

    // ── Procedural constants ──────────────────────────────────────
    public static final int      PLATFORM_WIDTH  = 5;
    public static final int      PLATFORM_DEPTH  = 3;
    public static final int      PLATFORM_GAP    = 5;
    public static final int      VOID_DEPTH      = 5;
    public static final Material PLATFORM_MAT    = Material.STONE;
    public static final Material PRESSURE_PLATE  = Material.STONE_PLATE;

    private final UUID     playerUuid;
    private final World    world;
    private final Location base;

    private Location            spawnLocation;
    private BridgeDistance      currentDistance;
    private BridgeDistance      lastBuiltDistance;

    /** Blocks placed by the player during bridging (cleared on reset/success/void). */
    private final List<Location> placedBlocks = new ArrayList<>();

    // ── Template state ────────────────────────────────────────────
    private MapTemplate         activeTemplate;
    /** Block locations that were pasted from the template (cleared when switching maps). */
    private final List<Location> templateBlocks = new ArrayList<>();
    /** Absolute target locations resolved from the active template. */
    private final List<Location> targetLocations = new ArrayList<>();

    public Island(UUID playerUuid, Location base) {
        this.playerUuid    = playerUuid;
        this.world         = base.getWorld();
        this.base          = base.clone();
        this.spawnLocation = base.clone()
                .add(PLATFORM_WIDTH / 2.0, 1.0, PLATFORM_DEPTH / 2.0);
        this.spawnLocation.setYaw(90f);
    }

    // ── Template mode ─────────────────────────────────────────────

    public void applyTemplate(MapTemplate template, SchematicManager schematicManager) {
        clearTemplate();
        activeTemplate = template;
        targetLocations.clear();

        List<Location> pasted = schematicManager.paste(template, base);
        templateBlocks.addAll(pasted);

        // Resolve spawn
        spawnLocation = base.clone().add(
                template.getSpawnRelX() + 0.5,
                template.getSpawnRelY(),
                template.getSpawnRelZ() + 0.5);
        spawnLocation.setYaw(template.getSpawnYaw());
        spawnLocation.setPitch(template.getSpawnPitch());

        // Resolve targets
        for (int[] off : template.getTargetOffsets()) {
            targetLocations.add(base.clone().add(off[0], off[1], off[2]));
        }
    }

    public void clearTemplate() {
        if (!templateBlocks.isEmpty()) {
            for (Location loc : templateBlocks) {
                world.getBlockAt(loc).setType(Material.AIR);
            }
            templateBlocks.clear();
        }
        targetLocations.clear();
        activeTemplate = null;
    }

    public boolean isTemplateMode() {
        return activeTemplate != null;
    }

    public MapTemplate getActiveTemplate() { return activeTemplate; }

    // ── Procedural generation ─────────────────────────────────────

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
        this.currentDistance   = distance;
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
        world.getBlockAt(tx + PLATFORM_WIDTH / 2, by + 1, bz + PLATFORM_DEPTH / 2)
             .setType(PRESSURE_PLATE);

        // Keep targetLocations in sync for procedural mode too
        targetLocations.clear();
        targetLocations.add(new Location(world,
                tx + PLATFORM_WIDTH / 2, by + 1, bz + PLATFORM_DEPTH / 2));
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
            if (!isTemplateBlock(loc) && !isProceduralStructure(loc)) {
                world.getBlockAt(loc).setType(Material.AIR);
            }
        }
        placedBlocks.clear();
    }

    private boolean isTemplateBlock(Location loc) {
        return templateBlocks.contains(loc);
    }

    private boolean isProceduralStructure(Location loc) {
        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();

        // Start platform floor
        if (loc.getBlockY() == by
                && loc.getBlockX() >= bx && loc.getBlockX() < bx + PLATFORM_WIDTH
                && loc.getBlockZ() >= bz && loc.getBlockZ() < bz + PLATFORM_DEPTH) return true;

        // Target platform floor
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
        if (isTemplateMode()) {
            return isOnThisIsland(loc);
        }
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
        if (isTemplateMode()) {
            int w = Math.max(activeTemplate.getWidth(),  64);
            int h = Math.max(activeTemplate.getHeight(), 32);
            int d = Math.max(activeTemplate.getDepth(),  64);
            return loc.getBlockX() >= base.getBlockX() - 10
                    && loc.getBlockX() <= base.getBlockX() + w + 10
                    && loc.getBlockY() >= base.getBlockY() - 5
                    && loc.getBlockY() <= base.getBlockY() + h + 10
                    && loc.getBlockZ() >= base.getBlockZ() - 10
                    && loc.getBlockZ() <= base.getBlockZ() + d + 10;
        }
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

    /** Returns true if the player is standing on any target block. */
    public boolean isOnTarget(Location loc) {
        for (Location t : targetLocations) {
            if (loc.getBlockX() == t.getBlockX()
                    && loc.getBlockY() == t.getBlockY()
                    && loc.getBlockZ() == t.getBlockZ()) return true;
        }
        return false;
    }

    /** Legacy pressure-plate check for procedural mode (delegates to isOnTarget). */
    public boolean isPressurePlate(Location loc) {
        return isOnTarget(loc);
    }

    // ── Positions for NPC / Hologram ──────────────────────────────

    public Location getNpcLocation() {
        return spawnLocation.clone().add(-1.0, 0, -2.0);
    }

    public Location getHologramLocation() {
        return spawnLocation.clone().add(0.0, 4.0, 0.0);
    }

    // ── Getters / Setters ─────────────────────────────────────────

    public UUID           getPlayerUuid()      { return playerUuid;      }
    public Location       getSpawnLocation()   { return spawnLocation.clone(); }
    public BridgeDistance getCurrentDistance() { return currentDistance; }
    public int            getPlacedBlockCount(){ return placedBlocks.size(); }

    public void setSpawnLocation(Location loc) { this.spawnLocation = loc.clone(); }
}
