package com.gamestate.monitor.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.gamestate.monitor.MainActivity;
import com.gamestate.monitor.R;
import com.gamestate.monitor.model.CpuInfo;
import com.gamestate.monitor.model.DeviceInfo;
import com.gamestate.monitor.model.GpuInfo;
import com.gamestate.monitor.model.PerformanceStats;
import com.gamestate.monitor.util.CpuMonitor;
import com.gamestate.monitor.util.DeviceStatsManager;
import com.gamestate.monitor.util.FormatUtils;
import com.gamestate.monitor.util.GpuMonitor;

/**
 * OverlayService
 * --------------
 * An Android Foreground Service that creates and manages the Floating Gaming HUD.
 *
 * Key Concepts Explained for Beginners:
 * 1. WindowManager: The Android OS system service that manages windows on screen.
 *    By using WindowManager.addView() with TYPE_APPLICATION_OVERLAY, this view draws
 *    on top of other full-screen apps and 3D games.
 * 2. FLAG_NOT_FOCUSABLE: Crucial for gaming overlays! It ensures touch events
 *    outside our floating window continue to pass directly to the underlying game.
 * 3. Foreground Service: Android enforces that long-running background tasks displaying
 *    overlays must show a notification so the user is always aware an overlay is active.
 * 4. Coordinate Dragging: We track (event.getRawX() - initialTouchX) to move the window
 *    fluidly across the screen as the player drags it.
 * 5. Real-Time CPU & GPU Tracking: Samples multi-core CPU usage % and frequency via
 *    CpuMonitor, and renders GPU chipset info and utilization via GpuMonitor.
 */
public class OverlayService extends Service {

    private static final String TAG = "OverlayService";
    private static final String CHANNEL_ID = "channel_game_hud";
    private static final int NOTIFICATION_ID = 1001;

    // Static flag allowing MainActivity to know if the HUD is currently active
    public static boolean isRunning = false;

    // Window Management
    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;

    // UI Widgets in Floating HUD
    private View overlayPillView;
    private View overlayExpandedPanel;
    private View vOverlayStatusDot;
    private TextView tvOverlayCpu;
    private TextView tvOverlayGpu;
    private TextView tvOverlayRam;
    private TextView tvOverlayTemp;
    private ImageView ivOverlayToggle;

    // Expanded panel widgets
    private ImageView ivOverlayClose;
    private TextView tvOverlayDevice;
    private TextView tvOverlayCpuDetails;
    private TextView tvOverlayGpuDetails;
    private TextView tvOverlayBattery;
    private TextView tvOverlayRamFull;
    private TextView tvOverlayStorage;
    private TextView btnOverlayStop;

    // Hardware Monitors
    private CpuMonitor cpuMonitor;
    private GpuMonitor gpuMonitor;
    private GpuInfo cachedGpuInfo;

    // Performance Stats Manager & Timer
    private DeviceStatsManager statsManager;
    private DeviceInfo cachedDeviceInfo;
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private static final long UPDATE_INTERVAL_MS = 1500; // updates every 1.5 seconds

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateHudMetrics();
            updateHandler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        // We do not bind to this service; it runs independently via startForegroundService
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        statsManager = new DeviceStatsManager(this);
        cachedDeviceInfo = statsManager.getDeviceInfo();
        cpuMonitor = new CpuMonitor();
        gpuMonitor = new GpuMonitor();
        cachedGpuInfo = gpuMonitor.getGpuInfo();

        // 1. Promote to Foreground Service with required notification
        startInForeground();

        // 2. Inflate and configure the floating Window
        createFloatingHudWindow();

        // 3. Start real-time monitoring loop
        updateHandler.post(updateRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Return START_STICKY so Android restarts the service if memory becomes available
        return START_STICKY;
    }

