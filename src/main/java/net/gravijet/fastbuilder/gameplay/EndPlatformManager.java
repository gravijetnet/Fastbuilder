package net.gravijet.fastbuilder.gameplay;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages end-island / end-platform placement and removal for custom-length maps.
 */
class EndPlatformManager {

    private final FastBuilder plugin;
    private final Map<UUID, RunSession> activeSessions;

    // New end-island system: region {x,y,z,w,h,l}
    private final Map<UUID, int[]> endIslandRegions = new HashMap<>();

    // Legacy platform: per-player list of placed block locations + original states
    private final Map<UUID, List<Location>> endPlatforms = new HashMap<>();
    private final Map<UUID, Map<String, int[]>> endPlatformOrigStates = new HashMap<>();

    // Per-player chunk-load task IDs — cancel the old task before starting a new one
    private final Map<UUID, Integer> chunkLoadTasks = new HashMap<>();

    EndPlatformManager(FastBuilder plugin, Map<UUID, RunSession> activeSessions) {
        this.plugin = plugin;
        this.activeSessions = activeSessions;
    }

    void placeEndPlatform(org.bukkit.entity.Player player, MapData map, RunSession session, int customLength) {
        UUID uuid = player.getUniqueId();

        if (customLength <= 0 || !map.hasCustomLength()) {
            clearEndPlatform(uuid);
            return;
        }

        customLength = Math.max(map.getEffectiveMinCustomLength(),
                Math.min(map.getEffectiveMaxCustomLength(), customLength));

        if (map.hasEndIsland()) {
            placeEndIslandTemplateAfterClear(uuid, map, session.getIslandIndex(), customLength);
        } else {
            clearEndPlatform(uuid);
            placeEndPlatformLegacy(uuid, map, session, customLength);
        }
    }

    // Clears the old end island region first (async), then pastes the new position in its callback.
    private void placeEndIslandTemplateAfterClear(UUID uuid, MapData map, int islandIndex, int customLength) {
        org.bukkit.World world = map.getWorld();
        if (world == null) return;

        int[] oldRegion = endIslandRegions.remove(uuid);

        net.gravijet.fastbuilder.player.PlayerData pData =
                plugin.getPlayerManager().getCachedData(uuid);
        int yAdjust = pData != null ? pData.getCustomLengthY(map.getName()) : 0;

        // Diagonal maps shift each slot in +X by diagonalStepX (0 for straight maps).
        int diagX = (int) ((long) islandIndex * map.getDiagonalStepX());
        int endX = map.getOriginX() + diagX + map.getIslandWidth() + customLength - 2;
        int endY = map.getOriginY() + map.getEndIslandYOffset() + yAdjust;
        int endZ = map.getOriginZ() + (int) ((long) islandIndex * map.getActualZStep()) + map.getEndIslandZOffset();

        endIslandRegions.put(uuid, new int[]{
            endX, endY, endZ,
            map.getEndIslandWidth(), map.getEndIslandHeight(), map.getEndIslandLength()
        });

        int clearMaxX = endX + map.getEndIslandWidth()  - 1;
        int clearMaxY = endY + map.getEndIslandHeight() - 1;
        int clearMaxZ = endZ + map.getEndIslandLength()  - 1;

        Runnable pasteNew = () -> {
            forceLoadChunkCorridor(uuid, world,
                    map.getOriginX() + diagX, endY,
                    map.getOriginZ() + (int) ((long) islandIndex * map.getActualZStep()),
                    clearMaxX, clearMaxY, clearMaxZ);
            plugin.getFawePaster().clearRegion(world, endX, endY, endZ, clearMaxX, clearMaxY, clearMaxZ, () ->
                plugin.getFawePaster().pasteTemplate(world, map.getEndIslandTemplateFile(), endX, endY, endZ, null)
            );
        };

        if (oldRegion != null) {
            // Clear the old island region first, then place the new one
            plugin.getFawePaster().clearRegion(world,
                    oldRegion[0], oldRegion[1], oldRegion[2],
                    oldRegion[0] + oldRegion[3] - 1,
                    oldRegion[1] + oldRegion[4] - 1,
                    oldRegion[2] + oldRegion[5] - 1,
                    pasteNew);
        } else {
            pasteNew.run();
        }
    }

