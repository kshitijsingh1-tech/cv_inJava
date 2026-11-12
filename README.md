# cv_inJava

MemeMotion is a Java desktop application that uses a webcam, OpenCV, and MoveNet pose estimation to trigger meme images and clips from detected gestures.

## Requirements

- Java 21 or newer
- Maven 3.9 or newer
- A webcam

## Run

From the repository root:

```powershell
cd memeMotion
mvn clean compile
mvn exec:java
```

The application expects its models and media under `memeMotion/assets`, which is included in this repository.

## Project contents

- `memeMotion/src/main/java` — Java application source
- `memeMotion/assets` — pose-estimation model, face detector, and meme media
- `memeMotion/API_SPECIFICATION.md` — detailed API and computer-vision reference
- `guide_for_cvinJava.md` — project guide
