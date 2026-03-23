package de.fastbuilder.gui;

import de.fastbuilder.model.BridgeDistance;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * GUI für die Distanz-Auswahl.
 *
 * Öffnet ein 3x9 Inventar mit einem Icon für jede Distanz-Stufe.
 * Das Klick-Event wird in GuiListener abgefangen.
 */
public class DistanceGui {

    /** Titel des GUIs (wird auch zur Erkennung im GuiListener genutzt) */
    public static final String GUI_TITLE = ChatColor.DARK_AQUA + "» Distanz wählen «";

    /** Slots der Distanz-Items im 9er-Inventar */
    private static final int SLOT_EXTRA_SHORT = 2;
    private static final int SLOT_SHORT        = 4;
    private static final int SLOT_LONG         = 6;

    /**
     * Öffnet das Distanz-Auswahl-GUI für den Spieler.
     */
    public void open(Player player) {
        // Ein-Zeilen-Inventar (9 Slots, genug für 3 Optionen)
        Inventory inv = Bukkit.createInventory(null, 9, GUI_TITLE);

        // Items an feste Slots setzen
        inv.setItem(SLOT_EXTRA_SHORT, BridgeDistance.EXTRA_SHORT.toGuiItem());
        inv.setItem(SLOT_SHORT,       BridgeDistance.SHORT.toGuiItem());
        inv.setItem(SLOT_LONG,        BridgeDistance.LONG.toGuiItem());

        player.openInventory(inv);
    }

    /**
     * Gibt die BridgeDistance zurück, die dem angeklickten Slot entspricht.
     * Gibt null zurück, wenn kein gültiger Slot angeklickt wurde.
     */
    public static BridgeDistance getDistanceForSlot(int slot) {
        switch (slot) {
            case SLOT_EXTRA_SHORT: return BridgeDistance.EXTRA_SHORT;
            case SLOT_SHORT:       return BridgeDistance.SHORT;
            case SLOT_LONG:        return BridgeDistance.LONG;
            default:               return null;
        }
    }
}
