package com.gamestate.monitor.model;

/**
 * SessionInsight
 * --------------
 * Represents an intelligent observation produced by the SessionInsightsEngine.
 */
public class SessionInsight {

    public enum Type {
        PERFORMANCE, // Positive / Stable (✓ green)
        WARNING,     // Anomaly / Throttling / High drain (⚠ amber/red)
        COMPARISON,  // Comparison with past sessions (📊 cyan)
        MILESTONE    // Personal best / record achieved (🏆 purple/gold)
    }

    private final Type type;
    private final String title;
    private final String message;

    public SessionInsight(Type type, String title, String message) {
        this.type = type;
        this.title = title;
        this.message = message;
    }

    public Type getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }
}
