package com.gamestate.monitor;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import com.gamestate.monitor.ui.CardHeaderView;
import com.gamestate.monitor.ui.KeyValueRowView;
import com.gamestate.monitor.util.CpuMonitor;
import com.gamestate.monitor.util.DeviceStatsManager;
import com.gamestate.monitor.util.FormatUtils;
import com.gamestate.monitor.util.GpuMonitor;
import com.google.android.material.button.MaterialButton;

/**
 * MainActivity
 * ------------
 * Controller for GameState Monitor, bound 1:1 to the Figma UI specification with
 * the restored Floating Gaming HUD card in Cyan.
 */
public class MainActivity extends AppCompatActivity {

    // =========================================================================
    // UI View References
    // =========================================================================

    // Card 1: Device Info
    private TextView tvDeviceModel;
    private TextView tvDeviceAndroidVersion;
    private TextView tvDeviceKernelVersion;
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
    private TextView tvCpuTemp;
    private TextView tvThermalStatus;
    private TextView tvBatteryVoltage;

    // Card 6: Internal Storage
    private TextView tvStoragePercentage;
    private ProgressBar pbStorageUsage;
    private TextView tvStorageUsageValues;
    private TextView tvStorageAvailable;

    // Card 7: Floating Gaming Overlay Controls
    private TextView tvOverlayBadgeStatus;
    private MaterialButton btnToggleOverlay;
    private MaterialButton btnCopyAdbCommand;

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

