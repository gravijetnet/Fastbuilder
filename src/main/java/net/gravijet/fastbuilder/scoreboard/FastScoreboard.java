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

        // Rank (PlaceholderAPI: %phoenix_player_real_rank%)
        line = line.replace("%rank%", resolvePlaceholder(player, "%phoenix_player_real_rank%", "N/A"));

        // Players (PlaceholderAPI: %phoenix_server_global_online%)
        line = line.replace("%players%", resolvePlaceholder(player, "%phoenix_server_global_online%",
                String.valueOf(Bukkit.getOnlinePlayers().size())));

        // Coins (PlaceholderAPI: %pxcosmetics_player_coins%)
        String fallbackCoins = data != null ? String.valueOf(data.getCoins()) : "0";
        line = line.replace("%coins%", resolvePlaceholder(player, "%pxcosmetics_player_coins%", fallbackCoins));

        // Level (PlaceholderAPI: %phoenix_player_level_displayname%)
        line = line.replace("%level%", resolvePlaceholder(player, "%phoenix_player_level_displayname%", "1"));

        // Playtime (calculate from Bukkit statistic)
        @SuppressWarnings("deprecation")
        long ticksPlayed = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_TICK);
        long hours = ticksPlayed / 20 / 3600;
        line = line.replace("%playtime%", String.valueOf(hours));

        return line;
    }

    /**
     * Try PlaceholderAPI first, fall back to provided default.
     */
    private String resolvePlaceholder(Player player, String placeholder, String fallback) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                java.lang.reflect.Method method = papi.getMethod("setPlaceholders",
                        org.bukkit.OfflinePlayer.class, String.class);
                String result = (String) method.invoke(null, player, placeholder);
                // If PAPI didn't resolve it, it returns the placeholder unchanged
                if (result != null && !result.equals(placeholder)) {
                    return result;
                }
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    public void shutdown() {
        if (updateTaskId != -1) {
            Bukkit.getScheduler().cancelTask(updateTaskId);
        }
    }
}
