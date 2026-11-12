package com.visionforge.mememotion;

import nu.pattern.OpenCV;

import com.visionforge.mememotion.capture.CameraEngine;
import com.visionforge.mememotion.detection.PoseEstimator;
import com.visionforge.mememotion.detection.PoseResult;
import com.visionforge.mememotion.motion.GestureClassifier;
import com.visionforge.mememotion.motion.GestureState;
import com.visionforge.mememotion.motion.GestureType;
import com.visionforge.mememotion.render.MemeRenderer;

import org.opencv.core.*;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;


/**
 * MemeMotion v0.2 — AI-Powered Real-Time Meme Webcam
 *
 * Architecture:
 * Camera → MoveNet ONNX (17 Keypoints) → Gesture Classifier → Gesture State
 * (Smoothing) → Dynamic Overlay → Display
 *
 * Features:
 * - Real-time MoveNet SinglePose Lightning pose estimation
 * - Automatic gesture recognition:
 * • Hands Raised → Hallelujah
 * • Head Holding → I'm Cooked
 * • Dramatic Shrug → I Don't Know
 * • Looking Down → Depression Mode
 * • T-Pose → Assert Dominance
 * - Dynamic Body / Torso & Face Anchoring (follows you in 2D space)
 * - Live Skeleton Visualization (toggleable with [S])
 * - Manual / AI auto-switch modes (toggleable with [M])
 * - Full FPS and Inference Latency HUD
 */
public class MemeMotionApp {

    private static final int CAMERA_WIDTH = 1280;
    private static final int CAMERA_HEIGHT = 720;
    private static final String WINDOW_TITLE = "MemeMotion v0.2 \u2014 AI Meme Webcam";
    private static final String MEMES_DIR = "assets/memes";
    private static final String MODEL_PATH = "assets/models/movenet_lightning.onnx";

    public enum AnchorMode {
        TORSO("Torso"),
        FACE("Face"),
        CENTER("Center Screen");

        private final String label;

        AnchorMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    // ── Application state ─────────────────────────────────
    private static String[] memeNames;
    private static int manualMemeIndex = 0;
    private static boolean autoGestureMode = true;
    private static boolean showMeme = true;
    private static boolean showSkeleton = true;
    private static AnchorMode anchorMode = AnchorMode.TORSO;
    private static double memeScale = 0.55;
    private static volatile boolean exitRequested = false;

    // ── HUD colors ────────────────────────────────────────
    private static final Scalar COLOR_GREEN = new Scalar(0, 255, 0);
    private static final Scalar COLOR_CYAN = new Scalar(255, 255, 0);
    private static final Scalar COLOR_YELLOW = new Scalar(0, 255, 255);
    private static final Scalar COLOR_RED = new Scalar(0, 0, 255);
    private static final Scalar COLOR_WHITE = new Scalar(255, 255, 255);
    private static final Scalar COLOR_GRAY = new Scalar(160, 160, 160);
    private static final Scalar COLOR_HUD_BG = new Scalar(20, 20, 20);

