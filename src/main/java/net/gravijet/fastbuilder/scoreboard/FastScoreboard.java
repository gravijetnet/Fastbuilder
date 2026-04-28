package net.gravijet.fastbuilder.scoreboard;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.RunSession;
import net.gravijet.fastbuilder.player.PlayerData;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player sidebar scoreboard — team-based implementation.
 *
 * Lines are updated in-place using Bukkit {@link Team} prefix/suffix pairs.
 * Only lines whose content changed since the last tick are touched, which
 * eliminates the full-scoreboard-recreate flicker caused by the previous
 * approach.
 *
 * <h2>Line length budget</h2>
 * Each visible scoreboard line is composed of:
 * <pre>
 *   [team prefix] [invisible entry] [team suffix]
 *    ≤ 16 chars    2 colour codes     ≤ 16 chars
 * </pre>
 * Total visible: up to 32 characters per line.  Lines longer than 32 chars
 * (after colour codes are counted) will be truncated — keep config lines short.
 *
 * <h2>Colour carry-over</h2>
 * When a line is split across prefix + suffix, the last colour/formatting code
 * present in the prefix is prepended to the suffix so colour does not bleed.
 */
public class FastScoreboard {

    private static final int MAX_LINES  = 15;
    /** Unique invisible entries — one per scoreboard slot (colour code pairs). */
    private static final String[] ENTRIES;

    static {
        ENTRIES = new String[MAX_LINES];
        ChatColor[] colours = ChatColor.values();
        for (int i = 0; i < MAX_LINES; i++) {
            // Two colour codes → unique, invisible, non-empty string
            ENTRIES[i] = "" + colours[i % colours.length] + colours[(i + 1) % colours.length];
        }
    }

    private final FastBuilder plugin;
    private int updateTaskId     = -1;
    private int tickTaskId       = -1;
    private final boolean papiAvailable;

    /** Per-player: the scoreboard object. */
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    /** Per-player: last rendered line text (for dirty-check). */
    private final Map<UUID, String[]>   lastLines = new HashMap<>();

    public FastScoreboard(FastBuilder plugin) {
        this.plugin        = plugin;
        this.papiAvailable = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        startUpdateTask();
    }

    // -------------------------------------------------------------------------
    // Create / Remove
    // -------------------------------------------------------------------------

    /**
     * Create a fresh scoreboard for {@code player} and store it.
     * Call when a player joins an island.
     */
    public void createScoreboard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj    = board.registerNewObjective("fb", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(ColorUtil.translate(plugin.getConfigManager().getScoreboardTitle()));

        List<String> configLines = plugin.getConfigManager().getScoreboardLines();
        int lineCount = Math.min(configLines.size(), MAX_LINES);

        // Create teams for each slot and assign entries
        for (int i = 0; i < lineCount; i++) {
            String teamName = "fb_" + player.getName().hashCode() + "_" + i;
            Team team = board.getTeam(teamName);
            if (team == null) team = board.registerNewTeam(teamName);

            team.addEntry(ENTRIES[i]);
            obj.getScore(ENTRIES[i]).setScore(lineCount - i); // top line = highest score
        }

        boards.put(player.getUniqueId(), board);
        lastLines.put(player.getUniqueId(), new String[lineCount]);
        player.setScoreboard(board);

        // Force first full render
        renderLines(player, board, buildLines(player, configLines));
    }

