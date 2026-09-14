# Implementation Plan: On-Demand Session Recording & Zero-Overhead Lifecycle

## Objective
1. **Kill all idle background polling**: Stop all background services and CPU/thermal loops when GameState Monitor is not in foreground, unless the Overlay is active or a session is being actively recorded.
2. **On-Demand Session Recording**: Do NOT automatically record when the overlay is opened. Instead, give the user complete control via an explicit **"Record Session"** / **"Stop Recording"** button:
   - In the **Statistics Tab** (`fragment_statistics.xml` / `StatisticsFragment.java`).
   - In the **Floating Overlay HUD** (`layout_overlay_hud.xml` / `OverlayService.java`) with a quick in-game `[● REC]` button.
   - In the **System Notification Shade** with a direct "Stop & Save" action.

---

## Proposed Changes

### 1. Ultra-Lightweight Service Lifecycle
- **`MainActivity.java`**:
  - Track app visibility via `isAppInForeground`.
  - In `onResume()`: Start `GameStateService` so in-app dashboards update.
  - In `onStop()`: If `!OverlayService.isRunning` and `!SessionAnalyticsTracker.isRecording()`, stop `GameStateService` immediately. Zero background threads, zero CPU/battery drain.
- **`GameStateService.java`**:
  - Check whether `isAppInForeground`, `OverlayService.isRunning`, or `SessionAnalyticsTracker.isRecording()`. If none are active, terminate polling and `stopSelf()`.
  - In `evaluateGameState()`: Only feed metrics to `SessionAnalyticsTracker` if `SessionAnalyticsTracker.isRecording()` is active.
- **`OverlayService.java`**:
  - In `onDestroy()`: If `!SessionAnalyticsTracker.isRecording()` and `!MainActivity.isAppInForeground`, stop `GameStateService`.

### 2. Explicit Recording Control in `SessionAnalyticsTracker.java`
- Introduce state machine:
  - `isRecording()` boolean flag (defaults to `false`).
  - `startRecording()`: Captures baseline battery, thermal, and start timestamp, and resets metric buffers.
  - `stopRecording()`: Finalizes calculations, generates grade, saves session to `SessionHistoryManager`, updates `lastCompletedSession`, and posts notification update.
  - Broadcasts `ACTION_RECORDING_STATE_CHANGED` whenever recording starts or stops.

### 3. Statistics Tab UI (`fragment_statistics.xml` & `StatisticsFragment.java`)
- Add a prominent **Session Recording Action Bar / Card**:
  - When Idle:
    - Glowing cyan **[ ▶ Start Recording Session ]** button.
    - Description: *"Zero background drain. Tap to begin benchmark profiling for your active game."*
  - When Recording:
    - Pulsing red dot + **[ ⏹ Stop & Save Session ]** button.
    - Live recording elapsed timer (e.g. `Recording: 04m 12s`).
    - Live target game name badge.
- Updates cards in real-time while recording, or displays the saved session immediately upon stopping.

### 4. Floating Overlay In-Game REC Button (`OverlayService.java` & `layout_overlay_hud.xml`)
- Add a quick `[● REC]` / `[■ STOP]` control inside the expanded panel (and/or an optional pill icon) so the user can begin or end a benchmark recording in the middle of a game without switching out of the game.

### 5. Ongoing Recording Notification
- When recording is active, show an ongoing notification with elapsed time and a **"Stop & Save"** action button so the user can easily end recording from anywhere.

---

## Verification Plan
1. **Zero-Resource Idle Verification**:
   - Launch app -> minimize app without overlay/recording.
   - Run `adb shell dumpsys activity services com.gamestate.monitor` and `adb shell ps -A | grep gamestate` to confirm service shuts down completely with 0 background execution.
2. **On-Demand Recording Verification**:
   - Open overlay -> verify overlay displays live FPS/hardware stats but does **NOT** record a session.
   - Tap **"Start Recording"** (either from Statistics tab or inside overlay HUD) -> verify recording starts, timer ticks, metrics collect.
   - Play game -> tap **"Stop & Save"**.
   - Verify session report appears instantly with real metrics (stability %, 1% lows, thermals, battery impact).
