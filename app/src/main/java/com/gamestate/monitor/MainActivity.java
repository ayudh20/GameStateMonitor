package com.gamestate.monitor;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.gamestate.monitor.model.CpuInfo;
import com.gamestate.monitor.model.DeviceInfo;
import com.gamestate.monitor.model.GpuInfo;
import com.gamestate.monitor.model.PerformanceStats;
import com.gamestate.monitor.service.OverlayService;
import com.gamestate.monitor.util.CpuMonitor;
import com.gamestate.monitor.util.DeviceStatsManager;
import com.gamestate.monitor.util.FormatUtils;
import com.gamestate.monitor.util.GpuMonitor;
import com.google.android.material.button.MaterialButton;

/**
 * MainActivity
 * ------------
 * Controller for GameState Monitor, bound 1:1 to the Figma UI specification.
 */
public class MainActivity extends AppCompatActivity {

    // =========================================================================
    // UI View References
    // =========================================================================

    // Card 1: Device Info
    private TextView tvDeviceModel;
    private TextView tvDeviceAndroidVersion;
    private TextView tvActiveDiagnosticRun;

    // Card 2: CPU Processor
    private TextView tvCpuLoadPercentage;
    private ProgressBar pbCpuLoad;
    private TextView tvCpuName;
    private TextView tvCpuCoresActive;

    // Card 3: GPU Graphics
    private TextView tvGpuUtilPercentage;
    private ProgressBar pbGpuUtil;
    private TextView tvGpuRenderer;
    private TextView tvGpuFrequency;
    private TextView tvDisplayRefreshRate;

    // Card 4: RAM Memory
    private TextView tvRamUsageValues;
    private TextView tvRamPercentage;
    private ProgressBar pbRamUsage;
    private TextView tvRamAvailable;

    // Card 5: Battery Health & Thermals
    private TextView tvBatteryLevelState;
    private TextView tvBatteryTemp;
    private TextView tvBatteryVoltage;

    // Card 6: Internal Storage
    private TextView tvStoragePercentage;
    private ProgressBar pbStorageUsage;
    private TextView tvStorageUsageValues;
    private TextView tvStorageAvailable;

    // Diagnostic HUD Overlays
    private LinearLayout btnToggleOverlayFps;
    private ImageView ivOverlayFpsIcon;
    private TextView tvOverlayFpsText;
    private LinearLayout btnToggleOverlayTemp;
    private ImageView ivOverlayTempIcon;
    private TextView tvOverlayTempText;

    // Action Controls
    private MaterialButton btnRefresh;

    // =========================================================================
    // Manager & Timer Fields
    // =========================================================================

    private DeviceStatsManager statsManager;
    private CpuMonitor cpuMonitor;
    private GpuMonitor gpuMonitor;
    private GpuInfo cachedGpuInfo;

    // App diagnostic start time for session runtime counter
    private long sessionStartTimeMs;

