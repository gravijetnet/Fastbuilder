package net.gravijet.fastbuilder.gui;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.IslandInstance;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class IslandSelectorGui {

    static final String PREFIX = "Island Selector";

    private final FastBuilder plugin;
    private final Map<UUID, Integer> islandPages = new HashMap<>();

    public IslandSelectorGui(FastBuilder plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, MapData map) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int pageSize = guis.getInt("island-selector-gui.page-size", 45);
        pageSize = Math.max(9, Math.min(45, (pageSize / 9) * 9));
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        int currentIsland = data != null ? data.getLastIsland() : -1;
        int page = currentIsland >= 0 ? (currentIsland / pageSize) + 1 : 1;
        openPage(player, map, page);
    }

    public void openPage(Player player, MapData map, int page) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        FileConfiguration itemsConfig = plugin.getConfigManager().getItemsConfig();

        String titleTemplate = ColorUtil.translate(guis.getString("island-selector", "Island Selector"));
        List<IslandInstance> islands = plugin.getMapManager().getIslands(map.getName());
        int pageSize = guis.getInt("island-selector-gui.page-size", 45);
        pageSize = Math.max(9, Math.min(45, (pageSize / 9) * 9));
        int totalPages = islands.isEmpty() ? 1 : (int) Math.ceil(islands.size() / (double) pageSize);
        page = Math.max(1, Math.min(page, totalPages));

        int contentOnPage = Math.min(pageSize, Math.max(0, islands.size() - (page - 1) * pageSize));
        int size;
        if (totalPages > 1) {
            size = 54;
        } else {
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

        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = size - 9; i < size; i++) inv.setItem(i, filler);

        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, islands.size());
        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            if (slot >= size - 9) break;
            IslandInstance island = islands.get(i);
            ItemStack item;
            int displayNumber = i + 1;

            boolean mapScaling = plugin.getMapManager().isMapScaling(map.getName());
            if (island.isOccupied()) {
                item = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
                SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
                if (plugin.getSkinManager() != null) {
                    plugin.getSkinManager().applyCachedSkin(skullMeta,
                            island.getOccupantUuid(), island.getOccupantName());
                } else {
                    skullMeta.setOwner(island.getOccupantName());
                }
                skullMeta.setDisplayName(ColorUtil.translate("&c#" + displayNumber + " &7- &f" + island.getOccupantName()));
                java.util.List<String> lore = new java.util.ArrayList<>();
                lore.add(ColorUtil.translate("&cOccupied"));
                skullMeta.setLore(lore);
                item.setItemMeta(skullMeta);
            } else if (mapScaling) {
                item = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 14)
                        .name("&c#" + displayNumber + " &7- &fNot ready")
                        .lore("&7This island is still being prepared.",
                              "&7Please try again in a moment.")
                        .build();
            } else {
                item = new ItemBuilder(Material.SKULL_ITEM, (byte) 3)
                        .name("&a#" + displayNumber)
                        .lore("&aClick to join")
                        .build();
            }
            inv.setItem(slot, item);
        }

        String backMat  = itemsConfig.getString("island-selector.back-button.material", "BARRIER:0");
        String backName = itemsConfig.getString("island-selector.back-button.name", "&cClose");
        inv.setItem(size - 5, ItemBuilder.fromString(backMat).name(backName).build());

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

    void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        int invSize = event.getInventory().getSize();

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null || data.getLastMap() == null) return;

        MapData map = plugin.getMapManager().getMap(data.getLastMap());
        if (map == null) return;

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

        if (invSize == 54 && totalPages > 1) {
            if (slot == invSize - 9 && currentPage > 1) {
                openPage(player, map, currentPage - 1);
                return;
            }
            if (slot == invSize - 1 && currentPage < totalPages) {
                openPage(player, map, currentPage + 1);
                return;
            }
        }

        int islandIndex = (currentPage - 1) * pageSize + slot;
        if (slot < 0 || slot >= invSize - 9 || islandIndex < 0 || islandIndex >= islands.size()) return;

        IslandInstance island = islands.get(islandIndex);
        if (island.isOccupied()) {
            net.gravijet.fastbuilder.util.Messages.send(player, "island-already-occupied",
                    "island_player", island.getOccupantName());
            player.closeInventory();
            return;
        }
        if (plugin.getMapManager().isMapScaling(map.getName())) {
            net.gravijet.fastbuilder.util.Messages.send(player, "island-scaling");
            player.closeInventory();
            return;
        }

        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());
            plugin.getGameplayManager().clearEndPlatform(player.getUniqueId());
            plugin.getGameplayManager().revertIslandDesign(map, data.getLastIsland());
            plugin.getGameplayManager().removeGlobalSessionBest(player.getUniqueId());
        }
        data.clearCustomLengths();

        if (plugin.getCpsListener() != null) {
            plugin.getCpsListener().cleanupPlayer(player.getUniqueId());
        }
        if (plugin.getNpcManager() != null) {
            plugin.getNpcManager().despawnNpc(player.getUniqueId());
        }
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().removeHologram(map.getName(), data.getLastIsland());
        }

        plugin.getMapManager().freeIsland(map.getName(), player.getUniqueId());
        if (plugin.getGameplayManager() != null) plugin.getGameplayManager().removeSession(player.getUniqueId());

        plugin.getMapManager().assignIsland(map.getName(), islandIndex, player.getUniqueId(), player.getName());
        data.setLastIsland(islandIndex);
        player.teleport(map.getIslandSpawn(islandIndex));
        player.setGameMode(GameMode.SURVIVAL);
        player.closeInventory();

        if (plugin.getGameplayManager() != null) {
            net.gravijet.fastbuilder.gameplay.RunSession newSess =
                    plugin.getGameplayManager().createSession(player.getUniqueId(), map.getName(), islandIndex);
            if (map.hasEndIsland()) {
                int desiredLen = data.getCustomLength(map.getName());
                if (desiredLen <= 0) desiredLen = map.getBaseCustomLength();
                plugin.getGameplayManager().placeEndPlatform(player, map, newSess, desiredLen);
            } else if (map.hasCustomLength()) {
                int desiredLen = data.getCustomLength(map.getName());
                if (desiredLen <= 0) desiredLen = map.getEffectiveMinCustomLength();
                if (desiredLen > 0) {
                    plugin.getGameplayManager().placeEndPlatform(player, map, newSess, desiredLen);
                }
            }
            plugin.getGameplayManager().applyPlayerDesign(player, map, islandIndex);
        }

        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().giveItems(player);
        }

        // Spawn NPC only if the selected design doesn't have a custom NPC position
        // (applyPlayerDesign already spawned it at the design position when applicable)
        boolean designHasNpc = false;
        boolean designHasHolo = false;
        if (data != null) {
            String design = data.getSelectedDesign(map.getName());
            if (design != null && !design.equals(map.getTemplateFile())) {
                net.gravijet.fastbuilder.map.MapData.DesignProfile prof = map.getDesignProfile(design);
                if (prof != null) {
                    if (prof.hasNpcPosition()) designHasNpc = true;
                    if (prof.hasHologramPosition()) designHasHolo = true;
                }
            }
        }
        if (plugin.getNpcManager() != null && !designHasNpc) {
            plugin.getNpcManager().spawnNpc(player, map.getIslandNpcLocation(islandIndex));
        }
        if (plugin.getHologramManager() != null) {
            org.bukkit.Location holoLoc = plugin.getGameplayManager() != null
                    ? plugin.getGameplayManager().getEffectiveHologramLocation(
                            player.getUniqueId(), map, islandIndex)
                    : map.getIslandHologramLocation(islandIndex);
            plugin.getHologramManager().updateHologramAt(map.getName(), islandIndex, player, holoLoc);
        }

        net.gravijet.fastbuilder.util.Messages.send(player, "island-joined",
                "island_number", String.valueOf(islandIndex + 1));
    }
}
