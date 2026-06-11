package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.map.MapManager;
import net.gravijet.fastbuilder.map.SetupSession;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * Handles /map setup and /map edit subcommands.
 */
class MapSetupHandler {

    private final FastBuilder plugin;
    private final MapCommandMessages msg;

    MapSetupHandler(FastBuilder plugin) {
        this.plugin = plugin;
        this.msg = new MapCommandMessages(plugin);
    }

    void handleSetup(Player player, String[] args, MapManager mm) {
        if (!player.hasPermission("fastbuilder.command.map.setup")) {
            msg.msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        if (args.length == 1) {
            startSetup(player, mm, false, false, false);
            return;
        }

        if (args[1].equalsIgnoreCase("--infinite")) {
            startSetup(player, mm, true, false, false);
            return;
        }
        if (args[1].equalsIgnoreCase("--customlength")) {
            startSetup(player, mm, false, true, false);
            return;
        }
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
                if (args.length < 3) {
                    msg.msg(player, plugin.getConfigManager().getPrefix()
                            + "&cUsage: &f/map setup finish <name>");
                    return;
                }
                handleSetupFinishWithName(player, args[2], mm);
                break;
            case "cancel":
                handleSetupCancel(player, mm);
                break;
            default:
                msg.msg(player, "&cUnknown setup subcommand. Use: continue, finish <name>, cancel");
                break;
        }
    }

