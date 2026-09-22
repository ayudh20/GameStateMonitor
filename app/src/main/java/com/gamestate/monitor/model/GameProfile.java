package com.gamestate.monitor.model;

import android.graphics.drawable.Drawable;

import java.io.Serializable;

/**
 * GameProfile
 * -----------
 * Represents an installed game and its aggregated lifetime analytics
 * computed from recorded benchmark sessions.
 */
public class GameProfile implements Serializable {

    private String packageName;
    private String appName;
    private boolean isFavorite;
    private int customOrder;

    // Aggregated Lifetime Session Statistics
    private long lastPlayedMs;
    private int totalSessionsCount;
    private long totalPlayTimeMs;
    private long longestSessionMs;

    // Aggregated Performance Benchmarks
    private float avgFps;
    private float bestSessionFps;
    private float avgStabilityPercent;

    // Aggregated Thermals
    private float avgTempC;
    private float peakTempC;

    // Aggregated Battery
    private float avgDrainRatePerHour;

    // Transient loaded app icon (not serialized)
    private transient Drawable iconDrawable;

    public GameProfile(String packageName, String appName) {
        this.packageName = packageName;
        this.appName = appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public int getCustomOrder() {
        return customOrder;
    }

    public void setCustomOrder(int customOrder) {
        this.customOrder = customOrder;
    }

    public long getLastPlayedMs() {
        return lastPlayedMs;
    }

    public void setLastPlayedMs(long lastPlayedMs) {
        this.lastPlayedMs = lastPlayedMs;
    }

    public int getTotalSessionsCount() {
        return totalSessionsCount;
    }

    public void setTotalSessionsCount(int totalSessionsCount) {
        this.totalSessionsCount = totalSessionsCount;
    }

    public long getTotalPlayTimeMs() {
        return totalPlayTimeMs;
    }

    public void setTotalPlayTimeMs(long totalPlayTimeMs) {
        this.totalPlayTimeMs = totalPlayTimeMs;
    }

    public long getLongestSessionMs() {
        return longestSessionMs;
    }

    public void setLongestSessionMs(long longestSessionMs) {
        this.longestSessionMs = longestSessionMs;
    }

    public float getAvgFps() {
        return avgFps;
    }

    public void setAvgFps(float avgFps) {
        this.avgFps = avgFps;
    }

    public float getBestSessionFps() {
        return bestSessionFps;
    }

    public void setBestSessionFps(float bestSessionFps) {
        this.bestSessionFps = bestSessionFps;
    }

    public float getAvgStabilityPercent() {
        return avgStabilityPercent;
    }

    public void setAvgStabilityPercent(float avgStabilityPercent) {
        this.avgStabilityPercent = avgStabilityPercent;
    }

    public float getAvgTempC() {
        return avgTempC;
    }

    public void setAvgTempC(float avgTempC) {
        this.avgTempC = avgTempC;
    }

    public float getPeakTempC() {
        return peakTempC;
    }

    public void setPeakTempC(float peakTempC) {
        this.peakTempC = peakTempC;
    }

    public float getAvgDrainRatePerHour() {
        return avgDrainRatePerHour;
    }

    public void setAvgDrainRatePerHour(float avgDrainRatePerHour) {
        this.avgDrainRatePerHour = avgDrainRatePerHour;
    }

    public Drawable getIconDrawable() {
        return iconDrawable;
    }

    public void setIconDrawable(Drawable iconDrawable) {
        this.iconDrawable = iconDrawable;
    }

    public boolean hasRecordedSessions() {
        return totalSessionsCount > 0;
    }
}
