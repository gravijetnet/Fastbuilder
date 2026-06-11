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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ShopGui {

    static final String SHOP_PREFIX             = "Shop";
    static final String PICKAXE_PREFIX          = "Pickaxe Shop";
    static final String ANIMATION_PREFIX        = "Reset Animations";
    static final String DEATH_SOUND_PREFIX      = "Death Sounds";
    static final String DESIGN_PREFIX           = "Island Designs";

    private final FastBuilder plugin;
    private final Map<UUID, Integer> pickaxePages = new HashMap<>();
    private final Map<UUID, Integer> animationPages = new HashMap<>();

    public ShopGui(FastBuilder plugin) {
        this.plugin = plugin;
    }

    // ===== Main Shop =====

    public void open(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(guis.getString("shop.name", "Shop"));
        int rawSize = guis.getInt("shop.max-slots", 45);
        int size = Math.max(9, Math.min(54, ((rawSize + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(null, size, title);

        PlayerData shopPData = plugin.getPlayerManager().getCachedData(player.getUniqueId());

        if (plugin.getConfigManager().isShopCategoryVisible(player, "blocks")) {
            int ownedBlocks = shopPData != null ? shopPData.getPurchasedBlockCount() : 0;
            String status = player.hasPermission("fastbuilder.blocks.*")
                    ? "&aAll unlocked" : (ownedBlocks > 0 ? "&a" + ownedBlocks + " unlocked" : "&7None unlocked");
            inv.setItem(guis.getInt("shop.blocks-slot", 9),
                    buildCategoryItem(guis, "shop.items.blocks", "SANDSTONE:0", "&c&lBlocks",
                            new String[]{"&7Click to browse building blocks", status, "", "&aClick to browse"}));
        }
        if (plugin.getConfigManager().isShopCategoryVisible(player, "boosters")) {
            boolean hasBooster = shopPData != null && shopPData.getBoosterExpiry() > System.currentTimeMillis();
            boolean hasOwned   = shopPData != null && shopPData.hasAnyBoosters();
            String boosterName = hasBooster ? "&6Boosters &a(Active)" : "&6Boosters";
            String[] boosterLore = hasBooster
                    ? new String[]{"&7Multiplies all coin rewards",
                                   "&7Active: &a" + plugin.getBoosterManager().formatRemaining(player.getUniqueId()),
                                   hasOwned ? "&7Owned: &f" + BoosterGui.countOwned(shopPData) + " booster(s)" : "",
                                   "", "&aClick to open Booster Shop"}
                    : new String[]{"&7Multiplies all coin rewards", "&7No active booster",
                                   hasOwned ? "&7Owned: &f" + BoosterGui.countOwned(shopPData) + " booster(s)" : "",
                                   "", "&aClick to open Booster Shop"};
            inv.setItem(guis.getInt("shop.boosters-slot", 10),
                    buildCategoryItem(guis, "shop.items.boosters", "POTION:0", boosterName, boosterLore));
        }
        if (plugin.getConfigManager().isShopCategoryVisible(player, "pickaxes")) {
            boolean ocpUnlocked = player.hasPermission("fastbuilder.cosmetic.oneclickpick")
                    || (shopPData != null && shopPData.hasOneClickPick());
            String pickaxeStatus = ocpUnlocked ? "&aOne-Click Pick unlocked" : "&7One-Click Pick locked";
            inv.setItem(guis.getInt("shop.pickaxes-slot", 11),
                    buildCategoryItem(guis, "shop.items.pickaxes", "DIAMOND_PICKAXE:0", "&bPickaxe Shop",
                            new String[]{"&7Click to browse pickaxes and tools", pickaxeStatus, "", "&aClick to browse"}));
        }
        if (plugin.getConfigManager().isShopCategoryVisible(player, "animations")) {
            int ownedAnims = shopPData != null ? shopPData.getPurchasedAnimationCount() : 0;
            String animStatus = player.hasPermission("fastbuilder.cosmetic.animations.*")
                    ? "&aAll unlocked" : (ownedAnims > 0 ? "&a" + ownedAnims + " unlocked" : "&7None unlocked");
            inv.setItem(guis.getInt("shop.animations-slot", 15),
                    buildCategoryItem(guis, "shop.items.animations", "FIREWORK:0", "&c&lReset Animations",
                            new String[]{"&7Click to browse reset animations", animStatus, "", "&aClick to browse"}));
        }
        if (plugin.getConfigManager().isShopCategoryVisible(player, "sounds")) {
            int ownedSounds = shopPData != null ? shopPData.getPurchasedSoundCount() : 0;
            String soundStatus = player.hasPermission("fastbuilder.cosmetic.sounds.*")
                    ? "&aAll unlocked" : (ownedSounds > 0 ? "&a" + ownedSounds + " unlocked" : "&7None unlocked");
            inv.setItem(guis.getInt("shop.death-sounds-slot", 17),
                    buildCategoryItem(guis, "shop.items.sounds", "NOTE_BLOCK:0", "&6Death Sounds",
                            new String[]{"&7Click to browse death sounds", soundStatus, "", "&aClick to browse"}));
        }
        if (plugin.getConfigManager().isShopCategoryVisible(player, "designs")) {
            boolean hasDesigns = false;
            String currentMap = shopPData != null ? shopPData.getLastMap() : null;
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
                    buildCategoryItem(guis, configPath, "EMERALD_BLOCK:0", defaultName, defaultLore));
        }

        player.openInventory(inv);
    }

    void handleShopClick(InventoryClickEvent event, BoosterGui boosterGui, BlockSelectorGui blockGui) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();

        player.closeInventory();

        if (slot == guis.getInt("shop.blocks-slot", 9)) {
            blockGui.open(player, 1);
        } else if (slot == guis.getInt("shop.boosters-slot", 10)) {
            boosterGui.openHub(player);
        } else if (slot == guis.getInt("shop.pickaxes-slot", 11)) {
            openPickaxeSelector(player, 1);
        } else if (slot == guis.getInt("shop.designs-slot", 13)) {
            openDesignSelector(player);
        } else if (slot == guis.getInt("shop.animations-slot", 15)) {
            openAnimationSelector(player);
        } else if (slot == guis.getInt("shop.death-sounds-slot", 17)) {
            openDeathSoundSelector(player);
        }
    }

    // ===== Pickaxe Selector =====

    public void openPickaxeSelector(Player player, int page) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int maxSlots = guis.getInt("pickaxe-selector.max-slots", 45);
        String titleTemplate = guis.getString("pickaxe-selector.name", "Pickaxe Shop - %page%");

        ConfigurationSection pageSection = guis.getConfigurationSection("pickaxe-selector-slots." + page);
        if (pageSection == null) {
            player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix() + "&cNo tools on this page."));
            return;
        }

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

        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 7).name(" ").build();
        for (int i = 0; i < maxSlots; i++) inv.setItem(i, filler);

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        String purchasedStatus   = guis.getString("pickaxe-status.purchased",     "&aYou already own this pickaxe!");
        String notPurchasedStatus = guis.getString("pickaxe-status.not-purchased", "&cYou don't own this pickaxe yet!");

        for (String slotKey : pageSection.getKeys(false)) {
            try {
                int slotIndex = Integer.parseInt(slotKey) - 1;
                if (slotIndex < 0 || slotIndex >= maxSlots - 9) continue;

                String name  = pageSection.getString(slotKey + ".name",     "Tool");
                String mat   = pageSection.getString(slotKey + ".material", "STONE_PICKAXE:0");
                int    price = pageSection.getInt(slotKey    + ".price",    0);

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
                inv.setItem(slotIndex, ItemBuilder.fromString(mat).name("&f" + name).lore(lore).build());
            } catch (NumberFormatException ignored) {}
        }

        int ocpPage = guis.getInt("one-click-pick.page", 1);
        if (page == ocpPage) {
            boolean hasOcp       = data != null && data.hasOneClickPick();
            boolean ocpPurchased = (data != null && data.hasPurchasedBlock("cosmetic:one_click_pick"))
                    || player.hasPermission("fastbuilder.cosmetic.oneclickpick")
                    || player.hasPermission("fastbuilder.blocks.*");
            int    ocpPrice = guis.getInt("one-click-pick.price",  5000);
            int    ocpSlot  = guis.getInt("one-click-pick.slot",   23) - 1;
            String ocpName  = guis.getString("one-click-pick.name", "&bOne-Click Pick");
            String ocpStatus = ocpPurchased
                    ? (hasOcp ? "&a&lACTIVE" : "&7Owned &8» &aClick to enable")
                    : "&cNot owned &8» &aClick to buy";

            List<String> ocpLoreTemplate = guis.getStringList("one-click-pick.lore");
            List<String> ocpLore = new ArrayList<>();
            for (String line : ocpLoreTemplate) {
                ocpLore.add(line.replace("%price%", ocpPurchased ? "Owned" : String.valueOf(ocpPrice)));
            }
            ocpLore.add(ColorUtil.translate(ocpStatus));
            inv.setItem(ocpSlot, new ItemBuilder(Material.DIAMOND_AXE).name(ocpName).lore(ocpLore.toArray(new String[0])).build());
        }

        FileConfiguration itemsConfig = plugin.getConfigManager().getItemsConfig();
        String backMat  = itemsConfig.getString("change-page.back-to-shop.material", "BARRIER:0");
        String backName = itemsConfig.getString("change-page.back-to-shop.name",     "&cBack to Shop");
        inv.setItem(maxSlots - 5, ItemBuilder.fromString(backMat).name(backName).build());

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

    void handlePickaxeSelectorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int maxSlots = guis.getInt("pickaxe-selector.max-slots", 45);

        Integer currentPage = pickaxePages.get(player.getUniqueId());
        if (currentPage == null) currentPage = 1;

        if (slot == maxSlots - 5) { openPickaxeSelector(player, 1); open(player); return; }
        if (slot == maxSlots - 9 && currentPage > 1) { openPickaxeSelector(player, currentPage - 1); return; }

        int maxPage = 1;
        ConfigurationSection allPagesSection = guis.getConfigurationSection("pickaxe-selector-slots");
        if (allPagesSection != null) {
            for (String key : allPagesSection.getKeys(false)) {
                try { int p = Integer.parseInt(key); if (p > maxPage) maxPage = p; } catch (NumberFormatException ignored) {}
            }
        }
        if (slot == maxSlots - 1 && currentPage < maxPage) { openPickaxeSelector(player, currentPage + 1); return; }

        int ocpPage = guis.getInt("one-click-pick.page", 1);
        int ocpSlot = guis.getInt("one-click-pick.slot", 23) - 1;
        if (currentPage == ocpPage && slot == ocpSlot) {
            handleOneClickPickClick(player);
            return;
        }

        ConfigurationSection pageSection = guis.getConfigurationSection("pickaxe-selector-slots." + currentPage);
        if (pageSection == null) return;

        int itemKey = slot + 1;
        if (!pageSection.isConfigurationSection(String.valueOf(itemKey))) return;

        String mat   = pageSection.getString(itemKey + ".material", "STONE_PICKAXE:0");
        int    price = pageSection.getInt(itemKey    + ".price",    0);
        String name  = pageSection.getString(itemKey + ".name",     "Tool");

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        String pickPerm = "fastbuilder.pickaxe." + mat.toLowerCase().replace(":", ".");
        boolean owned = price == 0 || data.hasPurchasedBlock("pickaxe:" + mat)
                || player.hasPermission("fastbuilder.blocks.*")
                || player.hasPermission(pickPerm);

        if (!owned) {
            if (data.removeCoins(price)) {
                data.purchaseBlock("pickaxe:" + mat);
                data.setSelectedPickaxe(mat);
                if (data.hasOneClickPick()) {
                    data.setOneClickPick(false);
                }
                plugin.getPlayerManager().savePlayerData(player.getUniqueId());
                player.closeInventory();
                if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
                net.gravijet.fastbuilder.util.Messages.send(player, "shop-unlocked",
                        "item", name, "price", String.valueOf(price));
            } else {
                net.gravijet.fastbuilder.util.Messages.send(player, "shop-not-enough-coins",
                        "price", String.valueOf(price));
            }
            return;
        }

        data.setSelectedPickaxe(mat);
        if (data.hasOneClickPick()) {
            data.setOneClickPick(false);
        }
        plugin.getPlayerManager().savePlayerData(player.getUniqueId());
        player.closeInventory();
        if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
        net.gravijet.fastbuilder.util.Messages.send(player, "shop-pickaxe-selected", "item", name);
    }

    private void handleOneClickPickClick(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int price = guis.getInt("one-click-pick.price", 5000);
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        boolean purchased = data.hasPurchasedBlock("cosmetic:one_click_pick")
                || player.hasPermission("fastbuilder.cosmetic.oneclickpick");
        if (!purchased) {
            if (data.removeCoins(price)) {
                data.purchaseBlock("cosmetic:one_click_pick");
                data.setOneClickPick(true);
                plugin.getPlayerManager().savePlayerData(player.getUniqueId());
                if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
                net.gravijet.fastbuilder.util.Messages.send(player, "one-click-pick-unlocked",
                        "price", String.valueOf(price));
            } else {
                net.gravijet.fastbuilder.util.Messages.send(player, "shop-not-enough-coins",
                        "price", String.valueOf(price));
            }
        } else {
            data.setOneClickPick(!data.hasOneClickPick());
            plugin.getPlayerManager().savePlayerData(player.getUniqueId());
            if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
            net.gravijet.fastbuilder.util.Messages.send(player,
                    data.hasOneClickPick() ? "one-click-pick-on" : "one-click-pick-off");
        }
        int ocpPage = plugin.getConfigManager().getGuisConfig().getInt("one-click-pick.page", 1);
        openPickaxeSelector(player, ocpPage);
    }

    // ===== Animation Selector =====

    public void openAnimationSelector(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(guis.getString("animation-selector.name", ANIMATION_PREFIX));
        int maxSlots = guis.getInt("animation-selector.max-slots", 27);
        Inventory inv = Bukkit.createInventory(null, maxSlots, title);

        ConfigurationSection slotsSection = guis.getConfigurationSection("animation-selector-slots");
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
                boolean owned = price == 0 || (data != null && data.hasPurchasedBlock("anim:" + animId))
                        || player.hasPermission("fastbuilder.cosmetic.animations.*")
                        || player.hasPermission("fastbuilder.animation." + animId.toLowerCase());
                boolean selected = animId.equalsIgnoreCase(currentAnim);
                boolean isNoneOption = animId.equalsIgnoreCase("NONE");
                if (isNoneOption) mat = "BEDROCK:0";

                List<String> loreTemplate = slotsSection.getStringList(slotKey + ".lore");
                List<String> lore = new ArrayList<>();
                for (String line : loreTemplate) lore.add(line.replace("%price%", price == 0 ? "Free" : String.valueOf(price)));
                if (selected) {
                    lore.add(ColorUtil.translate("&a&lCurrently selected"));
                } else if (owned && price > 0) {
                    lore.add(ColorUtil.translate("&aAlready owned &8» &7Click to select"));
                } else if (!owned) {
                    lore.add(ColorUtil.translate("&cNot purchased"));
                }

                ItemStack item = ItemBuilder.fromString(mat).name("&r" + name).lore(lore.toArray(new String[0])).build();
                boolean canGlow = !mat.toUpperCase().startsWith("CHEST") && !isNoneOption;
                if (selected && canGlow) {
                    org.bukkit.inventory.meta.ItemMeta im = item.getItemMeta();
                    if (im != null) { im.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true); item.setItemMeta(im); }
                }
                inv.setItem(slotIndex, item);
            } catch (NumberFormatException ignored) {}
        }

        FileConfiguration itemsConfig = plugin.getConfigManager().getItemsConfig();
        String backMat  = itemsConfig.getString("change-page.back-to-shop.material", "BARRIER:0");
        String backName = itemsConfig.getString("change-page.back-to-shop.name", "&cBack to Shop");
        inv.setItem(maxSlots - 5, ItemBuilder.fromString(backMat).name(backName).build());
        player.openInventory(inv);
    }

    void handleAnimationSelectorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int maxSlots = event.getInventory().getSize();

        if (slot == maxSlots - 5) { player.closeInventory(); open(player); return; }

        ConfigurationSection slotsSection = guis.getConfigurationSection("animation-selector-slots");
        if (slotsSection == null) return;

        int configKey = slot + 1;
        if (!slotsSection.isConfigurationSection(String.valueOf(configKey))) return;

        String animId = slotsSection.getString(configKey + ".animation", "NONE");
        int price = slotsSection.getInt(configKey + ".price", 0);
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        boolean owned = price == 0 || data.hasPurchasedBlock("anim:" + animId)
                || player.hasPermission("fastbuilder.cosmetic.animations.*")
                || player.hasPermission("fastbuilder.animation." + animId.toLowerCase());
        if (!owned) {
            if (data.removeCoins(price)) {
                data.purchaseBlock("anim:" + animId);
                data.setSelectedAnimation(animId);
                plugin.getPlayerManager().savePlayerData(player.getUniqueId());
                player.closeInventory();
                if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
                String aName = slotsSection.getString(configKey + ".name", "Animation");
                net.gravijet.fastbuilder.util.Messages.send(player, "shop-unlocked",
                        "item", aName, "price", String.valueOf(price));
            } else {
                net.gravijet.fastbuilder.util.Messages.send(player, "shop-not-enough-coins",
                        "price", String.valueOf(price));
            }
            return;
        }

        data.setSelectedAnimation(animId);
        plugin.getPlayerManager().savePlayerData(player.getUniqueId());
        player.closeInventory();
        if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
        String aName = slotsSection.getString(configKey + ".name", "Animation");
        net.gravijet.fastbuilder.util.Messages.send(player, "shop-animation-selected", "item", aName);
    }

    // ===== Death Sound Selector =====

    public void openDeathSoundSelector(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(guis.getString("death-sound-selector.name", "Death Sounds"));
        int maxSlots = guis.getInt("death-sound-selector.max-slots", 27);
        ConfigurationSection soundsSection = guis.getConfigurationSection("death-sound-selector-slots");
        if (soundsSection != null) {
            int slotsNeeded = soundsSection.getKeys(false).size() + 9;
            if (slotsNeeded > maxSlots) maxSlots = (int) Math.ceil(slotsNeeded / 9.0) * 9;
        }
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
                boolean owned = price == 0 || (data != null && data.hasPurchasedBlock("sound:" + soundId))
                        || player.hasPermission("fastbuilder.cosmetic.sounds.*")
                        || player.hasPermission("fastbuilder.sound." + soundId.toLowerCase());
                boolean selected = soundId.equalsIgnoreCase(currentSound);
                boolean isNoneOption = soundId.equalsIgnoreCase("NONE");
                if (isNoneOption) mat = "BEDROCK:0";

                List<String> loreTemplate = soundsSection.getStringList(slotKey + ".lore");
                List<String> lore = new ArrayList<>();
                for (String line : loreTemplate) lore.add(line.replace("%price%", price == 0 ? "Free" : String.valueOf(price)));
                if (selected) {
                    lore.add(ColorUtil.translate("&a&lCurrently selected"));
                } else if (owned && price > 0) {
                    lore.add(ColorUtil.translate("&aAlready owned &8» &7Click to select"));
                } else if (!owned) {
                    lore.add(ColorUtil.translate("&cNot purchased"));
                }

                ItemStack it = ItemBuilder.fromString(mat).name("&r" + name).lore(lore.toArray(new String[0])).build();
                boolean canGlow = !mat.toUpperCase().startsWith("FIREWORK") && !isNoneOption;
                if (selected && canGlow) {
                    org.bukkit.inventory.meta.ItemMeta im = it.getItemMeta();
                    if (im != null) { im.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true); it.setItemMeta(im); }
                }
                inv.setItem(slotIndex, it);
            } catch (NumberFormatException ignored) {}
        }

        FileConfiguration soundItemsConfig = plugin.getConfigManager().getItemsConfig();
        String soundBackMat  = soundItemsConfig.getString("change-page.back-to-shop.material", "BARRIER:0");
        String soundBackName = soundItemsConfig.getString("change-page.back-to-shop.name", "&cBack to Shop");
        inv.setItem(maxSlots - 5, ItemBuilder.fromString(soundBackMat).name(soundBackName).build());
        player.openInventory(inv);
    }

    void handleDeathSoundSelectorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int maxSlots = event.getInventory().getSize();

        if (slot == maxSlots - 5) { player.closeInventory(); open(player); return; }

        ConfigurationSection slotsSection = guis.getConfigurationSection("death-sound-selector-slots");
        if (slotsSection == null) return;

        int configKey = slot + 1;
        if (!slotsSection.isConfigurationSection(String.valueOf(configKey))) return;

        String soundId = slotsSection.getString(configKey + ".sound", "NONE");
        int price = slotsSection.getInt(configKey + ".price", 0);
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        boolean owned = price == 0 || data.hasPurchasedBlock("sound:" + soundId)
                || player.hasPermission("fastbuilder.cosmetic.sounds.*")
                || player.hasPermission("fastbuilder.sound." + soundId.toLowerCase());
        if (!owned) {
            if (data.removeCoins(price)) {
                data.purchaseBlock("sound:" + soundId);
                data.setSelectedDeathSound(soundId);
                plugin.getPlayerManager().savePlayerData(player.getUniqueId());
                player.closeInventory();
                if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
                String sName = slotsSection.getString(configKey + ".name", "Sound");
                net.gravijet.fastbuilder.util.Messages.send(player, "shop-unlocked",
                        "item", sName, "price", String.valueOf(price));
            } else {
                net.gravijet.fastbuilder.util.Messages.send(player, "shop-not-enough-coins",
                        "price", String.valueOf(price));
            }
            return;
        }

        data.setSelectedDeathSound(soundId);
        plugin.getPlayerManager().savePlayerData(player.getUniqueId());
        player.closeInventory();
        if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
        String sName = slotsSection.getString(configKey + ".name", "Sound");
        net.gravijet.fastbuilder.util.Messages.send(player, "shop-death-sound-selected", "item", sName);
    }

    // ===== Design Selector =====

    public void openDesignSelector(Player player) {
        PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (pData == null) return;
        String mapName = pData.getLastMap();
        if (mapName == null) return;
        MapData map = plugin.getMapManager().getMap(mapName);
        if (map == null) return;

        List<String> templates = map.getTemplatesForMode();
        String title = ColorUtil.translate("&c&lIsland Designs &7- &f" + map.getName());
        Inventory inv = Bukkit.createInventory(null, 27, title);

        String selectedDesign = pData.getSelectedDesign(mapName);
        String defaultKey = map.getTemplateFile();
        if (selectedDesign == null) selectedDesign = defaultKey;

        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int designPrice = guis.getInt("island-designs.default-price", 0);
        String loreSelected = guis.getString("island-designs.lore-selected", "&a&l» Currently selected");
        String loreOwned    = guis.getString("island-designs.lore-owned",    "&aAlready owned &8- &7Click to select");
        String loreUnlocked = guis.getString("island-designs.lore-unlocked", "&aClick to select this design");
        String loreLocked   = guis.getString("island-designs.lore-locked",   "&cLocked &8- &e%price% coins to unlock");
        String loreNoCoins  = guis.getString("island-designs.lore-cannot-afford", "&cNot enough coins &7(&f%coins% / %price%&7)");

        int maxContentSlots = 18;
        for (int i = 0; i < templates.size() && i < maxContentSlots; i++) {
            String key = templates.get(i);
            boolean isDefault = key.equals(defaultKey);
            boolean selected = key.equalsIgnoreCase(selectedDesign);
            boolean hasPermission = player.hasPermission("fastbuilder.design.*")
                    || player.hasPermission("fastbuilder.design." + key.toLowerCase());
            boolean unlocked = isDefault || designPrice == 0 || pData.hasPurchasedDesign(key)
                    || hasPermission;
            String displayName = "&a" + (isDefault ? "Default Design" : key);

            List<String> lore = new ArrayList<>();
            if (selected) {
                lore.add(ColorUtil.translate(loreSelected));
            } else if (unlocked && !isDefault && designPrice > 0) {
                lore.add(ColorUtil.translate(loreOwned));
            } else if (unlocked) {
                lore.add(ColorUtil.translate(loreUnlocked));
            } else {
                lore.add(ColorUtil.translate(loreLocked.replace("%price%", String.valueOf(designPrice))));
                if (pData.getCoins() < designPrice) {
                    lore.add(ColorUtil.translate(loreNoCoins.replace("%coins%", String.valueOf(pData.getCoins())).replace("%price%", String.valueOf(designPrice))));
                }
            }

            inv.setItem(i, new ItemBuilder(Material.PAPER).name(displayName).lore(lore.toArray(new String[0])).build());
        }

        FileConfiguration designItemsConfig = plugin.getConfigManager().getItemsConfig();
        String designBackMat  = designItemsConfig.getString("change-page.back-to-shop.material", "BARRIER:0");
        String designBackName = designItemsConfig.getString("change-page.back-to-shop.name", "&cBack to Shop");
        inv.setItem(22, ItemBuilder.fromString(designBackMat).name(designBackName).build());
        player.openInventory(inv);
    }

    void handleDesignSelectorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        String displayName = ColorUtil.strip(item.getItemMeta().getDisplayName());
        if (displayName.equals("Back to Shop")) { player.closeInventory(); open(player); return; }

        PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (pData == null) return;
        String mapName = pData.getLastMap();
        if (mapName == null) return;
        MapData map = plugin.getMapManager().getMap(mapName);
        if (map == null) return;

        int clickedSlot = event.getSlot();
        List<String> templates = map.getTemplatesForMode();
        if (clickedSlot < 0 || clickedSlot >= templates.size()) return;
        String templateKey = templates.get(clickedSlot);

        String currentlySelected = pData.getSelectedDesign(mapName);
        String defaultKey2 = map.getTemplateFile();
        if (currentlySelected == null) currentlySelected = defaultKey2;
        if (templateKey.equalsIgnoreCase(currentlySelected)) {
            net.gravijet.fastbuilder.util.Messages.send(player, "design-already-active");
            return;
        }

        String defaultKey = map.getTemplateFile();
        boolean isDefault = templateKey.equals(defaultKey);
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        int designPrice = guis.getInt("island-designs.default-price", 0);
        boolean unlocked = isDefault || designPrice == 0 || pData.hasPurchasedDesign(templateKey)
                || player.hasPermission("fastbuilder.design.*")
                || player.hasPermission("fastbuilder.design." + templateKey.toLowerCase());

        if (!unlocked) {
            if (pData.removeCoins(designPrice)) {
                pData.purchaseDesign(templateKey);
                plugin.getPlayerManager().savePlayerData(player.getUniqueId());
                net.gravijet.fastbuilder.util.Messages.send(player, "design-unlocked",
                        "design", templateKey, "price", String.valueOf(designPrice));
            } else {
                net.gravijet.fastbuilder.util.Messages.send(player, "shop-not-enough-coins",
                        "price", String.valueOf(designPrice));
                openDesignSelector(player);
                return;
            }
        }

        pData.setSelectedDesign(mapName, templateKey);
        player.closeInventory();

        net.gravijet.fastbuilder.gameplay.RunSession designSession =
                plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
        if (designSession != null) {
            int islandIdx = designSession.getIslandIndex();
            plugin.getGameplayManager().clearAllPlacedBlocks(player.getUniqueId());
            designSession.reset();

            if (templateKey.equals(map.getTemplateFile())) {
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
                plugin.getGameplayManager().applyPlayerDesign(player, map, islandIdx);
            }
            if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);
        }

        net.gravijet.fastbuilder.util.Messages.send(player, "design-applied", "design", templateKey);
    }

    private ItemStack buildCategoryItem(FileConfiguration guis, String path,
                                         String defaultMat, String defaultName, String[] defaultLore) {
        String mat  = guis.getString(path + ".material", defaultMat);
        String name = guis.getString(path + ".name",     defaultName);
        List<String> loreList = guis.getStringList(path + ".lore");
        String[] lore = loreList.isEmpty() ? defaultLore : loreList.toArray(new String[0]);
        return ItemBuilder.fromString(mat).name(name).lore(lore).build();
    }
}
