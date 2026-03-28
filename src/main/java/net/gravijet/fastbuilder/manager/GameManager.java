package net.gravijet.fastbuilder.manager;

import net.gravijet.fastbuilder.Main;
import net.gravijet.fastbuilder.gui.DistanceGui;
import net.gravijet.fastbuilder.gui.IslandSelectorGui;
import net.gravijet.fastbuilder.gui.MaterialGui;
import net.gravijet.fastbuilder.model.BridgeDistance;
import net.gravijet.fastbuilder.model.BridgeMaterial;
import net.gravijet.fastbuilder.model.Island;
import net.gravijet.fastbuilder.model.MapTemplate;
import net.gravijet.fastbuilder.model.PlayerData;
import net.gravijet.fastbuilder.model.PlayerStats;
import net.gravijet.fastbuilder.util.ActionBarUtil;
import net.gravijet.fastbuilder.util.CC;
import net.gravijet.fastbuilder.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameManager {

    private final Main plugin;

    private final IslandManager      islandManager;
    private final HologramManager    hologramManager;
    private final NpcManager         npcManager;
    private final ScoreboardManager  scoreboardManager;
    private final StatsManager       statsManager;
    private final SchematicManager   schematicManager;
    private final MapManager         mapManager;

    private final DistanceGui        distanceGui      = new DistanceGui();
    private final MaterialGui        materialGui      = new MaterialGui();
    private final IslandSelectorGui  islandSelectorGui = new IslandSelectorGui();

    private final Map<UUID, PlayerData> playerData  = new HashMap<>();
    private final Map<UUID, BukkitTask> timerTasks  = new HashMap<>();
    /** Tracks which page a player has open in the island-selector GUI. */
    private final Map<UUID, Integer>    selectorPage = new HashMap<>();
    /** Tracks which page a player has open in the material GUI. */
    private final Map<UUID, Integer>    materialPage = new HashMap<>();

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
        this.schematicManager  = new SchematicManager(plugin);
        this.mapManager        = new MapManager(schematicManager);

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

        statsManager.load(uuid);
        PlayerData data = new PlayerData(uuid, defaultDistance, defaultMaterial);
        playerData.put(uuid, data);

        Island island = islandManager.createIsland(uuid);
        island.generateTargetPlatform(defaultDistance);

        npcManager.spawnNpc(island);
        hologramManager.createHologram(island, statsManager.get(uuid), defaultDistance);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            scoreboardManager.createBoard(player, data, statsManager.get(uuid));
            player.teleport(island.getSpawnLocation());
            giveHotbarItems(player);
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.sendMessage(CC.PREFIX + CC.c("&7Willkommen! &fRechtsklick auf den NPC um eine Map zu wählen."));
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
        mapManager.clearPlayer(uuid);
        selectorPage.remove(uuid);
        materialPage.remove(uuid);
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

        ActionBarUtil.send(player, CC.c("&c✗ &7Void! Nochmal versuchen."));
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

        String msg = CC.c("&a✔ &7Fertig in &e" + timeStr + (newRecord ? " &6&l★ Neuer Rekord!" : ""));
        player.sendMessage(CC.PREFIX + msg);
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

    // ── NPC / Map Selection ───────────────────────────────────────

    /** Called when the player right-clicks the NPC → open map selector. */
    public void onNpcRightClick(Player player) {
        openIslandSelector(player);
    }

    public void openIslandSelector(Player player) {
        int page = selectorPage.getOrDefault(player.getUniqueId(), 0);
        islandSelectorGui.open(player, mapManager, page);
    }

    /** Called from GuiListener when a map is chosen in the selector. */
    public void onMapSelected(Player player, String mapName) {
        UUID uuid = player.getUniqueId();
        player.closeInventory();

        if (mapName == null) {
            // null = Quick Practice → open distance GUI
            PlayerData data = playerData.get(uuid);
            if (data != null) distanceGui.open(player, data.getSelectedDistance());
            return;
        }

        MapTemplate template = mapManager.getTemplate(mapName);
        if (template == null) {
            player.sendMessage(CC.ERROR + "Map nicht gefunden.");
            return;
        }

        PlayerData data  = playerData.get(uuid);
        PlayerStats stats = statsManager.get(uuid);
        Island island    = islandManager.getIsland(uuid);
        if (data == null || island == null) return;

        // Stop current attempt
        if (data.isAttemptStarted()) {
            stopTimerTask(uuid);
            data.resetTimer();
            island.clearPlacedBlocks();
        }

        // Apply template
        island.clearTemplate();
        island.applyTemplate(template, schematicManager);
        mapManager.setPlayerTemplate(uuid, mapName);

        player.teleport(island.getSpawnLocation());
        giveHotbarItems(player);
        player.setFoodLevel(20);

        hologramManager.updateHologram(uuid, stats, data.getSelectedDistance());
        scoreboardManager.updateBoard(player, data, stats, null);

        player.sendMessage(CC.PREFIX + CC.c("&7Map gewechselt: " + template.getDisplayName()));
        player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 1.5f);
    }

    /** Handles page navigation in island selector. */
    public void onIslandSelectorNavigate(Player player, int delta) {
        int page = selectorPage.getOrDefault(player.getUniqueId(), 0) + delta;
        selectorPage.put(player.getUniqueId(), Math.max(0, page));
        islandSelectorGui.open(player, mapManager, selectorPage.get(player.getUniqueId()));
    }

    // ── Distance / Material ───────────────────────────────────────

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
        // Only regenerate procedural target if not in template mode
        if (!island.isTemplateMode()) {
            island.generateTargetPlatform(distance);
        }
        player.closeInventory();

        hologramManager.updateHologram(uuid, stats, distance);
        scoreboardManager.updateBoard(player, data, stats, null);

        player.sendMessage(CC.PREFIX + CC.c("&7Distanz: "
                + distance.getColorCode() + distance.getDisplayName()
                + "&7 (&f" + distance.getDistance() + " Blöcke&7)."));
        player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 1.5f);
    }

    public void onMaterialSelected(Player player, BridgeMaterial material) {
        PlayerData data = playerData.get(player.getUniqueId());
        if (data == null) return;
        data.setSelectedMaterial(material);
        player.closeInventory();
        giveHotbarItems(player);
        player.sendMessage(CC.PREFIX + CC.c("&7Material: &f" + material.getDisplayName()));
        player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 1.5f);
    }

    public void openMaterialGui(Player player) {
        PlayerData data = playerData.get(player.getUniqueId());
        if (data == null) return;
        int page = materialPage.getOrDefault(player.getUniqueId(), 0);
        materialGui.open(player, data.getSelectedMaterial(), page);
    }

    public void onMaterialGuiNavigate(Player player, int delta) {
        int page = materialPage.getOrDefault(player.getUniqueId(), 0) + delta;
        materialPage.put(player.getUniqueId(), Math.max(0, page));
        PlayerData data = playerData.get(player.getUniqueId());
        if (data != null) materialGui.open(player, data.getSelectedMaterial(), materialPage.get(player.getUniqueId()));
    }

    // ── Spawn Commands ────────────────────────────────────────────

    public void setSpawn(Player player) {
        Island island = islandManager.getIsland(player.getUniqueId());
        if (island == null) { player.sendMessage(CC.ERROR + "Keine Insel gefunden."); return; }
        island.setSpawnLocation(player.getLocation());
        player.sendMessage(CC.SUCCESS + "Spawnpunkt gesetzt.");
    }

    public void teleportToSpawn(Player player) {
        Island island = islandManager.getIsland(player.getUniqueId());
        if (island == null) { player.sendMessage(CC.ERROR + "Keine Insel gefunden."); return; }
        player.teleport(island.getSpawnLocation());
        player.sendMessage(CC.SUCCESS + "Zum Spawn teleportiert.");
    }

    public void resetAttempt(Player player) {
        UUID uuid   = player.getUniqueId();
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

    @SuppressWarnings("deprecation")
    public void giveHotbarItems(Player player) {
        PlayerData data = playerData.get(player.getUniqueId());
        if (data == null) return;

        player.getInventory().clear();

        // Slots 0-1: 2 stacks of bridging material
        ItemStack blockStack = data.getSelectedMaterial().toHotbarStack();
        player.getInventory().setItem(0, blockStack);
        player.getInventory().setItem(1, blockStack.clone());

        // Slot 4: compass → open distance selector (quick-practice)
        player.getInventory().setItem(4,
                new ItemBuilder(Material.COMPASS)
                        .name("&c&lDistanz wählen")
                        .lore("&7Rechtsklick zum Auswählen")
                        .build());

        // Slot 6: book → open material selector
        player.getInventory().setItem(6,
                new ItemBuilder(Material.BOOK)
                        .name("&c&lMaterial wählen")
                        .lore("&7Rechtsklick zum Auswählen")
                        .build());

        // Slot 8: player skull → open island selector
        ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        skullMeta.setOwner(player.getName());
        skullMeta.setDisplayName(CC.c("&c&lMap wechseln"));
        skullMeta.setLore(java.util.Arrays.asList(CC.c("&7Rechtsklick zum Auswählen")));
        skull.setItemMeta(skullMeta);
        player.getInventory().setItem(8, skull);

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

    public IslandManager      getIslandManager()     { return islandManager;     }
    public StatsManager       getStatsManager()      { return statsManager;      }
    public NpcManager         getNpcManager()        { return npcManager;        }
    public ScoreboardManager  getScoreboardManager() { return scoreboardManager; }
    public MapManager         getMapManager()        { return mapManager;        }
    public SchematicManager   getSchematicManager()  { return schematicManager;  }
    public PlayerData         getPlayerData(UUID id) { return playerData.get(id); }

    public int getMaterialPage(Player player) {
        return materialPage.getOrDefault(player.getUniqueId(), 0);
    }

    public int getSelectorPage(Player player) {
        return selectorPage.getOrDefault(player.getUniqueId(), 0);
    }
}
