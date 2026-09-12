package com.gamestate.monitor.fps;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.concurrent.Executors;

/**
 * RootUtils
 * ---------
 * Detects presence of root binaries and silently grants system permissions
 * (DUMP and PACKAGE_USAGE_STATS) on rooted devices such as Poco F1.
 */
public class RootUtils {

    private static final String TAG = "RootUtils";
    private static final String[] KNOWN_SU_PATHS = new String[]{
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su"
    };

    private static Boolean cachedRootAvailable = null;

    public static boolean isRootAvailable() {
        if (cachedRootAvailable != null) {
            return cachedRootAvailable;
        }
        for (String path : KNOWN_SU_PATHS) {
            if (new File(path).exists()) {
                cachedRootAvailable = true;
                return true;
            }
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(":")) {
                if (new File(dir, "su").exists()) {
                    cachedRootAvailable = true;
                    return true;
                }
            }
        }
        cachedRootAvailable = false;
        return false;
    }

    /**
     * Silently grants DUMP and PACKAGE_USAGE_STATS to GameState Monitor using root su.
     */
    public static void grantPrivilegesViaRoot(Context context) {
        if (!isRootAvailable()) return;

        final String packageName = context.getPackageName();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{
                        "su", "-c", "pm grant " + packageName + " android.permission.DUMP && pm grant " + packageName + " android.permission.PACKAGE_USAGE_STATS"
                });
                p.waitFor();
                Log.i(TAG, "Granted permissions via root successfully");
            } catch (Exception e) {
                Log.w(TAG, "Failed granting permissions via root: " + e.getMessage());
            }
        });
    }
}
