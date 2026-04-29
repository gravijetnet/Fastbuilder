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
            "setup", "edit", "rename", "regen", "seticon", "enable", "disable", "delete", "scale", "distance",
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
            case "edit":
                handleEdit(player, args, mm);
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
        // Yaw -90 = facing East (spec requirement)
        Location origin = new Location(player.getWorld(), -1000, 20, -1000, -90f, 0f);

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
        String modeTag = infinite ? " &7(Infinite)" : customLength ? " &7(Custom Length)" : diagonal ? " &7(Diagonal)" : "";
        player.sendMessage(ColorUtil.translate(prefix + "&fSetup started." + modeTag));
        if (customLength) {
            player.sendMessage(ColorUtil.translate("&7Build your &cStart Island &7and &cEnd Island &7in this area."));
        }
        player.sendMessage(ColorUtil.translate("&cStep 1: &fSelect your island area."));
        player.sendMessage(ColorUtil.translate("&7Left-click the blaze rod: &fPos 1 &7(corner 1)"));
        player.sendMessage(ColorUtil.translate("&7Right-click the blaze rod: &fPos 2 &7(corner 2)"));
        player.sendMessage(ColorUtil.translate("&7Build your island here, then:"));
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
                    player.sendMessage(ColorUtil.translate(prefix + "&fIsland area saved."));
                    player.sendMessage(ColorUtil.translate("&cStep 2 (Diagonal): &fSet X direction."));
                    player.sendMessage(ColorUtil.translate("&7Right-click a block at the X position where island slot 1 starts."));
                } else {
                    session.advanceToSpawn();
                    player.sendMessage(ColorUtil.translate(prefix + "&fIsland area saved."));
                    player.sendMessage(ColorUtil.translate("&cStep 2: &fSet spawn point."));
                    player.sendMessage(ColorUtil.translate("&7Stand where players spawn, face East, then right-click the blaze rod."));
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
                player.sendMessage(ColorUtil.translate(prefix + "&fSpawn point saved."));
                player.sendMessage(ColorUtil.translate("&cStep 3: &fSet NPC location."));
                player.sendMessage(ColorUtil.translate("&7Go to the NPC position and right-click the blaze rod."));
                sendClickableContinue(player);
                break;
            case SELECTING_NPC:
                session.advanceToHologram();
                player.sendMessage(ColorUtil.translate(prefix + "&fNPC location saved."));
                player.sendMessage(ColorUtil.translate("&cStep 4: &fSet hologram location."));
                player.sendMessage(ColorUtil.translate("&7Go to where the stats hologram should float and right-click the blaze rod."));
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
                    player.sendMessage(ColorUtil.translate(prefix + "&fHologram saved. Infinite mode &7— no finish zone needed."));
                    player.sendMessage(ColorUtil.translate("&cFinal step: &fName your map."));
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
                    player.sendMessage(ColorUtil.translate(prefix + "&fHologram saved."));
                    player.sendMessage(ColorUtil.translate("&cStep 5 (Custom Length): &fSelect the End Island region."));
                    player.sendMessage(ColorUtil.translate("&7Build the end island to the &c+X &7(east) side, then select it with the blaze rod."));
                    sendClickableContinue(player);

                } else {
                    session.advanceToFinish();
                    player.sendMessage(ColorUtil.translate(prefix + "&fHologram saved."));
                    player.sendMessage(ColorUtil.translate("&cStep 5: &fSelect the finish zone."));
                    player.sendMessage(ColorUtil.translate("&7Left-click: Finish Pos 1 &f| &7Right-click: Finish Pos 2"));
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
                if (baseLen < 0) {
                    msg(player, "&cEnd Island must be placed to the &c+X &7(east) side of the Start Island. Base distance is " + baseLen + " — check placement.");
                    return;
                }
                session.finalizeEndIsland();
                player.sendMessage(ColorUtil.translate(prefix + "&fEnd island saved. Base distance: &c" + baseLen + " &fblocks."));
                player.sendMessage(ColorUtil.translate("&cFinal step: &fName your map."));
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

        // Stay in creative/build mode after setup — the admin remains where they are
        // so they can immediately test, adjust, or continue working on the map.
        // They can use /fb leave or /fb join to return to a normal island.
    }

    // --- /map setup cancel ---

    // --- /map edit <map> [continue|finish|cancel] ---

    /**
     * Re-opens an existing map for editing. The admin is teleported to the setup area
     * with a blaze rod and can redefine spawn, NPC, hologram, finish zone, and
     * island template. On /map edit <map> finish, the map is updated in-place.
     */
    private void handleEdit(Player player, String[] args, MapManager mm) {
        if (!player.hasPermission("fastbuilder.command.map.setup")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length < 2) {
            msg(player, plugin.getConfigManager().getPrefix() + "&cUsage: &f/map edit <map> [continue|finish|cancel]");
            return;
        }

        String mapName = args[1];

        // /map edit <map> continue / finish / cancel — delegate to setup flow
        if (args.length >= 3) {
            String sub2 = args[2].toLowerCase();
            switch (sub2) {
                case "continue":
                    handleSetupContinue(player, new String[]{"setup", "continue"}, mm);
                    return;
                case "finish":
                    if (args.length < 4) {
                        msg(player, plugin.getConfigManager().getPrefix() + "&cUsage: &f/map edit <map> finish <name>");
                        return;
                    }
                    // Apply edits back to the existing map
                    handleEditFinish(player, mapName, mm);
                    return;
                case "cancel":
                    handleSetupCancel(player, mm);
                    return;
                default:
                    msg(player, "&cUnknown edit subcommand. Use: continue, finish, cancel");
                    return;
            }
        }

        // /map edit <map> — start edit session for this map
        MapData map = mm.getMap(mapName);
        if (map == null) {
            msgMap(player, "map-not-found", mapName);
            return;
        }

        // Cancel any existing session
        mm.removeSetupSession(player.getUniqueId());

        // Teleport to setup area (same as setup wizard)
        Location origin = new Location(player.getWorld(), -1000, 20, -1000, -90f, 0f);
        SetupSession session = mm.startSetupSession(player.getUniqueId(), origin);
        session.setInfinite(map.isInfinite());

        // Pre-populate the session from existing map data so partial edits work
        session.setEditingMap(mapName);

        player.teleport(origin);
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.getInventory().clear();

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
        player.sendMessage(ColorUtil.translate(prefix + "&fEditing map &c" + mapName + "&f."));
        player.sendMessage(ColorUtil.translate("&7Use the same steps as /map setup to redefine areas."));
        player.sendMessage(ColorUtil.translate("&7When done: &c/map edit " + mapName + " finish"));
        player.sendMessage(ColorUtil.translate("&7To discard: &c/map edit " + mapName + " cancel"));
        sendClickableContinue(player);
    }

    private void handleEditFinish(Player player, String mapName, MapManager mm) {
        SetupSession session = mm.getSetupSession(player.getUniqueId());
        if (session == null) {
            msgAdmin(player, "setup-no-session");
            return;
        }

        MapData map = mm.getMap(mapName);
        if (map == null) {
            msgMap(player, "map-not-found", mapName);
            return;
        }

        // Apply updated values from the session back to the existing map
        if (session.getIslandMin() != null && session.getIslandMax() != null) {
            map.setIslandWidth(session.getIslandWidth());
            map.setIslandHeight(session.getIslandHeight());
            map.setIslandLength(session.getIslandLength());

            // Save updated template schematic
            String tempName = "setup_" + player.getUniqueId().toString().substring(0, 8);
            boolean saved = plugin.getFawePaster().saveTemplate(
                    session.getIslandMin().getWorld(),
                    session.getIslandMin().getBlockX(), session.getIslandMin().getBlockY(), session.getIslandMin().getBlockZ(),
                    session.getIslandMax().getBlockX(), session.getIslandMax().getBlockY(), session.getIslandMax().getBlockZ(),
                    tempName);
            if (saved) {
                java.io.File tempFile = new java.io.File(plugin.getFawePaster().getTemplatesDir(), tempName + ".schematic");
                java.io.File finalFile = new java.io.File(plugin.getFawePaster().getTemplatesDir(), map.getTemplateFile() + ".schematic");
                if (tempFile.exists()) {
                    finalFile.delete();
                    tempFile.renameTo(finalFile);
                }
            }
        }
        if (session.getSpawnPoint() != null) {
            map.setSpawnOffsetX(session.getSpawnOffsetX());
            map.setSpawnOffsetY(session.getSpawnOffsetY());
            map.setSpawnOffsetZ(session.getSpawnOffsetZ());
            map.setSpawnYaw(session.getSpawnPoint().getYaw());
            map.setSpawnPitch(session.getSpawnPoint().getPitch());
        }
        if (session.getNpcPoint() != null) {
            map.setNpcOffsetX(session.getNpcOffsetX());
            map.setNpcOffsetY(session.getNpcOffsetY());
            map.setNpcOffsetZ(session.getNpcOffsetZ());
            map.setNpcYaw(session.getNpcPoint().getYaw());
            map.setNpcPitch(session.getNpcPoint().getPitch());
        }
        if (session.getState().ordinal() >= SetupSession.State.SELECTING_FINISH.ordinal()) {
            map.setFinishMinX(session.getFinishMinX());
            map.setFinishMinY(session.getFinishMinY());
            map.setFinishMinZ(session.getFinishMinZ());
            map.setFinishMaxX(session.getFinishMaxX());
            map.setFinishMaxY(session.getFinishMaxY());
            map.setFinishMaxZ(session.getFinishMaxZ());
        }

        mm.saveMap(map);
        mm.removeSetupSession(player.getUniqueId());

        String prefix = plugin.getConfigManager().getPrefix();
        player.sendMessage(ColorUtil.translate(prefix + "&fMap &c" + mapName + " &fupdated."));
        player.sendMessage(ColorUtil.translate("&7Use &c/map regen " + mapName + " &7to repaste islands with the new layout."));
    }

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
        // Spawn
        player.sendMessage(ColorUtil.translate("  &7» &cSpawn offset: &f+"
                + String.format("%.1f", map.getSpawnOffsetX()) + "x, +"
                + String.format("%.1f", map.getSpawnOffsetY()) + "y, +"
                + String.format("%.1f", map.getSpawnOffsetZ()) + "z"
                + " &7yaw: &f" + String.format("%.1f", map.getSpawnYaw())));
        // NPC
        player.sendMessage(ColorUtil.translate("  &7» &cNPC offset: &f+"
                + String.format("%.1f", map.getNpcOffsetX()) + "x, +"
                + String.format("%.1f", map.getNpcOffsetY()) + "y, +"
                + String.format("%.1f", map.getNpcOffsetZ()) + "z"));
        // Finish zone
        player.sendMessage(ColorUtil.translate("  &7» &cFinish zone: &fX " + map.getFinishMinX() + "→" + map.getFinishMaxX()
                + " &7Y &f" + map.getFinishMinY() + "→" + map.getFinishMaxY()
                + " &7Z &f" + map.getFinishMinZ() + "→" + map.getFinishMaxZ()));
        // Modes
        player.sendMessage(ColorUtil.translate("  &7» &cInfinite: &f" + (map.isInfinite() ? "&aYes" : "&cNo")));
        if (map.isDiagonal()) {
            player.sendMessage(ColorUtil.translate("  &7» &cDiagonal: &aEnabled &7(X step: &f" + map.getDiagonalStepX() + "&7)"));
        }
        // Custom length status — differentiate end-island mode from legacy bounds mode
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
        // Template
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
            player.sendMessage(ColorUtil.translate("  &7» &cMin valid time: &f" + net.gravijet.fastbuilder.util.TimeUtil.formatTime(map.getMinValidTime())));
        }
        if (map.getMaxCompletionTime() > 0) {
            player.sendMessage(ColorUtil.translate("  &7» &cMax run time: &f" + net.gravijet.fastbuilder.util.TimeUtil.formatTime(map.getMaxCompletionTime())));
        }
        // Ranks
        if (map.getDiamondTime() > 0 || map.getGoldTime() > 0 || map.getSilverTime() > 0 || map.getBronzeTime() > 0) {
            player.sendMessage(ColorUtil.translate("  &7» &cRanks:"));
            if (map.getDiamondTime() > 0) player.sendMessage(ColorUtil.translate("    &7» &bDiamond &7≤ &f" + net.gravijet.fastbuilder.util.TimeUtil.formatTime(map.getDiamondTime())));
            if (map.getGoldTime() > 0)    player.sendMessage(ColorUtil.translate("    &7» &6Gold &7≤ &f" + net.gravijet.fastbuilder.util.TimeUtil.formatTime(map.getGoldTime())));
            if (map.getSilverTime() > 0)  player.sendMessage(ColorUtil.translate("    &7» &7Silver &7≤ &f" + net.gravijet.fastbuilder.util.TimeUtil.formatTime(map.getSilverTime())));
            if (map.getBronzeTime() > 0)  player.sendMessage(ColorUtil.translate("    &7» &cBronze &7≤ &f" + net.gravijet.fastbuilder.util.TimeUtil.formatTime(map.getBronzeTime())));
        }
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
        player.sendMessage(ColorUtil.translate(prefix + "&fRegenerating all " + map.getScale()
                + " island(s) for &c" + map.getName() + "&f. Please wait..."));

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

        // --force (not tab-completed): allows negative total-step values (e.g. overlapping islands)
        boolean force = args.length > 3 && args[3].equalsIgnoreCase("--force");

        int blocks;
        try {
            blocks = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            msg(player, "&cInvalid number: &f" + args[2]);
            return;
        }

        if (!force && blocks < 1) {
            msg(player, "&cDistance must be at least 1. Use --force for negative values.");
            return;
        }

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

        // The input value is the desired GAP (blocks between last block of one island
        // and first block of the next). Stored directly as distance.
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

    // --- /map adddesign <map> [--customlength|--infinite] ---
    // Saves the current setup area template as an alternative design for the map.
    // --customlength: design is only available on custom-length maps
    // --infinite    : design is only available on infinite maps
    // (no flag)     : standard design, available on normal maps only

    private void handleAddDesign(Player player, String[] args, MapManager mm) {
        if (args.length < 2) {
            msg(player, plugin.getConfigManager().getPrefix()
                    + "&cUsage: &f/map adddesign <map> [<templateKey>] [--customlength|--infinite]");
            return;
        }
        MapData map = mm.getMap(args[1]);
        if (map == null) { msgMap(player, "map-not-found", args[1]); return; }

        String prefix = plugin.getConfigManager().getPrefix();

        // Parse optional flags from remaining args
        boolean isCustomLength = false;
        boolean isInfinite = false;
        for (String a : args) {
            if ("--customlength".equalsIgnoreCase(a)) isCustomLength = true;
            else if ("--infinite".equalsIgnoreCase(a)) isInfinite = true;
        }

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
            // No active setup session — check for explicit key in args
            String keyArg = null;
            for (int i = 2; i < args.length; i++) {
                if (!args[i].startsWith("--")) { keyArg = args[i]; break; }
            }
            if (keyArg == null) {
                msg(player, prefix + "&cNo active setup selection found. "
                        + "&fUse the setup wizard to select an area, or provide a template key: "
                        + "&c/map adddesign <map> <existingTemplateKey>");
                return;
            }
            // Validate that the template file actually exists
            java.io.File templateFile = new java.io.File(
                    plugin.getFawePaster().getTemplatesDir(), keyArg + ".schematic");
            if (!templateFile.exists()) {
                msg(player, prefix + "&cTemplate &f" + keyArg + " &cdoes not exist. "
                        + "&7Use the setup wizard to build and select an island first.");
                return;
            }
            designKey = keyArg;
        }

        String modeTag = isCustomLength ? " &7[custom-length]" : isInfinite ? " &7[infinite]" : "";
        if (isCustomLength) {
            map.addCustomLengthTemplate(designKey);
        } else if (isInfinite) {
            map.addInfiniteTemplate(designKey);
        } else {
            map.addAlternativeTemplate(designKey);
        }
        mm.saveMap(map);
        msg(player, prefix + "&fAlternative design &c" + designKey + modeTag
                + " &fadded to map &c" + map.getName() + "&f.");
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
        // Remove from whichever list contains this key
        boolean removed = map.removeAlternativeTemplate(key)
                || map.removeCustomLengthTemplate(key)
                || map.removeInfiniteTemplate(key);
        if (removed) {
            mm.saveMap(map);
            msg(player, prefix + "&fDesign &c" + key + " &fremoved from map &c" + map.getName() + "&f.");
        } else {
            msg(player, prefix + "&cDesign &f" + key + " &cnot found for map &f" + map.getName()
                    + "&c. Available alternatives: &f" + map.getAllTemplates());
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
                ColorUtil.translate(prefix + "&7» &c/map setup continue"));
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
        player.sendMessage(ColorUtil.translate(prefix + "&fMap &c" + mapName + " &fcreated. Next steps:"));
        sendSuggestHint(player, "&7Enable the map: ", "/map enable " + mapName);
        sendSuggestHint(player, "&7Set island count: ", "/map scale " + mapName + " 15");
        sendSuggestHint(player, "&7Test it: ", "/fb join " + mapName);
    }

    private void sendSuggestHint(Player player, String label, String command) {
        net.md_5.bungee.api.chat.TextComponent line =
                new net.md_5.bungee.api.chat.TextComponent(ColorUtil.translate("  &7» " + label));
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

        // Pagination footer
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
                ColorUtil.translate("&7Page &c" + page + " &7/ &c" + totalPages)));

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
            String cmd  = (String) entry[1];
            String desc = (String) entry[2];
            if (player.hasPermission("fastbuilder.admin") || player.hasPermission(perm)) {
                lines.add("&c● " + cmd + " &7» &f" + desc);
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
                case "edit":
                    return filter(mm.getMapNames(), args[1]);
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
                case "edit":
                    return filter(Arrays.asList("continue", "finish", "cancel"), args[2]);
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
