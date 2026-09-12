package com.gamestate.monitor.fps;

import android.content.Context;

/**
 * RootFpsBackend
 * --------------
 * FPS data provider utilizing elevated root (su) shell privileges.
 * Inherits the full SurfaceFlinger hardware timing and BLAST frame pacing engine,
 * executing shell commands via su / RootUtils without requiring tethered ADB or Shizuku.
 */
public class RootFpsBackend extends SurfaceFlingerFpsBackend {

    public RootFpsBackend(Context context) {
        super(context);
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
        return RootUtils.isRootAvailable();
    }

    @Override
    public AvailabilityStatus getAvailabilityStatus(Context context) {
        if (isAvailable(context)) {
            return AvailabilityStatus.AVAILABLE;
        }
        return AvailabilityStatus.REQUIRES_ROOT;
    }
}
