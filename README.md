# YOLO Object Detector (Android)

[![Maven Central](https://img.shields.io/maven-central/v/io.github.omarabushanb/yolo-object-detector.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.omarabushanb/yolo-object-detector)
[![JitPack](https://jitpack.io/v/OmarAbuShanb/yolo-object-detector-android.svg)](https://jitpack.io/#$OmarAbuShanb/yolo-object-detector-android)
![Android API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Lightweight YOLO object detection library for Android using TensorFlow Lite.  
Designed to be simple to use while still supporting common YOLO model variations.

---

## Features

- Supports YOLO float and INT8 TensorFlow Lite models
- Works with models that include NMS or raw outputs
- Automatic GPU delegate usage when available
- CPU fallback with XNNPACK
- Simple Kotlin API
- Custom labels support

---

## Installation

### ✅ Maven Central (Recommended)

```kotlin
dependencies {
    implementation("io.github.omarabushanb:yolo-object-detector:1.0.3")
}
```

---

### Alternative: JitPack / GitHub

Add JitPack repository:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Add the dependency:

```kotlin
dependencies {
    implementation("com.github.OmarAbuShanb:yolo-object-detector-android:1.0.3")
}
```

---

## Quick Start

### 1. Put your model in `assets/`

Example:

```
app/src/main/assets/best_int8_nms.tflite
```

---

### 2. Create the detector

```kotlin
val detector = YoloObjectDetector(
    context = this,
    config = YoloConfig(
        modelAssetPath = "best_int8_nms.tflite",
        labels = listOf(
            "password_field",
            "username_field"
        )
    )
)
```

---

### 3. Run detection

```kotlin
val boxes = detector.detect(bitmap)

for (box in boxes) {
    val classId = box.classId
    val score = box.score
    val rect = box.box
}
```

---

### 4. Release resources

```kotlin
override fun onDestroy() {
    super.onDestroy()
    detector.close()
}
```

---

## Configuration

`YoloConfig` controls how the detector behaves:

| Property            | Description                                 |
|---------------------|---------------------------------------------|
| `modelAssetPath`    | Path to the `.tflite` model inside `assets` |
| `confThreshold`     | Minimum confidence score                    |
| `iouThreshold`      | IOU threshold for NMS                       |
| `padColor`          | Padding color used during letterboxing      |
| `numThreads`        | Number of CPU threads                       |
| `labels`            | Optional list of class labels               |
| `useGpuIfAvailable` | Enable GPU delegate automatically           |

Example:

```kotlin
YoloConfig(
    modelAssetPath = "model.tflite",
    confThreshold = 0.25f,
    iouThreshold = 0.7f,
    numThreads = 4,
    useGpuIfAvailable = true
)
```

---

## Output

Detection results are returned as a list of `DetectedBox`:

```kotlin
data class DetectedBox(
    val classId: Int,
    val score: Float,
    val box: RectF
)
```

- `classId`: detected class index
- `score`: confidence score
- `box`: bounding box in original image coordinates

---

## Model Requirements

- Input must be **square** (e.g. 320×320, 640×640)
- RGB input
- YOLO-style output
- Supports:
    - Raw outputs (manual NMS applied)
    - Models with built-in NMS

Both float and full INT8 quantized models are supported.

---

## Performance Notes

- GPU delegate is enabled automatically if supported
- Falls back to CPU + XNNPACK when GPU is unavailable
- Always call `close()` to release resources

---

## License

Apache License 2.0

