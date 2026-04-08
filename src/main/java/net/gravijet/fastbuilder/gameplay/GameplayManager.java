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
import java.util.LinkedHashMap;
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

    // -------------------------------------------------------------------------
    // Death Sounds: display name → Bukkit Sound enum name (1.8.8)
    // Curated to short, punchy sounds only — no long ambient clips.
    // -------------------------------------------------------------------------
    public static final Map<String, String> DEATH_SOUNDS = new LinkedHashMap<>();
    static {
        DEATH_SOUNDS.put("NONE",           null);
        DEATH_SOUNDS.put("CreeperPrime",   "CREEPER_PRIME");   // short, punchy fuse ignition
        DEATH_SOUNDS.put("AnvilLand",      "ANVIL_LAND");      // sharp metallic thud
        DEATH_SOUNDS.put("Explode",        "EXPLODE");         // crisp explosion pop
        DEATH_SOUNDS.put("LevelUp",        "LEVEL_UP");        // bright ascending chime
        DEATH_SOUNDS.put("ArrowHit",       "ARROW_HIT");       // clean impact click
        DEATH_SOUNDS.put("Fizz",           "FIZZ");            // short sizzle
        DEATH_SOUNDS.put("Note",           "NOTE_PLING");      // clean single note
        DEATH_SOUNDS.put("Splash",         "SPLASH");          // short water slap
        DEATH_SOUNDS.put("ItemBreak",      "ITEM_BREAK");      // crisp crack
        DEATH_SOUNDS.put("Zombie",         "ZOMBIE_HURT");     // short impact grunt
        DEATH_SOUNDS.put("Skeleton",       "SKELETON_HURT");   // short rattle
        DEATH_SOUNDS.put("BlazeDeath",     "BLAZE_DEATH");     // fast airy pop
        DEATH_SOUNDS.put("FireworkBlast",  "FIREWORK_BLAST");  // punchy burst
        DEATH_SOUNDS.put("Enderman",       "ENDERMAN_SCREAM"); // short screech
        DEATH_SOUNDS.put("Portal",         "PORTAL");          // short whoosh
        DEATH_SOUNDS.put("AnvilBreak",     "ANVIL_BREAK");     // crunchy snap
    }

    // Entity UUIDs of FallingBlocks spawned by animations — used to cancel their landing
    private final java.util.Set<UUID> animationEntities = new java.util.HashSet<>();

    // Global session bests: per-player best time this session (unique per player)
    private final java.util.LinkedHashMap<String, Long> globalSessionBests = new java.util.LinkedHashMap<>();
    // Kept for backward compat
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
        // Build mode: never track or modify session
        if (buildModePlayers.contains(player.getUniqueId())) return;

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
     * Called when a player enters the finish zone.
     * Does nothing for infinite-mode maps (no finish condition).
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

        // Infinite mode maps have no finish condition
        MapData infiniteCheck = plugin.getMapManager().getMap(session.getMapName());
        if (infiniteCheck != null && infiniteCheck.isInfinite()) {
            finishCooldown.remove(uuid);
            return;
        }

        long time = session.finish();

        // Anticheat: reject times below the configured minimum valid time
        long mapMinTime = -1;
        MapData mapForMinTime = plugin.getMapManager().getMap(session.getMapName());
        if (mapForMinTime != null && mapForMinTime.getMinValidTime() > 0) {
            mapMinTime = mapForMinTime.getMinValidTime();
        }
        long globalMinTime = plugin.getConfigManager().getMinValidTime();
        long effectiveMin = mapMinTime > 0 ? mapMinTime : globalMinTime;

        if (effectiveMin > 0 && time < effectiveMin) {
            if (plugin.getReplayManager() != null) {
                plugin.getReplayManager().stopRecording(player.getUniqueId(), false);
            }
            finishCooldown.remove(uuid);
            String msg = plugin.getConfigManager().getMessage("min-time-not-recorded");
            if (msg == null || msg.isEmpty()) msg = "%prefix%&cTime too fast to be recorded &7(&f%time%&7).";
            msg = msg.replace("%time%", net.gravijet.fastbuilder.util.TimeUtil.formatTime(time))
                    .replace("%prefix%", plugin.getConfigManager().getPrefix());
            player.sendMessage(net.gravijet.fastbuilder.util.ColorUtil.translate(msg));
            // Reset without counting as attempt
            new BukkitRunnable() {
                @Override public void run() { if (player.isOnline()) resetRun(player); }
            }.runTaskLater(plugin, 5L);
            return;
        }

        if (plugin.getReplayManager() != null) {
            plugin.getReplayManager().stopRecording(player.getUniqueId(), true);
        }

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        session.addSessionBest(time);

        // Update global session bests (unique per player)
        Long existing = globalSessionBests.get(player.getName());
        if (existing == null || time < existing) {
            globalSessionBests.put(player.getName(), time);
        }
        if (globalSessionBestTime < 0 || time < globalSessionBestTime) {
            globalSessionBestTime = time;
            globalSessionBestPlayer = player.getName();
        }

        // Disable stats for Infinite mode and Custom Length mode (no record-keeping)
        MapData statsMap = plugin.getMapManager().getMap(session.getMapName());
        boolean statsDisabled = session.isPracticeMode()
                || (statsMap != null && (statsMap.isInfinite() || statsMap.hasCustomLength()));

        if (!statsDisabled) {
            PlayerData.MapStats stats = data.getOrCreateStats(session.getMapName());
            stats.totalAttempts++;
            stats.successfulAttempts++;

            boolean isNewPB = !stats.hasBestTime() || time < stats.bestTime;
            long oldBest = stats.bestTime;
            if (isNewPB) stats.bestTime = time;
            stats.totalSuccessTime += time;

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
                // Invalidate global percentile cache so the new PB is reflected immediately
                plugin.getPlayerManager().invalidateGlobalBestTimesCache(session.getMapName());

                plugin.getHologramManager().updateHologram(session.getMapName(), session.getIslandIndex(), player);

                // Refresh holograms for all other online players on this map so their [Top x%] updates
                final String finishedMapName = session.getMapName();
                for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getUniqueId().equals(uuid)) continue;
                    RunSession otherSession = activeSessions.get(online.getUniqueId());
                    if (otherSession != null && otherSession.getMapName().equals(finishedMapName)) {
                        plugin.getHologramManager().updateHologram(
                                finishedMapName, otherSession.getIslandIndex(), online);
                    }
                }
            }

            // Rank check: notify only the FIRST time a player achieves a specific rank
            MapData rankMap = plugin.getMapManager().getMap(session.getMapName());
            if (rankMap != null) {
                String rank = rankMap.getPlayerRank(stats.bestTime);
                if (rank != null && !data.hasBeenNotifiedOfRank(session.getMapName(), rank)) {
                    data.markRankNotified(session.getMapName(), rank);
                    String rankMsg = plugin.getConfigManager().getMessage("rank-achieved");
                    if (rankMsg == null || rankMsg.isEmpty()) {
                        rankMsg = "%prefix%&fYou achieved the &6%rank% &frank on &c%map%&f!";
                    }
                    rankMsg = rankMsg.replace("%rank%", rank)
                            .replace("%map%", session.getMapName())
                            .replace("%prefix%", plugin.getConfigManager().getPrefix());
                    player.sendMessage(net.gravijet.fastbuilder.util.ColorUtil.translate(rankMsg));
                }
            }
        } else {
            // Practice or stats-disabled mode: show time but note it's not saved
            String modeLabel = session.isPracticeMode() ? "&6&lPractice: " : "&a&lFinish: ";
            String noteSuffix = (statsMap != null && statsMap.isInfinite()) ? "&7Infinite mode"
                    : (statsMap != null && statsMap.hasCustomLength()) ? "&7Custom length"
                    : "&7Time not saved";
            player.sendTitle(ColorUtil.translate(modeLabel + "&f" + TimeUtil.formatTime(time)),
                    ColorUtil.translate(noteSuffix));
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
        // Build mode: no falls, no resets
        if (buildModePlayers.contains(player.getUniqueId())) return;

        RunSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        if (session.isRunning()) {
            if (plugin.getReplayManager() != null) {
                plugin.getReplayManager().stopRecording(player.getUniqueId(), false);
            }

            // Only record fall attempts for normal (non-practice, non-infinite, non-custom) maps
            MapData fallMap = plugin.getMapManager().getMap(session.getMapName());
            boolean fallStatsDisabled = session.isPracticeMode()
                    || (fallMap != null && (fallMap.isInfinite() || fallMap.hasCustomLength()));
            if (!fallStatsDisabled) {
                PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
                if (data != null) {
                    PlayerData.MapStats stats = data.getOrCreateStats(session.getMapName());
                    stats.totalAttempts++;

                    if (plugin.getHologramManager() != null) {
                        plugin.getHologramManager().updateHologram(
                                session.getMapName(), session.getIslandIndex(), player);
                    }
                }
            }
        }

        playDeathSound(player);
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

        for (Long best : bests) session.addSessionBest(best);
        session.setPracticeMode(practice);

        player.teleport(map.getIslandSpawn(session.getIslandIndex()));

        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().giveItems(player);
        }

        plugin.getScoreboardManager().updateScoreboard(player);
    }

    /**
     * Plays the player's selected death sound at their location.
     */
    public void playDeathSound(Player player) {
        net.gravijet.fastbuilder.player.PlayerData data =
                plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) return;
        String soundKey = data.getSelectedDeathSound();
        if (soundKey == null || soundKey.equalsIgnoreCase("NONE")) return;
        String soundEnum = DEATH_SOUNDS.getOrDefault(soundKey, soundKey);
        if (soundEnum == null) return;
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundEnum);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    private void clearBlocksWithAnimation(Player player, List<Location> blocks,
                                           List<Location> practiceBlocks,
                                           Map<String, int[]> origStates,
                                           boolean isPracticeMode, String animation) {
        // Read animation speed from config (default: 3 blocks/tick, every 1 tick)
        final int batchSize  = plugin.getConfigManager().getAnimationBlocksPerTick();
        final long tickDelay = plugin.getConfigManager().getAnimationTickInterval();

        if ("FALL_DOWN".equalsIgnoreCase(animation) || "SLIDE_DOWN".equalsIgnoreCase(animation)) {
            // Sequential top-to-bottom falling blocks
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
                    for (int i = 0; i < batchSize && idx[0] < toClear.size(); i++, idx[0]++) {
                        Location loc = toClear.get(idx[0]);
                        Block block = loc.getBlock();
                        if (block != null && block.getType() != Material.AIR) {
                            int typeId = block.getTypeId();
                            byte data = block.getData();
                            spawnAnimationFallingBlock(loc, typeId, data,
                                    new org.bukkit.util.Vector(
                                            0.0,
                                            -0.1 - Math.random() * 0.25,
                                            0.0), null);
                            String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
                            int[] orig = origStates.get(key);
                            if (orig != null && orig[0] != 0) block.setTypeIdAndData(orig[0], (byte) orig[1], false);
                            else block.setType(Material.AIR);
                        }
                    }
                    if (idx[0] >= toClear.size()) this.cancel();
                }
            }.runTaskTimer(plugin, 0L, tickDelay);
        } else if ("EXPLODE".equalsIgnoreCase(animation)) {
            for (Location loc : blocks) {
                if (isPracticeMode && practiceBlocks.contains(loc)) continue;
                Block block = loc.getBlock();
                if (block != null && block.getType() != Material.AIR) {
                    int typeId = block.getTypeId();
                    byte data = block.getData();
                    double vx = (Math.random() - 0.5) * 1.0;
                    double vy = 0.3 + Math.random() * 0.6;
                    double vz = (Math.random() - 0.5) * 1.0;
                    spawnAnimationFallingBlock(loc, typeId, data,
                            new org.bukkit.util.Vector(vx, vy, vz), player.getUniqueId());
                    String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
                    int[] orig = origStates.get(key);
                    if (orig != null && orig[0] != 0) block.setTypeIdAndData(orig[0], (byte) orig[1], false);
                    else block.setType(Material.AIR);
                }
            }
        } else if ("ITEM_DROP".equalsIgnoreCase(animation)) {
            clearBlocksItemDrop(blocks, practiceBlocks, origStates, isPracticeMode);
        } else if ("ICE_MELT".equalsIgnoreCase(animation)) {
            clearBlocksIceMelt(blocks, practiceBlocks, origStates, isPracticeMode);
        } else if ("CREATIVE_NPC".equalsIgnoreCase(animation)) {
            clearBlocksCreativeNpc(blocks, practiceBlocks, origStates, isPracticeMode);
        } else {
            // NONE: fast sequential clear — uses config batch size × 2 for instant feel
            final int noneBatch = Math.max(1, batchSize * 2);
            List<Location> toClear = new ArrayList<>();
            for (Location loc : blocks) {
                if (isPracticeMode && practiceBlocks.contains(loc)) continue;
                toClear.add(loc);
            }
            final int[] idx = {0};
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    for (int i = 0; i < noneBatch && idx[0] < toClear.size(); i++, idx[0]++) {
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
        }
    }

    /**
     * ITEM_DROP animation: blocks drop one-by-one as item entities with no enchantments.
     */
    @SuppressWarnings("deprecation")
    private void clearBlocksItemDrop(List<Location> blocks, List<Location> practiceBlocks,
                                      Map<String, int[]> origStates, boolean isPracticeMode) {
        List<Location> toClear = new ArrayList<>();
        for (Location loc : blocks) {
            if (isPracticeMode && practiceBlocks.contains(loc)) continue;
            Block block = loc.getBlock();
            if (block == null || block.getType() == Material.AIR) continue;
            toClear.add(loc);
        }

        final int dropBatch = plugin.getConfigManager().getAnimationBlocksPerTick();
        final long dropInterval = plugin.getConfigManager().getAnimationTickInterval();
        final int[] idx = {0};
        new BukkitRunnable() {
            @Override
            @SuppressWarnings("deprecation")
            public void run() {
                for (int i = 0; i < dropBatch && idx[0] < toClear.size(); i++, idx[0]++) {
                    Location loc = toClear.get(idx[0]);
                    Block block = loc.getBlock();
                    if (block == null || block.getType() == Material.AIR) continue;

                    Material mat = block.getType();
                    short durability = block.getData();
                    Location center = loc.clone().add(0.5, 0.5, 0.5);
                    try {
                        // Create a plain item stack with NO enchantments (no Unbreaking glow)
                        org.bukkit.inventory.ItemStack stack = new org.bukkit.inventory.ItemStack(mat, 1, durability);
                        org.bukkit.entity.Item item = loc.getWorld().dropItem(center, stack);
                        item.setPickupDelay(32767); // prevent pickup
                        item.setVelocity(new org.bukkit.util.Vector(
                                (Math.random() - 0.5) * 0.25,
                                0.15 + Math.random() * 0.25,
                                (Math.random() - 0.5) * 0.25));
                        final org.bukkit.entity.Item ref = item;
                        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (!ref.isDead()) ref.remove(); }, 40L);
                    } catch (Exception ignored) {}

                    // Restore underlying block
                    String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
                    int[] orig = origStates.get(key);
                    if (orig != null && orig[0] != 0) block.setTypeIdAndData(orig[0], (byte) orig[1], false);
                    else block.setType(Material.AIR);
                }
                if (idx[0] >= toClear.size()) this.cancel();
            }
        }.runTaskTimer(plugin, 0L, dropInterval);
    }

    /**
     * ICE_MELT animation: blocks turn to ice one-by-one, each "melting" away with a FIZZ sound.
     */
    @SuppressWarnings("deprecation")
    private void clearBlocksIceMelt(List<Location> blocks, List<Location> practiceBlocks,
                                     Map<String, int[]> origStates, boolean isPracticeMode) {
        List<Location> toClear = new ArrayList<>();
        for (Location loc : blocks) {
            if (isPracticeMode && practiceBlocks.contains(loc)) continue;
            Block block = loc.getBlock();
            if (block == null || block.getType() == Material.AIR) continue;
            toClear.add(loc);
        }
        if (toClear.isEmpty()) return;

        final int[] idx = {0};
        new BukkitRunnable() {
            @Override
            @SuppressWarnings("deprecation")
            public void run() {
                int batch = 3;
                for (int i = 0; i < batch && idx[0] < toClear.size(); i++, idx[0]++) {
                    Location loc = toClear.get(idx[0]);
                    Block block = loc.getBlock();
                    if (block == null || block.getType() == Material.AIR) continue;

                    // Turn block to ice
                    block.setTypeIdAndData(79, (byte) 0, false);

                    // Play ice-melting sound at this location
                    try {
                        loc.getWorld().playSound(loc, org.bukkit.Sound.FIZZ, 0.4f, 1.8f);
                    } catch (Exception ignored) {}

                    // Schedule melt (remove ice) after 3 ticks
                    final Location frozenLoc = loc.clone();
                    final int[] origArr = origStates.get(loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Block b = frozenLoc.getBlock();
                        if (b == null) return;
                        if (origArr != null && origArr[0] != 0) b.setTypeIdAndData(origArr[0], (byte) origArr[1], false);
                        else b.setType(Material.AIR);
                    }, 3L);
                }
                if (idx[0] >= toClear.size()) this.cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * CREATIVE_NPC animation: a Citizens NPC in creative-mode skin rapidly "mines" all blocks away.
     * The NPC teleports to each block and removes it instantly — very fast (~10 blocks/tick).
     */
    @SuppressWarnings("deprecation")
    private void clearBlocksCreativeNpc(List<Location> blocks, List<Location> practiceBlocks,
                                         Map<String, int[]> origStates, boolean isPracticeMode) {
        List<Location> toClear = new ArrayList<>();
        for (Location loc : blocks) {
            if (isPracticeMode && practiceBlocks.contains(loc)) continue;
            Block block = loc.getBlock();
            if (block == null || block.getType() == Material.AIR) continue;
            toClear.add(loc);
        }
        if (toClear.isEmpty()) return;

        // Spawn NPC at the first block location
        net.citizensnpcs.api.npc.NPC[] npcRef = new net.citizensnpcs.api.npc.NPC[1];
        try {
            net.citizensnpcs.api.npc.NPCRegistry reg = net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
            net.citizensnpcs.api.npc.NPC npc = reg.createNPC(
                    org.bukkit.entity.EntityType.PLAYER, "§6Builder");
            Location spawnLoc = toClear.get(0).clone().add(0.5, 0, 0.5);
            npc.spawn(spawnLoc);
            npcRef[0] = npc;
        } catch (NoClassDefFoundError | Exception ignored) {}

        final int BLOCKS_PER_TICK = 10;
        final int[] idx = {0};
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < BLOCKS_PER_TICK && idx[0] < toClear.size(); i++, idx[0]++) {
                    Location loc = toClear.get(idx[0]);
                    Block block = loc.getBlock();
                    if (block == null || block.getType() == Material.AIR) continue;
                    // Teleport NPC to block
                    if (npcRef[0] != null && npcRef[0].isSpawned()) {
                        try { npcRef[0].getEntity().teleport(loc.clone().add(0.5, 0, 0.5)); }
                        catch (Exception ignored) {}
                    }
                    // Block break effect
                    try { loc.getWorld().playEffect(loc, org.bukkit.Effect.STEP_SOUND, block.getTypeId()); }
                    catch (Exception ignored) {}
                    // Remove block
                    String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
                    int[] orig = origStates.get(key);
                    if (orig != null && orig[0] != 0) block.setTypeIdAndData(orig[0], (byte) orig[1], false);
                    else block.setType(Material.AIR);
                }
                if (idx[0] >= toClear.size()) {
                    if (npcRef[0] != null) {
                        try { npcRef[0].destroy(); } catch (Exception ignored) {}
                    }
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @SuppressWarnings("deprecation")
    private void spawnAnimationFallingBlock(Location loc, int typeId, byte data,
                                             org.bukkit.util.Vector velocity, UUID ownerUuid) {
        try {
            if (typeId == 0) return;
            Material mat = Material.getMaterial(typeId);
            if (mat == null || mat == Material.AIR) return;

            // Spawn 1 block above the placed block's position so the entity has room to fall
            // even for blocks placed directly on the map surface (ground-level blocks).
            Location spawnLoc = loc.clone().add(0.5, 1.0, 0.5);
            org.bukkit.entity.FallingBlock fb = loc.getWorld().spawnFallingBlock(spawnLoc, mat, data);
            fb.setDropItem(false);
            fb.setVelocity(velocity);

            // Track this entity so the EntityChangeBlockEvent handler can cancel + remove it on impact
            animationEntities.add(fb.getUniqueId());

            // Prevent the falling block from placing when it lands (NMS 1.8.8 dontSetBlock field)
            try {
                Object handle = fb.getClass().getMethod("getHandle").invoke(fb);
                java.lang.reflect.Field f = handle.getClass().getDeclaredField("dontSetBlock");
                f.setAccessible(true);
                f.set(handle, true);
            } catch (Exception ignored) {}

            final org.bukkit.entity.FallingBlock fbRef = fb;
            final UUID fbEntityId = fb.getUniqueId();

            // EXPLODE only: proximity cleanup — remove entity if any non-owner player is nearby
            if (ownerUuid != null) {
                final int[] checksLeft = {12};
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (fbRef.isDead() || checksLeft[0] <= 0) {
                            this.cancel();
                            return;
                        }
                        checksLeft[0]--;
                        Location fbLoc = fbRef.getLocation();
                        for (org.bukkit.entity.Player nearby : fbLoc.getWorld().getPlayers()) {
                            if (nearby.getUniqueId().equals(ownerUuid)) continue;
                            Location pLoc = nearby.getLocation();
                            if (Math.abs(pLoc.getX() - fbLoc.getX()) <= 2.5
                                    && Math.abs(pLoc.getY() - fbLoc.getY()) <= 4.0
                                    && Math.abs(pLoc.getZ() - fbLoc.getZ()) <= 2.5) {
                                animationEntities.remove(fbEntityId);
                                fbRef.remove();
                                this.cancel();
                                return;
                            }
                        }
                    }
                }.runTaskTimer(plugin, 5L, 5L);
            }

            // Schedule removal after 3 seconds to clean up if still alive in the world
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                animationEntities.remove(fbEntityId);
                if (!fbRef.isDead()) fbRef.remove();
            }, 60L);
        } catch (Exception ignored) {}
    }

    public boolean isAnimationEntity(UUID entityId) {
        return animationEntities.contains(entityId);
    }

    public void removeAnimationEntity(UUID entityId) {
        animationEntities.remove(entityId);
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
        actionbarTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                String actionBarFormat = plugin.getConfigManager().getActionBar();
                boolean onlyWhenRunning = plugin.getConfigManager().isActionBarOnlyWhenRunning();

                for (Map.Entry<UUID, RunSession> entry : new HashMap<>(activeSessions).entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) continue;

                    RunSession session = entry.getValue();

                    // Max completion time check: auto-fail runs that exceed the configured limit
                    if (session.isRunning()) {
                        MapData mapData = plugin.getMapManager().getMap(session.getMapName());
                        if (mapData != null && mapData.getMaxCompletionTime() > 0
                                && session.getElapsed() > mapData.getMaxCompletionTime()) {
                            if (plugin.getReplayManager() != null) {
                                plugin.getReplayManager().stopRecording(player.getUniqueId(), false);
                            }
                            PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
                            if (pData != null) {
                                MapData timerMap = plugin.getMapManager().getMap(session.getMapName());
                                boolean timerStatsOk = !session.isPracticeMode()
                                        && (timerMap == null || (!timerMap.isInfinite() && !timerMap.hasCustomLength()));
                                if (timerStatsOk) {
                                    PlayerData.MapStats stats = pData.getOrCreateStats(session.getMapName());
                                    stats.totalAttempts++;
                                }
                            }
                            String limitMsg = plugin.getConfigManager().getMessage("max-time-exceeded");
                            if (limitMsg == null || limitMsg.isEmpty()) {
                                limitMsg = "%prefix%&cRun failed: time limit exceeded.";
                            }
                            limitMsg = limitMsg.replace("%prefix%", plugin.getConfigManager().getPrefix());
                            player.sendMessage(ColorUtil.translate(limitMsg));
                            resetRun(player);
                            continue;
                        }
                    }

                    if (actionBarFormat == null || actionBarFormat.isEmpty()) continue;

                    // Respect the only-when-running setting
                    if (onlyWhenRunning && !session.isRunning()) continue;

                    String timer = session.isRunning()
                            ? TimeUtil.formatTime(session.getElapsed()) : "00:00.000";

                    // Support both %time% and %timer% placeholders
                    String msg = actionBarFormat
                            .replace("%time%", timer)
                            .replace("%timer%", timer);
                    sendActionBar(player, ColorUtil.translate(msg));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L).getTaskId();
    }

    /**
     * Called on /fb reload — restarts the actionbar task so new config values take effect.
     */
    public void reloadActionbar() {
        if (actionbarTaskId != -1) {
            Bukkit.getScheduler().cancelTask(actionbarTaskId);
            actionbarTaskId = -1;
        }
        startActionbarTask();
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

    /**
     * Returns the top N unique-player session bests as [playerName, timeMillisStr] pairs, sorted best first.
     */
    public List<String[]> getGlobalSessionTop(int n) {
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(globalSessionBests.entrySet());
        sorted.sort((a, b) -> Long.compare(a.getValue(), b.getValue()));
        List<String[]> result = new ArrayList<>();
        for (int i = 0; i < Math.min(n, sorted.size()); i++) {
            Map.Entry<String, Long> entry = sorted.get(i);
            result.add(new String[]{entry.getKey(), String.valueOf(entry.getValue())});
        }
        return result;
    }

    /** Convenience overload — returns top 3 (backward compat). */
    public List<String[]> getGlobalSessionTop3() {
        return getGlobalSessionTop(3);
    }

    public void removeGlobalSessionBest(String playerName) {
        globalSessionBests.remove(playerName);
        if (playerName.equals(globalSessionBestPlayer)) {
            globalSessionBestTime = -1;
            globalSessionBestPlayer = null;
            for (Map.Entry<String, Long> e : globalSessionBests.entrySet()) {
                if (globalSessionBestTime < 0 || e.getValue() < globalSessionBestTime) {
                    globalSessionBestTime = e.getValue();
                    globalSessionBestPlayer = e.getKey();
                }
            }
        }
    }

    public void shutdown() {
        if (actionbarTaskId != -1) Bukkit.getScheduler().cancelTask(actionbarTaskId);
        activeSessions.clear();
        finishCooldown.clear();
        buildModePlayers.clear();
        globalSessionBests.clear();
    }

    public Map<UUID, RunSession> getActiveSessions() { return activeSessions; }
}