    public static void main(String[] args) {

        // 1. Initialize OpenCV
        OpenCV.loadLocally();

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║          MemeMotion v0.2                         ║");
        System.out.println("║    AI Real-Time Motion-Reactive Meme Webcam      ║");
        System.out.println("║               VisionForge                        ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        // 2. Initialize Camera Engine
        System.out.println("[Camera] Opening webcam (ID 0)...");
        CameraEngine camera = new CameraEngine(0, CAMERA_WIDTH, CAMERA_HEIGHT);

        if (!camera.isOpened()) {
            System.err.println("[Camera] ERROR: Could not open webcam!");
            System.err.println("         Make sure no other app (e.g. Zoom/Teams/Browser) is using the camera.");
            camera.close();
            return;
        }

        System.out.println("[Camera] Webcam opened: " + camera.getFrameWidth() + "x" + camera.getFrameHeight());

        // 3. Initialize MoveNet Pose Estimator
        System.out.println("[AI] Initializing MoveNet Lightning pose estimator...");
        PoseEstimator poseEstimator = null;
        try {
            poseEstimator = new PoseEstimator(MODEL_PATH);
            System.out.println("[AI] MoveNet Lightning loaded successfully from: " + MODEL_PATH);
        } catch (Exception e) {
            System.err.println("[AI] WARNING: Could not load MoveNet model: " + e.getMessage());
            System.err.println("     Running in manual overlay mode.");
        }

        // 4. Initialize Meme Renderer
        System.out.println("[Memes] Loading meme library...");
        MemeRenderer renderer = new MemeRenderer();
        int loaded = renderer.loadMemesFromDirectory(MEMES_DIR);
        System.out.println("[Memes] Loaded " + loaded + " memes.");
        memeNames = renderer.getMemeNames();

        // 5. Initialize Gesture State
        GestureState gestureState = new GestureState();

        // 6. Print controls
        printControls();

        // Register global key event dispatcher so ESC or Q exits instantly from any window focus
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() == KeyEvent.KEY_PRESSED) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_Q) {
                        System.out.println("[Input] Exit signal received via keyboard (ESC/Q). Exiting...");
                        exitRequested = true;
                        return true;
                    }
                }
                return false;
            }
        });

        // ── Main pipeline loop ────────────────────────────
        Mat frame;
        long frameNumber = 0;
        long lastInferenceTimeMs = 0;

        try {
            while (!exitRequested && HighGui.n_closed_windows == 0) {

                // A. Capture camera frame
                frame = camera.captureFrame();
                if (frame == null)
                    continue;
                frameNumber++;

                PoseResult pose = null;
                GestureType activeGesture = GestureType.NONE;

                // B. Run Pose Estimation (MoveNet ONNX)
                if (poseEstimator != null) {
                    long t0 = System.currentTimeMillis();
                    pose = poseEstimator.estimate(frame);
                    lastInferenceTimeMs = System.currentTimeMillis() - t0;

                    // C. Motion Intelligence: Gesture Recognition
                    if (pose != null) {
                        GestureType rawGesture = GestureClassifier.classify(pose);
                        activeGesture = gestureState.update(rawGesture);
                    }
                }

                // D. Draw Skeleton if enabled
                if (showSkeleton && pose != null) {
                    pose.drawSkeleton(frame);
                }

                // E. Meme Overlay Selection & Anchor Calculation
                String targetMeme = null;
                if (autoGestureMode) {
                    if (activeGesture != GestureType.NONE) {
                        targetMeme = activeGesture.getMemeName();
                    }
                } else if (showMeme && memeNames.length > 0) {
                    targetMeme = memeNames[manualMemeIndex % memeNames.length];
                }

                if (targetMeme != null && showMeme) {
                    Rect targetRegion = computeMemeRegion(frame, pose, anchorMode, memeScale);
                    if (targetRegion != null) {
                        renderer.overlayMeme(frame, targetMeme, targetRegion);
                    }
                }

                // F. Draw HUD
                drawHud(frame, camera.getFps(), lastInferenceTimeMs, activeGesture, pose != null);

                // G. Display
                HighGui.imshow(WINDOW_TITLE, frame);

                // H. Keyboard input
                int rawKey = HighGui.waitKey(1);
                if (rawKey != -1 && handleInput(rawKey & 0xFF, gestureState))
                    break;
                if (exitRequested || HighGui.n_closed_windows > 0)
                    break;
            }

        } finally {
            camera.close();
            renderer.release();
            if (poseEstimator != null) {
                poseEstimator.close();
            }
            HighGui.destroyAllWindows();
            System.out.println();
            System.out.println("[MemeMotion] Stopped. Total frames processed: " + frameNumber);
            System.exit(0);
        }
    }

    /**
     * Computes the bounding rect for the meme overlay based on chosen anchor mode.
     */
    private static Rect computeMemeRegion(Mat frame, PoseResult pose, AnchorMode mode, double scale) {
        int fw = frame.cols();
        int fh = frame.rows();

        if (pose != null) {
            if (mode == AnchorMode.TORSO) {
                Rect torso = pose.getTorsoBoundingBox(fw, fh);
                if (torso != null)
                    return torso;
            } else if (mode == AnchorMode.FACE) {
                Rect face = pose.getFaceBoundingBox(fw, fh);
                if (face != null) {
                    // Place meme slightly above or on face
                    int size = (int) (face.width * 1.5);
                    int x = Math.max(0, face.x + (face.width - size) / 2);
                    int y = Math.max(0, face.y - size / 3);
                    int w = Math.min(fw - x, size);
                    int h = Math.min(fh - y, size);
                    return new Rect(x, y, w, h);
                }
            }
        }

        // Default or Fallback: Center screen overlay
        int memeH = (int) (fh * scale);
        int memeW = memeH;
        int x = (fw - memeW) / 2;
        int y = (fh - memeH) / 2;
        return new Rect(x, y, memeW, memeH);
    }

    /**
     * Draw modern HUD overlay with FPS, inference latency, detected gesture, and
     * controls.
     */
    private static void drawHud(Mat frame, double fps, long inferMs, GestureType gesture, boolean poseTracked) {
        int margin = 16;
        int hudW = 340;
        int hudH = 135;

        // Background panel with alpha simulated by semi-dark box
        Imgproc.rectangle(frame, new Point(margin, margin), new Point(margin + hudW, margin + hudH), COLOR_HUD_BG,
                Imgproc.FILLED);
        Imgproc.rectangle(frame, new Point(margin, margin), new Point(margin + hudW, margin + hudH),
                new Scalar(70, 70, 70), 1);

        int y = margin + 26;

        // 1. Title & Mode
        String modeStr = autoGestureMode ? "AI AUTO-GESTURE" : "MANUAL";
        Scalar modeColor = autoGestureMode ? COLOR_CYAN : COLOR_YELLOW;
        Imgproc.putText(frame, "MemeMotion | " + modeStr, new Point(margin + 12, y),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.55, modeColor, 2);

        // 2. FPS & Inference Latency
        y += 24;
        Scalar fpsColor = fps >= 25 ? COLOR_GREEN : (fps >= 15 ? COLOR_YELLOW : COLOR_RED);
        String perfText = String.format("FPS: %.1f  |  AI: %dms", fps, inferMs);
        Imgproc.putText(frame, perfText, new Point(margin + 12, y),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.48, fpsColor, 1);

        // 3. Pose Tracking Status
        y += 24;
        String trackText = poseTracked ? "Tracking: POSE DETECTED (17 pts)" : "Tracking: SEARCHING...";
        Scalar trackColor = poseTracked ? COLOR_GREEN : COLOR_GRAY;
        Imgproc.putText(frame, trackText, new Point(margin + 12, y),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.45, trackColor, 1);

        // 4. Detected Gesture / Meme Trigger
        y += 28;
        if (gesture != GestureType.NONE) {
            String gestureBadge = "🔥 REACTION: " + gesture.getDisplayName().toUpperCase();
            // Draw highlight box behind active reaction
            Imgproc.rectangle(frame, new Point(margin + 8, y - 18), new Point(margin + hudW - 8, y + 6),
                    new Scalar(0, 80, 200), Imgproc.FILLED);
            Imgproc.putText(frame, gestureBadge, new Point(margin + 14, y - 2),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.52, COLOR_WHITE, 2);
        } else {
            String anchorText = "Anchor: " + anchorMode.getLabel() + " | Skele: " + (showSkeleton ? "ON" : "OFF");
            Imgproc.putText(frame, anchorText, new Point(margin + 12, y - 2),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.45, COLOR_GRAY, 1);
        }

        // 5. Bottom hint
        Imgproc.putText(frame, "[M] Mode  [A] Anchor  [S] Skeleton  [1-5] Meme  [SPACE] Toggle  [ESC] Exit",
                new Point(margin, frame.rows() - 14),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.42, new Scalar(180, 180, 180), 1);
    }

    /**
     * Handle keyboard commands.
     */
    private static boolean handleInput(int key, GestureState state) {
        if (key == 27 || key == 'q' || key == 'Q') { // ESC or Q
            System.out.println("[Input] Exit key pressed.");
            return true;
        }

        // [M] Toggle between AI Auto-Gesture mode and Manual Mode
        if (key == 'm' || key == 'M') {
            autoGestureMode = !autoGestureMode;
            state.reset();
            System.out.println("[Mode] Switched to: " + (autoGestureMode ? "AI AUTO-GESTURE" : "MANUAL"));
        }

        // [A] Toggle Anchor mode: Torso -> Face -> Center Screen
        if (key == 'a' || key == 'A') {
            AnchorMode[] modes = AnchorMode.values();
            anchorMode = modes[(anchorMode.ordinal() + 1) % modes.length];
            System.out.println("[Anchor] Switched to: " + anchorMode.getLabel());
        }

        // [S] Toggle Skeleton visualization
        if (key == 's' || key == 'S') {
            showSkeleton = !showSkeleton;
            System.out.println("[Skeleton] Display: " + (showSkeleton ? "ON" : "OFF"));
        }

        // [SPACE] Toggle meme overlay
        if (key == ' ') {
            showMeme = !showMeme;
            System.out.println("[Overlay] Meme display: " + (showMeme ? "ON" : "OFF"));
        }

        // [1-5] Manual meme selection (switches to manual mode)
        if (key >= '1' && key <= '9' && memeNames != null && memeNames.length > 0) {
            int index = key - '1';
            if (index < memeNames.length) {
                manualMemeIndex = index;
                autoGestureMode = false;
                System.out.println("[Manual] Selected meme: " + memeNames[manualMemeIndex]);
            }
        }

        // [+/-] Scale
        if (key == '+' || key == '=') {
            memeScale = Math.min(0.95, memeScale + 0.05);
            System.out.printf("[Scale] %.0f%%%n", memeScale * 100);
        }
        if (key == '-' || key == '_') {
            memeScale = Math.max(0.20, memeScale - 0.05);
            System.out.printf("[Scale] %.0f%%%n", memeScale * 100);
        }

        return false;
    }

    private static void printControls() {
        System.out.println();
        System.out.println("─── MemeMotion Live Controls ────────────────");
        System.out.println("  [M]      Toggle AI Auto-Gesture / Manual Mode");
        System.out.println("  [A]      Cycle Anchor (Torso -> Face -> Center)");
        System.out.println("  [S]      Toggle Skeleton Visualization");
        System.out.println("  [1-5]    Manual Meme Switch");
        System.out.println("  [SPACE]  Toggle Meme Overlay On/Off");
        System.out.println("  [+/-]    Resize Meme");
        System.out.println("  [ESC]    Exit");
        System.out.println("─────────────────────────────────────────────");
        System.out.println();
    }
}
