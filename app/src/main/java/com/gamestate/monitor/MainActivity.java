package com.gamestate.monitor;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
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
 * In Android, an Activity represents a single focused screen that a user interacts with.
 *
 * This Activity serves as the Controller in the MVC architecture:
 * 1. Inflates the View defined in activity_main.xml.
 * 2. Queries Model data through DeviceStatsManager, CpuMonitor, and GpuMonitor.
 * 3. Binds and displays the statistics onto the UI widgets.
 * 4. Maintains an auto-refresh timer loop using Android's Handler mechanism.
 * 5. Controls launching and stopping the Floating Gaming HUD Overlay.
 */
public class MainActivity extends AppCompatActivity {

    // =========================================================================
    // UI View References
    // =========================================================================

    // Header & Status
    private View statusIndicatorDot;
    private TextView tvSystemStatus;
    private TextView tvLastUpdated;

    // Device Info Card
    private TextView tvDeviceModel;
    private TextView tvAndroidVersion;
    private TextView tvApiLevel;

    // CPU Card
    private TextView tvCpuUsage;
    private TextView tvCpuCoresAndFreq;
    private ProgressBar pbCpuUsage;

    // GPU & Display Card
    private TextView tvGpuUsage;
    private TextView tvGpuUsageNote;
    private ProgressBar pbGpuUsage;
    private TextView tvGpuRenderer;
    private TextView tvGpuVendor;
    private TextView tvGpuOpengl;
    private TextView tvRefreshRate;

    // RAM Card
    private TextView tvRamDetails;
    private TextView tvRamPercentage;
    private ProgressBar pbRamUsage;
    private TextView tvRamAvailable;

    // Battery & Thermals Card
    private TextView tvBatteryStatus;
    private TextView tvBatteryLevel;
    private ProgressBar pbBatteryLevel;
    private TextView tvBatteryTemp;

    // Storage Card
    private TextView tvStorageDetails;
    private TextView tvStoragePercentage;
    private ProgressBar pbStorageUsage;
    private TextView tvStorageFree;

    // Floating Overlay Controls
    private TextView tvOverlayBadgeStatus;
    private MaterialButton btnToggleOverlay;

    // Action Controls
    private MaterialButton btnRefresh;

    // =========================================================================
    // Manager & Timer Fields
    // =========================================================================

    private DeviceStatsManager statsManager;
    private CpuMonitor cpuMonitor;
    private GpuMonitor gpuMonitor;
    private GpuInfo cachedGpuInfo;

