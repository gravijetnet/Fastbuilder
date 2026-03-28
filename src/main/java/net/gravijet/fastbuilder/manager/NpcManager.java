package net.gravijet.fastbuilder.manager;

import net.gravijet.fastbuilder.model.Island;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NpcManager {

    /** npcUUID → ownerUUID */
    private final Map<UUID, UUID> npcToOwner = new HashMap<>();
    /** ownerUUID → Villager */
    private final Map<UUID, Villager> ownerToNpc = new HashMap<>();

    public void spawnNpc(Island island) {
        UUID playerUuid = island.getPlayerUuid();
        removeNpc(playerUuid);

        Location loc = island.getNpcLocation();
        loc.setYaw(180f);

        Villager v = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        v.setCustomName(ChatColor.translateAlternateColorCodes('&',
                "&c&l» &fSelect Distance &c&l«"));
        v.setCustomNameVisible(true);
        v.setProfession(Villager.Profession.LIBRARIAN);
        v.setMaxHealth(2048);
        v.setHealth(2048);
        v.setRemoveWhenFarAway(false);
        disableAI(v);

        npcToOwner.put(v.getUniqueId(), playerUuid);
        ownerToNpc.put(playerUuid, v);
    }

    public void removeNpc(UUID playerUuid) {
        Villager v = ownerToNpc.remove(playerUuid);
        if (v != null && !v.isDead()) {
            npcToOwner.remove(v.getUniqueId());
            v.remove();
        }
    }

    public void removeAll() {
        new ArrayList<>(ownerToNpc.keySet()).forEach(this::removeNpc);
    }

    public boolean isNpc(UUID entityUuid) {
        return npcToOwner.containsKey(entityUuid);
    }

    public UUID getOwner(UUID npcUuid) {
        return npcToOwner.get(npcUuid);
    }

    private void disableAI(Villager villager) {
        try {
            String version = villager.getClass().getPackage().getName().split("\\.")[3];
            Class<?> craftVillagerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftVillager");
            Object nmsVillager = craftVillagerClass.getMethod("getHandle").invoke(craftVillagerClass.cast(villager));

            Field goalField   = findField(nmsVillager.getClass(), "goalSelector");
            Field targetField = findField(nmsVillager.getClass(), "targetSelector");

            if (goalField != null)   clearSelector(goalField.get(nmsVillager));
            if (targetField != null) clearSelector(targetField.get(nmsVillager));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    private static void clearSelector(Object selector) throws Exception {
        for (String fieldName : new String[]{"a", "b"}) {
            try {
                Field f = selector.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                ((java.util.Set<?>) f.get(selector)).clear();
            } catch (NoSuchFieldException ignored) {}
        }
    }
}
