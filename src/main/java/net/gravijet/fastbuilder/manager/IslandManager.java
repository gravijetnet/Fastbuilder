package net.gravijet.fastbuilder.manager;

import net.gravijet.fastbuilder.model.Island;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class IslandManager {

    private final String  worldName;
    private final int     islandY;
    private final int     spacing;

    private final AtomicInteger        nextIndex = new AtomicInteger(0);
    private final Map<UUID, Island>    islands   = new HashMap<>();
    /** Frees island indices when players leave, so slots are reused. */
    private final java.util.Deque<Integer> freeIndices = new java.util.ArrayDeque<>();

    public IslandManager(String worldName, int islandY, int spacing) {
        this.worldName = worldName;
        this.islandY   = islandY;
        this.spacing   = spacing;
    }

    public Island createIsland(UUID uuid) {
        int index = freeIndices.isEmpty() ? nextIndex.getAndIncrement() : freeIndices.poll();
        Location base = new Location(getWorld(), (long) index * spacing, islandY, 0);
        Island island = new Island(uuid, base);
        island.generateStartPlatform();
        islands.put(uuid, island);
        return island;
    }

    public Island getIsland(UUID uuid) {
        return islands.get(uuid);
    }

    public void removeIsland(UUID uuid) {
        islands.remove(uuid);
    }

    public Map<UUID, Island> getAll() {
        return islands;
    }

    public World getWorld() {
        World w = Bukkit.getWorld(worldName);
        return w != null ? w : Bukkit.getWorlds().get(0);
    }
}