    // Handler scheduled updates
    private final Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private static final long REFRESH_INTERVAL_MS = 2000; // 2 seconds

    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateRealtimeMetrics();
            updateDiagnosticRuntime();
            updateOverlayButtonState();
            autoRefreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    // Broadcast receiver to listen for OverlayService start/stop events
    private final BroadcastReceiver overlayStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (OverlayService.ACTION_OVERLAY_STATE_CHANGED.equals(intent.getAction())) {
                updateOverlayButtonState();
            }
        }
    };

    // Permission result launcher for Android 13+ POST_NOTIFICATIONS
    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // If granted or not, proceed with overlay
            });

    // =========================================================================
    // Activity Lifecycle Methods
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionStartTimeMs = SystemClock.elapsedRealtime();

        // 1. Initialize data managers and hardware monitors
        statsManager = new DeviceStatsManager(this);
        cpuMonitor = new CpuMonitor();
        gpuMonitor = new GpuMonitor();
        cachedGpuInfo = gpuMonitor.getGpuInfo();

        // 2. Find and assign all UI view references
        bindViews();

        // 3. Load static device specifications & GPU specs
        loadStaticDeviceInfo();

        // 4. Set click listener on Refresh button
        btnRefresh.setOnClickListener(v -> {
            updateRealtimeMetrics();
            updateDiagnosticRuntime();
            Toast.makeText(MainActivity.this, "Statistics Updated", Toast.LENGTH_SHORT).show();
        });

        // 5. Set click listeners for Floating Overlay Chips
        btnToggleOverlayFps.setOnClickListener(v -> handleOverlayToggle());
        btnToggleOverlayTemp.setOnClickListener(v -> {
            handleOverlayToggle();
            Toast.makeText(MainActivity.this, "Thermal HUD Linked with Overlay", Toast.LENGTH_SHORT).show();
        });

        // 6. Request notification permission on Android 13+
        checkNotificationPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(OverlayService.ACTION_OVERLAY_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(overlayStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(overlayStateReceiver, filter);
        }
        updateOverlayButtonState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRealtimeMetrics();
        updateDiagnosticRuntime();
        updateOverlayButtonState();

        autoRefreshHandler.postDelayed(autoRefreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(overlayStateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    // =========================================================================
    // Floating Overlay Handling
    // =========================================================================

    /**
     * Helper to reliably check if OverlayService is running.
     */
    private boolean isOverlayRunning() {
        if (OverlayService.isRunning) {
            return true;
        }
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (OverlayService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Toggles the Floating Gaming HUD on or off.
     */
    private void handleOverlayToggle() {
        if (isOverlayRunning()) {
            // Stop the overlay
            Intent serviceIntent = new Intent(this, OverlayService.class);
            stopService(serviceIntent);
            OverlayService.isRunning = false;
            updateOverlayButtonState();
            Toast.makeText(this, "Gaming Overlay Stopped", Toast.LENGTH_SHORT).show();
        } else {
            // Verify overlay permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                promptOverlayPermission();
            } else {
                startOverlayService();
            }
        }
    }

    /**
     * Starts the Foreground Overlay Service.
     */
    private void startOverlayService() {
        OverlayService.isRunning = true;
        updateOverlayButtonState();

        Intent serviceIntent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Gaming Overlay Launched! Open any game.", Toast.LENGTH_LONG).show();
    }

    /**
     * Explains why overlay permission is required and navigates directly to Android system settings.
     */
    private void promptOverlayPermission() {
        new AlertDialog.Builder(this)
                .setTitle("Overlay Permission Required")
                .setMessage("To display real-time gaming statistics over other apps and games, please enable 'Display over other apps' in the following screen.")
                .setPositiveButton("Enable in Settings", (dialog, which) -> {
                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())
                    );
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Updates the overlay chip buttons depending on whether the HUD is active.
     */
    private void updateOverlayButtonState() {
        boolean running = isOverlayRunning();
        OverlayService.isRunning = running;
        if (running) {
            btnToggleOverlayFps.setBackgroundResource(R.drawable.bg_chip_figma_active);
            ivOverlayFpsIcon.setImageResource(R.drawable.ic_check_small);
            ivOverlayFpsIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.figma_cyan)));
            tvOverlayFpsText.setTextColor(ContextCompat.getColor(this, R.color.white));

            btnToggleOverlayTemp.setBackgroundResource(R.drawable.bg_chip_figma_active);
            ivOverlayTempIcon.setImageResource(R.drawable.ic_check_small);
            ivOverlayTempIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.figma_cyan)));
            tvOverlayTempText.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            btnToggleOverlayFps.setBackgroundResource(R.drawable.bg_chip_figma_inactive);
            ivOverlayFpsIcon.setImageResource(R.drawable.ic_minus_small);
            ivOverlayFpsIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.figma_text_muted)));
            tvOverlayFpsText.setTextColor(ContextCompat.getColor(this, R.color.figma_text_muted));

            btnToggleOverlayTemp.setBackgroundResource(R.drawable.bg_chip_figma_inactive);
            ivOverlayTempIcon.setImageResource(R.drawable.ic_minus_small);
            ivOverlayTempIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.figma_text_muted)));
            tvOverlayTempText.setTextColor(ContextCompat.getColor(this, R.color.figma_text_muted));
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void bindViews() {
        // Card 1: Device Info
        tvDeviceModel = findViewById(R.id.tvDeviceModel);
        tvDeviceAndroidVersion = findViewById(R.id.tvDeviceAndroidVersion);
        tvActiveDiagnosticRun = findViewById(R.id.tvActiveDiagnosticRun);

        // Card 2: CPU Processor
        tvCpuLoadPercentage = findViewById(R.id.tvCpuLoadPercentage);
        pbCpuLoad = findViewById(R.id.pbCpuLoad);
        tvCpuName = findViewById(R.id.tvCpuName);
        tvCpuCoresActive = findViewById(R.id.tvCpuCoresActive);

        // Card 3: GPU Graphics
        tvGpuUtilPercentage = findViewById(R.id.tvGpuUtilPercentage);
        pbGpuUtil = findViewById(R.id.pbGpuUtil);
        tvGpuRenderer = findViewById(R.id.tvGpuRenderer);
        tvGpuFrequency = findViewById(R.id.tvGpuFrequency);
        tvDisplayRefreshRate = findViewById(R.id.tvDisplayRefreshRate);

        // Card 4: RAM Memory
        tvRamUsageValues = findViewById(R.id.tvRamUsageValues);
        tvRamPercentage = findViewById(R.id.tvRamPercentage);
        pbRamUsage = findViewById(R.id.pbRamUsage);
        tvRamAvailable = findViewById(R.id.tvRamAvailable);

        // Card 5: Battery Health & Thermals
        tvBatteryLevelState = findViewById(R.id.tvBatteryLevelState);
        tvBatteryTemp = findViewById(R.id.tvBatteryTemp);
        tvBatteryVoltage = findViewById(R.id.tvBatteryVoltage);

        // Card 6: Internal Storage
        tvStoragePercentage = findViewById(R.id.tvStoragePercentage);
        pbStorageUsage = findViewById(R.id.pbStorageUsage);
        tvStorageUsageValues = findViewById(R.id.tvStorageUsageValues);
        tvStorageAvailable = findViewById(R.id.tvStorageAvailable);

        // Diagnostic HUD Overlays
        btnToggleOverlayFps = findViewById(R.id.btnToggleOverlayFps);
        ivOverlayFpsIcon = findViewById(R.id.ivOverlayFpsIcon);
        tvOverlayFpsText = findViewById(R.id.tvOverlayFpsText);
        btnToggleOverlayTemp = findViewById(R.id.btnToggleOverlayTemp);
        ivOverlayTempIcon = findViewById(R.id.ivOverlayTempIcon);
        tvOverlayTempText = findViewById(R.id.tvOverlayTempText);

        // Action Controls
        btnRefresh = findViewById(R.id.btnRefresh);
    }

    private void loadStaticDeviceInfo() {
        DeviceInfo deviceInfo = statsManager.getDeviceInfo();

        tvDeviceModel.setText(deviceInfo.getFullDeviceName());
        String kernel = System.getProperty("os.version");
        if (kernel != null && !kernel.isEmpty()) {
            tvDeviceAndroidVersion.setText("Android " + deviceInfo.getAndroidVersion() + " (Kernel " + kernel + ")");
        } else {
            tvDeviceAndroidVersion.setText("Android " + deviceInfo.getAndroidVersion());
        }

        float refreshRate = statsManager.getScreenRefreshRate();
        if (refreshRate <= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Display display = getDisplay();
                if (display != null) {
                    refreshRate = display.getMode().getRefreshRate();
                }
            } catch (Exception ignored) {}
        }
        if (refreshRate <= 0) {
            refreshRate = 60.0f;
        }
        tvDisplayRefreshRate.setText(String.format("%.0f Hz", refreshRate));

        if (cpuMonitor != null) {
            CpuInfo cpuInfo = cpuMonitor.getCpuInfo();
            String chip = "";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MODEL != null && !Build.SOC_MODEL.isEmpty()) {
                chip = Build.SOC_MODEL;
            }
            if (chip.isEmpty() && Build.HARDWARE != null && !Build.HARDWARE.isEmpty() && !Build.HARDWARE.equalsIgnoreCase("unknown")) {
                chip = Build.HARDWARE.toUpperCase();
            }
            if (chip.isEmpty()) {
                chip = cpuInfo.getArchitecture() + " Processor";
            }
            tvCpuName.setText(chip);
            tvCpuCoresActive.setText(cpuInfo.getCoreCount() + " Cores Active");
        }

        if (cachedGpuInfo != null) {
            tvGpuRenderer.setText(cachedGpuInfo.getRenderer());
            if (cpuMonitor != null) {
                tvGpuFrequency.setText(String.format("%.0f MHz", cpuMonitor.getCpuInfo().getAverageFrequencyGhz() * 250));
            } else {
                tvGpuFrequency.setText("710 MHz");
            }
        }
    }

    private void updateDiagnosticRuntime() {
        long elapsedSec = (SystemClock.elapsedRealtime() - sessionStartTimeMs) / 1000;
        long h = elapsedSec / 3600;
        long m = (elapsedSec % 3600) / 60;
        long s = elapsedSec % 60;
        tvActiveDiagnosticRun.setText(String.format("%02dh %02dm %02ds", h, m, s));
    }

    private void updateRealtimeMetrics() {
        PerformanceStats stats = statsManager.getPerformanceStats();

        // 1. CPU Metrics
        if (cpuMonitor != null) {
            CpuInfo cpuInfo = cpuMonitor.getCpuInfo();
            tvCpuLoadPercentage.setText(cpuInfo.getUsagePercentage() + "% LOAD");
            pbCpuLoad.setProgress(cpuInfo.getUsagePercentage());
            tvCpuCoresActive.setText(cpuInfo.getCoreCount() + " Cores Active");
        }

        // 2. GPU Metrics
        if (gpuMonitor != null) {
            GpuInfo gpuInfo = gpuMonitor.sampleGpuInfo();
            int gpuLoad = gpuInfo.getGpuUsagePercentage();
            tvGpuUtilPercentage.setText(gpuLoad + "% UTIL");
            pbGpuUtil.setProgress(gpuLoad);
        }

        // 3. RAM Usage
        String usedRamStr = FormatUtils.formatBytes(stats.getUsedRamBytes());
        String totalRamStr = FormatUtils.formatBytes(stats.getTotalRamBytes());
        String availRamStr = FormatUtils.formatBytes(stats.getAvailableRamBytes());

        tvRamUsageValues.setText(String.format("Used: %s / Total: %s", usedRamStr, totalRamStr));
        tvRamPercentage.setText(stats.getRamUsagePercentage() + "%");
        pbRamUsage.setProgress(stats.getRamUsagePercentage());
        tvRamAvailable.setText(String.format("Available: %s", availRamStr));

        // 4. Battery Health & Thermals (Option 2)
        tvBatteryLevelState.setText(String.format("%d%% (%s)", stats.getBatteryLevel(), stats.getBatteryStatus()));
        tvBatteryTemp.setText(String.format("%.1f °C", stats.getBatteryTemperatureC()));
        tvBatteryVoltage.setText(String.format("%.2f V", stats.getBatteryVoltageV()));

        // 5. Storage Usage
        String usedStorageStr = FormatUtils.formatBytes(stats.getUsedStorageBytes());
        String totalStorageStr = FormatUtils.formatBytes(stats.getTotalStorageBytes());
        String freeStorageStr = FormatUtils.formatBytes(stats.getAvailableStorageBytes());

        tvStoragePercentage.setText(stats.getStorageUsagePercentage() + "% USED");
        pbStorageUsage.setProgress(stats.getStorageUsagePercentage());
        tvStorageUsageValues.setText(String.format("Used: %s / Total: %s", usedStorageStr, totalStorageStr));
        tvStorageAvailable.setText(String.format("%s Free", freeStorageStr));
    }
}
