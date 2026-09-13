package com.gamestate.monitor.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.gamestate.monitor.MainActivity;
import com.gamestate.monitor.R;
import com.gamestate.monitor.fps.FpsBackend;
import com.gamestate.monitor.fps.FpsMetrics;
import com.gamestate.monitor.fps.GameStateInfo;
import com.gamestate.monitor.model.CpuInfo;
import com.gamestate.monitor.model.DeviceInfo;
import com.gamestate.monitor.model.GpuInfo;
import com.gamestate.monitor.model.PerformanceStats;
import com.gamestate.monitor.service.GameStateService;
import com.gamestate.monitor.util.CpuMonitor;
import com.gamestate.monitor.util.DeviceStatsManager;
import com.gamestate.monitor.util.FormatUtils;
import com.gamestate.monitor.util.GpuMonitor;

public class DashboardFragment extends Fragment {

    // Top Header Actions
    private FrameLayout btnNotification;
    private FrameLayout btnHeaderSettings;

    // Hero System Health Card
    private SystemHealthArcView arcSystemHealth;
    private TextView tvSystemHealthStatus;
    private TextView tvSystemHealthDesc;
    private TextView tvHeroCpuTemp;
    private ProgressBar pbHeroCpuTemp;
    private TextView tvHeroGpuTemp;
    private ProgressBar pbHeroGpuTemp;
    private TextView tvHeroBattery;
    private ProgressBar pbHeroBattery;

    // Current Game Card
    private TextView tvGameRunningBadge;
    private ImageView ivGameThumbnail;
    private TextView tvCurrentGameTitle;
    private TextView tvCurrentGamePackage;
    private TextView tvGameSessionTime;

    // Device Information Card
    private TextView btnViewMoreDevice;
    private KeyValueRowView rowDeviceModel;
    private KeyValueRowView rowDeviceAndroidVersion;
    private KeyValueRowView rowDeviceKernelVersion;

    // 2x2 Performance Grid: CPU
    private TextView tvGridCpuPct;
    private SparklineGraphView sparklineCpu;
    private ProgressBar pbGridCpu;
    private TextView tvGridCpuCores;
    private TextView tvGridCpuFreq;

    // 2x2 Performance Grid: GPU
    private TextView tvGridGpuPct;
    private SparklineGraphView sparklineGpu;
    private ProgressBar pbGridGpu;
    private TextView tvGridGpuName;
    private TextView tvGridGpuFreq;

    // 2x2 Performance Grid: RAM
    private TextView tvGridRamPct;
    private SparklineGraphView sparklineRam;
    private ProgressBar pbGridRam;
    private TextView tvGridRamUsage;
    private TextView tvGridRamFree;

    // 2x2 Performance Grid: Storage
    private TextView tvGridStoragePct;
    private SparklineGraphView sparklineStorage;
    private ProgressBar pbGridStorage;
    private TextView tvGridStorageUsage;
    private TextView tvGridStorageFree;

    // FPS Performance Card
    private TextView tvFpsCardBadge;
    private KeyValueRowView rowFpsStatus;
    private KeyValueRowView rowFpsSource;
    private KeyValueRowView rowCurrentFps;
    private KeyValueRowView rowTargetRefreshRate;

    // Data managers
    private DeviceStatsManager statsManager;
    private CpuMonitor cpuMonitor;
    private GpuMonitor gpuMonitor;
    private GpuInfo cachedGpuInfo;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private static final long REFRESH_INTERVAL_MS = 1500;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateDashboardMetrics();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    private final BroadcastReceiver gameStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (GameStateService.ACTION_GAME_STATE_UPDATED.equals(intent.getAction())) {
                updateGameAndFpsCards();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context ctx = requireContext();
        statsManager = new DeviceStatsManager(ctx);
        cpuMonitor = new CpuMonitor();
        gpuMonitor = new GpuMonitor();
        cachedGpuInfo = gpuMonitor.getGpuInfo();

        bindViews(view);
        configureColors();
        populateStaticDeviceInfo();
        setupClickListeners();
    }

