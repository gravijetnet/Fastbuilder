package de.fastbuilder.manager;

import de.fastbuilder.model.Island;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verwaltet die Positionen und das Erstellen von Spieler-Inseln.
 *
 * Inseln werden in einer Reihe generiert:
 *   Insel 0: X=0,   Z=0
 *   Insel 1: X=250, Z=0
 *   Insel 2: X=500, Z=0
 *   ...
 *
 * Die Basis-Y-Ebene ist konfigurierbar (Standard: 64).
 */
public class IslandManager {

    /** Abstand zwischen zwei Inseln in Blöcken */
    private static final int ISLAND_SPACING = 250;

    /** Y-Koordinate der Plattformen */
    private static final int ISLAND_Y = 64;

    /** Name der Welt, in der Inseln generiert werden */
    private final String worldName;

    /** Zähler für die nächste freie Insel-Position */
    private final AtomicInteger nextIslandIndex = new AtomicInteger(0);

    /** Mapping: UUID → Island-Objekt */
    private final Map<UUID, Island> islandMap = new HashMap<>();

    public IslandManager(String worldName) {
        this.worldName = worldName;
    }

    /**
     * Erstellt eine neue Insel für den Spieler, generiert die Startplattform
     * und gibt das Island-Objekt zurück.
     */
    public Island createIsland(UUID playerUuid) {
        // Nächste freie Position bestimmen
        int index = nextIslandIndex.getAndIncrement();
        Location base = calculateBaseLocation(index);

        // Island-Objekt erstellen und Startplattform generieren
        Island island = new Island(playerUuid, base);
        island.generateStartPlatform();

        islandMap.put(playerUuid, island);
        return island;
    }

    /**
     * Gibt die Insel eines Spielers zurück (oder null wenn keine vorhanden).
     */
    public Island getIsland(UUID playerUuid) {
        return islandMap.get(playerUuid);
    }

    /**
     * Entfernt die Insel des Spielers aus dem Manager.
     * (Blöcke werden NICHT entfernt – Inseln bleiben in der Welt)
     */
    public void removeIsland(UUID playerUuid) {
        islandMap.remove(playerUuid);
    }

    /**
     * Gibt die Welt zurück, in der Inseln generiert werden.
     */
    public World getWorld() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            // Fallback: erste geladene Welt
            world = Bukkit.getWorlds().get(0);
        }
        return world;
    }

    /**
     * Berechnet den Basis-Standort für den n-ten Insel-Index.
     */
    private Location calculateBaseLocation(int index) {
        World world = getWorld();
        int x = index * ISLAND_SPACING;
        return new Location(world, x, ISLAND_Y, 0);
    }

    /** Gibt alle gespeicherten Inseln zurück */
    public Map<UUID, Island> getIslandMap() {
        return islandMap;
    }
}
