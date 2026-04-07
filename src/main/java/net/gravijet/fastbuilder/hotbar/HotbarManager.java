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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Manages hotbar items and their right-click interactions.
 */
public class HotbarManager implements Listener {

    private final FastBuilder plugin;

    private static final int SLOT_BLOCK_1 = 0;
    private static final int SLOT_BLOCK_2 = 1;
    private static final int SLOT_PICKAXE = 2;
    private static final int SLOT_PRACTICE_BLOCKS = 3;
    private static final int SLOT_SHOP = 4;

    private static final int SLOT_REPLAY = 5;
    private static final int SLOT_ISLAND_SELECTOR = 6;
    private static final int SLOT_SETTINGS = 7;
    private static final int SLOT_LEAVE = 8;

    public HotbarManager(FastBuilder plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Give all hotbar items to a player.
     * Normal blocks always go in slots 0-1.
     * Practice blocks go in slot 3 ADDITIONALLY when in practice mode.
     */
    public void giveItems(Player player) {
        FileConfiguration items = plugin.getConfigManager().getItemsConfig();
        player.getInventory().clear();

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        net.gravijet.fastbuilder.gameplay.RunSession runSession = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
        boolean inPractice = runSession != null && runSession.isPracticeMode();

        // Slots 0-1: always give normal (sandstone/selected) blocks
        if (data != null) {
            String block = data.getSelectedBlock();
            if (block != null && !block.isEmpty()) {
                ItemStack blockItem1 = ItemBuilder.fromString(block).amount(64).build();
                ItemStack blockItem2 = ItemBuilder.fromString(block).amount(64).build();
                player.getInventory().setItem(SLOT_BLOCK_1, blockItem1);
                player.getInventory().setItem(SLOT_BLOCK_2, blockItem2);
            }
        }

        // Slot 2: Pickaxe (selected by player, unbreakable)
        // One-Click Pick overrides the tool to a Diamond Axe
        String pickaxeMat;
        if (data != null && data.hasOneClickPick()) {
            pickaxeMat = "DIAMOND_AXE:0";
        } else {
            pickaxeMat = (data != null && data.getSelectedPickaxe() != null && !data.getSelectedPickaxe().isEmpty())
                    ? data.getSelectedPickaxe() : "DIAMOND_PICKAXE:0";
        }
        ItemStack pickaxe = ItemBuilder.fromString(pickaxeMat).name("&r&bPickaxe").build();
        org.bukkit.inventory.meta.ItemMeta picMeta = pickaxe.getItemMeta();
        if (picMeta != null) {
            picMeta.spigot().setUnbreakable(true);
            pickaxe.setItemMeta(picMeta);
        }
        player.getInventory().setItem(SLOT_PICKAXE, pickaxe);

        // Slot 3: Practice blocks ONLY if in practice mode
        if (inPractice) {
            ItemStack practiceStack = new ItemBuilder(Material.STAINED_CLAY, (byte) 5)
                    .amount(64)
                    .name("&r&aPractice Blocks")
                    .build();
            player.getInventory().setItem(SLOT_PRACTICE_BLOCKS, practiceStack);
        }

        // Slot 4: Shop item
        String shopName = items.getString("shop-item", "&6Shop &7(Right-Click to use)");
        String shopMat = items.getString("shop-item-material", "GOLD_NUGGET:0");
        player.getInventory().setItem(SLOT_SHOP, ItemBuilder.fromString(shopMat).name(shopName).build());

        // Slot 5: Replay item
        String replayName = items.getString("replay-item", "&5Replay View &7(Right-Click to use)");
        String replayMat = items.getString("replay-item-material", "BOOK:0");
        player.getInventory().setItem(SLOT_REPLAY, ItemBuilder.fromString(replayMat).name(replayName).build());

        // Slot 6: Island selector
        String islandName = items.getString("islandselector-item", "&6Island Selector &7(Right-Click to use)");
        String islandMat = items.getString("islandselector-item-material", "NETHER_STAR:0");
        player.getInventory().setItem(SLOT_ISLAND_SELECTOR, ItemBuilder.fromString(islandMat).name(islandName).build());

        // Slot 7: Settings
        String settingsName = items.getString("settings-item", "&2Settings &7(Right-Click to use)");
        String settingsMat = items.getString("settings-item-material", "EMERALD:0");
        player.getInventory().setItem(SLOT_SETTINGS, ItemBuilder.fromString(settingsMat).name(settingsName).build());

        // Slot 8: Leave
        boolean leaveToggled = items.getBoolean("leave-item-toggled", false);
        if (!leaveToggled) {
            String leaveName = items.getString("leave-item", "&cLeave &7(Right-Click to use)");
            String leaveMat = items.getString("leave-item-material", "EYE_OF_ENDER:0");
            player.getInventory().setItem(SLOT_LEAVE, ItemBuilder.fromString(leaveMat).name(leaveName).build());
        }
    }

    public void updateBlockSlot(Player player) {
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data != null) {
            String block = data.getSelectedBlock();
            if (block != null && !block.isEmpty()) {
                player.getInventory().setItem(SLOT_BLOCK_1, ItemBuilder.fromString(block).amount(64).build());
                player.getInventory().setItem(SLOT_BLOCK_2, ItemBuilder.fromString(block).amount(64).build());
            }
        }
    }

