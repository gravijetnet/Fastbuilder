package net.gravijet.fastbuilder.listener;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.StructureGrowEvent;

/**
 * Globally disables tree/sapling growth.
 */
public class TreeGrowthListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTreeGrow(StructureGrowEvent event) {
        event.setCancelled(true);
    }
}
