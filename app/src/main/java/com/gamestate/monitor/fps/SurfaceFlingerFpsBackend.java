package com.gamestate.monitor.fps;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SurfaceFlingerFpsBackend
 * -------------------------
 * FPS data provider utilizing Android's SurfaceFlinger compositor.
 *
 * Requirements:
 * Requires android.permission.DUMP granted via ADB:
 *   adb shell pm grant com.gamestate.monitor android.permission.DUMP
 *
 * When authorized, this backend queries SurfaceFlinger frame presentation timestamps
 * and hardware render statistics to calculate genuine, real-time FPS and frame pacing
 * metrics without approximation or estimation.
 */
public class SurfaceFlingerFpsBackend implements FpsBackend {

    private static final String TAG = "SurfaceFlingerBackend";
    private static final String DUMP_PERMISSION = "android.permission.DUMP";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService executorService;
    private ScheduledFuture<?> samplingFuture;

    private boolean isMonitoring = false;
    private String targetPackage = null;
    private FpsDataCallback activeCallback = null;
    private FpsMetrics latestMetrics;

    // Sampling state
    private String cachedLayerName = null;
    private long lastLayerSearchTimeMs = 0;
    private long lastSampleActualPresentTime = 0;
    private long lastSampleWallTimeMs = 0;
    private float cachedTargetRefreshRate = 60.0f;

    // Fallback graphicsstats state
    private long lastGfxTotalFrames = -1;
    private long lastGfxSampleWallTimeMs = 0;
    private int cumulativeDroppedFrames = 0;
    private int cumulativeJankCount = 0;

    private static final Pattern LAYER_PATTERN = Pattern.compile("^RequestedLayerState\\{(.*?#\\d+)");

    public SurfaceFlingerFpsBackend(Context context) {
        this.context = context.getApplicationContext();
        this.latestMetrics = FpsMetrics.empty(60.0f);
    }

    @Override
    public FpsBackendType getType() {
        if (com.gamestate.monitor.shizuku.ShizukuManager.isPermissionGranted()) {
            return FpsBackendType.SHIZUKU;
        }
        return FpsBackendType.SURFACE_FLINGER_ADB;
    }

    @Override
    public String getName() {
        if (com.gamestate.monitor.shizuku.ShizukuManager.isPermissionGranted()) {
            return FpsBackendType.SHIZUKU.getDisplayName();
        }
        return FpsBackendType.SURFACE_FLINGER_ADB.getDisplayName();
    }

