package net.gravijet.fastbuilder.player;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent data for a single player.
 * Stored as plugins/FastBuilder/playerdata/<uuid>.yml
 */
public class PlayerData {

    private final UUID uuid;
    private String name;
    private int coins;
    private String lastMap;
    private int lastIsland;
    private String selectedBlock;

    // Per-map statistics
    private final Map<String, MapStats> mapStats = new HashMap<>();

    // Purchased blocks
    private final Set<String> purchasedBlocks = new HashSet<>();

    // Auto-refill perk
    private boolean autoRefill = false;

    // Infinite blocks perk (purchasable for 1000 coins)
    private boolean infiniteBlocksUnlocked = false;
    private boolean infiniteBlocks = false;

    // Cosmetic selections
    private String selectedPickaxe = "DIAMOND_PICKAXE:0";
    private String selectedAnimation = "NONE";
    private String selectedDeathSound = "NONE";

    // One-Click Pick cosmetic: breaks blocks in a single click
    private boolean oneClickPickEnabled = false;

    // Per-map island design selection (mapName -> templateFile key)
    private final Map<String, String> selectedDesigns = new HashMap<>();

    // Favorited replay file names (protected from deletion)
    private final Set<String> favoriteReplays = new HashSet<>();

    // Tracks which rank names (per map) the player has already been notified about (one-time messages)
    private final Map<String, Set<String>> notifiedRanks = new HashMap<>();

    // Per-map custom run length preference (session-only, NOT persisted to disk).
    // Automatically resets to 0 (base distance) when the player logs out or changes maps.
    private final Map<String, Integer> customLengths = new HashMap<>();

    // Per-map SAVED custom run length (persisted). Restored when player re-enters a custom length map.
    private final Map<String, Integer> savedCustomLengths = new HashMap<>();

    // Per-map custom Y offset for end island (session-only, NOT persisted to disk).
    // Adjusts the end island vertically via the Custom Length sub-menu.
    private final Map<String, Integer> customLengthYOffsets = new HashMap<>();

    // Per-map custom length toggle (session-only, NOT persisted to disk).
    private final Map<String, Boolean> customLengthToggles = new HashMap<>();

    // Per-map best infinite-mode distance (blocks placed before dying). Persisted to disk.
    private final Map<String, Integer> infiniteDistances = new HashMap<>();

    // Per-map time (ms) achieved on the best infinite-mode run (same run as the best distance). Persisted.
    private final Map<String, Long> infiniteDistanceTimes = new HashMap<>();

    // Per-map active finish zone override derived from the selected island design profile.
    // Array: {finishMinX, finishMinY, finishMinZ, finishMaxX, finishMaxY, finishMaxZ}
    // Session-only — cleared when the player leaves or resets their design to default.
    private final Map<String, int[]> activeFinishZoneOverrides = new HashMap<>();

    // Globally purchased island design template keys (persisted).
    // If a template key appears in multiple maps, buying it once grants access everywhere.
    private final Set<String> purchasedDesigns = new HashSet<>();

    // Coin booster (persisted so it survives restarts)
    private long boosterExpiry = 0;         // epoch-ms when the active booster expires
    private double boosterMultiplier = 1.0; // multiplier of the currently active booster

    // Owned booster items (purchased from shop, not yet activated)
    private final Map<String, Integer> boosterInventory = new HashMap<>();

