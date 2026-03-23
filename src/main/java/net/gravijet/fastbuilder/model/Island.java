package de.fastbuilder.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repräsentiert die Insel eines Spielers.
 *
 * Layout (Draufsicht, X-Achse nach rechts):
 *
 *  [NPC]  [HOLO]
 *  [START 5x3]  [--- GAP 5 ---]  [--- BRIDGE (distance) ---]  [TARGET 5x3]
 *
 * Koordinaten-Ursprung = baseLocation (Nord-West-Ecke der Startplattform)
 */
public class Island {

    // ── Konstanten ────────────────────────────────────────────────
    public static final int PLATFORM_WIDTH  = 5; // X-Ausdehnung der Plattform
    public static final int PLATFORM_DEPTH  = 3; // Z-Ausdehnung der Plattform
    public static final int GAP_BLOCKS      = 5; // Lücke zwischen Start und Brücke
    public static final int VOID_DEPTH      = 5; // Wie weit unter Plattform der Void ist
    public static final Material PLATFORM_MATERIAL      = Material.STONE;
    public static final Material PRESSURE_PLATE_MATERIAL = Material.STONE_PLATE;

    // ── Instanzvariablen ─────────────────────────────────────────
    private final UUID playerUuid;
    private final World world;
    private final Location baseLocation;    // Ecke (0,0) der Startplattform
    private Location spawnLocation;         // Spawnpunkt auf der Startplattform
    private BridgeDistance currentDistance; // Aktuell gewählte Distanz

    // Blöcke, die der Spieler während des aktuellen Versuchs gesetzt hat
    private final List<Location> placedBlockLocations = new ArrayList<>();

    // Aktuell platzierte Zielplattform (zum späteren Aufräumen)
    private BridgeDistance lastBuiltDistance = null;

    public Island(UUID playerUuid, Location baseLocation) {
        this.playerUuid  = playerUuid;
        this.world       = baseLocation.getWorld();
        this.baseLocation = baseLocation.clone();

        // Standard-Spawnpunkt: Mitte der Startplattform, eine Ebene drüber
        this.spawnLocation = baseLocation.clone().add(
                PLATFORM_WIDTH / 2.0,   // X-Mitte
                1.0,                    // Y: eine Block über der Plattform
                PLATFORM_DEPTH / 2.0    // Z-Mitte
        );
        this.spawnLocation.setYaw(90f); // Spieler schaut nach Osten (positive X-Richtung)
    }

    // ─────────────────────────────────────────────────────────────
    //  GENERIERUNG
    // ─────────────────────────────────────────────────────────────

    /**
     * Generiert die feste Startplattform (5x3 Blöcke aus STONE).
     * Wird einmalig beim Island-Setup aufgerufen.
     */
    public void generateStartPlatform() {
        int bx = baseLocation.getBlockX();
        int by = baseLocation.getBlockY();
        int bz = baseLocation.getBlockZ();

        for (int x = 0; x < PLATFORM_WIDTH; x++) {
            for (int z = 0; z < PLATFORM_DEPTH; z++) {
                world.getBlockAt(bx + x, by, bz + z).setType(PLATFORM_MATERIAL);
                // Sicherstellen, dass ein Block drüber frei ist (Luft)
                world.getBlockAt(bx + x, by + 1, bz + z).setType(Material.AIR);
            }
        }
    }

