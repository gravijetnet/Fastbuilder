package net.gravijet.fastbuilder.paste;

import net.gravijet.fastbuilder.FastBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Handles island template saving, pasting, and clearing.
 *
 * Templates are stored in a custom binary format (.template):
 *   Header: 4-byte magic (FBTP), 4-byte version, 4-byte block count
 *   Per block: 2-byte relX, 2-byte relY, 2-byte relZ, 2-byte blockId, 1-byte data
 *
 * Block operations use Bukkit API with tick-batched scheduling to avoid lag.
 * If FAWE is installed, it automatically optimizes chunk-level operations.
 *
 * Batch size is tuned for 1.8.8 servers: ~5000 blocks per tick keeps TPS stable.
 */
public class FawePaster {

    private static final int MAGIC = 0x46425450; // "FBTP"
    private static final int VERSION = 1;
    private static final int BLOCKS_PER_TICK = 5000;

    private final FastBuilder plugin;
    private final File templatesDir;

    public FawePaster(FastBuilder plugin) {
        this.plugin = plugin;
        this.templatesDir = new File(plugin.getDataFolder(), "templates");
        if (!templatesDir.exists()) {
            templatesDir.mkdirs();
        }
    }

    /**
     * Scan a world region and save all non-air blocks to a template file.
     * Must be called from the main thread (reads blocks from world).
     *
     * @param world The bukkit world
     * @param minX  Min X of the region (absolute)
     * @param minY  Min Y of the region (absolute)
     * @param minZ  Min Z of the region (absolute)
     * @param maxX  Max X of the region (absolute)
     * @param maxY  Max Y of the region (absolute)
     * @param maxZ  Max Z of the region (absolute)
     * @param name  Template file name (without extension)
     * @return true if saved successfully
     */
    @SuppressWarnings("deprecation")
    public boolean saveTemplate(World world, int minX, int minY, int minZ,
                                 int maxX, int maxY, int maxZ, String name) {
        List<BlockEntry> entries = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR) {
                        entries.add(new BlockEntry(
                                (short) (x - minX),
                                (short) (y - minY),
                                (short) (z - minZ),
                                (short) block.getTypeId(),
                                block.getData()
                        ));
                    }
                }
            }
        }

        File file = new File(templatesDir, name + ".template");
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(entries.size());
            for (BlockEntry entry : entries) {
                out.writeShort(entry.relX);
                out.writeShort(entry.relY);
                out.writeShort(entry.relZ);
                out.writeShort(entry.blockId);
                out.writeByte(entry.data);
            }
            plugin.getLogger().info("Saved template '" + name + "' with " + entries.size() + " blocks.");
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save template: " + name, e);
            return false;
        }
    }

    /**
     * Load a template from file.
     *
     * @param name Template file name (without extension)
     * @return List of block entries, or null if loading fails
     */
    public List<BlockEntry> loadTemplate(String name) {
        File file = new File(templatesDir, name + ".template");
        if (!file.exists()) {
            plugin.getLogger().warning("Template file not found: " + file.getPath());
            return null;
        }

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                plugin.getLogger().severe("Invalid template file (bad magic): " + name);
                return null;
            }
            int version = in.readInt();
            if (version != VERSION) {
                plugin.getLogger().severe("Unsupported template version " + version + ": " + name);
                return null;
            }
            int count = in.readInt();
            List<BlockEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new BlockEntry(
                        in.readShort(),
                        in.readShort(),
                        in.readShort(),
                        in.readShort(),
                        in.readByte()
                ));
            }
            return entries;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load template: " + name, e);
            return null;
        }
    }

    /**
     * Paste a template at the given world position using tick-batched scheduling.
     *
     * @param world      The bukkit world
     * @param name       Template name (without extension)
     * @param targetX    Target min X (absolute)
     * @param targetY    Target min Y (absolute)
     * @param targetZ    Target min Z (absolute)
     * @param onComplete Callback on the main thread when done (may be null)
     */
    public void pasteTemplate(World world, String name, int targetX, int targetY, int targetZ,
                               Runnable onComplete) {
        // Load template off main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final List<BlockEntry> entries = loadTemplate(name);
                if (entries == null) {
                    plugin.getLogger().severe("Cannot paste template: " + name);
                    return;
                }
                // Schedule block placement on main thread in batches
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        placeBlocksBatched(world, entries, targetX, targetY, targetZ, onComplete);
                    }
                });
            }
        });
    }

    /**
     * Clear a region (set all blocks to air) using tick-batched scheduling.
     */
    public void clearRegion(World world, int minX, int minY, int minZ,
                             int maxX, int maxY, int maxZ, Runnable onComplete) {
        // Build list of positions to clear
        final List<int[]> positions = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = maxY; y >= minY; y--) { // Top-down for natural physics
                for (int z = minZ; z <= maxZ; z++) {
                    positions.add(new int[]{x, y, z});
                }
            }
        }

        clearBlocksBatched(world, positions, 0, onComplete);
    }

    /**
     * Paste multiple island instances.
     * Loads the template once, then pastes at each island offset.
     */
    public void pasteIslands(World world, String templateName,
                              int originX, int originY, int originZ,
                              int distance, int startIndex, int endIndex,
                              Runnable onComplete) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final List<BlockEntry> entries = loadTemplate(templateName);
                if (entries == null) {
                    plugin.getLogger().severe("Cannot paste islands: template " + templateName + " not found.");
                    return;
                }

                // Build combined block list for all islands
                final List<BlockEntry> allBlocks = new ArrayList<>();
                for (int i = startIndex; i < endIndex; i++) {
                    int offsetX = i * distance;
                    for (BlockEntry entry : entries) {
                        allBlocks.add(new BlockEntry(
                                (short) (entry.relX + offsetX),
                                entry.relY,
                                entry.relZ,
                                entry.blockId,
                                entry.data
                        ));
                    }
                }

                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        placeBlocksBatched(world, allBlocks, originX, originY, originZ, new Runnable() {
                            @Override
                            public void run() {
                                plugin.getLogger().info("Pasted " + (endIndex - startIndex)
                                        + " islands for template '" + templateName + "'.");
                                if (onComplete != null) onComplete.run();
                            }
                        });
                    }
                });
            }
        });
    }

    /**
     * Clear multiple island regions.
     */
    public void clearIslands(World world, int originX, int originY, int originZ,
                              int islandWidth, int islandHeight, int islandLength,
                              int distance, int startIndex, int endIndex,
                              Runnable onComplete) {
        final List<int[]> positions = new ArrayList<>();
        for (int i = startIndex; i < endIndex; i++) {
            int baseX = originX + i * distance;
            for (int x = baseX; x < baseX + islandWidth; x++) {
                for (int y = originY + islandHeight - 1; y >= originY; y--) {
                    for (int z = originZ; z < originZ + islandLength; z++) {
                        positions.add(new int[]{x, y, z});
                    }
                }
            }
        }

        clearBlocksBatched(world, positions, 0, new Runnable() {
            @Override
            public void run() {
                plugin.getLogger().info("Cleared " + (endIndex - startIndex) + " island regions.");
                if (onComplete != null) onComplete.run();
            }
        });
    }

    // --- Internal batched processing ---

    /**
     * Place blocks in batches across ticks to avoid lag spikes.
     * Uses BukkitRunnable for clean self-cancellation.
     */
    @SuppressWarnings("deprecation")
    private void placeBlocksBatched(final World world, final List<BlockEntry> entries,
                                     final int baseX, final int baseY, final int baseZ,
                                     final Runnable onComplete) {
        new org.bukkit.scheduler.BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                int end = Math.min(index + BLOCKS_PER_TICK, entries.size());

                for (int i = index; i < end; i++) {
                    BlockEntry entry = entries.get(i);
                    Block block = world.getBlockAt(
                            baseX + entry.relX,
                            baseY + entry.relY,
                            baseZ + entry.relZ);
                    block.setTypeIdAndData(entry.blockId, entry.data, false);
                }

                index = end;

                if (index >= entries.size()) {
                    cancel();
                    if (onComplete != null) onComplete.run();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Clear blocks (set to air) in batches across ticks.
     */
    @SuppressWarnings("deprecation")
    private void clearBlocksBatched(final World world, final List<int[]> positions,
                                     final int startIdx, final Runnable onComplete) {
        new org.bukkit.scheduler.BukkitRunnable() {
            int index = startIdx;

            @Override
            public void run() {
                int end = Math.min(index + BLOCKS_PER_TICK, positions.size());

                for (int i = index; i < end; i++) {
                    int[] pos = positions.get(i);
                    Block block = world.getBlockAt(pos[0], pos[1], pos[2]);
                    if (block.getType() != Material.AIR) {
                        block.setTypeIdAndData(0, (byte) 0, false);
                    }
                }

                index = end;

                if (index >= positions.size()) {
                    cancel();
                    if (onComplete != null) onComplete.run();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public File getTemplatesDir() {
        return templatesDir;
    }

    /**
     * A single block entry in a template.
     */
    public static class BlockEntry {
        public final short relX, relY, relZ;
        public final short blockId;
        public final byte data;

        public BlockEntry(short relX, short relY, short relZ, short blockId, byte data) {
            this.relX = relX;
            this.relY = relY;
            this.relZ = relZ;
            this.blockId = blockId;
            this.data = data;
        }
    }
}
