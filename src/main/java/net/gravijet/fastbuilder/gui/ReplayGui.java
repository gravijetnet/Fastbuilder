package net.gravijet.fastbuilder.gui;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.replay.ReplayData;
import net.gravijet.fastbuilder.replay.ReplayManager;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ReplayGui {

    static final String PREFIX = "Replays";

    private final FastBuilder plugin;
    private final Map<UUID, Integer> replayPages = new HashMap<>();
    private final Map<UUID, List<ReplayData>> displayedReplays = new HashMap<>();
    private final Map<UUID, Boolean> replayFavoritesMode = new HashMap<>();
    private final Map<UUID, String> replayGuiMap = new HashMap<>();

    public ReplayGui(FastBuilder plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String mapName, boolean showFavorites) {
        if (plugin.getReplayManager() == null) {
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&cReplay system is not available."));
            return;
        }

        List<ReplayData> allReplays = plugin.getReplayManager().getPlayerReplays(player.getUniqueId(), mapName);

        // Filter replays to match the player's current mode (custom length vs normal)
        net.gravijet.fastbuilder.gameplay.RunSession currentSession = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
        boolean playerInCustomLength = false;
        if (currentSession != null) {
            net.gravijet.fastbuilder.map.MapData curMap = plugin.getMapManager().getMap(currentSession.getMapName());
            if (curMap != null && curMap.hasCustomLength()) {
                net.gravijet.fastbuilder.player.PlayerData pd =
                        plugin.getPlayerManager().getCachedData(player.getUniqueId());
                playerInCustomLength = pd != null && pd.isCustomLengthEnabled(currentSession.getMapName());
            }
        }
        final boolean inCL = playerInCustomLength;
        allReplays = allReplays.stream()
                .filter(r -> inCL ? r.getCustomLength() > 0 : r.getCustomLength() == 0)
                .collect(java.util.stream.Collectors.toList());

        List<ReplayData> replays;
        if (showFavorites) {
            PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            Set<String> favs = pData != null ? pData.getFavoriteReplays() : Collections.emptySet();
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
        openPage(player, replays, 1, showFavorites, mapName);
    }

    void openPage(Player player, List<ReplayData> replays, int page, boolean favoritesMode, String mapName) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int itemsPerPage = 28;
        int maxPage = Math.max(1, (int) Math.ceil(replays.size() / (double) itemsPerPage));

        String titleTemplate = guis.getString("replays", "Replays - Page %page%/%max_page%");
        String title = ColorUtil.translate(titleTemplate
                .replace("%page%", String.valueOf(page))
                .replace("%max_page%", String.valueOf(maxPage)));

        Inventory inv = Bukkit.createInventory(null, 54, title);

        PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        Set<String> favorites = pData != null ? pData.getFavoriteReplays() : Collections.<String>emptySet();
        long pbTime = Long.MAX_VALUE;
        for (ReplayData rd : replays) {
            if (rd.isSuccessful() && rd.getRunTimeMillis() > 0 && rd.getRunTimeMillis() < pbTime) {
                pbTime = rd.getRunTimeMillis();
            }
        }

        ItemStack border = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9, border);
            inv.setItem(row * 9 + 8, border);
        }

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
            if (isPb) icon = Material.NETHER_STAR;
            else if (isFav) icon = Material.GOLD_INGOT;
            else icon = replay.isSuccessful() ? Material.EMERALD : Material.REDSTONE;

            String status = replay.isSuccessful() ? "&aSuccessful" : "&cFailed";
            String time = replay.getRunTimeMillis() > 0 ? TimeUtil.formatTime(replay.getRunTimeMillis()) : "N/A";
            String favLine = isFav ? "&6Favorited &e(Right-click to remove)" : "&7Right-click to favorite";
            String pbLine  = isPb  ? "&6&lPersonal Best" : "";

            net.gravijet.fastbuilder.map.MapData replayMap =
                    plugin.getMapManager().getMap(replay.getMapName());
            boolean isInfiniteReplay = replayMap != null && replayMap.isInfinite();

            java.util.List<String> loreList = new java.util.ArrayList<>();
            loreList.add(ColorUtil.translate(status));
            if (isInfiniteReplay) {
                loreList.add(ColorUtil.translate("&7Distance: &f" + replay.getBlocksPlaced() + " blocks"));
                if (replay.getRunTimeMillis() > 0)
                    loreList.add(ColorUtil.translate("&7Time: &f" + time));
            } else {
                loreList.add(ColorUtil.translate("&7Time: &f" + time));
                if (replay.getCustomLength() > 0)
                    loreList.add(ColorUtil.translate("&7Distance: &f" + replay.getCustomLength() + " blocks"));
            }
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

        if (page > 1) inv.setItem(45, new ItemBuilder(Material.ARROW).name("&c<< Previous Page").build());
        if (page < maxPage) inv.setItem(53, new ItemBuilder(Material.ARROW).name("&a» Next Page").build());
        inv.setItem(49, new ItemBuilder(Material.PAPER)
                .name("&7Page &f" + page + " &7/ &f" + maxPage).build());

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

    void handleClick(InventoryClickEvent event) {
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

        if (slot == 45 && currentPage > 1) {
            openPage(player, replays, currentPage - 1, favMode, mapName);
            return;
        }
        if (slot == 53 && currentPage < maxPage) {
            openPage(player, replays, currentPage + 1, favMode, mapName);
            return;
        }
        if (slot == 47) {
            open(player, mapName, !favMode);
            return;
        }

        int[] contentSlots = new int[28];
        int ci = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                contentSlots[ci++] = row * 9 + col;
            }
        }

        int contentIndex = -1;
        for (int i = 0; i < contentSlots.length; i++) {
            if (contentSlots[i] == slot) { contentIndex = i; break; }
        }
        if (contentIndex < 0) return;

        int replayIndex = (currentPage - 1) * itemsPerPage + contentIndex;
        if (replayIndex < 0 || replayIndex >= replays.size()) return;

        ReplayData replay = replays.get(replayIndex);
        boolean isRightClick = event.getClick() == org.bukkit.event.inventory.ClickType.RIGHT;

        if (isRightClick) {
            PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            if (pData == null) return;
            boolean wasFav = pData.isFavoriteReplay(replay.getFileName());
            pData.toggleFavoriteReplay(replay.getFileName());
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                    + (!wasFav ? "&aAdded to favorites." : "&7Removed from favorites.")));
            openPage(player, replays, currentPage, favMode, mapName);
        } else {
            player.closeInventory();
            if (plugin.getReplayManager() != null) {
                plugin.getReplayManager().startPlayback(player, replay);
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&fStarting replay..."));
            }
        }
    }
}