    /**
     * Generiert die Zielplattform (5x3 + Druckplatte) für die gegebene Distanz.
     * Entfernt zuerst eine eventuell vorhandene alte Zielplattform.
     */
    public void generateTargetPlatform(BridgeDistance distance) {
        // Alte Zielplattform entfernen
        if (lastBuiltDistance != null) {
            clearTargetPlatform(lastBuiltDistance);
        }

        this.currentDistance = distance;
        this.lastBuiltDistance = distance;

        int bx = baseLocation.getBlockX();
        int by = baseLocation.getBlockY();
        int bz = baseLocation.getBlockZ();

        // Startpunkt der Zielplattform: Start + PlatformWidth + Gap + Distanz
        int targetStartX = bx + PLATFORM_WIDTH + GAP_BLOCKS + distance.getDistance();

        // Plattform-Blöcke setzen
        for (int x = 0; x < PLATFORM_WIDTH; x++) {
            for (int z = 0; z < PLATFORM_DEPTH; z++) {
                world.getBlockAt(targetStartX + x, by, bz + z).setType(PLATFORM_MATERIAL);
                world.getBlockAt(targetStartX + x, by + 1, bz + z).setType(Material.AIR);
            }
        }

        // Druckplatte in der Mitte der Zielplattform
        int ppX = targetStartX + PLATFORM_WIDTH / 2;
        int ppZ = bz + PLATFORM_DEPTH / 2;
        world.getBlockAt(ppX, by + 1, ppZ).setType(PRESSURE_PLATE_MATERIAL);
    }

