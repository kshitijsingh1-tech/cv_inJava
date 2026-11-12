package com.visionforge.mememotion.detection;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;

/**
 * Fast Face Detection and Smooth Tracking using OpenCV Haar Cascade.
 */
public class FaceDetector {

    private final CascadeClassifier classifier;
    private final Mat grayFrame = new Mat();
    private final Mat smallGrayFrame = new Mat();

    // Smoothed bounding box
    private Rect smoothedBox = null;
    private static final double SMOOTHING_FACTOR = 0.35; // Exponential moving average weight

    public FaceDetector(String cascadeXmlPath) {
        File file = new File(cascadeXmlPath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Haar cascade file not found: " + file.getAbsolutePath());
        }
        this.classifier = new CascadeClassifier(file.getAbsolutePath());
        if (this.classifier.empty()) {
            throw new IllegalStateException("Failed to load Haar Cascade from: " + file.getAbsolutePath());
        }
    }

    /**
     * Detects the primary face in the frame and returns a smoothed bounding box.
     *
     * @param frame BGR camera frame
     * @return Rect representing detected & smoothed face box, or null if no face detected
     */
    public synchronized Rect detectFace(Mat frame) {
        if (frame == null || frame.empty() || classifier.empty()) {
            return null;
        }

        // Convert to grayscale
        Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(grayFrame, grayFrame);

        // Downscale for faster detection (scale factor 0.5)
        double scale = 0.5;
        Imgproc.resize(grayFrame, smallGrayFrame, new Size(), scale, scale, Imgproc.INTER_LINEAR);

        MatOfRect faces = new MatOfRect();
        // minSize is 30x30 in small space (60x60 in original space)
        classifier.detectMultiScale(smallGrayFrame, faces, 1.1, 4, 0, new Size(30, 30), new Size());

        Rect[] faceArray = faces.toArray();
        if (faceArray.length == 0) {
            // Keep previous box with decay if momentarily lost, or clear
            return smoothedBox;
        }

        // Find the largest face (closest to camera)
        Rect bestFace = faceArray[0];
        long maxArea = (long) bestFace.width * bestFace.height;
        for (int i = 1; i < faceArray.length; i++) {
            long area = (long) faceArray[i].width * faceArray[i].height;
            if (area > maxArea) {
                maxArea = area;
                bestFace = faceArray[i];
            }
        }

        // Scale back up to original coordinates
        int origX = (int) (bestFace.x / scale);
        int origY = (int) (bestFace.y / scale);
        int origW = (int) (bestFace.width / scale);
        int origH = (int) (bestFace.height / scale);

        Rect rawBox = new Rect(origX, origY, origW, origH);

        // Apply exponential smoothing to prevent jitter
        if (smoothedBox == null) {
            smoothedBox = rawBox;
        } else {
            int smoothX = (int) (smoothedBox.x * (1 - SMOOTHING_FACTOR) + rawBox.x * SMOOTHING_FACTOR);
            int smoothY = (int) (smoothedBox.y * (1 - SMOOTHING_FACTOR) + rawBox.y * SMOOTHING_FACTOR);
            int smoothW = (int) (smoothedBox.width * (1 - SMOOTHING_FACTOR) + rawBox.width * SMOOTHING_FACTOR);
            int smoothH = (int) (smoothedBox.height * (1 - SMOOTHING_FACTOR) + rawBox.height * SMOOTHING_FACTOR);
            smoothedBox = new Rect(smoothX, smoothY, smoothW, smoothH);
        }

        return smoothedBox;
    }

    public void release() {
        grayFrame.release();
        smallGrayFrame.release();
    }
}
