package com.gamestate.monitor.fps;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * FpsMetrics
 * ----------
 * Immutable data model representing frame pacing and performance metrics.
 *
 * Designed with strict zero-fake-metrics integrity:
 * When a real hardware or SurfaceFlinger data feed is inactive or unconfigured,
 * metrics are explicitly marked as invalid (Float.NaN / -1), preventing any synthetic,
 * guessed, or estimated values from corrupting diagnostic monitoring.
 *
 * Future-ready for:
 * - Real-time instantaneous & average FPS
 * - Frame times (ms) and pacing variance
 * - 1% Low FPS (stutter measurement)
 * - Dropped frame counters
 * - Jank occurrences (missed VSYNC intervals)
 */
public class FpsMetrics implements Serializable {

    private final float currentFps;
    private final float targetRefreshRate;
    private final float averageFrameTimeMs;
    private final float onePercentLowFps;
    private final int droppedFrames;
    private final int jankCount;
    private final List<Float> recentFrameTimesMs;
    private final long timestampMs;

    public FpsMetrics(float currentFps,
                      float targetRefreshRate,
                      float averageFrameTimeMs,
                      float onePercentLowFps,
                      int droppedFrames,
                      int jankCount,
                      List<Float> recentFrameTimesMs,
                      long timestampMs) {
        this.currentFps = currentFps;
        this.targetRefreshRate = targetRefreshRate;
        this.averageFrameTimeMs = averageFrameTimeMs;
        this.onePercentLowFps = onePercentLowFps;
        this.droppedFrames = droppedFrames;
        this.jankCount = jankCount;
        this.recentFrameTimesMs = recentFrameTimesMs != null
                ? Collections.unmodifiableList(recentFrameTimesMs)
                : Collections.emptyList();
        this.timestampMs = timestampMs;
    }

    /**
     * Creates an empty/awaiting metrics object with the screen's target refresh rate.
     */
    public static FpsMetrics empty(float targetRefreshRate) {
        return new FpsMetrics(
                Float.NaN,
                targetRefreshRate > 0 ? targetRefreshRate : 60.0f,
                Float.NaN,
                Float.NaN,
                0,
                0,
                Collections.emptyList(),
                System.currentTimeMillis()
        );
    }

    public boolean hasValidFps() {
        return !Float.isNaN(currentFps) && currentFps >= 0.0f;
    }

    public boolean hasValidFrameTime() {
        return !Float.isNaN(averageFrameTimeMs) && averageFrameTimeMs > 0.0f;
    }

    public boolean hasValidOnePercentLow() {
        return !Float.isNaN(onePercentLowFps) && onePercentLowFps >= 0.0f;
    }

    public float getCurrentFps() {
        return currentFps;
    }

    public float getTargetRefreshRate() {
        return targetRefreshRate;
    }

    public float getAverageFrameTimeMs() {
        return averageFrameTimeMs;
    }

    public float getOnePercentLowFps() {
        return onePercentLowFps;
    }

    public int getDroppedFrames() {
        return droppedFrames;
    }

    public int getJankCount() {
        return jankCount;
    }

    public List<Float> getRecentFrameTimesMs() {
        return recentFrameTimesMs;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    /**
     * Builder for constructing FpsMetrics instances when real data streams are active.
     */
    public static class Builder {
        private float currentFps = Float.NaN;
        private float targetRefreshRate = 60.0f;
        private float averageFrameTimeMs = Float.NaN;
        private float onePercentLowFps = Float.NaN;
        private int droppedFrames = 0;
        private int jankCount = 0;
        private List<Float> recentFrameTimesMs = null;
        private long timestampMs = System.currentTimeMillis();

        public Builder() {}

        public Builder currentFps(float fps) {
            this.currentFps = fps;
            return this;
        }

        public Builder targetRefreshRate(float refreshRate) {
            this.targetRefreshRate = refreshRate;
            return this;
        }

        public Builder averageFrameTimeMs(float frameTimeMs) {
            this.averageFrameTimeMs = frameTimeMs;
            return this;
        }

        public Builder onePercentLowFps(float onePercentLow) {
            this.onePercentLowFps = onePercentLow;
            return this;
        }

        public Builder droppedFrames(int droppedFrames) {
            this.droppedFrames = droppedFrames;
            return this;
        }

        public Builder jankCount(int jankCount) {
            this.jankCount = jankCount;
            return this;
        }

        public Builder recentFrameTimesMs(List<Float> frameTimes) {
            this.recentFrameTimesMs = frameTimes;
            return this;
        }

        public Builder timestampMs(long timestampMs) {
            this.timestampMs = timestampMs;
            return this;
        }

        public FpsMetrics build() {
            return new FpsMetrics(
                    currentFps,
                    targetRefreshRate,
                    averageFrameTimeMs,
                    onePercentLowFps,
                    droppedFrames,
                    jankCount,
                    recentFrameTimesMs,
                    timestampMs
            );
        }
    }
}
