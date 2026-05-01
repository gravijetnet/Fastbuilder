package net.gravijet.fastbuilder.gui;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.economy.BoosterType;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BoosterGui {

    static final String HUB_PREFIX       = "Booster Menu";
    static final String SHOP_PREFIX      = "Booster Shop";
    static final String INVENTORY_PREFIX = "Booster Inventory";

    private final FastBuilder plugin;

    public BoosterGui(FastBuilder plugin) {
        this.plugin = plugin;
    }

    // ===== Booster Hub =====

    public void openHub(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title    = ColorUtil.translate(guis.getString("booster-hub.name", HUB_PREFIX));
        int shopSlot    = guis.getInt("booster-hub.shop-slot",      11);
        int statSlot    = guis.getInt("booster-hub.status-slot",    13);
        int invSlot     = guis.getInt("booster-hub.inventory-slot", 15);
        int backSlot    = guis.getInt("booster-hub.back-slot",      22);

        int size = 27;
        Inventory inv = Bukkit.createInventory(null, size, title);

        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = 0; i < size; i++) inv.setItem(i, filler);

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        boolean hasActive = data != null && data.getBoosterExpiry() > System.currentTimeMillis();
        double activeMult = hasActive ? data.getBoosterMultiplier() : 1.0;
        int ownedTotal = data != null ? countOwned(data) : 0;

        inv.setItem(shopSlot, new ItemBuilder(Material.NETHER_STAR)
                .name("&cBooster Shop")
                .lore("&7Browse and purchase boosters", "&7that multiply your coin earnings.", "", "&fClick to open")
                .build());

        if (hasActive) {
            String remaining = plugin.getBoosterManager().formatRemaining(player.getUniqueId());
            inv.setItem(statSlot, new ItemBuilder(Material.POTION, (byte) 0)
                    .data((short) 8201)
                    .name("&c" + formatMult(activeMult) + " Coin Booster &factive")
                    .lore("&7Remaining: &f" + remaining, "", "&7All coin rewards are multiplied.")
                    .hideFlags().build());
        } else {
            inv.setItem(statSlot, new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 14)
                    .name("&cNo active booster")
                    .lore(ownedTotal > 0
                            ? "&7You have &f" + ownedTotal + " booster(s) &7ready to activate."
                            : "&7Purchase a booster from the shop first.")
                    .build());
        }

        String invLore1 = ownedTotal > 0 ? "&7You own &f" + ownedTotal + " booster(s)&7." : "&7You don't own any boosters yet.";
        inv.setItem(invSlot, new ItemBuilder(Material.CHEST)
                .name("&cBooster Inventory")
                .lore(invLore1, "&7Activate one to start multiplying coins.", "", "&fClick to open")
                .build());

        FileConfiguration itemsCfg = plugin.getConfigManager().getItemsConfig();
        String backMat  = itemsCfg.getString("change-page.back-to-shop.material", "BARRIER:0");
        String backName = itemsCfg.getString("change-page.back-to-shop.name", "&cBack to Shop");
        inv.setItem(backSlot, ItemBuilder.fromString(backMat).name(backName).build());

        player.openInventory(inv);
    }

    void handleHubClick(InventoryClickEvent event, ShopGui shopGui) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int shopSlot = guis.getInt("booster-hub.shop-slot", 11);
        int invSlot  = guis.getInt("booster-hub.inventory-slot", 15);
        int backSlot = guis.getInt("booster-hub.back-slot", 22);

        if (slot == shopSlot) {
            player.closeInventory();
            openShop(player);
        } else if (slot == invSlot) {
            player.closeInventory();
            openInventory(player);
        } else if (slot == backSlot) {
            player.closeInventory();
            shopGui.open(player);
        }
    }

    // ===== Booster Shop =====

    public void openShop(Player player) {
        List<BoosterType> types = plugin.getConfigManager().getBoosterTypes();
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title   = ColorUtil.translate(guis.getString("booster-shop.name", SHOP_PREFIX));
        int statSlot   = guis.getInt("booster-shop.status-slot", 4);

        int contentCount = Math.min(types.size(), 36);
        int rows = Math.max(3, 2 + (int) Math.ceil(contentCount / 9.0));
        int size = rows * 9;
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

        if (hasActive) {
            String remaining = plugin.getBoosterManager().formatRemaining(player.getUniqueId());
            inv.setItem(statSlot, new ItemBuilder(Material.POTION, (byte) 0)
                    .data((short) 8201)
                    .name("&c" + formatMult(activeMult) + " Coin Booster &factive")
                    .lore("&7Remaining: &f" + remaining, "", "&7All coin rewards are multiplied.")
                    .hideFlags().build());
        } else {
            inv.setItem(statSlot, new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 14)
                    .name("&cNo active booster")
                    .lore("&7Purchase a booster below, then", "&7activate it from &fBooster Inventory&7.")
                    .build());
        }

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
                    .lore("&7" + type.description, "",
                          "&7Multiplier: &c" + type.formatMultiplier(),
                          "&7Duration:   &f" + type.durationMinutes + " min",
                          "&7Price:      &c" + type.price + " coins",
                          ownedLine, "", affordLine)
                    .hideFlags().build());
        }

        inv.setItem(actualInvBtnSlot, new ItemBuilder(Material.CHEST)
                .name("&cBooster Inventory")
                .lore("&7View and activate boosters you own.").build());
        FileConfiguration itemsCfg = plugin.getConfigManager().getItemsConfig();
        String backMat = itemsCfg.getString("change-page.back-to-shop.material", "BARRIER:0");
        inv.setItem(actualBackSlot, ItemBuilder.fromString(backMat).name("&cBack").build());

        player.openInventory(inv);
    }

    void handleShopClick(InventoryClickEvent event) {
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
            openInventory(player);
            return;
        }
        if (slot == backBtnSlot) {
            player.closeInventory();
            openHub(player);
            return;
        }
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
        player.sendMessage(ColorUtil.translate(prefix + "&fPurchased &c" + type.displayName
                + " &7» &fActivate it from your &cBooster Inventory&f."));
        player.closeInventory();
        openShop(player);
    }

    // ===== Booster Inventory =====

    public void openInventory(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title   = ColorUtil.translate(guis.getString("booster-inventory.name", INVENTORY_PREFIX));
        int statSlot   = guis.getInt("booster-inventory.status-slot", 4);

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        long now = System.currentTimeMillis();
        boolean hasActive = data != null && data.getBoosterExpiry() > now;
        double activeMult = hasActive ? data.getBoosterMultiplier() : 1.0;

        List<BoosterType> allTypes = plugin.getConfigManager().getBoosterTypes();
        List<BoosterType> ownedTypes = new ArrayList<>();
        for (BoosterType type : allTypes) {
            if (data != null && data.getBoosterCount(type.id) > 0) ownedTypes.add(type);
        }

        int contentCount = Math.min(ownedTypes.size(), 36);
        int rows = Math.max(3, 2 + (int) Math.ceil(contentCount == 0 ? 0 : contentCount / 9.0));
        int size = rows * 9;
        int navRowStart = (rows - 1) * 9;
        int shopBtnSlot = navRowStart;
        int backBtnSlot = navRowStart + 4;

        Inventory inv = Bukkit.createInventory(null, size, title);
        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = 0; i < size; i++) inv.setItem(i, filler);

        if (hasActive) {
            String remaining = plugin.getBoosterManager().formatRemaining(player.getUniqueId());
            inv.setItem(statSlot, new ItemBuilder(Material.POTION, (byte) 0)
                    .data((short) 8201)
                    .name("&c" + formatMult(activeMult) + " Coin Booster &factive")
                    .lore("&7Remaining: &f" + remaining, "",
                          "&cWait for this booster to expire", "&cbefore activating another.")
                    .hideFlags().build());
        } else {
            inv.setItem(statSlot, new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 10)
                    .name("&fNo active booster")
                    .lore("&7Click a booster below to activate it.")
                    .build());
        }

        for (int i = 0; i < contentCount; i++) {
            BoosterType type = ownedTypes.get(i);
            int owned = data.getBoosterCount(type.id);
            String actionLine = hasActive ? "&cAlready active — wait for it to expire." : "&fClick to activate";

            inv.setItem(9 + i, new ItemBuilder(Material.POTION, (byte) 0)
                    .data(type.potionData)
                    .name(type.displayName + " &7x" + owned)
                    .lore("&7" + type.description, "",
                          "&7Multiplier: &c" + type.formatMultiplier(),
                          "&7Duration:   &f" + type.durationMinutes + " min",
                          "&7Owned:      &f" + owned, "", actionLine)
                    .hideFlags().build());
        }

        inv.setItem(shopBtnSlot, new ItemBuilder(Material.NETHER_STAR)
                .name("&cBooster Shop").lore("&7Browse and buy more boosters.").build());
        FileConfiguration itemsCfg = plugin.getConfigManager().getItemsConfig();
        String backMat = itemsCfg.getString("change-page.back-to-shop.material", "BARRIER:0");
        inv.setItem(backBtnSlot, ItemBuilder.fromString(backMat).name("&cBack").build());

        player.openInventory(inv);
    }

    void handleInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        Inventory inv = event.getInventory();
        int size = inv.getSize();
        int rows = size / 9;
        int navRowStart = (rows - 1) * 9;
        int shopBtnSlot = navRowStart;
        int backBtnSlot = navRowStart + 4;
        String prefix = plugin.getConfigManager().getPrefix();

        if (slot == shopBtnSlot) { player.closeInventory(); openShop(player); return; }
        if (slot == backBtnSlot) { player.closeInventory(); openHub(player); return; }
        if (slot < 9 || slot >= navRowStart) return;

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

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

        player.sendMessage(ColorUtil.translate(prefix + "&c" + type.formatMultiplier()
                + " Coin Booster &factivated &7» &f" + type.durationMinutes + " minutes"));
        player.closeInventory();
        openInventory(player);
    }

    static String formatMult(double mult) {
        if (mult == Math.floor(mult)) return (int) mult + "x";
        return String.format("%.1fx", mult);
    }

    static int countOwned(PlayerData data) {
        int total = 0;
        for (int qty : data.getBoosterInventory().values()) total += qty;
        return total;
    }
}
