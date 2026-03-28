package net.gravijet.fastbuilder.manager;

import net.gravijet.fastbuilder.model.BridgeDistance;
import net.gravijet.fastbuilder.model.Island;
import net.gravijet.fastbuilder.model.PlayerStats;
import net.gravijet.fastbuilder.util.CC;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HologramManager {

    private static final double LINE_SPACING = 0.28;

    private final Map<UUID, List<ArmorStand>> holograms = new HashMap<>();

    public void createHologram(Island island, PlayerStats stats, BridgeDistance distance) {
        UUID uuid = island.getPlayerUuid();
        removeHologram(uuid);

        Location base = island.getHologramLocation();
        String[] lines = buildLines(stats, distance);
        List<ArmorStand> stands = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            Location loc = base.clone().subtract(0, i * LINE_SPACING, 0);
            ArmorStand stand = spawnStand(loc, lines[i]);
            stands.add(stand);
        }
        holograms.put(uuid, stands);
    }

    public void updateHologram(UUID uuid, PlayerStats stats, BridgeDistance distance) {
        List<ArmorStand> stands = holograms.get(uuid);
        if (stands == null) return;
        String[] lines = buildLines(stats, distance);
        for (int i = 0; i < Math.min(lines.length, stands.size()); i++) {
            ArmorStand s = stands.get(i);
            if (s != null && !s.isDead()) s.setCustomName(lines[i]);
        }
    }

    public void removeHologram(UUID uuid) {
        List<ArmorStand> stands = holograms.remove(uuid);
        if (stands != null) stands.forEach(s -> { if (s != null && !s.isDead()) s.remove(); });
    }

    public void removeAll() {
        new ArrayList<>(holograms.keySet()).forEach(this::removeHologram);
    }

    private String[] buildLines(PlayerStats stats, BridgeDistance distance) {
        PlayerStats.DistanceStats ds = stats.getStats(distance);
        String rate = ds.getSuccessRate();
        return new String[]{
            CC.c("&c&l✦ FastBuilder ✦"),
            CC.c("&8" + "—".repeat(16)),
            CC.c("&7Distance: " + distance.getColorCode() + distance.getDisplayName()),
            CC.c("&7Attempts: &f" + ds.attempts + " &8| &7Success: &a" + ds.successes),
            CC.c("&7Rate: &f" + rate + " &8| &7Best: &e" + ds.getFormattedBestTime()),
        };
    }

    private ArmorStand spawnStand(Location loc, String text) {
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setCanPickupItems(false);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setCustomName(text);
        stand.setCustomNameVisible(true);
        return stand;
    }
}
