package net.gravijet.fastbuilder.model;

import net.gravijet.fastbuilder.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public enum BridgeMaterial {

    // ── Page 1 ────────────────────────────────────────────────────
    COBBLESTONE  ("Cobblestone",      Material.COBBLESTONE),
    STONE        ("Stone",            Material.STONE),
    DIRT         ("Dirt",             Material.DIRT),
    GRASS        ("Grass",            Material.GRASS),
    SAND         ("Sand",             Material.SAND),
    GRAVEL       ("Gravel",           Material.GRAVEL),
    WOOD         ("Wood Planks",      Material.WOOD),
    GLASS        ("Glass",            Material.GLASS),
    NETHERRACK   ("Netherrack",       Material.NETHERRACK),

    // ── Page 2 ────────────────────────────────────────────────────
    SANDSTONE    ("Sandstone",        Material.SANDSTONE),
    BRICKS       ("Bricks",           Material.BRICK),
    STONE_BRICK  ("Stone Bricks",     Material.SMOOTH_BRICK),
    NETHER_BRICK ("Nether Bricks",    Material.NETHER_BRICK),
    CLAY         ("Clay",             Material.CLAY),
    SPONGE       ("Sponge",           Material.SPONGE),
    OBSIDIAN     ("Obsidian",         Material.OBSIDIAN),
    WOOL         ("White Wool",       Material.WOOL),
    SNOW_BLOCK   ("Snow Block",       Material.SNOW_BLOCK),

    // ── Page 3 ────────────────────────────────────────────────────
    IRON_BLOCK   ("Iron Block",       Material.IRON_BLOCK),
    GOLD_BLOCK   ("Gold Block",       Material.GOLD_BLOCK),
    DIAMOND_BLOCK("Diamond Block",    Material.DIAMOND_BLOCK),
    EMERALD_BLOCK("Emerald Block",    Material.EMERALD_BLOCK),
    LAPIS_BLOCK  ("Lapis Block",      Material.LAPIS_BLOCK),
    REDSTONE_BLOCK("Redstone Block",  Material.REDSTONE_BLOCK),
    COAL_BLOCK   ("Coal Block",       Material.COAL_BLOCK),
    QUARTZ_BLOCK ("Quartz Block",     Material.QUARTZ_BLOCK),
    ICE          ("Ice",              Material.ICE);

    private final String   displayName;
    private final Material material;

    BridgeMaterial(String displayName, Material material) {
        this.displayName = displayName;
        this.material    = material;
    }

    public ItemStack toGuiItem(boolean selected) {
        ItemBuilder builder = new ItemBuilder(material)
                .name("&f" + displayName + (selected ? " &7(Selected)" : ""))
                .lore(selected ? "&aCurrently selected" : "&7Click to select");
        if (selected) builder.glow();
        return builder.build();
    }

    public ItemStack toHotbarStack() {
        return new ItemBuilder(material, 64)
                .name("&f" + displayName)
                .lore("&7Your bridging material")
                .build();
    }

    public String   getDisplayName() { return displayName; }
    public Material getMaterial()    { return material;    }
}
