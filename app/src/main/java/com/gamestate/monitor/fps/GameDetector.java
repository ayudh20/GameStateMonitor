package com.gamestate.monitor.fps;

import android.app.ActivityManager;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GameDetector
 * ------------
 * Responsible for detecting active running games with strict liveness verification.
 *
 * Ensures that when games are closed or cleared from recent tasks, they immediately
 * revert to "No Game Detected" instead of lingering in an active state.
 */
public class GameDetector {

    private static final String TAG = "GameDetector";

    private final Context context;
    private final PackageManager packageManager;
    private final Set<String> systemExclusions = new HashSet<>();

    // Short window (30 seconds) to accommodate quick app-switching to GameState Monitor
    private static final long ACTIVE_APP_WINDOW_MS = 30 * 1000;

    public GameDetector(Context context) {
        this.context = context.getApplicationContext();
        this.packageManager = this.context.getPackageManager();

        // System packages to ignore as games
        systemExclusions.add("com.android.systemui");
        systemExclusions.add("com.google.android.apps.nexuslauncher");
        systemExclusions.add("com.sec.android.app.launcher");
        systemExclusions.add("com.miui.home");
        systemExclusions.add("com.oppo.launcher");
        systemExclusions.add("com.oneplus.launcher");
        systemExclusions.add("com.huawei.android.launcher");
        systemExclusions.add("com.google.android.inputmethod.latin");
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
     * Samples the current active game, strictly verifying that the game's process
     * is alive and running on the system.
     */
    public GameStateInfo detectForegroundGame() {
        String candidatePkg = resolveActiveGamePackage();

        if (candidatePkg != null && !candidatePkg.isEmpty()) {
            // Verify that the process is actually running right now
            if (isPackageProcessAlive(candidatePkg)) {
                boolean isGame = isGamePackage(candidatePkg);
                String appName = getAppLabel(candidatePkg);

                return new GameStateInfo(
                        candidatePkg,
                        appName,
                        isGame,
                        true,
                        System.currentTimeMillis()
                );
            }
        }

        return GameStateInfo.none();
    }

    /**
     * Multi-tiered resolver to find the active game package candidate.
     */
    private String resolveActiveGamePackage() {
        boolean hasDump = context.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED;

        // Tier 1: Check SurfaceFlinger for active game composition surfaces
        if (hasDump) {
            String sfGame = getActiveGameFromSurfaceFlinger();
            if (sfGame != null) {
                return sfGame;
            }

            // Tier 2: Check current activity stack (top resumed activity)
            String activityGame = getActiveGameFromActivityStack();
            if (activityGame != null) {
                return activityGame;
            }
        }

        // Tier 3: UsageStatsManager Events within short 30-second window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && hasUsageStatsPermission()) {
            String usageGame = getActiveGameFromUsageEvents();
            if (usageGame != null) {
                return usageGame;
            }

            // Tier 4: UsageStats queryUsageStats within 30-second window
            String statsGame = getActiveGameFromUsageStats();
            if (statsGame != null) {
                return statsGame;
            }
        }

        return null;
    }

    /**
     * Strictly verifies whether an application's process is currently running on the device.
     * When a user swipes away or clears an app from recents, its process and tasks terminate.
     */
    public boolean isPackageProcessAlive(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;

        // 1. With DUMP permission: check dumpsys activity processes for live *APP* record
        if (context.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED) {
            try {
                java.lang.Process process = Runtime.getRuntime().exec(
                        new String[]{"dumpsys", "activity", "p", packageName}
                );
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                boolean isAlive = false;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("*APP*") && line.contains(":" + packageName)) {
                        isAlive = true;
                        break;
                    }
                }
                reader.close();
                if (isAlive) return true;
                // Also check if any active SurfaceFlinger layer exists for this package
                return hasSurfaceFlingerLayer(packageName);
            } catch (Exception e) {
                Log.d(TAG, "Error checking process liveness via dumpsys: " + e.getMessage());
            }
        }

        // 2. Standard ActivityManager running processes check
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.RunningAppProcessInfo> runningProcesses = am.getRunningAppProcesses();
                if (runningProcesses != null) {
                    for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                        if (packageName.equals(processInfo.processName)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    /**
     * Checks if any SurfaceView or ActivityRecord layers are currently active in SurfaceFlinger.
     */
    private boolean hasSurfaceFlingerLayer(String packageName) {
        try {
            java.lang.Process process = Runtime.getRuntime().exec(new String[]{"dumpsys", "SurfaceFlinger", "--list"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(packageName) && (line.contains("SurfaceView[") || line.contains("ActivityRecord{"))) {
                    reader.close();
                    return true;
                }
            }
            reader.close();
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Scans SurfaceFlinger for active game hardware surfaces (e.g. SurfaceView[com.pubg.imobile/...]).
     */
    private String getActiveGameFromSurfaceFlinger() {
        try {
            java.lang.Process process = Runtime.getRuntime().exec(new String[]{"dumpsys", "SurfaceFlinger", "--list"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.contains("SurfaceView[")) {
                    String pkg = extractPackageFromLine(line);
                    if (pkg != null && !systemExclusions.contains(pkg) && isGamePackage(pkg)) {
                        reader.close();
                        return pkg;
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            Log.d(TAG, "Error scanning SurfaceFlinger: " + e.getMessage());
        }
        return null;
    }

    /**
     * Scans activity stack for the top resumed game activity.
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
     * Inspects UsageEvents within the last 30 seconds.
     */
    private String getActiveGameFromUsageEvents() {
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;

            long endTime = System.currentTimeMillis();
            long startTime = endTime - ACTIVE_APP_WINDOW_MS;

            UsageEvents events = usm.queryEvents(startTime, endTime);
            UsageEvents.Event event = new UsageEvents.Event();

            String lastGame = null;

            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    String pkg = event.getPackageName();
                    if (pkg != null && !systemExclusions.contains(pkg) && isGamePackage(pkg)) {
                        lastGame = pkg;
                    }
                }
            }

            return lastGame;
        } catch (Exception e) {
            Log.d(TAG, "Error querying UsageEvents: " + e.getMessage());
        }
        return null;
    }

    /**
     * Inspects UsageStats within the last 30 seconds.
     */
    private String getActiveGameFromUsageStats() {
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;

            long endTime = System.currentTimeMillis();
            long startTime = endTime - ACTIVE_APP_WINDOW_MS;

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

            if (mostRecentGame != null && (endTime - mostRecentGame.getLastTimeUsed() < ACTIVE_APP_WINDOW_MS)) {
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
            // SurfaceView[com.pubg.imobile/com.epicgames...
            int svIndex = line.indexOf("SurfaceView[");
            if (svIndex >= 0) {
                int start = svIndex + "SurfaceView[".length();
                int slash = line.indexOf('/', start);
                if (slash > start) {
                    return line.substring(start, slash).trim();
                }
            }

            // ... u0 com.example.game/com.example...
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
