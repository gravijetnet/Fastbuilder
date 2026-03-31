package net.gravijet.fastbuilder.map;

import net.gravijet.fastbuilder.FastBuilder;
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

    // mapName (lowercase) -> MapData
    private final Map<String, MapData> maps = new HashMap<>();

    // mapName (lowercase) -> list of island instances
    private final Map<String, List<IslandInstance>> islands = new HashMap<>();

    // Active setup sessions: playerUUID -> SetupSession
    private final Map<UUID, SetupSession> setupSessions = new HashMap<>();

    public MapManager(FastBuilder plugin) {
        this.plugin = plugin;
        this.mapsDir = new File(plugin.getDataFolder(), "maps");
        if (!mapsDir.exists()) {
            mapsDir.mkdirs();
        }
    }

    // --- Load/Save ---

    public void loadMaps() {
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
                maps.put(mapName.toLowerCase(), data);
                initializeIslands(data);

                plugin.getLogger().info("Loaded map: " + mapName
                        + " (scale=" + data.getScale()
                        + ", enabled=" + data.isEnabled() + ")");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load map file: " + file.getName(), e);
            }
        }

        plugin.getLogger().info("Loaded " + maps.size() + " map(s).");
    }

    public void saveAll() {
        for (MapData data : maps.values()) {
            saveMap(data);
        }
    }

    public void saveMap(MapData data) {
        File file = new File(mapsDir, data.getName().toLowerCase() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        data.saveTo(config);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save map: " + data.getName(), e);
        }
    }

    private void initializeIslands(MapData data) {
        List<IslandInstance> list = new ArrayList<>();
        for (int i = 0; i < data.getScale(); i++) {
            list.add(new IslandInstance(i));
        }
        islands.put(data.getName().toLowerCase(), list);
    }

    // --- Map CRUD ---

    public MapData getMap(String name) {
        return maps.get(name.toLowerCase());
    }

    public Collection<MapData> getAllMaps() {
        return Collections.unmodifiableCollection(maps.values());
    }

    public List<String> getMapNames() {
        List<String> names = new ArrayList<>();
        for (MapData data : maps.values()) {
            names.add(data.getName());
        }
        return names;
    }

    public List<String> getEnabledMapNames() {
        List<String> names = new ArrayList<>();
        for (MapData data : maps.values()) {
            if (data.isEnabled()) names.add(data.getName());
        }
        return names;
    }

    public List<String> getDisabledMapNames() {
        List<String> names = new ArrayList<>();
        for (MapData data : maps.values()) {
            if (!data.isEnabled()) names.add(data.getName());
        }
        return names;
    }

    public boolean mapExists(String name) {
        return maps.containsKey(name.toLowerCase());
    }

    /**
     * Create a new map from a completed setup session.
     */
    public MapData createMap(SetupSession session, String name) {
        MapData data = new MapData(name);
        // Origin is the actual grid position, not the setup build area
        Location gridOrigin = getNextMapOrigin();
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
        data.setSpawnYaw(session.getSpawnPoint().getYaw());
        data.setSpawnPitch(session.getSpawnPoint().getPitch());
        data.setNpcOffsetX(session.getNpcOffsetX());
        data.setNpcOffsetY(session.getNpcOffsetY());
        data.setNpcOffsetZ(session.getNpcOffsetZ());
        if (session.getNpcPoint() != null) {
            data.setNpcYaw(session.getNpcPoint().getYaw());
        }
        data.setFinishMinX(session.getFinishMinX());
        data.setFinishMinY(session.getFinishMinY());
        data.setFinishMinZ(session.getFinishMinZ());
        data.setFinishMaxX(session.getFinishMaxX());
        data.setFinishMaxY(session.getFinishMaxY());
        data.setFinishMaxZ(session.getFinishMaxZ());

        // Default distance = island length (Z extent) + 10 blocks gap
        data.setDistance(data.getIslandLength() + 10);
        data.setScale(1); // Start with the original island
        data.setEnabled(false);
        data.setTemplateFile(name.toLowerCase());

        maps.put(name.toLowerCase(), data);
        initializeIslands(data);
        saveMap(data);

        return data;
    }

    /**
     * Rename a map.
     */
    public boolean renameMap(String oldName, String newName) {
        MapData data = maps.remove(oldName.toLowerCase());
        if (data == null) return false;

        // Delete old file
        File oldFile = new File(mapsDir, oldName.toLowerCase() + ".yml");
        if (oldFile.exists()) oldFile.delete();

        // Update and save with new name
        List<IslandInstance> islandList = islands.remove(oldName.toLowerCase());
        data.setName(newName);
        maps.put(newName.toLowerCase(), data);
        if (islandList != null) {
            islands.put(newName.toLowerCase(), islandList);
        }
        saveMap(data);
        return true;
    }

    // --- Island Assignment ---

    public List<IslandInstance> getIslands(String mapName) {
        List<IslandInstance> list = islands.get(mapName.toLowerCase());
        return list != null ? Collections.unmodifiableList(list) : Collections.<IslandInstance>emptyList();
    }

    /**
     * Find the first free island on a map and assign it to the player.
     *
     * @return The assigned island index, or -1 if no free island
     */
    public int assignFreeIsland(String mapName, UUID playerUuid, String playerName) {
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

    /**
     * Assign a specific island to a player.
     *
     * @return true if successful
     */
    public boolean assignIsland(String mapName, int index, UUID playerUuid, String playerName) {
        List<IslandInstance> list = islands.get(mapName.toLowerCase());
        if (list == null || index < 0 || index >= list.size()) return false;

        IslandInstance island = list.get(index);
        if (island.isOccupied()) return false;

        island.setOccupant(playerUuid, playerName);
        return true;
    }

    /**
     * Free the island occupied by the given player on the given map.
     */
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

    /**
     * Free all islands occupied by a player across all maps.
     */
    public void freeAllIslands(UUID playerUuid) {
        for (List<IslandInstance> list : islands.values()) {
            for (IslandInstance island : list) {
                if (playerUuid.equals(island.getOccupantUuid())) {
                    island.clearOccupant();
                }
            }
        }
    }

    /**
     * Get the island a player currently occupies on a map.
     *
     * @return Island index, or -1 if not on any island
     */
    public int getPlayerIsland(String mapName, UUID playerUuid) {
        List<IslandInstance> list = islands.get(mapName.toLowerCase());
        if (list == null) return -1;

        for (IslandInstance island : list) {
            if (playerUuid.equals(island.getOccupantUuid())) {
                return island.getIndex();
            }
        }
        return -1;
    }

    public int getOccupiedCount(String mapName) {
        List<IslandInstance> list = islands.get(mapName.toLowerCase());
        if (list == null) return 0;
        int count = 0;
        for (IslandInstance island : list) {
            if (island.isOccupied()) count++;
        }
        return count;
    }

    // --- Scaling ---

    /**
     * Update the island count for a map.
     * Pastes new islands or clears removed ones via FAWE.
     */
    public void updateScale(MapData map, int newScale) {
        int oldScale = map.getScale();
        String mapKey = map.getName().toLowerCase();

        if (newScale > oldScale) {
            // Paste new islands
            plugin.getFawePaster().pasteIslands(
                    map.getWorld(),
                    map.getTemplateFile(),
                    map.getOriginX(), map.getOriginY(), map.getOriginZ(),
                    map.getDistance(),
                    oldScale, newScale,
                    null
            );

            // Add island instances
            List<IslandInstance> list = islands.get(mapKey);
            if (list == null) {
                list = new ArrayList<>();
                islands.put(mapKey, list);
            }
            for (int i = oldScale; i < newScale; i++) {
                list.add(new IslandInstance(i));
            }

        } else if (newScale < oldScale) {
            // Evict players from removed islands
            List<IslandInstance> list = islands.get(mapKey);
            if (list != null) {
                for (int i = newScale; i < oldScale && i < list.size(); i++) {
                    IslandInstance island = list.get(i);
                    if (island.isOccupied()) {
                        // Teleport player to default map spawn
                        org.bukkit.entity.Player p = Bukkit.getPlayer(island.getOccupantUuid());
                        if (p != null && p.isOnline()) {
                            relocatePlayer(p, map.getName());
                        }
                        island.clearOccupant();
                    }
                }
                // Trim island list
                while (list.size() > newScale) {
                    list.remove(list.size() - 1);
                }
            }

            // Clear removed island regions
            plugin.getFawePaster().clearIslands(
                    map.getWorld(),
                    map.getOriginX(), map.getOriginY(), map.getOriginZ(),
                    map.getIslandWidth(), map.getIslandHeight(), map.getIslandLength(),
                    map.getDistance(),
                    newScale, oldScale,
                    null
            );
        }

        map.setScale(newScale);
        saveMap(map);
    }

    /**
     * Check and apply autoscale for a map if needed.
     */
    public void checkAutoscale(MapData map) {
        if (!map.isAutoscale()) return;

        int occupied = getOccupiedCount(map.getName());
        int newScale = GridCalculator.calculateAutoscale(
                occupied,
                map.getScale(),
                plugin.getConfigManager().getAutoscaleThreshold(),
                plugin.getConfigManager().getAutoscaleMinIslands()
        );

        if (newScale != map.getScale()) {
            plugin.getLogger().info("Autoscaling map '" + map.getName()
                    + "' from " + map.getScale() + " to " + newScale + " islands.");
            updateScale(map, newScale);
        }
    }

    /**
     * Relocate a player to the default map or another enabled map.
     */
    public void relocatePlayer(org.bukkit.entity.Player player, String excludeMap) {
        String defaultMap = plugin.getConfigManager().getDefaultMap();

        // Try default map first
        if (!defaultMap.isEmpty() && !defaultMap.equalsIgnoreCase(excludeMap)) {
            MapData def = getMap(defaultMap);
            if (def != null && def.isEnabled()) {
                int island = assignFreeIsland(def.getName(), player.getUniqueId(), player.getName());
                if (island >= 0) {
                    player.teleport(def.getIslandSpawn(island));
                    return;
                }
            }
        }

        // Try any other enabled map
        for (MapData m : maps.values()) {
            if (m.isEnabled() && !m.getName().equalsIgnoreCase(excludeMap)) {
                int island = assignFreeIsland(m.getName(), player.getUniqueId(), player.getName());
                if (island >= 0) {
                    player.teleport(m.getIslandSpawn(island));
                    return;
                }
            }
        }

        // No map available, teleport to world spawn
        player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
    }

    // --- Setup Sessions ---

    public SetupSession getSetupSession(UUID playerUuid) {
        return setupSessions.get(playerUuid);
    }

    public SetupSession startSetupSession(UUID playerUuid, Location origin) {
        SetupSession session = new SetupSession(playerUuid, origin);
        setupSessions.put(playerUuid, session);
        return session;
    }

    public void removeSetupSession(UUID playerUuid) {
        setupSessions.remove(playerUuid);
    }

    public boolean hasSetupSession(UUID playerUuid) {
        return setupSessions.containsKey(playerUuid);
    }

    // --- Utility ---

    /**
     * Calculate the next map origin for a new map setup.
     * Maps are placed along X-axis at 2000, 4000, 6000...
     */
    public Location getNextMapOrigin() {
        World world = Bukkit.getWorlds().get(0);
        int spacing = plugin.getConfigManager().getMapSpacing();
        int y = plugin.getConfigManager().getDefaultY();
        int x = GridCalculator.getNextMapX(maps.size(), spacing);
        return new Location(world, x, y, 0);
    }
}
