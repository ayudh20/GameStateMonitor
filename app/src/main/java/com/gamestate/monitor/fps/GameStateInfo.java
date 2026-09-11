package com.gamestate.monitor.fps;

import java.io.Serializable;

/**
 * GameStateInfo
 * -------------
 * Immutable snapshot of the detected foreground application state.
 */
public class GameStateInfo implements Serializable {

    private final String packageName;
    private final String appName;
    private final boolean isGame;
    private final boolean isForeground;
    private final long detectedTimestampMs;

    public GameStateInfo(String packageName,
                          String appName,
                          boolean isGame,
                          boolean isForeground,
                          long detectedTimestampMs) {
        this.packageName = packageName;
        this.appName = appName;
        this.isGame = isGame;
        this.isForeground = isForeground;
        this.detectedTimestampMs = detectedTimestampMs;
    }

    /**
     * Represents an empty state when no game is running in foreground.
     */
    public static GameStateInfo none() {
        return new GameStateInfo(null, "No Game Detected", false, false, System.currentTimeMillis());
    }

    public boolean hasGame() {
        return isGame && packageName != null && !packageName.isEmpty();
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppName() {
        return appName != null ? appName : (packageName != null ? packageName : "None");
    }

    public boolean isGame() {
        return isGame;
    }

    public boolean isForeground() {
        return isForeground;
    }

    public long getDetectedTimestampMs() {
        return detectedTimestampMs;
    }

    public String getFormattedTitle() {
        if (!hasGame()) {
            return "No Game Detected";
        }
        if (appName != null && !appName.equals(packageName)) {
            return appName + " (" + packageName + ")";
        }
        return packageName;
    }

    @Override
    public String toString() {
        return "GameStateInfo{" +
                "package='" + packageName + '\'' +
                ", appName='" + appName + '\'' +
                ", isGame=" + isGame +
                '}';
    }
}
