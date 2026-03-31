package net.gravijet.fastbuilder.gameplay;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.GridCalculator;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages active bridging runs: timer, finish detection, fall detection, session bests.
 */
public class GameplayManager {

    private final FastBuilder plugin;
    private final Map<UUID, RunSession> activeSessions = new HashMap<>();

    // Actionbar update task id
    private int actionbarTaskId = -1;
    // Boundary check task id
    private int boundaryTaskId = -1;

    public GameplayManager(FastBuilder plugin) {
        this.plugin = plugin;
        startActionbarTask();
        startBoundaryCheckTask();
    }

    /**
     * Create a new run session for a player on an island.
     */
    public RunSession createSession(UUID uuid, String mapName, int islandIndex) {
        RunSession session = new RunSession(uuid, mapName, islandIndex);
        activeSessions.put(uuid, session);
        return session;
    }

    public RunSession getSession(UUID uuid) {
        return activeSessions.get(uuid);
    }

    public void removeSession(UUID uuid) {
        activeSessions.remove(uuid);
    }

    /**
     * Called when a player places a block. Starts the timer if not already running.
     */
    public void onBlockPlace(Player player, Block block) {
        RunSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        if (session.isFinished()) {
            // Auto-reset for new attempt
            resetRun(player);
            session = activeSessions.get(player.getUniqueId());
            if (session == null) return;
        }

        if (!session.isRunning()) {
            session.start();

            // Start replay recording if replay manager exists
            if (plugin.getReplayManager() != null) {
                plugin.getReplayManager().startRecording(player, session.getMapName(), session.getIslandIndex());
            }
        }

        session.addPlacedBlock(block.getLocation());

        // Auto-refill blocks if perk is active
        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().checkAutoRefill(player);
        }