    /**
     * Entfernt die Zielplattform der angegebenen Distanz (setzt Blöcke auf Luft).
     */
    private void clearTargetPlatform(BridgeDistance distance) {
        int bx = baseLocation.getBlockX();
        int by = baseLocation.getBlockY();
        int bz = baseLocation.getBlockZ();
        int targetStartX = bx + PLATFORM_WIDTH + GAP_BLOCKS + distance.getDistance();

        for (int x = 0; x < PLATFORM_WIDTH; x++) {
            for (int z = 0; z < PLATFORM_DEPTH; z++) {
                world.getBlockAt(targetStartX + x, by, bz + z).setType(Material.AIR);
                world.getBlockAt(targetStartX + x, by + 1, bz + z).setType(Material.AIR);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  BLOCK-TRACKING (für Reset)
    // ─────────────────────────────────────────────────────────────

    /** Registriert einen vom Spieler gesetzten Block für späteres Aufräumen */
    public void trackPlacedBlock(Block block) {
        placedBlockLocations.add(block.getLocation());
    }

    /**
     * Entfernt alle vom Spieler gesetzten Blöcke (Brücken-Reset nach Versuch).
     */
    public void clearPlacedBlocks() {
        for (Location loc : placedBlockLocations) {
            Block block = world.getBlockAt(loc);
            // Nur entfernen wenn es nicht Teil der Start- oder Zielplattform ist
            if (!isPartOfFixedStructure(loc)) {
                block.setType(Material.AIR);
            }
        }
        placedBlockLocations.clear();
    }

    /**
     * Prüft ob die gegebene Location Teil der festen Struktur (Start/Zielplattform) ist.
     * Verhindert, dass diese Blöcke beim Reset entfernt werden.
     */
    private boolean isPartOfFixedStructure(Location loc) {
        int bx = baseLocation.getBlockX();
        int by = baseLocation.getBlockY();
        int bz = baseLocation.getBlockZ();

        // Startplattform
        if (loc.getBlockY() == by
                && loc.getBlockX() >= bx && loc.getBlockX() < bx + PLATFORM_WIDTH
                && loc.getBlockZ() >= bz && loc.getBlockZ() < bz + PLATFORM_DEPTH) {
            return true;
        }

        // Zielplattform (wenn vorhanden)
        if (currentDistance != null) {
            int tx = bx + PLATFORM_WIDTH + GAP_BLOCKS + currentDistance.getDistance();
            if (loc.getBlockY() == by
                    && loc.getBlockX() >= tx && loc.getBlockX() < tx + PLATFORM_WIDTH
                    && loc.getBlockZ() >= bz && loc.getBlockZ() < bz + PLATFORM_DEPTH) {
                return true;
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────
    //  PRÜFMETHODEN
    // ─────────────────────────────────────────────────────────────

    /**
     * Prüft ob die gegebene Location im erlaubten Block-Setz-Bereich liegt
     * (Brücken-Bereich zwischen Start- und Zielplattform).
     */
    public boolean isInBridgeArea(Location loc) {
        if (!loc.getWorld().equals(world)) return false;

        int bx = baseLocation.getBlockX();
        int by = baseLocation.getBlockY();
        int bz = baseLocation.getBlockZ();

        // X: nach Ende der Startplattform bis zum Anfang der Zielplattform
        int bridgeStartX = bx + PLATFORM_WIDTH;
        int bridgeEndX   = (currentDistance == null) ? bx + 200
                : bx + PLATFORM_WIDTH + GAP_BLOCKS + currentDistance.getDistance() - 1;

        // Z: etwas breiter als die Plattform für Bridging-Techniken
        int zMin = bz - 3;
        int zMax = bz + PLATFORM_DEPTH + 2;

        // Y: Spieler kann mehrere Ebenen hoch oder runter bauen
        int yMin = by - 3;
        int yMax = by + 5;

        return loc.getBlockX() >= bridgeStartX && loc.getBlockX() <= bridgeEndX
                && loc.getBlockZ() >= zMin && loc.getBlockZ() <= zMax
                && loc.getBlockY() >= yMin && loc.getBlockY() <= yMax;
    }

    /**
     * Prüft ob ein Spieler in den Void gefallen ist
     * (5 Blöcke unter der Plattform).
     */
    public boolean isInVoid(Location playerLoc) {
        return playerLoc.getWorld().equals(world)
                && playerLoc.getY() < (baseLocation.getBlockY() - VOID_DEPTH);
    }

    /**
     * Prüft ob die gegebene Location die Druckplatte der Zielplattform ist.
     */
    public boolean isPressurePlate(Location loc) {
        if (currentDistance == null) return false;
        if (!loc.getWorld().equals(world)) return false;

        int bx = baseLocation.getBlockX();
        int by = baseLocation.getBlockY();
        int bz = baseLocation.getBlockZ();
        int targetStartX = bx + PLATFORM_WIDTH + GAP_BLOCKS + currentDistance.getDistance();

        int ppX = targetStartX + PLATFORM_WIDTH / 2;
        int ppZ = bz + PLATFORM_DEPTH / 2;
        int ppY = by + 1;

        return loc.getBlockX() == ppX && loc.getBlockZ() == ppZ && loc.getBlockY() == ppY;
    }

    /**
     * Prüft ob eine Location zu DIESER Insel gehört.
     * Großzügige Prüfung (bounding box der Insel).
     */
    public boolean isOnThisIsland(Location loc) {
        if (!loc.getWorld().equals(world)) return false;

        int bx = baseLocation.getBlockX();
        int bz = baseLocation.getBlockZ();
        int maxX = bx + PLATFORM_WIDTH + GAP_BLOCKS
                + (currentDistance != null ? currentDistance.getDistance() : 64)
                + PLATFORM_WIDTH + 10;

        return loc.getBlockX() >= bx - 10 && loc.getBlockX() <= maxX
                && loc.getBlockZ() >= bz - 10 && loc.getBlockZ() <= bz + PLATFORM_DEPTH + 10;
    }

    // ─────────────────────────────────────────────────────────────
    //  NPC / HOLOGRAM POSITIONEN
    // ─────────────────────────────────────────────────────────────

    /** Position des NPCs (vor der Startplattform, negative Z-Richtung) */
    public Location getNpcLocation() {
        return baseLocation.clone().add(
                PLATFORM_WIDTH / 2.0, 1.0, -2.0
        );
    }

    /** Position des Holograms (über der Startplattform) */
    public Location getHologramBaseLocation() {
        return baseLocation.clone().add(
                PLATFORM_WIDTH / 2.0, 4.5, PLATFORM_DEPTH / 2.0
        );
    }

    // ─────────────────────────────────────────────────────────────
    //  GETTER / SETTER
    // ─────────────────────────────────────────────────────────────

    public UUID           getPlayerUuid()      { return playerUuid; }
    public World          getWorld()           { return world; }
    public Location       getBaseLocation()    { return baseLocation.clone(); }
    public BridgeDistance getCurrentDistance() { return currentDistance; }
    public int            getPlacedBlockCount(){ return placedBlockLocations.size(); }

    public Location getSpawnLocation() {
        return spawnLocation.clone();
    }

    /** Überschreibt den Spawnpunkt (z.B. via /f setspawn) */
    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = spawnLocation.clone();
    }
}
