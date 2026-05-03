package net.gravijet.fastbuilder.map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    // Physical Z extent of the template's non-air blocks (0 = not yet computed, falls back to islandLength).
    // When set, getActualZStep() uses this instead of islandLength so that the stored distance
    // equals the visual gap regardless of how much air padding the selection contains.
    private int physicalIslandLength = 0;

    // Template file name (primary design)
    private String templateFile;

    // Alternative design templates (admins can add extra schematics for the same map)
    private List<String> alternativeTemplates = new ArrayList<>();

    // Mode-specific design templates: custom-length maps use customLengthTemplates,
    // infinite maps use infiniteTemplates. Standard alternativeTemplates are for normal maps only.
    private List<String> customLengthTemplates = new ArrayList<>();
    private List<String> infiniteTemplates = new ArrayList<>();

    // Minimum valid run time (ms). Times faster than this are rejected. 0 = use global.
    private long minValidTime = 0;

    // Maximum allowed run time (ms). Runs that exceed this are auto-failed. 0 = disabled.
    private long maxCompletionTime = 0;

    // Infinite mode: no end island / finish zone. Players build indefinitely.
    private boolean infinite = false;

    // Custom length: per-player adjustable run distance (blocks from spawn to finish).
    // Legacy bounds mode: minCustomLength/maxCustomLength define the allowed range.
    // New end-island mode: endIslandTemplateFile is set and baseCustomLength is the default.
    private int minCustomLength = 0;
    private int maxCustomLength = 0;

    // End-island custom length: a separate physical island template that shifts in real-time.
    // When endIslandTemplateFile is non-null, this system replaces the simple end-platform.
    private String endIslandTemplateFile = null;
    private int endIslandWidth  = 0;
    private int endIslandHeight = 0;
    private int endIslandLength = 0;
    // Base distance: default X gap from the start island's spawn to the end island's min-X.
    private int baseCustomLength = 0;
    // Y and Z offsets of the end island's min corner relative to originY / island-slot Z.
    private int endIslandYOffset = 0;
    private int endIslandZOffset = 0;

    // Time-based rank requirements (ms). -1 = not configured. Diamond > Gold > Silver > Bronze.
    private long diamondTime = -1;
    private long goldTime = -1;
    private long silverTime = -1;
    private long bronzeTime = -1;

    // ── Diagonal layout ───────────────────────────────────────────────────────
    // When diagonal=true, island slot i is placed at:
    //   X = originX + i * diagonalStepX
    //   Z = originZ + i * actualZStep   (Z step is unchanged)
    // A positive diagonalStepX shifts each successive island eastward (+X).
    private boolean diagonal = false;
    private int diagonalStepX = 0;

    // Per-design spawn/finish/dimension profiles for alternative island templates.
    // Keyed by template file name (lower-case). Saved in the map YAML file.
    private final Map<String, DesignProfile> designProfiles = new HashMap<>();

    /**
     * Metadata for an alternative island design template: spawn location offset,
     * finish zone bounds, NPC position, and island dimensions — all relative to
     * the island's min corner. Stored per template key so the plugin can apply
     * the correct positions when a player selects a non-default design.
     */
    public static class DesignProfile {
        /** Spawn position offsets (in blocks) relative to island min corner. */
        public double spawnOffsetX, spawnOffsetY, spawnOffsetZ;
        public float  spawnYaw, spawnPitch;
        /** Finish zone bounds, relative to island min corner. */
        public int finishMinX, finishMinY, finishMinZ;
        public int finishMaxX, finishMaxY, finishMaxZ;
        /** Island dimensions for this design (used to adjust end-island gaps). */
        public int islandWidth, islandHeight, islandLength;
        /** NPC position relative to island min corner (0,0,0 = use map default). */
        public double npcOffsetX, npcOffsetY, npcOffsetZ;
        public float  npcYaw, npcPitch;
        /** Hologram position relative to island min corner (0,0,0 = use map default). */
        public double hologramOffsetX, hologramOffsetY, hologramOffsetZ;

        public DesignProfile() {}

        /** Returns true when a custom finish zone is configured for this profile. */
        public boolean hasFinishZone() {
            return finishMaxX > finishMinX || finishMaxZ > finishMinZ;
        }

        /** Returns true when a custom NPC position is configured for this profile. */
        public boolean hasNpcPosition() {
            return npcOffsetX != 0 || npcOffsetY != 0 || npcOffsetZ != 0;
        }

        /** Returns true when a custom hologram position is configured for this profile. */
        public boolean hasHologramPosition() {
            return hologramOffsetX != 0 || hologramOffsetY != 0 || hologramOffsetZ != 0;
        }
    }

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
        config.set("distance-version", 2); // gap-only format (not total Z step)
        config.set("island.physical-length", physicalIslandLength > 0 ? physicalIslandLength : null);
        config.set("scale", scale);
        config.set("autoscale", autoscale);
        config.set("template", templateFile);
        config.set("alternative-templates", alternativeTemplates.isEmpty() ? null : alternativeTemplates);
        config.set("custom-length-templates", customLengthTemplates.isEmpty() ? null : customLengthTemplates);
        config.set("infinite-templates", infiniteTemplates.isEmpty() ? null : infiniteTemplates);
        config.set("death-y", deathY == Integer.MIN_VALUE ? null : deathY);
        config.set("min-valid-time", minValidTime > 0 ? minValidTime : null);
        config.set("max-completion-time", maxCompletionTime > 0 ? maxCompletionTime : null);
        config.set("infinite", infinite ? true : null);
        config.set("custom-length.min", minCustomLength > 0 ? minCustomLength : null);
        config.set("custom-length.max", maxCustomLength > 0 ? maxCustomLength : null);
        config.set("custom-length.base", baseCustomLength > 0 ? baseCustomLength : null);
        config.set("custom-length.end-template", endIslandTemplateFile != null && !endIslandTemplateFile.isEmpty() ? endIslandTemplateFile : null);
        config.set("custom-length.end-width",  endIslandWidth  > 0 ? endIslandWidth  : null);
        config.set("custom-length.end-height", endIslandHeight > 0 ? endIslandHeight : null);
        config.set("custom-length.end-length", endIslandLength > 0 ? endIslandLength : null);
        config.set("custom-length.end-y-offset", endIslandYOffset != 0 ? endIslandYOffset : null);
        config.set("custom-length.end-z-offset", endIslandZOffset != 0 ? endIslandZOffset : null);
        config.set("rank.diamond", diamondTime > 0 ? diamondTime : null);
        config.set("rank.gold", goldTime > 0 ? goldTime : null);
        config.set("rank.silver", silverTime > 0 ? silverTime : null);
        config.set("rank.bronze", bronzeTime > 0 ? bronzeTime : null);

        // Diagonal layout
        config.set("diagonal", diagonal ? true : null);
        config.set("diagonal-step-x", diagonalStepX != 0 ? diagonalStepX : null);

        // Design profiles
        config.set("design-profiles", null); // clear stale entries
        for (Map.Entry<String, DesignProfile> e : designProfiles.entrySet()) {
            String b = "design-profiles." + e.getKey() + ".";
            DesignProfile p = e.getValue();
            config.set(b + "spawn.x",        p.spawnOffsetX);
            config.set(b + "spawn.y",        p.spawnOffsetY);
            config.set(b + "spawn.z",        p.spawnOffsetZ);
            config.set(b + "spawn.yaw",      (double) p.spawnYaw);
            config.set(b + "spawn.pitch",    (double) p.spawnPitch);
            config.set(b + "finish.min.x",   p.finishMinX);
            config.set(b + "finish.min.y",   p.finishMinY);
            config.set(b + "finish.min.z",   p.finishMinZ);
            config.set(b + "finish.max.x",   p.finishMaxX);
            config.set(b + "finish.max.y",   p.finishMaxY);
            config.set(b + "finish.max.z",   p.finishMaxZ);
            config.set(b + "island.width",   p.islandWidth);
            config.set(b + "island.height",  p.islandHeight);
            config.set(b + "island.length",  p.islandLength);
            if (p.hasNpcPosition()) {
                config.set(b + "npc.x",     p.npcOffsetX);
                config.set(b + "npc.y",     p.npcOffsetY);
                config.set(b + "npc.z",     p.npcOffsetZ);
                config.set(b + "npc.yaw",   (double) p.npcYaw);
                config.set(b + "npc.pitch", (double) p.npcPitch);
            }
            if (p.hasHologramPosition()) {
                config.set(b + "hologram.x", p.hologramOffsetX);
                config.set(b + "hologram.y", p.hologramOffsetY);
                config.set(b + "hologram.z", p.hologramOffsetZ);
            }
        }
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

        // Load raw distance, then migrate legacy format.
        // Old format: distance = islandLength + gap (total Z step).
        // New format: distance = gap only (southernmost to northernmost).
        // Maps without the "distance-version" key are old format.
        distance = config.getInt("distance");
        if (!config.contains("distance-version") && islandLength > 0 && distance >= islandLength) {
            distance -= islandLength;
        }
        physicalIslandLength = config.getInt("island.physical-length", 0);

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
        scale = config.getInt("scale", 1);
        autoscale = config.getBoolean("autoscale", false);
        templateFile = config.getString("template");
        alternativeTemplates = config.getStringList("alternative-templates");
        if (alternativeTemplates == null) alternativeTemplates = new ArrayList<>();
        customLengthTemplates = config.getStringList("custom-length-templates");
        if (customLengthTemplates == null) customLengthTemplates = new ArrayList<>();
        infiniteTemplates = config.getStringList("infinite-templates");
        if (infiniteTemplates == null) infiniteTemplates = new ArrayList<>();
        deathY = config.getInt("death-y", Integer.MIN_VALUE);
        minValidTime = config.getLong("min-valid-time", 0);
        maxCompletionTime = config.getLong("max-completion-time", 0);
        infinite = config.getBoolean("infinite", false);
        minCustomLength = config.getInt("custom-length.min", 0);
        maxCustomLength = config.getInt("custom-length.max", 0);
        baseCustomLength    = config.getInt("custom-length.base",       0);
        endIslandTemplateFile = config.getString("custom-length.end-template", null);
        endIslandWidth  = config.getInt("custom-length.end-width",  0);
        endIslandHeight = config.getInt("custom-length.end-height", 0);
        endIslandLength = config.getInt("custom-length.end-length", 0);
        endIslandYOffset = config.getInt("custom-length.end-y-offset", 0);
        endIslandZOffset = config.getInt("custom-length.end-z-offset", 0);
        diamondTime = config.getLong("rank.diamond", -1);
        goldTime = config.getLong("rank.gold", -1);
        silverTime = config.getLong("rank.silver", -1);
        bronzeTime = config.getLong("rank.bronze", -1);

        // Diagonal layout
        diagonal      = config.getBoolean("diagonal", false);
        diagonalStepX = config.getInt("diagonal-step-x", 0);

        // Design profiles
        designProfiles.clear();
        ConfigurationSection profilesSec = config.getConfigurationSection("design-profiles");
        if (profilesSec != null) {
            for (String key : profilesSec.getKeys(false)) {
                String b = "design-profiles." + key + ".";
                DesignProfile p = new DesignProfile();
                p.spawnOffsetX  = config.getDouble(b + "spawn.x");
                p.spawnOffsetY  = config.getDouble(b + "spawn.y");
                p.spawnOffsetZ  = config.getDouble(b + "spawn.z");
                p.spawnYaw      = (float) config.getDouble(b + "spawn.yaw");
                p.spawnPitch    = (float) config.getDouble(b + "spawn.pitch");
                p.finishMinX    = config.getInt(b + "finish.min.x");
                p.finishMinY    = config.getInt(b + "finish.min.y");
                p.finishMinZ    = config.getInt(b + "finish.min.z");
                p.finishMaxX    = config.getInt(b + "finish.max.x");
                p.finishMaxY    = config.getInt(b + "finish.max.y");
                p.finishMaxZ    = config.getInt(b + "finish.max.z");
                p.islandWidth   = config.getInt(b + "island.width");
                p.islandHeight  = config.getInt(b + "island.height");
                p.islandLength  = config.getInt(b + "island.length");
                p.npcOffsetX    = config.getDouble(b + "npc.x", 0);
                p.npcOffsetY    = config.getDouble(b + "npc.y", 0);
                p.npcOffsetZ    = config.getDouble(b + "npc.z", 0);
                p.npcYaw        = (float) config.getDouble(b + "npc.yaw", 0);
                p.npcPitch      = (float) config.getDouble(b + "npc.pitch", 0);
                p.hologramOffsetX = config.getDouble(b + "hologram.x", 0);
                p.hologramOffsetY = config.getDouble(b + "hologram.y", 0);
                p.hologramOffsetZ = config.getDouble(b + "hologram.z", 0);
                designProfiles.put(key.toLowerCase(), p);
            }
        }
    }

    // --- Computed ---

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public Location getOrigin() {
        return new Location(getWorld(), originX, originY, originZ);
    }

    /**
     * The actual Z separation between adjacent island slots.
     * Uses physicalIslandLength (non-air block span) when available so that
     * {@code distance} equals the visible gap in-game. Falls back to islandLength
     * for maps that pre-date physical-length tracking.
     */
    public int getActualZStep() {
        int len = physicalIslandLength > 0 ? physicalIslandLength : islandLength;
        return len > 0 ? len + distance : distance;
    }

    /**
     * X offset for island slot {@code index} due to diagonal layout (0 for straight maps).
     */
    private long diagonalX(int index) {
        return (long) index * diagonalStepX;
    }

    /**
     * Get the absolute spawn location for a specific island instance.
     */
    public Location getIslandSpawn(int islandIndex) {
        Location origin = getOrigin();
        origin.add(diagonalX(islandIndex) + spawnOffsetX,
                   spawnOffsetY,
                   (long) islandIndex * getActualZStep() + spawnOffsetZ);
        origin.setYaw(spawnYaw);
        origin.setPitch(spawnPitch);
        return origin;
    }

    /**
     * Get the NPC location for a specific island instance.
     */
    public Location getIslandNpcLocation(int islandIndex) {
        Location origin = getOrigin();
        origin.add(diagonalX(islandIndex) + npcOffsetX,
                   npcOffsetY,
                   (long) islandIndex * getActualZStep() + npcOffsetZ);
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
            Location spawn = getIslandSpawn(islandIndex);
            return spawn.clone().add(0, 3, 0);
        }
        Location origin = getOrigin();
        origin.add(diagonalX(islandIndex) + hologramOffsetX,
                   hologramOffsetY,
                   (long) islandIndex * getActualZStep() + hologramOffsetZ);
        return origin;
    }

    public Location getIslandMin(int islandIndex) {
        return new Location(getWorld(),
                originX + diagonalX(islandIndex),
                originY,
                originZ + (long) islandIndex * getActualZStep());
    }

    public Location getIslandMax(int islandIndex) {
        return new Location(getWorld(),
                originX + diagonalX(islandIndex) + islandWidth - 1,
                originY + islandHeight - 1,
                originZ + (long) islandIndex * getActualZStep() + islandLength - 1);
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

    public int getPhysicalIslandLength() { return physicalIslandLength; }
    public void setPhysicalIslandLength(int l) { this.physicalIslandLength = l; }

    public boolean isDiagonal() { return diagonal; }
    public void setDiagonal(boolean diagonal) { this.diagonal = diagonal; }

    public int getDiagonalStepX() { return diagonalStepX; }
    public void setDiagonalStepX(int stepX) { this.diagonalStepX = stepX; }

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

    // Custom-length-specific designs
    public List<String> getCustomLengthTemplates() { return customLengthTemplates; }
    public void addCustomLengthTemplate(String template) {
        if (!customLengthTemplates.contains(template)) customLengthTemplates.add(template);
    }
    public boolean removeCustomLengthTemplate(String template) {
        return customLengthTemplates.remove(template);
    }

    // Infinite-specific designs
    public List<String> getInfiniteTemplates() { return infiniteTemplates; }
    public void addInfiniteTemplate(String template) {
        if (!infiniteTemplates.contains(template)) infiniteTemplates.add(template);
    }
    public boolean removeInfiniteTemplate(String template) {
        return infiniteTemplates.remove(template);
    }

    /**
     * Returns the available design templates for a given map mode.
     * Always includes the primary template as index 0.
     * Custom-length maps show customLengthTemplates; infinite maps show infiniteTemplates;
     * standard maps show alternativeTemplates.
     */
    public List<String> getTemplatesForMode() {
        List<String> result = new ArrayList<>();
        if (templateFile != null) result.add(templateFile);
        if (infinite) {
            result.addAll(infiniteTemplates);
        } else if (minCustomLength > 0 || maxCustomLength > 0 || baseCustomLength > 0
                || endIslandTemplateFile != null) {
            result.addAll(customLengthTemplates);
        } else {
            result.addAll(alternativeTemplates);
        }
        return result;
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

    /** Returns true if the full end-island system is configured (new mode). */
    public boolean hasEndIsland() {
        return endIslandTemplateFile != null && !endIslandTemplateFile.isEmpty() && baseCustomLength > 0;
    }

    /** Returns true if the custom length feature is active for this map (either mode). */
    public boolean hasCustomLength() {
        if (hasEndIsland()) return true;
        return minCustomLength > 0 && maxCustomLength > 0 && maxCustomLength >= minCustomLength;
    }

    /**
     * Effective minimum custom length. For end-island maps without explicit bounds,
     * defaults to half the base distance (minimum 1).
     */
    public int getEffectiveMinCustomLength() {
        if (minCustomLength > 0) return minCustomLength;
        if (baseCustomLength > 0) return Math.max(1, baseCustomLength / 2);
        return 1;
    }

    /** Hard cap on custom length regardless of per-map configuration. */
    public static final int GLOBAL_MAX_CUSTOM_LENGTH = 1700;

    /**
     * Effective maximum custom length. Hard-capped at {@value #GLOBAL_MAX_CUSTOM_LENGTH} blocks.
     * For end-island maps without explicit bounds, defaults to 2× the base distance (or the cap).
     */
    public int getEffectiveMaxCustomLength() {
        int configured;
        if (maxCustomLength > 0) configured = maxCustomLength;
        else if (baseCustomLength > 0) configured = baseCustomLength * 2;
        else configured = GLOBAL_MAX_CUSTOM_LENGTH;
        return Math.min(configured, GLOBAL_MAX_CUSTOM_LENGTH);
    }

    public String getEndIslandTemplateFile() { return endIslandTemplateFile; }
    public void setEndIslandTemplateFile(String f) { this.endIslandTemplateFile = f; }

    public int getEndIslandWidth()  { return endIslandWidth; }
    public void setEndIslandWidth(int w)  { this.endIslandWidth = w; }

    public int getEndIslandHeight() { return endIslandHeight; }
    public void setEndIslandHeight(int h) { this.endIslandHeight = h; }

    public int getEndIslandLength() { return endIslandLength; }
    public void setEndIslandLength(int l) { this.endIslandLength = l; }

    public int getBaseCustomLength() { return baseCustomLength; }
    public void setBaseCustomLength(int base) { this.baseCustomLength = base; }

    public int getEndIslandYOffset() { return endIslandYOffset; }
    public void setEndIslandYOffset(int y) { this.endIslandYOffset = y; }

    public int getEndIslandZOffset() { return endIslandZOffset; }
    public void setEndIslandZOffset(int z) { this.endIslandZOffset = z; }

    // -------------------------------------------------------------------------
    // Design-profile accessors
    // -------------------------------------------------------------------------

    /** Returns the profile for the given template key, or null if none has been set. */
    public DesignProfile getDesignProfile(String templateKey) {
        if (templateKey == null) return null;
        return designProfiles.get(templateKey.toLowerCase());
    }

    /** Stores (or replaces) the design profile for the given template key. */
    public void setDesignProfile(String templateKey, DesignProfile profile) {
        if (profile == null) {
            designProfiles.remove(templateKey.toLowerCase());
        } else {
            designProfiles.put(templateKey.toLowerCase(), profile);
        }
    }

    /** Returns true if a design profile has been recorded for the given template key. */
    public boolean hasDesignProfile(String templateKey) {
        return templateKey != null && designProfiles.containsKey(templateKey.toLowerCase());
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
