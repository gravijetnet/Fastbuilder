package de.fastbuilder.manager;

import de.fastbuilder.FastBuilderPlugin;
import de.fastbuilder.gui.DistanceGui;
import de.fastbuilder.model.BridgeDistance;
import de.fastbuilder.model.Island;
import de.fastbuilder.model.PlayerData;
import de.fastbuilder.util.ActionBarUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Zentraler Koordinator des FastBuilder-Plugins.
 *
 * Verantwortlichkeiten:
 *  - Spieler beim Joinen/Verlassen verwalten
 *  - Spielablauf steuern (Start, Versuch, Reset, Erfolg)
 *  - Sub-Manager koordinieren (Island, Hologram, NPC)
 *  - Timer-Ticker verwalten
 */
public class GameManager {

    private final FastBuilderPlugin plugin;

    // Sub-Manager
    private final IslandManager  islandManager;
    private final HologramManager hologramManager;
    private final NpcManager      npcManager;

    // GUI-Instanz (zustandslos, eine Instanz reicht)
    private final DistanceGui distanceGui;

    // Spieler-Statistiken (UUID → PlayerData)
    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();

    // Laufende Timer-Tasks (UUID → BukkitTask) für die ActionBar
    private final Map<UUID, BukkitTask> timerTasks = new HashMap<>();

    public GameManager(FastBuilderPlugin plugin) {
        this.plugin          = plugin;
        this.islandManager   = new IslandManager(plugin.getConfig().getString("world", "world"));
        this.hologramManager = new HologramManager();
        this.npcManager      = new NpcManager();
        this.distanceGui     = new DistanceGui();
    }

    // ─────────────────────────────────────────────────────────────
    //  SPIELER JOIN / LEAVE
    // ─────────────────────────────────────────────────────────────

    /**
     * Wird aufgerufen wenn ein Spieler jointed.
     * Erstellt Insel, PlayerData, Hologramm, NPC.
     */
    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();

        // PlayerData anlegen
        playerDataMap.put(uuid, new PlayerData(uuid));

        // Insel erstellen und Startplattform generieren
        Island island = islandManager.createIsland(uuid);

        // NPC spawnen
        npcManager.spawnNpc(island);

        // Hologramm spawnen
        PlayerData data = playerDataMap.get(uuid);
        hologramManager.createHologram(island, data);

