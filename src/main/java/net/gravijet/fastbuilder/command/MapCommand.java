package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.map.MapManager;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.Material;
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
 * Admin command /map — thin router delegating to handler classes.
 */
public class MapCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "setup", "edit", "rename", "regen", "seticon", "enable", "disable", "delete", "scale", "distance",
            "autoscale", "setdeathy", "setmintime", "setmaxtime", "setrank", "adddesign",
            "removedesign", "setdesignmeta", "customlength", "setinfinite", "info", "list", "help"
    );
    private static final List<String> RANK_TIERS   = Arrays.asList("diamond", "gold", "silver", "bronze");
    private static final List<String> SETUP_SUBS   = Arrays.asList("continue", "finish", "cancel");
    private static final List<String> BOOLEANS     = Arrays.asList("true", "false");
    private static final int          HELP_PAGE_SIZE = 10;

    private final FastBuilder plugin;
    private final MapSetupHandler  setup;
    private final MapMetaHandler   meta;
    private final MapCommandMessages msg;

    public MapCommand(FastBuilder plugin) {
        this.plugin = plugin;
        this.setup  = new MapSetupHandler(plugin);
        this.meta   = new MapMetaHandler(plugin, setup);
        this.msg    = new MapCommandMessages(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtil.translate("&cOnly players can use this command."));
            return true;
        }

        Player player = (Player) sender;
        MapManager mm = plugin.getMapManager();

        if (args.length == 0) { sendHelp(player); return true; }

        String sub = args[0].toLowerCase();

        if (sub.equals("list")) { handleList(player, mm); return true; }

        if (!player.hasPermission("fastbuilder.admin")) {
            msg.msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        switch (sub) {
            case "setup":        setup.handleSetup(player, args, mm);          break;
            case "edit":         setup.handleEdit(player, args, mm);           break;
            case "rename":
            case "setname":      meta.handleRename(player, args, mm);          break;
            case "regen":        meta.handleRegen(player, args, mm);           break;
            case "help":         handleHelp(player, args);                     break;
            case "seticon":      meta.handleSetIcon(player, args, mm);         break;
            case "enable":       meta.handleEnable(player, args, mm);          break;
            case "disable":      meta.handleDisable(player, args, mm);         break;
            case "delete":       meta.handleDelete(player, args, mm);          break;
            case "scale":        meta.handleScale(player, args, mm);           break;
            case "distance":     meta.handleDistance(player, args, mm);        break;
            case "autoscale":    meta.handleAutoscale(player, args, mm);       break;
            case "setdeathy":    meta.handleSetDeathY(player, args, mm);       break;
            case "setmintime":   meta.handleSetMinTime(player, args, mm);      break;
            case "setmaxtime":   meta.handleSetMaxTime(player, args, mm);      break;
            case "setrank":      meta.handleSetRank(player, args, mm);         break;
            case "adddesign":    meta.handleAddDesign(player, args, mm);       break;
            case "removedesign": meta.handleRemoveDesign(player, args, mm);    break;
            case "setdesignmeta":meta.handleSetDesignMeta(player, args, mm);   break;
            case "info":         meta.handleInfo(player, args, mm);            break;
            default:             sendHelp(player);                             break;
        }
        return true;
    }

    // --- Paginated Help ---

    private void handleHelp(Player player, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }

        List<String> lines = buildHelpLines(player);
        if (lines.isEmpty()) {
            msg.msg(player, plugin.getConfigManager().getPrefix() + "&cNo commands available.");
            return;
        }
        int totalPages = Math.max(1, (int) Math.ceil(lines.size() / (double) HELP_PAGE_SIZE));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int start = (page - 1) * HELP_PAGE_SIZE;
        int end   = Math.min(start + HELP_PAGE_SIZE, lines.size());
        for (int i = start; i < end; i++) {
            player.sendMessage(ColorUtil.translate(lines.get(i)));
        }

        net.md_5.bungee.api.chat.TextComponent footer = new net.md_5.bungee.api.chat.TextComponent("");
        if (page > 1) {
            net.md_5.bungee.api.chat.TextComponent prev =
                    new net.md_5.bungee.api.chat.TextComponent(ColorUtil.translate("&c« "));
            prev.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/map help " + (page - 1)));
            prev.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.BaseComponent[]{
                            new net.md_5.bungee.api.chat.TextComponent(ColorUtil.translate("&7Previous page"))}));
            footer.addExtra(prev);
        }
        footer.addExtra(new net.md_5.bungee.api.chat.TextComponent(
                ColorUtil.translate("&7Page &c" + page + " &7/ &c" + totalPages)));
        if (page < totalPages) {
            net.md_5.bungee.api.chat.TextComponent next =
                    new net.md_5.bungee.api.chat.TextComponent(ColorUtil.translate(" &a»"));
            next.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/map help " + (page + 1)));
            next.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.BaseComponent[]{
                            new net.md_5.bungee.api.chat.TextComponent(ColorUtil.translate("&7Next page"))}));
            footer.addExtra(next);
        }
        player.spigot().sendMessage(footer);
    }

    private List<String> buildHelpLines(Player player) {
        Object[][] all = {
            {"fastbuilder.play",                  "/map list",                               "List all available maps."},
            {"fastbuilder.command.map.setup",     "/map setup",                              "Start the map setup wizard."},
            {"fastbuilder.command.map.setup",     "/map setup --infinite",                   "Start setup for an infinite map."},
            {"fastbuilder.command.map.setup",     "/map setup --customlength",               "Start setup with a custom-length end island."},
            {"fastbuilder.command.map.setup",     "/map setup continue",                     "Advance to the next setup step."},
            {"fastbuilder.command.map.setup",     "/map setup finish <name>",                "Finalize and name the map."},
            {"fastbuilder.command.map.setup",     "/map setup cancel",                       "Cancel an active setup session."},
            {"fastbuilder.command.map.setup",     "/map edit <map>",                         "Edit an existing map (spawn, hologram, finish, template)."},
            {"fastbuilder.command.map.info",      "/map info <map>",                         "Show detailed map information."},
            {"fastbuilder.command.map.rename",    "/map rename <old> <new>",                 "Rename a map."},
            {"fastbuilder.command.map.regen",     "/map regen <map>",                        "Regenerate all islands."},
            {"fastbuilder.command.map.seticon",   "/map seticon <map> [block]",              "Set the map display icon."},
            {"fastbuilder.command.map.enable",    "/map enable <map>",                       "Enable a map."},
            {"fastbuilder.command.map.disable",   "/map disable <map>",                      "Disable a map."},
            {"fastbuilder.command.map.delete",    "/map delete <map> [confirm]",             "Permanently delete a map."},
            {"fastbuilder.command.map.scale",     "/map scale <map> <count>",                "Set island count."},
            {"fastbuilder.command.map.distance",  "/map distance <map> <blocks>",            "Set island gap in blocks."},
            {"fastbuilder.command.map.autoscale", "/map autoscale <map> <true|false>",       "Toggle autoscaling."},
            {"fastbuilder.command.map.setdeathy", "/map setdeathy <map> <Y>",                "Set fall-death Y level."},
            {"fastbuilder.command.map.setrank",   "/map setmintime <map> <ms>",              "Set minimum valid run time."},
            {"fastbuilder.command.map.setrank",   "/map setmaxtime <map> <ms>",              "Set max run time (0=off)."},
            {"fastbuilder.command.map.setrank",   "/map setrank <map> <tier> <ms>",          "Set rank time threshold."},
            {"fastbuilder.command.map.adddesign", "/map adddesign <map> [key]",              "Add alternative island design."},
            {"fastbuilder.command.map.adddesign", "/map removedesign <map> <key>",           "Remove alternative island design."},
            {"fastbuilder.command.map.adddesign", "/map setdesignmeta <map> <key>",          "Save spawn/finish profile for a design."},
        };

        List<String> lines = new ArrayList<>();
        for (Object[] entry : all) {
            String perm = (String) entry[0];
            String command  = (String) entry[1];
            String desc = (String) entry[2];
            if (player.hasPermission("fastbuilder.admin") || player.hasPermission(perm)) {
                lines.add("&c● " + command + " &7» &f" + desc);
            }
        }
        return lines;
    }

    private void handleList(Player player, MapManager mm) {
        if (!player.hasPermission("fastbuilder.play")) {
            msg.msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        List<MapData> enabledMaps = new ArrayList<>();
        for (MapData map : mm.getAllMaps()) {
            if (map.isEnabled()) enabledMaps.add(map);
        }

        player.sendMessage(ColorUtil.translate("&c&lFastBuilder &7» &fAvailable Maps"));
        player.sendMessage(ColorUtil.translate("&7&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        if (enabledMaps.isEmpty()) {
            player.sendMessage(ColorUtil.translate("&7No maps are currently available."));
        } else {
            for (MapData map : enabledMaps) {
                int occupied = mm.getOccupiedCount(map.getName());
                int total = mm.getIslands(map.getName()).size();
                String mode = map.isInfinite() ? " &7[Infinite]" : map.hasCustomLength() ? " &7[Custom]" : "";
                player.sendMessage(ColorUtil.translate(
                        "&c● &f" + map.getName() + " &7» &f" + occupied + "/" + total + " players" + mode));
            }
        }
        player.sendMessage(ColorUtil.translate("&7&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(ColorUtil.translate("&7Use &c/fb join <map> &7to join."));
    }

    private void sendHelp(Player player) {
        handleHelp(player, new String[]{"help", "1"});
    }

    // --- Tab Completion ---

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        MapManager mm = plugin.getMapManager();

        if (args.length == 1) {
            if (!sender.hasPermission("fastbuilder.admin")) {
                return sender.hasPermission("fastbuilder.play")
                        ? filter(Collections.singletonList("list"), args[0])
                        : Collections.emptyList();
            }
            return filter(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("list")) return Collections.emptyList();
        if (!sender.hasPermission("fastbuilder.admin")) return Collections.emptyList();

        if (args.length == 2) {
            switch (sub) {
                case "setup":
                    List<String> setupOpts = new ArrayList<>(SETUP_SUBS);
                    setupOpts.add("--infinite");
                    setupOpts.add("--customlength");
                    setupOpts.add("--diagonal");
                    return filter(setupOpts, args[1]);
                case "edit":
                    return filter(mm.getMapNames(), args[1]);
                case "rename": case "setname": case "regen": case "seticon":
                case "scale": case "delete": case "autoscale": case "setdeathy":
                case "setmintime": case "setmaxtime": case "setrank": case "customlength":
                case "setinfinite": case "adddesign": case "removedesign":
                case "setdesignmeta": case "info": case "distance":
                    return filter(mm.getMapNames(), args[1]);
                case "enable":
                    return filter(mm.getDisabledMapNames(), args[1]);
                case "disable":
                    return filter(mm.getEnabledMapNames(), args[1]);
                case "help":
                    return Arrays.asList("1", "2", "3");
                default:
                    return Collections.emptyList();
            }
        }

        if (args.length == 3) {
            switch (sub) {
                case "setup":
                    if (args[1].equalsIgnoreCase("finish")) return Collections.singletonList("<name>");
                    return Collections.emptyList();
                case "edit":
                    return filter(Arrays.asList("continue", "finish", "cancel"), args[2]);
                case "rename": case "setname":
                    return Collections.singletonList("<new_name>");
                case "seticon":
                    return filterMaterials(args[2]);
                case "scale":
                    return Arrays.asList("1", "5", "10", "15", "20", "30");
                case "distance":
                    return Arrays.asList("3", "5", "10", "20", "30", "50", "100");
                case "setmintime":
                    return Arrays.asList("0", "500", "1000", "2000", "5000");
                case "setmaxtime":
                    return Arrays.asList("0", "30000", "60000", "120000", "300000");
                case "setrank":
                    return filter(RANK_TIERS, args[2]);
                case "autoscale": case "customlength": case "setinfinite":
                    return filter(BOOLEANS, args[2]);
                case "delete":
                    return filter(Collections.singletonList("confirm"), args[2]);
                case "removedesign": case "setdesignmeta": {
                    MapData rmap = mm.getMap(args[1]);
                    if (rmap != null) return filter(rmap.getAllTemplates(), args[2]);
                    return Collections.emptyList();
                }
                case "adddesign":
                    return filter(getAvailableTemplates(), args[2]);
                case "setdeathy":
                    return Arrays.asList("-1", "0", "10", "20", "30", "50");
                default:
                    return Collections.emptyList();
            }
        }

        if (args.length == 4 && sub.equals("setrank")) return Arrays.asList("1000", "2000", "5000", "10000", "30000");
        if (args.length == 4 && sub.equals("customlength")) return Arrays.asList("10", "20", "30", "50", "100");

        return Collections.emptyList();
    }

    private List<String> getAvailableTemplates() {
        if (plugin.getFawePaster() == null) return Collections.emptyList();
        java.io.File dir = plugin.getFawePaster().getTemplatesDir();
        java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".template"));
        if (files == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (java.io.File f : files) names.add(f.getName().replace(".template", ""));
        return names;
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) result.add(option);
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
}