        // 5. Set click listener for Floating Overlay Toggle & ADB Command
        btnToggleOverlay.setOnClickListener(v -> handleOverlayToggle());
        btnCopyAdbCommand.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("ADB Command", "adb shell pm grant " + getPackageName() + " android.permission.DUMP");
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, "ADB command copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
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
     * Updates the button label and status badge depending on whether the overlay is running.
     */
    private void updateOverlayButtonState() {
        boolean running = isOverlayRunning();
        OverlayService.isRunning = running;
        if (running) {
            btnToggleOverlay.setText("Stop Gaming Overlay");
            btnToggleOverlay.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.status_high_load)
            ));
            btnToggleOverlay.setTextColor(ContextCompat.getColor(this, R.color.white));
            btnToggleOverlay.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white)));
            tvOverlayBadgeStatus.setText("ACTIVE (FLOATING)");
            tvOverlayBadgeStatus.setTextColor(ContextCompat.getColor(this, R.color.figma_cyan));
        } else {
            btnToggleOverlay.setText("Launch Gaming Overlay");
            btnToggleOverlay.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.figma_cyan)
            ));
            btnToggleOverlay.setTextColor(ContextCompat.getColor(this, R.color.black));
            btnToggleOverlay.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.black)));
            tvOverlayBadgeStatus.setText("INACTIVE");
            tvOverlayBadgeStatus.setTextColor(ContextCompat.getColor(this, R.color.figma_text_muted));
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
        KeyValueRowView rowDeviceModel = findViewById(R.id.rowDeviceModel);
        KeyValueRowView rowDeviceAndroidVersion = findViewById(R.id.rowDeviceAndroidVersion);
        KeyValueRowView rowDeviceKernelVersion = findViewById(R.id.rowDeviceKernelVersion);
        KeyValueRowView rowActiveDiagnosticRun = findViewById(R.id.rowActiveDiagnosticRun);

        tvDeviceModel = rowDeviceModel.getValueTextView();
        tvDeviceAndroidVersion = rowDeviceAndroidVersion.getValueTextView();
        tvDeviceKernelVersion = rowDeviceKernelVersion.getValueTextView();
        tvActiveDiagnosticRun = rowActiveDiagnosticRun.getValueTextView();

        // Card 2: CPU Processor
        CardHeaderView headerCpu = findViewById(R.id.headerCpu);
        tvCpuLoadPercentage = headerCpu.getEndTextView();
        pbCpuLoad = findViewById(R.id.pbCpuLoad);
        tvCpuName = findViewById(R.id.tvCpuName);
        tvCpuCoresActive = findViewById(R.id.tvCpuCoresActive);

        // Card 3: GPU Graphics
        CardHeaderView headerGpu = findViewById(R.id.headerGpu);
        tvGpuUtilPercentage = headerGpu.getEndTextView();
        pbGpuUtil = findViewById(R.id.pbGpuUtil);
        tvGpuRenderer = findViewById(R.id.tvGpuRenderer);
        tvGpuFrequency = findViewById(R.id.tvGpuFrequency);
        KeyValueRowView rowDisplayRefreshRate = findViewById(R.id.rowDisplayRefreshRate);
        tvDisplayRefreshRate = rowDisplayRefreshRate.getValueTextView();

        // Card 4: RAM Memory
        tvRamUsageValues = findViewById(R.id.tvRamUsageValues);
        tvRamPercentage = findViewById(R.id.tvRamPercentage);
        pbRamUsage = findViewById(R.id.pbRamUsage);
        tvRamAvailable = findViewById(R.id.tvRamAvailable);

        // Card 5: Battery Health & Thermals
        KeyValueRowView rowBatteryLevel = findViewById(R.id.rowBatteryLevel);
        KeyValueRowView rowBatteryTemp = findViewById(R.id.rowBatteryTemp);
        KeyValueRowView rowCpuTemp = findViewById(R.id.rowCpuTemp);
        KeyValueRowView rowThermalStatus = findViewById(R.id.rowThermalStatus);
        KeyValueRowView rowBatteryVoltage = findViewById(R.id.rowBatteryVoltage);

        tvBatteryLevelState = rowBatteryLevel.getValueTextView();
        tvBatteryTemp = rowBatteryTemp.getValueTextView();
        tvCpuTemp = rowCpuTemp.getValueTextView();
        tvThermalStatus = rowThermalStatus.getValueTextView();
        tvBatteryVoltage = rowBatteryVoltage.getValueTextView();

        // Card 6: Internal Storage
        CardHeaderView headerStorage = findViewById(R.id.headerStorage);
        tvStoragePercentage = headerStorage.getEndTextView();
        pbStorageUsage = findViewById(R.id.pbStorageUsage);
        tvStorageUsageValues = findViewById(R.id.tvStorageUsageValues);
        tvStorageAvailable = findViewById(R.id.tvStorageAvailable);

        // Card 7: Floating Gaming Overlay
        CardHeaderView headerOverlay = findViewById(R.id.headerOverlay);
        tvOverlayBadgeStatus = headerOverlay.getEndTextView();
        btnToggleOverlay = findViewById(R.id.btnToggleOverlay);
        btnCopyAdbCommand = findViewById(R.id.btnCopyAdbCommand);

        // Action Controls
        btnRefresh = findViewById(R.id.btnRefresh);
    }

    private void loadStaticDeviceInfo() {
        DeviceInfo deviceInfo = statsManager.getDeviceInfo();

        tvDeviceModel.setText(deviceInfo.getFullDeviceName());
        tvDeviceAndroidVersion.setText("Android " + deviceInfo.getAndroidVersion());

        String kernel = System.getProperty("os.version");
        if (kernel != null && !kernel.isEmpty()) {
            tvDeviceKernelVersion.setText(kernel);
        } else {
            tvDeviceKernelVersion.setText("Linux (Unknown)");
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

        // 4. Battery Health & Thermals
        tvBatteryLevelState.setText(String.format("%d%% (%s)", stats.getBatteryLevel(), stats.getBatteryStatus()));
        tvBatteryTemp.setText(String.format("%.1f °C", stats.getBatteryTemperatureC()));

        // CPU Temperature (if available)
        if (stats.hasCpuTemperature()) {
            tvCpuTemp.setText(String.format("%.1f °C", stats.getCpuTemperatureC()));
        } else {
            tvCpuTemp.setText("N/A");
        }

        // Thermal Status (Normal, Warm, Hot, Critical) with color coding
        PerformanceStats.ThermalStatus thermalStatus = stats.getThermalStatus();
        tvThermalStatus.setText(stats.getThermalStatusText());
        int thermalColor;
        switch (thermalStatus) {
            case WARM:
                thermalColor = ContextCompat.getColor(this, R.color.thermal_warm);
                break;
            case HOT:
                thermalColor = ContextCompat.getColor(this, R.color.thermal_hot);
                break;
            case CRITICAL:
                thermalColor = ContextCompat.getColor(this, R.color.thermal_critical);
                break;
            case NORMAL:
            default:
                thermalColor = ContextCompat.getColor(this, R.color.thermal_normal);
                break;
        }
        tvThermalStatus.setTextColor(thermalColor);

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
