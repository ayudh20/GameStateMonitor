package com.gamestate.monitor.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.gamestate.monitor.R;
import com.gamestate.monitor.model.OverlayPreferences;
import com.gamestate.monitor.service.OverlayService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

public class OverlayControlsFragment extends Fragment {

    // Preview
    private CardHeaderView headerHudPreview;
    private LinearLayout previewPillView;
    private View vPreviewDot;
    private TextView tvPreviewFps;
    private TextView tvPreviewCpu;
    private TextView tvPreviewGpu;
    private TextView tvPreviewRam;
    private TextView tvPreviewTemp;
    private MaterialButton btnMasterToggleOverlay;

    // Presets
    private TextView chipCompact;
    private TextView chipBalanced;
    private TextView chipDetailed;

    // Metric Switches
    private MaterialSwitch switchFps;
    private MaterialSwitch switchCpu;
    private MaterialSwitch switchGpu;
    private MaterialSwitch switchRam;
    private MaterialSwitch switchBatTemp;
    private MaterialSwitch switchCpuTemp;
    private MaterialSwitch switchBatLevel;
    private MaterialSwitch switchRefreshRate;
    private MaterialSwitch switchGameName;

    // Appearance Sliders
    private Slider sliderOpacity;
    private Slider sliderTextSize;
    private Slider sliderCornerRadius;
    private TextView tvOpacityValue;
    private TextView tvTextSizeValue;
    private TextView tvCornerRadiusValue;

    // Color Swatches
    private View swatchCyan;
    private View swatchGreen;
    private View swatchAmber;
    private View swatchRed;
    private View swatchPurple;

    // Positioning
    private TextView btnPosFreeDrag;
    private TextView btnPosTopRight;

    private OverlayPreferences prefs;

    private final BroadcastReceiver overlayStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (OverlayService.ACTION_OVERLAY_STATE_CHANGED.equals(intent.getAction())) {
                updateMasterButtonState();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_overlay_controls, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = new OverlayPreferences(requireContext());
        bindViews(view);
        loadCurrentPreferences();
        setupListeners();
        updateMasterButtonState();
        refreshLivePreview();
    }

    private void bindViews(View root) {
        headerHudPreview = root.findViewById(R.id.headerHudPreview);
        previewPillView = root.findViewById(R.id.previewPillView);
        vPreviewDot = root.findViewById(R.id.vPreviewDot);
        tvPreviewFps = root.findViewById(R.id.tvPreviewFps);
        tvPreviewCpu = root.findViewById(R.id.tvPreviewCpu);
        tvPreviewGpu = root.findViewById(R.id.tvPreviewGpu);
        tvPreviewRam = root.findViewById(R.id.tvPreviewRam);
        tvPreviewTemp = root.findViewById(R.id.tvPreviewTemp);
        btnMasterToggleOverlay = root.findViewById(R.id.btnMasterToggleOverlay);

        chipCompact = root.findViewById(R.id.chipPresetCompact);
        chipBalanced = root.findViewById(R.id.chipPresetBalanced);
        chipDetailed = root.findViewById(R.id.chipPresetDetailed);

        switchFps = root.findViewById(R.id.switchFps);
        switchCpu = root.findViewById(R.id.switchCpu);
        switchGpu = root.findViewById(R.id.switchGpu);
        switchRam = root.findViewById(R.id.switchRam);
        switchBatTemp = root.findViewById(R.id.switchBatTemp);
        switchCpuTemp = root.findViewById(R.id.switchCpuTemp);
        switchBatLevel = root.findViewById(R.id.switchBatLevel);
        switchRefreshRate = root.findViewById(R.id.switchRefreshRate);
        switchGameName = root.findViewById(R.id.switchGameName);

        sliderOpacity = root.findViewById(R.id.sliderOpacity);
        sliderTextSize = root.findViewById(R.id.sliderTextSize);
        sliderCornerRadius = root.findViewById(R.id.sliderCornerRadius);
        tvOpacityValue = root.findViewById(R.id.tvOpacityValue);
        tvTextSizeValue = root.findViewById(R.id.tvTextSizeValue);
        tvCornerRadiusValue = root.findViewById(R.id.tvCornerRadiusValue);

        swatchCyan = root.findViewById(R.id.swatchCyan);
        swatchGreen = root.findViewById(R.id.swatchGreen);
        swatchAmber = root.findViewById(R.id.swatchAmber);
        swatchRed = root.findViewById(R.id.swatchRed);
        swatchPurple = root.findViewById(R.id.swatchPurple);

        btnPosFreeDrag = root.findViewById(R.id.btnPosFreeDrag);
        btnPosTopRight = root.findViewById(R.id.btnPosTopRight);
    }

