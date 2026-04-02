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

        // Remove existing hologram
        try {
            eu.decentsoftware.holograms.api.DHAPI.removeHologram(holoId);
        } catch (Exception ignored) {}

        // Create hologram 1 block above the sapling
        Location holoLoc = saplingLoc.clone().add(0.5, 1.8, 0.5);
        List<String> lines = new ArrayList<>();
        lines.add(net.gravijet.fastbuilder.util.ColorUtil.translate("&cClickspeed: &f" + cps));

        try {
            eu.decentsoftware.holograms.api.DHAPI.createHologram(holoId, holoLoc, false, lines);
        } catch (Exception e) {
            plugin.getLogger().warning("CPS hologram error: " + e.getMessage());
            return;
        }

        // Cancel any existing removal task for this player
        Integer existingTask = removalTasks.remove(uuid);
        if (existingTask != null) {
            plugin.getServer().getScheduler().cancelTask(existingTask);
        }

        // Schedule removal after 2 seconds of no clicks
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    eu.decentsoftware.holograms.api.DHAPI.removeHologram(holoId);
                } catch (Exception ignored) {}
                removalTasks.remove(uuid);
                saplingLocation.remove(uuid);
                Deque<Long> d = clickTimes.get(uuid);
                if (d != null) d.clear();
            }
        }.runTaskLater(plugin, 40L).getTaskId(); // 40 ticks = 2 seconds

        removalTasks.put(uuid, taskId);
    }

    public void cleanup() {
        for (Map.Entry<UUID, Integer> entry : removalTasks.entrySet()) {
            plugin.getServer().getScheduler().cancelTask(entry.getValue());
            String holoId = HOLO_PREFIX + entry.getKey().toString().substring(0, 8);
            try {
                eu.decentsoftware.holograms.api.DHAPI.removeHologram(holoId);
            } catch (Exception ignored) {}
        }
        removalTasks.clear();
        clickTimes.clear();
        saplingLocation.clear();
    }
}
