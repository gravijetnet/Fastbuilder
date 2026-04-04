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
import java.util.Map;

/**
 * Per-player scoreboards. GraviJet branding, &c/&f/&7 color scheme.
 * Lines are fully config-driven via scoreboard.lines in config.yml.
 */
public class FastScoreboard {

    private final FastBuilder plugin;
    private int updateTaskId = -1;

    public FastScoreboard(FastBuilder plugin) {
        this.plugin = plugin;
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
     */
    private List<String> buildLines(Player player) {
        Map<Integer, String> configLines = plugin.getConfigManager().getScoreboardLines();
        if (configLines.isEmpty()) {
            return Collections.emptyList();
        }

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        RunSession session = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;

        // Resolve placeholder values once
        String mapName = (session != null) ? session.getMapName() : "§8None";

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

        List<String[]> top3list = (plugin.getGameplayManager() != null)
                ? plugin.getGameplayManager().getGlobalSessionTop3()
                : Collections.emptyList();

        String top1 = top3list.size() >= 1
                ? "§f" + TimeUtil.formatTime(Long.parseLong(top3list.get(0)[1]))
                  + " §7(" + top3list.get(0)[0] + ")" : "§8-";
        String top2 = top3list.size() >= 2
                ? "§f" + TimeUtil.formatTime(Long.parseLong(top3list.get(1)[1]))
                  + " §7(" + top3list.get(1)[0] + ")" : "§8- ";
        String top3 = top3list.size() >= 3
                ? "§f" + TimeUtil.formatTime(Long.parseLong(top3list.get(2)[1]))
                  + " §7(" + top3list.get(2)[0] + ")" : "§8-  ";

        List<String> result = new ArrayList<>();
        // configLines is a TreeMap (sorted by key ascending) — display in ascending order
        for (String raw : configLines.values()) {
            String line = raw
                    .replace("%map%", mapName)
                    .replace("%pb%", pb)
                    .replace("%current_time%", currentTime)
                    .replace("%coins%", coins)
                    .replace("%top1%", top1)
                    .replace("%top2%", top2)
                    .replace("%top3%", top3)
                    .replace("%players%", "");  // removed per spec
            result.add(ColorUtil.translate(line));
        }
        return result;
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
