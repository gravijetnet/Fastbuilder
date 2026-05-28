package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.economy.BoosterType;
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
 * /booster — no args: opens the player booster menu.
 * /booster give|take|clear|info — admin subcommands (requires fastbuilder.booster.admin).
 */
public class BoosterCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ADMIN_SUBS = Arrays.asList("give", "take", "clear", "info");

    private final FastBuilder plugin;

    public BoosterCommand(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String prefix = plugin.getConfigManager().getPrefix();

        // No args → open booster menu for the player
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtil.translate(
                        bMsg("only-players").replace("%prefix%", prefix)));
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("fastbuilder.play")) {
                player.sendMessage(ColorUtil.translate(
                        bMsg("no-permission").replace("%prefix%", prefix)));
                return true;
            }
            plugin.getGuiManager().openBoosterHub(player);
            return true;
        }

        // All subcommands require admin
        if (!sender.hasPermission("fastbuilder.booster.admin")) {
            sender.sendMessage(ColorUtil.translate(
                    bMsg("no-permission").replace("%prefix%", prefix)));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "info":   return handleInfo(sender, prefix, args);
            case "give":   return handleGive(sender, prefix, args);
            case "take":   return handleTake(sender, prefix, args);
            case "clear":  return handleClear(sender, prefix, args);
            default:
                sendUsage(sender, prefix);
                return true;
        }
    }

    // -------------------------------------------------------------------------

    private boolean handleInfo(CommandSender sender, String prefix, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.translate(prefix + "&7Usage &8» &f/booster info <player>"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.translate(
                    bMsg("player-offline").replace("%prefix%", prefix).replace("%player%", args[1])));
            return true;
        }
        PlayerData data = plugin.getPlayerManager().getCachedData(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(ColorUtil.translate(
                    bMsg("data-error").replace("%prefix%", prefix).replace("%player%", args[1])));
            return true;
        }

        sender.sendMessage(ColorUtil.translate(
                bMsg("info-header").replace("%prefix%", prefix).replace("%player%", target.getName())));

        boolean hasActive = data.getBoosterExpiry() > System.currentTimeMillis();
        if (hasActive) {
            String remaining = plugin.getBoosterManager().formatRemaining(target.getUniqueId());
            sender.sendMessage(ColorUtil.translate(
                    bMsg("info-active")
                            .replace("%multiplier%", formatMult(data.getBoosterMultiplier()))
                            .replace("%remaining%", remaining)));
        } else {
            sender.sendMessage(ColorUtil.translate(bMsg("info-none")));
        }

        if (data.hasAnyBoosters()) {
            for (java.util.Map.Entry<String, Integer> e : data.getBoosterInventory().entrySet()) {
                BoosterType type = plugin.getConfigManager().getBoosterType(e.getKey());
                String typeName = type != null ? type.displayName : e.getKey();
                sender.sendMessage(ColorUtil.translate(
                        bMsg("info-inventory-entry")
                                .replace("%amount%", String.valueOf(e.getValue()))
                                .replace("%type%", typeName)));
            }
        } else {
            sender.sendMessage(ColorUtil.translate(bMsg("info-inventory-empty")));
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String prefix, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.translate(prefix + "&7Usage &8» &f/booster give <player> <type> [amount]"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.translate(
                    bMsg("player-offline").replace("%prefix%", prefix).replace("%player%", args[1])));
            return true;
        }
        String typeId = args[2].toUpperCase();
        BoosterType type = plugin.getConfigManager().getBoosterType(typeId);
        if (type == null) {
            sender.sendMessage(ColorUtil.translate(
                    bMsg("unknown-type").replace("%prefix%", prefix).replace("%type%", typeId)));
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtil.translate(
                        bMsg("invalid-amount").replace("%prefix%", prefix)));
                return true;
            }
        }

        plugin.getBoosterManager().giveBooster(target.getUniqueId(), typeId, amount);

        sender.sendMessage(ColorUtil.translate(
                bMsg("give-sender").replace("%prefix%", prefix)
                        .replace("%amount%", String.valueOf(amount))
                        .replace("%type%", type.displayName)
                        .replace("%player%", target.getName())));
        target.sendMessage(ColorUtil.translate(
                bMsg("give-target").replace("%prefix%", prefix)
                        .replace("%amount%", String.valueOf(amount))
                        .replace("%type%", type.displayName)));
        return true;
    }

    private boolean handleTake(CommandSender sender, String prefix, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.translate(prefix + "&7Usage &8» &f/booster take <player> <type> [amount]"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.translate(
                    bMsg("player-offline").replace("%prefix%", prefix).replace("%player%", args[1])));
            return true;
        }
        String typeId = args[2].toUpperCase();
        BoosterType type = plugin.getConfigManager().getBoosterType(typeId);
        if (type == null) {
            sender.sendMessage(ColorUtil.translate(
                    bMsg("unknown-type").replace("%prefix%", prefix).replace("%type%", typeId)));
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtil.translate(
                        bMsg("invalid-amount").replace("%prefix%", prefix)));
                return true;
            }
        }

        int removed = plugin.getBoosterManager().takeBooster(target.getUniqueId(), typeId, amount);
        if (removed == 0) {
            sender.sendMessage(ColorUtil.translate(
                    bMsg("take-none").replace("%prefix%", prefix)
                            .replace("%player%", target.getName())
                            .replace("%type%", type.displayName)));
        } else {
            sender.sendMessage(ColorUtil.translate(
                    bMsg("take-success").replace("%prefix%", prefix)
                            .replace("%amount%", String.valueOf(removed))
                            .replace("%type%", type.displayName)
                            .replace("%player%", target.getName())));
        }
        return true;
    }

    private boolean handleClear(CommandSender sender, String prefix, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.translate(prefix + "&7Usage &8» &f/booster clear <player>"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.translate(
                    bMsg("player-offline").replace("%prefix%", prefix).replace("%player%", args[1])));
            return true;
        }
        PlayerData data = plugin.getPlayerManager().getCachedData(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(ColorUtil.translate(
                    bMsg("data-error").replace("%prefix%", prefix).replace("%player%", args[1])));
            return true;
        }

        for (java.util.Map.Entry<String, Integer> e : new java.util.HashMap<>(data.getBoosterInventory()).entrySet()) {
            for (int i = 0; i < e.getValue(); i++) data.consumeBooster(e.getKey());
        }
        plugin.getBoosterManager().clearActiveBooster(target.getUniqueId());

        sender.sendMessage(ColorUtil.translate(
                bMsg("clear-success").replace("%prefix%", prefix).replace("%player%", target.getName())));
        return true;
    }

    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            // Players with fastbuilder.play see no suggestions (opening menu needs no args)
            // Admins see the subcommand list
            if (sender.hasPermission("fastbuilder.booster.admin")) {
                return filter(ADMIN_SUBS, args[0]);
            }
            return Collections.emptyList();
        }

        if (!sender.hasPermission("fastbuilder.booster.admin")) return Collections.emptyList();

        String sub = args[0].toLowerCase();
        if (args.length == 2 && !sub.equals("info") && !sub.equals("clear")
                && !sub.equals("give") && !sub.equals("take")) {
            return Collections.emptyList();
        }
        if (args.length == 2) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
            return filter(players, args[1]);
        }
        if (args.length == 3 && (sub.equals("give") || sub.equals("take"))) {
            List<String> ids = new ArrayList<>();
            for (BoosterType t : plugin.getConfigManager().getBoosterTypes()) ids.add(t.id);
            return filter(ids, args[2]);
        }
        if (args.length == 4 && (sub.equals("give") || sub.equals("take"))) {
            return Arrays.asList("1", "2", "3", "5", "10");
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

    private void sendUsage(CommandSender sender, String prefix) {
        sender.sendMessage(ColorUtil.translate(
                bMsg("usage-header").replace("%prefix%", prefix)));
        sender.sendMessage(ColorUtil.translate(bMsg("usage-give")));
        sender.sendMessage(ColorUtil.translate(bMsg("usage-take")));
        sender.sendMessage(ColorUtil.translate(bMsg("usage-clear")));
        sender.sendMessage(ColorUtil.translate(bMsg("usage-info")));
    }

    private String bMsg(String key) {
        String raw = plugin.getConfigManager().getBoosterMessage(key);
        if (raw == null || raw.isEmpty()) return "&c[booster." + key + " not set]";
        return raw;
    }

    private static String formatMult(double mult) {
        if (mult == Math.floor(mult)) return (int) mult + "x";
        return String.format("%.1fx", mult);
    }
}
