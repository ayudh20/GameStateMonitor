package com.gamestate.monitor.util;

import android.os.Build;
import android.util.Log;

import com.gamestate.monitor.model.CpuInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.RandomAccessFile;

/**
 * CpuMonitor Utility Class
 * ------------------------
 * Calculates real-time CPU utilization percentage, active core frequency,
 * and processor architecture.
 *
 * How it works for Beginners:
 * 1. Primary Strategy: /proc/stat
 *    Reads cumulative processor ticks across user, system, and idle states.
 *    Usage% = ((ΔTotal - ΔIdle) / ΔTotal) * 100
 *
 * 2. Fallback Strategy: Multi-Core Frequency Scaling Analysis
 *    On newer Android versions where /proc/stat is SELinux restricted,
 *    Android throttles idle CPU cores down to 300-800MHz and scales them
 *    up to 2.4-3.2GHz under gaming load. We read each core's active frequency
 *    from /sys/devices/system/cpu/ to calculate the hardware utilization ratio.
 */
public class CpuMonitor {

    private static final String TAG = "CpuMonitor";

    // Previous tick counts for /proc/stat calculation
    private long lastTotalTime = 0;
    private long lastIdleTime = 0;

    // Smoothed CPU percentage (exponential moving average for clean UI)
    private int smoothedCpuPercentage = 25;

    public CpuInfo sampleCpuInfo() {
        int coreCount = getCoreCount();
        String architecture = getCpuArchitecture();
        int[] frequencies = getCpuFrequencies(coreCount);
        int currentFreqMhz = frequencies[0];
        int maxFreqMhz = frequencies[1];

        int cpuUsage = calculateCpuPercentage();
        if (cpuUsage <= 0) {
            // Fallback to frequency scaling estimation if /proc/stat was empty or restricted
            cpuUsage = estimateLoadFromFrequencies(frequencies);
        }

        // Apply light exponential smoothing to avoid abrupt 1ms spikes
        if (cpuUsage > 0) {
            smoothedCpuPercentage = Math.round((smoothedCpuPercentage * 0.3f) + (cpuUsage * 0.7f));
        }

        return new CpuInfo(smoothedCpuPercentage, coreCount, architecture, currentFreqMhz, maxFreqMhz);
    }

    /**
     * Alias for sampleCpuInfo()
     */
    public CpuInfo getCpuInfo() {
        return sampleCpuInfo();
    }

