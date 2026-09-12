package com.gamestate.monitor.fps;

import android.util.Log;

import com.gamestate.monitor.shizuku.ShizukuManager;

import java.io.IOException;

/**
 * ShellExecutor
 * -------------
 * Unified shell command execution layer.
 * Prioritizes:
 * 1. Shizuku remote process (wireless on-device ADB IPC)
 * 2. Direct Runtime.getRuntime().exec() (if DUMP permission is present)
 */
public class ShellExecutor {

    private static final String TAG = "ShellExecutor";

    /**
     * Executes the given command array and returns the running Process.
     */
    public static Process exec(String[] command) throws IOException {
        // Priority 1: Shizuku IPC execution if active
        if (ShizukuManager.isPermissionGranted()) {
            try {
                Process shizukuProc = ShizukuManager.executeCommand(command);
                if (shizukuProc != null) {
                    return shizukuProc;
                }
            } catch (Throwable t) {
                Log.w(TAG, "Shizuku exec failed, falling back to runtime exec: " + t.getMessage());
            }
        }

        // Priority 2: Standard Runtime exec
        return Runtime.getRuntime().exec(command);
    }
}
