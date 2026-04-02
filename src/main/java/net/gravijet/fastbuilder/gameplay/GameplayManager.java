package net.gravijet.fastbuilder.gameplay;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Manages active bridging runs: timer, finish detection, fall detection, session bests.
 */
public class GameplayManager {

    private final FastBuilder plugin;
    private final Map<UUID, RunSession> activeSessions = new HashMap<>();

    private static final int PRACTICE_BLOCK_ID = 159; // STAINED_CLAY
    private static final byte PRACTICE_BLOCK_DATA = 5; // Lime

    private int actionbarTaskId = -1;

    private final java.util.Set<UUID> finishCooldown = new java.util.HashSet<>();
    private final java.util.Set<UUID> buildModePlayers = new java.util.HashSet<>();

    // Global session best: best run across all online players this session
    private long globalSessionBestTime = -1;
    private String globalSessionBestPlayer = null;

    public GameplayManager(FastBuilder plugin) {
        this.plugin = plugin;
        startActionbarTask();
    }

    public RunSession createSession(UUID uuid, String mapName, int islandIndex) {
        RunSession session = new RunSession(uuid, mapName, islandIndex);
        activeSessions.put(uuid, session);
        return session;
    }

    public RunSession getSession(UUID uuid) {
        return activeSessions.get(uuid);
    }

    public void removeSession(UUID uuid) {
        activeSessions.remove(uuid);
    }

