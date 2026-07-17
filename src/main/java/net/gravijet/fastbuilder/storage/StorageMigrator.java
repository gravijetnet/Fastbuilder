package net.gravijet.fastbuilder.storage;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.player.PlayerData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Copies every player record from one storage backend into another.
 *
 * <p>Source and target are opened independently of the running server, so any pair of
 * {@code yaml} / {@code sqlite} / {@code mysql} can be converted in either direction
 * without changing {@code storage.type} first. Records are upserted into the target:
 * a player that already exists there is overwritten, one that only exists in the target
 * is left alone.
 *
 * <p>Everything here does blocking I/O — run it off the main thread.
 */
public final class StorageMigrator {

    /** Backend names accepted by {@link #open}. */
    public static final List<String> TYPES = java.util.Arrays.asList("yaml", "sqlite", "mysql");

    /** Outcome of a migration run. */
    public static final class Result {
        public final int migrated;
        public final int failed;
        public final int total;
        /** Null on success, otherwise the reason the run aborted before touching any data. */
        public final String error;

        Result(int migrated, int failed, int total, String error) {
            this.migrated = migrated;
            this.failed   = failed;
            this.total    = total;
            this.error    = error;
        }

        public boolean isAborted() { return error != null; }
    }

    private StorageMigrator() {}

    public static boolean isValidType(String type) {
        return type != null && TYPES.contains(type.toLowerCase(Locale.ROOT));
    }

    /**
     * Construct a backend by name. The returned provider is NOT initialised.
     * The YAML provider gets a private empty live-cache: it must not see the server's
     * real cache here, or a migration would mix live state into the scan.
     */
    private static StorageProvider open(FastBuilder plugin, String type) {
        switch (type.toLowerCase(Locale.ROOT)) {
            case "sqlite": return new SqliteStorageProvider(plugin);
            case "mysql":  return new MySqlStorageProvider(plugin);
            default:       return new YamlStorageProvider(plugin, new HashMap<UUID, PlayerData>());
        }
    }

    /**
     * Copy every record from {@code fromType} to {@code toType}.
     *
     * @param plugin   plugin instance
     * @param fromType source backend name
     * @param toType   target backend name
     * @param progress called with (done, total) roughly every 25 records; may be null
     * @return counts, or an aborted result carrying the reason
     */
    public static Result migrate(FastBuilder plugin, String fromType, String toType,
                                 ProgressCallback progress) {

        if (!isValidType(fromType)) return new Result(0, 0, 0, "Unknown source backend: " + fromType);
        if (!isValidType(toType))   return new Result(0, 0, 0, "Unknown target backend: " + toType);
        if (fromType.equalsIgnoreCase(toType)) {
            return new Result(0, 0, 0, "Source and target are the same backend.");
        }

        String activeType = plugin.getPlayerManager().getActiveStorageType();

        // Anything a player changed this session may still only exist in memory. Flush it
        // into the active backend first, otherwise migrating away from it loses that state.
        plugin.getPlayerManager().saveAll();

        StorageProvider source = null, target = null;
        boolean closeSource = false, closeTarget = false;
        try {
            // Reuse the live provider when it is one of the two endpoints. Opening a second
            // instance of the same backend would mean two SQLite handles on one file, or a
            // second MySQL pool — both avoidable.
            if (fromType.equalsIgnoreCase(activeType)) {
                source = plugin.getPlayerManager().getProvider();
            } else {
                source = open(plugin, fromType);
                source.init();
                closeSource = true;
            }

            if (toType.equalsIgnoreCase(activeType)) {
                target = plugin.getPlayerManager().getProvider();
            } else {
                target = open(plugin, toType);
                target.init();
                closeTarget = true;
            }

            List<UUID> uuids = source.getAllPlayerUuids();
            int total = uuids.size();
            int migrated = 0, failed = 0;

            for (int i = 0; i < total; i++) {
                UUID uuid = uuids.get(i);
                try {
                    // Providers fill in the real name from storage; "" is only a fallback
                    // for a record that has none.
                    PlayerData data = source.loadPlayerData(uuid, "");
                    // Providers swallow their own write errors, so trust the return value,
                    // not the absence of an exception.
                    if (target.savePlayerData(data)) migrated++;
                    else {
                        failed++;
                        plugin.getLogger().warning("[Migrate] Target rejected player " + uuid);
                    }
                } catch (Exception e) {
                    failed++;
                    plugin.getLogger().log(Level.WARNING,
                            "[Migrate] Failed to migrate player " + uuid, e);
                }
                if (progress != null && (i + 1) % 25 == 0) progress.onProgress(i + 1, total);
            }

            if (progress != null) progress.onProgress(total, total);
            return new Result(migrated, failed, total, null);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Migrate] Migration aborted", e);
            return new Result(0, 0, 0, e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            // Only shut down what we opened — never the server's live provider.
            if (closeSource && source != null) {
                try { source.shutdown(); } catch (Exception ignored) {}
            }
            if (closeTarget && target != null) {
                try { target.shutdown(); } catch (Exception ignored) {}
            }
        }
    }

    /** Progress hook, invoked on the migration thread. */
    public interface ProgressCallback {
        void onProgress(int done, int total);
    }
}
