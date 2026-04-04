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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-player scoreboards. GraviJet branding, &c/&f/&7 color scheme.
 * Lines are fully config-driven via scoreboard.lines in config.yml.
 * Supports up to 15 lines, top-10 granular placeholders, %blocks%, and PlaceholderAPI.
 */
public class FastScoreboard {

    private final FastBuilder plugin;
    private int updateTaskId = -1;

    // Whether PlaceholderAPI is available at runtime
    private final boolean papiAvailable;

    public FastScoreboard(FastBuilder plugin) {
        this.plugin = plugin;
        this.papiAvailable = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        startUpdateTask();
    }

    public void createScoreboard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("fb", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        String title = plugin.getConfigManager().getScoreboardTitle();
        obj.setDisplayName(ColorUtil.translate(title));

        List<String> lines = buildLines(player);
        int score = lines.size();
        for (String line : lines) {
            obj.getScore(line).setScore(score--);
        }

        player.setScoreboard(board);
    }

    /**
     * Build display lines from config, resolving all placeholders for this player.
     * Lines are returned in top-to-bottom order (index 0 = top of sidebar).
     */
    private List<String> buildLines(Player player) {
        List<String> configLines = plugin.getConfigManager().getScoreboardLines();
        if (configLines.isEmpty()) {
            return Collections.emptyList();
        }

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        RunSession session = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;

        // Resolve placeholder values once
        String pb = "§8-";
        if (data != null && session != null) {
            PlayerData.MapStats stats = data.getStats(session.getMapName());
            if (stats != null && stats.hasBestTime()) {
                pb = "§f" + TimeUtil.formatTime(stats.bestTime);
            }
        }

        String currentTime = "§700:00.000";
        if (session != null && session.isRunning()) {
            currentTime = "§f" + TimeUtil.formatTime(session.getElapsed());
        }

        String coins = data != null ? String.valueOf(data.getCoins()) : "0";

        // Blocks placed: use running session count, or last finished count if session finished
        String blocks = "§80";
        if (session != null) {
            blocks = "§f" + session.getPlacedBlocks().size();
        }

        // Top-10 session bests
        List<String[]> topList = (plugin.getGameplayManager() != null)
                ? plugin.getGameplayManager().getGlobalSessionTop(10)
                : Collections.<String[]>emptyList();

        // Build all result lines (in top-to-bottom order)
        List<String> result = new ArrayList<>();
        for (String raw : configLines) {
            // Internal placeholder replacement
            String line = raw
                    .replace("%pb%", pb)
                    .replace("%current_time%", currentTime)
                    .replace("%coins%", coins)
                    .replace("%blocks%", blocks);

            // Replace top_name_N and top_time_N for N = 1..10
            for (int i = 1; i <= 10; i++) {
                if (topList.size() >= i) {
                    String[] entry = topList.get(i - 1);
                    // Use unique padding to avoid duplicate scoreboard entries for empty slots
                    line = line.replace("%top_name_" + i + "%", "§f" + entry[0]);
                    line = line.replace("%top_time_" + i + "%", "§f" + TimeUtil.formatTime(Long.parseLong(entry[1])));
                } else {
                    line = line.replace("%top_name_" + i + "%", "§8-" + spaces(i - 1));
                    line = line.replace("%top_time_" + i + "%", "§8-" + spaces(i - 1));
                }
            }

            // Apply color codes before PAPI so PAPI can also use color codes
            line = ColorUtil.translate(line);

            // Apply PlaceholderAPI expansions (e.g. %phoenix_player_rank_color%, %online%, etc.)
            line = applyPapi(player, line);

            result.add(line);
        }
        return result;
    }

    /**
     * Returns a string of N invisible spaces to make scoreboard entries unique.
     * Scoreboard lines must all be unique strings or the last one wins.
     */
    private String spaces(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(' ');
        return sb.toString();
    }

    /**
     * Applies PlaceholderAPI placeholders if the plugin is available.
     */
    private String applyPapi(Player player, String text) {
        if (!papiAvailable) return text;
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Exception ignored) {}
        return text;
    }

    public void updateScoreboard(Player player) {
        createScoreboard(player);
    }

    public void removeScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void startUpdateTask() {
        long interval = plugin.getConfigManager().getScoreboardUpdateInterval();
        updateTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (plugin.getGameplayManager() != null) {
                        updateScoreboard(player);
                    }
                }
            }
        }.runTaskTimer(plugin, interval, interval).getTaskId();
    }

    public void shutdown() {
        if (updateTaskId != -1) Bukkit.getScheduler().cancelTask(updateTaskId);
    }
}
