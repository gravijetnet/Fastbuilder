package net.gravijet.fastbuilder.manager;

import net.gravijet.fastbuilder.model.BridgeDistance;
import net.gravijet.fastbuilder.model.PlayerStats;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class StatsManager {

    private final Plugin plugin;
    private final File   statsDir;

    private final Map<UUID, PlayerStats> cache = new HashMap<>();

    public StatsManager(Plugin plugin) {
        this.plugin   = plugin;
        this.statsDir = new File(plugin.getDataFolder(), "stats");
        if (!statsDir.exists()) statsDir.mkdirs();
    }

    public PlayerStats load(UUID uuid) {
        PlayerStats stats = new PlayerStats();
        File file = fileFor(uuid);
        if (!file.exists()) {
            cache.put(uuid, stats);
            return stats;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        stats.setTotalAttempts(cfg.getInt("total.attempts", 0));
        stats.setTotalSuccesses(cfg.getInt("total.successes", 0));

        for (BridgeDistance d : BridgeDistance.values()) {
            String key = "distance." + d.name();
            PlayerStats.DistanceStats ds = stats.getStats(d);
            ds.attempts      = cfg.getInt(key + ".attempts",   0);
            ds.successes     = cfg.getInt(key + ".successes",  0);
            long best        = cfg.getLong(key + ".bestTime",  Long.MAX_VALUE);
            ds.bestTimeMillis = best;
        }

        cache.put(uuid, stats);
        return stats;
    }

    public void save(UUID uuid) {
        PlayerStats stats = cache.get(uuid);
        if (stats == null) return;

        File file = fileFor(uuid);
        YamlConfiguration cfg = new YamlConfiguration();

        cfg.set("total.attempts",  stats.getTotalAttempts());
        cfg.set("total.successes", stats.getTotalSuccesses());

        for (BridgeDistance d : BridgeDistance.values()) {
            String key = "distance." + d.name();
            PlayerStats.DistanceStats ds = stats.getStats(d);
            cfg.set(key + ".attempts",  ds.attempts);
            cfg.set(key + ".successes", ds.successes);
            cfg.set(key + ".bestTime",  ds.hasBestTime() ? ds.bestTimeMillis : Long.MAX_VALUE);
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save stats for " + uuid, e);
        }
    }

    public void saveAndUnload(UUID uuid) {
        save(uuid);
        cache.remove(uuid);
    }

    public PlayerStats get(UUID uuid) {
        return cache.getOrDefault(uuid, new PlayerStats());
    }

    private File fileFor(UUID uuid) {
        return new File(statsDir, uuid + ".yml");
    }
}
