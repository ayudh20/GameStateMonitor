package com.gamestate.monitor.fps;

/**
 * FpsBackendType
 * --------------
 * Identifies the mechanism or authority providing real frame pacing / FPS data.
 */
public enum FpsBackendType {
    SURFACE_FLINGER_ADB("SurfaceFlinger (ADB)"),
    ROOT("Root Shell (su)"),
    FALLBACK("Fallback"),
    NONE("None (Unconfigured)");

    private final String displayName;

    FpsBackendType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
