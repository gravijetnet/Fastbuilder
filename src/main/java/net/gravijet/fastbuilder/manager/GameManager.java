package net.gravijet.fastbuilder.manager;

import net.gravijet.fastbuilder.Main;
import net.gravijet.fastbuilder.gui.DistanceGui;
import net.gravijet.fastbuilder.gui.MaterialGui;
import net.gravijet.fastbuilder.model.BridgeDistance;
import net.gravijet.fastbuilder.model.BridgeMaterial;
import net.gravijet.fastbuilder.model.Island;
import net.gravijet.fastbuilder.model.PlayerData;
import net.gravijet.fastbuilder.model.PlayerStats;
import net.gravijet.fastbuilder.util.ActionBarUtil;
import net.gravijet.fastbuilder.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameManager {

    private final Main plugin;

    private final IslandManager    islandManager;
    private final HologramManager  hologramManager;
    private final NpcManager       npcManager;
    private final ScoreboardManager scoreboardManager;
    private final StatsManager     statsManager;
    private final DistanceGui      distanceGui;
    private final MaterialGui      materialGui;

    private final Map<UUID, PlayerData> playerData = new HashMap<>();
    private final Map<UUID, BukkitTask> timerTasks = new HashMap<>();

    private final BridgeDistance defaultDistance;
    private final BridgeMaterial defaultMaterial;

    public GameManager(Main plugin) {
        this.plugin = plugin;

        String world   = plugin.getConfig().getString("world", "world");
        int    y       = plugin.getConfig().getInt("island-y", 64);
        int    spacing = plugin.getConfig().getInt("island-spacing", 300);

        this.islandManager     = new IslandManager(world, y, spacing);
        this.hologramManager   = new HologramManager();
        this.npcManager        = new NpcManager();
        this.scoreboardManager = new ScoreboardManager();
        this.statsManager      = new StatsManager(plugin);
        this.distanceGui       = new DistanceGui();
        this.materialGui       = new MaterialGui();

        String defDist = plugin.getConfig().getString("default-distance", "NORMAL");
        String defMat  = plugin.getConfig().getString("default-material", "COBBLESTONE");

        BridgeDistance bd;
        try { bd = BridgeDistance.valueOf(defDist); }
        catch (IllegalArgumentException e) { bd = BridgeDistance.NORMAL; }

        BridgeMaterial bm;
        try { bm = BridgeMaterial.valueOf(defMat); }
        catch (IllegalArgumentException e) { bm = BridgeMaterial.COBBLESTONE; }

        this.defaultDistance = bd;
        this.defaultMaterial = bm;
    }

    // ── Join / Leave ──────────────────────────────────────────────

    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();

        PlayerStats stats = statsManager.load(uuid);
        PlayerData  data  = new PlayerData(uuid, defaultDistance, defaultMaterial);
        playerData.put(uuid, data);

        Island island = islandManager.createIsland(uuid);
        island.generateTargetPlatform(defaultDistance);

        npcManager.spawnNpc(island);
        hologramManager.createHologram(island, stats, defaultDistance);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            scoreboardManager.createBoard(player, data, stats);
            player.teleport(island.getSpawnLocation());
            giveHotbarItems(player);
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.sendMessage(CC.PREFIX + "Welcome! &7Right-click the NPC to select a distance.");
        }, 5L);
    }

    public void onPlayerLeave(Player player) {
        UUID uuid = player.getUniqueId();
        stopTimerTask(uuid);
        statsManager.saveAndUnload(uuid);
        npcManager.removeNpc(uuid);
        hologramManager.removeHologram(uuid);
        scoreboardManager.removeBoard(player);
        islandManager.removeIsland(uuid);
        playerData.remove(uuid);
    }

    // ── Game Logic ────────────────────────────────────────────────

    public void onFirstBlockPlaced(Player player) {
        PlayerData data = playerData.get(player.getUniqueId());
        if (data == null || data.isTimerRunning()) return;
        data.startTimer();
        startTimerTask(player);
    }

    public void onVoidFall(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData  data   = playerData.get(uuid);
        PlayerStats stats  = statsManager.get(uuid);
        Island      island = islandManager.getIsland(uuid);
        if (data == null || island == null) return;

        if (data.isAttemptStarted()) {
            stats.recordAttempt(data.getSelectedDistance());
        }

        stopTimerTask(uuid);
        data.resetTimer();
        island.clearPlacedBlocks();

        player.setHealth(player.getMaxHealth());
        player.teleport(island.getSpawnLocation());
        giveHotbarItems(player);
        player.setFoodLevel(20);

        ActionBarUtil.send(player, CC.c("&c✗ &7Void! Try again."));
        player.playSound(player.getLocation(), Sound.ENDERMAN_TELEPORT, 1f, 0.8f);

        hologramManager.updateHologram(uuid, stats, data.getSelectedDistance());
        scoreboardManager.updateBoard(player, data, stats, null);
    }

    public void onPressurePlate(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData  data   = playerData.get(uuid);
        PlayerStats stats  = statsManager.get(uuid);
        Island      island = islandManager.getIsland(uuid);
        if (data == null || island == null || !data.isAttemptStarted()) return;

        stopTimerTask(uuid);
        long elapsed = data.stopTimer();

        stats.recordAttempt(data.getSelectedDistance());
        stats.recordSuccess(data.getSelectedDistance(), elapsed);

        boolean newRecord = stats.getStats(data.getSelectedDistance()).bestTimeMillis == elapsed;
        String  timeStr   = String.format("%.2fs", elapsed / 1000.0);

        String msg = CC.c("&a✔ &7Finished in &e" + timeStr
                + (newRecord ? " &6&l★ New Best!" : ""));
        player.sendMessage(CC.PREFIX + CC.c("&7Finished in &e" + timeStr
                + (newRecord ? " &6&l★ New Best!" : "")));
        ActionBarUtil.send(player, msg);
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1f);

        hologramManager.updateHologram(uuid, stats, data.getSelectedDistance());
        scoreboardManager.updateBoard(player, data, stats, null);

        island.clearPlacedBlocks();
        data.resetTimer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.teleport(island.getSpawnLocation());
                giveHotbarItems(player);
                player.setFoodLevel(20);
            }
        }, 20L);
    }

    public void onNpcRightClick(Player player) {
        PlayerData data = playerData.get(player.getUniqueId());
        if (data == null) return;
        distanceGui.open(player, data.getSelectedDistance());
    }

    public void onDistanceSelected(Player player, BridgeDistance distance) {
        UUID uuid = player.getUniqueId();
        PlayerData  data   = playerData.get(uuid);
        PlayerStats stats  = statsManager.get(uuid);
        Island      island = islandManager.getIsland(uuid);
        if (data == null || island == null) return;

        if (data.isAttemptStarted()) {
            stopTimerTask(uuid);
            data.resetTimer();
            island.clearPlacedBlocks();
        }

        data.setSelectedDistance(distance);
        island.generateTargetPlatform(distance);
        player.closeInventory();

        hologramManager.updateHologram(uuid, stats, distance);
        scoreboardManager.updateBoard(player, data, stats, null);

        player.sendMessage(CC.PREFIX + CC.c("&7Distance changed to "
                + distance.getColorCode() + distance.getDisplayName()
                + "&7 (&f" + distance.getDistance() + " blocks&7)."));
        player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 1.5f);
    }

    public void onMaterialSelected(Player player, BridgeMaterial material) {
        PlayerData data = playerData.get(player.getUniqueId());
        if (data == null) return;

        data.setSelectedMaterial(material);
        player.closeInventory();
        giveHotbarItems(player);

        player.sendMessage(CC.PREFIX + CC.c("&7Material changed to &f"
                + material.getDisplayName() + "&7."));
        player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 1.5f);
    }

    public void openMaterialGui(Player player) {
        PlayerData data = playerData.get(player.getUniqueId());
        if (data == null) return;
        materialGui.open(player, data.getSelectedMaterial());
    }

    public void resetAttempt(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data   = playerData.get(uuid);
        Island     island = islandManager.getIsland(uuid);
        if (data == null || island == null) return;

        stopTimerTask(uuid);
        data.resetTimer();
        island.clearPlacedBlocks();

        player.teleport(island.getSpawnLocation());
        giveHotbarItems(player);
        player.setFoodLevel(20);

        ActionBarUtil.send(player, CC.c("&7Reset!"));
        scoreboardManager.updateBoard(player, data, statsManager.get(uuid), null);
    }

    // ── Spawn Commands ────────────────────────────────────────────

    public void setSpawn(Player player) {
        Island island = islandManager.getIsland(player.getUniqueId());
        if (island == null) {
            player.sendMessage(CC.ERROR + "No island found.");
            return;
        }
        island.setSpawnLocation(player.getLocation());
        player.sendMessage(CC.SUCCESS + "Spawn point updated.");
    }

    public void teleportToSpawn(Player player) {
        Island island = islandManager.getIsland(player.getUniqueId());
        if (island == null) {
            player.sendMessage(CC.ERROR + "No island found.");
            return;
        }
        player.teleport(island.getSpawnLocation());
        player.sendMessage(CC.SUCCESS + "Teleported to spawn.");
    }

    // ── Timer ─────────────────────────────────────────────────────

    private void startTimerTask(Player player) {
        UUID uuid = player.getUniqueId();
        stopTimerTask(uuid);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) { cancel(); return; }
                PlayerData d = playerData.get(uuid);
                if (d == null || !d.isTimerRunning()) { cancel(); return; }

                String timeStr = d.getFormattedElapsed();
                ActionBarUtil.send(p, CC.c("&e⏱ &f" + timeStr));
                scoreboardManager.updateBoard(p, d, statsManager.get(uuid), timeStr);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        timerTasks.put(uuid, task);
    }

    private void stopTimerTask(UUID uuid) {
        BukkitTask task = timerTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    // ── Hotbar Items ──────────────────────────────────────────────

    public void giveHotbarItems(Player player) {
        PlayerData data = playerData.get(player.getUniqueId());
        if (data == null) return;

        player.getInventory().clear();

        // Slots 0-2: bridging material
        ItemStack blockStack = data.getSelectedMaterial().toHotbarStack();
        player.getInventory().setItem(0, blockStack);
        player.getInventory().setItem(1, blockStack.clone());
        player.getInventory().setItem(2, blockStack.clone());

        // Slot 4: compass → select distance
        player.getInventory().setItem(4,
                new net.gravijet.fastbuilder.util.ItemBuilder(org.bukkit.Material.COMPASS)
                        .name("&c&lSelect Distance")
                        .lore("&7Right-click to choose a distance")
                        .build());

        // Slot 6: book → select material
        player.getInventory().setItem(6,
                new net.gravijet.fastbuilder.util.ItemBuilder(org.bukkit.Material.BOOK)
                        .name("&c&lSelect Material")
                        .lore("&7Right-click to choose a block material")
                        .build());

        // Slot 8: barrier → reset
        player.getInventory().setItem(8,
                new net.gravijet.fastbuilder.util.ItemBuilder(org.bukkit.Material.BARRIER)
                        .name("&c&lReset")
                        .lore("&7Right-click to reset your current attempt")
                        .build());

        player.getInventory().setHeldItemSlot(0);
    }

    // ── Cleanup ───────────────────────────────────────────────────

    public void cleanup() {
        timerTasks.values().forEach(BukkitTask::cancel);
        timerTasks.clear();
        npcManager.removeAll();
        hologramManager.removeAll();
    }

    // ── Getters ───────────────────────────────────────────────────

    public IslandManager    getIslandManager()    { return islandManager;    }
    public StatsManager     getStatsManager()     { return statsManager;     }
    public NpcManager       getNpcManager()       { return npcManager;       }
    public ScoreboardManager getScoreboardManager(){ return scoreboardManager; }
    public PlayerData       getPlayerData(UUID id){ return playerData.get(id); }
}
