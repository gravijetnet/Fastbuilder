package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.GameplayManager;
import net.gravijet.fastbuilder.gameplay.RunSession;
import net.gravijet.fastbuilder.util.ColorUtil;
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
            sender.sendMessage(ColorUtil.translate("&cOnly players can use this command."));
            return true;
        }

        Player player = (Player) sender;
        String prefix = plugin.getConfigManager().getPrefix();

        if (!player.hasPermission("fastbuilder.admin")) {
            String msg = plugin.getConfigManager().getMessage("no-permission");
            if (msg == null || msg.isEmpty()) msg = prefix + "&cYou do not have permission to do this.";
            player.sendMessage(ColorUtil.translate(msg.replace("%prefix%", prefix)));
            return true;
        }

        GameplayManager gm = plugin.getGameplayManager();
        if (gm == null) return true;

        RunSession session = gm.getSession(player.getUniqueId());
        if (session == null) {
            String msg = plugin.getConfigManager().getMessage("build-mode-not-on-island");
            if (msg == null || msg.isEmpty()) msg = prefix + "&cYou must be on an island to use Build Mode.";
            player.sendMessage(ColorUtil.translate(msg.replace("%prefix%", prefix)));
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
            String msg = plugin.getConfigManager().getMessage("build-mode-exit");
            if (msg == null || msg.isEmpty()) msg = prefix + "&cBuild Mode exited. Your blocks are preserved until the map is rescaled.";
            player.sendMessage(ColorUtil.translate(msg.replace("%prefix%", prefix)));
        } else {
            // Enter build mode: clear any active run first
            gm.clearAllPlacedBlocks(player.getUniqueId());
            session.reset();
            gm.enterBuildMode(player.getUniqueId());
            player.setGameMode(GameMode.CREATIVE);
            player.setAllowFlight(true);
            player.setFlying(true);
            String msg = plugin.getConfigManager().getMessage("build-mode-enter");
            if (msg == null || msg.isEmpty()) msg = prefix + "&aYou entered Build Mode. Changes will be preserved. Use &f/build &aagain to exit.";
            player.sendMessage(ColorUtil.translate(msg.replace("%prefix%", prefix)));
        }

        return true;
    }
}