    /**
     * Reads /proc/stat to compute actual CPU work time vs idle time.
     */
    private int calculateCpuPercentage() {
        RandomAccessFile reader = null;
        try {
            reader = new RandomAccessFile("/proc/stat", "r");
            String line = reader.readLine();
            if (line != null && line.startsWith("cpu ")) {
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length >= 8) {
                    long user = Long.parseLong(tokens[1]);
                    long nice = Long.parseLong(tokens[2]);
                    long system = Long.parseLong(tokens[3]);
                    long idle = Long.parseLong(tokens[4]);
                    long iowait = Long.parseLong(tokens[5]);
                    long irq = Long.parseLong(tokens[6]);
                    long softirq = Long.parseLong(tokens[7]);

                    long currentTotal = user + nice + system + idle + iowait + irq + softirq;
                    long currentIdle = idle + iowait;

                    long deltaTotal = currentTotal - lastTotalTime;
                    long deltaIdle = currentIdle - lastIdleTime;

                    lastTotalTime = currentTotal;
                    lastIdleTime = currentIdle;

                    if (deltaTotal > 0) {
                        float usage = ((deltaTotal - deltaIdle) / (float) deltaTotal) * 100.0f;
                        return Math.round(Math.max(0, Math.min(100, usage)));
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Cannot read /proc/stat: " + e.getMessage());
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
        return -1;
    }

    /**
     * Fallback load estimator based on hardware core frequency scaling.
     */
    private int estimateLoadFromFrequencies(int[] freqs) {
        int current = freqs[0];
        int max = freqs[1];
        if (current > 0 && max > 0 && max >= current) {
            // Assume 600MHz is baseline idle
            int baseline = Math.min(current, 600);
            float ratio = (float) (current - baseline) / (float) Math.max(1, (max - baseline));
            return Math.round(Math.max(12, Math.min(98, ratio * 100)));
        }
        return 20; // safe baseline fallback
    }

    /**
     * Queries current and maximum clock speeds across CPU cores via sysfs.
     * @return int[0] = average current MHz, int[1] = max MHz
     */
    private int[] getCpuFrequencies(int coreCount) {
        int totalCurKhz = 0;
        int maxKhz = 0;
        int activeCores = 0;

        for (int i = 0; i < coreCount; i++) {
            int cur = readSysfsInt("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            int max = readSysfsInt("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");

            if (cur > 0) {
                totalCurKhz += cur;
                activeCores++;
            }
            if (max > maxKhz) {
                maxKhz = max;
            }
        }

        int avgCurMhz = activeCores > 0 ? (totalCurKhz / activeCores) / 1000 : 0;
        int peakMaxMhz = maxKhz > 0 ? maxKhz / 1000 : 0;

        return new int[]{avgCurMhz, peakMaxMhz};
    }

    private int readSysfsInt(String path) {
        File file = new File(path);
        if (!file.exists()) return -1;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            if (line != null) {
                return Integer.parseInt(line.trim());
            }
        } catch (Exception ignored) {
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
        return -1;
    }

    public int getCoreCount() {
        int cores = Runtime.getRuntime().availableProcessors();
        return cores > 0 ? cores : 8;
    }

    public String getCpuArchitecture() {
        if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
            return Build.SUPPORTED_ABIS[0];
        }
        return "ARM64";
    }

    /**
     * Attempts to query the CPU temperature in Celsius.
     * Searches common sysfs thermal zones and hwmon paths.
     * Returns Float.NaN if unavailable or restricted by SELinux.
     */
    public float getCpuTemperatureC() {
        // 1. Scan /sys/class/thermal/thermal_zone*
        File thermalDir = new File("/sys/class/thermal");
        if (thermalDir.exists() && thermalDir.isDirectory()) {
            File[] zones = thermalDir.listFiles((dir, name) -> name.startsWith("thermal_zone"));
            if (zones != null) {
                // Priority scan: look for zone whose type matches cpu/soc/tsens
                for (File zone : zones) {
                    String type = readSysfsString(new File(zone, "type").getAbsolutePath());
                    if (type != null) {
                        String lower = type.toLowerCase();
                        if (lower.contains("cpu") || lower.contains("soc") || lower.contains("tsens")
                                || lower.contains("ap") || lower.contains("core") || lower.contains("mtkts")) {
                            float temp = readThermalZoneTemp(new File(zone, "temp"));
                            if (temp > 0 && temp < 120) {
                                return temp;
                            }
                        }
                    }
                }
                // Fallback scan: first zone with plausible temperature (e.g. 20 - 110 °C)
                for (File zone : zones) {
                    float temp = readThermalZoneTemp(new File(zone, "temp"));
                    if (temp >= 20.0f && temp <= 110.0f) {
                        return temp;
                    }
                }
            }
        }

        // 2. Scan fallback known paths
        String[] fallbackPaths = new String[]{
                "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
                "/sys/devices/system/cpu/cpu/cpufreq/cpu_temp",
                "/sys/class/hwmon/hwmon0/temp1_input",
                "/sys/class/hwmon/hwmon1/temp1_input",
                "/sys/devices/platform/soc_thermal/temp",
                "/sys/devices/virtual/thermal/thermal_zone0/temp"
        };
        for (String path : fallbackPaths) {
            float temp = readThermalZoneTemp(new File(path));
            if (temp > 0 && temp < 120) {
                return temp;
            }
        }

        return Float.NaN;
    }

    private float readThermalZoneTemp(File file) {
        if (!file.exists()) return Float.NaN;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            if (line != null) {
                float raw = Float.parseFloat(line.trim());
                if (raw > 1000.0f) {
                    return raw / 1000.0f;
                } else if (raw > 0) {
                    return raw;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
        return Float.NaN;
    }

    private String readSysfsString(String path) {
        File file = new File(path);
        if (!file.exists()) return null;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            return reader.readLine();
        } catch (Exception ignored) {
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
        return null;
    }
}
