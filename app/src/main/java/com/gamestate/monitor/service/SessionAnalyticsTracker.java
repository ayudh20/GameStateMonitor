package com.gamestate.monitor.service;

import android.content.Context;
import android.content.Intent;

import com.gamestate.monitor.fps.FpsMetrics;
import com.gamestate.monitor.fps.GameStateInfo;
import com.gamestate.monitor.model.CpuInfo;
import com.gamestate.monitor.model.GameSession;
import com.gamestate.monitor.model.GpuInfo;
import com.gamestate.monitor.model.PerformanceStats;
import com.gamestate.monitor.util.CpuMonitor;
import com.gamestate.monitor.util.DeviceStatsManager;
import com.gamestate.monitor.util.GpuMonitor;
import com.gamestate.monitor.util.SessionHistoryManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SessionAnalyticsTracker
 * -----------------------
 * Real-time aggregator for active gaming session telemetry.
 * Operates purely on-demand: records benchmark statistics ONLY when the user explicitly triggers recording.
 * Zero background processing when recording is idle.
 */
public class SessionAnalyticsTracker {

    public static final String ACTION_RECORDING_STATE_CHANGED = "com.gamestate.monitor.ACTION_RECORDING_STATE_CHANGED";
    public static final String EXTRA_IS_RECORDING = "is_recording";

    private static SessionAnalyticsTracker instance;
    private final Context context;
    private final SessionHistoryManager historyManager;

    private boolean isRecording = false;
    private long recordingStartTimeMs = 0;

    private GameSession activeSession = null;
    private GameSession lastCompletedSession = null;

    // Running buffers for active session calculations
    private final List<Float> sessionFpsBuffer = new ArrayList<>();
    private final List<Float> sessionTempBuffer = new ArrayList<>();
    private final List<Integer> sessionCpuBuffer = new ArrayList<>();
    private final List<Integer> sessionGpuBuffer = new ArrayList<>();

    public static final float GAMEPLAY_FPS_THRESHOLD = 5.0f;
    private boolean hasSampledAnyFps = false;

    private int totalFramesSampled = 0;
    private int smoothFramesCount = 0;
    private int minorStuttersCount = 0;
    private int majorStuttersCount = 0;

    private SessionAnalyticsTracker(Context context) {
        this.context = context.getApplicationContext();
        this.historyManager = SessionHistoryManager.getInstance(this.context);
    }

    public static synchronized SessionAnalyticsTracker getInstance(Context context) {
        if (instance == null) {
            instance = new SessionAnalyticsTracker(context);
        }
        return instance;
    }

    public synchronized boolean isRecording() {
        return isRecording;
    }

    public synchronized long getRecordingStartTimeMs() {
        return recordingStartTimeMs;
    }

    public synchronized GameSession getActiveSession() {
        return activeSession;
    }

    public synchronized GameSession getLastCompletedSession() {
        return lastCompletedSession;
    }

    /**
     * Start explicit benchmark session recording on-demand.
     */
    public synchronized void startRecording(GameStateInfo gameState, FpsMetrics fpsMetrics, PerformanceStats stats) {
        if (isRecording) return;
        isRecording = true;
        recordingStartTimeMs = System.currentTimeMillis();
        startNewSession(gameState, fpsMetrics, stats);
        broadcastRecordingState();
    }

    /**
     * Stop explicit benchmark session recording, compute score, and save to history.
     */
    public synchronized void stopRecording() {
        if (!isRecording) return;
        isRecording = false;
        finalizeActiveSession();
        broadcastRecordingState();
    }

