package net.gravijet.fastbuilder.player;

import org.bukkit.configuration.file.FileConfiguration;

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

    // Favorited replay file names (protected from deletion)
    private final Set<String> favoriteReplays = new HashSet<>();

    // Tracks which rank names (per map) the player has already been notified about (one-time messages)
    private final Map<String, Set<String>> notifiedRanks = new HashMap<>();

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
        for (Map.Entry<String, Set<String>> e : notifiedRanks.entrySet()) {
            config.set("notified-ranks." + e.getKey(),
                    new java.util.ArrayList<>(e.getValue()));
        }
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
        notifiedRanks.clear();
        if (config.isConfigurationSection("notified-ranks")) {
            for (String mapKey : config.getConfigurationSection("notified-ranks").getKeys(false)) {
                java.util.List<String> rankList = config.getStringList("notified-ranks." + mapKey);
                notifiedRanks.put(mapKey, new HashSet<>(rankList));
            }
        }
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

    public boolean hasBeenNotifiedOfRank(String mapName, String rankName) {
        Set<String> ranks = notifiedRanks.get(mapName.toLowerCase());
        return ranks != null && ranks.contains(rankName);
    }

    public void markRankNotified(String mapName, String rankName) {
        notifiedRanks.computeIfAbsent(mapName.toLowerCase(), k -> new HashSet<>()).add(rankName);
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
