package com.gamestate.monitor.fps;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

/**
 * GameDetector
 * ------------
 * Responsible for detecting the active foreground package and determining
 * whether it belongs to a 2D/3D game.
 */
public class GameDetector {

    private static final String TAG = "GameDetector";

    private final Context context;
    private final PackageManager packageManager;
    private final Set<String> systemExclusions = new HashSet<>();

    public GameDetector(Context context) {
        this.context = context.getApplicationContext();
        this.packageManager = this.context.getPackageManager();

        // Populate common system UI and home launchers to ignore
        systemExclusions.add("com.android.systemui");
        systemExclusions.add("com.google.android.apps.nexuslauncher");
        systemExclusions.add("com.sec.android.app.launcher");
        systemExclusions.add("com.miui.home");
        systemExclusions.add(context.getPackageName()); // Ignore our own monitor app
    }

    /**
     * Checks if the app has been granted Android's Usage Access permission.
     */
    public boolean hasUsageStatsPermission() {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.getPackageName()
            );
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            Log.d(TAG, "Error checking usage stats permission: " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns an intent to navigate directly to the Usage Access Settings screen.
     */
    public Intent getUsageAccessSettingsIntent() {
        return new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
    }

    /**
     * Samples the current foreground application and categorizes it.
     */
    public GameStateInfo detectForegroundGame() {
        String foregroundPackage = getForegroundPackageName();

        if (foregroundPackage == null || foregroundPackage.isEmpty() || systemExclusions.contains(foregroundPackage)) {
            return GameStateInfo.none();
        }

        // Determine if this package is categorized as a game
        boolean isGame = isGamePackage(foregroundPackage);
        String appName = getAppLabel(foregroundPackage);

        return new GameStateInfo(
                foregroundPackage,
                appName,
                isGame,
                true,
                System.currentTimeMillis()
        );
    }

    /**
     * Resolves the current foreground package using UsageStatsManager or DUMP fallback.
     */
    private String getForegroundPackageName() {
        // 1. Try UsageStatsManager (Recommended Android API)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && hasUsageStatsPermission()) {
            UsageStatsManager usageStatsManager =
                    (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usageStatsManager != null) {
                long endTime = System.currentTimeMillis();
                long startTime = endTime - 10000; // Check last 10 seconds

                UsageEvents events = usageStatsManager.queryEvents(startTime, endTime);
                UsageEvents.Event event = new UsageEvents.Event();
                String lastPackage = null;

                while (events.hasNextEvent()) {
                    events.getNextEvent(event);
                    if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                        lastPackage = event.getPackageName();
                    }
                }

                if (lastPackage != null && !lastPackage.isEmpty()) {
                    return lastPackage;
                }
            }
        }

        // 2. Fallback: Query via ADB dumpsys window if DUMP permission is granted
        if (context.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED) {
            String pkgFromDump = getForegroundPackageFromDumpsys();
            if (pkgFromDump != null && !pkgFromDump.isEmpty()) {
                return pkgFromDump;
            }
        }

        return null;
    }

    /**
     * Evaluates whether an application is registered as a game in Android.
     */
    public boolean isGamePackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);

            // Android 8.0+ (API 26+) explicit category
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                    return true;
                }
            }

            // Android legacy flag
            if ((appInfo.flags & ApplicationInfo.FLAG_IS_GAME) != 0) {
                return true;
            }

            // Substring heuristic for common engine/game package signatures
            String lower = packageName.toLowerCase();
            if (lower.contains(".game") || lower.contains(".games") ||
                lower.contains(".pubg") || lower.contains(".cod") ||
                lower.contains(".genshin") || lower.contains(".fortnite") ||
                lower.contains(".minecraft") || lower.contains(".roblox") ||
                lower.contains(".unity") || lower.contains(".unreal")) {
                return true;
            }

        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return false;
    }

    /**
     * Resolves the human-friendly name of the app.
     */
    public String getAppLabel(String packageName) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(appInfo);
            return label != null ? label.toString() : packageName;
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private String getForegroundPackageFromDumpsys() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"dumpsys", "window"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("mCurrentFocus") || line.contains("mFocusedApp")) {
                    // Example format: mCurrentFocus=Window{... u0 com.example.game/com.example.game.MainActivity}
                    int slashIndex = line.indexOf('/');
                    if (slashIndex > 0) {
                        int spaceIndex = line.lastIndexOf(' ', slashIndex);
                        if (spaceIndex >= 0 && spaceIndex < slashIndex) {
                            String pkg = line.substring(spaceIndex + 1, slashIndex).trim();
                            if (!pkg.isEmpty() && !pkg.contains("}")) {
                                reader.close();
                                return pkg;
                            }
                        }
                    }
                }
            }
            reader.close();
        } catch (Exception ignored) {
        }
        return null;
    }
}
