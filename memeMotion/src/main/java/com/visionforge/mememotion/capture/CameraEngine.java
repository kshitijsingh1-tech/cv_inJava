package com.visionforge.mememotion.capture;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

/**
 * Camera Engine — Handles webcam capture with FPS tracking.
 *
 * Encapsulates OpenCV VideoCapture with:
 * - Configurable resolution
 * - Frame capture with null-safety
 * - Real-time FPS measurement
 * - Proper native resource cleanup
 */
public class CameraEngine implements AutoCloseable {

    private final VideoCapture camera;
    private final Mat frame;
    private final int requestedWidth;
    private final int requestedHeight;

    // FPS tracking
    private long frameCount;
    private long fpsStartTime;
    private double currentFps;
    private static final long FPS_UPDATE_INTERVAL_NS = 500_000_000L; // Update FPS every 500ms

    /**
     * Opens the system camera at the specified resolution.
     *
     * @param cameraIndex Camera device index (0 = default)
     * @param width       Requested frame width
     * @param height      Requested frame height
     */
    public CameraEngine(int cameraIndex, int width, int height) {
        this.camera = new VideoCapture(cameraIndex);
        this.frame = new Mat();
        this.requestedWidth = width;
        this.requestedHeight = height;
        this.frameCount = 0;
        this.fpsStartTime = System.nanoTime();
        this.currentFps = 0.0;

        // Request kr rha resolution from the camera driver
        camera.set(Videoio.CAP_PROP_FRAME_WIDTH, width);
        camera.set(Videoio.CAP_PROP_FRAME_HEIGHT, height);
    }

    /**
     * Check kr rha hu if the camera opened successfully.
     */
    public boolean isOpened() {
        return camera.isOpened();
    }

    /**
     * Capture the latest frame from the camera.
     *
     * @return The captured frame, or null if capture failed
     */
    public Mat captureFrame() {
        if (camera.read(frame) && !frame.empty()) {
            frameCount++;
            updateFps();
            return frame;
        }
        return null;
    }

    /**
     * Get the current measured FPS (updated every 500ms).
     */
    public double getFps() {
        return currentFps;
    }

    /**
     * Get the actual frame dimensions from the camera.
     */
    public int getFrameWidth() {
        return (int) camera.get(Videoio.CAP_PROP_FRAME_WIDTH);
    }

    public int getFrameHeight() {
        return (int) camera.get(Videoio.CAP_PROP_FRAME_HEIGHT);
    }

    public int getRequestedWidth() {
        return requestedWidth;
    }

    public int getRequestedHeight() {
        return requestedHeight;
    }

    /**
     * Update FPS calculation using a sliding window.
     */
    private void updateFps() {
        long now = System.nanoTime();
        long elapsed = now - fpsStartTime;

        if (elapsed >= FPS_UPDATE_INTERVAL_NS) {
            currentFps = frameCount / (elapsed / 1_000_000_000.0);
            frameCount = 0;
            fpsStartTime = now;
        }
    }

    @Override
    public void close() {
        if (camera.isOpened()) {
            camera.release();
        }
        frame.release();
    }
}
