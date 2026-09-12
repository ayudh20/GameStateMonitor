package com.gamestate.monitor.fps;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * FpsBackendManager
 * -----------------
 * Coordinates and auto-resolves the best available FPS backend on the device.
 * Priority hierarchy:
 * 1. SurfaceFlingerFpsBackend via Shizuku (Wireless on-device ADB) or granted DUMP permission
 * 2. FallbackFpsBackend (Awaiting authorization)
 */
public class FpsBackendManager {

    private final Context context;
    private final SurfaceFlingerFpsBackend surfaceFlingerBackend;
    private final FallbackFpsBackend fallbackBackend;
    private final List<FpsBackend> registeredBackends;

    public FpsBackendManager(Context context, float screenRefreshRate) {
        this.context = context.getApplicationContext();
        this.surfaceFlingerBackend = new SurfaceFlingerFpsBackend(this.context);
        this.fallbackBackend = new FallbackFpsBackend(screenRefreshRate);

        List<FpsBackend> backends = new ArrayList<>();
        backends.add(surfaceFlingerBackend);
        backends.add(fallbackBackend);
        this.registeredBackends = Collections.unmodifiableList(backends);
    }

    /**
     * Resolves the primary FPS backend based on device capabilities and granted permissions.
     */
    public FpsBackend getActiveBackend() {
        if (surfaceFlingerBackend.isAvailable(context)) {
            return surfaceFlingerBackend;
        }
        return fallbackBackend;
    }

    /**
     * @return true if an elevated backend (Shizuku or ADB SurfaceFlinger) is authorized.
     */
    public boolean isSupportedBackendAvailable() {
        return surfaceFlingerBackend.isAvailable(context);
    }

    /**
     * Returns a human-friendly string describing what permission or prerequisite is missing.
     */
    public AvailabilityStatus getPrimaryAvailabilityStatus() {
        return surfaceFlingerBackend.getAvailabilityStatus(context);
    }

    public SurfaceFlingerFpsBackend getSurfaceFlingerBackend() {
        return surfaceFlingerBackend;
    }

    public FallbackFpsBackend getFallbackBackend() {
        return fallbackBackend;
    }

    public List<FpsBackend> getRegisteredBackends() {
        return registeredBackends;
    }
}
