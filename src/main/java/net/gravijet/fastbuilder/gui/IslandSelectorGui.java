package net.gravijet.fastbuilder.gui;

import net.gravijet.fastbuilder.manager.MapManager;
import net.gravijet.fastbuilder.model.MapTemplate;
import net.gravijet.fastbuilder.util.CC;
import net.gravijet.fastbuilder.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 54-slot inventory that lets a player choose which map to play on.
 *
 * Layout:
 *   Rows 0-4  → map entries (up to 45), filled left-to-right
 *   Row 5     → navigation bar (prev  |  info  |  next)
 */
public class IslandSelectorGui {

    public static final String TITLE = CC.c("&c&lFastBuilder &8- &7Select Map");

    private static final int PAGE_SIZE  = 45;
    private static final int PREV_SLOT  = 45;
    private static final int INFO_SLOT  = 49;
    private static final int NEXT_SLOT  = 53;

    private static final ItemStack FILLER = new ItemBuilder(Material.STAINED_GLASS_PANE)
            .name("&r").build();

    private static final ItemStack QUICK_PRACTICE = new ItemBuilder(Material.COMPASS)
            .name("&e&lQuick Practice")
            .lore("&7Choose a distance and build", "&7on a classic stone island.")
            .build();

    public void open(Player player, MapManager mapManager, int page) {
        List<MapTemplate> maps = new ArrayList<>(mapManager.getAllTemplates());
        int totalPages = Math.max(1, (int) Math.ceil((maps.size() + 1) / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // Fill bottom row with glass
        for (int i = PAGE_SIZE; i < 54; i++) inv.setItem(i, FILLER);

        // Navigation items
        if (page > 0) {
            inv.setItem(PREV_SLOT, new ItemBuilder(Material.ARROW)
                    .name("&7\u25c4 Previous").lore("&7Page " + page).build());
        }
        if (page < totalPages - 1) {
            inv.setItem(NEXT_SLOT, new ItemBuilder(Material.ARROW)
                    .name("&7Next \u25ba").lore("&7Page " + (page + 2)).build());
        }
        inv.setItem(INFO_SLOT, new ItemBuilder(Material.PAPER)
                .name("&7Page &f" + (page + 1) + " &7/ &f" + totalPages).build());

        // Build entry list: quick-practice first, then admin maps
        List<ItemStack> entries = new ArrayList<>();
        entries.add(QUICK_PRACTICE);
        for (MapTemplate t : maps) {
            entries.add(new ItemBuilder(t.getIconMaterial())
                    .name(t.getDisplayName())
                    .lore("&7Click to play on this map")
                    .build());
        }

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && (start + i) < entries.size(); i++) {
            inv.setItem(i, entries.get(start + i));
        }

        player.openInventory(inv);
    }

    /** Returns the map name for the clicked slot on the given page, or null for quick-practice,
     *  or "PREV"/"NEXT" for navigation, or empty string for invalid. */
    public static String resolveClick(int slot, int page, MapManager mapManager) {
        if (slot == PREV_SLOT) return "PREV";
        if (slot == NEXT_SLOT) return "NEXT";
        if (slot >= PAGE_SIZE) return "";

        List<MapTemplate> maps = new ArrayList<>(mapManager.getAllTemplates());
        List<String> names = new ArrayList<>();
        names.add(null); // quick-practice
        for (MapTemplate t : maps) names.add(t.getName());

        int idx = page * PAGE_SIZE + slot;
        if (idx >= names.size()) return "";
        return names.get(idx); // null → quick-practice
    }
}