    /** Remove the custom scoreboard from a player (e.g. on leave). */
    public void removeScoreboard(Player player) {
        boards.remove(player.getUniqueId());
        lastLines.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    // -------------------------------------------------------------------------
    // Update (called by task)
    // -------------------------------------------------------------------------

    /** Force-refresh the scoreboard for a single player (no dirty-check). */
    public void updateScoreboard(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;

        List<String> configLines = plugin.getConfigManager().getScoreboardLines();
        List<String> lines       = buildLines(player, configLines);
        renderLines(player, board, lines);
    }

    // -------------------------------------------------------------------------
    // Core rendering
    // -------------------------------------------------------------------------

    /**
     * Render {@code lines} into {@code board}'s teams.
     * Only lines that changed since the last render are updated.
     */
    private void renderLines(Player player, Scoreboard board, List<String> lines) {
        String[] prev = lastLines.get(player.getUniqueId());
        if (prev == null) return;

        // Find the objective (created in createScoreboard)
        Objective obj = board.getObjective("fb");
        if (obj == null) return;

        int lineCount = Math.min(lines.size(), MAX_LINES);

        // Synchronise team count if config changed
        for (int i = lineCount; i < prev.length; i++) {
            // Hide extra lines by setting an empty prefix/suffix
            Team team = getTeam(board, player, i);
            if (team != null) { team.setPrefix(""); team.setSuffix(""); }
        }

        String[] next = new String[lineCount];
        for (int i = 0; i < lineCount; i++) {
            String rendered = lines.get(i);
            next[i] = rendered;

            if (rendered.equals(prev.length > i ? prev[i] : null)) continue; // no change

            // Apply to team
            Team team = getTeam(board, player, i);
            if (team == null) continue;

            setTeamLine(team, rendered);
        }
        lastLines.put(player.getUniqueId(), next);
    }

    /** Retrieve the team for line slot {@code index} from the board. */
    private Team getTeam(Scoreboard board, Player player, int index) {
        String teamName = "fb_" + player.getName().hashCode() + "_" + index;
        return board.getTeam(teamName);
    }

    /**
     * Split a line across team prefix + suffix (32-char budget).
     * Colour carry-over is preserved via {@link ChatColor#getLastColors(String)}.
     * The split point is adjusted backwards if it would land inside a § colour sequence
     * (e.g. splitting "§6" between § and 6 would produce a visible "6c"-style artefact).
     */
    @SuppressWarnings("deprecation")
    private void setTeamLine(Team team, String line) {
        if (line.length() <= 16) {
            team.setPrefix(line);
            team.setSuffix("");
        } else {
            // Avoid splitting mid-colour-sequence: if char at position 15 is §, back up by 1
            int splitAt = 16;
            if (splitAt > 0 && line.charAt(splitAt - 1) == '\u00a7') {
                splitAt = 15;
            }
            String prefix = line.substring(0, splitAt);
            String rest   = line.substring(splitAt);
            // Carry last colour from prefix into suffix so rendering is seamless
            String carry  = ChatColor.getLastColors(prefix);
            String suffix;
            if (carry.isEmpty()) {
                suffix = rest.length() <= 16 ? rest : rest.substring(0, 16);
            } else {
                String candidate = carry + rest;
                suffix = candidate.length() <= 16 ? candidate : candidate.substring(0, 16);
            }
            team.setPrefix(prefix);
            team.setSuffix(suffix);
        }
    }

    // -------------------------------------------------------------------------
    // Line builder
    // -------------------------------------------------------------------------

    /**
     * Build the list of display strings (top-to-bottom) for {@code player}.
     * All placeholders are resolved here.
     */
    private List<String> buildLines(Player player, List<String> configLines) {
        if (configLines.isEmpty()) return Collections.emptyList();

        PlayerData data    = plugin.getPlayerManager().getCachedData(player.getUniqueId());
        RunSession session  = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;

        // --- Resolve common placeholders once ---
        // IMPORTANT: Do NOT add hardcoded colour codes to placeholder values.
        // The config lines supply their own colour prefix (e.g. "&c%pb%"), so
        // injecting §f here would override whatever colour the admin chose.
        String pb;
        if (data != null && session != null) {
            PlayerData.MapStats stats = data.getStats(session.getMapName());
            pb = (stats != null && stats.hasBestTime())
                    ? TimeUtil.formatTime(stats.bestTime)
                    : TimeUtil.EMPTY;
        } else {
            pb = TimeUtil.EMPTY;
        }

        String currentTime;
        if (session != null && session.isRunning()) {
            currentTime = TimeUtil.formatTime(session.getElapsed());
        } else {
            long lastTime = (plugin.getGameplayManager() != null)
                    ? plugin.getGameplayManager().getLastFinishTime(player.getUniqueId()) : -1L;
            currentTime = lastTime > 0 ? TimeUtil.formatTime(lastTime) : "0,000";
        }

        String coins  = data != null ? String.valueOf(data.getCoins()) : "0";

        String blocks;
        if (session != null && session.isRunning()) {
            blocks = String.valueOf(session.getPlacedBlocks().size());
        } else {
            int lastBlocks = (plugin.getGameplayManager() != null)
                    ? plugin.getGameplayManager().getLastFinishBlocks(player.getUniqueId()) : -1;
            blocks = lastBlocks >= 0 ? String.valueOf(lastBlocks) : "0";
        }

        // Session top-10
        List<String[]> topList = (plugin.getGameplayManager() != null)
                ? plugin.getGameplayManager().getGlobalSessionTop(10)
                : Collections.<String[]>emptyList();

        // --- Build all lines ---
        List<String> result = new ArrayList<>(configLines.size());
        for (String raw : configLines) {
            String line = raw
                    .replace("%pb%",           pb)
                    .replace("%current_time%", currentTime)
                    .replace("%coins%",        coins)
                    .replace("%blocks%",       blocks);

            // top_name_N / top_time_N (N = 1..10)
            for (int n = 1; n <= 10; n++) {
                String namePh = "%top_name_" + n + "%";
                String timePh = "%top_time_" + n + "%";
                if (topList.size() >= n) {
                    String[] entry = topList.get(n - 1);
                    // Return raw values — let the config control colours
                    line = line.replace(namePh, entry[0]);
                    line = line.replace(timePh, TimeUtil.formatTime(Long.parseLong(entry[1])));
                } else {
                    // Empty entry: if this line references either placeholder, replace the
                    // ENTIRE line with a single "-,---" so no colon ever appears.
                    if (line.contains(namePh) || line.contains(timePh)) {
                        line = "§8-,---" + invisPad(n);
                        break; // done processing placeholders for this line
                    }
                }
            }

            // PlaceholderAPI (applied before colour translation so PAPI values can contain &codes)
            line = applyPapi(player, line);
            line = ColorUtil.translate(line);

            result.add(line);
        }
        return result;
    }

    /** Return a zero-width padding string unique per {@code n} (prevents duplicate entries). */
    private String invisPad(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }

    private String applyPapi(Player player, String text) {
        if (!papiAvailable) return text;
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Exception ignored) {}
        return text;
    }

