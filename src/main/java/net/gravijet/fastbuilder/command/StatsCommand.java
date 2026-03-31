package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
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
 * /stats <player> [map] - View player statistics.
 */
public class StatsCommand implements CommandExecutor, TabCompleter {

    private final FastBuilder plugin;

    public StatsCommand(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String targetName;
        String mapFilter = args.length >= 2 ? args[1] : null;

        if (args.length < 1) {
            // Show own stats if sender is a player
            if (!(sender instanceof Player)) {
                String raw = plugin.getConfigManager().getMessage("usage");
                raw = raw.replace("%command%", "/stats <player> [map]");
                raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
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
            // Try offline lookup
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
            raw = raw.replace("%player%", targetName);
            raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
            sender.sendMessage(ColorUtil.translate(raw));
            return true;
        }

        // Display stats
        String prefix = plugin.getConfigManager().getPrefix();
        sender.sendMessage(ColorUtil.translate("&7&m                                  "));
        sender.sendMessage(ColorUtil.translate("  &c&lFastbuilder &7| &fStats: &c" + data.getName()));
        sender.sendMessage(ColorUtil.translate("  &7Coins: &f" + data.getCoins()));
        sender.sendMessage(ColorUtil.translate("&7&m                                  "));

        Map<String, PlayerData.MapStats> allStats = data.getAllStats();
        if (allStats.isEmpty()) {
            sender.sendMessage(ColorUtil.translate("  &7No statistics recorded yet."));
        } else {
            for (Map.Entry<String, PlayerData.MapStats> entry : allStats.entrySet()) {
                String mapName = entry.getKey();
                if (mapFilter != null && !mapName.equalsIgnoreCase(mapFilter)) continue;

                PlayerData.MapStats stats = entry.getValue();
                String bestTime = stats.hasBestTime()
                        ? TimeUtil.formatTime(stats.bestTime)
                        : "N/A";
                int successRate = stats.totalAttempts > 0
                        ? (int) ((double) stats.successfulAttempts / stats.totalAttempts * 100)
                        : 0;

                sender.sendMessage(ColorUtil.translate("  &c" + mapName));
                sender.sendMessage(ColorUtil.translate("  &7Best Time: &f" + bestTime));
                sender.sendMessage(ColorUtil.translate("  &7Successful: &f" + stats.successfulAttempts
                        + " &7/ &f" + stats.totalAttempts + " &7(" + successRate + "%)"));
                sender.sendMessage("");
            }
        }

        sender.sendMessage(ColorUtil.translate("&7&m                                  "));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            // Suggest online player names
            String input = args[0].toLowerCase();
            List<String> result = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(input)) {
                    result.add(p.getName());
                }
            }
            return result;
        }

        if (args.length == 2) {
            // Suggest map names
            String input = args[1].toLowerCase();
            List<String> result = new ArrayList<>();
            for (String name : plugin.getMapManager().getMapNames()) {
                if (name.toLowerCase().startsWith(input)) {
                    result.add(name);
                }
            }
            return result;
        }

        return Collections.emptyList();
    }
}