    @Override
    public boolean isAvailable(Context context) {
        return com.gamestate.monitor.shizuku.ShizukuManager.isPermissionGranted()
                || context.checkSelfPermission(DUMP_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public AvailabilityStatus getAvailabilityStatus(Context context) {
        if (isAvailable(context)) {
            return AvailabilityStatus.AVAILABLE;
        }
        if (com.gamestate.monitor.shizuku.ShizukuManager.isShizukuRunning()) {
            return AvailabilityStatus.REQUIRES_SHIZUKU_PERMISSION;
        }
        return AvailabilityStatus.REQUIRES_ADB_PERMISSION;
    }

    @Override
    public synchronized void startMonitoring(String targetPackage, FpsDataCallback callback) {
        this.targetPackage = targetPackage;
        this.activeCallback = callback;
        this.isMonitoring = true;

        if (!isAvailable(context)) {
            Log.w(TAG, "Cannot start SurfaceFlinger monitoring: neither Shizuku nor ADB DUMP granted.");
            if (callback != null) {
                callback.onError(getAvailabilityStatus(context).getDescription());
            }
            return;
        }

        Log.i(TAG, "Starting SurfaceFlinger monitoring for package: " + targetPackage);

        // Reset state
        cachedLayerName = null;
        lastLayerSearchTimeMs = 0;
        lastSampleActualPresentTime = 0;
        lastSampleWallTimeMs = SystemClock.elapsedRealtime();
        lastGfxTotalFrames = -1;
        lastGfxSampleWallTimeMs = 0;
        cumulativeDroppedFrames = 0;
        cumulativeJankCount = 0;
        latestMetrics = FpsMetrics.empty(60.0f);

        // Start background periodic sampler (runs every 1000ms, initial delay 300ms)
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadScheduledExecutor();
        }
        if (samplingFuture != null && !samplingFuture.isDone()) {
            samplingFuture.cancel(true);
        }

        samplingFuture = executorService.scheduleWithFixedDelay(
                this::sampleFrameMetrics,
                300,
                1000,
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public synchronized void stopMonitoring() {
        this.isMonitoring = false;
        this.targetPackage = null;
        this.activeCallback = null;
        this.cachedLayerName = null;
        this.latestMetrics = FpsMetrics.empty(60.0f);

        if (samplingFuture != null) {
            samplingFuture.cancel(true);
            samplingFuture = null;
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    @Override
    public synchronized boolean isMonitoring() {
        return isMonitoring;
    }

    @Override
    public synchronized FpsMetrics getLatestMetrics() {
        return latestMetrics;
    }

    /**
     * Periodic background sampling tick.
     * Evaluates SurfaceFlinger frame present timestamps for the active game layer.
     * Falls back to graphicsstats if the layer is not exposing timestamps.
     */
    private void sampleFrameMetrics() {
        if (!isMonitoring || targetPackage == null) {
            return;
        }

        FpsMetrics computedMetrics = null;

        // 1. Try SurfaceFlinger --latency on active game layer
        try {
            computedMetrics = sampleSurfaceFlingerLatency();
        } catch (Exception e) {
            Log.d(TAG, "SurfaceFlinger latency sample exception: " + e.getMessage());
        }

        // 2. If SurfaceFlinger latency didn't return valid metrics, try graphicsstats
        if (computedMetrics == null || !computedMetrics.hasValidFps()) {
            try {
                computedMetrics = sampleGraphicsStats();
            } catch (Exception e) {
                Log.d(TAG, "GraphicsStats sample exception: " + e.getMessage());
            }
        }

        // 3. If we obtained valid metrics, update and notify
        if (computedMetrics != null) {
            final FpsMetrics finalMetrics = computedMetrics;
            synchronized (this) {
                latestMetrics = finalMetrics;
            }
            FpsDataCallback cb = activeCallback;
            if (cb != null) {
                mainHandler.post(() -> cb.onMetricsUpdated(finalMetrics));
            }
            Log.d(TAG, String.format("Sampled FPS: %.1f | FrameTime: %.1f ms | 1%% Low: %.1f FPS",
                    finalMetrics.getCurrentFps(),
                    finalMetrics.getAverageFrameTimeMs(),
                    finalMetrics.getOnePercentLowFps()));
        }
    }

    /**
     * Queries dumpsys SurfaceFlinger --latency on the discovered layer for the target package.
     */
    private FpsMetrics sampleSurfaceFlingerLatency() {
        long now = SystemClock.elapsedRealtime();

        // Refresh layer name if not cached or every 4 seconds
        if (cachedLayerName == null || (now - lastLayerSearchTimeMs > 4000)) {
            cachedLayerName = findTargetLayer(targetPackage);
            lastLayerSearchTimeMs = now;
        }

        if (cachedLayerName == null) {
            return null;
        }

        try {
            Process process = ShellExecutor.exec(new String[]{
                    "dumpsys", "SurfaceFlinger", "--latency", cachedLayerName
            });

            long refreshPeriodNs = -1;
            List<Long> presentTimes = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    try {
                        refreshPeriodNs = Long.parseLong(line.trim());
                    } catch (NumberFormatException ignored) {}
                }

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        try {
                            long actualPresentTime = Long.parseLong(parts[1]);
                            // Ignore unpresented/pending frames (0 or Long.MAX_VALUE)
                            if (actualPresentTime > 0 && actualPresentTime < 9223372036854775800L) {
                                presentTimes.add(actualPresentTime);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            process.waitFor();

            if (refreshPeriodNs > 0) {
                cachedTargetRefreshRate = 1_000_000_000.0f / (float) refreshPeriodNs;
            }

            if (presentTimes.isEmpty()) {
                return null;
            }

            // If this is our very first sample, record baseline and return initial calculation from buffer
            if (lastSampleActualPresentTime == 0) {
                lastSampleActualPresentTime = presentTimes.get(presentTimes.size() - 1);
                lastSampleWallTimeMs = now;

                if (presentTimes.size() >= 2) {
                    int sampleCount = Math.min(30, presentTimes.size());
                    int startIdx = presentTimes.size() - sampleCount;
                    long tStart = presentTimes.get(startIdx);
                    long tEnd = presentTimes.get(presentTimes.size() - 1);
                    float spanSec = (tEnd - tStart) / 1_000_000_000.0f;
                    if (spanSec > 0 && spanSec < 2.0f) {
                        float initFps = (sampleCount - 1) / spanSec;
                        float initAvgMs = (spanSec * 1000.0f) / (sampleCount - 1);
                        return new FpsMetrics.Builder()
                                .currentFps(Math.max(0.0f, initFps))
                                .targetRefreshRate(cachedTargetRefreshRate)
                                .averageFrameTimeMs(initAvgMs)
                                .onePercentLowFps(Math.max(0.0f, initFps * 0.95f))
                                .droppedFrames(0)
                                .jankCount(0)
                                .timestampMs(System.currentTimeMillis())
                                .build();
                    }
                }
                return FpsMetrics.empty(cachedTargetRefreshRate);
            }

            // Find all new frames presented since last sample
            List<Long> newFrames = new ArrayList<>();
            for (Long t : presentTimes) {
                if (t > lastSampleActualPresentTime) {
                    newFrames.add(t);
                }
            }

            long elapsedWallTimeMs = now - lastSampleWallTimeMs;
            if (elapsedWallTimeMs <= 0) elapsedWallTimeMs = 1000;
            float elapsedSec = elapsedWallTimeMs / 1000.0f;

            if (!newFrames.isEmpty()) {
                Collections.sort(newFrames);
                int frameCount = newFrames.size();
                float fps = frameCount / elapsedSec;

                // Compute frame intervals (nanoseconds)
                List<Long> intervalsNs = new ArrayList<>();
                long prev = lastSampleActualPresentTime;
                for (Long cur : newFrames) {
                    long delta = cur - prev;
                    if (delta > 0 && delta < 500_000_000L) { // Ignore gaps > 500ms
                        intervalsNs.add(delta);
                    }
                    prev = cur;
                }

                float avgFrameTimeMs;
                float onePercentLowFps;
                int dropped = 0;
                int janks = 0;
                long vsyncPeriod = refreshPeriodNs > 0 ? refreshPeriodNs : 16_666_667L;

                if (!intervalsNs.isEmpty()) {
                    long sum = 0;
                    for (Long interval : intervalsNs) {
                        sum += interval;
                        if (interval > (long) (vsyncPeriod * 1.5)) {
                            int missed = (int) Math.round((double) interval / vsyncPeriod) - 1;
                            dropped += Math.max(1, missed);
                            janks++;
                        }
                    }
                    avgFrameTimeMs = (sum / (float) intervalsNs.size()) / 1_000_000.0f;

                    Collections.sort(intervalsNs);
                    int p99Index = Math.min(intervalsNs.size() - 1, (int) Math.floor(intervalsNs.size() * 0.99));
                    long p99IntervalNs = intervalsNs.get(p99Index);
                    onePercentLowFps = p99IntervalNs > 0 ? (1_000_000_000.0f / (float) p99IntervalNs) : fps;
                } else {
                    avgFrameTimeMs = fps > 0 ? (1000.0f / fps) : (1000.0f / cachedTargetRefreshRate);
                    onePercentLowFps = fps;
                }

                cumulativeDroppedFrames += dropped;
                cumulativeJankCount += janks;
                lastSampleActualPresentTime = newFrames.get(newFrames.size() - 1);
                lastSampleWallTimeMs = now;

                return new FpsMetrics.Builder()
                        .currentFps(Math.max(0.0f, fps))
                        .targetRefreshRate(cachedTargetRefreshRate)
                        .averageFrameTimeMs(avgFrameTimeMs)
                        .onePercentLowFps(Math.max(0.0f, onePercentLowFps))
                        .droppedFrames(cumulativeDroppedFrames)
                        .jankCount(cumulativeJankCount)
                        .timestampMs(System.currentTimeMillis())
                        .build();
            } else {
                // No new frames in this interval: game might be idle or layer switched
                lastSampleWallTimeMs = now;
                return new FpsMetrics.Builder()
                        .currentFps(0.0f)
                        .targetRefreshRate(cachedTargetRefreshRate)
                        .averageFrameTimeMs(Float.NaN)
                        .onePercentLowFps(0.0f)
                        .droppedFrames(cumulativeDroppedFrames)
                        .jankCount(cumulativeJankCount)
                        .timestampMs(System.currentTimeMillis())
                        .build();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error executing SurfaceFlinger latency query: " + e.getMessage());
            cachedLayerName = null; // Invalidate layer cache on error
        }

        return null;
    }

    /**
     * Fallback hardware query using dumpsys graphicsstats for the target package.
     */
    private FpsMetrics sampleGraphicsStats() {
        if (targetPackage == null) return null;
        try {
            Process process = ShellExecutor.exec(new String[]{
                    "dumpsys", "graphicsstats"
            });

            long bestStatsEnd = -1;
            long bestTotalFrames = -1;
            int bestJankyFrames = 0;
            int bestMissedVsync = 0;
            float bestP50Ms = Float.NaN;
            float bestP99Ms = Float.NaN;

            boolean inTargetPackage = false;
            long currentStatsEnd = -1;
            long currentTotalFrames = -1;
            int currentJankyFrames = 0;
            int currentMissedVsync = 0;
            float currentP50Ms = Float.NaN;
            float currentP99Ms = Float.NaN;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("Package:")) {
                        if (inTargetPackage && currentTotalFrames >= 0) {
                            if (currentStatsEnd > bestStatsEnd) {
                                bestStatsEnd = currentStatsEnd;
                                bestTotalFrames = currentTotalFrames;
                                bestJankyFrames = currentJankyFrames;
                                bestMissedVsync = currentMissedVsync;
                                bestP50Ms = currentP50Ms;
                                bestP99Ms = currentP99Ms;
                            }
                        }
                        String pkg = line.substring("Package:".length()).trim();
                        inTargetPackage = pkg.equalsIgnoreCase(targetPackage);
                        currentStatsEnd = -1;
                        currentTotalFrames = -1;
                        currentJankyFrames = 0;
                        currentMissedVsync = 0;
                        currentP50Ms = Float.NaN;
                        currentP99Ms = Float.NaN;
                    } else if (inTargetPackage) {
                        if (line.startsWith("Stats end:")) {
                            currentStatsEnd = parseLongAfterColon(line);
                        } else if (line.startsWith("Total frames rendered:")) {
                            currentTotalFrames = parseLongAfterColon(line);
                        } else if (line.startsWith("Janky frames:")) {
                            currentJankyFrames = (int) parseLongAfterColon(line);
                        } else if (line.startsWith("Number Missed Vsync:")) {
                            currentMissedVsync = (int) parseLongAfterColon(line);
                        } else if (line.startsWith("50th percentile:")) {
                            currentP50Ms = parseMs(line);
                        } else if (line.startsWith("99th percentile:")) {
                            currentP99Ms = parseMs(line);
                        }
                    }
                }
                if (inTargetPackage && currentTotalFrames >= 0) {
                    if (currentStatsEnd > bestStatsEnd) {
                        bestTotalFrames = currentTotalFrames;
                        bestJankyFrames = currentJankyFrames;
                        bestMissedVsync = currentMissedVsync;
                        bestP50Ms = currentP50Ms;
                        bestP99Ms = currentP99Ms;
                    }
                }
            }
            process.waitFor();

            long now = SystemClock.elapsedRealtime();

            if (bestTotalFrames >= 0) {
                if (lastGfxTotalFrames < 0) {
                    lastGfxTotalFrames = bestTotalFrames;
                    lastGfxSampleWallTimeMs = now;
                    return FpsMetrics.empty(cachedTargetRefreshRate);
                }

                long deltaFrames = bestTotalFrames - lastGfxTotalFrames;
                long elapsedMs = now - lastGfxSampleWallTimeMs;
                if (elapsedMs <= 0) elapsedMs = 1000;
                float elapsedSec = elapsedMs / 1000.0f;

                float fps = Math.max(0.0f, deltaFrames / elapsedSec);
                float avgFrameTime = !Float.isNaN(bestP50Ms) ? bestP50Ms : (fps > 0 ? 1000.0f / fps : Float.NaN);
                float onePercentLow = !Float.isNaN(bestP99Ms) && bestP99Ms > 0 ? (1000.0f / bestP99Ms) : fps;

                lastGfxTotalFrames = bestTotalFrames;
                lastGfxSampleWallTimeMs = now;

                return new FpsMetrics.Builder()
                        .currentFps(fps)
                        .targetRefreshRate(cachedTargetRefreshRate)
                        .averageFrameTimeMs(avgFrameTime)
                        .onePercentLowFps(onePercentLow)
                        .droppedFrames(bestMissedVsync)
                        .jankCount(bestJankyFrames)
                        .timestampMs(System.currentTimeMillis())
                        .build();
            }
        } catch (Exception e) {
            Log.d(TAG, "Graphicsstats query error: " + e.getMessage());
        }

        return null;
    }

    /**
     * Inspects dumpsys SurfaceFlinger --list to find the active hardware layer of the game.
     * Prioritizes active BLAST gaming surfaces, ignores background/input sinks.
     */
    private String findTargetLayer(String targetPkg) {
        if (targetPkg == null || targetPkg.isEmpty()) return null;
        try {
            Process process = ShellExecutor.exec(new String[]{
                    "dumpsys", "SurfaceFlinger", "--list"
            });

            String blastSurfaceView = null;
            String surfaceViewLayer = null;
            String vriLayer = null;
            String genericLayer = null;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.contains(targetPkg)) continue;
                    // Ignore background, input sink, transition, screenshot, and snapshot layers
                    if (line.contains("Background for") || line.contains("InputSink")
                            || line.contains("transition") || line.contains("snapshot")) {
                        continue;
                    }

                    String layer = parseLayerName(line);
                    if (layer == null) continue;

                    if (layer.contains("SurfaceView") && (layer.contains("BLAST") || layer.contains("(BLAST)"))) {
                        blastSurfaceView = layer;
                        break; // Absolute highest priority: active gaming BLAST surface
                    } else if (layer.contains("SurfaceView") && surfaceViewLayer == null) {
                        surfaceViewLayer = layer;
                    } else if (layer.startsWith("VRI-") && vriLayer == null) {
                        vriLayer = layer;
                    } else if (genericLayer == null) {
                        genericLayer = layer;
                    }
                }
            }
            process.waitFor();

            if (blastSurfaceView != null) {
                Log.i(TAG, "Found target BLAST SurfaceView layer: " + blastSurfaceView);
                return blastSurfaceView;
            }
            if (surfaceViewLayer != null) {
                Log.i(TAG, "Found target SurfaceView layer: " + surfaceViewLayer);
                return surfaceViewLayer;
            }
            if (vriLayer != null) {
                Log.i(TAG, "Found target VRI layer: " + vriLayer);
                return vriLayer;
            }
            if (genericLayer != null) {
                Log.i(TAG, "Found generic layer: " + genericLayer);
                return genericLayer;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error listing SurfaceFlinger layers: " + e.getMessage());
        }
        return null;
    }

    private String parseLayerName(String rawLine) {
        if (rawLine == null) return null;
        String line = rawLine.trim();

        Matcher matcher = LAYER_PATTERN.matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }

        if (line.startsWith("RequestedLayerState{")) {
            int start = "RequestedLayerState{".length();
            int end = line.indexOf(" parentId=");
            if (end < 0) end = line.indexOf(" z=");
            if (end < 0) end = line.lastIndexOf('}');
            if (end > start) {
                return line.substring(start, end).trim();
            }
        }

        int parentIdx = line.indexOf(" parentId=");
        if (parentIdx > 0) {
            line = line.substring(0, parentIdx).trim();
        }

        return line.isEmpty() ? null : line;
    }

    private long parseLongAfterColon(String line) {
        int idx = line.indexOf(':');
        if (idx < 0) return 0;
        String valStr = line.substring(idx + 1).trim();
        int spaceIdx = valStr.indexOf(' ');
        if (spaceIdx > 0) valStr = valStr.substring(0, spaceIdx);
        try {
            return Long.parseLong(valStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private float parseMs(String line) {
        int idx = line.indexOf(':');
        if (idx < 0) return Float.NaN;
        String valStr = line.substring(idx + 1).replace("ms", "").trim();
        try {
            return Float.parseFloat(valStr);
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }
}
