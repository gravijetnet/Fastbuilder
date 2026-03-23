package de.fastbuilder.util;

import net.minecraft.server.v1_8_R3.ChatComponentText;
import net.minecraft.server.v1_8_R3.PacketPlayOutChat;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * Hilfsmethoden für die ActionBar-Anzeige über der Hotbar.
 * Nutzt NMS (Net Minecraft Server) für Spigot 1.8.8, da die offizielle API
 * diese Funktion damals noch nicht unterstützte.
 */
public final class ActionBarUtil {

    // Utility-Klasse – kein Instanziieren erlaubt
    private ActionBarUtil() {}

    /**
     * Sendet eine Nachricht an die ActionBar des Spielers.
     *
     * @param player  Empfänger
     * @param message Nachricht (§-Farbcodes unterstützt)
     */
    public static void sendActionBar(Player player, String message) {
        try {
            // Packet vom Typ "2" = ActionBar (0 = Chat, 1 = System, 2 = ActionBar)
            PacketPlayOutChat packet = new PacketPlayOutChat(
                    new ChatComponentText(message),
                    (byte) 2
            );
            ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
        } catch (Exception e) {
            // Fallback: Sendet nichts – lieber still scheitern als crashen
        }
    }
}
