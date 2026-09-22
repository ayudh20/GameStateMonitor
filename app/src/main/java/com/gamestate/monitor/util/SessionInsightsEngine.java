package com.gamestate.monitor.util;

import com.gamestate.monitor.model.GameSession;
import com.gamestate.monitor.model.SessionInsight;

import java.util.ArrayList;
import java.util.List;

/**
 * SessionInsightsEngine
 * ---------------------
 * Intelligent rule-based engine that evaluates game sessions.
 * Generates descriptive explanations, warnings, and historical comparisons
 * rather than raw telemetry numbers.
 */
public class SessionInsightsEngine {

    /**
     * Generates a list of SessionInsights for a completed session,
     * comparing it against prior sessions of the same game.
     */
    public static List<SessionInsight> analyzeSession(GameSession current, List<GameSession> pastSessions) {
        List<SessionInsight> insights = new ArrayList<>();
        if (current == null) return insights;

        float avgFps = current.getAvgFps();
        float stability = current.getStabilityScorePercent();
        float oneLow = current.getOnePercentLowFps();
        float targetHz = current.getTargetRefreshRate();
        float peakTemp = current.getPeakTempC();
        float avgTemp = current.getAvgTempC();
        float drainRate = current.getAvgDrainRatePerHour();
        int stutters = current.getStutterEvents();
        long durationMs = current.getDurationMs();
        int throttlingEvents = current.getThrottlingEvents();

        // ----------------------------------------------------
        // 1. FPS & Frame Pacing Insights
        // ----------------------------------------------------
        if (targetHz > 0 && Math.abs(avgFps - targetHz) <= 2.5f) {
            insights.add(new SessionInsight(
                    SessionInsight.Type.PERFORMANCE,
                    "Locked Frame Rate",
                    "Performance remained locked near the target " + Math.round(targetHz) + " FPS throughout gameplay."
            ));
        } else if (stability >= 90.0f) {
            insights.add(new SessionInsight(
                    SessionInsight.Type.PERFORMANCE,
                    "Excellent Frame Consistency",
                    "FPS remained remarkably consistent with minimal frame pacing fluctuations (" + Math.round(stability) + "% stability)."
            ));
        } else if (stability < 75.0f || stutters > 20) {
            insights.add(new SessionInsight(
                    SessionInsight.Type.WARNING,
                    "Frame Drops Observed",
                    "Detected notable frame dips (" + stutters + " stutters). 1% Low dropped to " + String.format("%.1f", oneLow) + " FPS."
            ));
        }

        // Mid-session drop detection via sample buffer
        List<Float> samples = current.getFpsSamples();
        if (samples != null && samples.size() >= 12 && durationMs > 10 * 60 * 1000) {
            int mid = samples.size() / 2;
            float firstHalfSum = 0;
            for (int i = 0; i < mid; i++) firstHalfSum += samples.get(i);
            float firstHalfAvg = firstHalfSum / mid;

            float secondHalfSum = 0;
            for (int i = mid; i < samples.size(); i++) secondHalfSum += samples.get(i);
            float secondHalfAvg = secondHalfSum / (samples.size() - mid);

            if (firstHalfAvg - secondHalfAvg >= 6.0f) {
                long dropMinute = (durationMs / (2 * 60 * 1000));
                insights.add(new SessionInsight(
                        SessionInsight.Type.WARNING,
                        "Late Session FPS Dip",
                        "Average FPS dropped by " + Math.round(firstHalfAvg - secondHalfAvg) + " FPS after approximately " + dropMinute + " minutes of play."
                ));
            }
        }

        // ----------------------------------------------------
        // 2. Thermal Insights
        // ----------------------------------------------------
        if (peakTemp >= 45.0f || throttlingEvents > 0) {
            insights.add(new SessionInsight(
                    SessionInsight.Type.WARNING,
                    "Thermal Throttling Suspected",
                    "Device peak temperature reached " + Math.round(peakTemp) + "°C. Hardware throttling likely limited peak sustained performance."
            ));
        } else if (peakTemp <= 39.0f && avgTemp > 0) {
            insights.add(new SessionInsight(
                    SessionInsight.Type.PERFORMANCE,
                    "Optimal Thermal Headroom",
                    "Device remained thermally cool throughout the entire run (peak " + Math.round(peakTemp) + "°C)."
            ));
        } else if (current.getTempDeltaC() >= 8.0f) {
            insights.add(new SessionInsight(
                    SessionInsight.Type.WARNING,
                    "Rapid Temperature Rise",
                    "Internal temperature surged +" + Math.round(current.getTempDeltaC()) + "°C from initial startup."
            ));
        }

        // ----------------------------------------------------
        // 3. Battery & Efficiency Insights
        // ----------------------------------------------------
        if (drainRate > 0 && drainRate <= 11.0f) {
            insights.add(new SessionInsight(
                    SessionInsight.Type.PERFORMANCE,
                    "High Energy Efficiency",
                    "Battery consumption was exceptionally low (~" + String.format("%.1f", drainRate) + "%/hr drain rate)."
            ));
        } else if (drainRate >= 22.0f) {
            insights.add(new SessionInsight(
                    SessionInsight.Type.WARNING,
                    "High Battery Drain",
                    "Power consumption was heavy (~" + String.format("%.1f", drainRate) + "%/hr). Consider lowering graphical settings for longer battery life."
            ));
        }

        // ----------------------------------------------------
        // 4. Comparison with Past Sessions of Same Game
        // ----------------------------------------------------
        if (pastSessions != null && !pastSessions.isEmpty()) {
            List<GameSession> gameHistory = new ArrayList<>();
            for (GameSession s : pastSessions) {
                if (current.getPackageName() != null && current.getPackageName().equals(s.getPackageName())
                        && !current.getSessionId().equals(s.getSessionId())) {
                    gameHistory.add(s);
                }
            }

            if (!gameHistory.isEmpty()) {
                float histFpsSum = 0;
                float histTempSum = 0;
                float histDrainSum = 0;
                float bestPastFps = 0;

                for (GameSession s : gameHistory) {
                    histFpsSum += s.getAvgFps();
                    histTempSum += s.getAvgTempC();
                    histDrainSum += s.getAvgDrainRatePerHour();
                    if (s.getAvgFps() > bestPastFps) bestPastFps = s.getAvgFps();
                }

                float histAvgFps = histFpsSum / gameHistory.size();
                float histAvgTemp = histTempSum / gameHistory.size();
                float histAvgDrain = histDrainSum / gameHistory.size();

                // Check for Personal Best
                if (avgFps > bestPastFps && gameHistory.size() >= 2) {
                    insights.add(0, new SessionInsight(
                            SessionInsight.Type.MILESTONE,
                            "New Best Session Record!",
                            "This was your highest performing " + current.getAppName() + " session on record (" + String.format("%.1f", avgFps) + " FPS)!"
                    ));
                } else if (avgFps - histAvgFps >= 2.0f) {
                    float pct = ((avgFps - histAvgFps) / histAvgFps) * 100f;
                    insights.add(new SessionInsight(
                            SessionInsight.Type.COMPARISON,
                            "FPS Improvement",
                            "Average FPS improved by +" + Math.round(pct) + "% compared to your previous " + current.getAppName() + " average."
                    ));
                } else if (histAvgFps - avgFps >= 4.0f) {
                    float pct = ((histAvgFps - avgFps) / histAvgFps) * 100f;
                    insights.add(new SessionInsight(
                            SessionInsight.Type.WARNING,
                            "Performance Below Normal",
                            "Average FPS was " + Math.round(pct) + "% lower than your typical sessions for this game."
                    ));
                }

                // Temp comparison
                if (histAvgTemp - avgTemp >= 2.5f) {
                    insights.add(new SessionInsight(
                            SessionInsight.Type.COMPARISON,
                            "Ran Cooler",
                            "Device ran " + Math.round(histAvgTemp - avgTemp) + "°C cooler than your usual session average."
                    ));
                }

                // Battery comparison
                if (histAvgDrain - drainRate >= 3.0f && drainRate > 0) {
                    insights.add(new SessionInsight(
                            SessionInsight.Type.COMPARISON,
                            "Better Battery Efficiency",
                            "Consumed battery at a lower rate than your previous sessions."
                    ));
                }
            }
        }

        // Fallback if no specific trigger fired
        if (insights.isEmpty()) {
            insights.add(new SessionInsight(
                    SessionInsight.Type.PERFORMANCE,
                    "Session Complete",
                    "Hardware telemetry recorded with standard gaming stability."
            ));
        }

        return insights;
    }

