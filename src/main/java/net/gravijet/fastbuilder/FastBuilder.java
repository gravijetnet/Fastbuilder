package net.gravijet.fastbuilder;

import net.gravijet.fastbuilder.command.BoosterCommand;
import net.gravijet.fastbuilder.command.FastBuilderCommand;
import net.gravijet.fastbuilder.command.LeaderboardCommand;
import net.gravijet.fastbuilder.command.MapCommand;
import net.gravijet.fastbuilder.command.StatsCommand;
import net.gravijet.fastbuilder.config.ConfigManager;
import net.gravijet.fastbuilder.economy.BoosterManager;
import net.gravijet.fastbuilder.economy.CoinManager;
import net.gravijet.fastbuilder.gameplay.GameplayManager;
import net.gravijet.fastbuilder.gui.GuiManager;
import net.gravijet.fastbuilder.hologram.HologramManager;
import net.gravijet.fastbuilder.hotbar.HotbarManager;
import net.gravijet.fastbuilder.listener.CpsListener;
import net.gravijet.fastbuilder.listener.GameplayListener;
import net.gravijet.fastbuilder.listener.PlayerListener;
import net.gravijet.fastbuilder.listener.ProtectionListener;
import net.gravijet.fastbuilder.listener.SetupListener;
import net.gravijet.fastbuilder.map.MapManager;
import net.gravijet.fastbuilder.npc.NpcManager;
import net.gravijet.fastbuilder.paste.FawePaster;
import net.gravijet.fastbuilder.player.PlayerManager;
import net.gravijet.fastbuilder.replay.ReplayManager;
import net.gravijet.fastbuilder.scoreboard.FastScoreboard;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class FastBuilder extends JavaPlugin {

    private static FastBuilder instance;

    private ConfigManager configManager;
    private MapManager mapManager;
    private PlayerManager playerManager;
    private FawePaster fawePaster;
    private CoinManager coinManager;
    private BoosterManager boosterManager;
    private GuiManager guiManager;
    private HologramManager hologramManager;
    private NpcManager npcManager;
    private FastScoreboard scoreboardManager;
    private GameplayManager gameplayManager;
    private ReplayManager replayManager;
    private HotbarManager hotbarManager;
    private CpsListener cpsListener;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize configuration
        configManager = new ConfigManager(this);
        configManager.loadAll();

        // Check for WorldEdit
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
            getLogger().severe("WorldEdit (or FAWE) is required but not found! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize FAWE paster
        fawePaster = new FawePaster(this);

        // Initialize managers
        mapManager = new MapManager(this);
        playerManager = new PlayerManager(this);
        boosterManager = new BoosterManager(this);
        coinManager = new CoinManager(this);
        guiManager = new GuiManager(this);
        scoreboardManager = new FastScoreboard(this);
        gameplayManager = new GameplayManager(this);
        replayManager = new ReplayManager(this);
        hotbarManager = new HotbarManager(this);

        // Optional integrations
        if (Bukkit.getPluginManager().getPlugin("DecentHolograms") != null
                && configManager.isHologramsEnabled()) {
            hologramManager = new HologramManager(this);
            getLogger().info("DecentHolograms integration enabled.");
        }
        if (Bukkit.getPluginManager().getPlugin("Citizens") != null
                && configManager.isNpcsEnabled()) {
            npcManager = new NpcManager(this);
            getLogger().info("Citizens integration enabled.");
        }

        // Register commands
        MapCommand mapCommand = new MapCommand(this);
        getCommand("map").setExecutor(mapCommand);
        getCommand("map").setTabCompleter(mapCommand);

        StatsCommand statsCommand = new StatsCommand(this);
        getCommand("stats").setExecutor(statsCommand);
        getCommand("stats").setTabCompleter(statsCommand);

        FastBuilderCommand fbCommand = new FastBuilderCommand(this);
        getCommand("fb").setExecutor(fbCommand);
        getCommand("fb").setTabCompleter(fbCommand);

        net.gravijet.fastbuilder.command.CoinsCommand coinsCmd = new net.gravijet.fastbuilder.command.CoinsCommand(this);
        getCommand("coins").setExecutor(coinsCmd);
        getCommand("coins").setTabCompleter(coinsCmd);

        net.gravijet.fastbuilder.command.BuildCommand buildCmd = new net.gravijet.fastbuilder.command.BuildCommand(this);
        getCommand("build").setExecutor(buildCmd);

        net.gravijet.fastbuilder.command.LengthCommand lengthCmd = new net.gravijet.fastbuilder.command.LengthCommand(this);
        getCommand("length").setExecutor(lengthCmd);
        getCommand("length").setTabCompleter(lengthCmd);

        BoosterCommand boosterCmd = new BoosterCommand(this);
        getCommand("booster").setExecutor(boosterCmd);
        getCommand("booster").setTabCompleter(boosterCmd);

        LeaderboardCommand lbCmd = new LeaderboardCommand(this);
        getCommand("leaderboard").setExecutor(lbCmd);
        getCommand("leaderboard").setTabCompleter(lbCmd);

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new SetupListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GameplayListener(this), this);
        Bukkit.getPluginManager().registerEvents(new net.gravijet.fastbuilder.listener.TreeGrowthListener(), this);

        // CPS Counter (only register if DecentHolograms is available)
        if (hologramManager != null) {
            cpsListener = new CpsListener(this);
            Bukkit.getPluginManager().registerEvents(cpsListener, this);
        }

        // BungeeCord channel
        if (configManager.isBungeeEnabled()) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        }

        // Load data
        mapManager.loadMaps();

        getLogger().info("FastBuilder v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        // Shutdown managers with tasks
        if (gameplayManager != null) gameplayManager.shutdown();
        if (replayManager != null) replayManager.shutdown();
        if (coinManager != null) coinManager.shutdown();
        if (scoreboardManager != null) scoreboardManager.shutdown();

        // Save all data and shut down storage provider
        if (mapManager != null) mapManager.saveAll();
        if (playerManager != null) playerManager.shutdown();

        // Cleanup integrations
        if (cpsListener != null) cpsListener.cleanup();
        if (npcManager != null) npcManager.despawnAll();
        if (hologramManager != null) hologramManager.removeAll();

        // Unregister BungeeCord channel
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);

        getLogger().info("FastBuilder disabled.");
    }

    // --- Accessors ---

    public static FastBuilder getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public MapManager getMapManager() { return mapManager; }
    public PlayerManager getPlayerManager() { return playerManager; }
    public FawePaster getFawePaster() { return fawePaster; }
    public CoinManager getCoinManager() { return coinManager; }
    public BoosterManager getBoosterManager() { return boosterManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public HologramManager getHologramManager() { return hologramManager; }
    public NpcManager getNpcManager() { return npcManager; }
    public FastScoreboard getScoreboardManager() { return scoreboardManager; }
    public GameplayManager getGameplayManager() { return gameplayManager; }
    public ReplayManager getReplayManager() { return replayManager; }
    public HotbarManager getHotbarManager() { return hotbarManager; }
    public CpsListener getCpsListener() { return cpsListener; }
}
