package com.gamestate.monitor.fps;

import android.content.Context;

/**
 * RootFpsBackend
 * --------------
 * Disabled in favor of Shizuku.
 */
public class RootFpsBackend implements FpsBackend {

    public RootFpsBackend(Context context) {
    }

    @Override
    public FpsBackendType getType() {
        return FpsBackendType.ROOT;
    }

    @Override
    public String getName() {
        return FpsBackendType.ROOT.getDisplayName();
    }

    @Override
    public boolean isAvailable(Context context) {
        return false;
    }

    @Override
    public AvailabilityStatus getAvailabilityStatus(Context context) {
        return AvailabilityStatus.REQUIRES_ROOT;
    }

    @Override
    public void startMonitoring(String targetPackage, FpsDataCallback callback) {
    }

    @Override
    public void stopMonitoring() {
    }

    @Override
    public boolean isMonitoring() {
        return false;
    }

    @Override
    public FpsMetrics getLatestMetrics() {
        return FpsMetrics.empty(60.0f);
    }
}
