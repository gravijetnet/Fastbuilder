package net.gravijet.fastbuilder.manager;

import net.gravijet.fastbuilder.model.MapTemplate;

import java.util.*;

/**
 * Manages all loaded {@link MapTemplate}s and tracks which map each player
 * is currently playing on.
 */
public class MapManager {

    private final SchematicManager schematicManager;

    /** All loaded templates, keyed by name. */
    private final Map<String, MapTemplate> templates = new LinkedHashMap<>();

    /** Player UUID → currently selected template name (null = quick-practice). */
    private final Map<UUID, String> playerMap = new HashMap<>();

    public MapManager(SchematicManager schematicManager) {
        this.schematicManager = schematicManager;
        reload();
    }

    // ── Templates ─────────────────────────────────────────────────

    public void reload() {
        templates.clear();
        for (MapTemplate t : schematicManager.loadAll()) {
            templates.put(t.getName(), t);
        }
    }

    public void registerTemplate(MapTemplate template) {
        templates.put(template.getName(), template);
    }

    public MapTemplate getTemplate(String name) {
        return templates.get(name);
    }

    public Collection<MapTemplate> getAllTemplates() {
        return Collections.unmodifiableCollection(templates.values());
    }

    public boolean hasTemplates() {
        return !templates.isEmpty();
    }

    public void removeTemplate(String name) {
        templates.remove(name);
    }

    // ── Player assignment ─────────────────────────────────────────

    /** Returns the template selected by this player, or null for quick-practice. */
    public MapTemplate getPlayerTemplate(UUID uuid) {
        String name = playerMap.get(uuid);
        return name == null ? null : templates.get(name);
    }

    public void setPlayerTemplate(UUID uuid, String templateName) {
        if (templateName == null) playerMap.remove(uuid);
        else playerMap.put(uuid, templateName);
    }

    public void clearPlayer(UUID uuid) {
        playerMap.remove(uuid);
    }

    public SchematicManager getSchematicManager() {
        return schematicManager;
    }
}
