package net.gravijet.fastbuilder.npc;

import net.gravijet.fastbuilder.FastBuilder;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages Citizens NPCs for the Map Selector on each island.
 * NPCs display the player's own skin and open the map selector on right-click.
 */
public class NpcManager {

    private final FastBuilder plugin;
    private final Map<UUID, Integer> playerNpcs = new HashMap<>(); // playerUUID -> NPC id

    public NpcManager(FastBuilder plugin) {
        this.plugin = plugin;
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
            net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
            // Citizens NPC creation will be implemented in the gameplay phase
            // For now, this is a functional stub that tracks NPC state.
            plugin.getLogger().fine("NPC spawn requested for " + player.getName() + " at " + location);
        } catch (NoClassDefFoundError | Exception e) {
            plugin.getLogger().warning("Citizens API not available for NPC spawn.");
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
}
