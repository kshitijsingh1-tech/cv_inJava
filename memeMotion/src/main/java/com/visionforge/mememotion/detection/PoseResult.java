package com.visionforge.mememotion.detection;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Encapsulates the 17 detected keypoints for a frame,
 * providing skeleton visualization and bounding box computations for face and body.
 */
public class PoseResult {

    private final Keypoint[] keypoints;
    private final float minConfidenceThreshold;

    // Bone connections (pairs of keypoint indices)
    private static final int[][] SKELETON_EDGES = {
        {0, 1}, {0, 2}, {1, 3}, {2, 4},           // Facial connections
        {5, 6},                                   // Shoulders
        {5, 7}, {7, 9},                           // Left arm
        {6, 8}, {8, 10},                          // Right arm
        {5, 11}, {6, 12},                         // Torso sides
        {11, 12},                                 // Hips
        {11, 13}, {13, 15},                       // Left leg
        {12, 14}, {14, 16}                        // Right leg
    };

    public PoseResult(Keypoint[] keypoints, float minConfidenceThreshold) {
        this.keypoints = keypoints;
        this.minConfidenceThreshold = minConfidenceThreshold;
    }

    public Keypoint[] getKeypoints() {
        return keypoints;
    }

    public Keypoint get(int index) {
        if (index >= 0 && index < keypoints.length) {
            return keypoints[index];
        }
        return null;
    }

    public boolean isConfident(int index) {
        Keypoint kp = get(index);
        return kp != null && kp.isConfident(minConfidenceThreshold);
    }

    /**
     * Computes a bounding box around the detected face based on nose, eyes, and ears.
     * Returns null if face landmarks are not confident.
     */
    public Rect getFaceBoundingBox(int frameWidth, int frameHeight) {
        if (!isConfident(0)) return null; // Nose is crucial

        float minX = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = Float.MIN_VALUE;
        int count = 0;

        for (int i = 0; i <= 4; i++) {
            if (isConfident(i)) {
                Keypoint kp = keypoints[i];
                minX = Math.min(minX, kp.getX());
                maxX = Math.max(maxX, kp.getX());
                minY = Math.min(minY, kp.getY());
                maxY = Math.max(maxY, kp.getY());
                count++;
            }
        }

        if (count < 2) {
            // If only nose is confident, estimate face size using shoulder distance
            if (isConfident(5) && isConfident(6)) {
                float shoulderDist = Math.abs(keypoints[5].getX() - keypoints[6].getX());
                float size = shoulderDist * 0.5f;
                Keypoint nose = keypoints[0];
                int x = Math.max(0, (int) (nose.getX() - size / 2));
                int y = Math.max(0, (int) (nose.getY() - size / 2));
                int w = Math.min(frameWidth - x, (int) size);
                int h = Math.min(frameHeight - y, (int) size);
                return new Rect(x, y, w, h);
            }
            return null;
        }

        // Add generous margin for forehead, chin, and hair
        float width = maxX - minX;
        float height = maxY - minY;
        float padX = Math.max(width * 0.6f, 30f);
        float padY = Math.max(height * 0.8f, 40f);

        int x = (int) Math.max(0, minX - padX);
        int y = (int) Math.max(0, minY - padY);
        int w = (int) Math.min(frameWidth - x, (maxX - minX) + 2 * padX);
        int h = (int) Math.min(frameHeight - y, (maxY - minY) + 2 * padY);

        return new Rect(x, y, w, h);
    }

    /**
     * Computes a torso bounding box for overlaying memes on the chest/torso,
     * leaving the user's arms free.
     */
    public Rect getTorsoBoundingBox(int frameWidth, int frameHeight) {
        if (!isConfident(5) || !isConfident(6)) {
            return null; // Shoulders must be detected
        }

        Keypoint leftShoulder = keypoints[5];
        Keypoint rightShoulder = keypoints[6];

        float shoulderWidth = Math.abs(leftShoulder.getX() - rightShoulder.getX());
        float shoulderMidX = (leftShoulder.getX() + rightShoulder.getX()) / 2.0f;
        float shoulderMidY = (leftShoulder.getY() + rightShoulder.getY()) / 2.0f;

        // If hips are detected, use hip Y; otherwise extrapolate torso height
        float hipMidY;
        if (isConfident(11) && isConfident(12)) {
            hipMidY = (keypoints[11].getY() + keypoints[12].getY()) / 2.0f;
        } else {
            hipMidY = shoulderMidY + shoulderWidth * 1.3f;
        }

        float torsoHeight = Math.max(shoulderWidth * 1.1f, hipMidY - shoulderMidY);
        float boxWidth = shoulderWidth * 1.25f;

        int x = (int) Math.max(0, shoulderMidX - boxWidth / 2.0f);
        int y = (int) Math.max(0, shoulderMidY - torsoHeight * 0.15f);
        int w = (int) Math.min(frameWidth - x, boxWidth);
        int h = (int) Math.min(frameHeight - y, torsoHeight * 1.1f);

        if (w <= 10 || h <= 10) return null;
        return new Rect(x, y, w, h);
    }

    /**
     * Visualizes the skeleton (joints and bones) on the target frame.
     */
    public void drawSkeleton(Mat frame) {
        Scalar jointColor = new Scalar(0, 255, 255); // Yellow dots
        Scalar boneColor = new Scalar(0, 255, 0);    // Green lines

        // Draw bones
        for (int[] edge : SKELETON_EDGES) {
            int u = edge[0];
            int v = edge[1];
            if (isConfident(u) && isConfident(v)) {
                Keypoint kp1 = keypoints[u];
                Keypoint kp2 = keypoints[v];
                Imgproc.line(frame,
                        new Point(kp1.getX(), kp1.getY()),
                        new Point(kp2.getX(), kp2.getY()),
                        boneColor, 2);
            }
        }

        // Draw joints
        for (Keypoint kp : keypoints) {
            if (kp.isConfident(minConfidenceThreshold)) {
                Imgproc.circle(frame,
                        new Point(kp.getX(), kp.getY()),
                        4, jointColor, -1);
            }
        }
    }
}
