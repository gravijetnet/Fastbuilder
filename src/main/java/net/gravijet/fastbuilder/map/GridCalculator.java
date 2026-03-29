package net.gravijet.fastbuilder.map;

import org.bukkit.Location;

/**
 * Static utility for all island grid math.
 * Islands are placed linearly along the X axis from the map origin.
 * Different map types are spaced along the Z axis.
 */
public final class GridCalculator {

    private GridCalculator() {}

    /**
     * Get the absolute world position of an island instance's min corner.
     *
     * @param map   The map data
     * @param index The 0-based island index
     * @return Absolute world location of the island's min corner
     */
    public static Location getIslandOrigin(MapData map, int index) {
        return new Location(map.getWorld(),
                map.getOriginX() + (long) index * map.getDistance(),
                map.getOriginY(),
                map.getOriginZ());
    }

    /**
     * Get the spawn location for a specific island instance.
     */
    public static Location getIslandSpawn(MapData map, int index) {
        return map.getIslandSpawn(index);
    }

    /**
     * Determine which island index a world location belongs to.
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

        // Check Z bounds
        if (relZ < -1 || relZ > map.getIslandLength()) return -1;

        // Check X bounds and determine island index
        if (relX < 0) return -1;

        int distance = map.getDistance();
        if (distance <= 0) return -1;

        int index = (int) (relX / distance);
        if (index >= map.getScale()) return -1;

        // Check if within the island area (not in the gap between islands)
        double posInSlot = relX - ((long) index * distance);
        if (posInSlot >= map.getIslandWidth()) return -1;

        return index;
    }

    /**
     * Check if a location is strictly within the bounds of a specific island.
     */
    public static boolean isWithinIsland(MapData map, int index, Location loc) {
        if (!loc.getWorld().getName().equals(map.getWorldName())) return false;

        long islandMinX = map.getOriginX() + (long) index * map.getDistance();
        int islandMinY = map.getOriginY();
        int islandMinZ = map.getOriginZ();

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

        long baseX = map.getOriginX() + (long) index * map.getDistance();
        int baseY = map.getOriginY();
        int baseZ = map.getOriginZ();

        int fMinX = (int) baseX + map.getFinishMinX();
        int fMinY = baseY + map.getFinishMinY();
        int fMinZ = baseZ + map.getFinishMinZ();
        int fMaxX = (int) baseX + map.getFinishMaxX();
        int fMaxY = baseY + map.getFinishMaxY();
        int fMaxZ = baseZ + map.getFinishMaxZ();

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
        int minX = map.getOriginX() + index * map.getDistance();
        int minY = map.getOriginY();
        int minZ = map.getOriginZ();
        int maxX = minX + map.getIslandWidth() - 1;
        int maxY = minY + map.getIslandHeight() - 1;
        int maxZ = minZ + map.getIslandLength() - 1;
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    /**
     * Minimum distance required to prevent island overlap (island width + 1 block gap).
     */
    public static int getMinimumDistance(int islandWidth) {
        return islandWidth + 1;
    }

    /**
     * Check if the given distance would cause overlapping islands.
     */
    public static boolean wouldOverlap(int distance, int islandWidth) {
        return distance < islandWidth;
    }

    /**
     * Calculate the next map origin Z coordinate, given existing maps.
     *
     * @param mapCount Current number of maps
     * @param spacing  Block spacing between map types (default 2000)
     * @return The Z coordinate for the next map origin
     */
    public static int getNextMapZ(int mapCount, int spacing) {
        return mapCount * spacing;
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
