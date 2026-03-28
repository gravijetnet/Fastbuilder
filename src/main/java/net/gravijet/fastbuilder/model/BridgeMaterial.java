package net.gravijet.fastbuilder.model;

import net.gravijet.fastbuilder.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public enum BridgeMaterial {

    COBBLESTONE ("Cobblestone", Material.COBBLESTONE),
    STONE       ("Stone",       Material.STONE),
    DIRT        ("Dirt",        Material.DIRT),
    SAND        ("Sand",        Material.SAND),
    WOOD        ("Wood Planks", Material.WOOD),
    GLASS       ("Glass",       Material.GLASS);

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

    /** Returns a stack of 64 for the player's hotbar */
    public ItemStack toHotbarStack() {
        return new ItemBuilder(material, 64)
                .name("&f" + displayName)
                .lore("&7Your bridging material")
                .build();
    }

    public String   getDisplayName() { return displayName; }
    public Material getMaterial()    { return material;    }
}
