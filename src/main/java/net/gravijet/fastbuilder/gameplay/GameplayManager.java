package net.gravijet.fastbuilder.gameplay;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active bridging runs: timer, finish detection, fall detection, session bests.
 */
public class GameplayManager {

    private final FastBuilder plugin;
    private final Map<UUID, RunSession> activeSessions = new ConcurrentHashMap<>();

    private static final int PRACTICE_BLOCK_ID = 159; // STAINED_CLAY
    private static final byte PRACTICE_BLOCK_DATA = 5; // Lime

    private int actionbarTaskId = -1;

    private final BlockAnimator blockAnimator;
    private final EndPlatformManager endPlatformManager;
    private final FinishCelebration finishCelebration;

    private final java.util.Set<UUID> finishCooldown = new java.util.HashSet<>();
    private final java.util.Set<UUID> buildModePlayers = new java.util.HashSet<>();

    // Islands currently being reset — key format: "mapName:islandIndex"
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

    // Global session bests: per-player best time this session (keyed by UUID to survive renames)
    private final java.util.LinkedHashMap<UUID, Long> globalSessionBests = new java.util.LinkedHashMap<>();
    // Kept for backward compat
    private long globalSessionBestTime = -1;
    private String globalSessionBestPlayer = null;

    // Infinite session bests: per-player best {distance, timeMs} this session
    private final java.util.LinkedHashMap<UUID, long[]> infiniteSessionBests = new java.util.LinkedHashMap<>();

    // Last finished run data — shown on scoreboard/actionbar until the next run starts
    private final Map<UUID, Long>    lastFinishTimes  = new HashMap<>();
    private final Map<UUID, Integer> lastFinishBlocks = new HashMap<>();

    private int deathCheckTaskId = -1;

    public GameplayManager(FastBuilder plugin) {
        this.plugin = plugin;
        this.blockAnimator = new BlockAnimator(plugin);
        this.endPlatformManager = new EndPlatformManager(plugin, activeSessions);
        this.finishCelebration = new FinishCelebration(plugin);
        startActionbarTask();
        startDeathCheckTask();
    }

    /**
     * Per-tick exact-Y death check for maps with a configured deathY threshold.
     * PlayerMoveEvent only fires on block changes, causing up to a 1-block lag.
     * This task checks the player's sub-block Y every tick so death is detected
     * within 50 ms of crossing the threshold.
     */
    // Players currently mid-island-hop — suppress death check during the switch frame
    private final Set<UUID> hoppingPlayers = Collections.synchronizedSet(new HashSet<>());

    /** Mark a player as currently hopping (suppress death check). */
    public void markIslandHopping(UUID uuid) { hoppingPlayers.add(uuid); }
    /** Unmark after the hop completes. */
    public void unmarkIslandHopping(UUID uuid) { hoppingPlayers.remove(uuid); }

