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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // Track whether a player is viewing the favorites tab
    private final Map<UUID, Boolean> replayFavoritesMode = new HashMap<>();

    // Track the map name for the current replay GUI (needed for tab switching)
    private final Map<UUID, String> replayGuiMap = new HashMap<>();

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
                // Player skull with actual skin texture
                item = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
                SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
                skullMeta.setOwner(island.getOccupantName());
                skullMeta.setDisplayName(ColorUtil.translate("&c#" + (i + 1) + " &7- &f" + island.getOccupantName()));
                java.util.List<String> lore = new java.util.ArrayList<>();
                lore.add(ColorUtil.translate("&cOccupied"));
                skullMeta.setLore(lore);
                item.setItemMeta(skullMeta);
            } else {
                // Numbered head for empty island
                item = new ItemBuilder(Material.SKULL_ITEM, (byte) 3)
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
        String title = ColorUtil.translate(guis.getString("map-selector.name", MAP_SELECTOR_PREFIX));
        int size = guis.getInt("map-selector.max-slots", 54);

        Inventory inv = Bukkit.createInventory(null, size, title);

        // Fill with filler items first
        boolean fillerEnabled = guis.getBoolean("map-selector.filler.enabled", true);
        if (fillerEnabled) {
            String fillerMat = guis.getString("map-selector.filler.material", "STAINED_GLASS_PANE:7");
            String fillerName = guis.getString("map-selector.filler.name", " ");
            ItemStack filler = ItemBuilder.fromString(fillerMat).name(fillerName).build();
            for (int i = 0; i < size; i++) {
                inv.setItem(i, filler);
            }
        }

        // Build map->slot assignments from config
        ConfigurationSection slotsSection = guis.getConfigurationSection("map-selector.slots");
        Map<String, Integer> configuredSlots = new HashMap<>();
        if (slotsSection != null) {
            for (String slotKey : slotsSection.getKeys(false)) {
                try {
                    int slotNum = Integer.parseInt(slotKey);
                    String mapName = slotsSection.getString(slotKey);
                    if (mapName != null) {
                        configuredSlots.put(mapName, slotNum);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // Place maps: configured slots first, then fill remaining into first free slots
        Collection<MapData> allMaps = plugin.getMapManager().getAllMaps();
        java.util.Set<Integer> usedSlots = new java.util.HashSet<>(configuredSlots.values());

        for (MapData map : allMaps) {
            if (!map.isEnabled()) continue;

            int slot;
            if (configuredSlots.containsKey(map.getName())) {
                slot = configuredSlots.get(map.getName());
            } else {
                // Find next free slot
                slot = 0;
                while (slot < size && usedSlots.contains(slot)) {
                    slot++;
                }
                if (slot >= size) break;
            }
            usedSlots.add(slot);

            int occupied = plugin.getMapManager().getOccupiedCount(map.getName());
            int total = plugin.getMapManager().getIslands(map.getName()).size();

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

    /**
     * Open the replay selector.
     * @param showFavorites true to show only favorited replays + PB; false for all replays
     */
    public void openReplaySelector(Player player, String mapName, boolean showFavorites) {
        if (plugin.getReplayManager() == null) {
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&cReplay system is not available."));
            return;
        }

        List<ReplayData> allReplays = plugin.getReplayManager().getPlayerReplays(player.getUniqueId(), mapName);

        List<ReplayData> replays;
        if (showFavorites) {
            PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            Set<String> favs = pData != null ? pData.getFavoriteReplays() : Collections.emptySet();
            // Find PB file name
            long pbTime = Long.MAX_VALUE;
            for (ReplayData rd : allReplays) {
                if (rd.isSuccessful() && rd.getRunTimeMillis() > 0 && rd.getRunTimeMillis() < pbTime) {
                    pbTime = rd.getRunTimeMillis();
                }
            }
            final long finalPb = pbTime;
            replays = new ArrayList<>();
            for (ReplayData rd : allReplays) {
                boolean isFav = favs.contains(rd.getFileName());
                boolean isPb  = rd.isSuccessful() && rd.getRunTimeMillis() == finalPb;
                if (isFav || isPb) replays.add(rd);
            }
        } else {
            replays = allReplays;
        }

        replayFavoritesMode.put(player.getUniqueId(), showFavorites);
        replayGuiMap.put(player.getUniqueId(), mapName);
        openReplayPage(player, replays, 1, showFavorites, mapName);
    }

    private void openReplayPage(Player player, List<ReplayData> replays, int page,
                                boolean favoritesMode, String mapName) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int itemsPerPage = 28; // 4 rows of 7 content slots (bordered)
        int maxPage = Math.max(1, (int) Math.ceil(replays.size() / (double) itemsPerPage));

        String titleTemplate = guis.getString("replays", "Replays - Page %page%/%max_page%");
        String title = ColorUtil.translate(titleTemplate
                .replace("%page%", String.valueOf(page))
                .replace("%max_page%", String.valueOf(maxPage)));

        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Gather player favorites and PB for display
        PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        Set<String> favorites = pData != null ? pData.getFavoriteReplays() : Collections.<String>emptySet();
        long pbTime = Long.MAX_VALUE;
        for (ReplayData rd : replays) {
            if (rd.isSuccessful() && rd.getRunTimeMillis() > 0 && rd.getRunTimeMillis() < pbTime) {
                pbTime = rd.getRunTimeMillis();
            }
        }

        // Fill border with gray glass panes
        ItemStack border = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9, border);
            inv.setItem(row * 9 + 8, border);
        }

        // Content slots: columns 1-7 in rows 1-4
        int[] contentSlots = new int[28];
        int ci = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                contentSlots[ci++] = row * 9 + col;
            }
        }

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, replays.size());

        for (int i = startIndex; i < endIndex; i++) {
            ReplayData replay = replays.get(i);
            int slot = contentSlots[i - startIndex];

            boolean isFav = favorites.contains(replay.getFileName());
            boolean isPb  = replay.isSuccessful() && replay.getRunTimeMillis() > 0
                            && replay.getRunTimeMillis() == pbTime;

            Material icon;
            if (isPb) {
                icon = Material.NETHER_STAR;
            } else if (isFav) {
                icon = Material.GOLD_INGOT;
            } else {
                icon = replay.isSuccessful() ? Material.EMERALD : Material.REDSTONE;
            }

            String status = replay.isSuccessful() ? "&aSuccessful" : "&cFailed";
            String time = replay.getRunTimeMillis() > 0
                    ? TimeUtil.formatTime(replay.getRunTimeMillis()) : "N/A";
            String favLine = isFav ? "&6Favorited &e(Right-click to remove)" : "&7Right-click to favorite";
            String pbLine  = isPb  ? "&6&lPersonal Best" : "";

            java.util.List<String> loreList = new java.util.ArrayList<>();
            loreList.add(ColorUtil.translate(status));
            loreList.add(ColorUtil.translate("&7Time: &f" + time));
            loreList.add(ColorUtil.translate("&7Map: &f" + replay.getMapName()));
            if (!pbLine.isEmpty()) loreList.add(ColorUtil.translate(pbLine));
            loreList.add("");
            loreList.add(ColorUtil.translate("&eLeft-click to watch"));
            loreList.add(ColorUtil.translate(favLine));

            ItemStack item = new ItemBuilder(icon)
                    .name("&f" + ReplayManager.formatTimestamp(replay.getTimestamp()))
                    .lore(loreList.toArray(new String[0]))
                    .build();

            inv.setItem(slot, item);
        }

        // Navigation in bottom border
        if (page > 1) {
            inv.setItem(45, new ItemBuilder(Material.ARROW).name("&c<< Previous Page").build());
        }
        if (page < maxPage) {
            inv.setItem(53, new ItemBuilder(Material.ARROW).name("&a>> Next Page").build());
        }
        inv.setItem(49, new ItemBuilder(Material.PAPER)
                .name("&7Page &f" + page + " &7/ &f" + maxPage).build());

        // Favorites tab toggle button
        if (favoritesMode) {
            inv.setItem(47, new ItemBuilder(Material.GOLD_INGOT)
                    .name("&6Favorites &7(viewing)")
                    .lore("&7Click to view all replays").build());
        } else {
            inv.setItem(47, new ItemBuilder(Material.GOLD_INGOT)
                    .name("&7Favorites")
                    .lore("&7Click to view favorited replays").build());
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

        // Clear placed blocks on old island
        net.gravijet.fastbuilder.gameplay.RunSession oldSession = plugin.getGameplayManager().getSession(player.getUniqueId());
        if (oldSession != null) {
            for (org.bukkit.Location loc : oldSession.getPlacedBlocks()) {
                org.bukkit.block.Block block = loc.getBlock();
                if (block != null) block.setType(org.bukkit.Material.AIR);
            }
        }

        // Remove old NPC and hologram
        if (plugin.getNpcManager() != null) {
            plugin.getNpcManager().despawnNpc(player.getUniqueId());
        }
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().removeHologram(map.getName(), data.getLastIsland());
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

        // Spawn new NPC and hologram at the new island
        if (plugin.getNpcManager() != null) {
            plugin.getNpcManager().spawnNpc(player, map.getIslandNpcLocation(slot));
        }
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().updateHologram(map.getName(), slot, player);
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
                    boolean newState = !run.isPracticeMode();
                    run.setPracticeMode(newState);

                    // Anti-exploit: when disabling practice mode, clear ALL practice blocks
                    if (!newState && run.hasPracticeBlocks()) {
                        for (org.bukkit.Location loc : run.getPracticeBlocks()) {
                            org.bukkit.block.Block block = loc.getBlock();
                            if (block != null) {
                                block.setType(org.bukkit.Material.AIR);
                            }
                        }
                        // Also remove them from placed blocks list
                        run.getPlacedBlocks().removeAll(run.getPracticeBlocks());
                        run.getPracticeBlocks().clear();
                        player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                                + "&fAll practice blocks have been cleared."));
                    }

                    String stateStr = newState ? "&aenabled" : "&cdisabled";
                    player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                            + "&fPractice mode " + stateStr + "&f."));
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

        // Ignore filler item clicks (blank name or single space)
        if (displayName.trim().isEmpty()) return;

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

        boolean favMode = Boolean.TRUE.equals(replayFavoritesMode.get(player.getUniqueId()));
        String mapName  = replayGuiMap.getOrDefault(player.getUniqueId(), "");

        int itemsPerPage = 28;
        int maxPage = Math.max(1, (int) Math.ceil(replays.size() / (double) itemsPerPage));

        // Previous page
        if (slot == 45 && currentPage > 1) {
            openReplayPage(player, replays, currentPage - 1, favMode, mapName);
            return;
        }
        // Next page
        if (slot == 53 && currentPage < maxPage) {
            openReplayPage(player, replays, currentPage + 1, favMode, mapName);
            return;
        }
        // Favorites tab toggle
        if (slot == 47) {
            openReplaySelector(player, mapName, !favMode);
            return;
        }

        // Map slot → content index
        int[] contentSlots = new int[28];
        int ci = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                contentSlots[ci++] = row * 9 + col;
            }
        }

        int contentIndex = -1;
        for (int i = 0; i < contentSlots.length; i++) {
            if (contentSlots[i] == slot) {
                contentIndex = i;
                break;
            }
        }
        if (contentIndex < 0) return;

        int replayIndex = (currentPage - 1) * itemsPerPage + contentIndex;
        if (replayIndex < 0 || replayIndex >= replays.size()) return;

        ReplayData replay = replays.get(replayIndex);

        boolean isRightClick = event.getClick() == org.bukkit.event.inventory.ClickType.RIGHT;

        if (isRightClick) {
            // Toggle favorite
            PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            if (pData == null) return;
            boolean wasFav = pData.isFavoriteReplay(replay.getFileName());
            pData.toggleFavoriteReplay(replay.getFileName());
            boolean nowFav = !wasFav;
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                    + (nowFav ? "&aAdded to favorites." : "&7Removed from favorites.")));
            // Refresh current view
            openReplayPage(player, replays, currentPage, favMode, mapName);
        } else {
            // Left-click: watch
            player.closeInventory();
            if (plugin.getReplayManager() != null) {
                plugin.getReplayManager().startPlayback(player, replay);
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                        + "&fStarting replay..."));
            }
        }
    }
}
