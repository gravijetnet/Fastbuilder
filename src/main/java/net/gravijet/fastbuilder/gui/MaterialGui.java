package net.gravijet.fastbuilder.gui;

import net.gravijet.fastbuilder.model.BridgeMaterial;
import net.gravijet.fastbuilder.util.CC;
import net.gravijet.fastbuilder.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class MaterialGui {

    public static final String TITLE = CC.c("&c&lFastBuilder &8- &7Select Material");

    private static final ItemStack FILLER = new ItemBuilder(Material.STAINED_GLASS_PANE)
            .name("&r").build();

    /** Material slots in a 27-slot GUI, centered in row 1 */
    private static final int[] MATERIAL_SLOTS = {10, 11, 12, 13, 14, 15};

    public void open(Player player, BridgeMaterial selected) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        for (int i = 0; i < 27; i++) inv.setItem(i, FILLER);

        BridgeMaterial[] values = BridgeMaterial.values();
        for (int i = 0; i < values.length && i < MATERIAL_SLOTS.length; i++) {
            inv.setItem(MATERIAL_SLOTS[i], values[i].toGuiItem(values[i] == selected));
        }

        player.openInventory(inv);
    }

    public static BridgeMaterial getMaterialForSlot(int rawSlot) {
        for (int i = 0; i < MATERIAL_SLOTS.length; i++) {
            if (MATERIAL_SLOTS[i] == rawSlot) {
                BridgeMaterial[] values = BridgeMaterial.values();
                return i < values.length ? values[i] : null;
            }
        }
        return null;
    }
}