    private void startDeathCheckTask() {
        deathCheckTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : new ArrayList<>(activeSessions.keySet())) {
                    RunSession sess = activeSessions.get(uuid);
                    if (sess == null || sess.isResetting()) continue;
                    if (buildModePlayers.contains(uuid)) continue;
                    if (hoppingPlayers.contains(uuid)) continue;
                    Player pl = Bukkit.getPlayer(uuid);
                    if (pl == null || !pl.isOnline()) continue;
                    MapData map = plugin.getMapManager().getMap(sess.getMapName());
                    if (map == null || !map.hasDeathY()) continue;
                    // Detect at deathY+1.8 so the player is reset before visually falling below deathY
                    if (pl.getLocation().getY() < map.getDeathY() + 1.8) {
                        onFall(pl);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L).getTaskId();
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
        lastFinishTimes.remove(uuid);
        lastFinishBlocks.remove(uuid);
        finishCooldown.remove(uuid);
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
                net.gravijet.fastbuilder.util.Messages.send(player, "practice-blocks-required-clear");
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

        // Enforce max block limit for infinite maps (matches CustomLength's max)
        MapData infLimitMap = plugin.getMapManager().getMap(session.getMapName());
        if (infLimitMap != null && infLimitMap.isInfinite()) {
            int maxBlocks = infLimitMap.getEffectiveMaxCustomLength();
            if (session.getPlacedBlocks().size() >= maxBlocks) {
                // Trigger fall/death — player reached the limit
                onFall(player);
                return;
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

        // Cache the map lookup once for use throughout onFinish
        MapData map = plugin.getMapManager().getMap(session.getMapName());

        // Infinite mode maps have no finish condition
        if (map != null && map.isInfinite()) {
            finishCooldown.remove(uuid);
            return;
        }

        // Anticheat: reject times below the configured minimum valid time BEFORE committing finish
        long mapMinTime = -1;
        if (map != null && map.getMinValidTime() > 0) {
            mapMinTime = map.getMinValidTime();
        }
        long globalMinTime = plugin.getConfigManager().getMinValidTime();
        long effectiveMin = mapMinTime > 0 ? mapMinTime : globalMinTime;

        long rawElapsed = session.peekElapsed();
        if (effectiveMin > 0 && rawElapsed < effectiveMin) {
            if (plugin.getReplayManager() != null) {
                plugin.getReplayManager().stopRecording(player.getUniqueId(), false);
            }
            finishCooldown.remove(uuid);
            String msg = plugin.getConfigManager().getMessage("min-time-not-recorded");
            if (msg == null || msg.isEmpty()) msg = "%prefix%&cTime too fast to be recorded &7(&f%time%&7).";
            msg = msg.replace("%time%", net.gravijet.fastbuilder.util.TimeUtil.formatTime(rawElapsed))
                    .replace("%prefix%", plugin.getConfigManager().getPrefix());
            player.sendMessage(net.gravijet.fastbuilder.util.ColorUtil.translate(msg));
            // Reset without counting as attempt.
            // Re-lookup player by UUID so a stale Player object never reaches resetRun.
            UUID minTimeUuid = uuid;
            new BukkitRunnable() {
                @Override public void run() {
                    Player mp = Bukkit.getPlayer(minTimeUuid);
                    if (mp != null && mp.isOnline()) resetRun(mp);
                }
            }.runTaskLater(plugin, 5L);
            return;
        }

        long time = session.finish();

        if (plugin.getReplayManager() != null) {
            plugin.getReplayManager().stopRecording(player.getUniqueId(), true);
        }

        PlayerData data = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        if (data == null) {
            finishCooldown.remove(uuid);
            return;
        }

        // Disable stats for practice mode, Infinite mode and Custom Length mode.
        // Practice runs (including any run that used practice blocks at any point)
        // must NEVER contribute to session top-3 or the global leaderboard.
        // practiceUsedThisRun() is sticky — survives the "toggle practice off mid-run" cheat.
        MapData statsMap = map;
        boolean playerCustomLengthActive = statsMap != null && statsMap.hasCustomLength()
                && data.isCustomLengthEnabled(session.getMapName());
        boolean statsDisabled = session.isPracticeMode()
                || session.hasPracticeBlocks()
                || session.practiceUsedThisRun()
                || (statsMap != null && statsMap.isInfinite())
                || playerCustomLengthActive;

        // Session top-3 (scoreboard) and global session bests must reflect only
        // legitimate, ranked runs — exclude practice (mode on OR practice blocks placed)
        // so a player who toggled practice mid-run can't sneak a PB onto the leaderboard.
        boolean wasPractice = session.isPracticeMode()
                || session.hasPracticeBlocks()
                || session.practiceUsedThisRun();
        if (!wasPractice) {
            session.addSessionBest(time);

            // Update global session bests (keyed by UUID — immune to player renames)
            Long existing = globalSessionBests.get(uuid);
            if (existing == null || time < existing) {
                globalSessionBests.put(uuid, time);
            }
            if (globalSessionBestTime < 0 || time < globalSessionBestTime) {
                globalSessionBestTime = time;
                globalSessionBestPlayer = player.getName();
            }
        }

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

            // Resolve booster state so we can display it in title
            double boostMult = plugin.getBoosterManager().getMultiplier(player);
            boolean hasBoost = boostMult > 1.01;
            // boostLabel used in %booster% placeholder: e.g. "3xx Booster"
            String boostLabel = hasBoost ? formatMult(boostMult) + " Booster" : "";
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

            // Booster display is title-only \u2014 no chat message

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
                            ? "&6" + formatMult(boostMult) + " Booster active"
                            : subtitle + " &8| &6" + formatMult(boostMult);
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
            if (map != null) {
                String rank = map.getPlayerRank(stats.bestTime);
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
            // Practice or stats-disabled mode: show time but note it's not saved.
            // Titles come from messages.yml (finish.*) so the wording stays consistent
            // with the rest of the plugin and is fully configurable.
            String titleKey = session.isPracticeMode() ? "practice-title" : "finish-title";
            String titleTpl = plugin.getConfigManager().getFinishMessage(titleKey);
            if (titleTpl == null || titleTpl.isEmpty()) {
                titleTpl = session.isPracticeMode() ? "&6Practice &8» &f%time%" : "&aFinish &8» &f%time%";
            }
            String noteSuffix = "";
            if (statsMap != null && statsMap.isInfinite()) {
                int blocks = session.getPlacedBlocks().size() - session.getPracticeBlocks().size();
                noteSuffix = formatFinishNote("note-infinite", "&7Infinite &8» &f%blocks% blocks", blocks);
            } else if (playerCustomLengthActive) {
                int activeDist = data.getCustomLength(session.getMapName());
                if (activeDist <= 0) activeDist = statsMap != null && statsMap.getBaseCustomLength() > 0
                        ? statsMap.getBaseCustomLength() : 0;
                // Record custom-length bests (per distance, not globally ranked) — but never
                // for practice runs, so a practice attempt can't claim a custom-length record.
                if (activeDist > 0 && statsMap != null && !wasPractice) {
                    activeDist = Math.max(statsMap.getEffectiveMinCustomLength(),
                            Math.min(statsMap.getEffectiveMaxCustomLength(), activeDist));
                    data.updateCustomLengthBest(session.getMapName(), activeDist, time);
                    data.updateCustomLengthSessionBest(session.getMapName(), activeDist, time);
                    plugin.getPlayerManager().savePlayerData(player.getUniqueId());
                }
                noteSuffix = formatFinishNote("note-custom", "&7Custom &8» &f%blocks% blocks", activeDist);
            }
            player.sendTitle(ColorUtil.translate(titleTpl.replace("%time%", TimeUtil.formatTime(time))),
                    ColorUtil.translate(noteSuffix));
        }

        // Store last-finish data for scoreboard/actionbar persistence
        lastFinishTimes.put(uuid, time);
        lastFinishBlocks.put(uuid, session.getPlacedBlocks().size());

        // Immediately push the actionbar with the rounded finish time so it matches the
        // title exactly — without this the actionbar would lag 1 tick behind.
        String abFmt = plugin.getConfigManager().getActionBar();
        if (abFmt != null && !abFmt.isEmpty()) {
            String abMsg = abFmt.replace("%time%", TimeUtil.formatTime(time))
                    .replace("%timer%", TimeUtil.formatTime(time));
            sendActionBar(player, ColorUtil.translate(abMsg));
        }

        // Massive celebration — full visual fireworks only on a new PB
        finishCelebration.launch(player, player.getLocation(), isNewPB);

        plugin.getScoreboardManager().updateScoreboard(player);

        // Start the reset animation IMMEDIATELY (spec: "trigger instantly")
        startResetAnimation(player);

        // Finalize reset (teleport + hotbar) after 2 seconds so player sees celebration.
        // Re-lookup player by UUID so a stale Player object never reaches finalizeReset.
        new BukkitRunnable() {
            @Override
            public void run() {
                finishCooldown.remove(uuid);
                Player fp = Bukkit.getPlayer(uuid);
                if (fp != null && fp.isOnline()) finalizeReset(fp);
            }
        }.runTaskLater(plugin, 40L);
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
            // Only record fall attempts for normal (non-practice, non-infinite, non-custom) maps
            MapData fallMap = plugin.getMapManager().getMap(session.getMapName());
            if (plugin.getReplayManager() != null) {
                // Infinite/custom runs: save as successful when blocks were placed so the replay is watchable
                boolean infiniteOrCustom = fallMap != null && (fallMap.isInfinite() || fallMap.hasCustomLength());
                boolean saveable = infiniteOrCustom && session.getPlacedBlocks().size() > 0;
                plugin.getReplayManager().stopRecording(player.getUniqueId(), saveable);
            }
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

            // Record best infinite distance (blocks placed) + elapsed time on fall for infinite maps.
            // Practice runs (mode on, practice blocks placed, or the sticky flag) never count —
            // mirrors the finish-path stats gate so a practice attempt can't set a distance record.
            boolean infinitePractice = session.isPracticeMode()
                    || session.hasPracticeBlocks()
                    || session.practiceUsedThisRun();
            if (fallMap != null && fallMap.isInfinite() && !infinitePractice) {
                PlayerData infData = plugin.getPlayerManager().getCachedData(player.getUniqueId());
                if (infData != null) {
                    // Exclude practice blocks: only count real bridging blocks toward distance
                    int blockCount = session.getPlacedBlocks().size() - session.getPracticeBlocks().size();
                    long elapsed = session.getElapsed();
                    if (blockCount > 0) {
                        infData.updateInfiniteDistance(session.getMapName(), blockCount, elapsed);
                        // Update infinite session bests (highest distance, then lowest time on tie)
                        long[] cur = infiniteSessionBests.get(player.getUniqueId());
                        if (cur == null || blockCount > cur[0]
                                || (blockCount == cur[0] && elapsed < cur[1])) {
                            infiniteSessionBests.put(player.getUniqueId(), new long[]{blockCount, elapsed});
                        }
                    }
                }
            }

            // Award consolation coins on failed runs if configured — only when ≥12 blocks placed.
            // Title/subtitle wording comes from messages.yml (finish.*) for consistency.
            if (plugin.getConfigManager().isCoinsOnFailed() && session.getPlacedBlocks().size() >= 12) {
                int failCoins = plugin.getCoinManager().awardFailedRunCoins(player);
                if (failCoins > 0) {
                    String failTitle = plugin.getConfigManager().getFinishMessage("fail-title");
                    if (failTitle == null) failTitle = "";
                    String failCoinsTpl = plugin.getConfigManager().getFinishMessage("fail-coins");
                    if (failCoinsTpl == null || failCoinsTpl.isEmpty()) failCoinsTpl = "&7+%coins% coins";
                    player.sendTitle(
                        ColorUtil.translate(failTitle),
                        ColorUtil.translate(failCoinsTpl.replace("%coins%", String.valueOf(failCoins)))
                    );
                }
            }
        }

        playDeathSound(player);
        // Trigger animation instantly on death (spec requirement)
        startResetAnimation(player);
        // Teleport on the very next task slot — minimises post-death fall distance.
        // Re-lookup player by UUID so a stale Player object never reaches finalizeReset.
        UUID fallUuid = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player fp = Bukkit.getPlayer(fallUuid);
            if (fp != null && fp.isOnline()) finalizeReset(fp);
        });
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

