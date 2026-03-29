package net.gravijet.fastbuilder.npc;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
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
 * NPCs display the player's own skin and open the map selector on right-click.
 */
public class NpcManager implements Listener {

    private final FastBuilder plugin;
    private final Map<UUID, Integer> playerNpcs = new HashMap<>(); // playerUUID -> NPC id

    public NpcManager(FastBuilder plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Spawn a Map Selector NPC for a player at the given location.
     * The NPC uses the player's skin.
     */
    public void spawnNpc(Player player, Location location) {
        if (!plugin.getConfigManager().isNpcsEnabled()) return;

        // Remove existing NPC first
        despawnNpc(player.getUniqueId());

        try {
            net.citizensnpcs.api.npc.NPCRegistry registry = net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
            String npcName = ColorUtil.translate(plugin.getConfigManager().getNpcName());
            net.citizensnpcs.api.npc.NPC npc = registry.createNPC(EntityType.PLAYER, npcName);

            // Set the NPC skin to match the player
            npc.data().set("player-skin-uuid", player.getUniqueId().toString());
            npc.data().set("player-skin-name", player.getName());

            // Spawn the NPC slightly in front of the player's spawn
            Location spawnLoc = location.clone();
            spawnLoc.add(spawnLoc.getDirection().normalize().multiply(2));
            spawnLoc.setY(location.getY());

            npc.spawn(spawnLoc);
            npc.setProtected(true);

            playerNpcs.put(player.getUniqueId(), npc.getId());

            plugin.getLogger().fine("Spawned NPC for " + player.getName() + " at " + spawnLoc);
        } catch (NoClassDefFoundError | Exception e) {
            plugin.getLogger().warning("Citizens API not available for NPC spawn: " + e.getMessage());
        }
    }

    /**
     * Despawn the Map Selector NPC for a player.
     */
    public void despawnNpc(UUID playerUuid) {
        Integer npcId = playerNpcs.remove(playerUuid);
        if (npcId == null) return;

        try {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);
            if (npc != null) {
                npc.destroy();
            }
        } catch (NoClassDefFoundError | Exception ignored) {}
    }

    /**
     * Despawn all managed NPCs.
     */
    public void despawnAll() {
        for (UUID uuid : new java.util.ArrayList<>(playerNpcs.keySet())) {
            despawnNpc(uuid);
        }
    }

    public boolean hasNpc(UUID playerUuid) {
        return playerNpcs.containsKey(playerUuid);
    }

    /**
     * Handle NPC right-click via Citizens events.
     */
    @EventHandler
    public void onNPCRightClick(net.citizensnpcs.api.event.NPCRightClickEvent event) {
        Player player = event.getClicker();
        int clickedNpcId = event.getNPC().getId();

        // Check if this NPC is one of our managed NPCs
        Integer playerNpcId = playerNpcs.get(player.getUniqueId());
        if (playerNpcId != null && playerNpcId == clickedNpcId) {
            // Open map selector for the player
            plugin.getGuiManager().openMapSelector(player);
        }
    }
}
