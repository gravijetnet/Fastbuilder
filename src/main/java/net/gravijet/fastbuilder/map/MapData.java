package net.gravijet.fastbuilder.map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Holds all persistent data for a single map type.
 * Maps are saved as individual YAML files in plugins/FastBuilder/maps/.
 */
public class MapData {

    private String name;
    private boolean enabled;
    private String icon;
    private String worldName;

    // Origin of the first island (absolute world coordinates, min corner)
    private int originX, originY, originZ;

    // Island dimensions (calculated from selection during setup)
    private int islandWidth;  // X extent (build direction)
    private int islandHeight; // Y extent
    private int islandLength; // Z extent

    // Spawn offset relative to island min corner
    private double spawnOffsetX, spawnOffsetY, spawnOffsetZ;
    // Spawn yaw/pitch - always overridden to East (-90) in getIslandSpawn()
    private float spawnYaw, spawnPitch;

    // NPC offset relative to island min corner
    private double npcOffsetX, npcOffsetY, npcOffsetZ;
    private float npcYaw;

    // Hologram offset relative to island min corner
    private double hologramOffsetX, hologramOffsetY, hologramOffsetZ;

    // Finish zone bounds relative to island min corner
    private int finishMinX, finishMinY, finishMinZ;
    private int finishMaxX, finishMaxY, finishMaxZ;

    // Island placement
    private int distance;
    private int scale;
    private boolean autoscale;

    // Template file name
    private String templateFile;

    public MapData(String name) {
        this.name = name;
        this.enabled = false;
        this.icon = "SANDSTONE:0";
        this.worldName = "world";
        this.scale = 1;
        this.autoscale = false;
    }

    // --- Persistence ---

    public void saveTo(FileConfiguration config) {
        config.set("name", name);
        config.set("enabled", enabled);
        config.set("icon", icon);
        config.set("world", worldName);
        config.set("origin.x", originX);
        config.set("origin.y", originY);
        config.set("origin.z", originZ);
        config.set("island.width", islandWidth);
        config.set("island.height", islandHeight);
        config.set("island.length", islandLength);
        config.set("spawn.x", spawnOffsetX);
        config.set("spawn.y", spawnOffsetY);
        config.set("spawn.z", spawnOffsetZ);
        config.set("spawn.yaw", spawnYaw);
        config.set("spawn.pitch", spawnPitch);
        config.set("npc.x", npcOffsetX);
        config.set("npc.y", npcOffsetY);
        config.set("npc.z", npcOffsetZ);
        config.set("npc.yaw", npcYaw);
        config.set("hologram.x", hologramOffsetX);
        config.set("hologram.y", hologramOffsetY);
        config.set("hologram.z", hologramOffsetZ);
        config.set("finish.min.x", finishMinX);
        config.set("finish.min.y", finishMinY);
        config.set("finish.min.z", finishMinZ);
        config.set("finish.max.x", finishMaxX);
        config.set("finish.max.y", finishMaxY);
        config.set("finish.max.z", finishMaxZ);
        config.set("distance", distance);
        config.set("scale", scale);
        config.set("autoscale", autoscale);
        config.set("template", templateFile);
    }

    public void loadFrom(FileConfiguration config) {
        name = config.getString("name", name);
        enabled = config.getBoolean("enabled", false);
        icon = config.getString("icon", "SANDSTONE:0");
        worldName = config.getString("world", "world");
        originX = config.getInt("origin.x");
        originY = config.getInt("origin.y");
        originZ = config.getInt("origin.z");
        islandWidth = config.getInt("island.width");
        islandHeight = config.getInt("island.height");
        islandLength = config.getInt("island.length");
        spawnOffsetX = config.getDouble("spawn.x");
        spawnOffsetY = config.getDouble("spawn.y");
        spawnOffsetZ = config.getDouble("spawn.z");
        spawnYaw = (float) config.getDouble("spawn.yaw");
        spawnPitch = (float) config.getDouble("spawn.pitch");
        npcOffsetX = config.getDouble("npc.x");
        npcOffsetY = config.getDouble("npc.y");
        npcOffsetZ = config.getDouble("npc.z");
        npcYaw = (float) config.getDouble("npc.yaw");
        hologramOffsetX = config.getDouble("hologram.x", 0);
        hologramOffsetY = config.getDouble("hologram.y", 0);
        hologramOffsetZ = config.getDouble("hologram.z", 0);
        finishMinX = config.getInt("finish.min.x");
        finishMinY = config.getInt("finish.min.y");
        finishMinZ = config.getInt("finish.min.z");
        finishMaxX = config.getInt("finish.max.x");
        finishMaxY = config.getInt("finish.max.y");
        finishMaxZ = config.getInt("finish.max.z");
        distance = config.getInt("distance");
        scale = config.getInt("scale", 1);
        autoscale = config.getBoolean("autoscale", false);
        templateFile = config.getString("template");
    }

    // --- Computed ---

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public Location getOrigin() {
        return new Location(getWorld(), originX, originY, originZ);
    }

    /**
     * Get the absolute spawn location for a specific island instance.
     * Always faces East (yaw -90) as per spec.
     */
    public Location getIslandSpawn(int islandIndex) {
        Location origin = getOrigin();
        origin.add(spawnOffsetX, spawnOffsetY, (long) islandIndex * distance + spawnOffsetZ);
        origin.setYaw(-90f); // Always face East (positive X direction)
        origin.setPitch(0f);
        return origin;
    }