        blockAnimator.clearBlocksWithAnimation(player, blocksCopy, practiceBlocksCopy, origStatesCopy, practice, animation);
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
        // startResetAnimation marks the session as resetting but does not remove it —
        // use the already-captured reference to avoid a redundant lookup.
        if (!activeSessions.containsKey(player.getUniqueId())) return;

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

        // Unblock block placements — island reset is complete
        unmarkIslandResetting(mapName, islandIndex);
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
        long carriedStartTime = session.getStartTime();
        boolean carriedRunning = session.isRunning();

        net.gravijet.fastbuilder.player.PlayerData data = plugin.getPlayerManager().getCachedData(uuid);

        // Revert old island to default design (so the next player gets a clean slate)
        revertIslandDesign(map, oldIsland);

        // Clear placed blocks and end island on old island (instant, no animation)
        clearAllPlacedBlocks(uuid);
        clearEndPlatform(uuid);
        // Restore the end island on the old slot to base distance so it looks correct for the next player
        if (map.hasEndIsland()) {
            restoreDefaultEndPlatform(map, oldIsland);
        }

        // Save the player's custom-length preference before clearing (will be re-applied on new island)
        int savedCustomLength = 0;
        int savedCustomLengthY = 0;
        if (data != null && map.hasCustomLength()) {
            savedCustomLength = data.getCustomLength(map.getName());
            savedCustomLengthY = data.getCustomLengthY(map.getName());
        }
        if (data != null) {
            data.setCustomLength(map.getName(), 0);
            data.setCustomLengthY(map.getName(), 0);
            data.clearActiveFinishZone(map.getName());
        }

