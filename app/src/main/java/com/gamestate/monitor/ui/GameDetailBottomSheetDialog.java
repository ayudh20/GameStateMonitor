package com.gamestate.monitor.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamestate.monitor.R;
import com.gamestate.monitor.model.GameProfile;
import com.gamestate.monitor.util.FormatUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

/**
 * GameDetailBottomSheetDialog
 * ----------------------------
 * Displays detailed lifetime performance, thermal, battery, and session
 * statistics for a specific game.
 */
public class GameDetailBottomSheetDialog extends BottomSheetDialogFragment {

    private static final String ARG_PROFILE = "arg_profile";
    private GameProfile profile;

    public static GameDetailBottomSheetDialog newInstance(GameProfile profile) {
        GameDetailBottomSheetDialog dialog = new GameDetailBottomSheetDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_PROFILE, profile);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            profile = (GameProfile) getArguments().getSerializable(ARG_PROFILE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_game_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (profile == null) return;

        ImageView ivIcon = view.findViewById(R.id.ivDetailIcon);
        TextView tvTitle = view.findViewById(R.id.tvDetailTitle);
        TextView tvPkg = view.findViewById(R.id.tvDetailPackage);
        MaterialButton btnLaunch = view.findViewById(R.id.btnLaunchGame);
        TextView tvNoSessionsNotice = view.findViewById(R.id.tvNoSessionsNotice);

        TextView tvAvgFps = view.findViewById(R.id.tvDetailAvgFps);
        TextView tvBestFps = view.findViewById(R.id.tvDetailBestFps);
        TextView tvAvgStability = view.findViewById(R.id.tvDetailAvgStability);

        TextView tvAvgTemp = view.findViewById(R.id.tvDetailAvgTemp);
        TextView tvPeakTemp = view.findViewById(R.id.tvDetailPeakTemp);
        TextView tvAvgDrain = view.findViewById(R.id.tvDetailAvgDrain);

        TextView tvSessionsCount = view.findViewById(R.id.tvDetailSessionsCount);
        TextView tvTotalPlayTime = view.findViewById(R.id.tvDetailTotalPlayTime);
        TextView tvLongestSession = view.findViewById(R.id.tvDetailLongestSession);

        tvTitle.setText(profile.getAppName());
        tvPkg.setText(profile.getPackageName());
        if (profile.getIconDrawable() != null) {
            ivIcon.setImageDrawable(profile.getIconDrawable());
        }

        btnLaunch.setOnClickListener(v -> {
            try {
                Intent launchIntent = requireContext().getPackageManager().getLaunchIntentForPackage(profile.getPackageName());
                if (launchIntent != null) {
                    startActivity(launchIntent);
                    dismiss();
                } else {
                    Toast.makeText(requireContext(), "Cannot launch " + profile.getAppName(), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Error launching game", Toast.LENGTH_SHORT).show();
            }
        });

        if (!profile.hasRecordedSessions()) {
            tvNoSessionsNotice.setVisibility(View.VISIBLE);
            tvAvgFps.setText("--");
            tvBestFps.setText("--");
            tvAvgStability.setText("--");
            tvAvgTemp.setText("--");
            tvPeakTemp.setText("--");
            tvAvgDrain.setText("--");
            tvSessionsCount.setText("0");
            tvTotalPlayTime.setText("0m");
            tvLongestSession.setText("0m");
        } else {
            tvNoSessionsNotice.setVisibility(View.GONE);
            tvAvgFps.setText(String.format(Locale.getDefault(), "%.1f", profile.getAvgFps()));
            tvBestFps.setText(String.format(Locale.getDefault(), "%.1f", profile.getBestSessionFps()));
            tvAvgStability.setText(String.format(Locale.getDefault(), "%.0f%%", profile.getAvgStabilityPercent()));

            tvAvgTemp.setText(String.format(Locale.getDefault(), "%.0f°C", profile.getAvgTempC()));
            tvPeakTemp.setText(String.format(Locale.getDefault(), "%.0f°C", profile.getPeakTempC()));
            tvAvgDrain.setText(String.format(Locale.getDefault(), "%.1f%%/h", profile.getAvgDrainRatePerHour()));

            tvSessionsCount.setText(String.valueOf(profile.getTotalSessionsCount()));
            tvTotalPlayTime.setText(FormatUtils.formatDuration(profile.getTotalPlayTimeMs()));
            tvLongestSession.setText(FormatUtils.formatDuration(profile.getLongestSessionMs()));
        }
    }
}
