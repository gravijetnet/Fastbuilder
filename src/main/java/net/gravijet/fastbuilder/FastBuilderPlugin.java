package de.fastbuilder;

import de.fastbuilder.command.FastBuilderCommand;
import de.fastbuilder.listener.GuiListener;
import de.fastbuilder.listener.PlayerListener;
import de.fastbuilder.manager.GameManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * FastBuilder – Bridging Practice Plugin für Spigot 1.8.8
 * Einstiegspunkt des Plugins.
 */
public final class FastBuilderPlugin extends JavaPlugin {

    // Singleton-Instanz für einfachen globalen Zugriff
    private static FastBuilderPlugin instance;

    // Zentrale Spielverwaltung
    private GameManager gameManager;

    @Override
    public void onEnable() {
        instance = this;

        // Standard-Konfiguration speichern falls nicht vorhanden
        saveDefaultConfig();

        // GameManager initialisieren (enthält alle Sub-Manager)
        gameManager = new GameManager(this);

        // Befehle registrieren
        FastBuilderCommand commandExecutor = new FastBuilderCommand(gameManager);
        getCommand("f").setExecutor(commandExecutor);

        // Event-Listener registrieren
        getServer().getPluginManager().registerEvents(new PlayerListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new GuiListener(gameManager), this);

        getLogger().info("FastBuilder Plugin wurde gestartet!");
    }

    @Override
    public void onDisable() {
        // Aufräumen: NPCs, Holograms, gelegte Blöcke entfernen
        if (gameManager != null) {
            gameManager.cleanup();
        }
        getLogger().info("FastBuilder Plugin wurde gestoppt!");
    }

    /** Globale Instanz des Plugins */
    public static FastBuilderPlugin getInstance() {
        return instance;
    }

    /** Gibt den GameManager zurück */
    public GameManager getGameManager() {
        return gameManager;
    }
}
