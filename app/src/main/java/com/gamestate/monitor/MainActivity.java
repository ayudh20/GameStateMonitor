package com.gamestate.monitor;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.gamestate.monitor.model.OverlayPreferences;
import com.gamestate.monitor.service.GameStateService;
import com.gamestate.monitor.service.OverlayService;
import com.gamestate.monitor.service.SessionAnalyticsTracker;
import com.gamestate.monitor.shizuku.ShizukuManager;
import com.gamestate.monitor.ui.DashboardFragment;
import com.gamestate.monitor.ui.DiagnosticsFragment;
import com.gamestate.monitor.ui.OverlayControlsFragment;
import com.gamestate.monitor.ui.SettingsFragment;
import com.gamestate.monitor.ui.StatisticsFragment;

import rikka.shizuku.Shizuku;

/**
 * MainActivity
 * ------------
 * Primary entry point featuring the Modern Gaming Bottom Dock with an
 * elevated center glowing overlay control, and 5 responsive tabs:
 * 1. Dashboard (Clean hardware diagnostics)
 * 2. Diagnostics (Low-level kernel frequencies & thermal zones)
 * 3. Overlay (Center glowing button - live HUD styling & presets)
 * 4. Statistics (Session benchmarks & frame time distribution)
 * 5. Settings (Shizuku status & monitoring preferences)
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static volatile boolean isAppInForeground = false;

    public enum Tab {
        DASHBOARD,
        DIAGNOSTICS,
        OVERLAY,
        STATISTICS,
        SETTINGS
    }

    private Tab currentTab = Tab.DASHBOARD;

    // Dock View References
    private LinearLayout navTabDashboard;
    private LinearLayout navTabDiagnostics;
    private FrameLayout btnNavCenter;
    private View centerHalo;
    private ImageView ivNavCenterIcon;
    private TextView tvCenterOverlayStatus;
    private LinearLayout navTabStatistics;
    private LinearLayout navTabSettings;

    // Icons & Labels
    private ImageView ivNavDashboard;
    private TextView tvNavDashboard;
    private View indNavDashboard;

    private ImageView ivNavDiagnostics;
    private TextView tvNavDiagnostics;
    private View indNavDiagnostics;

    private ImageView ivNavStatistics;
    private TextView tvNavStatistics;
    private View indNavStatistics;

    private ImageView ivNavSettings;
    private TextView tvNavSettings;
    private View indNavSettings;

    // Pulsing animator for center halo
    private ObjectAnimator haloPulseAnimator;

    // App diagnostic start time
    private long sessionStartTimeMs;

    private OverlayPreferences overlayPreferences;

    // Broadcast receiver for OverlayService state changes
    private final BroadcastReceiver overlayStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (OverlayService.ACTION_OVERLAY_STATE_CHANGED.equals(intent.getAction())) {
                updateCenterButtonVisuals();
            }
        }
    };

    // Shizuku listeners
    private final Shizuku.OnBinderReceivedListener shizukuBinderReceivedListener = () -> {
        runOnUiThread(() -> {
            Log.i(TAG, "Shizuku binder received");
        });
    };

    private final Shizuku.OnBinderDeadListener shizukuBinderDeadListener = () -> {
        runOnUiThread(() -> {
            Log.i(TAG, "Shizuku binder dead");
        });
    };

    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionResultListener = (requestCode, grantResult) -> {
        if (requestCode == ShizukuManager.SHIZUKU_REQUEST_CODE) {
            runOnUiThread(() -> {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "Shizuku authorized! Granting system permissions...", Toast.LENGTH_SHORT).show();
                    ShizukuManager.grantAppPrivileges(MainActivity.this, new ShizukuManager.PermissionGrantCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(MainActivity.this, "FPS & Game Detection active via Shizuku!", Toast.LENGTH_SHORT).show();
                            Intent gameServiceIntent = new Intent(MainActivity.this, GameStateService.class);
                            try {
                                startService(gameServiceIntent);
                            } catch (Exception ignored) {}
                        }

                        @Override
                        public void onFailure(String error) {
                            Toast.makeText(MainActivity.this, "Permission grant failed: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    Toast.makeText(MainActivity.this, "Shizuku permission denied.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    };

    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {});

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionStartTimeMs = SystemClock.elapsedRealtime();
        overlayPreferences = new OverlayPreferences(this);

        // Keep Screen On preference check
        if (overlayPreferences.isKeepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // Bind and setup dock
        bindDockViews();
        setupDockListeners();
        setupHaloAnimation();

        // Shizuku registration
        try {
            Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener);
            Shizuku.addBinderDeadListener(shizukuBinderDeadListener);
            Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener);
            if (ShizukuManager.isShizukuAvailable() && !ShizukuManager.hasShizukuPermission()) {
                ShizukuManager.requestPermission(this);
            }
        } catch (Exception e) {
            Log.d(TAG, "Shizuku listener setup info: " + e.getMessage());
        }

        // Load default initial fragment
        if (savedInstanceState == null) {
            selectTab(Tab.DASHBOARD);
        }
    }

    private void bindDockViews() {
        navTabDashboard = findViewById(R.id.navTabDashboard);
        navTabDiagnostics = findViewById(R.id.navTabDiagnostics);
        btnNavCenter = findViewById(R.id.btnNavCenter);
        centerHalo = findViewById(R.id.centerHalo);
        ivNavCenterIcon = findViewById(R.id.ivNavCenterIcon);
        tvCenterOverlayStatus = findViewById(R.id.tvCenterOverlayStatus);
        navTabStatistics = findViewById(R.id.navTabStatistics);
        navTabSettings = findViewById(R.id.navTabSettings);

        ivNavDashboard = findViewById(R.id.ivNavDashboard);
        tvNavDashboard = findViewById(R.id.tvNavDashboard);
        indNavDashboard = findViewById(R.id.indNavDashboard);

        ivNavDiagnostics = findViewById(R.id.ivNavDiagnostics);
        tvNavDiagnostics = findViewById(R.id.tvNavDiagnostics);
        indNavDiagnostics = findViewById(R.id.indNavDiagnostics);

        ivNavStatistics = findViewById(R.id.ivNavStatistics);
        tvNavStatistics = findViewById(R.id.tvNavStatistics);
        indNavStatistics = findViewById(R.id.indNavStatistics);

        ivNavSettings = findViewById(R.id.ivNavSettings);
        tvNavSettings = findViewById(R.id.tvNavSettings);
        indNavSettings = findViewById(R.id.indNavSettings);
    }

    private void setupDockListeners() {
        navTabDashboard.setOnClickListener(v -> selectTab(Tab.DASHBOARD));
        navTabDiagnostics.setOnClickListener(v -> selectTab(Tab.DIAGNOSTICS));
        btnNavCenter.setOnClickListener(v -> selectTab(Tab.OVERLAY));
        navTabStatistics.setOnClickListener(v -> selectTab(Tab.STATISTICS));
        navTabSettings.setOnClickListener(v -> selectTab(Tab.SETTINGS));
    }

    private void setupHaloAnimation() {
        if (centerHalo == null) return;
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.25f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.25f);
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 0.15f);

        haloPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(centerHalo, scaleX, scaleY, alpha);
        haloPulseAnimator.setDuration(1600);
        haloPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        haloPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        haloPulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        haloPulseAnimator.start();
    }

    public void selectTab(Tab tab) {
        currentTab = tab;
        updateDockVisuals(tab);

        Fragment fragment;
        switch (tab) {
            case DIAGNOSTICS:
                fragment = new DiagnosticsFragment();
                break;
            case OVERLAY:
                fragment = new OverlayControlsFragment();
                break;
            case STATISTICS:
                fragment = new StatisticsFragment();
                break;
            case SETTINGS:
                fragment = new SettingsFragment();
                break;
            case DASHBOARD:
            default:
                fragment = new DashboardFragment();
                break;
        }

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragmentContainer, fragment)
                .commitAllowingStateLoss();
    }

    private void updateDockVisuals(Tab tab) {
        int activeColor = ContextCompat.getColor(this, R.color.figma_cyan);
        int inactiveColor = ContextCompat.getColor(this, R.color.figma_text_muted);

        // Dashboard
        boolean isDash = (tab == Tab.DASHBOARD);
        ivNavDashboard.setImageTintList(ColorStateList.valueOf(isDash ? activeColor : inactiveColor));
        tvNavDashboard.setTextColor(isDash ? activeColor : inactiveColor);
        indNavDashboard.setVisibility(isDash ? View.VISIBLE : View.INVISIBLE);

        // Diagnostics
        boolean isDiag = (tab == Tab.DIAGNOSTICS);
        ivNavDiagnostics.setImageTintList(ColorStateList.valueOf(isDiag ? activeColor : inactiveColor));
        tvNavDiagnostics.setTextColor(isDiag ? activeColor : inactiveColor);
        indNavDiagnostics.setVisibility(isDiag ? View.VISIBLE : View.INVISIBLE);

        // Statistics
        boolean isStats = (tab == Tab.STATISTICS);
        ivNavStatistics.setImageTintList(ColorStateList.valueOf(isStats ? activeColor : inactiveColor));
        tvNavStatistics.setTextColor(isStats ? activeColor : inactiveColor);
        indNavStatistics.setVisibility(isStats ? View.VISIBLE : View.INVISIBLE);

        // Settings
        boolean isSettings = (tab == Tab.SETTINGS);
        ivNavSettings.setImageTintList(ColorStateList.valueOf(isSettings ? activeColor : inactiveColor));
        tvNavSettings.setTextColor(isSettings ? activeColor : inactiveColor);
        indNavSettings.setVisibility(isSettings ? View.VISIBLE : View.INVISIBLE);

        updateCenterButtonVisuals();
    }

    private void updateCenterButtonVisuals() {
        boolean isOverlayTab = (currentTab == Tab.OVERLAY);
        boolean isOverlayRunning = OverlayService.isRunning;

        if (isOverlayRunning) {
            tvCenterOverlayStatus.setText("ACTIVE");
            tvCenterOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.status_optimal));
            if (centerHalo != null) {
                centerHalo.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_optimal)));
            }
        } else {
            tvCenterOverlayStatus.setText(isOverlayTab ? "OPEN" : "OVERLAY");
            tvCenterOverlayStatus.setTextColor(ContextCompat.getColor(this, isOverlayTab ? R.color.figma_cyan : R.color.figma_text_muted));
            if (centerHalo != null) {
                centerHalo.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.dock_halo)));
            }
        }

        if (btnNavCenter != null) {
            btnNavCenter.setScaleX(isOverlayTab ? 1.08f : 1.0f);
            btnNavCenter.setScaleY(isOverlayTab ? 1.08f : 1.0f);
        }
    }

    public void updateKeepScreenOn(boolean keepOn) {
        if (keepOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public long getSessionStartTimeMs() {
        return sessionStartTimeMs;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isAppInForeground = true;
        updateCenterButtonVisuals();

        // Start GameStateService so in-app dashboards update in real-time
        try {
            Intent gameServiceIntent = new Intent(this, GameStateService.class);
            startService(gameServiceIntent);
        } catch (Exception ignored) {}

        try {
            IntentFilter filter = new IntentFilter(OverlayService.ACTION_OVERLAY_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(overlayStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(overlayStateReceiver, filter);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(overlayStateReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onStop() {
        super.onStop();
        isAppInForeground = false;
        // Ultra-light: If neither Overlay nor Session Recording is active, KILL GameStateService!
        boolean overlayRunning = OverlayService.isRunning;
        boolean isRecording = SessionAnalyticsTracker.getInstance(this).isRecording();
        if (!overlayRunning && !isRecording) {
            try {
                Intent gameServiceIntent = new Intent(this, GameStateService.class);
                stopService(gameServiceIntent);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (haloPulseAnimator != null) {
            haloPulseAnimator.cancel();
        }
        try {
            Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener);
            Shizuku.removeBinderDeadListener(shizukuBinderDeadListener);
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener);
        } catch (Exception ignored) {}
    }
}
