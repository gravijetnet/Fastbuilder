package net.gravijet.fastbuilder.util;

/**
 * Time formatting utilities.
 *
 * Display convention (matches McPlayHD style):
 *   - Times use a <strong>comma</strong> as the decimal separator: {@code 3,750}
 *   - Unset / empty slots display as {@code -,---}
 *   - Full format includes minutes when ≥ 60 s: {@code 1:03,750}
 *   - Difference format: {@code +1,234s} / {@code -0,500s}
 */
public final class TimeUtil {

    /** Displayed instead of a time value when no data is available. */
    public static final String EMPTY = "-,---";

    private TimeUtil() {}

    // -------------------------------------------------------------------------
    // Primary formatter  (seconds,milliseconds with comma decimal)
    // -------------------------------------------------------------------------

    /**
     * Format milliseconds to {@code SS,mmm} or {@code M:SS,mmm} display.
     *
     * <ul>
     *   <li>{@code 3750} → {@code 3,750}</li>
     *   <li>{@code 63500} → {@code 1:03,500}</li>
     *   <li>Negative → {@value #EMPTY}</li>
     * </ul>
     */
    public static String formatTime(long millis) {
        if (millis < 0) return EMPTY;

        long totalSeconds = millis / 1000;
        long ms           = millis % 1000;

        if (totalSeconds < 60) {
            return totalSeconds + "," + String.format("%03d", ms);
        }

        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + ":" + String.format("%02d", seconds) + "," + String.format("%03d", ms);
    }

    // -------------------------------------------------------------------------
    // Full zero-padded format (MM:SS,mmm) — used in scoreboards / holograms
    // -------------------------------------------------------------------------

    /**
     * Format milliseconds to the full {@code MM:SS,mmm} format with zero-padded
     * minutes.  Always shows the minute component, even for short times.
     *
     * <ul>
     *   <li>{@code 3750} → {@code 00:03,750}</li>
     *   <li>{@code 63500} → {@code 01:03,500}</li>
     * </ul>
     */
    public static String formatTimeFull(long millis) {
        if (millis < 0) return "00:00,000";
        long minutes = millis / 60_000;
        long seconds = (millis % 60_000) / 1000;
        long ms      = millis % 1000;
        return String.format("%02d:%02d,%03d", minutes, seconds, ms);
    }

    // -------------------------------------------------------------------------
    // Difference formatter
    // -------------------------------------------------------------------------

    /**
     * Format the signed difference between two times.
     *
     * <ul>
     *   <li>Slower than PB: {@code +1,234s} (red)</li>
     *   <li>Faster than PB: {@code -0,500s} (green)</li>
     * </ul>
     */
    public static String formatDifference(long currentMillis, long bestMillis) {
        long diff = currentMillis - bestMillis;
        String sign = diff >= 0 ? "+" : "-";
        long   abs  = Math.abs(diff);
        long   sec  = abs / 1000;
        long   ms   = abs % 1000;
        return sign + sec + "," + String.format("%03d", ms) + "s";
    }

    // -------------------------------------------------------------------------
    // Compact formatter (no leading zeroes, no minutes unless needed)
    // -------------------------------------------------------------------------

    /**
     * Short display without leading zeroes — for chat messages where brevity matters.
     *
     * <ul>
     *   <li>{@code 3750} → {@code 3,750}</li>
     *   <li>{@code 63500} → {@code 1:03,500}</li>
     * </ul>
     */
    public static String formatTimeShort(long millis) {
        return formatTime(millis); // alias — same behaviour
    }
}
