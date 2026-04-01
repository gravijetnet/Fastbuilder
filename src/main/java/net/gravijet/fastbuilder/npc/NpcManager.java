package net.gravijet.fastbuilder.npc;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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

    public NpcManager(FastBuilder plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Spawn a Map Selector NPC for a player at the given location.
     * Adjusts Y so the NPC stands on top of a solid block, not inside one.
     */
    @SuppressWarnings("deprecation")
    public void spawnNpc(Player player, Location npcLocation) {
        if (!plugin.getConfigManager().isNpcsEnabled()) return;

        despawnNpc(player.getUniqueId());

        // Fix Y: snap to integer to avoid floating-point sinking, then ensure
        // the NPC stands on top of a block rather than inside it.
        Location adjusted = npcLocation.clone();
        adjusted.setY(Math.floor(adjusted.getY()));

        // If the block at this position is solid, move up by 1
        Block blockAt = adjusted.getBlock();
        if (blockAt != null && blockAt.getType() != Material.AIR) {
            adjusted.setY(adjusted.getBlockY() + 1);
        }

        try {
            net.citizensnpcs.api.npc.NPCRegistry registry = net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
            String npcName = ColorUtil.translate(plugin.getConfigManager().getNpcName());
            net.citizensnpcs.api.npc.NPC npc = registry.createNPC(EntityType.PLAYER, npcName);

            npc.data().set("player-skin-uuid", player.getUniqueId().toString());
            npc.data().set("player-skin-name", player.getName());

            npc.spawn(adjusted);
            npc.setProtected(true);

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

        Integer playerNpcId = playerNpcs.get(player.getUniqueId());
        if (playerNpcId != null && playerNpcId == clickedNpcId) {
            plugin.getGuiManager().openMapSelector(player);
        }
    }
}
