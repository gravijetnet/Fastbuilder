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
    private static final int SLOT_ISLAND_SELECTOR = 4;
    private static final int SLOT_SHOP = 5;
    private static final int SLOT_REPLAY = 6;
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
        // One-Click Pick overrides the tool to a Diamond Axe with a custom name.
        // All other pickaxes use their natural Minecraft item name (no custom display name).
        String pickaxeMat;
        boolean isOneClickPick = data != null && data.hasOneClickPick();
        if (isOneClickPick) {
            pickaxeMat = "DIAMOND_AXE:0";
        } else {
            pickaxeMat = (data != null && data.getSelectedPickaxe() != null && !data.getSelectedPickaxe().isEmpty())
                    ? data.getSelectedPickaxe() : "DIAMOND_PICKAXE:0";
        }
        ItemBuilder pickaxeBuilder = ItemBuilder.fromString(pickaxeMat);
        if (isOneClickPick) {
            pickaxeBuilder = pickaxeBuilder.name("&6One Click Pick");
        }
        ItemStack pickaxe = pickaxeBuilder.build();
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

        // Slot 4: Island selector
        String islandName = items.getString("islandselector-item", "&6Island Selector &7(Right-Click to use)");
        String islandMat = items.getString("islandselector-item-material", "NETHER_STAR:0");
        player.getInventory().setItem(SLOT_ISLAND_SELECTOR, ItemBuilder.fromString(islandMat).name(islandName).build());

        // Slot 5: Shop item
        String shopName = items.getString("shop-item", "&6Shop &7(Right-Click to use)");
        String shopMat = items.getString("shop-item-material", "GOLD_NUGGET:0");
        player.getInventory().setItem(SLOT_SHOP, ItemBuilder.fromString(shopMat).name(shopName).build());

        // Slot 6: Replay item
        String replayName = items.getString("replay-item", "&5Replay View &7(Right-Click to use)");
        String replayMat = items.getString("replay-item-material", "BOOK:0");
        player.getInventory().setItem(SLOT_REPLAY, ItemBuilder.fromString(replayMat).name(replayName).build());

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

        // Infinite blocks also keeps practice blocks topped up
        if (data.hasInfiniteBlocks()) {
            ItemStack practiceSlot = player.getInventory().getItem(SLOT_PRACTICE_BLOCKS);
            if (practiceSlot != null && practiceSlot.getType() != Material.AIR && practiceSlot.getAmount() < 64) {
                practiceSlot.setAmount(64);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        // Replay controls respond to both left- and right-click
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK
                    || action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                handleReplayControls(player, event);
            }
            return;
        }

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;

        String displayName = item.getItemMeta().getDisplayName();
        FileConfiguration items = plugin.getConfigManager().getItemsConfig();

        String leaveName = ColorUtil.translate(items.getString("leave-item", ""));
        if (!leaveName.isEmpty() && displayName.equals(leaveName)) {
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
        // Stop active replay / recording first
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
            plugin.getGameplayManager().removeGlobalSessionBest(player.getUniqueId());
        }

        // Despawn NPC
        if (plugin.getNpcManager() != null) {
            plugin.getNpcManager().despawnNpc(player.getUniqueId());
        }

        // Remove hologram
        if (plugin.getHologramManager() != null) {
            net.gravijet.fastbuilder.player.PlayerData d =
                    plugin.getPlayerManager().getCachedData(player.getUniqueId());
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

        // Execute leave action
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
                    // BungeeCord failed — kick so the proxy can route to a fallback server
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
                } else if (!Bukkit.getWorlds().isEmpty()) {
                    player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                }
                break;
            }
            default: // "SPAWN" or anything else
                if (!Bukkit.getWorlds().isEmpty()) player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                break;
        }
    }

    private void handleReplayOpen(Player player) {
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null || data.getLastMap() == null) return;
        plugin.getGuiManager().openReplayGui(player, data.getLastMap(), false);
    }

    private void handleIslandSelector(Player player) {
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null || data.getLastMap() == null) return;

        net.gravijet.fastbuilder.map.MapData map = plugin.getMapManager().getMap(data.getLastMap());
        if (map != null) plugin.getGuiManager().openIslandSelector(player, map);
    }

    @EventHandler
    public void onPlayerSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (plugin.getReplayManager() == null || !plugin.getReplayManager().isInPlayback(player.getUniqueId())) return;
        ReplaySession session = plugin.getReplayManager().getPlaybackSession(player.getUniqueId());
        if (session != null && session.isInNpcCamera()) {
            session.toggleNpcCamera(player);
        } else {
            plugin.getReplayManager().stopPlayback(player.getUniqueId());
        }
    }

    private void handleReplayControls(Player player, PlayerInteractEvent event) {
        event.setCancelled(true);

        ReplaySession session = plugin.getReplayManager().getPlaybackSession(player.getUniqueId());
        if (session == null) return;

        // Any click exits the NPC camera mode
        if (session.isInNpcCamera()) {
            session.toggleNpcCamera(player);
            return;
        }

        int slot = player.getInventory().getHeldItemSlot();
        boolean leftClick = event.getAction() == Action.LEFT_CLICK_AIR
                || event.getAction() == Action.LEFT_CLICK_BLOCK;

        switch (slot) {
            case ReplaySession.SLOT_TIMELINE:
                // Left = rewind 0.5 s, Right = fast-forward 0.5 s
                if (leftClick) session.rewind(10);
                else           session.fastForward(10);
                break;
            case ReplaySession.SLOT_PAUSE_RESUME:
                // When replay has ended, the Lime Dye in this slot acts as "restart"
                if (session.isEnded()) {
                    session.restart();
                } else {
                    session.togglePause();
                    session.updateControlItems();
                }
                break;
            case ReplaySession.SLOT_SPEED:
                // Finer steps at slow speeds so the viewer can reach 0.05× (1 fps)
                // without large jumps near the bottom of the range.
                double speed = session.getPlaybackSpeed();
                if (leftClick) {
                    double step = speed <= 0.25 ? 0.05 : (speed <= 1.0 ? 0.25 : 0.5);
                    session.setPlaybackSpeed(speed - step);
                } else {
                    double step = speed < 0.25 ? 0.05 : (speed < 1.0 ? 0.25 : 0.5);
                    session.setPlaybackSpeed(speed + step);
                }
                session.updateControlItems();
                break;
            case ReplaySession.SLOT_STOP:
                plugin.getReplayManager().stopPlayback(player.getUniqueId());
                break;
            default:
                plugin.getReplayManager().stopPlayback(player.getUniqueId());
                break;
        }
    }
}