    // Experience points (kept for storage compatibility; XP grants now go via external plugin)
    private int experience = 0;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.coins = 0;
        this.selectedBlock = "SANDSTONE:0";
    }

    public void saveTo(FileConfiguration config) {
        config.set("uuid", uuid.toString());
        config.set("name", name);
        config.set("coins", coins);
        config.set("last-map", lastMap);
        config.set("last-island", lastIsland);
        config.set("selected-block", selectedBlock);

        for (Map.Entry<String, MapStats> entry : mapStats.entrySet()) {
            String path = "stats." + entry.getKey();
            MapStats stats = entry.getValue();
            config.set(path + ".best-time", stats.bestTime);
            config.set(path + ".total-attempts", stats.totalAttempts);
            config.set(path + ".successful-attempts", stats.successfulAttempts);
            config.set(path + ".total-success-time", stats.totalSuccessTime);
        }

        config.set("purchased-blocks", new java.util.ArrayList<>(purchasedBlocks));
        config.set("auto-refill", autoRefill);
        config.set("infinite-blocks-unlocked", infiniteBlocksUnlocked);
        config.set("infinite-blocks", infiniteBlocks);
        config.set("favorite-replays", new java.util.ArrayList<>(favoriteReplays));
        config.set("selected-pickaxe", selectedPickaxe);
        config.set("selected-animation", selectedAnimation);
        config.set("selected-death-sound", selectedDeathSound);
        config.set("one-click-pick", oneClickPickEnabled);
        for (Map.Entry<String, String> e : selectedDesigns.entrySet()) {
            config.set("selected-designs." + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Set<String>> e : notifiedRanks.entrySet()) {
            config.set("notified-ranks." + e.getKey(),
                    new java.util.ArrayList<>(e.getValue()));
        }
        // custom-lengths (session) is not saved, but savedCustomLengths (persistent) is.
        config.set("saved-custom-lengths", null);
        for (Map.Entry<String, Integer> e : savedCustomLengths.entrySet()) {
            if (e.getValue() > 0) config.set("saved-custom-lengths." + e.getKey(), e.getValue());
        }
        // Infinite distances (persisted)
        config.set("infinite-distances", null);
        for (Map.Entry<String, Integer> e : infiniteDistances.entrySet()) {
            if (e.getValue() > 0) config.set("infinite-distances." + e.getKey(), e.getValue());
        }
        config.set("infinite-distance-times", null);
        for (Map.Entry<String, Long> e : infiniteDistanceTimes.entrySet()) {
            if (e.getValue() > 0) config.set("infinite-distance-times." + e.getKey(), e.getValue());
        }
        config.set("purchased-designs",
                purchasedDesigns.isEmpty() ? null : new java.util.ArrayList<>(purchasedDesigns));

        config.set("booster-expiry", boosterExpiry);
        config.set("booster-multiplier", boosterMultiplier);

        // Booster inventory: clear old keys first, then write non-zero quantities
        config.set("booster-inventory", null);
        for (Map.Entry<String, Integer> e : boosterInventory.entrySet()) {
            if (e.getValue() > 0) {
                config.set("booster-inventory." + e.getKey(), e.getValue());
            }
        }

        config.set("experience", experience);
    }

    public void loadFrom(FileConfiguration config) {
        name = config.getString("name", name);
        coins = config.getInt("coins", 0);
        lastMap = config.getString("last-map");
        lastIsland = config.getInt("last-island", -1);
        selectedBlock = config.getString("selected-block", "SANDSTONE:0");

        mapStats.clear();
        if (config.isConfigurationSection("stats")) {
            for (String mapName : config.getConfigurationSection("stats").getKeys(false)) {
                String path = "stats." + mapName;
                MapStats stats = new MapStats();
                stats.bestTime = config.getLong(path + ".best-time", -1);
                stats.totalAttempts = config.getInt(path + ".total-attempts", 0);
                stats.successfulAttempts = config.getInt(path + ".successful-attempts", 0);
                stats.totalSuccessTime = config.getLong(path + ".total-success-time", 0);
                mapStats.put(mapName, stats);
            }
        }

        purchasedBlocks.clear();
        if (config.isList("purchased-blocks")) {
            purchasedBlocks.addAll(config.getStringList("purchased-blocks"));
        }
        autoRefill = config.getBoolean("auto-refill", false);
        infiniteBlocksUnlocked = config.getBoolean("infinite-blocks-unlocked", false);
        infiniteBlocks = config.getBoolean("infinite-blocks", false);

        favoriteReplays.clear();
        if (config.isList("favorite-replays")) {
            favoriteReplays.addAll(config.getStringList("favorite-replays"));
        }

        selectedPickaxe = config.getString("selected-pickaxe", "DIAMOND_PICKAXE:0");
        selectedAnimation = config.getString("selected-animation", "NONE");
        selectedDeathSound = config.getString("selected-death-sound", "NONE");
        oneClickPickEnabled = config.getBoolean("one-click-pick", false);
        selectedDesigns.clear();
        if (config.isConfigurationSection("selected-designs")) {
            for (String mapKey : config.getConfigurationSection("selected-designs").getKeys(false)) {
                selectedDesigns.put(mapKey, config.getString("selected-designs." + mapKey));
            }
        }
        notifiedRanks.clear();
        if (config.isConfigurationSection("notified-ranks")) {
            for (String mapKey : config.getConfigurationSection("notified-ranks").getKeys(false)) {
                java.util.List<String> rankList = config.getStringList("notified-ranks." + mapKey);
                notifiedRanks.put(mapKey, new HashSet<>(rankList));
            }
        }
        // Load saved custom lengths (persistent), then seed the session map from them.
        savedCustomLengths.clear();
        if (config.isConfigurationSection("saved-custom-lengths")) {
            for (String key : config.getConfigurationSection("saved-custom-lengths").getKeys(false)) {
                int v = config.getInt("saved-custom-lengths." + key, 0);
                if (v > 0) savedCustomLengths.put(key, v);
            }
        }
        // Session custom-lengths start from the saved values so they're immediately usable.
        customLengths.clear();
        customLengths.putAll(savedCustomLengths);
        customLengthToggles.clear();

        // Infinite distances
        infiniteDistances.clear();
        if (config.isConfigurationSection("infinite-distances")) {
            for (String key : config.getConfigurationSection("infinite-distances").getKeys(false)) {
                int v = config.getInt("infinite-distances." + key, 0);
                if (v > 0) infiniteDistances.put(key, v);
            }
        }
        infiniteDistanceTimes.clear();
        if (config.isConfigurationSection("infinite-distance-times")) {
            for (String key : config.getConfigurationSection("infinite-distance-times").getKeys(false)) {
                long v = config.getLong("infinite-distance-times." + key, 0);
                if (v > 0) infiniteDistanceTimes.put(key, v);
            }
        }

        purchasedDesigns.clear();
        if (config.isList("purchased-designs")) {
            purchasedDesigns.addAll(config.getStringList("purchased-designs"));
        }

        boosterExpiry     = config.getLong("booster-expiry", 0);
        boosterMultiplier = config.getDouble("booster-multiplier", 1.0);

        boosterInventory.clear();
        if (config.isConfigurationSection("booster-inventory")) {
            for (String key : config.getConfigurationSection("booster-inventory").getKeys(false)) {
                int qty = config.getInt("booster-inventory." + key, 0);
                if (qty > 0) boosterInventory.put(key, qty);
            }
        }

        experience = config.getInt("experience", 0);
    }

    // --- Stats helpers ---

    public MapStats getOrCreateStats(String mapName) {
        return mapStats.computeIfAbsent(mapName, k -> new MapStats());
    }

    public MapStats getStats(String mapName) {
        return mapStats.get(mapName);
    }

    public Map<String, MapStats> getAllStats() {
        return mapStats;
    }

    // --- Economy ---

    public boolean hasPurchasedBlock(String block) {
        return purchasedBlocks.contains(block);
    }

    public void purchaseBlock(String block) {
        purchasedBlocks.add(block);
    }

    public Set<String> getPurchasedBlocks() {
        return purchasedBlocks;
    }

    // --- Favorites ---

    public boolean isFavoriteReplay(String fileName) {
        return favoriteReplays.contains(fileName);
    }

    public void addFavoriteReplay(String fileName) {
        favoriteReplays.add(fileName);
    }

    public void removeFavoriteReplay(String fileName) {
        favoriteReplays.remove(fileName);
    }

    public void toggleFavoriteReplay(String fileName) {
        if (favoriteReplays.contains(fileName)) {
            favoriteReplays.remove(fileName);
        } else {
            favoriteReplays.add(fileName);
        }
    }

    public Set<String> getFavoriteReplays() {
        return favoriteReplays;
    }

    // --- Getters/Setters ---

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getCoins() { return coins; }
    public void setCoins(int coins) { this.coins = coins; }
    public void addCoins(int amount) {
        // Hard cap at Integer.MAX_VALUE to prevent overflow
        this.coins = (int) Math.min((long) this.coins + amount, Integer.MAX_VALUE);
    }
    public boolean removeCoins(int amount) {
        if (coins >= amount) { coins -= amount; return true; }
        return false;
    }

    public String getLastMap() { return lastMap; }
    public void setLastMap(String lastMap) { this.lastMap = lastMap; }

    public int getLastIsland() { return lastIsland; }
    public void setLastIsland(int lastIsland) { this.lastIsland = lastIsland; }

    public String getSelectedBlock() { return selectedBlock; }
    public void setSelectedBlock(String selectedBlock) { this.selectedBlock = selectedBlock; }

    public boolean hasAutoRefill() { return autoRefill; }
    public void setAutoRefill(boolean autoRefill) { this.autoRefill = autoRefill; }

    public boolean hasInfiniteBlocksUnlocked() { return infiniteBlocksUnlocked; }
    public void setInfiniteBlocksUnlocked(boolean unlocked) { this.infiniteBlocksUnlocked = unlocked; }
    public boolean hasInfiniteBlocks() { return infiniteBlocks; }
    public void setInfiniteBlocks(boolean enabled) { this.infiniteBlocks = enabled; }

    public String getSelectedPickaxe() { return selectedPickaxe; }
    public void setSelectedPickaxe(String selectedPickaxe) { this.selectedPickaxe = selectedPickaxe; }

    public String getSelectedAnimation() { return selectedAnimation; }
    public void setSelectedAnimation(String selectedAnimation) { this.selectedAnimation = selectedAnimation; }

    public String getSelectedDeathSound() { return selectedDeathSound; }
    public void setSelectedDeathSound(String sound) { this.selectedDeathSound = sound; }

    public boolean hasOneClickPick() { return oneClickPickEnabled; }
    public void setOneClickPick(boolean enabled) { this.oneClickPickEnabled = enabled; }

    public String getSelectedDesign(String mapName) { return selectedDesigns.get(mapName.toLowerCase()); }
    public void setSelectedDesign(String mapName, String templateKey) {
        if (templateKey == null) selectedDesigns.remove(mapName.toLowerCase());
        else selectedDesigns.put(mapName.toLowerCase(), templateKey);
    }
    public Map<String, String> getSelectedDesigns() { return selectedDesigns; }

    public boolean hasBeenNotifiedOfRank(String mapName, String rankName) {
        Set<String> ranks = notifiedRanks.get(mapName.toLowerCase());
        return ranks != null && ranks.contains(rankName);
    }

    public void markRankNotified(String mapName, String rankName) {
        notifiedRanks.computeIfAbsent(mapName.toLowerCase(), k -> new HashSet<>()).add(rankName);
    }

    public Map<String, Set<String>> getNotifiedRanks() {
        return Collections.unmodifiableMap(notifiedRanks);
    }

    // --- Purchased Designs ---

    /**
     * Returns true if the player has purchased/unlocked the given design template key.
     * Because the set is global (not per-map), a design unlocked on any map grants access
     * to all maps that share the same template key name.
     */
    public boolean hasPurchasedDesign(String templateKey) {
        return purchasedDesigns.contains(templateKey);
    }

    public void purchaseDesign(String templateKey) {
        purchasedDesigns.add(templateKey);
    }

    public Set<String> getPurchasedDesigns() { return purchasedDesigns; }

    // --- Coin Booster (active) ---

    public long getBoosterExpiry() { return boosterExpiry; }
    public void setBoosterExpiry(long expiry) { this.boosterExpiry = expiry; }

    public double getBoosterMultiplier() { return boosterMultiplier; }
    public void setBoosterMultiplier(double multiplier) { this.boosterMultiplier = multiplier; }

    // --- Booster Inventory (owned, not yet activated) ---

    /** How many of a specific booster type the player owns. */
    public int getBoosterCount(String typeId) {
        return boosterInventory.getOrDefault(typeId, 0);
    }

    /** Add boosters of a type to this player's inventory. */
    public void addBooster(String typeId, int amount) {
        boosterInventory.merge(typeId, amount, Integer::sum);
    }

    /**
     * Consume one booster of the given type from inventory.
     * Returns false if the player has none.
     */
    public boolean consumeBooster(String typeId) {
        int count = boosterInventory.getOrDefault(typeId, 0);
        if (count <= 0) return false;
        if (count == 1) boosterInventory.remove(typeId);
        else boosterInventory.put(typeId, count - 1);
        return true;
    }

    /** Read-only view of all owned boosters (type id → quantity). */
    public Map<String, Integer> getBoosterInventory() {
        return Collections.unmodifiableMap(boosterInventory);
    }

    public boolean hasAnyBoosters() {
        return !boosterInventory.isEmpty();
    }

    // --- Experience ---

    public int getExperience() { return experience; }
    public void addExperience(int amount) {
        this.experience = (int) Math.min((long) this.experience + amount, Integer.MAX_VALUE);
    }
    public void setExperience(int amount) { this.experience = Math.max(0, amount); }

    // --- Custom Length (session-only) ---

    /** Clears all session custom-length data. Call on logout or map change. */
    public void clearCustomLengths() {
        customLengths.clear();
        customLengthToggles.clear();
        customLengthYOffsets.clear();
        activeFinishZoneOverrides.clear();
    }

    /**
     * Returns the player's custom run length for the given map, or 0 if not set.
     */
    public int getCustomLength(String mapName) {
        Integer val = customLengths.get(mapName.toLowerCase());
        return val != null ? val : 0;
    }

    /**
     * Sets the player's preferred custom run length for the given map.
     * Pass 0 to clear/reset to default. Automatically persists to savedCustomLengths.
     */
    public void setCustomLength(String mapName, int length) {
        String key = mapName.toLowerCase();
        if (length <= 0) {
            customLengths.remove(key);
            // Keep savedCustomLengths intact — clearing session length doesn't erase the saved pref.
        } else {
            customLengths.put(key, length);
            savedCustomLengths.put(key, length);
        }
    }

    /** Returns the player's saved (persistent) custom length for the given map, or 0 if none. */
    public int getSavedCustomLength(String mapName) {
        Integer v = savedCustomLengths.get(mapName.toLowerCase());
        return v != null ? v : 0;
    }

    /** Returns all saved (persistent) custom lengths as an unmodifiable map. */
    public Map<String, Integer> getSavedCustomLengthsMap() {
        return Collections.unmodifiableMap(savedCustomLengths);
    }

    /**
     * Returns whether custom length is active for the given map.
     * True when the player explicitly toggled it on, OR when they have a positive
     * length set via the settings distance adjuster or /length command.
     */
    public boolean isCustomLengthEnabled(String mapName) {
        Boolean val = customLengthToggles.get(mapName.toLowerCase());
        if (val != null && val) return true;
        return getCustomLength(mapName) > 0;
    }

    /**
     * Toggle custom length on/off for the given map. Returns the new state.
     */
    public boolean toggleCustomLength(String mapName) {
        String key = mapName.toLowerCase();
        boolean current = customLengthToggles.getOrDefault(key, false);
        boolean newValue = !current;
        if (newValue) {
            customLengthToggles.put(key, true);
        } else {
            customLengthToggles.remove(key); // false is default, don't store it
        }
        return newValue;
    }

    // -------------------------------------------------------------------------
    // Per-player end-island Y offset (custom length height adjustment)
    // -------------------------------------------------------------------------

    /** Returns the vertical offset applied to the end island for the given map, or 0 if none. */
    public int getCustomLengthY(String mapName) {
        Integer val = customLengthYOffsets.get(mapName.toLowerCase());
        return val != null ? val : 0;
    }

    /** Sets the per-player end-island Y offset for the given map. Pass 0 to reset. */
    public void setCustomLengthY(String mapName, int offset) {
        if (offset == 0) {
            customLengthYOffsets.remove(mapName.toLowerCase());
        } else {
            customLengthYOffsets.put(mapName.toLowerCase(), offset);
        }
    }

    // -------------------------------------------------------------------------
    // Active finish-zone override (set when a design profile defines a custom finish zone)
    // -------------------------------------------------------------------------

    /**
     * Returns the active finish zone override for this map, or null if none is set.
     * Array layout: {finishMinX, finishMinY, finishMinZ, finishMaxX, finishMaxY, finishMaxZ}
     * All values are offsets relative to the island's min corner.
     */
    public int[] getActiveFinishZone(String mapName) {
        return activeFinishZoneOverrides.get(mapName.toLowerCase());
    }

    /**
     * Stores a finish zone override for this map derived from a design profile.
     * All values must be offsets relative to the island's min corner.
     */
    public void setActiveFinishZone(String mapName,
                                    int minX, int minY, int minZ,
                                    int maxX, int maxY, int maxZ) {
        activeFinishZoneOverrides.put(mapName.toLowerCase(),
                new int[]{minX, minY, minZ, maxX, maxY, maxZ});
    }

    /** Clears the active finish zone override for this map (reverts to map defaults). */
    public void clearActiveFinishZone(String mapName) {
        activeFinishZoneOverrides.remove(mapName.toLowerCase());
    }

    // -------------------------------------------------------------------------
    // Infinite-mode distance leaderboard
    // -------------------------------------------------------------------------

    /** Returns the player's best infinite-mode distance (blocks placed) for the given map, or 0. */
    public int getInfiniteDistance(String mapName) {
        Integer v = infiniteDistances.get(mapName.toLowerCase());
        return v != null ? v : 0;
    }

    /**
     * Updates the player's infinite-mode best if {@code distance} beats the current record,
     * or if distance ties and {@code time} is faster (lower ms). Returns true when a new record is set.
     */
    public boolean updateInfiniteDistance(String mapName, int distance, long time) {
        String key = mapName.toLowerCase();
        int currentDist = infiniteDistances.getOrDefault(key, 0);
        long currentTime = infiniteDistanceTimes.getOrDefault(key, Long.MAX_VALUE);
        if (distance > currentDist || (distance == currentDist && time < currentTime)) {
            infiniteDistances.put(key, distance);
            infiniteDistanceTimes.put(key, time);
            return true;
        }
        return false;
    }

    /** Returns the time (ms) of the run that set the best infinite-mode distance, or -1. */
    public long getInfiniteDistanceTime(String mapName) {
        Long v = infiniteDistanceTimes.get(mapName.toLowerCase());
        return v != null ? v : -1L;
    }

    public Map<String, Integer> getInfiniteDistances() {
        return java.util.Collections.unmodifiableMap(infiniteDistances);
    }

    public static class MapStats {
        public long bestTime = -1;
        public int totalAttempts = 0;
        public int successfulAttempts = 0;
        // Sum of all successful completion times — used to compute average
        public long totalSuccessTime = 0;

        public boolean hasBestTime() { return bestTime > 0; }

        /** Average completion time in ms, or -1 if no successful runs. */
        public long getAverageTime() {
            return successfulAttempts > 0 ? totalSuccessTime / successfulAttempts : -1;
        }
    }
}
