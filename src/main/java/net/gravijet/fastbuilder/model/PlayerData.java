package net.gravijet.fastbuilder.model;

import java.util.UUID;

/**
 * Per-session (in-memory) state for a player.
 * Persistent data lives in {@link PlayerStats}.
 */
public class PlayerData {

    private final UUID uuid;

    private BridgeDistance selectedDistance;
    private BridgeMaterial selectedMaterial;

    private long    timerStartMillis = -1;
    private boolean timerRunning     = false;
    private boolean attemptStarted   = false;

    public PlayerData(UUID uuid, BridgeDistance defaultDistance, BridgeMaterial defaultMaterial) {
        this.uuid             = uuid;
        this.selectedDistance = defaultDistance;
        this.selectedMaterial = defaultMaterial;
    }

    // ── Timer ─────────────────────────────────────────────────────

    public void startTimer() {
        timerStartMillis = System.currentTimeMillis();
        timerRunning     = true;
        attemptStarted   = true;
    }

    /** Stops the timer and returns elapsed milliseconds. */
    public long stopTimer() {
        if (!timerRunning) return 0;
        long elapsed = System.currentTimeMillis() - timerStartMillis;
        timerRunning = false;
        return elapsed;
    }

    public void resetTimer() {
        timerStartMillis = -1;
        timerRunning     = false;
        attemptStarted   = false;
    }

    public long getElapsedMillis() {
        if (!timerRunning || timerStartMillis == -1) return 0;
        return System.currentTimeMillis() - timerStartMillis;
    }

    public String getFormattedElapsed() {
        return String.format("%.2fs", getElapsedMillis() / 1000.0);
    }

    // ── Getters / Setters ─────────────────────────────────────────

    public UUID           getUuid()             { return uuid;             }
    public BridgeDistance getSelectedDistance() { return selectedDistance; }
    public BridgeMaterial getSelectedMaterial() { return selectedMaterial; }
    public boolean        isTimerRunning()      { return timerRunning;     }
    public boolean        isAttemptStarted()    { return attemptStarted;   }

    public void setSelectedDistance(BridgeDistance d) { this.selectedDistance = d; }
    public void setSelectedMaterial(BridgeMaterial m) { this.selectedMaterial = m; }
}
