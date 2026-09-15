package com.gamestate.monitor.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.gamestate.monitor.MainActivity;
import com.gamestate.monitor.R;
import com.gamestate.monitor.model.OverlayPreferences;
import com.gamestate.monitor.shizuku.ShizukuManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import rikka.shizuku.Shizuku;

public class SettingsFragment extends Fragment {

    private CardHeaderView headerShizuku;
    private MaterialButton btnCheckShizuku;
    private MaterialButton btnCopyAdbCommandSettings;

    private RadioGroup rgUpdateInterval;
    private RadioButton rbInterval1s;
    private RadioButton rbInterval15s;
    private RadioButton rbInterval2s;

    private MaterialSwitch switchKeepScreenOn;
    private MaterialSwitch switchAutoHideOverlay;
    private MaterialButton btnResetAllSettings;

    private OverlayPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs = new OverlayPreferences(requireContext());

        bindViews(view);
        loadPreferences();
        setupListeners();
        updateShizukuBadge();
    }

    private void bindViews(View root) {
        headerShizuku = root.findViewById(R.id.headerShizuku);
        btnCheckShizuku = root.findViewById(R.id.btnCheckShizuku);
        btnCopyAdbCommandSettings = root.findViewById(R.id.btnCopyAdbCommandSettings);

        rgUpdateInterval = root.findViewById(R.id.rgUpdateInterval);
        rbInterval1s = root.findViewById(R.id.rbInterval1s);
        rbInterval15s = root.findViewById(R.id.rbInterval15s);
        rbInterval2s = root.findViewById(R.id.rbInterval2s);

        switchKeepScreenOn = root.findViewById(R.id.switchKeepScreenOn);
        switchAutoHideOverlay = root.findViewById(R.id.switchAutoHideOverlay);
        btnResetAllSettings = root.findViewById(R.id.btnResetAllSettings);

        TextView tvAboutAppVersion = root.findViewById(R.id.tvAboutAppVersion);
        if (tvAboutAppVersion != null) {
            String ver = com.gamestate.monitor.BuildConfig.VERSION_NAME;
            tvAboutAppVersion.setText("GameState Monitor v" + ver + "\nPure Android Hardware Telemetry Engine\nZero fake data • Direct SurfaceFlinger integration");
        }
    }

    private void loadPreferences() {
        long interval = prefs.getTelemetryIntervalMs();
        if (interval <= 1000) {
            rbInterval1s.setChecked(true);
        } else if (interval <= 1500) {
            rbInterval15s.setChecked(true);
        } else {
            rbInterval2s.setChecked(true);
        }

        switchKeepScreenOn.setChecked(prefs.isKeepScreenOn());
        switchAutoHideOverlay.setChecked(prefs.isAutoHideOverlay());
    }

    private void setupListeners() {
        btnCheckShizuku.setOnClickListener(v -> {
            if (ShizukuManager.isShizukuAvailable()) {
                if (ShizukuManager.hasShizukuPermission()) {
                    Toast.makeText(requireContext(), "Shizuku is running and authorized!", Toast.LENGTH_SHORT).show();
                    updateShizukuBadge();
                } else {
                    ShizukuManager.requestPermission(requireActivity());
                }
            } else {
                Toast.makeText(requireContext(), "Shizuku service is not running on device.", Toast.LENGTH_LONG).show();
            }
        });

        btnCopyAdbCommandSettings.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                String cmd = "adb shell pm grant " + requireContext().getPackageName() + " android.permission.DUMP && " +
                             "adb shell pm grant " + requireContext().getPackageName() + " android.permission.PACKAGE_USAGE_STATS";
                ClipData clip = ClipData.newPlainText("ADB Command", cmd);
                cm.setPrimaryClip(clip);
                Toast.makeText(requireContext(), "ADB unlock command copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });

        rgUpdateInterval.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbInterval1s) {
                prefs.setTelemetryIntervalMs(1000L);
            } else if (checkedId == R.id.rbInterval15s) {
                prefs.setTelemetryIntervalMs(1500L);
            } else if (checkedId == R.id.rbInterval2s) {
                prefs.setTelemetryIntervalMs(2000L);
            }
        });

        switchKeepScreenOn.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.setKeepScreenOn(isChecked);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).updateKeepScreenOn(isChecked);
            }
        });

        switchAutoHideOverlay.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.setAutoHideOverlay(isChecked);
        });

        btnResetAllSettings.setOnClickListener(v -> {
            prefs.resetToDefaults();
            loadPreferences();
            Toast.makeText(requireContext(), "Preferences restored to defaults.", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateShizukuBadge() {
        if (headerShizuku != null) {
            boolean authed = ShizukuManager.isShizukuAvailable() && ShizukuManager.hasShizukuPermission();
            headerShizuku.setEndText(authed ? "AUTHORIZED" : "DISCONNECTED");
            headerShizuku.setEndTextColor(ContextCompat.getColor(requireContext(),
                    authed ? R.color.status_optimal : R.color.status_high_load));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateShizukuBadge();
    }
}
