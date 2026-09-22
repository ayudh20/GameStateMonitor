package com.gamestate.monitor.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamestate.monitor.R;
import com.gamestate.monitor.model.GameProfile;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

/**
 * GameComparisonDialog
 * --------------------
 * Displays head-to-head performance, stability, thermal, and battery
 * comparisons between two selected games.
 */
public class GameComparisonDialog extends BottomSheetDialogFragment {

    private static final String ARG_GAME_A = "arg_game_a";
    private static final String ARG_GAME_B = "arg_game_b";

    private GameProfile gameA;
    private GameProfile gameB;

    public static GameComparisonDialog newInstance(GameProfile a, GameProfile b) {
        GameComparisonDialog dialog = new GameComparisonDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_GAME_A, a);
        args.putSerializable(ARG_GAME_B, b);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            gameA = (GameProfile) getArguments().getSerializable(ARG_GAME_A);
            gameB = (GameProfile) getArguments().getSerializable(ARG_GAME_B);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_game_comparison, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (gameA == null || gameB == null) return;

        ImageView ivIconA = view.findViewById(R.id.ivCompIconA);
        TextView tvTitleA = view.findViewById(R.id.tvCompTitleA);
        ImageView ivIconB = view.findViewById(R.id.ivCompIconB);
        TextView tvTitleB = view.findViewById(R.id.tvCompTitleB);

        TextView tvAvgFpsA = view.findViewById(R.id.tvCompAvgFpsA);
        TextView tvAvgFpsB = view.findViewById(R.id.tvCompAvgFpsB);

        TextView tvStabilityA = view.findViewById(R.id.tvCompStabilityA);
        TextView tvStabilityB = view.findViewById(R.id.tvCompStabilityB);

        TextView tvTempA = view.findViewById(R.id.tvCompTempA);
        TextView tvTempB = view.findViewById(R.id.tvCompTempB);

        TextView tvDrainA = view.findViewById(R.id.tvCompDrainA);
        TextView tvDrainB = view.findViewById(R.id.tvCompDrainB);

        tvTitleA.setText(gameA.getAppName());
        tvTitleB.setText(gameB.getAppName());

        if (gameA.getIconDrawable() != null) ivIconA.setImageDrawable(gameA.getIconDrawable());
        if (gameB.getIconDrawable() != null) ivIconB.setImageDrawable(gameB.getIconDrawable());

        // FPS
        tvAvgFpsA.setText(gameA.hasRecordedSessions() ? String.format(Locale.getDefault(), "%.1f", gameA.getAvgFps()) : "--");
        tvAvgFpsB.setText(gameB.hasRecordedSessions() ? String.format(Locale.getDefault(), "%.1f", gameB.getAvgFps()) : "--");

        // Stability
        tvStabilityA.setText(gameA.hasRecordedSessions() ? String.format(Locale.getDefault(), "%.0f%%", gameA.getAvgStabilityPercent()) : "--");
        tvStabilityB.setText(gameB.hasRecordedSessions() ? String.format(Locale.getDefault(), "%.0f%%", gameB.getAvgStabilityPercent()) : "--");

        // Temp
        tvTempA.setText(gameA.hasRecordedSessions() ? String.format(Locale.getDefault(), "%.0f°C", gameA.getAvgTempC()) : "--");
        tvTempB.setText(gameB.hasRecordedSessions() ? String.format(Locale.getDefault(), "%.0f°C", gameB.getAvgTempC()) : "--");

        // Drain
        tvDrainA.setText(gameA.hasRecordedSessions() ? String.format(Locale.getDefault(), "%.1f%%/h", gameA.getAvgDrainRatePerHour()) : "--");
        tvDrainB.setText(gameB.hasRecordedSessions() ? String.format(Locale.getDefault(), "%.1f%%/h", gameB.getAvgDrainRatePerHour()) : "--");
    }
}
