package com.gamestate.monitor.fps;

import android.content.Context;

/**
 * FallbackFpsBackend
 * ------------------
 * Safe fallback backend active when elevated permissions (ADB DUMP or Root)
 * have not yet been granted to the application.
 *
 * Adheres strictly to the zero-fake-metrics rule:
 * Returns no synthetic, random, or estimated values, safely indicating
 * that the system is waiting for a supported FPS backend.
 */
public class FallbackFpsBackend implements FpsBackend {

    private final FpsMetrics emptyMetrics;

    public FallbackFpsBackend(float refreshRate) {
        this.emptyMetrics = FpsMetrics.empty(refreshRate);
    }

    @Override
    public FpsBackendType getType() {
        return FpsBackendType.FALLBACK;
    }

    @Override
    public String getName() {
        return FpsBackendType.FALLBACK.getDisplayName();
    }

    @Override
    public boolean isAvailable(Context context) {
        return false;
    }

    @Override
    public AvailabilityStatus getAvailabilityStatus(Context context) {
        return AvailabilityStatus.UNSUPPORTED;
    }

    @Override
    public void startMonitoring(String targetPackage, FpsDataCallback callback) {
        if (callback != null) {
            callback.onError("Waiting for supported FPS backend");
        }
    }

    @Override
    public void stopMonitoring() {
        // No-op
    }

    @Override
    public boolean isMonitoring() {
        return false;
    }

    @Override
    public FpsMetrics getLatestMetrics() {
        return emptyMetrics;
    }
}
