package net.gravijet.fastbuilder.map;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages all maps: loading, saving, scaling, and island assignment.
 */
public class MapManager {

    private final FastBuilder plugin;
    private final File mapsDir;

    private final Map<String, MapData> maps = new HashMap<>();
    private final Map<String, List<IslandInstance>> islands = new HashMap<>();
    private final Map<UUID, SetupSession> setupSessions = new HashMap<>();
    // Maps that are currently being scaled (paste in flight) — joining is blocked while true.
    private final java.util.Set<String> scalingMaps = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    public MapManager(FastBuilder plugin) {
        this.plugin = plugin;
        this.mapsDir = new File(plugin.getDataFolder(), "maps");
        if (!mapsDir.exists()) mapsDir.mkdirs();
    }

    // --- Load/Save ---

    public void loadMaps() {
        // Preserve island assignments so active player sessions survive a /fb reload.
        // Key: mapNameLower -> (islandIndex -> [occupantUuid, occupantName])
        Map<String, Map<Integer, String[]>> preserved = new HashMap<>();
        for (Map.Entry<String, List<IslandInstance>> entry : islands.entrySet()) {
            Map<Integer, String[]> assignments = new HashMap<>();
            for (IslandInstance island : entry.getValue()) {
                if (island.isOccupied()) {
                    assignments.put(island.getIndex(),
                            new String[]{island.getOccupantUuid().toString(), island.getOccupantName()});
                }
            }
            preserved.put(entry.getKey(), assignments);
        }

        maps.clear();
        islands.clear();

        File[] files = mapsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                String mapName = config.getString("name");
                if (mapName == null || mapName.isEmpty()) continue;

                MapData data = new MapData(mapName);
                data.loadFrom(config);
                refreshDesignDimensions(data);
                maps.put(mapName.toLowerCase(), data);
                initializeIslands(data);

                plugin.getLogger().info("Loaded map: " + mapName
                        + " (scale=" + data.getScale()
                        + ", enabled=" + data.isEnabled() + ")");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load map file: " + file.getName(), e);
            }
        }

        // Re-apply island assignments for players who were online during the reload.
        for (Map.Entry<String, Map<Integer, String[]>> entry : preserved.entrySet()) {
            String key = entry.getKey();
            List<IslandInstance> list = islands.get(key);
            if (list == null) continue;
            for (Map.Entry<Integer, String[]> assign : entry.getValue().entrySet()) {
                int idx = assign.getKey();
                if (idx < 0 || idx >= list.size()) continue;
                String[] info = assign.getValue();
                try {
                    UUID uuid = UUID.fromString(info[0]);
                    // Only re-assign if the player is still online
                    if (Bukkit.getPlayer(uuid) != null) {
                        list.get(idx).setOccupant(uuid, info[1]);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        plugin.getLogger().info("Loaded " + maps.size() + " map(s).");
    }

    public void saveAll() {
        for (MapData data : maps.values()) saveMap(data);
    }

    public void saveMap(MapData data) {
        // Keep the design-dimension cache fresh so newly added designs immediately enlarge
        // the island bounds (getMaxDesign*) even before the next reload.
        refreshDesignDimensions(data);
        File file = new File(mapsDir, data.getName().toLowerCase() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        data.saveTo(config);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save map: " + data.getName(), e);
        }
    }

    /**
     * Populates the map's runtime design-dimension cache by reading each alternative/
     * custom-length/infinite design schematic's bounding box from disk. This lets the
     * island bounds (getMaxDesign*) account for designs that were added without a
     * DesignProfile, so a longer/wider/taller design is fully walkable, buildable, and
     * cleared. Reads are cached inside FawePaster, so repeat calls are cheap.
     */
    public void refreshDesignDimensions(MapData data) {
        if (plugin.getFawePaster() == null) return;
        java.util.Set<String> keys = new java.util.HashSet<>();
        keys.addAll(data.getAlternativeTemplates());
        keys.addAll(data.getCustomLengthTemplates());
        keys.addAll(data.getInfiniteTemplates());
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            // A saved DesignProfile already supplies dimensions; skip the disk read for those.
            MapData.DesignProfile prof = data.getDesignProfile(key);
            if (prof != null && (prof.islandWidth > 0 || prof.islandHeight > 0 || prof.islandLength > 0)) {
                continue;
            }
            int[] size = plugin.getFawePaster().getSchematicSize(key);
            if (size != null) data.setDesignDimension(key, size);
        }
    }

    private void initializeIslands(MapData data) {
        List<IslandInstance> list = new ArrayList<>();
        for (int i = 0; i < data.getScale(); i++) list.add(new IslandInstance(i));
        islands.put(data.getName().toLowerCase(), list);
    }

    // --- Map CRUD ---

    public MapData getMap(String name) { return maps.get(name.toLowerCase()); }

    public Collection<MapData> getAllMaps() { return Collections.unmodifiableCollection(maps.values()); }

    public List<String> getMapNames() {
        List<String> names = new ArrayList<>();
        for (MapData data : maps.values()) names.add(data.getName());
        return names;
    }

    public List<String> getEnabledMapNames() {
        List<String> names = new ArrayList<>();
        for (MapData data : maps.values()) if (data.isEnabled()) names.add(data.getName());
        return names;
    }

    public List<String> getDisabledMapNames() {
        List<String> names = new ArrayList<>();
        for (MapData data : maps.values()) if (!data.isEnabled()) names.add(data.getName());
        return names;
    }

    public boolean mapExists(String name) { return maps.containsKey(name.toLowerCase()); }

    /**
     * Permanently delete a map: relocates any occupants, removes all island instances,
     * removes the map from memory, and deletes its YAML file.
     * The template schematic file is left intact (admin may want to reuse it).
     */
    public void deleteMap(String name) {
        String key = name.toLowerCase();
        MapData map = maps.remove(key);
        if (map == null) return;

        List<IslandInstance> islandList = islands.remove(key);
        if (islandList != null) {
            for (IslandInstance island : islandList) {
                if (island.isOccupied()) {
                    org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(island.getOccupantUuid());
                    if (p != null && p.isOnline()) {
                        relocatePlayer(p, name);
                    }
                }
            }
        }

        File mapFile = new File(mapsDir, key + ".yml");
        if (mapFile.exists() && !mapFile.delete()) {
            plugin.getLogger().warning("Could not delete map file: " + mapFile.getName());
        }

        // Clear all pasted island schematics from the world
        if (map.getWorld() != null && map.getScale() > 0) {
            int dW = map.getMaxDesignWidth();
            int dH = map.getMaxDesignHeight();
            int dL = map.getMaxDesignLength();
            if (map.isDiagonal()) {
                plugin.getFawePaster().clearIslandsDiagonal(
                        map.getWorld(),
                        map.getOriginX(), map.getOriginY(), map.getOriginZ(),
                        dW, dH, dL,
                        map.getActualZStep(), map.getDiagonalStepX(), 0, map.getScale(), null);
            } else {
                plugin.getFawePaster().clearIslands(
                        map.getWorld(),
                        map.getOriginX(), map.getOriginY(), map.getOriginZ(),
                        dW, dH, dL,
                        map.getActualZStep(), 0, map.getScale(), null);
            }
        }
    }

    /**
     * Create a new map from a completed setup session.
     * Hologram offset from session is also stored.
     * After creation the initial island (index 0) is pasted immediately.
     */
    public MapData createMap(SetupSession session, String name) {
        MapData data = new MapData(name);
        Location gridOrigin = getNextMapOrigin(session.isInfinite());
        data.setWorldName(gridOrigin.getWorld().getName());
        data.setOriginX(gridOrigin.getBlockX());
        data.setOriginY(gridOrigin.getBlockY());
        data.setOriginZ(gridOrigin.getBlockZ());
        data.setIslandWidth(session.getIslandWidth());
        data.setIslandHeight(session.getIslandHeight());
        data.setIslandLength(session.getIslandLength());
        data.setSpawnOffsetX(session.getSpawnOffsetX());
        data.setSpawnOffsetY(session.getSpawnOffsetY());
        data.setSpawnOffsetZ(session.getSpawnOffsetZ());
        if (session.getSpawnPoint() != null) {
            data.setSpawnYaw(session.getSpawnPoint().getYaw());
            data.setSpawnPitch(session.getSpawnPoint().getPitch());
        }
        data.setNpcOffsetX(session.getNpcOffsetX());
        data.setNpcOffsetY(session.getNpcOffsetY());
        data.setNpcOffsetZ(session.getNpcOffsetZ());
        if (session.getNpcPoint() != null) {
            data.setNpcYaw(session.getNpcPoint().getYaw());
            data.setNpcPitch(session.getNpcPoint().getPitch());
        }
        data.setHologramOffsetX(session.getHologramOffsetX());
        data.setHologramOffsetY(session.getHologramOffsetY());
        data.setHologramOffsetZ(session.getHologramOffsetZ());
        data.setFinishMinX(session.getFinishMinX());
        data.setFinishMinY(session.getFinishMinY());
        data.setFinishMinZ(session.getFinishMinZ());
        data.setFinishMaxX(session.getFinishMaxX());
        data.setFinishMaxY(session.getFinishMaxY());
        data.setFinishMaxZ(session.getFinishMaxZ());

        // Diagonal mode
        if (session.isDiagonalMode()) {
            data.setDiagonal(true);
            data.setDiagonalStepX(session.getDiagonalStepX());
        }

        // Default gap = 3 blocks between south edge of one island and north edge of the next
        data.setDistance(3);
        data.setScale(1);
        data.setEnabled(false);
        // Default deathY: 2 blocks below the map's grid origin Y (spawn level)
        data.setDeathY(gridOrigin.getBlockY() - 2);
        data.setTemplateFile(name.toLowerCase());

        maps.put(name.toLowerCase(), data);
        initializeIslands(data);
        saveMap(data);

        return data;
    }

    /**
     * Paste the initial island(s) for a map right after creation or on demand.
     * Must be called after createMap() to place the template in the world.
     */
    public void pasteInitialIsland(MapData map) {
        if (map.isDiagonal()) {
            plugin.getFawePaster().pasteIslandsDiagonal(
                    map.getWorld(), map.getTemplateFile(),
                    map.getOriginX(), map.getOriginY(), map.getOriginZ(),
                    map.getActualZStep(), map.getDiagonalStepX(),
                    0, map.getScale(),
                    () -> plugin.getLogger().info("Pasted initial island(s) for map: " + map.getName()));
        } else {
            plugin.getFawePaster().pasteIslands(
                    map.getWorld(), map.getTemplateFile(),
                    map.getOriginX(), map.getOriginY(), map.getOriginZ(),
                    map.getActualZStep(),
                    0, map.getScale(),
                    () -> plugin.getLogger().info("Pasted initial island(s) for map: " + map.getName()));
        }
    }

    public boolean renameMap(String oldName, String newName) {
        MapData data = maps.remove(oldName.toLowerCase());
        if (data == null) return false;

        List<IslandInstance> islandList = islands.remove(oldName.toLowerCase());
        data.setName(newName);
        maps.put(newName.toLowerCase(), data);
        if (islandList != null) islands.put(newName.toLowerCase(), islandList);

        // Save new file first, then delete old — prevents data loss if the process crashes between the two
        saveMap(data);
        File oldFile = new File(mapsDir, oldName.toLowerCase() + ".yml");
        if (oldFile.exists() && !oldFile.delete()) {
            plugin.getLogger().warning("Could not delete old map file: " + oldFile.getName());
        }
        return true;
    }

    // --- Island Assignment ---

    public List<IslandInstance> getIslands(String mapName) {
        List<IslandInstance> list = islands.get(mapName.toLowerCase());
        return list != null ? Collections.unmodifiableList(list) : Collections.<IslandInstance>emptyList();
    }

    /** Returns true if the map is currently being scaled (paste in flight). */
    public boolean isMapScaling(String mapName) {
        return scalingMaps.contains(mapName.toLowerCase());
    }

    public synchronized int assignFreeIsland(String mapName, UUID playerUuid, String playerName) {
        List<IslandInstance> list = islands.get(mapName.toLowerCase());
        if (list == null) return -1;

        for (IslandInstance island : list) {
            if (!island.isOccupied()) {
                island.setOccupant(playerUuid, playerName);
                return island.getIndex();
            }
        }
        return -1;
    }

    public boolean assignIsland(String mapName, int index, UUID playerUuid, String playerName) {
        List<IslandInstance> list = islands.get(mapName.toLowerCase());
        if (list == null || index < 0 || index >= list.size()) return false;

        IslandInstance island = list.get(index);
        if (island.isOccupied()) return false;

        island.setOccupant(playerUuid, playerName);
        return true;
    }

    public void freeIsland(String mapName, UUID playerUuid) {
        List<IslandInstance> list = islands.get(mapName.toLowerCase());
        if (list == null) return;

        for (IslandInstance island : list) {
            if (playerUuid.equals(island.getOccupantUuid())) {
                island.clearOccupant();
                return;
            }
        }
    }

    public void freeAllIslands(UUID playerUuid) {
        for (List<IslandInstance> list : islands.values()) {
            for (IslandInstance island : list) {
                if (playerUuid.equals(island.getOccupantUuid())) island.clearOccupant();
            }
        }
    }

    public int getPlayerIsland(String mapName, UUID playerUuid) {
        List<IslandInstance> list = islands.get(mapName.toLowerCase());
        if (list == null) return -1;

        for (IslandInstance island : list) {
            if (playerUuid.equals(island.getOccupantUuid())) return island.getIndex();
        }
        return -1;
    }

    public int getOccupiedCount(String mapName) {
        List<IslandInstance> list = islands.get(mapName.toLowerCase());
        if (list == null) return 0;
        int count = 0;
        for (IslandInstance island : list) if (island.isOccupied()) count++;
        return count;
    }

    // --- Scaling ---

    public void updateScale(MapData map, int newScale) {
        int oldScale = map.getScale();
        String mapKey = map.getName().toLowerCase();

        if (newScale > oldScale) {
            scalingMaps.add(mapKey);
            Runnable onScaleDone = () -> scalingMaps.remove(mapKey);
            if (map.isDiagonal()) {
                plugin.getFawePaster().pasteIslandsDiagonal(
                        map.getWorld(), map.getTemplateFile(),
                        map.getOriginX(), map.getOriginY(), map.getOriginZ(),
                        map.getActualZStep(), map.getDiagonalStepX(), oldScale, newScale, onScaleDone);
            } else {
                plugin.getFawePaster().pasteIslands(
                        map.getWorld(), map.getTemplateFile(),
                        map.getOriginX(), map.getOriginY(), map.getOriginZ(),
                        map.getActualZStep(), oldScale, newScale, onScaleDone);
            }

            List<IslandInstance> list = islands.computeIfAbsent(mapKey, k -> new ArrayList<>());
            for (int i = oldScale; i < newScale; i++) list.add(new IslandInstance(i));

        } else if (newScale < oldScale) {
            List<IslandInstance> list = islands.get(mapKey);
            if (list != null) {
                for (int i = newScale; i < oldScale && i < list.size(); i++) {
                    IslandInstance island = list.get(i);
                    if (island.isOccupied()) {
                        org.bukkit.entity.Player p = Bukkit.getPlayer(island.getOccupantUuid());
                        if (p != null && p.isOnline()) {
                            // Full cleanup before relocation
                            if (plugin.getGameplayManager() != null) {
                                plugin.getGameplayManager().clearAllPlacedBlocks(p.getUniqueId());
                                plugin.getGameplayManager().clearEndPlatform(p.getUniqueId());
                                plugin.getGameplayManager().revertIslandDesign(map, island.getIndex());
                                plugin.getGameplayManager().removeSession(p.getUniqueId());
                                plugin.getGameplayManager().removeGlobalSessionBest(p.getUniqueId());
                            }
                            net.gravijet.fastbuilder.player.PlayerData pData =
                                    plugin.getPlayerManager().getCachedData(p.getUniqueId());
                            if (pData != null) pData.clearCustomLengths();
                            if (plugin.getNpcManager() != null) plugin.getNpcManager().despawnNpc(p.getUniqueId());
                            if (plugin.getHologramManager() != null)
                                plugin.getHologramManager().removeHologram(map.getName(), island.getIndex());
                            relocatePlayer(p, map.getName());
                        }
                        island.clearOccupant();
                    }
                }
                while (list.size() > newScale) list.remove(list.size() - 1);
            }

            int clW = map.getMaxDesignWidth();
            int clH = map.getMaxDesignHeight();
            int clL = map.getMaxDesignLength();
            if (map.isDiagonal()) {
                plugin.getFawePaster().clearIslandsDiagonal(
                        map.getWorld(),
                        map.getOriginX(), map.getOriginY(), map.getOriginZ(),
                        clW, clH, clL,
                        map.getActualZStep(), map.getDiagonalStepX(), newScale, oldScale, null);
            } else {
                plugin.getFawePaster().clearIslands(
                        map.getWorld(),
                        map.getOriginX(), map.getOriginY(), map.getOriginZ(),
                        clW, clH, clL,
                        map.getActualZStep(), newScale, oldScale, null);
            }
        }

        map.setScale(newScale);
        saveMap(map);
    }

    /**
     * Re-paste all islands after a distance change.
     * @param oldActualStep previous total Z step (islandLength + gap, or physicalLength + gap)
     */
    public void regenerateIslands(final MapData map, final int oldActualStep) {
        final int scale = map.getScale();
        final int newActualStep = map.getActualZStep();

        plugin.getLogger().info("Regenerating " + scale + " islands for map '" + map.getName()
                + "' (old step=" + oldActualStep + ", new step=" + newActualStep + ")");

        // Step 1: Evict all players from this map to world spawn temporarily
        List<IslandInstance> list = islands.get(map.getName().toLowerCase());
        if (list != null) {
            for (IslandInstance island : list) {
                if (island.isOccupied()) {
                    org.bukkit.entity.Player p = Bukkit.getPlayer(island.getOccupantUuid());
                    if (p != null && p.isOnline()) {
                        p.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                                + "&eIsland layout is being updated, please wait..."));
                        if (!Bukkit.getWorlds().isEmpty()) p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                        // Remove stale session so death-check doesn't fire at spawn
                        if (plugin.getGameplayManager() != null) {
                            plugin.getGameplayManager().removeSession(p.getUniqueId());
                        }
                    }
                }
            }
        }

        // Step 2: Clear old positions, then paste at new positions
        Runnable onPasteComplete = new Runnable() {
            @Override
            public void run() {
                plugin.getLogger().info("Regeneration complete for map: " + map.getName());
                List<IslandInstance> iList = islands.get(map.getName().toLowerCase());
                if (iList == null) return;
                for (IslandInstance island : iList) {
                    if (island.isOccupied()) {
                        org.bukkit.entity.Player p = Bukkit.getPlayer(island.getOccupantUuid());
                        if (p != null && p.isOnline()) {
                            p.teleport(map.getIslandSpawn(island.getIndex()));
                            p.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                                    + "&aIsland layout updated! Teleported to new spawn."));
                            if (plugin.getNpcManager() != null) {
                                plugin.getNpcManager().despawnNpc(p.getUniqueId());
                                plugin.getNpcManager().spawnNpc(p, map.getIslandNpcLocation(island.getIndex()));
                            }
                            if (plugin.getHologramManager() != null) {
                                plugin.getHologramManager().updateHologram(map.getName(), island.getIndex(), p);
                            }
                        }
                    }
                }
            }
        };

        Runnable onClearComplete = new Runnable() {
            @Override
            public void run() {
                if (map.isDiagonal()) {
                    plugin.getFawePaster().pasteIslandsDiagonal(
                            map.getWorld(), map.getTemplateFile(),
                            map.getOriginX(), map.getOriginY(), map.getOriginZ(),
                            newActualStep, map.getDiagonalStepX(), 0, scale, onPasteComplete);
                } else {
                    plugin.getFawePaster().pasteIslands(
                            map.getWorld(), map.getTemplateFile(),
                            map.getOriginX(), map.getOriginY(), map.getOriginZ(),
                            newActualStep, 0, scale, onPasteComplete);
                }
            }
        };

        // Clear using the maximum dimensions across all design profiles so a previously
        // pasted longer/wider/taller design's blocks are removed before regeneration.
        int clearW = map.getMaxDesignWidth();
        int clearH = map.getMaxDesignHeight();
        int clearL = map.getMaxDesignLength();
        if (map.isDiagonal()) {
            plugin.getFawePaster().clearIslandsDiagonal(
                    map.getWorld(),
                    map.getOriginX(), map.getOriginY(), map.getOriginZ(),
                    clearW, clearH, clearL,
                    oldActualStep, map.getDiagonalStepX(), 0, scale, onClearComplete);
        } else {
            plugin.getFawePaster().clearIslands(
                    map.getWorld(),
                    map.getOriginX(), map.getOriginY(), map.getOriginZ(),
                    clearW, clearH, clearL,
                    oldActualStep, 0, scale, onClearComplete);
        }
    }

    public void checkAutoscale(MapData map) {
        if (!map.isAutoscale()) return;

        int occupied = getOccupiedCount(map.getName());
        int newScale = GridCalculator.calculateAutoscale(
                occupied, map.getScale(),
                plugin.getConfigManager().getAutoscaleThreshold(),
                plugin.getConfigManager().getAutoscaleMinIslands());

        if (newScale != map.getScale()) {
            plugin.getLogger().info("Autoscaling map '" + map.getName()
                    + "' from " + map.getScale() + " to " + newScale + " islands.");
            updateScale(map, newScale);
        }
    }

    public void relocatePlayer(org.bukkit.entity.Player player, String excludeMap) {
        String defaultMap = plugin.getConfigManager().getDefaultMap();

        if (!defaultMap.isEmpty() && !defaultMap.equalsIgnoreCase(excludeMap)) {
            MapData def = getMap(defaultMap);
            if (def != null && def.isEnabled() && !isMapScaling(def.getName())) {
                int island = assignFreeIsland(def.getName(), player.getUniqueId(), player.getName());
                if (island >= 0) {
                    setupRelocatedPlayer(player, def, island);
                    return;
                }
            }
        }

        for (MapData m : maps.values()) {
            if (m.isEnabled() && !m.getName().equalsIgnoreCase(excludeMap) && !isMapScaling(m.getName())) {
                int island = assignFreeIsland(m.getName(), player.getUniqueId(), player.getName());
                if (island >= 0) {
                    setupRelocatedPlayer(player, m, island);
                    return;
                }
            }
        }

        // No free island anywhere — put the player into spectator so they can't place blocks
        // without a session, and notify them.
        if (!Bukkit.getWorlds().isEmpty()) {
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        String noIslands = plugin.getConfigManager().getMessage("no-free-islands");
        if (noIslands == null || noIslands.isEmpty()) noIslands = "%prefix%&cNo free island available right now.";
        player.sendMessage(net.gravijet.fastbuilder.util.ColorUtil.translate(
                noIslands.replace("%prefix%", plugin.getConfigManager().getPrefix())));
    }

    private void setupRelocatedPlayer(org.bukkit.entity.Player player, MapData map, int island) {
        net.gravijet.fastbuilder.player.PlayerData pData =
                plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (pData != null) {
            pData.setLastMap(map.getName());
            pData.setLastIsland(island);
        }
        org.bukkit.Location spawn = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getEffectiveSpawn(player.getUniqueId(), map, island)
                : map.getIslandSpawn(island);
        player.teleport(spawn);
        // Restore normal play state — the player may have been in SPECTATOR/CREATIVE from a
        // previous failed relocation attempt or admin no-island fallback.
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setFoodLevel(20);
        player.setHealth(player.getMaxHealth());
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().createSession(player.getUniqueId(), map.getName(), island);
        }
        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().giveItems(player);
        }
        // Re-create the scoreboard rather than just updating it: a relocated player coming from
        // a deleted/rescaled map may have lost their board, and updateScoreboard() no-ops without one.
        plugin.getScoreboardManager().createScoreboard(player);
    }

    // --- Setup Sessions ---

    public SetupSession getSetupSession(UUID playerUuid) { return setupSessions.get(playerUuid); }

    public SetupSession startSetupSession(UUID playerUuid, Location origin) {
        SetupSession session = new SetupSession(playerUuid, origin);
        setupSessions.put(playerUuid, session);
        return session;
    }

    public void removeSetupSession(UUID playerUuid) { setupSessions.remove(playerUuid); }

    public boolean hasSetupSession(UUID playerUuid) { return setupSessions.containsKey(playerUuid); }

    // --- Utility ---

    public Location getNextMapOrigin() {
        return getNextMapOrigin(false);
    }

    public Location getNextMapOrigin(boolean infinite) {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) throw new IllegalStateException("No worlds are loaded — cannot determine map origin");
        int spacing = plugin.getConfigManager().getMapSpacing();
        int y = plugin.getConfigManager().getDefaultY();
        int x = GridCalculator.getNextMapX(maps.size(), spacing);
        if (infinite) {
            // Infinite maps get a completely isolated coordinate block to prevent
            // building into normal maps (spec §4: Infinite Map Collision).
            x += plugin.getConfigManager().getInfiniteMapOffsetX();
            int z = plugin.getConfigManager().getInfiniteMapOffsetZ();
            return new Location(world, x, y, z);
        }
        return new Location(world, x, y, 0);
    }
}
