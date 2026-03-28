package net.gravijet.fastbuilder.util;

import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ActionBarUtil {

    private ActionBarUtil() {}

    public static void send(Player player, String message) {
        try {
            String version = player.getClass().getPackage().getName().split("\\.")[3];

            Class<?> craftPlayerClass    = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            Class<?> chatTextClass       = Class.forName("net.minecraft.server." + version + ".ChatComponentText");
            Class<?> iChatClass          = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent");
            Class<?> packetClass         = Class.forName("net.minecraft.server." + version + ".PacketPlayOutChat");
            Class<?> basePacketClass     = Class.forName("net.minecraft.server." + version + ".Packet");

            Object chatComponent = chatTextClass.getConstructor(String.class).newInstance(message);
            Constructor<?> packetCtor = packetClass.getConstructor(iChatClass, byte.class);
            Object packet = packetCtor.newInstance(chatComponent, (byte) 2);

            Object craftPlayer = craftPlayerClass.cast(player);
            Object nmsPlayer = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);
            Field connectionField = nmsPlayer.getClass().getField("playerConnection");
            Object playerConnection = connectionField.get(nmsPlayer);
            Method sendPacket = playerConnection.getClass().getMethod("sendPacket", basePacketClass);
            sendPacket.invoke(playerConnection, packet);
        } catch (Exception ignored) {}
    }
}
