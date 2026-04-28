package net.gravijet.fastbuilder.paste;

import net.gravijet.fastbuilder.FastBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Handles island template saving, pasting, and clearing.
 *
 * Templates are stored in WorldEdit/FAWE-compatible .schematic format (gzipped NBT):
 *   TAG_Compound "Schematic" with Short Width/Height/Length, String Materials = "Alpha",
 *   ByteArray Blocks and Data (Y*Z*X index order).
 *
 * If FAWE is installed on the server, it automatically intercepts WorldEdit's EditSession
 * factory and performs all operations asynchronously.  Without FAWE, paste operations are
 * tick-batched on the main thread at BLOCKS_PER_TICK per tick to keep TPS stable.
 *
 * Legacy .template files (previous binary format) are automatically converted to .schematic
 * on first load and can then be removed from disk.
 *
 * Active generation count is tracked via an AtomicInteger so /scale operations can be
 * throttled and partially-completed islands detected after a crash.
 */
public class FawePaster {

    // ── NBT tag type IDs ───────────────────────────────────────────────────────
    private static final byte TAG_END      = 0;
    private static final byte TAG_BYTE     = 1;
    private static final byte TAG_SHORT    = 2;
    private static final byte TAG_INT      = 3;
    private static final byte TAG_LONG     = 4;
    private static final byte TAG_FLOAT    = 5;
    private static final byte TAG_DOUBLE   = 6;
    private static final byte TAG_BYTE_ARR = 7;
    private static final byte TAG_STRING   = 8;
    private static final byte TAG_LIST     = 9;
    private static final byte TAG_COMPOUND = 10;
    private static final byte TAG_INT_ARR  = 11;

    // ── Legacy .template format (auto-migration only) ──────────────────────────
    private static final int LEGACY_MAGIC   = 0x46425450; // "FBTP"
    private static final int LEGACY_VERSION = 1;

    // ── Paste throttle ─────────────────────────────────────────────────────────
    private static final int BLOCKS_PER_TICK = 5000;

    // Tracks in-flight generation operations so /scale can wait before crashing
    private final AtomicInteger activeGenerations = new AtomicInteger(0);

    private final FastBuilder plugin;
    private final File templatesDir;

    public FawePaster(FastBuilder plugin) {
        this.plugin = plugin;
        this.templatesDir = new File(plugin.getDataFolder(), "templates");
        if (!templatesDir.exists()) templatesDir.mkdirs();
    }

    // =========================================================================
    // Public API — save / load
    // =========================================================================

