package de.fastbuilder.listener;

import de.fastbuilder.manager.GameManager;
import de.fastbuilder.model.Island;
import de.fastbuilder.model.PlayerData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Haupt-Event-Listener für das Spielgeschehen.
 *
 * Behandelt:
 *  - Join / Leave
 *  - Block-Platzieren (Timer starten, Block tracken)
 *  - Block-Abbauen (nur eigene Brückenblöcke)
 *  - Void-Erkennung (via PlayerMoveEvent)
 *  - NPC-Interaktion (rechtsklick → GUI)
 *  - Druckplatten-Erkennung (via PlayerMoveEvent)
 *  - Schaden-Verhinderung (Spieler im FastBuilder sollen nicht sterben)
 */
public class PlayerListener implements Listener {

    private final GameManager gameManager;

    public PlayerListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    // ─────────────────────────────────────────────────────────────
    //  JOIN / LEAVE
    // ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        gameManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        gameManager.onPlayerLeave(event.getPlayer());
    }

    // ─────────────────────────────────────────────────────────────
    //  BLOCK-PLATZIEREN
    // ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        UUID uuid     = player.getUniqueId();
        Block block   = event.getBlockPlaced();

        Island island = gameManager.getIslandManager().getIsland(uuid);
        if (island == null) return;

        // Sicherstellen, dass Block auf dieser Insel liegt
        if (!island.isOnThisIsland(block.getLocation())) {
            event.setCancelled(true); // Fremde Insel – nicht erlaubt
            return;
        }

        // Block im Brücken-Bereich?
        if (!island.isInBridgeArea(block.getLocation())) {
            // Außerhalb des Brückenbereichs (z.B. auf der Start-Plattform)
            event.setCancelled(true);
            return;
        }

        // Block für späteres Aufräumen tracken
        island.trackPlacedBlock(block);

        // Timer starten falls noch nicht gestartet (erster Block)
        PlayerData data = gameManager.getPlayerData(uuid);
        if (data != null && !data.isTimerRunning()) {
            gameManager.onFirstBlockPlaced(player);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  BLOCK-ABBAUEN
    // ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Island island = gameManager.getIslandManager().getIsland(player.getUniqueId());
        if (island == null) return;

        Block block = event.getBlock();

        // Nur Blöcke im Brückenbereich abbauen erlauben
        if (!island.isInBridgeArea(block.getLocation())) {
            event.setCancelled(true);
        }
        // Hinweis: Getrackte Blöcke werden beim clearPlacedBlocks automatisch entfernt.
        // Falls ein Spieler selbst abbaut, müssen wir die Liste aktualisieren.
        // Das ist ok – beim Reset werden nur noch vorhandene Blöcke entfernt.
    }

    // ─────────────────────────────────────────────────────────────
    //  MOVEMENT: VOID + DRUCKPLATTE
    // ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        // Optimierung: Nur prüfen wenn Spieler die Block-Position gewechselt hat
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid     = player.getUniqueId();
        Island island = gameManager.getIslandManager().getIsland(uuid);
        if (island == null) return;

        // ── Void-Check ──────────────────────────────────────────
        if (island.isInVoid(event.getTo())) {
            gameManager.onVoidFall(player);
            return;
        }

        // ── Druckplatten-Check ──────────────────────────────────
        PlayerData data = gameManager.getPlayerData(uuid);
        if (data != null && data.isAttemptStarted()) {
            // Block unter dem Spieler prüfen (Spieler steht halb auf dem Block)
            // Prüfe sowohl aktuelle als auch -1 Y Position
            org.bukkit.Location feet = event.getTo().clone();
            feet.setY(Math.floor(feet.getY())); // Block-Level
            if (island.isPressurePlate(feet)) {
                gameManager.onPressurePlate(player);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  NPC-INTERAKTION
    // ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        // Nur Rechtsklick auf Villager (NPCs)
        if (!(event.getRightClicked() instanceof org.bukkit.entity.Villager)) return;

        UUID npcUuid = event.getRightClicked().getUniqueId();

        // Ist es unser verwalteter NPC?
        if (!gameManager.getNpcManager().isNpc(npcUuid)) return;

        event.setCancelled(true); // Standard-Händler-Dialog verhindern

        UUID ownerUuid = gameManager.getNpcManager().getOwnerOfNpc(npcUuid);
        if (ownerUuid == null) return;

        // Nur der Besitzer darf mit dem NPC interagieren
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(ownerUuid)) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Das ist nicht dein NPC!");
            return;
        }

        gameManager.onNpcRightClick(player);
    }

    // ─────────────────────────────────────────────────────────────
    //  SCHADEN / TOD VERHINDERN
    // ─────────────────────────────────────────────────────────────

    /**
     * Verhindert normalen Schaden für Spieler im FastBuilder-Modus.
     * Void-Behandlung erfolgt separat in onMove.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        UUID uuid = player.getUniqueId();

        // Falls Spieler auf einer Insel ist → Schaden canceln (kein PvP, kein Fallschaden)
        if (gameManager.getIslandManager().getIsland(uuid) != null) {
            // Void-Schaden erlauben (wird in onMove behandelt)
            if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
                event.setCancelled(true);
            }
        }

        // Schaden an NPCs verhindern
        if (event.getEntity() instanceof org.bukkit.entity.Villager) {
            if (gameManager.getNpcManager().isNpc(event.getEntity().getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Verhindert Tod-Bildschirm und teleportiert den Spieler direkt zurück.
     * (Als zusätzliche Sicherung falls Void-Schaden nicht gecancelt wird)
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (gameManager.getIslandManager().getIsland(uuid) != null) {
            event.setDeathMessage(null); // Keine Death-Message
            event.getDrops().clear();   // Keine Item-Drops
            // Respawn verzögert (Bukkit erfordert 1 Tick Verzögerung)
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    gameManager.onVoidFall(player);
                }
            }.runTaskLater(
                    de.fastbuilder.FastBuilderPlugin.getInstance(), 1L
            );
        }
    }
}
