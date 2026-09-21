package dev.anonymous.yoloobjectdetector

/**
 * Hardware delegate execution mode for the TensorFlow Lite interpreter.
 */
enum class DelegateMode {
    /**
     * Automatically selects the safest and fastest backend:
     * - For INT8/UINT8 quantized models: Uses CPU with XNNPACK directly (GPU does not support INT8,
     *   and NNAPI has vendor driver bugs returning all zeros on chipsets like Snapdragon 680 / Redmi Note 11).
     * - For Float models: Probes GPU -> NNAPI -> CPU (XNNPACK).
     */
    AUTO,

    /**
     * Forces CPU with XNNPACK acceleration.
     * Highly recommended for INT8 quantized models. It is 100% deterministic, robust across all
     * devices/OEMs, and extremely fast (~10-20ms) using ARM NEON vector instructions.
     */
    CPU,

    /**
     * Forces GPU Delegate (falls back to CPU if unsupported or fails).
     * Suitable only for FP32/FP16 models.
     */
    GPU,

    /**
     * Forces NNAPI Delegate (falls back to CPU if unsupported or fails).
     */
    NNAPI
}

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
 * @property delegateMode The hardware delegate selection mode (AUTO, CPU, GPU, NNAPI). Default is [DelegateMode.AUTO].
 */
data class YoloConfig(
    val modelAssetPath: String,
    var confThreshold: Float = 0.25f,
    val iouThreshold: Float = 0.7f,
    val padColor: Int = 114,
    val numThreads: Int = 4,
    val labels: List<String>? = null,
    val delegateMode: DelegateMode = DelegateMode.AUTO,
)
