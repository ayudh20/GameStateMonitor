package com.gamestate.monitor.fps;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GameDetector
 * ------------
 * Responsible for detecting the active or currently running game.
 *
 * Uses a multi-tiered detection pipeline:
 * 1. SurfaceFlinger Layer Inspection (Active 3D Game / SurfaceView layers via ADB DUMP)
 * 2. Activity Stack & Focus Inspection (dumpsys activity via ADB DUMP)
 * 3. UsageStatsManager Events with intelligent system exclusion filtering
 * 4. Recent UsageStats query fallback
 */
public class GameDetector {

    private static final String TAG = "GameDetector";

    private final Context context;
    private final PackageManager packageManager;
    private final Set<String> systemExclusions = new HashSet<>();

    // Cache the last identified game so temporary task switches to GameState Monitor
    // or system quickstep recents keep the target game active
    private String lastIdentifiedGamePackage = null;
    private long lastIdentifiedGameTimestampMs = 0;
    private static final long GAME_RECENCY_WINDOW_MS = 15 * 60 * 1000; // 15 minutes

    public GameDetector(Context context) {
        this.context = context.getApplicationContext();
        this.packageManager = this.context.getPackageManager();

        // Populate system UI, launchers, keyboards, and monitor app to ignore as targets
        systemExclusions.add("com.android.systemui");
        systemExclusions.add("com.google.android.apps.nexuslauncher");
        systemExclusions.add("com.sec.android.app.launcher");
        systemExclusions.add("com.miui.home");
        systemExclusions.add("com.oppo.launcher");
        systemExclusions.add("com.oneplus.launcher");
        systemExclusions.add("com.huawei.android.launcher");
        systemExclusions.add("com.google.android.inputmethod.latin"); // Gboard
        systemExclusions.add(context.getPackageName()); // Ignore GameState Monitor itself
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
     * Samples the current foreground or active game and categorizes it.
     */
    public GameStateInfo detectForegroundGame() {
        String activeGamePkg = resolveActiveGamePackage();

        if (activeGamePkg != null && !activeGamePkg.isEmpty()) {
            boolean isGame = isGamePackage(activeGamePkg);
            String appName = getAppLabel(activeGamePkg);
            lastIdentifiedGamePackage = activeGamePkg;
            lastIdentifiedGameTimestampMs = System.currentTimeMillis();

            return new GameStateInfo(
                    activeGamePkg,
                    appName,
                    isGame,
                    true,
                    System.currentTimeMillis()
            );
        }

        // Check if a game was active recently within the recency window
        if (lastIdentifiedGamePackage != null &&
                (System.currentTimeMillis() - lastIdentifiedGameTimestampMs < GAME_RECENCY_WINDOW_MS)) {
            String appName = getAppLabel(lastIdentifiedGamePackage);
            return new GameStateInfo(
                    lastIdentifiedGamePackage,
                    appName,
                    true,
                    false, // in background/recents
                    lastIdentifiedGameTimestampMs
            );
        }

        return GameStateInfo.none();
    }

    /**
     * Multi-tiered resolver to find the active game package.
     */
    private String resolveActiveGamePackage() {
        boolean hasDump = context.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED;

        // Tier 1: Check SurfaceFlinger for running game SurfaceView layers (Requires DUMP)
        if (hasDump) {
            String sfGame = getActiveGameFromSurfaceFlinger();
            if (sfGame != null) {
                return sfGame;
            }

            // Tier 2: Check activity stack (dumpsys activity activities)
            String activityGame = getActiveGameFromActivityStack();
            if (activityGame != null) {
                return activityGame;
            }
        }

        // Tier 3: UsageStatsManager Events (Iterate with reverse system-filter)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && hasUsageStatsPermission()) {
            String usageGame = getActiveGameFromUsageEvents();
            if (usageGame != null) {
                return usageGame;
            }

            // Tier 4: UsageStats queryUsageStats fallback
            String statsGame = getActiveGameFromUsageStats();
            if (statsGame != null) {
                return statsGame;
            }
        }

        return null;
    }

