package net.gravijet.fastbuilder.skin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Caches player skin textures (Base64 value + signature) extracted via NMS GameProfile.
 * Persists across server restarts via skins.yml.
 * Works on cracked/offline servers — no Mojang API calls at runtime.
 */
public class SkinManager {

    private final net.gravijet.fastbuilder.FastBuilder plugin;
    private final Map<UUID, String[]> skinByUuid = new HashMap<>();
    private final Map<String, UUID>   uuidByName = new HashMap<>();
    private final File skinsFile;
    private YamlConfiguration skinsConfig;

    public SkinManager(net.gravijet.fastbuilder.FastBuilder plugin) {
        this.plugin = plugin;
        this.skinsFile = new File(plugin.getDataFolder(), "skins.yml");
        load();
    }

    private void load() {
        skinsConfig = YamlConfiguration.loadConfiguration(skinsFile);
        for (String uuidStr : skinsConfig.getKeys(false)) {
            try {
                UUID uuid      = UUID.fromString(uuidStr);
                String name    = skinsConfig.getString(uuidStr + ".name", "");
                String value   = skinsConfig.getString(uuidStr + ".value", "");
                String sig     = skinsConfig.getString(uuidStr + ".signature", "");
                if (!value.isEmpty()) {
                    skinByUuid.put(uuid, new String[]{value, sig});
                    if (!name.isEmpty()) uuidByName.put(name.toLowerCase(), uuid);
                }
            } catch (Exception ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // Cache management
    // -------------------------------------------------------------------------

    public void cacheSkin(Player player) {
        try {
            String[] skin = extractSkin(player);
            if (skin != null) {
                skinByUuid.put(player.getUniqueId(), skin);
                uuidByName.put(player.getName().toLowerCase(), player.getUniqueId());
                persist(player.getUniqueId(), player.getName(), skin[0], skin[1]);
            }
        } catch (Exception ignored) {}
    }

    public String getSkinValue(UUID uuid) {
        String[] s = skinByUuid.get(uuid);
        return s != null ? s[0] : null;
    }

    public String getSkinSignature(UUID uuid) {
        String[] s = skinByUuid.get(uuid);
        return s != null ? s[1] : null;
    }

    public String[] getSkinByName(String name) {
        UUID uuid = uuidByName.get(name.toLowerCase());
        return uuid != null ? skinByUuid.get(uuid) : null;
    }

    // -------------------------------------------------------------------------
    // Skull item — apply texture without online Mojang lookup
    // -------------------------------------------------------------------------

    /**
     * Apply a skin texture to a SkullMeta by injecting a GameProfile via reflection.
     * Returns true on success.
     */
    public boolean applyTextureToSkullMeta(SkullMeta meta, String skinValue, String skinSignature) {
        if (skinValue == null || skinValue.isEmpty()) return false;
        try {
            GameProfile profile = new GameProfile(UUID.randomUUID(), "FastBuilder");
            Property prop = (skinSignature != null && !skinSignature.isEmpty())
                    ? new Property("textures", skinValue, skinSignature)
                    : new Property("textures", skinValue);
            profile.getProperties().put("textures", prop);

            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Citizens NPC — apply texture without Mojang lookup
    // -------------------------------------------------------------------------

    /**
     * Apply a skin to a spawned Citizens NPC entity.
     * Injects the texture into the NPC's NMS GameProfile and forces each viewer's client
     * to refresh the skin via remove/add player-info + entity destroy/respawn packets.
     */
    public void applySkinToNpc(net.citizensnpcs.api.npc.NPC npc, String skinValue,
                                String skinSignature, Collection<Player> viewers) {
        if (!npc.isSpawned() || !(npc.getEntity() instanceof Player)) return;
        if (skinValue == null || skinValue.isEmpty()) return;
        Player npcEntity = (Player) npc.getEntity();
        try {
            Object nmsPlayer = npcEntity.getClass().getMethod("getHandle").invoke(npcEntity);

            GameProfile profile = (GameProfile)
                    nmsPlayer.getClass().getMethod("getProfile").invoke(nmsPlayer);
            profile.getProperties().removeAll("textures");
            Property prop = (skinSignature != null && !skinSignature.isEmpty())
                    ? new Property("textures", skinValue, skinSignature)
                    : new Property("textures", skinValue);
            profile.getProperties().put("textures", prop);

            String ver = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> packetIface = Class.forName("net.minecraft.server." + ver + ".Packet");

            for (Player viewer : viewers) {
                try {
                    Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
                    Object conn   = handle.getClass().getField("playerConnection").get(handle);
                    sendSkinRefreshPackets(ver, conn, packetIface, nmsPlayer);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static String[] extractSkin(Player player) throws Exception {
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        GameProfile profile = (GameProfile)
                handle.getClass().getMethod("getProfile").invoke(handle);
        Collection<Property> textures = profile.getProperties().get("textures");
        if (textures == null || textures.isEmpty()) return null;
        Property prop = textures.iterator().next();
        String value = prop.getValue();
        String sig   = prop.getSignature();
        if (value == null || value.isEmpty()) return null;
        return new String[]{value, sig != null ? sig : ""};
    }

    private static void sendSkinRefreshPackets(String ver, Object conn,
                                                Class<?> packetIface, Object nmsPlayer)
            throws Exception {
        Class<?> pktInfoClass    = Class.forName("net.minecraft.server." + ver + ".PacketPlayOutPlayerInfo");
        Class<?> enumActionClass = Class.forName("net.minecraft.server." + ver + ".PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
        Class<?> entityPlayerCls = Class.forName("net.minecraft.server." + ver + ".EntityPlayer");

        Object[] enumValues  = (Object[]) enumActionClass.getMethod("values").invoke(null);
        Object addAction     = enumValues[0]; // ADD_PLAYER
        Object removeAction  = enumValues[4]; // REMOVE_PLAYER

        Object entityArr = java.lang.reflect.Array.newInstance(entityPlayerCls, 1);
        java.lang.reflect.Array.set(entityArr, 0, nmsPlayer);

        Object removePacket = pktInfoClass.getDeclaredConstructors()[0].newInstance(removeAction, entityArr);
        Object addPacket    = pktInfoClass.getDeclaredConstructors()[0].newInstance(addAction,    entityArr);
        sendPacket(conn, packetIface, removePacket);
        sendPacket(conn, packetIface, addPacket);

        // Destroy and re-spawn entity so the client applies the updated skin texture
        int entityId = ((Number) nmsPlayer.getClass().getMethod("getId").invoke(nmsPlayer)).intValue();
        Object destroyPacket = Class.forName("net.minecraft.server." + ver + ".PacketPlayOutEntityDestroy")
                .getConstructor(int[].class)
                .newInstance(new Object[]{new int[]{entityId}});
        sendPacket(conn, packetIface, destroyPacket);

        Class<?> entityHumanCls = Class.forName("net.minecraft.server." + ver + ".EntityHuman");
        Object spawnPacket = Class.forName("net.minecraft.server." + ver + ".PacketPlayOutNamedEntitySpawn")
                .getConstructor(entityHumanCls)
                .newInstance(nmsPlayer);
        sendPacket(conn, packetIface, spawnPacket);
    }

    private static void sendPacket(Object conn, Class<?> packetIface, Object packet)
            throws Exception {
        conn.getClass().getMethod("sendPacket", packetIface).invoke(conn, packet);
    }

    private void persist(UUID uuid, String name, String value, String signature) {
        String key = uuid.toString();
        skinsConfig.set(key + ".name",      name);
        skinsConfig.set(key + ".value",     value);
        skinsConfig.set(key + ".signature", signature);
        try {
            skinsConfig.save(skinsFile);
        } catch (IOException ignored) {}
    }
}
