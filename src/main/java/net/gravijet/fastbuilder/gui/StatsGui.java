package net.gravijet.fastbuilder.gui;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.ItemBuilder;
import net.gravijet.fastbuilder.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StatsGui {

    static final String STATS_PREFIX       = "Stats";
    static final String LEADERBOARD_PREFIX = "Leaderboard";

    private final FastBuilder plugin;

    public StatsGui(FastBuilder plugin) {
        this.plugin = plugin;
    }

    // ===== Stats GUI =====

    public void openStatsGui(Player viewer, PlayerData data) {
        String title = ColorUtil.translate("&c&lStats &7- &f" + data.getName());
        Inventory inv = Bukkit.createInventory(null, 54, title);

        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        int totalAttempts = 0;
        int totalSuccesses = 0;
        for (PlayerData.MapStats s : data.getAllStats().values()) {
            totalAttempts += s.totalAttempts;
            totalSuccesses += s.successfulAttempts;
        }
        int globalRate = totalAttempts > 0 ? (int) ((double) totalSuccesses / totalAttempts * 100) : 0;

        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        if (plugin.getSkinManager() != null) {
            plugin.getSkinManager().applyCachedSkin(skullMeta, data.getUuid(), data.getName());
        }
        skullMeta.setDisplayName(ColorUtil.translate("&c&l" + data.getName()));
        List<String> headLore = new ArrayList<>();
        headLore.add(ColorUtil.translate("&7Coins: &f" + data.getCoins()));
        headLore.add(ColorUtil.translate("&7Total Runs: &f" + totalAttempts));
        headLore.add(ColorUtil.translate("&7Successful: &f" + totalSuccesses));
        headLore.add(ColorUtil.translate("&7Success Rate: &f" + globalRate + "%"));
        skullMeta.setLore(headLore);
        head.setItemMeta(skullMeta);
        inv.setItem(4, head);

        int slot = 18;
        for (Map.Entry<String, PlayerData.MapStats> entry : data.getAllStats().entrySet()) {
            if (slot >= 54) break;
            String mapName = entry.getKey();
            PlayerData.MapStats stats = entry.getValue();

            MapData mapData = plugin.getMapManager().getMap(mapName);
            String iconStr = mapData != null ? mapData.getIcon() : null;
            ItemBuilder builder = (iconStr != null && !iconStr.isEmpty())
                    ? ItemBuilder.fromString(iconStr)
                    : new ItemBuilder(Material.GRASS);

            String bestTime = stats.hasBestTime() ? TimeUtil.formatTime(stats.bestTime) : "N/A";
            String avgTime = stats.getAverageTime() >= 0 ? TimeUtil.formatTime(stats.getAverageTime()) : "N/A";
            int rate = stats.totalAttempts > 0 ? (int) ((double) stats.successfulAttempts / stats.totalAttempts * 100) : 0;

            List<String> lore = new ArrayList<>();
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
            if (slot % 9 == 0) slot++;
        }

        viewer.openInventory(inv);
    }

    // ===== Leaderboard GUI =====

    public void openLeaderboardMapPicker(Player player) {
        List<MapData> maps = new ArrayList<>();
        for (MapData m : plugin.getMapManager().getAllMaps()) {
            if (m.isEnabled()) maps.add(m);
        }

        int itemCount = maps.size();
        int rawSize = itemCount + 9;
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
            inv.setItem(slot, builder.name("&c" + map.getName()).lore("&7Click to view leaderboard").build());
            slot++;
        }

        FileConfiguration itemsCfg = plugin.getConfigManager().getItemsConfig();
        String closeMat  = itemsCfg.getString("island-selector.back-button.material", "BARRIER:0");
        String closeName = itemsCfg.getString("island-selector.back-button.name", "&cClose");
        inv.setItem(size - 5, ItemBuilder.fromString(closeMat).name(closeName).build());

        player.openInventory(inv);
    }

    public void openLeaderboardGui(Player player, String mapName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            MapData lbMap = plugin.getMapManager().getMap(mapName);
            boolean infinite = lbMap != null && lbMap.isInfinite();

            if (infinite) {
                List<Map.Entry<String, long[]>> topInf =
                        plugin.getPlayerManager().getTopInfiniteDistancesForMap(mapName, 10);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    String title = ColorUtil.translate("&c&lLeaderboard &7- &f" + mapName);
                    Inventory inv = Bukkit.createInventory(null, 54, title);
                    ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
                    for (int i = 0; i < 54; i++) inv.setItem(i, filler);

                    int[] entrySlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
                    for (int i = 0; i < Math.min(topInf.size(), entrySlots.length); i++) {
                        Map.Entry<String, long[]> entry = topInf.get(i);
                        int pos = i + 1;
                        String rankColor = pos == 1 ? "&6" : pos == 2 ? "&7" : pos == 3 ? "&c" : "&8";
                        ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
                        SkullMeta meta = (SkullMeta) skull.getItemMeta();
                        if (plugin.getSkinManager() != null) {
                            plugin.getSkinManager().applyCachedSkin(meta, null, entry.getKey());
                        }
                        meta.setDisplayName(ColorUtil.translate(rankColor + "&l#" + pos + " &f" + entry.getKey()));
                        List<String> lore = new ArrayList<>();
                        lore.add(ColorUtil.translate("&7Distance: &f" + entry.getValue()[0] + " blocks"));
                        long t = entry.getValue()[1];
                        if (t > 0 && t < Long.MAX_VALUE)
                            lore.add(ColorUtil.translate("&7Time: &f" + TimeUtil.formatTime(t)));
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

                    int[] entrySlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
                    for (int i = 0; i < Math.min(top.size(), entrySlots.length); i++) {
                        Map.Entry<String, Long> entry = top.get(i);
                        int pos = i + 1;
                        String rankColor = pos == 1 ? "&6" : pos == 2 ? "&7" : pos == 3 ? "&c" : "&8";
                        ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
                        SkullMeta meta = (SkullMeta) skull.getItemMeta();
                        if (plugin.getSkinManager() != null) {
                            plugin.getSkinManager().applyCachedSkin(meta, null, entry.getKey());
                        }
                        meta.setDisplayName(ColorUtil.translate(rankColor + "&l#" + pos + " &f" + entry.getKey()));
                        List<String> lore = new ArrayList<>();
                        lore.add(ColorUtil.translate("&7Time: &f" + TimeUtil.formatTime(entry.getValue())));
                        lore.add(ColorUtil.translate("&aClick to watch replay"));
                        meta.setLore(lore);
                        skull.setItemMeta(meta);
                        inv.setItem(entrySlots[i], skull);
                    }
                    if (top.isEmpty()) {
                        inv.setItem(22, new ItemBuilder(Material.BARRIER).name("&7No times recorded yet.").build());
                    }
                    FileConfiguration itemsCfg = plugin.getConfigManager().getItemsConfig();
                    String closeMat = itemsCfg.getString("island-selector.back-button.material", "BARRIER:0");
                    inv.setItem(49, ItemBuilder.fromString(closeMat).name("&cBack").build());
                    player.openInventory(inv);
                });
            }
        });
    }

    void handleLeaderboardClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        int invSize = event.getInventory().getSize();
        String stripped = ColorUtil.strip(event.getView().getTitle());

        if (slot == invSize - 5) {
            if (stripped.contains(" - ")) {
                openLeaderboardMapPicker(player);
            } else {
                player.closeInventory();
            }
            return;
        }

        if (stripped.equals("Leaderboard")) {
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.STAINED_GLASS_PANE
                    || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;
            String mapName = ColorUtil.strip(item.getItemMeta().getDisplayName());
            if (plugin.getMapManager().getMap(mapName) == null) return;
            openLeaderboardGui(player, mapName);
            return;
        }

        if (stripped.contains(" - ") && slot != invSize - 5) {
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() != Material.SKULL_ITEM) return;
            if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;
            String[] parts = stripped.split(" - ", 2);
            if (parts.length < 2) return;
            String mapName = parts[1].trim();

            String displayName = ColorUtil.strip(item.getItemMeta().getDisplayName());
            String targetName = displayName.replaceFirst("^#\\d+\\s+", "").trim();
            if (targetName.isEmpty()) return;

            player.closeInventory();

            if (plugin.getReplayManager() == null) {
                net.gravijet.fastbuilder.util.Messages.send(player, "replay-unavailable");
                return;
            }

            final String finalMapName = mapName;
            final String finalName = targetName;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                UUID targetUuid = plugin.getPlayerManager().getUuidForPlayerName(finalName);
                if (targetUuid == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                        net.gravijet.fastbuilder.util.Messages.send(player, "player-not-found",
                                "player", finalName));
                    return;
                }
                net.gravijet.fastbuilder.replay.ReplayData pbReplay =
                        plugin.getReplayManager().getPbReplay(targetUuid, finalMapName);
                if (pbReplay == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                        net.gravijet.fastbuilder.util.Messages.send(player, "replay-none-found",
                                "player", finalName, "map", finalMapName));
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getReplayManager().startPlayback(player, pbReplay));
            });
        }
    }
}
