package com.gamestate.monitor.shizuku;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/**
 * ShizukuManager
 * --------------
 * Bridges GameState Monitor with the on-device Shizuku ADB service.
 * Allows executing elevated shell operations and granting Android permissions
 * without needing a computer or physical USB cable.
 */
public class ShizukuManager {

    private static final String TAG = "ShizukuManager";
    public static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    public static final int SHIZUKU_REQUEST_CODE = 4401;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface PermissionGrantCallback {
        void onSuccess();
        void onFailure(String error);
    }

    /**
     * Checks if the Shizuku app is installed on this device.
     */
    public static boolean isShizukuInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks if the Shizuku service binder is currently running and alive.
     */
    public static boolean isShizukuRunning() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Checks if Shizuku has granted authorization to GameState Monitor.
     */
    public static boolean isPermissionGranted() {
        if (!isShizukuRunning()) {
            return false;
        }
        try {
            if (Shizuku.isPreV11()) {
                return false;
            }
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            Log.w(TAG, "Failed checking Shizuku permission: " + t.getMessage());
            return false;
        }
    }

    /**
     * Requests authorization from Shizuku.
     */
    public static void requestPermission(Activity activity, int requestCode) {
        if (!isShizukuRunning()) {
            Log.w(TAG, "Cannot request Shizuku permission: binder is not alive.");
            return;
        }
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error requesting Shizuku permission: " + t.getMessage(), t);
        }
    }

    private static java.lang.reflect.Method newProcessMethod = null;

    private static synchronized java.lang.Process invokeShizukuNewProcess(String[] cmd, String[] env, String dir) {
        try {
            if (newProcessMethod == null) {
                newProcessMethod = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                newProcessMethod.setAccessible(true);
            }
            return (java.lang.Process) newProcessMethod.invoke(null, (Object) cmd, (Object) env, (Object) dir);
        } catch (Throwable t) {
            Log.e(TAG, "Error invoking Shizuku.newProcess: " + t.getMessage(), t);
            return null;
        }
    }

    /**
     * Uses Shizuku's elevated shell identity to automatically grant DUMP and PACKAGE_USAGE_STATS
     * directly to GameState Monitor on-device.
     */
    public static void grantAppPrivileges(Context context, PermissionGrantCallback callback) {
        if (!isPermissionGranted()) {
            if (callback != null) {
                callback.onFailure("Shizuku is not authorized");
            }
            return;
        }

        final String packageName = context.getPackageName();
        executor.execute(() -> {
            try {
                // Grant android.permission.DUMP
                java.lang.Process p1 = invokeShizukuNewProcess(new String[]{"pm", "grant", packageName, "android.permission.DUMP"}, null, null);
                if (p1 != null) p1.waitFor();

                // Grant android.permission.PACKAGE_USAGE_STATS
                java.lang.Process p2 = invokeShizukuNewProcess(new String[]{"pm", "grant", packageName, "android.permission.PACKAGE_USAGE_STATS"}, null, null);
                if (p2 != null) p2.waitFor();

                Log.i(TAG, "Successfully granted DUMP and PACKAGE_USAGE_STATS via Shizuku!");
                if (callback != null) {
                    mainHandler.post(callback::onSuccess);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed granting permissions via Shizuku: " + e.getMessage(), e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onFailure(e.getMessage()));
                }
            }
        });
    }

    /**
     * Executes a command using Shizuku's remote process if available.
     * Returns null if Shizuku is not available or authorized.
     */
    public static java.lang.Process executeCommand(String[] command) {
        if (!isPermissionGranted()) {
            return null;
        }
        return invokeShizukuNewProcess(command, null, null);
    }

    /**
     * Creates an Intent to open the Shizuku app, or redirect to Play Store if not installed.
     */
    public static Intent getOpenShizukuIntent(Context context) {
        if (isShizukuInstalled(context)) {
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE);
            if (launchIntent != null) {
                return launchIntent;
            }
        }
        // Fallback to Play Store or browser
        Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + SHIZUKU_PACKAGE));
        marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return marketIntent;
    }
}
