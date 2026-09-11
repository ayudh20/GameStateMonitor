package com.gamestate.monitor.fps;

/**
 * AvailabilityStatus
 * ------------------
 * Diagnostic status indicating whether an FPS backend is authorized and operational,
 * or explaining what permission/capability is required to activate it.
 */
public enum AvailabilityStatus {
    AVAILABLE("Ready"),
    REQUIRES_ADB_PERMISSION("Requires ADB DUMP permission"),
    REQUIRES_ROOT("Root access not detected"),
    UNSUPPORTED("Waiting for supported FPS backend");

    private final String description;

    AvailabilityStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAvailable() {
        return this == AVAILABLE;
    }

    @Override
    public String toString() {
        return description;
    }
}
