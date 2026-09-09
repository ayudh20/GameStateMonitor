package com.gamestate.monitor.model;

import java.util.Locale;

/**
 * CpuInfo Data Model Class
 * ------------------------
 * Encapsulates the hardware processor specifications and live real-time
 * CPU workload metrics of the Android device.
 */
public class CpuInfo {

    private final int cpuUsagePercentage; // 0% to 100%
    private final int coreCount;          // e.g., 8 cores
    private final String architecture;    // e.g., "arm64-v8a"
    private final int currentFreqMhz;     // current active average frequency in MHz
    private final int maxFreqMhz;         // maximum rated frequency in MHz

    public CpuInfo(int cpuUsagePercentage, int coreCount, String architecture, int currentFreqMhz, int maxFreqMhz) {
        this.cpuUsagePercentage = Math.max(0, Math.min(100, cpuUsagePercentage));
        this.coreCount = Math.max(1, coreCount);
        this.architecture = architecture != null ? architecture : "Unknown";
        this.currentFreqMhz = currentFreqMhz;
        this.maxFreqMhz = maxFreqMhz;
    }

    public int getCpuUsagePercentage() {
        return cpuUsagePercentage;
    }

    public int getUsagePercentage() {
        return cpuUsagePercentage;
    }

    public int getCoreCount() {
        return coreCount;
    }

    public String getArchitecture() {
        return architecture;
    }

    public int getCurrentFreqMhz() {
        return currentFreqMhz;
    }

    public float getAverageFrequencyGhz() {
        if (currentFreqMhz > 0) {
            return currentFreqMhz / 1000.0f;
        } else if (maxFreqMhz > 0) {
            return maxFreqMhz / 1000.0f;
        }
        return 2.0f;
    }

    public int getMaxFreqMhz() {
        return maxFreqMhz;
    }

    /**
     * Returns a human-friendly description of core topology (e.g. "Octa-Core (8 Cores)").
     */
    public String getCoreDescription() {
        switch (coreCount) {
            case 4: return "Quad-Core (4 Cores)";
            case 6: return "Hexa-Core (6 Cores)";
            case 8: return "Octa-Core (8 Cores)";
            case 10: return "Deca-Core (10 Cores)";
            default: return coreCount + " Cores";
        }
    }

    /**
     * Formats the current frequency into GHz or MHz.
     */
    public String getFormattedFrequency() {
        if (currentFreqMhz > 0) {
            return String.format(Locale.getDefault(), "%.2f GHz", currentFreqMhz / 1000.0f);
        } else if (maxFreqMhz > 0) {
            return String.format(Locale.getDefault(), "Up to %.2f GHz", maxFreqMhz / 1000.0f);
        }
        return "Dynamic Scale";
    }
}