        // Cleanup old island integrations
        if (plugin.getCpsListener() != null) plugin.getCpsListener().cleanupPlayer(uuid);
        if (plugin.getNpcManager() != null) plugin.getNpcManager().despawnNpc(uuid);
        if (plugin.getHologramManager() != null) plugin.getHologramManager().removeHologram(map.getName(), oldIsland);

        // Free old island, remove old session (do NOT clear global session bests — island switch preserves them)
        plugin.getMapManager().freeIsland(map.getName(), uuid);
        activeSessions.remove(uuid);
        unmarkIslandResetting(map.getName(), oldIsland);

        // Assign new island
        plugin.getMapManager().assignIsland(map.getName(), targetIsland, uuid, player.getName());
        if (data != null) {
            data.setLastIsland(targetIsland);
            data.setLastMap(map.getName());
        }

        // Teleport to new island spawn (design profile override if active) and ensure Survival
        player.teleport(getEffectiveSpawn(uuid, map, targetIsland));
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);

        // Create new session and carry over session bests, practice mode, and timer state
        RunSession newSession = createSession(uuid, map.getName(), targetIsland);
        for (Long best : carriedBests) newSession.addSessionBest(best);
        newSession.setPracticeMode(carriedPractice);
        if (carriedStartTime > 0) newSession.resumeTimerFrom(carriedStartTime, carriedRunning);

        // Give hotbar items
        if (plugin.getHotbarManager() != null) plugin.getHotbarManager().giveItems(player);

        // Restore end island at the player's preferred distance on the new island slot
        if (map.hasCustomLength()) {
            if (data != null && savedCustomLength > 0) {
                // Re-apply the player's saved preference
                data.setCustomLength(map.getName(), savedCustomLength);
                data.setCustomLengthY(map.getName(), savedCustomLengthY);
                placeEndPlatform(player, map, newSession, savedCustomLength);
            } else {
                int len = map.hasEndIsland() ? map.getBaseCustomLength() : map.getEffectiveMinCustomLength();
                if (len > 0) placeEndPlatform(player, map, newSession, len);
            }
        }

        // Reset/redesign the new island so the jumping player always starts on a clean slate
        // (same behaviour as the Map Selector → switch flow).
        String targetDesign = data != null ? data.getSelectedDesign(map.getName()) : null;
        boolean usesDefaultDesign = targetDesign == null || targetDesign.equals(map.getTemplateFile());
        if (usesDefaultDesign) {
            // Default design: simply repaint the default template over the new island slot.
            revertIslandDesign(map, targetIsland);
        } else {
            // Non-default design: applyPlayerDesign already does its own clear-and-paste of the chosen schematic.
            applyPlayerDesign(player, map, targetIsland);
        }

        // Spawn NPC at new island — skip default spawn when the design profile
        // already placed it at a custom position (done inside applyPlayerDesign).
        boolean switchHasNpc = false;
        boolean switchHasHolo = false;
        if (data != null) {
            String switchDesign = data.getSelectedDesign(map.getName());
            if (switchDesign != null && !switchDesign.equals(map.getTemplateFile())) {
                net.gravijet.fastbuilder.map.MapData.DesignProfile switchProf =
                        map.getDesignProfile(switchDesign);
                if (switchProf != null) {
                    if (switchProf.hasNpcPosition()) switchHasNpc = true;
                    if (switchProf.hasHologramPosition()) switchHasHolo = true;
                }
            }
        }
        if (plugin.getNpcManager() != null && !switchHasNpc) {
            plugin.getNpcManager().spawnNpc(player, map.getIslandNpcLocation(targetIsland));
        }
        if (plugin.getHologramManager() != null) {
            org.bukkit.Location switchHoloLoc = getEffectiveHologramLocation(
                    uuid, map, targetIsland);
            plugin.getHologramManager().updateHologramAt(map.getName(), targetIsland,
                    player, switchHoloLoc);
        }

        plugin.getScoreboardManager().updateScoreboard(player);
    }

    // =========================================================================
    // Island design helpers
    // =========================================================================

    /**
     * Computes the union clear box for an island slot: a region large enough to cover the
     * default island AND every known design's full footprint (width/height/length and
     * vertical shift), so that switching to a smaller/shorter/lower design never leaves
     * leftover blocks from a previously-applied larger one.
     *
     * Each design's footprint is taken from its {@link MapData.DesignProfile} when one was
     * saved (via {@code /map setdesignmeta}); otherwise it falls back to the raw schematic
     * bounding box so admin-added designs without metadata are still fully covered.
     *
     * @return {@code int[]{minX, minY, minZ, maxX, maxY, maxZ}} in absolute world coords.
     */
    private int[] computeDesignUnionClearBox(net.gravijet.fastbuilder.map.MapData map, int islandIndex) {
        org.bukkit.Location min = map.getIslandMin(islandIndex);
        org.bukkit.Location max = map.getIslandMax(islandIndex);

        int maxDesignWidth  = map.getIslandWidth();
        int maxDesignHeight = map.getIslandHeight();
        int maxDesignLength = map.getIslandLength();
        int maxShiftUp   = 0;
        int maxShiftDown = 0;

        java.util.Set<String> allTemplates = new java.util.HashSet<>();
        allTemplates.addAll(map.getAlternativeTemplates());
        allTemplates.addAll(map.getCustomLengthTemplates());
        allTemplates.addAll(map.getInfiniteTemplates());
        for (String templateKey : allTemplates) {
            net.gravijet.fastbuilder.map.MapData.DesignProfile prof = map.getDesignProfile(templateKey);
            int pw, ph, pl;
            if (prof != null && (prof.islandWidth > 0 || prof.islandHeight > 0 || prof.islandLength > 0)) {
                pw = prof.islandWidth;
                ph = prof.islandHeight;
                pl = prof.islandLength;
            } else {
                int[] schemSize = plugin.getFawePaster().getSchematicSize(templateKey);
                if (schemSize == null) continue;
                pw = schemSize[0]; ph = schemSize[1]; pl = schemSize[2];
            }
            if (pw > maxDesignWidth)  maxDesignWidth  = pw;
            if (ph > maxDesignHeight) maxDesignHeight = ph;
            if (pl > maxDesignLength) maxDesignLength = pl;
            int shift = computeDesignYShift(map, prof);
            if (shift > maxShiftUp)   maxShiftUp   = shift;
            if (shift < maxShiftDown) maxShiftDown = shift;
        }

        int clearMinY = Math.min(min.getBlockY(), min.getBlockY() + maxShiftDown);
        int clearMaxX = Math.max(max.getBlockX(), min.getBlockX() + maxDesignWidth  - 1);
        // Cover the tallest design at every shift level: the highest reachable top block is
        // (minY + maxShiftUp) + (maxDesignHeight - 1). Using both maxima over-covers slightly,
        // which is safe (the region is repainted), and is never short of any single design.
        int clearMaxY = Math.max(max.getBlockY(), min.getBlockY() + maxShiftUp + maxDesignHeight - 1);
        int clearMaxZ = Math.max(max.getBlockZ(), min.getBlockZ() + maxDesignLength - 1);

        return new int[]{ min.getBlockX(), clearMinY, min.getBlockZ(), clearMaxX, clearMaxY, clearMaxZ };
    }

    /**
     * Re-paste the default island template at island slot {@code islandIndex}, erasing any
     * custom design the previous player had applied. Called before a player leaves or switches.
     * The operation is async-batched so it does not freeze the server.
     *
     * The clear region spans the union of the default island and every known design footprint
     * (see {@link #computeDesignUnionClearBox}) so leftover blocks from a previous (taller /
     * longer / wider) design never survive the revert.
     */
    public void revertIslandDesign(net.gravijet.fastbuilder.map.MapData map, int islandIndex) {
        if (map.getTemplateFile() == null) return;
        org.bukkit.Location min = map.getIslandMin(islandIndex);
        if (min == null || map.getWorld() == null) return;

        int[] box = computeDesignUnionClearBox(map, islandIndex);
        plugin.getFawePaster().clearRegion(
                map.getWorld(),
                box[0], box[1], box[2],
                box[3], box[4], box[5],
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
        // Check all template lists (alternative, custom-length, and infinite)
        if (!map.getAlternativeTemplates().contains(selectedDesign)
                && !map.getCustomLengthTemplates().contains(selectedDesign)
                && !map.getInfiniteTemplates().contains(selectedDesign)) return;

        org.bukkit.Location min = map.getIslandMin(islandIndex);
        if (min == null || map.getWorld() == null) return;

        // Compute the vertical correction so the design's spawn level lines up
        // with the default island's spawn level. designYShift is non-zero only
        // when the admin saved the design at a different setup Y than the map origin.
        // This anchors the spawn floor for the player while leaving the design's
        // own NPC/hologram/finish offsets (relative to its spawn floor) intact.
        net.gravijet.fastbuilder.map.MapData.DesignProfile profile =
                map.getDesignProfile(selectedDesign);
        int designYShift = computeDesignYShift(map, profile);
        int pasteY = min.getBlockY() + designYShift;
        // Clear the union of the default island and EVERY design footprint (the same box used
        // by revertIslandDesign), so a previously-applied longer/taller/wider design can never
        // leave leftover blocks behind when this design is pasted over it.
        int[] box = computeDesignUnionClearBox(map, islandIndex);
        final int finalPasteY = pasteY;
        plugin.getFawePaster().clearRegion(
                map.getWorld(),
                box[0], box[1], box[2],
                box[3], box[4], box[5],
                () -> plugin.getFawePaster().pasteTemplate(
                        map.getWorld(), selectedDesign,
                        min.getBlockX(), finalPasteY, min.getBlockZ(), null)
        );

        // Apply design profile if one has been recorded for this template
        if (profile != null) {
            // Teleport player to the design-specific spawn position (account for diagonal X offset)
            long diagX = (long) islandIndex * map.getDiagonalStepX();
            org.bukkit.Location profileSpawn = new org.bukkit.Location(
                    map.getWorld(),
                    map.getOriginX() + diagX + profile.spawnOffsetX,
                    map.getOriginY() + map.getSpawnOffsetY(),
                    map.getOriginZ() + (long) islandIndex * map.getActualZStep() + profile.spawnOffsetZ,
                    profile.spawnYaw, profile.spawnPitch
            );
            player.teleport(profileSpawn);

            // Store finish-zone override so GameplayListener uses the correct bounds.
            // Y is shifted so the design's finish-zone Y stays aligned with the design
            // blocks now pasted at the corrected height.
            if (profile.hasFinishZone()) {
                pData.setActiveFinishZone(map.getName(),
                        profile.finishMinX, profile.finishMinY + designYShift, profile.finishMinZ,
                        profile.finishMaxX, profile.finishMaxY + designYShift, profile.finishMaxZ);
            } else {
                pData.clearActiveFinishZone(map.getName());
            }

            // Respawn NPC at the design-profile NPC position if one is recorded.
            // Y uses profile.npcOffsetY + designYShift so the NPC stays at the level
            // the admin originally placed it relative to the design's blocks.
            if (profile.hasNpcPosition() && plugin.getNpcManager() != null) {
                long slotZ = map.getOriginZ() + (long) islandIndex * map.getActualZStep();
                org.bukkit.Location npcLoc = new org.bukkit.Location(
                        map.getWorld(),
                        map.getOriginX() + diagX + profile.npcOffsetX,
                        map.getOriginY() + profile.npcOffsetY + designYShift,
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
                    long diagX = (long) islandIndex * map.getDiagonalStepX();
                    return new org.bukkit.Location(
                            map.getWorld(),
                            map.getOriginX() + diagX + profile.spawnOffsetX,
                            map.getOriginY() + map.getSpawnOffsetY(),
                            map.getOriginZ() + (long) islandIndex * map.getActualZStep()
                                    + profile.spawnOffsetZ,
                            profile.spawnYaw, profile.spawnPitch
                    );
                }
            }
        }
        return map.getIslandSpawn(islandIndex);
    }

    /**
     * Returns the effective hologram location for the given player on the given island,
     * accounting for any active design profile hologram override.
     */
    public org.bukkit.Location getEffectiveHologramLocation(UUID uuid,
                                                              net.gravijet.fastbuilder.map.MapData map,
                                                              int islandIndex) {
        net.gravijet.fastbuilder.player.PlayerData pData =
                plugin.getPlayerManager().getCachedData(uuid);
        if (pData != null) {
            String design = pData.getSelectedDesign(map.getName());
            if (design != null && !design.equals(map.getTemplateFile())) {
                net.gravijet.fastbuilder.map.MapData.DesignProfile profile =
                        map.getDesignProfile(design);
                if (profile != null && profile.hasHologramPosition()) {
                    long diagX = (long) islandIndex * map.getDiagonalStepX();
                    int designYShift = computeDesignYShift(map, profile);
                    return new org.bukkit.Location(
                            map.getWorld(),
                            map.getOriginX() + diagX + profile.hologramOffsetX,
                            map.getOriginY() + profile.hologramOffsetY + designYShift,
                            map.getOriginZ() + (long) islandIndex * map.getActualZStep()
                                    + profile.hologramOffsetZ
                    );
                }
            }
        }
        return map.getIslandHologramLocation(islandIndex);
    }

    /**
     * Returns the vertical correction (in blocks) needed to make the design's
     * spawn level line up with the default island's spawn level. The default
     * island schematic is anchored at {@code map.getSpawnOffsetY()} above the
     * island origin; if the admin saved the design at a different setup Y the
     * profile carries that offset in {@code profile.spawnOffsetY}.
     *
     * Returning a non-zero shift moves the design's blocks (and its NPC /
     * hologram / finish offsets) up or down so the player still spawns on top
     * of the design's intended floor.
     *
     * @return 0 when no profile is set or already aligned; otherwise the delta
     */
    public int computeDesignYShift(net.gravijet.fastbuilder.map.MapData map,
                                    net.gravijet.fastbuilder.map.MapData.DesignProfile profile) {
        if (profile == null) return 0;
        return (int) Math.round(map.getSpawnOffsetY() - profile.spawnOffsetY);
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
        endPlatformManager.placeEndPlatform(player, map, session, customLength);
    }

    public void clearEndPlatform(UUID uuid) {
        endPlatformManager.clearEndPlatform(uuid);
    }

    public void restoreDefaultEndPlatform(MapData map, int islandIndex) {
        endPlatformManager.restoreDefaultEndPlatform(map, islandIndex);
    }

    public void clearAllEndPlatforms() {
        endPlatformManager.clearAllEndPlatforms();
    }

    public int[] getEndIslandRegion(UUID uuid) {
        return endPlatformManager.getEndIslandRegion(uuid);
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

    public boolean isAnimationEntity(UUID entityId) {
        return blockAnimator.isAnimationEntity(entityId);
    }

    public void removeAnimationEntity(UUID entityId) {
        blockAnimator.removeAnimationEntity(entityId);
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

                for (Map.Entry<UUID, RunSession> entry : new ArrayList<>(activeSessions.entrySet())) {
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
                                boolean timerStatsOk = !session.isPracticeMode()
                                        && !mapData.isInfinite() && !mapData.hasCustomLength();
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
            // Escape backslashes and double-quotes so the JSON payload is always valid
            String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
            Object packet = getNMSClass("PacketPlayOutChat")
                    .getConstructor(getNMSClass("IChatBaseComponent"), byte.class)
                    .newInstance(
                            getNMSClass("IChatBaseComponent$ChatSerializer")
                                    .getMethod("a", String.class)
                                    .invoke(null, "{\"text\":\"" + escaped + "\"}"),
                            (byte) 2
                    );
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
            playerConnection.getClass().getMethod("sendPacket", getNMSClass("Packet"))
                    .invoke(playerConnection, packet);
        } catch (Exception e) {
            plugin.getLogger().warning("sendActionBar failed for " + player.getName() + ": " + e.getMessage());
        }
    }

    private Class<?> getNMSClass(String name) throws ClassNotFoundException {
        String[] parts = Bukkit.getServer().getClass().getPackage().getName().split("\\.");
        if (parts.length < 4) throw new ClassNotFoundException("Cannot determine NMS version from package: "
                + Bukkit.getServer().getClass().getPackage().getName());
        String version = parts[3];
        return Class.forName("net.minecraft.server." + version + "." + name);
    }

    private static String formatMult(double mult) {
        if (Math.abs(mult - Math.floor(mult)) < 1e-9) return (int) mult + "x";
        return String.format("%.1fx", mult);
    }

    /** Resolves a finish.* note template (with %blocks%) from messages.yml, falling back to a default. */
    private String formatFinishNote(String key, String fallback, int blocks) {
        String tpl = plugin.getConfigManager().getFinishMessage(key);
        if (tpl == null || tpl.isEmpty()) tpl = fallback;
        return tpl.replace("%blocks%", String.valueOf(blocks));
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
        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(globalSessionBests.entrySet());
        sorted.sort((a, b) -> Long.compare(a.getValue(), b.getValue()));
        List<String[]> result = new ArrayList<>();
        for (int i = 0; i < Math.min(n, sorted.size()); i++) {
            Map.Entry<UUID, Long> entry = sorted.get(i);
            Player onlinePlayer = Bukkit.getPlayer(entry.getKey());
            String name = onlinePlayer != null ? onlinePlayer.getName() : entry.getKey().toString();
            result.add(new String[]{name, String.valueOf(entry.getValue())});
        }
        return result;
    }

    /** Convenience overload — returns top 3 (backward compat). */
    public List<String[]> getGlobalSessionTop3() {
        return getGlobalSessionTop(3);
    }

    /**
     * Returns the top N unique-player infinite session bests as [playerName, distance, timeMs] triples,
     * sorted by distance descending, then time ascending on tie.
     */
    public List<String[]> getInfiniteSessionTop(int n) {
        List<Map.Entry<UUID, long[]>> sorted = new ArrayList<>(infiniteSessionBests.entrySet());
        sorted.sort((a, b) -> {
            int cmp = Long.compare(b.getValue()[0], a.getValue()[0]); // distance desc
            if (cmp != 0) return cmp;
            return Long.compare(a.getValue()[1], b.getValue()[1]); // time asc
        });
        List<String[]> result = new ArrayList<>();
        for (int i = 0; i < Math.min(n, sorted.size()); i++) {
            Map.Entry<UUID, long[]> entry = sorted.get(i);
            Player onlinePlayer = Bukkit.getPlayer(entry.getKey());
            String name = onlinePlayer != null ? onlinePlayer.getName() : entry.getKey().toString();
            result.add(new String[]{name,
                    String.valueOf(entry.getValue()[0]),
                    String.valueOf(entry.getValue()[1])});
        }
        return result;
    }

    public void removeGlobalSessionBest(UUID playerUuid) {
        globalSessionBests.remove(playerUuid);
        infiniteSessionBests.remove(playerUuid);
        // Rebuild the legacy scalar best if this player held it
        Player wasPlayer = Bukkit.getPlayer(playerUuid);
        String removedName = wasPlayer != null ? wasPlayer.getName() : null;
        if (removedName != null && removedName.equals(globalSessionBestPlayer)) {
            globalSessionBestTime = -1;
            globalSessionBestPlayer = null;
            for (Map.Entry<UUID, Long> e : globalSessionBests.entrySet()) {
                if (globalSessionBestTime < 0 || e.getValue() < globalSessionBestTime) {
                    globalSessionBestTime = e.getValue();
                    Player p = Bukkit.getPlayer(e.getKey());
                    globalSessionBestPlayer = p != null ? p.getName() : e.getKey().toString();
                }
            }
        }
    }

    /** Legacy name-based overload — kept for callers that only have a player name. */
    public void removeGlobalSessionBest(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            removeGlobalSessionBest(online.getUniqueId());
        }
    }

    public void shutdown() {
        if (actionbarTaskId != -1) Bukkit.getScheduler().cancelTask(actionbarTaskId);
        if (deathCheckTaskId != -1) Bukkit.getScheduler().cancelTask(deathCheckTaskId);
        clearAllEndPlatforms();
        activeSessions.clear();
        finishCooldown.clear();
        buildModePlayers.clear();
        globalSessionBests.clear();
        infiniteSessionBests.clear();
        lastFinishTimes.clear();
        lastFinishBlocks.clear();
    }

    public Map<UUID, RunSession> getActiveSessions() { return activeSessions; }
}
