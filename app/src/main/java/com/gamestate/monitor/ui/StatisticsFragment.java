package com.gamestate.monitor.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.gamestate.monitor.R;
import com.gamestate.monitor.model.GameSession;
import com.gamestate.monitor.service.GameStateService;
import com.gamestate.monitor.service.SessionAnalyticsTracker;
import com.gamestate.monitor.util.DeviceStatsManager;
import com.gamestate.monitor.util.FormatUtils;
import com.gamestate.monitor.util.SessionHistoryManager;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * StatisticsFragment
 * ------------------
 * Full-fledged Gaming Session Analytics Dashboard inspired by Scene / PerfStats.
 * Features 2-column benchmark grids, frame time distributions, thermal analysis,
 * battery impact, automated grading, and persistent session history.
 */
public class StatisticsFragment extends Fragment {

    // On-Demand Session Recorder
    private View cardSessionRecorder;
    private View vRecorderStatusDot;
    private TextView tvRecorderTitle;
    private TextView tvRecorderTimer;
    private TextView tvRecorderSubtitle;
    private MaterialButton btnToggleRecording;

    // Header & Actions
    private FrameLayout btnHeaderShare;
    private FrameLayout btnHeaderClear;
    private TextView btnLiveSessionToggle;

    // Containers
    private View layoutEmptyState;
    private View layoutSessionData;
    private LinearLayout layoutSessionHistoryList;
    private TextView tvEmptyHistoryNotice;

    // Session Overview
    private TextView tvSessionGameName;
    private TextView tvSessionStatusBadge;
    private TextView tvSessionDuration;
    private TextView tvSessionStartTime;
    private TextView tvSessionTargetHz;

    // Grade
    private TextView tvPerformanceGrade;
    private TextView tvGradeStability;
    private TextView tvGradeThermals;
    private TextView tvGradeBattery;

    // FPS Benchmark Grid
    private TextView tvBenchmarkAvgFps;
    private TextView tvBenchmarkMaxFps;
    private TextView tvBenchmarkMinFps;
    private TextView tvBenchmarkOneLow;
    private TextView tvBenchmarkPointOneLow;
    private TextView tvBenchmarkVariance;
    private TextView tvBenchmarkStability;
    private TextView tvBenchmarkDropped;
    private TextView tvBenchmarkStutters;

    // Frame Time Distribution
    private FrameTimeDistributionBarView barFrameTimeDistribution;
    private TextView tvFrameSmoothPct;
    private TextView tvFrameMinorPct;
    private TextView tvFrameMajorPct;

    // Thermal Analysis
    private TextView tvThermalStatus;
    private TextView tvThermalAvgTemp;
    private TextView tvThermalPeakTemp;
    private TextView tvThermalDelta;
    private SparklineGraphView sparklineThermals;

    // CPU & GPU Analytics
    private TextView tvCpuAvgUsage;
    private TextView tvCpuPeakUsage;
    private TextView tvGpuAvgUsage;
    private TextView tvGpuPeakUsage;
    private SparklineGraphView sparklineCpuStats;
    private SparklineGraphView sparklineGpuStats;

    // Battery Impact
    private TextView tvBatteryUsed;
    private TextView tvBatteryDrainRate;
    private TextView tvBatteryEstPower;

    // Buttons
    private MaterialButton btnExportReport;
    private MaterialButton btnShareSummary;
    private View btnDeleteCurrentSession;

    // Managers & State
    private SessionAnalyticsTracker analyticsTracker;
    private SessionHistoryManager historyManager;

    private GameSession inspectedSession = null;
    private boolean isInspectingPastSession = false;

    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private static final long REFRESH_INTERVAL_MS = 1000;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateRecorderUi();
            if (!isInspectingPastSession) {
                refreshActiveSessionData();
            }
            updateHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateRecorderUi();
            if (GameStateService.ACTION_GAME_STATE_UPDATED.equals(intent.getAction()) && !isInspectingPastSession) {
                refreshActiveSessionData();
            } else if (SessionAnalyticsTracker.ACTION_RECORDING_STATE_CHANGED.equals(intent.getAction())) {
                refreshActiveSessionData();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_statistics, container, false);