        // Spieler auf die Insel teleportieren
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.teleport(island.getSpawnLocation());
            giveDefaultItems(player);
            player.sendMessage(ChatColor.GOLD + "[FastBuilder] " + ChatColor.YELLOW
                    + "Willkommen! Rechtsklicke den NPC um eine Distanz zu wählen.");
        }, 5L); // kurze Verzögerung nach Join
    }

    /**
     * Wird aufgerufen wenn ein Spieler die Verbindung trennt.
     * Räumt Ressourcen auf.
     */
    public void onPlayerLeave(Player player) {
        UUID uuid = player.getUniqueId();
        stopTimerTask(uuid);
        npcManager.removeNpc(uuid);
        hologramManager.removeHologram(uuid);
        islandManager.removeIsland(uuid);
        playerDataMap.remove(uuid);
    }

    // ─────────────────────────────────────────────────────────────
    //  SPIELABLAUF
    // ─────────────────────────────────────────────────────────────

    /**
     * Wird aufgerufen wenn der Spieler seinen ersten Block platziert.
     * Startet den Timer und markiert den Versuch als begonnen.
     */
    public void onFirstBlockPlaced(Player player) {
        PlayerData data = playerDataMap.get(player.getUniqueId());
        if (data == null || data.isTimerRunning()) return;

        data.startTimer();
        startTimerTask(player);
        player.sendMessage(ChatColor.YELLOW + "▶ Timer gestartet!");
    }

    /**
     * Wird aufgerufen wenn der Spieler in den Void fällt.
     * Zählt den Versuch als fehlgeschlagen.
     */
    public void onVoidFall(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = playerDataMap.get(uuid);
        Island island    = islandManager.getIsland(uuid);
        if (data == null || island == null) return;

        // Nur zählen wenn Versuch begonnen (Block gesetzt)
        if (data.isAttemptStarted()) {
            data.incrementAttempts();
            hologramManager.updateHologram(uuid, data);
        }

        // Timer stoppen und UI leeren
        stopTimerTask(uuid);
        data.resetTimer();

        // Blöcke aufräumen
        island.clearPlacedBlocks();

        // Spieler wiederbeleben und zurückteleportieren
        player.setHealth(player.getMaxHealth());
        player.teleport(island.getSpawnLocation());
        giveDefaultItems(player);

        ActionBarUtil.sendActionBar(player, ChatColor.RED + "✗ Void! Neuer Versuch.");
        player.playSound(player.getLocation(), Sound.ENDERMAN_TELEPORT, 1f, 0.8f);
    }

    /**
     * Wird aufgerufen wenn der Spieler die Druckplatte betritt.
     * Zählt den Versuch als erfolgreich und stoppt den Timer.
     */
    public void onPressurePlate(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = playerDataMap.get(uuid);
        Island island    = islandManager.getIsland(uuid);
        if (data == null || island == null) return;
        if (!data.isAttemptStarted()) return; // Kein aktiver Versuch

        // Timer stoppen
        stopTimerTask(uuid);
        long elapsed = data.stopTimer();

        // Statistiken aktualisieren
        data.incrementAttempts();
        data.recordSuccess(elapsed);
        hologramManager.updateHologram(uuid, data);

        // Neue Bestzeit?
        boolean newRecord = data.getBestTimeMillis() == elapsed;
        String timeStr = String.format("%.2fs", elapsed / 1000.0);

        // Feedback
        String msg = ChatColor.GREEN + "✔ Geschafft in " + ChatColor.GOLD + timeStr;
        if (newRecord) msg += ChatColor.AQUA + " ★ Neue Bestzeit!";
        player.sendMessage(ChatColor.GOLD + "[FastBuilder] " + msg);
        ActionBarUtil.sendActionBar(player, msg);
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1f);

        // Blöcke aufräumen und zurückteleportieren
        island.clearPlacedBlocks();
        data.resetTimer();
        Player p = player; // für Lambda
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            p.teleport(island.getSpawnLocation());
            giveDefaultItems(p);
        }, 20L); // 1 Sekunde Verzögerung damit der Spieler Feedback sieht
    }

    /**
     * Wird aufgerufen wenn der Spieler den NPC rechtsklickt.
     * Öffnet das Distanz-Auswahl-GUI.
     */
    public void onNpcRightClick(Player player) {
        distanceGui.open(player);
    }

    /**
     * Wird aufgerufen wenn der Spieler eine Distanz im GUI auswählt.
     * Generiert die Zielplattform neu.
     */
    public void onDistanceSelected(Player player, BridgeDistance distance) {
        UUID uuid = player.getUniqueId();
        Island island = islandManager.getIsland(uuid);
        if (island == null) return;

        // Laufenden Versuch abbrechen
        if (playerDataMap.containsKey(uuid)) {
            PlayerData data = playerDataMap.get(uuid);
            if (data.isAttemptStarted()) {
                stopTimerTask(uuid);
                data.resetTimer();
                island.clearPlacedBlocks();
            }
        }

        // Neue Zielplattform generieren
        island.generateTargetPlatform(distance);

        player.closeInventory();
        player.sendMessage(ChatColor.GOLD + "[FastBuilder] " + ChatColor.YELLOW
                + "Distanz geändert: " + distance.getColor() + distance.getDisplayName()
                + ChatColor.YELLOW + " (" + distance.getDistance() + " Blöcke)");
        player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 1.5f);
    }

    // ─────────────────────────────────────────────────────────────
    //  SPAWN-BEFEHLE
    // ─────────────────────────────────────────────────────────────

    /**
     * Setzt den Spawnpunkt der eigenen Insel auf die aktuelle Spieler-Position.
     */
    public void setSpawn(Player player) {
        Island island = islandManager.getIsland(player.getUniqueId());
        if (island == null) {
            player.sendMessage(ChatColor.RED + "Keine Insel gefunden!");
            return;
        }
        island.setSpawnLocation(player.getLocation());
        player.sendMessage(ChatColor.GREEN + "Spawnpunkt gesetzt!");
    }

    /**
     * Teleportiert den Spieler zu seinem Insel-Spawnpunkt.
     */
    public void teleportToSpawn(Player player) {
        Island island = islandManager.getIsland(player.getUniqueId());
        if (island == null) {
            player.sendMessage(ChatColor.RED + "Keine Insel gefunden!");
            return;
        }
        player.teleport(island.getSpawnLocation());
        player.sendMessage(ChatColor.GREEN + "Zum Spawn teleportiert!");
    }

    // ─────────────────────────────────────────────────────────────
    //  TIMER
    // ─────────────────────────────────────────────────────────────

    /**
     * Startet den ActionBar-Timer-Ticker für einen Spieler.
     * Läuft jede Tick (50ms) und aktualisiert die ActionBar-Anzeige.
     */
    private void startTimerTask(Player player) {
        UUID uuid = player.getUniqueId();
        stopTimerTask(uuid); // Sicherstellen, dass kein alter Task läuft

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                // Spieler noch online?
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) {
                    cancel();
                    return;
                }
                PlayerData data = playerDataMap.get(uuid);
                if (data == null || !data.isTimerRunning()) {
                    cancel();
                    return;
                }
                // ActionBar aktualisieren
                String timeStr = data.getFormattedCurrentTime();
                ActionBarUtil.sendActionBar(p,
                        ChatColor.YELLOW + "⏱ " + ChatColor.WHITE + timeStr);
            }
        }.runTaskTimer(plugin, 0L, 1L); // Jede 1 Tick (~50ms)

        timerTasks.put(uuid, task);
    }

    /**
     * Stoppt den ActionBar-Timer-Ticker eines Spielers.
     */
    private void stopTimerTask(UUID uuid) {
        BukkitTask task = timerTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  HILFSMETHODEN
    // ─────────────────────────────────────────────────────────────

    /**
     * Gibt dem Spieler die Standard-Items (Cobblestone zum Bridgen).
     */
    private void giveDefaultItems(Player player) {
        player.getInventory().clear();
        // Stack Cobblestone für die Brücke
        player.getInventory().setItem(0,
                new org.bukkit.inventory.ItemStack(org.bukkit.Material.COBBLESTONE, 64));
        player.getInventory().setItem(1,
                new org.bukkit.inventory.ItemStack(org.bukkit.Material.COBBLESTONE, 64));
        player.getInventory().setItem(2,
                new org.bukkit.inventory.ItemStack(org.bukkit.Material.COBBLESTONE, 64));
        player.getInventory().setHeldItemSlot(0);
    }

    /**
     * Räumt beim Plugin-Stop alle Ressourcen auf.
     */
    public void cleanup() {
        // Alle Timer stoppen
        for (BukkitTask task : timerTasks.values()) {
            task.cancel();
        }
        timerTasks.clear();

        // NPCs und Holograms entfernen
        npcManager.removeAll();
        hologramManager.removeAll();
    }

    // ─────────────────────────────────────────────────────────────
    //  GETTER
    // ─────────────────────────────────────────────────────────────

    public IslandManager   getIslandManager()   { return islandManager; }
    public HologramManager getHologramManager() { return hologramManager; }
    public NpcManager      getNpcManager()      { return npcManager; }
    public PlayerData      getPlayerData(UUID uuid) { return playerDataMap.get(uuid); }
    public DistanceGui     getDistanceGui()     { return distanceGui; }
}
