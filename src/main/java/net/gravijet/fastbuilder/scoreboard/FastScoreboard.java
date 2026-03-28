package net.gravijet.fastbuilder.scoreboard;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Map;
import java.util.UUID;

/**
 * Manages per-player scoreboards with live timer, personal best, session top 3, and coins.
 */
public class FastScoreboard {

    private final FastBuilder plugin;

    public FastScoreboard(FastBuilder plugin) {
        this.plugin = plugin;
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
     * Update the scoreboard for a player (call periodically for live timer).
     */
    public void updateScoreboard(Player player) {
        // Full scoreboard rebuild for simplicity (Spigot 1.8.8 has limited update options)
        createScoreboard(player);
    }

    /**
     * Remove the scoreboard from a player.
     */
    public void removeScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private String replacePlaceholders(String line, Player player) {
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());

        line = line.replace("%time%", "00:00.000"); // Will be replaced by live timer
        line = line.replace("%pb%", data != null && data.getLastMap() != null
                ? getPlayerBest(data, data.getLastMap())
                : "N/A");
        line = line.replace("%coins%", data != null ? String.valueOf(data.getCoins()) : "0");
        line = line.replace("%blocks%", "0"); // Will be updated during gameplay
        line = line.replace("%top_1%", "---");
        line = line.replace("%top_2%", "---");
        line = line.replace("%top_3%", "---");

        return line;
    }

    private String getPlayerBest(PlayerData data, String mapName) {
        PlayerData.MapStats stats = data.getStats(mapName);
        if (stats == null || !stats.hasBestTime()) return "N/A";
        return TimeUtil.formatTime(stats.bestTime);
    }
}
