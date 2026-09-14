package com.gamestate.monitor.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.gamestate.monitor.fps.FpsBackend;
import com.gamestate.monitor.fps.FpsBackendManager;
import com.gamestate.monitor.fps.FpsDataCallback;
import com.gamestate.monitor.fps.FpsMetrics;
import com.gamestate.monitor.fps.FpsMonitorState;
import com.gamestate.monitor.fps.GameDetector;
import com.gamestate.monitor.fps.GameStateInfo;

/**
 * GameStateService
 * ----------------
 * Service responsible for monitoring game state and coordinating the FPS backend.
 *
 * Responsibilities:
 * 1. Periodically detects active foreground game packages using GameDetector.
 * 2. Evaluates FPS backend readiness (ADB SurfaceFlinger, Root, Fallback).
 * 3. Transitions between:
 *    - "No Game Detected"
 *    - "Waiting for supported FPS backend"
 *    - "FPS Monitoring Active"
 * 4. Dispatches zero-delay state changes to the UI layer and Floating HUD.
 */
public class GameStateService extends Service implements FpsDataCallback {

    private static final String TAG = "GameStateService";

    public static final String ACTION_GAME_STATE_UPDATED = "com.gamestate.monitor.ACTION_GAME_STATE_UPDATED";
    public static final String EXTRA_MONITOR_STATE = "monitor_state";
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    public static final String EXTRA_APP_NAME = "app_name";

    public static final String ACTION_START_RECORDING = "com.gamestate.monitor.ACTION_START_RECORDING";
    public static final String ACTION_STOP_RECORDING = "com.gamestate.monitor.ACTION_STOP_RECORDING";

    // Singleton state snapshot for immediate synchronous UI polling
    private static volatile GameStateInfo currentGameState = GameStateInfo.none();
    private static volatile FpsMonitorState currentMonitorState = FpsMonitorState.NO_GAME_DETECTED;
    private static volatile FpsMetrics currentMetrics = FpsMetrics.empty(60.0f);
    private static volatile FpsBackend currentActiveBackend = null;
    private static volatile boolean isServiceRunning = false;

    private final IBinder binder = new LocalBinder();
    private final Handler loopHandler = new Handler(Looper.getMainLooper());
    private static final long SCAN_INTERVAL_MS = 1000;

    private GameDetector gameDetector;
    private FpsBackendManager backendManager;
    private FpsBackend activeBackend;
    private String monitoredPackage = null;

    private SessionAnalyticsTracker analyticsTracker;
    private com.gamestate.monitor.util.DeviceStatsManager statsManager;
    private com.gamestate.monitor.util.CpuMonitor cpuMonitor;
    private com.gamestate.monitor.util.GpuMonitor gpuMonitor;

    public class LocalBinder extends Binder {
        public GameStateService getService() {
            return GameStateService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isServiceRunning = true;
        gameDetector = new GameDetector(this);
        backendManager = new FpsBackendManager(this, 60.0f);
        activeBackend = backendManager.getActiveBackend();
        currentActiveBackend = activeBackend;

        analyticsTracker = SessionAnalyticsTracker.getInstance(this);
        statsManager = new com.gamestate.monitor.util.DeviceStatsManager(this);
        cpuMonitor = new com.gamestate.monitor.util.CpuMonitor();
        gpuMonitor = new com.gamestate.monitor.util.GpuMonitor();

        // Start scanning cycle
        loopHandler.removeCallbacks(scanRunnable);
        loopHandler.post(scanRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_RECORDING.equals(action)) {
                if (analyticsTracker != null) {
                    com.gamestate.monitor.model.PerformanceStats stats = statsManager != null ? statsManager.getPerformanceStats() : null;
                    analyticsTracker.startRecording(currentGameState, currentMetrics, stats);
                }
            } else if (ACTION_STOP_RECORDING.equals(action)) {
                if (analyticsTracker != null) {
                    analyticsTracker.stopRecording();
                }
            }
        }
        loopHandler.removeCallbacks(scanRunnable);
        loopHandler.post(scanRunnable);
        return START_NOT_STICKY;
    }

    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            // Ultra-light check: verify if any client or recording is active
            boolean appVisible = com.gamestate.monitor.MainActivity.isAppInForeground;
            boolean overlayActive = OverlayService.isRunning;
            boolean isRecording = (analyticsTracker != null && analyticsTracker.isRecording());

