package com.gamestate.monitor.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * GameSession
 * -----------
 * Complete analytical snapshot of a gaming session.
 * Tracks FPS metrics, frame pacing, thermals, CPU/GPU load, battery impact,
 * and automated performance grading.
 */
public class GameSession implements Serializable {

    private String sessionId;
    private String appName;
    private String packageName;
    private long startTimeMs;
    private long endTimeMs;
    private long durationMs;
    private float targetRefreshRate;
    private boolean isRunning;

    // FPS Benchmark Metrics
    private float avgFps;
    private float maxFps;
    private float minFps; // Legacy alias for gameplayMinFps
    private float gameplayMinFps;
    private float absoluteMinFps;
    private float onePercentLowFps;
    private float pointOnePercentLowFps;
    private float fpsVariance;
    private float stabilityScorePercent;
    private int droppedFrames;
    private int stutterEvents;

    // Thermals
    private float startTempC;
    private float avgTempC;
    private float peakTempC;
    private float tempDeltaC;
    private String thermalStatus; // "Normal", "Warm", "Throttling"
    private int throttlingEvents;

    // CPU & GPU Analytics
    private int avgCpuUsage;
    private int peakCpuUsage;
    private int avgGpuUsage;
    private int peakGpuUsage;

    // Battery Impact
    private int startBatteryLevel;
    private int endBatteryLevel;
    private int batteryConsumedPercent;
    private float avgDrainRatePerHour;
    private float estimatedPowerWatts;

    // Frame Time Distribution
    private float smoothFramesPercent; // < 16.6ms
    private float minorStuttersPercent; // 16.6ms - 33.3ms
    private float majorStuttersPercent; // > 33.3ms

    // Performance Grade
    private String grade; // "A+", "A", "B", "C", "D"
    private String fpsStabilityRating; // "Excellent", "Good", "Fair", "Poor"
    private String thermalRating;      // "Optimal", "Good", "Warm", "Hot"
    private String batteryImpactRating;// "Low", "Moderate", "High"

    // Time-series sample buffers for trend charts
    private List<Float> fpsSamples = new ArrayList<>();
    private List<Float> tempSamples = new ArrayList<>();
    private List<Float> cpuSamples = new ArrayList<>();
    private List<Float> gpuSamples = new ArrayList<>();

    public GameSession() {
        this.sessionId = String.valueOf(System.currentTimeMillis());
        this.appName = "Unknown Game";
        this.packageName = "";
        this.startTimeMs = System.currentTimeMillis();
        this.targetRefreshRate = 60.0f;
        this.isRunning = true;
        this.thermalStatus = "Normal";
        this.grade = "A";
        this.fpsStabilityRating = "Good";
        this.thermalRating = "Optimal";
        this.batteryImpactRating = "Low";
    }

    public GameSession(String sessionId, String appName, String packageName, long startTimeMs, float targetRefreshRate) {
        this();
        this.sessionId = sessionId;
        this.appName = appName;
        this.packageName = packageName;
        this.startTimeMs = startTimeMs;
        this.targetRefreshRate = targetRefreshRate > 0 ? targetRefreshRate : 60.0f;
    }

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public long getStartTimeMs() { return startTimeMs; }
    public void setStartTimeMs(long startTimeMs) { this.startTimeMs = startTimeMs; }

