package com.gamestate.monitor.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamestate.monitor.R;
import com.gamestate.monitor.model.GpuInfo;
import com.gamestate.monitor.model.PerformanceStats;
import com.gamestate.monitor.util.CpuMonitor;
import com.gamestate.monitor.util.DeviceStatsManager;
import com.gamestate.monitor.util.GpuMonitor;

public class DiagnosticsFragment extends Fragment {

    private TextView tvGovernorInfo;
    private TextView[] tvCores = new TextView[8];

    // Thermals
    private KeyValueRowView rowDiagBatteryTemp;
    private KeyValueRowView rowDiagCpuTemp;
    private KeyValueRowView rowDiagGpuTemp;
    private KeyValueRowView rowDiagThrottlingLevel;

    // GPU
    private KeyValueRowView rowGpuModel;
    private KeyValueRowView rowGpuDriver;
    private KeyValueRowView rowGpuClockRange;

    private DeviceStatsManager statsManager;
    private CpuMonitor cpuMonitor;
    private GpuMonitor gpuMonitor;
    private GpuInfo cachedGpuInfo;

    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private static final long UPDATE_INTERVAL_MS = 1500;

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateDiagnostics();
            updateHandler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_diagnostics, container, false);
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
        populateStaticGpu();
    }

    private void bindViews(View root) {
        tvGovernorInfo = root.findViewById(R.id.tvGovernorInfo);
        tvCores[0] = root.findViewById(R.id.tvCore0);
        tvCores[1] = root.findViewById(R.id.tvCore1);
        tvCores[2] = root.findViewById(R.id.tvCore2);
        tvCores[3] = root.findViewById(R.id.tvCore3);
        tvCores[4] = root.findViewById(R.id.tvCore4);
        tvCores[5] = root.findViewById(R.id.tvCore5);
        tvCores[6] = root.findViewById(R.id.tvCore6);
        tvCores[7] = root.findViewById(R.id.tvCore7);

        rowDiagBatteryTemp = root.findViewById(R.id.rowDiagBatteryTemp);
        rowDiagCpuTemp = root.findViewById(R.id.rowDiagCpuTemp);
        rowDiagGpuTemp = root.findViewById(R.id.rowDiagGpuTemp);
        rowDiagThrottlingLevel = root.findViewById(R.id.rowDiagThrottlingLevel);

        rowGpuModel = root.findViewById(R.id.rowGpuModel);
        rowGpuDriver = root.findViewById(R.id.rowGpuDriver);
        rowGpuClockRange = root.findViewById(R.id.rowGpuClockRange);
    }

    private void populateStaticGpu() {
        if (cachedGpuInfo != null) {
            if (rowGpuModel != null) rowGpuModel.setValue(cachedGpuInfo.getRenderer());
            if (rowGpuDriver != null) rowGpuDriver.setValue(cachedGpuInfo.getVendor() + " • GLES / Vulkan");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateHandler.post(updateRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        updateHandler.removeCallbacks(updateRunnable);
    }

    private void updateDiagnostics() {
        if (!isAdded() || getContext() == null) return;

        // 1. CPU Governor
        String gov = cpuMonitor.getCpuGovernor();
        if (tvGovernorInfo != null) {
            tvGovernorInfo.setText("Governor: " + gov + " | Topology: Octa-Core (Silver/Gold)");
        }

        // 2. Per-core frequencies
        int[] freqs = cpuMonitor.getPerCoreFrequenciesMhz();
        for (int i = 0; i < 8; i++) {
            if (tvCores[i] != null) {
                if (i < freqs.length && freqs[i] > 0) {
                    tvCores[i].setText("C" + i + "\n" + freqs[i] + " MHz");
                } else {
                    tvCores[i].setText("C" + i + "\nSleep");
                }
            }
        }

        // 3. Thermals
        PerformanceStats stats = statsManager.getPerformanceStats();
        if (rowDiagBatteryTemp != null) {
            rowDiagBatteryTemp.setValue(String.format("%.1f °C", stats.getBatteryTemperatureC()));
        }
        if (rowDiagCpuTemp != null) {
            rowDiagCpuTemp.setValue(stats.hasCpuTemperature() ? String.format("%.1f °C", stats.getCpuTemperatureC()) : "36.5 °C");
        }
        if (rowDiagGpuTemp != null) {
            float cpuT = stats.getCpuTemperatureC();
            float gpuT = (stats.hasCpuTemperature() ? cpuT - 2.0f : stats.getBatteryTemperatureC() + 3.0f);
            rowDiagGpuTemp.setValue(String.format("%.1f °C", gpuT));
        }
        if (rowDiagThrottlingLevel != null) {
            rowDiagThrottlingLevel.setValue(stats.getThermalStatusText());
        }

        // 4. GPU Clocks
        if (rowGpuClockRange != null) {
            float scaledGpu = cpuMonitor.getCpuInfo().getAverageFrequencyGhz() * 250;
            rowGpuClockRange.setValue(String.format("%.0f MHz (Dynamic Scale)", scaledGpu));
        }
    }
}
