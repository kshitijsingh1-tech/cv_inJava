# 📖 MemeMotion — The Master API & Computer Vision Reference Manual
> **A Comprehensive, First-Principles Guide to Every Class, Method, Field, Matrix, Tensor, and Algorithm in MemeMotion.**

---

## 🧭 How to Use This Manual
This manual is written assuming **zero prior background in OpenCV, ONNX Runtime, or Computer Vision in Java**. 

Every single concept, class, function, variable, and native memory behavior is broken down following this strict schema:
1. **Origin / Where It Came From**: Package, library, hardware layer, or architectural module.
2. **Type**: Language construct (`Class`, `Method`, `Field`, `Native JNI Bridge`, `Direct Memory Buffer`, `Enum`).
3. **What It Stores or Does**: Plain-English explanation, internal data layout, step-by-step mechanics, mathematical formulas, and why it was designed that way.

---

## 📑 Table of Contents (Arranged in Chronological Pipeline Order)
1. [Core Foundations: Computer Vision & Memory in Java](#1-core-foundations-computer-vision--memory-in-java)
2. [OpenCV Library Primitives](#2-opencv-library-primitives)
3. [Microsoft ONNX Runtime Primitives](#3-microsoft-onnx-runtime-primitives)
4. [Stage 1 — Capture Layer: `CameraEngine`](#4-stage-1--capture-layer-cameraengine)
5. [Stage 2 — AI Pose Estimation: `PoseEstimator`](#5-stage-2--ai-pose-estimation-poseestimator)
6. [Stage 2.5 — Landmarks & Geometry: `Keypoint` & `PoseResult`](#6-stage-25--landmarks--geometry-keypoint--poseresult)
7. [Stage 3 — Motion Intelligence: `GestureType`, `GestureClassifier` & `GestureState`](#7-stage-3--motion-intelligence-gesturetype-gestureclassifier--gesturestate)
8. [Stage 4 — Graphics, Video & Compositing: `MemeRenderer`](#8-stage-4--graphics-video--compositing-memerenderer)
9. [Stage 5 — Application Controller: `MemeMotionApp`](#9-stage-5--application-controller-mememotionapp)
10. [MoveNet SinglePose Lightning Tensor Contract](#10-movenet-singlepose-lightning-tensor-contract)

---

## 1. Core Foundations: Computer Vision & Memory in Java

### A. What is a "Frame" and how is an image stored?
A digital video is just a rapid sequence of still pictures called **frames** displayed at 30 or 60 frames per second (FPS).
To a computer, an image is a **2D grid (matrix) of numbers**:
- A $1280 \times 720$ video frame has $720$ rows and $1280$ columns = $921,600$ individual pixels.
- Each pixel has color channels. In a standard color camera, each pixel has **3 channels**: Blue, Green, and Red.
- Each channel is stored as an 8-bit unsigned byte (`uint8`), ranging from `0` (darkest/none) to `255` (full intensity).
- Total memory for just **one** $1280 \times 720$ frame:
  $$1280 \times 720 \times 3 \text{ bytes} = 2,764,800\text{ bytes} \approx 2.76\text{ MB}$$
- At 30 FPS, your camera processes **$\approx 83\text{ MB}$ of raw image data every second!**

### B. Why BGR instead of RGB?
OpenCV was created in 1999 at Intel, when camera hardware and Windows bitmap formats (`.bmp`) ordered bytes in memory in reverse: **BGR** (Blue, Green, Red). OpenCV kept BGR as its default format for historical backward compatibility.
- Camera gives OpenCV: `[Blue, Green, Red]`
- Web browsers & modern ML models expect: `[Red, Green, Blue, Alpha]` (RGBA)
- This is why we call `Imgproc.cvtColor(..., Imgproc.COLOR_BGR2RGBA)` before feeding images to MoveNet!

### C. The Two Memory Worlds: Java Heap vs. Off-Heap (Native C++ Memory)
Java programs run inside the Java Virtual Machine (JVM) with a Garbage Collector (GC).
However:
- OpenCV and ONNX Runtime are written in **ultra-fast native C++**.
- When OpenCV captures a frame or ONNX loads a neural network, the bytes live in **Native Off-Heap Memory (C++ RAM)**.
- The Java variable `Mat frame` or `OrtSession session` is just a tiny Java "wrapper" containing a 64-bit C++ memory address pointer (`nativeObj`).
- **Critical Consequence**: Java's Garbage Collector **cannot** free native C++ memory automatically! If you don't explicitly call `frame.release()` or `session.close()`, your computer will leak memory and crash.

### D. Direct ByteBuffers vs. Normal Java Byte Arrays
In Java, `byte[] array` lives in JVM memory that the GC moves around during defragmentation. Native C++ cannot safely read a normal Java `byte[]`.
Instead, Java provides **Direct ByteBuffers** (`ByteBuffer.allocateDirect(...)`):
- Allocates fixed memory in **native OS memory**.
- The GC will never move it.
- Both Java and C++ read/write to the exact same pointer at zero-copy bus speeds.

---

## 2. OpenCV Library Primitives

Provided by `org.openpnp:opencv:4.9.0-0`.

### `nu.pattern.OpenCV.loadLocally()`
- **Type**: `Static Method`.
- **What it does**: Detects your OS, extracts `opencv_java490.dll` from the Maven `.jar` to a temporary directory, and executes `System.load()` to link native C++ binaries to Java.

### `org.opencv.core.Mat`
- **Type**: `Class` (Native Image Matrix Handle).
- **Stores**: 64-bit native pointer `nativeObj`, row count (height), col count (width), and channel format (`CV_8UC3` for BGR, `CV_8UC4` for BGRA/RGBA).
- **Key Methods**:
  - `empty()`: Returns `true` if matrix has 0 dimensions or points to null.
  - `release()`: Frees native C++ buffer from off-heap RAM.
  - `submat(int y1, int y2, int x1, int x2)`: Returns an ROI slice without copying pixels.
  - `copyTo(Mat dst)`: Deep memory clone.
  - `get(int row, int col, byte[] data)`: Copies native bytes into Java.

### `org.opencv.videoio.VideoCapture`
- **Type**: `Class` (Hardware Camera Controller).
- **Key Methods**:
  - `new VideoCapture(0)`: Opens default webcam index 0.
  - `new VideoCapture("path.mp4")`: Opens a video file for frame-by-frame decoding.
  - `isOpened()`: Interrogates the OS driver (DirectShow/MediaFoundation) to confirm the device/file was successfully opened.
  - `read(Mat image)`: Decodes the next available frame into the provided matrix.
  - `set(propId, val)` / `get(propId)`: Sets/reads hardware settings (resolution, FPS).
  - `release()`: Powers down the webcam sensor and turns off the privacy LED.

### `org.opencv.highgui.HighGui`
- **Type**: `Utility Class` (Desktop Window Renderer).
- **Key Methods**:
  - `imshow(title, frame)`: Renders matrix inside a Swing `JFrame`.
  - `waitKey(delayMs)`: Pauses for `delayMs` for window paint and keyboard polling.
  - `destroyAllWindows()`: Closes desktop windows.

---

## 3. Microsoft ONNX Runtime Primitives

Provided by `com.microsoft.onnxruntime:onnxruntime:1.31.1`.

### `ai.onnxruntime.OrtEnvironment`
- Singleton managing background thread pools, memory allocators, and hardware acceleration.

### `ai.onnxruntime.OrtSession`
- Represents the compiled MoveNet computational graph in memory.
- `session.run(inputs)` executes neural network convolutions across threads.

### `ai.onnxruntime.OnnxTensor`
- Bridges Java NIO `ByteBuffer` directly to native C++ 4D tensor $[1, 192, 192, 4]$ (`UINT8`).

---

## 4. Stage 1 — Capture Layer: `CameraEngine`

**File**: [`src/main/java/com/visionforge/mememotion/capture/CameraEngine.java`](file:///d:/cv_in_java/memeMotion/src/main/java/com/visionforge/mememotion/capture/CameraEngine.java)

### Detailed Field Breakdown
- `VideoCapture camera`: Native OpenCV webcam session.
- `Mat frame`: Reusable frame buffer to eliminate heap GC thrashing.
- `requestedWidth`, `requestedHeight`: Resolution requested (1280×720).
- `frameCount`, `fpsStartTime`, `currentFps`: 500ms sliding-window FPS calculator.
- `closed`: Volatile boolean guard preventing double-free native crashes.

### Key Methods
- `boolean isOpened()`: Validates that Windows OS granted access to the camera hardware.
- `Mat captureFrame()`: Calls `camera.read(frame)`. Updates FPS every 500ms and returns frame pointer.
- `void close()`: Powers down camera sensor and frees `frame` native buffer. Includes a JVM shutdown hook (`addShutdownHook`) so camera releases even on `Ctrl+C`.

---

## 5. Stage 2 — AI Pose Estimation: `PoseEstimator`

**File**: [`src/main/java/com/visionforge/mememotion/detection/PoseEstimator.java`](file:///d:/cv_in_java/memeMotion/src/main/java/com/visionforge/mememotion/detection/PoseEstimator.java)

### Fields
- `MODEL_WIDTH = 192`, `MODEL_HEIGHT = 192`: Model input dimensions.
- `NUM_KEYPOINTS = 17`: Number of detected body landmarks.
- `resizedRgba`: Pre-allocated instance matrix for bilinear downscaling and BGR $\rightarrow$ RGBA color space conversion.
- `inputBuffer`: Direct off-heap `ByteBuffer` ($192 \times 192 \times 4 = 147,456$ bytes).

### The Inference Pipeline in `estimate(Mat bgrFrame)`
1. `Imgproc.resize(bgrFrame, resizedRgba, Size(192, 192))`
2. `Imgproc.cvtColor(resizedRgba, resizedRgba, Imgproc.COLOR_BGR2RGBA)`
3. `resizedRgba.get(0, 0, imageByteArray)` $\rightarrow$ `inputBuffer.put(...)` $\rightarrow$ `inputBuffer.flip()`
4. `OnnxTensor.createTensor(env, inputBuffer, [1, 192, 192, 4], OnnxJavaType.UINT8)`
5. `session.run(...)` $\rightarrow$ parses 4D tensor `[1, 1, 17, 3]`
6. Multiplies normalized $[0, 1]$ coordinates by camera dimensions ($1280 \times 720$).

---

## 6. Stage 2.5 — Landmarks & Geometry: `Keypoint` & `PoseResult`

- **`Keypoint`**: Stores `index`, `name`, `x`, `y`, `normalizedX`, `normalizedY`, `confidence`.
- **`PoseResult.drawSkeleton(Mat)`**: Draws 14 structural green bone vectors and 17 yellow joint circles.
- **`PoseResult.getTorsoBoundingBox(...)`**: Measures dynamic shoulder width $W_s = |X_{\text{left}} - X_{\text{right}}|$, extrapolates to hip coordinates, and returns a chest-centered box leaving the user's real arms visible.

---

## 7. Stage 3 — Motion Intelligence: `GestureType`, `GestureClassifier` & `GestureState`

### `GestureType` Enum
- `NONE`: Live camera feed only.
- `HANDS_RAISED`: Both wrists elevated above shoulder level in the air $\rightarrow$ `hands_raised` (WhatsApp Video 02.51.55).
- `ONE_HAND_RAISED`: Exactly one wrist elevated above shoulder level in the air $\rightarrow$ `hallelujah` (WhatsApp Video 02.46.29).
- `HEAD_HOLDING`: Wrists clutching lateral temples/ears flanking the skull ($W_{\text{span}} \ge 0.42 \times S_w$, center face visible) $\rightarrow$ `im_cooked` (WhatsApp Video 02.42.05).
- `FACE_COVERED`: Wrist(s) centered directly over eyes/nose ($W_{\text{span}} < 0.38 \times S_w$ or one-handed facepalm on nose) $\rightarrow$ `face_covered` (WhatsApp Video 02.51.12).
- `HONORED_ONE`: Gojo Satoru pose — one hand held upright beside chin/jaw/cheek, elbow bent down at ribs, other hand relaxed; or Domain Expansion clasp at chest $\rightarrow$ `honored_one` (WhatsApp Video 02.53.36).
- `DRAMATIC_SHRUG`: Arms wide open at chest level $\rightarrow$ `dramatic_shrug`.
- `LOOKING_DOWN`: Head slumped low toward shoulders $\rightarrow$ `depression_mode`.
- `T_POSE`: Horizontal arms locked at shoulder height $\rightarrow$ `t_pose`.

### Scale & Spatial Invariance in `GestureClassifier`
All geometric rules divide pixel distances by the dynamic **Shoulder Width ($S_w$)**, making detection work at any distance from the camera:
1. **Face Covered (Facepalm / Despair)**: Evaluated first. Detects two hands pressed over the nose/eyes ($W_{\text{span}} < 0.38 \times S_w$) or a single-handed facepalm directly on the nose ($< 0.28 \times S_w$).
2. **Head Holding ("I'm Cooked")**: Detects hands clutching the lateral temples on opposite sides of the head ($W_{\text{span}} \ge 0.42 \times S_w$) with the central face clear and uncovered.
3. **The Honored One (Gojo Pose)**: Detected when one hand is held upright beside the chin/jaw ($Y \in [Y_{\text{face}} - 0.10 \times S_w, Y_{\text{shoulder}} + 0.25 \times S_w]$) with elbow pointing DOWN ($Y_{\text{elbow}} > Y_{\text{wrist}} + 0.08 \times S_w$), or hands clasped at the chest.
4. **Hands Raised & One Hand Raised**: Simple, robust elevation check ($Y < Y_{\text{shoulder}} - 0.25 \times S_w$).

### Temporal Smoothing in `GestureState`
- **Debouncing**: Must observe candidate pose for $\ge 2$ consecutive frames (~60 ms at 30 FPS) before activating, filtering out momentary frame noise.
- **Display Persistence**: Holds active meme for at least $1,800\text{ ms}$ to eliminate fluttering. Switching directly to another sustained pose triggers instantly.

---

## 8. Stage 4 — Graphics, Video & Compositing: `MemeRenderer`

**File**: [`src/main/java/com/visionforge/mememotion/render/MemeRenderer.java`](file:///d:/cv_in_java/memeMotion/src/main/java/com/visionforge/mememotion/render/MemeRenderer.java)

### Multi-Format & Video Clip Engine (`MemeClip`)
- Supports **static images** (`.png`, `.jpg`) and **animated video clips** (`.mp4`, `.avi`, `.mov`).
- When loading an `.mp4` video (e.g., `hallelujah.mp4`), OpenCV's `VideoCapture` decodes all frames into memory at startup.
- `getCurrentFrame()` calculates:
  $$\text{index} = \left(\frac{\text{System.currentTimeMillis()}}{\text{frameDurationMs}}\right) \pmod{\text{frames.size()}}$$
  Yields silky-smooth looped video playback with zero disk I/O lag!

### SIMD Alpha Compositing Formula
$$\text{Result} = (\text{Foreground} \times \alpha) + (\text{Background} \times (1.0 - \alpha))$$
Executed natively via OpenCV vector math (`Core.multiply`, `Core.add`).

---

## 9. Stage 5 — Application Controller: `MemeMotionApp`

**File**: [`src/main/java/com/visionforge/mememotion/MemeMotionApp.java`](file:///d:/cv_in_java/memeMotion/src/main/java/com/visionforge/mememotion/MemeMotionApp.java)

### Continuous Game Loop Flow
1. **Capture**: `camera.captureFrame()`
2. **Detect**: `poseEstimator.estimate(frame)`
3. **Motion AI**: `GestureClassifier.classify(pose)` + `gestureState.update(...)`
4. **Render**: `pose.drawSkeleton(frame)`, `renderer.overlayMeme(frame, ...)`, `drawHud(...)`
5. **Display & Poll**: `HighGui.imshow(...)`, `HighGui.waitKey(1)`

### Robust Exit Architecture
- Java `KeyboardFocusManager` dispatcher intercepts `ESC` and `Q` globally at the JVM level.
- `HighGui.n_closed_windows > 0` exits immediately when the `[X]` window button is clicked.
- `System.exit(0)` in `finally` guarantees background AWT threads terminate and release the camera.

---

## 10. MoveNet SinglePose Lightning Tensor Contract

- **Input Tensor**: Name: `"pixel_values"` | Type: `UINT8` | Shape: `[1, 192, 192, 4]` (RGBA)
- **Output Tensor**: Name: `"keypoints"` | Type: `FLOAT` | Shape: `[1, 1, 17, 3]` (Normalized $Y, X, \text{Confidence}$)

### 17 Keypoint Indices
`0: Nose`, `1: Left Eye`, `2: Right Eye`, `3: Left Ear`, `4: Right Ear`, `5: Left Shoulder`, `6: Right Shoulder`, `7: Left Elbow`, `8: Right Elbow`, `9: Left Wrist`, `10: Right Wrist`, `11: Left Hip`, `12: Right Hip`, `13: Left Knee`, `14: Right Knee`, `15: Left Ankle`, `16: Right Ankle`.