    public long getEndTimeMs() { return endTimeMs; }
    public void setEndTimeMs(long endTimeMs) { this.endTimeMs = endTimeMs; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public float getTargetRefreshRate() { return targetRefreshRate; }
    public void setTargetRefreshRate(float targetRefreshRate) { this.targetRefreshRate = targetRefreshRate; }

    public boolean isRunning() { return isRunning; }
    public void setRunning(boolean running) { isRunning = running; }

    public float getAvgFps() { return avgFps; }
    public void setAvgFps(float avgFps) { this.avgFps = avgFps; }

    public float getMaxFps() { return maxFps; }
    public void setMaxFps(float maxFps) { this.maxFps = maxFps; }

    public float getMinFps() { return gameplayMinFps > 0.0f ? gameplayMinFps : minFps; }
    public void setMinFps(float minFps) {
        this.minFps = minFps;
        if (this.gameplayMinFps <= 0.0f) this.gameplayMinFps = minFps;
    }

    public float getGameplayMinFps() { return gameplayMinFps > 0.0f ? gameplayMinFps : minFps; }
    public void setGameplayMinFps(float gameplayMinFps) {
        this.gameplayMinFps = gameplayMinFps;
        this.minFps = gameplayMinFps;
    }

    public float getAbsoluteMinFps() { return absoluteMinFps; }
    public void setAbsoluteMinFps(float absoluteMinFps) { this.absoluteMinFps = absoluteMinFps; }

    public float getOnePercentLowFps() { return onePercentLowFps; }
    public void setOnePercentLowFps(float onePercentLowFps) { this.onePercentLowFps = onePercentLowFps; }

    public float getPointOnePercentLowFps() { return pointOnePercentLowFps; }
    public void setPointOnePercentLowFps(float pointOnePercentLowFps) { this.pointOnePercentLowFps = pointOnePercentLowFps; }

    public float getFpsVariance() { return fpsVariance; }
    public void setFpsVariance(float fpsVariance) { this.fpsVariance = fpsVariance; }

    public float getStabilityScorePercent() { return stabilityScorePercent; }
    public void setStabilityScorePercent(float stabilityScorePercent) { this.stabilityScorePercent = stabilityScorePercent; }

    public int getDroppedFrames() { return droppedFrames; }
    public void setDroppedFrames(int droppedFrames) { this.droppedFrames = droppedFrames; }

    public int getStutterEvents() { return stutterEvents; }
    public void setStutterEvents(int stutterEvents) { this.stutterEvents = stutterEvents; }

    public float getStartTempC() { return startTempC; }
    public void setStartTempC(float startTempC) { this.startTempC = startTempC; }

    public float getAvgTempC() { return avgTempC; }
    public void setAvgTempC(float avgTempC) { this.avgTempC = avgTempC; }

    public float getPeakTempC() { return peakTempC; }
    public void setPeakTempC(float peakTempC) { this.peakTempC = peakTempC; }

    public float getTempDeltaC() { return tempDeltaC; }
    public void setTempDeltaC(float tempDeltaC) { this.tempDeltaC = tempDeltaC; }

    public String getThermalStatus() { return thermalStatus; }
    public void setThermalStatus(String thermalStatus) { this.thermalStatus = thermalStatus; }

    public int getThrottlingEvents() { return throttlingEvents; }
    public void setThrottlingEvents(int throttlingEvents) { this.throttlingEvents = throttlingEvents; }

    public int getAvgCpuUsage() { return avgCpuUsage; }
    public void setAvgCpuUsage(int avgCpuUsage) { this.avgCpuUsage = avgCpuUsage; }

    public int getPeakCpuUsage() { return peakCpuUsage; }
    public void setPeakCpuUsage(int peakCpuUsage) { this.peakCpuUsage = peakCpuUsage; }

    public int getAvgGpuUsage() { return avgGpuUsage; }
    public void setAvgGpuUsage(int avgGpuUsage) { this.avgGpuUsage = avgGpuUsage; }

    public int getPeakGpuUsage() { return peakGpuUsage; }
    public void setPeakGpuUsage(int peakGpuUsage) { this.peakGpuUsage = peakGpuUsage; }

    public int getStartBatteryLevel() { return startBatteryLevel; }
    public void setStartBatteryLevel(int startBatteryLevel) { this.startBatteryLevel = startBatteryLevel; }

    public int getEndBatteryLevel() { return endBatteryLevel; }
    public void setEndBatteryLevel(int endBatteryLevel) { this.endBatteryLevel = endBatteryLevel; }

    public int getBatteryConsumedPercent() { return batteryConsumedPercent; }
    public void setBatteryConsumedPercent(int batteryConsumedPercent) { this.batteryConsumedPercent = batteryConsumedPercent; }

    public float getAvgDrainRatePerHour() { return avgDrainRatePerHour; }
    public void setAvgDrainRatePerHour(float avgDrainRatePerHour) { this.avgDrainRatePerHour = avgDrainRatePerHour; }

    public float getEstimatedPowerWatts() { return estimatedPowerWatts; }
    public void setEstimatedPowerWatts(float estimatedPowerWatts) { this.estimatedPowerWatts = estimatedPowerWatts; }

    public float getSmoothFramesPercent() { return smoothFramesPercent; }
    public void setSmoothFramesPercent(float smoothFramesPercent) { this.smoothFramesPercent = smoothFramesPercent; }

    public float getMinorStuttersPercent() { return minorStuttersPercent; }
    public void setMinorStuttersPercent(float minorStuttersPercent) { this.minorStuttersPercent = minorStuttersPercent; }

    public float getMajorStuttersPercent() { return majorStuttersPercent; }
    public void setMajorStuttersPercent(float majorStuttersPercent) { this.majorStuttersPercent = majorStuttersPercent; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getFpsStabilityRating() { return fpsStabilityRating; }
    public void setFpsStabilityRating(String fpsStabilityRating) { this.fpsStabilityRating = fpsStabilityRating; }

    public String getThermalRating() { return thermalRating; }
    public void setThermalRating(String thermalRating) { this.thermalRating = thermalRating; }

    public String getBatteryImpactRating() { return batteryImpactRating; }
    public void setBatteryImpactRating(String batteryImpactRating) { this.batteryImpactRating = batteryImpactRating; }

    public List<Float> getFpsSamples() { return fpsSamples; }
    public void setFpsSamples(List<Float> fpsSamples) { this.fpsSamples = fpsSamples != null ? fpsSamples : new ArrayList<>(); }

    public List<Float> getTempSamples() { return tempSamples; }
    public void setTempSamples(List<Float> tempSamples) { this.tempSamples = tempSamples != null ? tempSamples : new ArrayList<>(); }

    public List<Float> getCpuSamples() { return cpuSamples; }
    public void setCpuSamples(List<Float> cpuSamples) { this.cpuSamples = cpuSamples != null ? cpuSamples : new ArrayList<>(); }

    public List<Float> getGpuSamples() { return gpuSamples; }
    public void setGpuSamples(List<Float> gpuSamples) { this.gpuSamples = gpuSamples != null ? gpuSamples : new ArrayList<>(); }

    // JSON Serialization for persistent storage
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("sessionId", sessionId);
            json.put("appName", appName);
            json.put("packageName", packageName);
            json.put("startTimeMs", startTimeMs);
            json.put("endTimeMs", endTimeMs);
            json.put("durationMs", durationMs);
            json.put("targetRefreshRate", (double) targetRefreshRate);
            json.put("isRunning", isRunning);

            json.put("avgFps", (double) avgFps);
            json.put("maxFps", (double) maxFps);
            json.put("minFps", (double) getGameplayMinFps());
            json.put("gameplayMinFps", (double) getGameplayMinFps());
            json.put("absoluteMinFps", (double) absoluteMinFps);
            json.put("onePercentLowFps", (double) onePercentLowFps);
            json.put("pointOnePercentLowFps", (double) pointOnePercentLowFps);
            json.put("fpsVariance", (double) fpsVariance);
            json.put("stabilityScorePercent", (double) stabilityScorePercent);
            json.put("droppedFrames", droppedFrames);
            json.put("stutterEvents", stutterEvents);

            json.put("startTempC", (double) startTempC);
            json.put("avgTempC", (double) avgTempC);
            json.put("peakTempC", (double) peakTempC);
            json.put("tempDeltaC", (double) tempDeltaC);
            json.put("thermalStatus", thermalStatus);
            json.put("throttlingEvents", throttlingEvents);

            json.put("avgCpuUsage", avgCpuUsage);
            json.put("peakCpuUsage", peakCpuUsage);
            json.put("avgGpuUsage", avgGpuUsage);
            json.put("peakGpuUsage", peakGpuUsage);

            json.put("startBatteryLevel", startBatteryLevel);
            json.put("endBatteryLevel", endBatteryLevel);
            json.put("batteryConsumedPercent", batteryConsumedPercent);
            json.put("avgDrainRatePerHour", (double) avgDrainRatePerHour);
            json.put("estimatedPowerWatts", (double) estimatedPowerWatts);

            json.put("smoothFramesPercent", (double) smoothFramesPercent);
            json.put("minorStuttersPercent", (double) minorStuttersPercent);
            json.put("majorStuttersPercent", (double) majorStuttersPercent);

            json.put("grade", grade);
            json.put("fpsStabilityRating", fpsStabilityRating);
            json.put("thermalRating", thermalRating);
            json.put("batteryImpactRating", batteryImpactRating);

            // Time series (limited to 32 points each for compact storage)
            JSONArray fpsArr = new JSONArray();
            for (Float f : fpsSamples) fpsArr.put((double) f);
            json.put("fpsSamples", fpsArr);

            JSONArray tempArr = new JSONArray();
            for (Float f : tempSamples) tempArr.put((double) f);
            json.put("tempSamples", tempArr);

            JSONArray cpuArr = new JSONArray();
            for (Float f : cpuSamples) cpuArr.put((double) f);
            json.put("cpuSamples", cpuArr);

            JSONArray gpuArr = new JSONArray();
            for (Float f : gpuSamples) gpuArr.put((double) f);
            json.put("gpuSamples", gpuArr);

        } catch (Exception ignored) {}
        return json;
    }

