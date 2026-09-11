package com.gamestate.monitor.fps;

/**
 * FpsMonitorState
 * ---------------
 * High-level lifecycle state for game performance monitoring.
 */
public enum FpsMonitorState {
    /** No game active in foreground. */
    NO_GAME_DETECTED("No Game Detected", "NO GAME"),

    /** Game active in foreground, but neither ADB DUMP nor Root is authorized. */
    WAITING_FOR_BACKEND("Waiting for supported FPS backend", "WAITING FOR BACKEND"),

    /** Game active and elevated backend is actively streaming frame pacing data. */
    FPS_MONITORING_ACTIVE("FPS Monitoring Active", "ACTIVE"),

    /** Service or monitoring stopped. */
    STOPPED("Monitoring Inactive", "INACTIVE");

    private final String displayStatus;
    private final String badgeText;

    FpsMonitorState(String displayStatus, String badgeText) {
        this.displayStatus = displayStatus;
        this.badgeText = badgeText;
    }

    public String getDisplayStatus() {
        return displayStatus;
    }

    public String getBadgeText() {
        return badgeText;
    }

    @Override
    public String toString() {
        return displayStatus;
    }
}