    /**
     * Get the NPC location for a specific island instance.
     */
    public Location getIslandNpcLocation(int islandIndex) {
        Location origin = getOrigin();
        origin.add(npcOffsetX, npcOffsetY, (long) islandIndex * distance + npcOffsetZ);
        origin.setYaw(npcYaw);
        origin.setPitch(0f);
        return origin;
    }

    /**
     * Get the hologram location for a specific island instance.
     * Falls back to 3 blocks above spawn if hologram offset not configured.
     */
    public Location getIslandHologramLocation(int islandIndex) {
        if (hologramOffsetX == 0 && hologramOffsetY == 0 && hologramOffsetZ == 0) {
            // Fallback: 3 blocks above spawn point
            Location spawn = getIslandSpawn(islandIndex);
            return spawn.clone().add(0, 3, 0);
        }
        Location origin = getOrigin();
        origin.add(hologramOffsetX, hologramOffsetY, (long) islandIndex * distance + hologramOffsetZ);
        return origin;
    }

    public Location getIslandMin(int islandIndex) {
        return new Location(getWorld(), originX, originY, originZ + (long) islandIndex * distance);
    }

    public Location getIslandMax(int islandIndex) {
        return new Location(getWorld(),
                originX + islandWidth - 1,
                originY + islandHeight - 1,
                originZ + (long) islandIndex * distance + islandLength - 1);
    }

    // --- Getters/Setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }

    public int getOriginX() { return originX; }
    public void setOriginX(int originX) { this.originX = originX; }

    public int getOriginY() { return originY; }
    public void setOriginY(int originY) { this.originY = originY; }

    public int getOriginZ() { return originZ; }
    public void setOriginZ(int originZ) { this.originZ = originZ; }

    public int getIslandWidth() { return islandWidth; }
    public void setIslandWidth(int islandWidth) { this.islandWidth = islandWidth; }

    public int getIslandHeight() { return islandHeight; }
    public void setIslandHeight(int islandHeight) { this.islandHeight = islandHeight; }

    public int getIslandLength() { return islandLength; }
    public void setIslandLength(int islandLength) { this.islandLength = islandLength; }

    public double getSpawnOffsetX() { return spawnOffsetX; }
    public void setSpawnOffsetX(double x) { this.spawnOffsetX = x; }

    public double getSpawnOffsetY() { return spawnOffsetY; }
    public void setSpawnOffsetY(double y) { this.spawnOffsetY = y; }

    public double getSpawnOffsetZ() { return spawnOffsetZ; }
    public void setSpawnOffsetZ(double z) { this.spawnOffsetZ = z; }

    public float getSpawnYaw() { return spawnYaw; }
    public void setSpawnYaw(float yaw) { this.spawnYaw = yaw; }

    public float getSpawnPitch() { return spawnPitch; }
    public void setSpawnPitch(float pitch) { this.spawnPitch = pitch; }

    public int getFinishMinX() { return finishMinX; }
    public void setFinishMinX(int x) { this.finishMinX = x; }
    public int getFinishMinY() { return finishMinY; }
    public void setFinishMinY(int y) { this.finishMinY = y; }
    public int getFinishMinZ() { return finishMinZ; }
    public void setFinishMinZ(int z) { this.finishMinZ = z; }

    public int getFinishMaxX() { return finishMaxX; }
    public void setFinishMaxX(int x) { this.finishMaxX = x; }
    public int getFinishMaxY() { return finishMaxY; }
    public void setFinishMaxY(int y) { this.finishMaxY = y; }
    public int getFinishMaxZ() { return finishMaxZ; }
    public void setFinishMaxZ(int z) { this.finishMaxZ = z; }

    public double getNpcOffsetX() { return npcOffsetX; }
    public void setNpcOffsetX(double x) { this.npcOffsetX = x; }
    public double getNpcOffsetY() { return npcOffsetY; }
    public void setNpcOffsetY(double y) { this.npcOffsetY = y; }
    public double getNpcOffsetZ() { return npcOffsetZ; }
    public void setNpcOffsetZ(double z) { this.npcOffsetZ = z; }
    public float getNpcYaw() { return npcYaw; }
    public void setNpcYaw(float yaw) { this.npcYaw = yaw; }

    public double getHologramOffsetX() { return hologramOffsetX; }
    public void setHologramOffsetX(double x) { this.hologramOffsetX = x; }
    public double getHologramOffsetY() { return hologramOffsetY; }
    public void setHologramOffsetY(double y) { this.hologramOffsetY = y; }
    public double getHologramOffsetZ() { return hologramOffsetZ; }
    public void setHologramOffsetZ(double z) { this.hologramOffsetZ = z; }

    public int getDistance() { return distance; }
    public void setDistance(int distance) { this.distance = distance; }

    public int getScale() { return scale; }
    public void setScale(int scale) { this.scale = scale; }

    public boolean isAutoscale() { return autoscale; }
    public void setAutoscale(boolean autoscale) { this.autoscale = autoscale; }

    public String getTemplateFile() { return templateFile; }
    public void setTemplateFile(String templateFile) { this.templateFile = templateFile; }
}
