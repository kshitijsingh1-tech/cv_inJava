package com.visionforge.mememotion.detection;

/**
 * Represents a single MoveNet body keypoint landmark.
 */
public class Keypoint {

    public static final String[] NAMES = {
        "nose",           // 0
        "left_eye",       // 1
        "right_eye",      // 2
        "left_ear",       // 3
        "right_ear",      // 4
        "left_shoulder",  // 5
        "right_shoulder", // 6
        "left_elbow",     // 7
        "right_elbow",    // 8
        "left_wrist",     // 9
        "right_wrist",    // 10
        "left_hip",       // 11
        "right_hip",      // 12
        "left_knee",      // 13
        "right_knee",     // 14
        "left_ankle",     // 15
        "right_ankle"     // 16
    };

    private final int index;
    private final String name;
    private final float x;          // Pixel X in original frame
    private final float y;          // Pixel Y in original frame
    private final float normalizedX; // 0.0 - 1.0
    private final float normalizedY; // 0.0 - 1.0
    private final float confidence; // 0.0 - 1.0

    public Keypoint(int index, float normalizedY, float normalizedX, float confidence, int frameWidth, int frameHeight) {
        this.index = index;
        this.name = (index >= 0 && index < NAMES.length) ? NAMES[index] : "unknown_" + index;
        this.normalizedX = normalizedX;
        this.normalizedY = normalizedY;
        this.x = normalizedX * frameWidth;
        this.y = normalizedY * frameHeight;
        this.confidence = confidence;
    }

    public int getIndex() { return index; }
    public String getName() { return name; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getNormalizedX() { return normalizedX; }
    public float getNormalizedY() { return normalizedY; }
    public float getConfidence() { return confidence; }

    public boolean isConfident(float threshold) {
        return confidence >= threshold;
    }

    @Override
    public String toString() {
        return String.format("%s (%.1f, %.1f) conf: %.2f", name, x, y, confidence);
    }
}
