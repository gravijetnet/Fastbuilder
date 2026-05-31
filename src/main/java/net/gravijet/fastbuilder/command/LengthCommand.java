package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.RunSession;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.Messages;
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
 * Admin enables custom length per-map either by using the --customlength setup mode
 * (end-island system) or by setting min/max bounds via /map commands.
 */
public class LengthCommand implements CommandExecutor, TabCompleter {

    private final FastBuilder plugin;

    public LengthCommand(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Messages.send(sender, "players-only");
            return true;
        }

        Player player = (Player) sender;

        RunSession session = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
        if (session == null) {
            Messages.send(player, "not-on-island");
            return true;
        }

        MapData map = plugin.getMapManager().getMap(session.getMapName());
        if (map == null || !map.hasCustomLength()) {
            Messages.send(player, "custom-length-unavailable");
            return true;
        }

        // Mirror the permission gate used by the Settings menu and the in-world
        // end-island click control so /length can't bypass it.
        if (!player.hasPermission("fastbuilder.feature.custom_length")) {
            Messages.send(player, "custom-length-no-permission");
            return true;
        }

        PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (pData == null) return true;

        if (args.length == 0) {
            int current = pData.getCustomLength(map.getName());
            int display = current > 0 ? current
                    : (map.getBaseCustomLength() > 0 ? map.getBaseCustomLength() : map.getEffectiveMinCustomLength());
            Messages.send(player, "custom-length-current",
                    "current", String.valueOf(display),
                    "min", String.valueOf(map.getEffectiveMinCustomLength()),
                    "max", String.valueOf(map.getEffectiveMaxCustomLength()));
            Messages.send(player, "custom-length-usage");
            return true;
        }

        String arg = args[0].toLowerCase();

        if (arg.equals("reset")) {
            pData.setCustomLength(map.getName(), 0);
            pData.setCustomLengthY(map.getName(), 0);
            int resetTo = map.getBaseCustomLength() > 0 ? map.getBaseCustomLength() : map.getEffectiveMinCustomLength();
            if (plugin.getGameplayManager() != null) {
                plugin.getGameplayManager().placeEndPlatform(player, map, session, resetTo);
            }
            Messages.send(player, "custom-length-reset", "length", String.valueOf(resetTo));
            return true;
        }

        int length;
        try {
            length = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            Messages.send(player, "custom-length-usage");
            return true;
        }

        if (length < map.getEffectiveMinCustomLength() || length > map.getEffectiveMaxCustomLength()) {
            Messages.send(player, "custom-length-range",
                    "min", String.valueOf(map.getEffectiveMinCustomLength()),
                    "max", String.valueOf(map.getEffectiveMaxCustomLength()));
            return true;
        }

        pData.setCustomLength(map.getName(), length);
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().placeEndPlatform(player, map, session, length);
        }
        Messages.send(player, "custom-length-set", "length", String.valueOf(length));
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
