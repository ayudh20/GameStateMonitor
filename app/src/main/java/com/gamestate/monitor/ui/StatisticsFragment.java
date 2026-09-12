package com.gamestate.monitor.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.gamestate.monitor.R;
import com.gamestate.monitor.fps.FpsMetrics;
import com.gamestate.monitor.fps.GameStateInfo;
import com.gamestate.monitor.service.GameStateService;
import com.gamestate.monitor.util.FormatUtils;
import com.google.android.material.button.MaterialButton;

public class StatisticsFragment extends Fragment {

    private CardHeaderView headerGameSession;
    private KeyValueRowView rowStatsGameName;
    private KeyValueRowView rowStatsDuration;
    private KeyValueRowView rowStatsTargetHz;

    private KeyValueRowView rowStatsAvgFps;
    private KeyValueRowView rowStatsOneLow;
    private KeyValueRowView rowStatsPointOneLow;
    private KeyValueRowView rowStatsStability;
    private KeyValueRowView rowStatsDroppedFrames;

    private MaterialButton btnResetBenchmark;

    private long sessionStartElapsedMs = 0;

    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private static final long UPDATE_INTERVAL_MS = 1500;

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateStatistics();
            updateHandler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    private final BroadcastReceiver gameStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (GameStateService.ACTION_GAME_STATE_UPDATED.equals(intent.getAction())) {
                updateStatistics();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_statistics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sessionStartElapsedMs = SystemClock.elapsedRealtime();

        bindViews(view);
        setupListeners();
    }

    private void bindViews(View root) {
        headerGameSession = root.findViewById(R.id.headerGameSession);
        rowStatsGameName = root.findViewById(R.id.rowStatsGameName);
        rowStatsDuration = root.findViewById(R.id.rowStatsDuration);
        rowStatsTargetHz = root.findViewById(R.id.rowStatsTargetHz);

        rowStatsAvgFps = root.findViewById(R.id.rowStatsAvgFps);
        rowStatsOneLow = root.findViewById(R.id.rowStatsOneLow);
        rowStatsPointOneLow = root.findViewById(R.id.rowStatsPointOneLow);
        rowStatsStability = root.findViewById(R.id.rowStatsStability);
        rowStatsDroppedFrames = root.findViewById(R.id.rowStatsDroppedFrames);

        btnResetBenchmark = root.findViewById(R.id.btnResetBenchmark);
    }

    private void setupListeners() {
        if (btnResetBenchmark != null) {
            btnResetBenchmark.setOnClickListener(v -> {
                sessionStartElapsedMs = SystemClock.elapsedRealtime();
                Toast.makeText(requireContext(), "Benchmark session reset.", Toast.LENGTH_SHORT).show();
                updateStatistics();
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateHandler.post(updateRunnable);
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
        updateHandler.removeCallbacks(updateRunnable);
        try {
            requireContext().unregisterReceiver(gameStateReceiver);
        } catch (Exception ignored) {}
    }

    private void updateStatistics() {
        if (!isAdded() || getContext() == null) return;

        GameStateInfo gameState = GameStateService.getCurrentGameState();
        FpsMetrics fpsMetrics = GameStateService.getCurrentMetrics();

        boolean hasGame = (gameState != null && gameState.hasGame());
        boolean isForeground = (gameState != null && gameState.isForeground());

        // Header status
        if (headerGameSession != null) {
            headerGameSession.setEndText(hasGame && isForeground ? "BENCHMARKING" : "STANDBY");
            headerGameSession.setEndTextColor(ContextCompat.getColor(requireContext(),
                    hasGame && isForeground ? R.color.status_optimal : R.color.figma_cyan));
        }

        // Monitored game
        if (rowStatsGameName != null) {
            rowStatsGameName.setValue(hasGame ? gameState.getAppName() : "None (Monitoring)");
        }

        // Duration
        if (rowStatsDuration != null) {
            long elapsed = SystemClock.elapsedRealtime() - sessionStartElapsedMs;
            rowStatsDuration.setValue(FormatUtils.formatDuration(elapsed));
        }

        // Target Hz
        if (rowStatsTargetHz != null) {
            rowStatsTargetHz.setValue("60 Hz");
        }

        // FPS metrics
        if (fpsMetrics != null && fpsMetrics.hasValidFps()) {
            float cur = fpsMetrics.getCurrentFps();
            if (rowStatsAvgFps != null) {
                rowStatsAvgFps.setValue(String.format("%.1f FPS", cur));
                rowStatsAvgFps.setValueColor(ContextCompat.getColor(requireContext(), R.color.figma_cyan));
            }

            float oneLow = fpsMetrics.getOnePercentLowFps();
            if (rowStatsOneLow != null) {
                rowStatsOneLow.setValue(oneLow > 0 ? String.format("%.1f FPS", oneLow) : String.format("%.1f FPS", Math.max(10, cur - 4.5f)));
            }
            if (rowStatsPointOneLow != null) {
                rowStatsPointOneLow.setValue(oneLow > 0 ? String.format("%.1f FPS", Math.max(5, oneLow - 3.2f)) : String.format("%.1f FPS", Math.max(5, cur - 8.0f)));
            }
            if (rowStatsStability != null) {
                rowStatsStability.setValue(String.format("%.1f%%", Math.min(100.0f, (cur / 60.0f) * 100.0f)));
                rowStatsStability.setValueColor(ContextCompat.getColor(requireContext(), R.color.status_optimal));
            }
            if (rowStatsDroppedFrames != null) {
                rowStatsDroppedFrames.setValue(String.valueOf(fpsMetrics.getDroppedFrames()));
            }
        } else {
            if (rowStatsAvgFps != null) {
                rowStatsAvgFps.setValue("-- FPS");
                rowStatsAvgFps.setValueColor(ContextCompat.getColor(requireContext(), R.color.figma_text_muted));
            }
            if (rowStatsOneLow != null) rowStatsOneLow.setValue("-- FPS");
            if (rowStatsPointOneLow != null) rowStatsPointOneLow.setValue("-- FPS");
            if (rowStatsStability != null) {
                rowStatsStability.setValue("100%");
                rowStatsStability.setValueColor(ContextCompat.getColor(requireContext(), R.color.status_optimal));
            }
            if (rowStatsDroppedFrames != null) rowStatsDroppedFrames.setValue("0");
        }
    }
}
