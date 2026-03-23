package de.fastbuilder.listener;

import de.fastbuilder.gui.DistanceGui;
import de.fastbuilder.manager.GameManager;
import de.fastbuilder.model.BridgeDistance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Listener für Klicks im Distanz-Auswahl-GUI.
 *
 * Erkennt das GUI anhand des Inventar-Titels und leitet
 * die Auswahl an den GameManager weiter.
 */
public class GuiListener implements Listener {

    private final GameManager gameManager;

    public GuiListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Sicherstellen, dass es sich um ein Spieler-Inventar handelt
        if (!(event.getWhoClicked() instanceof Player)) return;

        // GUI anhand des Titels erkennen
        String title = event.getView().getTitle();
        if (!title.equals(DistanceGui.GUI_TITLE)) return;

        // Immer canceln um Item-Nehmen zu verhindern
        event.setCancelled(true);

        // Kein Item angeklickt?
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == org.bukkit.Material.AIR) return;

        // Distanz aus dem angeklickten Slot bestimmen
        BridgeDistance distance = DistanceGui.getDistanceForSlot(event.getRawSlot());
        if (distance == null) return;

        // Auswahl verarbeiten
        Player player = (Player) event.getWhoClicked();
        gameManager.onDistanceSelected(player, distance);
    }
}
