package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.map.MapManager;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
 *   reload        - Reload plugin configuration (admin)
 *   dump          - Upload diagnostic info to Bytebin (admin)
 */
public class FastBuilderCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBS = Arrays.asList(
            "join", "leave", "leavemap", "reset"
    );
    private static final List<String> ALL_SUBS = Arrays.asList(
            "join", "leave", "leavemap", "reset", "reload", "dump"
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
            case "leavemap":
                handleLeaveMap(player);
                break;
            case "reset":
                handleReset(player, mm);
                break;
            case "reload":
                handleReload(player);
                break;
            case "dump":
                handleDump(player);
                break;
            default:
                sendHelp(player);
                break;
        }
        return true;
    }

    // --- /fb join <map> ---

    private void handleJoin(Player player, String[] args, MapManager mm) {
        if (!player.hasPermission("fastbuilder.command.fb.join")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
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

        // Full cleanup of the player's current island before switching
        PlayerData prevData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (plugin.getNpcManager() != null) {
            plugin.getNpcManager().despawnNpc(player.getUniqueId());
        }
        if (prevData != null && prevData.getLastMap() != null && plugin.getHologramManager() != null) {
            plugin.getHologramManager().removeHologram(prevData.getLastMap(), prevData.getLastIsland());
        }
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());
            plugin.getGameplayManager().clearEndPlatform(player.getUniqueId());
            if (prevData != null && prevData.getLastMap() != null) {
                net.gravijet.fastbuilder.map.MapData oldMap =
                        plugin.getMapManager().getMap(prevData.getLastMap());
                if (oldMap != null) {
                    plugin.getGameplayManager().revertIslandDesign(oldMap, prevData.getLastIsland());
                }
            }
            plugin.getGameplayManager().removeSession(player.getUniqueId());
            plugin.getGameplayManager().removeGlobalSessionBest(player.getName());
        }
        if (prevData != null) prevData.clearCustomLengths();
        if (plugin.getCpsListener() != null) {
            plugin.getCpsListener().cleanupPlayer(player.getUniqueId());
        }

        // Free all island slots
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

        // Full island setup on the new island
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setFoodLevel(20);
        player.setHealth(player.getMaxHealth());
        if (plugin.getGameplayManager() != null) {
            net.gravijet.fastbuilder.gameplay.RunSession fbSess =
                    plugin.getGameplayManager().createSession(player.getUniqueId(), map.getName(), island);
            if (map.hasEndIsland()) {
                plugin.getGameplayManager().placeEndPlatform(player, map, fbSess, map.getBaseCustomLength());
            }
            plugin.getGameplayManager().applyPlayerDesign(player, map, island);
        }
        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().giveItems(player);
        }
        plugin.getScoreboardManager().createScoreboard(player);
        if (plugin.getNpcManager() != null) {
            plugin.getNpcManager().spawnNpc(player, map.getIslandNpcLocation(island));
        }
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().updateHologram(map.getName(), island, player);
        }

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
        if (!player.hasPermission("fastbuilder.command.fb.leave")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        // Stop active replay / recording
        if (plugin.getReplayManager() != null) {
            if (plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
                plugin.getReplayManager().stopPlayback(player.getUniqueId());
            }
            plugin.getReplayManager().stopRecording(player.getUniqueId(), false);
        }

        // Full session cleanup: blocks, end platform, island design, session state
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());
            plugin.getGameplayManager().clearEndPlatform(player.getUniqueId());
            PlayerData leaveData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            if (leaveData != null && leaveData.getLastMap() != null) {
                net.gravijet.fastbuilder.map.MapData leaveMap =
                        plugin.getMapManager().getMap(leaveData.getLastMap());
                if (leaveMap != null) {
                    plugin.getGameplayManager().revertIslandDesign(leaveMap, leaveData.getLastIsland());
                }
                leaveData.clearCustomLengths();
            }
            plugin.getGameplayManager().removeSession(player.getUniqueId());
            plugin.getGameplayManager().removeGlobalSessionBest(player.getName());
        }

        // Despawn NPC
        if (plugin.getNpcManager() != null) {
            plugin.getNpcManager().despawnNpc(player.getUniqueId());
        }

        // Remove hologram
        if (plugin.getHologramManager() != null) {
            PlayerData d = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            if (d != null && d.getLastMap() != null) {
                plugin.getHologramManager().removeHologram(d.getLastMap(), d.getLastIsland());
            }
        }

        // Clean up CPS hologram
        if (plugin.getCpsListener() != null) {
            plugin.getCpsListener().cleanupPlayer(player.getUniqueId());
        }

        // Free all island slots
        plugin.getMapManager().freeAllIslands(player.getUniqueId());

        String action = plugin.getConfigManager().getLeaveAction();
        switch (action) {
            case "BUNGEE": {
                String lobbyServer = plugin.getConfigManager().getLobbyServer();
                try {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    DataOutputStream out = new DataOutputStream(b);
                    out.writeUTF("Connect");
                    out.writeUTF(lobbyServer);
                    player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to send player to lobby via BungeeCord.");
                    player.kickPlayer(ColorUtil.translate("&fYou left FastBuilder."));
                }
                break;
            }
            case "KICK": {
                player.kickPlayer(ColorUtil.translate("&fYou left FastBuilder."));
                break;
            }
            case "COMMAND": {
                String cmd = plugin.getConfigManager().getLeaveCommand();
                if (cmd != null && !cmd.isEmpty()) {
                    Bukkit.dispatchCommand(player, cmd);
                } else {
                    player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                }
                break;
            }
            default: // "SPAWN"
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                break;
        }

        // Only send the message for non-kick actions (kick reason serves as the message)
        if (!action.equals("KICK") && !action.equals("BUNGEE")) {
            msg(player, plugin.getConfigManager().getPrefix() + "&fYou left the game.");
        }
    }

    // --- /fb leavemap ---
    // Leaves the current map/island and enters a permanent build mode without server disconnect.

    private void handleLeaveMap(Player player) {
        if (!player.hasPermission("fastbuilder.command.fb.leave")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        if (plugin.getReplayManager() != null) {
            if (plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
                plugin.getReplayManager().stopPlayback(player.getUniqueId());
            }
            plugin.getReplayManager().stopRecording(player.getUniqueId(), false);
        }

        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());
            plugin.getGameplayManager().clearEndPlatform(player.getUniqueId());
            net.gravijet.fastbuilder.player.PlayerData leaveData =
                    plugin.getPlayerManager().getCachedData(player.getUniqueId());
            if (leaveData != null && leaveData.getLastMap() != null) {
                net.gravijet.fastbuilder.map.MapData leaveMap =
                        plugin.getMapManager().getMap(leaveData.getLastMap());
                if (leaveMap != null) {
                    plugin.getGameplayManager().revertIslandDesign(leaveMap, leaveData.getLastIsland());
                }
                leaveData.clearCustomLengths();
            }
            plugin.getGameplayManager().removeSession(player.getUniqueId());
            plugin.getGameplayManager().removeGlobalSessionBest(player.getName());
            plugin.getGameplayManager().enterBuildMode(player.getUniqueId());
        }

        if (plugin.getNpcManager() != null) plugin.getNpcManager().despawnNpc(player.getUniqueId());
        if (plugin.getHologramManager() != null) {
            net.gravijet.fastbuilder.player.PlayerData d =
                    plugin.getPlayerManager().getCachedData(player.getUniqueId());
            if (d != null && d.getLastMap() != null)
                plugin.getHologramManager().removeHologram(d.getLastMap(), d.getLastIsland());
        }
        if (plugin.getCpsListener() != null) plugin.getCpsListener().cleanupPlayer(player.getUniqueId());
        plugin.getMapManager().freeAllIslands(player.getUniqueId());

        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        msg(player, plugin.getConfigManager().getPrefix() + "&fYou left your island. Build mode active.");
    }

    // --- /fb reset ---

    private void handleReset(Player player, MapManager mm) {
        if (!player.hasPermission("fastbuilder.command.fb.reset")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
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

        org.bukkit.Location min = map.getIslandMin(currentIsland);
        org.bukkit.Location max = map.getIslandMax(currentIsland);

        player.teleport(map.getIslandSpawn(currentIsland));

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

    // --- /fb reload ---

    private void handleReload(Player player) {
        if (!player.hasPermission("fastbuilder.command.fb.reload")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        plugin.getConfigManager().reload();
        plugin.getMapManager().loadMaps();

        // Restart scoreboard and actionbar tasks with new config
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().reload();
        }
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().reloadActionbar();
        }

        msg(player, plugin.getConfigManager().getPrefix() + "&fConfiguration reloaded. Active sessions preserved.");
    }

    // --- /fb dump ---

    private void handleDump(Player player) {
        if (!player.hasPermission("fastbuilder.command.fb.dump")) {
            msg(player, plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        msg(player, plugin.getConfigManager().getPrefix() + "&7Generating diagnostic dump...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String dump = buildDump();
                String url = uploadToBytebin(dump);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (url != null) {
                        // Clickable + copyable URL via chat component
                        net.md_5.bungee.api.chat.TextComponent link =
                                new net.md_5.bungee.api.chat.TextComponent(
                                        ColorUtil.translate(plugin.getConfigManager().getPrefix()
                                                + "&fDump uploaded: &a&n" + url));
                        link.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                                net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, url));
                        link.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                                new net.md_5.bungee.api.chat.BaseComponent[]{
                                        new net.md_5.bungee.api.chat.TextComponent(
                                                ColorUtil.translate("&7Click to open in browser"))}));
                        player.spigot().sendMessage(link);

                        net.md_5.bungee.api.chat.TextComponent copy =
                                new net.md_5.bungee.api.chat.TextComponent(
                                        ColorUtil.translate("&7[&eClick to copy URL&7]"));
                        copy.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                                net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND, url));
                        copy.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                                new net.md_5.bungee.api.chat.BaseComponent[]{
                                        new net.md_5.bungee.api.chat.TextComponent(
                                                ColorUtil.translate("&7Click to paste URL into chat"))}));
                        player.spigot().sendMessage(copy);
                    } else {
                        msg(player, plugin.getConfigManager().getPrefix()
                                + "&cFailed to upload dump. Check console for the raw output.");
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to generate dump: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        msg(player, plugin.getConfigManager().getPrefix() + "&cDump generation failed: " + e.getMessage()));
            }
        });
    }

    private String buildDump() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== FastBuilder Diagnostic Dump ===\n");
        sb.append("Generated: ").append(new java.util.Date()).append("\n\n");

        // Plugin version
        sb.append("--- Plugin ---\n");
        sb.append("Version: ").append(plugin.getDescription().getVersion()).append("\n");

        // Server / Java / OS
        sb.append("\n--- Environment ---\n");
        sb.append("Server: ").append(Bukkit.getServer().getName())
                .append(" ").append(Bukkit.getServer().getVersion()).append("\n");
        sb.append("Bukkit API: ").append(Bukkit.getBukkitVersion()).append("\n");
        sb.append("Java: ").append(System.getProperty("java.version"))
                .append(" (").append(System.getProperty("java.vendor")).append(")\n");
        sb.append("OS: ").append(System.getProperty("os.name"))
                .append(" ").append(System.getProperty("os.version"))
                .append(" (").append(System.getProperty("os.arch")).append(")\n");

        // RAM usage
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long totalMB = rt.totalMemory() / 1024 / 1024;
        long maxMB = rt.maxMemory() / 1024 / 1024;
        sb.append("RAM: ").append(usedMB).append("MB used / ")
                .append(totalMB).append("MB allocated / ")
                .append(maxMB).append("MB max\n");
        sb.append("CPU cores: ").append(rt.availableProcessors()).append("\n");

        // All installed plugins
        sb.append("\n--- All Installed Plugins (").append(Bukkit.getPluginManager().getPlugins().length).append(") ---\n");
        for (org.bukkit.plugin.Plugin p : Bukkit.getPluginManager().getPlugins()) {
            sb.append(p.getName())
              .append(" v").append(p.getDescription().getVersion())
              .append(" [").append(p.isEnabled() ? "ENABLED" : "DISABLED").append("]\n");
        }

        // Online players
        sb.append("\n--- Players ---\n");
        sb.append("Online: ").append(Bukkit.getOnlinePlayers().size())
                .append("/").append(Bukkit.getMaxPlayers()).append("\n");

        // Maps
        sb.append("\n--- Maps ---\n");
        for (net.gravijet.fastbuilder.map.MapData m : plugin.getMapManager().getAllMaps()) {
            sb.append(m.getName())
                    .append(": enabled=").append(m.isEnabled())
                    .append(", scale=").append(m.getScale())
                    .append(", distance=").append(m.getDistance())
                    .append(", infinite=").append(m.isInfinite())
                    .append(", customLength=").append(m.hasCustomLength())
                    .append(", occupied=").append(plugin.getMapManager().getOccupiedCount(m.getName()))
                    .append("\n");
        }

        // All Fastbuilder YAML config files
        String[] yamlFiles = {"config.yml", "messages.yml", "guis.yml", "items.yml"};
        for (String yamlName : yamlFiles) {
            sb.append("\n--- ").append(yamlName).append(" ---\n");
            try {
                java.io.File yamlFile = new java.io.File(plugin.getDataFolder(), yamlName);
                if (yamlFile.exists()) {
                    sb.append(new String(java.nio.file.Files.readAllBytes(yamlFile.toPath()), StandardCharsets.UTF_8));
                } else {
                    sb.append("[File not found]\n");
                }
            } catch (Exception e) {
                sb.append("[Could not read ").append(yamlName).append(": ").append(e.getMessage()).append("]\n");
            }
        }

        // Map data files
        sb.append("\n--- Map Data Files ---\n");
        java.io.File mapsDir = new java.io.File(plugin.getDataFolder(), "maps");
        if (mapsDir.exists()) {
            java.io.File[] mapFiles = mapsDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (mapFiles != null) {
                for (java.io.File mapFile : mapFiles) {
                    sb.append("\n[").append(mapFile.getName()).append("]\n");
                    try {
                        sb.append(new String(java.nio.file.Files.readAllBytes(mapFile.toPath()), StandardCharsets.UTF_8));
                    } catch (Exception e) {
                        sb.append("[Could not read: ").append(e.getMessage()).append("]\n");
                    }
                }
            }
        }

        return sb.toString();
    }

    /**
     * Upload content to Bytebin (https://bytebin.lucko.me) and return the URL.
     * Returns null on failure.
     */
    private String uploadToBytebin(String content) {
        try {
            URL url = new URL("https://bytebin.lucko.me/post");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }

            int status = conn.getResponseCode();
            if (status == 201 || status == 200) {
                // Read the key from the response
                try (InputStream is = conn.getInputStream()) {
                    byte[] resp = is.readAllBytes();
                    String json = new String(resp, StandardCharsets.UTF_8);
                    // Response is {"key":"XXXX"}
                    int keyStart = json.indexOf("\"key\":");
                    if (keyStart >= 0) {
                        int q1 = json.indexOf('"', keyStart + 6);
                        int q2 = json.indexOf('"', q1 + 1);
                        if (q1 >= 0 && q2 > q1) {
                            String key = json.substring(q1 + 1, q2);
                            return "https://bytebin.lucko.me/" + key;
                        }
                    }
                    // Fallback: just return the raw response if it looks like a key
                    String trimmed = json.trim().replaceAll("[^a-zA-Z0-9]", "");
                    if (!trimmed.isEmpty()) return "https://bytebin.lucko.me/" + trimmed;
                }
            } else {
                plugin.getLogger().warning("Bytebin upload returned HTTP " + status);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to upload to Bytebin: " + e.getMessage());
            // Log the dump to console as fallback
            plugin.getLogger().info("=== DUMP OUTPUT ===\n" + content);
        }
        return null;
    }

    // --- Help ---

    private void sendHelp(Player player) {
        player.sendMessage(ColorUtil.translate("&c&lFastBuilder &8» &fCommands"));
        player.sendMessage(ColorUtil.translate("&c● /fb join &f<map> &7» &fJoin a map."));
        player.sendMessage(ColorUtil.translate("&c● /fb leave &7» &fLeave to lobby."));
        player.sendMessage(ColorUtil.translate("&c● /fb reset &7» &fReset your island."));
        if (player.hasPermission("fastbuilder.command.fb.reload")) {
            player.sendMessage(ColorUtil.translate("&c● /fb reload &8» &fReload configuration."));
        }
        if (player.hasPermission("fastbuilder.command.fb.dump")) {
            player.sendMessage(ColorUtil.translate("&c● /fb dump &8» &fUpload diagnostic report."));
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
