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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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

    // Legacy end-platform: simple row of glass panes (used when map.hasEndIsland() == false)
    private final Map<UUID, List<Location>> endPlatforms = new HashMap<>();
    private final Map<UUID, Map<String, int[]>> endPlatformOrigStates = new HashMap<>();

    // New end-island system: stores the placed region for each player {x, y, z, w, h, l}
    private final Map<UUID, int[]> endIslandRegions = new HashMap<>();

    private final java.util.Set<UUID> finishCooldown = new java.util.HashSet<>();
    private final java.util.Set<UUID> buildModePlayers = new java.util.HashSet<>();

    // Islands currently being reset — key format: "mapName:islandIndex"
    // Blocks placements on these islands are rejected until reset finishes.
    private final Set<String> resettingIslands = Collections.synchronizedSet(new HashSet<>());

    // -------------------------------------------------------------------------
    // Death Sounds: display name → Bukkit Sound enum name (1.8.8)
    // Curated to short, punchy sounds only — no long ambient clips.
    // -------------------------------------------------------------------------
    public static final Map<String, String> DEATH_SOUNDS = new LinkedHashMap<>();
    static {
        // Keys MUST match the "sound:" values in guis.yml (what gets saved to player data).
        // Values are Bukkit 1.8.8 Sound enum names.
        DEATH_SOUNDS.put("NONE",           null);
        DEATH_SOUNDS.put("Creeper",        "CREEPER_HISS");
        DEATH_SOUNDS.put("Anvil",          "ANVIL_LAND");
        DEATH_SOUNDS.put("Ghast",          "GHAST_SCREAM");
        DEATH_SOUNDS.put("Wither",         "WITHER_HURT");
        DEATH_SOUNDS.put("IronGolem",      "IRONGOLEM_HIT");
        DEATH_SOUNDS.put("Enderman",       "ENDERMAN_SCREAM");
        DEATH_SOUNDS.put("Zombie",         "ZOMBIE_HURT");
        DEATH_SOUNDS.put("Piglin",         "ZOMBIE_PIG_ANGRY");
        DEATH_SOUNDS.put("Blaze",          "BLAZE_DEATH");
        DEATH_SOUNDS.put("Wolf",           "WOLF_BARK");
        DEATH_SOUNDS.put("Firework",       "FIREWORK_BLAST");
        DEATH_SOUNDS.put("Splash",         "SPLASH");
        DEATH_SOUNDS.put("DragonGrowl",    "ENDERDRAGON_GROWL");
        DEATH_SOUNDS.put("Bat",            "BAT_HURT");
        DEATH_SOUNDS.put("ArrowHit",       "ARROW_HIT");
        DEATH_SOUNDS.put("Note",           "NOTE_PLING");
        DEATH_SOUNDS.put("WitherSpawn",    "WITHER_SPAWN");
        // Added short sounds (all ≤ 0.8 s in 1.8.8)
        DEATH_SOUNDS.put("Click",          "CLICK");          // lever/button click  ~0.2s
        DEATH_SOUNDS.put("WoodClick",      "WOOD_CLICK");     // wooden button click ~0.2s
        DEATH_SOUNDS.put("BassDrum",       "NOTE_BASS_DRUM"); // note-block bass drum ~0.2s
        DEATH_SOUNDS.put("SnareDrum",      "NOTE_SNARE_DRUM");// note-block snare     ~0.2s
        DEATH_SOUNDS.put("ExpPickup",      "ORB_PICKUP");     // XP orb pickup        ~0.2s
        DEATH_SOUNDS.put("SlimeHit",       "SLIME_ATTACK");   // slime attack         ~0.4s
        DEATH_SOUNDS.put("Villager",       "VILLAGER_IDLE");  // villager idle hmm    ~0.6s
        DEATH_SOUNDS.put("Chicken",        "CHICKEN_IDLE");   // chicken cluck        ~0.3s
    }

    // Entity UUIDs of FallingBlocks spawned by animations — used to cancel their landing
    private final java.util.Set<UUID> animationEntities = new java.util.HashSet<>();

    // Global session bests: per-player best time this session (unique per player)
    private final java.util.LinkedHashMap<String, Long> globalSessionBests = new java.util.LinkedHashMap<>();
    // Kept for backward compat
    private long globalSessionBestTime = -1;
    private String globalSessionBestPlayer = null;

    // Last finished run data — shown on scoreboard/actionbar until the next run starts
    private final Map<UUID, Long>    lastFinishTimes  = new HashMap<>();
    private final Map<UUID, Integer> lastFinishBlocks = new HashMap<>();

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
        // lastFinishTimes and lastFinishBlocks are intentionally NOT cleared here.
        // They persist on the scoreboard/actionbar until the player physically starts a new run.
        // They are cleared in onBlockPlace() when the timer starts.
    }

    /** Time (ms) from the player's most recent completed run, or -1 if none this session. */
    public long getLastFinishTime(UUID uuid) {
        Long t = lastFinishTimes.get(uuid);
        return t != null ? t : -1L;
    }

    /** Block count from the player's most recent completed run, or -1 if none. */
    public int getLastFinishBlocks(UUID uuid) {
        Integer b = lastFinishBlocks.get(uuid);
        return b != null ? b : -1;
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

        // If the session is finished or its reset animation is already playing,
        // absolutely do NOT allow any further block placement or second reset.
        if (session.isFinished() || session.isResetting()) return;

        if (!session.isRunning()) {
            // Block the timer start if there are un-cleared practice blocks
            if (!session.isPracticeMode() && session.hasPracticeBlocks()) {
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                        + "&cClear your practice blocks before starting a real run."));
                return;
            }
            // New run starting — clear the persistent last-finish display
            lastFinishTimes.remove(player.getUniqueId());
            lastFinishBlocks.remove(player.getUniqueId());
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
        if (session == null || !session.isRunning() || session.isFinished() || session.isResetting()) {
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
        boolean playerCustomLengthActive = statsMap != null && statsMap.hasCustomLength()
                && data.isCustomLengthEnabled(session.getMapName());
        boolean statsDisabled = session.isPracticeMode()
                || (statsMap != null && statsMap.isInfinite())
                || playerCustomLengthActive;

        boolean isNewPB = false;  // hoisted — set below if stats are tracked
        if (!statsDisabled) {
            PlayerData.MapStats stats = data.getOrCreateStats(session.getMapName());
            stats.totalAttempts++;
            stats.successfulAttempts++;

            isNewPB = !stats.hasBestTime() || time < stats.bestTime;
            long oldBest = stats.bestTime;
            if (isNewPB) stats.bestTime = time;
            stats.totalSuccessTime += time;

            int coins = plugin.getCoinManager().awardCompletionCoins(player, time, session.getMapName());

            // Resolve booster state so we can display it in chat / title
            double boostMult = plugin.getBoosterManager().getMultiplier(player);
            boolean hasBoost = boostMult > 1.01;
            String boostLabel = hasBoost ? formatMult(boostMult) + "x Booster" : "";
            int baseCoins = hasBoost
                    ? plugin.getCoinManager().computeBaseCoins(player.getUniqueId(), time, session.getMapName())
                    : coins;

            String prefix = plugin.getConfigManager().getPrefix();
            if (isNewPB) {
                List<String> messages = plugin.getConfigManager().getEndPBMessages();
                for (String line : messages) {
                    line = line.replace("%time%", TimeUtil.formatTime(time))
                            .replace("%pb%", TimeUtil.formatTime(time))
                            .replace("%difference%", oldBest > 0
                                    ? TimeUtil.formatDifference(time, oldBest) : "N/A")
                            .replace("%coins%", String.valueOf(coins))
                            .replace("%booster%", boostLabel)
                            .replace("%prefix%", prefix);
                    player.sendMessage(ColorUtil.translate(line));
                }
            } else {
                List<String> messages = plugin.getConfigManager().getEndTimeMessages();
                for (String line : messages) {
                    line = line.replace("%time%", TimeUtil.formatTime(time))
                            .replace("%pb%", TimeUtil.formatTime(stats.bestTime))
                            .replace("%difference%", TimeUtil.formatDifference(time, stats.bestTime))
                            .replace("%coins%", String.valueOf(coins))
                            .replace("%booster%", boostLabel)
                            .replace("%prefix%", prefix);
                    player.sendMessage(ColorUtil.translate(line));
                }
            }

            // Always show booster breakdown in chat when one was active
            if (hasBoost) {
                player.sendMessage(ColorUtil.translate(prefix
                        + "&6" + formatMult(boostMult) + "x Booster &7active! &8("
                        + baseCoins + " \u2192 &6" + coins + " coins&8)"));
            }

            String title = plugin.getConfigManager().getTitle();
            String subtitle = plugin.getConfigManager().getSubtitle();
            if (title != null && !title.isEmpty()) {
                title = title.replace("%time%", TimeUtil.formatTime(time))
                        .replace("%coins%", String.valueOf(coins))
                        .replace("%booster%", boostLabel);
                if (subtitle == null) subtitle = "";
                subtitle = subtitle.replace("%time%", TimeUtil.formatTime(time))
                        .replace("%coins%", String.valueOf(coins))
                        .replace("%booster%", boostLabel);
                // Append booster notice to subtitle when no %booster% placeholder was configured
                if (hasBoost && !subtitle.contains(formatMult(boostMult))) {
                    subtitle = subtitle.isEmpty()
                            ? "&6" + formatMult(boostMult) + "x Booster active"
                            : subtitle + " &8| &6" + formatMult(boostMult) + "x";
                }
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
            String noteSuffix;
            if (statsMap != null && statsMap.isInfinite()) {
                noteSuffix = "&7Infinite mode";
            } else if (playerCustomLengthActive) {
                // Display exact block distance bridged for custom length runs
                int blocksBridged = session.getPlacedBlocks().size();
                noteSuffix = "&7Custom length &8- &f" + blocksBridged + " blocks";
            } else {
                noteSuffix = "&7Time not saved";
            }
            player.sendTitle(ColorUtil.translate(modeLabel + "&f" + TimeUtil.formatTime(time)),
                    ColorUtil.translate(noteSuffix));
        }

        // Store last-finish data for scoreboard/actionbar persistence
        lastFinishTimes.put(uuid, time);
        lastFinishBlocks.put(uuid, session.getPlacedBlocks().size());

        // Massive celebration — full visual fireworks only on a new PB
        launchCelebration(player, player.getLocation(), isNewPB);

        plugin.getScoreboardManager().updateScoreboard(player);

        // Start the reset animation IMMEDIATELY (spec: "trigger instantly")
        startResetAnimation(player);

        // Finalize reset (teleport + hotbar) after 2 seconds so player sees celebration
        new BukkitRunnable() {
            @Override
            public void run() {
                finishCooldown.remove(uuid);
                if (player.isOnline()) finalizeReset(player);
            }
        }.runTaskLater(plugin, 40L);
    }

    /**
     * Launch a finish celebration.
     * On a new PB: spawn full firework entities visible to the whole server.
     * On a normal finish: play firework sounds only for the finishing player.
     */
    private void launchCelebration(final Player player, final Location location, final boolean isPB) {
        final Random rand = new Random();

        for (int wave = 0; wave < 8; wave++) {
            final int delay = wave * 7; // ~0.35s between waves
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    if (isPB) {
                        // Full entity-based fireworks — server-wide visual + audio
                        spawnFirework(location, rand);
                        Location off1 = location.clone().add(
                                (rand.nextDouble() - 0.5) * 6, 0, (rand.nextDouble() - 0.5) * 6);
                        Location off2 = location.clone().add(
                                (rand.nextDouble() - 0.5) * 6, 0, (rand.nextDouble() - 0.5) * 6);
                        spawnFirework(off1, rand);
                        spawnFirework(off2, rand);
                    } else {
                        // Sound-only for the finishing player (no visual entity)
                        if (!player.isOnline()) return;
                        try {
                            player.playSound(location,
                                    org.bukkit.Sound.valueOf("FIREWORK_LAUNCH"), 1.0f, 1.0f);
                            player.playSound(location,
                                    org.bukkit.Sound.valueOf("FIREWORK_BLAST"), 1.0f, 1.0f);
                        } catch (IllegalArgumentException ignored) {}
                    }
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
        if (session.isResetting()) return;

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

            // Award consolation coins on failed runs if configured
            if (plugin.getConfigManager().isCoinsOnFailed()) {
                int failCoins = plugin.getCoinManager().awardFailedRunCoins(player);
                if (failCoins > 0) {
                    String prefix = plugin.getConfigManager().getPrefix();
                    player.sendMessage(ColorUtil.translate(prefix + "&7+" + failCoins + " coins &8(failed run)"));
                }
            }
        }

        playDeathSound(player);
        // Trigger animation instantly on death (spec requirement)
        startResetAnimation(player);
        // Finalize on next tick so teleport happens after animation starts
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) finalizeReset(player);
        }, 2L);
    }

    /**
     * Start the block-clearing animation immediately (for instant death/finish response).
     * Does NOT teleport the player — call finalizeReset() after the delay.
     */
    // =========================================================================
    // Island reset lock (block placements rejected while island is resetting)
    // =========================================================================

    private static String islandKey(String mapName, int islandIndex) {
        return mapName + ":" + islandIndex;
    }

    public void markIslandResetting(String mapName, int islandIndex) {
        resettingIslands.add(islandKey(mapName, islandIndex));
    }

    public void unmarkIslandResetting(String mapName, int islandIndex) {
        resettingIslands.remove(islandKey(mapName, islandIndex));
    }

    public boolean isIslandResetting(String mapName, int islandIndex) {
        return resettingIslands.contains(islandKey(mapName, islandIndex));
    }

    // =========================================================================

    public void startResetAnimation(Player player) {
        RunSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        // Lock the session immediately so no second reset can fire.
        session.setResetting(true);
        markIslandResetting(session.getMapName(), session.getIslandIndex());

        PlayerData pData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        String animation = pData != null ? pData.getSelectedAnimation() : "NONE";

        // Snapshot blocks NOW — before session.reset() clears them
        List<Location> blocksCopy = new ArrayList<>(session.getPlacedBlocks());
        List<Location> practiceBlocksCopy = new ArrayList<>(session.getPracticeBlocks());
        Map<String, int[]> origStatesCopy = new HashMap<>(session.getOriginalBlockStates());
        boolean practice = session.isPracticeMode();

        clearBlocksWithAnimation(player, blocksCopy, practiceBlocksCopy, origStatesCopy, practice, animation);
    }

    /**
     * Finalize the run reset: reset session state, teleport to spawn, give hotbar items.
     * Call this after the animation delay (typically 2 seconds after finish/death).
     * Uses the design profile spawn override when one is active.
     */
    public void finalizeReset(Player player) {
        RunSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        MapData map = plugin.getMapManager().getMap(session.getMapName());
        if (map == null) return;

        String mapName = session.getMapName();
        int islandIndex = session.getIslandIndex();

        java.util.List<Long> bests = new java.util.ArrayList<>(session.getSessionBests());
        boolean practice = session.isPracticeMode();

        // Persist block count so the scoreboard doesn't flash "0" between runs
        if (!lastFinishBlocks.containsKey(player.getUniqueId())) {
            lastFinishBlocks.put(player.getUniqueId(), session.getPlacedBlocks().size());
        }

        session.reset();

        for (Long best : bests) session.addSessionBest(best);
        session.setPracticeMode(practice);

        player.teleport(getEffectiveSpawn(player.getUniqueId(), map, session.getIslandIndex()));

        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().giveItems(player);
        }

        plugin.getScoreboardManager().updateScoreboard(player);

        // Island reset is complete — unblock placements
        unmarkIslandResetting(mapName, islandIndex);
    }

    /**
     * Full reset in one shot (used by GUI/command resets that don't need animation/delay split).
     * Uses the design profile spawn override when one is active.
     */
    public void resetRun(Player player) {
        RunSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;
        if (session.isResetting()) return;
        startResetAnimation(player);
        // For synchronous callers (e.g. island switch), finalize immediately
        session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        MapData map = plugin.getMapManager().getMap(session.getMapName());
        if (map == null) return;

        java.util.List<Long> bests = new java.util.ArrayList<>(session.getSessionBests());
        boolean practice = session.isPracticeMode();

        // Persist block count so the scoreboard doesn't flash "0" between runs
        if (!lastFinishBlocks.containsKey(player.getUniqueId())) {
            lastFinishBlocks.put(player.getUniqueId(), session.getPlacedBlocks().size());
        }

        session.reset();

        for (Long best : bests) session.addSessionBest(best);
        session.setPracticeMode(practice);

        player.teleport(getEffectiveSpawn(player.getUniqueId(), map, session.getIslandIndex()));

        if (plugin.getHotbarManager() != null) {
            plugin.getHotbarManager().giveItems(player);
        }

        plugin.getScoreboardManager().updateScoreboard(player);
    }

    /**
     * Switch a player to a different island within the same map (island hopping).
     * Clears old blocks, frees old island, assigns new island, teleports, spawns NPC/hologram.
     * Session bests are carried over to the new session.
     */
    public void switchIsland(Player player, MapData map, RunSession session, int targetIsland) {
        UUID uuid = player.getUniqueId();
        int oldIsland = session.getIslandIndex();

        // Preserve state that survives the island switch
        List<Long> carriedBests = new ArrayList<>(session.getSessionBests());
        boolean carriedPractice = session.isPracticeMode();

        net.gravijet.fastbuilder.player.PlayerData data = plugin.getPlayerManager().getCachedData(uuid);

        // Revert old island to default design (so the next player gets a clean slate)
        revertIslandDesign(map, oldIsland);

        // Clear placed blocks and end island on old island (instant, no animation)
        clearAllPlacedBlocks(uuid);
        clearEndPlatform(uuid);

        // Reset session custom-length (X and Y) so the new island starts at default
        if (data != null) {
            data.setCustomLength(map.getName(), 0);
            data.setCustomLengthY(map.getName(), 0);
            data.clearActiveFinishZone(map.getName());
        }

        // Cleanup old island integrations
        if (plugin.getCpsListener() != null) plugin.getCpsListener().cleanupPlayer(uuid);
        if (plugin.getNpcManager() != null) plugin.getNpcManager().despawnNpc(uuid);
        if (plugin.getHologramManager() != null) plugin.getHologramManager().removeHologram(map.getName(), oldIsland);

        // Free old island, remove old session
        plugin.getMapManager().freeIsland(map.getName(), uuid);
        removeSession(uuid);
        unmarkIslandResetting(map.getName(), oldIsland);

        // Assign new island
        plugin.getMapManager().assignIsland(map.getName(), targetIsland, uuid, player.getName());
        if (data != null) data.setLastIsland(targetIsland);

        // Teleport to new island spawn (design profile override if active) and ensure Survival
        player.teleport(getEffectiveSpawn(uuid, map, targetIsland));
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);

        // Create new session and carry over session bests + practice mode
        RunSession newSession = createSession(uuid, map.getName(), targetIsland);
        for (Long best : carriedBests) newSession.addSessionBest(best);
        newSession.setPracticeMode(carriedPractice);

        // Give hotbar items
        if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);

        // Spawn NPC and hologram at new island
        if (plugin.getNpcManager() != null) plugin.getNpcManager().spawnNpc(player, map.getIslandNpcLocation(targetIsland));
        if (plugin.getHologramManager() != null) plugin.getHologramManager().updateHologram(map.getName(), targetIsland, player);

        // Restore end island at base distance on the new island slot
        if (map.hasCustomLength()) {
            int len = map.hasEndIsland() ? map.getBaseCustomLength() : map.getMinCustomLength();
            if (len > 0) placeEndPlatform(player, map, newSession, len);
        }

        // Apply the player's selected design on the new island
        applyPlayerDesign(player, map, targetIsland);

        plugin.getScoreboardManager().updateScoreboard(player);
    }

    // =========================================================================
    // Island design helpers
    // =========================================================================

    /**
     * Re-paste the default island template at island slot {@code islandIndex}, erasing any
     * custom design the previous player had applied. Called before a player leaves or switches.
     * The operation is async-batched so it does not freeze the server.
     */
    public void revertIslandDesign(net.gravijet.fastbuilder.map.MapData map, int islandIndex) {
        if (map.getTemplateFile() == null) return;
        org.bukkit.Location min = map.getIslandMin(islandIndex);
        org.bukkit.Location max = map.getIslandMax(islandIndex);
        if (min == null || map.getWorld() == null) return;

        plugin.getFawePaster().clearRegion(
                map.getWorld(),
                min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                max.getBlockX(), max.getBlockY(), max.getBlockZ(),
                () -> plugin.getFawePaster().pasteTemplate(
                        map.getWorld(), map.getTemplateFile(),
                        min.getBlockX(), min.getBlockY(), min.getBlockZ(), null)
        );
    }

    /**
     * Apply the player's selected island design to the given island slot.
     *
     * <ul>
     *   <li>Pastes the chosen schematic over the island area.</li>
     *   <li>If the design has a {@link net.gravijet.fastbuilder.map.MapData.DesignProfile},
     *       teleports the player to the design's spawn point and stores the finish-zone
     *       override so finish detection uses the correct coordinates.</li>
     *   <li>For custom-length maps with a different island width, shifts the end-island
     *       to maintain the same visual gap.</li>
     *   <li>Does nothing if the player has the default design (or no selection).</li>
     * </ul>
     */
    public void applyPlayerDesign(Player player, net.gravijet.fastbuilder.map.MapData map, int islandIndex) {
        net.gravijet.fastbuilder.player.PlayerData pData =
                plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (pData == null) return;

        String selectedDesign = pData.getSelectedDesign(map.getName());

        // Default design: clear any stale per-player overrides and do nothing else
        if (selectedDesign == null || selectedDesign.equals(map.getTemplateFile())) {
            pData.clearActiveFinishZone(map.getName());
            return;
        }
        if (!map.getAllTemplates().contains(selectedDesign)) return;

        org.bukkit.Location min = map.getIslandMin(islandIndex);
        org.bukkit.Location max = map.getIslandMax(islandIndex);
        if (min == null || map.getWorld() == null) return;

        plugin.getFawePaster().clearRegion(
                map.getWorld(),
                min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                max.getBlockX(), max.getBlockY(), max.getBlockZ(),
                () -> plugin.getFawePaster().pasteTemplate(
                        map.getWorld(), selectedDesign,
                        min.getBlockX(), min.getBlockY(), min.getBlockZ(), null)
        );

        // Apply design profile if one has been recorded for this template
        net.gravijet.fastbuilder.map.MapData.DesignProfile profile =
                map.getDesignProfile(selectedDesign);
        if (profile != null) {
            // Teleport player to the design-specific spawn position
            org.bukkit.Location profileSpawn = new org.bukkit.Location(
                    map.getWorld(),
                    map.getOriginX() + profile.spawnOffsetX,
                    map.getOriginY() + profile.spawnOffsetY,
                    map.getOriginZ() + (long) islandIndex * map.getActualZStep() + profile.spawnOffsetZ,
                    profile.spawnYaw, profile.spawnPitch
            );
            player.teleport(profileSpawn);

            // Store finish-zone override so GameplayListener uses the correct bounds
            if (profile.hasFinishZone()) {
                pData.setActiveFinishZone(map.getName(),
                        profile.finishMinX, profile.finishMinY, profile.finishMinZ,
                        profile.finishMaxX, profile.finishMaxY, profile.finishMaxZ);
            } else {
                pData.clearActiveFinishZone(map.getName());
            }

            // Respawn NPC at the design-profile NPC position if one is recorded
            if (profile.hasNpcPosition() && plugin.getNpcManager() != null) {
                long diagX = (long) islandIndex * map.getDiagonalStepX();
                long slotZ = map.getOriginZ() + (long) islandIndex * map.getActualZStep();
                org.bukkit.Location npcLoc = new org.bukkit.Location(
                        map.getWorld(),
                        map.getOriginX() + diagX + profile.npcOffsetX,
                        map.getOriginY() + profile.npcOffsetY,
                        slotZ + profile.npcOffsetZ,
                        profile.npcYaw, profile.npcPitch
                );
                plugin.getNpcManager().despawnNpc(player.getUniqueId());
                plugin.getNpcManager().spawnNpc(player, npcLoc);
            }

            // For custom-length maps: if this design has a different island width,
            // shift the end-island so the physical gap stays the same.
            if (map.hasEndIsland() && profile.islandWidth > 0
                    && profile.islandWidth != map.getIslandWidth()) {
                RunSession session = activeSessions.get(player.getUniqueId());
                if (session != null) {
                    int widthDiff = profile.islandWidth - map.getIslandWidth();
                    int curLen = pData.getCustomLength(map.getName());
                    if (curLen <= 0) curLen = map.getBaseCustomLength();
                    int adjusted = Math.max(map.getEffectiveMinCustomLength(),
                            Math.min(map.getEffectiveMaxCustomLength(), curLen + widthDiff));
                    pData.setCustomLength(map.getName(), adjusted);
                    placeEndPlatform(player, map, session, adjusted);
                }
            }
        } else {
            pData.clearActiveFinishZone(map.getName());
        }
    }

    /**
     * Returns the effective spawn location for the given player on the given island,
     * accounting for any active design profile spawn override.
     */
    public org.bukkit.Location getEffectiveSpawn(UUID uuid,
                                                   net.gravijet.fastbuilder.map.MapData map,
                                                   int islandIndex) {
        net.gravijet.fastbuilder.player.PlayerData pData =
                plugin.getPlayerManager().getCachedData(uuid);
        if (pData != null) {
            String design = pData.getSelectedDesign(map.getName());
            if (design != null && !design.equals(map.getTemplateFile())) {
                net.gravijet.fastbuilder.map.MapData.DesignProfile profile =
                        map.getDesignProfile(design);
                if (profile != null) {
                    return new org.bukkit.Location(
                            map.getWorld(),
                            map.getOriginX() + profile.spawnOffsetX,
                            map.getOriginY() + profile.spawnOffsetY,
                            map.getOriginZ() + (long) islandIndex * map.getActualZStep()
                                    + profile.spawnOffsetZ,
                            profile.spawnYaw, profile.spawnPitch
                    );
                }
            }
        }
        return map.getIslandSpawn(islandIndex);
    }

    // =========================================================================
    // End-island platform management
    // =========================================================================

    /**
     * Place (or replace) the end-island / end-platform for a player.
     *
     * <ul>
     *   <li>When {@code map.hasEndIsland()} is true: clears the old region and pastes the
     *       end-island template at the new position (real-time schematic movement).</li>
     *   <li>Otherwise (legacy): places a thin row of glass-pane blocks as before.</li>
     * </ul>
     */
    public void placeEndPlatform(Player player, MapData map, RunSession session, int customLength) {
        UUID uuid = player.getUniqueId();
        clearEndPlatform(uuid);

        if (customLength <= 0 || !map.hasCustomLength()) return;

        // Clamp to effective bounds
        customLength = Math.max(map.getEffectiveMinCustomLength(),
                Math.min(map.getEffectiveMaxCustomLength(), customLength));

        if (map.hasEndIsland()) {
            placeEndIslandTemplate(uuid, map, session.getIslandIndex(), customLength);
        } else {
            placeEndPlatformLegacy(uuid, map, session, customLength);
        }
    }

    /** New system: paste the end-island template at the computed position. */
    private void placeEndIslandTemplate(UUID uuid, MapData map, int islandIndex, int customLength) {
        org.bukkit.World world = map.getWorld();
        if (world == null) return;

        // Apply per-player Y offset (set via the Custom Length sub-menu)
        net.gravijet.fastbuilder.player.PlayerData pData =
                plugin.getPlayerManager().getCachedData(uuid);
        int yAdjust = pData != null ? pData.getCustomLengthY(map.getName()) : 0;

        // endX = east edge of start island + gap to west edge of end island
        int endX = map.getOriginX() + map.getIslandWidth() - 1 + customLength;
        int endY = map.getOriginY() + map.getEndIslandYOffset() + yAdjust;
        int endZ = map.getOriginZ() + islandIndex * map.getActualZStep() + map.getEndIslandZOffset();

        // Record the region so we can clear it later
        endIslandRegions.put(uuid, new int[]{
            endX, endY, endZ,
            map.getEndIslandWidth(), map.getEndIslandHeight(), map.getEndIslandLength()
        });

        // Clear the target area first, then paste the template into it.
        // Also force-load all chunks between start and end island so spectators and
        // late-joining players can see the destination regardless of view distance.
        int clearMaxX = endX + map.getEndIslandWidth()  - 1;
        int clearMaxY = endY + map.getEndIslandHeight() - 1;
        int clearMaxZ = endZ + map.getEndIslandLength()  - 1;
        forceLoadChunkCorridor(world,
                map.getOriginX(), endY, map.getOriginZ() + islandIndex * map.getActualZStep(),
                clearMaxX, clearMaxY, clearMaxZ);
        plugin.getFawePaster().clearRegion(world, endX, endY, endZ, clearMaxX, clearMaxY, clearMaxZ, () ->
            plugin.getFawePaster().pasteTemplate(world, map.getEndIslandTemplateFile(), endX, endY, endZ, null)
        );
    }

    /** Legacy system: a thin row of glass-pane blocks. */
    @SuppressWarnings("deprecation")
    private void placeEndPlatformLegacy(UUID uuid, MapData map, RunSession session, int customLength) {
        int platformX = map.getOriginX() + (int) map.getSpawnOffsetX() + customLength;
        int platformY = map.getOriginY() + map.getFinishMinY();
        int islandBaseZ = map.getOriginZ() + session.getIslandIndex() * map.getActualZStep();
        int minZ = islandBaseZ + map.getFinishMinZ();
        int maxZ = islandBaseZ + map.getFinishMaxZ();

        int configDepth = plugin.getConfigManager().getEndPlatformDepth();
        if (configDepth > 0 && (maxZ - minZ + 1) > configDepth) {
            int zCenter = (minZ + maxZ) / 2;
            minZ = zCenter - configDepth / 2;
            maxZ = minZ + configDepth - 1;
        }

        String matStr = plugin.getConfigManager().getEndPlatformMaterial();
        Material mat;
        byte matData = 0;
        try {
            if (matStr.contains(":")) {
                String[] parts = matStr.split(":");
                mat = Material.getMaterial(parts[0].toUpperCase());
                matData = (byte) Integer.parseInt(parts[1]);
            } else {
                mat = Material.getMaterial(matStr.toUpperCase());
            }
            if (mat == null || mat == Material.AIR) throw new IllegalArgumentException("bad material");
        } catch (Exception e) {
            mat = Material.STAINED_GLASS_PANE;
            matData = 5;
        }

        org.bukkit.World world = map.getWorld();
        if (world == null) return;

        List<Location> platform = new ArrayList<>();
        Map<String, int[]> origStates = new HashMap<>();
        for (int z = minZ; z <= maxZ; z++) {
            Location loc = new Location(world, platformX, platformY, z);
            org.bukkit.block.Block block = loc.getBlock();
            String key = platformX + "," + platformY + "," + z;
            origStates.put(key, new int[]{block.getTypeId(), block.getData()});
            block.setTypeIdAndData(mat.getId(), matData, false);
            platform.add(loc);
        }
        endPlatforms.put(uuid, platform);
        endPlatformOrigStates.put(uuid, origStates);
    }

    /**
     * Remove the end-island / end-platform for a player.
     * Handles both the new end-island template system and the legacy glass-pane platform.
     */
    public void clearEndPlatform(UUID uuid) {
        // New end-island template system
        int[] region = endIslandRegions.remove(uuid);
        if (region != null) {
            // Find the map for this player to get the world
            RunSession sess = activeSessions.get(uuid);
            if (sess != null) {
                net.gravijet.fastbuilder.map.MapData m =
                        plugin.getMapManager().getMap(sess.getMapName());
                if (m != null && m.getWorld() != null) {
                    plugin.getFawePaster().clearRegion(
                            m.getWorld(),
                            region[0], region[1], region[2],
                            region[0] + region[3] - 1,
                            region[1] + region[4] - 1,
                            region[2] + region[5] - 1,
                            null);
                }
            }
        }

        // Legacy end-platform (glass pane rows)
        @SuppressWarnings("deprecation")
        List<Location> platform = endPlatforms.remove(uuid);
        Map<String, int[]> origStates = endPlatformOrigStates.remove(uuid);
        if (platform != null) {
            for (Location loc : platform) {
                String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
                int[] orig = origStates != null ? origStates.get(key) : null;
                org.bukkit.block.Block block = loc.getBlock();
                if (orig != null && orig[0] != 0) {
                    //noinspection deprecation
                    block.setTypeIdAndData(orig[0], (byte) orig[1], false);
                } else {
                    block.setType(Material.AIR);
                }
            }
        }
    }

    /** Remove all active end platforms/islands (called on plugin disable). */
    public void clearAllEndPlatforms() {
        for (UUID uuid : new ArrayList<>(endPlatforms.keySet())) {
            clearEndPlatform(uuid);
        }
        for (UUID uuid : new ArrayList<>(endIslandRegions.keySet())) {
            clearEndPlatform(uuid);
        }
    }

    /**
     * Returns the current end-island position for a player, or null if none.
     * Returns {endX, endY, endZ, endWidth, endHeight, endLength}.
     */
    public int[] getEndIslandRegion(UUID uuid) {
        return endIslandRegions.get(uuid);
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

        // Block removed sounds — Portal (long ambient), LevelUp (removed from selector)
        if (soundKey.toUpperCase().contains("PORTAL")) return;
        if (soundKey.equalsIgnoreCase("LevelUp")) return;

        // Migrate legacy key names from previous DEATH_SOUNDS map format
        if (soundKey.equalsIgnoreCase("CreeperPrime") || soundKey.equalsIgnoreCase("CreeperHiss"))
            soundKey = "Creeper";
        else if (soundKey.equalsIgnoreCase("AnvilLand")) soundKey = "Anvil";
        else if (soundKey.equalsIgnoreCase("BlazeDeath")) soundKey = "Blaze";
        else if (soundKey.equalsIgnoreCase("FireworkBlast")) soundKey = "Firework";

        String soundEnum = DEATH_SOUNDS.getOrDefault(soundKey, soundKey);
        if (soundEnum == null) return;

        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundEnum.toUpperCase());
            // Play to the player at a slightly elevated volume so it's unmistakably heard.
            player.playSound(player.getLocation(), sound, 1.5f, 1.0f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid death sound '" + soundEnum + "' for player "
                    + player.getName() + " — check your guis.yml death-sound-selector-slots.");
        }
    }

    @SuppressWarnings("deprecation")
    private void clearBlocksWithAnimation(final Player player, List<Location> blocks,
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
            clearBlocksCreativeNpcWithPlayer(player, blocks, practiceBlocks, origStates, isPracticeMode);
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
                    // Skip materials that don't have valid item representations (avoids purple-black missing texture).
                    // mat.isItem() does not exist in 1.8.8; instead try creating an ItemStack and catch the exception.
                    boolean hasItem;
                    try {
                        new org.bukkit.inventory.ItemStack(mat, 1);
                        hasItem = mat != Material.AIR && mat.getId() < 256;
                    } catch (Exception ex) {
                        hasItem = false;
                    }
                    if (!hasItem) {
                        // Restore block directly without dropping item
                        String skipKey = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
                        int[] skipOrig = origStates.get(skipKey);
                        if (skipOrig != null && skipOrig[0] != 0) block.setTypeIdAndData(skipOrig[0], (byte) skipOrig[1], false);
                        else block.setType(Material.AIR);
                        continue;
                    }
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
        clearBlocksCreativeNpcWithPlayer(null, blocks, practiceBlocks, origStates, isPracticeMode);
    }

    @SuppressWarnings("deprecation")
    private void clearBlocksCreativeNpcWithPlayer(Player owner, List<Location> blocks, List<Location> practiceBlocks,
                                         Map<String, int[]> origStates, boolean isPracticeMode) {
        List<Location> toClear = new ArrayList<>();
        for (Location loc : blocks) {
            if (isPracticeMode && practiceBlocks.contains(loc)) continue;
            Block block = loc.getBlock();
            if (block == null || block.getType() == Material.AIR) continue;
            toClear.add(loc);
        }
        if (toClear.isEmpty()) return;

        // Spawn NPC at the first block location, mirroring the player's skin and name.
        net.citizensnpcs.api.npc.NPC[] npcRef = new net.citizensnpcs.api.npc.NPC[1];
        try {
            net.citizensnpcs.api.npc.NPCRegistry reg = net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
            String npcName = owner != null ? owner.getName() : "Builder";
            net.citizensnpcs.api.npc.NPC npc = reg.createNPC(
                    org.bukkit.entity.EntityType.PLAYER, npcName);
            if (owner != null) {
                npc.data().set("player-skin-uuid", owner.getUniqueId().toString());
                npc.data().set("player-skin-name", owner.getName());
            }
            Location spawnLoc = toClear.get(0).clone().add(0.5, 0, 0.5);
            npc.spawn(spawnLoc);
            npcRef[0] = npc;

            // Zero-tick tablist removal: send REMOVE_PLAYER immediately in the same tick
            // so the NPC never flashes in the tab list for any viewer.
            if (npc.isSpawned() && npc.getEntity() instanceof org.bukkit.entity.Player) {
                try {
                    org.bukkit.entity.Player npcEntity = (org.bukkit.entity.Player) npc.getEntity();
                    String ver = org.bukkit.Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
                    Object nmsPlayer = npcEntity.getClass().getMethod("getHandle").invoke(npcEntity);
                    Class<?> pktClass = Class.forName("net.minecraft.server." + ver + ".PacketPlayOutPlayerInfo");
                    Class<?> enumClass = Class.forName("net.minecraft.server." + ver + ".PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
                    Class<?> entityPlayerClass = Class.forName("net.minecraft.server." + ver + ".EntityPlayer");
                    Object removeAction = java.lang.reflect.Array.get(enumClass.getMethod("values").invoke(null), 4); // REMOVE_PLAYER = index 4
                    Object entityPlayerArr = java.lang.reflect.Array.newInstance(entityPlayerClass, 1);
                    java.lang.reflect.Array.set(entityPlayerArr, 0, nmsPlayer);
                    Object removePacket = pktClass.getDeclaredConstructors()[0].newInstance(removeAction, entityPlayerArr);
                    for (org.bukkit.entity.Player viewer : spawnLoc.getWorld().getPlayers()) {
                        Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
                        Object conn = handle.getClass().getField("playerConnection").get(handle);
                        Class<?> packetIface = Class.forName("net.minecraft.server." + ver + ".Packet");
                        conn.getClass().getMethod("sendPacket", packetIface).invoke(conn, removePacket);
                    }
                } catch (Exception ignored) {}
            }
        } catch (NoClassDefFoundError | Exception ignored) {}

        // Slower pace for more visible NPC animation (3 blocks/tick, every 2 ticks = visible swing)
        final int BLOCKS_PER_TICK = 3;
        final long TICK_INTERVAL = 2L;
        final int[] idx = {0};
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < BLOCKS_PER_TICK && idx[0] < toClear.size(); i++, idx[0]++) {
                    Location loc = toClear.get(idx[0]);
                    Block block = loc.getBlock();
                    if (block == null || block.getType() == Material.AIR) continue;
                    // Teleport NPC to block so it appears to be mining it
                    if (npcRef[0] != null && npcRef[0].isSpawned()) {
                        try {
                            org.bukkit.entity.Entity e = npcRef[0].getEntity();
                            e.teleport(loc.clone().add(0.5, 0, 0.5));
                            // Arm swing animation — swingMainHand() is 1.9+; broadcast NMS packet for 1.8.8
                            try {
                                Object nmsEntity = e.getClass().getMethod("getHandle").invoke(e);
                                Object packet = Class.forName(nmsEntity.getClass().getPackage().getName() + ".PacketPlayOutAnimation")
                                        .getConstructor(nmsEntity.getClass(), int.class).newInstance(nmsEntity, 0);
                                for (org.bukkit.entity.Player viewer : e.getWorld().getPlayers()) {
                                    Object conn = viewer.getClass().getMethod("getHandle").invoke(viewer);
                                    Object playerConn = conn.getClass().getField("playerConnection").get(conn);
                                    playerConn.getClass().getMethod("sendPacket", Class.forName(
                                            nmsEntity.getClass().getPackage().getName() + ".Packet")).invoke(playerConn, packet);
                                }
                            } catch (Exception ignored) {}
                        } catch (Exception ignored) {}
                    }
                    // Block-breaking step sound and dig effect
                    try {
                        loc.getWorld().playEffect(loc, org.bukkit.Effect.STEP_SOUND, block.getTypeId());
                        loc.getWorld().playSound(loc, org.bukkit.Sound.DIG_STONE, 0.5f, 1.0f + (float)(Math.random() * 0.4f));
                    } catch (Exception ignored) {}
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
        }.runTaskTimer(plugin, 0L, TICK_INTERVAL);
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
    @SuppressWarnings("deprecation")
    public void clearAllPlacedBlocks(UUID uuid) {
        RunSession session = activeSessions.get(uuid);
        if (session == null) return;

        Map<String, int[]> origStates = session.getOriginalBlockStates();
        for (Location loc : session.getPlacedBlocks()) {
            Block block = loc.getBlock();
            if (block == null) continue;
            String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
            int[] orig = origStates.get(key);
            if (orig != null && orig[0] != 0) {
                block.setTypeIdAndData(orig[0], (byte) orig[1], false);
            } else {
                block.setType(Material.AIR);
            }
        }
        for (Location loc : session.getPracticeBlocks()) {
            Block block = loc.getBlock();
            if (block == null) continue;
            String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
            int[] orig = origStates.get(key);
            if (orig != null && orig[0] != 0) {
                block.setTypeIdAndData(orig[0], (byte) orig[1], false);
            } else {
                block.setType(Material.AIR);
            }
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

                    String timer;
                    if (session.isRunning()) {
                        timer = TimeUtil.formatTime(session.getElapsed());
                    } else {
                        long lastTime = lastFinishTimes.containsKey(entry.getKey())
                                ? lastFinishTimes.get(entry.getKey()) : -1L;
                        timer = lastTime > 0 ? TimeUtil.formatTime(lastTime) : "0,000";
                    }

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

    /**
     * Force-loads every chunk in the rectangular corridor between two block coordinates,
     * batched across ticks so large distances (1700+ blocks ≈ 200+ chunks) don't cause
     * a server freeze.  Chunk loading is done on the main thread in batches of 10 per tick.
     */
    private void forceLoadChunkCorridor(org.bukkit.World world,
                                         int fromX, int fromY, int fromZ,
                                         int toX,   int toY,   int toZ) {
        int minCX = Math.min(fromX, toX) >> 4;
        int maxCX = Math.max(fromX, toX) >> 4;
        int minCZ = Math.min(fromZ, toZ) >> 4;
        int maxCZ = Math.max(fromZ, toZ) >> 4;

        List<int[]> chunks = new ArrayList<>();
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    chunks.add(new int[]{cx, cz});
                }
            }
        }
        if (chunks.isEmpty()) return;

        final int batchSize = 10;
        final int[] idx = {0};
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < batchSize && idx[0] < chunks.size(); i++, idx[0]++) {
                    int[] c = chunks.get(idx[0]);
                    if (!world.isChunkLoaded(c[0], c[1])) {
                        world.loadChunk(c[0], c[1], true);
                    }
                }
                if (idx[0] >= chunks.size()) this.cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static String formatMult(double mult) {
        if (mult == Math.floor(mult)) return (int) mult + "x";
        return String.format("%.1fx", mult);
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
        clearAllEndPlatforms();
        activeSessions.clear();
        finishCooldown.clear();
        buildModePlayers.clear();
        globalSessionBests.clear();
    }

    public Map<UUID, RunSession> getActiveSessions() { return activeSessions; }
}
