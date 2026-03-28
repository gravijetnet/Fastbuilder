package net.gravijet.fastbuilder.model;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * An admin-created map template.  Stores the schematic blocks, spawn offset,
 * target block offsets and display meta relative to the schematic origin (pos1).
 */
public class MapTemplate {

    private final String     name;
    private String           displayName;
    private Material         iconMaterial = Material.MAP;
    private final List<MapBlock> blocks   = new ArrayList<>();

    // Spawn offset from origin
    private int   spawnRelX, spawnRelY, spawnRelZ;
    private float spawnYaw   = 90f;
    private float spawnPitch = 0f;

    // Target block offsets (player must reach one of these)
    private final List<int[]> targetOffsets = new ArrayList<>(); // each int[]{relX,relY,relZ}

    // Bounding box size (set when saving)
    private int width, height, depth;

    public MapTemplate(String name) {
        this.name        = name;
        this.displayName = name;
    }

    // ── Accessors ─────────────────────────────────────────────────

    public String       getName()        { return name;         }
    public String       getDisplayName() { return displayName;  }
    public Material     getIconMaterial(){ return iconMaterial; }
    public List<MapBlock> getBlocks()    { return blocks;       }

    public int   getSpawnRelX()  { return spawnRelX;  }
    public int   getSpawnRelY()  { return spawnRelY;  }
    public int   getSpawnRelZ()  { return spawnRelZ;  }
    public float getSpawnYaw()   { return spawnYaw;   }
    public float getSpawnPitch() { return spawnPitch; }

    public List<int[]> getTargetOffsets() { return targetOffsets; }

    public int getWidth()  { return width;  }
    public int getHeight() { return height; }
    public int getDepth()  { return depth;  }

    // ── Mutators ──────────────────────────────────────────────────

    public void setDisplayName(String n)    { this.displayName  = n; }
    public void setIconMaterial(Material m) { this.iconMaterial = m; }

    public void setSpawn(int x, int y, int z, float yaw, float pitch) {
        this.spawnRelX  = x;
        this.spawnRelY  = y;
        this.spawnRelZ  = z;
        this.spawnYaw   = yaw;
        this.spawnPitch = pitch;
    }

    public void addTarget(int relX, int relY, int relZ) {
        targetOffsets.add(new int[]{relX, relY, relZ});
    }

    public void clearTargets() { targetOffsets.clear(); }

    public void setDimensions(int w, int h, int d) {
        this.width  = w;
        this.height = h;
        this.depth  = d;
    }
}
