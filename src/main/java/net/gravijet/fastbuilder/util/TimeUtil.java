package net.gravijet.fastbuilder.util;

/**
 * Time formatting utilities.
 *
 * Display convention (matches McPlayHD style):
 *   - Times use a <strong>comma</strong> as the decimal separator: {@code 3,750}
 *   - Unset / empty slots display as {@code §8-,---} (dark gray)
 *   - Times are ALWAYS in seconds — never minutes or hours: {@code 145,250}
 *   - Millisecond part is always a multiple of 50 (tens digit is 0 or 5, ones digit always 0)
 *   - Difference format: {@code +1,250s} / {@code -0,500s}
 */
public final class TimeUtil {

    /** Displayed instead of a time value when no data is available (dark gray). */
    public static final String EMPTY = "§8-,---";

    /** Raw empty value without color code — for contexts that apply their own color. */
    public static final String EMPTY_RAW = "-,---";

    private TimeUtil() {}

    /**
     * Round millis to the nearest 50 ms boundary.
     * Guarantees the ms component is always a multiple of 50
     * (tens digit is 0 or 5, ones digit is always 0).
     * Examples: 3499 → 3500, 3450 → 3450, 3474 → 3450, 3476 → 3500.
     */
    public static long roundTo50(long millis) {
        return ((millis + 25) / 50) * 50;
    }

    // -------------------------------------------------------------------------
    // Primary formatter  (always seconds,milliseconds with comma decimal)
    // -------------------------------------------------------------------------

    /**
     * Format milliseconds to {@code SS,mmm} display. Always seconds, never minutes.
     * The millisecond part is rounded to the nearest 50 ms so the last digit is
     * always 0 and the tens digit is always 0 or 5.
     *
     * <ul>
     *   <li>{@code 3750} → {@code 3,750}</li>
     *   <li>{@code 3499} → {@code 3,500}</li>
     *   <li>{@code 63500} → {@code 63,500}</li>
     *   <li>{@code 145250} → {@code 145,250}</li>
     *   <li>Negative → {@value #EMPTY}</li>
     * </ul>
     */
    public static String formatTime(long millis) {
        if (millis < 0) return EMPTY;

        long rounded      = roundTo50(millis);
        long totalSeconds = rounded / 1000;
        long ms           = rounded % 1000;

        return totalSeconds + "," + String.format("%03d", ms);
    }

    // -------------------------------------------------------------------------
    // Full format — same as primary but returns raw (no color on empty)
    // -------------------------------------------------------------------------

    /**
     * Format milliseconds to {@code SS,mmm} display without color codes on empty.
     * The millisecond part is rounded to the nearest 50 ms.
     *
     * <ul>
     *   <li>{@code 3750} → {@code 3,750}</li>
     *   <li>{@code 63500} → {@code 63,500}</li>
     * </ul>
     */
    public static String formatTimeFull(long millis) {
        if (millis < 0) return EMPTY_RAW;
        long rounded      = roundTo50(millis);
        long totalSeconds = rounded / 1000;
        long ms           = rounded % 1000;
        return totalSeconds + "," + String.format("%03d", ms);
    }

    // -------------------------------------------------------------------------
    // Difference formatter
    // -------------------------------------------------------------------------

    /**
     * Format the signed difference between two times.
     * The absolute difference is rounded to the nearest 50 ms.
     *
     * <ul>
     *   <li>Slower than PB: {@code +1,250s}</li>
     *   <li>Faster than PB: {@code -0,500s}</li>
     * </ul>
     */
    public static String formatDifference(long currentMillis, long bestMillis) {
        long diff    = currentMillis - bestMillis;
        String sign  = diff >= 0 ? "+" : "-";
        long   abs   = roundTo50(Math.abs(diff));
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
