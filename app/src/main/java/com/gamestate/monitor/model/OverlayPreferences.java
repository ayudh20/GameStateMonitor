package com.gamestate.monitor.model;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * OverlayPreferences
 * ------------------
 * Persistent configuration store for the Floating Gaming HUD overlay.
 * Controls metric visibility, presets, order, appearance (opacity, text size,
 * corner radius, accent color), and position behavior.
 */
public class OverlayPreferences {

    public static final String ACTION_OVERLAY_CONFIG_CHANGED = "com.gamestate.monitor.ACTION_OVERLAY_CONFIG_CHANGED";
    private static final String PREF_NAME = "gamestate_overlay_prefs";

    // Keys
    private static final String KEY_PRESET = "pref_preset";
    private static final String KEY_SHOW_FPS = "pref_show_fps";
    private static final String KEY_SHOW_CPU = "pref_show_cpu";
    private static final String KEY_SHOW_GPU = "pref_show_gpu";
    private static final String KEY_SHOW_RAM = "pref_show_ram";
    private static final String KEY_SHOW_BAT_TEMP = "pref_show_bat_temp";
    private static final String KEY_SHOW_CPU_TEMP = "pref_show_cpu_temp";
    private static final String KEY_SHOW_BAT_LEVEL = "pref_show_bat_level";
    private static final String KEY_SHOW_REFRESH_RATE = "pref_show_refresh_rate";
    private static final String KEY_SHOW_GAME_NAME = "pref_show_game_name";
    private static final String KEY_METRIC_ORDER = "pref_metric_order";

    private static final String KEY_OPACITY = "pref_opacity";
    private static final String KEY_TEXT_SIZE = "pref_text_size";
    private static final String KEY_CORNER_RADIUS = "pref_corner_radius";
    private static final String KEY_ACCENT_COLOR = "pref_accent_color";
    private static final String KEY_POSITION_MODE = "pref_position_mode";
    private static final String KEY_POS_X = "pref_pos_x";
    private static final String KEY_POS_Y = "pref_pos_y";

    // General App Settings Keys
    private static final String KEY_UPDATE_INTERVAL = "pref_update_interval_ms";
    private static final String KEY_KEEP_SCREEN_ON = "pref_keep_screen_on";
    private static final String KEY_AUTO_HIDE_OVERLAY = "pref_auto_hide_overlay";

    public enum Preset {
        COMPACT,
        BALANCED,
        DETAILED
    }

    public enum PositionMode {
        FREE_DRAG,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    public static final String METRIC_FPS = "FPS";
    public static final String METRIC_CPU = "CPU";
    public static final String METRIC_GPU = "GPU";
    public static final String METRIC_RAM = "RAM";
    public static final String METRIC_BAT_TEMP = "BAT_TEMP";
    public static final String METRIC_CPU_TEMP = "CPU_TEMP";
    public static final String METRIC_BAT_LEVEL = "BAT_LEVEL";
    public static final String METRIC_REFRESH_RATE = "HZ";
    public static final String METRIC_GAME_NAME = "GAME";

    private static final String DEFAULT_ORDER = "FPS,CPU,GPU,RAM,BAT_TEMP,CPU_TEMP,BAT_LEVEL,HZ,GAME";

    private final SharedPreferences prefs;
    private final Context context;

