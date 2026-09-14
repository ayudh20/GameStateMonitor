package com.gamestate.monitor.util;

import android.content.Context;
import android.util.Log;

import com.gamestate.monitor.model.GameSession;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SessionHistoryManager
 * ---------------------
 * Thread-safe persistent storage manager for gaming session benchmarks.
 * Stores records in a compact JSON file in app-internal storage.
 */
public class SessionHistoryManager {

    private static final String TAG = "SessionHistoryManager";
    private static final String SESSIONS_FILE = "game_sessions.json";
    private static final int MAX_SESSIONS = 25;

    private static SessionHistoryManager instance;
    private final Context context;
    private final Object fileLock = new Object();

    private SessionHistoryManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized SessionHistoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionHistoryManager(context);
        }
        return instance;
    }

    public void saveSession(GameSession session) {
        if (session == null) return;
        synchronized (fileLock) {
            List<GameSession> sessions = getAllSessions();
            // Remove if existing with same id
            for (int i = 0; i < sessions.size(); i++) {
                if (sessions.get(i).getSessionId().equals(session.getSessionId())) {
                    sessions.remove(i);
                    break;
                }
            }
            // Add to top (newest first)
            sessions.add(0, session);

            // Cap at MAX_SESSIONS
            if (sessions.size() > MAX_SESSIONS) {
                sessions = sessions.subList(0, MAX_SESSIONS);
            }

            writeSessionsToFile(sessions);
        }
    }

    public List<GameSession> getAllSessions() {
        synchronized (fileLock) {
            List<GameSession> list = new ArrayList<>();
            File file = new File(context.getFilesDir(), SESSIONS_FILE);
            if (!file.exists()) {
                return list;
            }

            try (FileInputStream fis = new FileInputStream(file);
                 InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(isr)) {

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                JSONArray arr = new JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj != null) {
                        GameSession s = GameSession.fromJson(obj);
                        if (s != null) list.add(s);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading session history: " + e.getMessage());
            }

            return list;
        }
    }

    public GameSession getSessionById(String id) {
        if (id == null) return null;
        for (GameSession s : getAllSessions()) {
            if (id.equals(s.getSessionId())) return s;
        }
        return null;
    }

    public boolean deleteSession(String sessionId) {
        if (sessionId == null) return false;
        synchronized (fileLock) {
            List<GameSession> sessions = getAllSessions();
            boolean removed = false;
            for (int i = 0; i < sessions.size(); i++) {
                if (sessionId.equals(sessions.get(i).getSessionId())) {
                    sessions.remove(i);
                    removed = true;
                    break;
                }
            }
            if (removed) {
                writeSessionsToFile(sessions);
            }
            return removed;
        }
    }

    public void clearHistory() {
        synchronized (fileLock) {
            File file = new File(context.getFilesDir(), SESSIONS_FILE);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    private void writeSessionsToFile(List<GameSession> sessions) {
        try {
            JSONArray arr = new JSONArray();
            for (GameSession s : sessions) {
                arr.put(s.toJson());
            }

            File file = new File(context.getFilesDir(), SESSIONS_FILE);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(arr.toString().getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error writing session history: " + e.getMessage());
        }
    }
}