    private void startSetup(Player player, MapManager mm, boolean infinite, boolean customLength, boolean diagonal) {
        // Stop any active replay before entering setup
        if (plugin.getReplayManager() != null) {
            if (plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
                plugin.getReplayManager().stopPlayback(player.getUniqueId());
            }
            plugin.getReplayManager().stopRecording(player.getUniqueId(), false);
        }
        // Clean up any active gameplay session so the deathCheckTask doesn't teleport the player back
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());
            plugin.getGameplayManager().clearEndPlatform(player.getUniqueId());
            net.gravijet.fastbuilder.player.PlayerData existingData =
                    plugin.getPlayerManager().getCachedData(player.getUniqueId());
            if (existingData != null && existingData.getLastMap() != null) {
                net.gravijet.fastbuilder.map.MapData existingMap = mm.getMap(existingData.getLastMap());
                if (existingMap != null) {
                    plugin.getGameplayManager().revertIslandDesign(existingMap, existingData.getLastIsland());
                }
                existingData.clearCustomLengths();
            }
            plugin.getGameplayManager().removeSession(player.getUniqueId());
        }
        if (plugin.getNpcManager() != null) plugin.getNpcManager().despawnNpc(player.getUniqueId());
        if (plugin.getHologramManager() != null) {
            net.gravijet.fastbuilder.player.PlayerData d =
                    plugin.getPlayerManager().getCachedData(player.getUniqueId());
            if (d != null && d.getLastMap() != null)
                plugin.getHologramManager().removeHologram(d.getLastMap(), d.getLastIsland());
        }
        // Clear lastMap/lastIsland AFTER hologram removal so the cleanup runs correctly
        net.gravijet.fastbuilder.player.PlayerData cleanupData =
                plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (cleanupData != null) {
            cleanupData.setLastMap(null);
            cleanupData.setLastIsland(-1);
        }
        plugin.getScoreboardManager().removeScoreboard(player);
        mm.freeAllIslands(player.getUniqueId());

        mm.removeSetupSession(player.getUniqueId());

        Location origin = new Location(player.getWorld(), -1000, 20, -1000, -90f, 0f);
        SetupSession session = mm.startSetupSession(player.getUniqueId(), origin);
        if (infinite) session.setInfinite(true);
        if (customLength) session.setCustomLengthMode(true);
        if (diagonal) session.setDiagonalMode(true);

        player.teleport(origin);
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setHeldItemSlot(0);

        ItemStack rod = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = rod.getItemMeta();
        meta.setDisplayName(ColorUtil.translate("&c&lSelection Tool"));
        meta.setLore(Arrays.asList(
                ColorUtil.translate("&7Left-click  &8» &fPos 1"),
                ColorUtil.translate("&7Right-click &8» &fPos 2 / spawn / NPC / hologram")
        ));
        rod.setItemMeta(meta);
        player.getInventory().setItem(0, rod);
        player.updateInventory();

        sendSetupHeader(player, session);
        sendStep(player, 1, totalSteps(session), "Select the island area",
                "Left-click corner 1, right-click corner 2.",
                customLength ? "Build the &cstart island &7here (the end island is selected later)." : null);
        msg.sendClickableContinue(player);
    }

    void handleSetupContinue(Player player, String[] args, MapManager mm) {
        SetupSession session = mm.getSetupSession(player.getUniqueId());
        if (session == null) {
            msg.msgAdmin(player, "setup-no-session");
            return;
        }
        if (!session.canContinue()) {
            msg.msgAdmin(player, "setup-not-ready");
            return;
        }

        boolean forceSpawnLoc = false;
        for (String a : args) {
            if ("--force".equalsIgnoreCase(a)) { forceSpawnLoc = true; break; }
        }

        int total = totalSteps(session);
        // For diagonal maps, every step after island selection is shifted by +1
        // (because step 2 is "Set diagonal step"). Pre-compute the offset here.
        int d = session.isDiagonalMode() ? 1 : 0;

        switch (session.getState()) {
            case SELECTING_ISLAND:
                if (session.isDiagonalMode()) {
                    session.advanceToDiagonal();
                    sendSavedConfirm(player, "Island area");
                    sendStep(player, 2, total, "Set the diagonal step",
                            "Right-click a block at the X position where island slot &c#2 &7starts.",
                            null);
                } else {
                    session.advanceToSpawn();
                    sendSavedConfirm(player, "Island area");
                    sendStep(player, 2, total, "Set the spawn point",
                            "Stand where players should spawn, face &cEast&7, then right-click the rod.",
                            null);
                    msg.sendClickableContinue(player);
                }
                break;
            case SELECTING_SPAWN:
                if (!forceSpawnLoc) {
                    Location spawnPt = session.getSpawnPoint();
                    if (spawnPt != null) {
                        float yaw = spawnPt.getYaw();
                        float normalized = ((yaw % 360) + 360) % 360;
                        boolean facingEast = Math.abs(normalized - 270f) <= 15f;
                        // Diagonal maps may also face South (yaw ≈ 0°/360°)
                        boolean facingSouth = session.isDiagonalMode()
                                && (normalized <= 15f || normalized >= 345f);
                        if (!facingEast && !facingSouth) {
                            msg.msgAdmin(player, "setup-spawn-not-east");
                            return;
                        }
                    }
                }
                session.advanceToNpc();
                sendSavedConfirm(player, "Spawn point");
                sendStep(player, 3 + d, total, "Set the NPC location",
                        "Stand where the map-selector NPC should appear, then right-click the rod.",
                        null);
                msg.sendClickableContinue(player);
                break;
            case SELECTING_NPC:
                session.advanceToHologram();
                sendSavedConfirm(player, "NPC location");
                sendStep(player, 4 + d, total, "Set the hologram location",
                        "Stand where the stats hologram should float, then right-click the rod.",
                        null);
                msg.sendClickableContinue(player);
                break;
            case SELECTING_HOLOGRAM: {
                boolean isInfiniteFlag = session.isInfinite();
                boolean isCLMode = session.isCustomLengthMode();
                for (String a : args) {
                    if ("--infinite".equalsIgnoreCase(a)) { isInfiniteFlag = true; break; }
                }

                if (isInfiniteFlag) {
                    session.setInfinite(true);
                    Location setupMin = session.getIslandMin();
                    Location setupMax = session.getIslandMax();
                    if (setupMin == null || setupMax == null) {
                        sendSetupError(player, "Island selection is missing — restart the setup.");
                        return;
                    }
                    String tempName = "setup_" + player.getUniqueId().toString().substring(0, 8);
                    boolean saved = plugin.getFawePaster().saveTemplate(
                            setupMin.getWorld(),
                            setupMin.getBlockX(), setupMin.getBlockY(), setupMin.getBlockZ(),
                            setupMax.getBlockX(), setupMax.getBlockY(), setupMax.getBlockZ(),
                            tempName);
                    if (!saved) {
                        sendSetupError(player, "Failed to save the island template — see the console.");
                        return;
                    }
                    session.advanceToName();
                    sendSavedConfirm(player, "Hologram location");
                    sendStep(player, 5 + d, total, "Name your map",
                            "Infinite mode skips the finish zone — pick a name to finish setup.",
                            null);
                    msg.sendClickableFinish(player);

                } else if (isCLMode) {
                    Location setupMin = session.getIslandMin();
                    Location setupMax = session.getIslandMax();
                    if (setupMin == null || setupMax == null) {
                        sendSetupError(player, "Island selection is missing — restart the setup.");
                        return;
                    }
                    String tempName = "setup_" + player.getUniqueId().toString().substring(0, 8);
                    boolean saved = plugin.getFawePaster().saveTemplate(
                            setupMin.getWorld(),
                            setupMin.getBlockX(), setupMin.getBlockY(), setupMin.getBlockZ(),
                            setupMax.getBlockX(), setupMax.getBlockY(), setupMax.getBlockZ(),
                            tempName);
                    if (!saved) {
                        sendSetupError(player, "Failed to save the start-island template — see the console.");
                        return;
                    }
                    session.advanceToEndIsland();
                    sendSavedConfirm(player, "Hologram location");
                    sendStep(player, 5 + d, total, "Select the end-island area",
                            "Build the end island east of the start (&c+X&7), then mark both corners.",
                            "Left-click corner 1, right-click corner 2.");
                    msg.sendClickableContinue(player);

                } else {
                    session.advanceToFinish();
                    sendSavedConfirm(player, "Hologram location");
                    sendStep(player, 5 + d, total, "Select the finish zone",
                            "Left-click corner 1, right-click corner 2 (where players win).",
                            null);
                    msg.sendClickableFinish(player);
                }
                break;
            }
            case SELECTING_END_ISLAND: {
                Location endMin = session.getEndIslandMin();
                Location endMax = session.getEndIslandMax();
                if (endMin == null || endMax == null) {
                    msg.msgAdmin(player, "setup-not-ready");
                    return;
                }
                String endTempName = "setup_" + player.getUniqueId().toString().substring(0, 8) + "_end";
                boolean savedEnd = plugin.getFawePaster().saveTemplate(
                        endMin.getWorld(),
                        endMin.getBlockX(), endMin.getBlockY(), endMin.getBlockZ(),
                        endMax.getBlockX(), endMax.getBlockY(), endMax.getBlockZ(),
                        endTempName);
                if (!savedEnd) {
                    sendSetupError(player, "Failed to save the end-island template — see the console.");
                    return;
                }
                int baseLen = session.getBaseCustomLength();
                if (baseLen < 0) {
                    sendSetupError(player, "End Island must be placed east (&c+X&c) of the Start Island — currently &f"
                            + Math.abs(baseLen) + " &cblock(s) west. Re-select corner positions.");
                    return;
                }
                session.finalizeEndIsland();
                sendSavedConfirm(player, "End island &7(base distance: &f" + baseLen + " blocks&7)");
                sendStep(player, 6 + d, total, "Name your map",
                        "Pick a name to finish setup.",
                        null);
                msg.sendClickableFinish(player);
                break;
            }
            default:
                msg.msgAdmin(player, "setup-not-ready");
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Setup UI helpers
    // -------------------------------------------------------------------------

    /** Returns the total number of steps for the current session's mode. */
    private static int totalSteps(SetupSession session) {
        // Diagonal inserts an extra "set diagonal step" between island and spawn.
        if (session.isDiagonalMode())     return 7; // 1 area, 2 diagonal, 3 spawn, 4 NPC, 5 holo, 6 finish, 7 name
        if (session.isCustomLengthMode()) return 6; // 1 area, 2 spawn, 3 NPC, 4 holo, 5 end-island, 6 name
        if (session.isInfinite())         return 5; // 1 area, 2 spawn, 3 NPC, 4 holo, 5 name
        return 6;                                    // 1 area, 2 spawn, 3 NPC, 4 holo, 5 finish, 6 name
    }

    /** Returns a short tag like " &7(Infinite)" for the active mode. */
    private static String modeTag(SetupSession session) {
        if (session.isInfinite())         return " &7(Infinite)";
        if (session.isCustomLengthMode()) return " &7(Custom Length)";
        if (session.isDiagonalMode())     return " &7(Diagonal)";
        return "";
    }

    /** Pretty header sent once at setup start. */
    private void sendSetupHeader(Player player, SetupSession session) {
        String prefix = plugin.getConfigManager().getPrefix();
        player.sendMessage(ColorUtil.translate(prefix + "&a&lSetup started" + modeTag(session)));
        player.sendMessage(ColorUtil.translate("&7Use the &cblaze rod &7to mark positions. Type &c/map setup cancel &7to abort."));
    }

    /**
     * Sends a uniform step header + body. Hint is optional (use null to skip).
     */
    private void sendStep(Player player, int step, int total, String title, String body, String hint) {
        String prefix = plugin.getConfigManager().getPrefix();
        player.sendMessage(ColorUtil.translate(prefix + "&cStep " + step + "&8/&c" + total + " &8» &f" + title));
        if (body != null) player.sendMessage(ColorUtil.translate("  &7" + body));
        if (hint != null) player.sendMessage(ColorUtil.translate("  &7" + hint));
    }

    /** "✓ <Label> saved" confirmation, prefixed and color-consistent. */
    private void sendSavedConfirm(Player player, String label) {
        String prefix = plugin.getConfigManager().getPrefix();
        player.sendMessage(ColorUtil.translate(prefix + "&a✓ " + label + " &asaved."));
    }

    /** Consistent error line for setup-flow errors. */
    private void sendSetupError(Player player, String body) {
        String prefix = plugin.getConfigManager().getPrefix();
        player.sendMessage(ColorUtil.translate(prefix + "&c" + body));
    }

    void handleSetupFinishWithName(Player player, String name, MapManager mm) {
        SetupSession session = mm.getSetupSession(player.getUniqueId());
        if (session == null) {
            msg.msgAdmin(player, "setup-no-session");
            return;
        }

        if (!MapManager.isValidMapName(name)) {
            sendSetupError(player, "Invalid map name. Use 1-32 letters, numbers, '-' or '_'.");
            return;
        }

        if (session.getState() == SetupSession.State.SELECTING_FINISH) {
            if (!session.canFinalize()) {
                msg.msgAdmin(player, "setup-not-ready");
                return;
            }
            session.finalize_();
            Location min = session.getIslandMin();
            Location max = session.getIslandMax();
            String tempName = "setup_" + player.getUniqueId().toString().substring(0, 8);
            boolean saved = plugin.getFawePaster().saveTemplate(
                    min.getWorld(),
                    min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                    max.getBlockX(), max.getBlockY(), max.getBlockZ(),
                    tempName);
            if (!saved) {
                msg.msg(player, "&cFailed to save island template. Check console for errors.");
                return;
            }
        }

        if (session.getState() != SetupSession.State.AWAITING_NAME) {
            msg.msgAdmin(player, "setup-not-ready");
            return;
        }
        if (mm.mapExists(name)) {
            msg.msgAdmin(player, "map-already-exists");
            return;
        }

        String tempName = "setup_" + player.getUniqueId().toString().substring(0, 8);
        java.io.File tempFile = new java.io.File(plugin.getFawePaster().getTemplatesDir(), tempName + ".schematic");
        java.io.File finalFile = new java.io.File(plugin.getFawePaster().getTemplatesDir(), name.toLowerCase() + ".schematic");
        if (tempFile.exists() && !tempFile.renameTo(finalFile)) {
            plugin.getLogger().warning("Could not rename temp schematic to " + finalFile.getName()
                    + " — map template may be missing!");
        }

        MapData map = mm.createMap(session, name);
        map.setTemplateFile(name.toLowerCase());
        if (session.isInfinite()) map.setInfinite(true);

        // Default deathY = 2 blocks below the spawn point (the island floor level)
        if (session.getSpawnPoint() != null && !map.hasDeathY()) {
            map.setDeathY((int) Math.floor(session.getSpawnPoint().getY()) - 2);
        }

        if (!session.isInfinite() && plugin.getFawePaster() != null) {
            int physLen = computePhysicalIslandLength(name.toLowerCase());
            if (physLen > 0) map.setPhysicalIslandLength(physLen);
        }

        if (session.isCustomLengthMode()) {
            String endTempName = "setup_" + player.getUniqueId().toString().substring(0, 8) + "_end";
            java.io.File endTempFile = new java.io.File(plugin.getFawePaster().getTemplatesDir(), endTempName + ".schematic");
            String endFinalKey = name.toLowerCase() + "_end";
            java.io.File endFinalFile = new java.io.File(plugin.getFawePaster().getTemplatesDir(), endFinalKey + ".schematic");
            if (endTempFile.exists()) {
                // Use copy+delete fallback when renameTo fails (cross-device move on some setups).
                // Without this the end-island schematic stays at its temp name and the live paste
                // can't find it — leaving the end island invisible.
                boolean renamed = endTempFile.renameTo(endFinalFile);
                if (!renamed) {
                    try {
                        java.nio.file.Files.copy(endTempFile.toPath(), endFinalFile.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        endTempFile.delete();
                    } catch (java.io.IOException ex) {
                        plugin.getLogger().warning("Could not move end-island schematic to "
                                + endFinalFile.getName() + ": " + ex.getMessage());
                    }
                }
            }

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
        mm.pasteInitialIsland(map);

        Location setupMin = session.getIslandMin();
        Location setupMax = session.getIslandMax();
        if (setupMin != null && setupMax != null) {
            plugin.getFawePaster().clearRegion(
                    setupMin.getWorld(),
                    setupMin.getBlockX(), setupMin.getBlockY(), setupMin.getBlockZ(),
                    setupMax.getBlockX(), setupMax.getBlockY(), setupMax.getBlockZ(),
                    null);
        }

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

        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.getInventory().clear();

        String raw = plugin.getConfigManager().getAdminMessage("setup-complete");
        raw = raw.replace("%map%", name).replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
        player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                + "&7You remain in creative — use &c/fb leavemap &7or &c/fb join <map> &7to continue."));

        msg.sendPostCreationHints(player, name);
    }

    void handleSetupCancel(Player player, MapManager mm) {
        String prefix = plugin.getConfigManager().getPrefix();
        if (!mm.hasSetupSession(player.getUniqueId())) {
            msg.msg(player, prefix + "&cNo active setup session to cancel.");
            return;
        }
        mm.removeSetupSession(player.getUniqueId());
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.getInventory().clear();
        msg.msg(player, prefix + "&cSetup cancelled. You remain in creative — use /fb leavemap or /fb join <map> to continue.");
    }

    void handleEdit(Player player, String[] args, MapManager mm) {
        if (!player.hasPermission("fastbuilder.command.map.setup")) {
            msg.msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (args.length < 2) {
            msg.msg(player, plugin.getConfigManager().getPrefix() + "&cUsage: &f/map edit <map> [continue|finish|cancel]");
            return;
        }

        String mapName = args[1];

        if (args.length >= 3) {
            String sub2 = args[2].toLowerCase();
            switch (sub2) {
                case "continue":
                    handleSetupContinue(player, new String[]{"setup", "continue"}, mm);
                    return;
                case "finish":
                    if (args.length < 4) {
                        msg.msg(player, plugin.getConfigManager().getPrefix() + "&cUsage: &f/map edit <map> finish <name>");
                        return;
                    }
                    handleEditFinish(player, mapName, mm);
                    return;
                case "cancel":
                    handleSetupCancel(player, mm);
                    return;
                default:
                    msg.msg(player, "&cUnknown edit subcommand. Use: continue, finish, cancel");
                    return;
            }
        }

        MapData map = mm.getMap(mapName);
        if (map == null) {
            msg.msgMap(player, "map-not-found", mapName);
            return;
        }

        mm.removeSetupSession(player.getUniqueId());

        // Clean up any active gameplay session so the admin isn't teleported back to their island
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());
            plugin.getGameplayManager().clearEndPlatform(player.getUniqueId());
            net.gravijet.fastbuilder.player.PlayerData editData =
                    plugin.getPlayerManager().getCachedData(player.getUniqueId());
            if (editData != null) {
                if (editData.getLastMap() != null) {
                    net.gravijet.fastbuilder.map.MapData editMap = mm.getMap(editData.getLastMap());
                    if (editMap != null) {
                        plugin.getGameplayManager().revertIslandDesign(editMap, editData.getLastIsland());
                    }
                    editData.clearCustomLengths();
                }
                editData.setLastMap(null);
                editData.setLastIsland(-1);
            }
            plugin.getGameplayManager().removeSession(player.getUniqueId());
        }

        Location origin = new Location(player.getWorld(), -1000, 20, -1000, -90f, 0f);
        SetupSession session = mm.startSetupSession(player.getUniqueId(), origin);
        session.setInfinite(map.isInfinite());
        session.setEditingMap(mapName);

        player.teleport(origin);
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setHeldItemSlot(0);

        ItemStack rod = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = rod.getItemMeta();
        meta.setDisplayName(ColorUtil.translate("&c&lSelection Tool"));
        meta.setLore(Arrays.asList(
                ColorUtil.translate("&7Left-click: &fSet Position 1"),
                ColorUtil.translate("&7Right-click: &fSet Position 2 / Spawn")
        ));
        rod.setItemMeta(meta);
        player.getInventory().setItem(0, rod);
        player.updateInventory();

        // Paste the existing map template into the setup area so the admin can see and modify it
        if (map.getTemplateFile() != null && !map.getTemplateFile().isEmpty()) {
            plugin.getFawePaster().pasteTemplate(
                    player.getWorld(), map.getTemplateFile(),
                    -1000, 20, -1000, null);
        }

        String prefix = plugin.getConfigManager().getPrefix();
        player.sendMessage(ColorUtil.translate(prefix + "&fEditing map &c" + mapName + "&f."));
        player.sendMessage(ColorUtil.translate("&7The existing schematic has been loaded into the setup area."));
        player.sendMessage(ColorUtil.translate("&7Use the same steps as /map setup to redefine areas."));
        player.sendMessage(ColorUtil.translate("&7When done: &c/map edit " + mapName + " finish"));
        player.sendMessage(ColorUtil.translate("&7To discard: &c/map edit " + mapName + " cancel"));
        msg.sendClickableContinue(player);
    }

    private void handleEditFinish(Player player, String mapName, MapManager mm) {
        SetupSession session = mm.getSetupSession(player.getUniqueId());
        if (session == null) {
            msg.msgAdmin(player, "setup-no-session");
            return;
        }
        MapData map = mm.getMap(mapName);
        if (map == null) {
            msg.msgMap(player, "map-not-found", mapName);
            return;
        }

        if (session.getIslandMin() != null && session.getIslandMax() != null) {
            map.setIslandWidth(session.getIslandWidth());
            map.setIslandHeight(session.getIslandHeight());
            map.setIslandLength(session.getIslandLength());

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
                    // Rename to final first; only delete the old file after the rename succeeds.
                    // This prevents data loss if the process crashes between the two operations.
                    boolean renamed = tempFile.renameTo(finalFile);
                    if (!renamed) {
                        // renameTo can fail cross-device — fall back to copy-then-delete
                        try {
                            java.nio.file.Files.copy(tempFile.toPath(), finalFile.toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            tempFile.delete();
                        } catch (java.io.IOException ex) {
                            plugin.getLogger().warning("Could not replace schematic " + finalFile.getName()
                                    + ": " + ex.getMessage());
                        }
                    }
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

    int computePhysicalIslandLength(String templateFile) {
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
}