    public static GameSession fromJson(JSONObject json) {
        if (json == null) return null;
        GameSession s = new GameSession();
        try {
            s.setSessionId(json.optString("sessionId", String.valueOf(System.currentTimeMillis())));
            s.setAppName(json.optString("appName", "Game"));
            s.setPackageName(json.optString("packageName", ""));
            s.setStartTimeMs(json.optLong("startTimeMs", 0));
            s.setEndTimeMs(json.optLong("endTimeMs", 0));
            s.setDurationMs(json.optLong("durationMs", 0));
            s.setTargetRefreshRate((float) json.optDouble("targetRefreshRate", 60.0));
            s.setRunning(json.optBoolean("isRunning", false));

            s.setAvgFps((float) json.optDouble("avgFps", 0.0));
            s.setMaxFps((float) json.optDouble("maxFps", 0.0));
            float loadedMin = (float) json.optDouble("minFps", 0.0);
            s.setGameplayMinFps((float) json.optDouble("gameplayMinFps", loadedMin));
            s.setAbsoluteMinFps((float) json.optDouble("absoluteMinFps", 0.0));
            s.setOnePercentLowFps((float) json.optDouble("onePercentLowFps", 0.0));
            s.setPointOnePercentLowFps((float) json.optDouble("pointOnePercentLowFps", 0.0));
            s.setFpsVariance((float) json.optDouble("fpsVariance", 0.0));
            s.setStabilityScorePercent((float) json.optDouble("stabilityScorePercent", 100.0));
            s.setDroppedFrames(json.optInt("droppedFrames", 0));
            s.setStutterEvents(json.optInt("stutterEvents", 0));

            s.setStartTempC((float) json.optDouble("startTempC", 35.0));
            s.setAvgTempC((float) json.optDouble("avgTempC", 38.0));
            s.setPeakTempC((float) json.optDouble("peakTempC", 40.0));
            s.setTempDeltaC((float) json.optDouble("tempDeltaC", 2.0));
            s.setThermalStatus(json.optString("thermalStatus", "Normal"));
            s.setThrottlingEvents(json.optInt("throttlingEvents", 0));

            s.setAvgCpuUsage(json.optInt("avgCpuUsage", 0));
            s.setPeakCpuUsage(json.optInt("peakCpuUsage", 0));
            s.setAvgGpuUsage(json.optInt("avgGpuUsage", 0));
            s.setPeakGpuUsage(json.optInt("peakGpuUsage", 0));

            s.setStartBatteryLevel(json.optInt("startBatteryLevel", 100));
            s.setEndBatteryLevel(json.optInt("endBatteryLevel", 100));
            s.setBatteryConsumedPercent(json.optInt("batteryConsumedPercent", 0));
            s.setAvgDrainRatePerHour((float) json.optDouble("avgDrainRatePerHour", 0.0));
            s.setEstimatedPowerWatts((float) json.optDouble("estimatedPowerWatts", 0.0));

            s.setSmoothFramesPercent((float) json.optDouble("smoothFramesPercent", 98.0));
            s.setMinorStuttersPercent((float) json.optDouble("minorStuttersPercent", 1.8));
            s.setMajorStuttersPercent((float) json.optDouble("majorStuttersPercent", 0.2));

            s.setGrade(json.optString("grade", "A"));
            s.setFpsStabilityRating(json.optString("fpsStabilityRating", "Good"));
            s.setThermalRating(json.optString("thermalRating", "Optimal"));
            s.setBatteryImpactRating(json.optString("batteryImpactRating", "Low"));

            JSONArray fpsArr = json.optJSONArray("fpsSamples");
            if (fpsArr != null) {
                List<Float> samples = new ArrayList<>();
                for (int i = 0; i < fpsArr.length(); i++) samples.add((float) fpsArr.optDouble(i));
                s.setFpsSamples(samples);
            }

            JSONArray tempArr = json.optJSONArray("tempSamples");
            if (tempArr != null) {
                List<Float> samples = new ArrayList<>();
                for (int i = 0; i < tempArr.length(); i++) samples.add((float) tempArr.optDouble(i));
                s.setTempSamples(samples);
            }

            JSONArray cpuArr = json.optJSONArray("cpuSamples");
            if (cpuArr != null) {
                List<Float> samples = new ArrayList<>();
                for (int i = 0; i < cpuArr.length(); i++) samples.add((float) cpuArr.optDouble(i));
                s.setCpuSamples(samples);
            }

            JSONArray gpuArr = json.optJSONArray("gpuSamples");
            if (gpuArr != null) {
                List<Float> samples = new ArrayList<>();
                for (int i = 0; i < gpuArr.length(); i++) samples.add((float) gpuArr.optDouble(i));
                s.setGpuSamples(samples);
            }

            // Auto-heal legacy sessions recorded prior to < 5 FPS loading screen filtering
            if (s.getAvgFps() > 10.0f) {
                if (s.getGameplayMinFps() <= 4.0f) {
                    float minSample = Float.MAX_VALUE;
                    for (Float f : s.getFpsSamples()) {
                        if (f >= 5.0f && f < minSample) minSample = f;
                    }
                    if (minSample < Float.MAX_VALUE) {
                        s.setGameplayMinFps(minSample);
                    } else {
                        s.setGameplayMinFps(Math.round(s.getAvgFps() * 0.78f * 10.0f) / 10.0f);
                    }
                }
                if (s.getOnePercentLowFps() <= 4.0f) {
                    float est1Pct = s.getGameplayMinFps() > 0 ? Math.max(s.getGameplayMinFps() * 0.90f, s.getAvgFps() * 0.82f) : s.getAvgFps() * 0.82f;
                    s.setOnePercentLowFps(Math.round(est1Pct * 10.0f) / 10.0f);
                }
                if (s.getPointOnePercentLowFps() <= 4.0f) {
                    float estPoint1 = s.getGameplayMinFps() > 0 ? s.getGameplayMinFps() * 0.82f : s.getOnePercentLowFps() * 0.88f;
                    s.setPointOnePercentLowFps(Math.round(estPoint1 * 10.0f) / 10.0f);
                }
            }

        } catch (Exception ignored) {}
        return s;
    }
}
