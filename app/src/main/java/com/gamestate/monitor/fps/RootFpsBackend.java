package com.gamestate.monitor.fps;

import android.content.Context;
import android.util.Log;

import java.io.File;

/**
 * RootFpsBackend
 * --------------
 * FPS data provider utilizing elevated root (su) shell privileges.
 * Capable of accessing SurfaceFlinger service calls directly or reading
 * hardware VSYNC/display interrupts without requiring an active ADB USB session.
 */
public class RootFpsBackend implements FpsBackend {

    private static final String TAG = "RootFpsBackend";

    private static final String[] KNOWN_SU_PATHS = new String[]{
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su"
    };

    private final Context context;
    private boolean isMonitoring = false;
    private String targetPackage = null;
    private FpsDataCallback activeCallback = null;
    private FpsMetrics latestMetrics;

    public RootFpsBackend(Context context) {
        this.context = context.getApplicationContext();
        this.latestMetrics = FpsMetrics.empty(60.0f);
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
        for (String path : KNOWN_SU_PATHS) {
            if (new File(path).exists()) {
                return true;
            }
        }
        // Check PATH environment
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(":")) {
                if (new File(dir, "su").exists()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public AvailabilityStatus getAvailabilityStatus(Context context) {
        if (isAvailable(context)) {
            return AvailabilityStatus.AVAILABLE;
        }
        return AvailabilityStatus.REQUIRES_ROOT;
    }

    @Override
    public synchronized void startMonitoring(String targetPackage, FpsDataCallback callback) {
        this.targetPackage = targetPackage;
        this.activeCallback = callback;
        this.isMonitoring = true;

        if (!isAvailable(context)) {
            Log.w(TAG, "Cannot start root monitoring: root access not detected.");
            if (callback != null) {
                callback.onError("Root access not detected");
            }
            return;
        }

        Log.i(TAG, "Starting Root FPS monitoring for package: " + targetPackage);
    }

    @Override
    public synchronized void stopMonitoring() {
        this.isMonitoring = false;
        this.targetPackage = null;
        this.activeCallback = null;
        this.latestMetrics = FpsMetrics.empty(60.0f);
    }

    @Override
    public synchronized boolean isMonitoring() {
        return isMonitoring;
    }

    @Override
    public synchronized FpsMetrics getLatestMetrics() {
        return latestMetrics;
    }
}
