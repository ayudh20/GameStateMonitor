package com.gamestate.monitor.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gamestate.monitor.R;
import com.gamestate.monitor.model.GameProfile;
import com.gamestate.monitor.util.FormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * GameLibraryAdapter
 * ------------------
 * Renders games in the library list with favorite toggling and click handling.
 */
public class GameLibraryAdapter extends RecyclerView.Adapter<GameLibraryAdapter.GameViewHolder> {

    public interface OnGameClickListener {
        void onGameClick(GameProfile profile);
        void onFavoriteToggle(GameProfile profile, boolean isFavorite);
        void onGameLongClick(GameProfile profile);
    }

    private final Context context;
    private final List<GameProfile> games = new ArrayList<>();
    private final OnGameClickListener listener;

    public GameLibraryAdapter(Context context, OnGameClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setGames(List<GameProfile> newGames) {
        this.games.clear();
        if (newGames != null) {
            this.games.addAll(newGames);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_game_card, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        GameProfile game = games.get(position);
        holder.bind(game, listener);
    }

    @Override
    public int getItemCount() {
        return games.size();
    }

    static class GameViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivIcon;
        private final TextView tvTitle;
        private final TextView tvPackage;
        private final FrameLayout btnFavorite;
        private final ImageView ivFavoriteStar;
        private final TextView tvLastPlayed;
        private final TextView tvTotalSessions;
        private final TextView tvPlayTime;

        public GameViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivGameIcon);
            tvTitle = itemView.findViewById(R.id.tvGameTitle);
            tvPackage = itemView.findViewById(R.id.tvGamePackage);
            btnFavorite = itemView.findViewById(R.id.btnFavorite);
            ivFavoriteStar = itemView.findViewById(R.id.ivFavoriteStar);
            tvLastPlayed = itemView.findViewById(R.id.tvLastPlayed);
            tvTotalSessions = itemView.findViewById(R.id.tvTotalSessions);
            tvPlayTime = itemView.findViewById(R.id.tvPlayTime);
        }

        public void bind(GameProfile game, OnGameClickListener listener) {
            tvTitle.setText(game.getAppName());
            tvPackage.setText(game.getPackageName());

            if (game.getIconDrawable() != null) {
                ivIcon.setImageDrawable(game.getIconDrawable());
            } else {
                ivIcon.setImageResource(R.drawable.ic_gamepad);
            }

            if (game.isFavorite()) {
                ivFavoriteStar.setColorFilter(Color.parseColor("#FFD600"));
            } else {
                ivFavoriteStar.setColorFilter(Color.parseColor("#8A8A98"));
            }

            btnFavorite.setOnClickListener(v -> {
                boolean newFav = !game.isFavorite();
                game.setFavorite(newFav);
                if (newFav) {
                    ivFavoriteStar.setColorFilter(Color.parseColor("#FFD600"));
                } else {
                    ivFavoriteStar.setColorFilter(Color.parseColor("#8A8A98"));
                }
                if (listener != null) listener.onFavoriteToggle(game, newFav);
            });

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onGameClick(game);
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onGameLongClick(game);
                return true;
            });

            if (game.hasRecordedSessions()) {
                long diff = System.currentTimeMillis() - game.getLastPlayedMs();
                if (diff < 60 * 60 * 1000) {
                    tvLastPlayed.setText((diff / (60 * 1000)) + "m ago");
                } else if (diff < 24 * 60 * 60 * 1000) {
                    tvLastPlayed.setText((diff / (60 * 60 * 1000)) + "h ago");
                } else {
                    tvLastPlayed.setText((diff / (24 * 60 * 60 * 1000)) + "d ago");
                }
                tvTotalSessions.setText(String.valueOf(game.getTotalSessionsCount()));
                tvPlayTime.setText(FormatUtils.formatDuration(game.getTotalPlayTimeMs()));
            } else {
                tvLastPlayed.setText("Never");
                tvTotalSessions.setText("0");
                tvPlayTime.setText("0m");
            }
        }
    }
}