    /**
     * Scans SurfaceFlinger composition layers for active game surfaces (e.g. BGMI SurfaceView).
     */
    private String getActiveGameFromSurfaceFlinger() {
        try {
            java.lang.Process process = Runtime.getRuntime().exec(new String[]{"dumpsys", "SurfaceFlinger", "--list"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String foundGame = null;

            while ((line = reader.readLine()) != null) {
                // Look for patterns:
                // SurfaceView[com.pubg.imobile/com.epicgames.ue4.GameActivity]
                // ActivityRecord{... com.pubg.imobile/...}
                if (line.contains("SurfaceView[") || line.contains("ActivityRecord{") || line.contains("com.")) {
                    String pkg = extractPackageFromLine(line);
                    if (pkg != null && !systemExclusions.contains(pkg) && isGamePackage(pkg)) {
                        foundGame = pkg;
                        // SurfaceView indicates an active hardware rendered game layer
                        if (line.contains("SurfaceView[")) {
                            reader.close();
                            return pkg;
                        }
                    }
                }
            }
            reader.close();
            if (foundGame != null) return foundGame;
        } catch (Exception e) {
            Log.d(TAG, "Error scanning SurfaceFlinger: " + e.getMessage());
        }
        return null;
    }

    /**
     * Scans activity stack for the top game activity.
     */
    private String getActiveGameFromActivityStack() {
        try {
            java.lang.Process process = Runtime.getRuntime().exec(new String[]{"dumpsys", "activity", "activities"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String topGame = null;

            while ((line = reader.readLine()) != null) {
                if (line.contains("topResumedActivity=") || line.contains("ResumedActivity:") || line.contains("mFocusedApp=")) {
                    String pkg = extractPackageFromLine(line);
                    if (pkg != null && !systemExclusions.contains(pkg)) {
                        if (isGamePackage(pkg)) {
                            reader.close();
                            return pkg;
                        }
                    }
                } else if (line.contains("ActivityRecord{") && topGame == null) {
                    String pkg = extractPackageFromLine(line);
                    if (pkg != null && !systemExclusions.contains(pkg) && isGamePackage(pkg)) {
                        topGame = pkg;
                    }
                }
            }
            reader.close();
            return topGame;
        } catch (Exception e) {
            Log.d(TAG, "Error scanning activity stack: " + e.getMessage());
        }
        return null;
    }

    /**
     * Inspects UsageEvents within recent minutes, filtering out system launchers & GameState Monitor.
     */
    private String getActiveGameFromUsageEvents() {
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;

            long endTime = System.currentTimeMillis();
            long startTime = endTime - (10 * 60 * 1000); // 10 minutes window

            UsageEvents events = usm.queryEvents(startTime, endTime);
            UsageEvents.Event event = new UsageEvents.Event();

            String lastNonSystemApp = null;
            String lastGame = null;

            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    String pkg = event.getPackageName();
                    if (pkg != null && !systemExclusions.contains(pkg)) {
                        lastNonSystemApp = pkg;
                        if (isGamePackage(pkg)) {
                            lastGame = pkg;
                        }
                    }
                }
            }

            if (lastGame != null) {
                return lastGame;
            }
            if (lastNonSystemApp != null && isGamePackage(lastNonSystemApp)) {
                return lastNonSystemApp;
            }
        } catch (Exception e) {
            Log.d(TAG, "Error querying UsageEvents: " + e.getMessage());
        }
        return null;
    }

    /**
     * Inspects UsageStats for any game executed recently.
     */
    private String getActiveGameFromUsageStats() {
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;

            long endTime = System.currentTimeMillis();
            long startTime = endTime - (15 * 60 * 1000); // 15 minutes window

            List<UsageStats> statsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);
            if (statsList == null || statsList.isEmpty()) return null;

            UsageStats mostRecentGame = null;

            for (UsageStats stat : statsList) {
                String pkg = stat.getPackageName();
                if (pkg != null && !systemExclusions.contains(pkg) && isGamePackage(pkg)) {
                    if (mostRecentGame == null || stat.getLastTimeUsed() > mostRecentGame.getLastTimeUsed()) {
                        mostRecentGame = stat;
                    }
                }
            }

            if (mostRecentGame != null && (endTime - mostRecentGame.getLastTimeUsed() < GAME_RECENCY_WINDOW_MS)) {
                return mostRecentGame.getPackageName();
            }
        } catch (Exception e) {
            Log.d(TAG, "Error querying UsageStats: " + e.getMessage());
        }
        return null;
    }

    private String extractPackageFromLine(String line) {
        if (line == null) return null;
        try {
            // Check for format: SurfaceView[com.pubg.imobile/com.epicgames...
            int svIndex = line.indexOf("SurfaceView[");
            if (svIndex >= 0) {
                int start = svIndex + "SurfaceView[".length();
                int slash = line.indexOf('/', start);
                if (slash > start) {
                    return line.substring(start, slash).trim();
                }
            }

            // Check for format: ... u0 com.example.game/com.example...
            int slashIndex = line.indexOf('/');
            if (slashIndex > 0) {
                int spaceIndex = line.lastIndexOf(' ', slashIndex);
                if (spaceIndex >= 0 && spaceIndex < slashIndex) {
                    String candidate = line.substring(spaceIndex + 1, slashIndex).trim();
                    if (!candidate.isEmpty() && !candidate.contains("}") && !candidate.contains("{")) {
                        return candidate;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Evaluates whether an application is registered or categorized as a game.
     */
    public boolean isGamePackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;

        String lower = packageName.toLowerCase();

        // 1. Explicit high-priority game package whitelist
        if (lower.equals("com.pubg.imobile") || // BGMI (Battlegrounds Mobile India)
            lower.equals("com.tencent.ig") ||    // PUBG Mobile Global
            lower.equals("com.pubg.krmobile") ||  // PUBG Mobile Korea
            lower.equals("com.vng.pubgmobile") || // PUBG Mobile Vietnam
            lower.equals("com.rekoo.pubgm") ||    // PUBG Mobile Taiwan
            lower.equals("com.pubg.newstate") ||  // New State Mobile
            lower.contains("pubg") ||
            lower.contains("freefire") ||
            lower.contains("callofduty") ||
            lower.contains("codm") ||
            lower.contains("genshin") ||
            lower.contains("fortnite") ||
            lower.contains("minecraft") ||
            lower.contains("roblox") ||
            lower.contains("fifamobile") ||
            lower.contains("wildrift") ||
            lower.contains("brawlstars") ||
            lower.contains("clashofclans") ||
            lower.contains("clashroyale") ||
            lower.contains("asphalt") ||
            lower.contains(".game") ||
            lower.contains(".games") ||
            lower.contains("unity") ||
            lower.contains("unreal")) {
            return true;
        }

        // 2. PackageManager metadata check
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
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        return false;
    }

    /**
     * Resolves the human-friendly name of the app.
     */
    public String getAppLabel(String packageName) {
        if (packageName == null) return "Unknown";
        if (packageName.equals("com.pubg.imobile")) {
            return "BGMI";
        }
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(appInfo);
            return label != null ? label.toString() : packageName;
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }
}
