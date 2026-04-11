package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /adminexp add <player> <amount>
 *
 * Grants experience points to a player.  Requires fastbuilder.admin.exp.
 */
public class AdminExpCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList("add", "set", "get");

    private final FastBuilder plugin;

    public AdminExpCommand(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String prefix = plugin.getConfigManager().getPrefix();

        if (!sender.hasPermission("fastbuilder.admin.exp")) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cYou don't have permission to use this command."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cUsage: /adminexp <add|set|get> <player> [amount]"));
            return true;
        }

        String sub        = args[0].toLowerCase();
        String targetName = args[1];
        Player target     = Bukkit.getPlayer(targetName);

        if (target == null) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cPlayer &f" + targetName + " &cis not online."));
            return true;
        }

        PlayerData data = plugin.getPlayerManager().getCachedData(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cCould not load data for &f" + targetName + "&c."));
            return true;
        }

        switch (sub) {
            case "get": {
                sender.sendMessage(ColorUtil.translate(prefix
                        + "&f" + target.getName() + " &7has &c" + data.getExperience() + " &7EXP."));
                return true;
            }
            case "add":
            case "set": {
                if (args.length < 3) {
                    sender.sendMessage(ColorUtil.translate(prefix + "&cUsage: /adminexp " + sub + " <player> <amount>"));
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ColorUtil.translate(prefix + "&cInvalid amount: &f" + args[2]));
                    return true;
                }
                if (amount < 0) {
                    sender.sendMessage(ColorUtil.translate(prefix + "&cAmount must be non-negative."));
                    return true;
                }

                if (sub.equals("add")) {
                    data.addExperience(amount);
                    sender.sendMessage(ColorUtil.translate(prefix
                            + "&a+" + amount + " EXP &fgranted to &c" + target.getName() + "&f."));
                    target.sendMessage(ColorUtil.translate(prefix
                            + "&a+" + amount + " experience points &fgranted by an admin!"));
                } else {
                    data.setExperience(amount);
                    sender.sendMessage(ColorUtil.translate(prefix
                            + "&fEXP for &c" + target.getName() + " &fset to &a" + amount + "&f."));
                }

                plugin.getPlayerManager().savePlayerData(target.getUniqueId());
                return true;
            }
            default:
                sender.sendMessage(ColorUtil.translate(prefix + "&cUsage: /adminexp <add|set|get> <player> [amount]"));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("fastbuilder.admin.exp")) return Collections.emptyList();
        if (args.length == 1) return filter(SUBS, args[0]);
        if (args.length == 2) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
            return filter(players, args[1]);
        }
        if (args.length == 3 && !args[0].equalsIgnoreCase("get")) {
            return Arrays.asList("10", "50", "100", "500", "1000");
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(lower)) result.add(o);
        }
        return result;
    }
}
