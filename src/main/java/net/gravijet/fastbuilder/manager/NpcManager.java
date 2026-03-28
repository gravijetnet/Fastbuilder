package net.gravijet.fastbuilder.manager;

import net.gravijet.fastbuilder.model.Island;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Spawns client-side fake player NPCs (EntityPlayer via NMS) that look like
 * a real player model.  No Citizens dependency required.
 *
 * Uses pure reflection so it works on any CraftBukkit 1.8.x build.
 */
public class NpcManager {

    /** npcEntityId → ownerUUID */
    private final Map<Integer, UUID> npcToOwner  = new HashMap<>();
    /** ownerUUID → npc entity id */
    private final Map<UUID, Integer> ownerToId   = new HashMap<>();
    /** ownerUUID → the raw NMS EntityPlayer object */
    private final Map<UUID, Object>  ownerToNms  = new HashMap<>();

    private static String NMS_VERSION = null;

    private static String nmsVersion() {
        if (NMS_VERSION == null) {
            NMS_VERSION = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        }
        return NMS_VERSION;
    }

    // ── Spawn ─────────────────────────────────────────────────────

    public void spawnNpc(Island island) {
        UUID playerUuid = island.getPlayerUuid();
        removeNpc(playerUuid);

        Location loc = island.getNpcLocation();
        loc.setYaw(180f);

        try {
            Object nmsEntity = createEntityPlayer(loc, "FastBuilder");
            if (nmsEntity == null) return;

            int entityId = (int) nmsEntity.getClass().getMethod("getId").invoke(nmsEntity);
            ownerToId.put(playerUuid, entityId);
            ownerToNms.put(playerUuid, nmsEntity);
            npcToOwner.put(entityId, playerUuid);

            // Show to all online players
            for (Player p : Bukkit.getOnlinePlayers()) {
                sendSpawnPackets(p, nmsEntity);
            }

            // Remove from tab list after 1 s so it's not in the player list
            Bukkit.getScheduler().runTaskLater(net.gravijet.fastbuilder.Main.getInstance(), () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    sendRemoveTabPacket(p, nmsEntity);
                }
            }, 20L);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Called when a new player joins so they see existing NPCs. */
    public void showAllTo(Player player) {
        for (Object nms : ownerToNms.values()) {
            try {
                sendSpawnPackets(player, nms);
                Bukkit.getScheduler().runTaskLater(net.gravijet.fastbuilder.Main.getInstance(), () -> {
                    sendRemoveTabPacket(player, nms);
                }, 20L);
            } catch (Exception ignored) {}
        }
    }

    // ── Remove ────────────────────────────────────────────────────

