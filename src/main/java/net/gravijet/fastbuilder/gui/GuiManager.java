package net.gravijet.fastbuilder.gui;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.IslandInstance;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Handles all plugin GUIs: Block Selector, Map Selector, Island Selector, Settings.
 * GUI layouts are defined in guis.yml.
 */
public class GuiManager implements Listener {

    private final FastBuilder plugin;

    // GUI title prefixes for identification
    private static final String BLOCK_SELECTOR_PREFIX = "Block Selector";
    private static final String SETTINGS_PREFIX = "Settings";
    private static final String ISLAND_SELECTOR_PREFIX = "Island Selector";
    private static final String CONFIRM_PREFIX = "Confirm";

    public GuiManager(FastBuilder plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Open the Island Selector GUI for a player on a specific map.
     */
    public void openIslandSelector(Player player, MapData map) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(guis.getString("island-selector", "Island Selector"));
        int size = Math.min(54, ((map.getScale() / 9) + 1) * 9);
        if (size < 9) size = 9;

        Inventory inv = Bukkit.createInventory(null, size, title);

        List<IslandInstance> islands = plugin.getMapManager().getIslands(map.getName());
        for (int i = 0; i < islands.size() && i < size; i++) {
            IslandInstance island = islands.get(i);
            ItemStack item;

            if (island.isOccupied()) {
                // Show occupied island with player head
                item = new ItemBuilder(Material.SKULL_ITEM, (byte) 3)
                        .name("&c#" + (i + 1) + " &7- &f" + island.getOccupantName())
                        .lore("&cOccupied")
                        .build();
            } else {
                // Show empty island with number
                item = new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 5)
                        .name("&a#" + (i + 1))
                        .lore("&aClick to join")
                        .build();
            }

            inv.setItem(i, item);
        }

        player.openInventory(inv);
    }

    /**
     * Open the Settings GUI.
     */
    public void openSettings(Player player) {
        FileConfiguration guis = plugin.getConfigManager().getGuisConfig();
        String title = ColorUtil.translate(guis.getString("settings.name", "Settings"));
        int slots = guis.getInt("settings.max-slots", 27);

        Inventory inv = Bukkit.createInventory(null, slots, title);

        ConfigurationSection settingsSlots = guis.getConfigurationSection("settings-gui-slots");
        if (settingsSlots != null) {
            for (String slotKey : settingsSlots.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotKey);
                    String name = settingsSlots.getString(slotKey + ".name", "");
                    String mat = settingsSlots.getString(slotKey + ".material", "STONE:0");

                    ItemStack item = ItemBuilder.fromString(mat)
                            .name(name)
                            .lore(settingsSlots.getStringList(slotKey + ".lore"))
                            .build();

                    inv.setItem(slot, item);
                } catch (NumberFormatException ignored) {}
            }
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        String title = event.getInventory().getTitle();
        if (title == null) return;

        String stripped = ColorUtil.strip(title);

        if (stripped.startsWith(ISLAND_SELECTOR_PREFIX)) {
            event.setCancelled(true);
            handleIslandSelectorClick(event);
        } else if (stripped.startsWith(BLOCK_SELECTOR_PREFIX)) {
            event.setCancelled(true);
            // Block selector click handling will be implemented in the gameplay phase
        } else if (stripped.startsWith(SETTINGS_PREFIX)) {
            event.setCancelled(true);
            // Settings click handling will be implemented in the gameplay phase
        } else if (stripped.startsWith(CONFIRM_PREFIX)) {
            event.setCancelled(true);
            // Confirm dialog handling will be implemented in the gameplay phase
        }
    }

    private void handleIslandSelectorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        // Determine which map the player is currently on
        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null || data.getLastMap() == null) return;

        MapData map = plugin.getMapManager().getMap(data.getLastMap());
        if (map == null) return;

        List<IslandInstance> islands = plugin.getMapManager().getIslands(map.getName());
        if (slot < 0 || slot >= islands.size()) return;

        IslandInstance island = islands.get(slot);
        if (island.isOccupied()) {
            String raw = plugin.getConfigManager().getMessage("island-already-occupied");
            raw = raw.replace("%island_player%", island.getOccupantName());
            raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
            player.sendMessage(ColorUtil.translate(raw));
            player.closeInventory();
            return;
        }

        // Free current island, assign new one
        plugin.getMapManager().freeIsland(map.getName(), player.getUniqueId());
        plugin.getMapManager().assignIsland(map.getName(), slot, player.getUniqueId(), player.getName());

        data.setLastIsland(slot);
        player.teleport(map.getIslandSpawn(slot));
        player.closeInventory();

        String raw = plugin.getConfigManager().getMessage("island-joined");
        raw = raw.replace("%island_number%", String.valueOf(slot + 1));
        raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
    }
}
