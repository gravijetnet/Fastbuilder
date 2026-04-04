package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.RunSession;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * /length [blocks|reset] — Set or reset the player's custom run length for their current map.
 *
 * The admin must first enable custom length on the map with:
 *   /map setcustomlength <map> <min> <max>
 *
 * Players then use /length <blocks> to set their preferred distance to the finish island.
 * The end island repositions dynamically based on their setting.
 */
public class LengthCommand implements CommandExecutor, TabCompleter {

    private final FastBuilder plugin;

    public LengthCommand(FastBuilder plugin) {
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

        // Must be on an island
        RunSession session = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
        if (session == null) {
            player.sendMessage(ColorUtil.translate(prefix + "&cYou must be on an island to use this command."));
            return true;
        }

        MapData map = plugin.getMapManager().getMap(session.getMapName());
        if (map == null || !map.hasCustomLength()) {
            player.sendMessage(ColorUtil.translate(prefix + "&cCustom length is not enabled on this map."));
            return true;
        }

        PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (pData == null) return true;

        // /length with no args — show current setting and limits
        if (args.length == 0) {
            int current = pData.getCustomLength(map.getName());
            String currentStr = current > 0 ? String.valueOf(current) + " blocks" : "default";
            player.sendMessage(ColorUtil.translate(prefix
                    + "&fCurrent length: &c" + currentStr
                    + "  &7(allowed: &f" + map.getMinCustomLength()
                    + " - " + map.getMaxCustomLength() + " blocks&7)"));
            player.sendMessage(ColorUtil.translate("&7Use &f/length <blocks> &7to set, or &f/length reset &7to restore default."));
            return true;
        }

        String arg = args[0].toLowerCase();

        // /length reset
        if (arg.equals("reset")) {
            pData.setCustomLength(map.getName(), 0);
            plugin.getPlayerManager().savePlayerData(player.getUniqueId());
            player.sendMessage(ColorUtil.translate(prefix + "&fRun length reset to default for &c" + map.getName() + "&f."));
            return true;
        }

        // /length <blocks>
        int length;
        try {
            length = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            player.sendMessage(ColorUtil.translate(prefix + "&cUsage: &f/length <blocks> &7or &f/length reset"));
            return true;
        }

        if (length < map.getMinCustomLength() || length > map.getMaxCustomLength()) {
            player.sendMessage(ColorUtil.translate(prefix
                    + "&cLength must be between &f" + map.getMinCustomLength()
                    + " &cand &f" + map.getMaxCustomLength() + " &cblocks."));
            return true;
        }

        pData.setCustomLength(map.getName(), length);
        plugin.getPlayerManager().savePlayerData(player.getUniqueId());
        player.sendMessage(ColorUtil.translate(prefix
                + "&fRun length set to &c" + length + " blocks &ffor &c" + map.getName() + "&f."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return java.util.Arrays.asList("reset");
        }
        return Collections.emptyList();
    }
}