    public void removeNpc(UUID playerUuid) {
        Object nms = ownerToNms.remove(playerUuid);
        Integer id = ownerToId.remove(playerUuid);
        if (id != null) npcToOwner.remove(id);

        if (nms != null) {
            // Send destroy packet to all players
            try {
                Object destroyPacket = createDestroyPacket(nms);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    sendPacket(p, destroyPacket);
                }
            } catch (Exception ignored) {}
        }
    }

    public void removeAll() {
        new ArrayList<>(ownerToNms.keySet()).forEach(this::removeNpc);
    }

    // ── Checks ────────────────────────────────────────────────────

    public boolean isNpc(int entityId) {
        return npcToOwner.containsKey(entityId);
    }

    public UUID getOwnerByEntityId(int entityId) {
        return npcToOwner.get(entityId);
    }

    // Legacy UUID-based check (kept for PlayerListener compatibility).
    // In 1.8 right-click events expose the entity UUID, so we check by iterating.
    public boolean isNpc(UUID entityUuid) {
        for (Map.Entry<UUID, Object> entry : ownerToNms.entrySet()) {
            try {
                UUID nmsUuid = (UUID) entry.getValue().getClass().getMethod("getUniqueID").invoke(entry.getValue());
                if (entityUuid.equals(nmsUuid)) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    public UUID getOwner(UUID npcEntityUuid) {
        for (Map.Entry<UUID, Object> entry : ownerToNms.entrySet()) {
            try {
                UUID nmsUuid = (UUID) entry.getValue().getClass().getMethod("getUniqueID").invoke(entry.getValue());
                if (npcEntityUuid.equals(nmsUuid)) return entry.getKey();
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ── NMS helpers ───────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Object createEntityPlayer(Location loc, String name) throws Exception {
        String v = nmsVersion();

        Class<?> craftServerClass  = Class.forName("org.bukkit.craftbukkit." + v + ".CraftServer");
        Class<?> craftWorldClass   = Class.forName("org.bukkit.craftbukkit." + v + ".CraftWorld");
        Class<?> minecraftServer   = Class.forName("net.minecraft.server." + v + ".MinecraftServer");
        Class<?> worldServerClass  = Class.forName("net.minecraft.server." + v + ".WorldServer");
        Class<?> entityPlayerClass = Class.forName("net.minecraft.server." + v + ".EntityPlayer");
        Class<?> gameProfileClass  = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> interactManager   = Class.forName("net.minecraft.server." + v + ".PlayerInteractManager");

        Object server    = craftServerClass.getMethod("getServer").invoke(Bukkit.getServer());
        Object worldSrv  = craftWorldClass.getMethod("getHandle").invoke(craftWorldClass.cast(loc.getWorld()));

        // Create a GameProfile
        Object profile = gameProfileClass.getConstructor(UUID.class, String.class)
                .newInstance(UUID.randomUUID(), name);

        // Create PlayerInteractManager
        Constructor<?> pimCon = interactManager.getConstructor(worldServerClass);
        Object pim = pimCon.newInstance(worldSrv);

        // Create EntityPlayer
        Constructor<?> epCon = entityPlayerClass.getConstructor(minecraftServer, worldServerClass,
                gameProfileClass, interactManager);
        Object ep = epCon.newInstance(server, worldSrv, profile, pim);

        // Set location (setLocation(x,y,z,yaw,pitch))
        ep.getClass().getMethod("setLocation", double.class, double.class, double.class, float.class, float.class)
                .invoke(ep, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());

        // Set custom name visible via DataWatcher
        setCustomName(ep, ChatColor.translateAlternateColorCodes('&', "&c&l» &fWähle eine Map &c&l«"));

        return ep;
    }

    private void setCustomName(Object ep, String name) {
        try {
            String v = nmsVersion();
            // a(String) in EntityHuman sets the custom name
            ep.getClass().getMethod("setCustomName", String.class).invoke(ep, name);
            ep.getClass().getMethod("setCustomNameVisible", boolean.class).invoke(ep, true);
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void sendSpawnPackets(Player player, Object nmsEntity) throws Exception {
        String v = nmsVersion();
        Class<?> infoClass = Class.forName("net.minecraft.server." + v + ".PacketPlayOutPlayerInfo");
        Class<?> enumAction = Class.forName("net.minecraft.server." + v + ".PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
        Class<?> spawnClass = Class.forName("net.minecraft.server." + v + ".PacketPlayOutNamedEntitySpawn");
        Class<?> headClass  = Class.forName("net.minecraft.server." + v + ".PacketPlayOutEntityHeadRotation");
        Class<?> entityLiving = Class.forName("net.minecraft.server." + v + ".EntityLiving");

        Object addAction = enumAction.getMethod("valueOf", String.class).invoke(null, "ADD_PLAYER");

        // PacketPlayOutPlayerInfo(ADD_PLAYER, Iterable<EntityPlayer>)
        // In 1.8.8 the constructor is (EnumPlayerInfoAction, EntityPlayer...)
        Object infoPacket;
        try {
            // Try varargs constructor
            Class<?> epClass = Class.forName("net.minecraft.server." + v + ".EntityPlayer");
            Object arr = java.lang.reflect.Array.newInstance(epClass, 1);
            java.lang.reflect.Array.set(arr, 0, nmsEntity);
            Constructor<?> con = infoClass.getConstructor(enumAction, arr.getClass());
            infoPacket = con.newInstance(addAction, arr);
        } catch (NoSuchMethodException e) {
            // Fallback: try with Iterable
            Constructor<?> con = infoClass.getConstructor(enumAction, Iterable.class);
            infoPacket = con.newInstance(addAction, Arrays.asList(nmsEntity));
        }

        Object spawnPacket = spawnClass.getConstructor(
                Class.forName("net.minecraft.server." + v + ".EntityHuman"))
                .newInstance(nmsEntity);

        // Head rotation
        float yaw = (float) nmsEntity.getClass().getMethod("getHeadRotation").invoke(nmsEntity);
        byte yawByte = (byte) (yaw * 256f / 360f);
        Object headPacket = headClass.getConstructor(
                Class.forName("net.minecraft.server." + v + ".Entity"), byte.class)
                .newInstance(nmsEntity, yawByte);

        sendPacket(player, infoPacket);
        sendPacket(player, spawnPacket);
        sendPacket(player, headPacket);
    }

    private void sendRemoveTabPacket(Player player, Object nmsEntity) {
        try {
            String v = nmsVersion();
            Class<?> infoClass = Class.forName("net.minecraft.server." + v + ".PacketPlayOutPlayerInfo");
            Class<?> enumAction = Class.forName("net.minecraft.server." + v + ".PacketPlayOutPlayerInfo$EnumPlayerInfoAction");

            Object removeAction = enumAction.getMethod("valueOf", String.class).invoke(null, "REMOVE_PLAYER");

            Object removePacket;
            try {
                Class<?> epClass = Class.forName("net.minecraft.server." + v + ".EntityPlayer");
                Object arr = java.lang.reflect.Array.newInstance(epClass, 1);
                java.lang.reflect.Array.set(arr, 0, nmsEntity);
                Constructor<?> con = infoClass.getConstructor(enumAction, arr.getClass());
                removePacket = con.newInstance(removeAction, arr);
            } catch (NoSuchMethodException e) {
                Constructor<?> con = infoClass.getConstructor(enumAction, Iterable.class);
                removePacket = con.newInstance(removeAction, Arrays.asList(nmsEntity));
            }

            sendPacket(player, removePacket);
        } catch (Exception ignored) {}
    }

    private Object createDestroyPacket(Object nmsEntity) throws Exception {
        String v = nmsVersion();
        int id = (int) nmsEntity.getClass().getMethod("getId").invoke(nmsEntity);
        Class<?> destroyClass = Class.forName("net.minecraft.server." + v + ".PacketPlayOutEntityDestroy");
        return destroyClass.getConstructor(int[].class).newInstance((Object) new int[]{id});
    }

    private void sendPacket(Player player, Object packet) throws Exception {
        String v = nmsVersion();
        Class<?> craftPlayer   = Class.forName("org.bukkit.craftbukkit." + v + ".entity.CraftPlayer");
        Object   entityPlayer  = craftPlayer.getMethod("getHandle").invoke(craftPlayer.cast(player));
        Object   conn          = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);
        Class<?> packetClass   = Class.forName("net.minecraft.server." + v + ".Packet");
        conn.getClass().getMethod("sendPacket", packetClass).invoke(conn, packet);
    }
}
