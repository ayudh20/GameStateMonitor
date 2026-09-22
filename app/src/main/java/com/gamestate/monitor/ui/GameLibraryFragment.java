package com.gamestate.monitor.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamestate.monitor.R;
import com.gamestate.monitor.model.GameProfile;
import com.gamestate.monitor.util.GameLibraryManager;

import java.util.ArrayList;
import java.util.List;

/**
 * GameLibraryFragment
 * -------------------
 * Tab displaying all detected installed games on device, lifetime statistics,
 * search filtering, favorite pinning, and game comparison launcher.
 */
public class GameLibraryFragment extends Fragment implements GameLibraryAdapter.OnGameClickListener {

    private GameLibraryManager libraryManager;
    private GameLibraryAdapter adapter;
    private List<GameProfile> allGames = new ArrayList<>();

    private FrameLayout btnHeaderCompare;
    private EditText etSearchGames;
    private TextView chipFavoritesFilter;
    private TextView tvEmptyLibrary;
    private RecyclerView rvGameLibrary;

    private boolean isFavoritesFilterActive = false;
    private String currentSearchQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_game_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        libraryManager = GameLibraryManager.getInstance(requireContext());

        bindViews(view);
        setupRecyclerView();
        setupListeners();
        loadGames();
    }

    private void bindViews(View root) {
        btnHeaderCompare = root.findViewById(R.id.btnHeaderCompare);
        etSearchGames = root.findViewById(R.id.etSearchGames);
        chipFavoritesFilter = root.findViewById(R.id.chipFavoritesFilter);
        tvEmptyLibrary = root.findViewById(R.id.tvEmptyLibrary);
        rvGameLibrary = root.findViewById(R.id.rvGameLibrary);
    }

    private void setupRecyclerView() {
        adapter = new GameLibraryAdapter(requireContext(), this);
        rvGameLibrary.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvGameLibrary.setAdapter(adapter);
    }

    private void setupListeners() {
        btnHeaderCompare.setOnClickListener(v -> launchComparison());

        chipFavoritesFilter.setOnClickListener(v -> {
            isFavoritesFilterActive = !isFavoritesFilterActive;
            updateFilterChipVisuals();
            applyFilter();
        });

        etSearchGames.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s != null ? s.toString().trim().toLowerCase() : "";
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void updateFilterChipVisuals() {
        if (isFavoritesFilterActive) {
            chipFavoritesFilter.setBackgroundResource(R.drawable.bg_chip_figma_active);
            chipFavoritesFilter.setTextColor(Color.parseColor("#00D2E0"));
        } else {
            chipFavoritesFilter.setBackgroundResource(R.drawable.bg_chip_figma_inactive);
            chipFavoritesFilter.setTextColor(Color.parseColor("#8A8A98"));
        }
    }

    public void loadGames() {
        allGames = libraryManager.getInstalledGames();
        applyFilter();
    }

    private void applyFilter() {
        List<GameProfile> filtered = new ArrayList<>();
        for (GameProfile g : allGames) {
            if (isFavoritesFilterActive && !g.isFavorite()) {
                continue;
            }
            if (!currentSearchQuery.isEmpty()) {
                boolean matchName = g.getAppName() != null && g.getAppName().toLowerCase().contains(currentSearchQuery);
                boolean matchPkg = g.getPackageName() != null && g.getPackageName().toLowerCase().contains(currentSearchQuery);
                if (!matchName && !matchPkg) continue;
            }
            filtered.add(g);
        }

        adapter.setGames(filtered);
        tvEmptyLibrary.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onGameClick(GameProfile profile) {
        GameDetailBottomSheetDialog dialog = GameDetailBottomSheetDialog.newInstance(profile);
        dialog.show(getParentFragmentManager(), "GameDetailDialog");
    }

    @Override
    public void onFavoriteToggle(GameProfile profile, boolean isFavorite) {
        libraryManager.setGameFavorite(profile.getPackageName(), isFavorite);
        if (isFavoritesFilterActive) {
            applyFilter();
        }
    }

    @Override
    public void onGameLongClick(GameProfile profile) {
        // Quick compare trigger
        if (allGames.size() >= 2) {
            GameProfile other = null;
            for (GameProfile g : allGames) {
                if (!g.getPackageName().equals(profile.getPackageName())) {
                    other = g;
                    break;
                }
            }
            if (other != null) {
                GameComparisonDialog dialog = GameComparisonDialog.newInstance(profile, other);
                dialog.show(getParentFragmentManager(), "GameComparisonDialog");
            }
        }
    }

    private void launchComparison() {
        if (allGames.size() < 2) {
            Toast.makeText(requireContext(), "At least 2 games are required for comparison.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Pick top two games
        GameProfile gameA = allGames.get(0);
        GameProfile gameB = allGames.get(1);

        GameComparisonDialog dialog = GameComparisonDialog.newInstance(gameA, gameB);
        dialog.show(getParentFragmentManager(), "GameComparisonDialog");
    }

    @Override
    public void onResume() {
        super.onResume();
        loadGames();
    }
}
