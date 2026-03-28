package net.gravijet.fastbuilder.model;

import org.bukkit.Material;

/** A single block stored in a map schematic, with a position relative to the schematic origin. */
public class MapBlock {

    private final int      relX;
    private final int      relY;
    private final int      relZ;
    private final Material material;
    private final byte     data;

    public MapBlock(int relX, int relY, int relZ, Material material, byte data) {
        this.relX     = relX;
        this.relY     = relY;
        this.relZ     = relZ;
        this.material = material;
        this.data     = data;
    }

    public int      getRelX()     { return relX;     }
    public int      getRelY()     { return relY;     }
    public int      getRelZ()     { return relZ;     }
    public Material getMaterial() { return material; }
    public byte     getData()     { return data;     }

    /** Serializes to "relX,relY,relZ:MATERIAL:data" */
    public String serialize() {
        return relX + "," + relY + "," + relZ + ":" + material.name() + ":" + data;
    }

    /** Parses from "relX,relY,relZ:MATERIAL:data" */
    public static MapBlock deserialize(String s) {
        String[] parts = s.split(":");
        String[] pos   = parts[0].split(",");
        Material mat   = Material.getMaterial(parts[1]);
        byte     d     = parts.length > 2 ? Byte.parseByte(parts[2]) : 0;
        return new MapBlock(Integer.parseInt(pos[0]), Integer.parseInt(pos[1]),
                Integer.parseInt(pos[2]), mat, d);
    }
}