    // Handler scheduled updates
    private final Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private static final long REFRESH_INTERVAL_MS = 3000; // 3 seconds

    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateRealtimeMetrics();
            autoRefreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
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
            Toast.makeText(MainActivity.this, "Statistics Updated", Toast.LENGTH_SHORT).show();
        });

        // 5. Set click listener for Floating Overlay Toggle
        btnToggleOverlay.setOnClickListener(v -> handleOverlayToggle());

        // 6. Request notification permission on Android 13+
        checkNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRealtimeMetrics();
        updateOverlayButtonState();

        autoRefreshHandler.postDelayed(autoRefreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    // =========================================================================
    // Floating Overlay Handling
    // =========================================================================

    /**
     * Toggles the Floating Gaming HUD on or off.
     */
    private void handleOverlayToggle() {
        if (OverlayService.isRunning) {
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
        Intent serviceIntent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        updateOverlayButtonState();
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
        if (OverlayService.isRunning) {
            btnToggleOverlay.setText("Stop Gaming Overlay");
            btnToggleOverlay.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.status_high_load)
            ));
            tvOverlayBadgeStatus.setText("ACTIVE (FLOATING)");
            tvOverlayBadgeStatus.setTextColor(ContextCompat.getColor(this, R.color.primary_neon));
        } else {
            btnToggleOverlay.setText("Launch Gaming Overlay");
            btnToggleOverlay.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.primary_neon)
            ));
            tvOverlayBadgeStatus.setText("INACTIVE");
            tvOverlayBadgeStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
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
        statusIndicatorDot = findViewById(R.id.statusIndicatorDot);
        tvSystemStatus = findViewById(R.id.tvSystemStatus);
        tvLastUpdated = findViewById(R.id.tvLastUpdated);

        tvDeviceModel = findViewById(R.id.tvDeviceModel);
        tvAndroidVersion = findViewById(R.id.tvAndroidVersion);
        tvApiLevel = findViewById(R.id.tvApiLevel);

        tvCpuUsage = findViewById(R.id.tvCpuUsage);
        tvCpuCoresAndFreq = findViewById(R.id.tvCpuCoresAndFreq);
        pbCpuUsage = findViewById(R.id.pbCpuUsage);

        tvGpuUsage = findViewById(R.id.tvGpuUsage);
        tvGpuUsageNote = findViewById(R.id.tvGpuUsageNote);
        pbGpuUsage = findViewById(R.id.pbGpuUsage);
        tvGpuRenderer = findViewById(R.id.tvGpuRenderer);
        tvGpuVendor = findViewById(R.id.tvGpuVendor);
        tvGpuOpengl = findViewById(R.id.tvGpuOpengl);
        tvRefreshRate = findViewById(R.id.tvRefreshRate);

        tvRamDetails = findViewById(R.id.tvRamDetails);
        tvRamPercentage = findViewById(R.id.tvRamPercentage);
        pbRamUsage = findViewById(R.id.pbRamUsage);
        tvRamAvailable = findViewById(R.id.tvRamAvailable);

        tvBatteryStatus = findViewById(R.id.tvBatteryStatus);
        tvBatteryLevel = findViewById(R.id.tvBatteryLevel);
        pbBatteryLevel = findViewById(R.id.pbBatteryLevel);
        tvBatteryTemp = findViewById(R.id.tvBatteryTemp);

        tvStorageDetails = findViewById(R.id.tvStorageDetails);
        tvStoragePercentage = findViewById(R.id.tvStoragePercentage);
        pbStorageUsage = findViewById(R.id.pbStorageUsage);
        tvStorageFree = findViewById(R.id.tvStorageFree);

        tvOverlayBadgeStatus = findViewById(R.id.tvOverlayBadgeStatus);
        btnToggleOverlay = findViewById(R.id.btnToggleOverlay);

        btnRefresh = findViewById(R.id.btnRefresh);
    }

    private void loadStaticDeviceInfo() {
        DeviceInfo deviceInfo = statsManager.getDeviceInfo();

        tvDeviceModel.setText(deviceInfo.getFullDeviceName());
        tvAndroidVersion.setText("Android " + deviceInfo.getAndroidVersion());
        tvApiLevel.setText("API " + deviceInfo.getApiLevel());
        tvRefreshRate.setText(FormatUtils.formatRefreshRate(deviceInfo.getRefreshRate()));

        if (cachedGpuInfo != null) {
            tvGpuRenderer.setText(cachedGpuInfo.getRenderer());
            tvGpuVendor.setText(cachedGpuInfo.getVendor());
            tvGpuOpengl.setText(cachedGpuInfo.getOpenglVersion());
        }
    }

    private void updateRealtimeMetrics() {
        PerformanceStats stats = statsManager.getPerformanceStats();

        // 1. CPU Metrics
        if (cpuMonitor != null) {
            CpuInfo cpuInfo = cpuMonitor.getCpuInfo();
            tvCpuUsage.setText(cpuInfo.getUsagePercentage() + "%");
            pbCpuUsage.setProgress(cpuInfo.getUsagePercentage());
            tvCpuCoresAndFreq.setText(String.format("%d Cores @ %.2f GHz",
                    cpuInfo.getCoreCount(), cpuInfo.getAverageFrequencyGhz()));
        }

        // 2. GPU Metrics
        if (gpuMonitor != null) {
            GpuInfo gpuInfo = gpuMonitor.sampleGpuInfo();
            int gpuLoad = gpuInfo.getGpuUsagePercentage();
            tvGpuUsage.setText(gpuLoad + "%");
            pbGpuUsage.setProgress(gpuLoad);
            tvGpuUsageNote.setText(String.format("Graphics Engine Active (%s)", gpuInfo.getVendor()));
        }

        // 3. RAM Usage
        String usedRamStr = FormatUtils.formatBytes(stats.getUsedRamBytes());
        String totalRamStr = FormatUtils.formatBytes(stats.getTotalRamBytes());
        String availRamStr = FormatUtils.formatBytes(stats.getAvailableRamBytes());

        tvRamDetails.setText(String.format("Used: %s / Total: %s", usedRamStr, totalRamStr));
        tvRamPercentage.setText(stats.getRamUsagePercentage() + "%");
        pbRamUsage.setProgress(stats.getRamUsagePercentage());
        tvRamAvailable.setText(String.format("Available: %s", availRamStr));

        // 3. Battery & Thermals
        tvBatteryStatus.setText("Status: " + stats.getBatteryStatus());
        tvBatteryLevel.setText(stats.getBatteryLevel() + "%");
        pbBatteryLevel.setProgress(stats.getBatteryLevel());
        tvBatteryTemp.setText(FormatUtils.formatTemperature(stats.getBatteryTemperatureC()));

        // 4. Storage Usage
        String usedStorageStr = FormatUtils.formatBytes(stats.getUsedStorageBytes());
        String totalStorageStr = FormatUtils.formatBytes(stats.getTotalStorageBytes());
        String freeStorageStr = FormatUtils.formatBytes(stats.getAvailableStorageBytes());

        tvStorageDetails.setText(String.format("Used: %s / Total: %s", usedStorageStr, totalStorageStr));
        tvStoragePercentage.setText(stats.getStorageUsagePercentage() + "%");
        pbStorageUsage.setProgress(stats.getStorageUsagePercentage());
        tvStorageFree.setText(String.format("Free Space: %s", freeStorageStr));

        // 5. Timestamp
        tvLastUpdated.setText("Last Updated: " + stats.getFormattedTimestamp());

        // 6. System Status Indicator Evaluation
        applySystemStatusUI(stats.getSystemStatus());
    }

    private void applySystemStatusUI(PerformanceStats.SystemStatus status) {
        int colorRes;
        String statusText;

        switch (status) {
            case HIGH_LOAD:
                colorRes = ContextCompat.getColor(this, R.color.status_high_load);
                statusText = getString(R.string.status_high_load);
                break;
            case MODERATE:
                colorRes = ContextCompat.getColor(this, R.color.status_moderate);
                statusText = getString(R.string.status_moderate);
                break;
            case OPTIMAL:
            default:
                colorRes = ContextCompat.getColor(this, R.color.status_optimal);
                statusText = getString(R.string.status_optimal);
                break;
        }

        tvSystemStatus.setText(statusText);
        tvSystemStatus.setTextColor(colorRes);
        statusIndicatorDot.setBackgroundTintList(ColorStateList.valueOf(colorRes));
    }
}
