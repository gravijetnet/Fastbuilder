package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
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
 * /leaderboard [map] [limit]  (alias: /lb)
 *
 * Without arguments: shows the top 10 entries for every enabled map.
 * With a map name:   shows the top 10 (or custom limit) for that map only.
 */
public class LeaderboardCommand implements CommandExecutor, TabCompleter {

    private static final int DEFAULT_LIMIT = 10;

    private final FastBuilder plugin;

    public LeaderboardCommand(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("fastbuilder.command.leaderboard")) {
            String noPermMsg = plugin.getConfigManager().getMessage("no-permission");
            if (noPermMsg == null) noPermMsg = "%prefix%&cYou do not have permission.";
            sender.sendMessage(ColorUtil.translate(
                    noPermMsg.replace("%prefix%", plugin.getConfigManager().getPrefix())));
            return true;
        }

        String prefix = plugin.getConfigManager().getPrefix();

        // Parse arguments
        String targetMap = null;
        int limit = DEFAULT_LIMIT;

        if (args.length >= 1) {
            // First arg may be a map name or a number
            try {
                limit = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                targetMap = args[0];
            }
        }
        if (args.length >= 2) {
            try {
                limit = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }
        limit = Math.max(1, Math.min(limit, 50));

        // Players get a GUI; console/command-blocks get the chat output
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (targetMap != null) {
                MapData map = plugin.getMapManager().getMap(targetMap);
                if (map == null) {
                    player.sendMessage(ColorUtil.translate(prefix + "&cNo map called &f" + targetMap + "&c."));
                    return true;
                }
                plugin.getGuiManager().openLeaderboardGui(player, map.getName());
            } else {
                plugin.getGuiManager().openLeaderboardMapPicker(player);
            }
            return true;
        }

        // Console / command-block: chat output
        if (targetMap != null) {
            final String mapName = targetMap;
            MapData map = plugin.getMapManager().getMap(mapName);
            if (map == null) {
                sender.sendMessage(ColorUtil.translate(prefix + "&cNo map called &f" + mapName + "&c."));
                return true;
            }
            showLeaderboardForMap(sender, map, limit, prefix);
        } else {
            // Show all enabled maps
            List<MapData> maps = new ArrayList<>();
            for (MapData m : plugin.getMapManager().getAllMaps()) {
                if (m.isEnabled()) maps.add(m);
            }
            if (maps.isEmpty()) {
                sender.sendMessage(ColorUtil.translate(prefix + "&cNo enabled maps found."));
                return true;
            }
            for (MapData m : maps) {
                showLeaderboardForMap(sender, m, limit, prefix);
            }
        }
        return true;
    }

    private void showLeaderboardForMap(CommandSender sender, MapData map, int limit, String prefix) {
        String mapName = map.getName();
        // Run the storage query asynchronously so we never block the main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Map.Entry<String, Long>> top =
                    plugin.getPlayerManager().getTopPlayerTimesForMap(mapName, limit);

            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage("");
                sender.sendMessage(ColorUtil.translate("  &c&lLEADERBOARD &8» &f" + mapName));
                sender.sendMessage("");

                if (top.isEmpty()) {
                    sender.sendMessage(ColorUtil.translate("  &7No times recorded yet."));
                } else {
                    int pos = 1;
                    for (Map.Entry<String, Long> entry : top) {
                        String rank = rankPrefix(pos);
                        sender.sendMessage(ColorUtil.translate(
                                "  " + rank + " &f" + entry.getKey()
                                        + "  &8" + TimeUtil.formatTime(entry.getValue())));
                        pos++;
                    }
                }
                sender.sendMessage("");
            });
        });
    }

    private static String rankPrefix(int pos) {
        switch (pos) {
            case 1: return "&6#1";
            case 2: return "&7#2";
            case 3: return "&c#3";
            default: return "&8#" + pos;
        }
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("fastbuilder.command.leaderboard")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> options = new ArrayList<>(plugin.getMapManager().getEnabledMapNames());
            String input = args[0].toLowerCase();
            List<String> result = new ArrayList<>();
            for (String opt : options) {
                if (opt.toLowerCase().startsWith(input)) result.add(opt);
            }
            return result;
        }
        return Collections.emptyList();
    }
}
