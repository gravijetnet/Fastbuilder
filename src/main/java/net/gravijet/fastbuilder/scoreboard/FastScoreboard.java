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

/**
 * Per-player scoreboards. GraviJet branding, &c/&f/&7 color scheme.
 * Uses direct sidebar entries (≤40 chars each) — no team hacks needed for 1.8.8.
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
        obj.setDisplayName(ColorUtil.translate("§c§lFASTBUILDER"));

        String[] lines = buildLines(player);
        // lines[0] = top, lines[n-1] = bottom
        int score = lines.length;
        for (String line : lines) {
            obj.getScore(line).setScore(score--);
        }

        player.setScoreboard(board);
    }

    /**
     * Build the lines to display top → bottom.
     * Each must be unique and ≤ 40 characters (1.8.8 sidebar limit).
     */
    private String[] buildLines(Player player) {
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        RunSession session = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;

        // ── Map ───────────────────────────────────────────────────────────────
        String mapName = (session != null) ? session.getMapName() : "§8None";

        // ── Current Timer ─────────────────────────────────────────────────────
        String timer;
        if (session != null && session.isRunning()) {
            timer = "§f" + TimeUtil.formatTime(session.getElapsed());
        } else {
            timer = "§80.000s";
        }

        // ── Personal Best ─────────────────────────────────────────────────────
        String pb = "§8N/A";
        if (data != null && session != null) {
            PlayerData.MapStats stats = data.getAllStats().get(session.getMapName().toLowerCase());
            if (stats == null) stats = data.getAllStats().get(session.getMapName());
            if (stats != null && stats.hasBestTime()) {
                pb = "§f" + TimeUtil.formatTime(stats.bestTime);
            }
        }

        // ── Session / Global Best ─────────────────────────────────────────────
        List<Long> bests = session != null ? session.getSessionBests() : java.util.Collections.emptyList();
        String sessionBest;
        if (!bests.isEmpty()) {
            sessionBest = "§f" + TimeUtil.formatTime(bests.get(0)) + " §7(" + player.getName() + ")";
        } else {
            long gt = plugin.getGameplayManager() != null
                    ? plugin.getGameplayManager().getGlobalSessionBestTime() : -1;
            String gn = plugin.getGameplayManager() != null
                    ? plugin.getGameplayManager().getGlobalSessionBestPlayer() : null;
            sessionBest = (gt > 0 && gn != null)
                    ? "§f" + TimeUtil.formatTime(gt) + " §7(" + gn + ")"
                    : "§8-";
        }

        // ── Fastbuilder Coins ─────────────────────────────────────────────────
        String fbCoins = data != null ? String.valueOf(data.getCoins()) : "0";

        // ── Top 3 Session Bests ───────────────────────────────────────────────
        List<Long> sb = session != null ? session.getSessionBests() : java.util.Collections.emptyList();
        String top1 = sb.size() >= 1 ? "§f" + TimeUtil.formatTime(sb.get(0)) : "§8-";
        String top2 = sb.size() >= 2 ? "§f" + TimeUtil.formatTime(sb.get(1)) : "§8-";
        String top3 = sb.size() >= 3 ? "§f" + TimeUtil.formatTime(sb.get(2)) : "§8-";

        return new String[] {
            ColorUtil.translate("§7§m─────────────────"),
            ColorUtil.translate(" §7Map: §c" + mapName),
            ColorUtil.translate(" §7Time: " + timer),
            ColorUtil.translate(" §7PB: §c" + pb),
            ColorUtil.translate("§r"),
            ColorUtil.translate(" §7Best: §c" + sessionBest),
            ColorUtil.translate("§r "),
            ColorUtil.translate(" §7Session Top:"),
            ColorUtil.translate("  §8#1 " + top1),
            ColorUtil.translate("  §8#2 " + top2),
            ColorUtil.translate("  §8#3 " + top3),
            ColorUtil.translate("§r  "),
            ColorUtil.translate(" §8» §cCoins: §6" + fbCoins),
            ColorUtil.translate("§r   "),
            ColorUtil.translate("§7§oexample.invalid"),
            ColorUtil.translate("§c§m─────────────────"),
        };
    }

    public void updateScoreboard(Player player) {
        createScoreboard(player);
    }

    public void removeScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void startUpdateTask() {
        updateTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (plugin.getGameplayManager() != null) {
                        updateScoreboard(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L).getTaskId();
    }

    private String resolvePlaceholder(Player player, String placeholder, String fallback) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                java.lang.reflect.Method method = papi.getMethod("setPlaceholders",
                        org.bukkit.OfflinePlayer.class, String.class);
                String result = (String) method.invoke(null, player, placeholder);
                if (result != null && !result.equals(placeholder)) return result;
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    public void shutdown() {
        if (updateTaskId != -1) Bukkit.getScheduler().cancelTask(updateTaskId);
    }
}
