package net.gravijet.fastbuilder.util;

import net.minecraft.server.v1_8_R3.ChatComponentText;
import net.minecraft.server.v1_8_R3.PacketPlayOutChat;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

public final class ActionBarUtil {

    private ActionBarUtil() {}

    public static void send(Player player, String message) {
        try {
            PacketPlayOutChat packet = new PacketPlayOutChat(
                    new ChatComponentText(message), (byte) 2);
            ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
        } catch (Exception ignored) {}
    }
}