    private void placeEndIslandTemplate(UUID uuid, MapData map, int islandIndex, int customLength) {
        org.bukkit.World world = map.getWorld();
        if (world == null) return;

        net.gravijet.fastbuilder.player.PlayerData pData =
                plugin.getPlayerManager().getCachedData(uuid);
        int yAdjust = pData != null ? pData.getCustomLengthY(map.getName()) : 0;

        // Diagonal maps shift each slot in +X by diagonalStepX (0 for straight maps).
        int diagX = (int) ((long) islandIndex * map.getDiagonalStepX());
        int endX = map.getOriginX() + diagX + map.getIslandWidth() + customLength - 2;
        int endY = map.getOriginY() + map.getEndIslandYOffset() + yAdjust;
        int endZ = map.getOriginZ() + (int) ((long) islandIndex * map.getActualZStep()) + map.getEndIslandZOffset();

        endIslandRegions.put(uuid, new int[]{
            endX, endY, endZ,
            map.getEndIslandWidth(), map.getEndIslandHeight(), map.getEndIslandLength()
        });

        int clearMaxX = endX + map.getEndIslandWidth()  - 1;
        int clearMaxY = endY + map.getEndIslandHeight() - 1;
        int clearMaxZ = endZ + map.getEndIslandLength()  - 1;
        forceLoadChunkCorridor(uuid, world,
                map.getOriginX() + diagX, endY,
                map.getOriginZ() + (int) ((long) islandIndex * map.getActualZStep()),
                clearMaxX, clearMaxY, clearMaxZ);
        plugin.getFawePaster().clearRegion(world, endX, endY, endZ, clearMaxX, clearMaxY, clearMaxZ, () ->
            plugin.getFawePaster().pasteTemplate(world, map.getEndIslandTemplateFile(), endX, endY, endZ, null)
        );
    }

    @SuppressWarnings("deprecation")
    private void placeEndPlatformLegacy(UUID uuid, MapData map, RunSession session, int customLength) {
        int diagX = (int) ((long) session.getIslandIndex() * map.getDiagonalStepX());
        int platformX = map.getOriginX() + diagX + (int) map.getSpawnOffsetX() + customLength;
        int platformY = map.getOriginY() + map.getFinishMinY();
        int islandBaseZ = map.getOriginZ() + session.getIslandIndex() * map.getActualZStep();
        int minZ = islandBaseZ + map.getFinishMinZ();
        int maxZ = islandBaseZ + map.getFinishMaxZ();

        int configDepth = plugin.getConfigManager().getEndPlatformDepth();
        if (configDepth > 0 && (maxZ - minZ + 1) > configDepth) {
            int zCenter = (minZ + maxZ) / 2;
            minZ = zCenter - configDepth / 2;
            maxZ = minZ + configDepth - 1;
        }

        String matStr = plugin.getConfigManager().getEndPlatformMaterial();
        Material mat;
        byte matData = 0;
        try {
            if (matStr.contains(":")) {
                String[] parts = matStr.split(":");
                mat = Material.getMaterial(parts[0].toUpperCase());
                matData = (byte) Integer.parseInt(parts[1]);
            } else {
                mat = Material.getMaterial(matStr.toUpperCase());
            }
            if (mat == null || mat == Material.AIR) throw new IllegalArgumentException();
        } catch (Exception e) {
            mat = Material.STAINED_GLASS_PANE;
            matData = 5;
        }

        org.bukkit.World world = map.getWorld();
        if (world == null) return;

        List<Location> platform = new ArrayList<>();
        Map<String, int[]> origStates = new HashMap<>();
        for (int z = minZ; z <= maxZ; z++) {
            Location loc = new Location(world, platformX, platformY, z);
            org.bukkit.block.Block block = loc.getBlock();
            String key = platformX + "," + platformY + "," + z;
            origStates.put(key, new int[]{block.getTypeId(), block.getData()});
            block.setTypeIdAndData(mat.getId(), matData, false);
            platform.add(loc);
        }
        endPlatforms.put(uuid, platform);
        endPlatformOrigStates.put(uuid, origStates);
    }

