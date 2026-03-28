package net.gravijet.fastbuilder.model;

import java.util.EnumMap;
import java.util.Map;

/**
 * Persistent statistics for a player, saved/loaded via StatsManager.
 * Tracks per-distance and global stats.
 */
public class PlayerStats {

    private final Map<BridgeDistance, DistanceStats> distanceStats = new EnumMap<>(BridgeDistance.class);
    private int totalAttempts  = 0;
    private int totalSuccesses = 0;

    public PlayerStats() {
        for (BridgeDistance d : BridgeDistance.values()) {
            distanceStats.put(d, new DistanceStats());
        }
    }

    public void recordAttempt(BridgeDistance distance) {
        totalAttempts++;
        distanceStats.get(distance).attempts++;
    }

    public void recordSuccess(BridgeDistance distance, long timeMillis) {
        totalSuccesses++;
        DistanceStats s = distanceStats.get(distance);
        s.successes++;
        if (timeMillis < s.bestTimeMillis) {
            s.bestTimeMillis = timeMillis;
        }
    }

    public DistanceStats getStats(BridgeDistance distance) {
        return distanceStats.get(distance);
    }

    public int getTotalAttempts()  { return totalAttempts;  }
    public int getTotalSuccesses() { return totalSuccesses; }

    public void setTotalAttempts(int v)  { this.totalAttempts  = v; }
    public void setTotalSuccesses(int v) { this.totalSuccesses = v; }

    public Map<BridgeDistance, DistanceStats> getAllDistanceStats() {
        return distanceStats;
    }

    // ── Inner class ──────────────────────────────────────────────

    public static class DistanceStats {
        public int  attempts     = 0;
        public int  successes    = 0;
        public long bestTimeMillis = Long.MAX_VALUE;

        public boolean hasBestTime() { return bestTimeMillis != Long.MAX_VALUE; }

        public String getFormattedBestTime() {
            if (!hasBestTime()) return "--";
            return String.format("%.2fs", bestTimeMillis / 1000.0);
        }

        public String getSuccessRate() {
            if (attempts == 0) return "0%";
            return String.format("%.0f%%", (successes * 100.0) / attempts);
        }
    }
}
