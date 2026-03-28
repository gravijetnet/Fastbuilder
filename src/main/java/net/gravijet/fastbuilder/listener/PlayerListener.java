package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.map.MapManager;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.player.PlayerManager;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player join (route to map) and quit (save + free island).
 */
public class PlayerListener implements Listener {

    private final FastBuilder plugin;

    public PlayerListener(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerManager pm = plugin.getPlayerManager();
        MapManager mm = plugin.getMapManager();

        // Load player data
        PlayerData data = pm.getPlayerData(player.getUniqueId(), player.getName());

        // Route to last played map, or default
        String targetMap = data.getLastMap();
        if (targetMap == null || targetMap.isEmpty() || mm.getMap(targetMap) == null) {
            targetMap = plugin.getConfigManager().getDefaultMap();
        }

        if (targetMap != null && !targetMap.isEmpty()) {
            MapData map = mm.getMap(targetMap);
            if (map != null && map.isEnabled()) {
                int island = mm.assignFreeIsland(map.getName(), player.getUniqueId(), player.getName());
                if (island >= 0) {
                    player.teleport(map.getIslandSpawn(island));
                    data.setLastMap(map.getName());
                    data.setLastIsland(island);

                    String raw = plugin.getConfigManager().getMessage("joined-mode");
                    if (raw != null && !raw.isEmpty()) {
                        raw = raw.replace("%map%", map.getName());
                        raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
                        player.sendMessage(ColorUtil.translate(raw));
                    }

                    // Check autoscale
                    mm.checkAutoscale(map);
                    return;
                }
            }
        }

        // No valid map found, relocate to any available
        mm.relocatePlayer(player, "");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Free all islands held by this player
        plugin.getMapManager().freeAllIslands(player.getUniqueId());

        // Save and unload player data
        plugin.getPlayerManager().unload(player.getUniqueId());
    }
}
