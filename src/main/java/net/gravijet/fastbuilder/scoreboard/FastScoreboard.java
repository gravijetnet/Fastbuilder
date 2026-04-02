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

        // ── Blocks Used ───────────────────────────────────────────────────────
        int blocksPlaced = (session != null) ? session.getPlacedBlocks().size() : 0;
        String blocksStr = "§f" + blocksPlaced;

        // ── Fastbuilder Coins ─────────────────────────────────────────────────
        String fbCoins = data != null ? String.valueOf(data.getCoins()) : "0";

        // ── Global Top 3 Session Bests (unique per player) ────────────────────
        List<String[]> top3 = (plugin.getGameplayManager() != null)
                ? plugin.getGameplayManager().getGlobalSessionTop3()
                : java.util.Collections.emptyList();

        String top1 = top3.size() >= 1
                ? "§f" + TimeUtil.formatTime(Long.parseLong(top3.get(0)[1])) + " §7(" + top3.get(0)[0] + ")"
                : "§8-";
        String top2 = top3.size() >= 2
                ? "§f" + TimeUtil.formatTime(Long.parseLong(top3.get(1)[1])) + " §7(" + top3.get(1)[0] + ")"
                : "§8- ";
        String top3str = top3.size() >= 3
                ? "§f" + TimeUtil.formatTime(Long.parseLong(top3.get(2)[1])) + " §7(" + top3.get(2)[0] + ")"
                : "§8-  ";

        return new String[] {
            ColorUtil.translate("§7§m─────────────────"),
            ColorUtil.translate(" §7Map: §c" + mapName),
            ColorUtil.translate(" §7Blocks: " + blocksStr),
            ColorUtil.translate("§r"),
            ColorUtil.translate(" §7Session Top 3:"),
            ColorUtil.translate("  §8#1 " + top1),
            ColorUtil.translate("  §8#2 " + top2),
            ColorUtil.translate("  §8#3 " + top3str),
            ColorUtil.translate("§r "),
            ColorUtil.translate(" §8» §cCoins: §6" + fbCoins),
            ColorUtil.translate("§r  "),
            ColorUtil.translate("§7§ogravijet.net"),
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

    public void shutdown() {
        if (updateTaskId != -1) Bukkit.getScheduler().cancelTask(updateTaskId);
    }
}
