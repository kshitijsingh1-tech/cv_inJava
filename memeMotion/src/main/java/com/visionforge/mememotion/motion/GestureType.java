package com.visionforge.mememotion.motion;

/**
 * Gesture types that MemeMotion can recognize.
 *
 * Each gesture maps to a specific meme in the meme library.
 * In M1 these are manually cycled; in M4+ they will be
 * automatically detected from pose landmarks.
 */
public enum GestureType {

    /** No gesture detected — show live camera only. */
    NONE("none", "No gesture"),

    /** Both wrists above shoulders — double celebration. */
    HANDS_RAISED("hands_raised", "Both Hands Raised"),

    /** Single wrist above shoulder — one hand up. */
    ONE_HAND_RAISED("hallelujah", "One Hand Up"),

    /** Both wrists near head — distress / panic. */
    HEAD_HOLDING("im_cooked", "Head Holding"),

    /** Arms wide, wrists far apart — confusion / shrug. */
    DRAMATIC_SHRUG("dramatic_shrug", "Dramatic Shrug"),

    /** Head below shoulder midpoint — looking down. */
    LOOKING_DOWN("depression_mode", "Looking Down"),

    /** Arms perfectly horizontal — T-pose dominance. */
    T_POSE("t_pose", "T-Pose"),

    /** Gojo pose — hand extended forward / in front of chest / chin. */
    HONORED_ONE("honored_one", "The Honored One (Gojo)"),

    /** Both hands covering face / eyes in despair / facepalm. */
    FACE_COVERED("face_covered", "Face Covered / Facepalm");

    private final String memeName;
    private final String displayName;

    GestureType(String memeName, String displayName) {
        this.memeName = memeName;
        this.displayName = displayName;
    }

    /**
     * Get the meme asset name associated with this gesture.
     */
    public String getMemeName() {
        return memeName;
    }

    /**
     * Get a human-readable name for this gesture.
     */
    public String getDisplayName() {
        return displayName;
    }
}
