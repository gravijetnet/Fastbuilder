package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.FastBuilder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Listens for left/right clicks on saplings and shows a temporary DecentHolograms
 * hologram above the sapling with the player's current CPS (clicks per second).
 */
public class CpsListener implements Listener {

    private final FastBuilder plugin;

    // Per-player: list of click timestamps (ms) in the last 1 second
    private final Map<UUID, Deque<Long>> clickTimes = new HashMap<>();

    // Per-player: the location of the sapling they last clicked
    private final Map<UUID, Location> saplingLocation = new HashMap<>();

    // Per-player: task ID of the scheduled hologram removal
    private final Map<UUID, Integer> removalTasks = new HashMap<>();

    // Stores the actual Hologram object so we can update in-place without relying on DHAPI.getHologram()
    // (DHAPI.getHologram() is unreliable on some server versions and causes duplicate creation bugs)
    private final Map<UUID, eu.decentsoftware.holograms.api.holograms.Hologram> activeHolograms = new HashMap<>();

    // Hologram ID prefix
    private static final String HOLO_PREFIX = "fb_cps_";

    public CpsListener(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Material type = clicked.getType();
        if (type != Material.SAPLING) return;

        // Only count clicks on saplings within the player's own assigned island
        net.gravijet.fastbuilder.gameplay.RunSession session =
                plugin.getGameplayManager().getSession(player.getUniqueId());
        if (session == null) return;
        net.gravijet.fastbuilder.map.MapData map =
                plugin.getMapManager().getMap(session.getMapName());
        if (map != null) {
            Location clickLoc = clicked.getLocation();
            Location islandMin = map.getIslandMin(session.getIslandIndex());
            Location islandMax = map.getIslandMax(session.getIslandIndex());
            if (clickLoc.getBlockX() < islandMin.getBlockX() || clickLoc.getBlockX() > islandMax.getBlockX()
                    || clickLoc.getBlockZ() < islandMin.getBlockZ() || clickLoc.getBlockZ() > islandMax.getBlockZ()) {
                return;
            }
        }

        Location loc = clicked.getLocation();
        UUID uuid = player.getUniqueId();

        // Record click time
        long now = System.currentTimeMillis();
        clickTimes.computeIfAbsent(uuid, k -> new ArrayDeque<>()).addLast(now);

        // Prune clicks older than 1 second
        Deque<Long> times = clickTimes.get(uuid);
        while (!times.isEmpty() && now - times.peekFirst() > 1000) {
            times.pollFirst();
        }

        int cps = times.size();

        saplingLocation.put(uuid, loc.clone());

        updateCpsHologram(player, loc, cps);
    }

    private void updateCpsHologram(Player player, Location saplingLoc, int cps) {
        if (!plugin.getConfigManager().isHologramsEnabled()) return;

        UUID uuid = player.getUniqueId();
        String holoId = HOLO_PREFIX + uuid.toString().substring(0, 8);
        String text = net.gravijet.fastbuilder.util.ColorUtil.translate("&cClickspeed: &f" + cps);

        // Cancel any existing removal task first
        Integer existingTask = removalTasks.remove(uuid);
        if (existingTask != null) {
            plugin.getServer().getScheduler().cancelTask(existingTask);
        }

        boolean privateMode = "private".equalsIgnoreCase(
                plugin.getConfigManager().getCpsHologramVisibility());

        try {
            eu.decentsoftware.holograms.api.holograms.Hologram hologram = activeHolograms.get(uuid);

            if (hologram != null) {
                // Update text in-place using the stored object reference.
                // This avoids DHAPI.getHologram() which can return null even for live holograms
                // on certain server builds, causing the remove+create path to create duplicates.
                eu.decentsoftware.holograms.api.DHAPI.setHologramLine(hologram, 0, text);
                // Re-apply visibility in case the config changed since creation
                if (privateMode) {
                    hologram.setDefaultVisibleState(false);
                    hologram.setShowPlayer(player);
                } else {
                    hologram.setDefaultVisibleState(true);
                }
            } else {
                // No tracked hologram — clean any stale DHAPI entry then create fresh
                try { eu.decentsoftware.holograms.api.DHAPI.removeHologram(holoId); } catch (Exception ignored) {}
                Location holoLoc = saplingLoc.clone().add(0.5, 1.55, 0.5);
                List<String> lines = new ArrayList<>();
                lines.add(text);
                hologram = eu.decentsoftware.holograms.api.DHAPI.createHologram(holoId, holoLoc, false, lines);
                if (hologram != null) {
                    if (privateMode) {
                        hologram.setDefaultVisibleState(false);
                        hologram.setShowPlayer(player);
                    }
                    activeHolograms.put(uuid, hologram);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("CPS hologram error: " + e.getMessage());
            return;
        }

        // Schedule removal after 0.5 seconds (10 ticks) of no clicks — twice as fast as before
        final eu.decentsoftware.holograms.api.holograms.Hologram finalHologram = activeHolograms.get(uuid);
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                // Delete via stored reference first (bypasses registry lookup)
                try { if (finalHologram != null) finalHologram.delete(); } catch (Exception ignored) {}
                // Also remove by ID as a safety net for the DHAPI registry
                try { eu.decentsoftware.holograms.api.DHAPI.removeHologram(holoId); } catch (Exception ignored) {}
                removalTasks.remove(uuid);
                activeHolograms.remove(uuid);
                saplingLocation.remove(uuid);
                Deque<Long> d = clickTimes.get(uuid);
                if (d != null) d.clear();
            }
        }.runTaskLater(plugin, 10L).getTaskId(); // 10 ticks = 0.5 seconds

        removalTasks.put(uuid, taskId);
    }

    /** Remove CPS hologram and state for a specific player (call on leave/island switch). */
    public void cleanupPlayer(UUID uuid) {
        Integer taskId = removalTasks.remove(uuid);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
        String holoId = HOLO_PREFIX + uuid.toString().substring(0, 8);
        eu.decentsoftware.holograms.api.holograms.Hologram hologram = activeHolograms.remove(uuid);
        try { if (hologram != null) hologram.delete(); } catch (Exception ignored) {}
        try { eu.decentsoftware.holograms.api.DHAPI.removeHologram(holoId); } catch (Exception ignored) {}
        clickTimes.remove(uuid);
        saplingLocation.remove(uuid);
    }

    public void cleanup() {
        for (Map.Entry<UUID, Integer> entry : removalTasks.entrySet()) {
            plugin.getServer().getScheduler().cancelTask(entry.getValue());
        }
        removalTasks.clear();
        for (Map.Entry<UUID, eu.decentsoftware.holograms.api.holograms.Hologram> entry : activeHolograms.entrySet()) {
            String holoId = HOLO_PREFIX + entry.getKey().toString().substring(0, 8);
            try { if (entry.getValue() != null) entry.getValue().delete(); } catch (Exception ignored) {}
            try { eu.decentsoftware.holograms.api.DHAPI.removeHologram(holoId); } catch (Exception ignored) {}
        }
        activeHolograms.clear();
        clickTimes.clear();
        saplingLocation.clear();
    }
}
