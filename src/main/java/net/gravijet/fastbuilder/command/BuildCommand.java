package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.GameplayManager;
import net.gravijet.fastbuilder.gameplay.RunSession;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /build — Toggle creative build mode on the player's island.
 * Changes are preserved in-world until the map is explicitly rescaled or regenerated.
 */
public class BuildCommand implements CommandExecutor {

    private final FastBuilder plugin;

    public BuildCommand(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            net.gravijet.fastbuilder.util.Messages.send(sender, "players-only");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("fastbuilder.admin")) {
            net.gravijet.fastbuilder.util.Messages.send(player, "no-permission");
            return true;
        }

        GameplayManager gm = plugin.getGameplayManager();
        if (gm == null) return true;

        RunSession session = gm.getSession(player.getUniqueId());
        if (session == null) {
            net.gravijet.fastbuilder.util.Messages.send(player, "build-mode-not-on-island");
            return true;
        }

        if (gm.isInBuildMode(player.getUniqueId())) {
            // Exit build mode
            gm.exitBuildMode(player.getUniqueId());
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
            plugin.getScoreboardManager().updateScoreboard(player);
            net.gravijet.fastbuilder.util.Messages.send(player, "build-mode-exit");
        } else {
            // Enter build mode — session and blocks intentionally preserved
            gm.enterBuildMode(player.getUniqueId());
            player.setGameMode(GameMode.CREATIVE);
            player.setAllowFlight(true);
            player.setFlying(true);
            net.gravijet.fastbuilder.util.Messages.send(player, "build-mode-enter");
        }

        return true;
    }
}
