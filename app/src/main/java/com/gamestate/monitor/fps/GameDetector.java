package com.gamestate.monitor.fps;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
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
     * Samples the current active game state.
     * - If the game is running in foreground: returns GameStateInfo with isForeground = true.
     * - If the game is in background (still in task manager): returns GameStateInfo with isForeground = false.
     * - If the game is cleared/swiped away from task manager: immediately returns GameStateInfo.none().
     */
    public GameStateInfo detectForegroundGame() {
        // 1. If screen is off / not interactive, return none
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) {
                return GameStateInfo.none();
            }
        } catch (Exception ignored) {
        }

        // 2. Priority 1: Check if a game is actively in the FOREGROUND
        String fgPkg = getAuthoritativeForegroundPackage();
        if (fgPkg != null && !systemExclusions.contains(fgPkg) && isGamePackage(fgPkg)) {
            String appName = getAppLabel(fgPkg);
            return new GameStateInfo(
                    fgPkg,
                    appName,
                    true,
                    true, // isForeground = true
                    System.currentTimeMillis()
            );
        }

        // 3. Priority 2: Check if a game is active in the TASK MANAGER (running in background)
        boolean hasPrivilege = (context.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED)
                || com.gamestate.monitor.shizuku.ShizukuManager.isPermissionGranted();
        if (hasPrivilege) {
            String taskManagerGame = getActiveGameFromTaskManager();
            if (taskManagerGame != null && !taskManagerGame.isEmpty()) {
                String appName = getAppLabel(taskManagerGame);
                return new GameStateInfo(
                        taskManagerGame,
                        appName,
                        true,
                        false, // isForeground = false
                        System.currentTimeMillis()
                );
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && hasUsageStatsPermission()) {
            String usageGame = getActiveGameFromUsageEvents();
            if (usageGame != null && !usageGame.isEmpty()) {
                String appName = getAppLabel(usageGame);
                return new GameStateInfo(
                        usageGame,
                        appName,
                        true,
                        false,
                        System.currentTimeMillis()
                );
            }
        }

        return GameStateInfo.none();
    }

    /**
     * Scans the Android Task Manager (Recent Tasks) for an active background game task.
     * When an app is cleared or swiped away from recent tasks, it is immediately removed from this list.
     */
    private String getActiveGameFromTaskManager() {
        try {
            java.lang.Process process = ShellExecutor.exec(new String[]{"dumpsys", "activity", "recents"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                // Check live task activities: Activities=[ActivityRecord{... u0 <package>/... t<id>}]
                if (line.contains("Activities=[ActivityRecord{")) {
                    String pkg = extractPackageFromLine(line);
                    if (pkg != null && !systemExclusions.contains(pkg) && isGamePackage(pkg)) {
                        reader.close();
                        return pkg;
                    }
                }
                // Check task affinity: affinity=<uid>:<package>
                else if (line.contains("affinity=") && !line.contains("affinity=null")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        String pkg = line.substring(colon + 1).trim();
                        if (!systemExclusions.contains(pkg) && isGamePackage(pkg)) {
                            if (isPackageProcessAlive(pkg)) {
                                reader.close();
                                return pkg;
                            }
                        }
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            Log.d(TAG, "Error scanning task manager: " + e.getMessage());
        }
        return null;
    }

    /**
     * Queries dumpsys activity activities and dumpsys window displays for the active foreground package.
     */
    private String getAuthoritativeForegroundPackage() {
        // 1. Check dumpsys activity activities (topResumedActivity is at the very top)
        try {
            java.lang.Process process = ShellExecutor.exec(new String[]{"dumpsys", "activity", "activities"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            int linesRead = 0;
            while ((line = reader.readLine()) != null && linesRead < 50) {
                linesRead++;
                if (line.contains("topResumedActivity=") || line.contains("ResumedActivity:") || line.contains("mFocusedApp=")) {
                    String pkg = extractPackageFromLine(line);
                    if (pkg != null && !pkg.isEmpty()) {
                        reader.close();
                        return pkg;
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            Log.d(TAG, "Error checking dumpsys activity activities: " + e.getMessage());
        }

        // 2. Fallback: Check dumpsys window displays (mCurrentFocus / mFocusedApp)
        try {
            java.lang.Process process = ShellExecutor.exec(new String[]{"dumpsys", "window", "displays"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("mCurrentFocus=Window{") || line.contains("mFocusedApp=ActivityRecord{")) {
                    String pkg = extractPackageFromLine(line);
                    if (pkg != null && !pkg.isEmpty()) {
                        reader.close();
                        return pkg;
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            Log.d(TAG, "Error checking dumpsys window displays: " + e.getMessage());
        }

        return null;
    }

    /**
     * Strictly verifies whether an application's process is currently running on the device.
     */
    public boolean isPackageProcessAlive(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;

        // 1. With DUMP permission or Shizuku: check dumpsys activity processes for live *APP* record
        if (context.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED
                || com.gamestate.monitor.shizuku.ShizukuManager.isPermissionGranted()) {
            try {
                java.lang.Process process = ShellExecutor.exec(
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
                return isAlive;
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
     * Chronological UsageEvents parser.
     * If the most recent resumed app is a game, returns it.
     * If the game has been paused/stopped, or another app (launcher, recents) was resumed, returns null.
     */
    private String getActiveGameFromUsageEvents() {
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;

            long endTime = System.currentTimeMillis();
            long startTime = endTime - 60 * 1000; // 60 seconds

            UsageEvents events = usm.queryEvents(startTime, endTime);
            UsageEvents.Event event = new UsageEvents.Event();

            String currentResumedPkg = null;

            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                String pkg = event.getPackageName();
                if (pkg == null) continue;

                if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
                    currentResumedPkg = pkg;
                } else if (type == UsageEvents.Event.ACTIVITY_PAUSED || type == UsageEvents.Event.ACTIVITY_STOPPED) {
                    if (pkg.equals(currentResumedPkg)) {
                        currentResumedPkg = null;
                    }
                }
            }

            if (currentResumedPkg != null && !systemExclusions.contains(currentResumedPkg) && isGamePackage(currentResumedPkg)) {
                return currentResumedPkg;
            }
        } catch (Exception e) {
            Log.d(TAG, "Error querying UsageEvents: " + e.getMessage());
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
