package net.gravijet.fastbuilder.map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds all persistent data for a single map type.
 * Maps are saved as individual YAML files in plugins/FastBuilder/maps/.
 */
public class MapData {

    private String name;
    private boolean enabled;
    private String icon;
    private String worldName;
    private int deathY = Integer.MIN_VALUE;

    // Origin of the first island (absolute world coordinates, min corner)
    private int originX, originY, originZ;

    // Island dimensions (calculated from selection during setup)
    private int islandWidth;  // X extent (build direction)
    private int islandHeight; // Y extent
    private int islandLength; // Z extent

    // Spawn offset relative to island min corner
    private double spawnOffsetX, spawnOffsetY, spawnOffsetZ;
    private float spawnYaw, spawnPitch;

    // NPC offset relative to island min corner
    private double npcOffsetX, npcOffsetY, npcOffsetZ;
    private float npcYaw;
    private float npcPitch;

    // Hologram offset relative to island min corner
    private double hologramOffsetX, hologramOffsetY, hologramOffsetZ;

    // Finish zone bounds relative to island min corner
    private int finishMinX, finishMinY, finishMinZ;
    private int finishMaxX, finishMaxY, finishMaxZ;

    // Island placement
    private int distance;
    private int scale;
    private boolean autoscale;

    // Template file name (primary design)
    private String templateFile;

    // Alternative design templates (admins can add extra schematics for the same map)
    private List<String> alternativeTemplates = new ArrayList<>();

    // Minimum valid run time (ms). Times faster than this are rejected. 0 = use global.
    private long minValidTime = 0;

    // Maximum allowed run time (ms). Runs that exceed this are auto-failed. 0 = disabled.
    private long maxCompletionTime = 0;

    // Infinite mode: no end island / finish zone. Players build indefinitely.
    private boolean infinite = false;

    // Custom length: per-player adjustable run distance (blocks from spawn to finish).
    // 0 = feature disabled for this map. When enabled, players can set their own length
    // within [minCustomLength, maxCustomLength].
    private int minCustomLength = 0;
    private int maxCustomLength = 0;

    // Time-based rank requirements (ms). -1 = not configured. Diamond > Gold > Silver > Bronze.
    private long diamondTime = -1;
    private long goldTime = -1;
    private long silverTime = -1;
    private long bronzeTime = -1;

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
        config.set("npc.pitch", npcPitch);
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
        config.set("alternative-templates", alternativeTemplates.isEmpty() ? null : alternativeTemplates);
        config.set("death-y", deathY == Integer.MIN_VALUE ? null : deathY);
        config.set("min-valid-time", minValidTime > 0 ? minValidTime : null);
        config.set("max-completion-time", maxCompletionTime > 0 ? maxCompletionTime : null);
        config.set("infinite", infinite ? true : null);
        config.set("custom-length.min", minCustomLength > 0 ? minCustomLength : null);
        config.set("custom-length.max", maxCustomLength > 0 ? maxCustomLength : null);
        config.set("rank.diamond", diamondTime > 0 ? diamondTime : null);
        config.set("rank.gold", goldTime > 0 ? goldTime : null);
        config.set("rank.silver", silverTime > 0 ? silverTime : null);
        config.set("rank.bronze", bronzeTime > 0 ? bronzeTime : null);
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
        npcPitch = (float) config.getDouble("npc.pitch", 0);
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
        alternativeTemplates = config.getStringList("alternative-templates");
        if (alternativeTemplates == null) alternativeTemplates = new ArrayList<>();
        deathY = config.getInt("death-y", Integer.MIN_VALUE);
        minValidTime = config.getLong("min-valid-time", 0);
        maxCompletionTime = config.getLong("max-completion-time", 0);
        infinite = config.getBoolean("infinite", false);
        minCustomLength = config.getInt("custom-length.min", 0);
        maxCustomLength = config.getInt("custom-length.max", 0);
        diamondTime = config.getLong("rank.diamond", -1);
        goldTime = config.getLong("rank.gold", -1);
        silverTime = config.getLong("rank.silver", -1);
        bronzeTime = config.getLong("rank.bronze", -1);
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
     */
    public Location getIslandSpawn(int islandIndex) {
        Location origin = getOrigin();
        origin.add(spawnOffsetX, spawnOffsetY, (long) islandIndex * distance + spawnOffsetZ);
        origin.setYaw(spawnYaw);
        origin.setPitch(spawnPitch);
        return origin;
    }

    /**
     * Get the NPC location for a specific island instance.
     */
    public Location getIslandNpcLocation(int islandIndex) {
        Location origin = getOrigin();
        origin.add(npcOffsetX, npcOffsetY, (long) islandIndex * distance + npcOffsetZ);
        origin.setYaw(npcYaw);
        origin.setPitch(npcPitch);
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

    public float getNpcPitch() { return npcPitch; }
    public void setNpcPitch(float pitch) { this.npcPitch = pitch; }

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

    /** Returns all available design templates: index 0 = primary, 1..N = alternatives. */
    public List<String> getAllTemplates() {
        List<String> all = new ArrayList<>();
        if (templateFile != null) all.add(templateFile);
        all.addAll(alternativeTemplates);
        return all;
    }

    public List<String> getAlternativeTemplates() { return alternativeTemplates; }

    public void addAlternativeTemplate(String template) {
        if (!alternativeTemplates.contains(template)) alternativeTemplates.add(template);
    }

    public boolean removeAlternativeTemplate(String template) {
        return alternativeTemplates.remove(template);
    }

    public int getDeathY() { return deathY; }
    public void setDeathY(int y) { this.deathY = y; }
    public boolean hasDeathY() { return deathY != Integer.MIN_VALUE; }

    public long getMinValidTime() { return minValidTime; }
    public void setMinValidTime(long t) { this.minValidTime = t; }

    public long getMaxCompletionTime() { return maxCompletionTime; }
    public void setMaxCompletionTime(long t) { this.maxCompletionTime = t; }

    public long getDiamondTime() { return diamondTime; }
    public void setDiamondTime(long t) { this.diamondTime = t; }

    public long getGoldTime() { return goldTime; }
    public void setGoldTime(long t) { this.goldTime = t; }

    public long getSilverTime() { return silverTime; }
    public void setSilverTime(long t) { this.silverTime = t; }

    public long getBronzeTime() { return bronzeTime; }
    public void setBronzeTime(long t) { this.bronzeTime = t; }

    public boolean isInfinite() { return infinite; }
    public void setInfinite(boolean infinite) { this.infinite = infinite; }

    public int getMinCustomLength() { return minCustomLength; }
    public void setMinCustomLength(int min) { this.minCustomLength = min; }

    public int getMaxCustomLength() { return maxCustomLength; }
    public void setMaxCustomLength(int max) { this.maxCustomLength = max; }

    /** Returns true if the custom length feature is active for this map. */
    public boolean hasCustomLength() {
        return minCustomLength > 0 && maxCustomLength > 0 && maxCustomLength >= minCustomLength;
    }

    /**
     * Returns the highest rank the player achieves with the given best time.
     * Order: Diamond > Gold > Silver > Bronze. Returns null if none met.
     */
    public String getPlayerRank(long bestTime) {
        if (bestTime <= 0) return null;
        if (diamondTime > 0 && bestTime <= diamondTime) return "Diamond";
        if (goldTime > 0 && bestTime <= goldTime) return "Gold";
        if (silverTime > 0 && bestTime <= silverTime) return "Silver";
        if (bronzeTime > 0 && bestTime <= bronzeTime) return "Bronze";
        return null;
    }
}
