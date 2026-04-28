package net.gravijet.fastbuilder.gui;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.economy.BoosterType;
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
    private static final String BOOSTER_HUB_PREFIX = "Booster Menu";
    private static final String BOOSTER_SHOP_PREFIX = "Booster Shop";
    private static final String BOOSTER_INVENTORY_PREFIX = "Booster Inventory";
    private static final String PICKAXE_SELECTOR_PREFIX = "Pickaxe Shop";
    private static final String ANIMATION_SELECTOR_PREFIX = "Reset Animations";
    private static final String DEATH_SOUND_SELECTOR_PREFIX = "Death Sounds";
    private static final String STATS_PREFIX = "Stats";
    private static final String DESIGN_SELECTOR_PREFIX = "Island Designs";
    private static final String LEADERBOARD_PREFIX = "Leaderboard";
    private static final String CUSTOM_LENGTH_MENU_PREFIX = "Custom Length";

    // Track which block selector page a player is on
    private final Map<UUID, Integer> blockSelectorPages = new HashMap<>();

    // Track pickaxe/animation selector pages
    private final Map<UUID, Integer> pickaxePages = new HashMap<>();
    private final Map<UUID, Integer> animationPages = new HashMap<>();

    // Track island selector pages
    private final Map<UUID, Integer> islandPages = new HashMap<>();

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
        int pageSize = guis.getInt("island-selector-gui.page-size", 45);
        pageSize = Math.max(9, Math.min(45, (pageSize / 9) * 9));
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        int currentIsland = data != null ? data.getLastIsland() : -1;
        int page = currentIsland >= 0 ? (currentIsland / pageSize) + 1 : 1;
        openIslandSelectorPage(player, map, page);
    }

    public void openIslandSelectorPage(Player player, MapData map, int page) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        FileConfiguration itemsConfig = plugin.getConfigManager().getItemsConfig();

        String titleTemplate = ColorUtil.translate(guis.getString("island-selector", "Island Selector"));
        List<IslandInstance> islands = plugin.getMapManager().getIslands(map.getName());
        int pageSize = guis.getInt("island-selector-gui.page-size", 45);
        // Ensure pageSize is valid (multiple of 9, 9–45)
        pageSize = Math.max(9, Math.min(45, (pageSize / 9) * 9));
        int totalPages = islands.isEmpty() ? 1 : (int) Math.ceil(islands.size() / (double) pageSize);
        page = Math.max(1, Math.min(page, totalPages));

        // Auto-size: if single page, fit exactly to island count + 1 nav row; multi-page = 54
        int contentOnPage = Math.min(pageSize, Math.max(0, islands.size() - (page - 1) * pageSize));
        int size;
        if (totalPages > 1) {
            size = 54;
        } else {
            // Round up to the next multiple of 9, then add 1 nav row
            int contentRows = (int) Math.ceil(Math.max(1, contentOnPage) / 9.0);
            size = (contentRows + 1) * 9;
            if (size > 54) size = 54;
            if (size < 18) size = 18;
        }

        String title = titleTemplate;
        if (totalPages > 1) {
            title = ColorUtil.translate(guis.getString("island-selector", "Island Selector")
                    + " &8- &7" + page + "/" + totalPages);
        }

        Inventory inv = Bukkit.createInventory(null, size, title);

        // Filler for nav row
        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = size - 9; i < size; i++) inv.setItem(i, filler);

        // Island items
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, islands.size());
        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            if (slot >= size - 9) break; // do not bleed into nav row
            IslandInstance island = islands.get(i);
            ItemStack item;
            int displayNumber = i + 1;

            if (island.isOccupied()) {
                item = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
                SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
                skullMeta.setOwner(island.getOccupantName());
                skullMeta.setDisplayName(ColorUtil.translate("&c#" + displayNumber + " &7- &f" + island.getOccupantName()));
                java.util.List<String> lore = new java.util.ArrayList<>();
                lore.add(ColorUtil.translate("&cOccupied"));
                skullMeta.setLore(lore);
                item.setItemMeta(skullMeta);
            } else {
                item = new ItemBuilder(Material.SKULL_ITEM, (byte) 3)
                        .name("&a#" + displayNumber)
                        .lore("&aClick to join")
                        .build();
            }
            inv.setItem(slot, item);
        }

        // Back button — center of nav row
        String backMat  = itemsConfig.getString("island-selector.back-button.material", "BARRIER:0");
        String backName = itemsConfig.getString("island-selector.back-button.name", "&cClose");
        inv.setItem(size - 5, ItemBuilder.fromString(backMat).name(backName).build());

        // Pagination arrows — only rendered when more than one page
        if (totalPages > 1) {
            String prevMat  = itemsConfig.getString("change-page.previous-page.material", "ARROW:0");
            String prevName = itemsConfig.getString("change-page.previous-page.name", "&c« Previous Page");
            String nextMat  = itemsConfig.getString("change-page.next-page.material", "ARROW:0");
            String nextName = itemsConfig.getString("change-page.next-page.name", "&aNext Page »");
            String disPrevMat  = itemsConfig.getString("change-page.no-previous-page.material", "ARROW:0");
            String disPrevName = itemsConfig.getString("change-page.no-previous-page.name", "&8« No Previous Page");
            String disNextMat  = itemsConfig.getString("change-page.no-next-page.material", "ARROW:0");
            String disNextName = itemsConfig.getString("change-page.no-next-page.name", "&8No Next Page »");

            if (page > 1) {
                inv.setItem(size - 9, ItemBuilder.fromString(prevMat).name(prevName).build());
            } else {
                inv.setItem(size - 9, ItemBuilder.fromString(disPrevMat).name(disPrevName).build());
            }
            if (page < totalPages) {
                inv.setItem(size - 1, ItemBuilder.fromString(nextMat).name(nextName).build());
            } else {
                inv.setItem(size - 1, ItemBuilder.fromString(disNextMat).name(disNextName).build());
            }
        }

        islandPages.put(player.getUniqueId(), page);
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

        String title = ColorUtil.translate(titleTemplate
                .replace("%page%", String.valueOf(page))
                .replace("%max_page%", String.valueOf(maxPage)));
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

                // Skip wall and fence blocks — not suitable for FastBuilder gameplay
                if (isWallOrFence(mat)) continue;

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

        // Navigation — bottom row
        FileConfiguration itemsConfig = plugin.getConfigManager().getItemsConfig();

        // Back to Shop — center of bottom row (always rendered)
        String backMat  = itemsConfig.getString("change-page.back-to-shop.material", "BARRIER:0");
        String backName = itemsConfig.getString("change-page.back-to-shop.name", "&cBack to Shop");
        inv.setItem(maxSlots - 5, ItemBuilder.fromString(backMat).name(backName).build());

        // Prev / Next arrows — only rendered when there are multiple pages
        if (maxPage > 1) {
            if (page > 1) {
                String prevMat  = itemsConfig.getString("change-page.previous-page.material", "ARROW:0");
                String prevName = itemsConfig.getString("change-page.previous-page.name", "&c« Previous Page");
                inv.setItem(maxSlots - 9, ItemBuilder.fromString(prevMat).name(prevName).build());
            } else {
                String disMat  = itemsConfig.getString("change-page.no-previous-page.material", "ARROW:0");
                String disName = itemsConfig.getString("change-page.no-previous-page.name", "&8« No Previous Page");
                inv.setItem(maxSlots - 9, ItemBuilder.fromString(disMat).name(disName).build());
            }
            if (page < maxPage) {
                String nextMat  = itemsConfig.getString("change-page.next-page.material", "ARROW:0");
                String nextName = itemsConfig.getString("change-page.next-page.name", "&aNext Page »");
                inv.setItem(maxSlots - 1, ItemBuilder.fromString(nextMat).name(nextName).build());
            } else {
                String disMat  = itemsConfig.getString("change-page.no-next-page.material", "ARROW:0");
                String disName = itemsConfig.getString("change-page.no-next-page.name", "&8No Next Page »");
                inv.setItem(maxSlots - 1, ItemBuilder.fromString(disMat).name(disName).build());
            }
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
                                if (clValue <= 0) clValue = clMap.getBaseCustomLength() > 0
                                        ? clMap.getBaseCustomLength() : clMap.getEffectiveMinCustomLength();
                                clValueStr = "&f" + clValue;
                                clMinStr = String.valueOf(clMap.getEffectiveMinCustomLength());
                                clMaxStr = String.valueOf(clMap.getEffectiveMaxCustomLength());
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

    // ===== Custom Length Sub-Menu =====

    /**
     * Opens the dedicated Custom Length menu for the player.
     * Slot 11 = X control (distance), slot 13 = Y control (height),
     * slot 15 = reset, slot 22 = back.
     */
    public void openCustomLengthMenu(Player player) {
        net.gravijet.fastbuilder.gameplay.RunSession run = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
        if (run == null) return;

        net.gravijet.fastbuilder.map.MapData map = plugin.getMapManager().getMap(run.getMapName());
        if (map == null || !map.hasCustomLength()) return;

        PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());

        // Resolve current X (distance)
        int currentX = pData != null ? pData.getCustomLength(run.getMapName()) : 0;
        if (currentX <= 0) currentX = map.getBaseCustomLength() > 0
                ? map.getBaseCustomLength() : map.getEffectiveMinCustomLength();
        int minX = map.getEffectiveMinCustomLength();
        int maxX = map.getEffectiveMaxCustomLength();

        // Resolve current Y (height offset)
        int currentY = pData != null ? pData.getCustomLengthY(run.getMapName()) : 0;
        int minY = plugin.getConfigManager().getCustomLengthMinY();
        int maxY = plugin.getConfigManager().getCustomLengthMaxY();

        FileConfiguration guisCfg = plugin.getConfigManager().getGuisConfig();
        int menuSize = guisCfg.getInt("custom-length-menu.max-slots", 27);
        menuSize = Math.max(27, Math.min(54, ((menuSize + 8) / 9) * 9));

        String title = ColorUtil.translate("&cCustom Length &7- &f" + map.getName());
        Inventory inv = Bukkit.createInventory(null, menuSize, title);

        // Fill all slots with gray glass pane
        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = 0; i < menuSize; i++) inv.setItem(i, filler);

        // Slot 11 — X-axis (distance) control — STICK
        inv.setItem(11, new ItemBuilder(Material.STICK)
                .name("&eX &7— Distance")
                .lore("&7Click to adjust distance")
                .build());

        // Slot 13 — Y-axis (height) control — BLAZE_ROD
        inv.setItem(13, new ItemBuilder(Material.BLAZE_ROD)
                .name("&eY &7— Height Offset")
                .lore("&7Click to adjust height")
                .build());

        // Slot 15 — Reset both axes to defaults — BEDROCK
        inv.setItem(15, new ItemBuilder(Material.BEDROCK)
                .name("&cReset to Default")
                .lore("&7Puts the island back at the",
                        "&7default distance and height.")
                .build());

        // Back button — center of last row
        int backSlot = menuSize - 5;
        inv.setItem(backSlot, new ItemBuilder(Material.BARRIER)
                .name("&cBack")
                .lore("&7Return to Settings")
                .build());

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

    // ===== Booster Hub =====

    /**
     * Opens the Booster Hub — a clean selection screen with two navigation buttons:
     * Booster Shop and Booster Inventory.
     *
     * Layout (27 slots):
     *   Row 0: filler × 9
     *   Row 1: filler filler [Shop:11] filler [Status:13] filler [Inv:15] filler filler
     *   Row 2: filler × 4  [Back:22]  filler × 4
     */
    public void openBoosterHub(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(guis.getString("booster-hub.name", BOOSTER_HUB_PREFIX));
        int shopSlot  = guis.getInt("booster-hub.shop-slot", 11);
        int statSlot  = guis.getInt("booster-hub.status-slot", 13);
        int invSlot   = guis.getInt("booster-hub.inventory-slot", 15);
        int backSlot  = guis.getInt("booster-hub.back-slot", 22);

        int size = 27;
        Inventory inv = Bukkit.createInventory(null, size, title);

        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = 0; i < size; i++) inv.setItem(i, filler);

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        boolean hasActive = data != null && data.getBoosterExpiry() > System.currentTimeMillis();
        double activeMult = hasActive ? data.getBoosterMultiplier() : 1.0;
        int ownedTotal = data != null ? countOwnedBoosters(data) : 0;

        // Shop button
        inv.setItem(shopSlot, new ItemBuilder(Material.NETHER_STAR)
                .name("&cBooster Shop")
                .lore("&7Browse and purchase boosters",
                      "&7that multiply your coin earnings.",
                      "",
                      "&fClick to open")
                .build());

        // Active status indicator
        if (hasActive) {
            String remaining = plugin.getBoosterManager().formatRemaining(player.getUniqueId());
            inv.setItem(statSlot, new ItemBuilder(Material.POTION, (byte) 0)
                    .data((short) 8201)
                    .name("&c" + formatMult(activeMult) + " Coin Booster &factive")
                    .lore("&7Remaining: &f" + remaining,
                          "",
                          "&7All coin rewards are multiplied.")
                    .hideFlags()
                    .build());
        } else {
            inv.setItem(statSlot, new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 14)
                    .name("&cNo active booster")
                    .lore(ownedTotal > 0
                            ? "&7You have &f" + ownedTotal + " booster(s) &7ready to activate."
                            : "&7Purchase a booster from the shop first.")
                    .build());
        }

        // Inventory button
        String invLore1 = ownedTotal > 0
                ? "&7You own &f" + ownedTotal + " booster(s)&7."
                : "&7You don't own any boosters yet.";
        inv.setItem(invSlot, new ItemBuilder(Material.CHEST)
                .name("&cBooster Inventory")
                .lore(invLore1,
                      "&7Activate one to start multiplying coins.",
                      "",
                      "&fClick to open")
                .build());

        // Back button
        FileConfiguration itemsCfg = plugin.getConfigManager().getItemsConfig();
        String backMat  = itemsCfg.getString("change-page.back-to-shop.material", "BARRIER:0");
        String backName = itemsCfg.getString("change-page.back-to-shop.name", "&cBack to Shop");
        inv.setItem(backSlot, ItemBuilder.fromString(backMat).name(backName).build());

        player.openInventory(inv);
    }

    private void handleBoosterHubClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int shopSlot = guis.getInt("booster-hub.shop-slot", 11);
        int invSlot  = guis.getInt("booster-hub.inventory-slot", 15);
        int backSlot = guis.getInt("booster-hub.back-slot", 22);

        if (slot == shopSlot) {
            player.closeInventory();
            openBoosterShop(player);
        } else if (slot == invSlot) {
            player.closeInventory();
            openBoosterInventory(player);
        } else if (slot == backSlot) {
            player.closeInventory();
            openShop(player);
        }
    }

    // ===== Booster Shop =====

    /**
     * Opens the Booster Shop — displays all available booster types.
     * The inventory size scales with booster count so there are never empty content rows.
     * Clicking a booster purchases it into the player's Booster Inventory (not auto-activated).
     */
    public void openBoosterShop(Player player) {
        List<BoosterType> types = plugin.getConfigManager().getBoosterTypes();
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title   = ColorUtil.translate(guis.getString("booster-shop.name", BOOSTER_SHOP_PREFIX));
        int statSlot   = guis.getInt("booster-shop.status-slot", 4);
        int invBtnSlot = guis.getInt("booster-shop.inventory-btn-slot", 45);
        int backSlot   = guis.getInt("booster-shop.back-slot", 49);

        // Size inventory to content: header row + content rows + footer row (min 3 rows)
        int contentCount = Math.min(types.size(), 36);
        int rows = Math.max(3, 2 + (int) Math.ceil(contentCount / 9.0));
        int size = rows * 9;
        // Nav slots in the last row
        int navRowStart = (rows - 1) * 9;
        int actualInvBtnSlot = navRowStart;
        int actualBackSlot   = navRowStart + 4;

        Inventory inv = Bukkit.createInventory(null, size, title);
        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = 0; i < size; i++) inv.setItem(i, filler);

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        long now = System.currentTimeMillis();
        boolean hasActive = data != null && data.getBoosterExpiry() > now;
        double activeMult = hasActive ? data.getBoosterMultiplier() : 1.0;

        // Status indicator in header row
        if (hasActive) {
            String remaining = plugin.getBoosterManager().formatRemaining(player.getUniqueId());
            inv.setItem(statSlot, new ItemBuilder(Material.POTION, (byte) 0)
                    .data((short) 8201)
                    .name("&c" + formatMult(activeMult) + " Coin Booster &factive")
                    .lore("&7Remaining: &f" + remaining,
                          "",
                          "&7All coin rewards are multiplied.")
                    .hideFlags()
                    .build());
        } else {
            inv.setItem(statSlot, new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 14)
                    .name("&cNo active booster")
                    .lore("&7Purchase a booster below, then",
                          "&7activate it from &fBooster Inventory&7.")
                    .build());
        }

        // Booster type items starting at slot 9
        int coins = data != null ? data.getCoins() : 0;
        for (int i = 0; i < contentCount; i++) {
            BoosterType type = types.get(i);
            boolean canAfford = coins >= type.price;
            int owned = data != null ? data.getBoosterCount(type.id) : 0;

            String affordLine = canAfford
                    ? "&fClick to purchase &7(&c" + type.price + " coins&7)"
                    : "&cNeed &f" + type.price + " coins &7(have &f" + coins + "&7)";
            String ownedLine = owned > 0 ? "&7Owned: &f" + owned : "&7Not owned";

            inv.setItem(9 + i, new ItemBuilder(Material.POTION, (byte) 0)
                    .data(type.potionData)
                    .name(type.displayName)
                    .lore("&7" + type.description,
                          "",
                          "&7Multiplier: &c" + type.formatMultiplier(),
                          "&7Duration:   &f" + type.durationMinutes + " min",
                          "&7Price:      &c" + type.price + " coins",
                          ownedLine,
                          "",
                          affordLine)
                    .hideFlags()
                    .build());
        }

        // Navigation row
        inv.setItem(actualInvBtnSlot, new ItemBuilder(Material.CHEST)
                .name("&cBooster Inventory")
                .lore("&7View and activate boosters you own.")
                .build());
        FileConfiguration itemsCfg = plugin.getConfigManager().getItemsConfig();
        String backMat = itemsCfg.getString("change-page.back-to-shop.material", "BARRIER:0");
        inv.setItem(actualBackSlot, ItemBuilder.fromString(backMat).name("&cBack").build());

        player.openInventory(inv);
    }

    private void handleBoosterShopClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        Inventory inv = event.getInventory();
        int size = inv.getSize();
        int rows = size / 9;
        int navRowStart = (rows - 1) * 9;
        int invBtnSlot = navRowStart;
        int backBtnSlot = navRowStart + 4;
        String prefix = plugin.getConfigManager().getPrefix();

        if (slot == invBtnSlot) {
            player.closeInventory();
            openBoosterInventory(player);
            return;
        }

        if (slot == backBtnSlot) {
            player.closeInventory();
            openBoosterHub(player);
            return;
        }

        // Content rows: slots 9 up to start of nav row
        if (slot < 9 || slot >= navRowStart) return;

        int typeIndex = slot - 9;
        List<BoosterType> types = plugin.getConfigManager().getBoosterTypes();
        if (typeIndex < 0 || typeIndex >= types.size()) return;

        BoosterType type = types.get(typeIndex);
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        if (data.getCoins() < type.price) {
            player.sendMessage(ColorUtil.translate(prefix
                    + "&cNot enough coins &7(&fneed &c" + type.price + "&7, have &f" + data.getCoins() + "&7)."));
            return;
        }

        data.removeCoins(type.price);
        data.addBooster(type.id, 1);
        plugin.getPlayerManager().savePlayerData(player.getUniqueId());

        player.sendMessage(ColorUtil.translate(prefix
                + "&fPurchased &c" + type.displayName
                + " &7» &fActivate it from your &cBooster Inventory&f."));
        player.closeInventory();
        openBoosterShop(player);
    }

    // ===== Booster Inventory =====

    /**
     * Opens the Booster Inventory — shows all boosters the player owns.
     * The inventory size scales with owned booster count so there are no empty rows.
     * Activating while another booster is running is blocked.
     */
    public void openBoosterInventory(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title   = ColorUtil.translate(guis.getString("booster-inventory.name", BOOSTER_INVENTORY_PREFIX));
        int statSlot   = guis.getInt("booster-inventory.status-slot", 4);

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        long now = System.currentTimeMillis();
        boolean hasActive = data != null && data.getBoosterExpiry() > now;
        double activeMult = hasActive ? data.getBoosterMultiplier() : 1.0;

        // Collect owned boosters to determine inventory size
        List<BoosterType> allTypes = plugin.getConfigManager().getBoosterTypes();
        List<BoosterType> ownedTypes = new ArrayList<>();
        for (BoosterType type : allTypes) {
            if (data != null && data.getBoosterCount(type.id) > 0) ownedTypes.add(type);
        }

        // Size dynamically: header + content rows + footer
        int contentCount = Math.min(ownedTypes.size(), 36);
        int rows = Math.max(3, 2 + (int) Math.ceil(contentCount == 0 ? 0 : contentCount / 9.0));
        int size = rows * 9;
        int navRowStart = (rows - 1) * 9;
        int shopBtnSlot = navRowStart;
        int backBtnSlot = navRowStart + 4;

        Inventory inv = Bukkit.createInventory(null, size, title);
        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = 0; i < size; i++) inv.setItem(i, filler);

        // Status indicator in header row
        if (hasActive) {
            String remaining = plugin.getBoosterManager().formatRemaining(player.getUniqueId());
            inv.setItem(statSlot, new ItemBuilder(Material.POTION, (byte) 0)
                    .data((short) 8201)
                    .name("&c" + formatMult(activeMult) + " Coin Booster &factive")
                    .lore("&7Remaining: &f" + remaining,
                          "",
                          "&cWait for this booster to expire",
                          "&cbefore activating another.")
                    .hideFlags()
                    .build());
        } else {
            inv.setItem(statSlot, new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 10)
                    .name("&fNo active booster")
                    .lore("&7Click a booster below to activate it.")
                    .build());
        }

        // Owned booster items starting at slot 9
        for (int i = 0; i < contentCount; i++) {
            BoosterType type = ownedTypes.get(i);
            int owned = data.getBoosterCount(type.id);

            String actionLine = hasActive
                    ? "&cAlready active — wait for it to expire."
                    : "&fClick to activate";

            inv.setItem(9 + i, new ItemBuilder(Material.POTION, (byte) 0)
                    .data(type.potionData)
                    .name(type.displayName + " &7x" + owned)
                    .lore("&7" + type.description,
                          "",
                          "&7Multiplier: &c" + type.formatMultiplier(),
                          "&7Duration:   &f" + type.durationMinutes + " min",
                          "&7Owned:      &f" + owned,
                          "",
                          actionLine)
                    .hideFlags()
                    .build());
        }

        // Navigation row
        inv.setItem(shopBtnSlot, new ItemBuilder(Material.NETHER_STAR)
                .name("&cBooster Shop")
                .lore("&7Browse and buy more boosters.")
                .build());
        FileConfiguration itemsCfg = plugin.getConfigManager().getItemsConfig();
        String backMat = itemsCfg.getString("change-page.back-to-shop.material", "BARRIER:0");
        inv.setItem(backBtnSlot, ItemBuilder.fromString(backMat).name("&cBack").build());

        player.openInventory(inv);
    }

    private void handleBoosterInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        Inventory inv = event.getInventory();
        int size = inv.getSize();
        int rows = size / 9;
        int navRowStart = (rows - 1) * 9;
        int shopBtnSlot = navRowStart;
        int backBtnSlot = navRowStart + 4;
        String prefix = plugin.getConfigManager().getPrefix();

        if (slot == shopBtnSlot) {
            player.closeInventory();
            openBoosterShop(player);
            return;
        }

        if (slot == backBtnSlot) {
            player.closeInventory();
            openBoosterHub(player);
            return;
        }

        // Content rows: slots 9 up to nav row
        if (slot < 9 || slot >= navRowStart) return;

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        // Rebuild owned list in same order as render
        List<BoosterType> allTypes = plugin.getConfigManager().getBoosterTypes();
        List<BoosterType> ownedTypes = new ArrayList<>();
        for (BoosterType type : allTypes) {
            if (data.getBoosterCount(type.id) > 0) ownedTypes.add(type);
        }

        int index = slot - 9;
        if (index < 0 || index >= ownedTypes.size()) return;

        BoosterType type = ownedTypes.get(index);

        if (plugin.getBoosterManager().hasActiveTemporaryBooster(player.getUniqueId())) {
            String remaining = plugin.getBoosterManager().formatRemaining(player.getUniqueId());
            player.sendMessage(ColorUtil.translate(prefix
                    + "&cYou already have an active booster! &7Wait &c" + remaining + " &7for it to expire."));
            return;
        }

        boolean activated = plugin.getBoosterManager().activateBooster(player, type.id);
        if (!activated) {
            player.sendMessage(ColorUtil.translate(prefix + "&cFailed to activate booster."));
            return;
        }

        player.sendMessage(ColorUtil.translate(prefix
                + "&c" + type.formatMultiplier() + " Coin Booster &factivated &7» &f"
                + type.durationMinutes + " minutes"));
        player.closeInventory();
        openBoosterInventory(player);
    }

    private static String formatMult(double mult) {
        if (mult == Math.floor(mult)) return (int) mult + "xx";
        return String.format("%.1fxx", mult);
    }

    /** Total number of booster items the player owns across all types. */
    private static int countOwnedBoosters(PlayerData data) {
        int total = 0;
        for (int qty : data.getBoosterInventory().values()) total += qty;
        return total;
    }

    private static double toDouble(Object val, double def) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return def;
    }

    private static int toInt(Object val, int def) {
        if (val instanceof Number) return ((Number) val).intValue();
        return def;
    }

    // ===== Shop =====

    public void openShop(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(guis.getString("shop.name", "Shop"));
        int rawSize = guis.getInt("shop.max-slots", 45);
        // Snap to valid inventory size (9, 18, 27, 36, 45, 54)
        int size = Math.max(9, Math.min(54, ((rawSize + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(null, size, title);

        if (plugin.getConfigManager().isShopCategoryVisible(player, "blocks")) {
            inv.setItem(guis.getInt("shop.blocks-slot", 9),
                    buildShopCategoryItem(guis, "shop.items.blocks", "SANDSTONE:0", "&eBlocks",
                            new String[]{"&7Click to browse building blocks", "", "&aClick to browse"}));
        }
        if (plugin.getConfigManager().isShopCategoryVisible(player, "boosters")) {
            PlayerData bData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            boolean hasBooster = bData != null && bData.getBoosterExpiry() > System.currentTimeMillis();
            boolean hasOwned   = bData != null && bData.hasAnyBoosters();
            String boosterName = hasBooster ? "&6Boosters &a(Active)" : "&6Boosters";
            String[] boosterLore = hasBooster
                    ? new String[]{"&7Multiplies all coin rewards",
                                   "&7Active: &a" + plugin.getBoosterManager().formatRemaining(player.getUniqueId()),
                                   hasOwned ? "&7Owned: &f" + countOwnedBoosters(bData) + " booster(s)" : "",
                                   "", "&aClick to open Booster Shop"}
                    : new String[]{"&7Multiplies all coin rewards",
                                   "&7No active booster",
                                   hasOwned ? "&7Owned: &f" + countOwnedBoosters(bData) + " booster(s)" : "",
                                   "", "&aClick to open Booster Shop"};
            inv.setItem(guis.getInt("shop.boosters-slot", 10),
                    buildShopCategoryItem(guis, "shop.items.boosters", "POTION:0",
                            boosterName, boosterLore));
        }
        if (plugin.getConfigManager().isShopCategoryVisible(player, "pickaxes")) {
            inv.setItem(guis.getInt("shop.pickaxes-slot", 11),
                    buildShopCategoryItem(guis, "shop.items.pickaxes", "DIAMOND_PICKAXE:0", "&bPickaxe Shop",
                            new String[]{"&7Click to browse pickaxes and tools", "", "&aClick to browse"}));
        }
        if (plugin.getConfigManager().isShopCategoryVisible(player, "animations")) {
            inv.setItem(guis.getInt("shop.animations-slot", 15),
                    buildShopCategoryItem(guis, "shop.items.animations", "FIREWORK:0", "&dReset Animations",
                            new String[]{"&7Click to browse reset animations", "", "&aClick to browse"}));
        }
        if (plugin.getConfigManager().isShopCategoryVisible(player, "sounds")) {
            inv.setItem(guis.getInt("shop.death-sounds-slot", 17),
                    buildShopCategoryItem(guis, "shop.items.sounds", "NOTE_BLOCK:0", "&6Death Sounds",
                            new String[]{"&7Click to browse death sounds", "", "&aClick to browse"}));
        }

        // Island Designs — always shown (EMERALD_BLOCK in both states, configurable).
        if (plugin.getConfigManager().isShopCategoryVisible(player, "designs")) {
            PlayerData shopData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            boolean hasDesigns = false;
            String currentMap = shopData != null ? shopData.getLastMap() : null;
            if (currentMap != null) {
                MapData designMap = plugin.getMapManager().getMap(currentMap);
                hasDesigns = designMap != null && !designMap.getAlternativeTemplates().isEmpty();
            }

            String configPath = hasDesigns ? "shop.items.designs-active" : "shop.items.designs-inactive";
            String defaultName = hasDesigns ? "&aIsland Designs" : "&7Island Designs";
            String[] defaultLore = hasDesigns
                    ? new String[]{"&7Choose a design for your island", "", "&aClick to browse"}
                    : new String[]{"&7No designs available for this map"};

            inv.setItem(guis.getInt("shop.designs-slot", 13),
                    buildShopCategoryItem(guis, configPath, "EMERALD_BLOCK:0", defaultName, defaultLore));
        }

        player.openInventory(inv);
    }

    /**
     * Builds a shop category {@link ItemStack} from config, falling back to hardcoded defaults
     * when the config path is absent.
     */
    private ItemStack buildShopCategoryItem(FileConfiguration guis, String path,
                                             String defaultMat, String defaultName,
                                             String[] defaultLore) {
        String mat  = guis.getString(path + ".material", defaultMat);
        String name = guis.getString(path + ".name",     defaultName);
        List<String> loreList = guis.getStringList(path + ".lore");
        String[] lore = loreList.isEmpty() ? defaultLore : loreList.toArray(new String[0]);
        return ItemBuilder.fromString(mat).name(name).lore(lore).build();
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

        List<String> templates = map.getTemplatesForMode();
        // Spec: Island Selector must be EXACTLY 3×9 (27 slots)
        String title = ColorUtil.translate("&aIsland Designs &7- &f" + map.getName());
        Inventory inv = Bukkit.createInventory(null, 27, title);

        String selectedDesign = pData.getSelectedDesign(mapName);
        String defaultKey = map.getTemplateFile();
        if (selectedDesign == null) selectedDesign = defaultKey;

        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int designPrice = guis.getInt("island-designs.default-price", 0);
        String loreSelected = guis.getString("island-designs.lore-selected", "&a&l» Currently selected");
        String loreUnlocked = guis.getString("island-designs.lore-unlocked", "&eClick to select this design");
        String loreLocked   = guis.getString("island-designs.lore-locked",   "&cLocked &8- &e%price% coins to unlock");
        String loreNoCoins  = guis.getString("island-designs.lore-cannot-afford", "&cNot enough coins &7(&f%coins% / %price%&7)");

        // Content slots: 0-17 (first two rows), leaving row 3 for navigation
        int maxContentSlots = 18;
        for (int i = 0; i < templates.size() && i < maxContentSlots; i++) {
            String key = templates.get(i);
            boolean isDefault = key.equals(defaultKey);
            boolean selected = key.equalsIgnoreCase(selectedDesign);
            boolean unlocked = isDefault
                    || designPrice == 0
                    || (pData.hasPurchasedDesign(key))
                    || player.hasPermission("fastbuilder.design.*")
                    || player.hasPermission("fastbuilder.design." + key.toLowerCase());
            // Name = &a + template key (or "Default Design" for the default template)
            String displayName = "&a" + (isDefault ? "Default Design" : key);

            List<String> lore = new ArrayList<>();
            if (selected) {
                lore.add(ColorUtil.translate(loreSelected));
            } else if (unlocked) {
                lore.add(ColorUtil.translate(loreUnlocked));
            } else {
                String priceLine = loreLocked.replace("%price%", String.valueOf(designPrice));
                lore.add(ColorUtil.translate(priceLine));
                String coinLine = loreNoCoins
                        .replace("%coins%", String.valueOf(pData.getCoins()))
                        .replace("%price%", String.valueOf(designPrice));
                lore.add(ColorUtil.translate(coinLine));
            }

            ItemStack item = new ItemBuilder(Material.PAPER).name(displayName).lore(lore.toArray(new String[0])).build();
            inv.setItem(i, item);
        }

        // "Back to Shop" button in the bottom center slot (slot 22 = center of row 3 in 27-slot)
        FileConfiguration designItemsConfig = plugin.getConfigManager().getItemsConfig();
        String designBackMat  = designItemsConfig.getString("change-page.back-to-shop.material", "BARRIER:0");
        String designBackName = designItemsConfig.getString("change-page.back-to-shop.name", "&cBack to Shop");
        inv.setItem(22, ItemBuilder.fromString(designBackMat).name(designBackName).build());
        player.openInventory(inv);
    }

    public void openPickaxeSelector(Player player, int page) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int maxSlots = guis.getInt("pickaxe-selector.max-slots", 45);
        String titleTemplate = guis.getString("pickaxe-selector.name", "Pickaxe Shop - %page%");

        ConfigurationSection pageSection = guis.getConfigurationSection("pickaxe-selector-slots." + page);
        if (pageSection == null) {
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&cNo tools on this page."));
            return;
        }

        // Count total pages
        int maxPage = 1;
        ConfigurationSection allPagesSection = guis.getConfigurationSection("pickaxe-selector-slots");
        if (allPagesSection != null) {
            for (String key : allPagesSection.getKeys(false)) {
                try {
                    int p = Integer.parseInt(key);
                    if (p > maxPage) maxPage = p;
                } catch (NumberFormatException ignored) {}
            }
        }

        String title = ColorUtil.translate(titleTemplate
                .replace("%page%", String.valueOf(page))
                .replace("%max_page%", String.valueOf(maxPage)));
        Inventory inv = Bukkit.createInventory(null, maxSlots, title);

        // Fill ALL slots with gray filler first (mirrors block selector style)
        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = 0; i < maxSlots; i++) inv.setItem(i, filler);

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        String purchasedStatus   = guis.getString("block-status.purchased",     "&aYou already own this!");
        String notPurchasedStatus = guis.getString("block-status.not-purchased", "&cYou don't own this yet!");

        // Render tool items from the current page's section
        for (String slotKey : pageSection.getKeys(false)) {
            try {
                int slotIndex = Integer.parseInt(slotKey) - 1; // 1-based → 0-based
                if (slotIndex < 0 || slotIndex >= maxSlots - 9) continue; // leave bottom nav row

                String name  = pageSection.getString(slotKey + ".name",     "Tool");
                String mat   = pageSection.getString(slotKey + ".material", "STONE_PICKAXE:0");
                int    price = pageSection.getInt(slotKey    + ".price",    0);

                // NONE / reset option must always use BEDROCK (not BARRIER)
                boolean isNonePickaxe = name.equalsIgnoreCase("None") || mat.toUpperCase().startsWith("BARRIER");
                if (isNonePickaxe) mat = "BEDROCK:0";

                String pickPerm = "fastbuilder.pickaxe." + mat.toLowerCase().replace(":", ".");
                boolean owned = price == 0
                        || (data != null && data.hasPurchasedBlock("pickaxe:" + mat))
                        || player.hasPermission("fastbuilder.blocks.*")
                        || player.hasPermission(pickPerm);

                List<String> loreTemplate = pageSection.getStringList(slotKey + ".lore");
                String[] lore = new String[loreTemplate.size()];
                for (int i = 0; i < loreTemplate.size(); i++) {
                    lore[i] = loreTemplate.get(i)
                            .replace("%price%", price == 0 ? "Free" : String.valueOf(price))
                            .replace("%block_status%", owned ? purchasedStatus : notPurchasedStatus);
                }
                inv.setItem(slotIndex, ItemBuilder.fromString(mat).name("&r" + name).lore(lore).build());
            } catch (NumberFormatException ignored) {}
        }

        // One-Click Pick — rendered on its configured page at its configured slot
        int ocpPage = guis.getInt("one-click-pick.page", 1);
        if (page == ocpPage) {
            boolean hasOcp       = data != null && data.hasOneClickPick();
            boolean ocpPurchased = (data != null && data.hasPurchasedBlock("cosmetic:one_click_pick"))
                    || player.hasPermission("fastbuilder.cosmetic.oneclickpick")
                    || player.hasPermission("fastbuilder.blocks.*");

            int    ocpPrice = guis.getInt("one-click-pick.price",  5000);
            int    ocpSlot  = guis.getInt("one-click-pick.slot",   23) - 1; // 1-based → 0-based
            String ocpName  = guis.getString("one-click-pick.name", "&bOne-Click Pick");
            String ocpStatus = ocpPurchased
                    ? (hasOcp ? "&a&lACTIVE" : "&7Owned &8- &eClick to enable")
                    : "&cNot owned &8- &eClick to buy";

            List<String> ocpLoreTemplate = guis.getStringList("one-click-pick.lore");
            List<String> ocpLore = new ArrayList<>();
            for (String line : ocpLoreTemplate) {
                ocpLore.add(line.replace("%price%", ocpPurchased ? "Owned" : String.valueOf(ocpPrice)));
            }
            ocpLore.add(ColorUtil.translate(ocpStatus));

            inv.setItem(ocpSlot, new ItemBuilder(Material.DIAMOND_AXE)
                    .name(ocpName)
                    .lore(ocpLore.toArray(new String[0]))
                    .build());
        }

        // Navigation — bottom row (same structure as block selector)
        FileConfiguration itemsConfig = plugin.getConfigManager().getItemsConfig();

        String backMat  = itemsConfig.getString("change-page.back-to-shop.material", "BARRIER:0");
        String backName = itemsConfig.getString("change-page.back-to-shop.name",     "&cBack to Shop");
        inv.setItem(maxSlots - 5, ItemBuilder.fromString(backMat).name(backName).build());

        // Prev / Next arrows — only shown when there are multiple pages
        if (maxPage > 1) {
            if (page > 1) {
                String prevMat  = itemsConfig.getString("change-page.previous-page.material", "ARROW:0");
                String prevName = itemsConfig.getString("change-page.previous-page.name",     "&c« Previous Page");
                inv.setItem(maxSlots - 9, ItemBuilder.fromString(prevMat).name(prevName).build());
            } else {
                String disMat  = itemsConfig.getString("change-page.no-previous-page.material", "ARROW:0");
                String disName = itemsConfig.getString("change-page.no-previous-page.name",     "&8« No Previous Page");
                inv.setItem(maxSlots - 9, ItemBuilder.fromString(disMat).name(disName).build());
            }
            if (page < maxPage) {
                String nextMat  = itemsConfig.getString("change-page.next-page.material", "ARROW:0");
                String nextName = itemsConfig.getString("change-page.next-page.name",     "&aNext Page »");
                inv.setItem(maxSlots - 1, ItemBuilder.fromString(nextMat).name(nextName).build());
            } else {
                String disMat  = itemsConfig.getString("change-page.no-next-page.material", "ARROW:0");
                String disName = itemsConfig.getString("change-page.no-next-page.name",     "&8No Next Page »");
                inv.setItem(maxSlots - 1, ItemBuilder.fromString(disMat).name(disName).build());
            }
        }

        pickaxePages.put(player.getUniqueId(), page);
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
                boolean isNoneOption = animId.equalsIgnoreCase("NONE");
                // NONE option always uses BEDROCK (not BARRIER or any other material)
                if (isNoneOption) mat = "BEDROCK:0";
                java.util.List<String> loreTemplate = slotsSection.getStringList(slotKey + ".lore");
                java.util.List<String> lore = new java.util.ArrayList<>();
                for (String line : loreTemplate) {
                    lore.add(line.replace("%price%", price == 0 ? "Free" : String.valueOf(price)));
                }
                if (!owned) lore.add(ColorUtil.translate("&cNot purchased"));
                if (selected) lore.add(ColorUtil.translate("&a&lCurrently selected"));
                ItemStack item = ItemBuilder.fromString(mat).name("&r" + name).lore(lore.toArray(new String[0])).build();
                // Do not add enchant glow to CHEST (Item Drop) — it renders as a broken texture in 1.8.8
                // Do not add enchant glow to NONE option — spec requires no Unbreaking I enchantment
                boolean canGlow = !mat.toUpperCase().startsWith("CHEST") && !isNoneOption;
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

        // Navigation — bottom row: Back to Shop only (single-page, no pagination arrows)
        FileConfiguration animItemsConfig = plugin.getConfigManager().getItemsConfig();
        String animBackMat  = animItemsConfig.getString("change-page.back-to-shop.material", "BARRIER:0");
        String animBackName = animItemsConfig.getString("change-page.back-to-shop.name", "&cBack to Shop");
        inv.setItem(maxSlots - 5, ItemBuilder.fromString(animBackMat).name(animBackName).build());

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
                boolean isNoneOption = soundId.equalsIgnoreCase("NONE");
                // NONE option always uses BEDROCK (not BARRIER or any other material)
                if (isNoneOption) mat = "BEDROCK:0";
                java.util.List<String> loreTemplate = soundsSection.getStringList(slotKey + ".lore");
                java.util.List<String> lore = new java.util.ArrayList<>();
                for (String line : loreTemplate) {
                    lore.add(line.replace("%price%", price == 0 ? "Free" : String.valueOf(price)));
                }
                if (!owned) lore.add(ColorUtil.translate("&cNot purchased"));
                if (selected) lore.add(ColorUtil.translate("&a&lCurrently selected"));
                ItemStack it = ItemBuilder.fromString(mat).name("&r" + name).lore(lore.toArray(new String[0])).build();
                // Do not add enchant glow to FIREWORK (Firework Rocket) — unsupported in 1.8.8
                // Do not add enchant glow to NONE option — spec requires no Unbreaking I enchantment
                boolean canGlow = !mat.toUpperCase().startsWith("FIREWORK") && !isNoneOption;
                if (selected && canGlow) {
                    org.bukkit.inventory.meta.ItemMeta im = it.getItemMeta();
                    if (im != null) { im.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true); it.setItemMeta(im); }
                }
                inv.setItem(slotIndex, it);
            } catch (NumberFormatException ignored) {}
        }

        // Navigation — bottom row: Back to Shop only (single-page, no pagination arrows)
        FileConfiguration soundItemsConfig = plugin.getConfigManager().getItemsConfig();
        String soundBackMat  = soundItemsConfig.getString("change-page.back-to-shop.material", "BARRIER:0");
        String soundBackName = soundItemsConfig.getString("change-page.back-to-shop.name", "&cBack to Shop");
        inv.setItem(maxSlots - 5, ItemBuilder.fromString(soundBackMat).name(soundBackName).build());

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

    // ===== Leaderboard GUI =====

    /** Opens a map-picker GUI so the player can choose which map's leaderboard to view. */
    public void openLeaderboardMapPicker(Player player) {
        List<MapData> maps = new ArrayList<>();
        for (MapData m : plugin.getMapManager().getAllMaps()) {
            if (m.isEnabled()) maps.add(m);
        }

        int itemCount = maps.size();
        int rawSize = itemCount + 9; // items + bottom row
        int size = Math.max(9, Math.min(54, ((rawSize + 8) / 9) * 9));

        String title = ColorUtil.translate("&c&lLeaderboard");
        Inventory inv = Bukkit.createInventory(null, size, title);

        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = 0; i < size; i++) inv.setItem(i, filler);

        int slot = 0;
        for (MapData map : maps) {
            if (slot >= size - 9) break;
            String iconStr = map.getIcon();
            ItemBuilder builder = (iconStr != null && !iconStr.isEmpty())
                    ? ItemBuilder.fromString(iconStr)
                    : new ItemBuilder(Material.GRASS);
            ItemStack item = builder
                    .name("&c" + map.getName())
                    .lore("&7Click to view leaderboard")
                    .build();
            inv.setItem(slot, item);
            slot++;
        }

        // Close button — center of bottom row
        FileConfiguration itemsCfg = plugin.getConfigManager().getItemsConfig();
        String closeMat = itemsCfg.getString("island-selector.back-button.material", "BARRIER:0");
        String closeName = itemsCfg.getString("island-selector.back-button.name", "&cClose");
        ItemStack closeBtn = ItemBuilder.fromString(closeMat).name(closeName).build();
        inv.setItem(size - 5, closeBtn);

        player.openInventory(inv);
    }

    /** Fetches the top 10 for {@code mapName} asynchronously, then opens the leaderboard GUI. */
    public void openLeaderboardGui(Player player, String mapName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            net.gravijet.fastbuilder.map.MapData lbMap = plugin.getMapManager().getMap(mapName);
            boolean infinite = lbMap != null && lbMap.isInfinite();

            if (infinite) {
                List<Map.Entry<String, Integer>> topInf =
                        plugin.getPlayerManager().getTopInfiniteDistancesForMap(mapName, 10);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    String title = ColorUtil.translate("&c&lLeaderboard &7- &f" + mapName);
                    Inventory inv = Bukkit.createInventory(null, 54, title);
                    ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
                    for (int i = 0; i < 54; i++) inv.setItem(i, filler);
                    int[] entrySlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
                    for (int i = 0; i < Math.min(topInf.size(), entrySlots.length); i++) {
                        Map.Entry<String, Integer> entry = topInf.get(i);
                        int pos = i + 1;
                        String rankColor = pos == 1 ? "&6" : pos == 2 ? "&7" : pos == 3 ? "&c" : "&8";
                        ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
                        SkullMeta meta = (SkullMeta) skull.getItemMeta();
                        meta.setOwner(entry.getKey());
                        meta.setDisplayName(ColorUtil.translate(rankColor + "&l#" + pos + " &f" + entry.getKey()));
                        List<String> lore = new ArrayList<>();
                        lore.add(ColorUtil.translate("&7Distance: &f" + entry.getValue() + " blocks"));
                        meta.setLore(lore);
                        skull.setItemMeta(meta);
                        inv.setItem(entrySlots[i], skull);
                    }
                    if (topInf.isEmpty()) {
                        inv.setItem(22, new ItemBuilder(Material.BARRIER).name("&7No distances recorded yet.").build());
                    }
                    FileConfiguration itemsCfg = plugin.getConfigManager().getItemsConfig();
                    String closeMat = itemsCfg.getString("island-selector.back-button.material", "BARRIER:0");
                    inv.setItem(49, ItemBuilder.fromString(closeMat).name("&cBack").build());
                    player.openInventory(inv);
                });
            } else {
                List<Map.Entry<String, Long>> top =
                        plugin.getPlayerManager().getTopPlayerTimesForMap(mapName, 10);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    String title = ColorUtil.translate("&c&lLeaderboard &7- &f" + mapName);
                    Inventory inv = Bukkit.createInventory(null, 54, title);

                    ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
                    for (int i = 0; i < 54; i++) inv.setItem(i, filler);

                    // Slots: row 2 (10-16 = 7), row 3 (19-21 = 3) → 10 total
                    int[] entrySlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};

                    for (int i = 0; i < Math.min(top.size(), entrySlots.length); i++) {
                        Map.Entry<String, Long> entry = top.get(i);
                        int pos = i + 1;
                        String rankColor = pos == 1 ? "&6" : pos == 2 ? "&7" : pos == 3 ? "&c" : "&8";

                        ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
                        SkullMeta meta = (SkullMeta) skull.getItemMeta();
                        meta.setOwner(entry.getKey());
                        meta.setDisplayName(ColorUtil.translate(rankColor + "&l#" + pos + " &f" + entry.getKey()));
                        List<String> lore = new ArrayList<>();
                        lore.add(ColorUtil.translate("&7Time: &f" + TimeUtil.formatTime(entry.getValue())));
                        lore.add(ColorUtil.translate("&eClick to watch replay"));
                        meta.setLore(lore);
                        skull.setItemMeta(meta);
                        inv.setItem(entrySlots[i], skull);
                    }

                    if (top.isEmpty()) {
                        ItemStack none = new ItemBuilder(Material.BARRIER).name("&7No times recorded yet.").build();
                        inv.setItem(22, none);
                    }

                    // Back button — center of bottom row (slot 49) — returns to map picker
                    FileConfiguration itemsCfg = plugin.getConfigManager().getItemsConfig();
                    String closeMat = itemsCfg.getString("island-selector.back-button.material", "BARRIER:0");
                    inv.setItem(49, ItemBuilder.fromString(closeMat).name("&cBack").build());

                    player.openInventory(inv);
                });
            }
        });
    }

    private void handleLeaderboardClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        int invSize = event.getInventory().getSize();
        String stripped = ColorUtil.strip(event.getView().getTitle());

        // Back / close button — center of bottom row
        if (slot == invSize - 5) {
            // In a per-map leaderboard the title contains " - <mapName>"; go back to picker.
            // In the map picker itself, just close.
            if (stripped.contains(" - ")) {
                openLeaderboardMapPicker(player);
            } else {
                player.closeInventory();
            }
            return;
        }

        // Map picker: clicking a map icon opens that map's leaderboard
        if (stripped.equals("Leaderboard")) {
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.STAINED_GLASS_PANE
                    || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;
            String mapName = ColorUtil.strip(item.getItemMeta().getDisplayName());
            if (plugin.getMapManager().getMap(mapName) == null) return;
            openLeaderboardGui(player, mapName);
            return;
        }

        // Per-map leaderboard: skull click → watch that player's PB replay
        if (stripped.contains(" - ") && slot != invSize - 5) {
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() != Material.SKULL_ITEM) return;
            if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;
            // Extract map name from title (format: "§8Leaderboard - <mapName>")
            String[] parts = stripped.split(" - ", 2);
            if (parts.length < 2) return;
            String mapName = parts[1].trim();

            // Strip rank prefix from display name to get player name
            String displayName = ColorUtil.strip(item.getItemMeta().getDisplayName());
            // Display name format: "#1 PlayerName" — extract name after "# N "
            String targetName = displayName.replaceFirst("^#\\d+\\s+", "").trim();
            if (targetName.isEmpty()) return;

            player.closeInventory();

            if (plugin.getReplayManager() == null) {
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                        + "&cReplay system is not available."));
                return;
            }

            final String finalMapName = mapName;
            final String finalName = targetName;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                UUID targetUuid = plugin.getPlayerManager().getUuidForPlayerName(finalName);
                if (targetUuid == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                                + "&cCould not find player data for &f" + finalName + "&c.")));
                    return;
                }
                net.gravijet.fastbuilder.replay.ReplayData pbReplay =
                        plugin.getReplayManager().getPbReplay(targetUuid, finalMapName);
                if (pbReplay == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                                + "&cNo replay found for &f" + finalName + " &con map &f" + finalMapName + "&c.")));
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getReplayManager().startPlayback(player, pbReplay));
            });
        }
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
                || stripped.startsWith(BOOSTER_HUB_PREFIX)
                || stripped.startsWith(BOOSTER_SHOP_PREFIX)
                || stripped.startsWith(BOOSTER_INVENTORY_PREFIX)
                || stripped.startsWith(PICKAXE_SELECTOR_PREFIX)
                || stripped.startsWith(ANIMATION_SELECTOR_PREFIX)
                || stripped.startsWith(DEATH_SOUND_SELECTOR_PREFIX)
                || stripped.startsWith(STATS_PREFIX)
                || stripped.startsWith(DESIGN_SELECTOR_PREFIX)
                || stripped.startsWith(LEADERBOARD_PREFIX)
                || stripped.startsWith(CUSTOM_LENGTH_MENU_PREFIX);
    }

    /**
     * HIGH-priority drag-cancel: any drag (split-stack, paint) across a plugin GUI is
     * unconditionally blocked to prevent item duplication exploits.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top != null && top.getType() == InventoryType.CRAFTING) {
            // Player's own inventory (E key) — lock item movement when a session is active
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
        } else if (stripped.startsWith(BOOSTER_HUB_PREFIX)) {
            handleBoosterHubClick(event);
        } else if (stripped.startsWith(BOOSTER_INVENTORY_PREFIX)) {
            handleBoosterInventoryClick(event);
        } else if (stripped.startsWith(BOOSTER_SHOP_PREFIX)) {
            handleBoosterShopClick(event);
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
        } else if (stripped.startsWith(LEADERBOARD_PREFIX)) {
            handleLeaderboardClick(event);
        } else if (stripped.startsWith(CUSTOM_LENGTH_MENU_PREFIX)) {
            handleCustomLengthMenuClick(event);
        }
        // STATS_PREFIX: read-only — no handler needed
    }

    private void handleIslandSelectorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        int invSize = event.getInventory().getSize();

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null || data.getLastMap() == null) return;

        MapData map = plugin.getMapManager().getMap(data.getLastMap());
        if (map == null) return;

        // Back button — close the menu
        if (slot == invSize - 5) {
            player.closeInventory();
            return;
        }

        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        List<IslandInstance> islands = plugin.getMapManager().getIslands(map.getName());
        int pageSize = guis.getInt("island-selector-gui.page-size", 45);
        pageSize = Math.max(9, Math.min(45, (pageSize / 9) * 9));
        int totalPages = islands.isEmpty() ? 1 : (int) Math.ceil(islands.size() / (double) pageSize);
        int currentPage = islandPages.getOrDefault(player.getUniqueId(), 1);

        // Pagination arrows (only in 54-slot multi-page GUI)
        if (invSize == 54 && totalPages > 1) {
            if (slot == invSize - 9 && currentPage > 1) {
                openIslandSelectorPage(player, map, currentPage - 1);
                return;
            }
            if (slot == invSize - 1 && currentPage < totalPages) {
                openIslandSelectorPage(player, map, currentPage + 1);
                return;
            }
        }

        // Determine island index from slot + page
        int islandIndex = (currentPage - 1) * pageSize + slot;
        if (slot < 0 || slot >= invSize - 9 || islandIndex < 0 || islandIndex >= islands.size()) return;

        IslandInstance island = islands.get(islandIndex);
        if (island.isOccupied()) {
            String raw = plugin.getConfigManager().getMessage("island-already-occupied");
            raw = raw.replace("%island_player%", island.getOccupantName());
            raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
            player.sendMessage(ColorUtil.translate(raw));
            player.closeInventory();
            return;
        }

        // Clear placed blocks, end platform, revert design, reset custom length
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());
            plugin.getGameplayManager().clearEndPlatform(player.getUniqueId());
            plugin.getGameplayManager().revertIslandDesign(map, data.getLastIsland());
            plugin.getGameplayManager().removeGlobalSessionBest(player.getName());
        }
        data.clearCustomLengths();

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
        if (plugin.getGameplayManager() != null) plugin.getGameplayManager().removeSession(player.getUniqueId());

        plugin.getMapManager().assignIsland(map.getName(), islandIndex, player.getUniqueId(), player.getName());
        data.setLastIsland(islandIndex);
        player.teleport(map.getIslandSpawn(islandIndex));
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.closeInventory();

        // Create new gameplay session and set up end island / design
        if (plugin.getGameplayManager() != null) {
            net.gravijet.fastbuilder.gameplay.RunSession newSess =
                    plugin.getGameplayManager().createSession(player.getUniqueId(), map.getName(), islandIndex);
            if (map.hasEndIsland()) {
                plugin.getGameplayManager().placeEndPlatform(player, map, newSess, map.getBaseCustomLength());
            }
            plugin.getGameplayManager().applyPlayerDesign(player, map, islandIndex);
        }

        // Give hotbar items
        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().giveItems(player);
        }

        // Spawn new NPC and hologram at the new island
        if (plugin.getNpcManager() != null) {
            plugin.getNpcManager().spawnNpc(player, map.getIslandNpcLocation(islandIndex));
        }
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().updateHologram(map.getName(), islandIndex, player);
        }

        String raw = plugin.getConfigManager().getMessage("island-joined");
        raw = raw.replace("%island_number%", String.valueOf(islandIndex + 1));
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
                openCustomLengthMenu(player);
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

    private void handleCustomLengthMenuClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        String prefix = plugin.getConfigManager().getPrefix();

        if (!player.hasPermission("fastbuilder.feature.custom_length")) {
            player.sendMessage(ColorUtil.translate(prefix + "&cYou don't have permission to use custom length."));
            player.closeInventory();
            return;
        }

        net.gravijet.fastbuilder.gameplay.RunSession run = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
        if (run == null) { player.closeInventory(); return; }

        net.gravijet.fastbuilder.map.MapData map = plugin.getMapManager().getMap(run.getMapName());
        if (map == null || !map.hasCustomLength()) { player.closeInventory(); return; }

        PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (pData == null) { player.closeInventory(); return; }

        org.bukkit.event.inventory.ClickType click = event.getClick();
        int invSize = event.getInventory().getSize();

        // Back button — dynamic position based on menu size
        if (slot == invSize - 5) {
            openSettings(player);
            return;
        }

        if (slot == 15) {
            // Reset both axes to defaults
            int defaultX = map.getBaseCustomLength() > 0 ? map.getBaseCustomLength() : map.getEffectiveMinCustomLength();
            pData.setCustomLength(run.getMapName(), defaultX);
            pData.setCustomLengthY(run.getMapName(), 0);
            if (plugin.getGameplayManager() != null) {
                plugin.getGameplayManager().placeEndPlatform(player, map, run, defaultX);
            }
            player.sendMessage(ColorUtil.translate(prefix + "&fDistance and height reset to map defaults."));
            openCustomLengthMenu(player);
            return;
        }

        // Left = large increase, Shift+Left = small increase; Right = large decrease, Shift+Right = small decrease
        int delta;
        if (click == org.bukkit.event.inventory.ClickType.LEFT) delta = 10;
        else if (click == org.bukkit.event.inventory.ClickType.SHIFT_LEFT) delta = 1;
        else if (click == org.bukkit.event.inventory.ClickType.RIGHT) delta = -10;
        else if (click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) delta = -1;
        else return;

        if (slot == 11) {
            // X-axis (distance) control
            int current = pData.getCustomLength(run.getMapName());
            if (current <= 0) current = map.getBaseCustomLength() > 0
                    ? map.getBaseCustomLength() : map.getEffectiveMinCustomLength();

            int newVal = Math.max(map.getEffectiveMinCustomLength(),
                    Math.min(map.getEffectiveMaxCustomLength(), current + delta));

            if (newVal == current) {
                String limitMsg = delta > 0
                        ? "&cCan't go further &7(max: &f" + map.getEffectiveMaxCustomLength() + " blocks&7)."
                        : "&cCan't go shorter &7(min: &f" + map.getEffectiveMinCustomLength() + " blocks&7).";
                player.sendMessage(ColorUtil.translate(prefix + limitMsg));
            } else {
                pData.setCustomLength(run.getMapName(), newVal);
                if (plugin.getGameplayManager() != null) {
                    plugin.getGameplayManager().placeEndPlatform(player, map, run, newVal);
                }
            }
            openCustomLengthMenu(player);

        } else if (slot == 13) {
            // Y-axis (height) control
            int minY = plugin.getConfigManager().getCustomLengthMinY();
            int maxY = plugin.getConfigManager().getCustomLengthMaxY();
            int current = pData.getCustomLengthY(run.getMapName());

            int newVal = Math.max(minY, Math.min(maxY, current + delta));

            if (newVal == current) {
                String limitMsg = delta > 0
                        ? "&cAlready at the highest offset &7(+" + maxY + ")."
                        : "&cAlready at the lowest offset &7(" + minY + ").";
                player.sendMessage(ColorUtil.translate(prefix + limitMsg));
            } else {
                pData.setCustomLengthY(run.getMapName(), newVal);
                int currentX = pData.getCustomLength(run.getMapName());
                if (currentX <= 0) currentX = map.getBaseCustomLength() > 0
                        ? map.getBaseCustomLength() : map.getEffectiveMinCustomLength();
                if (plugin.getGameplayManager() != null) {
                    plugin.getGameplayManager().placeEndPlatform(player, map, run, currentX);
                }
            }
            openCustomLengthMenu(player);
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

        // Revert old island design, clear end platform, reset custom lengths
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());
            plugin.getGameplayManager().clearEndPlatform(player.getUniqueId());
            if (existingData != null && existingData.getLastMap() != null) {
                MapData oldMap = plugin.getMapManager().getMap(existingData.getLastMap());
                if (oldMap != null) {
                    plugin.getGameplayManager().revertIslandDesign(oldMap, existingData.getLastIsland());
                }
            }
            plugin.getGameplayManager().removeGlobalSessionBest(player.getName());
        }
        if (existingData != null) existingData.clearCustomLengths();

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

        // Assign a free island on the new map
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

        // Create gameplay session and set up end island / design on the new map
        if (plugin.getGameplayManager() != null) {
            net.gravijet.fastbuilder.gameplay.RunSession newSess =
                    plugin.getGameplayManager().createSession(player.getUniqueId(), map.getName(), island);
            if (map.hasEndIsland()) {
                plugin.getGameplayManager().placeEndPlatform(player, map, newSess, map.getBaseCustomLength());
            }
            plugin.getGameplayManager().applyPlayerDesign(player, map, island);
        }

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
        int slot = event.getSlot();
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();

        player.closeInventory();

        if (slot == guis.getInt("shop.blocks-slot", 10)) {
            openBlockSelector(player, 1);
        } else if (slot == guis.getInt("shop.boosters-slot", 28)) {
            openBoosterHub(player);
        } else if (slot == guis.getInt("shop.pickaxes-slot", 13)) {
            openPickaxeSelector(player, 1);
        } else if (slot == guis.getInt("shop.designs-slot", 16)) {
            openDesignSelector(player);
        } else if (slot == guis.getInt("shop.animations-slot", 31)) {
            openAnimationSelector(player);
        } else if (slot == guis.getInt("shop.death-sounds-slot", 34)) {
            openDeathSoundSelector(player);
        }
    }

    /** Handles One-Click Pick purchase/toggle from within the Pickaxe Shop. */
    private void handleOneClickPickShopClick(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int price = guis.getInt("one-click-pick.price", 5000);
        String prefix = plugin.getConfigManager().getPrefix();
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        boolean purchased = data.hasPurchasedBlock("cosmetic:one_click_pick")
                || player.hasPermission("fastbuilder.cosmetic.oneclickpick");
        if (!purchased) {
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
        // Reopen on the OCP's configured page so the player sees the updated status
        int ocpPage = plugin.getConfigManager().getGuisConfig().getInt("one-click-pick.page", 1);
        openPickaxeSelector(player, ocpPage);
    }

    private void handlePickaxeSelectorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int maxSlots = guis.getInt("pickaxe-selector.max-slots", 45);

        Integer currentPage = pickaxePages.get(player.getUniqueId());
        if (currentPage == null) currentPage = 1;

        // Back to Shop
        if (slot == maxSlots - 5) {
            openShop(player);
            return;
        }

        // Previous page
        if (slot == maxSlots - 9 && currentPage > 1) {
            openPickaxeSelector(player, currentPage - 1);
            return;
        }

        // Next page
        int maxPage = 1;
        ConfigurationSection allPagesSection = guis.getConfigurationSection("pickaxe-selector-slots");
        if (allPagesSection != null) {
            for (String key : allPagesSection.getKeys(false)) {
                try {
                    int p = Integer.parseInt(key);
                    if (p > maxPage) maxPage = p;
                } catch (NumberFormatException ignored) {}
            }
        }
        if (slot == maxSlots - 1 && currentPage < maxPage) {
            openPickaxeSelector(player, currentPage + 1);
            return;
        }

        // One-Click Pick — slot-based detection, page-aware
        int ocpPage = guis.getInt("one-click-pick.page", 1);
        int ocpSlot = guis.getInt("one-click-pick.slot", 23) - 1; // 1-based → 0-based
        if (currentPage == ocpPage && slot == ocpSlot) {
            handleOneClickPickShopClick(player);
            return;
        }

        // Regular tool — look up by page + slot
        ConfigurationSection pageSection = guis.getConfigurationSection("pickaxe-selector-slots." + currentPage);
        if (pageSection == null) return;

        int itemKey = slot + 1; // 0-based slot → 1-based config key
        if (!pageSection.isConfigurationSection(String.valueOf(itemKey))) return;

        String mat   = pageSection.getString(itemKey + ".material", "STONE_PICKAXE:0");
        int    price = pageSection.getInt(itemKey    + ".price",    0);
        String name  = pageSection.getString(itemKey + ".name",     "Tool");

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        String pickPerm = "fastbuilder.pickaxe." + mat.toLowerCase().replace(":", ".");
        boolean owned = price == 0
                || data.hasPurchasedBlock("pickaxe:" + mat)
                || player.hasPermission("fastbuilder.blocks.*")
                || player.hasPermission(pickPerm);

        String prefix = plugin.getConfigManager().getPrefix();

        if (!owned) {
            if (data.getCoins() >= price) {
                data.removeCoins(price);
                data.purchaseBlock("pickaxe:" + mat);
                data.setSelectedPickaxe(mat);
                player.closeInventory();
                if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
                player.sendMessage(ColorUtil.translate(prefix + "&fTool purchased and selected: &c" + name
                        + " &7(&f" + price + " coins&7)"));
            } else {
                player.sendMessage(ColorUtil.translate(prefix + "&cNot enough coins! You need &f" + price + " &ccoins."));
            }
            return;
        }

        data.setSelectedPickaxe(mat);
        player.closeInventory();
        if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
        player.sendMessage(ColorUtil.translate(prefix + "&fSelected tool: &c" + name));
    }

    private void handleAnimationSelectorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int maxSlots = event.getInventory().getSize();

        // Back to Shop — center of bottom row
        if (slot == maxSlots - 5) {
            player.closeInventory();
            openShop(player);
            return;
        }

        org.bukkit.configuration.ConfigurationSection slotsSection = guis.getConfigurationSection("animation-selector-slots");
        if (slotsSection == null) return;

        int configKey = slot + 1; // 0-based slot → 1-based config key
        if (!slotsSection.isConfigurationSection(String.valueOf(configKey))) return;

        String animId = slotsSection.getString(configKey + ".animation", "NONE");
        int price = slotsSection.getInt(configKey + ".price", 0);
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
                String aName = slotsSection.getString(configKey + ".name", "Animation");
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&fAnimation purchased and selected: &c" + aName + " &7(&f" + price + " coins&7)"));
            } else {
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&cNot enough coins! You need &f" + price + " &ccoins."));
            }
            return;
        }

        data.setSelectedAnimation(animId);
        player.closeInventory();
        String aName = slotsSection.getString(configKey + ".name", "Animation");
        player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&fSelected animation: &c" + aName));
    }

    private void handleDeathSoundSelectorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int maxSlots = event.getInventory().getSize();

        // Back to Shop — center of bottom row
        if (slot == maxSlots - 5) {
            player.closeInventory();
            openShop(player);
            return;
        }

        org.bukkit.configuration.ConfigurationSection slotsSection = guis.getConfigurationSection("death-sound-selector-slots");
        if (slotsSection == null) return;

        int configKey = slot + 1; // 0-based slot → 1-based config key
        if (!slotsSection.isConfigurationSection(String.valueOf(configKey))) return;

        String soundId = slotsSection.getString(configKey + ".sound", "NONE");
        int price = slotsSection.getInt(configKey + ".price", 0);
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
                String sName = slotsSection.getString(configKey + ".name", "Sound");
                player.sendMessage(ColorUtil.translate(prefix + "&fDeath sound purchased and selected: &c" + sName + " &7(&f" + price + " coins&7)"));
            } else {
                player.sendMessage(ColorUtil.translate(prefix + "&cNot enough coins! You need &f" + price + " &ccoins."));
            }
            return;
        }

        data.setSelectedDeathSound(soundId);
        player.closeInventory();
        String sName = slotsSection.getString(configKey + ".name", "Sound");
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

        // Resolve template key from the clicked slot index (items are placed 0-based by mode template list order)
        int clickedSlot = event.getSlot();
        List<String> templates = map.getTemplatesForMode();
        if (clickedSlot < 0 || clickedSlot >= templates.size()) return;
        String templateKey = templates.get(clickedSlot);

        // Guard: cannot select a design that is already active
        String currentlySelected = pData.getSelectedDesign(mapName);
        String defaultKey2 = map.getTemplateFile();
        if (currentlySelected == null) currentlySelected = defaultKey2;
        if (templateKey.equalsIgnoreCase(currentlySelected)) {
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                    + "&cThis design is already active."));
            return;
        }

        String defaultKey = map.getTemplateFile();
        boolean isDefault = templateKey.equals(defaultKey);
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int designPrice = guis.getInt("island-designs.default-price", 0);
        boolean unlocked = isDefault
                || designPrice == 0
                || pData.hasPurchasedDesign(templateKey)
                || player.hasPermission("fastbuilder.design.*")
                || player.hasPermission("fastbuilder.design." + templateKey.toLowerCase());

        String prefix = plugin.getConfigManager().getPrefix();
        if (!unlocked) {
            // Attempt purchase
            if (pData.getCoins() >= designPrice) {
                pData.removeCoins(designPrice);
                pData.purchaseDesign(templateKey);
                plugin.getPlayerManager().savePlayerData(player.getUniqueId());
                player.sendMessage(ColorUtil.translate(prefix
                        + "&fDesign unlocked: &c" + templateKey + " &7(&f" + designPrice + " coins&7)"));
                // Fall through to apply
            } else {
                String msg = guis.getString("island-designs.lore-cannot-afford",
                        "&cNot enough coins &7(&f%coins% / %price%&7)")
                        .replace("%coins%", String.valueOf(pData.getCoins()))
                        .replace("%price%", String.valueOf(designPrice));
                player.sendMessage(ColorUtil.translate(prefix + msg));
                openDesignSelector(player);
                return;
            }
        }

        pData.setSelectedDesign(mapName, templateKey);
        player.closeInventory();

        // Spec: "Clicking a design must instantly change the island and clear all blocks immediately."
        net.gravijet.fastbuilder.gameplay.RunSession designSession =
                plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
        if (designSession != null) {
            int islandIdx = designSession.getIslandIndex();
            plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());
            designSession.reset();

            if (templateKey.equals(map.getTemplateFile())) {
                // Reverting to default: re-paste default template and clear overrides
                pData.clearActiveFinishZone(map.getName());
                plugin.getGameplayManager().revertIslandDesign(map, islandIdx);
                player.teleport(map.getIslandSpawn(islandIdx));
                if (plugin.getNpcManager() != null) {
                    plugin.getNpcManager().despawnNpc(player.getUniqueId());
                    plugin.getNpcManager().spawnNpc(player, map.getIslandNpcLocation(islandIdx));
                }
                if (plugin.getHologramManager() != null)
                    plugin.getHologramManager().updateHologram(map.getName(), islandIdx, player);
            } else {
                // Non-default design: full apply pipeline (paste, teleport, NPC, hologram, finish zone)
                plugin.getGameplayManager().applyPlayerDesign(player, map, islandIdx);
            }
            if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
        }

        player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                + "&fIsland design &capplied instantly: &f" + templateKey));
    }

    private static boolean isWallOrFence(String materialString) {
        if (materialString == null) return false;
        String upper = materialString.toUpperCase().split(":")[0];
        switch (upper) {
            case "COBBLESTONE_WALL":
            case "FENCE":
            case "SPRUCE_FENCE":
            case "BIRCH_FENCE":
            case "JUNGLE_FENCE":
            case "DARK_OAK_FENCE":
            case "ACACIA_FENCE":
            case "NETHER_BRICK_FENCE":
            case "FENCE_GATE":
            case "SPRUCE_FENCE_GATE":
            case "BIRCH_FENCE_GATE":
            case "JUNGLE_FENCE_GATE":
            case "DARK_OAK_FENCE_GATE":
            case "ACACIA_FENCE_GATE":
                return true;
            default:
                return false;
        }
    }
}
