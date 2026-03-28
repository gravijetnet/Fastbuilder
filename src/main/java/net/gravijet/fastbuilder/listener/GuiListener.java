package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.gui.DistanceGui;
import net.gravijet.fastbuilder.gui.IslandSelectorGui;
import net.gravijet.fastbuilder.gui.MaterialGui;
import net.gravijet.fastbuilder.manager.GameManager;
import net.gravijet.fastbuilder.model.BridgeDistance;
import net.gravijet.fastbuilder.model.BridgeMaterial;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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

        // ── Distance GUI ──────────────────────────────────────────
        if (title.equals(DistanceGui.TITLE)) {
            event.setCancelled(true);
            if (isEmptyOrFiller(event)) return;
            BridgeDistance distance = DistanceGui.getDistanceForSlot(event.getRawSlot());
            if (distance != null) gameManager.onDistanceSelected(player, distance);
            return;
        }

        // ── Material GUI ──────────────────────────────────────────
        if (title.equals(MaterialGui.TITLE)) {
            event.setCancelled(true);
            if (isEmptyOrFiller(event)) return;
            int page = gameManager.getMaterialPage(player);
            // Navigation slots
            if (event.getRawSlot() == 45) { gameManager.onMaterialGuiNavigate(player, -1); return; }
            if (event.getRawSlot() == 53) { gameManager.onMaterialGuiNavigate(player, +1); return; }
            BridgeMaterial material = MaterialGui.getMaterialForSlot(event.getRawSlot(), page);
            if (material != null) gameManager.onMaterialSelected(player, material);
            return;
        }

        // ── Island Selector GUI ───────────────────────────────────
        if (title.equals(IslandSelectorGui.TITLE)) {
            event.setCancelled(true);
            if (isEmptyOrFiller(event)) return;
            int page = gameManager.getSelectorPage(player);
            String result = IslandSelectorGui.resolveClick(event.getRawSlot(), page, gameManager.getMapManager());
            if (result == null)       { gameManager.onMapSelected(player, null);   return; } // quick-practice
            if (result.equals(""))    { return; }
            if (result.equals("PREV")){ gameManager.onIslandSelectorNavigate(player, -1); return; }
            if (result.equals("NEXT")){ gameManager.onIslandSelectorNavigate(player, +1); return; }
            gameManager.onMapSelected(player, result);
            return;
        }

        // ── Lock player inventory for players in game ─────────────
        if (gameManager.getIslandManager().getIsland(player.getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    private boolean isEmptyOrFiller(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        return item == null
                || item.getType() == Material.AIR
                || item.getType() == Material.STAINED_GLASS_PANE;
    }
}
