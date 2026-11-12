
package com.visionforge.mememotion.capture;

import nu.pattern.OpenCV;

import org.opencv.core.Mat;
import org.opencv.highgui.HighGui;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

public class CameraPreview {

    public static void main(String[] args) {

        // Load native OpenCV libraries
        OpenCV.loadLocally();

        // Access the default system camera
        VideoCapture camera = new VideoCapture(0);

        if (!camera.isOpened()) {
            System.out.println("Could not access camera!");
            return;
        }

        // 3. Request camera resolution
        camera.set(Videoio.CAP_PROP_FRAME_WIDTH, 1280);
        camera.set(Videoio.CAP_PROP_FRAME_HEIGHT, 720);

        // 4. Create a container for camera frames
        Mat frame = new Mat();

        System.out.println("Camera started!");

        try {

            // 5. Continuously capture frames
            while (true) {

                boolean captured = camera.read(frame);

                if (!captured || frame.empty()) {
                    continue;
                }

                // 6. Display the current frame
                HighGui.imshow("MemeMotion - Live Camera", frame);

                // 7. Exit when ESC is pressed
                int key = HighGui.waitKey(1);

                if (key == 27) {
                    break;
                }
            }

        } finally {

            // 8. Release native resources
            camera.release();
            frame.release();
            HighGui.destroyAllWindows();

            System.out.println("Camera stopped!");
        }
    }
}
