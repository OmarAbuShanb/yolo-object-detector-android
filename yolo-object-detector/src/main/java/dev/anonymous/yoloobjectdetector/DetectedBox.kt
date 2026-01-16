package dev.anonymous.yoloobjectdetector

import android.graphics.RectF

/**
 * Data class representing a single detected object (bounding box) by the YOLO model.
 *
 * @property classId The integer ID of the detected object's class.
 * @property score The confidence score (probability) of the detection, typically between 0.0 and 1.0.
 * @property box The bounding box of the detected object, represented as an [RectF] with float coordinates.
 *               These coordinates are relative to the original image dimensions.
 */
data class DetectedBox(
    val classId: Int,
    val score: Float,
    val box: RectF
)
