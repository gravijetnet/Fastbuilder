package net.gravijet.fastbuilder.gui;

import net.gravijet.fastbuilder.model.BridgeDistance;
import net.gravijet.fastbuilder.util.CC;
import net.gravijet.fastbuilder.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class DistanceGui {

    public static final String TITLE = CC.c("&c&lFastBuilder &8- &7Select Distance");

    private static final ItemStack FILLER = new ItemBuilder(Material.STAINED_GLASS_PANE)
            .name("&r").build();

    /**
     * Distance slots in a 27-slot GUI (3 rows).
     * Row 0: all glass
     * Row 1: glass | E | N | M | H | Ex | I | G | glass
     * Row 2: all glass
     */
    private static final int[] DISTANCE_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    public void open(Player player, BridgeDistance selected) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        // Fill all with glass
        for (int i = 0; i < 27; i++) inv.setItem(i, FILLER);

        BridgeDistance[] values = BridgeDistance.values();
        for (int i = 0; i < values.length && i < DISTANCE_SLOTS.length; i++) {
            inv.setItem(DISTANCE_SLOTS[i], values[i].toGuiItem(values[i] == selected));
        }

        player.openInventory(inv);
    }

    public static BridgeDistance getDistanceForSlot(int rawSlot) {
        for (int i = 0; i < DISTANCE_SLOTS.length; i++) {
            if (DISTANCE_SLOTS[i] == rawSlot) {
                BridgeDistance[] values = BridgeDistance.values();
                return i < values.length ? values[i] : null;
            }
        }
        return null;
    }
}
