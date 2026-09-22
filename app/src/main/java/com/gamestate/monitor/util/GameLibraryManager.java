package com.gamestate.monitor.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.util.Log;

import com.gamestate.monitor.fps.GameDetector;
import com.gamestate.monitor.model.GameProfile;
import com.gamestate.monitor.model.GameSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GameLibraryManager
 * ------------------
 * Scans, indexes, and maintains the device's installed games catalog.
 * Correlates historical benchmark sessions from SessionHistoryManager
 * to compute per-game lifetime metrics, favorites, and custom ordering.
 */
public class GameLibraryManager {

    private static final String TAG = "GameLibraryManager";
    private static final String PREFS_NAME = "game_library_prefs";
    private static final String KEY_FAVORITES = "favorite_packages";

    private static GameLibraryManager instance;
    private final Context context;
    private final PackageManager packageManager;
    private final SharedPreferences prefs;
    private final GameDetector gameDetector;
    private final SessionHistoryManager historyManager;

    private GameLibraryManager(Context context) {
        this.context = context.getApplicationContext();
        this.packageManager = this.context.getPackageManager();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gameDetector = new GameDetector(this.context);
        this.historyManager = SessionHistoryManager.getInstance(this.context);
    }

    public static synchronized GameLibraryManager getInstance(Context context) {
        if (instance == null) {
            instance = new GameLibraryManager(context);
        }
        return instance;
    }

    /**
     * Scans installed applications and returns a list of GameProfiles.
     * Integrates with SessionHistoryManager to populate lifetime statistics.
     */
    public List<GameProfile> getInstalledGames() {
        List<GameProfile> games = new ArrayList<>();
        Set<String> processedPackages = new HashSet<>();
        Set<String> favorites = getFavoritePackages();

        // 1. Query launcher applications
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(mainIntent, 0);
        for (ResolveInfo ri : resolveInfos) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg == null || pkg.equals(context.getPackageName()) || processedPackages.contains(pkg)) {
                continue;
            }

            if (isGame(pkg, ri.activityInfo.applicationInfo)) {
                processedPackages.add(pkg);
                String label = gameDetector.getAppLabel(pkg);
                GameProfile profile = new GameProfile(pkg, label);
                try {
                    profile.setIconDrawable(ri.loadIcon(packageManager));
                } catch (Exception e) {
                    profile.setIconDrawable(packageManager.getDefaultActivityIcon());
                }
                profile.setFavorite(favorites.contains(pkg));
                aggregateSessionStats(profile);
                games.add(profile);
            }
        }

        // 2. Also check if any past recorded sessions in history exist for games that might not have CATEGORY_LAUNCHER
        List<GameSession> allSessions = historyManager.getAllSessions();
        for (GameSession s : allSessions) {
            String pkg = s.getPackageName();
            if (pkg != null && !processedPackages.contains(pkg)) {
                try {
                    ApplicationInfo appInfo = packageManager.getApplicationInfo(pkg, 0);
                    processedPackages.add(pkg);
                    GameProfile profile = new GameProfile(pkg, s.getAppName());
                    try {
                        profile.setIconDrawable(packageManager.getApplicationIcon(appInfo));
                    } catch (Exception e) {
                        profile.setIconDrawable(packageManager.getDefaultActivityIcon());
                    }
                    profile.setFavorite(favorites.contains(pkg));
                    aggregateSessionStats(profile);
                    games.add(profile);
                } catch (PackageManager.NameNotFoundException ignored) {
                    // App was uninstalled since the session was saved
                }
            }
        }

        // 3. Sort: Favorites first, then most recently played, then alphabetical
        Collections.sort(games, new Comparator<GameProfile>() {
            @Override
            public int compare(GameProfile g1, GameProfile g2) {
                if (g1.isFavorite() != g2.isFavorite()) {
                    return g1.isFavorite() ? -1 : 1;
                }
                if (g1.getLastPlayedMs() != g2.getLastPlayedMs()) {
                    return Long.compare(g2.getLastPlayedMs(), g1.getLastPlayedMs());
                }
                return g1.getAppName().compareToIgnoreCase(g2.getAppName());
            }
        });

        return games;
    }

    /**
     * Determines whether an application is a game using package name hints,
     * category metadata, or known identifiers.
     */
    private boolean isGame(String packageName, ApplicationInfo appInfo) {
        if (gameDetector.isGamePackage(packageName)) {
            return true;
        }
        if (appInfo != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                    return true;
                }
            }
            if ((appInfo.flags & ApplicationInfo.FLAG_IS_GAME) != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Aggregates session history for this game package.
     */
    public void aggregateSessionStats(GameProfile profile) {
        if (profile == null) return;
        List<GameSession> allSessions = historyManager.getAllSessions();
        List<GameSession> gameSessions = new ArrayList<>();

        for (GameSession s : allSessions) {
            if (profile.getPackageName().equals(s.getPackageName())) {
                gameSessions.add(s);
            }
        }

        if (gameSessions.isEmpty()) {
            profile.setTotalSessionsCount(0);
            profile.setTotalPlayTimeMs(0);
            profile.setLastPlayedMs(0);
            return;
        }

        profile.setTotalSessionsCount(gameSessions.size());

        long totalPlayTime = 0;
        long longestSession = 0;
        long lastPlayed = 0;

        float fpsSum = 0;
        float bestFps = 0;
        float stabilitySum = 0;

        float tempSum = 0;
        float peakTemp = 0;

        float drainSum = 0;

        for (GameSession s : gameSessions) {
            totalPlayTime += s.getDurationMs();
            if (s.getDurationMs() > longestSession) longestSession = s.getDurationMs();
            if (s.getEndTimeMs() > lastPlayed) lastPlayed = s.getEndTimeMs();

            fpsSum += s.getAvgFps();
            if (s.getAvgFps() > bestFps) bestFps = s.getAvgFps();
            stabilitySum += s.getStabilityScorePercent();

            tempSum += s.getAvgTempC();
            if (s.getPeakTempC() > peakTemp) peakTemp = s.getPeakTempC();

            drainSum += s.getAvgDrainRatePerHour();
        }

        int count = gameSessions.size();
        profile.setTotalPlayTimeMs(totalPlayTime);
        profile.setLongestSessionMs(longestSession);
        profile.setLastPlayedMs(lastPlayed);

        profile.setAvgFps(Math.round((fpsSum / count) * 10.0f) / 10.0f);
        profile.setBestSessionFps(Math.round(bestFps * 10.0f) / 10.0f);
        profile.setAvgStabilityPercent(Math.round(stabilitySum / count));

        profile.setAvgTempC(Math.round(tempSum / count));
        profile.setPeakTempC(Math.round(peakTemp));

        profile.setAvgDrainRatePerHour(Math.round((drainSum / count) * 10.0f) / 10.0f);
    }

    public Set<String> getFavoritePackages() {
        return new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    public void setGameFavorite(String packageName, boolean favorite) {
        if (packageName == null) return;
        Set<String> set = getFavoritePackages();
        if (favorite) {
            set.add(packageName);
        } else {
            set.remove(packageName);
        }
        prefs.edit().putStringSet(KEY_FAVORITES, set).apply();
    }

    public boolean isGameFavorite(String packageName) {
        return getFavoritePackages().contains(packageName);
    }
}
