package de.fastbuilder.model;

import java.util.UUID;

/**
 * Speichert alle spielerspezifischen Statistiken und den aktuellen Spielstatus.
 * Wird pro Spieler einmal erstellt und im GameManager verwaltet.
 */
public class PlayerData {

    private final UUID playerUuid;

    // ── Statistiken ──────────────────────────────────────────────
    /** Gesamtanzahl der Versuche (gezählt wenn erster Block gesetzt + Void oder Erfolg) */
    private int totalAttempts = 0;
    /** Anzahl erfolgreicher Versuche (Druckplatte betreten) */
    private int successfulAttempts = 0;
    /** Beste Zeit in Millisekunden (Long.MAX_VALUE = noch keine Zeit) */
    private long bestTimeMillis = Long.MAX_VALUE;

    // ── Timer / Aktiver Versuch ──────────────────────────────────
    /** System.currentTimeMillis() beim Platzieren des ersten Blocks */
    private long timerStartMillis = -1;
    /** Ob der Timer gerade läuft */
    private boolean timerRunning = false;
    /** Ob der aktuelle Versuch bereits begonnen hat (erster Block gesetzt) */
    private boolean attemptStarted = false;

    public PlayerData(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    // ── Timer-Methoden ───────────────────────────────────────────

    /** Startet den Timer (beim Platzieren des ersten Blocks) */
    public void startTimer() {
        timerStartMillis = System.currentTimeMillis();
        timerRunning     = true;
        attemptStarted   = true;
    }

    /** Stoppt den Timer und gibt die Elapsed-Zeit in ms zurück */
    public long stopTimer() {
        if (!timerRunning) return 0;
        long elapsed = System.currentTimeMillis() - timerStartMillis;
        timerRunning = false;
        return elapsed;
    }

    /** Gibt die aktuelle verstrichene Zeit zurück (auch wenn Timer noch läuft) */
    public long getElapsedMillis() {
        if (!timerRunning) return 0;
        return System.currentTimeMillis() - timerStartMillis;
    }

    /** Setzt den Timer-Zustand zurück (für neuen Versuch) */
    public void resetTimer() {
        timerStartMillis = -1;
        timerRunning     = false;
        attemptStarted   = false;
    }

    // ── Versuch-Methoden ─────────────────────────────────────────

    /** Zählt einen abgeschlossenen Versuch (Void-Fall oder Druckplatte) */
    public void incrementAttempts() {
        totalAttempts++;
    }

    /** Zählt einen erfolgreichen Versuch und aktualisiert ggf. die Bestzeit */
    public void recordSuccess(long timeMillis) {
        successfulAttempts++;
        if (timeMillis < bestTimeMillis) {
            bestTimeMillis = timeMillis;
        }
    }

    // ── Getter / Setter ──────────────────────────────────────────

    public UUID getPlayerUuid()         { return playerUuid; }
    public int  getTotalAttempts()      { return totalAttempts; }
    public int  getSuccessfulAttempts() { return successfulAttempts; }

    public boolean hasBestTime()        { return bestTimeMillis != Long.MAX_VALUE; }
    public long    getBestTimeMillis()  { return bestTimeMillis; }

    public boolean isTimerRunning()   { return timerRunning; }
    public boolean isAttemptStarted() { return attemptStarted; }

    /**
     * Formatiert die beste Zeit als lesbaren String (z.B. "1.43s")
     * Gibt "--" zurück wenn noch keine Zeit vorhanden.
     */
    public String getFormattedBestTime() {
        if (!hasBestTime()) return "--";
        return String.format("%.2fs", bestTimeMillis / 1000.0);
    }

    /**
     * Formatiert die aktuelle Laufzeit des Timers (z.B. "1.43s")
     */
    public String getFormattedCurrentTime() {
        long elapsed = getElapsedMillis();
        return String.format("%.2fs", elapsed / 1000.0);
    }
}