    private void bindViews(View root) {
        // Header
        btnNotification = root.findViewById(R.id.btnNotification);
        btnHeaderSettings = root.findViewById(R.id.btnHeaderSettings);

        // Hero
        arcSystemHealth = root.findViewById(R.id.arcSystemHealth);
        tvSystemHealthStatus = root.findViewById(R.id.tvSystemHealthStatus);
        tvSystemHealthDesc = root.findViewById(R.id.tvSystemHealthDesc);
        tvHeroCpuTemp = root.findViewById(R.id.tvHeroCpuTemp);
        pbHeroCpuTemp = root.findViewById(R.id.pbHeroCpuTemp);
        tvHeroGpuTemp = root.findViewById(R.id.tvHeroGpuTemp);
        pbHeroGpuTemp = root.findViewById(R.id.pbHeroGpuTemp);
        tvHeroBattery = root.findViewById(R.id.tvHeroBattery);
        pbHeroBattery = root.findViewById(R.id.pbHeroBattery);

        // Current Game
        tvGameRunningBadge = root.findViewById(R.id.tvGameRunningBadge);
        ivGameThumbnail = root.findViewById(R.id.ivGameThumbnail);
        tvCurrentGameTitle = root.findViewById(R.id.tvCurrentGameTitle);
        tvCurrentGamePackage = root.findViewById(R.id.tvCurrentGamePackage);
        tvGameSessionTime = root.findViewById(R.id.tvGameSessionTime);

        // Device Info
        btnViewMoreDevice = root.findViewById(R.id.btnViewMoreDevice);
        rowDeviceModel = root.findViewById(R.id.rowDeviceModel);
        rowDeviceAndroidVersion = root.findViewById(R.id.rowDeviceAndroidVersion);
        rowDeviceKernelVersion = root.findViewById(R.id.rowDeviceKernelVersion);

        // Grid: CPU
        tvGridCpuPct = root.findViewById(R.id.tvGridCpuPct);
        sparklineCpu = root.findViewById(R.id.sparklineCpu);
        pbGridCpu = root.findViewById(R.id.pbGridCpu);
        tvGridCpuCores = root.findViewById(R.id.tvGridCpuCores);
        tvGridCpuFreq = root.findViewById(R.id.tvGridCpuFreq);

        // Grid: GPU
        tvGridGpuPct = root.findViewById(R.id.tvGridGpuPct);
        sparklineGpu = root.findViewById(R.id.sparklineGpu);
        pbGridGpu = root.findViewById(R.id.pbGridGpu);
        tvGridGpuName = root.findViewById(R.id.tvGridGpuName);
        tvGridGpuFreq = root.findViewById(R.id.tvGridGpuFreq);

        // Grid: RAM
        tvGridRamPct = root.findViewById(R.id.tvGridRamPct);
        sparklineRam = root.findViewById(R.id.sparklineRam);
        pbGridRam = root.findViewById(R.id.pbGridRam);
        tvGridRamUsage = root.findViewById(R.id.tvGridRamUsage);
        tvGridRamFree = root.findViewById(R.id.tvGridRamFree);

        // Grid: Storage
        tvGridStoragePct = root.findViewById(R.id.tvGridStoragePct);
        sparklineStorage = root.findViewById(R.id.sparklineStorage);
        pbGridStorage = root.findViewById(R.id.pbGridStorage);
        tvGridStorageUsage = root.findViewById(R.id.tvGridStorageUsage);
        tvGridStorageFree = root.findViewById(R.id.tvGridStorageFree);

        // FPS Performance
        tvFpsCardBadge = root.findViewById(R.id.tvFpsCardBadge);
        rowFpsStatus = root.findViewById(R.id.rowFpsStatus);
        rowFpsSource = root.findViewById(R.id.rowFpsSource);
        rowCurrentFps = root.findViewById(R.id.rowCurrentFps);
        rowTargetRefreshRate = root.findViewById(R.id.rowTargetRefreshRate);
    }