    /**
     * Computes premium S/A+/A/B/C Grade based on comprehensive gaming telemetry.
     */
    public static String computeSessionGrade(GameSession s) {
        if (s == null) return "B";

        float stability = s.getStabilityScorePercent();
        float smoothPercent = s.getSmoothFramesPercent();
        float peakTemp = s.getPeakTempC();
        float drainRate = s.getAvgDrainRatePerHour();
        float oneLow = s.getOnePercentLowFps();
        float targetHz = s.getTargetRefreshRate();

        // 100-point composite scoring:
        // 35% Stability score
        // 25% Smooth frame percent
        // 15% 1% Low frame floor (relative to target)
        // 15% Thermals (penalize > 40°C)
        // 10% Battery drain efficiency (penalize > 15%/hr)
        float score = (stability * 0.35f) + (smoothPercent * 0.25f);

        if (targetHz > 0) {
            float lowRatio = Math.min(1.0f, oneLow / targetHz);
            score += (lowRatio * 100.0f) * 0.15f;
        } else {
            score += Math.min(15.0f, (oneLow / 4.0f));
        }

        float thermalScore = Math.max(0.0f, 100.0f - (Math.max(30.0f, peakTemp) - 30.0f) * 4.0f);
        score += thermalScore * 0.15f;

        float drainScore = Math.max(0.0f, 100.0f - drainRate * 3.5f);
        score += drainScore * 0.10f;

        if (score >= 94.0f && stability >= 94.0f && peakTemp <= 42.0f) {
            return "S";
        } else if (score >= 88.0f) {
            return "A+";
        } else if (score >= 78.0f) {
            return "A";
        } else if (score >= 65.0f) {
            return "B";
        } else {
            return "C";
        }
    }
}
