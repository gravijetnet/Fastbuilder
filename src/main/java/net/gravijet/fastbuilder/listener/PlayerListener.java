package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.Main;
import net.gravijet.fastbuilder.manager.GameManager;
import net.gravijet.fastbuilder.model.Island;
import net.gravijet.fastbuilder.model.PlayerData;
import net.gravijet.fastbuilder.util.CC;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerListener implements Listener {

    private final GameManager gameManager;

    public PlayerListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    // ── Join / Leave ──────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        gameManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gameManager.onPlayerLeave(event.getPlayer());
    }

    // ── Block Place ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();
        Block  block  = event.getBlockPlaced();

        Island island = gameManager.getIslandManager().getIsland(uuid);
        if (island == null) return;

        if (!island.isOnThisIsland(block.getLocation())) {
            event.setCancelled(true);
            return;
        }

        if (!island.isInBridgeArea(block.getLocation())) {
            event.setCancelled(true);
            return;
        }

        PlayerData data = gameManager.getPlayerData(uuid);
        if (data != null && !island.isTemplateMode()
                && block.getType() != data.getSelectedMaterial().getMaterial()) {
            event.setCancelled(true);
            return;
        }

        island.trackPlacedBlock(block);

        if (data != null && !data.isTimerRunning()) {
            gameManager.onFirstBlockPlaced(player);
        }
    }

    // ── Block Break ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Island island = gameManager.getIslandManager().getIsland(player.getUniqueId());
        if (island == null) return;

        if (!island.isInBridgeArea(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ── Movement: Void + Target ───────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();
        Island island = gameManager.getIslandManager().getIsland(uuid);
        if (island == null) return;

        if (island.isInVoid(event.getTo())) {
            gameManager.onVoidFall(player);
            return;
        }

        PlayerData data = gameManager.getPlayerData(uuid);
        if (data != null && data.isAttemptStarted()) {
            org.bukkit.Location feet = event.getTo().clone();
            feet.setY(Math.floor(feet.getY()));
            if (island.isOnTarget(feet)) {
                gameManager.onPressurePlate(player);
            }
        }
    }

    // ── Hotbar Item Interactions ──────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getItemInHand() == null) return;

        Material held = player.getItemInHand().getType();

        if (held == Material.COMPASS) {
            event.setCancelled(true);
            // Open distance GUI (quick-practice only)
            PlayerData data = gameManager.getPlayerData(player.getUniqueId());
            if (data != null) gameManager.onNpcRightClick(player);
        } else if (held == Material.BOOK) {
            event.setCancelled(true);
            gameManager.openMaterialGui(player);
        } else if (held == Material.SKULL_ITEM) {
            event.setCancelled(true);
            gameManager.openIslandSelector(player);
        }
    }

    // ── NPC Interaction ───────────────────────────────────────────

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        UUID   npcUuid = clicked.getUniqueId();

        if (!gameManager.getNpcManager().isNpc(npcUuid)) return;
        event.setCancelled(true);

        UUID ownerUuid = gameManager.getNpcManager().getOwner(npcUuid);
        Player player  = event.getPlayer();

        if (ownerUuid == null || !player.getUniqueId().equals(ownerUuid)) {
            player.sendMessage(CC.ERROR + "Dieser NPC gehört dir nicht.");
            return;
        }

        gameManager.onNpcRightClick(player);
    }

    // ── Damage / Death ────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (gameManager.getIslandManager().getIsland(player.getUniqueId()) != null) {
                if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (gameManager.getIslandManager().getIsland(player.getUniqueId()) == null) return;

        event.setDeathMessage(null);
        event.getDrops().clear();

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) gameManager.onVoidFall(player);
            }
        }.runTaskLater(Main.getInstance(), 1L);
    }

    // ── Drop prevention ───────────────────────────────────────────

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (gameManager.getIslandManager().getIsland(event.getPlayer().getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }
}
