package net.gravijet.fastbuilder.gui;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.IslandInstance;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.replay.ReplayData;
import net.gravijet.fastbuilder.replay.ReplayManager;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.ItemBuilder;
import net.gravijet.fastbuilder.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles all plugin GUIs: Block Selector, Map Selector, Island Selector, Settings, Replays, Confirm.
 */
public class GuiManager implements Listener {

    private final FastBuilder plugin;

    // GUI title prefixes for identification
    private static final String BLOCK_SELECTOR_PREFIX = "Block Selector";
    private static final String SETTINGS_PREFIX = "Settings";
    private static final String ISLAND_SELECTOR_PREFIX = "Island Selector";
    private static final String CONFIRM_PREFIX = "Confirm";
    private static final String MAP_SELECTOR_PREFIX = "Map Selector";
    private static final String REPLAYS_PREFIX = "Replays";

    // Track which block selector page a player is on
    private final Map<UUID, Integer> blockSelectorPages = new HashMap<>();

    // Track which replay page a player is on
    private final Map<UUID, Integer> replayPages = new HashMap<>();

    // Track replays currently displayed in the GUI for click handling
    private final Map<UUID, List<ReplayData>> displayedReplays = new HashMap<>();

    public GuiManager(FastBuilder plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ===== Island Selector =====

    public void openIslandSelector(Player player, MapData map) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(guis.getString("island-selector", "Island Selector"));
        int size = Math.min(54, ((map.getScale() / 9) + 1) * 9);
        if (size < 9) size = 9;

        Inventory inv = Bukkit.createInventory(null, size, title);

        List<IslandInstance> islands = plugin.getMapManager().getIslands(map.getName());
        for (int i = 0; i < islands.size() && i < size; i++) {
            IslandInstance island = islands.get(i);
            ItemStack item;

            if (island.isOccupied()) {
                item = new ItemBuilder(Material.SKULL_ITEM, (byte) 3)
                        .name("&c#" + (i + 1) + " &7- &f" + island.getOccupantName())
                        .lore("&cOccupied")
                        .build();
            } else {
                item = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 5)
                        .name("&a#" + (i + 1))
                        .lore("&aClick to join")
                        .build();
            }

            inv.setItem(i, item);
        }