    @SuppressWarnings("deprecation")
    void clearEndPlatform(UUID uuid) {
        int[] region = endIslandRegions.remove(uuid);
        if (region != null) {
            RunSession sess = activeSessions.get(uuid);
            if (sess != null) {
                MapData m = plugin.getMapManager().getMap(sess.getMapName());
                if (m != null && m.getWorld() != null) {
                    plugin.getFawePaster().clearRegion(m.getWorld(),
                            region[0], region[1], region[2],
                            region[0] + region[3] - 1, region[1] + region[4] - 1, region[2] + region[5] - 1,
                            null);
                }
            }
        }

        List<Location> platform = endPlatforms.remove(uuid);
        Map<String, int[]> origStates = endPlatformOrigStates.remove(uuid);
        if (platform != null) {
            for (Location loc : platform) {
                String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
                int[] orig = origStates != null ? origStates.get(key) : null;
                org.bukkit.block.Block block = loc.getBlock();
                if (orig != null && orig[0] != 0) block.setTypeIdAndData(orig[0], (byte) orig[1], false);
                else block.setType(Material.AIR);
            }
        }
    }

    /**
     * Restores the end island on a specific island slot to the map's base/default distance.
     * Called after a player leaves or switches away from a slot so the slot looks correct for the next occupant.
     */
    void restoreDefaultEndPlatform(MapData map, int islandIndex) {
        if (!map.hasEndIsland()) return;
        org.bukkit.World world = map.getWorld();
        if (world == null) return;

        int defaultLength = map.getBaseCustomLength() > 0
                ? map.getBaseCustomLength() : map.getEffectiveMinCustomLength();
        if (defaultLength <= 0) return;

        int diagX = (int) ((long) islandIndex * map.getDiagonalStepX());
        int endX = map.getOriginX() + diagX + map.getIslandWidth() + defaultLength - 2;
        int endY = map.getOriginY() + map.getEndIslandYOffset();
        int endZ = map.getOriginZ() + (int) ((long) islandIndex * map.getActualZStep()) + map.getEndIslandZOffset();

        int clearMaxX = endX + map.getEndIslandWidth()  - 1;
        int clearMaxY = endY + map.getEndIslandHeight() - 1;
        int clearMaxZ = endZ + map.getEndIslandLength()  - 1;

        forceLoadChunkCorridor(null, world, map.getOriginX() + diagX, endY,
                map.getOriginZ() + (int) ((long) islandIndex * map.getActualZStep()), clearMaxX, clearMaxY, clearMaxZ);
        plugin.getFawePaster().clearRegion(world, endX, endY, endZ, clearMaxX, clearMaxY, clearMaxZ,
                () -> plugin.getFawePaster().pasteTemplate(
                        world, map.getEndIslandTemplateFile(), endX, endY, endZ, null));
    }

    void clearAllEndPlatforms() {
        for (UUID uuid : new ArrayList<>(endPlatforms.keySet())) clearEndPlatform(uuid);
        for (UUID uuid : new ArrayList<>(endIslandRegions.keySet())) clearEndPlatform(uuid);
    }

    int[] getEndIslandRegion(UUID uuid) { return endIslandRegions.get(uuid); }

    private void forceLoadChunkCorridor(UUID uuid, org.bukkit.World world, int fromX, int fromY, int fromZ,
                                         int toX, int toY, int toZ) {
        // Cancel any in-progress chunk-load task for this player before starting a new one
        if (uuid != null) {
            Integer oldTask = chunkLoadTasks.remove(uuid);
            if (oldTask != null) org.bukkit.Bukkit.getScheduler().cancelTask(oldTask);
        }

        int minCX = Math.min(fromX, toX) >> 4, maxCX = Math.max(fromX, toX) >> 4;
        int minCZ = Math.min(fromZ, toZ) >> 4, maxCZ = Math.max(fromZ, toZ) >> 4;

        List<int[]> chunks = new ArrayList<>();
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) chunks.add(new int[]{cx, cz});
            }
        }
        if (chunks.isEmpty()) return;

        final int[] idx = {0};
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10 && idx[0] < chunks.size(); i++, idx[0]++) {
                    int[] c = chunks.get(idx[0]);
                    if (!world.isChunkLoaded(c[0], c[1])) world.loadChunk(c[0], c[1], true);
                }
                if (idx[0] >= chunks.size()) {
                    if (uuid != null) chunkLoadTasks.remove(uuid);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();
        if (uuid != null) chunkLoadTasks.put(uuid, taskId);
    }
}
