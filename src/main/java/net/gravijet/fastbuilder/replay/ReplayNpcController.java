package net.gravijet.fastbuilder.replay;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Manages the Citizens NPC used during replay playback and NMS packet dispatch
 * for NPC head rotation, metadata, arm swing, equipment, and camera follow.
 */
class ReplayNpcController {

    private final FastBuilder plugin;
    private final ReplayData replayData;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;

    private int npcId = -1;

    ReplayNpcController(FastBuilder plugin, ReplayData replayData,
                        int offsetX, int offsetY, int offsetZ) {
        this.plugin = plugin;
        this.replayData = replayData;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    int getNpcId() { return npcId; }

    // -------------------------------------------------------------------------
    // NPC lifecycle
    // -------------------------------------------------------------------------

    void spawn(Player viewer) {
        try {
            String tag = replayData.getPlayerDisplayTag();
            String npcName = (tag != null && !tag.isEmpty())
                    ? tag
                    : plugin.getReplayManager().getReplayDisplayName(replayData);

            net.citizensnpcs.api.npc.NPCRegistry registry = net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
            net.citizensnpcs.api.npc.NPC npc = registry.createNPC(
                    org.bukkit.entity.EntityType.PLAYER, npcName);

            // Do not set player-skin-uuid/name — that triggers a Mojang lookup which fails on
            // cracked servers. The stored skin texture is injected directly after spawn below.

            MapData map = plugin.getMapManager().getMap(replayData.getMapName());
            if (map != null && map.getWorld() != null && !replayData.getFrames().isEmpty()) {
                ReplayFrame first = replayData.getFrames().get(0);
                Location spawnLoc = new Location(map.getWorld(),
                        first.getX() + offsetX,
                        first.getY() + offsetY,
                        first.getZ() + offsetZ,
                        first.getYaw(), first.getPitch());
                npc.spawn(spawnLoc);
            }

            npcId = npc.getId();

            // Apply stored skin texture directly, bypassing Mojang API
            if (plugin.getSkinManager() != null && !replayData.getSkinValue().isEmpty()) {
                java.util.List<Player> viewers = new java.util.ArrayList<>();
                if (viewer != null) viewers.add(viewer);
                plugin.getSkinManager().applySkinToNpc(npc, replayData.getSkinValue(),
                        replayData.getSkinSignature(), viewers);
            }

            try {
                npc.getNavigator().cancelNavigation();
            } catch (Exception ignored) {}

        } catch (NoClassDefFoundError | Exception e) {
            plugin.getLogger().warning("Could not spawn replay NPC: " + e.getMessage());
        }
    }

    void move(Location location) {
        if (npcId < 0) return;
        try {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);
            if (npc != null && npc.isSpawned()) {
                npc.getEntity().teleport(location);
            }
        } catch (NoClassDefFoundError | Exception ignored) {}
    }

