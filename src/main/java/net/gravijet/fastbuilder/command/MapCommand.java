package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.GridCalculator;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.map.MapManager;
import net.gravijet.fastbuilder.map.SetupSession;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Admin command /map with full tab-completion.
 *
 * Subcommands:
 *   setup [continue|finish|name <name>]
 *   setname <old> <new>
 *   seticon <map> <MATERIAL:DATA>
 *   enable <map>
 *   disable <map>
 *   scale <map> <count>
 *   distance <map> <blocks>
 *   autoscale <map> <true|false>
 */
public class MapCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "setup", "setname", "seticon", "enable", "disable", "scale", "distance", "autoscale"
    );
    private static final List<String> SETUP_SUBS = Arrays.asList("continue", "finish", "name");
    private static final List<String> BOOLEANS = Arrays.asList("true", "false");

    private final FastBuilder plugin;

    public MapCommand(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtil.translate("&cOnly players can use this command."));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("fastbuilder.admin")) {
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
            case "setup":
                handleSetup(player, args, mm);
                break;
            case "setname":
                handleSetName(player, args, mm);
                break;
            case "seticon":
                handleSetIcon(player, args, mm);
                break;
            case "enable":
                handleEnable(player, args, mm);
                break;
            case "disable":
                handleDisable(player, args, mm);
                break;
            case "scale":
                handleScale(player, args, mm);
                break;
            case "distance":
                handleDistance(player, args, mm);
                break;
            case "autoscale":
                handleAutoscale(player, args, mm);
                break;
            default:
                sendHelp(player);
                break;
        }
        return true;
    }

    // --- /map setup ---

    private void handleSetup(Player player, String[] args, MapManager mm) {
        if (args.length == 1) {
            // Start new setup
            startSetup(player, mm);
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "continue":
                handleSetupContinue(player, mm);
                break;
            case "finish":
                handleSetupFinish(player, mm);
                break;
            case "name":
                if (args.length < 3) {
                    msgAdmin(player, "usage", "%command%", "/map setup name <name>");
                    return;
                }
                handleSetupName(player, args[2], mm);
                break;
            default:
                msg(player, "&cUnknown setup subcommand. Use: continue, finish, name");
                break;
        }
    }

    private void startSetup(Player player, MapManager mm) {
        // Cancel existing session
        mm.removeSetupSession(player.getUniqueId());

        // Calculate next map origin
        Location origin = mm.getNextMapOrigin();

        // Start session
        mm.startSetupSession(player.getUniqueId(), origin);

        // Prepare player
        player.teleport(origin);
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.getInventory().clear();

        // Give blaze rod selection tool
        ItemStack rod = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = rod.getItemMeta();
        meta.setDisplayName(ColorUtil.translate("&c&lSelection Tool"));
        meta.setLore(Arrays.asList(
                ColorUtil.translate("&7Left-click: &fSet Position 1"),
                ColorUtil.translate("&7Right-click: &fSet Position 2 / Spawn")
        ));
        rod.setItemMeta(meta);
        player.getInventory().setItem(0, rod);

        msgAdmin(player, "setup-started");
    }

    private void handleSetupContinue(Player player, MapManager mm) {
        SetupSession session = mm.getSetupSession(player.getUniqueId());
        if (session == null) {
            msgAdmin(player, "setup-no-session");
            return;
        }

        if (!session.canContinue()) {
            msgAdmin(player, "setup-not-ready");
            return;
        }

        switch (session.getState()) {
            case SELECTING_ISLAND:
                session.advanceToSpawn();
                msgAdmin(player, "setup-set-spawn");
                break;
            case SELECTING_SPAWN:
                session.advanceToFinish();
                msgAdmin(player, "setup-select-finish");
                break;
            default:
                msgAdmin(player, "setup-not-ready");
                break;
        }
    }

    private void handleSetupFinish(Player player, MapManager mm) {
        SetupSession session = mm.getSetupSession(player.getUniqueId());
        if (session == null) {
            msgAdmin(player, "setup-no-session");
            return;
        }

        if (!session.canFinalize()) {
            msgAdmin(player, "setup-not-ready");
            return;
        }

        session.finalize_();

        // Save the template via FAWE
        Location min = session.getIslandMin();
        Location max = session.getIslandMax();
        String tempName = "setup_" + player.getUniqueId().toString().substring(0, 8);
        boolean saved = plugin.getFawePaster().saveTemplate(
                min.getWorld(),
                min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                max.getBlockX(), max.getBlockY(), max.getBlockZ(),
                tempName
        );

        if (!saved) {
            msg(player, "&cFailed to save island template. Check console for errors.");
            return;
        }

        msgAdmin(player, "setup-saved");
    }

    private void handleSetupName(Player player, String name, MapManager mm) {
        SetupSession session = mm.getSetupSession(player.getUniqueId());
        if (session == null) {
            msgAdmin(player, "setup-no-session");
            return;
        }

        if (session.getState() != SetupSession.State.AWAITING_NAME) {
            msgAdmin(player, "setup-not-ready");
            return;
        }

        if (mm.mapExists(name)) {
            msgAdmin(player, "map-already-exists");
            return;
        }

        // Rename the temp template file to the final name
        String tempName = "setup_" + player.getUniqueId().toString().substring(0, 8);
        java.io.File tempFile = new java.io.File(plugin.getFawePaster().getTemplatesDir(), tempName + ".template");
        java.io.File finalFile = new java.io.File(plugin.getFawePaster().getTemplatesDir(), name.toLowerCase() + ".template");
        if (tempFile.exists()) {
            tempFile.renameTo(finalFile);
        }

        // Create map
        MapData map = mm.createMap(session, name);
        map.setTemplateFile(name.toLowerCase());
        mm.saveMap(map);

        // Clean up session
        mm.removeSetupSession(player.getUniqueId());

        String raw = plugin.getConfigManager().getAdminMessage("setup-complete");
        raw = raw.replace("%map%", name);
        player.sendMessage(ColorUtil.translate(raw));
    }

    // --- /map setname <old> <new> ---

    private void handleSetName(Player player, String[] args, MapManager mm) {
        if (args.length < 3) {
            msgAdmin(player, "usage", "%command%", "/map setname <old> <new>");
            return;
        }

        String oldName = args[1];
        String newName = args[2];

        if (!mm.mapExists(oldName)) {
            msgMap(player, "map-not-found", oldName);
            return;
        }
        if (mm.mapExists(newName)) {
            msgAdmin(player, "map-already-exists");
            return;
        }

        mm.renameMap(oldName, newName);

        String raw = plugin.getConfigManager().getAdminMessage("map-renamed");
        raw = raw.replace("%old%", oldName).replace("%new%", newName);
        player.sendMessage(ColorUtil.translate(raw));
    }

    // --- /map seticon <map> <MATERIAL:DATA> ---

    private void handleSetIcon(Player player, String[] args, MapManager mm) {
        if (args.length < 3) {
            msgAdmin(player, "usage", "%command%", "/map seticon <map> <MATERIAL:DATA>");
            return;
        }

        MapData map = mm.getMap(args[1]);
        if (map == null) {
            msgMap(player, "map-not-found", args[1]);
            return;
        }

        String icon = args[2].toUpperCase();
        // Validate material
        String matName = icon.contains(":") ? icon.split(":")[0] : icon;
        if (Material.matchMaterial(matName) == null) {
            msg(player, "&cInvalid material: &f" + matName);
            return;
        }

        map.setIcon(icon);
        mm.saveMap(map);

        String raw = plugin.getConfigManager().getAdminMessage("map-icon-set");
        raw = raw.replace("%map%", map.getName()).replace("%icon%", icon);
        player.sendMessage(ColorUtil.translate(raw));
    }

    // --- /map enable <map> ---

    private void handleEnable(Player player, String[] args, MapManager mm) {
        if (args.length < 2) {
            msgAdmin(player, "usage", "%command%", "/map enable <map>");
            return;
        }

        MapData map = mm.getMap(args[1]);
        if (map == null) {
            msgMap(player, "map-not-found", args[1]);
            return;
        }

        map.setEnabled(true);
        mm.saveMap(map);

        // Check autoscale on enable
        mm.checkAutoscale(map);

        String raw = plugin.getConfigManager().getAdminMessage("map-enabled");
        raw = raw.replace("%map%", map.getName());
        player.sendMessage(ColorUtil.translate(raw));
    }

    // --- /map disable <map> ---

    private void handleDisable(Player player, String[] args, MapManager mm) {
        if (args.length < 2) {
            msgAdmin(player, "usage", "%command%", "/map disable <map>");
            return;
        }

        MapData map = mm.getMap(args[1]);
        if (map == null) {
            msgMap(player, "map-not-found", args[1]);
            return;
        }

        map.setEnabled(false);
        mm.saveMap(map);

        // Relocate all players on this map
        for (net.gravijet.fastbuilder.map.IslandInstance island : mm.getIslands(map.getName())) {
            if (island.isOccupied()) {
                org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(island.getOccupantUuid());
                if (p != null && p.isOnline()) {
                    mm.relocatePlayer(p, map.getName());
                }
                island.clearOccupant();
            }
        }

        String raw = plugin.getConfigManager().getAdminMessage("map-disabled");
        raw = raw.replace("%map%", map.getName());
        player.sendMessage(ColorUtil.translate(raw));
    }

    // --- /map scale <map> <count> ---

    private void handleScale(Player player, String[] args, MapManager mm) {
        if (args.length < 3) {
            msgAdmin(player, "usage", "%command%", "/map scale <map> <count>");
            return;
        }

        MapData map = mm.getMap(args[1]);
        if (map == null) {
            msgMap(player, "map-not-found", args[1]);
            return;
        }

        int count;
        try {
            count = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            msg(player, "&cInvalid number: &f" + args[2]);
            return;
        }

        if (count < 1) {
            msg(player, "&cScale must be at least 1.");
            return;
        }

        String raw = plugin.getConfigManager().getAdminMessage("scale-updating");
        raw = raw.replace("%map%", map.getName());
        player.sendMessage(ColorUtil.translate(raw));

        mm.updateScale(map, count);

        raw = plugin.getConfigManager().getAdminMessage("map-scale-set");
        raw = raw.replace("%map%", map.getName()).replace("%scale%", String.valueOf(count));
        player.sendMessage(ColorUtil.translate(raw));
    }

    // --- /map distance <map> <blocks> ---

    private void handleDistance(Player player, String[] args, MapManager mm) {
        if (args.length < 3) {
            msgAdmin(player, "usage", "%command%", "/map distance <map> <blocks>");
            return;
        }

        MapData map = mm.getMap(args[1]);
        if (map == null) {
            msgMap(player, "map-not-found", args[1]);
            return;
        }

        int blocks;
        try {
            blocks = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            msg(player, "&cInvalid number: &f" + args[2]);
            return;
        }

        int minDist = GridCalculator.getMinimumDistance(map.getIslandWidth());
        if (blocks < minDist) {
            String raw = plugin.getConfigManager().getAdminMessage("distance-too-small");
            raw = raw.replace("%min%", String.valueOf(minDist));
            player.sendMessage(ColorUtil.translate(raw));
            return;
        }

        map.setDistance(blocks);
        mm.saveMap(map);

        String raw = plugin.getConfigManager().getAdminMessage("map-distance-set");
        raw = raw.replace("%map%", map.getName()).replace("%distance%", String.valueOf(blocks));
        player.sendMessage(ColorUtil.translate(raw));
    }

    // --- /map autoscale <map> <true/false> ---

    private void handleAutoscale(Player player, String[] args, MapManager mm) {
        if (args.length < 3) {
            msgAdmin(player, "usage", "%command%", "/map autoscale <map> <true|false>");
            return;
        }

        MapData map = mm.getMap(args[1]);
        if (map == null) {
            msgMap(player, "map-not-found", args[1]);
            return;
        }

        boolean value = Boolean.parseBoolean(args[2]);
        map.setAutoscale(value);
        mm.saveMap(map);

        if (value) {
            mm.checkAutoscale(map);
        }

        String raw = plugin.getConfigManager().getAdminMessage("map-autoscale-set");
        raw = raw.replace("%map%", map.getName()).replace("%value%", String.valueOf(value));
        player.sendMessage(ColorUtil.translate(raw));
    }

    // --- Help ---

    private void sendHelp(Player player) {
        player.sendMessage(ColorUtil.translate("&c&lFastBuilder &7- &fMap Commands"));
        player.sendMessage(ColorUtil.translate("&4- &c/map setup &7- &fStart map setup wizard"));
        player.sendMessage(ColorUtil.translate("&4- &c/map setup continue &7- &fAdvance to next step"));
        player.sendMessage(ColorUtil.translate("&4- &c/map setup finish &7- &fFinalize map template"));
        player.sendMessage(ColorUtil.translate("&4- &c/map setup name <name> &7- &fSet the map name"));
        player.sendMessage(ColorUtil.translate("&4- &c/map setname <old> <new> &7- &fRename a map"));
        player.sendMessage(ColorUtil.translate("&4- &c/map seticon <map> <block> &7- &fSet map display icon"));
        player.sendMessage(ColorUtil.translate("&4- &c/map enable <map> &7- &fEnable a map"));
        player.sendMessage(ColorUtil.translate("&4- &c/map disable <map> &7- &fDisable a map"));
        player.sendMessage(ColorUtil.translate("&4- &c/map scale <map> <count> &7- &fSet island count"));
        player.sendMessage(ColorUtil.translate("&4- &c/map distance <map> <blocks> &7- &fSet island spacing"));
        player.sendMessage(ColorUtil.translate("&4- &c/map autoscale <map> <true|false> &7- &fToggle autoscaling"));
    }

    // --- Tab Completion ---

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("fastbuilder.admin")) {
            return Collections.emptyList();
        }

        MapManager mm = plugin.getMapManager();

        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase();

        if (args.length == 2) {
            switch (sub) {
                case "setup":
                    return filter(SETUP_SUBS, args[1]);
                case "setname":
                case "seticon":
                case "scale":
                case "distance":
                    return filter(mm.getMapNames(), args[1]);
                case "enable":
                    return filter(mm.getDisabledMapNames(), args[1]);
                case "disable":
                    return filter(mm.getEnabledMapNames(), args[1]);
                case "autoscale":
                    return filter(mm.getMapNames(), args[1]);
                default:
                    return Collections.emptyList();
            }
        }

        if (args.length == 3) {
            switch (sub) {
                case "setup":
                    if (args[1].equalsIgnoreCase("name")) {
                        return Collections.singletonList("<name>");
                    }
                    return Collections.emptyList();
                case "setname":
                    return Collections.singletonList("<new_name>");
                case "seticon":
                    return filterMaterials(args[2]);
                case "scale":
                    return Arrays.asList("1", "5", "10", "15", "20", "30");
                case "distance":
                    return Arrays.asList("50", "75", "100", "150", "200");
                case "autoscale":
                    return filter(BOOLEANS, args[2]);
                default:
                    return Collections.emptyList();
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

    private List<String> filterMaterials(String input) {
        String upper = input.toUpperCase();
        List<String> result = new ArrayList<>();
        for (Material mat : Material.values()) {
            if (mat.isBlock() && mat.name().startsWith(upper)) {
                result.add(mat.name());
                if (result.size() >= 20) break;
            }
        }
        return result;
    }

    // --- Message Helpers ---

    private void msg(Player player, String text) {
        player.sendMessage(ColorUtil.translate(text));
    }

    private void msgAdmin(Player player, String key) {
        String raw = plugin.getConfigManager().getAdminMessage(key);
        player.sendMessage(ColorUtil.translate(raw));
    }

    private void msgAdmin(Player player, String key, String placeholder, String value) {
        String raw = plugin.getConfigManager().getAdminMessage(key);
        if (raw == null || raw.isEmpty()) {
            raw = plugin.getConfigManager().getMessage(key);
        }
        raw = raw.replace(placeholder, value);
        raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
    }

    private void msgMap(Player player, String key, String mapName) {
        String raw = plugin.getConfigManager().getMessage(key);
        raw = raw.replace("%map%", mapName);
        raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
    }
}
