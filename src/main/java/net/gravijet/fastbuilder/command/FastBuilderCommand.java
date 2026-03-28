package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.manager.GameManager;
import net.gravijet.fastbuilder.model.BridgeDistance;
import net.gravijet.fastbuilder.model.PlayerStats;
import net.gravijet.fastbuilder.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class FastBuilderCommand implements CommandExecutor {

    private final GameManager gameManager;

    public FastBuilderCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            // ── Player commands ───────────────────────────────────

            case "spawn":
            case "tp":
                requirePlayer(sender, () ->
                        gameManager.teleportToSpawn((Player) sender));
                break;

            case "reset":
                requirePlayer(sender, () ->
                        gameManager.resetAttempt((Player) sender));
                break;

            case "stats":
                handleStats(sender, args);
                break;

            // ── Admin commands ────────────────────────────────────

            case "setspawn":
                if (!sender.hasPermission("fastbuilder.admin")) {
                    sender.sendMessage(CC.ERROR + "You don't have permission.");
                    return true;
                }
                requirePlayer(sender, () -> gameManager.setSpawn((Player) sender));
                break;

            case "reload":
                if (!sender.hasPermission("fastbuilder.admin")) {
                    sender.sendMessage(CC.ERROR + "You don't have permission.");
                    return true;
                }
                net.gravijet.fastbuilder.Main.getInstance().reloadConfig();
                sender.sendMessage(CC.SUCCESS + "Config reloaded.");
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleStats(CommandSender sender, String[] args) {
        UUID targetUuid;
        String targetName;

        if (args.length >= 2) {
            if (!sender.hasPermission("fastbuilder.stats.others")) {
                sender.sendMessage(CC.ERROR + "You don't have permission to view others' stats.");
                return;
            }
            @SuppressWarnings("deprecation")
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(CC.ERROR + "Player &f" + args[1] + " &cnot found.");
                return;
            }
            targetUuid = target.getUniqueId();
            targetName = target.getName();
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(CC.ERROR + "Usage: /fb stats <player>");
                return;
            }
            Player player = (Player) sender;
            targetUuid = player.getUniqueId();
            targetName = player.getName();
        }

        PlayerStats stats = gameManager.getStatsManager().get(targetUuid);

        sender.sendMessage(CC.c("&c&lFastBuilder &7\u00bb &f&lStats &8- &7" + targetName));
        sender.sendMessage(CC.c(" &4\u25cf &7Total Attempts: &f" + stats.getTotalAttempts()));
        sender.sendMessage(CC.c(" &4\u25cf &7Total Successes: &a" + stats.getTotalSuccesses()));
        sender.sendMessage(CC.c(" "));
        sender.sendMessage(CC.c(" &4\u25cf &7Per Distance:"));

        for (BridgeDistance d : BridgeDistance.values()) {
            PlayerStats.DistanceStats ds = stats.getStats(d);
            if (ds.attempts == 0) continue;
            sender.sendMessage(CC.c("   &8- " + d.getColorCode() + d.getDisplayName()
                    + " &8| &7Att: &f" + ds.attempts
                    + " &8| &7Suc: &a" + ds.successes
                    + " &8| &7Best: &e" + ds.getFormattedBestTime()));
        }
    }

    private void sendHelp(CommandSender sender) {
        boolean admin = sender.hasPermission("fastbuilder.admin");
        sender.sendMessage(CC.c("&c&lFastBuilder &7\u00bb &f&lCommands"));
        sender.sendMessage(CC.c(" &4\u25cf &c/fb spawn &7\u00bb &fTeleport to your island spawn"));
        sender.sendMessage(CC.c(" &4\u25cf &c/fb reset &7\u00bb &fReset your current attempt"));
        sender.sendMessage(CC.c(" &4\u25cf &c/fb stats [player] &7\u00bb &fView your statistics"));
        if (admin) {
            sender.sendMessage(CC.c(" &4\u25cf &c/fb setspawn &7\u00bb &fSet your island spawn point"));
            sender.sendMessage(CC.c(" &4\u25cf &c/fb reload &7\u00bb &fReload the plugin config"));
        }
    }

    private void requirePlayer(CommandSender sender, Runnable action) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(CC.ERROR + "This command can only be used by players.");
            return;
        }
        action.run();
    }
}
