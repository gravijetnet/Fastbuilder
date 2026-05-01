package net.gravijet.fastbuilder.gui;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

/**
 * Thin coordinator: registers as an event listener, holds all sub-GUI instances,
 * and routes inventory clicks/drags to the appropriate handler.
 */
public class GuiManager implements Listener {

    private final FastBuilder plugin;

    private final BlockSelectorGui blockSelectorGui;
    private final MapSelectorGui   mapSelectorGui;
    private final IslandSelectorGui islandSelectorGui;
    private final SettingsGui      settingsGui;
    private final ReplayGui        replayGui;
    private final BoosterGui       boosterGui;
    private final ShopGui          shopGui;
    private final StatsGui         statsGui;

    public GuiManager(FastBuilder plugin) {
        this.plugin = plugin;
        blockSelectorGui  = new BlockSelectorGui(plugin);
        mapSelectorGui    = new MapSelectorGui(plugin);
        islandSelectorGui = new IslandSelectorGui(plugin);
        settingsGui       = new SettingsGui(plugin);
        replayGui         = new ReplayGui(plugin);
        boosterGui        = new BoosterGui(plugin);
        shopGui           = new ShopGui(plugin);
        statsGui          = new StatsGui(plugin);
    }

    // ===== Public open methods (delegated) =====

    public void openBlockSelector(Player player, int page)                { blockSelectorGui.open(player, page); }
    public void openMapSelector(Player player)                             { mapSelectorGui.open(player); }
    public void openIslandSelector(Player player, MapData map)             { islandSelectorGui.open(player, map); }
    public void openIslandSelector(Player player, String mapName)          {
        MapData map = plugin.getMapManager().getMap(mapName);
        if (map != null) islandSelectorGui.open(player, map);
    }
    public void openSettings(Player player)                                { settingsGui.openSettings(player); }
    public void openReplayGui(Player player, String mapName, boolean favs) { replayGui.open(player, mapName, favs); }
    public void openBoosterHub(Player player)                              { boosterGui.openHub(player); }
    public void openBoosterShop(Player player)                             { boosterGui.openShop(player); }
    public void openBoosterInventory(Player player)                        { boosterGui.openInventory(player); }
    public void openShop(Player player)                                    { shopGui.open(player); }
    public void openStatsGui(Player viewer, PlayerData data)               { statsGui.openStatsGui(viewer, data); }
    public void openLeaderboardMapPicker(Player player)                    { statsGui.openLeaderboardMapPicker(player); }
    public void openLeaderboardGui(Player player, String mapName)          { statsGui.openLeaderboardGui(player, mapName); }

    // ===== isPluginGui =====

    private boolean isPluginGui(String stripped) {
        return stripped.startsWith(IslandSelectorGui.PREFIX)
                || stripped.startsWith(BlockSelectorGui.PREFIX)
                || stripped.startsWith(SettingsGui.SETTINGS_PREFIX)
                || stripped.startsWith(SettingsGui.CONFIRM_PREFIX)
                || stripped.startsWith(SettingsGui.CUSTOM_LENGTH_PREFIX)
                || stripped.startsWith(MapSelectorGui.PREFIX)
                || stripped.startsWith(ReplayGui.PREFIX)
                || stripped.startsWith(ShopGui.SHOP_PREFIX)
                || stripped.startsWith(ShopGui.PICKAXE_PREFIX)
                || stripped.startsWith(ShopGui.ANIMATION_PREFIX)
                || stripped.startsWith(ShopGui.DEATH_SOUND_PREFIX)
                || stripped.startsWith(ShopGui.DESIGN_PREFIX)
                || stripped.startsWith(BoosterGui.HUB_PREFIX)
                || stripped.startsWith(BoosterGui.SHOP_PREFIX)
                || stripped.startsWith(BoosterGui.INVENTORY_PREFIX)
                || stripped.startsWith(StatsGui.STATS_PREFIX)
                || stripped.startsWith(StatsGui.LEADERBOARD_PREFIX);
    }

    // ===== Event handlers =====

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top != null && top.getType() == InventoryType.CRAFTING) {
            Player p = (Player) event.getWhoClicked();
            if (plugin.getGameplayManager() != null
                    && plugin.getGameplayManager().getSession(p.getUniqueId()) != null) {
                event.setCancelled(true);
            }
            return;
        }
        if (top == null) return;
        String title = event.getView().getTitle();
        if (title != null && isPluginGui(ColorUtil.strip(title))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null || top.getType() != InventoryType.CRAFTING) return;
        Player player = (Player) event.getWhoClicked();
        if (plugin.getGameplayManager() != null
                && plugin.getGameplayManager().getSession(player.getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Inventory top = event.getView().getTopInventory();
        if (top == null || top.getType() == InventoryType.CRAFTING) return;

        String title = event.getView().getTitle();
        if (title == null) return;

        String stripped = ColorUtil.strip(title);
        if (!isPluginGui(stripped)) return;

        event.setCancelled(true);

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || clicked != top) return;

        if (stripped.startsWith(IslandSelectorGui.PREFIX)) {
            islandSelectorGui.handleClick(event);
        } else if (stripped.startsWith(BlockSelectorGui.PREFIX)) {
            blockSelectorGui.handleClick(event, shopGui);
        } else if (stripped.startsWith(SettingsGui.SETTINGS_PREFIX)) {
            settingsGui.handleSettingsClick(event);
        } else if (stripped.startsWith(SettingsGui.CONFIRM_PREFIX)) {
            settingsGui.handleConfirmClick(event);
        } else if (stripped.startsWith(SettingsGui.CUSTOM_LENGTH_PREFIX)) {
            settingsGui.handleCustomLengthMenuClick(event);
        } else if (stripped.startsWith(MapSelectorGui.PREFIX)) {
            mapSelectorGui.handleClick(event);
        } else if (stripped.startsWith(ReplayGui.PREFIX)) {
            replayGui.handleClick(event);
        } else if (stripped.startsWith(BoosterGui.HUB_PREFIX)) {
            boosterGui.handleHubClick(event, shopGui);
        } else if (stripped.startsWith(BoosterGui.INVENTORY_PREFIX)) {
            boosterGui.handleInventoryClick(event);
        } else if (stripped.startsWith(BoosterGui.SHOP_PREFIX)) {
            boosterGui.handleShopClick(event);
        } else if (stripped.startsWith(ShopGui.SHOP_PREFIX)) {
            shopGui.handleShopClick(event, boosterGui, blockSelectorGui);
        } else if (stripped.startsWith(ShopGui.PICKAXE_PREFIX)) {
            shopGui.handlePickaxeSelectorClick(event);
        } else if (stripped.startsWith(ShopGui.ANIMATION_PREFIX)) {
            shopGui.handleAnimationSelectorClick(event);
        } else if (stripped.startsWith(ShopGui.DEATH_SOUND_PREFIX)) {
            shopGui.handleDeathSoundSelectorClick(event);
        } else if (stripped.startsWith(ShopGui.DESIGN_PREFIX)) {
            shopGui.handleDesignSelectorClick(event);
        } else if (stripped.startsWith(StatsGui.LEADERBOARD_PREFIX)) {
            statsGui.handleLeaderboardClick(event);
        }
        // StatsGui.STATS_PREFIX: read-only, no handler needed
    }
}