    public void checkAutoRefill(Player player) {
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;
        if (!data.hasAutoRefill() && !data.hasInfiniteBlocks()) return;

        ItemStack slot1 = player.getInventory().getItem(SLOT_BLOCK_1);
        ItemStack slot2 = player.getInventory().getItem(SLOT_BLOCK_2);

        if (slot1 != null && slot1.getType() != Material.AIR && slot1.getAmount() < 64) slot1.setAmount(64);
        if (slot2 != null && slot2.getType() != Material.AIR && slot2.getAmount() < 64) slot2.setAmount(64);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;

        String displayName = item.getItemMeta().getDisplayName();
        FileConfiguration items = plugin.getConfigManager().getItemsConfig();

        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            handleReplayControls(player, event);
            return;
        }

        String leaveName = ColorUtil.translate(items.getString("leave-item", ""));
        if (displayName.equals(leaveName)) {
            event.setCancelled(true);
            handleLeave(player);
            return;
        }

        String replayName = ColorUtil.translate(items.getString("replay-item", ""));
        if (displayName.equals(replayName)) {
            event.setCancelled(true);
            handleReplayOpen(player);
            return;
        }

        String islandName = ColorUtil.translate(items.getString("islandselector-item", ""));
        if (displayName.equals(islandName)) {
            event.setCancelled(true);
            handleIslandSelector(player);
            return;
        }

        String settingsName = ColorUtil.translate(items.getString("settings-item", ""));
        if (displayName.equals(settingsName)) {
            event.setCancelled(true);
            plugin.getGuiManager().openSettings(player);
            return;
        }

        String shopName = ColorUtil.translate(items.getString("shop-item", "&6Shop &7(Right-Click to use)"));
        if (displayName.equals(shopName)) {
            event.setCancelled(true);
            plugin.getGuiManager().openShop(player);
            return;
        }
    }

    private void handleLeave(Player player) {
        plugin.getMapManager().freeAllIslands(player.getUniqueId());
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());
            plugin.getGameplayManager().removeSession(player.getUniqueId());
        }

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
        plugin.getGuiManager().openReplaySelector(player, data.getLastMap(), false);
    }

    private void handleIslandSelector(Player player) {
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null || data.getLastMap() == null) return;

        net.gravijet.fastbuilder.map.MapData map = plugin.getMapManager().getMap(data.getLastMap());
        if (map != null) plugin.getGuiManager().openIslandSelector(player, map);
    }

    private void handleReplayControls(Player player, PlayerInteractEvent event) {
        event.setCancelled(true);

        ReplaySession session = plugin.getReplayManager().getPlaybackSession(player.getUniqueId());
        if (session == null) return;

        int slot = player.getInventory().getHeldItemSlot();

        switch (slot) {
            case ReplaySession.SLOT_REWIND:
                session.rewind(100);
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
                session.fastForward(100);
                break;
            case ReplaySession.SLOT_REPLAY_AGAIN:
                session.restart();
                break;
            case ReplaySession.SLOT_STOP:
                plugin.getReplayManager().stopPlayback(player.getUniqueId());
                break;
        }
    }
}
