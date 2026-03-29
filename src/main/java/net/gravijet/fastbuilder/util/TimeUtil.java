package net.gravijet.fastbuilder.util;

public final class TimeUtil {

    private TimeUtil() {}

    /**
     * Format milliseconds to MM:SS.mmm display.
     */
    public static String formatTime(long millis) {
        if (millis < 0) return "00:00.000";
        long minutes = millis / 60000;
        long seconds = (millis % 60000) / 1000;
        long ms = millis % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, ms);
    }

    /**
     * Format a time difference as "+X.XXXs" or "-X.XXXs".
     */
    public static String formatDifference(long currentMillis, long bestMillis) {
        long diff = currentMillis - bestMillis;
        String sign = diff >= 0 ? "+" : "-";
        long abs = Math.abs(diff);
        long sec = abs / 1000;
        long ms = abs % 1000;
        return sign + sec + "." + String.format("%03d", ms) + "s";
    }

    /**
     * Format milliseconds to a short display: SS.mmm or M:SS.mmm.
     */
    public static String formatTimeShort(long millis) {
        if (millis < 0) return "0.000";
        long totalSeconds = millis / 1000;
        long ms = millis % 1000;
        if (totalSeconds < 60) {
            return totalSeconds + "." + String.format("%03d", ms);
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + ":" + String.format("%02d", seconds) + "." + String.format("%03d", ms);
    }
}
