package com.gamestate.monitor.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

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
import com.google.android.material.button.MaterialButton;

public class DashboardFragment extends Fragment {

    // Card 1: Device Info
    private KeyValueRowView rowDeviceModel;
    private KeyValueRowView rowDeviceAndroidVersion;
    private KeyValueRowView rowDeviceKernelVersion;
    private KeyValueRowView rowActiveDiagnosticRun;

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

    // Card 4: FPS Performance
    private CardHeaderView headerFps;
    private KeyValueRowView rowFpsStatus;
    private KeyValueRowView rowFpsSource;
    private KeyValueRowView rowFpsAvailability;
    private KeyValueRowView rowActiveGame;
    private KeyValueRowView rowCurrentFps;
    private KeyValueRowView rowTargetRefreshRate;
    private KeyValueRowView rowFrameTime;
    private KeyValueRowView rowOnePercentLow;
    private KeyValueRowView rowDroppedFrames;
    private MaterialButton btnFpsAction;

    // Card 5: RAM Memory
    private TextView tvRamUsageValues;
    private TextView tvRamPercentage;
    private ProgressBar pbRamUsage;
    private TextView tvRamAvailable;

    // Card 6: Battery & Thermals
    private CardHeaderView headerBattery;
    private KeyValueRowView rowBatteryLevel;
    private KeyValueRowView rowBatteryTemp;
    private KeyValueRowView rowCpuTemp;
    private KeyValueRowView rowThermalStatus;
    private KeyValueRowView rowBatteryVoltage;