            if (!appVisible && !overlayActive && !isRecording) {
                // Zero active clients and no recording in progress -> completely kill service!
                Log.d(TAG, "Zero active clients and no recording in progress. Shutting down GameStateService to preserve battery.");
                stopSelf();
                return;
            }

            evaluateGameState();
            loopHandler.postDelayed(this, SCAN_INTERVAL_MS);
        }
    };

    /**
     * Periodic evaluation cycle for game state and backend synchronization.
     */
    public synchronized void evaluateGameState() {
        if (gameDetector == null || backendManager == null) return;

        // 1. Detect foreground package and game status
        GameStateInfo detectedGame = gameDetector.detectForegroundGame();
        currentGameState = detectedGame;

        // 2. Resolve best available backend
        activeBackend = backendManager.getActiveBackend();
        currentActiveBackend = activeBackend;

        // 3. Determine high-level monitoring state
        FpsMonitorState newState;
        if (!detectedGame.hasGame()) {
            newState = FpsMonitorState.NO_GAME_DETECTED;
            if (activeBackend.isMonitoring()) {
                activeBackend.stopMonitoring();
                monitoredPackage = null;
            }
            currentMetrics = FpsMetrics.empty(60.0f);
        } else {
            // A game is detected in foreground!
            if (backendManager.isSupportedBackendAvailable()) {
                newState = FpsMonitorState.FPS_MONITORING_ACTIVE;
                String targetPkg = detectedGame.getPackageName();
                if (!targetPkg.equals(monitoredPackage) || !activeBackend.isMonitoring()) {
                    activeBackend.stopMonitoring();
                    activeBackend.startMonitoring(targetPkg, this);
                    monitoredPackage = targetPkg;
                }
                currentMetrics = activeBackend.getLatestMetrics();
            } else {
                newState = FpsMonitorState.WAITING_FOR_BACKEND;
                if (activeBackend.isMonitoring()) {
                    activeBackend.stopMonitoring();
                    monitoredPackage = null;
                }
                currentMetrics = FpsMetrics.empty(60.0f);
            }
        }

        currentMonitorState = newState;

        // Feed metrics to SessionAnalyticsTracker strictly when recording is active
        if (analyticsTracker != null && analyticsTracker.isRecording()) {
            com.gamestate.monitor.model.PerformanceStats stats = statsManager != null ? statsManager.getPerformanceStats() : null;
            com.gamestate.monitor.model.CpuInfo cpu = cpuMonitor != null ? cpuMonitor.getCpuInfo() : null;
            com.gamestate.monitor.model.GpuInfo gpu = gpuMonitor != null ? gpuMonitor.sampleGpuInfo() : null;
            analyticsTracker.onTick(detectedGame, currentMetrics, stats, cpu, gpu);
        }

        broadcastStateUpdate(detectedGame, newState);
    }

    private void broadcastStateUpdate(GameStateInfo game, FpsMonitorState state) {
        Intent intent = new Intent(ACTION_GAME_STATE_UPDATED);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_MONITOR_STATE, state.name());
        intent.putExtra(EXTRA_PACKAGE_NAME, game.getPackageName());
        intent.putExtra(EXTRA_APP_NAME, game.getAppName());
        sendBroadcast(intent);
    }

    @Override
    public void onMetricsUpdated(FpsMetrics metrics) {
        if (metrics != null) {
            currentMetrics = metrics;
            broadcastStateUpdate(currentGameState, currentMonitorState);
        }
    }

    @Override
    public void onError(String error) {
        Log.w(TAG, "FPS Backend reported error: " + error);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceRunning = false;
        loopHandler.removeCallbacks(scanRunnable);
        if (analyticsTracker != null) {
            analyticsTracker.finalizeActiveSession();
        }
        if (activeBackend != null && activeBackend.isMonitoring()) {
            activeBackend.stopMonitoring();
        }
        currentMonitorState = FpsMonitorState.STOPPED;
    }

    // Static accessors for instant reading from UI
    public static boolean isRunning() {
        return isServiceRunning;
    }

    public static GameStateInfo getCurrentGameState() {
        return currentGameState;
    }

    public static FpsMonitorState getCurrentMonitorState() {
        return currentMonitorState;
    }

    public static FpsMetrics getCurrentMetrics() {
        return currentMetrics;
    }

    public static FpsBackend getActiveBackend() {
        return currentActiveBackend;
    }

    public GameDetector getGameDetector() {
        return gameDetector;
    }

    public FpsBackendManager getBackendManager() {
        return backendManager;
    }
}
