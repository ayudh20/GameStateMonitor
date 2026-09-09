package com.gamestate.monitor.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * FormatUtils Helper Class
 * ------------------------
 * A Utility class in Java contains static methods that perform common
 * transformations and formatting tasks. Static methods can be called directly
 * without creating an instance of the class (e.g., FormatUtils.formatBytes(...)).
 */
public final class FormatUtils {

    // Private constructor prevents instantiation of this utility class
    private FormatUtils() {}

    /**
     * Converts a raw number of bytes into a human-readable string (MB or GB).
     *
     * Example: 4294967296 bytes -> "4.0 GB"
     *
     * @param bytes Number of bytes
     * @return Formatted string representation
     */
    public static String formatBytes(long bytes) {
        if (bytes < 0) return "0 MB";

        double kilobyte = 1024.0;
        double megabyte = kilobyte * 1024.0;
        double gigabyte = megabyte * 1024.0;

        if (bytes >= gigabyte) {
            return String.format(Locale.getDefault(), "%.1f GB", bytes / gigabyte);
        } else if (bytes >= megabyte) {
            return String.format(Locale.getDefault(), "%.1f MB", bytes / megabyte);
        } else {
            return String.format(Locale.getDefault(), "%.1f KB", bytes / kilobyte);
        }
    }

    /**
     * Formats Celsius and Fahrenheit temperatures into a combined string.
     * Example: 34.5f -> "34.5 °C (94.1 °F)"
     */
    public static String formatTemperature(float celsius) {
        float fahrenheit = (celsius * 9.0f / 5.0f) + 32.0f;
        return String.format(Locale.getDefault(), "%.1f °C  /  %.1f °F", celsius, fahrenheit);
    }

    /**
     * Formats refresh rate in Hz.
     * Example: 119.999f -> "120 Hz"
     */
    public static String formatRefreshRate(float rate) {
        int roundedRate = Math.round(rate);
        return roundedRate + " Hz";
    }

    /**
     * Returns the current date and time formatted for dashboard display.
     * Example: "Sep 09, 2026 - 14:32:05"
     */
    public static String getCurrentDateTimeFormatted() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }
}