    private void broadcastRecordingState() {
        try {
            Intent intent = new Intent(ACTION_RECORDING_STATE_CHANGED);
            intent.setPackage(context.getPackageName());
            intent.putExtra(EXTRA_IS_RECORDING, isRecording);
            context.sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    /**
     * Called on each evaluation tick by GameStateService (every ~1s).
     * Strictly active ONLY while isRecording == true.
     */
    public synchronized void onTick(GameStateInfo gameState, FpsMetrics fpsMetrics,
                                   PerformanceStats stats, CpuInfo cpuInfo, GpuInfo gpuInfo) {
        if (!isRecording) {
            // Zero processing when idle! Ultra-lightweight guarantee.
            return;
        }

        if (activeSession == null) {
            startNewSession(gameState, fpsMetrics, stats);
        }

        // Dynamically update game name if game was launched after recording started
        if (gameState != null && gameState.hasGame()) {
            if ("Gaming Session".equals(activeSession.getAppName()) ||
                    "Waiting for Game Launch".equals(activeSession.getAppName()) ||
                    !gameState.getPackageName().equals(activeSession.getPackageName())) {
                activeSession.setAppName(gameState.getAppName());
                activeSession.setPackageName(gameState.getPackageName());
            }
        }

        updateActiveSession(fpsMetrics, stats, cpuInfo, gpuInfo);
    }

    private void startNewSession(GameStateInfo gameState, FpsMetrics fpsMetrics, PerformanceStats stats) {
        float refreshRate = (fpsMetrics != null && fpsMetrics.getTargetRefreshRate() > 0)
                ? fpsMetrics.getTargetRefreshRate() : 60.0f;

        boolean hasGame = (gameState != null && gameState.hasGame());
        String appName = hasGame ? gameState.getAppName() : "Gaming Session";
        String pkgName = hasGame ? gameState.getPackageName() : "com.gamestate.game";

        activeSession = new GameSession(
                String.valueOf(System.currentTimeMillis()),
                appName,
                pkgName,
                System.currentTimeMillis(),
                refreshRate
        );

        int currentBat = stats != null ? stats.getBatteryLevel() : 100;
        float currentTemp = stats != null ? stats.getBatteryTemperatureC() : 35.0f;

        activeSession.setStartBatteryLevel(currentBat);
        activeSession.setEndBatteryLevel(currentBat);
        activeSession.setStartTempC(currentTemp);
        activeSession.setAvgTempC(currentTemp);
        activeSession.setPeakTempC(currentTemp);

        hasSampledAnyFps = false;
        activeSession.setGameplayMinFps(0.0f);
        activeSession.setAbsoluteMinFps(0.0f);

        sessionFpsBuffer.clear();
        sessionTempBuffer.clear();
        sessionCpuBuffer.clear();
        sessionGpuBuffer.clear();

        totalFramesSampled = 0;
        smoothFramesCount = 0;
        minorStuttersCount = 0;
        majorStuttersCount = 0;
    }

    private void updateActiveSession(FpsMetrics fpsMetrics, PerformanceStats stats, CpuInfo cpuInfo, GpuInfo gpuInfo) {
        if (activeSession == null) return;

        long now = System.currentTimeMillis();
        long duration = now - activeSession.getStartTimeMs();
        activeSession.setDurationMs(duration);

        // 1. FPS Metrics Aggregation
        if (fpsMetrics != null && fpsMetrics.hasValidFps()) {
            float fps = fpsMetrics.getCurrentFps();

            // Track Absolute Min FPS across all samples (including loading screens / 0-4 FPS)
            if (!hasSampledAnyFps) {
                activeSession.setAbsoluteMinFps(fps);
                hasSampledAnyFps = true;
            } else if (fps < activeSession.getAbsoluteMinFps()) {
                activeSession.setAbsoluteMinFps(fps);
            }

            // Gameplay FPS Filtering:
            // Samples < 5.0 FPS are classified as loading screens, app startup, match loading,
            // returning to lobby, or background state. Exclude them from benchmark gameplay calculations.
            if (fps >= GAMEPLAY_FPS_THRESHOLD) {
                sessionFpsBuffer.add(fps);

                // Gameplay Min / Max (Active gameplay)
                if (activeSession.getGameplayMinFps() <= 0.0f || fps < activeSession.getGameplayMinFps()) {
                    activeSession.setGameplayMinFps(fps);
                }
                if (fps > activeSession.getMaxFps()) {
                    activeSession.setMaxFps(fps);
                }

                // Average Gameplay FPS
                float sum = 0f;
                for (Float f : sessionFpsBuffer) sum += f;
                float avg = sum / sessionFpsBuffer.size();
                activeSession.setAvgFps(avg);

                // Gameplay Variance
                float varSum = 0f;
                for (Float f : sessionFpsBuffer) varSum += (f - avg) * (f - avg);
                float variance = varSum / sessionFpsBuffer.size();
                activeSession.setFpsVariance(variance);

                // 1% Low and 0.1% Low (Calculated only from valid gameplay samples)
                if (sessionFpsBuffer.size() >= 5) {
                    List<Float> sorted = new ArrayList<>(sessionFpsBuffer);
                    Collections.sort(sorted);
                    int n = sorted.size();

                    // 1% Low: 1st percentile of gameplay frames
                    float rank1 = 0.01f * (n - 1);
                    int low1 = (int) Math.floor(rank1);
                    int high1 = (int) Math.ceil(rank1);
                    float weight1 = rank1 - low1;
                    float onePctLow = sorted.get(low1) + weight1 * (sorted.get(high1) - sorted.get(low1));

                    // 0.1% Low: 0.1th percentile of gameplay frames
                    float rank01 = 0.001f * (n - 1);
                    int low01 = (int) Math.floor(rank01);
                    int high01 = (int) Math.ceil(rank01);
                    float weight01 = rank01 - low01;
                    float pointOnePctLow = sorted.get(low01) + weight01 * (sorted.get(high01) - sorted.get(low01));

                    activeSession.setOnePercentLowFps(Math.round(onePctLow * 10.0f) / 10.0f);
                    activeSession.setPointOnePercentLowFps(Math.round(pointOnePctLow * 10.0f) / 10.0f);
                } else {
                    float fallback1Pct = fpsMetrics.hasValidOnePercentLow() && fpsMetrics.getOnePercentLowFps() >= GAMEPLAY_FPS_THRESHOLD
                            ? fpsMetrics.getOnePercentLowFps() : Math.max(GAMEPLAY_FPS_THRESHOLD, fps - 4.0f);
                    activeSession.setOnePercentLowFps(Math.round(fallback1Pct * 10.0f) / 10.0f);
                    activeSession.setPointOnePercentLowFps(Math.max(GAMEPLAY_FPS_THRESHOLD, Math.round((fallback1Pct - 3.0f) * 10.0f) / 10.0f));
                }

                // Dropped Frames & Stutters (Only track during active gameplay)
                activeSession.setDroppedFrames(fpsMetrics.getDroppedFrames());
                activeSession.setStutterEvents(fpsMetrics.getJankCount());

                // Stability Score: % of active gameplay samples within 10% of target
                // Stable gameplay = High score; Loading screens = No penalty!
                float targetHz = activeSession.getTargetRefreshRate();
                if (targetHz > 0) {
                    int stableCount = 0;
                    for (Float f : sessionFpsBuffer) {
                        if (f >= targetHz * 0.90f) stableCount++;
                    }
                    float stability = (float) stableCount / sessionFpsBuffer.size() * 100f;
                    activeSession.setStabilityScorePercent(Math.min(100f, stability));
                }

                // Frame time classification (Only during active gameplay)
                float frameTimeMs = fpsMetrics.hasValidFrameTime() ? fpsMetrics.getAverageFrameTimeMs() : (1000f / fps);
                totalFramesSampled++;
                if (frameTimeMs < 16.7f) {
                    smoothFramesCount++;
                } else if (frameTimeMs <= 33.3f) {
                    minorStuttersCount++;
                } else {
                    majorStuttersCount++;
                }

                if (totalFramesSampled > 0) {
                    activeSession.setSmoothFramesPercent((float) smoothFramesCount / totalFramesSampled * 100f);
                    activeSession.setMinorStuttersPercent((float) minorStuttersCount / totalFramesSampled * 100f);
                    activeSession.setMajorStuttersPercent((float) majorStuttersCount / totalFramesSampled * 100f);
                }

                // Time series sample (limit to 32 points)
                List<Float> samples = activeSession.getFpsSamples();
                if (samples.size() >= 32) samples.remove(0);
                samples.add(fps);
                activeSession.setFpsSamples(samples);
            }
        }

        // 2. Thermals
        if (stats != null) {
            float temp = stats.getBatteryTemperatureC();
            sessionTempBuffer.add(temp);

            if (temp > activeSession.getPeakTempC()) {
                activeSession.setPeakTempC(temp);
            }
            float tSum = 0f;
            for (Float t : sessionTempBuffer) tSum += t;
            activeSession.setAvgTempC(tSum / sessionTempBuffer.size());
            activeSession.setTempDeltaC(Math.max(0f, activeSession.getPeakTempC() - activeSession.getStartTempC()));

            if (temp >= 45.0f) {
                activeSession.setThermalStatus("Throttling Possible");
                activeSession.setThrottlingEvents(activeSession.getThrottlingEvents() + 1);
            } else if (temp >= 40.0f) {
                activeSession.setThermalStatus("Warm");
            } else {
                activeSession.setThermalStatus("Normal");
            }

            List<Float> tSamples = activeSession.getTempSamples();
            if (tSamples.size() >= 32) tSamples.remove(0);
            tSamples.add(temp);
            activeSession.setTempSamples(tSamples);

            // 3. Battery
            int currentBat = stats.getBatteryLevel();
            activeSession.setEndBatteryLevel(currentBat);
            int consumed = Math.max(0, activeSession.getStartBatteryLevel() - currentBat);
            activeSession.setBatteryConsumedPercent(consumed);

            float hours = Math.max(0.001f, (float) duration / (1000f * 3600f));
            float drainRate = consumed / hours;
            activeSession.setAvgDrainRatePerHour(drainRate);

            // Estimated power watts: approx 4.0V * (drainRate * 50mAh / 1000) ~ 3.5 - 5.5W
            float estWatts = Math.max(2.5f, Math.min(10.0f, (drainRate / 100f) * 4500f * 4.0f / 1000f));
            activeSession.setEstimatedPowerWatts(estWatts);
        }

        // 4. CPU & GPU
        if (cpuInfo != null) {
            int cpu = cpuInfo.getUsagePercentage();
            sessionCpuBuffer.add(cpu);
            if (cpu > activeSession.getPeakCpuUsage()) activeSession.setPeakCpuUsage(cpu);
            int cpuSum = 0;
            for (Integer c : sessionCpuBuffer) cpuSum += c;
            activeSession.setAvgCpuUsage(cpuSum / sessionCpuBuffer.size());

            List<Float> cSamples = activeSession.getCpuSamples();
            if (cSamples.size() >= 32) cSamples.remove(0);
            cSamples.add((float) cpu);
            activeSession.setCpuSamples(cSamples);
        }

        if (gpuInfo != null) {
            int gpu = Math.max(0, gpuInfo.getGpuUsagePercentage());
            sessionGpuBuffer.add(gpu);
            if (gpu > activeSession.getPeakGpuUsage()) activeSession.setPeakGpuUsage(gpu);
            int gpuSum = 0;
            for (Integer g : sessionGpuBuffer) gpuSum += g;
            activeSession.setAvgGpuUsage(gpuSum / sessionGpuBuffer.size());

            List<Float> gSamples = activeSession.getGpuSamples();
            if (gSamples.size() >= 32) gSamples.remove(0);
            gSamples.add((float) gpu);
            activeSession.setGpuSamples(gSamples);
        }

        // 5. Compute Automated Performance Grade
        computeGrade(activeSession);
    }

    private void computeGrade(GameSession s) {
        if (s == null) return;

        float stability = s.getStabilityScorePercent();
        float peakTemp = s.getPeakTempC();
        float drainRate = s.getAvgDrainRatePerHour();

        // Sub ratings
        if (stability >= 92f) s.setFpsStabilityRating("Excellent");
        else if (stability >= 82f) s.setFpsStabilityRating("Good");
        else if (stability >= 70f) s.setFpsStabilityRating("Fair");
        else s.setFpsStabilityRating("Poor");

        if (peakTemp <= 39f) s.setThermalRating("Optimal");
        else if (peakTemp <= 43f) s.setThermalRating("Good");
        else if (peakTemp <= 47f) s.setThermalRating("Warm");
        else s.setThermalRating("Hot");

        if (drainRate <= 12f) s.setBatteryImpactRating("Low");
        else if (drainRate <= 20f) s.setBatteryImpactRating("Moderate");
        else s.setBatteryImpactRating("High");

        // Composite scoring (100 pts)
        float score = (stability * 0.40f)
                + (s.getSmoothFramesPercent() * 0.25f)
                + (Math.max(0f, 100f - (peakTemp - 30f) * 4f) * 0.20f)
                + (Math.max(0f, 100f - drainRate * 3f) * 0.15f);

        if (score >= 90f) s.setGrade("A+");
        else if (score >= 80f) s.setGrade("A");
        else if (score >= 70f) s.setGrade("B");
        else if (score >= 58f) s.setGrade("C");
        else s.setGrade("D");
    }

    public synchronized void finalizeActiveSession() {
        if (activeSession != null) {
            activeSession.setRunning(false);
            activeSession.setEndTimeMs(System.currentTimeMillis());
            activeSession.setDurationMs(activeSession.getEndTimeMs() - activeSession.getStartTimeMs());

            // Only persist meaningful sessions longer than 5 seconds
            if (activeSession.getDurationMs() >= 5000) {
                historyManager.saveSession(activeSession);
            }
            lastCompletedSession = activeSession;
            activeSession = null;
        }
    }
}
