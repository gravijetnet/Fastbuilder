package net.gravijet.fastbuilder.map;

import org.bukkit.Location;

/**
 * Static utility for all island grid math.
 * Maps are placed along the X-axis: Map 1 at 2000,20,0 | Map 2 at 4000,20,0 etc.
 * Island instances for a map are placed along the positive Z-axis from the map origin.
 */
public final class GridCalculator {

    private GridCalculator() {}

    /**
     * Get the absolute world position of an island instance's min corner.
     * Islands scale along the Z-axis from the map origin.
     *
     * @param map   The map data
     * @param index The 0-based island index
     * @return Absolute world location of the island's min corner
     */
    public static Location getIslandOrigin(MapData map, int index) {
        return new Location(map.getWorld(),
                map.getOriginX(),
                map.getOriginY(),
                map.getOriginZ() + (long) index * map.getDistance());
    }

    /**
     * Get the spawn location for a specific island instance.
     */
    public static Location getIslandSpawn(MapData map, int index) {
        return map.getIslandSpawn(index);
    }

    /**
     * Determine which island index a world location belongs to.
     * Islands are spaced along Z-axis with the configured distance.
     *
     * @return The island index (0-based), or -1 if the location is not within any island
     */
    public static int getIslandIndex(MapData map, Location loc) {
        if (!loc.getWorld().getName().equals(map.getWorldName())) return -1;

        double relX = loc.getX() - map.getOriginX();
        double relY = loc.getY() - map.getOriginY();
        double relZ = loc.getZ() - map.getOriginZ();

        // Check Y bounds
        if (relY < -1 || relY > map.getIslandHeight() + 1) return -1;

        // Check X bounds (island width along X, the build direction)
        if (relX < -1 || relX > map.getIslandWidth()) return -1;

        // Check Z bounds and determine island index
        if (relZ < 0) return -1;

        int distance = map.getDistance();
        if (distance <= 0) return -1;

        int index = (int) (relZ / distance);
        if (index >= map.getScale()) return -1;

        // Check if within the island area (not in the gap between islands)
        double posInSlot = relZ - ((long) index * distance);
        if (posInSlot >= map.getIslandLength()) return -1;

        return index;
    }

    /**
     * Check if a location is strictly within the bounds of a specific island.
     */
    public static boolean isWithinIsland(MapData map, int index, Location loc) {
        if (!loc.getWorld().getName().equals(map.getWorldName())) return false;

        int islandMinX = map.getOriginX();
        int islandMinY = map.getOriginY();
        long islandMinZ = map.getOriginZ() + (long) index * map.getDistance();

        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        return x >= islandMinX && x < islandMinX + map.getIslandWidth()
                && y >= islandMinY && y < islandMinY + map.getIslandHeight()
                && z >= islandMinZ && z < islandMinZ + map.getIslandLength();
    }

    /**
     * Check if a location is within the finish zone of a specific island.
     */
    public static boolean isInFinishZone(MapData map, int index, Location loc) {
        if (!loc.getWorld().getName().equals(map.getWorldName())) return false;

        int baseX = map.getOriginX();
        int baseY = map.getOriginY();
        long baseZ = map.getOriginZ() + (long) index * map.getDistance();

        int fMinX = baseX + map.getFinishMinX();
        int fMinY = baseY + map.getFinishMinY();
        int fMinZ = (int) baseZ + map.getFinishMinZ();
        int fMaxX = baseX + map.getFinishMaxX();
        int fMaxY = baseY + map.getFinishMaxY();
        int fMaxZ = (int) baseZ + map.getFinishMaxZ();

        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();

        return bx >= fMinX && bx <= fMaxX
                && by >= fMinY && by <= fMaxY
                && bz >= fMinZ && bz <= fMaxZ;
    }

    /**
     * Get the bounding box of an island as [minX, minY, minZ, maxX, maxY, maxZ].
     */
    public static int[] getIslandBounds(MapData map, int index) {
        int minX = map.getOriginX();
        int minY = map.getOriginY();
        int minZ = map.getOriginZ() + index * map.getDistance();
        int maxX = minX + map.getIslandWidth() - 1;
        int maxY = minY + map.getIslandHeight() - 1;
        int maxZ = minZ + map.getIslandLength() - 1;
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    /**
     * Minimum distance required to prevent island overlap along the Z-axis.
     * Based on island length (Z extent) + 1 block gap.
     */
    public static int getMinimumDistance(int islandLength) {
        return islandLength + 1;
    }

    /**
     * Check if the given distance would cause overlapping islands.
     */
    public static boolean wouldOverlap(int distance, int islandLength) {
        return distance < islandLength;
    }

    /**
     * Calculate the next map origin X coordinate.
     * Maps are placed at 2000, 4000, 6000... along the X-axis.
     *
     * @param mapCount Current number of maps
     * @param spacing  Block spacing between map types (default 2000)
     * @return The X coordinate for the next map origin
     */
    public static int getNextMapX(int mapCount, int spacing) {
        return (mapCount + 1) * spacing;
    }

    /**
     * Calculate the required scale when autoscaling.
     * Scale up by 50% when occupancy exceeds threshold.
     *
     * @param currentOccupied Number of currently occupied islands
     * @param currentTotal    Current total island count
     * @param thresholdPct    Occupancy threshold percentage (e.g. 80)
     * @param minIslands      Minimum island count to maintain
     * @return The new required scale
     */
    public static int calculateAutoscale(int currentOccupied, int currentTotal,
                                          int thresholdPct, int minIslands) {
        int required = Math.max(currentTotal, minIslands);
        if (currentTotal > 0) {
            double occupancy = (double) currentOccupied / currentTotal * 100;
            if (occupancy >= thresholdPct) {
                required = Math.max((int) Math.ceil(currentTotal * 1.5), required);
            }
        }
        return required;
    }
}
