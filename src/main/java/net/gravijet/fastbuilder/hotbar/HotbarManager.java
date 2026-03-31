package net.gravijet.fastbuilder.hotbar;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.replay.ReplaySession;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Manages hotbar items and their right-click interactions.
 */
public class HotbarManager implements Listener {

    private final FastBuilder plugin;

    // Hotbar slot assignments
    private static final int SLOT_LEAVE = 8;
    private static final int SLOT_SETTINGS = 7;
    private static final int SLOT_ISLAND_SELECTOR = 6;
    private static final int SLOT_REPLAY = 5;

    // Block slots (2 stacks of blocks + 1 pickaxe)
    private static final int SLOT_BLOCK_1 = 0;
    private static final int SLOT_BLOCK_2 = 1;
    private static final int SLOT_PICKAXE = 2;

    public HotbarManager(FastBuilder plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Give all hotbar items to a player.
     */
    public void giveItems(Player player) {
        FileConfiguration items = plugin.getConfigManager().getItemsConfig();
        player.getInventory().clear();

        // 2 stacks of building blocks (slots 0-1)
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        // Check if in practice mode
        net.gravijet.fastbuilder.gameplay.RunSession runSession = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
        boolean inPractice = runSession != null && runSession.isPracticeMode();
        if (inPractice) {
            // Practice mode: give stained hardened clay (lime = STAINED_CLAY:5)
            ItemStack clay1 = new ItemBuilder(Material.STAINED_CLAY, (byte) 5).amount(64)
                    .name("&r&aPractice Blocks").build();
            ItemStack clay2 = new ItemBuilder(Material.STAINED_CLAY, (byte) 5).amount(64)
                    .name("&r&aPractice Blocks").build();
            player.getInventory().setItem(SLOT_BLOCK_1, clay1);
            player.getInventory().setItem(SLOT_BLOCK_2, clay2);
        } else if (data != null) {
            String block = data.getSelectedBlock();
            if (block != null && !block.isEmpty()) {
                ItemStack blockItem1 = ItemBuilder.fromString(block).amount(64).build();
                ItemStack blockItem2 = ItemBuilder.fromString(block).amount(64).build();
                player.getInventory().setItem(SLOT_BLOCK_1, blockItem1);
                player.getInventory().setItem(SLOT_BLOCK_2, blockItem2);
            }
        }

        // Diamond pickaxe (slot 2) - unbreakable
        ItemStack pickaxe = new ItemBuilder(Material.DIAMOND_PICKAXE)
                .name("&r&bPickaxe")
                .build();
        org.bukkit.inventory.meta.ItemMeta picMeta = pickaxe.getItemMeta();
        if (picMeta != null) {
            picMeta.spigot().setUnbreakable(true);
            pickaxe.setItemMeta(picMeta);
        }
        player.getInventory().setItem(SLOT_PICKAXE, pickaxe);

        // Replay item
        String replayName = items.getString("replay-item", "&5Replay View &7(Right-Click to use)");
        String replayMat = items.getString("replay-item-material", "BOOK:0");
        player.getInventory().setItem(SLOT_REPLAY, ItemBuilder.fromString(replayMat).name(replayName).build());

        // Island selector item
        String islandName = items.getString("islandselector-item", "&6Island Selector &7(Right-Click to use)");
        String islandMat = items.getString("islandselector-item-material", "NETHER_STAR:0");
        player.getInventory().setItem(SLOT_ISLAND_SELECTOR, ItemBuilder.fromString(islandMat).name(islandName).build());

        // Settings item
        String settingsName = items.getString("settings-item", "&2Settings &7(Right-Click to use)");
        String settingsMat = items.getString("settings-item-material", "EMERALD:0");
        player.getInventory().setItem(SLOT_SETTINGS, ItemBuilder.fromString(settingsMat).name(settingsName).build());

        // Leave item
        boolean leaveToggled = items.getBoolean("leave-item-toggled", false);
        if (!leaveToggled) {
            String leaveName = items.getString("leave-item", "&cLeave &7(Right-Click to use)");
            String leaveMat = items.getString("leave-item-material", "EYE_OF_ENDER:0");
            player.getInventory().setItem(SLOT_LEAVE, ItemBuilder.fromString(leaveMat).name(leaveName).build());
        }
    }

    /**
     * Refresh both building block slots.
     */
    public void updateBlockSlot(Player player) {
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data != null) {
            String block = data.getSelectedBlock();
            if (block != null && !block.isEmpty()) {
                ItemStack blockItem1 = ItemBuilder.fromString(block).amount(64).build();
                ItemStack blockItem2 = ItemBuilder.fromString(block).amount(64).build();
                player.getInventory().setItem(SLOT_BLOCK_1, blockItem1);
                player.getInventory().setItem(SLOT_BLOCK_2, blockItem2);
            }
        }
    }

