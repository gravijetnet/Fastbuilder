package de.fastbuilder.manager;

import de.fastbuilder.model.Island;
import net.minecraft.server.v1_8_R3.EntityVillager;
import net.minecraft.server.v1_8_R3.PathfinderGoalSelector;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftVillager;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Verwaltet die NPCs (Villager) pro Spieler-Insel.
 *
 * Jede Insel hat einen Villager der:
 *  - Keiner KI (Pathfinder) hat → steht still
 *  - Unverwundbar ist
 *  - Einen Custom-Name trägt
 *  - Beim Rechtsklick das Distanz-GUI öffnet
 *
 * NPC-Erkennung erfolgt über UUID-Mapping.
 */
public class NpcManager {

    /** Mapping: NPC-UUID → Spieler-UUID (um bei Interaktion den Spieler zu kennen) */
    private final Map<UUID, UUID> npcToPlayer = new HashMap<>();

    /** Mapping: Spieler-UUID → NPC-Villager */
    private final Map<UUID, Villager> playerToNpc = new HashMap<>();

    /**
     * Spawnt einen NPC auf der gegebenen Insel.
     */
    public void spawnNpc(Island island) {
        UUID playerUuid = island.getPlayerUuid();

        // Alten NPC entfernen falls vorhanden
        removeNpc(playerUuid);

        Location npcLoc = island.getNpcLocation();
        npcLoc.setYaw(180f); // NPC schaut zur Startplattform

        // Villager spawnen
        Villager villager = (Villager) npcLoc.getWorld()
                .spawnEntity(npcLoc, EntityType.VILLAGER);
        villager.setCustomName(ChatColor.GREEN + "» Distanz wählen «");
        villager.setCustomNameVisible(true);
        villager.setProfession(Villager.Profession.LIBRARIAN);
        // Hohe HP damit er nicht schnell stirbt
        villager.setMaxHealth(2048);
        villager.setHealth(2048);
        villager.setRemoveWhenFarAway(false);

        // KI deaktivieren via NMS (Pathfinder-Goals leeren)
        disableAI(villager);

        // Mappings speichern
        npcToPlayer.put(villager.getUniqueId(), playerUuid);
        playerToNpc.put(playerUuid, villager);
    }

    /**
     * Entfernt den NPC des Spielers.
     */
    public void removeNpc(UUID playerUuid) {
        Villager villager = playerToNpc.remove(playerUuid);
        if (villager != null && !villager.isDead()) {
            npcToPlayer.remove(villager.getUniqueId());
            villager.remove();
        }
    }

    /**
     * Entfernt alle NPCs (beim Plugin-Stop).
     */
    public void removeAll() {
        for (UUID uuid : new ArrayList<>(playerToNpc.keySet())) {
            removeNpc(uuid);
        }
    }

    /**
     * Gibt die UUID des Spielers zurück, dem der NPC mit der angegebenen UUID gehört.
     * Gibt null zurück, wenn die Entity kein verwalteter NPC ist.
     */
    public UUID getOwnerOfNpc(UUID npcUuid) {
        return npcToPlayer.get(npcUuid);
    }

    /**
     * Gibt zurück ob die Entity-UUID ein verwalteter NPC ist.
     */
    public boolean isNpc(UUID entityUuid) {
        return npcToPlayer.containsKey(entityUuid);
    }

    // ─────────────────────────────────────────────────────────────
    //  PRIVATE HELFER
    // ─────────────────────────────────────────────────────────────

    /**
     * Deaktiviert die KI des Villagers via NMS indem alle Pathfinder-Goals
     * aus den internen Sets entfernt werden.
     *
     * In 1.8.8 gibt es noch kein Bukkit-API `setAI()` – daher Reflection.
     */
    private void disableAI(Villager villager) {
        try {
            EntityVillager nmsVillager = ((CraftVillager) villager).getHandle();

            // goalSelector und targetSelector sind PathfinderGoalSelector-Felder
            // die interne Sets 'a' (Prioritized Goals) und 'b' (Running Goals) haben
            clearGoalSelector(nmsVillager.goalSelector);
            clearGoalSelector(nmsVillager.targetSelector);
        } catch (Exception e) {
            // Falls Reflection scheitert: Kein Crash, NPC hat nur noch KI
            e.printStackTrace();
        }
    }

    /**
     * Leert die internen Sets eines PathfinderGoalSelectors via Reflection.
     */
    private void clearGoalSelector(PathfinderGoalSelector selector) throws Exception {
        // 'a' = alle registrierten Goals, 'b' = aktuell ausgeführte Goals
        Field aField = PathfinderGoalSelector.class.getDeclaredField("a");
        Field bField = PathfinderGoalSelector.class.getDeclaredField("b");
        aField.setAccessible(true);
        bField.setAccessible(true);
        ((java.util.Set<?>) aField.get(selector)).clear();
        ((java.util.Set<?>) bField.get(selector)).clear();
    }
}
