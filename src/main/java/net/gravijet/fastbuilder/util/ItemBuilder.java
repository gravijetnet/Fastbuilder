package net.gravijet.fastbuilder.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ItemBuilder {

    private Material material;
    /**
     * Stored as {@code short} to accommodate POTION durability values (e.g. 8194 for Speed)
     * which exceed the byte range.  For normal block/item data values the lower 8 bits are
     * sufficient and the cast in the constructor is lossless.
     */
    private short data;
    private int amount = 1;
    private String name;
    private List<String> lore;

    public ItemBuilder(Material material) {
        this.material = material;
        this.data = 0;
    }

    /** Constructs with a standard block-data byte (values 0–15). */
    public ItemBuilder(Material material, byte data) {
        this.material = material;
        this.data = data;
    }

    /** Constructs with a full short durability/data value (for potions, etc.). */
    public ItemBuilder(Material material, short data) {
        this.material = material;
        this.data = data;
    }

    /**
     * Parse a "MATERIAL:DATA" string into an ItemBuilder.
     */
    public static ItemBuilder fromString(String materialString) {
        String[] parts = materialString.split(":");
        Material mat = Material.matchMaterial(parts[0]);
        if (mat == null) mat = Material.STONE;
        byte d = 0;
        if (parts.length > 1) {
            try {
                d = Byte.parseByte(parts[1]);
            } catch (NumberFormatException ignored) {}
        }
        return new ItemBuilder(mat, d);
    }

    public ItemBuilder material(Material material) {
        this.material = material;
        return this;
    }

    /** Set block-data byte (values 0–15, e.g. wool colour, stained clay). */
    public ItemBuilder data(byte data) {
        this.data = data;
        return this;
    }

    /** Set full durability value as short (use for potions: 8193–8206 range). */
    public ItemBuilder data(short data) {
        this.data = data;
        return this;
    }

    public ItemBuilder amount(int amount) {
        this.amount = amount;
        return this;
    }

    public ItemBuilder name(String name) {
        this.name = ColorUtil.translate(name);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        this.lore = new ArrayList<>();
        for (String line : lines) {
            this.lore.add(ColorUtil.translate(line));
        }
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        this.lore = new ArrayList<>();
        for (String line : lines) {
            this.lore.add(ColorUtil.translate(line));
        }
        return this;
    }

    @SuppressWarnings("deprecation")
    public ItemStack build() {
        ItemStack item = new ItemStack(material, amount, (short) data);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(name);
            }
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
