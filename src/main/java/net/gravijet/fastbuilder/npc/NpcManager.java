package net.gravijet.fastbuilder.npc;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages Citizens NPCs for the Map Selector on each island.
 */
public class NpcManager implements Listener {

    private final FastBuilder plugin;
    private final Map<UUID, Integer> playerNpcs = new HashMap<>();

    private static final String MARKER_KEY = "fastbuilder-npc";

    public NpcManager(FastBuilder plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        cleanupOrphanedNpcs();
    }

    /** Destroy any NPCs left behind by a previous crash. */
    private void cleanupOrphanedNpcs() {
        try {
            net.citizensnpcs.api.npc.NPCRegistry registry = net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
            for (net.citizensnpcs.api.npc.NPC npc : registry) {
                if ("true".equals(npc.data().get(MARKER_KEY))) {
                    npc.destroy();
                }
            }
        } catch (NoClassDefFoundError | Exception ignored) {}
    }

    /**
     * Spawn a Map Selector NPC for a player at the given location.
     * The NPC is spawned at the exact stored location — the admin is responsible for
     * positioning it correctly during setup.
     */
    @SuppressWarnings("deprecation")
    public void spawnNpc(Player player, Location npcLocation) {
        if (!plugin.getConfigManager().isNpcsEnabled()) return;

        despawnNpc(player.getUniqueId());

        Location adjusted = npcLocation.clone();
        // Find the highest solid block at this X/Z and stand the NPC 1 block above it.
        // Walk downward from the stored Y to handle cases where the location was saved
        // slightly above or below the actual surface.
        int searchY = adjusted.getBlockY();
        org.bukkit.World npcWorld = adjusted.getWorld();
        // Search up to 5 blocks below for a solid surface
        for (int dy = 0; dy <= 5; dy++) {
            org.bukkit.block.Block below = npcWorld.getBlockAt(adjusted.getBlockX(), searchY - dy, adjusted.getBlockZ());
            if (below.getType().isSolid()) {
                adjusted.setY(searchY - dy + 1);
                break;
            }
        }
        // If the block AT the location is solid, step up so the NPC isn't inside it
        if (adjusted.getBlock().getType().isSolid()) {
            adjusted.setY(adjusted.getBlockY() + 1);
        }

        try {
            net.citizensnpcs.api.npc.NPCRegistry registry = net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
            String npcName = ColorUtil.translate(plugin.getConfigManager().getNpcName());
            net.citizensnpcs.api.npc.NPC npc = registry.createNPC(EntityType.PLAYER, npcName);

            // Do NOT set player-skin-uuid/name — that triggers a Mojang lookup which fails
            // on cracked servers. We inject the cached skin texture directly after spawn.
            npc.data().setPersistent(MARKER_KEY, "true");

            npc.spawn(adjusted);
            npc.setProtected(true);

            // Apply the skin texture directly via NMS. A nicked player's NPC wears the disguise
            // skin, not their real one — otherwise the NPC standing on their island gives the
            // nick away instantly.
            if (plugin.getSkinManager() != null && plugin.getNickManager() != null) {
                String[] skin = plugin.getNickManager().getEffectiveSkin(player.getUniqueId());
                if (skin != null) {
                    java.util.List<org.bukkit.entity.Player> viewers =
                            adjusted.getWorld().getPlayers();
                    plugin.getSkinManager().applySkinToNpc(npc, skin[0], skin[1], viewers);
                }
            }

            // Zero-tick tablist removal: hide the NPC from the tab list immediately in the same tick.
            if (npc.isSpawned() && npc.getEntity() instanceof org.bukkit.entity.Player) {
                try {
                    org.bukkit.entity.Player npcEntity = (org.bukkit.entity.Player) npc.getEntity();
                    String ver = org.bukkit.Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
                    Object nmsNpcPlayer = npcEntity.getClass().getMethod("getHandle").invoke(npcEntity);
                    Class<?> pktClass = Class.forName("net.minecraft.server." + ver + ".PacketPlayOutPlayerInfo");
                    Class<?> enumClass = Class.forName("net.minecraft.server." + ver + ".PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
                    Class<?> entityPlayerClass = Class.forName("net.minecraft.server." + ver + ".EntityPlayer");
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Object removeAction = Enum.valueOf((Class<Enum>) enumClass, "REMOVE_PLAYER");
                    Object entityPlayerArr = java.lang.reflect.Array.newInstance(entityPlayerClass, 1);
                    java.lang.reflect.Array.set(entityPlayerArr, 0, nmsNpcPlayer);
                    Object removePacket = pktClass.getDeclaredConstructors()[0].newInstance(removeAction, entityPlayerArr);
                    Class<?> packetIface = Class.forName("net.minecraft.server." + ver + ".Packet");
                    for (org.bukkit.entity.Player viewer : adjusted.getWorld().getPlayers()) {
                        Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
                        Object conn = handle.getClass().getField("playerConnection").get(handle);
                        conn.getClass().getMethod("sendPacket", packetIface).invoke(conn, removePacket);
                    }
                } catch (Exception e) {
                    plugin.getLogger().fine("Could not hide NPC from tablist: " + e.getMessage());
                }
            }

            playerNpcs.put(player.getUniqueId(), npc.getId());

            plugin.getLogger().fine("Spawned NPC for " + player.getName() + " at " + adjusted);
        } catch (NoClassDefFoundError | Exception e) {
            plugin.getLogger().warning("Citizens API not available for NPC spawn: " + e.getMessage());
        }
    }

    public void despawnNpc(UUID playerUuid) {
        Integer npcId = playerNpcs.remove(playerUuid);
        if (npcId == null) return;

        try {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);
            if (npc != null) npc.destroy();
        } catch (NoClassDefFoundError | Exception ignored) {}
    }

    public void despawnAll() {
        for (UUID uuid : new java.util.ArrayList<>(playerNpcs.keySet())) {
            despawnNpc(uuid);
        }
    }

    public boolean hasNpc(UUID playerUuid) {
        return playerNpcs.containsKey(playerUuid);
    }

    @EventHandler
    public void onNPCRightClick(net.citizensnpcs.api.event.NPCRightClickEvent event) {
        Player player = event.getClicker();
        int clickedNpcId = event.getNPC().getId();

        // Gameplay NPC → open the Map Selector GUI
        Integer playerNpcId = playerNpcs.get(player.getUniqueId());
        if (playerNpcId != null && playerNpcId == clickedNpcId) {
            plugin.getGuiManager().openMapSelector(player);
            return;
        }

        // Replay NPC → toggle first-person camera perspective
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            net.gravijet.fastbuilder.replay.ReplaySession session =
                    plugin.getReplayManager().getPlaybackSessionByNpcId(clickedNpcId);
            if (session != null && session.getViewerUuid().equals(player.getUniqueId())) {
                session.toggleNpcCamera(player);
            }
        }
    }
}