        player.openInventory(inv);
    }

    // ===== Map Selector =====

    public void openMapSelector(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(MAP_SELECTOR_PREFIX);

        Collection<MapData> allMaps = plugin.getMapManager().getAllMaps();
        int enabledCount = 0;
        for (MapData m : allMaps) {
            if (m.isEnabled()) enabledCount++;
        }

        int size = Math.min(54, ((enabledCount / 9) + 1) * 9);
        if (size < 9) size = 9;

        Inventory inv = Bukkit.createInventory(null, size, title);

        int slot = 0;
        for (MapData map : allMaps) {
            if (!map.isEnabled()) continue;
            if (slot >= size) break;

            int occupied = plugin.getMapManager().getOccupiedCount(map.getName());
            int total = plugin.getMapManager().getIslands(map.getName()).size();

            // Use map icon or default
            String iconStr = map.getIcon();
            ItemBuilder builder = (iconStr != null && !iconStr.isEmpty())
                    ? ItemBuilder.fromString(iconStr)
                    : new ItemBuilder(Material.GRASS);

            ItemStack item = builder
                    .name("&c" + map.getName())
                    .lore(
                            "&7Players: &f" + occupied + "/" + total,
                            "",
                            "&aClick to join"
                    )
                    .build();

            inv.setItem(slot, item);
            slot++;
        }

        player.openInventory(inv);
    }

    // ===== Block Selector =====

    public void openBlockSelector(Player player, int page) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int maxSlots = guis.getInt("block-selector.max-slots", 45);
        String titleTemplate = guis.getString("block-selector.name", "Block Selector - %page%/3");

        ConfigurationSection pageSection = guis.getConfigurationSection("block-selector-slots." + page);
        if (pageSection == null) {
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&cNo blocks on this page."));
            return;
        }

        // Count total pages
        int maxPage = 1;
        for (String key : guis.getConfigurationSection("block-selector-slots").getKeys(false)) {
            try {
                int p = Integer.parseInt(key);
                if (p > maxPage) maxPage = p;
            } catch (NumberFormatException ignored) {}
        }

        String title = ColorUtil.translate(titleTemplate.replace("%page%", String.valueOf(page)));
        Inventory inv = Bukkit.createInventory(null, maxSlots, title);

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        String purchasedStatus = guis.getString("block-status.purchased", "&aYou already own this block!");
        String notPurchasedStatus = guis.getString("block-status.not-purchased", "&cYou don't own this block yet!");

        for (String slotKey : pageSection.getKeys(false)) {
            try {
                int slotIndex = Integer.parseInt(slotKey) - 1; // 1-based to 0-based
                if (slotIndex < 0 || slotIndex >= maxSlots - 9) continue; // Leave bottom row for navigation

                String name = pageSection.getString(slotKey + ".name", "Block");
                String mat = pageSection.getString(slotKey + ".material", "STONE:0");
                int price = pageSection.getInt(slotKey + ".price", 0);

                boolean owned = price == 0 || (data != null && data.hasPurchasedBlock(mat))
                        || player.hasPermission("fastbuilder.blocks.*");

                List<String> loreTemplate = pageSection.getStringList(slotKey + ".lore");
                String[] lore = new String[loreTemplate.size()];
                for (int i = 0; i < loreTemplate.size(); i++) {
                    lore[i] = loreTemplate.get(i)
                            .replace("%price%", price == 0 ? "Free" : String.valueOf(price))
                            .replace("%block_status%", owned ? purchasedStatus : notPurchasedStatus);
                }

                ItemStack item = ItemBuilder.fromString(mat).name(name).lore(lore).build();
                inv.setItem(slotIndex, item);
            } catch (NumberFormatException ignored) {}
        }

        // Navigation items (bottom row)
        FileConfiguration itemsConfig = plugin.getConfigManager().getItemsConfig();

        if (page > 1) {
            String prevMat = itemsConfig.getString("change-page.previous-page.material", "ARROW:0");
            String prevName = itemsConfig.getString("change-page.previous-page.name", "&cPrevious Page");
            inv.setItem(maxSlots - 9, ItemBuilder.fromString(prevMat).name(prevName).build());
        }

        if (page < maxPage) {
            String nextMat = itemsConfig.getString("change-page.next-page.material", "ARROW:0");
            String nextName = itemsConfig.getString("change-page.next-page.name", "&aNext Page");
            inv.setItem(maxSlots - 1, ItemBuilder.fromString(nextMat).name(nextName).build());
        }

        blockSelectorPages.put(player.getUniqueId(), page);
        player.openInventory(inv);
    }

    // ===== Settings =====

    public void openSettings(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(guis.getString("settings.name", "Settings"));
        int slots = guis.getInt("settings.max-slots", 27);

        Inventory inv = Bukkit.createInventory(null, slots, title);

        ConfigurationSection settingsSlots = guis.getConfigurationSection("settings-gui-slots");
        if (settingsSlots != null) {
            PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            net.gravijet.fastbuilder.gameplay.RunSession run = plugin.getGameplayManager() != null
                    ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;

            for (String slotKey : settingsSlots.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotKey);
                    String name = settingsSlots.getString(slotKey + ".name", "");
                    String mat = settingsSlots.getString(slotKey + ".material", "STONE:0");

                    List<String> loreList = settingsSlots.getStringList(slotKey + ".lore");
                    String[] lore = new String[loreList.size()];
                    for (int i = 0; i < loreList.size(); i++) {
                        String line = loreList.get(i);
                        line = line.replace("%price%", String.valueOf(plugin.getConfigManager().getResetStatsCost()));
                        line = line.replace("%practice_mode_enabled%",
                                (run != null && run.isPracticeMode()) ? "&aEnabled" : "&cDisabled");
                        lore[i] = line;
                    }

                    ItemStack item = ItemBuilder.fromString(mat).name(name).lore(lore).build();
                    inv.setItem(slot, item);
                } catch (NumberFormatException ignored) {}
            }
        }

        player.openInventory(inv);
    }

    // ===== Confirm Stats Reset =====

    public void openConfirmStatsReset(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(guis.getString("confirm-stats-reset", "Confirm"));

        Inventory inv = Bukkit.createInventory(null, 27, title);

        int cost = plugin.getConfigManager().getResetStatsCost();
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        int coins = data != null ? data.getCoins() : 0;

        inv.setItem(11, new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 5)
                .name("&a&lConfirm Reset")
                .lore("&7Cost: &c" + cost + " coins",
                        "&7Your coins: &f" + coins,
                        "",
                        "&aClick to confirm")
                .build());

        inv.setItem(15, new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 14)
                .name("&c&lCancel")
                .lore("&7Go back to settings")
                .build());

        player.openInventory(inv);
    }

    // ===== Replay Selector =====

    public void openReplaySelector(Player player, String mapName) {
        if (plugin.getReplayManager() == null) {
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&cReplay system is not available."));
            return;
        }

        List<ReplayData> replays = plugin.getReplayManager().getPlayerReplays(player.getUniqueId(), mapName);
        int page = 1;
        openReplayPage(player, replays, page);
    }

    private void openReplayPage(Player player, List<ReplayData> replays, int page) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int itemsPerPage = 36; // 4 rows of items, bottom row for navigation
        int maxPage = Math.max(1, (int) Math.ceil(replays.size() / (double) itemsPerPage));

        String titleTemplate = guis.getString("replays", "Replays - Page %page%/%max_page%");
        String title = ColorUtil.translate(titleTemplate
                .replace("%page%", String.valueOf(page))
                .replace("%max_page%", String.valueOf(maxPage)));

        Inventory inv = Bukkit.createInventory(null, 45, title);

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, replays.size());

        for (int i = startIndex; i < endIndex; i++) {
            ReplayData replay = replays.get(i);
            int slot = i - startIndex;

            Material icon = replay.isSuccessful() ? Material.EMERALD : Material.REDSTONE;
            String status = replay.isSuccessful() ? "&aSuccessful" : "&cFailed";
            String time = replay.getRunTimeMillis() > 0
                    ? TimeUtil.formatTime(replay.getRunTimeMillis()) : "N/A";

            ItemStack item = new ItemBuilder(icon)
                    .name("&f" + ReplayManager.formatTimestamp(replay.getTimestamp()))
                    .lore(
                            status,
                            "&7Time: &f" + time,
                            "&7Map: &f" + replay.getMapName(),
                            "",
                            "&eClick to watch"
                    )
                    .build();

            inv.setItem(slot, item);
        }

        // Navigation
        FileConfiguration itemsConfig = plugin.getConfigManager().getItemsConfig();

        if (page > 1) {
            String prevMat = itemsConfig.getString("change-page.previous-page.material", "ARROW:0");
            String prevName = itemsConfig.getString("change-page.previous-page.name", "&cPrevious Page");
            inv.setItem(36, ItemBuilder.fromString(prevMat).name(prevName).build());
        }

        if (page < maxPage) {
            String nextMat = itemsConfig.getString("change-page.next-page.material", "ARROW:0");
            String nextName = itemsConfig.getString("change-page.next-page.name", "&aNext Page");
            inv.setItem(44, ItemBuilder.fromString(nextMat).name(nextName).build());
        }

        replayPages.put(player.getUniqueId(), page);
        displayedReplays.put(player.getUniqueId(), replays);
        player.openInventory(inv);
    }

    // ===== Click Handling =====

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        String title = event.getInventory().getTitle();
        if (title == null) return;

        String stripped = ColorUtil.strip(title);

        if (stripped.startsWith(ISLAND_SELECTOR_PREFIX)) {
            event.setCancelled(true);
            handleIslandSelectorClick(event);
        } else if (stripped.startsWith(BLOCK_SELECTOR_PREFIX)) {
            event.setCancelled(true);
            handleBlockSelectorClick(event);
        } else if (stripped.startsWith(SETTINGS_PREFIX)) {
            event.setCancelled(true);
            handleSettingsClick(event);
        } else if (stripped.startsWith(CONFIRM_PREFIX)) {
            event.setCancelled(true);
            handleConfirmClick(event);
        } else if (stripped.startsWith(MAP_SELECTOR_PREFIX)) {
            event.setCancelled(true);
            handleMapSelectorClick(event);
        } else if (stripped.startsWith(REPLAYS_PREFIX)) {
            event.setCancelled(true);
            handleReplayClick(event);
        }
    }

    private void handleIslandSelectorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null || data.getLastMap() == null) return;

        MapData map = plugin.getMapManager().getMap(data.getLastMap());
        if (map == null) return;

        List<IslandInstance> islands = plugin.getMapManager().getIslands(map.getName());
        if (slot < 0 || slot >= islands.size()) return;

        IslandInstance island = islands.get(slot);
        if (island.isOccupied()) {
            String raw = plugin.getConfigManager().getMessage("island-already-occupied");
            raw = raw.replace("%island_player%", island.getOccupantName());
            raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
            player.sendMessage(ColorUtil.translate(raw));
            player.closeInventory();
            return;
        }

        // Free current island, assign new one
        plugin.getMapManager().freeIsland(map.getName(), player.getUniqueId());
        plugin.getGameplayManager().removeSession(player.getUniqueId());

        plugin.getMapManager().assignIsland(map.getName(), slot, player.getUniqueId(), player.getName());
        data.setLastIsland(slot);
        player.teleport(map.getIslandSpawn(slot));
        player.closeInventory();

        // Create new gameplay session
        plugin.getGameplayManager().createSession(player.getUniqueId(), map.getName(), slot);

        // Give hotbar items
        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().giveItems(player);
        }

        String raw = plugin.getConfigManager().getMessage("island-joined");
        raw = raw.replace("%island_number%", String.valueOf(slot + 1));
        raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
    }

    private void handleBlockSelectorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int maxSlots = guis.getInt("block-selector.max-slots", 45);

        Integer currentPage = blockSelectorPages.get(player.getUniqueId());
        if (currentPage == null) currentPage = 1;

        // Previous page button
        if (slot == maxSlots - 9 && currentPage > 1) {
            openBlockSelector(player, currentPage - 1);
            return;
        }

        // Next page button
        int maxPage = 1;
        if (guis.isConfigurationSection("block-selector-slots")) {
            for (String key : guis.getConfigurationSection("block-selector-slots").getKeys(false)) {
                try {
                    int p = Integer.parseInt(key);
                    if (p > maxPage) maxPage = p;
                } catch (NumberFormatException ignored) {}
            }
        }
        if (slot == maxSlots - 1 && currentPage < maxPage) {
            openBlockSelector(player, currentPage + 1);
            return;
        }

        // Block click
        ConfigurationSection pageSection = guis.getConfigurationSection("block-selector-slots." + currentPage);
        if (pageSection == null) return;

        int blockIndex = slot + 1; // 0-based slot to 1-based config key
        if (!pageSection.isConfigurationSection(String.valueOf(blockIndex))) return;

        String mat = pageSection.getString(blockIndex + ".material", "STONE:0");
        int price = pageSection.getInt(blockIndex + ".price", 0);

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        boolean owned = price == 0 || data.hasPurchasedBlock(mat)
                || player.hasPermission("fastbuilder.blocks.*");

        if (!owned) {
            // Try to purchase
            if (data.getCoins() >= price) {
                data.removeCoins(price);
                data.purchaseBlock(mat);
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                        + "&fBlock purchased for &c" + price + " &fcoins."));
                // Refresh the page
                openBlockSelector(player, currentPage);
            } else {
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                        + "&cNot enough coins! You need &f" + price + " &ccoins."));
            }
            return;
        }

        // Select the block
        data.setSelectedBlock(mat);
        player.closeInventory();

        // Update hotbar block
        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().updateBlockSlot(player);
        }

        String blockName = pageSection.getString(blockIndex + ".name", "Block");
        player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                + "&fSelected block: &c" + blockName));
    }

    private void handleSettingsClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        ConfigurationSection settingsSlots = guis.getConfigurationSection("settings-gui-slots");
        if (settingsSlots == null) return;

        String gui = settingsSlots.getString(slot + ".gui", "");

        switch (gui) {
            case "block_selector":
                player.closeInventory();
                openBlockSelector(player, 1);
                break;
            case "reset_stats":
                player.closeInventory();
                openConfirmStatsReset(player);
                break;
            case "practice_mode":
                net.gravijet.fastbuilder.gameplay.RunSession run = plugin.getGameplayManager() != null
                        ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
                if (run != null) {
                    run.setPracticeMode(!run.isPracticeMode());
                    String state = run.isPracticeMode() ? "&aenabled" : "&cdisabled";
                    player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                            + "&fPractice mode " + state + "&f."));
                }
                // Refresh settings GUI
                openSettings(player);
                break;
            default:
                break;
        }
    }

    private void handleConfirmClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == 11) {
            // Confirm reset
            PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            if (data == null) return;

            int cost = plugin.getConfigManager().getResetStatsCost();
            if (data.removeCoins(cost)) {
                // Clear all stats
                data.getAllStats().clear();
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                        + "&fYour stats have been reset."));
                // Update scoreboard
                plugin.getScoreboardManager().updateScoreboard(player);
            } else {
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                        + "&cNot enough coins! You need &f" + cost + " &ccoins."));
            }
            player.closeInventory();
        } else if (slot == 15) {
            // Cancel
            player.closeInventory();
            openSettings(player);
        }
    }

    private void handleMapSelectorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;

        String displayName = ColorUtil.strip(item.getItemMeta().getDisplayName());

        // Find the map by name
        MapData map = plugin.getMapManager().getMap(displayName);
        if (map == null || !map.isEnabled()) return;

        player.closeInventory();

        // Free current island
        plugin.getMapManager().freeAllIslands(player.getUniqueId());
        plugin.getGameplayManager().removeSession(player.getUniqueId());

        // Assign a free island
        int island = plugin.getMapManager().assignFreeIsland(map.getName(), player.getUniqueId(), player.getName());
        if (island < 0) {
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getMessage("no-free-islands")
                    .replace("%prefix%", plugin.getConfigManager().getPrefix())));
            return;
        }

        // Update player data
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId(), player.getName());
        data.setLastMap(map.getName());
        data.setLastIsland(island);

        player.teleport(map.getIslandSpawn(island));

        // Create gameplay session
        plugin.getGameplayManager().createSession(player.getUniqueId(), map.getName(), island);

        // Give hotbar items
        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().giveItems(player);
        }

        // Check autoscale
        plugin.getMapManager().checkAutoscale(map);

        String raw = plugin.getConfigManager().getMessage("joined-mode");
        if (raw != null && !raw.isEmpty()) {
            raw = raw.replace("%map%", map.getName())
                    .replace("%prefix%", plugin.getConfigManager().getPrefix());
            player.sendMessage(ColorUtil.translate(raw));
        }
    }

    private void handleReplayClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        Integer currentPage = replayPages.get(player.getUniqueId());
        if (currentPage == null) currentPage = 1;

        List<ReplayData> replays = displayedReplays.get(player.getUniqueId());
        if (replays == null) return;

        // Navigation
        if (slot == 36 && currentPage > 1) {
            openReplayPage(player, replays, currentPage - 1);
            return;
        }
        int maxPage = Math.max(1, (int) Math.ceil(replays.size() / 36.0));
        if (slot == 44 && currentPage < maxPage) {
            openReplayPage(player, replays, currentPage + 1);
            return;
        }

        // Replay click
        int replayIndex = (currentPage - 1) * 36 + slot;
        if (replayIndex < 0 || replayIndex >= replays.size()) return;

        ReplayData replay = replays.get(replayIndex);

        player.closeInventory();

        if (plugin.getReplayManager() != null) {
            plugin.getReplayManager().startPlayback(player, replay);
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                    + "&fStarting replay playback..."));
        }
    }
}
