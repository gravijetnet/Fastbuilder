package net.gravijet.fastbuilder.gui;

import net.gravijet.fastbuilder.model.BridgeMaterial;
import net.gravijet.fastbuilder.util.CC;
import net.gravijet.fastbuilder.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * 54-slot material picker with multiple pages.
 *
 * Layout:
 *   Rows 0-4  (slots 0-44) → materials, 9 per row (up to 45 per page)
 *   Row 5     (slots 45-53) → navigation bar
 */
public class MaterialGui {

    public static final String TITLE     = CC.c("&c&lFastBuilder &8- &7Select Material");
    public static final int    PAGE_SIZE = 45;
    private static final int   PREV_SLOT = 45;
    private static final int   INFO_SLOT = 49;
    private static final int   NEXT_SLOT = 53;

    private static final ItemStack FILLER = new ItemBuilder(Material.STAINED_GLASS_PANE)
            .name("&r").build();

    public void open(Player player, BridgeMaterial selected, int page) {
        BridgeMaterial[] values    = BridgeMaterial.values();
        int              totalPages = (int) Math.ceil(values.length / (double) PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // Bottom navigation row
        for (int i = PAGE_SIZE; i < 54; i++) inv.setItem(i, FILLER);

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

        // Fill material slots
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && (start + i) < values.length; i++) {
            BridgeMaterial mat = values[start + i];
            inv.setItem(i, mat.toGuiItem(mat == selected));
        }

        player.openInventory(inv);
    }

    @Deprecated
    public void open(Player player, BridgeMaterial selected) {
        open(player, selected, 0);
    }

    /** Returns the BridgeMaterial for the clicked slot on the given page, or null if none. */
    public static BridgeMaterial getMaterialForSlot(int rawSlot, int page) {
        if (rawSlot < 0 || rawSlot >= PAGE_SIZE) return null;
        int idx = page * PAGE_SIZE + rawSlot;
        BridgeMaterial[] values = BridgeMaterial.values();
        return idx < values.length ? values[idx] : null;
    }

    /** Legacy single-page lookup (page 0). */
    public static BridgeMaterial getMaterialForSlot(int rawSlot) {
        return getMaterialForSlot(rawSlot, 0);
    }
}
