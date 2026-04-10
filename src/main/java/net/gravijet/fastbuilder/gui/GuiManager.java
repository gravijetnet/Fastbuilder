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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
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
    private static final String SHOP_PREFIX = "Shop";
    private static final String PICKAXE_SELECTOR_PREFIX = "Pickaxe Selector";
    private static final String ANIMATION_SELECTOR_PREFIX = "Reset Animations";
    private static final String DEATH_SOUND_SELECTOR_PREFIX = "Death Sounds";
    private static final String STATS_PREFIX = "Stats";
    private static final String DESIGN_SELECTOR_PREFIX = "Island Designs";

    // Track which block selector page a player is on
    private final Map<UUID, Integer> blockSelectorPages = new HashMap<>();

    // Track pickaxe/animation selector pages
    private final Map<UUID, Integer> pickaxePages = new HashMap<>();
    private final Map<UUID, Integer> animationPages = new HashMap<>();

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

        // Fill ALL slots with a gray filler first (no empty gaps)
        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = 0; i < maxSlots; i++) inv.setItem(i, filler);

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

                // Block display names always use &c<name> as per spec
                ItemStack item = ItemBuilder.fromString(mat).name("&c" + name).lore(lore).build();
                inv.setItem(slotIndex, item);
            } catch (NumberFormatException ignored) {}
        }

        // Navigation — bottom row (all three buttons are always rendered)
        FileConfiguration itemsConfig = plugin.getConfigManager().getItemsConfig();

        // Back to Shop — center of bottom row
        String backMat  = itemsConfig.getString("change-page.back-to-shop.material", "ARROW:0");
        String backName = itemsConfig.getString("change-page.back-to-shop.name", "&cBack to Shop");
        inv.setItem(maxSlots - 5, ItemBuilder.fromString(backMat).name(backName).build());

        // Previous page — left corner; shows a disabled pane on page 1
        if (page > 1) {
            String prevMat  = itemsConfig.getString("change-page.previous-page.material", "ARROW:0");
            String prevName = itemsConfig.getString("change-page.previous-page.name", "&c« Previous Page");
            inv.setItem(maxSlots - 9, ItemBuilder.fromString(prevMat).name(prevName).build());
        } else {
            String disMat  = itemsConfig.getString("change-page.no-previous-page.material", "STAINED_GLASS_PANE:8");
            String disName = itemsConfig.getString("change-page.no-previous-page.name", "&8« No Previous Page");
            inv.setItem(maxSlots - 9, ItemBuilder.fromString(disMat).name(disName).build());
        }

        // Next page — right corner; shows a disabled pane on the last page
        if (page < maxPage) {
            String nextMat  = itemsConfig.getString("change-page.next-page.material", "ARROW:0");
            String nextName = itemsConfig.getString("change-page.next-page.name", "&aNext Page »");
            inv.setItem(maxSlots - 1, ItemBuilder.fromString(nextMat).name(nextName).build());
        } else {
            String disMat  = itemsConfig.getString("change-page.no-next-page.material", "STAINED_GLASS_PANE:8");
            String disName = itemsConfig.getString("change-page.no-next-page.name", "&8No Next Page »");
            inv.setItem(maxSlots - 1, ItemBuilder.fromString(disMat).name(disName).build());
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
                        line = line.replace("%infinite_blocks_status%",
                                (data != null && data.hasInfiniteBlocks()) ? "&aEnabled" : "&cDisabled");
                        line = line.replace("%infinite_blocks_unlocked%",
                                (data != null && data.hasInfiniteBlocksUnlocked()) ? "&aUnlocked" : "&cLocked");
                        // Custom length — distance adjuster placeholders
                        String customLengthStr = "&cNot available";
                        String clValueStr = "&8---";
                        String clMinStr = "---";
                        String clMaxStr = "---";
                        if (run != null) {
                            net.gravijet.fastbuilder.map.MapData clMap =
                                    plugin.getMapManager().getMap(run.getMapName());
                            if (clMap != null && clMap.hasCustomLength()) {
                                int clValue = data != null ? data.getCustomLength(run.getMapName()) : 0;
                                if (clValue <= 0) clValue = clMap.getMinCustomLength();
                                clValueStr = "&f" + clValue;
                                clMinStr = String.valueOf(clMap.getMinCustomLength());
                                clMaxStr = String.valueOf(clMap.getMaxCustomLength());
                                customLengthStr = "&f" + clValue + " &7blocks";
                            }
                        }
                        line = line.replace("%custom_length%", customLengthStr);
                        line = line.replace("%custom_length_value%", clValueStr);
                        line = line.replace("%custom_length_min%", clMinStr);
                        line = line.replace("%custom_length_max%", clMaxStr);
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
            inv.setItem(53, new ItemBuilder(Material.ARROW).name("&a» Next Page").build());
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

    // ===== Shop =====

    public void openShop(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(guis.getString("shop.name", "Shop"));
        // Shop is strictly 3×9 (27 slots)
        int size = guis.getInt("shop.max-slots", 27);
        if (size < 27) size = 27;
        if (size > 27) size = 27; // enforce 3×9
        Inventory inv = Bukkit.createInventory(null, size, title);

        // Category: Blocks (permission-gated) — Spec: Sandstone on slot 9
        if (plugin.getConfigManager().isShopCategoryVisible(player, "blocks")) {
            inv.setItem(guis.getInt("shop.blocks-slot", 9), new ItemBuilder(Material.SANDSTONE)
                    .name("&eBlocks").lore("&7Click to browse building blocks", "", "&aClick to browse").build());
        }
        // Category: Pickaxes (permission-gated) — Spec: Pickaxe Shop on slot 11
        if (plugin.getConfigManager().isShopCategoryVisible(player, "pickaxes")) {
            inv.setItem(guis.getInt("shop.pickaxes-slot", 11), new ItemBuilder(Material.DIAMOND_PICKAXE)
                    .name("&bPickaxes &7& Tools").lore("&7Click to browse pickaxes and tools", "", "&aClick to browse").build());
        }
        // Category: Reset Animations (permission-gated)
        if (plugin.getConfigManager().isShopCategoryVisible(player, "animations")) {
            inv.setItem(guis.getInt("shop.animations-slot", 15), new ItemBuilder(Material.FIREWORK)
                    .name("&dReset Animations").lore("&7Click to browse reset animations", "", "&aClick to browse").build());
        }
        // Category: Death Sounds (permission-gated)
        if (plugin.getConfigManager().isShopCategoryVisible(player, "sounds")) {
            inv.setItem(guis.getInt("shop.death-sounds-slot", 17), new ItemBuilder(Material.NOTE_BLOCK)
                    .name("&6Death Sounds").lore("&7Click to browse death sounds", "", "&aClick to browse").build());
        }

        // Category: Island Designs (only shown if the player's current map has alt designs)
        net.gravijet.fastbuilder.player.PlayerData shopData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        boolean hasDesigns = false;
        String currentMapForDesign = shopData != null ? shopData.getLastMap() : null;
        if (currentMapForDesign != null) {
            net.gravijet.fastbuilder.map.MapData designMap = plugin.getMapManager().getMap(currentMapForDesign);
            hasDesigns = designMap != null && !designMap.getAlternativeTemplates().isEmpty();
        }
        int designsSlot = guis.getInt("shop.designs-slot", 13);
        if (plugin.getConfigManager().isShopCategoryVisible(player, "designs")) {
            if (hasDesigns) {
                // Use an appealing block (not a glass pane) for the designs icon
                inv.setItem(designsSlot, new ItemBuilder(Material.EMERALD_BLOCK)
                        .name("&aIsland Designs").lore("&7Choose a design for your island", "", "&aClick to browse").build());
            } else {
                inv.setItem(designsSlot, new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 8)
                        .name("&7Island Designs").lore("&cNo designs available for this map").build());
            }
        }

        // One-Click Pick has been moved to the Pickaxe Shop — not shown here.

        player.openInventory(inv);
    }

    /**
     * Open the island design selector for the player's current map.
     */
    public void openDesignSelector(Player player) {
        net.gravijet.fastbuilder.player.PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (pData == null) return;
        String mapName = pData.getLastMap();
        if (mapName == null) return;
        net.gravijet.fastbuilder.map.MapData map = plugin.getMapManager().getMap(mapName);
        if (map == null) return;

        List<String> templates = map.getAllTemplates();
        // Spec: Island Selector must be EXACTLY 3×9 (27 slots)
        String title = ColorUtil.translate("&aIsland Designs &7- &f" + map.getName());
        Inventory inv = Bukkit.createInventory(null, 27, title);

        String selectedDesign = pData.getSelectedDesign(mapName);
        String defaultKey = map.getTemplateFile();
        if (selectedDesign == null) selectedDesign = defaultKey;

        // Content slots: 0-17 (first two rows), leaving row 3 for navigation
        int maxContentSlots = 18;
        for (int i = 0; i < templates.size() && i < maxContentSlots; i++) {
            String key = templates.get(i);
            boolean isDefault = key.equals(defaultKey);
            boolean selected = key.equalsIgnoreCase(selectedDesign);
            String displayName = isDefault ? "&fDefault Design" : "&aDesign #" + (i + 1);

            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.translate("&7Template: &f" + key));
            // Spec: Use Material.PAPER. No enchantments. Show selection via name color only.
            if (selected) {
                lore.add(ColorUtil.translate("&a&l» Currently selected"));
            } else {
                lore.add(ColorUtil.translate("&eClick to select this design instantly"));
            }

            // Spec: Material.PAPER, no enchanted books, no glass panes, no enchantments
            ItemStack item = new ItemBuilder(Material.PAPER).name(displayName).lore(lore.toArray(new String[0])).build();
            inv.setItem(i, item);
        }

        // Spec: "Back to Shop" button in the bottom center slot (slot 22 = center of row 3 in 27-slot)
        inv.setItem(22, new ItemBuilder(Material.ARROW).name("&cBack to Shop").build());
        player.openInventory(inv);
    }

    public void openPickaxeSelector(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(guis.getString("pickaxe-selector.name", PICKAXE_SELECTOR_PREFIX));
        int maxSlots = guis.getInt("pickaxe-selector.max-slots", 27);
        Inventory inv = Bukkit.createInventory(null, maxSlots, title);

        org.bukkit.configuration.ConfigurationSection slotsSection = guis.getConfigurationSection("pickaxe-selector-slots");
        if (slotsSection == null) {
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&cNo pickaxes configured."));
            return;
        }

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        String purchasedStatus = guis.getString("block-status.purchased", "&aYou already own this!");
        String notPurchasedStatus = guis.getString("block-status.not-purchased", "&cYou don't own this yet!");

        for (String slotKey : slotsSection.getKeys(false)) {
            try {
                int slotIndex = Integer.parseInt(slotKey) - 1;
                if (slotIndex < 0 || slotIndex >= maxSlots) continue;
                String name = slotsSection.getString(slotKey + ".name", "Pickaxe");
                String mat = slotsSection.getString(slotKey + ".material", "DIAMOND_PICKAXE:0");
                int price = slotsSection.getInt(slotKey + ".price", 0);
                boolean owned = price == 0 || (data != null && data.hasPurchasedBlock("pickaxe:" + mat)) || player.hasPermission("fastbuilder.blocks.*");
                java.util.List<String> loreTemplate = slotsSection.getStringList(slotKey + ".lore");
                String[] lore = new String[loreTemplate.size()];
                for (int i = 0; i < loreTemplate.size(); i++) {
                    lore[i] = loreTemplate.get(i).replace("%price%", price == 0 ? "Free" : String.valueOf(price)).replace("%block_status%", owned ? purchasedStatus : notPurchasedStatus);
                }
                inv.setItem(slotIndex, ItemBuilder.fromString(mat).name("&r" + name).lore(lore).build());
            } catch (NumberFormatException ignored) {}
        }

        // One-Click Pick — placed at a configurable content-area slot (1-based in config, default 23)
        PlayerData ocpData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        boolean hasOcp      = ocpData != null && ocpData.hasOneClickPick();
        boolean ocpPurchased = (ocpData != null && ocpData.hasPurchasedBlock("cosmetic:one_click_pick"))
                || player.hasPermission("fastbuilder.cosmetic.oneclickpick");
        if (!player.hasPermission("fastbuilder.shop.pickaxe.one_click_pick.hide")) {
            int    ocpPrice  = guis.getInt("one-click-pick.price", 5000);
            int    ocpSlot   = guis.getInt("one-click-pick.slot", 23) - 1; // 1-based → 0-based
            String ocpName   = guis.getString("one-click-pick.name", "&bOne-Click Pick");
            String ocpStatus = ocpPurchased
                    ? (hasOcp ? "&a&lACTIVE" : "&7Owned &8- &eClick to enable")
                    : "&cNot owned &8- &eClick to buy";
            inv.setItem(ocpSlot, new ItemBuilder(Material.DIAMOND_AXE)
                    .name(ocpName)
                    .lore("&7Insta-breaks your placed blocks on left-click",
                            "",
                            ocpStatus,
                            "&ePrice: " + (ocpPurchased ? "Owned" : ocpPrice + " coins"))
                    .build());
        }

        // Navigation — standardized bottom row (always visible, no pagination for this menu)
        FileConfiguration itemsConfig = plugin.getConfigManager().getItemsConfig();
        String backMat  = itemsConfig.getString("change-page.back-to-shop.material", "ARROW:0");
        String backName = itemsConfig.getString("change-page.back-to-shop.name", "&cBack to Shop");
        inv.setItem(maxSlots - 5, ItemBuilder.fromString(backMat).name(backName).build());

        String disPrevMat  = itemsConfig.getString("change-page.no-previous-page.material", "STAINED_GLASS_PANE:8");
        String disPrevName = itemsConfig.getString("change-page.no-previous-page.name", "&8« No Previous Page");
        inv.setItem(maxSlots - 9, ItemBuilder.fromString(disPrevMat).name(disPrevName).build());

        String disNextMat  = itemsConfig.getString("change-page.no-next-page.material", "STAINED_GLASS_PANE:8");
        String disNextName = itemsConfig.getString("change-page.no-next-page.name", "&8No Next Page »");
        inv.setItem(maxSlots - 1, ItemBuilder.fromString(disNextMat).name(disNextName).build());

        player.openInventory(inv);
    }

    public void openAnimationSelector(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(guis.getString("animation-selector.name", ANIMATION_SELECTOR_PREFIX));
        int maxSlots = guis.getInt("animation-selector.max-slots", 27);
        Inventory inv = Bukkit.createInventory(null, maxSlots, title);

        org.bukkit.configuration.ConfigurationSection slotsSection = guis.getConfigurationSection("animation-selector-slots");
        if (slotsSection == null) {
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&cNo animations configured."));
            return;
        }

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        String currentAnim = data != null ? data.getSelectedAnimation() : "NONE";

        for (String slotKey : slotsSection.getKeys(false)) {
            try {
                int slotIndex = Integer.parseInt(slotKey) - 1;
                if (slotIndex < 0 || slotIndex >= maxSlots) continue;
                String name = slotsSection.getString(slotKey + ".name", "Animation");
                String mat = slotsSection.getString(slotKey + ".material", "STAINED_GLASS_PANE:0");
                String animId = slotsSection.getString(slotKey + ".animation", "NONE");
                int price = slotsSection.getInt(slotKey + ".price", 0);
                boolean owned = price == 0 || (data != null && data.hasPurchasedBlock("anim:" + animId));
                boolean selected = animId.equalsIgnoreCase(currentAnim);
                java.util.List<String> loreTemplate = slotsSection.getStringList(slotKey + ".lore");
                java.util.List<String> lore = new java.util.ArrayList<>();
                for (String line : loreTemplate) {
                    lore.add(line.replace("%price%", price == 0 ? "Free" : String.valueOf(price)));
                }
                if (selected) lore.add(ColorUtil.translate("&a&lCurrently selected"));
                ItemStack item = ItemBuilder.fromString(mat).name("&r" + name).lore(lore.toArray(new String[0])).build();
                // Do not add enchant glow to CHEST (Item Drop) — it renders as a broken texture in 1.8.8
                boolean canGlow = !mat.toUpperCase().startsWith("CHEST");
                if (selected && canGlow) {
                    org.bukkit.inventory.meta.ItemMeta im = item.getItemMeta();
                    if (im != null) {
                        im.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                        item.setItemMeta(im);
                    }
                }
                inv.setItem(slotIndex, item);
            } catch (NumberFormatException ignored) {}
        }

        // Navigation — standardized bottom row (always visible, single-page menu)
        FileConfiguration animItemsConfig = plugin.getConfigManager().getItemsConfig();
        String animBackMat  = animItemsConfig.getString("change-page.back-to-shop.material", "ARROW:0");
        String animBackName = animItemsConfig.getString("change-page.back-to-shop.name", "&cBack to Shop");
        inv.setItem(maxSlots - 5, ItemBuilder.fromString(animBackMat).name(animBackName).build());

        String animDisPrevMat  = animItemsConfig.getString("change-page.no-previous-page.material", "STAINED_GLASS_PANE:8");
        String animDisPrevName = animItemsConfig.getString("change-page.no-previous-page.name", "&8« No Previous Page");
        inv.setItem(maxSlots - 9, ItemBuilder.fromString(animDisPrevMat).name(animDisPrevName).build());

        String animDisNextMat  = animItemsConfig.getString("change-page.no-next-page.material", "STAINED_GLASS_PANE:8");
        String animDisNextName = animItemsConfig.getString("change-page.no-next-page.name", "&8No Next Page »");
        inv.setItem(maxSlots - 1, ItemBuilder.fromString(animDisNextMat).name(animDisNextName).build());

        player.openInventory(inv);
    }

    public void openDeathSoundSelector(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(guis.getString("death-sound-selector.name", "Death Sounds"));
        int maxSlots = guis.getInt("death-sound-selector.max-slots", 27);
        // Extend to 54 if more than 27 entries need to fit
        int slotsNeeded = 0;
        org.bukkit.configuration.ConfigurationSection soundsSection = guis.getConfigurationSection("death-sound-selector-slots");
        if (soundsSection != null) slotsNeeded = soundsSection.getKeys(false).size() + 9;
        if (slotsNeeded > maxSlots) maxSlots = (int) Math.ceil(slotsNeeded / 9.0) * 9;
        if (maxSlots > 54) maxSlots = 54;
        Inventory inv = Bukkit.createInventory(null, maxSlots, title);

        if (soundsSection == null) {
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&cNo death sounds configured."));
            return;
        }

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        String currentSound = data != null ? data.getSelectedDeathSound() : "NONE";

        for (String slotKey : soundsSection.getKeys(false)) {
            try {
                int slotIndex = Integer.parseInt(slotKey) - 1;
                if (slotIndex < 0 || slotIndex >= maxSlots) continue;
                String name = soundsSection.getString(slotKey + ".name", "Sound");
                String mat = soundsSection.getString(slotKey + ".material", "NOTE_BLOCK:0");
                String soundId = soundsSection.getString(slotKey + ".sound", "NONE");
                int price = soundsSection.getInt(slotKey + ".price", 0);
                boolean owned = price == 0 || (data != null && data.hasPurchasedBlock("sound:" + soundId));
                boolean selected = soundId.equalsIgnoreCase(currentSound);
                java.util.List<String> loreTemplate = soundsSection.getStringList(slotKey + ".lore");
                java.util.List<String> lore = new java.util.ArrayList<>();
                for (String line : loreTemplate) {
                    lore.add(line.replace("%price%", price == 0 ? "Free" : String.valueOf(price)));
                }
                if (!owned) lore.add(ColorUtil.translate("&cNot purchased"));
                if (selected) lore.add(ColorUtil.translate("&a&lCurrently selected"));
                ItemStack it = ItemBuilder.fromString(mat).name("&r" + name).lore(lore.toArray(new String[0])).build();
                // Do not add enchant glow to FIREWORK (Firework Rocket) — unsupported in 1.8.8
                boolean canGlow = !mat.toUpperCase().startsWith("FIREWORK");
                if (selected && canGlow) {
                    org.bukkit.inventory.meta.ItemMeta im = it.getItemMeta();
                    if (im != null) { im.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true); it.setItemMeta(im); }
                }
                inv.setItem(slotIndex, it);
            } catch (NumberFormatException ignored) {}
        }

        // Navigation — standardized bottom row (always visible, single-page menu)
        FileConfiguration soundItemsConfig = plugin.getConfigManager().getItemsConfig();
        String soundBackMat  = soundItemsConfig.getString("change-page.back-to-shop.material", "ARROW:0");
        String soundBackName = soundItemsConfig.getString("change-page.back-to-shop.name", "&cBack to Shop");
        inv.setItem(maxSlots - 5, ItemBuilder.fromString(soundBackMat).name(soundBackName).build());

        String soundDisPrevMat  = soundItemsConfig.getString("change-page.no-previous-page.material", "STAINED_GLASS_PANE:8");
        String soundDisPrevName = soundItemsConfig.getString("change-page.no-previous-page.name", "&8« No Previous Page");
        inv.setItem(maxSlots - 9, ItemBuilder.fromString(soundDisPrevMat).name(soundDisPrevName).build());

        String soundDisNextMat  = soundItemsConfig.getString("change-page.no-next-page.material", "STAINED_GLASS_PANE:8");
        String soundDisNextName = soundItemsConfig.getString("change-page.no-next-page.name", "&8No Next Page »");
        inv.setItem(maxSlots - 1, ItemBuilder.fromString(soundDisNextMat).name(soundDisNextName).build());

        player.openInventory(inv);
    }

    // ===== Stats GUI =====

    public void openStatsGui(Player viewer, PlayerData data) {
        String title = ColorUtil.translate("&c&lStats &7- &f" + data.getName());
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Filler
        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // Global stats summary (center of top row)
        int totalAttempts = 0;
        int totalSuccesses = 0;
        for (PlayerData.MapStats s : data.getAllStats().values()) {
            totalAttempts += s.totalAttempts;
            totalSuccesses += s.successfulAttempts;
        }
        int globalRate = totalAttempts > 0 ? (int) ((double) totalSuccesses / totalAttempts * 100) : 0;

        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        skullMeta.setOwner(data.getName());
        skullMeta.setDisplayName(ColorUtil.translate("&c&l" + data.getName()));
        List<String> headLore = new ArrayList<>();
        headLore.add(ColorUtil.translate("&7Coins: &f" + data.getCoins()));
        headLore.add(ColorUtil.translate("&7Total Runs: &f" + totalAttempts));
        headLore.add(ColorUtil.translate("&7Successful: &f" + totalSuccesses));
        headLore.add(ColorUtil.translate("&7Success Rate: &f" + globalRate + "%"));
        skullMeta.setLore(headLore);
        head.setItemMeta(skullMeta);
        inv.setItem(4, head);

        // Per-map stats (start at slot 18)
        int slot = 18;
        for (Map.Entry<String, PlayerData.MapStats> entry : data.getAllStats().entrySet()) {
            if (slot >= 54) break;
            String mapName = entry.getKey();
            PlayerData.MapStats stats = entry.getValue();

            net.gravijet.fastbuilder.map.MapData mapData = plugin.getMapManager().getMap(mapName);
            String iconStr = mapData != null ? mapData.getIcon() : null;
            ItemBuilder builder = (iconStr != null && !iconStr.isEmpty())
                    ? ItemBuilder.fromString(iconStr)
                    : new ItemBuilder(Material.GRASS);

            String bestTime = stats.hasBestTime() ? TimeUtil.formatTime(stats.bestTime) : "N/A";
            String avgTime = stats.getAverageTime() >= 0 ? TimeUtil.formatTime(stats.getAverageTime()) : "N/A";
            int rate = stats.totalAttempts > 0 ? (int) ((double) stats.successfulAttempts / stats.totalAttempts * 100) : 0;

            List<String> lore = new ArrayList<>();
            // Global percentile for this map
            String topPercent = "";
            if (stats.hasBestTime() && plugin.getHologramManager() != null) {
                topPercent = plugin.getHologramManager().calculateTopPercent(mapName, stats.bestTime);
            }
            String bestTimeDisplay = bestTime + (topPercent.isEmpty() ? "" : " &7" + topPercent);

            lore.add(ColorUtil.translate("&7Best Time: &f" + bestTimeDisplay));
            lore.add(ColorUtil.translate("&7Average Time: &f" + avgTime));
            lore.add(ColorUtil.translate("&7Total Attempts: &f" + stats.totalAttempts));
            lore.add(ColorUtil.translate("&7Successful: &f" + stats.successfulAttempts));
            lore.add(ColorUtil.translate("&7Success Rate: &f" + rate + "%"));

            // Rank display
            if (mapData != null) {
                if (stats.hasBestTime()) {
                    String rank = mapData.getPlayerRank(stats.bestTime);
                    if (rank != null) {
                        String color = rank.equals("Diamond") ? "&b"
                                : rank.equals("Gold") ? "&6"
                                : rank.equals("Silver") ? "&7" : "&c";
                        lore.add(ColorUtil.translate("&7Rank: " + color + "&l" + rank));
                    }
                }
                // Show ALL configured rank thresholds (including unachieved)
                if (mapData.getDiamondTime() > 0 || mapData.getGoldTime() > 0
                        || mapData.getSilverTime() > 0 || mapData.getBronzeTime() > 0) {
                    lore.add(ColorUtil.translate("&8---"));
                    if (mapData.getDiamondTime() > 0) lore.add(ColorUtil.translate("&bDiamond: &f" + TimeUtil.formatTime(mapData.getDiamondTime())));
                    if (mapData.getGoldTime() > 0) lore.add(ColorUtil.translate("&6Gold: &f" + TimeUtil.formatTime(mapData.getGoldTime())));
                    if (mapData.getSilverTime() > 0) lore.add(ColorUtil.translate("&7Silver: &f" + TimeUtil.formatTime(mapData.getSilverTime())));
                    if (mapData.getBronzeTime() > 0) lore.add(ColorUtil.translate("&cBronze: &f" + TimeUtil.formatTime(mapData.getBronzeTime())));
                }
            }

            ItemStack item = builder.name("&c" + mapName).lore(lore.toArray(new String[0])).build();
            inv.setItem(slot, item);
            slot++;
            if (slot % 9 == 0) slot++; // skip border column
        }

        viewer.openInventory(inv);
    }

    // =========================================================================
    // Strict inventory lock — prevents ALL unauthorised item movement
    // =========================================================================

    /**
     * Returns true if {@code stripped} (colour-stripped title) belongs to one of
     * this plugin's GUI windows.
     */
    private boolean isPluginGui(String stripped) {
        return stripped.startsWith(ISLAND_SELECTOR_PREFIX)
                || stripped.startsWith(BLOCK_SELECTOR_PREFIX)
                || stripped.startsWith(SETTINGS_PREFIX)
                || stripped.startsWith(CONFIRM_PREFIX)
                || stripped.startsWith(MAP_SELECTOR_PREFIX)
                || stripped.startsWith(REPLAYS_PREFIX)
                || stripped.startsWith(SHOP_PREFIX)
                || stripped.startsWith(PICKAXE_SELECTOR_PREFIX)
                || stripped.startsWith(ANIMATION_SELECTOR_PREFIX)
                || stripped.startsWith(DEATH_SOUND_SELECTOR_PREFIX)
                || stripped.startsWith(STATS_PREFIX)
                || stripped.startsWith(DESIGN_SELECTOR_PREFIX);
    }

    /**
     * HIGH-priority drag-cancel: any drag (split-stack, paint) across a plugin GUI is
     * unconditionally blocked to prevent item duplication exploits.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null || top.getType() == InventoryType.CRAFTING) return;
        String title = event.getView().getTitle();
        if (title != null && isPluginGui(ColorUtil.strip(title))) {
            event.setCancelled(true);
        }
    }

    // ===== Click Handling =====

    /**
     * HIGH priority so we process before other plugins (e.g. item-protection plugins)
     * and our cancel verdict is respected by them.
     *
     * Strategy:
     *   1. Identify the top inventory using event.getView().getTopInventory() — this is
     *      always the plugin's GUI, even when the player clicks their own bottom inventory.
     *   2. If the top inventory belongs to us, IMMEDIATELY cancel the entire event —
     *      stops all item movement regardless of which sub-inventory was clicked.
     *   3. Only invoke action handlers when the click was inside the TOP inventory.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        // Always use the view's top inventory — reliable in 1.8.8 regardless of click source.
        Inventory top = event.getView().getTopInventory();
        if (top == null || top.getType() == InventoryType.CRAFTING) return;

        // getView().getTitle() is the definitive title of the open inventory.
        String title = event.getView().getTitle();
        if (title == null) return;

        String stripped = ColorUtil.strip(title);
        if (!isPluginGui(stripped)) return;

        // ---- Lock: cancel EVERYTHING in this inventory context ----
        // This cancels shift-clicks from the bottom player-inventory too.
        event.setCancelled(true);

        // Only dispatch actions for clicks inside the top GUI panel.
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || clicked != top) return;

        // ---- Route to specific handler ----
        if (stripped.startsWith(ISLAND_SELECTOR_PREFIX)) {
            handleIslandSelectorClick(event);
        } else if (stripped.startsWith(BLOCK_SELECTOR_PREFIX)) {
            handleBlockSelectorClick(event);
        } else if (stripped.startsWith(SETTINGS_PREFIX)) {
            handleSettingsClick(event);
        } else if (stripped.startsWith(CONFIRM_PREFIX)) {
            handleConfirmClick(event);
        } else if (stripped.startsWith(MAP_SELECTOR_PREFIX)) {
            handleMapSelectorClick(event);
        } else if (stripped.startsWith(REPLAYS_PREFIX)) {
            handleReplayClick(event);
        } else if (stripped.startsWith(SHOP_PREFIX)) {
            handleShopClick(event);
        } else if (stripped.startsWith(PICKAXE_SELECTOR_PREFIX)) {
            handlePickaxeSelectorClick(event);
        } else if (stripped.startsWith(ANIMATION_SELECTOR_PREFIX)) {
            handleAnimationSelectorClick(event);
        } else if (stripped.startsWith(DEATH_SOUND_SELECTOR_PREFIX)) {
            handleDeathSoundSelectorClick(event);
        } else if (stripped.startsWith(DESIGN_SELECTOR_PREFIX)) {
            handleDesignSelectorClick(event);
        }
        // STATS_PREFIX: read-only — no handler needed
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

        // Clear ALL placed blocks (including practice) on old island
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());
        }

        // Clean up CPS hologram
        if (plugin.getCpsListener() != null) {
            plugin.getCpsListener().cleanupPlayer(player.getUniqueId());
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

        // Back to Shop button
        if (slot == maxSlots - 5) {
            openShop(player);
            return;
        }

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

        // Per-item permission: fastbuilder.block.<material> (e.g. fastbuilder.block.stained_clay.5)
        String blockPerm = "fastbuilder.block." + mat.toLowerCase().replace(":", ".");
        boolean owned = price == 0 || data.hasPurchasedBlock(mat)
                || player.hasPermission("fastbuilder.blocks.*")
                || player.hasPermission(blockPerm);

        if (!owned) {
            // Try to purchase
            if (data.getCoins() >= price) {
                data.removeCoins(price);
                data.purchaseBlock(mat);
                // Auto-equip on purchase
                String oldBlockOnPurchase = data.getSelectedBlock();
                data.setSelectedBlock(mat);
                player.closeInventory();
                if (plugin.getHotbarManager() != null) {
                    plugin.getHotbarManager().updateBlockSlot(player);
                }
                // Block type changed — always reset the current track and clear all placed blocks
                if (oldBlockOnPurchase != null && !oldBlockOnPurchase.equals(mat)
                        && plugin.getGameplayManager() != null) {
                    net.gravijet.fastbuilder.gameplay.RunSession swapSession =
                            plugin.getGameplayManager().getSession(player.getUniqueId());
                    if (swapSession != null) {
                        plugin.getGameplayManager().resetRun(player);
                    }
                }
                String blockName = pageSection.getString(blockIndex + ".name", "Block");
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                        + "&fBlock purchased and selected: &c" + blockName + " &7(&f" + price + " coins&7)"));
            } else {
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                        + "&cNot enough coins! You need &f" + price + " &ccoins."));
            }
            return;
        }

        // Select the block
        String oldBlock = data.getSelectedBlock();
        data.setSelectedBlock(mat);
        player.closeInventory();

        // Update hotbar block
        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().updateBlockSlot(player);
        }

        // Block type changed — always reset the current track and clear all placed blocks instantly
        if (oldBlock != null && !oldBlock.equals(mat) && plugin.getGameplayManager() != null) {
            net.gravijet.fastbuilder.gameplay.RunSession swapSession =
                    plugin.getGameplayManager().getSession(player.getUniqueId());
            if (swapSession != null) {
                plugin.getGameplayManager().resetRun(player);
            }
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

        String prefix = plugin.getConfigManager().getPrefix();

        switch (gui) {
            case "infinite_blocks": {
                PlayerData iData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
                if (iData == null) break;
                // Permission bypass: skip purchase entirely
                if (player.hasPermission("fastbuilder.cosmetic.infiniteblocks")) {
                    iData.setInfiniteBlocksUnlocked(true);
                    boolean newState = !iData.hasInfiniteBlocks();
                    iData.setInfiniteBlocks(newState);
                    String stateStr = newState ? "&aenabled" : "&cdisabled";
                    player.sendMessage(ColorUtil.translate(prefix + "&fInfinite Blocks " + stateStr + "&f."));
                    openSettings(player);
                    break;
                }
                if (!iData.hasInfiniteBlocksUnlocked()) {
                    int unlockCost = plugin.getConfigManager().getInfiniteBlocksUnlockCost();
                    if (iData.removeCoins(unlockCost)) {
                        iData.setInfiniteBlocksUnlocked(true);
                        iData.setInfiniteBlocks(true);
                        player.sendMessage(ColorUtil.translate(prefix
                                + "&fInfinite Blocks unlocked and enabled for &c" + unlockCost + " &fcoins."));
                        plugin.getPlayerManager().savePlayerData(player.getUniqueId());
                    } else {
                        player.sendMessage(ColorUtil.translate(prefix
                                + "&cNot enough coins! Infinite Blocks costs &f" + unlockCost + " &ccoins."));
                    }
                } else {
                    boolean newState = !iData.hasInfiniteBlocks();
                    iData.setInfiniteBlocks(newState);
                    String stateStr = newState ? "&aenabled" : "&cdisabled";
                    player.sendMessage(ColorUtil.translate(prefix + "&fInfinite Blocks " + stateStr + "&f."));
                }
                openSettings(player);
                break;
            }
            case "reset_stats":
                if (!player.hasPermission("fastbuilder.stats.reset")) {
                    player.sendMessage(ColorUtil.translate(prefix
                            + "&cYou don't have permission to reset your stats."));
                    break;
                }
                player.closeInventory();
                openConfirmStatsReset(player);
                break;
            case "custom_length": {
                if (!player.hasPermission("fastbuilder.feature.custom_length")) {
                    player.sendMessage(ColorUtil.translate(prefix
                            + "&cYou don't have permission to use custom length."));
                    break;
                }
                net.gravijet.fastbuilder.gameplay.RunSession clRun = plugin.getGameplayManager() != null
                        ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
                if (clRun == null) {
                    player.sendMessage(ColorUtil.translate(prefix
                            + "&cYou must be on an island to use custom length."));
                    break;
                }
                net.gravijet.fastbuilder.map.MapData clMap = plugin.getMapManager().getMap(clRun.getMapName());
                if (clMap == null || !clMap.hasCustomLength()) {
                    player.sendMessage(ColorUtil.translate(prefix
                            + "&cCustom length is not available on your current map."));
                    break;
                }
                PlayerData clData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
                if (clData == null) break;

                int clCurrent = clData.getCustomLength(clRun.getMapName());
                if (clCurrent <= 0) clCurrent = clMap.getMinCustomLength();

                // Determine delta from click type
                int clDelta;
                org.bukkit.event.inventory.ClickType clClick = event.getClick();
                if (clClick == org.bukkit.event.inventory.ClickType.LEFT) clDelta = -1;
                else if (clClick == org.bukkit.event.inventory.ClickType.SHIFT_LEFT) clDelta = -10;
                else if (clClick == org.bukkit.event.inventory.ClickType.RIGHT) clDelta = 1;
                else if (clClick == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) clDelta = 10;
                else break; // middle-click etc — ignore

                int clNew = Math.max(clMap.getMinCustomLength(),
                        Math.min(clMap.getMaxCustomLength(), clCurrent + clDelta));

                if (clNew == clCurrent) {
                    String limitMsg = (clDelta < 0)
                            ? "&cAlready at minimum (" + clMap.getMinCustomLength() + " blocks)."
                            : "&cAlready at maximum (" + clMap.getMaxCustomLength() + " blocks).";
                    player.sendMessage(ColorUtil.translate(prefix + limitMsg));
                    break;
                }

                clData.setCustomLength(clRun.getMapName(), clNew);

                // Move the end-island platform physically
                if (plugin.getGameplayManager() != null) {
                    plugin.getGameplayManager().placeEndPlatform(player, clMap, clRun, clNew);
                }

                player.sendMessage(ColorUtil.translate(prefix
                        + "&fCustom length set to &e" + clNew + " &fblocks."));
                openSettings(player);
                break;
            }
            case "practice_mode":
                if (!player.hasPermission("fastbuilder.feature.practice_mode")) {
                    player.sendMessage(ColorUtil.translate(prefix
                            + "&cYou don't have permission to use practice mode."));
                    break;
                }
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
                        player.sendMessage(ColorUtil.translate(prefix
                                + "&fAll practice blocks have been cleared."));
                    }

                    String stateStr = newState ? "&aenabled" : "&cdisabled";
                    player.sendMessage(ColorUtil.translate(prefix + "&fPractice mode " + stateStr + "&f."));
                }
                // Update hotbar items to reflect practice mode change
                if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
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
                // Instant hologram sync — update immediately without requiring island switch
                if (plugin.getHologramManager() != null) {
                    net.gravijet.fastbuilder.player.PlayerData pData =
                            plugin.getPlayerManager().getCachedData(player.getUniqueId());
                    if (pData != null && pData.getLastMap() != null) {
                        plugin.getHologramManager().updateHologram(
                                pData.getLastMap(), pData.getLastIsland(), player);
                    }
                }
                plugin.getPlayerManager().savePlayerData(player.getUniqueId());
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

        // Check if already on this map
        PlayerData existingData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (existingData != null && map.getName().equalsIgnoreCase(existingData.getLastMap())) {
            player.closeInventory();
            String raw = plugin.getConfigManager().getMessage("map-already-on");
            if (raw == null || raw.isEmpty()) raw = plugin.getConfigManager().getPrefix() + "&cYou are already on this map.";
            player.sendMessage(ColorUtil.translate(raw.replace("%prefix%", plugin.getConfigManager().getPrefix())));
            return;
        }

        player.closeInventory();

        // Clear all placed blocks and despawn NPC/hologram before switching
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());
        }
        // Clean up CPS hologram
        if (plugin.getCpsListener() != null) {
            plugin.getCpsListener().cleanupPlayer(player.getUniqueId());
        }
        if (existingData != null) {
            if (plugin.getNpcManager() != null) plugin.getNpcManager().despawnNpc(player.getUniqueId());
            if (plugin.getHologramManager() != null && existingData.getLastMap() != null) {
                plugin.getHologramManager().removeHologram(existingData.getLastMap(), existingData.getLastIsland());
            }
        }

        // Free current island
        plugin.getMapManager().freeAllIslands(player.getUniqueId());
        if (plugin.getGameplayManager() != null) plugin.getGameplayManager().removeSession(player.getUniqueId());

        // Assign a free island
        int island = plugin.getMapManager().assignFreeIsland(map.getName(), player.getUniqueId(), player.getName());
        if (island < 0) {
            String noIsland = plugin.getConfigManager().getMessage("no-islands");
            if (noIsland == null || noIsland.isEmpty()) noIsland = plugin.getConfigManager().getMessage("no-free-islands");
            player.sendMessage(ColorUtil.translate(noIsland.replace("%prefix%", plugin.getConfigManager().getPrefix())));
            return;
        }

        // Update player data
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId(), player.getName());
        data.setLastMap(map.getName());
        data.setLastIsland(island);

        player.teleport(map.getIslandSpawn(island));

        // Create gameplay session
        if (plugin.getGameplayManager() != null) plugin.getGameplayManager().createSession(player.getUniqueId(), map.getName(), island);

        // Give hotbar items
        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().giveItems(player);
        }

        // Spawn NPC and hologram
        if (plugin.getNpcManager() != null) plugin.getNpcManager().spawnNpc(player, map.getIslandNpcLocation(island));
        if (plugin.getHologramManager() != null) plugin.getHologramManager().updateHologram(map.getName(), island, player);

        // Check autoscale
        plugin.getMapManager().checkAutoscale(map);

        // Update scoreboard
        plugin.getScoreboardManager().updateScoreboard(player);

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

    private void handleShopClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;
        String name = ColorUtil.strip(item.getItemMeta().getDisplayName());
        player.closeInventory();
        if (name.equals("Blocks")) {
            openBlockSelector(player, 1);
        } else if (name.startsWith("Pickaxes")) {
            openPickaxeSelector(player);
        } else if (name.equals("Reset Animations")) {
            openAnimationSelector(player);
        } else if (name.equals("Death Sounds")) {
            openDeathSoundSelector(player);
        } else if (name.equals("Island Designs")) {
            openDesignSelector(player);
        }
        // One-Click Pick is now in the Pickaxe Shop
    }

    /** Handles One-Click Pick purchase/toggle from within the Pickaxe Shop. */
    private void handleOneClickPickShopClick(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int price = guis.getInt("one-click-pick.price", 5000);
        String prefix = plugin.getConfigManager().getPrefix();
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        boolean purchased = data.hasPurchasedBlock("cosmetic:one_click_pick")
                || player.hasPermission("fastbuilder.shop.pickaxe.one_click_pick");
        if (!purchased) {
            // Cannot re-purchase — this is a strict permission gate
            if (player.hasPermission("fastbuilder.shop.pickaxe.one_click_pick")) {
                player.sendMessage(ColorUtil.translate(prefix + "&cYou already have One-Click Pick via permission."));
                return;
            }
            if (data.getCoins() >= price) {
                data.removeCoins(price);
                data.purchaseBlock("cosmetic:one_click_pick");
                data.setOneClickPick(true);
                plugin.getPlayerManager().savePlayerData(player.getUniqueId());
                player.sendMessage(ColorUtil.translate(prefix + "&fOne-Click Pick &apurchased and enabled! &7(&f" + price + " coins&7)"));
            } else {
                player.sendMessage(ColorUtil.translate(prefix + "&cNot enough coins! You need &f" + price + " &ccoins."));
            }
        } else {
            // Toggle on/off if already owned
            data.setOneClickPick(!data.hasOneClickPick());
            plugin.getPlayerManager().savePlayerData(player.getUniqueId());
            player.sendMessage(ColorUtil.translate(prefix + "&fOne-Click Pick "
                    + (data.hasOneClickPick() ? "&aenabled" : "&cdisabled") + "&f."));
        }
        openPickaxeSelector(player);
    }

    private void handlePickaxeSelectorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;
        String displayName = ColorUtil.strip(item.getItemMeta().getDisplayName());
        if (displayName.equals("Back to Shop")) {
            player.closeInventory();
            openShop(player);
            return;
        }

        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int maxSlots = guis.getInt("pickaxe-selector.max-slots", 27);
        int ocpSlot  = guis.getInt("one-click-pick.slot", 23) - 1; // must match openPickaxeSelector
        if (event.getSlot() == ocpSlot) {
            handleOneClickPickShopClick(player);
            return;
        }

        org.bukkit.configuration.ConfigurationSection slotsSection = guis.getConfigurationSection("pickaxe-selector-slots");
        if (slotsSection == null) return;

        int slot = event.getSlot() + 1;
        if (!slotsSection.isConfigurationSection(String.valueOf(slot))) return;

        String mat = slotsSection.getString(slot + ".material", "DIAMOND_PICKAXE:0");
        int price = slotsSection.getInt(slot + ".price", 0);
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        // Per-item permission: fastbuilder.pickaxe.<material>
        String pickPerm = "fastbuilder.pickaxe." + mat.toLowerCase().replace(":", ".");
        boolean owned = price == 0 || data.hasPurchasedBlock("pickaxe:" + mat)
                || player.hasPermission("fastbuilder.blocks.*")
                || player.hasPermission(pickPerm);
        if (!owned) {
            if (data.getCoins() >= price) {
                data.removeCoins(price);
                data.purchaseBlock("pickaxe:" + mat);
                // Auto-equip on purchase
                data.setSelectedPickaxe(mat);
                player.closeInventory();
                if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
                String pName = slotsSection.getString(slot + ".name", "Pickaxe");
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&fPickaxe purchased and selected: &c" + pName + " &7(&f" + price + " coins&7)"));
            } else {
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&cNot enough coins! You need &f" + price + " &ccoins."));
            }
            return;
        }

        data.setSelectedPickaxe(mat);
        player.closeInventory();
        if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
        String pName = slotsSection.getString(slot + ".name", "Pickaxe");
        player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&fSelected pickaxe: &c" + pName));
    }

    private void handleAnimationSelectorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;
        String displayName = ColorUtil.strip(item.getItemMeta().getDisplayName());
        if (displayName.equals("Back to Shop")) {
            player.closeInventory();
            openShop(player);
            return;
        }

        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        org.bukkit.configuration.ConfigurationSection slotsSection = guis.getConfigurationSection("animation-selector-slots");
        if (slotsSection == null) return;

        int slot = event.getSlot() + 1;
        if (!slotsSection.isConfigurationSection(String.valueOf(slot))) return;

        String animId = slotsSection.getString(slot + ".animation", "NONE");
        int price = slotsSection.getInt(slot + ".price", 0);
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        // Per-item permission: fastbuilder.animation.<id>
        boolean owned = price == 0 || data.hasPurchasedBlock("anim:" + animId)
                || player.hasPermission("fastbuilder.animation." + animId.toLowerCase());
        if (!owned) {
            if (data.getCoins() >= price) {
                data.removeCoins(price);
                data.purchaseBlock("anim:" + animId);
                // Auto-equip on purchase
                data.setSelectedAnimation(animId);
                player.closeInventory();
                String aName = slotsSection.getString(slot + ".name", "Animation");
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&fAnimation purchased and selected: &c" + aName + " &7(&f" + price + " coins&7)"));
            } else {
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&cNot enough coins! You need &f" + price + " &ccoins."));
            }
            return;
        }

        data.setSelectedAnimation(animId);
        player.closeInventory();
        String aName = slotsSection.getString(slot + ".name", "Animation");
        player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&fSelected animation: &c" + aName));
    }

    private void handleDeathSoundSelectorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;
        String displayName = ColorUtil.strip(item.getItemMeta().getDisplayName());
        if (displayName.equals("Back to Shop")) {
            player.closeInventory();
            openShop(player);
            return;
        }

        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        org.bukkit.configuration.ConfigurationSection slotsSection = guis.getConfigurationSection("death-sound-selector-slots");
        if (slotsSection == null) return;

        int slot = event.getSlot() + 1;
        if (!slotsSection.isConfigurationSection(String.valueOf(slot))) return;

        String soundId = slotsSection.getString(slot + ".sound", "NONE");
        int price = slotsSection.getInt(slot + ".price", 0);
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        String prefix = plugin.getConfigManager().getPrefix();
        // Per-item permission: fastbuilder.sound.<id>
        boolean owned = price == 0 || data.hasPurchasedBlock("sound:" + soundId)
                || player.hasPermission("fastbuilder.sound." + soundId.toLowerCase());
        if (!owned) {
            if (data.getCoins() >= price) {
                data.removeCoins(price);
                data.purchaseBlock("sound:" + soundId);
                data.setSelectedDeathSound(soundId);
                player.closeInventory();
                String sName = slotsSection.getString(slot + ".name", "Sound");
                player.sendMessage(ColorUtil.translate(prefix + "&fDeath sound purchased and selected: &c" + sName + " &7(&f" + price + " coins&7)"));
            } else {
                player.sendMessage(ColorUtil.translate(prefix + "&cNot enough coins! You need &f" + price + " &ccoins."));
            }
            return;
        }

        data.setSelectedDeathSound(soundId);
        player.closeInventory();
        String sName = slotsSection.getString(slot + ".name", "Sound");
        player.sendMessage(ColorUtil.translate(prefix + "&fSelected death sound: &c" + sName));
    }

    private void handleDesignSelectorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        String displayName = ColorUtil.strip(item.getItemMeta().getDisplayName());
        if (displayName.equals("Back to Shop")) {
            player.closeInventory();
            openShop(player);
            return;
        }

        PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (pData == null) return;
        String mapName = pData.getLastMap();
        if (mapName == null) return;
        net.gravijet.fastbuilder.map.MapData map = plugin.getMapManager().getMap(mapName);
        if (map == null) return;

        // Extract the template key from the lore ("Template: <key>")
        if (!item.getItemMeta().hasLore()) return;
        String templateKey = null;
        for (String loreLine : item.getItemMeta().getLore()) {
            String stripped = ColorUtil.strip(loreLine);
            if (stripped.startsWith("Template: ")) {
                templateKey = stripped.substring("Template: ".length()).trim();
                break;
            }
        }
        if (templateKey == null) return;

        // Validate template exists
        if (!map.getAllTemplates().contains(templateKey)) {
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                    + "&cDesign not found: &f" + templateKey));
            return;
        }

        pData.setSelectedDesign(mapName, templateKey);
        player.closeInventory();

        // Spec: "Clicking a design must instantly change the island and clear all blocks immediately."
        net.gravijet.fastbuilder.gameplay.RunSession designSession =
                plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
        if (designSession != null) {
            int islandIdx = designSession.getIslandIndex();
            final org.bukkit.Location islandMin = map.getIslandMin(islandIdx);
            final org.bukkit.Location islandMax = map.getIslandMax(islandIdx);
            final String finalTemplateKey = templateKey;
            final net.gravijet.fastbuilder.map.MapData finalMap = map;

            // Clear all placed blocks immediately
            plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());

            // Clear the island area and paste the new template
            plugin.getFawePaster().clearRegion(
                    map.getWorld(),
                    islandMin.getBlockX(), islandMin.getBlockY(), islandMin.getBlockZ(),
                    islandMax.getBlockX(), islandMax.getBlockY(), islandMax.getBlockZ(),
                    new Runnable() {
                        @Override
                        public void run() {
                            plugin.getFawePaster().pasteTemplate(
                                    finalMap.getWorld(),
                                    finalTemplateKey,
                                    islandMin.getBlockX(), islandMin.getBlockY(), islandMin.getBlockZ(),
                                    null
                            );
                        }
                    }
            );

            // Reset the session so the run starts fresh
            designSession.reset();
            if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
        }

        player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                + "&fIsland design &capplied instantly: &f" + templateKey));
    }
}
