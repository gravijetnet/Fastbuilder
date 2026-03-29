package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.map.MapManager;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Player command /fb (/fastbuilder) with subcommands:
 *   join <map>    - Join a specific map
 *   leave         - Leave to lobby (BungeeCord) or world spawn
 *   reset         - Reset your current island
 *   list          - List available maps
 *   info          - Show info about your current map/island
 *   reload        - Reload plugin configuration (admin)
 */
public class FastBuilderCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBS = Arrays.asList(
            "join", "leave", "reset", "list", "info"
    );
    private static final List<String> ALL_SUBS = Arrays.asList(
            "join", "leave", "reset", "list", "info", "reload"
    );

    private final FastBuilder plugin;

    public FastBuilderCommand(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtil.translate("&cOnly players can use this command."));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("fastbuilder.play")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        MapManager mm = plugin.getMapManager();

        switch (sub) {
            case "join":
                handleJoin(player, args, mm);
                break;
            case "leave":
                handleLeave(player);
                break;
            case "reset":
                handleReset(player, mm);
                break;
            case "list":
                handleList(player, mm);
                break;
            case "info":
                handleInfo(player, mm);
                break;
            case "reload":
                handleReload(player);
                break;
            default:
                sendHelp(player);
                break;
        }
        return true;
    }

    // --- /fb join <map> ---

    private void handleJoin(Player player, String[] args, MapManager mm) {
        if (args.length < 2) {
            msg(player, plugin.getConfigManager().getMessage("usage")
                    .replace("%command%", "/fb join <map>")
                    .replace("%prefix%", plugin.getConfigManager().getPrefix()));
            return;
        }

        String mapName = args[1];
        MapData map = mm.getMap(mapName);

        if (map == null) {
            msg(player, plugin.getConfigManager().getMessage("map-not-found")
                    .replace("%map%", mapName)
                    .replace("%prefix%", plugin.getConfigManager().getPrefix()));
            return;
        }

        if (!map.isEnabled()) {
            msg(player, plugin.getConfigManager().getMessage("map-disabled")
                    .replace("%map%", map.getName())
                    .replace("%prefix%", plugin.getConfigManager().getPrefix()));
            return;
        }

        // Free current island first
        mm.freeAllIslands(player.getUniqueId());

        // Assign a free island on the target map
        int island = mm.assignFreeIsland(map.getName(), player.getUniqueId(), player.getName());
        if (island < 0) {
            msg(player, plugin.getConfigManager().getMessage("no-free-islands")
                    .replace("%prefix%", plugin.getConfigManager().getPrefix()));
            return;
        }

        player.teleport(map.getIslandSpawn(island));

        // Update player data
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId(), player.getName());
        data.setLastMap(map.getName());
        data.setLastIsland(island);

        // Check autoscale
        mm.checkAutoscale(map);

        String raw = plugin.getConfigManager().getMessage("joined-mode");
        if (raw != null && !raw.isEmpty()) {
            raw = raw.replace("%map%", map.getName())
                    .replace("%prefix%", plugin.getConfigManager().getPrefix());
            msg(player, raw);
        }
    }

    // --- /fb leave ---

    private void handleLeave(Player player) {
        // Free all islands
        plugin.getMapManager().freeAllIslands(player.getUniqueId());

        if (plugin.getConfigManager().isBungeeEnabled()) {
            // Send to lobby server via BungeeCord
            String lobbyServer = plugin.getConfigManager().getLobbyServer();
            try {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(b);
                out.writeUTF("Connect");
                out.writeUTF(lobbyServer);
                player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to send player to lobby via BungeeCord.");
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        } else {
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }

        msg(player, plugin.getConfigManager().getPrefix() + "&fYou left the game.");
    }

    // --- /fb reset ---

    private void handleReset(Player player, MapManager mm) {
        // Find the player's current map and island
        String currentMap = null;
        int currentIsland = -1;

        for (String mapName : mm.getMapNames()) {
            int idx = mm.getPlayerIsland(mapName, player.getUniqueId());
            if (idx >= 0) {
                currentMap = mapName;
                currentIsland = idx;
                break;
            }
        }

        if (currentMap == null) {
            msg(player, plugin.getConfigManager().getPrefix() + "&cYou are not on any island.");
            return;
        }

        MapData map = mm.getMap(currentMap);
        if (map == null) return;

        // Clear and re-paste the island
        Location min = map.getIslandMin(currentIsland);
        Location max = map.getIslandMax(currentIsland);

        // Teleport player to spawn first
        player.teleport(map.getIslandSpawn(currentIsland));

        // Clear the island region, then paste the template
        plugin.getFawePaster().clearRegion(
                map.getWorld(),
                min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                max.getBlockX(), max.getBlockY(), max.getBlockZ(),
                new Runnable() {
                    @Override
                    public void run() {
                        plugin.getFawePaster().pasteTemplate(
                                map.getWorld(),
                                map.getTemplateFile(),
                                min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                                null
                        );
                    }
                }
        );

        msg(player, plugin.getConfigManager().getPrefix() + "&fYour island has been reset.");
    }

    // --- /fb list ---

    private void handleList(Player player, MapManager mm) {
        List<MapData> enabledMaps = new ArrayList<>();
        for (MapData map : mm.getAllMaps()) {
            if (map.isEnabled()) {
                enabledMaps.add(map);
            }
        }

        player.sendMessage(ColorUtil.translate("&c&lFastBuilder &7- &fAvailable Maps"));
        player.sendMessage(ColorUtil.translate("&8----------------------------------"));

        if (enabledMaps.isEmpty()) {
            player.sendMessage(ColorUtil.translate("&7No maps available."));
        } else {
            for (MapData map : enabledMaps) {
                int occupied = mm.getOccupiedCount(map.getName());
                int total = mm.getIslands(map.getName()).size();
                player.sendMessage(ColorUtil.translate(
                        "&4- &c" + map.getName() + " &7(" + occupied + "/" + total + " players)"
                ));
            }
        }

        player.sendMessage(ColorUtil.translate("&8----------------------------------"));
        player.sendMessage(ColorUtil.translate("&7Use &c/fb join <map> &7to join a map."));
    }

    // --- /fb info ---

    private void handleInfo(Player player, MapManager mm) {
        String currentMap = null;
        int currentIsland = -1;

        for (String mapName : mm.getMapNames()) {
            int idx = mm.getPlayerIsland(mapName, player.getUniqueId());
            if (idx >= 0) {
                currentMap = mapName;
                currentIsland = idx;
                break;
            }
        }

        if (currentMap == null) {
            msg(player, plugin.getConfigManager().getPrefix() + "&cYou are not on any island.");
            return;
        }

        MapData map = mm.getMap(currentMap);
        if (map == null) return;

        int occupied = mm.getOccupiedCount(currentMap);
        int total = mm.getIslands(currentMap).size();

        player.sendMessage(ColorUtil.translate("&c&lFastBuilder &7- &fIsland Info"));
        player.sendMessage(ColorUtil.translate("&8----------------------------------"));
        player.sendMessage(ColorUtil.translate("&fMap: &c" + map.getName()));
        player.sendMessage(ColorUtil.translate("&fIsland: &c#" + (currentIsland + 1)));
        player.sendMessage(ColorUtil.translate("&fPlayers: &c" + occupied + "/" + total));
        player.sendMessage(ColorUtil.translate("&fAutoscale: &c" + (map.isAutoscale() ? "enabled" : "disabled")));
        player.sendMessage(ColorUtil.translate("&8----------------------------------"));
    }

    // --- /fb reload ---

    private void handleReload(Player player) {
        if (!player.hasPermission("fastbuilder.admin")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        plugin.getConfigManager().reload();
        plugin.getMapManager().loadMaps();

        msg(player, plugin.getConfigManager().getPrefix() + "&fConfiguration reloaded.");
    }

    // --- Help ---

    private void sendHelp(Player player) {
        player.sendMessage(ColorUtil.translate("&c&lFastBuilder &7- &fCommands"));
        player.sendMessage(ColorUtil.translate("&4- &c/fb join <map> &7- &fJoin a map"));
        player.sendMessage(ColorUtil.translate("&4- &c/fb leave &7- &fLeave to lobby"));
        player.sendMessage(ColorUtil.translate("&4- &c/fb reset &7- &fReset your island"));
        player.sendMessage(ColorUtil.translate("&4- &c/fb list &7- &fList available maps"));
        player.sendMessage(ColorUtil.translate("&4- &c/fb info &7- &fShow island info"));
        if (player.hasPermission("fastbuilder.admin")) {
            player.sendMessage(ColorUtil.translate("&4- &c/fb reload &7- &fReload configuration"));
        }
    }

    // --- Tab Completion ---

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("fastbuilder.play")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subs = sender.hasPermission("fastbuilder.admin") ? ALL_SUBS : PLAYER_SUBS;
            return filter(subs, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("join")) {
                return filter(plugin.getMapManager().getEnabledMapNames(), args[1]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    // --- Message Helper ---

    private void msg(Player player, String text) {
        player.sendMessage(ColorUtil.translate(text));
    }
}