    /**
     * Refill block stacks to 64 if the player has the auto-refill perk.
     */
    public void checkAutoRefill(Player player) {
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null || !data.hasAutoRefill()) return;

        ItemStack slot1 = player.getInventory().getItem(SLOT_BLOCK_1);
        ItemStack slot2 = player.getInventory().getItem(SLOT_BLOCK_2);

        if (slot1 != null && slot1.getType() != Material.AIR && slot1.getAmount() < 64) {
            slot1.setAmount(64);
        }
        if (slot2 != null && slot2.getType() != Material.AIR && slot2.getAmount() < 64) {
            slot2.setAmount(64);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;

        String displayName = item.getItemMeta().getDisplayName();
        FileConfiguration items = plugin.getConfigManager().getItemsConfig();

        // Check if player is in replay mode
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            handleReplayControls(player, event);
            return;
        }

        // Leave item
        String leaveName = ColorUtil.translate(items.getString("leave-item", ""));
        if (displayName.equals(leaveName)) {
            event.setCancelled(true);
            handleLeave(player);
            return;
        }

        // Replay item
        String replayName = ColorUtil.translate(items.getString("replay-item", ""));
        if (displayName.equals(replayName)) {
            event.setCancelled(true);
            handleReplayOpen(player);
            return;
        }

        // Island selector item
        String islandName = ColorUtil.translate(items.getString("islandselector-item", ""));
        if (displayName.equals(islandName)) {
            event.setCancelled(true);
            handleIslandSelector(player);
            return;
        }

        // Settings item
        String settingsName = ColorUtil.translate(items.getString("settings-item", ""));
        if (displayName.equals(settingsName)) {
            event.setCancelled(true);
            plugin.getGuiManager().openSettings(player);
            return;
        }
    }

    private void handleLeave(Player player) {
        plugin.getMapManager().freeAllIslands(player.getUniqueId());
        plugin.getGameplayManager().removeSession(player.getUniqueId());

        if (plugin.getConfigManager().isBungeeEnabled()) {
            String lobbyServer = plugin.getConfigManager().getLobbyServer();
            try {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(b);
                out.writeUTF("Connect");
                out.writeUTF(lobbyServer);
                player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            } catch (IOException e) {
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        } else {
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    private void handleReplayOpen(Player player) {
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null || data.getLastMap() == null) return;
        plugin.getGuiManager().openReplaySelector(player, data.getLastMap());
    }

    private void handleIslandSelector(Player player) {
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null || data.getLastMap() == null) return;

        net.gravijet.fastbuilder.map.MapData map = plugin.getMapManager().getMap(data.getLastMap());
        if (map != null) {
            plugin.getGuiManager().openIslandSelector(player, map);
        }
    }

    /**
     * Handle replay playback control items.
     */
    private void handleReplayControls(Player player, PlayerInteractEvent event) {
        event.setCancelled(true);

        ReplaySession session = plugin.getReplayManager().getPlaybackSession(player.getUniqueId());
        if (session == null) return;

        int slot = player.getInventory().getHeldItemSlot();

        switch (slot) {
            case ReplaySession.SLOT_REWIND:
                session.rewind(100); // 5 seconds = 100 ticks
                break;
            case ReplaySession.SLOT_SLOW:
                session.setPlaybackSpeed(session.getPlaybackSpeed() - 0.25);
                session.updateControlItems();
                break;
            case ReplaySession.SLOT_PAUSE:
                session.togglePause();
                session.updateControlItems();
                break;
            case ReplaySession.SLOT_FAST:
                session.setPlaybackSpeed(session.getPlaybackSpeed() + 0.25);
                session.updateControlItems();
                break;
            case ReplaySession.SLOT_FORWARD:
                session.fastForward(100); // 5 seconds
                break;
            case ReplaySession.SLOT_STOP:
                plugin.getReplayManager().stopPlayback(player.getUniqueId());
                break;
        }
    }
}
