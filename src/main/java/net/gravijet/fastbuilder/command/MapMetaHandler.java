package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.map.MapManager;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.TimeUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Handles map metadata subcommands: info, rename, regen, seticon, enable, disable,
 * delete, scale, distance, autoscale, setdeathy, setmintime, setmaxtime, setrank,
 * adddesign, removedesign, setdesignmeta.
 */
class MapMetaHandler {

    private final FastBuilder plugin;
    private final MapSetupHandler setupHandler;
    private final MapCommandMessages msg;

    MapMetaHandler(FastBuilder plugin, MapSetupHandler setupHandler) {
        this.plugin = plugin;
        this.setupHandler = setupHandler;
        this.msg = new MapCommandMessages(plugin);
    }

    void handleInfo(Player player, String[] args, MapManager mm) {
        if (!player.hasPermission("fastbuilder.command.map.info")) {
            msg.msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length < 2) {
            msg.msg(player, plugin.getConfigManager().getPrefix() + "&cUsage: &f/map info <map>");
            return;
        }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msg.msgMap(player, "map-not-found", args[1]); return; }

        String prefix = plugin.getConfigManager().getPrefix();
        int occupied = mm.getOccupiedCount(map.getName());
        int total = mm.getIslands(map.getName()).size();
        player.sendMessage(ColorUtil.translate(prefix + "&c" + map.getName() + " &7» &fMap Info"));
        player.sendMessage(ColorUtil.translate("  &7» &cEnabled: &f" + (map.isEnabled() ? "&aYes" : "&cNo")));
        player.sendMessage(ColorUtil.translate("  &7» &cWorld: &f" + map.getWorldName()));
        player.sendMessage(ColorUtil.translate("  &7» &cOrigin: &f"
                + map.getOriginX() + ", " + map.getOriginY() + ", " + map.getOriginZ()));
        player.sendMessage(ColorUtil.translate("  &7» &cIslands: &f" + occupied + "/" + total
                + " &7(autoscale: " + (map.isAutoscale() ? "&aon" : "&coff") + "&7)"));
        player.sendMessage(ColorUtil.translate("  &7» &cIsland size: &f"
                + map.getIslandWidth() + "w &7× &f" + map.getIslandHeight() + "h &7× &f" + map.getIslandLength() + "l"));
        if (map.getPhysicalIslandLength() > 0) {
            player.sendMessage(ColorUtil.translate("  &7» &cPhysical length: &f" + map.getPhysicalIslandLength() + " blocks"));
        }
        player.sendMessage(ColorUtil.translate("  &7» &cIsland gap: &f" + map.getDistance()
                + " blocks &7(Z step: &f" + map.getActualZStep() + "&7)"));
        player.sendMessage(ColorUtil.translate("  &7» &cSpawn offset: &f+"
                + String.format("%.1f", map.getSpawnOffsetX()) + "x, +"
                + String.format("%.1f", map.getSpawnOffsetY()) + "y, +"
                + String.format("%.1f", map.getSpawnOffsetZ()) + "z"
                + " &7yaw: &f" + String.format("%.1f", map.getSpawnYaw())));
        player.sendMessage(ColorUtil.translate("  &7» &cNPC offset: &f+"
                + String.format("%.1f", map.getNpcOffsetX()) + "x, +"
                + String.format("%.1f", map.getNpcOffsetY()) + "y, +"
                + String.format("%.1f", map.getNpcOffsetZ()) + "z"));
        player.sendMessage(ColorUtil.translate("  &7» &cFinish zone: &fX " + map.getFinishMinX() + "→" + map.getFinishMaxX()
                + " &7Y &f" + map.getFinishMinY() + "→" + map.getFinishMaxY()
                + " &7Z &f" + map.getFinishMinZ() + "→" + map.getFinishMaxZ()));
        player.sendMessage(ColorUtil.translate("  &7» &cInfinite: &f" + (map.isInfinite() ? "&aYes" : "&cNo")));
        if (map.isDiagonal()) {
            player.sendMessage(ColorUtil.translate("  &7» &cDiagonal: &aEnabled &7(X step: &f" + map.getDiagonalStepX() + "&7)"));
        }
        if (map.hasEndIsland()) {
            player.sendMessage(ColorUtil.translate("  &7» &cCustom Length: &aEnd Island mode"
                    + " &7(base: &f" + map.getBaseCustomLength() + " &7blocks"
                    + ", range: &f" + map.getEffectiveMinCustomLength()
                    + "-" + map.getEffectiveMaxCustomLength() + "&7)"));
        } else if (map.hasCustomLength()) {
            player.sendMessage(ColorUtil.translate("  &7» &cCustom Length: &aEnabled"
                    + " &7(range: &f" + map.getMinCustomLength()
                    + "-" + map.getMaxCustomLength() + " blocks&7)"));
        } else {
            player.sendMessage(ColorUtil.translate("  &7» &cCustom Length: &7disabled"));
        }
        player.sendMessage(ColorUtil.translate("  &7» &cTemplate: &f" + map.getTemplateFile()));
        if (map.getIcon() != null && !map.getIcon().isEmpty()) {
            player.sendMessage(ColorUtil.translate("  &7» &cIcon: &f" + map.getIcon()));
        }
        if (map.getEndIslandTemplateFile() != null && !map.getEndIslandTemplateFile().isEmpty()) {
            player.sendMessage(ColorUtil.translate("  &7» &cEnd Island: &f" + map.getEndIslandTemplateFile()
                    + " &7(" + map.getEndIslandWidth() + "×" + map.getEndIslandHeight()
                    + "×" + map.getEndIslandLength() + ", yOff: " + map.getEndIslandYOffset() + ")"));
        }
        if (!map.getAlternativeTemplates().isEmpty()) {
            player.sendMessage(ColorUtil.translate("  &7» &cDesigns: &f" + map.getAlternativeTemplates().size()
                    + " &7— " + map.getAlternativeTemplates()));
        }
        if (map.hasDeathY()) {
            player.sendMessage(ColorUtil.translate("  &7» &cDeath Y: &f" + map.getDeathY()));
        }
        if (map.getMinValidTime() > 0) {
            player.sendMessage(ColorUtil.translate("  &7» &cMin valid time: &f" + TimeUtil.formatTime(map.getMinValidTime())));
        }
        if (map.getMaxCompletionTime() > 0) {
            player.sendMessage(ColorUtil.translate("  &7» &cMax run time: &f" + TimeUtil.formatTime(map.getMaxCompletionTime())));
        }
        if (map.getDiamondTime() > 0 || map.getGoldTime() > 0 || map.getSilverTime() > 0 || map.getBronzeTime() > 0) {
            player.sendMessage(ColorUtil.translate("  &7» &cRanks:"));
            if (map.getDiamondTime() > 0) player.sendMessage(ColorUtil.translate("    &7» &bDiamond &7≤ &f" + TimeUtil.formatTime(map.getDiamondTime())));
            if (map.getGoldTime() > 0)    player.sendMessage(ColorUtil.translate("    &7» &6Gold &7≤ &f" + TimeUtil.formatTime(map.getGoldTime())));
            if (map.getSilverTime() > 0)  player.sendMessage(ColorUtil.translate("    &7» &7Silver &7≤ &f" + TimeUtil.formatTime(map.getSilverTime())));
            if (map.getBronzeTime() > 0)  player.sendMessage(ColorUtil.translate("    &7» &cBronze &7≤ &f" + TimeUtil.formatTime(map.getBronzeTime())));
        }
    }

    void handleRename(Player player, String[] args, MapManager mm) {
        if (!player.hasPermission("fastbuilder.command.map.rename")) {
            msg.msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length < 3) { msg.msgAdmin(player, "usage", "%command%", "/map rename <old> <new>"); return; }

        String oldName = args[1], newName = args[2];
        if (!mm.mapExists(oldName)) { msg.msgMap(player, "map-not-found", oldName); return; }
        if (mm.mapExists(newName)) { msg.msgAdmin(player, "map-already-exists"); return; }

        mm.renameMap(oldName, newName);
        String raw = plugin.getConfigManager().getAdminMessage("map-renamed");
        raw = raw.replace("%old%", oldName).replace("%new%", newName)
                .replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
    }

    void handleRegen(Player player, String[] args, MapManager mm) {
        if (!player.hasPermission("fastbuilder.command.map.regen")) {
            msg.msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length < 2) { msg.msgAdmin(player, "usage", "%command%", "/map regen <map>"); return; }

        MapData map = mm.getMap(args[1]);
        if (map == null) { msg.msgMap(player, "map-not-found", args[1]); return; }

        String prefix = plugin.getConfigManager().getPrefix();
        player.sendMessage(ColorUtil.translate(prefix + "&fRegenerating all " + map.getScale()
                + " island(s) for &c" + map.getName() + "&f. Please wait..."));
        mm.regenerateIslands(map, map.getActualZStep());
        player.sendMessage(ColorUtil.translate(prefix + "&aRegeneration started for &c" + map.getName() + "&a."));
    }

    @SuppressWarnings("deprecation")
    void handleSetIcon(Player player, String[] args, MapManager mm) {
        if (args.length < 2) { msg.msgAdmin(player, "usage", "%command%", "/map seticon <map> [MATERIAL:DATA]"); return; }

        MapData map = mm.getMap(args[1]);
        if (map == null) { msg.msgMap(player, "map-not-found", args[1]); return; }

        String icon;
        if (args.length < 3) {
            ItemStack held = player.getItemInHand();
            if (held == null || held.getType() == Material.AIR) {
                msg.msgAdmin(player, "usage", "%command%", "/map seticon <map> [MATERIAL:DATA]");
                return;
            }
            icon = held.getType().name() + ":" + held.getDurability();
        } else {
            icon = args[2].toUpperCase();
        }
        String matName = icon.contains(":") ? icon.split(":")[0] : icon;
        if (Material.matchMaterial(matName) == null) {
            msg.msg(player, "&cInvalid material: &f" + matName);
            return;
        }

        map.setIcon(icon);
        mm.saveMap(map);
        String raw = plugin.getConfigManager().getAdminMessage("map-icon-set");
        raw = raw.replace("%map%", map.getName()).replace("%icon%", icon)
                .replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
    }

    void handleEnable(Player player, String[] args, MapManager mm) {
        if (args.length < 2) { msg.msgAdmin(player, "usage", "%command%", "/map enable <map>"); return; }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msg.msgMap(player, "map-not-found", args[1]); return; }

        map.setEnabled(true);
        mm.saveMap(map);
        mm.checkAutoscale(map);

        String raw = plugin.getConfigManager().getAdminMessage("map-enabled");
        raw = raw.replace("%map%", map.getName()).replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
    }

    void handleDisable(Player player, String[] args, MapManager mm) {
        if (args.length < 2) { msg.msgAdmin(player, "usage", "%command%", "/map disable <map>"); return; }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msg.msgMap(player, "map-not-found", args[1]); return; }

        map.setEnabled(false);
        mm.saveMap(map);

        for (net.gravijet.fastbuilder.map.IslandInstance island : mm.getIslands(map.getName())) {
            if (island.isOccupied()) {
                org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(island.getOccupantUuid());
                if (p != null && p.isOnline()) mm.relocatePlayer(p, map.getName());
                island.clearOccupant();
            }
        }

        String raw = plugin.getConfigManager().getAdminMessage("map-disabled");
        raw = raw.replace("%map%", map.getName()).replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
    }

    void handleDelete(Player player, String[] args, MapManager mm) {
        if (!player.hasPermission("fastbuilder.command.map.delete")) {
            msg.msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length < 2) {
            msg.msg(player, plugin.getConfigManager().getPrefix() + "&cUsage: &f/map delete <map>");
            return;
        }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msg.msgMap(player, "map-not-found", args[1]); return; }

        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            String prefix = plugin.getConfigManager().getPrefix();
            player.sendMessage(ColorUtil.translate(prefix
                    + "&cYou are about to permanently delete map &f" + map.getName()
                    + "&c. This cannot be undone!"));
            msg.sendDeleteConfirm(player, map.getName());
            return;
        }

        String name = map.getName();
        mm.deleteMap(name);
        msg.msg(player, plugin.getConfigManager().getPrefix() + "&aMap &c" + name + " &adeleted.");
    }

    void handleScale(Player player, String[] args, MapManager mm) {
        if (args.length < 3) { msg.msgAdmin(player, "usage", "%command%", "/map scale <map> <count>"); return; }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msg.msgMap(player, "map-not-found", args[1]); return; }

        int count;
        try { count = Integer.parseInt(args[2]); } catch (NumberFormatException e) {
            msg.msg(player, "&cInvalid number: &f" + args[2]); return;
        }
        if (count < 1) { msg.msg(player, "&cScale must be at least 1."); return; }

        String raw = plugin.getConfigManager().getAdminMessage("scale-updating");
        raw = raw.replace("%map%", map.getName()).replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));

        mm.updateScale(map, count);

        raw = plugin.getConfigManager().getAdminMessage("map-scale-set");
        raw = raw.replace("%map%", map.getName()).replace("%scale%", String.valueOf(count))
                .replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
    }

    void handleDistance(Player player, String[] args, MapManager mm) {
        if (args.length < 3) { msg.msgAdmin(player, "usage", "%command%", "/map distance <map> <blocks>"); return; }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msg.msgMap(player, "map-not-found", args[1]); return; }

        boolean force = args.length > 3 && args[3].equalsIgnoreCase("--force");
        int blocks;
        try { blocks = Integer.parseInt(args[2]); } catch (NumberFormatException e) {
            msg.msg(player, "&cInvalid number: &f" + args[2]); return;
        }
        if (!force && blocks < 1) {
            msg.msg(player, "&cDistance must be at least 1. Use --force for negative values.");
            return;
        }

        int oldActualZStep = map.getActualZStep();
        if (map.getPhysicalIslandLength() <= 0 && map.getTemplateFile() != null && plugin.getFawePaster() != null) {
            int physLen = setupHandler.computePhysicalIslandLength(map.getTemplateFile());
            if (physLen > 0) map.setPhysicalIslandLength(physLen);
        }

        map.setDistance(blocks);
        mm.saveMap(map);

        String raw = plugin.getConfigManager().getAdminMessage("map-distance-set");
        raw = raw.replace("%map%", map.getName()).replace("%distance%", String.valueOf(blocks))
                .replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));

        int newActualZStep = map.getActualZStep();
        if (newActualZStep != oldActualZStep && map.getScale() > 0) {
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                    + "&fRegenerating " + map.getScale() + " island(s) at new distance. Please wait..."));
            mm.regenerateIslands(map, oldActualZStep);
        }
    }

    void handleAutoscale(Player player, String[] args, MapManager mm) {
        if (args.length < 3) { msg.msgAdmin(player, "usage", "%command%", "/map autoscale <map> <true|false>"); return; }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msg.msgMap(player, "map-not-found", args[1]); return; }

        boolean value = Boolean.parseBoolean(args[2]);
        map.setAutoscale(value);
        mm.saveMap(map);
        if (value) mm.checkAutoscale(map);

        String raw = plugin.getConfigManager().getAdminMessage("map-autoscale-set");
        raw = raw.replace("%map%", map.getName()).replace("%value%", String.valueOf(value))
                .replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
    }

    void handleSetDeathY(Player player, String[] args, MapManager mm) {
        if (args.length < 3) {
            msg.msgAdmin(player, "usage", "%command%", "/map setdeathy <map> <yLevel>");
            player.sendMessage(ColorUtil.translate("&7Sets the Y-level at which players die and get reset. Use -1 to remove."));
            return;
        }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msg.msgMap(player, "map-not-found", args[1]); return; }

        int yLevel;
        try { yLevel = Integer.parseInt(args[2]); } catch (NumberFormatException e) {
            msg.msg(player, "&cInvalid Y level: &f" + args[2]); return;
        }

        if (yLevel < 0) {
            map.setDeathY(Integer.MIN_VALUE);
            mm.saveMap(map);
            msg.msg(player, plugin.getConfigManager().getPrefix() + "&aFall death height removed for map &c" + map.getName() + "&a.");
        } else {
            int stored = yLevel - 2;
            map.setDeathY(stored);
            mm.saveMap(map);
            msg.msg(player, plugin.getConfigManager().getPrefix() + "&aFall death height set to Y=&c" + stored
                    + " &7(input " + yLevel + " − 2) &afor map &c" + map.getName() + "&a.");
        }
    }

    void handleSetMinTime(Player player, String[] args, MapManager mm) {
        if (args.length < 3) {
            msg.msg(player, plugin.getConfigManager().getPrefix() + "&cUsage: &f/map setmintime <map> <ms> &7(0 to use global)");
            return;
        }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msg.msgMap(player, "map-not-found", args[1]); return; }

        long ms;
        try { ms = Long.parseLong(args[2]); } catch (NumberFormatException e) {
            msg.msg(player, "&cInvalid number: &f" + args[2]); return;
        }
        map.setMinValidTime(Math.max(0, ms));
        mm.saveMap(map);
        msg.msg(player, plugin.getConfigManager().getPrefix() + "&fMin valid time for &c" + map.getName()
                + " &fset to &c" + ms + "ms" + (ms <= 0 ? " &7(uses global)" : "") + "&f.");
    }

    void handleSetMaxTime(Player player, String[] args, MapManager mm) {
        if (args.length < 3) {
            msg.msg(player, plugin.getConfigManager().getPrefix() + "&cUsage: &f/map setmaxtime <map> <ms> &7(0 to disable)");
            return;
        }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msg.msgMap(player, "map-not-found", args[1]); return; }

        long ms;
        try { ms = Long.parseLong(args[2]); } catch (NumberFormatException e) {
            msg.msg(player, "&cInvalid number: &f" + args[2]); return;
        }
        map.setMaxCompletionTime(Math.max(0, ms));
        mm.saveMap(map);
        msg.msg(player, plugin.getConfigManager().getPrefix() + "&fMax completion time for &c" + map.getName()
                + " &fset to &c" + (ms <= 0 ? "disabled" : ms + "ms") + "&f.");
    }

    void handleSetRank(Player player, String[] args, MapManager mm) {
        if (args.length < 4) {
            msg.msg(player, plugin.getConfigManager().getPrefix() + "&cUsage: &f/map setrank <map> <diamond|gold|silver|bronze> <ms> &7(-1 to remove)");
            return;
        }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msg.msgMap(player, "map-not-found", args[1]); return; }

        String tier = args[2].toLowerCase();
        long ms;
        try { ms = Long.parseLong(args[3]); } catch (NumberFormatException e) {
            msg.msg(player, "&cInvalid number: &f" + args[3]); return;
        }

        switch (tier) {
            case "diamond": map.setDiamondTime(ms); break;
            case "gold":    map.setGoldTime(ms);    break;
            case "silver":  map.setSilverTime(ms);  break;
            case "bronze":  map.setBronzeTime(ms);  break;
            default:
                msg.msg(player, "&cInvalid rank tier. Use: diamond, gold, silver, bronze"); return;
        }
        mm.saveMap(map);
        String display = ms <= 0 ? "removed" : ms + "ms";
        msg.msg(player, plugin.getConfigManager().getPrefix() + "&f" + capitalize(tier) + " rank time for &c" + map.getName() + " &fset to &c" + display + "&f.");
    }

    void handleAddDesign(Player player, String[] args, MapManager mm) {
        if (args.length < 2) {
            msg.msg(player, plugin.getConfigManager().getPrefix()
                    + "&cUsage: &f/map adddesign <map> [<templateKey>] [--customlength|--infinite]");
            return;
        }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msg.msgMap(player, "map-not-found", args[1]); return; }

        String prefix = plugin.getConfigManager().getPrefix();
        boolean isCustomLength = false, isInfinite = false;
        for (String a : args) {
            if ("--customlength".equalsIgnoreCase(a)) isCustomLength = true;
            else if ("--infinite".equalsIgnoreCase(a)) isInfinite = true;
        }

        String designKey = map.getName().toLowerCase() + "_design_" + map.getAllTemplates().size();
        net.gravijet.fastbuilder.map.SetupSession session = mm.getSetupSession(player.getUniqueId());
        if (session != null && session.getIslandMin() != null && session.getIslandMax() != null) {
            Location min = session.getIslandMin();
            Location max = session.getIslandMax();
            boolean saved = plugin.getFawePaster().saveTemplate(
                    min.getWorld(),
                    min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                    max.getBlockX(), max.getBlockY(), max.getBlockZ(),
                    designKey);
            if (!saved) {
                msg.msg(player, prefix + "&cFailed to save design template. Check console for errors.");
                return;
            }
        } else {
            String keyArg = null;
            for (int i = 2; i < args.length; i++) {
                if (!args[i].startsWith("--")) { keyArg = args[i]; break; }
            }
            if (keyArg == null) {
                msg.msg(player, prefix + "&cNo active setup selection found. "
                        + "&fUse the setup wizard to select an area, or provide a template key: "
                        + "&c/map adddesign <map> <existingTemplateKey>");
                return;
            }
            java.io.File templateFile = new java.io.File(plugin.getFawePaster().getTemplatesDir(), keyArg + ".schematic");
            if (!templateFile.exists()) {
                msg.msg(player, prefix + "&cTemplate &f" + keyArg + " &cdoes not exist. "
                        + "&7Use the setup wizard to build and select an island first.");
                return;
            }
            designKey = keyArg;
        }

        String modeTag = isCustomLength ? " &7[custom-length]" : isInfinite ? " &7[infinite]" : "";
        if (isCustomLength) map.addCustomLengthTemplate(designKey);
        else if (isInfinite) map.addInfiniteTemplate(designKey);
        else map.addAlternativeTemplate(designKey);
        mm.saveMap(map);
        msg.msg(player, prefix + "&fAlternative design &c" + designKey + modeTag
                + " &fadded to map &c" + map.getName() + "&f.");
    }

    void handleRemoveDesign(Player player, String[] args, MapManager mm) {
        if (args.length < 3) {
            msg.msg(player, plugin.getConfigManager().getPrefix() + "&cUsage: &f/map removedesign <map> <templateKey>");
            return;
        }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msg.msgMap(player, "map-not-found", args[1]); return; }

        String key = args[2];
        boolean removed = map.removeAlternativeTemplate(key)
                || map.removeCustomLengthTemplate(key)
                || map.removeInfiniteTemplate(key);
        if (removed) {
            mm.saveMap(map);
            msg.msg(player, plugin.getConfigManager().getPrefix() + "&fDesign &c" + key + " &fremoved from map &c" + map.getName() + "&f.");
        } else {
            msg.msg(player, plugin.getConfigManager().getPrefix() + "&cDesign &f" + key + " &cnot found for map &f" + map.getName()
                    + "&c. Available alternatives: &f" + map.getAllTemplates());
        }
    }

    void handleSetDesignMeta(Player player, String[] args, MapManager mm) {
        if (!player.hasPermission("fastbuilder.admin")) {
            msg.msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length < 3) {
            msg.msg(player, plugin.getConfigManager().getPrefix() + "&cUsage: &f/map setdesignmeta <map> <template>");
            return;
        }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msg.msgMap(player, "map-not-found", args[1]); return; }

        String templateKey = args[2];
        if (!map.getAllTemplates().contains(templateKey)) {
            msg.msg(player, plugin.getConfigManager().getPrefix()
                    + "&cTemplate &f" + templateKey + " &cnot found for map &f" + map.getName()
                    + "&c.  Available: &f" + map.getAllTemplates());
            return;
        }

        String prefix = plugin.getConfigManager().getPrefix();
        net.gravijet.fastbuilder.map.SetupSession session = mm.getSetupSession(player.getUniqueId());
        MapData.DesignProfile profile = new MapData.DesignProfile();

        if (session != null && session.getIslandMin() != null && session.getSpawnPoint() != null) {
            profile.spawnOffsetX = session.getSpawnOffsetX();
            profile.spawnOffsetY = session.getSpawnOffsetY();
            profile.spawnOffsetZ = session.getSpawnOffsetZ();
            profile.spawnYaw     = session.getSpawnPoint().getYaw();
            profile.spawnPitch   = session.getSpawnPoint().getPitch();
            profile.finishMinX   = session.getFinishMinX();
            profile.finishMinY   = session.getFinishMinY();
            profile.finishMinZ   = session.getFinishMinZ();
            profile.finishMaxX   = session.getFinishMaxX();
            profile.finishMaxY   = session.getFinishMaxY();
            profile.finishMaxZ   = session.getFinishMaxZ();
            profile.islandWidth  = session.getIslandWidth();
            profile.islandHeight = session.getIslandHeight();
            profile.islandLength = session.getIslandLength();

            map.setDesignProfile(templateKey, profile);
            mm.saveMap(map);

            msg.msg(player, prefix + "&fDesign profile saved for &c" + templateKey
                    + " &fon &c" + map.getName() + "&f (from active setup session).");
            msg.msg(player, "&7Spawn offset: +" + String.format("%.1f", profile.spawnOffsetX)
                    + "x, +" + String.format("%.1f", profile.spawnOffsetY)
                    + "y, +" + String.format("%.1f", profile.spawnOffsetZ) + "z");
            if (profile.hasFinishZone()) {
                msg.msg(player, "&7Finish zone X: " + profile.finishMinX + " → " + profile.finishMaxX
                        + ", Y: " + profile.finishMinY + " → " + profile.finishMaxY);
            }
            msg.msg(player, "&7Island: " + profile.islandWidth + "w × " + profile.islandLength + "l");
        } else {
            int playerIsland = mm.getPlayerIsland(map.getName(), player.getUniqueId());
            if (playerIsland < 0) {
                msg.msg(player, prefix + "&cYou must be on an island of this map, "
                        + "or have an active setup session with spawn set.  "
                        + "Run &f/map setup &cand complete through Step 2 (spawn) first.");
                return;
            }

            Location islandMin = map.getIslandMin(playerIsland);
            Location playerLoc = player.getLocation();

            profile.spawnOffsetX = playerLoc.getX() - islandMin.getBlockX();
            profile.spawnOffsetY = playerLoc.getY() - islandMin.getBlockY();
            profile.spawnOffsetZ = playerLoc.getZ() - islandMin.getBlockZ();
            profile.spawnYaw     = playerLoc.getYaw();
            profile.spawnPitch   = playerLoc.getPitch();
            profile.islandWidth  = map.getIslandWidth();
            profile.islandHeight = map.getIslandHeight();
            profile.islandLength = map.getIslandLength();

            map.setDesignProfile(templateKey, profile);
            mm.saveMap(map);

            msg.msg(player, prefix + "&fDesign spawn saved for &c" + templateKey
                    + " &fon &c" + map.getName()
                    + " &7(finish zone uses map defaults — run from inside a setup wizard to also save a custom finish zone).");
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
