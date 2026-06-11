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
        if (!sender.hasPermission("fastbuilder.command.stats")) {
            net.gravijet.fastbuilder.util.Messages.send(sender, "no-permission");
            return true;
        }

        String targetName;

        if (args.length < 1) {
            if (!(sender instanceof Player)) {
                net.gravijet.fastbuilder.util.Messages.send(sender, "usage",
                        "command", "/stats [player]");
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
            net.gravijet.fastbuilder.util.Messages.send(sender, "player-not-found",
                    "player", targetName);
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
        sender.sendMessage("");
        sender.sendMessage(ColorUtil.translate("  &c&lSTATS &8» &f" + data.getName()));
        sender.sendMessage(ColorUtil.translate("  &7Coins &8» &6" + data.getCoins()));
        sender.sendMessage("");

        Map<String, PlayerData.MapStats> allStats = data.getAllStats();
        if (allStats.isEmpty()) {
            sender.sendMessage(ColorUtil.translate("  &7No runs recorded yet."));
        } else {
            for (Map.Entry<String, PlayerData.MapStats> entry : allStats.entrySet()) {
                PlayerData.MapStats stats = entry.getValue();
                String bestTime = stats.hasBestTime() ? TimeUtil.formatTime(stats.bestTime) : "&8—";
                String avgTime = stats.getAverageTime() >= 0 ? TimeUtil.formatTime(stats.getAverageTime()) : "&8—";
                int successRate = stats.totalAttempts > 0
                        ? (int) ((double) stats.successfulAttempts / stats.totalAttempts * 100) : 0;

                String topPercent = "";
                if (stats.hasBestTime() && plugin.getHologramManager() != null) {
                    topPercent = plugin.getHologramManager().calculateTopPercent(entry.getKey(), stats.bestTime);
                }
                sender.sendMessage(ColorUtil.translate("  &c" + entry.getKey()));
                sender.sendMessage(ColorUtil.translate("    &7Best  &8» &f" + bestTime
                        + (topPercent.isEmpty() ? "" : " &8" + topPercent)));
                sender.sendMessage(ColorUtil.translate("    &7Avg   &8» &f" + avgTime));
                sender.sendMessage(ColorUtil.translate("    &7Runs  &8» &f" + stats.successfulAttempts
                        + " &8/ &f" + stats.totalAttempts + " &8(" + successRate + "%)"));

                net.gravijet.fastbuilder.map.MapData mapData = plugin.getMapManager().getMap(entry.getKey());
                if (mapData != null) {
                    if (stats.hasBestTime()) {
                        String rank = mapData.getPlayerRank(stats.bestTime);
                        if (rank != null) {
                            String color = rank.equals("Diamond") ? "&b"
                                    : rank.equals("Gold") ? "&6"
                                    : rank.equals("Silver") ? "&7" : "&c";
                            sender.sendMessage(ColorUtil.translate("    &7Rank  &8» " + color + rank));
                        }
                    }
                }
                sender.sendMessage("");
            }
        }
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