    void despawn() {
        if (npcId < 0) return;
        try {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);
            if (npc != null) npc.destroy();
        } catch (NoClassDefFoundError | Exception ignored) {}
        npcId = -1;
    }

    // -------------------------------------------------------------------------
    // NMS packet helpers
    // -------------------------------------------------------------------------

    void applyNmsState(Player viewer, ReplayFrame frame) {
        if (npcId < 0) return;
        try {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);
            if (npc == null || !npc.isSpawned()) return;

            org.bukkit.entity.Entity entity = npc.getEntity();
            if (!(entity instanceof org.bukkit.entity.Player)) return;

            String ver = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Object nmsEntity    = entity.getClass().getMethod("getHandle").invoke(entity);
            Object viewerHandle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object viewerConn   = viewerHandle.getClass().getField("playerConnection").get(viewerHandle);
            Class<?> packetIface = nmsClass(ver, "Packet");
            Class<?> entityClass = nmsClass(ver, "Entity");

            // Head yaw
            byte headYawByte = (byte) (frame.getHeadYaw() * 256.0F / 360.0F);
            Object headPacket = nmsClass(ver, "PacketPlayOutEntityHeadRotation")
                    .getConstructor(entityClass, byte.class)
                    .newInstance(nmsEntity, headYawByte);
            sendPacket(viewerConn, packetIface, headPacket);

            // Sneak / sprint flags
            Object dw = nmsEntity.getClass().getMethod("getDataWatcher").invoke(nmsEntity);
            byte flags = 0;
            try {
                flags = ((Number) dw.getClass().getMethod("getByte", int.class).invoke(dw, 0)).byteValue();
            } catch (Exception ignored) {}
            if (frame.isSneaking())  flags |= 0x02; else flags &= ~0x02;
            if (frame.isSprinting()) flags |= 0x08; else flags &= ~0x08;
            dw.getClass().getMethod("watch", int.class, Object.class).invoke(dw, 0, flags);

            int entityId = ((Number) nmsEntity.getClass().getMethod("getId").invoke(nmsEntity)).intValue();
            Object metaPacket = nmsClass(ver, "PacketPlayOutEntityMetadata")
                    .getConstructor(int.class, nmsClass(ver, "DataWatcher"), boolean.class)
                    .newInstance(entityId, dw, false);
            sendPacket(viewerConn, packetIface, metaPacket);

            // Arm swing
            if (frame.isSwingingArm()) {
                Object animPacket = nmsClass(ver, "PacketPlayOutAnimation")
                        .getConstructor(entityClass, int.class)
                        .newInstance(nmsEntity, 0);
                sendPacket(viewerConn, packetIface, animPacket);
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    void applyHandItem(Player viewer, int itemId, byte itemData) {
        if (npcId < 0) return;
        try {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);
            if (npc == null || !npc.isSpawned()) return;

            org.bukkit.entity.Entity entity = npc.getEntity();
            if (!(entity instanceof org.bukkit.entity.Player)) return;

            String ver = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Object nmsEntity    = entity.getClass().getMethod("getHandle").invoke(entity);
            Object viewerHandle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object viewerConn   = viewerHandle.getClass().getField("playerConnection").get(viewerHandle);

            org.bukkit.Material mat = org.bukkit.Material.getMaterial(itemId);
            if (mat == null || mat == org.bukkit.Material.AIR) return;
            org.bukkit.inventory.ItemStack bukkit = new org.bukkit.inventory.ItemStack(mat, 1, itemData);

            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + ver + ".inventory.CraftItemStack");
            Object nmsItem = craftItemStackClass.getMethod("asNMSCopy", org.bukkit.inventory.ItemStack.class)
                    .invoke(null, bukkit);

            int entityId = ((Number) nmsEntity.getClass().getMethod("getId").invoke(nmsEntity)).intValue();
            Class<?> itemStackClass = nmsClass(ver, "ItemStack");
            Object equipPacket = nmsClass(ver, "PacketPlayOutEntityEquipment")
                    .getConstructor(int.class, int.class, itemStackClass)
                    .newInstance(entityId, 0, nmsItem);
            sendPacket(viewerConn, nmsClass(ver, "Packet"), equipPacket);
        } catch (Exception ignored) {}
    }

    void sendCameraFollowPacket(Player viewer, boolean followNpc) {
        if (npcId < 0 && followNpc) return;
        try {
            String ver = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Object viewerHandle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object viewerConn   = viewerHandle.getClass().getField("playerConnection").get(viewerHandle);
            Class<?> packetIface = nmsClass(ver, "Packet");

            Object targetNmsEntity;
            if (followNpc) {
                net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);
                if (npc == null || !npc.isSpawned()) return;
                targetNmsEntity = npc.getEntity().getClass().getMethod("getHandle").invoke(npc.getEntity());
            } else {
                targetNmsEntity = viewerHandle;
            }

            Class<?> entityClass = nmsClass(ver, "Entity");
            Object camPacket = nmsClass(ver, "PacketPlayOutCamera")
                    .getConstructor(entityClass)
                    .newInstance(targetNmsEntity);
            sendPacket(viewerConn, packetIface, camPacket);
        } catch (Exception ignored) {}
    }

    private static Class<?> nmsClass(String version, String name) throws ClassNotFoundException {
        return Class.forName("net.minecraft.server." + version + "." + name);
    }

    private static void sendPacket(Object conn, Class<?> packetIface, Object packet) throws Exception {
        conn.getClass().getMethod("sendPacket", packetIface).invoke(conn, packet);
    }
}
