package dev.anonymous.yoloobjectdetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

/**
 * A TensorFlow Lite interpreter wrapper for YOLO (You Only Look Once) object detection models.
 * This class handles model loading, input preprocessing, inference, and output post-processing
 * (including non-maximum suppression). It supports both float and quantized (INT8/UINT8) models,
 * and can optionally utilize a GPU delegate if available.
 *
 * @param context The Android application context, used for accessing assets.
 * @param config The [YoloConfig] object containing model configuration parameters.
 */
class YoloObjectDetector(
    context: Context,
    private val config: YoloConfig
) {

    companion object {
        private const val TAG = "YoloObjectDetector"
    }

    private val interpreter: Interpreter
    private var gpuDelegate: GpuDelegate? = null

    /** List of labels corresponding to the classes detected by the YOLO model. */
    private val labels: List<String> = config.labels ?: emptyList()

    private lateinit var inputBuffer: ByteBuffer
    private lateinit var inputBufferInt8: ByteBuffer

    /** The expected input size (width/height) of the model's input image. */
    private var inputSize = 0

    /** The number of color channels in the model's input image (e.g., 3 for RGB). */
    private var channels = 0

    // Output buffers for different model types (quantized, float, with/without NMS)
    private lateinit var outputInt8: Array<Array<ByteArray>>
    private lateinit var outputWithNmsInt8: Array<Array<ByteArray>>
    private lateinit var outputRaw: Array<Array<FloatArray>>
    private lateinit var outputWithNms: Array<Array<FloatArray>>

    /** The number of output channels in the model's output tensor. */
    private var outputChannels = 0

    /** The number of anchor boxes or detections in the model's output. */
    private var numAnchors = 0

    /** The number of classes the model is trained to detect. */
    private var numClasses = 0

    /** Quantization scale for dequantizing INT8/UINT8 model outputs. */
    private var outputScale = 0f

    /** Quantization zero point for dequantizing INT8/UINT8 model outputs. */
    private var outputZeroPoint = 0

    /** Flag indicating if the model's output already includes Non-Maximum Suppression (NMS). */
    private var hasNms = false

    /** Flag indicating if the model uses full integer (INT8/UINT8) quantization for both input and output. */
    private var isFullInt8 = false

    init {
        // Initialize the TensorFlow Lite interpreter with the model and options.
        interpreter = Interpreter(loadModel(context), createInterpreterOptions())
        // Determine if the model is fully quantized (INT8/UINT8).
        determineQuantizationStatus()
        // Initialize output buffers based on model type.
        initializeOutputBuffersAndParameters()
        // Initialize input buffers based on model type.
        initializeInputBuffers()
    }

    /**
     * Determines the quantization status of the loaded TensorFlow Lite model.
     * Sets `isFullInt8` to true if both input and output tensors are of INT8 or UINT8 type.
     */
    private fun determineQuantizationStatus() {
        val inputType = interpreter.getInputTensor(0).dataType()
        val outputType = interpreter.getOutputTensor(0).dataType()

        isFullInt8 =
            (inputType == DataType.INT8 || inputType == DataType.UINT8) &&
                    (outputType == DataType.INT8 || outputType == DataType.UINT8)
    }

    /**
     * Initializes the output buffers and extracts output-related parameters
     * like `outputScale`, `outputZeroPoint`, `numAnchors`, `outputChannels`,
     * and `numClasses` from the TensorFlow Lite model's output tensor.
     */
    private fun initializeOutputBuffersAndParameters() {
        val outTensor = interpreter.getOutputTensor(0)
        outputScale = outTensor.quantizationParams().scale
        outputZeroPoint = outTensor.quantizationParams().zeroPoint

        val outShape = outTensor.shape()
        // Determine if the model's output tensor already incorporates NMS.
        // A common pattern for models with NMS is an output shape of [1, N, C] where C <= 7.
        hasNms = outShape.size == 3 && outShape[2] <= 7

        if (hasNms) {
            // Output format: [1, N, C] where N is numAnchors and C is outputChannels
            numAnchors = outShape[1]
            outputChannels = outShape[2]

            if (isFullInt8) {
                outputWithNmsInt8 =
                    Array(1) { Array(numAnchors) { ByteArray(outputChannels) } }
            } else {
                outputWithNms =
                    Array(1) { Array(numAnchors) { FloatArray(outputChannels) } }
            }
        } else {
            // Output format: [1, C, N] where C is outputChannels and N is numAnchors
            outputChannels = outShape[1]
            numAnchors = outShape[2]

            if (isFullInt8) {
                outputInt8 = Array(1) { Array(outputChannels) { ByteArray(numAnchors) } }
            } else {
                outputRaw = Array(1) { Array(outputChannels) { FloatArray(numAnchors) } }
            }
        }

        require(outputChannels >= 5) {
            "Invalid YOLO outputChannels: $outputChannels. Expected at least 5 for (x, y, w, h, confidence, ...classes)"
        }

        // The number of classes is total output channels minus the bounding box and confidence scores (4 + 1).
        numClasses = outputChannels - 5
    }

    /**
     * Initializes the input buffers based on the model's expected input shape and quantization type.
     * Ensures the input is square as expected by YOLO models.
     */
    private fun initializeInputBuffers() {
        val inTensor = interpreter.getInputTensor(0)
        val inShape = inTensor.shape()
        val inputHeight = inShape[1]
        val inputWidth = inShape[2]

        require(inputHeight == inputWidth) {
            "YOLO model expects square input, got $inputHeight x $inputWidth"
        }

        inputSize = inputHeight
        channels = inShape[3]

        // Allocate direct ByteBuffer for efficient data transfer to the TensorFlow Lite interpreter.
        if (isFullInt8) {
            inputBufferInt8 = ByteBuffer
                .allocateDirect(1 * inputSize * inputSize * channels) // 1 (batch) * H * W * C
                .order(ByteOrder.nativeOrder())
        } else {
            inputBuffer = ByteBuffer
                .allocateDirect(1 * inputSize * inputSize * channels * 4) // 4 bytes per float
                .order(ByteOrder.nativeOrder())
        }
    }

    /**
     * Closes the TensorFlow Lite interpreter and any associated delegates (e.g., GPU delegate).
     * It is crucial to call this method to release resources when the detector is no longer needed.
     */
    fun close() {
        try {
            interpreter.close()
            gpuDelegate?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TensorFlow Lite interpreter and delegates", e)
        }
    }

    /**
     * Performs object detection on the given [Bitmap].
     * The input bitmap is first preprocessed (letterboxed) to match the model's input requirements.
     * Then, inference is run, and the raw output is post-processed to produce a list of [DetectedBox]es.
     *
     * @param bitmap The input [Bitmap] on which to perform detection.
     * @return A list of [DetectedBox] objects representing the detected objects.
     */
    fun detect(bitmap: Bitmap): List<DetectedBox> {
        // Preprocess the input bitmap using letterboxing to maintain aspect ratio and fit model input size.
        val letterboxedBitmap = letterbox(bitmap)

        return when {
            isFullInt8 && hasNms -> {
                convertBitmapToInt8Buffer(letterboxedBitmap.bmp)
                interpreter.run(inputBufferInt8, outputWithNmsInt8)
                decodeNmsOutputInt8(outputWithNmsInt8, bitmap, letterboxedBitmap)
            }

            isFullInt8 -> {
                convertBitmapToInt8Buffer(letterboxedBitmap.bmp)
                interpreter.run(inputBufferInt8, outputInt8)
                decodeRawOutputInt8(outputInt8, bitmap, letterboxedBitmap)
            }

            hasNms -> {
                convertBitmapToFloatBuffer(letterboxedBitmap.bmp)
                interpreter.run(inputBuffer, outputWithNms)
                decodeNmsOutputFloat(outputWithNms, bitmap, letterboxedBitmap)
            }

            else -> {
                convertBitmapToFloatBuffer(letterboxedBitmap.bmp)
                interpreter.run(inputBuffer, outputRaw)
                // If NMS is not part of the model output, apply it manually.
                nmsPerClass(decodeRawOutputFloat(outputRaw, bitmap, letterboxedBitmap))
            }
        }
    }

    /**
     * Returns the label string for a given class ID.
     *
     * @param id The integer ID of the class.
     * @return The corresponding label string, or "unknown" if the ID is out of bounds or labels are not provided.
     */
    fun getLabel(id: Int): String = labels.getOrNull(id) ?: "unknown"

    /**
     * Dequantizes a byte value into a float, using the provided zero point and scale.
     * This is used for INT8/UINT8 models.
     *
     * @param byteValue The byte value to dequantize.
     * @param zeroPoint The quantization zero point.
     * @param scale The quantization scale.
     * @return The dequantized float value.
     */
    private fun dequantizeByte(byteValue: Byte, zeroPoint: Int, scale: Float): Float {
        return (byteValue.toInt() - zeroPoint) * scale
    }

    /**
     * Helper function to process detected bounding box coordinates and add to a list.
     */
    private fun addDetectedBox(
        boxes: MutableList<DetectedBox>,
        classId: Int,
        score: Float,
        rawLeft: Float,
        rawTop: Float,
        rawRight: Float,
        rawBottom: Float,
        originalBitmap: Bitmap,
        letterboxData: Letterbox
    ) {
        if (score < config.confThreshold) return

        // Scale bounding box coordinates back to the original bitmap's dimensions.
        var left = (rawLeft - letterboxData.padX) / letterboxData.scale
        var right = (rawRight - letterboxData.padX) / letterboxData.scale
        var top = (rawTop - letterboxData.padY) / letterboxData.scale
        var bottom = (rawBottom - letterboxData.padY) / letterboxData.scale

        // Clip coordinates to ensure they are within the original bitmap's bounds.
        left = left.coerceIn(0f, originalBitmap.width.toFloat())
        right = right.coerceIn(0f, originalBitmap.width.toFloat())
        top = top.coerceIn(0f, originalBitmap.height.toFloat())
        bottom = bottom.coerceIn(0f, originalBitmap.height.toFloat())

        // Add the detected box if it has valid dimensions.
        if (right > left && bottom > top) {
            boxes.add(
                DetectedBox(
                    classId,
                    score,
                    RectF(left, top, right, bottom)
                )
            )
        }
    }

    /**
     * Decodes the raw output from a YOLO float model (without built-in NMS) and converts it into a list of [DetectedBox]es.
     *
     * @param out The raw output array from the TensorFlow Lite interpreter.
     * @param originalBitmap The original bitmap, used for scaling bounding box coordinates back to original size.
     * @param letterboxData The [Letterbox] data, containing scaling and padding information from preprocessing.
     * @return A list of raw [DetectedBox]es before Non-Maximum Suppression.
     */
    private fun decodeRawOutputFloat(
        out: Array<Array<FloatArray>>,
        originalBitmap: Bitmap,
        letterboxData: Letterbox
    ): List<DetectedBox> {
        val boxes = ArrayList<DetectedBox>()

        for (i in 0 until numAnchors) {
            var bestScore = 0f
            var bestClass = -1

            // Find the class with the highest confidence score for this anchor.
            for (c in 0 until numClasses) {
                val score = out[0][4 + c][i]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c
                }
            }

            // Extract bounding box coordinates (center_x, center_y, width, height) and scale by inputSize.
            val cx = out[0][0][i] * inputSize
            val cy = out[0][1][i] * inputSize
            val w = out[0][2][i] * inputSize
            val h = out[0][3][i] * inputSize

            // Convert center-width/height to top-left/bottom-right coordinates.
            val left = cx - w / 2f
            val top = cy - h / 2f
            val right = cx + w / 2f
            val bottom = cy + h / 2f

            addDetectedBox(
                boxes,
                bestClass,
                bestScore,
                left, top, right, bottom,
                originalBitmap,
                letterboxData
            )
        }
        return boxes
    }

    /**
     * Decodes the raw output from a YOLO INT8 model (without built-in NMS) and converts it into a list of [DetectedBox]es.
     *
     * @param out The raw output array from the TensorFlow Lite interpreter.
     * @param originalBitmap The original bitmap, used for scaling bounding box coordinates back to original size.
     * @param letterboxData The [Letterbox] data, containing scaling and padding information from preprocessing.
     * @return A list of raw [DetectedBox]es before Non-Maximum Suppression.
     */
    private fun decodeRawOutputInt8(
        out: Array<Array<ByteArray>>,
        originalBitmap: Bitmap,
        letterboxData: Letterbox
    ): List<DetectedBox> {
        val boxes = ArrayList<DetectedBox>()

        for (i in 0 until numAnchors) {
            var bestScore = 0f
            var bestClass = -1

            // Find the class with the highest confidence score for this anchor.
            for (c in 0 until numClasses) {
                val score = dequantizeByte(out[0][4 + c][i], outputZeroPoint, outputScale)
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c
                }
            }

            // Extract bounding box coordinates (center_x, center_y, width, height) and scale by inputSize.
            val cx = dequantizeByte(out[0][0][i], outputZeroPoint, outputScale) * inputSize
            val cy = dequantizeByte(out[0][1][i], outputZeroPoint, outputScale) * inputSize
            val w = dequantizeByte(out[0][2][i], outputZeroPoint, outputScale) * inputSize
            val h = dequantizeByte(out[0][3][i], outputZeroPoint, outputScale) * inputSize

            // Convert center-width/height to top-left/bottom-right coordinates.
            val left = cx - w / 2f
            val top = cy - h / 2f
            val right = cx + w / 2f
            val bottom = cy + h / 2f

            addDetectedBox(
                boxes,
                bestClass,
                bestScore,
                left, top, right, bottom,
                originalBitmap,
                letterboxData
            )
        }
        return boxes
    }

    /**
     * Decodes the output from a YOLO float model that has built-in NMS and converts it into a list of [DetectedBox]es.
     *
     * @param out The raw output array from the TensorFlow Lite interpreter.
     * @param originalBitmap The original bitmap, used for scaling bounding box coordinates back to original size.
     * @param letterboxData The [Letterbox] data, containing scaling and padding information from preprocessing.
     * @return A list of [DetectedBox] objects, already post-processed with NMS.
     */
    private fun decodeNmsOutputFloat(
        out: Array<Array<FloatArray>>,
        originalBitmap: Bitmap,
        letterboxData: Letterbox
    ): List<DetectedBox> {
        val results = ArrayList<DetectedBox>()

        for (i in out[0].indices) {
            val row = out[0][i]

            val score = row[4] // Confidence score is at index 4

            // Bounding box coordinates (left, top, right, bottom) and class ID.
            val left = row[0] * inputSize
            val top = row[1] * inputSize
            val right = row[2] * inputSize
            val bottom = row[3] * inputSize
            val cls = row[5].toInt() // Class ID is at index 5

            addDetectedBox(
                results,
                cls,
                score,
                left, top, right, bottom,
                originalBitmap,
                letterboxData
            )
        }
        return results
    }

    /**
     * Decodes the output from a YOLO INT8 model that has built-in NMS and converts it into a list of [DetectedBox]es.
     *
     * @param out The raw output array from the TensorFlow Lite interpreter.
     * @param originalBitmap The original bitmap, used for scaling bounding box coordinates back to original size.
     * @param letterboxData The [Letterbox] data, containing scaling and padding information from preprocessing.
     * @return A list of [DetectedBox] objects, already post-processed with NMS.
     */
    private fun decodeNmsOutputInt8(
        out: Array<Array<ByteArray>>,
        originalBitmap: Bitmap,
        letterboxData: Letterbox
    ): List<DetectedBox> {
        val results = ArrayList<DetectedBox>()

        for (i in out[0].indices) {
            val row = out[0][i]

            val score = dequantizeByte(
                row[4],
                outputZeroPoint,
                outputScale
            ) // Confidence score is at index 4

            // Bounding box coordinates (left, top, right, bottom) and class ID.
            val left = dequantizeByte(row[0], outputZeroPoint, outputScale) * inputSize
            val top = dequantizeByte(row[1], outputZeroPoint, outputScale) * inputSize
            val right = dequantizeByte(row[2], outputZeroPoint, outputScale) * inputSize
            val bottom = dequantizeByte(row[3], outputZeroPoint, outputScale) * inputSize
            val cls = dequantizeByte(
                row[5],
                outputZeroPoint,
                outputScale
            ).toInt() // Class ID is at index 5

            addDetectedBox(
                results,
                cls,
                score,
                left, top, right, bottom,
                originalBitmap,
                letterboxData
            )
        }
        return results
    }

    /**
     * Applies Non-Maximum Suppression (NMS) per class to a list of [DetectedBox]es.
     * NMS is used to remove redundant overlapping bounding boxes, keeping only the most confident ones.
     *
     * @param boxes The initial list of [DetectedBox]es to apply NMS on.
     * @return A new list of [DetectedBox]es after NMS has been applied.
     */
    private fun nmsPerClass(boxes: List<DetectedBox>): List<DetectedBox> {
        val out = ArrayList<DetectedBox>()
        // Group boxes by class ID, then apply NMS within each class.
        boxes.groupBy { it.classId }.forEach { (_, classBoxes) ->
            // Sort boxes by score in descending order.
            val sortedBoxes = classBoxes.sortedByDescending { it.score }
            val used = BooleanArray(sortedBoxes.size)

            for (i in sortedBoxes.indices) {
                if (used[i]) continue // Skip if this box has already been suppressed.

                val currentBox = sortedBoxes[i]
                out.add(currentBox) // Add the current confident box to the results.

                // Compare with all subsequent boxes.
                for (j in i + 1 until sortedBoxes.size) {
                    // If the IoU between the current box and a subsequent box is above the threshold,
                    // mark the subsequent box as used (suppressed).
                    if (iou(currentBox.box, sortedBoxes[j].box) > config.iouThreshold) {
                        used[j] = true
                    }
                }
            }
        }
        return out
    }

    /**
     * Calculates the Intersection over Union (IoU) of two bounding boxes.
     * IoU is a metric used to quantify the overlap between two bounding boxes.
     *
     * @param a The first bounding box [RectF].
     * @param b The second bounding box [RectF].
     * @return The IoU value, a float between 0.0 and 1.0.
     */
    private fun iou(a: RectF, b: RectF): Float {
        // Calculate the intersection area.
        val interW = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val interH = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val inter = interW * interH

        // Calculate the union area.
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union <= 0f) 0f else inter / union
    }

    /**
     * Data class to hold the result of the letterboxing operation, including the processed bitmap
     * and the scaling/padding factors needed to revert coordinates to the original image space.
     *
     * @property bmp The letterboxed bitmap, scaled and padded to the model's input size.
     * @property scale The scaling factor applied to the original image.
     * @property padX The horizontal padding applied to the letterboxed image.
     * @property padY The vertical padding applied to the letterboxed image.
     */
    private data class Letterbox(
        val bmp: Bitmap,
        val scale: Float,
        val padX: Float,
        val padY: Float
    )

    /**
     * Preprocesses a [Bitmap] by resizing and padding it to fit the model's input dimensions
     * while preserving its aspect ratio (letterboxing).
     *
     * @param sourceBitmap The original bitmap to be processed.
     * @return A [Letterbox] object containing the processed bitmap and scaling/padding information.
     */
    private fun letterbox(sourceBitmap: Bitmap): Letterbox {
        // Calculate the scaling ratio to fit the bitmap into the inputSize while maintaining aspect ratio.
        val ratio =
            min(inputSize / sourceBitmap.width.toFloat(), inputSize / sourceBitmap.height.toFloat())
        val newWidth = (sourceBitmap.width * ratio).toInt()
        val newHeight = (sourceBitmap.height * ratio).toInt()

        // Resize the source bitmap.
        val resizedBitmap = sourceBitmap.scale(newWidth, newHeight)
        // Create a new bitmap of the target input size with the specified padding color.
        val outputBitmap = createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(outputBitmap)
        canvas.drawColor(Color.rgb(config.padColor, config.padColor, config.padColor))

        // Calculate padding to center the resized image.
        val padX = (inputSize - newWidth) / 2f
        val padY = (inputSize - newHeight) / 2f

        // Draw the resized bitmap onto the output bitmap with padding.
        canvas.drawBitmap(resizedBitmap, padX, padY, null)

        return Letterbox(outputBitmap, ratio, padX, padY)
    }

    /**
     * Converts a [Bitmap] into a [ByteBuffer] suitable for a float TensorFlow Lite model input.
     * Pixels are converted from ARGB to RGB and normalized to [0, 1] range.
     *
     * @param bitmap The bitmap to convert.
     */
    private fun convertBitmapToFloatBuffer(bitmap: Bitmap) {
        inputBuffer.rewind()
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in pixels) {
            // Extract R, G, B components and normalize to [0, 1].
            inputBuffer.putFloat(((p shr 16) and 0xFF) / 255f) // Red
            inputBuffer.putFloat(((p shr 8) and 0xFF) / 255f)  // Green
            inputBuffer.putFloat((p and 0xFF) / 255f)          // Blue
        }
        inputBuffer.rewind()
    }

    /**
     * Converts a [Bitmap] into a [ByteBuffer] suitable for an INT8 TensorFlow Lite model input.
     * Pixels are converted from ARGB to RGB (bytes).
     *
     * @param bitmap The bitmap to convert.
     */
    private fun convertBitmapToInt8Buffer(bitmap: Bitmap) {
        inputBufferInt8.rewind()
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (p in pixels) {
            // Extract R, G, B components as bytes.
            inputBufferInt8.put(((p shr 16) and 0xFF).toByte()) // Red
            inputBufferInt8.put(((p shr 8) and 0xFF).toByte())  // Green
            inputBufferInt8.put((p and 0xFF).toByte())          // Blue
        }
        inputBufferInt8.rewind()
    }

    /**
     * Loads the TensorFlow Lite model file from the application's assets folder into a [ByteBuffer].
     *
     * @param context The Android application context.
     * @return A [ByteBuffer] containing the loaded model.
     * @throws IllegalArgumentException if the model file cannot be found or loaded.
     */
    private fun loadModel(context: Context): ByteBuffer {
        val fileDescriptor = context.assets.openFd(config.modelAssetPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Creates and configures [Interpreter.Options] for the TensorFlow Lite interpreter.
     * This includes setting the number of threads and optionally enabling a GPU delegate or XNNPACK.
     *
     * @return An [Interpreter.Options] object with the desired configurations.
     */
    private fun createInterpreterOptions(): Interpreter.Options {
        val options = Interpreter.Options().setNumThreads(config.numThreads)

        if (config.useGpuIfAvailable) {
            val compatibilityList = CompatibilityList()
            // Check if GPU delegate is supported on the current device.
            if (compatibilityList.isDelegateSupportedOnThisDevice) {
                // Create and add a GpuDelegate with the best available options.
                gpuDelegate = GpuDelegate(compatibilityList.bestOptionsForThisDevice)
                options.addDelegate(gpuDelegate)
                Log.d(TAG, "GPU Delegate Enabled")
                return options
            }
        }

        // If GPU is not used or available, enable XNNPACK for CPU acceleration.
        options.setUseXNNPACK(true)
        Log.d(TAG, "XNNPACK Enabled")
        return options
    }
}
