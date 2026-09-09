package com.gamestate.monitor.model;

/**
 * GpuInfo Data Model Class
 * ------------------------
 * Encapsulates GPU (Graphics Processing Unit) hardware specifications,
 * vendor, renderer engine, OpenGL ES version, and graphics load.
 */
public class GpuInfo {

    private final String renderer;          // e.g. "Adreno (TM) 730", "Mali-G710"
    private final String vendor;            // e.g. "Qualcomm", "ARM"
    private final String openGlVersion;     // e.g. "OpenGL ES 3.2"
    private final int gpuUsagePercentage;   // 0-100%, or -1 if restricted

    public GpuInfo(String renderer, String vendor, String openGlVersion, int gpuUsagePercentage) {
        this.renderer = renderer != null && !renderer.isEmpty() ? renderer : "Hardware Accelerated GPU";
        this.vendor = vendor != null && !vendor.isEmpty() ? vendor : "Android Graphics System";
        this.openGlVersion = openGlVersion != null && !openGlVersion.isEmpty() ? openGlVersion : "OpenGL ES 3.0+";
        this.gpuUsagePercentage = gpuUsagePercentage;
    }

    public String getRenderer() {
        return renderer;
    }

    public String getVendor() {
        return vendor;
    }

    public String getOpenGlVersion() {
        return openGlVersion;
    }

    public String getOpenglVersion() {
        return openGlVersion;
    }

    public int getGpuUsagePercentage() {
        return gpuUsagePercentage;
    }

    /**
     * Compact label for the floating pill (e.g., "Adreno 730" or "GPU Active").
     */
    public String getShortName() {
        if (renderer.contains("Adreno")) {
            int idx = renderer.indexOf("Adreno");
            return renderer.substring(idx).replace("(TM)", "").trim();
        } else if (renderer.contains("Mali")) {
            int idx = renderer.indexOf("Mali");
            return renderer.substring(idx).trim();
        } else if (renderer.contains("Xclipse")) {
            int idx = renderer.indexOf("Xclipse");
            return renderer.substring(idx).trim();
        }
        return renderer.length() > 18 ? renderer.substring(0, 18) + "..." : renderer;
    }
}
