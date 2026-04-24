package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
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
            "setup", "rename", "regen", "seticon", "enable", "disable", "delete", "scale", "distance",
            "autoscale", "setdeathy", "setmintime", "setmaxtime", "setrank", "adddesign",
            "removedesign", "setdesignmeta", "customlength", "setinfinite", "info", "list", "help"
    );
    private static final List<String> RANK_TIERS = Arrays.asList("diamond", "gold", "silver", "bronze");
    private static final List<String> SETUP_SUBS = Arrays.asList("continue", "finish", "cancel");
    private static final List<String> BOOLEANS = Arrays.asList("true", "false");

    private static final int HELP_PAGE_SIZE = 10;

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
        MapManager mm = plugin.getMapManager();

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        // /map list is accessible to regular players (fastbuilder.play)
        if (sub.equals("list")) {
            handleList(player, mm);
            return true;
        }

        if (!player.hasPermission("fastbuilder.admin")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        switch (sub) {
            case "setup":
                handleSetup(player, args, mm);
                break;
            case "rename":
                handleRename(player, args, mm);
                break;
            case "regen":
                handleRegen(player, args, mm);
                break;
            case "setname":
                handleSetName(player, args, mm);
                break;
            case "help":
                handleHelp(player, args);
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
            case "delete":
                handleDelete(player, args, mm);
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
            case "setdeathy":
                handleSetDeathY(player, args, mm);
                break;
            case "setmintime":
                handleSetMinTime(player, args, mm);
                break;
            case "setmaxtime":
                handleSetMaxTime(player, args, mm);
                break;
            case "setrank":
                handleSetRank(player, args, mm);
                break;
            case "adddesign":
                handleAddDesign(player, args, mm);
                break;
            case "removedesign":
                handleRemoveDesign(player, args, mm);
                break;
            case "setdesignmeta":
                handleSetDesignMeta(player, args, mm);
                break;
            case "info":
                handleInfo(player, args, mm);
                break;
            default:
                sendHelp(player);
                break;
        }
        return true;
    }

    // --- /map setup ---

    private void handleSetup(Player player, String[] args, MapManager mm) {
        if (!player.hasPermission("fastbuilder.command.map.setup")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        if (args.length == 1) {
            // /map setup — start new setup (normal)
            startSetup(player, mm, false, false, false);
            return;
        }

        // /map setup --infinite — start as infinite map
        if (args[1].equalsIgnoreCase("--infinite")) {
            startSetup(player, mm, true, false, false);
            return;
        }

        // /map setup --customlength — start with separate end island + real-time distance
        if (args[1].equalsIgnoreCase("--customlength")) {
            startSetup(player, mm, false, true, false);
            return;
        }

        // /map setup --diagonal — diagonal island layout (admin selects X-step after island selection)
        if (args[1].equalsIgnoreCase("--diagonal")) {
            startSetup(player, mm, false, false, true);
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "continue":
                handleSetupContinue(player, args, mm);
                break;
            case "finish":
                // /map setup finish <name> — finalize + name in one step
                if (args.length < 3) {
                    msg(player, plugin.getConfigManager().getPrefix()
                            + "&cUsage: &f/map setup finish <name>");
                    return;
                }
                handleSetupFinishWithName(player, args[2], mm);
                break;
            case "cancel":
                handleSetupCancel(player, mm);
                break;
            default:
                msg(player, "&cUnknown setup subcommand. Use: continue, finish <name>, cancel");
                break;
        }
    }

    private void startSetup(Player player, MapManager mm, boolean infinite, boolean customLength, boolean diagonal) {
        // Cancel existing session
        mm.removeSetupSession(player.getUniqueId());

        // Setup area is at -1000, 20, -1000 (dedicated build area, not the grid)
        Location origin = new Location(player.getWorld(), -1000, 20, -1000);

        // Start session and flag modes early
        SetupSession session = mm.startSetupSession(player.getUniqueId(), origin);
        if (infinite) session.setInfinite(true);
        if (customLength) session.setCustomLengthMode(true);
        if (diagonal) session.setDiagonalMode(true);

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

        String prefix = plugin.getConfigManager().getPrefix();
        String modeTag = infinite ? " &7(Infinite Mode)" : customLength ? " &7(Custom Length Mode)" : diagonal ? " &7(Diagonal Mode)" : "";
        player.sendMessage(ColorUtil.translate(prefix + "&aMap Setup Wizard started!" + modeTag));
        player.sendMessage(ColorUtil.translate("&7You have been teleported to the setup area."));
        if (customLength) {
            player.sendMessage(ColorUtil.translate("&6&lCustom Length Mode: &7Build your &bStart Island &7and &bEnd Island &7in this area."));
        }
        player.sendMessage(ColorUtil.translate("&e&lStep 1: &fSelect your &bstart island &7area."));
        player.sendMessage(ColorUtil.translate("&7  &c&lLeft-click &fthe blaze rod to set &bPosition 1 &7(one corner)."));
        player.sendMessage(ColorUtil.translate("&7  &c&lRight-click &fthe blaze rod to set &bPosition 2 &7(opposite corner)."));
        player.sendMessage(ColorUtil.translate("&7Build your island template here, then type:"));
        sendClickableContinue(player);
    }

    private void handleSetupContinue(Player player, String[] args, MapManager mm) {
        SetupSession session = mm.getSetupSession(player.getUniqueId());
        if (session == null) {
            msgAdmin(player, "setup-no-session");
            return;
        }

        if (!session.canContinue()) {
            msgAdmin(player, "setup-not-ready");
            return;
        }

        boolean forceSpawnLoc = false;
        for (String a : args) {
            if ("--force".equalsIgnoreCase(a)) {
                forceSpawnLoc = true;
                break;
            }
        }

        String prefix = plugin.getConfigManager().getPrefix();

        switch (session.getState()) {
            case SELECTING_ISLAND:
                if (session.isDiagonalMode()) {
                    session.advanceToDiagonal();
                    player.sendMessage(ColorUtil.translate(prefix + "&aIsland area saved!"));
                    player.sendMessage(ColorUtil.translate("&e&lStep 2 (Diagonal): &fSet the diagonal X direction."));
                    player.sendMessage(ColorUtil.translate("&7  &c&lRight-click &fa block at the X position where island slot 1's west edge should start."));
                    player.sendMessage(ColorUtil.translate("&7  The plugin will compute the X step automatically from that click."));
                } else {
                    session.advanceToSpawn();
                    player.sendMessage(ColorUtil.translate(prefix + "&aIsland area saved!"));
                    player.sendMessage(ColorUtil.translate("&e&lStep 2: &fSet the spawn point."));
                    player.sendMessage(ColorUtil.translate("&7  Stand exactly where players should spawn on the island."));
                    player.sendMessage(ColorUtil.translate("&7  &c&lRight-click &fthe blaze rod to set the spawn."));
                    player.sendMessage(ColorUtil.translate("&7  Then type:"));
                    sendClickableContinue(player);
                }
                break;
            case SELECTING_SPAWN:
                if (!forceSpawnLoc) {
                    Location spawnPt = session.getSpawnPoint();
                    if (spawnPt != null) {
                        float yaw = spawnPt.getYaw();
                        float normalized = ((yaw % 360) + 360) % 360; // normalize to 0–360
                        // East = yaw -90 → normalized 270; allow ±15° tolerance
                        boolean facingEast = Math.abs(normalized - 270f) <= 15f;
                        if (!facingEast) {
                            msgAdmin(player, "setup-spawn-not-east");
                            return;
                        }
                    }
                }
                session.advanceToNpc();
                player.sendMessage(ColorUtil.translate(prefix + "&aSpawn point saved!"));
                player.sendMessage(ColorUtil.translate("&e&lStep 3: &fSet the NPC location."));
                player.sendMessage(ColorUtil.translate("&7  Go to where the NPC should stand on the island."));
                player.sendMessage(ColorUtil.translate("&7  &c&lRight-click &fthe blaze rod to set the NPC position."));
                player.sendMessage(ColorUtil.translate("&7  Then type:"));
                sendClickableContinue(player);
                break;
            case SELECTING_NPC:
                session.advanceToHologram();
                player.sendMessage(ColorUtil.translate(prefix + "&aNPC location saved!"));
                player.sendMessage(ColorUtil.translate("&e&lStep 4: &fSet the hologram location."));
                player.sendMessage(ColorUtil.translate("&7  Go to where the stats hologram should appear."));
                player.sendMessage(ColorUtil.translate("&7  &c&lRight-click &fthe blaze rod to set the hologram position."));
                player.sendMessage(ColorUtil.translate("&7  Then type:"));
                sendClickableContinue(player);
                break;
            case SELECTING_HOLOGRAM: {
                // Determine mode: infinite takes priority, then customLength, then normal
                boolean isInfiniteFlag = session.isInfinite();
                boolean isCLMode = session.isCustomLengthMode();
                for (String a : args) {
                    if ("--infinite".equalsIgnoreCase(a)) { isInfiniteFlag = true; break; }
                }

                if (isInfiniteFlag) {
                    // Infinite mode: skip finish zone — save start template and jump to naming
                    session.setInfinite(true);
                    Location setupMin = session.getIslandMin();
                    Location setupMax = session.getIslandMax();
                    if (setupMin == null || setupMax == null) {
                        msg(player, "&cIsland selection is missing. Restart setup.");
                        return;
                    }
                    String tempName = "setup_" + player.getUniqueId().toString().substring(0, 8);
                    boolean saved = plugin.getFawePaster().saveTemplate(
                            setupMin.getWorld(),
                            setupMin.getBlockX(), setupMin.getBlockY(), setupMin.getBlockZ(),
                            setupMax.getBlockX(), setupMax.getBlockY(), setupMax.getBlockZ(),
                            tempName);
                    if (!saved) {
                        msg(player, "&cFailed to save island template. Check console for errors.");
                        return;
                    }
                    session.advanceToName();
                    player.sendMessage(ColorUtil.translate(prefix + "&aHologram location saved! Infinite mode enabled."));
                    player.sendMessage(ColorUtil.translate("&7No finish zone required for infinite maps."));
                    player.sendMessage(ColorUtil.translate("&e&lFinal Step: &fName your map:"));
                    sendClickableFinish(player);

                } else if (isCLMode) {
                    // Custom-length mode: save start island template, then select the end island
                    Location setupMin = session.getIslandMin();
                    Location setupMax = session.getIslandMax();
                    if (setupMin == null || setupMax == null) {
                        msg(player, "&cIsland selection is missing. Restart setup.");
                        return;
                    }
                    String tempName = "setup_" + player.getUniqueId().toString().substring(0, 8);
                    boolean saved = plugin.getFawePaster().saveTemplate(
                            setupMin.getWorld(),
                            setupMin.getBlockX(), setupMin.getBlockY(), setupMin.getBlockZ(),
                            setupMax.getBlockX(), setupMax.getBlockY(), setupMax.getBlockZ(),
                            tempName);
                    if (!saved) {
                        msg(player, "&cFailed to save start island template. Check console for errors.");
                        return;
                    }
                    session.advanceToEndIsland();
                    player.sendMessage(ColorUtil.translate(prefix + "&aHologram location saved!"));
                    player.sendMessage(ColorUtil.translate("&e&lStep 5 (Custom Length): &fSelect your &bEnd Island &7region."));
                    player.sendMessage(ColorUtil.translate("&7  Build the End Island somewhere to the &c+X &7side of the Start Island."));
                    player.sendMessage(ColorUtil.translate("&7  &c&lLeft-click &fthe blaze rod to set &bEnd Pos 1 &7(one corner)."));
                    player.sendMessage(ColorUtil.translate("&7  &c&lRight-click &fthe blaze rod to set &bEnd Pos 2 &7(opposite corner)."));
                    sendClickableContinue(player);

                } else {
                    session.advanceToFinish();
                    player.sendMessage(ColorUtil.translate(prefix + "&aHologram location saved!"));
                    player.sendMessage(ColorUtil.translate("&e&lStep 5: &fSelect the finish zone (any blocks can be the finish area)."));
                    player.sendMessage(ColorUtil.translate("&7  &c&lLeft-click &fthe blaze rod to set &bFinish Pos 1."));
                    player.sendMessage(ColorUtil.translate("&7  &c&lRight-click &fthe blaze rod to set &bFinish Pos 2."));
                    player.sendMessage(ColorUtil.translate("&7  When both corners are set, type:"));
                    sendClickableFinish(player);
                }
                break;
            }
            case SELECTING_END_ISLAND: {
                // Save the end island template and finalize into AWAITING_NAME
                Location endMin = session.getEndIslandMin();
                Location endMax = session.getEndIslandMax();
                if (endMin == null || endMax == null) {
                    msgAdmin(player, "setup-not-ready");
                    return;
                }
                String endTempName = "setup_" + player.getUniqueId().toString().substring(0, 8) + "_end";
                boolean savedEnd = plugin.getFawePaster().saveTemplate(
                        endMin.getWorld(),
                        endMin.getBlockX(), endMin.getBlockY(), endMin.getBlockZ(),
                        endMax.getBlockX(), endMax.getBlockY(), endMax.getBlockZ(),
                        endTempName);
                if (!savedEnd) {
                    msg(player, "&cFailed to save end island template. Check console for errors.");
                    return;
                }
                int baseLen = session.getBaseCustomLength();
                if (baseLen <= 0) {
                    msg(player, "&cEnd Island must be placed to the &c+X &cside of the Start Island spawn. Base distance is " + baseLen + " — check placement.");
                    return;
                }
                session.finalizeEndIsland();
                player.sendMessage(ColorUtil.translate(prefix + "&aEnd Island saved! Base distance: &f" + baseLen + " &ablocks."));
                player.sendMessage(ColorUtil.translate("&e&lFinal Step: &fName your map:"));
                sendClickableFinish(player);
                break;
            }
            default:
                msgAdmin(player, "setup-not-ready");
                break;
        }
    }

    /**
     * Combined /map setup finish <name>:
     * - For normal maps: finalizes the finish zone selection AND names/creates the map.
     * - For infinite maps: session is already at AWAITING_NAME, so just names/creates the map.
     */
    private void handleSetupFinishWithName(Player player, String name, MapManager mm) {
        SetupSession session = mm.getSetupSession(player.getUniqueId());
        if (session == null) {
            msgAdmin(player, "setup-no-session");
            return;
        }

        // For non-infinite maps that haven't finalized the finish zone yet, do so now.
        if (session.getState() == SetupSession.State.SELECTING_FINISH) {
            if (!session.canFinalize()) {
                msgAdmin(player, "setup-not-ready");
                return;
            }
            session.finalize_();

            // Save the template
            Location min = session.getIslandMin();
            Location max = session.getIslandMax();
            String tempName = "setup_" + player.getUniqueId().toString().substring(0, 8);
            boolean saved = plugin.getFawePaster().saveTemplate(
                    min.getWorld(),
                    min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                    max.getBlockX(), max.getBlockY(), max.getBlockZ(),
                    tempName);
            if (!saved) {
                msg(player, "&cFailed to save island template. Check console for errors.");
                return;
            }
        }

        // At this point the session must be AWAITING_NAME
        if (session.getState() != SetupSession.State.AWAITING_NAME) {
            msgAdmin(player, "setup-not-ready");
            return;
        }

        if (mm.mapExists(name)) {
            msgAdmin(player, "map-already-exists");
            return;
        }

        // Rename start template to final name
        String tempName = "setup_" + player.getUniqueId().toString().substring(0, 8);
        java.io.File tempFile = new java.io.File(plugin.getFawePaster().getTemplatesDir(), tempName + ".schematic");
        java.io.File finalFile = new java.io.File(plugin.getFawePaster().getTemplatesDir(), name.toLowerCase() + ".schematic");
        if (tempFile.exists()) {
            tempFile.renameTo(finalFile);
        }

        // Create map from grid
        MapData map = mm.createMap(session, name);
        map.setTemplateFile(name.toLowerCase());
        if (session.isInfinite()) {
            map.setInfinite(true);
        }

        // Compute physical island length so distance == visual gap from day one
        if (!session.isInfinite() && plugin.getFawePaster() != null) {
            int physLen = computePhysicalIslandLength(name.toLowerCase());
            if (physLen > 0) {
                map.setPhysicalIslandLength(physLen);
            }
        }

        // Custom-length mode: rename end island template and store end-island metadata
        if (session.isCustomLengthMode()) {
            String endTempName = "setup_" + player.getUniqueId().toString().substring(0, 8) + "_end";
            java.io.File endTempFile = new java.io.File(plugin.getFawePaster().getTemplatesDir(), endTempName + ".schematic");
            String endFinalKey = name.toLowerCase() + "_end";
            java.io.File endFinalFile = new java.io.File(plugin.getFawePaster().getTemplatesDir(), endFinalKey + ".schematic");
            if (endTempFile.exists()) endTempFile.renameTo(endFinalFile);

            map.setEndIslandTemplateFile(endFinalKey);
            map.setEndIslandWidth(session.getEndIslandWidth());
            map.setEndIslandHeight(session.getEndIslandHeight());
            map.setEndIslandLength(session.getEndIslandLength());
            map.setBaseCustomLength(session.getBaseCustomLength());
            map.setEndIslandYOffset(session.getEndIslandYOffset());
            map.setEndIslandZOffset(session.getEndIslandZOffset());

            String prefix2 = plugin.getConfigManager().getPrefix();
            player.sendMessage(ColorUtil.translate(prefix2 + "&7End Island: &f"
                    + session.getEndIslandWidth() + "×" + session.getEndIslandHeight() + "×" + session.getEndIslandLength()
                    + " &7— base distance: &f" + session.getBaseCustomLength() + " blocks"));
        }

        mm.saveMap(map);

        // Paste initial islands
        mm.pasteInitialIsland(map);

        // Clear the start island build area
        Location setupMin = session.getIslandMin();
        Location setupMax = session.getIslandMax();
        if (setupMin != null && setupMax != null) {
            plugin.getFawePaster().clearRegion(
                    setupMin.getWorld(),
                    setupMin.getBlockX(), setupMin.getBlockY(), setupMin.getBlockZ(),
                    setupMax.getBlockX(), setupMax.getBlockY(), setupMax.getBlockZ(),
                    null);
        }

        // Clear the end island build area if in custom-length mode
        if (session.isCustomLengthMode()) {
            Location endMin = session.getEndIslandMin();
            Location endMax = session.getEndIslandMax();
            if (endMin != null && endMax != null) {
                plugin.getFawePaster().clearRegion(
                        endMin.getWorld(),
                        endMin.getBlockX(), endMin.getBlockY(), endMin.getBlockZ(),
                        endMax.getBlockX(), endMax.getBlockY(), endMax.getBlockZ(),
                        null);
            }
        }

        mm.removeSetupSession(player.getUniqueId());

        String raw = plugin.getConfigManager().getAdminMessage("setup-complete");
        raw = raw.replace("%map%", name)
                 .replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));

        // Post-creation interactive hints
        sendPostCreationHints(player, name);

        // Return player to their island
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.getInventory().clear();
        net.gravijet.fastbuilder.player.PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (pData != null && pData.getLastMap() != null) {
            MapData pMap = mm.getMap(pData.getLastMap());
            if (pMap != null && pMap.isEnabled()) {
                int isl = pData.getLastIsland();
                if (isl >= 0) {
                    player.teleport(pMap.getIslandSpawn(isl));
                    if (plugin.getGameplayManager() != null) {
                        net.gravijet.fastbuilder.gameplay.RunSession retSess =
                                plugin.getGameplayManager().createSession(player.getUniqueId(), pMap.getName(), isl);
                        if (pMap.hasEndIsland()) {
                            plugin.getGameplayManager().placeEndPlatform(player, pMap, retSess, pMap.getBaseCustomLength());
                        }
                        plugin.getGameplayManager().applyPlayerDesign(player, pMap, isl);
                    }
                    if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
                    plugin.getScoreboardManager().createScoreboard(player);
                    if (plugin.getNpcManager() != null) plugin.getNpcManager().spawnNpc(player, pMap.getIslandNpcLocation(isl));
                    if (plugin.getHologramManager() != null) plugin.getHologramManager().updateHologram(pMap.getName(), isl, player);
                    return;
                }
            }
        }
        mm.relocatePlayer(player, "");
    }

    // --- /map setup cancel ---

    private void handleSetupCancel(Player player, MapManager mm) {
        String prefix = plugin.getConfigManager().getPrefix();
        if (!mm.hasSetupSession(player.getUniqueId())) {
            msg(player, prefix + "&cNo active setup session to cancel.");
            return;
        }
        mm.removeSetupSession(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.getInventory().clear();
        msg(player, prefix + "&cSetup cancelled.");
        mm.relocatePlayer(player, "");
    }

    // --- /map info <map> ---

    private void handleInfo(Player player, String[] args, MapManager mm) {
        if (!player.hasPermission("fastbuilder.command.map.info")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length < 2) {
            msg(player, plugin.getConfigManager().getPrefix() + "&cUsage: &f/map info <map>");
            return;
        }
        MapData map = mm.getMap(args[1]);
        if (map == null) {
            msgMap(player, "map-not-found", args[1]);
            return;
        }
        String prefix = plugin.getConfigManager().getPrefix();
        int occupied = mm.getOccupiedCount(map.getName());
        int total = mm.getIslands(map.getName()).size();
        player.sendMessage(ColorUtil.translate(prefix + "&c&l" + map.getName() + " &8» &fMap Info"));
        player.sendMessage(ColorUtil.translate("  &8» &cEnabled: &f" + map.isEnabled()));
        player.sendMessage(ColorUtil.translate("  &8» &cIslands: &f" + occupied + "/" + total
                + " &7(autoscale: " + map.isAutoscale() + ")"));
        player.sendMessage(ColorUtil.translate("  &8» &cIsland gap: &f" + map.getDistance()
                + " blocks &7(total Z step: " + map.getActualZStep() + ")"));
        player.sendMessage(ColorUtil.translate("  &8» &cInfinite: &f" + map.isInfinite()));
        // Custom length status — differentiate end-island mode from legacy bounds mode
        if (map.hasEndIsland()) {
            player.sendMessage(ColorUtil.translate("  &8» &cCustom Length: &aEnd Island mode"
                    + " &7(base: &f" + map.getBaseCustomLength() + " &7blocks"
                    + ", range: &f" + map.getEffectiveMinCustomLength()
                    + "-" + map.getEffectiveMaxCustomLength() + "&7)"));
        } else if (map.hasCustomLength()) {
            player.sendMessage(ColorUtil.translate("  &8» &cCustom Length: &fenabled"
                    + " &7(range: &f" + map.getMinCustomLength()
                    + "-" + map.getMaxCustomLength() + " blocks&7)"));
        } else {
            player.sendMessage(ColorUtil.translate("  &8» &cCustom Length: &7disabled"));
        }
        player.sendMessage(ColorUtil.translate("  &8» &cOrigin: &f"
                + map.getOriginX() + ", " + map.getOriginY() + ", " + map.getOriginZ()
                + " &7(" + map.getWorldName() + ")"));
        player.sendMessage(ColorUtil.translate("  &8» &cIsland size: &f"
                + map.getIslandWidth() + "×" + map.getIslandHeight() + "×" + map.getIslandLength()));
        player.sendMessage(ColorUtil.translate("  &8» &cTemplate: &f" + map.getTemplateFile()));
        if (map.getEndIslandTemplateFile() != null && !map.getEndIslandTemplateFile().isEmpty()) {
            player.sendMessage(ColorUtil.translate("  &8» &cEnd Island: &f" + map.getEndIslandTemplateFile()
                    + " &7(" + map.getEndIslandWidth() + "×" + map.getEndIslandHeight()
                    + "×" + map.getEndIslandLength() + ")"));
        }
        if (!map.getAlternativeTemplates().isEmpty()) {
            player.sendMessage(ColorUtil.translate("  &8» &cDesigns: &f" + map.getAlternativeTemplates().size() + " alternative(s)"));
        }
        if (map.getDiamondTime() > 0) player.sendMessage(ColorUtil.translate("  &8» &bDiamond: &f" + net.gravijet.fastbuilder.util.TimeUtil.formatTime(map.getDiamondTime())));
        if (map.getGoldTime() > 0)    player.sendMessage(ColorUtil.translate("  &8» &6Gold: &f" + net.gravijet.fastbuilder.util.TimeUtil.formatTime(map.getGoldTime())));
        if (map.getSilverTime() > 0)  player.sendMessage(ColorUtil.translate("  &8» &7Silver: &f" + net.gravijet.fastbuilder.util.TimeUtil.formatTime(map.getSilverTime())));
        if (map.getBronzeTime() > 0)  player.sendMessage(ColorUtil.translate("  &8» &cBronze: &f" + net.gravijet.fastbuilder.util.TimeUtil.formatTime(map.getBronzeTime())));
    }

    // --- /map rename <newname> (renames the map the player is on, or targeted)  ---

    private void handleRename(Player player, String[] args, MapManager mm) {
        if (!player.hasPermission("fastbuilder.command.map.rename")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length < 3) {
            msgAdmin(player, "usage", "%command%", "/map rename <old> <new>");
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
        raw = raw.replace("%old%", oldName).replace("%new%", newName)
                 .replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
    }

    // --- /map regen <map> ---

    private void handleRegen(Player player, String[] args, MapManager mm) {
        if (!player.hasPermission("fastbuilder.command.map.regen")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length < 2) {
            msgAdmin(player, "usage", "%command%", "/map regen <map>");
            return;
        }

        MapData map = mm.getMap(args[1]);
        if (map == null) {
            msgMap(player, "map-not-found", args[1]);
            return;
        }

        String prefix = plugin.getConfigManager().getPrefix();
        player.sendMessage(ColorUtil.translate(prefix + "&eRegenerating all " + map.getScale()
                + " island(s) for &c" + map.getName() + "&e. Please wait..."));

        // Re-paste all islands at current positions (oldActualStep == newActualStep — pure regen)
        mm.regenerateIslands(map, map.getActualZStep());

        player.sendMessage(ColorUtil.translate(prefix + "&aRegeneration started for &c" + map.getName() + "&a."));
    }

    // --- /map setname <old> <new> (kept for backward compat) ---

    private void handleSetName(Player player, String[] args, MapManager mm) {
        // Delegate to rename logic
        handleRename(player, args, mm);
    }

    // --- /map seticon <map> <MATERIAL:DATA> ---

    @SuppressWarnings("deprecation")
    private void handleSetIcon(Player player, String[] args, MapManager mm) {
        if (args.length < 2) {
            msgAdmin(player, "usage", "%command%", "/map seticon <map> [MATERIAL:DATA]");
            return;
        }

        MapData map = mm.getMap(args[1]);
        if (map == null) {
            msgMap(player, "map-not-found", args[1]);
            return;
        }

        String icon;
        if (args.length < 3) {
            ItemStack held = player.getItemInHand();
            if (held == null || held.getType() == Material.AIR) {
                msgAdmin(player, "usage", "%command%", "/map seticon <map> [MATERIAL:DATA]");
                return;
            }
            icon = held.getType().name() + ":" + held.getDurability();
        } else {
            icon = args[2].toUpperCase();
        }
        // Validate material
        String matName = icon.contains(":") ? icon.split(":")[0] : icon;
        if (Material.matchMaterial(matName) == null) {
            msg(player, "&cInvalid material: &f" + matName);
            return;
        }

        map.setIcon(icon);
        mm.saveMap(map);

        String raw = plugin.getConfigManager().getAdminMessage("map-icon-set");
        raw = raw.replace("%map%", map.getName()).replace("%icon%", icon)
                 .replace("%prefix%", plugin.getConfigManager().getPrefix());
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
        raw = raw.replace("%map%", map.getName())
                 .replace("%prefix%", plugin.getConfigManager().getPrefix());
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
        raw = raw.replace("%map%", map.getName())
                 .replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
    }

    // --- /map delete <map> [confirm] ---

    private void handleDelete(Player player, String[] args, MapManager mm) {
        if (!player.hasPermission("fastbuilder.command.map.delete")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length < 2) {
            msg(player, plugin.getConfigManager().getPrefix() + "&cUsage: &f/map delete <map>");
            return;
        }

        MapData map = mm.getMap(args[1]);
        if (map == null) {
            msgMap(player, "map-not-found", args[1]);
            return;
        }

        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            String prefix = plugin.getConfigManager().getPrefix();
            player.sendMessage(ColorUtil.translate(prefix
                    + "&cYou are about to permanently delete map &f" + map.getName()
                    + "&c. This cannot be undone!"));
            sendDeleteConfirm(player, map.getName());
            return;
        }

        String name = map.getName();
        mm.deleteMap(name);
        msg(player, plugin.getConfigManager().getPrefix() + "&aMap &c" + name + " &adeleted.");
    }

    private void sendDeleteConfirm(Player player, String mapName) {
        net.md_5.bungee.api.chat.TextComponent line =
                new net.md_5.bungee.api.chat.TextComponent(
                        ColorUtil.translate("&7Click to confirm: "));
        net.md_5.bungee.api.chat.TextComponent btn =
                new net.md_5.bungee.api.chat.TextComponent(
                        ColorUtil.translate("&c&l[Delete " + mapName + "]"));
        btn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                "/map delete " + mapName + " confirm"));
        btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.BaseComponent[]{
                        new net.md_5.bungee.api.chat.TextComponent(
                                ColorUtil.translate("&cClick to permanently delete &f" + mapName))}));
        line.addExtra(btn);
        player.spigot().sendMessage(line);
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
        raw = raw.replace("%map%", map.getName()).replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));

        mm.updateScale(map, count);

        raw = plugin.getConfigManager().getAdminMessage("map-scale-set");
        raw = raw.replace("%map%", map.getName()).replace("%scale%", String.valueOf(count))
                .replace("%prefix%", plugin.getConfigManager().getPrefix());
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

        int requestedGap = Math.max(1, blocks);

        // Capture actual step BEFORE any changes (used to detect whether repaste is needed)
        int oldActualZStep = map.getActualZStep();

        // If physicalIslandLength hasn't been computed yet for this map, do it now by
        // scanning the template. This one-time scan ensures distance == visual gap.
        if (map.getPhysicalIslandLength() <= 0 && map.getTemplateFile() != null
                && plugin.getFawePaster() != null) {
            int physLen = computePhysicalIslandLength(map.getTemplateFile());
            if (physLen > 0) {
                map.setPhysicalIslandLength(physLen);
            }
        }

        map.setDistance(requestedGap);
        mm.saveMap(map);

        String raw = plugin.getConfigManager().getAdminMessage("map-distance-set");
        raw = raw.replace("%map%", map.getName()).replace("%distance%", String.valueOf(requestedGap))
                .replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));

        int newActualZStep = map.getActualZStep();
        if (newActualZStep != oldActualZStep && map.getScale() > 0) {
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                    + "&eRegenerating " + map.getScale() + " island(s) at new distance. Please wait..."));
            mm.regenerateIslands(map, oldActualZStep);
        }
    }

    /** Scan a template file and return the Z span of its non-air blocks. Returns 0 on failure. */
    private int computePhysicalIslandLength(String templateFile) {
        java.util.List<net.gravijet.fastbuilder.paste.FawePaster.BlockEntry> entries =
                plugin.getFawePaster().loadTemplate(templateFile);
        if (entries == null || entries.isEmpty()) return 0;
        int minRelZ = Integer.MAX_VALUE, maxRelZ = Integer.MIN_VALUE;
        for (net.gravijet.fastbuilder.paste.FawePaster.BlockEntry e : entries) {
            if (e.relZ < minRelZ) minRelZ = e.relZ;
            if (e.relZ > maxRelZ) maxRelZ = e.relZ;
        }
        return (minRelZ <= maxRelZ) ? maxRelZ - minRelZ + 1 : 0;
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
        raw = raw.replace("%map%", map.getName()).replace("%value%", String.valueOf(value))
                 .replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
    }

    // --- /map setdeathy <map> <Y> ---

    private void handleSetDeathY(Player player, String[] args, MapManager mm) {
        if (args.length < 3) {
            msgAdmin(player, "usage", "%command%", "/map setdeathy <map> <yLevel>");
            player.sendMessage(ColorUtil.translate("&7Sets the Y-level at which players die and get reset. Use -1 to remove."));
            return;
        }

        MapData map = mm.getMap(args[1]);
        if (map == null) {
            msgMap(player, "map-not-found", args[1]);
            return;
        }

        int yLevel;
        try {
            yLevel = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            msg(player, "&cInvalid Y level: &f" + args[2]);
            return;
        }

        if (yLevel < 0) {
            map.setDeathY(Integer.MIN_VALUE);
            mm.saveMap(map);
            msg(player, plugin.getConfigManager().getPrefix() + "&aFall death height removed for map &c" + map.getName() + "&a.");
        } else {
            map.setDeathY(yLevel);
            mm.saveMap(map);
            msg(player, plugin.getConfigManager().getPrefix() + "&aFall death height set to Y=&c" + yLevel + " &afor map &c" + map.getName() + "&a.");
        }
    }

    // --- /map setmintime <map> <ms> ---

    private void handleSetMinTime(Player player, String[] args, MapManager mm) {
        if (args.length < 3) {
            msg(player, plugin.getConfigManager().getPrefix() + "&cUsage: &f/map setmintime <map> <ms> &7(0 to use global)");
            return;
        }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msgMap(player, "map-not-found", args[1]); return; }

        long ms;
        try { ms = Long.parseLong(args[2]); } catch (NumberFormatException e) {
            msg(player, "&cInvalid number: &f" + args[2]); return;
        }

        map.setMinValidTime(Math.max(0, ms));
        mm.saveMap(map);
        msg(player, plugin.getConfigManager().getPrefix() + "&fMin valid time for &c" + map.getName()
                + " &fset to &c" + ms + "ms" + (ms <= 0 ? " &7(uses global)" : "") + "&f.");
    }

    // --- /map setmaxtime <map> <ms> ---

    private void handleSetMaxTime(Player player, String[] args, MapManager mm) {
        if (args.length < 3) {
            msg(player, plugin.getConfigManager().getPrefix() + "&cUsage: &f/map setmaxtime <map> <ms> &7(0 to disable)");
            return;
        }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msgMap(player, "map-not-found", args[1]); return; }

        long ms;
        try { ms = Long.parseLong(args[2]); } catch (NumberFormatException e) {
            msg(player, "&cInvalid number: &f" + args[2]); return;
        }

        map.setMaxCompletionTime(Math.max(0, ms));
        mm.saveMap(map);
        msg(player, plugin.getConfigManager().getPrefix() + "&fMax completion time for &c" + map.getName()
                + " &fset to &c" + (ms <= 0 ? "disabled" : ms + "ms") + "&f.");
    }

    // --- /map setrank <map> <gold|silver|bronze> <ms> ---

    private void handleSetRank(Player player, String[] args, MapManager mm) {
        if (args.length < 4) {
            msg(player, plugin.getConfigManager().getPrefix() + "&cUsage: &f/map setrank <map> <diamond|gold|silver|bronze> <ms> &7(-1 to remove)");
            return;
        }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msgMap(player, "map-not-found", args[1]); return; }

        String tier = args[2].toLowerCase();
        long ms;
        try { ms = Long.parseLong(args[3]); } catch (NumberFormatException e) {
            msg(player, "&cInvalid number: &f" + args[3]); return;
        }

        switch (tier) {
            case "diamond": map.setDiamondTime(ms); break;
            case "gold":    map.setGoldTime(ms);    break;
            case "silver":  map.setSilverTime(ms);  break;
            case "bronze":  map.setBronzeTime(ms);  break;
            default:
                msg(player, "&cInvalid rank tier. Use: diamond, gold, silver, bronze"); return;
        }
        mm.saveMap(map);
        String display = ms <= 0 ? "removed" : ms + "ms";
        msg(player, plugin.getConfigManager().getPrefix() + "&f" + capitalize(tier) + " rank time for &c" + map.getName() + " &fset to &c" + display + "&f.");
    }

    // --- /map adddesign <map> ---
    // Saves the current setup area template as an alternative design for the map.

    private void handleAddDesign(Player player, String[] args, MapManager mm) {
        if (args.length < 2) {
            msg(player, plugin.getConfigManager().getPrefix()
                    + "&cUsage: &f/map adddesign <map> [<templateKey>]");
            return;
        }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msgMap(player, "map-not-found", args[1]); return; }

        String prefix = plugin.getConfigManager().getPrefix();
        // Save current setup area as a uniquely-named template file
        String designKey = map.getName().toLowerCase() + "_design_" + (map.getAllTemplates().size());

        // Check if admin is in setup mode with a valid selection
        net.gravijet.fastbuilder.map.SetupSession session =
                mm.getSetupSession(player.getUniqueId());
        if (session != null && session.getIslandMin() != null && session.getIslandMax() != null) {
            Location min = session.getIslandMin();
            Location max = session.getIslandMax();
            boolean saved = plugin.getFawePaster().saveTemplate(
                    min.getWorld(),
                    min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                    max.getBlockX(), max.getBlockY(), max.getBlockZ(),
                    designKey);
            if (!saved) {
                msg(player, prefix + "&cFailed to save design template. Check console for errors.");
                return;
            }
        } else {
            // No active setup session — use the name provided as args[2] if given
            if (args.length < 3) {
                msg(player, prefix + "&cNo active setup selection found. "
                        + "&fUse the setup wizard to select an area, or provide a template key: "
                        + "&c/map adddesign <map> <existingTemplateKey>");
                return;
            }
            designKey = args[2];
        }

        map.addAlternativeTemplate(designKey);
        mm.saveMap(map);
        msg(player, prefix + "&fAlternative design &c" + designKey + " &fadded to map &c" + map.getName() + "&f. "
                + "&7(" + (map.getAllTemplates().size() - 1) + " alternative(s) total)");
    }

    // --- /map removedesign <map> <templateKey> ---

    private void handleRemoveDesign(Player player, String[] args, MapManager mm) {
        if (args.length < 3) {
            msg(player, plugin.getConfigManager().getPrefix()
                    + "&cUsage: &f/map removedesign <map> <templateKey>");
            return;
        }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msgMap(player, "map-not-found", args[1]); return; }

        String key = args[2];
        String prefix = plugin.getConfigManager().getPrefix();
        if (map.removeAlternativeTemplate(key)) {
            mm.saveMap(map);
            msg(player, prefix + "&fDesign &c" + key + " &fremoved from map &c" + map.getName() + "&f.");
        } else {
            msg(player, prefix + "&cDesign &f" + key + " &cnot found for map &f" + map.getName()
                    + "&c. Available alternatives: &f" + map.getAlternativeTemplates());
        }
    }

    // --- /map setdesignmeta <map> <template> ---

    /**
     * Saves the spawn/finish/dimension profile for an alternative island design.
     *
     * If the admin has an active setup session (started via /map setup) with a spawn
     * and finish zone already recorded, those values are used directly.
     *
     * Otherwise the command falls back to the player's current stand position as
     * the design's spawn point (no finish zone override — map defaults apply).
     */
    private void handleSetDesignMeta(Player player, String[] args, MapManager mm) {
        if (!player.hasPermission("fastbuilder.admin")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length < 3) {
            msg(player, plugin.getConfigManager().getPrefix()
                    + "&cUsage: &f/map setdesignmeta <map> <template>");
            return;
        }

        MapData map = mm.getMap(args[1]);
        if (map == null) { msgMap(player, "map-not-found", args[1]); return; }

        String templateKey = args[2];
        if (!map.getAllTemplates().contains(templateKey)) {
            msg(player, plugin.getConfigManager().getPrefix()
                    + "&cTemplate &f" + templateKey + " &cnot found for map &f" + map.getName()
                    + "&c.  Available: &f" + map.getAllTemplates());
            return;
        }

        String prefix = plugin.getConfigManager().getPrefix();
        net.gravijet.fastbuilder.map.SetupSession session = mm.getSetupSession(player.getUniqueId());

        MapData.DesignProfile profile = new MapData.DesignProfile();

        if (session != null
                && session.getIslandMin() != null
                && session.getSpawnPoint() != null) {
            // Use the full data captured during the setup wizard
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

            msg(player, prefix + "&fDesign profile saved for &c" + templateKey
                    + " &fon &c" + map.getName() + "&f (from active setup session).");
            msg(player, "&7Spawn offset: +" + String.format("%.1f", profile.spawnOffsetX)
                    + "x, +" + String.format("%.1f", profile.spawnOffsetY)
                    + "y, +" + String.format("%.1f", profile.spawnOffsetZ) + "z");
            if (profile.hasFinishZone()) {
                msg(player, "&7Finish zone X: " + profile.finishMinX + " → " + profile.finishMaxX
                        + ", Y: " + profile.finishMinY + " → " + profile.finishMaxY);
            }
            msg(player, "&7Island: " + profile.islandWidth + "w × " + profile.islandLength + "l");
        } else {
            // Fallback: use player's current position as spawn, map defaults for finish
            int playerIsland = mm.getPlayerIsland(map.getName(), player.getUniqueId());
            if (playerIsland < 0) {
                msg(player, prefix + "&cYou must be on an island of this map, "
                        + "or have an active setup session with spawn set.  "
                        + "Run &f/map setup &cand complete through Step 2 (spawn) first.");
                return;
            }

            Location islandMin   = map.getIslandMin(playerIsland);
            Location playerLoc   = player.getLocation();

            profile.spawnOffsetX = playerLoc.getX() - islandMin.getBlockX();
            profile.spawnOffsetY = playerLoc.getY() - islandMin.getBlockY();
            profile.spawnOffsetZ = playerLoc.getZ() - islandMin.getBlockZ();
            profile.spawnYaw     = playerLoc.getYaw();
            profile.spawnPitch   = playerLoc.getPitch();
            // Finish zone left at 0 → hasFinishZone() returns false → map defaults apply
            profile.islandWidth  = map.getIslandWidth();
            profile.islandHeight = map.getIslandHeight();
            profile.islandLength = map.getIslandLength();

            map.setDesignProfile(templateKey, profile);
            mm.saveMap(map);

            msg(player, prefix + "&fDesign spawn saved for &c" + templateKey
                    + " &fon &c" + map.getName()
                    + " &7(finish zone uses map defaults — run from inside a setup wizard to also save a custom finish zone).");
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // --- Clickable Setup Prompts ---

    private void sendClickableContinue(Player player) {
        if (!plugin.getConfigManager().isAdminHintsEnabled()) return;
        String prefix = plugin.getConfigManager().getPrefix();
        net.md_5.bungee.api.chat.TextComponent msg = new net.md_5.bungee.api.chat.TextComponent(
                ColorUtil.translate(prefix + "&e[Click] &r&f/map setup continue"));
        // SUGGEST_COMMAND: fills the chat bar so admin can review/modify before sending
        msg.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND,
                "/map setup continue"));
        msg.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.BaseComponent[]{
                        new net.md_5.bungee.api.chat.TextComponent(
                                ColorUtil.translate("&7Click to fill the command in your chat bar"))}));
        player.spigot().sendMessage(msg);
    }

    private void sendClickableFinish(Player player) {
        if (!plugin.getConfigManager().isAdminHintsEnabled()) return;
        net.md_5.bungee.api.chat.TextComponent msg = new net.md_5.bungee.api.chat.TextComponent(
                ColorUtil.translate("&f/map setup finish <name>"));
        msg.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND,
                "/map setup finish "));
        msg.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.BaseComponent[]{
                        new net.md_5.bungee.api.chat.TextComponent(
                                ColorUtil.translate("&7Click to fill — then type the map name and press Enter"))}));
        player.spigot().sendMessage(msg);
    }

    /**
     * Send interactive post-creation hints after a map is successfully saved.
     * Each hint uses SUGGEST_COMMAND so the admin can review/edit before executing.
     */
    private void sendPostCreationHints(Player player, String mapName) {
        if (!plugin.getConfigManager().isAdminHintsEnabled()) return;
        String prefix = plugin.getConfigManager().getPrefix();
        player.sendMessage(ColorUtil.translate(prefix + "&aMap &c" + mapName + " &acreated! Next steps:"));
        sendSuggestHint(player, "&7Enable the map: ", "/map enable " + mapName);
        sendSuggestHint(player, "&7Set island count: ", "/map scale " + mapName + " 15");
        sendSuggestHint(player, "&7Test it: ", "/fb join " + mapName);
    }

    private void sendSuggestHint(Player player, String label, String command) {
        net.md_5.bungee.api.chat.TextComponent line =
                new net.md_5.bungee.api.chat.TextComponent(ColorUtil.translate("  &8» " + label));
        net.md_5.bungee.api.chat.TextComponent cmd =
                new net.md_5.bungee.api.chat.TextComponent(ColorUtil.translate("&e&n" + command));
        cmd.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND, command));
        cmd.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.BaseComponent[]{
                        new net.md_5.bungee.api.chat.TextComponent(
                                ColorUtil.translate("&7Click to fill this command in your chat bar"))}));
        line.addExtra(cmd);
        player.spigot().sendMessage(line);
    }

    // --- Paginated Help (/map help [page]) ---

    private void handleHelp(Player player, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }

        List<String> lines = buildHelpLines(player);
        if (lines.isEmpty()) {
            msg(player, plugin.getConfigManager().getPrefix() + "&cNo commands available.");
            return;
        }
        int totalPages = Math.max(1, (int) Math.ceil(lines.size() / (double) HELP_PAGE_SIZE));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int start = (page - 1) * HELP_PAGE_SIZE;
        int end   = Math.min(start + HELP_PAGE_SIZE, lines.size());

        // Print entries first, pagination footer at the bottom
        for (int i = start; i < end; i++) {
            player.sendMessage(ColorUtil.translate(lines.get(i)));
        }

        // Build pagination footer: [&c<] Showing page X of Y [&a>]
        net.md_5.bungee.api.chat.TextComponent footer = new net.md_5.bungee.api.chat.TextComponent("");

        if (page > 1) {
            net.md_5.bungee.api.chat.TextComponent prev =
                    new net.md_5.bungee.api.chat.TextComponent(ColorUtil.translate("&c\u00AB "));
            prev.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                    "/map help " + (page - 1)));
            prev.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.BaseComponent[]{
                            new net.md_5.bungee.api.chat.TextComponent(
                                    ColorUtil.translate("&7Previous page"))}));
            footer.addExtra(prev);
        }

        footer.addExtra(new net.md_5.bungee.api.chat.TextComponent(
                ColorUtil.translate("&8Page &c" + page + " &8/ &c" + totalPages
                        + " &8(&7" + lines.size() + " entries&8)")));

        if (page < totalPages) {
            net.md_5.bungee.api.chat.TextComponent next =
                    new net.md_5.bungee.api.chat.TextComponent(ColorUtil.translate(" &a\u00BB"));
            next.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                    "/map help " + (page + 1)));
            next.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.BaseComponent[]{
                            new net.md_5.bungee.api.chat.TextComponent(
                                    ColorUtil.translate("&7Next page"))}));
            footer.addExtra(next);
        }

        player.spigot().sendMessage(footer);
    }

    /** Build help entries, filtered to commands this player has permission for. */
    private List<String> buildHelpLines(Player player) {
        // [permission, command, description]
        Object[][] all = {
            {"fastbuilder.play",                  "/map list",                               "List all available maps."},
            {"fastbuilder.command.map.setup",     "/map setup",                              "Start the map setup wizard."},
            {"fastbuilder.command.map.setup",     "/map setup --infinite",                   "Start setup for an infinite map."},
            {"fastbuilder.command.map.setup",     "/map setup --customlength",               "Start setup with a custom-length end island."},
            {"fastbuilder.command.map.setup",     "/map setup continue",                     "Advance to the next setup step."},
            {"fastbuilder.command.map.setup",     "/map setup finish <name>",                "Finalize and name the map."},
            {"fastbuilder.command.map.setup",     "/map setup cancel",                       "Cancel an active setup session."},
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
            String cmd  = (String) entry[1];
            String desc = (String) entry[2];
            if (player.hasPermission("fastbuilder.admin") || player.hasPermission(perm)) {
                lines.add("&c● " + cmd + " &8» &f" + desc);
            }
        }
        return lines;
    }

    // --- /map list ---

    private void handleList(Player player, MapManager mm) {
        if (!player.hasPermission("fastbuilder.play")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        List<MapData> enabledMaps = new ArrayList<>();
        for (MapData map : mm.getAllMaps()) {
            if (map.isEnabled()) enabledMaps.add(map);
        }

        player.sendMessage(ColorUtil.translate("&c&lFastBuilder &8» &fAvailable Maps"));
        player.sendMessage(ColorUtil.translate("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));

        if (enabledMaps.isEmpty()) {
            player.sendMessage(ColorUtil.translate("&7No maps are currently available."));
        } else {
            for (MapData map : enabledMaps) {
                int occupied = mm.getOccupiedCount(map.getName());
                int total = mm.getIslands(map.getName()).size();
                String mode = map.isInfinite() ? " &8[&7Infinite&8]" : map.hasCustomLength() ? " &8[&7Custom&8]" : "";
                player.sendMessage(ColorUtil.translate(
                        "&c● &f" + map.getName() + " &8» &7" + occupied + "/" + total + " players" + mode));
            }
        }

        player.sendMessage(ColorUtil.translate("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
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
            // Non-admins only see "list"
            if (!sender.hasPermission("fastbuilder.admin")) {
                return sender.hasPermission("fastbuilder.play")
                        ? filter(Collections.singletonList("list"), args[0])
                        : Collections.emptyList();
            }
            return filter(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase();

        // list takes no arguments
        if (sub.equals("list")) return Collections.emptyList();

        // All remaining tab completions require admin
        if (!sender.hasPermission("fastbuilder.admin")) return Collections.emptyList();

        if (args.length == 2) {
            switch (sub) {
                case "setup":
                    List<String> setupOpts = new ArrayList<>(SETUP_SUBS);
                    setupOpts.add("--infinite");
                    setupOpts.add("--customlength");
                    setupOpts.add("--diagonal");
                    return filter(setupOpts, args[1]);
                case "rename":
                case "setname":
                case "regen":
                case "seticon":
                case "scale":
                case "delete":
                case "autoscale":
                case "setdeathy":
                case "setmintime":
                case "setmaxtime":
                case "setrank":
                case "customlength":
                case "setinfinite":
                case "adddesign":
                case "removedesign":
                case "setdesignmeta":
                case "info":
                    return filter(mm.getMapNames(), args[1]);
                case "distance":
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
                    if (args[1].equalsIgnoreCase("finish")) {
                        return Collections.singletonList("<name>");
                    }
                    // "continue" intentionally has no tab completions (--force is hidden)
                    return Collections.emptyList();
                case "rename":
                case "setname":
                    return Collections.singletonList("<new_name>");
                case "seticon":
                    return filterMaterials(args[2]);
                case "scale":
                    return Arrays.asList("1", "5", "10", "15", "20", "30");
                case "distance":
                    // --force intentionally not tab-completed
                    return Arrays.asList("3", "5", "10", "20", "30", "50", "100");
                case "setmintime":
                    return Arrays.asList("0", "500", "1000", "2000", "5000");
                case "setmaxtime":
                    return Arrays.asList("0", "30000", "60000", "120000", "300000");
                case "setrank":
                    return filter(RANK_TIERS, args[2]);
                case "autoscale":
                case "customlength":
                case "setinfinite":
                    return filter(BOOLEANS, args[2]);
                case "delete":
                    return filter(Collections.singletonList("confirm"), args[2]);
                case "removedesign":
                case "setdesignmeta": {
                    MapData rmap = mm.getMap(args[1]);
                    if (rmap != null) return filter(rmap.getAllTemplates(), args[2]);
                    return Collections.emptyList();
                }
                case "adddesign": {
                    return filter(getAvailableTemplates(), args[2]);
                }
                case "setdeathy":
                    return Arrays.asList("-1", "0", "10", "20", "30", "50");
                default:
                    return Collections.emptyList();
            }
        }

        if (args.length == 4 && sub.equals("setrank")) {
            return Arrays.asList("1000", "2000", "5000", "10000", "30000");
        }
        if (args.length == 4 && sub.equals("customlength")) {
            return Arrays.asList("10", "20", "30", "50", "100");
        }

        return Collections.emptyList();
    }

    private List<String> getAvailableTemplates() {
        if (plugin.getFawePaster() == null) return Collections.emptyList();
        java.io.File dir = plugin.getFawePaster().getTemplatesDir();
        java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".template"));
        if (files == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (java.io.File f : files) {
            names.add(f.getName().replace(".template", ""));
        }
        return names;
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
        if (raw == null || raw.isEmpty()) raw = plugin.getConfigManager().getMessage(key);
        if (raw == null) raw = "";
        raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
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
