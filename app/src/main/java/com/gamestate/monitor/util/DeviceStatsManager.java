package com.gamestate.monitor.util;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.gamestate.monitor.model.DeviceInfo;
import com.gamestate.monitor.model.PerformanceStats;

import java.io.File;

/**
 * DeviceStatsManager Class
 * ------------------------
 * This manager class acts as the bridge between Android Operating System APIs
 * and our application's data models.
 *
 * In Android development, hardware and OS metrics are accessed through
 * System Services (like ActivityManager, WindowManager) and Broadcast Intents
 * (like Intent.ACTION_BATTERY_CHANGED).
 */
public class DeviceStatsManager {

    private static final String TAG = "DeviceStatsManager";

    // Application context to prevent Activity memory leaks
    private final Context appContext;

    /**
     * Constructor accepts an Android Context.
     * We use getApplicationContext() to ensure we do not retain references to Activities.
     */
    public DeviceStatsManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Retrieves static device hardware and operating system specifications.
     * These specs rarely change during app runtime, but are queried here cleanly.
     *
     * @return DeviceInfo object populated with manufacturer, model, OS, and refresh rate.
     */
    public DeviceInfo getDeviceInfo() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        String androidVersion = Build.VERSION.RELEASE;
        int apiLevel = Build.VERSION.SDK_INT;
        float refreshRate = getScreenRefreshRate();

        return new DeviceInfo(manufacturer, model, androidVersion, apiLevel, refreshRate);
    }

    /**
     * Retrieves a real-time snapshot of system performance metrics:
     * RAM, Battery, Thermals, and Internal Storage.
     *
     * @return PerformanceStats snapshot object
     */
    public PerformanceStats getPerformanceStats() {
        // 1. Fetch RAM information
        long totalRam = 0;
        long availableRam = 0;
        try {
            ActivityManager activityManager = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(memoryInfo);
                totalRam = memoryInfo.totalMem;
                availableRam = memoryInfo.availMem;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching RAM stats: " + e.getMessage());
        }

        // 2. Fetch Battery status & Thermals via sticky system broadcast
        int batteryLevel = 0;
        float batteryTempC = 0.0f;
        boolean isCharging = false;
        String batteryStatusText = "Unknown";

        try {
            // ACTION_BATTERY_CHANGED is a "sticky" broadcast: it stays cached in the OS,
            // so passing null as the BroadcastReceiver returns the latest cached Intent immediately.
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = appContext.registerReceiver(null, ifilter);

            if (batteryStatus != null) {
                // Calculate percentage
                int rawLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (rawLevel >= 0 && scale > 0) {
                    batteryLevel = Math.round((rawLevel / (float) scale) * 100);
                }

                // Battery temperature is provided by Android in tenths of a degree Celsius (e.g. 345 = 34.5°C)
                int rawTemp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                batteryTempC = rawTemp / 10.0f;

                // Charging state and power source
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                              status == BatteryManager.BATTERY_STATUS_FULL);

                int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                if (isCharging) {
                    if (chargePlug == BatteryManager.BATTERY_PLUGGED_USB) {
                        batteryStatusText = "Charging (USB)";
                    } else if (chargePlug == BatteryManager.BATTERY_PLUGGED_AC) {
                        batteryStatusText = "Fast Charging (AC)";
                    } else if (chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS) {
                        batteryStatusText = "Charging (Wireless)";
                    } else {
                        batteryStatusText = "Charging";
                    }
                } else {
                    batteryStatusText = "Discharging";
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching Battery stats: " + e.getMessage());
        }

        // 3. Fetch Internal Storage statistics via StatFs
        long totalStorage = 0;
        long availableStorage = 0;
        try {
            // Environment.getDataDirectory() points to the primary internal data partition
            File dataDir = Environment.getDataDirectory();
            StatFs stat = new StatFs(dataDir.getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availableBlocks = stat.getAvailableBlocksLong();

            totalStorage = totalBlocks * blockSize;
            availableStorage = availableBlocks * blockSize;
        } catch (Exception e) {
            Log.e(TAG, "Error fetching Storage stats: " + e.getMessage());
        }

        // 4. Current Formatted Timestamp
        String formattedTimestamp = FormatUtils.getCurrentDateTimeFormatted();

        return new PerformanceStats(
                totalRam,
                availableRam,
                batteryLevel,
                batteryTempC,
                isCharging,
                batteryStatusText,
                totalStorage,
                availableStorage,
                formattedTimestamp
        );
    }

    /**
     * Determines screen refresh rate safely across different Android API versions.
     */
    public float getScreenRefreshRate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+) preferred approach via DisplayManager
                DisplayManager displayManager = (DisplayManager) appContext.getSystemService(Context.DISPLAY_SERVICE);
                if (displayManager != null) {
                    Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
                    if (display != null) {
                        return display.getMode().getRefreshRate();
                    }
                }
            }

            // Fallback for Android 7.0 to 10 (APIs 24-29)
            WindowManager wm = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null && wm.getDefaultDisplay() != null) {
                return wm.getDefaultDisplay().getRefreshRate();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not determine exact display refresh rate, defaulting to 60Hz: " + e.getMessage());
        }
        return 60.0f;
    }
}
