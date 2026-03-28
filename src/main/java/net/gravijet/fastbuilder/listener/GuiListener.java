package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.gui.DistanceGui;
import net.gravijet.fastbuilder.gui.MaterialGui;
import net.gravijet.fastbuilder.manager.GameManager;
import net.gravijet.fastbuilder.model.BridgeDistance;
import net.gravijet.fastbuilder.model.BridgeMaterial;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class GuiListener implements Listener {

    private final GameManager gameManager;

    public GuiListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title  = event.getView().getTitle();

        // Always cancel clicks inside our GUIs
        if (!title.equals(DistanceGui.TITLE) && !title.equals(MaterialGui.TITLE)) return;
        event.setCancelled(true);

        if (event.getCurrentItem() == null
                || event.getCurrentItem().getType() == Material.AIR
                || event.getCurrentItem().getType() == Material.STAINED_GLASS_PANE) return;

        if (title.equals(DistanceGui.TITLE)) {
            BridgeDistance distance = DistanceGui.getDistanceForSlot(event.getRawSlot());
            if (distance != null) gameManager.onDistanceSelected(player, distance);

        } else {
            BridgeMaterial material = MaterialGui.getMaterialForSlot(event.getRawSlot());
            if (material != null) gameManager.onMaterialSelected(player, material);
        }
    }
}