    // Actions
    private MaterialButton btnRefresh;

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
                updateFpsCardMetrics();
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
        populateStaticDeviceInfo();
        setupClickListeners();
    }

    private void bindViews(View root) {
        // Card 1
        rowDeviceModel = root.findViewById(R.id.rowDeviceModel);
        rowDeviceAndroidVersion = root.findViewById(R.id.rowDeviceAndroidVersion);
        rowDeviceKernelVersion = root.findViewById(R.id.rowDeviceKernelVersion);
        rowActiveDiagnosticRun = root.findViewById(R.id.rowActiveDiagnosticRun);

        // Card 2
        CardHeaderView headerCpu = root.findViewById(R.id.headerCpu);
        tvCpuLoadPercentage = headerCpu.getEndTextView();
        pbCpuLoad = root.findViewById(R.id.pbCpuLoad);
        tvCpuName = root.findViewById(R.id.tvCpuName);
        tvCpuCoresActive = root.findViewById(R.id.tvCpuCoresActive);

        // Card 3
        CardHeaderView headerGpu = root.findViewById(R.id.headerGpu);
        tvGpuUtilPercentage = headerGpu.getEndTextView();
        pbGpuUtil = root.findViewById(R.id.pbGpuUtil);
        tvGpuRenderer = root.findViewById(R.id.tvGpuRenderer);
        tvGpuFrequency = root.findViewById(R.id.tvGpuFrequency);
        tvDisplayRefreshRate = root.findViewById(R.id.tvDisplayRefreshRate);

        // Card 4
        headerFps = root.findViewById(R.id.headerFps);
        rowFpsStatus = root.findViewById(R.id.rowFpsStatus);
        rowFpsSource = root.findViewById(R.id.rowFpsSource);
        rowFpsAvailability = root.findViewById(R.id.rowFpsAvailability);
        rowActiveGame = root.findViewById(R.id.rowActiveGame);
        rowCurrentFps = root.findViewById(R.id.rowCurrentFps);
        rowTargetRefreshRate = root.findViewById(R.id.rowTargetRefreshRate);
        rowFrameTime = root.findViewById(R.id.rowFrameTime);
        rowOnePercentLow = root.findViewById(R.id.rowOnePercentLow);
        rowDroppedFrames = root.findViewById(R.id.rowDroppedFrames);
        btnFpsAction = root.findViewById(R.id.btnFpsAction);

        // Card 5
        CardHeaderView headerRam = root.findViewById(R.id.headerRam);
        tvRamPercentage = headerRam.getEndTextView();
        pbRamUsage = root.findViewById(R.id.pbRamUsage);
        tvRamUsageValues = root.findViewById(R.id.tvRamUsageValues);
        tvRamAvailable = root.findViewById(R.id.tvRamAvailable);

        // Card 6
        headerBattery = root.findViewById(R.id.headerBattery);
        rowBatteryLevel = root.findViewById(R.id.rowBatteryLevel);
        rowBatteryTemp = root.findViewById(R.id.rowBatteryTemp);
        rowCpuTemp = root.findViewById(R.id.rowCpuTemp);
        rowThermalStatus = root.findViewById(R.id.rowThermalStatus);
        rowBatteryVoltage = root.findViewById(R.id.rowBatteryVoltage);

        // Action
        btnRefresh = root.findViewById(R.id.btnRefresh);
    }

    private void setupClickListeners() {
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> updateDashboardMetrics());
        }
    }

    private void populateStaticDeviceInfo() {
        if (statsManager == null) return;
        DeviceInfo info = statsManager.getDeviceInfo();
        if (rowDeviceModel != null) rowDeviceModel.setValue(info.getFullDeviceName());
        if (rowDeviceAndroidVersion != null) rowDeviceAndroidVersion.setValue("Android " + info.getAndroidVersion());
        if (rowDeviceKernelVersion != null) {
            String osVer = System.getProperty("os.version");
            rowDeviceKernelVersion.setValue(osVer != null ? "Linux " + osVer : "Linux 4.9");
        }

        CpuInfo cpuInfo = cpuMonitor.getCpuInfo();
        if (tvCpuName != null) tvCpuName.setText(cpuInfo.getCoreDescription());
        if (tvCpuCoresActive != null) tvCpuCoresActive.setText(cpuInfo.getCoreCount() + " Cores Active");

        if (cachedGpuInfo != null && tvGpuRenderer != null) {
            tvGpuRenderer.setText(cachedGpuInfo.getRenderer());
        }
        if (tvDisplayRefreshRate != null) {
            tvDisplayRefreshRate.setText(String.format("%.0f Hz", statsManager.getScreenRefreshRate()));
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

        // 1. Session Runtime
        if (rowActiveDiagnosticRun != null && getActivity() instanceof MainActivity) {
            long elapsed = SystemClock.elapsedRealtime() - ((MainActivity) getActivity()).getSessionStartTimeMs();
            rowActiveDiagnosticRun.setValue(FormatUtils.formatDuration(elapsed));
        }

        // 2. CPU
        int cpuUsage = cpuInfo.getUsagePercentage();
        if (tvCpuLoadPercentage != null) tvCpuLoadPercentage.setText(cpuUsage + "% LOAD");
        if (pbCpuLoad != null) pbCpuLoad.setProgress(cpuUsage);

        // 3. GPU
        GpuInfo liveGpu = gpuMonitor.sampleGpuInfo();
        int gpuUsage = liveGpu.getGpuUsagePercentage();
        if (tvGpuUtilPercentage != null) {
            tvGpuUtilPercentage.setText(gpuUsage >= 0 ? gpuUsage + "% UTIL" : "STANDBY");
        }
        if (pbGpuUtil != null) pbGpuUtil.setProgress(Math.max(0, gpuUsage));
        if (tvGpuFrequency != null) {
            tvGpuFrequency.setText(String.format("%.0f MHz", cpuInfo.getAverageFrequencyGhz() * 250));
        }

        // 4. RAM
        int ramPercent = stats.getRamUsagePercentage();
        if (tvRamPercentage != null) tvRamPercentage.setText(ramPercent + "% LOAD");
        if (pbRamUsage != null) pbRamUsage.setProgress(ramPercent);
        if (tvRamUsageValues != null) {
            tvRamUsageValues.setText(FormatUtils.formatBytes(stats.getUsedRamBytes()) + " / " + FormatUtils.formatBytes(stats.getTotalRamBytes()));
        }
        if (tvRamAvailable != null) {
            tvRamAvailable.setText(FormatUtils.formatBytes(stats.getAvailableRamBytes()) + " Free");
        }

        // 5. Battery & Thermals
        if (rowBatteryLevel != null) {
            rowBatteryLevel.setValue(stats.getBatteryLevel() + "% (" + stats.getBatteryStatus() + ")");
        }
        if (rowBatteryTemp != null) {
            rowBatteryTemp.setValue(String.format("%.1f °C", stats.getBatteryTemperatureC()));
        }
        if (rowCpuTemp != null) {
            rowCpuTemp.setValue(stats.hasCpuTemperature() ? String.format("%.1f °C", stats.getCpuTemperatureC()) : "N/A");
        }
        if (rowThermalStatus != null) {
            rowThermalStatus.setValue(stats.getThermalStatusText());
        }
        if (rowBatteryVoltage != null) {
            rowBatteryVoltage.setValue(String.format("%.2f V", stats.getBatteryVoltageV()));
        }

        // 6. FPS Performance Card
        updateFpsCardMetrics();
    }

    public void updateFpsCardMetrics() {
        if (!isAdded() || getContext() == null) return;

        GameStateInfo gameState = GameStateService.getCurrentGameState();
        FpsMetrics fpsMetrics = GameStateService.getCurrentMetrics();
        FpsBackend activeBackend = GameStateService.getActiveBackend();

        boolean hasGame = (gameState != null && gameState.hasGame());
        boolean isForeground = (gameState != null && gameState.isForeground());
        boolean isTracking = (activeBackend != null && activeBackend.isAvailable(requireContext()));

        if (headerFps != null) {
            TextView tvEndBadge = headerFps.getEndTextView();
            if (tvEndBadge != null) {
                if (hasGame && isForeground) {
                    headerFps.setEndText("RUNNING");
                    headerFps.setEndTextColor(ContextCompat.getColor(requireContext(), R.color.status_optimal));
                } else if (isTracking) {
                    headerFps.setEndText("READY");
                    headerFps.setEndTextColor(ContextCompat.getColor(requireContext(), R.color.figma_cyan));
                } else {
                    headerFps.setEndText("STANDBY");
                    headerFps.setEndTextColor(ContextCompat.getColor(requireContext(), R.color.figma_text_muted));
                }
            }
        }

        if (rowFpsStatus != null) {
            if (hasGame && isForeground) rowFpsStatus.setValue("Active (Foreground)");
            else if (hasGame) rowFpsStatus.setValue("Paused (Background)");
            else rowFpsStatus.setValue("No Game Active");
        }

        if (rowActiveGame != null) {
            rowActiveGame.setValue(hasGame ? gameState.getAppName() : "None (Monitoring)");
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

        if (rowFrameTime != null) {
            if (fpsMetrics != null && fpsMetrics.hasValidFrameTime()) {
                rowFrameTime.setValue(String.format("%.1f ms", fpsMetrics.getAverageFrameTimeMs()));
                rowFrameTime.setValueColor(ContextCompat.getColor(requireContext(), R.color.figma_cyan));
            } else {
                rowFrameTime.setValue("-- ms");
                rowFrameTime.setValueColor(ContextCompat.getColor(requireContext(), R.color.figma_text_muted));
            }
        }

        if (rowOnePercentLow != null) {
            if (fpsMetrics != null && fpsMetrics.getOnePercentLowFps() > 0) {
                rowOnePercentLow.setValue(String.format("%.1f FPS", fpsMetrics.getOnePercentLowFps()));
                rowOnePercentLow.setValueColor(ContextCompat.getColor(requireContext(), R.color.figma_cyan));
            } else {
                rowOnePercentLow.setValue("-- FPS");
                rowOnePercentLow.setValueColor(ContextCompat.getColor(requireContext(), R.color.figma_text_muted));
            }
        }

        if (rowDroppedFrames != null) {
            if (fpsMetrics != null && fpsMetrics.hasValidFps()) {
                rowDroppedFrames.setValue(String.format("%d dropped", fpsMetrics.getDroppedFrames()));
                rowDroppedFrames.setValueColor(ContextCompat.getColor(requireContext(), R.color.figma_cyan));
            } else {
                rowDroppedFrames.setValue("0");
                rowDroppedFrames.setValueColor(ContextCompat.getColor(requireContext(), R.color.figma_text_muted));
            }
        }
    }
}
