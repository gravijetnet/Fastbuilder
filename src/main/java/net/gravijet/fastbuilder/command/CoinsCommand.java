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
 * /coins [player] — view balance
 * /coins add <player|*> <amount> — admin: add coins
 * /coins remove <player|*> <amount> — admin: remove coins
 * /coins set <player|*> <amount> — admin: set coins
 */
public class CoinsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ADMIN_SUBS = Arrays.asList("add", "remove", "set");

    private final FastBuilder plugin;

    public CoinsCommand(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String prefix = plugin.getConfigManager().getPrefix();

        if (args.length == 0) {
            // View own balance
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtil.translate("&cPlayers only."));
                return true;
            }
            Player player = (Player) sender;
            PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            int coins = data != null ? data.getCoins() : 0;
            String msg = plugin.getConfigManager().getMessage("coins-balance");
            if (msg == null || msg.isEmpty()) msg = prefix + "&fYour balance: &c%coins% &fcoins.";
            player.sendMessage(ColorUtil.translate(msg.replace("%prefix%", prefix).replace("%coins%", String.valueOf(coins))));
            return true;
        }

        String sub = args[0].toLowerCase();

        // /coins <player> — view other's balance
        if (!ADMIN_SUBS.contains(sub)) {
            String targetName = args[0];
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                String notFound = plugin.getConfigManager().getMessage("player-not-found");
                if (notFound == null || notFound.isEmpty()) notFound = prefix + "&cPlayer &f%player% &cnot found.";
                sender.sendMessage(ColorUtil.translate(notFound.replace("%prefix%", prefix).replace("%player%", targetName)));
                return true;
            }
            PlayerData data = plugin.getPlayerManager().getCachedData(target.getUniqueId());
            int coins = data != null ? data.getCoins() : 0;
            String msg = plugin.getConfigManager().getMessage("coins-balance-other");
            if (msg == null || msg.isEmpty()) msg = prefix + "&f%player%'s balance: &c%coins% &fcoins.";
            sender.sendMessage(ColorUtil.translate(msg.replace("%prefix%", prefix)
                    .replace("%player%", target.getName()).replace("%coins%", String.valueOf(coins))));
            return true;
        }

        // Admin subcommands
        if (!sender.hasPermission("fastbuilder.coins.admin")) {
            String noPerms = plugin.getConfigManager().getMessage("no-permission");
            if (noPerms == null || noPerms.isEmpty()) noPerms = prefix + "&cYou do not have permission.";
            sender.sendMessage(ColorUtil.translate(noPerms.replace("%prefix%", prefix)));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cUsage: /coins " + sub + " <player|*> <amount>"));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cInvalid amount: &f" + args[2]));
            return true;
        }

        boolean wildcard = args[1].equals("*");
        List<Player> targets = new ArrayList<>();
        if (wildcard) {
            targets.addAll(Bukkit.getOnlinePlayers());
        } else {
            Player t = Bukkit.getPlayer(args[1]);
            if (t == null) {
                String notFound = plugin.getConfigManager().getMessage("player-not-found");
                if (notFound == null || notFound.isEmpty()) notFound = prefix + "&cPlayer &f%player% &cnot found.";
                sender.sendMessage(ColorUtil.translate(notFound.replace("%prefix%", prefix).replace("%player%", args[1])));
                return true;
            }
            targets.add(t);
        }

        for (Player t : targets) {
            PlayerData data = plugin.getPlayerManager().getCachedData(t.getUniqueId());
            if (data == null) continue;

            switch (sub) {
                case "add":
                    data.addCoins(amount);
                    notifyChange(sender, t.getName(), amount, "added");
                    break;
                case "remove":
                    if (!data.removeCoins(amount)) {
                        String msg = plugin.getConfigManager().getMessage("coins-not-enough");
                        if (msg == null || msg.isEmpty()) msg = prefix + "&cNot enough coins to remove from &f" + t.getName() + "&c.";
                        sender.sendMessage(ColorUtil.translate(msg.replace("%prefix%", prefix)));
                    } else {
                        notifyChange(sender, t.getName(), amount, "removed");
                    }
                    break;
                case "set":
                    data.setCoins(amount);
                    String msg = plugin.getConfigManager().getMessage("coins-set");
                    if (msg == null || msg.isEmpty()) msg = prefix + "&fCoins for &c%player% &fset to &c%amount%&f.";
                    sender.sendMessage(ColorUtil.translate(msg.replace("%prefix%", prefix)
                            .replace("%player%", t.getName()).replace("%amount%", String.valueOf(amount))));
                    break;
            }
            plugin.getPlayerManager().savePlayerData(t.getUniqueId());
        }

        if (wildcard) {
            sender.sendMessage(ColorUtil.translate(prefix + "&fApplied to &c" + targets.size() + " &fonline players."));
        }

        return true;
    }

    private void notifyChange(CommandSender sender, String targetName, int amount, String type) {
        String prefix = plugin.getConfigManager().getPrefix();
        if (type.equals("added")) {
            String msg = plugin.getConfigManager().getMessage("coins-added");
            if (msg == null || msg.isEmpty()) msg = prefix + "&a+%amount% coins &fadded to &c%player%&f.";
            sender.sendMessage(ColorUtil.translate(msg.replace("%prefix%", prefix)
                    .replace("%player%", targetName).replace("%amount%", String.valueOf(amount))));
        } else {
            String msg = plugin.getConfigManager().getMessage("coins-removed");
            if (msg == null || msg.isEmpty()) msg = prefix + "&c-%amount% coins &fremoved from &c%player%&f.";
            sender.sendMessage(ColorUtil.translate(msg.replace("%prefix%", prefix)
                    .replace("%player%", targetName).replace("%amount%", String.valueOf(amount))));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(ADMIN_SUBS);
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
            return filter(options, args[0]);
        }
        if (args.length == 2 && ADMIN_SUBS.contains(args[0].toLowerCase())) {
            List<String> players = new ArrayList<>();
            players.add("*");
            for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
            return filter(players, args[1]);
        }
        if (args.length == 3) return Arrays.asList("100", "500", "1000", "5000");
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String o : options) if (o.toLowerCase().startsWith(lower)) result.add(o);
        return result;
    }
}