    public OverlayPreferences(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // --- Preset ---
    public Preset getPreset() {
        String val = prefs.getString(KEY_PRESET, Preset.BALANCED.name());
        try {
            return Preset.valueOf(val);
        } catch (Exception e) {
            return Preset.BALANCED;
        }
    }

    public void setPreset(Preset preset) {
        prefs.edit().putString(KEY_PRESET, preset.name()).apply();
        applyPresetDefaults(preset);
        notifyConfigChanged();
    }

    public void applyPresetDefaults(Preset preset) {
        SharedPreferences.Editor editor = prefs.edit();
        switch (preset) {
            case COMPACT:
                editor.putBoolean(KEY_SHOW_FPS, true);
                editor.putBoolean(KEY_SHOW_CPU, true);
                editor.putBoolean(KEY_SHOW_GPU, false);
                editor.putBoolean(KEY_SHOW_RAM, true);
                editor.putBoolean(KEY_SHOW_BAT_TEMP, false);
                editor.putBoolean(KEY_SHOW_CPU_TEMP, false);
                editor.putBoolean(KEY_SHOW_BAT_LEVEL, false);
                editor.putBoolean(KEY_SHOW_REFRESH_RATE, false);
                editor.putBoolean(KEY_SHOW_GAME_NAME, false);
                break;
            case BALANCED:
                editor.putBoolean(KEY_SHOW_FPS, true);
                editor.putBoolean(KEY_SHOW_CPU, true);
                editor.putBoolean(KEY_SHOW_GPU, true);
                editor.putBoolean(KEY_SHOW_RAM, true);
                editor.putBoolean(KEY_SHOW_BAT_TEMP, true);
                editor.putBoolean(KEY_SHOW_CPU_TEMP, false);
                editor.putBoolean(KEY_SHOW_BAT_LEVEL, false);
                editor.putBoolean(KEY_SHOW_REFRESH_RATE, false);
                editor.putBoolean(KEY_SHOW_GAME_NAME, false);
                break;
            case DETAILED:
                editor.putBoolean(KEY_SHOW_FPS, true);
                editor.putBoolean(KEY_SHOW_CPU, true);
                editor.putBoolean(KEY_SHOW_GPU, true);
                editor.putBoolean(KEY_SHOW_RAM, true);
                editor.putBoolean(KEY_SHOW_BAT_TEMP, true);
                editor.putBoolean(KEY_SHOW_CPU_TEMP, true);
                editor.putBoolean(KEY_SHOW_BAT_LEVEL, true);
                editor.putBoolean(KEY_SHOW_REFRESH_RATE, true);
                editor.putBoolean(KEY_SHOW_GAME_NAME, true);
                break;
        }
        editor.apply();
    }

    // --- Metric Visibility ---
    public boolean isShowFps() {
        return prefs.getBoolean(KEY_SHOW_FPS, true);
    }
    public void setShowFps(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_FPS, show).apply();
        notifyConfigChanged();
    }

    public boolean isShowCpu() {
        return prefs.getBoolean(KEY_SHOW_CPU, true);
    }
    public void setShowCpu(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_CPU, show).apply();
        notifyConfigChanged();
    }