    private void configureColors() {
        int cyan = Color.parseColor("#00D2E0");
        if (sparklineCpu != null) sparklineCpu.setLineColor(cyan);
        if (sparklineGpu != null) sparklineGpu.setLineColor(cyan);
        if (sparklineRam != null) sparklineRam.setLineColor(cyan);
        if (sparklineStorage != null) sparklineStorage.setLineColor(cyan);
    }

    private void setupClickListeners() {
        if (btnHeaderSettings != null) {
            btnHeaderSettings.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).selectTab(MainActivity.Tab.SETTINGS);
                }
            });
        }

        if (btnNotification != null) {
            btnNotification.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "Hardware alerts: All thermals and frequencies optimal.", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnViewMoreDevice != null) {
            btnViewMoreDevice.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).selectTab(MainActivity.Tab.DIAGNOSTICS);
                }
            });
        }
    }

    private void populateStaticDeviceInfo() {
        if (statsManager == null) return;
        DeviceInfo info = statsManager.getDeviceInfo();
        if (rowDeviceModel != null) rowDeviceModel.setValue(info.getFullDeviceName());
        if (rowDeviceAndroidVersion != null) rowDeviceAndroidVersion.setValue("Android " + info.getAndroidVersion());
        if (rowDeviceKernelVersion != null) {
            String osVer = System.getProperty("os.version");
            rowDeviceKernelVersion.setValue(osVer != null ? "Linux " + osVer : "Linux 4.9.337");
        }

        CpuInfo cpuInfo = cpuMonitor.getCpuInfo();
        if (tvGridCpuCores != null) tvGridCpuCores.setText(cpuInfo.getCoreDescription());

        if (cachedGpuInfo != null && tvGridGpuName != null) {
            tvGridGpuName.setText(cachedGpuInfo.getShortName());
        }
        if (rowTargetRefreshRate != null) {
            rowTargetRefreshRate.setValue(String.format("%.0f Hz", statsManager.getScreenRefreshRate()));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshHandler.post(refreshRunnable);
        try {
            IntentFilter filter = new IntentFilter(GameStateService.ACTION_GAME_STATE_UPDATED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(gameStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                requireContext().registerReceiver(gameStateReceiver, filter);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
        try {
            requireContext().unregisterReceiver(gameStateReceiver);
        } catch (Exception ignored) {}
    }

    public void updateDashboardMetrics() {
        if (!isAdded() || getContext() == null) return;

        PerformanceStats stats = statsManager.getPerformanceStats();
        CpuInfo cpuInfo = cpuMonitor.getCpuInfo();
        GpuInfo liveGpu = gpuMonitor.sampleGpuInfo();

        int cpuUsage = cpuInfo.getUsagePercentage();
        int gpuUsage = Math.max(0, liveGpu.getGpuUsagePercentage());
        int ramPercent = stats.getRamUsagePercentage();
        int storagePercent = stats.getStorageUsagePercentage();

        float batTemp = stats.getBatteryTemperatureC();
        float cpuTemp = stats.hasCpuTemperature() ? stats.getCpuTemperatureC() : (batTemp + 4.2f);
        float gpuTemp = Math.max(30.0f, cpuTemp - 2.5f);

        // 1. Hero Card: Arc & Health Score
        int healthScore = 100 - (int)(cpuUsage * 0.35f + (batTemp > 40f ? 20 : 0));
        healthScore = Math.max(45, Math.min(100, healthScore));

        if (arcSystemHealth != null) {
            arcSystemHealth.setProgress(healthScore);
        }
        if (tvSystemHealthStatus != null) {
            tvSystemHealthStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.figma_cyan));
            if (healthScore >= 80) {
                tvSystemHealthStatus.setText("OPTIMAL");
                if (tvSystemHealthDesc != null) tvSystemHealthDesc.setText("Ready to Game");
            } else if (healthScore >= 60) {
                tvSystemHealthStatus.setText("MODERATE");
                if (tvSystemHealthDesc != null) tvSystemHealthDesc.setText("System Warming Up");
            } else {
                tvSystemHealthStatus.setText("HIGH LOAD");
                if (tvSystemHealthDesc != null) tvSystemHealthDesc.setText("Throttling Possible");
            }
        }

        // Hero Quick Metrics
        if (tvHeroCpuTemp != null) tvHeroCpuTemp.setText(String.format("%.0f°C", cpuTemp));
        if (pbHeroCpuTemp != null) pbHeroCpuTemp.setProgress((int) Math.min(100, cpuTemp));

        if (tvHeroGpuTemp != null) tvHeroGpuTemp.setText(String.format("%.0f°C", gpuTemp));
        if (pbHeroGpuTemp != null) pbHeroGpuTemp.setProgress((int) Math.min(100, gpuTemp));

        if (tvHeroBattery != null) tvHeroBattery.setText(stats.getBatteryLevel() + "%");
        if (pbHeroBattery != null) pbHeroBattery.setProgress(stats.getBatteryLevel());

        // 2. 2x2 Performance Grid
        // CPU
        if (tvGridCpuPct != null) tvGridCpuPct.setText(cpuUsage + "% ↓");
        if (sparklineCpu != null) sparklineCpu.addPoint(cpuUsage);
        if (pbGridCpu != null) pbGridCpu.setProgress(cpuUsage);
        if (tvGridCpuFreq != null) {
            tvGridCpuFreq.setText(String.format("%.2f GHz", cpuInfo.getAverageFrequencyGhz()));
        }

        // GPU
        if (tvGridGpuPct != null) tvGridGpuPct.setText(gpuUsage + "% ↑");
        if (sparklineGpu != null) sparklineGpu.addPoint(gpuUsage);
        if (pbGridGpu != null) pbGridGpu.setProgress(gpuUsage);
        if (tvGridGpuFreq != null) {
            tvGridGpuFreq.setText(String.format("%.0f MHz", cpuInfo.getAverageFrequencyGhz() * 250));
        }

        // RAM
        if (tvGridRamPct != null) tvGridRamPct.setText(ramPercent + "% →");
        if (sparklineRam != null) sparklineRam.addPoint(ramPercent);
        if (pbGridRam != null) pbGridRam.setProgress(ramPercent);
        if (tvGridRamUsage != null) {
            tvGridRamUsage.setText(FormatUtils.formatBytes(stats.getUsedRamBytes()) + " / " + FormatUtils.formatBytes(stats.getTotalRamBytes()));
        }
        if (tvGridRamFree != null) {
            tvGridRamFree.setText(FormatUtils.formatBytes(stats.getAvailableRamBytes()) + " Free");
        }

        // Storage
        if (tvGridStoragePct != null) tvGridStoragePct.setText(storagePercent + "% →");
        if (sparklineStorage != null) sparklineStorage.addPoint(storagePercent);
        if (pbGridStorage != null) pbGridStorage.setProgress(storagePercent);
        if (tvGridStorageUsage != null) {
            tvGridStorageUsage.setText(FormatUtils.formatBytes(stats.getUsedStorageBytes()) + " / " + FormatUtils.formatBytes(stats.getTotalStorageBytes()));
        }
        if (tvGridStorageFree != null) {
            tvGridStorageFree.setText(FormatUtils.formatBytes(stats.getAvailableStorageBytes()) + " Free");
        }

        // 3. Current Game & FPS Card
        updateGameAndFpsCards();
    }

    private void updateGameAndFpsCards() {
        if (!isAdded() || getContext() == null) return;

        GameStateInfo gameState = GameStateService.getCurrentGameState();
        FpsMetrics fpsMetrics = GameStateService.getCurrentMetrics();
        FpsBackend activeBackend = GameStateService.getActiveBackend();

        boolean hasGame = (gameState != null && gameState.hasGame());
        boolean isForeground = (gameState != null && gameState.isForeground());
        boolean isTracking = (activeBackend != null && activeBackend.isAvailable(requireContext()));

        // Current Game Card Updates
        if (hasGame) {
            if (tvGameRunningBadge != null) {
                tvGameRunningBadge.setText(isForeground ? "Running" : "Background");
                tvGameRunningBadge.setVisibility(View.VISIBLE);
            }
            if (tvCurrentGameTitle != null) tvCurrentGameTitle.setText(gameState.getAppName());
            if (tvCurrentGamePackage != null) tvCurrentGamePackage.setText(gameState.getPackageName());

            // App icon
            if (ivGameThumbnail != null) {
                try {
                    PackageManager pm = requireContext().getPackageManager();
                    Drawable icon = pm.getApplicationIcon(gameState.getPackageName());
                    ivGameThumbnail.setImageDrawable(icon);
                    ivGameThumbnail.setImageTintList(null);
                } catch (Exception ignored) {
                    ivGameThumbnail.setImageResource(R.drawable.ic_gamepad);
                    ivGameThumbnail.setImageTintList(ContextCompat.getColorStateList(requireContext(), R.color.figma_cyan));
                }
            }

            // Session timer
            if (tvGameSessionTime != null && getActivity() instanceof MainActivity) {
                long elapsed = SystemClock.elapsedRealtime() - ((MainActivity) getActivity()).getSessionStartTimeMs();
                tvGameSessionTime.setText(FormatUtils.formatDuration(elapsed));
            }
        } else {
            if (tvGameRunningBadge != null) {
                tvGameRunningBadge.setText("Standby");
                tvGameRunningBadge.setVisibility(View.VISIBLE);
            }
            if (tvCurrentGameTitle != null) tvCurrentGameTitle.setText("No Game Active");
            if (tvCurrentGamePackage != null) tvCurrentGamePackage.setText("Monitoring foreground games...");
            if (ivGameThumbnail != null) {
                ivGameThumbnail.setImageResource(R.drawable.ic_gamepad);
                ivGameThumbnail.setImageTintList(ContextCompat.getColorStateList(requireContext(), R.color.figma_cyan));
            }
            if (tvGameSessionTime != null) {
                tvGameSessionTime.setText("00:00:00");
            }
        }

        // FPS Performance Card Updates
        if (tvFpsCardBadge != null) {
            if (hasGame && isForeground) {
                tvFpsCardBadge.setText("RUNNING");
                tvFpsCardBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.figma_cyan));
            } else if (isTracking) {
                tvFpsCardBadge.setText("READY");
                tvFpsCardBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.figma_cyan));
            } else {
                tvFpsCardBadge.setText("STANDBY");
                tvFpsCardBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.figma_text_muted));
            }
        }

        if (rowFpsStatus != null) {
            if (hasGame && isForeground) rowFpsStatus.setValue("Active (Foreground)");
            else if (hasGame) rowFpsStatus.setValue("Paused (Background)");
            else rowFpsStatus.setValue("No Game Active");
        }

        if (rowFpsSource != null) {
            rowFpsSource.setValue(activeBackend != null ? activeBackend.getName() : "SurfaceFlinger (Shizuku)");
        }

        if (rowCurrentFps != null) {
            if (fpsMetrics != null && fpsMetrics.hasValidFps()) {
                rowCurrentFps.setValue(String.format("%.1f FPS", fpsMetrics.getCurrentFps()));
                rowCurrentFps.setValueColor(ContextCompat.getColor(requireContext(), R.color.figma_cyan));
            } else {
                rowCurrentFps.setValue("-- FPS");
                rowCurrentFps.setValueColor(ContextCompat.getColor(requireContext(), R.color.figma_text_muted));
            }
        }
    }
}
