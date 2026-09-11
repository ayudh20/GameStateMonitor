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

    /**
     * Enum representing fine-grained thermal severity state:
     * Normal (Green), Warm (Yellow), Hot (Orange), Critical (Red)
     */
    public enum ThermalStatus {
        NORMAL,
        WARM,
        HOT,
        CRITICAL
    }

    // RAM Metrics (in bytes and calculated percentage)
    private final long totalRamBytes;
    private final long availableRamBytes;
    private final long usedRamBytes;
    private final int ramUsagePercentage;

    // Battery & Thermal Metrics
    private final int batteryLevel;             // Percentage: 0 to 100
    private final float batteryTemperatureC;    // Celsius
    private final float cpuTemperatureC;        // Celsius (Float.NaN if unavailable)
    private final ThermalStatus thermalStatus;  // Normal, Warm, Hot, Critical
    private final boolean isCharging;
    private final String batteryStatus;         // E.g., "Charging (AC)", "Discharging"
    private final float batteryVoltageV;        // Volts, e.g., 4.12V

    // Storage Metrics (in bytes and percentage)
    private final long totalStorageBytes;
    private final long availableStorageBytes;
    private final long usedStorageBytes;
    private final int storageUsagePercentage;

    // System Evaluation
    private final SystemStatus systemStatus;
    private final String formattedTimestamp;

    /**
     * Backward-compatible constructor defaulting voltage to 4.0V.
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
        this(totalRamBytes, availableRamBytes, batteryLevel, batteryTemperatureC, Float.NaN, null,
                isCharging, batteryStatus, 4.0f, totalStorageBytes, availableStorageBytes, formattedTimestamp);
    }

    /**
     * Backward-compatible constructor including battery voltage.
     */
    public PerformanceStats(long totalRamBytes,
                            long availableRamBytes,
                            int batteryLevel,
                            float batteryTemperatureC,
                            boolean isCharging,
                            String batteryStatus,
                            float batteryVoltageV,
                            long totalStorageBytes,
                            long availableStorageBytes,
                            String formattedTimestamp) {
        this(totalRamBytes, availableRamBytes, batteryLevel, batteryTemperatureC, Float.NaN, null,
                isCharging, batteryStatus, batteryVoltageV, totalStorageBytes, availableStorageBytes, formattedTimestamp);
    }

    /**
     * Comprehensive constructor for PerformanceStats snapshot including CPU thermals and ThermalStatus.
     */
    public PerformanceStats(long totalRamBytes,
                            long availableRamBytes,
                            int batteryLevel,
                            float batteryTemperatureC,
                            float cpuTemperatureC,
                            ThermalStatus thermalStatus,
                            boolean isCharging,
                            String batteryStatus,
                            float batteryVoltageV,
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
        this.cpuTemperatureC = cpuTemperatureC;
        this.isCharging = isCharging;
        this.batteryStatus = batteryStatus != null ? batteryStatus : "Unknown";
        this.batteryVoltageV = batteryVoltageV > 0 ? batteryVoltageV : 4.0f;

        this.totalStorageBytes = totalStorageBytes;
        this.availableStorageBytes = availableStorageBytes;
        this.usedStorageBytes = Math.max(0, totalStorageBytes - availableStorageBytes);
        this.storageUsagePercentage = totalStorageBytes > 0
                ? (int) ((usedStorageBytes * 100.0) / totalStorageBytes)
                : 0;

        this.formattedTimestamp = formattedTimestamp;
        this.systemStatus = evaluateSystemStatus(this.ramUsagePercentage, this.batteryTemperatureC);
        this.thermalStatus = thermalStatus != null ? thermalStatus : evaluateThermalStatus(this.batteryTemperatureC, this.cpuTemperatureC);
    }

    /**
     * Evaluates thermal severity state (Normal, Warm, Hot, Critical) based on sensor readings.
     */
    public static ThermalStatus evaluateThermalStatus(float batteryTempC, float cpuTempC) {
        float maxTemp = batteryTempC;
        if (!Float.isNaN(cpuTempC) && cpuTempC > 0) {
            maxTemp = Math.max(maxTemp, cpuTempC);
        }
        if (maxTemp >= 55.0f) {
            return ThermalStatus.CRITICAL;
        } else if (maxTemp >= 45.0f) {
            return ThermalStatus.HOT;
        } else if (maxTemp >= 38.0f) {
            return ThermalStatus.WARM;
        } else {
            return ThermalStatus.NORMAL;
        }
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

    public float getBatteryVoltageV() {
        return batteryVoltageV;
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

    public float getCpuTemperatureC() {
        return cpuTemperatureC;
    }

    public boolean hasCpuTemperature() {
        return !Float.isNaN(cpuTemperatureC) && cpuTemperatureC > 0;
    }

    public ThermalStatus getThermalStatus() {
        return thermalStatus != null ? thermalStatus : ThermalStatus.NORMAL;
    }

    public String getThermalStatusText() {
        ThermalStatus status = getThermalStatus();
        switch (status) {
            case WARM:
                return "Warm";
            case HOT:
                return "Hot";
            case CRITICAL:
                return "Critical";
            case NORMAL:
            default:
                return "Normal";
        }
    }

    public String getFormattedTimestamp() {
        return formattedTimestamp;
    }
}
