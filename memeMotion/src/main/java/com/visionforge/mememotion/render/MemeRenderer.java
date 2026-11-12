package com.visionforge.mememotion.render;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import java.io.File;
import java.util.*;

/**
 * Meme Renderer — Handles loading, scaling, animation, and compositing of memes
 * (both static images and animated video clips) onto the live camera feed.
 *
 * Supports:
 * - Loading static images (JPG/PNG) with alpha channel preservation
 * - Loading animated video clips (MP4/AVI/MOV) decoded into memory for zero-lag playback
 * - Time-synchronized looped animation playback
 * - Aspect-ratio-preserving scaling
 * - Robust boundary clamping (out-of-bounds safety)
 * - 32-bit floating point matrix alpha blending
 */
public class MemeRenderer {

    /**
     * Represents a single loaded meme asset — either a single static frame
     * or a sequence of animated video frames played back at a designated frame rate.
     */
    public static class MemeClip {
        private final List<Mat> frames;
        private final double fps;
        private final long frameDurationMs;

        public MemeClip(Mat staticFrame) {
            this.frames = new ArrayList<>(Collections.singletonList(staticFrame));
            this.fps = 1.0;
            this.frameDurationMs = 1000;
        }

        public MemeClip(List<Mat> frames, double fps) {
            this.frames = frames;
            this.fps = (fps > 0) ? fps : 30.0;
            this.frameDurationMs = Math.max(1, (long) (1000.0 / this.fps));
        }

        public Mat getCurrentFrame() {
            if (frames.isEmpty()) return null;
            if (frames.size() == 1) return frames.get(0);
            long now = System.currentTimeMillis();
            int index = (int) ((now / frameDurationMs) % frames.size());
            return frames.get(index);
        }

        public boolean isAnimated() {
            return frames.size() > 1;
        }

        public int getFrameCount() {
            return frames.size();
        }

        public int getWidth() {
            return frames.isEmpty() ? 0 : frames.get(0).cols();
        }

        public int getHeight() {
            return frames.isEmpty() ? 0 : frames.get(0).rows();
        }

        public int getChannels() {
            return frames.isEmpty() ? 0 : frames.get(0).channels();
        }

        public void release() {
            for (Mat f : frames) {
                if (f != null && !f.empty()) {
                    f.release();
                }
            }
            frames.clear();
        }
    }

    private final Map<String, MemeClip> memeCache = new LinkedHashMap<>();

