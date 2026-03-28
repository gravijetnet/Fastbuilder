package net.gravijet.fastbuilder.model;

import net.gravijet.fastbuilder.util.CC;
import net.gravijet.fastbuilder.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public enum BridgeDistance {

    EASY    ("Easy",    5,  Material.EMERALD,    "&a", "&7Distance: &a5 blocks"),
    NORMAL  ("Normal",  10, Material.GOLD_INGOT, "&e", "&7Distance: &e10 blocks"),
    MEDIUM  ("Medium",  15, Material.FEATHER,    "&6", "&7Distance: &615 blocks"),
    HARD    ("Hard",    20, Material.BLAZE_POWDER,"&c", "&7Distance: &c20 blocks"),
    EXPERT  ("Expert",  25, Material.REDSTONE,   "&4", "&7Distance: &425 blocks"),
    INSANE  ("Insane",  30, Material.NETHER_STAR,"&5", "&7Distance: &530 blocks"),
    GODLIKE ("Godlike", 64, Material.BEDROCK,    "&d", "&7Distance: &d64 blocks");

    private final String   displayName;
    private final int      distance;
    private final Material icon;
    private final String   colorCode;
    private final String   loreLine;

    BridgeDistance(String displayName, int distance, Material icon,
                   String colorCode, String loreLine) {
        this.displayName = displayName;
        this.distance    = distance;
        this.icon        = icon;
        this.colorCode   = colorCode;
        this.loreLine    = loreLine;
    }

    public ItemStack toGuiItem(boolean selected) {
        ItemBuilder builder = new ItemBuilder(icon)
                .name(colorCode + "&l" + displayName + (selected ? " &7(Selected)" : ""))
                .lore(loreLine,
                      "",
                      selected ? "&aCurrently selected" : "&7Click to select");
        if (selected) builder.glow();
        return builder.build();
    }

    public String getDisplayName() { return displayName; }
    public int    getDistance()    { return distance;    }
    public String getColorCode()   { return CC.c(colorCode); }

    /** Short label for scoreboard (max ~12 visible chars) */
    public String getShortLabel() {
        return CC.c(colorCode + displayName + " &8(" + distance + ")");
    }
}
