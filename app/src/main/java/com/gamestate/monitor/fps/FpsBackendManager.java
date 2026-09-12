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
 * 1. RootFpsBackend (Highest privilege, autonomous)
 * 2. SurfaceFlingerFpsBackend (ADB DUMP granted)
 * 3. FallbackFpsBackend (Awaiting authorization)
 */
public class FpsBackendManager {

    private final Context context;
    private final RootFpsBackend rootBackend;
    private final SurfaceFlingerFpsBackend surfaceFlingerBackend;
    private final FallbackFpsBackend fallbackBackend;
    private final List<FpsBackend> registeredBackends;

    public FpsBackendManager(Context context, float screenRefreshRate) {
        this.context = context.getApplicationContext();
        this.rootBackend = new RootFpsBackend(this.context);
        this.surfaceFlingerBackend = new SurfaceFlingerFpsBackend(this.context);
        this.fallbackBackend = new FallbackFpsBackend(screenRefreshRate);

        List<FpsBackend> backends = new ArrayList<>();
        backends.add(rootBackend);
        backends.add(surfaceFlingerBackend);
        backends.add(fallbackBackend);
        this.registeredBackends = Collections.unmodifiableList(backends);
    }

    /**
     * Resolves the primary FPS backend based on device capabilities and granted permissions.
     */
    public FpsBackend getActiveBackend() {
        if (rootBackend.isAvailable(context)) {
            return rootBackend;
        }
        if (surfaceFlingerBackend.isAvailable(context)) {
            return surfaceFlingerBackend;
        }
        return fallbackBackend;
    }

    /**
     * @return true if at least one elevated backend (Root or ADB SurfaceFlinger) is authorized.
     */
    public boolean isSupportedBackendAvailable() {
        return rootBackend.isAvailable(context) || surfaceFlingerBackend.isAvailable(context);
    }

    /**
     * Returns a human-friendly string describing what permission or prerequisite is missing.
     */
    public AvailabilityStatus getPrimaryAvailabilityStatus() {
        if (rootBackend.isAvailable(context)) {
            return AvailabilityStatus.AVAILABLE;
        }
        if (surfaceFlingerBackend.isAvailable(context)) {
            return AvailabilityStatus.AVAILABLE;
        }
        return surfaceFlingerBackend.getAvailabilityStatus(context);
    }

    public RootFpsBackend getRootBackend() {
        return rootBackend;
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