    /**
     * Called when a player places a block. Starts the timer if not already running.
     * Detects practice blocks by material (STAINED_CLAY:5 = lime).
     */
    @SuppressWarnings("deprecation")
    public void onBlockPlace(Player player, Block block, org.bukkit.block.BlockState replacedState) {
        RunSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        if (session.isFinished()) {
            resetRun(player);
            session = activeSessions.get(player.getUniqueId());
            if (session == null) return;
        }

        if (!session.isRunning()) {
            // Block the timer start if there are un-cleared practice blocks
            if (!session.isPracticeMode() && session.hasPracticeBlocks()) {
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                        + "&cClear your practice blocks before starting a real run."));
                return;
            }
            session.start();
            if (plugin.getReplayManager() != null) {
                plugin.getReplayManager().startRecording(player, session.getMapName(), session.getIslandIndex());
            }
        }

        // Determine if this is a practice block by material
        boolean isPractice = session.isPracticeMode()
                && block.getTypeId() == PRACTICE_BLOCK_ID
                && block.getData() == PRACTICE_BLOCK_DATA;

        int origTypeId = replacedState != null ? replacedState.getTypeId() : 0;
        byte origData = replacedState != null ? replacedState.getRawData() : (byte) 0;
        session.addPlacedBlock(block.getLocation(), isPractice, origTypeId, origData);

        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().checkAutoRefill(player);
        }

        if (plugin.getReplayManager() != null) {
            plugin.getReplayManager().recordBlockPlace(player.getUniqueId(),
                    block.getLocation(), block.getTypeId(), block.getData());
        }
    }

    /**
     * Called when a player steps on a pressure plate in the finish zone.
     */
    public void onFinish(Player player) {
        UUID uuid = player.getUniqueId();
        if (finishCooldown.contains(uuid)) return;
        finishCooldown.add(uuid);

        RunSession session = activeSessions.get(uuid);
        if (session == null || !session.isRunning() || session.isFinished()) {
            finishCooldown.remove(uuid);
            return;
        }

        long time = session.finish();

        if (plugin.getReplayManager() != null) {
            plugin.getReplayManager().stopRecording(player.getUniqueId(), true);
        }

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        session.addSessionBest(time);

        // Update global session best
        if (globalSessionBestTime < 0 || time < globalSessionBestTime) {
            globalSessionBestTime = time;
            globalSessionBestPlayer = player.getName();
        }

        if (!session.isPracticeMode()) {
            PlayerData.MapStats stats = data.getOrCreateStats(session.getMapName());
            stats.totalAttempts++;
            stats.successfulAttempts++;

            boolean isNewPB = !stats.hasBestTime() || time < stats.bestTime;
            long oldBest = stats.bestTime;
            if (isNewPB) stats.bestTime = time;

            int coins = plugin.getCoinManager().awardCompletionCoins(player, time);

            String prefix = plugin.getConfigManager().getPrefix();
            if (isNewPB) {
                List<String> messages = plugin.getConfigManager().getEndPBMessages();
                for (String line : messages) {
                    line = line.replace("%time%", TimeUtil.formatTime(time))
                            .replace("%pb%", TimeUtil.formatTime(time))
                            .replace("%difference%", oldBest > 0
                                    ? TimeUtil.formatDifference(time, oldBest) : "N/A")
                            .replace("%prefix%", prefix);
                    player.sendMessage(ColorUtil.translate(line));
                }
            } else {
                List<String> messages = plugin.getConfigManager().getEndTimeMessages();
                for (String line : messages) {
                    line = line.replace("%time%", TimeUtil.formatTime(time))
                            .replace("%pb%", TimeUtil.formatTime(stats.bestTime))
                            .replace("%difference%", TimeUtil.formatDifference(time, stats.bestTime))
                            .replace("%prefix%", prefix);
                    player.sendMessage(ColorUtil.translate(line));
                }
            }

            String title = plugin.getConfigManager().getTitle();
            String subtitle = plugin.getConfigManager().getSubtitle();
            if (title != null && !title.isEmpty()) {
                title = title.replace("%time%", TimeUtil.formatTime(time))
                        .replace("%coins%", String.valueOf(coins));
                subtitle = subtitle != null ? subtitle.replace("%time%", TimeUtil.formatTime(time))
                        .replace("%coins%", String.valueOf(coins)) : "";
                player.sendTitle(ColorUtil.translate(title), ColorUtil.translate(subtitle));
            }

            if (plugin.getHologramManager() != null) {
                plugin.getHologramManager().updateHologram(session.getMapName(), session.getIslandIndex(), player);
            }
        } else {
            String practiceTitle = "&6&lPractice: &f" + TimeUtil.formatTime(time);
            player.sendTitle(ColorUtil.translate(practiceTitle), ColorUtil.translate("&7Time not saved"));
        }

        // Massive celebration
        launchCelebration(player.getLocation());

        plugin.getScoreboardManager().updateScoreboard(player);

        new BukkitRunnable() {
            @Override
            public void run() {
                finishCooldown.remove(uuid);
                if (player.isOnline()) resetRun(player);
            }
        }.runTaskLater(plugin, 40L);
    }

    /**
     * Launch a massive firework celebration with intense sounds.
     */
    private void launchCelebration(final Location location) {
        final Random rand = new Random();

        // Launch 8 fireworks spread over ~3 seconds (firework sounds only, as on mcplayhd)
        for (int wave = 0; wave < 8; wave++) {
            final int delay = wave * 7; // ~0.35s between waves
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    // Center firework
                    spawnFirework(location, rand);
                    // Two offset fireworks per wave
                    Location off1 = location.clone().add(
                            (rand.nextDouble() - 0.5) * 6, 0, (rand.nextDouble() - 0.5) * 6);
                    Location off2 = location.clone().add(
                            (rand.nextDouble() - 0.5) * 6, 0, (rand.nextDouble() - 0.5) * 6);
                    spawnFirework(off1, rand);
                    spawnFirework(off2, rand);
                }
            }, delay);
        }
    }

    private void spawnFirework(Location location, Random rand) {
        try {
            Firework fw = location.getWorld().spawn(location, Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            Color[] colors = {Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.AQUA, Color.BLUE, Color.PURPLE, Color.WHITE};
            Color primary = colors[rand.nextInt(colors.length)];
            Color fade = colors[rand.nextInt(colors.length)];
            FireworkEffect.Type[] types = {
                FireworkEffect.Type.BALL_LARGE,
                FireworkEffect.Type.BALL,
                FireworkEffect.Type.STAR,
                FireworkEffect.Type.BURST
            };
            meta.addEffect(FireworkEffect.builder()
                    .withColor(primary, Color.WHITE)
                    .withFade(fade)
                    .with(types[rand.nextInt(types.length)])
                    .flicker(true)
                    .trail(true)
                    .build());
            meta.setPower(1 + rand.nextInt(2));
            fw.setFireworkMeta(meta);
        } catch (Exception ignored) {}
    }

    /**
     * Called when a player falls off their island.
     */
    public void onFall(Player player) {
        RunSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        if (session.isRunning()) {
            if (plugin.getReplayManager() != null) {
                plugin.getReplayManager().stopRecording(player.getUniqueId(), false);
            }

            if (!session.isPracticeMode()) {
                PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
                if (data != null) {
                    PlayerData.MapStats stats = data.getOrCreateStats(session.getMapName());
                    stats.totalAttempts++;
                }
            }
        }

        resetRun(player);
    }

    /**
     * Reset a player's current run: clear placed blocks (keep practice blocks),
     * teleport to spawn.
     */
    public void resetRun(Player player) {
        RunSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        MapData map = plugin.getMapManager().getMap(session.getMapName());
        if (map == null) return;

        PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        String animation = pData != null ? pData.getSelectedAnimation() : "NONE";

        // Capture copies BEFORE reset clears the session data
        List<Location> blocksCopy = new ArrayList<>(session.getPlacedBlocks());
        List<Location> practiceBlocksCopy = new ArrayList<>(session.getPracticeBlocks());
        Map<String, int[]> origStatesCopy = new HashMap<>(session.getOriginalBlockStates());
        java.util.List<Long> bests = new java.util.ArrayList<>(session.getSessionBests());
        boolean practice = session.isPracticeMode();

        session.reset();

        clearBlocksWithAnimation(player, blocksCopy, practiceBlocksCopy, origStatesCopy, practice, animation);

        if (!bests.isEmpty()) session.addSessionBest(bests.get(0));
        session.setPracticeMode(practice);

        player.teleport(map.getIslandSpawn(session.getIslandIndex()));

        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().giveItems(player);
        }

        plugin.getScoreboardManager().updateScoreboard(player);
    }

    @SuppressWarnings("deprecation")
    private void clearBlocksWithAnimation(Player player, List<Location> blocks,
                                           List<Location> practiceBlocks,
                                           Map<String, int[]> origStates,
                                           boolean isPracticeMode, String animation) {
        if ("SLIDE_DOWN".equalsIgnoreCase(animation)) {
            List<Location> toClear = new ArrayList<>();
            for (Location loc : blocks) {
                if (isPracticeMode && practiceBlocks.contains(loc)) continue;
                toClear.add(loc);
            }
            toClear.sort((a, b) -> b.getBlockY() - a.getBlockY());
            final int[] idx = {0};
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    int batch = 3;
                    for (int i = 0; i < batch && idx[0] < toClear.size(); i++, idx[0]++) {
                        Location loc = toClear.get(idx[0]);
                        Block block = loc.getBlock();
                        if (block != null) {
                            String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
                            int[] orig = origStates.get(key);
                            if (orig != null && orig[0] != 0) block.setTypeIdAndData(orig[0], (byte) orig[1], false);
                            else block.setType(Material.AIR);
                        }
                    }
                    if (idx[0] >= toClear.size()) this.cancel();
                }
            }.runTaskTimer(plugin, 0L, 1L);
        } else if ("EXPLODE".equalsIgnoreCase(animation)) {
            for (Location loc : blocks) {
                if (isPracticeMode && practiceBlocks.contains(loc)) continue;
                try {
                    loc.getWorld().playEffect(loc, org.bukkit.Effect.STEP_SOUND, loc.getBlock().getTypeId());
                } catch (Exception ignored) {}
                Block block = loc.getBlock();
                if (block != null) {
                    String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
                    int[] orig = origStates.get(key);
                    if (orig != null && orig[0] != 0) block.setTypeIdAndData(orig[0], (byte) orig[1], false);
                    else block.setType(Material.AIR);
                }
            }
        } else {
            // NONE: instant clear
            for (Location loc : blocks) {
                if (isPracticeMode && practiceBlocks.contains(loc)) continue;
                Block block = loc.getBlock();
                if (block != null) {
                    String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
                    int[] orig = origStates.get(key);
                    if (orig != null && orig[0] != 0) block.setTypeIdAndData(orig[0], (byte) orig[1], false);
                    else block.setType(Material.AIR);
                }
            }
        }
    }

    /**
     * Clear ALL placed blocks for a player unconditionally (used on disconnect).
     */
    public void clearAllPlacedBlocks(UUID uuid) {
        RunSession session = activeSessions.get(uuid);
        if (session == null) return;

        for (Location loc : session.getPlacedBlocks()) {
            Block block = loc.getBlock();
            if (block != null) block.setType(Material.AIR);
        }
        for (Location loc : session.getPracticeBlocks()) {
            Block block = loc.getBlock();
            if (block != null) block.setType(Material.AIR);
        }
    }

    private void startActionbarTask() {
        String actionBarFormat = plugin.getConfigManager().getActionBar();
        if (actionBarFormat == null || actionBarFormat.isEmpty()) return;

        actionbarTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, RunSession> entry : new HashMap<>(activeSessions).entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) continue;

                    RunSession session = entry.getValue();
                    String timer = session.isRunning()
                            ? TimeUtil.formatTime(session.getElapsed()) : "00:00.000";

                    String msg = actionBarFormat.replace("%timer%", timer);
                    sendActionBar(player, ColorUtil.translate(msg));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L).getTaskId();
    }

    @SuppressWarnings("deprecation")
    private void sendActionBar(Player player, String message) {
        try {
            Object packet = getNMSClass("PacketPlayOutChat")
                    .getConstructor(getNMSClass("IChatBaseComponent"), byte.class)
                    .newInstance(
                            getNMSClass("IChatBaseComponent$ChatSerializer")
                                    .getMethod("a", String.class)
                                    .invoke(null, "{\"text\":\"" + message + "\"}"),
                            (byte) 2
                    );
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
            playerConnection.getClass().getMethod("sendPacket", getNMSClass("Packet"))
                    .invoke(playerConnection, packet);
        } catch (Exception ignored) {}
    }

    private Class<?> getNMSClass(String name) throws ClassNotFoundException {
        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        return Class.forName("net.minecraft.server." + version + "." + name);
    }

    public void enterBuildMode(UUID uuid) { buildModePlayers.add(uuid); }
    public void exitBuildMode(UUID uuid) { buildModePlayers.remove(uuid); }
    public boolean isInBuildMode(UUID uuid) { return buildModePlayers.contains(uuid); }

    public long getGlobalSessionBestTime() { return globalSessionBestTime; }
    public String getGlobalSessionBestPlayer() { return globalSessionBestPlayer; }

    public void shutdown() {
        if (actionbarTaskId != -1) Bukkit.getScheduler().cancelTask(actionbarTaskId);
        activeSessions.clear();
        finishCooldown.clear();
        buildModePlayers.clear();
    }

    public Map<UUID, RunSession> getActiveSessions() { return activeSessions; }
}