    /**
     * Initializes the floating window and adds it to the WindowManager.
     */
    private void createFloatingHudWindow() {
        // Verify overlay permission before attempting to add window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Cannot create overlay: SYSTEM_ALERT_WINDOW permission missing.");
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        overlayView = inflater.inflate(R.layout.layout_floating_overlay, null);

        // Bind widgets
        overlayPillView = overlayView.findViewById(R.id.overlayPillView);
        overlayExpandedPanel = overlayView.findViewById(R.id.overlayExpandedPanel);
        vOverlayStatusDot = overlayView.findViewById(R.id.vOverlayStatusDot);
        tvOverlayCpu = overlayView.findViewById(R.id.tvOverlayCpu);
        tvOverlayGpu = overlayView.findViewById(R.id.tvOverlayGpu);
        tvOverlayRam = overlayView.findViewById(R.id.tvOverlayRam);
        tvOverlayTemp = overlayView.findViewById(R.id.tvOverlayTemp);
        ivOverlayToggle = overlayView.findViewById(R.id.ivOverlayToggle);

        ivOverlayClose = overlayView.findViewById(R.id.ivOverlayClose);
        tvOverlayDevice = overlayView.findViewById(R.id.tvOverlayDevice);
        tvOverlayCpuDetails = overlayView.findViewById(R.id.tvOverlayCpuDetails);
        tvOverlayGpuDetails = overlayView.findViewById(R.id.tvOverlayGpuDetails);
        tvOverlayBattery = overlayView.findViewById(R.id.tvOverlayBattery);
        tvOverlayRamFull = overlayView.findViewById(R.id.tvOverlayRamFull);
        tvOverlayStorage = overlayView.findViewById(R.id.tvOverlayStorage);
        btnOverlayStop = overlayView.findViewById(R.id.btnOverlayStop);

        // Set initial device info in expanded panel
        if (cachedDeviceInfo != null) {
            tvOverlayDevice.setText("Device: " + cachedDeviceInfo.getFullDeviceName());
        }
        if (cachedGpuInfo != null) {
            String shortGpu = cachedGpuInfo.getRenderer().contains("Adreno") ? "Adreno" :
                              cachedGpuInfo.getRenderer().contains("Mali") ? "Mali" :
                              cachedGpuInfo.getRenderer().contains("PowerVR") ? "PowerVR" : "GPU";
            tvOverlayGpu.setText("GPU " + shortGpu);
            tvOverlayGpuDetails.setText("GPU: " + cachedGpuInfo.getRenderer());
        }

        // Configure Window Layout Parameters
        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 40;  // Initial X offset in pixels
        params.y = 140; // Initial Y offset in pixels

        // Set drag & click listeners
        setupTouchListener();
        setupClickListeners();

        // Add view to WindowManager
        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            Log.e(TAG, "Error adding overlay view: " + e.getMessage());
        }
    }

    /**
     * Implements gesture tracking so the gamer can drag the HUD anywhere on screen.
     */
    private void setupTouchListener() {
        overlayPillView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isDragged = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragged = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);

                        // If user moved more than 8 pixels, mark as drag gesture
                        if (Math.abs(deltaX) > 8 || Math.abs(deltaY) > 8) {
                            isDragged = true;
                        }

                        params.x = initialX + deltaX;
                        params.y = initialY + deltaY;
                        windowManager.updateViewLayout(overlayView, params);
                        return true;

                    case MotionEvent.ACTION_UP:
                        // If user tapped without dragging, toggle the expanded stats panel
                        if (!isDragged) {
                            toggleExpandedPanel();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void setupClickListeners() {
        ivOverlayClose.setOnClickListener(v -> toggleExpandedPanel());
        btnOverlayStop.setOnClickListener(v -> stopSelf());
    }

    private void toggleExpandedPanel() {
        if (overlayExpandedPanel.getVisibility() == View.VISIBLE) {
            overlayExpandedPanel.setVisibility(View.GONE);
            ivOverlayToggle.setRotation(0);
        } else {
            overlayExpandedPanel.setVisibility(View.VISIBLE);
            ivOverlayToggle.setRotation(180);
        }
    }

    /**
     * Periodic method updating all stats inside the floating HUD.
     */
     private void updateHudMetrics() {
         if (overlayView == null || statsManager == null) return;

         PerformanceStats stats = statsManager.getPerformanceStats();
         CpuInfo cpuInfo = cpuMonitor != null ? cpuMonitor.getCpuInfo() : null;

         // 1. Compact Pill Updates
         if (cpuInfo != null) {
             tvOverlayCpu.setText(String.format("CPU %d%%", cpuInfo.getUsagePercentage()));
         }

         if (gpuMonitor != null) {
             GpuInfo liveGpu = gpuMonitor.sampleGpuInfo();
             int gpuUsage = liveGpu.getGpuUsagePercentage();
             tvOverlayGpu.setText(String.format("GPU %d%%", gpuUsage));
         }

         tvOverlayRam.setText(String.format("RAM %d%%", stats.getRamUsagePercentage()));
         tvOverlayTemp.setText(String.format("%.1f°C", stats.getBatteryTemperatureC()));

         // Update status dot glow
         int statusColor;
         switch (stats.getSystemStatus()) {
             case HIGH_LOAD:
                 statusColor = ContextCompat.getColor(this, R.color.status_high_load);
                 break;
             case MODERATE:
                 statusColor = ContextCompat.getColor(this, R.color.status_moderate);
                 break;
             case OPTIMAL:
             default:
                 statusColor = ContextCompat.getColor(this, R.color.status_optimal);
                 break;
         }
         vOverlayStatusDot.setBackgroundTintList(ColorStateList.valueOf(statusColor));

         // 2. Expanded Panel Updates (if visible)
         if (overlayExpandedPanel.getVisibility() == View.VISIBLE) {
             if (cpuInfo != null) {
                 tvOverlayCpuDetails.setText(String.format("CPU: %d%% (%d Cores @ %.2f GHz)",
                         cpuInfo.getUsagePercentage(), cpuInfo.getCoreCount(), cpuInfo.getAverageFrequencyGhz()));
             }

             if (cachedGpuInfo != null) {
                 GpuInfo liveGpu = gpuMonitor != null ? gpuMonitor.sampleGpuInfo() : cachedGpuInfo;
                 int gpuUsage = liveGpu.getGpuUsagePercentage();
                 if (gpuUsage >= 0) {
                     tvOverlayGpuDetails.setText(String.format("GPU: %d%% (%s)", gpuUsage, cachedGpuInfo.getRenderer()));
                 } else {
                     tvOverlayGpuDetails.setText(String.format("GPU: %s (%s)",
                             cachedGpuInfo.getRenderer(), cachedGpuInfo.getVendor()));
                 }
             }

             tvOverlayBattery.setText(String.format("Battery: %d%% (%s)",
                     stats.getBatteryLevel(), stats.getBatteryStatus()));

             String usedRam = FormatUtils.formatBytes(stats.getUsedRamBytes());
             String totalRam = FormatUtils.formatBytes(stats.getTotalRamBytes());
             tvOverlayRamFull.setText(String.format("RAM: %s / %s", usedRam, totalRam));

             String freeStorage = FormatUtils.formatBytes(stats.getAvailableStorageBytes());
             tvOverlayStorage.setText(String.format("Storage Free: %s", freeStorage));
         }
     }

     /**
      * Creates notification channel and starts foreground service.
      */
     private void startInForeground() {
         createNotificationChannel();

         Intent openAppIntent = new Intent(this, MainActivity.class);
         PendingIntent pendingIntent = PendingIntent.getActivity(
                 this, 0, openAppIntent,
                 PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
         );

         Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                 .setContentTitle("GameState Gaming HUD Active")
                 .setContentText("Monitoring CPU & GPU performance over running games")
                 .setSmallIcon(R.drawable.ic_device)
                 .setColor(ContextCompat.getColor(this, R.color.primary_neon))
                 .setContentIntent(pendingIntent)
                 .setOngoing(true)
                 .setPriority(NotificationCompat.PRIORITY_LOW)
                 .build();

         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
             startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
         } else {
             startForeground(NOTIFICATION_ID, notification);
         }
     }

     private void createNotificationChannel() {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             NotificationChannel channel = new NotificationChannel(
                     CHANNEL_ID,
                     "GameState Overlay HUD",
                     NotificationManager.IMPORTANCE_LOW
             );
             channel.setDescription("Shows persistent status when Gaming Overlay is active");
             NotificationManager manager = getSystemService(NotificationManager.class);
             if (manager != null) {
                 manager.createNotificationChannel(channel);
             }
         }
     }

     @Override
     public void onDestroy() {
         super.onDestroy();
         isRunning = false;

         // Stop updates
         updateHandler.removeCallbacks(updateRunnable);

         // Remove floating view from screen
         if (overlayView != null && windowManager != null) {
             try {
                 windowManager.removeView(overlayView);
             } catch (Exception e) {
                 Log.e(TAG, "Error removing overlay: " + e.getMessage());
             }
         }
     }
}
