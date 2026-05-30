package net.gravijet.fastbuilder.gui;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.ItemBuilder;
import net.gravijet.fastbuilder.util.Messages;
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
                                (run != null && run.isPracticeMode()) ? "&aon" : "&coff");
                        line = line.replace("%infinite_blocks_status%",
                                (data != null && data.hasInfiniteBlocks()) ? "&aon" : "&coff");
                        line = line.replace("%infinite_blocks_unlocked%",
                                (data != null && data.hasInfiniteBlocksUnlocked()) ? "&aunlocked" : "&clocked");
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
        int curY = 0;
        if (pDataCl != null) {
            curDist = pDataCl.getCustomLength(map.getName());
            if (curDist <= 0) curDist = map.getBaseCustomLength() > 0
                    ? map.getBaseCustomLength() : map.getEffectiveMinCustomLength();
            curY = pDataCl.getCustomLengthY(map.getName());
        }
        String curDistStr = curDist > 0 ? "&f" + curDist + " blocks" : "&8---";
        String curYStr = "&f" + (curY >= 0 ? "+" : "") + curY;
        inv.setItem(11, new ItemBuilder(Material.STICK)
                .name("&cDistance &8» " + curDistStr)
                .lore("&7Move the end island closer to or further from spawn.",
                      "",
                      "&7Left-click &8» &c-1 block &7closer",
                      "&7Shift+Left &8» &c-10 blocks &7closer",
                      "&7Right-click &8» &a+1 block &7further",
                      "&7Shift+Right &8» &a+10 blocks &7further",
                      "",
                      "&7Tip: you can also click the end island directly.")
                .build());
        inv.setItem(13, new ItemBuilder(Material.BLAZE_ROD)
                .name("&cHeight Offset &8» " + curYStr)
                .lore("&7Raise or lower the end island.",
                      "",
                      "&7Left-click &8» &a+1 block &7higher",
                      "&7Shift+Left &8» &a+10 blocks &7higher",
                      "&7Right-click &8» &c-1 block &7lower",
                      "&7Shift+Right &8» &c-10 blocks &7lower")
                .build());
        inv.setItem(15, new ItemBuilder(Material.BEDROCK)
                .name("&cReset to Default")
                .lore("&7Restores distance and height", "&7to the map defaults.")
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

        switch (gui) {
            case "infinite_blocks": {
                PlayerData iData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
                if (iData == null) break;
                if (player.hasPermission("fastbuilder.cosmetic.infiniteblocks")) {
                    iData.setInfiniteBlocksUnlocked(true);
                    boolean newState = !iData.hasInfiniteBlocks();
                    iData.setInfiniteBlocks(newState);
                    Messages.send(player, newState ? "infinite-blocks-on" : "infinite-blocks-off");
                    openSettings(player);
                    break;
                }
                if (!iData.hasInfiniteBlocksUnlocked()) {
                    int unlockCost = plugin.getConfigManager().getInfiniteBlocksUnlockCost();
                    if (iData.removeCoins(unlockCost)) {
                        iData.setInfiniteBlocksUnlocked(true);
                        iData.setInfiniteBlocks(true);
                        Messages.send(player, "infinite-blocks-unlocked",
                                "amount", String.valueOf(unlockCost));
                        plugin.getPlayerManager().savePlayerData(player.getUniqueId());
                    } else {
                        Messages.send(player, "coins-not-enough");
                    }
                } else {
                    boolean newState = !iData.hasInfiniteBlocks();
                    iData.setInfiniteBlocks(newState);
                    Messages.send(player, newState ? "infinite-blocks-on" : "infinite-blocks-off");
                }
                openSettings(player);
                break;
            }
            case "reset_stats":
                if (!player.hasPermission("fastbuilder.stats.reset")) {
                    Messages.send(player, "no-permission");
                    break;
                }
                player.closeInventory();
                openConfirmStatsReset(player);
                break;
            case "custom_length": {
                if (!player.hasPermission("fastbuilder.feature.custom_length")) {
                    Messages.send(player, "custom-length-no-permission");
                    break;
                }
                net.gravijet.fastbuilder.gameplay.RunSession clRun = plugin.getGameplayManager() != null
                        ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
                if (clRun == null) {
                    Messages.send(player, "not-on-island");
                    break;
                }
                MapData clMap = plugin.getMapManager().getMap(clRun.getMapName());
                if (clMap == null || !clMap.hasCustomLength()) {
                    Messages.send(player, "custom-length-unavailable");
                    break;
                }
                openCustomLengthMenu(player);
                break;
            }
            case "practice_mode":
                if (!player.hasPermission("fastbuilder.feature.practice_mode")) {
                    Messages.send(player, "practice-no-permission");
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
                        Messages.send(player, "practice-blocks-cleared");
                    }
                    Messages.send(player, newState ? "practice-mode-on" : "practice-mode-off");
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

        if (!player.hasPermission("fastbuilder.feature.custom_length")) {
            Messages.send(player, "custom-length-no-permission");
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
            Messages.send(player, "custom-length-defaults-reset");
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
                if (delta > 0) {
                    Messages.send(player, "custom-length-at-max-distance",
                            "max", String.valueOf(map.getEffectiveMaxCustomLength()));
                } else {
                    Messages.send(player, "custom-length-at-min-distance",
                            "min", String.valueOf(map.getEffectiveMinCustomLength()));
                }
            } else {
                pData.setCustomLength(run.getMapName(), newVal);
                if (plugin.getGameplayManager() != null) {
                    plugin.getGameplayManager().placeEndPlatform(player, map, run, newVal);
                }
            }
            openCustomLengthMenu(player);
        } else if (slot == 13) {
            // Y-height tooltip says: Left/Shift+Left = higher (+), Right/Shift+Right = lower (-).
            // The delta computed above is signed as for X (left = -, right = +), so invert here
            // to match the displayed lore on the BLAZE_ROD item.
            int yDelta = -delta;
            int minY = plugin.getConfigManager().getCustomLengthMinY();
            int maxY = plugin.getConfigManager().getCustomLengthMaxY();
            int current = pData.getCustomLengthY(run.getMapName());
            int newVal = Math.max(minY, Math.min(maxY, current + yDelta));
            if (newVal == current) {
                if (yDelta > 0) {
                    Messages.send(player, "custom-length-at-max-height",
                            "max", String.valueOf(maxY));
                } else {
                    Messages.send(player, "custom-length-at-min-height",
                            "min", String.valueOf(minY));
                }
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
                Messages.send(player, "stats-reset");
                plugin.getScoreboardManager().updateScoreboard(player);
                if (plugin.getHologramManager() != null) {
                    PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
                    if (pData != null && pData.getLastMap() != null) {
                        plugin.getHologramManager().updateHologram(pData.getLastMap(), pData.getLastIsland(), player);
                    }
                }
                plugin.getPlayerManager().savePlayerData(player.getUniqueId());
            } else {
                Messages.send(player, "coins-not-enough");
            }
            player.closeInventory();
        } else if (slot == 15) {
            player.closeInventory();
            openSettings(player);
        }
    }
}
