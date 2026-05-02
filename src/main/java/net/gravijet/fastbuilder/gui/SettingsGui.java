package net.gravijet.fastbuilder.gui;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class SettingsGui {

    static final String SETTINGS_PREFIX = "Settings";
    static final String CONFIRM_PREFIX = "Confirm";
    static final String CUSTOM_LENGTH_PREFIX = "Custom Length";

    private final FastBuilder plugin;

    public SettingsGui(FastBuilder plugin) {
        this.plugin = plugin;
    }

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
                        String customLengthStr = "&cNot available";
                        String clValueStr = "&8---";
                        String clMinStr = "---";
                        String clMaxStr = "---";
                        if (run != null) {
                            MapData clMap = plugin.getMapManager().getMap(run.getMapName());
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

    public void openCustomLengthMenu(Player player) {
        net.gravijet.fastbuilder.gameplay.RunSession run = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
        if (run == null) return;

        MapData map = plugin.getMapManager().getMap(run.getMapName());
        if (map == null || !map.hasCustomLength()) return;

        FileConfiguration guisCfg = plugin.getConfigManager().getGuisConfig();
        int menuSize = guisCfg.getInt("custom-length-menu.max-slots", 27);
        menuSize = Math.max(27, Math.min(54, ((menuSize + 8) / 9) * 9));

        String title = ColorUtil.translate("&cCustom Length &7- &f" + map.getName());
        Inventory inv = Bukkit.createInventory(null, menuSize, title);

        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = 0; i < menuSize; i++) inv.setItem(i, filler);

        PlayerData pDataCl = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        int curDist = 0;
        if (pDataCl != null) {
            curDist = pDataCl.getCustomLength(map.getName());
            if (curDist <= 0) curDist = map.getBaseCustomLength() > 0
                    ? map.getBaseCustomLength() : map.getEffectiveMinCustomLength();
        }
        String curDistStr = curDist > 0 ? "&f" + curDist + " blocks" : "&8---";
        inv.setItem(11, new ItemBuilder(Material.STICK)
                .name("&eX &7— Distance &8(" + curDistStr + "&8)")
                .lore("&7Left-click: &f-1 block closer",
                      "&7Shift+Left: &f-10 blocks closer",
                      "&7Right-click: &f+1 block further",
                      "&7Shift+Right: &f+10 blocks further",
                      "",
                      "&7You can also click the end island directly:")
                .build());
        inv.setItem(13, new ItemBuilder(Material.BLAZE_ROD)
                .name("&eY &7— Height Offset")
                .lore("&7Left-click: &f+1 block higher",
                      "&7Shift+Left: &f+10 blocks higher",
                      "&7Right-click: &f-1 block lower",
                      "&7Shift+Right: &f-10 blocks lower")
                .build());
        inv.setItem(15, new ItemBuilder(Material.BEDROCK)
                .name("&cReset to Default")
                .lore("&7Puts the island back at the", "&7default distance and height.")
                .build());

        int backSlot = menuSize - 5;
        inv.setItem(backSlot, new ItemBuilder(Material.BARRIER)
                .name("&cBack")
                .lore("&7Return to Settings")
                .build());

        player.openInventory(inv);
    }

    public void openConfirmStatsReset(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(guis.getString("confirm-stats-reset", "Confirm"));

        Inventory inv = Bukkit.createInventory(null, 27, title);

        int cost = plugin.getConfigManager().getResetStatsCost();
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        int coins = data != null ? data.getCoins() : 0;

        inv.setItem(11, new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 5)
                .name("&a&lConfirm Reset")
                .lore("&7Cost: &c" + cost + " coins", "&7Your coins: &f" + coins, "", "&aClick to confirm")
                .build());
        inv.setItem(15, new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 14)
                .name("&c&lCancel")
                .lore("&7Go back to settings")
                .build());

        player.openInventory(inv);
    }

    void handleSettingsClick(InventoryClickEvent event) {
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
                if (player.hasPermission("fastbuilder.cosmetic.infiniteblocks")) {
                    iData.setInfiniteBlocksUnlocked(true);
                    boolean newState = !iData.hasInfiniteBlocks();
                    iData.setInfiniteBlocks(newState);
                    player.sendMessage(ColorUtil.translate(prefix + "&fInfinite Blocks "
                            + (newState ? "&aenabled" : "&cdisabled") + "&f."));
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
                    player.sendMessage(ColorUtil.translate(prefix + "&fInfinite Blocks "
                            + (newState ? "&aenabled" : "&cdisabled") + "&f."));
                }
                openSettings(player);
                break;
            }
            case "reset_stats":
                if (!player.hasPermission("fastbuilder.stats.reset")) {
                    player.sendMessage(ColorUtil.translate(prefix + "&cYou don't have permission to reset your stats."));
                    break;
                }
                player.closeInventory();
                openConfirmStatsReset(player);
                break;
            case "custom_length": {
                if (!player.hasPermission("fastbuilder.feature.custom_length")) {
                    player.sendMessage(ColorUtil.translate(prefix + "&cYou don't have permission to use custom length."));
                    break;
                }
                net.gravijet.fastbuilder.gameplay.RunSession clRun = plugin.getGameplayManager() != null
                        ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
                if (clRun == null) {
                    player.sendMessage(ColorUtil.translate(prefix + "&cYou must be on an island to use custom length."));
                    break;
                }
                MapData clMap = plugin.getMapManager().getMap(clRun.getMapName());
                if (clMap == null || !clMap.hasCustomLength()) {
                    player.sendMessage(ColorUtil.translate(prefix + "&cCustom length is not available on your current map."));
                    break;
                }
                openCustomLengthMenu(player);
                break;
            }
            case "practice_mode":
                if (!player.hasPermission("fastbuilder.feature.practice_mode")) {
                    player.sendMessage(ColorUtil.translate(prefix + "&cYou don't have permission to use practice mode."));
                    break;
                }
                net.gravijet.fastbuilder.gameplay.RunSession run = plugin.getGameplayManager() != null
                        ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
                if (run != null) {
                    boolean newState = !run.isPracticeMode();
                    run.setPracticeMode(newState);
                    if (!newState && run.hasPracticeBlocks()) {
                        for (Location loc : run.getPracticeBlocks()) {
                            org.bukkit.block.Block block = loc.getBlock();
                            if (block != null) block.setType(org.bukkit.Material.AIR);
                        }
                        run.getPlacedBlocks().removeAll(run.getPracticeBlocks());
                        run.getPracticeBlocks().clear();
                        player.sendMessage(ColorUtil.translate(prefix + "&fAll practice blocks have been cleared."));
                    }
                    player.sendMessage(ColorUtil.translate(prefix + "&fPractice mode "
                            + (newState ? "&aenabled" : "&cdisabled") + "&f."));
                }
                if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
                openSettings(player);
                break;
            default:
                break;
        }
    }

    void handleCustomLengthMenuClick(InventoryClickEvent event) {
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

        MapData map = plugin.getMapManager().getMap(run.getMapName());
        if (map == null || !map.hasCustomLength()) { player.closeInventory(); return; }

        PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (pData == null) { player.closeInventory(); return; }

        ClickType click = event.getClick();
        int invSize = event.getInventory().getSize();

        if (slot == invSize - 5) {
            openSettings(player);
            return;
        }

        if (slot == 15) {
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

        // Left = closer (negative delta), right = further (positive delta)
        int delta;
        if (click == ClickType.LEFT) delta = -1;
        else if (click == ClickType.SHIFT_LEFT) delta = -10;
        else if (click == ClickType.RIGHT) delta = 1;
        else if (click == ClickType.SHIFT_RIGHT) delta = 10;
        else return;

        if (slot == 11) {
            int current = pData.getCustomLength(run.getMapName());
            if (current <= 0) current = map.getBaseCustomLength() > 0
                    ? map.getBaseCustomLength() : map.getEffectiveMinCustomLength();
            int newVal = Math.max(map.getEffectiveMinCustomLength(),
                    Math.min(map.getEffectiveMaxCustomLength(), current + delta));
            if (newVal == current) {
                String limitMsg = delta > 0
                        ? "&cCan't go further &7(max: &f" + map.getEffectiveMaxCustomLength() + " blocks&7)."
                        : "&cCan't go closer &7(min: &f" + map.getEffectiveMinCustomLength() + " blocks&7).";
                player.sendMessage(ColorUtil.translate(prefix + limitMsg));
            } else {
                pData.setCustomLength(run.getMapName(), newVal);
                if (plugin.getGameplayManager() != null) {
                    plugin.getGameplayManager().placeEndPlatform(player, map, run, newVal);
                }
            }
            openCustomLengthMenu(player);
        } else if (slot == 13) {
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

    void handleConfirmClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == 11) {
            PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
            if (data == null) return;
            int cost = plugin.getConfigManager().getResetStatsCost();
            if (data.removeCoins(cost)) {
                data.getAllStats().clear();
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                        + "&fYour stats have been reset."));
                plugin.getScoreboardManager().updateScoreboard(player);
                if (plugin.getHologramManager() != null) {
                    PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
                    if (pData != null && pData.getLastMap() != null) {
                        plugin.getHologramManager().updateHologram(pData.getLastMap(), pData.getLastIsland(), player);
                    }
                }
                plugin.getPlayerManager().savePlayerData(player.getUniqueId());
            } else {
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                        + "&cNot enough coins! You need &f" + cost + " &ccoins."));
            }
            player.closeInventory();
        } else if (slot == 15) {
            player.closeInventory();
            openSettings(player);
        }
    }
}