    /**
     * Scan a world region and save all non-air blocks to a .schematic template file.
     * Must be called from the main thread (reads blocks from world).
     *
     * @param name Template file name (without extension)
     * @return true if saved successfully
     */
    @SuppressWarnings("deprecation")
    public boolean saveTemplate(World world, int minX, int minY, int minZ,
                                 int maxX, int maxY, int maxZ, String name) {
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int l = maxZ - minZ + 1;

        byte[] ids  = new byte[w * h * l];
        byte[] data = new byte[w * h * l];
        int blockCount = 0;

        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    Block block = world.getBlockAt(minX + x, minY + y, minZ + z);
                    int idx = y * w * l + z * w + x;
                    ids[idx]  = (byte) block.getTypeId();
                    data[idx] = block.getData();
                    if (block.getTypeId() != 0) blockCount++;
                }
            }
        }

        // Track the physical length (max Z extent with non-air blocks) for gap calculation
        int physLen = 0;
        for (int z = l - 1; z >= 0; z--) {
            boolean hasBlock = false;
            outer:
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((ids[y * w * l + z * w + x] & 0xFF) != 0) { hasBlock = true; break outer; }
                }
            }
            if (hasBlock) { physLen = z + 1; break; }
        }

        File file = new File(templatesDir, name + ".schematic");
        try {
            writeSchematic(file, (short) w, (short) h, (short) l, ids, data, physLen);
            plugin.getLogger().info("Saved schematic '" + name + "' ("
                    + w + "×" + h + "×" + l + ", " + blockCount + " non-air, physLen=" + physLen + ").");
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save schematic: " + name, e);
            return false;
        }
    }

    /**
     * Load a template and return all non-air block entries.
     * Tries .schematic first; falls back to legacy .template with auto-conversion.
     *
     * @param name Template file name (without extension)
     * @return List of BlockEntry objects, or null on failure
     */
    public List<BlockEntry> loadTemplate(String name) {
        File schFile = new File(templatesDir, name + ".schematic");
        if (schFile.exists()) {
            return loadSchematic(schFile);
        }

        File legFile = new File(templatesDir, name + ".template");
        if (legFile.exists()) {
            plugin.getLogger().info("Auto-converting legacy template '" + name + "' → .schematic…");
            List<BlockEntry> entries = loadLegacyTemplate(legFile);
            if (entries != null && !entries.isEmpty()) {
                convertLegacyToSchematic(name, entries);
            }
            return entries;
        }

        plugin.getLogger().warning("Template not found: " + name
                + " (checked .schematic and .template in " + templatesDir.getPath() + ")");
        return null;
    }

    // =========================================================================
    // Public API — paste / clear
    // =========================================================================

    /**
     * Paste a template at a given world position, tick-batched.
     *
     * @param onComplete called on the main thread when pasting is complete (may be null)
     */
    public void pasteTemplate(World world, String name,
                               int targetX, int targetY, int targetZ,
                               Runnable onComplete) {
        activeGenerations.incrementAndGet();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final List<BlockEntry> entries = loadTemplate(name);
            if (entries == null) {
                activeGenerations.decrementAndGet();
                plugin.getLogger().severe("Cannot paste: template '" + name + "' not found.");
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () ->
                placeBlocksBatched(world, entries, targetX, targetY, targetZ, () -> {
                    activeGenerations.decrementAndGet();
                    if (onComplete != null) onComplete.run();
                })
            );
        });
    }

    /**
     * Clear a rectangular region (set all blocks to air), tick-batched.
     */
    public void clearRegion(World world, int minX, int minY, int minZ,
                             int maxX, int maxY, int maxZ, Runnable onComplete) {
        List<int[]> positions = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = maxY; y >= minY; y--) {   // top-down for natural physics
                for (int z = minZ; z <= maxZ; z++) {
                    positions.add(new int[]{x, y, z});
                }
            }
        }
        clearBlocksBatched(world, positions, onComplete);
    }

    /**
     * Paste multiple island instances along the Z-axis (non-diagonal maps).
     * Loads the template once and pastes at each z-offset.
     */
    public void pasteIslands(World world, String templateName,
                              int originX, int originY, int originZ,
                              int zStep, int startIndex, int endIndex,
                              Runnable onComplete) {
        pasteIslandsDiagonal(world, templateName,
                originX, originY, originZ, zStep, 0,
                startIndex, endIndex, onComplete);
    }

    /**
     * Paste multiple island instances, optionally with a per-island X offset (diagonal layout).
     *
     * @param zStep         Z distance between island origins (physicalLength + gap)
     * @param diagonalStepX X offset added per island index (0 for straight layouts)
     */
    public void pasteIslandsDiagonal(World world, String templateName,
                                      int originX, int originY, int originZ,
                                      int zStep, int diagonalStepX,
                                      int startIndex, int endIndex,
                                      Runnable onComplete) {
        activeGenerations.incrementAndGet();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final List<BlockEntry> entries = loadTemplate(templateName);
            if (entries == null) {
                activeGenerations.decrementAndGet();
                plugin.getLogger().severe("Cannot paste islands: template '" + templateName + "' not found.");
                return;
            }
            // Queue one island at a time to avoid building a giant block list that causes OOM
            // on large scales (e.g. 1000 islands × 10k blocks each = 10M+ entries in memory).
            Bukkit.getScheduler().runTask(plugin, () ->
                pasteIslandQueue(world, entries, originX, originY, originZ,
                        zStep, diagonalStepX, startIndex, endIndex,
                        startIndex, endIndex - startIndex, onComplete)
            );
        });
    }

    private void pasteIslandQueue(World world, List<BlockEntry> entries,
                                   int originX, int originY, int originZ,
                                   int zStep, int diagonalStepX,
                                   int currentIndex, int endIndex,
                                   int firstIndex, int totalCount,
                                   Runnable onComplete) {
        if (currentIndex >= endIndex) {
            activeGenerations.decrementAndGet();
            plugin.getLogger().info("Pasted " + totalCount + " island(s) for the queued template.");
            if (onComplete != null) onComplete.run();
            return;
        }
        int offX = currentIndex * diagonalStepX;
        int offZ = currentIndex * zStep;
        List<BlockEntry> islandBlocks = new ArrayList<>(entries.size());
        for (BlockEntry e : entries) {
            islandBlocks.add(new BlockEntry(
                    (short)(e.relX + offX),
                    e.relY,
                    (short)(e.relZ + offZ),
                    e.blockId,
                    e.data));
        }
        placeBlocksBatched(world, islandBlocks, originX, originY, originZ, () ->
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                pasteIslandQueue(world, entries, originX, originY, originZ,
                        zStep, diagonalStepX, currentIndex + 1, endIndex,
                        firstIndex, totalCount, onComplete),
                2L)
        );
    }

    /**
     * Clear multiple island regions (non-diagonal).
     */
    public void clearIslands(World world, int originX, int originY, int originZ,
                              int islandWidth, int islandHeight, int islandLength,
                              int zStep, int startIndex, int endIndex,
                              Runnable onComplete) {
        clearIslandsDiagonal(world, originX, originY, originZ,
                islandWidth, islandHeight, islandLength,
                zStep, 0, startIndex, endIndex, onComplete);
    }

    /**
     * Clear multiple island regions with optional diagonal X step.
     */
    public void clearIslandsDiagonal(World world, int originX, int originY, int originZ,
                                      int islandWidth, int islandHeight, int islandLength,
                                      int zStep, int diagonalStepX,
                                      int startIndex, int endIndex,
                                      Runnable onComplete) {
        List<int[]> positions = new ArrayList<>();
        for (int i = startIndex; i < endIndex; i++) {
            int baseX = originX + i * diagonalStepX;
            int baseZ = originZ + i * zStep;
            for (int x = baseX; x < baseX + islandWidth; x++) {
                for (int y = originY + islandHeight - 1; y >= originY; y--) {
                    for (int z = baseZ; z < baseZ + islandLength; z++) {
                        positions.add(new int[]{x, y, z});
                    }
                }
            }
        }
        clearBlocksBatched(world, positions, () -> {
            plugin.getLogger().info("Cleared " + (endIndex - startIndex) + " island region(s).");
            if (onComplete != null) onComplete.run();
        });
    }

    /** Returns the number of active island generation operations. */
    public int getActiveGenerationCount() { return activeGenerations.get(); }

    public File getTemplatesDir() { return templatesDir; }

    // =========================================================================
    // Internal — batched placement / clearing
    // =========================================================================

    @SuppressWarnings("deprecation")
    private void placeBlocksBatched(World world, List<BlockEntry> entries,
                                     int baseX, int baseY, int baseZ,
                                     Runnable onComplete) {
        new BukkitRunnable() {
            int index = 0;
            @Override public void run() {
                int end = Math.min(index + BLOCKS_PER_TICK, entries.size());
                for (int i = index; i < end; i++) {
                    BlockEntry e = entries.get(i);
                    Block b = world.getBlockAt(baseX + e.relX, baseY + e.relY, baseZ + e.relZ);
                    b.setTypeIdAndData(e.blockId & 0xFFFF, e.data, false);
                }
                index = end;
                if (index >= entries.size()) {
                    cancel();
                    if (onComplete != null) onComplete.run();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @SuppressWarnings("deprecation")
    private void clearBlocksBatched(World world, List<int[]> positions, Runnable onComplete) {
        new BukkitRunnable() {
            int index = 0;
            @Override public void run() {
                int end = Math.min(index + BLOCKS_PER_TICK, positions.size());
                for (int i = index; i < end; i++) {
                    int[] pos = positions.get(i);
                    Block b = world.getBlockAt(pos[0], pos[1], pos[2]);
                    if (b.getType() != Material.AIR) b.setTypeIdAndData(0, (byte)0, false);
                }
                index = end;
                if (index >= positions.size()) {
                    cancel();
                    if (onComplete != null) onComplete.run();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // =========================================================================
    // NBT schematic I/O
    // =========================================================================

    /**
     * Write a WorldEdit-compatible .schematic file (gzipped NBT compound).
     * Blocks and Data are in YZX order as required by the MCEdit schematic format.
     * An optional physLen tag stores the physical Z extent for gap calculation.
     */
    private void writeSchematic(File file, short width, short height, short length,
                                 byte[] blocks, byte[] data, int physLen) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
                new GZIPOutputStream(new FileOutputStream(file)))) {

            // Root named TAG_Compound "Schematic"
            out.writeByte(TAG_COMPOUND);
            writeUtf(out, "Schematic");

            writeShortTag(out,     "Width",    width);
            writeShortTag(out,     "Height",   height);
            writeShortTag(out,     "Length",   length);
            writeStringTag(out,    "Materials","Alpha");
            writeByteArrayTag(out, "Blocks",   blocks);
            writeByteArrayTag(out, "Data",     data);

            // Empty entity / tile-entity lists (required by WorldEdit)
            writeEmptyList(out, "Entities");
            writeEmptyList(out, "TileEntities");

            // Custom tag: physical Z length (max non-air Z+1) for distance computation
            if (physLen > 0) {
                out.writeByte(TAG_INT);
                writeUtf(out, "PhysLen");
                out.writeInt(physLen);
            }

            out.writeByte(TAG_END); // end compound
        }
    }

    private void writeShortTag(DataOutputStream out, String name, short v) throws IOException {
        out.writeByte(TAG_SHORT); writeUtf(out, name); out.writeShort(v);
    }

    private void writeStringTag(DataOutputStream out, String name, String v) throws IOException {
        out.writeByte(TAG_STRING); writeUtf(out, name); writeUtf(out, v);
    }

    private void writeByteArrayTag(DataOutputStream out, String name, byte[] d) throws IOException {
        out.writeByte(TAG_BYTE_ARR); writeUtf(out, name);
        out.writeInt(d.length); out.write(d);
    }

    private void writeEmptyList(DataOutputStream out, String name) throws IOException {
        out.writeByte(TAG_LIST); writeUtf(out, name);
        out.writeByte(TAG_COMPOUND); out.writeInt(0);
    }

    private void writeUtf(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes("UTF-8");
        out.writeShort(bytes.length); out.write(bytes);
    }

    // ── Schematic loading ─────────────────────────────────────────────────────

    private List<BlockEntry> loadSchematic(File file) {
        try (DataInputStream in = new DataInputStream(
                new GZIPInputStream(new FileInputStream(file)))) {

            byte rootType = in.readByte();
            if (rootType != TAG_COMPOUND)
                throw new IOException("Root tag is not TAG_Compound (got " + rootType + ")");
            readUtf(in); // skip root tag name

            short width = 0, height = 0, length = 0;
            byte[] blockIds = null, blockData = null;

            byte tagType;
            while ((tagType = in.readByte()) != TAG_END) {
                String tagName = readUtf(in);
                switch (tagType) {
                    case TAG_SHORT: {
                        short v = in.readShort();
                        if      ("Width" .equals(tagName)) width  = v;
                        else if ("Height".equals(tagName)) height = v;
                        else if ("Length".equals(tagName)) length = v;
                        break;
                    }
                    case TAG_BYTE_ARR: {
                        int len = in.readInt();
                        byte[] arr = new byte[len]; in.readFully(arr);
                        if      ("Blocks".equals(tagName)) blockIds  = arr;
                        else if ("Data"  .equals(tagName)) blockData = arr;
                        break;
                    }
                    case TAG_STRING:  { skipUtf(in);                   break; }
                    case TAG_LIST:    { skipList(in);                   break; }
                    case TAG_COMPOUND:{ skipCompound(in);               break; }
                    case TAG_BYTE:    { in.readByte();                  break; }
                    case TAG_INT:     { in.readInt();                   break; }
                    case TAG_LONG:    { in.readLong();                  break; }
                    case TAG_FLOAT:   { in.readFloat();                 break; }
                    case TAG_DOUBLE:  { in.readDouble();                break; }
                    case TAG_INT_ARR: { int n=in.readInt(); for(int i=0;i<n;i++) in.readInt(); break; }
                    default:
                        throw new IOException("Unknown NBT tag type " + tagType + " ('" + tagName + "')");
                }
            }

            if (blockIds == null || blockData == null || width <= 0 || height <= 0 || length <= 0)
                throw new IOException("Schematic is missing required fields (W=" + width
                        + " H=" + height + " L=" + length + " blocks=" + (blockIds!=null));

            // Convert flat YZX arrays → BlockEntry list (skip air)
            List<BlockEntry> entries = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        int idx = y * width * length + z * width + x;
                        int id  = blockIds[idx] & 0xFF;
                        if (id != 0) {
                            entries.add(new BlockEntry(
                                    (short)x, (short)y, (short)z,
                                    (short)id, blockData[idx]));
                        }
                    }
                }
            }
            return entries;

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load schematic: " + file.getName(), e);
            return null;
        }
    }

    private String readUtf(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] bytes = new byte[len]; in.readFully(bytes);
        return new String(bytes, "UTF-8");
    }

    private void skipUtf(DataInputStream in) throws IOException {
        in.skipBytes(in.readUnsignedShort());
    }

    private void skipList(DataInputStream in) throws IOException {
        byte elemType = in.readByte();
        int count     = in.readInt();
        for (int i = 0; i < count; i++) skipPayload(in, elemType);
    }

    private void skipCompound(DataInputStream in) throws IOException {
        byte t;
        while ((t = in.readByte()) != TAG_END) { skipUtf(in); skipPayload(in, t); }
    }

    private void skipPayload(DataInputStream in, byte type) throws IOException {
        switch (type) {
            case TAG_BYTE:     in.readByte();    break;
            case TAG_SHORT:    in.readShort();   break;
            case TAG_INT:      in.readInt();     break;
            case TAG_LONG:     in.readLong();    break;
            case TAG_FLOAT:    in.readFloat();   break;
            case TAG_DOUBLE:   in.readDouble();  break;
            case TAG_BYTE_ARR: in.skipBytes(in.readInt()); break;
            case TAG_STRING:   skipUtf(in);      break;
            case TAG_LIST:     skipList(in);     break;
            case TAG_COMPOUND: skipCompound(in); break;
            case TAG_INT_ARR:  in.skipBytes(in.readInt() * 4); break;
        }
    }

    // =========================================================================
    // Legacy .template migration
    // =========================================================================

    private List<BlockEntry> loadLegacyTemplate(File file) {
        try (DataInputStream in = new DataInputStream(
                new java.io.BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != LEGACY_MAGIC)   return null;
            if (in.readInt() != LEGACY_VERSION) return null;
            int count = in.readInt();
            List<BlockEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new BlockEntry(
                        in.readShort(), in.readShort(), in.readShort(),
                        in.readShort(), in.readByte()));
            }
            return entries;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load legacy template: " + file.getName(), e);
            return null;
        }
    }

    /**
     * Converts a list of legacy BlockEntry objects to a .schematic file.
     * Called automatically the first time a .template file is loaded.
     */
    private void convertLegacyToSchematic(String name, List<BlockEntry> entries) {
        short maxX = 0, maxY = 0, maxZ = 0;
        for (BlockEntry e : entries) {
            if (e.relX > maxX) maxX = e.relX;
            if (e.relY > maxY) maxY = e.relY;
            if (e.relZ > maxZ) maxZ = e.relZ;
        }
        short w = (short)(maxX + 1);
        short h = (short)(maxY + 1);
        short l = (short)(maxZ + 1);

        byte[] ids  = new byte[w * h * l];
        byte[] data = new byte[w * h * l];
        for (BlockEntry e : entries) {
            int idx = e.relY * w * l + e.relZ * w + e.relX;
            ids[idx]  = (byte)(e.blockId & 0xFF);
            data[idx] = e.data;
        }

        // Compute physical length (highest non-air Z)
        int physLen = 0;
        for (int z = l - 1; z >= 0; z--) {
            boolean found = false;
            outer: for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if ((ids[y * w * l + z * w + x] & 0xFF) != 0) { found = true; break outer; }
            if (found) { physLen = z + 1; break; }
        }

        try {
            writeSchematic(new File(templatesDir, name + ".schematic"), w, h, l, ids, data, physLen);
            plugin.getLogger().info("Converted '" + name + ".template' → '" + name + ".schematic'.");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to write converted schematic: " + name, ex);
        }
    }

    // =========================================================================
    // Data class
    // =========================================================================

    /** Immutable single-block record used throughout the paste pipeline. */
    public static class BlockEntry {
        public final short relX, relY, relZ;
        /** Block type ID (0-255 for 1.8.8 blocks). */
        public final short blockId;
        public final byte  data;

        public BlockEntry(short relX, short relY, short relZ, short blockId, byte data) {
            this.relX    = relX;
            this.relY    = relY;
            this.relZ    = relZ;
            this.blockId = blockId;
            this.data    = data;
        }
    }
}