    // -------------------------------------------------------------------------
    // Task management
    // -------------------------------------------------------------------------

    private void startUpdateTask() {
        long interval = plugin.getConfigManager().getScoreboardUpdateInterval();
        // Standard placeholders (coins, PB, top-N, PAPI) — update at configured interval (default 1 s)
        updateTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getGameplayManager() == null) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (boards.containsKey(player.getUniqueId())) {
                        updateScoreboard(player);
                    }
                }
            }
        }.runTaskTimer(plugin, interval, interval).getTaskId();

        // Timer (%current_time%) and block counter (%blocks%) — update every tick
        tickTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getGameplayManager() == null) return;
                List<String> configLines = plugin.getConfigManager().getScoreboardLines();
                // Only run if at least one line uses a fast-changing placeholder
                boolean hasTimerLine = false;
                for (String l : configLines) {
                    if (l.contains("%current_time%") || l.contains("%blocks%")) {
                        hasTimerLine = true;
                        break;
                    }
                }
                if (!hasTimerLine) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!boards.containsKey(player.getUniqueId())) continue;
                    RunSession session = plugin.getGameplayManager().getSession(player.getUniqueId());
                    if (session == null || !session.isRunning()) continue;
                    updateTickLines(player, configLines, session);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L).getTaskId();
    }

    /**
     * Update only the scoreboard lines that contain {@code %current_time%} or {@code %blocks%}.
     * Called every tick so the timer and block counter stay accurate.
     */
    private void updateTickLines(Player player, List<String> configLines, RunSession session) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;

        String currentTime = TimeUtil.formatTime(session.getElapsed());
        String blocks      = String.valueOf(session.getPlacedBlocks().size());

        String[] prev = lastLines.get(player.getUniqueId());
        if (prev == null) return;

        int lineCount = Math.min(configLines.size(), MAX_LINES);
        for (int i = 0; i < lineCount && i < prev.length; i++) {
            String raw = configLines.get(i);
            if (!raw.contains("%current_time%") && !raw.contains("%blocks%")) continue;

            String rendered = raw
                    .replace("%current_time%", currentTime)
                    .replace("%blocks%",       blocks);
            // Resolve other placeholders already handled by the 1-s task via cached prev values.
            // For tick lines we only refresh the time/blocks portion; other %ph% tokens stay
            // frozen between slow updates (acceptable for a fast-moving timer display).
            rendered = ColorUtil.translate(rendered);

            if (rendered.equals(prev[i])) continue;
            prev[i] = rendered;

            Team team = getTeam(board, player, i);
            if (team != null) setTeamLine(team, rendered);
        }
    }

    /** Called on {@code /fb reload} — restarts the update task with a potentially new interval. */
    public void reload() {
        shutdown();
        startUpdateTask();
    }

    public void shutdown() {
        if (updateTaskId != -1) {
            Bukkit.getScheduler().cancelTask(updateTaskId);
            updateTaskId = -1;
        }
        if (tickTaskId != -1) {
            Bukkit.getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }
    }
}
