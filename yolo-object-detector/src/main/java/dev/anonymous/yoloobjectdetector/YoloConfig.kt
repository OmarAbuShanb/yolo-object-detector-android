package dev.anonymous.yoloobjectdetector

/**
 * Configuration class for the [YoloObjectDetector].
 *
 * @property modelAssetPath The path to the TensorFlow Lite model file within the app's `assets` folder.
 * @property confThreshold The confidence threshold for object detection. Detections with a score below this value will be discarded.
 * @property iouThreshold The Intersection Over Union (IOU) threshold used for Non-Maximum Suppression (NMS).
 *                         Overlapping bounding boxes with an IOU greater than this value will be suppressed.
 * @property padColor The RGB color value (0-255) used for padding the input image during letterboxing.
 * @property numThreads The number of threads to use for TensorFlow Lite interpreter inference.
 * @property labels An optional list of strings representing the class labels corresponding to the model's output.
 * @property useGpuIfAvailable A boolean indicating whether to attempt to use a GPU delegate for inference if available on the device.
 */
data class YoloConfig(
    val modelAssetPath: String,
    val confThreshold: Float = 0.25f,
    val iouThreshold: Float = 0.7f,
    val padColor: Int = 114,
    val numThreads: Int = 4,
    val labels: List<String>? = null,
    val useGpuIfAvailable: Boolean = true,
)
