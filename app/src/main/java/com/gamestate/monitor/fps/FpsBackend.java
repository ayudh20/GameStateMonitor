package com.gamestate.monitor.fps;

import android.content.Context;

/**
 * FpsBackend
 * ----------
 * Pluggable abstraction for FPS and frame pacing acquisition engines.
 * Implementations can bind to ADB-granted SurfaceFlinger streams, Root shell sessions,
 * or future Android diagnostic APIs.
 */
public interface FpsBackend {

    /**
     * @return The specific type categorization of this backend.
     */
    FpsBackendType getType();

    /**
     * @return Human-readable name for UI and logging (e.g. "SurfaceFlinger (ADB)").
     */
    String getName();

    /**
     * Tests whether the prerequisites for this backend are fully met on the device.
     *
     * @param context Application context.
     * @return true if operational, false if permissions or dependencies are missing.
     */
    boolean isAvailable(Context context);

    /**
     * Detailed status explanation of availability or what is missing.
     *
     * @param context Application context.
     * @return Diagnostic availability status.
     */
    AvailabilityStatus getAvailabilityStatus(Context context);

    /**
     * Starts continuous frame pacing sampling for the specified foreground package.
     *
     * @param targetPackage The package name of the active game (e.g. com.epicgames.portal).
     * @param callback Callback to receive live metrics updates.
     */
    void startMonitoring(String targetPackage, FpsDataCallback callback);

    /**
     * Halts frame pacing sampling and releases resources.
     */
    void stopMonitoring();

    /**
     * @return true if currently actively monitoring a game process.
     */
    boolean isMonitoring();

    /**
     * @return The latest captured metrics snapshot, or FpsMetrics.empty() if inactive.
     */
    FpsMetrics getLatestMetrics();
}
