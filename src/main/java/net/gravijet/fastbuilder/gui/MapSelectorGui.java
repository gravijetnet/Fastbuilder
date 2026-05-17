package net.gravijet.fastbuilder.gui;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MapSelectorGui {

    static final String PREFIX = "Map Selector";

    private final FastBuilder plugin;

    public MapSelectorGui(FastBuilder plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(guis.getString("map-selector.name", PREFIX));
        int size = guis.getInt("map-selector.max-slots", 54);

        Inventory inv = Bukkit.createInventory(null, size, title);

        boolean fillerEnabled = guis.getBoolean("map-selector.filler.enabled", true);
        if (fillerEnabled) {
            String fillerMat = guis.getString("map-selector.filler.material", "STAINED_GLASS_PANE:7");
            String fillerName = guis.getString("map-selector.filler.name", " ");
            ItemStack filler = ItemBuilder.fromString(fillerMat).name(fillerName).build();
            for (int i = 0; i < size; i++) inv.setItem(i, filler);
        }

        ConfigurationSection slotsSection = guis.getConfigurationSection("map-selector.slots");
        Map<String, Integer> configuredSlots = new HashMap<>();
        if (slotsSection != null) {
            for (String slotKey : slotsSection.getKeys(false)) {
                try {
                    int slotNum = Integer.parseInt(slotKey);
                    String mapName = slotsSection.getString(slotKey);
                    if (mapName != null) configuredSlots.put(mapName, slotNum);
                } catch (NumberFormatException ignored) {}
            }
        }

        Collection<MapData> allMaps = plugin.getMapManager().getAllMaps();
        java.util.Set<Integer> usedSlots = new java.util.HashSet<>(configuredSlots.values());

        for (MapData map : allMaps) {
            if (!map.isEnabled()) continue;

            int slot;
            if (configuredSlots.containsKey(map.getName())) {
                slot = configuredSlots.get(map.getName());
            } else {
                slot = 0;
                while (slot < size && usedSlots.contains(slot)) slot++;
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
                    .lore("&7Players: &f" + occupied + "/" + total, "", "&aClick to join")
                    .build();

            inv.setItem(slot, item);
        }

        player.openInventory(inv);
    }

    void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;

        String displayName = ColorUtil.strip(item.getItemMeta().getDisplayName());
        if (displayName.trim().isEmpty()) return;

        MapData map = plugin.getMapManager().getMap(displayName);
        if (map == null || !map.isEnabled()) return;

        PlayerData existingData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (existingData != null && map.getName().equalsIgnoreCase(existingData.getLastMap())) {
            player.closeInventory();
            String raw = plugin.getConfigManager().getMessage("map-already-on");
            if (raw == null || raw.isEmpty()) raw = plugin.getConfigManager().getPrefix() + "&cYou are already on this map.";
            player.sendMessage(ColorUtil.translate(raw.replace("%prefix%", plugin.getConfigManager().getPrefix())));
            return;
        }

        player.closeInventory();

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

        if (plugin.getCpsListener() != null) {
            plugin.getCpsListener().cleanupPlayer(player.getUniqueId());
        }
        if (existingData != null) {
            if (plugin.getNpcManager() != null) plugin.getNpcManager().despawnNpc(player.getUniqueId());
            if (plugin.getHologramManager() != null && existingData.getLastMap() != null) {
                plugin.getHologramManager().removeHologram(existingData.getLastMap(), existingData.getLastIsland());
            }
        }

        plugin.getMapManager().freeAllIslands(player.getUniqueId());
        if (plugin.getGameplayManager() != null) plugin.getGameplayManager().removeSession(player.getUniqueId());

        int island = plugin.getMapManager().assignFreeIsland(map.getName(), player.getUniqueId(), player.getName());
        if (island < 0) {
            String noIsland = plugin.getConfigManager().getMessage("no-islands");
            if (noIsland == null || noIsland.isEmpty()) noIsland = plugin.getConfigManager().getMessage("no-free-islands");
            player.sendMessage(ColorUtil.translate(noIsland.replace("%prefix%", plugin.getConfigManager().getPrefix())));
            return;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId(), player.getName());
        data.setLastMap(map.getName());
        data.setLastIsland(island);
        player.teleport(map.getIslandSpawn(island));

        if (plugin.getGameplayManager() != null) {
            net.gravijet.fastbuilder.gameplay.RunSession newSess =
                    plugin.getGameplayManager().createSession(player.getUniqueId(), map.getName(), island);
            if (map.hasEndIsland()) {
                plugin.getGameplayManager().placeEndPlatform(player, map, newSess, map.getBaseCustomLength());
            }
            plugin.getGameplayManager().applyPlayerDesign(player, map, island);
        }

        if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);

        // Respect the player's selected design: applyPlayerDesign() already spawned the
        // NPC at the design's custom position, so don't overwrite it with the default one.
        boolean designHasNpc = false;
        String selDesign = data.getSelectedDesign(map.getName());
        if (selDesign != null && !selDesign.equals(map.getTemplateFile())) {
            MapData.DesignProfile prof = map.getDesignProfile(selDesign);
            if (prof != null && prof.hasNpcPosition()) designHasNpc = true;
        }
        if (plugin.getNpcManager() != null && !designHasNpc) {
            plugin.getNpcManager().spawnNpc(player, map.getIslandNpcLocation(island));
        }
        if (plugin.getHologramManager() != null) {
            org.bukkit.Location holoLoc = plugin.getGameplayManager() != null
                    ? plugin.getGameplayManager().getEffectiveHologramLocation(
                            player.getUniqueId(), map, island)
                    : map.getIslandHologramLocation(island);
            plugin.getHologramManager().updateHologramAt(map.getName(), island, player, holoLoc);
        }

        plugin.getMapManager().checkAutoscale(map);
        plugin.getScoreboardManager().updateScoreboard(player);

        String raw = plugin.getConfigManager().getMessage("joined-mode");
        if (raw != null && !raw.isEmpty()) {
            raw = raw.replace("%map%", map.getName())
                    .replace("%prefix%", plugin.getConfigManager().getPrefix());
            player.sendMessage(ColorUtil.translate(raw));
        }
    }
}
