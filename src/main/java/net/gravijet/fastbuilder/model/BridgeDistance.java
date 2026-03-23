package de.fastbuilder.model;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Enum für die wählbaren Brücken-Distanzen.
 * Jeder Wert enthält den Anzeigenamen, die Blockanzahl und ein GUI-Icon.
 */
public enum BridgeDistance {

    EXTRA_SHORT("Extra Short", 12, Material.EMERALD, ChatColor.GREEN),
    SHORT(      "Short",       13, Material.GOLD_INGOT, ChatColor.YELLOW),
    LONG(       "Long",        64, Material.REDSTONE,   ChatColor.RED);

    private final String displayName;   // Anzeigename im GUI
    private final int    distance;      // Anzahl der Blöcke zwischen Plattformen
    private final Material icon;        // GUI-Icon
    private final ChatColor color;      // Farbe im GUI

    BridgeDistance(String displayName, int distance, Material icon, ChatColor color) {
        this.displayName = displayName;
        this.distance    = distance;
        this.icon        = icon;
        this.color       = color;
    }

    /** Erstellt das ItemStack für das GUI-Inventar */
    public ItemStack toGuiItem() {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color + displayName);
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(ChatColor.GRAY + "Distanz: " + ChatColor.WHITE + distance + " Blöcke");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public String getDisplayName() { return displayName; }
    public int    getDistance()    { return distance;    }
    public Material getIcon()      { return icon;        }
    public ChatColor getColor()    { return color;       }
}
