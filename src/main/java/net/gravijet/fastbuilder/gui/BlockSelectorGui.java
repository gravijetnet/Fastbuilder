package net.gravijet.fastbuilder.gui;

import net.gravijet.fastbuilder.FastBuilder;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BlockSelectorGui {

    static final String PREFIX = "Block Selector";

    private final FastBuilder plugin;
    private final Map<UUID, Integer> blockSelectorPages = new HashMap<>();

    public BlockSelectorGui(FastBuilder plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int page) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int maxSlots = guis.getInt("block-selector.max-slots", 45);
        String titleTemplate = guis.getString("block-selector.name", "Block Selector - %page%/3");

        ConfigurationSection pageSection = guis.getConfigurationSection("block-selector-slots." + page);
        if (pageSection == null) {
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&cNo blocks on this page."));
            return;
        }

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

        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = 0; i < maxSlots; i++) inv.setItem(i, filler);

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        String purchasedStatus = guis.getString("block-status.purchased", "&aYou already own this block!");
        String notPurchasedStatus = guis.getString("block-status.not-purchased", "&cYou don't own this block yet!");

        for (String slotKey : pageSection.getKeys(false)) {
            try {
                int slotIndex = Integer.parseInt(slotKey) - 1;
                if (slotIndex < 0 || slotIndex >= maxSlots - 9) continue;

                String name = pageSection.getString(slotKey + ".name", "Block");
                String mat = pageSection.getString(slotKey + ".material", "STONE:0");
                int price = pageSection.getInt(slotKey + ".price", 0);

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

                ItemStack item = ItemBuilder.fromString(mat).name("&c" + name).lore(lore).build();
                inv.setItem(slotIndex, item);
            } catch (NumberFormatException ignored) {}
        }

        FileConfiguration itemsConfig = plugin.getConfigManager().getItemsConfig();
        String backMat  = itemsConfig.getString("change-page.back-to-shop.material", "BARRIER:0");
        String backName = itemsConfig.getString("change-page.back-to-shop.name", "&cBack to Shop");
        inv.setItem(maxSlots - 5, ItemBuilder.fromString(backMat).name(backName).build());

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

    void handleClick(InventoryClickEvent event, ShopGui shopGui) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int maxSlots = guis.getInt("block-selector.max-slots", 45);

        Integer currentPage = blockSelectorPages.get(player.getUniqueId());
        if (currentPage == null) currentPage = 1;

        if (slot == maxSlots - 5) {
            shopGui.open(player);
            return;
        }
        if (slot == maxSlots - 9 && currentPage > 1) {
            open(player, currentPage - 1);
            return;
        }

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
            open(player, currentPage + 1);
            return;
        }

        ConfigurationSection pageSection = guis.getConfigurationSection("block-selector-slots." + currentPage);
        if (pageSection == null) return;

        int blockIndex = slot + 1;
        if (!pageSection.isConfigurationSection(String.valueOf(blockIndex))) return;

        String mat = pageSection.getString(blockIndex + ".material", "STONE:0");
        int price = pageSection.getInt(blockIndex + ".price", 0);

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        String blockPerm = "fastbuilder.block." + mat.toLowerCase().replace(":", ".");
        boolean owned = price == 0 || data.hasPurchasedBlock(mat)
                || player.hasPermission("fastbuilder.blocks.*")
                || player.hasPermission(blockPerm);

        if (!owned) {
            if (data.getCoins() >= price) {
                data.removeCoins(price);
                data.purchaseBlock(mat);
                String oldBlockOnPurchase = data.getSelectedBlock();
                data.setSelectedBlock(mat);
                plugin.getPlayerManager().savePlayerData(player.getUniqueId());
                player.closeInventory();
                if (plugin.getHotbarManager() != null) plugin.getHotbarManager().updateBlockSlot(player);
                if (oldBlockOnPurchase != null && !oldBlockOnPurchase.equals(mat)
                        && plugin.getGameplayManager() != null) {
                    net.gravijet.fastbuilder.gameplay.RunSession swapSession =
                            plugin.getGameplayManager().getSession(player.getUniqueId());
                    if (swapSession != null) plugin.getGameplayManager().resetRun(player);
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

        String oldBlock = data.getSelectedBlock();
        data.setSelectedBlock(mat);
        plugin.getPlayerManager().savePlayerData(player.getUniqueId());
        player.closeInventory();
        if (plugin.getHotbarManager() != null) plugin.getHotbarManager().updateBlockSlot(player);
        if (oldBlock != null && !oldBlock.equals(mat) && plugin.getGameplayManager() != null) {
            net.gravijet.fastbuilder.gameplay.RunSession swapSession =
                    plugin.getGameplayManager().getSession(player.getUniqueId());
            if (swapSession != null) plugin.getGameplayManager().resetRun(player);
        }
        String blockName = pageSection.getString(blockIndex + ".name", "Block");
        player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                + "&fSelected block: &c" + blockName));
    }

    static boolean isWallOrFence(String materialString) {
        if (materialString == null) return false;
        String upper = materialString.toUpperCase().split(":")[0];
        switch (upper) {
            case "COBBLESTONE_WALL": case "COBBLE_WALL":
            case "FENCE": case "SPRUCE_FENCE": case "BIRCH_FENCE":
            case "JUNGLE_FENCE": case "DARK_OAK_FENCE": case "ACACIA_FENCE":
            case "NETHER_FENCE": case "NETHER_BRICK_FENCE":
            case "FENCE_GATE": case "SPRUCE_FENCE_GATE": case "BIRCH_FENCE_GATE":
            case "JUNGLE_FENCE_GATE": case "DARK_OAK_FENCE_GATE": case "ACACIA_FENCE_GATE":
            case "THIN_GLASS": case "STAINED_GLASS_PANE": case "IRON_FENCE":
                return true;
            default:
                return false;
        }
    }
}
