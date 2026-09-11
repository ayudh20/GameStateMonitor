package com.gamestate.monitor.fps;

/**
 * FpsDataCallback
 * ---------------
 * Callback interface for receiving real-time FPS and frame pacing metrics updates
 * from an active FpsBackend.
 */
public interface FpsDataCallback {
    /**
     * Dispatched when new frame metrics are sampled from the backend.
     *
     * @param metrics Newly measured frame pacing data.
     */
    void onMetricsUpdated(FpsMetrics metrics);

    /**
     * Dispatched if the backend encounters an error or loses connection to the target process.
     *
     * @param error Description of the error.
     */
    void onError(String error);
}
