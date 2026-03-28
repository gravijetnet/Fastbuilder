package net.gravijet.fastbuilder.manager;

import net.gravijet.fastbuilder.Main;
import net.gravijet.fastbuilder.model.MapBlock;
import net.gravijet.fastbuilder.model.MapTemplate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Saves and loads {@link MapTemplate} schematics as YAML files
 * under {@code plugins/FastBuilder/maps/<name>.yml}.
 */
public class SchematicManager {

    private final File mapsDir;

    public SchematicManager(Main plugin) {
        this.mapsDir = new File(plugin.getDataFolder(), "maps");
        if (!mapsDir.exists()) mapsDir.mkdirs();
    }

    // ── Save ──────────────────────────────────────────────────────

    /**
     * Captures a rectangular region of the world into a MapTemplate and saves it.
     *
     * @param name        template name (used as filename)
     * @param world       world of the region
     * @param pos1        one corner of the region
     * @param pos2        opposite corner of the region
     * @param spawnLoc    absolute spawn location (must be inside the region)
     * @param targetLocs  list of absolute target block locations
     * @return the created template
     */
    public MapTemplate saveSchematic(String name, World world,
                                     Location pos1, Location pos2,
                                     Location spawnLoc, List<Location> targetLocs) {
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        MapTemplate template = new MapTemplate(name);
        template.setDimensions(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);

        // Capture blocks
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    org.bukkit.block.Block b = world.getBlockAt(x, y, z);
                    if (b.getType() == Material.AIR) continue;
                    @SuppressWarnings("deprecation")
                    byte data = b.getData();
                    template.getBlocks().add(new MapBlock(x - minX, y - minY, z - minZ, b.getType(), data));
                }
            }
        }

        // Spawn offset
        template.setSpawn(
                spawnLoc.getBlockX() - minX,
                spawnLoc.getBlockY() - minY,
                spawnLoc.getBlockZ() - minZ,
                spawnLoc.getYaw(),
                spawnLoc.getPitch()
        );

        // Target offsets
        for (Location t : targetLocs) {
            template.addTarget(t.getBlockX() - minX, t.getBlockY() - minY, t.getBlockZ() - minZ);
        }

        persist(template);
        return template;
    }

    /** Persists an existing template (including display-name / icon changes). */
    public void persist(MapTemplate template) {
        File file = templateFile(template.getName());
        YamlConfiguration cfg = new YamlConfiguration();

        cfg.set("display-name", template.getDisplayName());
        cfg.set("icon", template.getIconMaterial().name());
        cfg.set("width",  template.getWidth());
        cfg.set("height", template.getHeight());
        cfg.set("depth",  template.getDepth());

        // Spawn
        cfg.set("spawn.x",     template.getSpawnRelX());
        cfg.set("spawn.y",     template.getSpawnRelY());
        cfg.set("spawn.z",     template.getSpawnRelZ());
        cfg.set("spawn.yaw",   template.getSpawnYaw());
        cfg.set("spawn.pitch", template.getSpawnPitch());

        // Targets
        List<String> tList = new ArrayList<>();
        for (int[] t : template.getTargetOffsets()) {
            tList.add(t[0] + "," + t[1] + "," + t[2]);
        }
        cfg.set("targets", tList);

        // Blocks
        List<String> bList = new ArrayList<>();
        for (MapBlock b : template.getBlocks()) bList.add(b.serialize());
        cfg.set("blocks", bList);

        try { cfg.save(file); }
        catch (Exception e) { Main.getInstance().getLogger().log(Level.WARNING, "Failed to save schematic " + template.getName(), e); }
    }

    // ── Load ──────────────────────────────────────────────────────

    public MapTemplate load(String name) {
        File file = templateFile(name);
        if (!file.exists()) return null;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        MapTemplate template  = new MapTemplate(name);

        template.setDisplayName(cfg.getString("display-name", name));
        Material icon = Material.getMaterial(cfg.getString("icon", "MAP"));
        template.setIconMaterial(icon != null ? icon : Material.MAP);
        template.setDimensions(cfg.getInt("width"), cfg.getInt("height"), cfg.getInt("depth"));

        template.setSpawn(
                cfg.getInt("spawn.x"), cfg.getInt("spawn.y"), cfg.getInt("spawn.z"),
                (float) cfg.getDouble("spawn.yaw"), (float) cfg.getDouble("spawn.pitch")
        );

        for (String ts : cfg.getStringList("targets")) {
            String[] p = ts.split(",");
            template.addTarget(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        }

        for (String bs : cfg.getStringList("blocks")) {
            try { template.getBlocks().add(MapBlock.deserialize(bs)); }
            catch (Exception e) { /* skip malformed block */ }
        }

        return template;
    }

    /** Loads all templates from the maps directory. */
    public List<MapTemplate> loadAll() {
        List<MapTemplate> list = new ArrayList<>();
        File[] files = mapsDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return list;
        for (File f : files) {
            String name = f.getName().replace(".yml", "");
            MapTemplate t = load(name);
            if (t != null) list.add(t);
        }
        return list;
    }

    public boolean delete(String name) {
        return templateFile(name).delete();
    }

    public boolean exists(String name) {
        return templateFile(name).exists();
    }

    // ── Paste ─────────────────────────────────────────────────────

    /**
     * Pastes a template into the world at the given origin.
     * Returns list of pasted block locations (for later clearing).
     */
    @SuppressWarnings("deprecation")
    public List<Location> paste(MapTemplate template, Location origin) {
        World world = origin.getWorld();
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        List<Location> placed = new ArrayList<>();
        for (MapBlock mb : template.getBlocks()) {
            Location loc = new Location(world, ox + mb.getRelX(), oy + mb.getRelY(), oz + mb.getRelZ());
            org.bukkit.block.Block block = world.getBlockAt(loc);
            block.setType(mb.getMaterial());
            block.setData(mb.getData());
            placed.add(loc);
        }
        return placed;
    }

    private File templateFile(String name) {
        return new File(mapsDir, name + ".yml");
    }
}
