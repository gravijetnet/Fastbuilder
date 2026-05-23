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
        String modeTag = infinite ? " &7(Infinite)" : customLength ? " &7(Custom Length)" : diagonal ? " &7(Diagonal)" : "";
        player.sendMessage(ColorUtil.translate(prefix + "&fSetup started." + modeTag));
        if (customLength) {
            player.sendMessage(ColorUtil.translate("&7Build your &cStart Island &7and &cEnd Island &7in this area."));
        }
        player.sendMessage(ColorUtil.translate("&cStep 1: &fSelect your island area."));
        player.sendMessage(ColorUtil.translate("&7Left-click the blaze rod: &fPos 1 &7(corner 1)"));
        player.sendMessage(ColorUtil.translate("&7Right-click the blaze rod: &fPos 2 &7(corner 2)"));
        player.sendMessage(ColorUtil.translate("&7Build your island here, then:"));
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
                player.sendMessage(ColorUtil.translate(prefix + "&fSpawn point saved."));
                player.sendMessage(ColorUtil.translate("&cStep 3: &fSet NPC location."));
                player.sendMessage(ColorUtil.translate("&7Go to the NPC position and right-click the blaze rod."));
                msg.sendClickableContinue(player);
                break;
            case SELECTING_NPC:
                session.advanceToHologram();
                player.sendMessage(ColorUtil.translate(prefix + "&fNPC location saved."));
                player.sendMessage(ColorUtil.translate("&cStep 4: &fSet hologram location."));
                player.sendMessage(ColorUtil.translate("&7Go to where the stats hologram should float and right-click the blaze rod."));
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
                        msg.msg(player, "&cIsland selection is missing. Restart setup.");
                        return;
                    }
                    String tempName = "setup_" + player.getUniqueId().toString().substring(0, 8);
                    boolean saved = plugin.getFawePaster().saveTemplate(
                            setupMin.getWorld(),
                            setupMin.getBlockX(), setupMin.getBlockY(), setupMin.getBlockZ(),
                            setupMax.getBlockX(), setupMax.getBlockY(), setupMax.getBlockZ(),
                            tempName);
                    if (!saved) {
                        msg.msg(player, "&cFailed to save island template. Check console for errors.");
                        return;
                    }
                    session.advanceToName();
                    player.sendMessage(ColorUtil.translate(prefix + "&fHologram saved. Infinite mode &7— no finish zone needed."));
                    player.sendMessage(ColorUtil.translate("&cFinal step: &fName your map."));
                    msg.sendClickableFinish(player);

                } else if (isCLMode) {
                    Location setupMin = session.getIslandMin();
                    Location setupMax = session.getIslandMax();
                    if (setupMin == null || setupMax == null) {
                        msg.msg(player, "&cIsland selection is missing. Restart setup.");
                        return;
                    }
                    String tempName = "setup_" + player.getUniqueId().toString().substring(0, 8);
                    boolean saved = plugin.getFawePaster().saveTemplate(
                            setupMin.getWorld(),
                            setupMin.getBlockX(), setupMin.getBlockY(), setupMin.getBlockZ(),
                            setupMax.getBlockX(), setupMax.getBlockY(), setupMax.getBlockZ(),
                            tempName);
                    if (!saved) {
                        msg.msg(player, "&cFailed to save start island template. Check console for errors.");
                        return;
                    }
                    session.advanceToEndIsland();
                    player.sendMessage(ColorUtil.translate(prefix + "&fHologram saved."));
                    player.sendMessage(ColorUtil.translate("&cStep 5 (Custom Length): &fSelect the End Island region."));
                    player.sendMessage(ColorUtil.translate("&7Build the end island to the &c+X &7(east) side, then select it with the blaze rod."));
                    msg.sendClickableContinue(player);

                } else {
                    session.advanceToFinish();
                    player.sendMessage(ColorUtil.translate(prefix + "&fHologram saved."));
                    player.sendMessage(ColorUtil.translate("&cStep 5: &fSelect the finish zone."));
                    player.sendMessage(ColorUtil.translate("&7Left-click: Finish Pos 1 &f| &7Right-click: Finish Pos 2"));
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
                    msg.msg(player, "&cFailed to save end island template. Check console for errors.");
                    return;
                }
                int baseLen = session.getBaseCustomLength();
                if (baseLen < 0) {
                    msg.msg(player, "&cEnd Island must be placed to the east (+X) of the Start Island. "
                            + "Currently it appears to be " + Math.abs(baseLen) + " block(s) to the west — fix placement and try again.");
                    return;
                }
                session.finalizeEndIsland();
                player.sendMessage(ColorUtil.translate(prefix + "&fEnd island saved. Base distance: &c" + baseLen + " &fblocks."));
                player.sendMessage(ColorUtil.translate("&cFinal step: &fName your map."));
                msg.sendClickableFinish(player);
                break;
            }
            default:
                msg.msgAdmin(player, "setup-not-ready");
                break;
        }
    }

    void handleSetupFinishWithName(Player player, String name, MapManager mm) {
        SetupSession session = mm.getSetupSession(player.getUniqueId());
        if (session == null) {
            msg.msgAdmin(player, "setup-no-session");
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
