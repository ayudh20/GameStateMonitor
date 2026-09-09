package com.gamestate.monitor.model;

/**
 * PerformanceStats Data Model Class
 * ---------------------------------
 * This class represents a snapshot of the dynamic system metrics collected
 * at a specific point in time (RAM, Battery, Thermals, Storage, Health Status).
 */
public class PerformanceStats {

    /**
     * Enum representing the high-level health state of the gaming device.
     * Enums in Java provide type-safe representations of fixed states.
     */
    public enum SystemStatus {
        OPTIMAL,     // RAM usage < 75% and Battery Temp < 38°C
        MODERATE,    // RAM usage 75-85% or Battery Temp 38-42°C
        HIGH_LOAD    // RAM usage > 85% or Battery Temp > 42°C (risk of thermal throttling)
    }

    // RAM Metrics (in bytes and calculated percentage)
    private final long totalRamBytes;
    private final long availableRamBytes;
    private final long usedRamBytes;
    private final int ramUsagePercentage;

    // Battery & Thermal Metrics
    private final int batteryLevel;             // Percentage: 0 to 100
    private final float batteryTemperatureC;    // Celsius
    private final boolean isCharging;
    private final String batteryStatus;         // E.g., "Charging (AC)", "Discharging"

    // Storage Metrics (in bytes and percentage)
    private final long totalStorageBytes;
    private final long availableStorageBytes;
    private final long usedStorageBytes;
    private final int storageUsagePercentage;

    // System Evaluation
    private final SystemStatus systemStatus;
    private final String formattedTimestamp;

    /**
     * Full constructor for PerformanceStats snapshot.
     */
    public PerformanceStats(long totalRamBytes,
                            long availableRamBytes,
                            int batteryLevel,
                            float batteryTemperatureC,
                            boolean isCharging,
                            String batteryStatus,
                            long totalStorageBytes,
                            long availableStorageBytes,
                            String formattedTimestamp) {

        this.totalRamBytes = totalRamBytes;
        this.availableRamBytes = availableRamBytes;
        this.usedRamBytes = Math.max(0, totalRamBytes - availableRamBytes);
        this.ramUsagePercentage = totalRamBytes > 0
                ? (int) ((usedRamBytes * 100.0) / totalRamBytes)
                : 0;

        this.batteryLevel = batteryLevel;
        this.batteryTemperatureC = batteryTemperatureC;
        this.isCharging = isCharging;
        this.batteryStatus = batteryStatus != null ? batteryStatus : "Unknown";

        this.totalStorageBytes = totalStorageBytes;
        this.availableStorageBytes = availableStorageBytes;
        this.usedStorageBytes = Math.max(0, totalStorageBytes - availableStorageBytes);
        this.storageUsagePercentage = totalStorageBytes > 0
                ? (int) ((usedStorageBytes * 100.0) / totalStorageBytes)
                : 0;

        this.formattedTimestamp = formattedTimestamp;
        this.systemStatus = evaluateSystemStatus(this.ramUsagePercentage, this.batteryTemperatureC);
    }

    /**
     * Logic to evaluate overall device stress level based on RAM and thermal metrics.
     */
    private SystemStatus evaluateSystemStatus(int ramPercent, float batteryTempC) {
        if (ramPercent >= 85 || batteryTempC >= 42.0f) {
            return SystemStatus.HIGH_LOAD;
        } else if (ramPercent >= 75 || batteryTempC >= 38.0f) {
            return SystemStatus.MODERATE;
        } else {
            return SystemStatus.OPTIMAL;
        }
    }

    // =========================================================================
    // Getters for UI access
    // =========================================================================

    public long getTotalRamBytes() {
        return totalRamBytes;
    }

    public long getAvailableRamBytes() {
        return availableRamBytes;
    }

    public long getUsedRamBytes() {
        return usedRamBytes;
    }

    public int getRamUsagePercentage() {
        return ramUsagePercentage;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public float getBatteryTemperatureC() {
        return batteryTemperatureC;
    }

    public float getBatteryTemperatureF() {
        return (batteryTemperatureC * 9.0f / 5.0f) + 32.0f;
    }

    public boolean isCharging() {
        return isCharging;
    }

    public String getBatteryStatus() {
        return batteryStatus;
    }

    public long getTotalStorageBytes() {
        return totalStorageBytes;
    }

    public long getAvailableStorageBytes() {
        return availableStorageBytes;
    }

    public long getUsedStorageBytes() {
        return usedStorageBytes;
    }

    public int getStorageUsagePercentage() {
        return storageUsagePercentage;
    }

    public SystemStatus getSystemStatus() {
        return systemStatus;
    }

    public String getFormattedTimestamp() {
        return formattedTimestamp;
    }
}
