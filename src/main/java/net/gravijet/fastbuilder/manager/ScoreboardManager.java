package net.gravijet.fastbuilder.manager;

import net.gravijet.fastbuilder.model.BridgeDistance;
import net.gravijet.fastbuilder.model.PlayerData;
import net.gravijet.fastbuilder.model.PlayerStats;
import net.gravijet.fastbuilder.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages per-player sidebar scoreboards.
 *
 * Uses the team-prefix/suffix trick to display long colored lines
 * while staying within Bukkit 1.8 character limits.
 *
 * Score layout (higher = displayed higher):
 *   11 = separator
 *   10 = "Distance:" label
 *    9 = distance value
 *    8 = blank
 *    7 = "Attempts:" label
 *    6 = attempts + successes value
 *    5 = blank
 *    4 = "Best Time:" label
 *    3 = best time value
 *    2 = blank
 *    1 = footer
 */
public class ScoreboardManager {

    private static final ChatColor[] UNIQUE = ChatColor.values();

    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public void createBoard(Player player, PlayerData data, PlayerStats stats) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("fb", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(CC.c("&c&lFastBuilder"));

        // Register 11 team lines for dynamic content
        for (int i = 1; i <= 11; i++) {
            String entry = UNIQUE[i].toString();
            Team team = board.registerNewTeam("fb_line_" + i);
            team.addEntry(entry);
        }

        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        updateBoard(player, data, stats, null);
    }

    public void updateBoard(Player player, PlayerData data, PlayerStats stats, String timerStr) {
        UUID uuid = player.getUniqueId();
        Scoreboard board = boards.get(uuid);
        if (board == null) return;

        Objective obj = board.getObjective("fb");
        if (obj == null) return;

        BridgeDistance dist  = data.getSelectedDistance();
        PlayerStats.DistanceStats ds = stats.getStats(dist);

        String currentTime = timerStr != null
                ? CC.c("&e" + timerStr)
                : CC.c("&7Not running");

        // Line 11: separator
        setLine(board, obj, 11, CC.c("&8&m") + "            ", "");
        // Line 10: Distance label
        setLine(board, obj, 10, CC.c("&7Distance"), "");
        // Line 9: distance value
        setLine(board, obj, 9, " " + dist.getColorCode() + dist.getDisplayName(), "");
        // Line 8: blank
        setLine(board, obj, 8, CC.c("&f"), "");
        // Line 7: Timer label
        setLine(board, obj, 7, CC.c("&7Timer"), "");
        // Line 6: timer value
        setLine(board, obj, 6, " ", currentTime);
        // Line 5: blank
        setLine(board, obj, 5, CC.c("&e"), "");
        // Line 4: Attempts label
        setLine(board, obj, 4, CC.c("&7Attempts"), "");
        // Line 3: attempts value
        setLine(board, obj, 3, CC.c(" &f" + ds.attempts + " &8| &a" + ds.successes), "");
        // Line 2: Best Time label + value
        setLine(board, obj, 2, CC.c("&7Best &f" + ds.getFormattedBestTime()), "");
        // Line 1: footer
        setLine(board, obj, 1, CC.c("&8gravijet.net"), "");
    }

    public void removeBoard(Player player) {
        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }

    public void removeAll() {
        boards.clear();
    }

    private void setLine(Scoreboard board, Objective obj, int score, String prefix, String suffix) {
        Team team = board.getTeam("fb_line_" + score);
        if (team == null) return;

        // Truncate to 16 chars each (Bukkit 1.8 limit)
        team.setPrefix(truncate(prefix, 16));
        team.setSuffix(truncate(suffix, 16));

        String entry = UNIQUE[score].toString();
        Score s = obj.getScore(entry);
        s.setScore(score);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
