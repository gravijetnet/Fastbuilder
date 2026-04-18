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
            String currentStr = current > 0 ? current + " blocks"
                    : (map.getBaseCustomLength() > 0
                    ? "base (" + map.getBaseCustomLength() + " blocks)" : "default");
            player.sendMessage(ColorUtil.translate(prefix
                    + "&fCurrent length: &c" + currentStr
                    + "  &7(allowed: &f" + map.getEffectiveMinCustomLength()
                    + " - " + map.getEffectiveMaxCustomLength() + " blocks&7)"));
            player.sendMessage(ColorUtil.translate("&7Use &f/length <blocks> &7to set, or &f/length reset &7to restore base distance."));
            return true;
        }

        String arg = args[0].toLowerCase();

        // /length reset
        if (arg.equals("reset")) {
            pData.setCustomLength(map.getName(), 0);
            int resetTo = map.getBaseCustomLength() > 0 ? map.getBaseCustomLength() : map.getEffectiveMinCustomLength();
            // Move the end island back to base distance
            if (plugin.getGameplayManager() != null) {
                plugin.getGameplayManager().placeEndPlatform(player, map, session, resetTo);
            }
            String resetLabel = map.getBaseCustomLength() > 0
                    ? "base distance (" + resetTo + " blocks)" : "default";
            player.sendMessage(ColorUtil.translate(prefix + "&fRun length reset to " + resetLabel
                    + " for &c" + map.getName() + "&f."));
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

        if (length < map.getEffectiveMinCustomLength() || length > map.getEffectiveMaxCustomLength()) {
            player.sendMessage(ColorUtil.translate(prefix
                    + "&cLength must be between &f" + map.getEffectiveMinCustomLength()
                    + " &cand &f" + map.getEffectiveMaxCustomLength() + " &cblocks."));
            return true;
        }

        pData.setCustomLength(map.getName(), length);
        // Move the end island to the new position
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().placeEndPlatform(player, map, session, length);
        }
        player.sendMessage(ColorUtil.translate(prefix
                + "&fRun length set to &c" + length + " blocks &ffor &c" + map.getName() + "&f."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> options = new java.util.ArrayList<>(java.util.Arrays.asList(
                    "reset", "10", "20", "30", "50", "75", "100", "150", "200"));
            String input = args[0].toLowerCase();
            List<String> result = new java.util.ArrayList<>();
            for (String o : options) if (o.toLowerCase().startsWith(input)) result.add(o);
            return result;
        }
        return Collections.emptyList();
    }
}
