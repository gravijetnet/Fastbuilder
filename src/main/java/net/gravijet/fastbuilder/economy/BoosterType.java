package net.gravijet.fastbuilder.economy;

/**
 * Immutable descriptor for one purchasable booster type.
 *
 * Instances are built from {@code booster-types} in config.yml and cached by
 * {@link net.gravijet.fastbuilder.config.ConfigManager}.
 */
public class BoosterType {

    public final String id;
    public final String displayName;
    public final String description;
    public final double multiplier;
    public final int    durationMinutes;
    public final int    price;
    /** 1.8.8 POTION durability value — controls the rendered colour. */
    public final short  potionData;

    public BoosterType(String id, String displayName, String description,
                       double multiplier, int durationMinutes, int price, short potionData) {
        this.id              = id;
        this.displayName     = displayName;
        this.description     = description;
        this.multiplier      = multiplier;
        this.durationMinutes = durationMinutes;
        this.price           = price;
        this.potionData      = potionData;
    }

    /** Format multiplier as "1.5x", "2x", etc. */
    public String formatMultiplier() {
        if (multiplier == Math.floor(multiplier)) return (int) multiplier + "x";
        return String.format("%.1fx", multiplier);
    }
}
