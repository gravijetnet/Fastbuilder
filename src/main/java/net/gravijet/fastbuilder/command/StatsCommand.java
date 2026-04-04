package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * /stats [player] - View player statistics.
 * Opens a GUI for in-game players; prints to chat for console.
 */
public class StatsCommand implements CommandExecutor, TabCompleter {

    private final FastBuilder plugin;

    public StatsCommand(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String targetName;

        if (args.length < 1) {
            if (!(sender instanceof Player)) {
                String raw = plugin.getConfigManager().getMessage("usage");
                raw = raw.replace("%command%", "/stats [player]")
                        .replace("%prefix%", plugin.getConfigManager().getPrefix());
                sender.sendMessage(ColorUtil.translate(raw));
                return true;
            }
            targetName = sender.getName();
        } else {
            targetName = args[0];
        }

        // Try online player first
        Player target = Bukkit.getPlayerExact(targetName);
        PlayerData data;

        if (target != null) {
            data = plugin.getPlayerManager().getPlayerData(target.getUniqueId(), target.getName());
        } else {
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
            if (offline.hasPlayedBefore()) {
                data = plugin.getPlayerManager().loadOfflineData(offline.getUniqueId());
            } else {
                data = null;
            }
        }

        if (data == null) {
            String raw = plugin.getConfigManager().getMessage("player-not-found");
            raw = raw.replace("%player%", targetName)
                    .replace("%prefix%", plugin.getConfigManager().getPrefix());
            sender.sendMessage(ColorUtil.translate(raw));
            return true;
        }

        // Players get a GUI; console gets chat output
        if (sender instanceof Player) {
            plugin.getGuiManager().openStatsGui((Player) sender, data);
        } else {
            printStatsToChat(sender, data);
        }

        return true;
    }

    private void printStatsToChat(CommandSender sender, PlayerData data) {
        sender.sendMessage(ColorUtil.translate("&7&m                                  "));
        sender.sendMessage(ColorUtil.translate("  &c&lFastbuilder &7| &fStats: &c" + data.getName()));
        sender.sendMessage(ColorUtil.translate("  &7Coins: &f" + data.getCoins()));
        sender.sendMessage(ColorUtil.translate("&7&m                                  "));

        Map<String, PlayerData.MapStats> allStats = data.getAllStats();
        if (allStats.isEmpty()) {
            sender.sendMessage(ColorUtil.translate("  &7No statistics recorded yet."));
        } else {
            for (Map.Entry<String, PlayerData.MapStats> entry : allStats.entrySet()) {
                PlayerData.MapStats stats = entry.getValue();
                String bestTime = stats.hasBestTime() ? TimeUtil.formatTime(stats.bestTime) : "N/A";
                String avgTime = stats.getAverageTime() >= 0 ? TimeUtil.formatTime(stats.getAverageTime()) : "N/A";
                int successRate = stats.totalAttempts > 0
                        ? (int) ((double) stats.successfulAttempts / stats.totalAttempts * 100) : 0;

                sender.sendMessage(ColorUtil.translate("  &c" + entry.getKey()));
                sender.sendMessage(ColorUtil.translate("  &7Best Time: &f" + bestTime));
                sender.sendMessage(ColorUtil.translate("  &7Average Time: &f" + avgTime));
                sender.sendMessage(ColorUtil.translate("  &7Total Attempts: &f" + stats.totalAttempts));
                sender.sendMessage(ColorUtil.translate("  &7Successful: &f" + stats.successfulAttempts));
                sender.sendMessage(ColorUtil.translate("  &7Success Rate: &f" + successRate + "%"));

                net.gravijet.fastbuilder.map.MapData mapData = plugin.getMapManager().getMap(entry.getKey());
                if (mapData != null) {
                    if (stats.hasBestTime()) {
                        String rank = mapData.getPlayerRank(stats.bestTime);
                        if (rank != null) {
                            String color = rank.equals("Diamond") ? "&b"
                                    : rank.equals("Gold") ? "&6"
                                    : rank.equals("Silver") ? "&7" : "&c";
                            sender.sendMessage(ColorUtil.translate("  &7Rank: " + color + rank));
                        }
                    }
                    if (mapData.getDiamondTime() > 0 || mapData.getGoldTime() > 0
                            || mapData.getSilverTime() > 0 || mapData.getBronzeTime() > 0) {
                        sender.sendMessage(ColorUtil.translate("  &7Rank Requirements:"));
                        if (mapData.getDiamondTime() > 0) sender.sendMessage(ColorUtil.translate("    &bDiamond: &f" + net.gravijet.fastbuilder.util.TimeUtil.formatTime(mapData.getDiamondTime())));
                        if (mapData.getGoldTime() > 0) sender.sendMessage(ColorUtil.translate("    &6Gold: &f" + net.gravijet.fastbuilder.util.TimeUtil.formatTime(mapData.getGoldTime())));
                        if (mapData.getSilverTime() > 0) sender.sendMessage(ColorUtil.translate("    &7Silver: &f" + net.gravijet.fastbuilder.util.TimeUtil.formatTime(mapData.getSilverTime())));
                        if (mapData.getBronzeTime() > 0) sender.sendMessage(ColorUtil.translate("    &cBronze: &f" + net.gravijet.fastbuilder.util.TimeUtil.formatTime(mapData.getBronzeTime())));
                    }
                }
                sender.sendMessage("");
            }
        }
        sender.sendMessage(ColorUtil.translate("&7&m                                  "));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> result = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(input)) result.add(p.getName());
            }
            return result;
        }
        return Collections.emptyList();
    }
}
