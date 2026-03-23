package de.fastbuilder.manager;

import de.fastbuilder.model.Island;
import de.fastbuilder.model.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verwaltet Hologramme pro Spieler-Insel.
 *
 * Hologramme bestehen aus unsichtbaren ArmorStands mit CustomName.
 * Jede Insel hat 3 Hologramm-Zeilen:
 *   Zeile 1: Versuche
 *   Zeile 2: Erfolgreich
 *   Zeile 3: Bestzeit
 *
 * Abstand zwischen Zeilen: 0.3 Blöcke (Standard-Hologramm-Abstand)
 */
public class HologramManager {

    /** Zeilenabstand der Hologramm-Zeilen */
    private static final double LINE_SPACING = 0.30;

    /** Mapping: UUID → Liste der ArmorStands (von oben nach unten) */
    private final Map<UUID, List<ArmorStand>> hologramMap = new HashMap<>();

    /**
     * Erstellt ein Hologramm für die gegebene Insel mit initial-Daten.
     */
    public void createHologram(Island island, PlayerData data) {
        UUID uuid = island.getPlayerUuid();

        // Altes Hologramm entfernen falls vorhanden
        removeHologram(uuid);

        Location base = island.getHologramBaseLocation();
        List<ArmorStand> stands = new ArrayList<>();

        // Zeilen von oben nach unten spawnen
        String[] lines = buildLines(data);
        for (int i = 0; i < lines.length; i++) {
            Location lineLoc = base.clone().subtract(0, i * LINE_SPACING, 0);
            ArmorStand stand = spawnLine(lineLoc, lines[i]);
            stands.add(stand);
        }

        hologramMap.put(uuid, stands);
    }

    /**
     * Aktualisiert die Texte aller Hologramm-Zeilen mit neuen Statistiken.
     */
    public void updateHologram(UUID playerUuid, PlayerData data) {
        List<ArmorStand> stands = hologramMap.get(playerUuid);
        if (stands == null || stands.isEmpty()) return;

        String[] lines = buildLines(data);
        for (int i = 0; i < Math.min(lines.length, stands.size()); i++) {
            ArmorStand stand = stands.get(i);
            if (stand != null && !stand.isDead()) {
                stand.setCustomName(lines[i]);
            }
        }
    }

    /**
     * Entfernt das Hologramm eines Spielers (ArmorStands werden despawned).
     */
    public void removeHologram(UUID playerUuid) {
        List<ArmorStand> stands = hologramMap.remove(playerUuid);
        if (stands != null) {
            for (ArmorStand stand : stands) {
                if (stand != null && !stand.isDead()) {
                    stand.remove();
                }
            }
        }
    }

    /**
     * Entfernt alle Hologramme (beim Plugin-Stop).
     */
    public void removeAll() {
        for (UUID uuid : new ArrayList<>(hologramMap.keySet())) {
            removeHologram(uuid);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  PRIVATE HELFER
    // ─────────────────────────────────────────────────────────────

    /** Spawnt einen einzelnen ArmorStand als Hologramm-Zeile */
    private ArmorStand spawnLine(Location location, String text) {
        ArmorStand stand = (ArmorStand) location.getWorld()
                .spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);           // Unsichtbar (kein Körper)
        stand.setGravity(false);           // Schwebt
        stand.setCanPickupItems(false);
        stand.setSmall(true);              // Kleiner Stand, nähere Zeilen
        stand.setCustomName(text);
        stand.setCustomNameVisible(true);
        stand.setBasePlate(false);
        stand.setArms(false);
        return stand;
    }

    /** Erstellt die Hologramm-Text-Zeilen basierend auf PlayerData */
    private String[] buildLines(PlayerData data) {
        return new String[]{
            // Zeile 1 (oben): Titel
            ChatColor.GOLD + "" + ChatColor.BOLD + "✦ FastBuilder ✦",
            // Zeile 2: Versuche
            ChatColor.GRAY + "Versuche: " + ChatColor.WHITE + data.getTotalAttempts()
                    + ChatColor.GRAY + "  ✔ " + ChatColor.GREEN + data.getSuccessfulAttempts(),
            // Zeile 3: Bestzeit
            ChatColor.GRAY + "Bestzeit: " + ChatColor.AQUA + data.getFormattedBestTime()
        };
    }
}
