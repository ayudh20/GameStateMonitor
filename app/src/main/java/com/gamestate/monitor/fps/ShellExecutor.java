package com.gamestate.monitor.fps;

import android.util.Log;

import com.gamestate.monitor.shizuku.ShizukuManager;

import java.io.IOException;

/**
 * ShellExecutor
 * -------------
 * Unified shell command execution layer.
 * Prioritizes:
 * 1. Shizuku remote process (if Shizuku is running and authorized)
 * 2. Direct Runtime.getRuntime().exec() (if DUMP permission or shell privileges are present)
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

        // Priority 2: Root execution via su if rooted
        if (RootUtils.isRootAvailable()) {
            try {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < command.length; i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(escapeShellArg(command[i]));
                }
                return Runtime.getRuntime().exec(new String[]{"su", "-c", sb.toString()});
            } catch (Throwable t) {
                Log.w(TAG, "Root su exec failed: " + t.getMessage());
            }
        }

        // Priority 3: Standard Runtime exec
        return Runtime.getRuntime().exec(command);
    }

    private static String escapeShellArg(String arg) {
        if (arg == null) return "''";
        return "'" + arg.replace("'", "'\\''") + "'";
    }
}