        // Record block placement for replay
        if (plugin.getReplayManager() != null) {
            plugin.getReplayManager().recordBlockPlace(player.getUniqueId(),
                    block.getLocation(), block.getTypeId(), block.getData());
        }
    }

    /**
     * Called when a player steps on a pressure plate in the finish zone.
     */
    public void onFinish(Player player) {
        RunSession session = activeSessions.get(player.getUniqueId());
        if (session == null || !session.isRunning() || session.isFinished()) return;

        long time = session.finish();

        // Stop replay recording
        if (plugin.getReplayManager() != null) {
            plugin.getReplayManager().stopRecording(player.getUniqueId(), true);
        }

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        // Add session best
        session.addSessionBest(time);

        if (!session.isPracticeMode()) {
            // Update stats
            PlayerData.MapStats stats = data.getOrCreateStats(session.getMapName());
            stats.totalAttempts++;
            stats.successfulAttempts++;

            boolean isNewPB = !stats.hasBestTime() || time < stats.bestTime;
            long oldBest = stats.bestTime;
            if (isNewPB) {
                stats.bestTime = time;
            }

            // Award coins
            int coins = plugin.getCoinManager().awardCompletionCoins(player, time);

            // Send messages
            String prefix = plugin.getConfigManager().getPrefix();
            if (isNewPB) {
                List<String> messages = plugin.getConfigManager().getEndPBMessages();
                for (String line : messages) {
                    line = line.replace("%time%", TimeUtil.formatTime(time))
                            .replace("%pb%", TimeUtil.formatTime(time))
                            .replace("%difference%", oldBest > 0
                                    ? TimeUtil.formatDifference(time, oldBest) : "N/A")
                            .replace("%prefix%", prefix);
                    player.sendMessage(ColorUtil.translate(line));
                }
            } else {
                List<String> messages = plugin.getConfigManager().getEndTimeMessages();
                for (String line : messages) {
                    line = line.replace("%time%", TimeUtil.formatTime(time))
                            .replace("%pb%", TimeUtil.formatTime(stats.bestTime))
                            .replace("%difference%", TimeUtil.formatDifference(time, stats.bestTime))
                            .replace("%prefix%", prefix);
                    player.sendMessage(ColorUtil.translate(line));
                }
            }

            // Send title
            String title = plugin.getConfigManager().getTitle();
            String subtitle = plugin.getConfigManager().getSubtitle();
            if (title != null && !title.isEmpty()) {
                title = title.replace("%time%", TimeUtil.formatTime(time))
                        .replace("%coins%", String.valueOf(coins));
                subtitle = subtitle != null ? subtitle.replace("%time%", TimeUtil.formatTime(time))
                        .replace("%coins%", String.valueOf(coins)) : "";
                player.sendTitle(ColorUtil.translate(title), ColorUtil.translate(subtitle));
            }

            // Update hologram
            if (plugin.getHologramManager() != null) {
                plugin.getHologramManager().updateHologram(
                        session.getMapName(), session.getIslandIndex(), player);
            }
        }

        if (session.isPracticeMode()) {
            // Show time as title but don't save stats
            String practiceTitle = "&6&lPractice: &f" + TimeUtil.formatTime(time);
            player.sendTitle(ColorUtil.translate(practiceTitle), ColorUtil.translate("&7Time not saved"));
        }

        // Play success sound + launch firework
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 1.0f, 1.0f);
        launchFirework(player.getLocation());

        // Update scoreboard
        plugin.getScoreboardManager().updateScoreboard(player);

        // Wait 2 seconds, then reset and teleport to start
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    resetRun(player);
                }
            }
        }.runTaskLater(plugin, 40L); // 40 ticks = 2 seconds
    }

    /**
     * Launch a celebration firework at the given location.
     */
    private void launchFirework(Location location) {
        try {
            Firework fw = location.getWorld().spawn(location, Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .withColor(Color.RED, Color.ORANGE, Color.YELLOW)
                    .withFade(Color.WHITE)
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .flicker(true)
                    .trail(true)
                    .build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
        } catch (Exception ignored) {}
    }

    /**
     * Called when a player falls off (Y < island Y - maxDistance).
     */
    public void onFall(Player player) {
        RunSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        // Count as failed attempt if run was active
        if (session.isRunning()) {
            // Stop replay recording as failed
            if (plugin.getReplayManager() != null) {
                plugin.getReplayManager().stopRecording(player.getUniqueId(), false);
            }

            if (!session.isPracticeMode()) {
                PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
                if (data != null) {
                    PlayerData.MapStats stats = data.getOrCreateStats(session.getMapName());
                    stats.totalAttempts++;
                }
            }
        }

        // Reset the run
        resetRun(player);
    }

    /**
     * Reset a player's current run: clear placed blocks, re-paste island, teleport to spawn.
     */
    public void resetRun(Player player) {
        RunSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        MapData map = plugin.getMapManager().getMap(session.getMapName());
        if (map == null) return;

        // Remove placed blocks (set to air)
        // In practice mode, keep practice blocks on the map
        for (Location loc : session.getPlacedBlocks()) {
            if (session.isPracticeMode() && session.getPracticeBlocks().contains(loc)) {
                continue; // Keep practice blocks
            }
            Block block = loc.getBlock();
            if (block != null) {
                block.setType(Material.AIR);
            }
        }

        // Keep session bests across resets
        java.util.List<Long> bests = new java.util.ArrayList<>(session.getSessionBests());
        boolean practice = session.isPracticeMode();

        // Reset session
        session.reset();
        // Restore session bests
        for (Long best : bests) {
            session.addSessionBest(best);
        }
        session.setPracticeMode(practice);

        // Teleport to spawn
        player.teleport(map.getIslandSpawn(session.getIslandIndex()));

        // Restore hotbar items after reset
        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().giveItems(player);
        }

        // Update scoreboard
        plugin.getScoreboardManager().updateScoreboard(player);
    }

    /**
     * Periodic actionbar update showing live timer.
     */
    private void startActionbarTask() {
        String actionBarFormat = plugin.getConfigManager().getActionBar();
        if (actionBarFormat == null || actionBarFormat.isEmpty()) return;

        actionbarTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, RunSession> entry : activeSessions.entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) continue;

                    RunSession session = entry.getValue();
                    String timer = session.isRunning()
                            ? TimeUtil.formatTime(session.getElapsed())
                            : "00:00.000";

                    String msg = actionBarFormat.replace("%timer%", timer);
                    sendActionBar(player, ColorUtil.translate(msg));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L).getTaskId();
    }

    /**
     * Boundary check is now handled by ProtectionListener.onMove().
     * This task is kept as a safety net for edge cases (e.g. plugin reload).
     */
    private void startBoundaryCheckTask() {
        // No-op: boundary enforcement moved to ProtectionListener for per-tick accuracy
        boundaryTaskId = -1;
    }

    @SuppressWarnings("deprecation")
    private void sendActionBar(Player player, String message) {
        try {
            // Use NMS for 1.8.8 actionbar
            Object packet = getNMSClass("PacketPlayOutChat")
                    .getConstructor(getNMSClass("IChatBaseComponent"), byte.class)
                    .newInstance(
                            getNMSClass("IChatBaseComponent$ChatSerializer")
                                    .getMethod("a", String.class)
                                    .invoke(null, "{\"text\":\"" + message + "\"}"),
                            (byte) 2
                    );
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
            playerConnection.getClass().getMethod("sendPacket", getNMSClass("Packet"))
                    .invoke(playerConnection, packet);
        } catch (Exception ignored) {
            // Fallback: no actionbar on failure
        }
    }

    private Class<?> getNMSClass(String name) throws ClassNotFoundException {
        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        return Class.forName("net.minecraft.server." + version + "." + name);
    }

    public void shutdown() {
        if (actionbarTaskId != -1) {
            Bukkit.getScheduler().cancelTask(actionbarTaskId);
        }
        if (boundaryTaskId != -1) {
            Bukkit.getScheduler().cancelTask(boundaryTaskId);
        }
        activeSessions.clear();
    }

    public Map<UUID, RunSession> getActiveSessions() {
        return activeSessions;
    }
}
