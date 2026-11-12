package com.visionforge.mememotion.detection;

import ai.onnxruntime.*;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Collections;

/**
 * MoveNet SinglePose Lightning ONNX Pose Estimator.
 *
 * Input: [1, 192, 192, 4] UINT8 (RGBA format)
 * Output: [1, 1, 17, 3] FLOAT (Keypoints [y, x, confidence])
 */
public class PoseEstimator implements AutoCloseable {

    private static final int MODEL_WIDTH = 192;
    private static final int MODEL_HEIGHT = 192;
    private static final int NUM_KEYPOINTS = 17;
    private static final float DEFAULT_MIN_CONFIDENCE = 0.25f;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;
    private final float minConfidence;

    private final Mat resizedRgba = new Mat();
    private final byte[] imageByteArray = new byte[MODEL_WIDTH * MODEL_HEIGHT * 4];
    private final ByteBuffer inputBuffer = ByteBuffer.allocateDirect(MODEL_WIDTH * MODEL_HEIGHT * 4);

    public PoseEstimator(String modelPath) throws OrtException {
        this(modelPath, DEFAULT_MIN_CONFIDENCE);
    }

    public PoseEstimator(String modelPath, float minConfidence) throws OrtException {
        this.minConfidence = minConfidence;
        File file = new File(modelPath);
        if (!file.exists()) {
            throw new IllegalArgumentException("MoveNet ONNX model not found at: " + file.getAbsolutePath());
        }

        this.env = OrtEnvironment.getEnvironment("MoveNetEnv");
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        // Use single-thread or multi-thread depending on available cores
        opts.setInterOpNumThreads(2);
        opts.setIntraOpNumThreads(2);

        this.session = env.createSession(modelPath, opts);
        this.inputName = session.getInputNames().iterator().next();
    }

    /**
     * Runs pose estimation on the given BGR camera frame.
     *
     * @param bgrFrame OpenCV BGR image
     * @return PoseResult with 17 detected keypoints
     */
    public synchronized PoseResult estimate(Mat bgrFrame) {
        if (bgrFrame == null || bgrFrame.empty()) {
            return null;
        }

        int origWidth = bgrFrame.cols();
        int origHeight = bgrFrame.rows();

        try {
            // 1. Preprocess: Resize to 192x192 and convert BGR -> RGBA
            Imgproc.resize(bgrFrame, resizedRgba, new Size(MODEL_WIDTH, MODEL_HEIGHT));
            Imgproc.cvtColor(resizedRgba, resizedRgba, Imgproc.COLOR_BGR2RGBA);

            // 2. Extract raw bytes
            resizedRgba.get(0, 0, imageByteArray);
            inputBuffer.clear();
            inputBuffer.put(imageByteArray);
            inputBuffer.flip();

            // 3. Create ONNX Tensor [1, 192, 192, 4] UINT8
            long[] shape = new long[]{1, MODEL_HEIGHT, MODEL_WIDTH, 4};
            try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, inputBuffer, shape, OnnxJavaType.UINT8);
                 OrtSession.Result result = session.run(Collections.singletonMap(inputName, inputTensor))) {

                // 4. Output tensor: [1, 1, 17, 3] FLOAT
                OnnxValue outputVal = result.get(0);
                float[][][][] rawOutput = (float[][][][]) outputVal.getValue();

                Keypoint[] keypoints = new Keypoint[NUM_KEYPOINTS];
                for (int i = 0; i < NUM_KEYPOINTS; i++) {
                    float y = rawOutput[0][0][i][0];
                    float x = rawOutput[0][0][i][1];
                    float conf = rawOutput[0][0][i][2];
                    keypoints[i] = new Keypoint(i, y, x, conf, origWidth, origHeight);
                }

                return new PoseResult(keypoints, minConfidence);
            }

        } catch (Exception e) {
            System.err.println("[PoseEstimator] Inference error: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void close() {
        try {
            resizedRgba.release();
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (OrtException e) {
            System.err.println("[PoseEstimator] Error during cleanup: " + e.getMessage());
        }
    }
}