        analyticsTracker = SessionAnalyticsTracker.getInstance(requireContext());
        historyManager = SessionHistoryManager.getInstance(requireContext());

        initViews(root);
        configureColors();
        setupListeners();
        loadInitialState();

        return root;
    }

    private void initViews(View root) {
        // Session Recorder Card
        cardSessionRecorder = root.findViewById(R.id.cardSessionRecorder);
        vRecorderStatusDot = root.findViewById(R.id.vRecorderStatusDot);
        tvRecorderTitle = root.findViewById(R.id.tvRecorderTitle);
        tvRecorderTimer = root.findViewById(R.id.tvRecorderTimer);
        tvRecorderSubtitle = root.findViewById(R.id.tvRecorderSubtitle);
        btnToggleRecording = root.findViewById(R.id.btnToggleRecording);

        btnHeaderShare = root.findViewById(R.id.btnHeaderShare);
        btnHeaderClear = root.findViewById(R.id.btnHeaderClear);
        btnLiveSessionToggle = root.findViewById(R.id.btnLiveSessionToggle);

        layoutEmptyState = root.findViewById(R.id.layoutEmptyState);
        layoutSessionData = root.findViewById(R.id.layoutSessionData);
        layoutSessionHistoryList = root.findViewById(R.id.layoutSessionHistoryList);
        tvEmptyHistoryNotice = root.findViewById(R.id.tvEmptyHistoryNotice);

        tvSessionGameName = root.findViewById(R.id.tvSessionGameName);
        tvSessionStatusBadge = root.findViewById(R.id.tvSessionStatusBadge);
        btnDeleteCurrentSession = root.findViewById(R.id.btnDeleteCurrentSession);
        tvSessionDuration = root.findViewById(R.id.tvSessionDuration);
        tvSessionStartTime = root.findViewById(R.id.tvSessionStartTime);
        tvSessionTargetHz = root.findViewById(R.id.tvSessionTargetHz);

        tvPerformanceGrade = root.findViewById(R.id.tvPerformanceGrade);
        tvGradeStability = root.findViewById(R.id.tvGradeStability);
        tvGradeThermals = root.findViewById(R.id.tvGradeThermals);
        tvGradeBattery = root.findViewById(R.id.tvGradeBattery);

        tvBenchmarkAvgFps = root.findViewById(R.id.tvBenchmarkAvgFps);
        tvBenchmarkMaxFps = root.findViewById(R.id.tvBenchmarkMaxFps);
        tvBenchmarkMinFps = root.findViewById(R.id.tvBenchmarkMinFps);
        tvBenchmarkOneLow = root.findViewById(R.id.tvBenchmarkOneLow);
        tvBenchmarkPointOneLow = root.findViewById(R.id.tvBenchmarkPointOneLow);
        tvBenchmarkVariance = root.findViewById(R.id.tvBenchmarkVariance);
        tvBenchmarkStability = root.findViewById(R.id.tvBenchmarkStability);
        tvBenchmarkDropped = root.findViewById(R.id.tvBenchmarkDropped);
        tvBenchmarkStutters = root.findViewById(R.id.tvBenchmarkStutters);

        barFrameTimeDistribution = root.findViewById(R.id.barFrameTimeDistribution);
        tvFrameSmoothPct = root.findViewById(R.id.tvFrameSmoothPct);
        tvFrameMinorPct = root.findViewById(R.id.tvFrameMinorPct);
        tvFrameMajorPct = root.findViewById(R.id.tvFrameMajorPct);

        tvThermalStatus = root.findViewById(R.id.tvThermalStatus);
        tvThermalAvgTemp = root.findViewById(R.id.tvThermalAvgTemp);
        tvThermalPeakTemp = root.findViewById(R.id.tvThermalPeakTemp);
        tvThermalDelta = root.findViewById(R.id.tvThermalDelta);
        sparklineThermals = root.findViewById(R.id.sparklineThermals);

        tvCpuAvgUsage = root.findViewById(R.id.tvCpuAvgUsage);
        tvCpuPeakUsage = root.findViewById(R.id.tvCpuPeakUsage);
        tvGpuAvgUsage = root.findViewById(R.id.tvGpuAvgUsage);
        tvGpuPeakUsage = root.findViewById(R.id.tvGpuPeakUsage);
        sparklineCpuStats = root.findViewById(R.id.sparklineCpuStats);
        sparklineGpuStats = root.findViewById(R.id.sparklineGpuStats);

        tvBatteryUsed = root.findViewById(R.id.tvBatteryUsed);
        tvBatteryDrainRate = root.findViewById(R.id.tvBatteryDrainRate);
        tvBatteryEstPower = root.findViewById(R.id.tvBatteryEstPower);

        btnExportReport = root.findViewById(R.id.btnExportReport);
        btnShareSummary = root.findViewById(R.id.btnShareSummary);
    }

    private void configureColors() {
        int cyan = Color.parseColor("#00D2E0");
        if (sparklineThermals != null) sparklineThermals.setLineColor(cyan);
        if (sparklineCpuStats != null) sparklineCpuStats.setLineColor(cyan);
        if (sparklineGpuStats != null) sparklineGpuStats.setLineColor(cyan);
    }

    private void setupListeners() {
        if (btnHeaderShare != null) {
            btnHeaderShare.setOnClickListener(v -> shareSessionSummary());
        }

        if (btnShareSummary != null) {
            btnShareSummary.setOnClickListener(v -> shareSessionSummary());
        }

        if (btnExportReport != null) {
            btnExportReport.setOnClickListener(v -> exportSessionReport());
        }

        if (btnDeleteCurrentSession != null) {
            btnDeleteCurrentSession.setOnClickListener(v -> {
                if (inspectedSession != null) {
                    confirmDeleteSession(inspectedSession);
                }
            });
        }

        if (btnHeaderClear != null) {
            btnHeaderClear.setOnClickListener(v -> {
                List<GameSession> sessions = historyManager.getAllSessions();
                if (sessions.isEmpty()) {
                    Toast.makeText(requireContext(), "No session history to clear.", Toast.LENGTH_SHORT).show();
                    return;
                }
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_GameStateMonitor)
                        .setTitle("Clear All History")
                        .setMessage("Delete all saved gaming benchmark sessions? This cannot be undone.")
                        .setPositiveButton("Clear All", (dialog, which) -> {
                            historyManager.clearHistory();
                            Toast.makeText(requireContext(), "Session history cleared.", Toast.LENGTH_SHORT).show();
                            loadInitialState();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        if (btnToggleRecording != null) {
            btnToggleRecording.setOnClickListener(v -> {
                if (analyticsTracker == null) return;
                if (analyticsTracker.isRecording()) {
                    try {
                        Intent stopIntent = new Intent(requireContext(), GameStateService.class);
                        stopIntent.setAction(GameStateService.ACTION_STOP_RECORDING);
                        requireContext().startService(stopIntent);
                    } catch (Exception ignored) {}
                    analyticsTracker.stopRecording();
                    Toast.makeText(requireContext(), "Benchmark Session Saved!", Toast.LENGTH_SHORT).show();
                    GameSession last = analyticsTracker.getLastCompletedSession();
                    if (last != null) {
                        isInspectingPastSession = true;
                        inspectedSession = last;
                        displaySession(last);
                    }
                } else {
                    try {
                        Intent startIntent = new Intent(requireContext(), GameStateService.class);
                        startIntent.setAction(GameStateService.ACTION_START_RECORDING);
                        ContextCompat.startForegroundService(requireContext(), startIntent);
                    } catch (Exception ignored) {}

                    DeviceStatsManager statsManager = new DeviceStatsManager(requireContext());
                    analyticsTracker.startRecording(
                            GameStateService.getCurrentGameState(),
                            GameStateService.getCurrentMetrics(),
                            statsManager.getPerformanceStats()
                    );
                    isInspectingPastSession = false;
                    Toast.makeText(requireContext(), "Recording Started! Launch your game.", Toast.LENGTH_SHORT).show();
                }
                updateRecorderUi();
                refreshActiveSessionData();
                populateHistoryList(historyManager.getAllSessions());
            });
        }

        if (btnLiveSessionToggle != null) {
            btnLiveSessionToggle.setOnClickListener(v -> {
                isInspectingPastSession = false;
                refreshActiveSessionData();
                btnLiveSessionToggle.setText("Live Session");
                btnLiveSessionToggle.setTextColor(ContextCompat.getColor(requireContext(), R.color.figma_cyan));
            });
        }
    }

    private void updateRecorderUi() {
        if (!isAdded() || getContext() == null || btnToggleRecording == null) return;
        boolean isRecording = (analyticsTracker != null && analyticsTracker.isRecording());
        if (isRecording) {
            if (vRecorderStatusDot != null) {
                vRecorderStatusDot.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF3B30")));
            }
            if (tvRecorderTitle != null) {
                tvRecorderTitle.setText("Recording Active Session...");
                tvRecorderTitle.setTextColor(Color.WHITE);
            }

            long elapsedMs = System.currentTimeMillis() - analyticsTracker.getRecordingStartTimeMs();
            long secs = (elapsedMs / 1000) % 60;
            long mins = (elapsedMs / (1000 * 60)) % 60;
            long hrs = elapsedMs / (1000 * 60 * 60);
            String timeStr = (hrs > 0) ? String.format(Locale.getDefault(), "%02d:%02d:%02d", hrs, mins, secs)
                    : String.format(Locale.getDefault(), "%02d:%02d", mins, secs);

            if (tvRecorderTimer != null) {
                tvRecorderTimer.setText(timeStr);
                tvRecorderTimer.setTextColor(Color.parseColor("#FF3B30"));
            }

            com.gamestate.monitor.fps.GameStateInfo game = GameStateService.getCurrentGameState();
            String gameName = (game != null && game.hasGame()) ? game.getAppName() : "Waiting for Game Launch";
            if (tvRecorderSubtitle != null) {
                tvRecorderSubtitle.setText("Target: " + gameName + " • Tap below to finish & save benchmark.");
            }

            btnToggleRecording.setText("⏹ Stop & Save Session");
            btnToggleRecording.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF3B30")));
            btnToggleRecording.setTextColor(Color.WHITE);
        } else {
            if (vRecorderStatusDot != null) {
                vRecorderStatusDot.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.figma_cyan)));
            }
            if (tvRecorderTitle != null) {
                tvRecorderTitle.setText("Session Benchmark Recorder");
                tvRecorderTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.figma_text_primary));
            }
            if (tvRecorderTimer != null) {
                tvRecorderTimer.setText("IDLE");
                tvRecorderTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.figma_text_muted));
            }
            if (tvRecorderSubtitle != null) {
                tvRecorderSubtitle.setText("Zero background drain. Start recording anytime to profile your game's FPS, stutters, and thermals.");
            }
            btnToggleRecording.setText("▶ Start Benchmark Recording");
            btnToggleRecording.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.figma_cyan)));
            btnToggleRecording.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
        }
    }

    private void loadInitialState() {
        updateRecorderUi();
        GameSession active = analyticsTracker.getActiveSession();
        List<GameSession> history = historyManager.getAllSessions();

        if (active != null) {
            inspectedSession = active;
            isInspectingPastSession = false;
            displaySession(inspectedSession);
        } else if (!history.isEmpty()) {
            inspectedSession = history.get(0);
            isInspectingPastSession = true;
            displaySession(inspectedSession);
            if (btnLiveSessionToggle != null) {
                btnLiveSessionToggle.setText("Inspecting Past");
                btnLiveSessionToggle.setTextColor(ContextCompat.getColor(requireContext(), R.color.figma_text_muted));
            }
        } else {
            // Absolutely no data (fresh app install & no game running) -> Clean Empty State
            showEmptyState();
        }

        populateHistoryList(history);
    }

    private void refreshActiveSessionData() {
        if (!isAdded() || getContext() == null) return;

        GameSession active = analyticsTracker.getActiveSession();
        if (active != null) {
            inspectedSession = active;
            displaySession(active);
        } else {
            // No active game
            List<GameSession> history = historyManager.getAllSessions();
            if (!history.isEmpty() && inspectedSession == null) {
                inspectedSession = history.get(0);
                isInspectingPastSession = true;
                displaySession(inspectedSession);
            } else if (inspectedSession == null) {
                showEmptyState();
            }
        }
        populateHistoryList(historyManager.getAllSessions());
    }

    private void showEmptyState() {
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
        if (layoutSessionData != null) layoutSessionData.setVisibility(View.GONE);
        if (btnDeleteCurrentSession != null) btnDeleteCurrentSession.setVisibility(View.GONE);
    }

    private void displaySession(GameSession s) {
        if (s == null) {
            showEmptyState();
            return;
        }

        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
        if (layoutSessionData != null) layoutSessionData.setVisibility(View.VISIBLE);

        // 1. Session Overview
        if (tvSessionGameName != null) tvSessionGameName.setText(s.getAppName());
        if (tvSessionStatusBadge != null) {
            tvSessionStatusBadge.setText(s.isRunning() ? "RUNNING" : "COMPLETED");
            tvSessionStatusBadge.setTextColor(ContextCompat.getColor(requireContext(),
                    s.isRunning() ? R.color.figma_cyan : R.color.figma_text_muted));
        }
        if (btnDeleteCurrentSession != null) {
            // Can delete any completed session
            btnDeleteCurrentSession.setVisibility(s.isRunning() ? View.GONE : View.VISIBLE);
        }
        if (tvSessionDuration != null) {
            tvSessionDuration.setText(FormatUtils.formatDuration(s.getDurationMs()));
        }
        if (tvSessionStartTime != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            tvSessionStartTime.setText(sdf.format(new Date(s.getStartTimeMs())));
        }
        if (tvSessionTargetHz != null) {
            tvSessionTargetHz.setText(String.format(Locale.getDefault(), "%.0fHz", s.getTargetRefreshRate()));
        }

        // 2. Grade
        if (tvPerformanceGrade != null) tvPerformanceGrade.setText(s.getGrade());
        if (tvGradeStability != null) tvGradeStability.setText("FPS: " + s.getFpsStabilityRating());
        if (tvGradeThermals != null) tvGradeThermals.setText("Thermals: " + s.getThermalRating());
        if (tvGradeBattery != null) tvGradeBattery.setText("Battery: " + s.getBatteryImpactRating());

        // 3. FPS Benchmark Grid
        if (tvBenchmarkAvgFps != null) tvBenchmarkAvgFps.setText(String.format(Locale.getDefault(), "%.1f", s.getAvgFps()));
        if (tvBenchmarkMaxFps != null) tvBenchmarkMaxFps.setText(String.format(Locale.getDefault(), "%.1f", s.getMaxFps()));
        if (tvBenchmarkMinFps != null) tvBenchmarkMinFps.setText(String.format(Locale.getDefault(), "%.1f", s.getMinFps()));
        if (tvBenchmarkOneLow != null) tvBenchmarkOneLow.setText(String.format(Locale.getDefault(), "%.1f", s.getOnePercentLowFps()));
        if (tvBenchmarkPointOneLow != null) tvBenchmarkPointOneLow.setText(String.format(Locale.getDefault(), "%.1f", s.getPointOnePercentLowFps()));
        if (tvBenchmarkVariance != null) tvBenchmarkVariance.setText(String.format(Locale.getDefault(), "%.1f", s.getFpsVariance()));

        if (tvBenchmarkStability != null) tvBenchmarkStability.setText(String.format(Locale.getDefault(), "%.0f%%", s.getStabilityScorePercent()));
        if (tvBenchmarkDropped != null) tvBenchmarkDropped.setText(String.valueOf(s.getDroppedFrames()));
        if (tvBenchmarkStutters != null) tvBenchmarkStutters.setText(String.valueOf(s.getStutterEvents()));

        // 4. Frame Time Distribution
        if (barFrameTimeDistribution != null) {
            barFrameTimeDistribution.setDistribution(s.getSmoothFramesPercent(), s.getMinorStuttersPercent(), s.getMajorStuttersPercent());
        }
        if (tvFrameSmoothPct != null) tvFrameSmoothPct.setText(String.format(Locale.getDefault(), "%.1f%%", s.getSmoothFramesPercent()));
        if (tvFrameMinorPct != null) tvFrameMinorPct.setText(String.format(Locale.getDefault(), "%.1f%%", s.getMinorStuttersPercent()));
        if (tvFrameMajorPct != null) tvFrameMajorPct.setText(String.format(Locale.getDefault(), "%.1f%%", s.getMajorStuttersPercent()));

        // 5. Thermals
        if (tvThermalStatus != null) tvThermalStatus.setText(s.getThermalStatus());
        if (tvThermalAvgTemp != null) tvThermalAvgTemp.setText(String.format(Locale.getDefault(), "%.0f°C", s.getAvgTempC()));
        if (tvThermalPeakTemp != null) tvThermalPeakTemp.setText(String.format(Locale.getDefault(), "%.0f°C", s.getPeakTempC()));
        if (tvThermalDelta != null) tvThermalDelta.setText(String.format(Locale.getDefault(), "+%.0f°C", s.getTempDeltaC()));

        // Thermals sparkline
        if (sparklineThermals != null && !s.getTempSamples().isEmpty()) {
            for (Float val : s.getTempSamples()) sparklineThermals.addPoint(val);
        }

        // 6. CPU & GPU Analytics
        if (tvCpuAvgUsage != null) tvCpuAvgUsage.setText(String.format(Locale.getDefault(), "Avg: %d%%", s.getAvgCpuUsage()));
        if (tvCpuPeakUsage != null) tvCpuPeakUsage.setText(String.format(Locale.getDefault(), "Peak: %d%%", s.getPeakCpuUsage()));
        if (tvGpuAvgUsage != null) tvGpuAvgUsage.setText(String.format(Locale.getDefault(), "Avg: %d%%", s.getAvgGpuUsage()));
        if (tvGpuPeakUsage != null) tvGpuPeakUsage.setText(String.format(Locale.getDefault(), "Peak: %d%%", s.getPeakGpuUsage()));

        if (sparklineCpuStats != null && !s.getCpuSamples().isEmpty()) {
            for (Float val : s.getCpuSamples()) sparklineCpuStats.addPoint(val);
        }
        if (sparklineGpuStats != null && !s.getGpuSamples().isEmpty()) {
            for (Float val : s.getGpuSamples()) sparklineGpuStats.addPoint(val);
        }

        // 7. Battery Impact
        if (tvBatteryUsed != null) tvBatteryUsed.setText(s.getBatteryConsumedPercent() + "%");
        if (tvBatteryDrainRate != null) tvBatteryDrainRate.setText(String.format(Locale.getDefault(), "%.1f%%/hr", s.getAvgDrainRatePerHour()));
        if (tvBatteryEstPower != null) tvBatteryEstPower.setText(String.format(Locale.getDefault(), "%.1fW", s.getEstimatedPowerWatts()));
    }

    private void populateHistoryList(List<GameSession> history) {
        if (layoutSessionHistoryList == null) return;
        layoutSessionHistoryList.removeAllViews();

        if (history == null || history.isEmpty()) {
            if (tvEmptyHistoryNotice != null) tvEmptyHistoryNotice.setVisibility(View.VISIBLE);
            return;
        }

        if (tvEmptyHistoryNotice != null) tvEmptyHistoryNotice.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (GameSession session : history) {
            View itemView = inflater.inflate(R.layout.item_session_history, layoutSessionHistoryList, false);

            TextView tvItemGame = itemView.findViewById(R.id.tvItemHistoryGame);
            TextView tvItemDuration = itemView.findViewById(R.id.tvItemHistoryDuration);
            TextView tvItemAvgFps = itemView.findViewById(R.id.tvItemHistoryAvgFps);
            TextView tvItemGrade = itemView.findViewById(R.id.tvItemHistoryGrade);
            View btnItemDelete = itemView.findViewById(R.id.btnItemDeleteSession);

            if (tvItemGame != null) tvItemGame.setText(session.getAppName());
            if (tvItemDuration != null) tvItemDuration.setText(FormatUtils.formatDuration(session.getDurationMs()));
            if (tvItemAvgFps != null) tvItemAvgFps.setText(String.format(Locale.getDefault(), "%.0f FPS", session.getAvgFps()));
            if (tvItemGrade != null) tvItemGrade.setText(session.getGrade());

            if (btnItemDelete != null) {
                btnItemDelete.setOnClickListener(v -> confirmDeleteSession(session));
            }

            itemView.setOnClickListener(v -> {
                inspectedSession = session;
                isInspectingPastSession = true;
                displaySession(session);
                if (btnLiveSessionToggle != null) {
                    btnLiveSessionToggle.setText("Inspecting: " + session.getAppName());
                    btnLiveSessionToggle.setTextColor(ContextCompat.getColor(requireContext(), R.color.figma_text_muted));
                }
            });

            layoutSessionHistoryList.addView(itemView);
        }
    }

    private void confirmDeleteSession(GameSession session) {
        if (session == null || getContext() == null) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_GameStateMonitor)
                .setTitle("Delete Session")
                .setMessage("Delete benchmark session for " + session.getAppName() + " (" + FormatUtils.formatDuration(session.getDurationMs()) + ")?")
                .setPositiveButton("Delete", (dialog, which) -> deleteSession(session))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSession(GameSession session) {
        if (session == null || getContext() == null) return;
        boolean removed = historyManager.deleteSession(session.getSessionId());
        if (removed) {
            Toast.makeText(requireContext(), "Session deleted", Toast.LENGTH_SHORT).show();

            // If the deleted session was currently inspected
            if (inspectedSession != null && session.getSessionId().equals(inspectedSession.getSessionId())) {
                List<GameSession> remaining = historyManager.getAllSessions();
                GameSession active = analyticsTracker != null ? analyticsTracker.getActiveSession() : null;
                if (active != null) {
                    inspectedSession = active;
                    isInspectingPastSession = false;
                    displaySession(inspectedSession);
                } else if (!remaining.isEmpty()) {
                    inspectedSession = remaining.get(0);
                    isInspectingPastSession = true;
                    displaySession(inspectedSession);
                } else {
                    inspectedSession = null;
                    isInspectingPastSession = false;
                    showEmptyState();
                }
            }
            populateHistoryList(historyManager.getAllSessions());
        }
    }

    private void shareSessionSummary() {
        if (inspectedSession == null) {
            Toast.makeText(requireContext(), "No session active to share.", Toast.LENGTH_SHORT).show();
            return;
        }

        String report = generateReportText(inspectedSession);
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, report);
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, "Share Game Benchmark Report");
        startActivity(shareIntent);
    }

    private void exportSessionReport() {
        if (inspectedSession == null) {
            Toast.makeText(requireContext(), "No session active to export.", Toast.LENGTH_SHORT).show();
            return;
        }
        shareSessionSummary();
    }

    private String generateReportText(GameSession s) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault());
        String dateStr = sdf.format(new Date(s.getStartTimeMs()));

        return "🎮 GameState Monitor Benchmark Report\n" +
                "Game: " + s.getAppName() + "\n" +
                "Date: " + dateStr + "\n" +
                "Duration: " + FormatUtils.formatDuration(s.getDurationMs()) + " | Grade: " + s.getGrade() + "\n" +
                "------------------------------------------\n" +
                "📊 FPS Benchmark:\n" +
                "• Average FPS: " + String.format(Locale.getDefault(), "%.1f", s.getAvgFps()) + "\n" +
                "• Max: " + String.format(Locale.getDefault(), "%.1f", s.getMaxFps()) + " | Min: " + String.format(Locale.getDefault(), "%.1f", s.getMinFps()) + "\n" +
                "• 1% Low: " + String.format(Locale.getDefault(), "%.1f", s.getOnePercentLowFps()) + " | 0.1% Low: " + String.format(Locale.getDefault(), "%.1f", s.getPointOnePercentLowFps()) + "\n" +
                "• FPS Variance: " + String.format(Locale.getDefault(), "%.1f", s.getFpsVariance()) + "\n" +
                "• Stability Score: " + String.format(Locale.getDefault(), "%.0f%%", s.getStabilityScorePercent()) + "\n" +
                "• Dropped Frames: " + s.getDroppedFrames() + " | Stutters: " + s.getStutterEvents() + "\n\n" +
                "⏱️ Frame Time Distribution:\n" +
                "• Smooth (<16.6ms): " + String.format(Locale.getDefault(), "%.1f%%", s.getSmoothFramesPercent()) + "\n" +
                "• Minor Stutters (16-33ms): " + String.format(Locale.getDefault(), "%.1f%%", s.getMinorStuttersPercent()) + "\n" +
                "• Major Stutters (>33ms): " + String.format(Locale.getDefault(), "%.1f%%", s.getMajorStuttersPercent()) + "\n\n" +
                "🌡️ Thermal Analysis:\n" +
                "• Avg Temp: " + String.format(Locale.getDefault(), "%.0f°C", s.getAvgTempC()) + " | Peak: " + String.format(Locale.getDefault(), "%.0f°C", s.getPeakTempC()) + "\n" +
                "• Delta: +" + String.format(Locale.getDefault(), "%.0f°C", s.getTempDeltaC()) + " (" + s.getThermalStatus() + ")\n\n" +
                "🧠 Hardware Load:\n" +
                "• CPU: Avg " + s.getAvgCpuUsage() + "% (Peak " + s.getPeakCpuUsage() + "%)\n" +
                "• GPU: Avg " + s.getAvgGpuUsage() + "% (Peak " + s.getPeakGpuUsage() + "%)\n\n" +
                "🔋 Battery & Power:\n" +
                "• Battery Consumed: " + s.getBatteryConsumedPercent() + "%\n" +
                "• Drain Rate: " + String.format(Locale.getDefault(), "%.1f%%/hr", s.getAvgDrainRatePerHour()) + " (~" + String.format(Locale.getDefault(), "%.1fW", s.getEstimatedPowerWatts()) + ")\n" +
                "------------------------------------------\n" +
                "Generated by GameState Monitor";
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRecorderUi();
        updateHandler.post(refreshRunnable);
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(GameStateService.ACTION_GAME_STATE_UPDATED);
            filter.addAction(SessionAnalyticsTracker.ACTION_RECORDING_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                requireContext().registerReceiver(stateReceiver, filter);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onPause() {
        super.onPause();
        updateHandler.removeCallbacks(refreshRunnable);
        try {
            requireContext().unregisterReceiver(stateReceiver);
        } catch (Exception ignored) {}
    }
}
