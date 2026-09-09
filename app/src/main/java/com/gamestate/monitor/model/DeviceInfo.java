package com.gamestate.monitor.model;

/**
 * DeviceInfo Data Model Class
 * ----------------------------
 * In Object-Oriented Programming (OOP), a Model or "POJO" (Plain Old Java Object)
 * is used to encapsulate related data together into an organized object.
 *
 * This class stores the static hardware and software specifications of the Android device
 * such as the brand, model name, OS version, and screen refresh rate.
 */
public class DeviceInfo {

    // Private member variables (Encapsulation principle in OOP:
    // keep data private to protect it from arbitrary external mutation)
    private final String manufacturer;
    private final String model;
    private final String androidVersion;
    private final int apiLevel;
    private final float refreshRate;

    /**
     * Constructor to initialize all device specifications at once.
     *
     * @param manufacturer   Device maker (e.g. "Google", "Samsung")
     * @param model          Model designation (e.g. "Pixel 7 Pro", "Galaxy S23")
     * @param androidVersion User-facing OS release (e.g. "14", "13")
     * @param apiLevel       SDK version integer (e.g. 34 for Android 14)
     * @param refreshRate    Screen refresh frequency in Hertz (Hz)
     */
    public DeviceInfo(String manufacturer, String model, String androidVersion, int apiLevel, float refreshRate) {
        this.manufacturer = manufacturer != null ? manufacturer : "Unknown";
        this.model = model != null ? model : "Unknown Device";
        this.androidVersion = androidVersion != null ? androidVersion : "Unknown";
        this.apiLevel = apiLevel;
        this.refreshRate = refreshRate;
    }

    // =========================================================================
    // Getters: Public accessor methods allowing other classes to read the data
    // =========================================================================

    public String getManufacturer() {
        return manufacturer;
    }

    public String getModel() {
        return model;
    }

    /**
     * Helper method to return a user-friendly complete device title
     * (e.g., "Google Pixel 7 Pro").
     */
    public String getFullDeviceName() {
        if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            return capitalize(model);
        }
        return capitalize(manufacturer) + " " + model;
    }

    public String getAndroidVersion() {
        return androidVersion;
    }

    public int getApiLevel() {
        return apiLevel;
    }

    public float getRefreshRate() {
        return refreshRate;
    }

    /**
     * Capitalizes the first letter of a word for clean display.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
