package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.player.PlayerData;
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
 * /coins pay <player> <amount> — transfer own coins to another player
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
        if (args.length == 0) {
            // View own balance
            if (!(sender instanceof Player)) {
                net.gravijet.fastbuilder.util.Messages.send(sender, "players-only");
                return true;
            }
            Player player = (Player) sender;
            PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            int coins = data != null ? data.getCoins() : 0;
            net.gravijet.fastbuilder.util.Messages.send(player, "coins-balance",
                    "coins", String.valueOf(coins));
            return true;
        }

        String sub = args[0].toLowerCase();

        // /coins pay <player> <amount> — player-to-player transfer
        if (sub.equals("pay")) {
            return handlePay(sender, args);
        }

        // /coins <player> — view other's balance
        if (!ADMIN_SUBS.contains(sub)) {
            String targetName = args[0];
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                net.gravijet.fastbuilder.util.Messages.send(sender, "player-not-found",
                        "player", targetName);
                return true;
            }
            PlayerData data = plugin.getPlayerManager().getCachedData(target.getUniqueId());
            int coins = data != null ? data.getCoins() : 0;
            net.gravijet.fastbuilder.util.Messages.send(sender, "coins-balance-other",
                    "player", target.getName(), "coins", String.valueOf(coins));
            return true;
        }

        // Admin subcommands
        if (!sender.hasPermission("fastbuilder.coins.admin")) {
            net.gravijet.fastbuilder.util.Messages.send(sender, "no-permission");
            return true;
        }

        if (args.length < 3) {
            net.gravijet.fastbuilder.util.Messages.send(sender, "usage",
                    "command", "/coins " + sub + " <player|*> <amount>");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            net.gravijet.fastbuilder.util.Messages.send(sender, "invalid-amount-value",
                    "amount", args[2]);
            return true;
        }
        if (amount < 0) {
            net.gravijet.fastbuilder.util.Messages.send(sender, "amount-non-negative");
            return true;
        }

        boolean wildcard = args[1].equals("*");
        List<Player> targets = new ArrayList<>();
        if (wildcard) {
            targets.addAll(Bukkit.getOnlinePlayers());
        } else {
            Player t = Bukkit.getPlayer(args[1]);
            if (t == null) {
                net.gravijet.fastbuilder.util.Messages.send(sender, "player-not-found",
                        "player", args[1]);
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
                    net.gravijet.fastbuilder.util.Messages.send(sender, "coins-added",
                            "player", t.getName(), "amount", String.valueOf(amount));
                    break;
                case "remove":
                    if (!data.removeCoins(amount)) {
                        net.gravijet.fastbuilder.util.Messages.send(sender, "coins-not-enough-other",
                                "player", t.getName());
                    } else {
                        net.gravijet.fastbuilder.util.Messages.send(sender, "coins-removed",
                                "player", t.getName(), "amount", String.valueOf(amount));
                    }
                    break;
                case "set":
                    data.setCoins(amount);
                    net.gravijet.fastbuilder.util.Messages.send(sender, "coins-set",
                            "player", t.getName(), "amount", String.valueOf(amount));
                    break;
            }
            plugin.getPlayerManager().savePlayerData(t.getUniqueId());
        }

        if (wildcard) {
            net.gravijet.fastbuilder.util.Messages.send(sender, "coins-applied-all",
                    "count", String.valueOf(targets.size()));
        }

        return true;
    }

    private boolean handlePay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            net.gravijet.fastbuilder.util.Messages.send(sender, "players-only");
            return true;
        }
        Player payer = (Player) sender;
        if (!payer.hasPermission("fastbuilder.coins.pay")) {
            net.gravijet.fastbuilder.util.Messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            net.gravijet.fastbuilder.util.Messages.send(sender, "usage",
                    "command", "/coins pay <player> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            net.gravijet.fastbuilder.util.Messages.send(sender, "player-not-found",
                    "player", args[1]);
            return true;
        }
        if (target.getUniqueId().equals(payer.getUniqueId())) {
            net.gravijet.fastbuilder.util.Messages.send(sender, "coins-pay-self");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            net.gravijet.fastbuilder.util.Messages.send(sender, "invalid-amount-value",
                    "amount", args[2]);
            return true;
        }
        if (amount < 1) {
            net.gravijet.fastbuilder.util.Messages.send(sender, "coins-pay-min");
            return true;
        }

        PlayerData payerData  = plugin.getPlayerManager().getCachedData(payer.getUniqueId());
        PlayerData targetData = plugin.getPlayerManager().getCachedData(target.getUniqueId());
        if (payerData == null || targetData == null) {
            net.gravijet.fastbuilder.util.Messages.send(sender, "player-not-found",
                    "player", args[1]);
            return true;
        }

        if (!payerData.removeCoins(amount)) {
            net.gravijet.fastbuilder.util.Messages.send(sender, "coins-pay-not-enough",
                    "amount", String.valueOf(amount));
            return true;
        }
        targetData.addCoins(amount);

        plugin.getPlayerManager().savePlayerData(payer.getUniqueId());
        plugin.getPlayerManager().savePlayerData(target.getUniqueId());

        net.gravijet.fastbuilder.util.Messages.send(payer, "coins-pay-sent",
                "player", target.getName(), "amount", String.valueOf(amount));
        net.gravijet.fastbuilder.util.Messages.send(target, "coins-pay-received",
                "player", payer.getName(), "amount", String.valueOf(amount));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(ADMIN_SUBS);
            options.add("pay");
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
            return filter(options, args[0]);
        }
        boolean subWithTarget = ADMIN_SUBS.contains(args[0].toLowerCase()) || args[0].equalsIgnoreCase("pay");
        if (args.length == 2 && subWithTarget) {
            List<String> players = new ArrayList<>();
            if (!args[0].equalsIgnoreCase("pay")) players.add("*");
            for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
            return filter(players, args[1]);
        }
        if (args.length == 3 && subWithTarget) return Arrays.asList("100", "500", "1000", "5000");
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String o : options) if (o.toLowerCase().startsWith(lower)) result.add(o);
        return result;
    }
}
