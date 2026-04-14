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
 * Admin command for managing player booster inventories.
 *
 * <pre>
 * /booster give   &lt;player&gt; &lt;type&gt; [amount]   – add boosters to inventory
 * /booster take   &lt;player&gt; &lt;type&gt; [amount]   – remove boosters from inventory
 * /booster clear  &lt;player&gt;                    – remove ALL owned boosters + cancel active
 * /booster info   &lt;player&gt;                    – list what a player owns
 * /booster list                               – list all configured booster types
 * </pre>
 *
 * Requires {@code fastbuilder.booster.admin}.
 */
public class BoosterCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList("give", "take", "clear", "info", "list");

    private final FastBuilder plugin;

    public BoosterCommand(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String prefix = plugin.getConfigManager().getPrefix();

        if (!sender.hasPermission("fastbuilder.booster.admin")) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cYou don't have permission."));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, prefix);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "list":   return handleList(sender, prefix);
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

    private boolean handleList(CommandSender sender, String prefix) {
        List<BoosterType> types = plugin.getConfigManager().getBoosterTypes();
        if (types.isEmpty()) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cNo booster types are configured."));
            return true;
        }
        sender.sendMessage(ColorUtil.translate(prefix + "&7Configured booster types:"));
        for (BoosterType t : types) {
            sender.sendMessage(ColorUtil.translate(
                    "&8  " + t.id + " &7→ " + t.formatMultiplier()
                    + " for &e" + t.durationMinutes + "m &7(&c" + t.price + " coins&7)"));
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String prefix, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cUsage: /booster info <player>"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cPlayer &f" + args[1] + " &cis not online."));
            return true;
        }
        PlayerData data = plugin.getPlayerManager().getCachedData(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cCould not load data for &f" + args[1] + "&c."));
            return true;
        }

        sender.sendMessage(ColorUtil.translate(prefix + "&7Boosters for &f" + target.getName() + "&7:"));

        // Active booster
        boolean hasActive = data.getBoosterExpiry() > System.currentTimeMillis();
        if (hasActive) {
            sender.sendMessage(ColorUtil.translate("&8  Active: &a"
                    + formatMult(data.getBoosterMultiplier())
                    + " &7(" + plugin.getBoosterManager().formatRemaining(target.getUniqueId()) + " left)"));
        } else {
            sender.sendMessage(ColorUtil.translate("&8  Active: &cNone"));
        }

        // Inventory
        if (data.hasAnyBoosters()) {
            for (java.util.Map.Entry<String, Integer> e : data.getBoosterInventory().entrySet()) {
                BoosterType type = plugin.getConfigManager().getBoosterType(e.getKey());
                String typeName = type != null ? type.displayName : e.getKey();
                sender.sendMessage(ColorUtil.translate(
                        "&8  Inventory: &f" + e.getValue() + "x " + typeName));
            }
        } else {
            sender.sendMessage(ColorUtil.translate("&8  Inventory: &7(empty)"));
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String prefix, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cUsage: /booster give <player> <type> [amount]"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cPlayer &f" + args[1] + " &cis not online."));
            return true;
        }
        String typeId = args[2].toUpperCase();
        BoosterType type = plugin.getConfigManager().getBoosterType(typeId);
        if (type == null) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cUnknown booster type &f" + typeId
                    + "&c. Use &f/booster list &cto see all types."));
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtil.translate(prefix + "&cInvalid amount."));
                return true;
            }
        }

        plugin.getBoosterManager().giveBooster(target.getUniqueId(), typeId, amount);
        sender.sendMessage(ColorUtil.translate(prefix
                + "&aGave &f" + amount + "x " + type.displayName + " &ato &f" + target.getName() + "&a."));
        target.sendMessage(ColorUtil.translate(prefix
                + "&aYou received &f" + amount + "x " + type.displayName
                + " &a— activate it from your &fBooster Inventory&a!"));
        return true;
    }

    private boolean handleTake(CommandSender sender, String prefix, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cUsage: /booster take <player> <type> [amount]"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cPlayer &f" + args[1] + " &cis not online."));
            return true;
        }
        String typeId = args[2].toUpperCase();
        BoosterType type = plugin.getConfigManager().getBoosterType(typeId);
        if (type == null) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cUnknown booster type &f" + typeId + "&c."));
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtil.translate(prefix + "&cInvalid amount."));
                return true;
            }
        }

        int removed = plugin.getBoosterManager().takeBooster(target.getUniqueId(), typeId, amount);
        if (removed == 0) {
            sender.sendMessage(ColorUtil.translate(prefix
                    + "&f" + target.getName() + " &chas no &f" + type.displayName + " &cboosters."));
        } else {
            sender.sendMessage(ColorUtil.translate(prefix
                    + "&aRemoved &f" + removed + "x " + type.displayName + " &afrom &f" + target.getName() + "&a."));
        }
        return true;
    }

    private boolean handleClear(CommandSender sender, String prefix, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cUsage: /booster clear <player>"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cPlayer &f" + args[1] + " &cis not online."));
            return true;
        }
        PlayerData data = plugin.getPlayerManager().getCachedData(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(ColorUtil.translate(prefix + "&cCould not load data for &f" + args[1] + "&c."));
            return true;
        }

        // Clear all owned boosters by consuming everything
        for (java.util.Map.Entry<String, Integer> e : new java.util.HashMap<>(data.getBoosterInventory()).entrySet()) {
            for (int i = 0; i < e.getValue(); i++) data.consumeBooster(e.getKey());
        }
        // Cancel active booster
        plugin.getBoosterManager().clearActiveBooster(target.getUniqueId());

        sender.sendMessage(ColorUtil.translate(prefix
                + "&aCleared all boosters for &f" + target.getName() + "&a."));
        return true;
    }

    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("fastbuilder.booster.admin")) return Collections.emptyList();

        if (args.length == 1) return filter(SUBS, args[0]);

        String sub = args[0].toLowerCase();
        if (args.length == 2 && !sub.equals("list")) {
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
        sender.sendMessage(ColorUtil.translate(prefix + "&cBooster admin commands:"));
        sender.sendMessage(ColorUtil.translate("&8  /booster give <player> <type> [amount]"));
        sender.sendMessage(ColorUtil.translate("&8  /booster take <player> <type> [amount]"));
        sender.sendMessage(ColorUtil.translate("&8  /booster clear <player>"));
        sender.sendMessage(ColorUtil.translate("&8  /booster info <player>"));
        sender.sendMessage(ColorUtil.translate("&8  /booster list"));
    }

    private static String formatMult(double mult) {
        if (mult == Math.floor(mult)) return (int) mult + "x";
        return String.format("%.1fx", mult);
    }
}
