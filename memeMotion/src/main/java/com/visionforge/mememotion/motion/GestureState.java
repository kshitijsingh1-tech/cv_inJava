package com.visionforge.mememotion.motion;

/**
 * Manages temporal smoothing, debouncing, and display persistence for gestures.
 * Prevents rapid flickering and provides a smooth, satisfying user experience.
 */
public class GestureState {

    private static final int REQUIRED_CONSECUTIVE_FRAMES = 2; // Debounce count (snappy 60ms response)
    private static final long MIN_DISPLAY_DURATION_MS = 1800; // Keep meme visible for at least 1.8s

    private GestureType candidateGesture = GestureType.NONE;
    private int consecutiveFrames = 0;

    private GestureType activeGesture = GestureType.NONE;
    private long activeGestureStartTime = 0;

    /**
     * Updates the state with the raw gesture detected in the current frame.
     *
     * @param detectedRaw The instantaneous gesture from GestureClassifier
     * @return The smoothed, stable GestureType to display
     */
    public synchronized GestureType update(GestureType detectedRaw) {
        long now = System.currentTimeMillis();

        // 1. Debounce new gesture candidates
        if (detectedRaw == candidateGesture) {
            consecutiveFrames++;
        } else {
            candidateGesture = detectedRaw;
            consecutiveFrames = 1;
        }

        // 2. Trigger new active gesture if candidate is sustained
        if (consecutiveFrames >= REQUIRED_CONSECUTIVE_FRAMES && candidateGesture != GestureType.NONE) {
            if (candidateGesture != activeGesture) {
                activeGesture = candidateGesture;
                activeGestureStartTime = now;
            } else {
                // Refresh active timer while gesture continues
                activeGestureStartTime = now;
            }
        }

        // 3. Check if active gesture has expired
        if (activeGesture != GestureType.NONE) {
            long elapsed = now - activeGestureStartTime;
            if (elapsed > MIN_DISPLAY_DURATION_MS && candidateGesture == GestureType.NONE) {
                activeGesture = GestureType.NONE;
            }
        }

        return activeGesture;
    }

    public synchronized GestureType getActiveGesture() {
        return activeGesture;
    }

    public synchronized void reset() {
        candidateGesture = GestureType.NONE;
        consecutiveFrames = 0;
        activeGesture = GestureType.NONE;
        activeGestureStartTime = 0;
    }
}