    public boolean isShowGpu() {
        return prefs.getBoolean(KEY_SHOW_GPU, true);
    }
    public void setShowGpu(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_GPU, show).apply();
        notifyConfigChanged();
    }

    public boolean isShowRam() {
        return prefs.getBoolean(KEY_SHOW_RAM, true);
    }
    public void setShowRam(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_RAM, show).apply();
        notifyConfigChanged();
    }

    public boolean isShowBatteryTemp() {
        return prefs.getBoolean(KEY_SHOW_BAT_TEMP, true);
    }
    public void setShowBatteryTemp(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_BAT_TEMP, show).apply();
        notifyConfigChanged();
    }

    public boolean isShowCpuTemp() {
        return prefs.getBoolean(KEY_SHOW_CPU_TEMP, false);
    }
    public void setShowCpuTemp(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_CPU_TEMP, show).apply();
        notifyConfigChanged();
    }

    public boolean isShowBatteryLevel() {
        return prefs.getBoolean(KEY_SHOW_BAT_LEVEL, false);
    }
    public void setShowBatteryLevel(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_BAT_LEVEL, show).apply();
        notifyConfigChanged();
    }

    public boolean isShowRefreshRate() {
        return prefs.getBoolean(KEY_SHOW_REFRESH_RATE, false);
    }
    public void setShowRefreshRate(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_REFRESH_RATE, show).apply();
        notifyConfigChanged();
    }

    public boolean isShowGameName() {
        return prefs.getBoolean(KEY_SHOW_GAME_NAME, false);
    }
    public void setShowGameName(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_GAME_NAME, show).apply();
        notifyConfigChanged();
    }

    // --- Metric Ordering ---
    public List<String> getMetricOrder() {
        String raw = prefs.getString(KEY_METRIC_ORDER, DEFAULT_ORDER);
        String[] parts = raw.split(",");
        return new ArrayList<>(Arrays.asList(parts));
    }

    public void setMetricOrder(List<String> order) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            sb.append(order.get(i));
            if (i < order.size() - 1) sb.append(",");
        }
        prefs.edit().putString(KEY_METRIC_ORDER, sb.toString()).apply();
        notifyConfigChanged();
    }

    // --- Appearance ---
    public float getOpacity() {
        return prefs.getFloat(KEY_OPACITY, 0.90f);
    }
    public void setOpacity(float opacity) {
        prefs.edit().putFloat(KEY_OPACITY, Math.max(0.20f, Math.min(1.0f, opacity))).apply();
        notifyConfigChanged();
    }

    public int getTextSizeSp() {
        return prefs.getInt(KEY_TEXT_SIZE, 12);
    }
    public void setTextSizeSp(int sp) {
        prefs.edit().putInt(KEY_TEXT_SIZE, Math.max(10, Math.min(18, sp))).apply();
        notifyConfigChanged();
    }

    public int getCornerRadiusDp() {
        return prefs.getInt(KEY_CORNER_RADIUS, 16);
    }
    public void setCornerRadiusDp(int dp) {
        prefs.edit().putInt(KEY_CORNER_RADIUS, Math.max(4, Math.min(28, dp))).apply();
        notifyConfigChanged();
    }

    public String getAccentColorHex() {
        return prefs.getString(KEY_ACCENT_COLOR, "#00D2E0");
    }
    public void setAccentColorHex(String hex) {
        prefs.edit().putString(KEY_ACCENT_COLOR, hex).apply();
        notifyConfigChanged();
    }

    // --- Position Mode ---
    public PositionMode getPositionMode() {
        String val = prefs.getString(KEY_POSITION_MODE, PositionMode.FREE_DRAG.name());
        try {
            return PositionMode.valueOf(val);
        } catch (Exception e) {
            return PositionMode.FREE_DRAG;
        }
    }
    public void setPositionMode(PositionMode mode) {
        prefs.edit().putString(KEY_POSITION_MODE, mode.name()).apply();
        notifyConfigChanged();
    }

    public int getPositionX() {
        return prefs.getInt(KEY_POS_X, 40);
    }
    public void setPositionX(int x) {
        prefs.edit().putInt(KEY_POS_X, x).apply();
    }

    public int getPositionY() {
        return prefs.getInt(KEY_POS_Y, 140);
    }
    public void setPositionY(int y) {
        prefs.edit().putInt(KEY_POS_Y, y).apply();
    }

    // --- App Settings ---
    public long getTelemetryIntervalMs() {
        return prefs.getLong(KEY_UPDATE_INTERVAL, 1500L);
    }
    public void setTelemetryIntervalMs(long ms) {
        prefs.edit().putLong(KEY_UPDATE_INTERVAL, ms).apply();
    }

    public boolean isKeepScreenOn() {
        return prefs.getBoolean(KEY_KEEP_SCREEN_ON, false);
    }
    public void setKeepScreenOn(boolean keepOn) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, keepOn).apply();
    }

    public boolean isAutoHideOverlay() {
        return prefs.getBoolean(KEY_AUTO_HIDE_OVERLAY, false);
    }
    public void setAutoHideOverlay(boolean autoHide) {
        prefs.edit().putBoolean(KEY_AUTO_HIDE_OVERLAY, autoHide).apply();
        notifyConfigChanged();
    }

    public void resetToDefaults() {
        prefs.edit().clear().apply();
        applyPresetDefaults(Preset.BALANCED);
        notifyConfigChanged();
    }

    public void notifyConfigChanged() {
        Intent intent = new Intent(ACTION_OVERLAY_CONFIG_CHANGED);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
}
