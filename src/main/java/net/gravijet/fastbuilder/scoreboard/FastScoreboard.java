package net.gravijet.fastbuilder.scoreboard;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.RunSession;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;
import java.util.Map;

/**
 * Manages per-player scoreboards with live timer, personal best, session top 3, and coins.
 */
public class FastScoreboard {

    private final FastBuilder plugin;
    private int updateTaskId = -1;

    public FastScoreboard(FastBuilder plugin) {
        this.plugin = plugin;
        startUpdateTask();
    }

    /**
     * Create and set the scoreboard for a player.
     */
    public void createScoreboard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("fb", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(ColorUtil.translate(plugin.getConfigManager().getScoreboardTitle()));

        Map<Integer, String> lines = plugin.getConfigManager().getScoreboardLines();
        for (Map.Entry<Integer, String> entry : lines.entrySet()) {
            String line = replacePlaceholders(entry.getValue(), player);
            obj.getScore(ColorUtil.translate(line)).setScore(entry.getKey());
        }

        player.setScoreboard(board);
    }

    /**
     * Update the scoreboard for a player (full rebuild for simplicity in 1.8.8).
     */
    public void updateScoreboard(Player player) {
        createScoreboard(player);
    }

    /**
     * Remove the scoreboard from a player.
     */
    public void removeScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /**
     * Start periodic scoreboard update task (every 10 ticks / 0.5s).
     */
    private void startUpdateTask() {
        updateTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Only update for players with active sessions
                    if (plugin.getGameplayManager() != null
                            && plugin.getGameplayManager().getSession(player.getUniqueId()) != null) {
                        updateScoreboard(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 10L).getTaskId();
    }

    private String replacePlaceholders(String line, Player player) {
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        RunSession run = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;

        // Live timer
        String timer = "00:00.000";
        if (run != null && run.isRunning()) {
            timer = TimeUtil.formatTime(run.getElapsed());
        }
        line = line.replace("%time%", timer);

        // Personal best
        line = line.replace("%pb%", data != null && data.getLastMap() != null
                ? getPlayerBest(data, data.getLastMap())
                : "N/A");

        // Coins
        line = line.replace("%coins%", data != null ? String.valueOf(data.getCoins()) : "0");

        // Blocks placed this session
        line = line.replace("%blocks%", run != null ? String.valueOf(run.getPlacedBlocks().size()) : "0");

        // Session top 3
        if (run != null) {
            List<Long> bests = run.getSessionBests();
            line = line.replace("%top_1%", bests.size() >= 1 ? TimeUtil.formatTime(bests.get(0)) : "---");
            line = line.replace("%top_2%", bests.size() >= 2 ? TimeUtil.formatTime(bests.get(1)) : "---");
            line = line.replace("%top_3%", bests.size() >= 3 ? TimeUtil.formatTime(bests.get(2)) : "---");
        } else {
            line = line.replace("%top_1%", "---");
            line = line.replace("%top_2%", "---");
            line = line.replace("%top_3%", "---");
        }

        return line;
    }

    private String getPlayerBest(PlayerData data, String mapName) {
        PlayerData.MapStats stats = data.getStats(mapName);
        if (stats == null || !stats.hasBestTime()) return "N/A";
        return TimeUtil.formatTime(stats.bestTime);
    }

    public void shutdown() {
        if (updateTaskId != -1) {
            Bukkit.getScheduler().cancelTask(updateTaskId);
        }
    }
}