    private void loadCurrentPreferences() {
        updatePresetChipHighlight(prefs.getPreset());

        switchFps.setChecked(prefs.isShowFps());
        switchCpu.setChecked(prefs.isShowCpu());
        switchGpu.setChecked(prefs.isShowGpu());
        switchRam.setChecked(prefs.isShowRam());
        switchBatTemp.setChecked(prefs.isShowBatteryTemp());
        switchCpuTemp.setChecked(prefs.isShowCpuTemp());
        switchBatLevel.setChecked(prefs.isShowBatteryLevel());
        switchRefreshRate.setChecked(prefs.isShowRefreshRate());
        switchGameName.setChecked(prefs.isShowGameName());

        int opacityPct = Math.round(prefs.getOpacity() * 100);
        sliderOpacity.setValue(opacityPct);
        tvOpacityValue.setText(opacityPct + "%");

        int textSize = prefs.getTextSizeSp();
        sliderTextSize.setValue(textSize);
        tvTextSizeValue.setText(textSize + " sp");

        int radius = prefs.getCornerRadiusDp();
        sliderCornerRadius.setValue(radius);
        tvCornerRadiusValue.setText(radius + " dp");

        updatePositionButtons(prefs.getPositionMode());
    }

    private void setupListeners() {
        // Master Start/Stop Button
        btnMasterToggleOverlay.setOnClickListener(v -> toggleOverlayService());

        // Presets
        chipCompact.setOnClickListener(v -> {
            prefs.setPreset(OverlayPreferences.Preset.COMPACT);
            loadCurrentPreferences();
            refreshLivePreview();
        });
        chipBalanced.setOnClickListener(v -> {
            prefs.setPreset(OverlayPreferences.Preset.BALANCED);
            loadCurrentPreferences();
            refreshLivePreview();
        });
        chipDetailed.setOnClickListener(v -> {
            prefs.setPreset(OverlayPreferences.Preset.DETAILED);
            loadCurrentPreferences();
            refreshLivePreview();
        });

        // Metric Switches
        switchFps.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setShowFps(isChecked);
            refreshLivePreview();
        });
        switchCpu.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setShowCpu(isChecked);
            refreshLivePreview();
        });
        switchGpu.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setShowGpu(isChecked);
            refreshLivePreview();
        });
        switchRam.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setShowRam(isChecked);
            refreshLivePreview();
        });
        switchBatTemp.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setShowBatteryTemp(isChecked);
            refreshLivePreview();
        });
        switchCpuTemp.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setShowCpuTemp(isChecked);
            refreshLivePreview();
        });
        switchBatLevel.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setShowBatteryLevel(isChecked);
            refreshLivePreview();
        });
        switchRefreshRate.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setShowRefreshRate(isChecked);
            refreshLivePreview();
        });
        switchGameName.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.setShowGameName(isChecked);
            refreshLivePreview();
        });

        // Sliders
        sliderOpacity.addOnChangeListener((slider, value, fromUser) -> {
            int pct = Math.round(value);
            tvOpacityValue.setText(pct + "%");
            if (fromUser) {
                prefs.setOpacity(value / 100.0f);
                refreshLivePreview();
            }
        });

        sliderTextSize.addOnChangeListener((slider, value, fromUser) -> {
            int sp = Math.round(value);
            tvTextSizeValue.setText(sp + " sp");
            if (fromUser) {
                prefs.setTextSizeSp(sp);
                refreshLivePreview();
            }
        });

        sliderCornerRadius.addOnChangeListener((slider, value, fromUser) -> {
            int dp = Math.round(value);
            tvCornerRadiusValue.setText(dp + " dp");
            if (fromUser) {
                prefs.setCornerRadiusDp(dp);
                refreshLivePreview();
            }
        });

        // Color Swatches
        swatchCyan.setOnClickListener(v -> setAccentColor("#00D2E0"));
        swatchGreen.setOnClickListener(v -> setAccentColor("#00E676"));
        swatchAmber.setOnClickListener(v -> setAccentColor("#FF9100"));
        swatchRed.setOnClickListener(v -> setAccentColor("#FF5252"));
        swatchPurple.setOnClickListener(v -> setAccentColor("#7C4DFF"));

        // Position Buttons
        btnPosFreeDrag.setOnClickListener(v -> {
            prefs.setPositionMode(OverlayPreferences.PositionMode.FREE_DRAG);
            updatePositionButtons(OverlayPreferences.PositionMode.FREE_DRAG);
        });
        btnPosTopRight.setOnClickListener(v -> {
            prefs.setPositionMode(OverlayPreferences.PositionMode.TOP_RIGHT);
            updatePositionButtons(OverlayPreferences.PositionMode.TOP_RIGHT);
        });
    }

    private void setAccentColor(String hex) {
        prefs.setAccentColorHex(hex);
        refreshLivePreview();
    }

    private void updatePresetChipHighlight(OverlayPreferences.Preset preset) {
        chipCompact.setBackgroundResource(preset == OverlayPreferences.Preset.COMPACT ?
                R.drawable.bg_chip_figma_active : R.drawable.bg_chip_figma_inactive);
        chipCompact.setTextColor(ContextCompat.getColor(requireContext(), preset == OverlayPreferences.Preset.COMPACT ?
                R.color.figma_cyan : R.color.figma_text_muted));

        chipBalanced.setBackgroundResource(preset == OverlayPreferences.Preset.BALANCED ?
                R.drawable.bg_chip_figma_active : R.drawable.bg_chip_figma_inactive);
        chipBalanced.setTextColor(ContextCompat.getColor(requireContext(), preset == OverlayPreferences.Preset.BALANCED ?
                R.color.figma_cyan : R.color.figma_text_muted));

        chipDetailed.setBackgroundResource(preset == OverlayPreferences.Preset.DETAILED ?
                R.drawable.bg_chip_figma_active : R.drawable.bg_chip_figma_inactive);
        chipDetailed.setTextColor(ContextCompat.getColor(requireContext(), preset == OverlayPreferences.Preset.DETAILED ?
                R.color.figma_cyan : R.color.figma_text_muted));
    }

    private void updatePositionButtons(OverlayPreferences.PositionMode mode) {
        boolean isFreeDrag = (mode == OverlayPreferences.PositionMode.FREE_DRAG);
        btnPosFreeDrag.setBackgroundResource(isFreeDrag ? R.drawable.bg_chip_figma_active : R.drawable.bg_chip_figma_inactive);
        btnPosFreeDrag.setTextColor(ContextCompat.getColor(requireContext(), isFreeDrag ? R.color.figma_cyan : R.color.figma_text_muted));

        btnPosTopRight.setBackgroundResource(!isFreeDrag ? R.drawable.bg_chip_figma_active : R.drawable.bg_chip_figma_inactive);
        btnPosTopRight.setTextColor(ContextCompat.getColor(requireContext(), !isFreeDrag ? R.color.figma_cyan : R.color.figma_text_muted));
    }

    public void refreshLivePreview() {
        if (!isAdded() || getContext() == null) return;

        // Metric visibility
        tvPreviewFps.setVisibility(prefs.isShowFps() ? View.VISIBLE : View.GONE);
        tvPreviewCpu.setVisibility(prefs.isShowCpu() ? View.VISIBLE : View.GONE);
        tvPreviewGpu.setVisibility(prefs.isShowGpu() ? View.VISIBLE : View.GONE);
        tvPreviewRam.setVisibility(prefs.isShowRam() ? View.VISIBLE : View.GONE);
        tvPreviewTemp.setVisibility(prefs.isShowBatteryTemp() ? View.VISIBLE : View.GONE);

        // Styling
        float opacity = prefs.getOpacity();
        previewPillView.setAlpha(opacity);

        int textSize = prefs.getTextSizeSp();
        tvPreviewFps.setTextSize(textSize);
        tvPreviewCpu.setTextSize(textSize);
        tvPreviewGpu.setTextSize(textSize);
        tvPreviewRam.setTextSize(textSize);
        tvPreviewTemp.setTextSize(textSize);

        // Accent Color
        int accentColor = Color.parseColor(prefs.getAccentColorHex());
        tvPreviewFps.setTextColor(accentColor);

        // Corner Radius & Background Stroke
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        float density = getResources().getDisplayMetrics().density;
        shape.setCornerRadius(prefs.getCornerRadiusDp() * density);
        shape.setColor(Color.argb((int)(opacity * 240), 13, 13, 20));
        shape.setStroke(Math.round(1.5f * density), accentColor);
        previewPillView.setBackground(shape);
    }

    public void updateMasterButtonState() {
        if (!isAdded() || getContext() == null) return;

        boolean running = OverlayService.isRunning;
        if (running) {
            btnMasterToggleOverlay.setText("Stop Floating HUD");
            btnMasterToggleOverlay.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.status_high_load)));
            btnMasterToggleOverlay.setTextColor(Color.WHITE);
            btnMasterToggleOverlay.setIconTint(ColorStateList.valueOf(Color.WHITE));

            if (headerHudPreview != null) {
                headerHudPreview.setEndText("ACTIVE");
                headerHudPreview.setEndTextColor(ContextCompat.getColor(requireContext(), R.color.status_optimal));
            }
        } else {
            btnMasterToggleOverlay.setText("Launch Floating HUD");
            btnMasterToggleOverlay.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.figma_cyan)));
            btnMasterToggleOverlay.setTextColor(Color.BLACK);
            btnMasterToggleOverlay.setIconTint(ColorStateList.valueOf(Color.BLACK));

            if (headerHudPreview != null) {
                headerHudPreview.setEndText("INACTIVE");
                headerHudPreview.setEndTextColor(ContextCompat.getColor(requireContext(), R.color.figma_cyan));
            }
        }
    }

    private void toggleOverlayService() {
        Context ctx = requireContext();
        if (OverlayService.isRunning) {
            Intent serviceIntent = new Intent(ctx, OverlayService.class);
            ctx.stopService(serviceIntent);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(ctx)) {
                Toast.makeText(ctx, "Grant 'Display over other apps' to enable the Gaming HUD", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + ctx.getPackageName()));
                startActivity(intent);
                return;
            }

            Intent serviceIntent = new Intent(ctx, OverlayService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(serviceIntent);
            } else {
                ctx.startService(serviceIntent);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateMasterButtonState();
        try {
            IntentFilter filter = new IntentFilter(OverlayService.ACTION_OVERLAY_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(overlayStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                requireContext().registerReceiver(overlayStateReceiver, filter);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(overlayStateReceiver);
        } catch (Exception ignored) {}
    }
}