    /**
     * Load a single meme (image or video) from file.
     *
     * @param name Meme identifier (e.g., "hallelujah")
     * @param path File path to the image or video
     */
    public void loadMeme(String name, String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mov") || lower.endsWith(".mkv")) {
            loadVideoMeme(name, path);
        } else {
            loadImageMeme(name, path);
        }
    }

    private void loadImageMeme(String name, String path) {
        Mat meme = Imgcodecs.imread(path, Imgcodecs.IMREAD_UNCHANGED);
        if (meme != null && !meme.empty()) {
            memeCache.put(name, new MemeClip(meme));
            System.out.println("  ✓ Loaded static meme: " + name
                    + " (" + meme.cols() + "x" + meme.rows()
                    + ", " + meme.channels() + "ch)");
        } else {
            System.err.println("  ✗ Failed to load meme image: " + path);
        }
    }

    private void loadVideoMeme(String name, String path) {
        VideoCapture cap = new VideoCapture(path);
        if (!cap.isOpened()) {
            System.err.println("  ✗ Failed to open video meme: " + path);
            return;
        }

        double fps = cap.get(Videoio.CAP_PROP_FPS);
        if (fps <= 0 || Double.isNaN(fps)) fps = 30.0;

        List<Mat> frames = new ArrayList<>();
        Mat temp = new Mat();
        while (cap.read(temp)) {
            if (!temp.empty()) {
                Mat frameCopy = new Mat();
                temp.copyTo(frameCopy);
                frames.add(frameCopy);
            }
        }
        temp.release();
        cap.release();

        if (!frames.isEmpty()) {
            memeCache.put(name, new MemeClip(frames, fps));
            System.out.println("  ✓ Loaded animated video meme: " + name
                    + " (" + frames.size() + " frames, " + frames.get(0).cols() + "x" + frames.get(0).rows()
                    + " @ " + String.format("%.1f", fps) + " FPS)");
        } else {
            System.err.println("  ✗ Video meme has no decoded frames: " + path);
        }
    }

    /**
     * Load all meme images and videos from a directory.
     * Uses filename (without extension) as the meme name.
     *
     * @param directoryPath Path to the memes directory
     * @return Number of memes loaded
     */
    public int loadMemesFromDirectory(String directoryPath) {
        File dir = new File(directoryPath);
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Meme directory not found: " + directoryPath);
            return 0;
        }

        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg")
                    || lower.endsWith(".png")
                    || lower.endsWith(".mp4")
                    || lower.endsWith(".avi")
                    || lower.endsWith(".mov");
        });

        if (files == null || files.length == 0) {
            System.err.println("No meme assets found in: " + directoryPath);
            return 0;
        }

        // Sort for consistent ordering
        Arrays.sort(files);

        for (File file : files) {
            String name = file.getName();
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex > 0) {
                name = name.substring(0, dotIndex);
            }
            loadMeme(name, file.getAbsolutePath());
        }

        return memeCache.size();
    }

    /**
     * Overlay a meme (static or active video frame) onto a camera frame at the specified region.
     *
     * The meme is scaled to fit within the region while preserving
     * its aspect ratio, and centered within the region.
     *
     * @param frame     Camera frame to draw on (modified in-place)
     * @param memeName  Name of the meme to overlay
     * @param region    Target region for the meme overlay
     */
    public void overlayMeme(Mat frame, String memeName, Rect region) {
        MemeClip clip = memeCache.get(memeName);
        if (clip == null || frame.empty()) return;

        Mat meme = clip.getCurrentFrame();
        if (meme == null || meme.empty()) return;

        // Calculate scale preserving aspect ratio
        double scaleX = (double) region.width / meme.cols();
        double scaleY = (double) region.height / meme.rows();
        double scale = Math.min(scaleX, scaleY);

        int newW = (int) (meme.cols() * scale);
        int newH = (int) (meme.rows() * scale);

        if (newW <= 0 || newH <= 0) return;

        // Center within target region
        int offsetX = region.x + (region.width - newW) / 2;
        int offsetY = region.y + (region.height - newH) / 2;

        // Scale meme to target size
        Mat resized = new Mat();
        Imgproc.resize(meme, resized, new Size(newW, newH), 0, 0, Imgproc.INTER_LINEAR);

        // Clamp to frame bounds
        int x1 = Math.max(0, offsetX);
        int y1 = Math.max(0, offsetY);
        int x2 = Math.min(frame.cols(), offsetX + newW);
        int y2 = Math.min(frame.rows(), offsetY + newH);

        if (x2 <= x1 || y2 <= y1) {
            resized.release();
            return;
        }

        // Calculate corresponding source region
        int srcX1 = x1 - offsetX;
        int srcY1 = y1 - offsetY;

        Mat frameRoi = frame.submat(y1, y2, x1, x2);
        Mat memeRoi = resized.submat(srcY1, srcY1 + (y2 - y1), srcX1, srcX1 + (x2 - x1));

        if (resized.channels() == 4) {
            // Has alpha channel — perform alpha compositing
            blendWithAlpha(memeRoi, frameRoi);
        } else {
            // No alpha — convert to BGR if needed and copy
            Mat bgr = new Mat();
            if (memeRoi.channels() == 1) {
                Imgproc.cvtColor(memeRoi, bgr, Imgproc.COLOR_GRAY2BGR);
            } else {
                memeRoi.copyTo(bgr);
            }
            bgr.copyTo(frameRoi);
            bgr.release();
        }

        resized.release();
    }

    /**
     * Alpha-blend a BGRA foreground onto a BGR background.
     *
     * Uses matrix operations for performance:
     *   result = foreground * alpha + background * (1 - alpha)
     *
     * @param foreground BGRA source (meme with transparency)
     * @param background BGR destination (camera frame ROI, modified in-place)
     */
    private void blendWithAlpha(Mat foreground, Mat background) {
        // Split BGRA into individual channels
        List<Mat> fgChannels = new ArrayList<>();
        Core.split(foreground, fgChannels);

        // Create normalized alpha as float [0.0, 1.0]
        Mat alpha1 = new Mat();
        fgChannels.get(3).convertTo(alpha1, CvType.CV_32FC1, 1.0 / 255.0);

        // Create 3-channel alpha for element-wise multiply
        Mat alpha3 = new Mat();
        Core.merge(Arrays.asList(alpha1, alpha1, alpha1), alpha3);

        // Create inverse alpha (1 - alpha)
        Mat invAlpha3 = new Mat(alpha3.size(), alpha3.type(), Scalar.all(1.0));
        Core.subtract(invAlpha3, alpha3, invAlpha3);

        // Convert foreground BGR channels to float
        Mat fgBgr = new Mat();
        Core.merge(fgChannels.subList(0, 3), fgBgr);
        Mat fgFloat = new Mat();
        fgBgr.convertTo(fgFloat, CvType.CV_32FC3);

        // Convert background to float
        Mat bgFloat = new Mat();
        background.convertTo(bgFloat, CvType.CV_32FC3);

        // Blend: result = fg * alpha + bg * (1 - alpha)
        Core.multiply(fgFloat, alpha3, fgFloat);
        Core.multiply(bgFloat, invAlpha3, bgFloat);
        Core.add(fgFloat, bgFloat, bgFloat);

        // Convert back to uint8 and write to background
        bgFloat.convertTo(background, CvType.CV_8UC3);

        // Release all temporary Mats
        for (Mat ch : fgChannels) ch.release();
        alpha1.release();
        alpha3.release();
        invAlpha3.release();
        fgBgr.release();
        fgFloat.release();
        bgFloat.release();
    }

    /**
     * Get all loaded meme names in order.
     */
    public String[] getMemeNames() {
        return memeCache.keySet().toArray(new String[0]);
    }

    /**
     * Get the number of loaded memes.
     */
    public int getMemeCount() {
        return memeCache.size();
    }

    /**
     * Check if a specific meme is an animated clip.
     */
    public boolean isMemeAnimated(String memeName) {
        MemeClip clip = memeCache.get(memeName);
        return clip != null && clip.isAnimated();
    }

    /**
     * Release all loaded meme images and video buffers.
     */
    public void release() {
        for (MemeClip clip : memeCache.values()) {
            clip.release();
        }
        memeCache.clear();
    }
}
