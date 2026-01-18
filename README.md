# YOLO Object Detector (Android)

Lightweight YOLO object detection library for Android using TensorFlow Lite.  
Designed to be simple to use while still supporting common YOLO model variations.

The library handles:
- Image preprocessing (letterbox)
- Model inference
- Output decoding
- Non-Maximum Suppression (NMS)

It works with both float and INT8 models, with or without built-in NMS.

---

## Features

- Supports YOLO float and INT8 TensorFlow Lite models
- Works with models that include NMS or raw outputs
- Automatic GPU delegate usage when available
- CPU fallback with XNNPACK
- Simple Kotlin API
- Custom labels support
- No external dependencies beyond TensorFlow Lite

---

## Installation

### Gradle (JitPack)

Add JitPack to your repositories:

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
implementation("com.github.OmarAbuShanb:yolo-object-detector:v1.0.0")
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
| ------------------- | ------------------------------------------- |
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
