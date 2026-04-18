package net.gravijet.fastbuilder.util;

/**
 * Time formatting utilities.
 *
 * Display convention (matches McPlayHD style):
 *   - Times use a <strong>comma</strong> as the decimal separator: {@code 3,750}
 *   - Unset / empty slots display as {@code §8-,---} (dark gray)
 *   - Times are ALWAYS in seconds — never minutes or hours: {@code 145,250}
 *   - Difference format: {@code +1,234s} / {@code -0,500s}
 */
public final class TimeUtil {

    /** Displayed instead of a time value when no data is available (dark gray). */
    public static final String EMPTY = "\u00A78-,---";

    /** Raw empty value without color code — for contexts that apply their own color. */
    public static final String EMPTY_RAW = "-,---";

    private TimeUtil() {}

    // -------------------------------------------------------------------------
    // Primary formatter  (always seconds,milliseconds with comma decimal)
    // -------------------------------------------------------------------------

    /**
     * Format milliseconds to {@code SS,mmm} display. Always seconds, never minutes.
     *
     * <ul>
     *   <li>{@code 3750} → {@code 3,750}</li>
     *   <li>{@code 63500} → {@code 63,500}</li>
     *   <li>{@code 145250} → {@code 145,250}</li>
     *   <li>Negative → {@value #EMPTY}</li>
     * </ul>
     */
    public static String formatTime(long millis) {
        if (millis < 0) return EMPTY;

        long floored      = (millis / 10) * 10; // floor to nearest 10 ms — no trailing 9s
        long totalSeconds = floored / 1000;
        long ms           = floored % 1000;

        return totalSeconds + "," + String.format("%03d", ms);
    }

    // -------------------------------------------------------------------------
    // Full format — same as primary but returns raw (no color on empty)
    // -------------------------------------------------------------------------

    /**
     * Format milliseconds to {@code SS,mmm} display without color codes on empty.
     *
     * <ul>
     *   <li>{@code 3750} → {@code 3,750}</li>
     *   <li>{@code 63500} → {@code 63,500}</li>
     * </ul>
     */
    public static String formatTimeFull(long millis) {
        if (millis < 0) return "0,000";
        long floored      = (millis / 10) * 10;
        long totalSeconds = floored / 1000;
        long ms           = floored % 1000;
        return totalSeconds + "," + String.format("%03d", ms);
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
        long diff    = currentMillis - bestMillis;
        String sign  = diff >= 0 ? "+" : "-";
        long   abs   = (Math.abs(diff) / 10) * 10; // floor to nearest 10 ms
        long   sec   = abs / 1000;
        long   ms    = abs % 1000;
        return sign + sec + "," + String.format("%03d", ms) + "s";
    }

    // -------------------------------------------------------------------------
    // Compact formatter (alias for primary)
    // -------------------------------------------------------------------------

    /**
     * Short display — alias for {@link #formatTime(long)}.
     */
    public static String formatTimeShort(long millis) {
        return formatTime(millis);
    }
}
