package net.gravijet.fastbuilder;

import net.gravijet.fastbuilder.command.FastBuilderCommand;
import net.gravijet.fastbuilder.listener.GuiListener;
import net.gravijet.fastbuilder.listener.PlayerListener;
import net.gravijet.fastbuilder.manager.GameManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    private static Main instance;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        gameManager = new GameManager(this);

        FastBuilderCommand cmd = new FastBuilderCommand(gameManager);
        getCommand("fb").setExecutor(cmd);

        getServer().getPluginManager().registerEvents(new PlayerListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new GuiListener(gameManager), this);

        getLogger().info("FastBuilder enabled.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.cleanup();
        getLogger().info("FastBuilder disabled.");
    }

    public static Main getInstance() { return instance; }
    public GameManager getGameManager() { return gameManager; }
}
