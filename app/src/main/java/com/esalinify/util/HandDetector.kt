package com.esalinify.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * Data class to hold hand detection results
 */
data class HandDetectionResult(
    val boundingBox: Rect,
    val landmarks: List<FloatArray>, // 21 landmarks, each [x, y, z]
    val normalizedLandmarks: List<FloatArray> // 21 landmarks normalized [x, y]
)

class HandDetector(context: Context) {

    private var handLandmarker: HandLandmarker? = null

    companion object {
        private const val TAG = "HandDetector"
        private const val PADDING = 30
    }

    init {
        try {
            Log.d(TAG, "Initializing HandLandmarker with VIDEO mode...")

            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
            Log.i(TAG, "✓ HandLandmarker initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize HandLandmarker", e)
        }
    }

    private var lastTimestamp = 0L

    /**
     * Detects hand and returns landmarks for classification
     * @return HandDetectionResult with bounding box and 21 landmarks, or null if no hand
     */
    fun detectHandWithLandmarks(bitmap: Bitmap, timestampMs: Long): HandDetectionResult? {
        if (handLandmarker == null) {
            return null
        }

        try {
            val adjustedTimestamp = if (timestampMs > lastTimestamp) {
                timestampMs
            } else {
                lastTimestamp + 1
            }
            lastTimestamp = adjustedTimestamp

            val mpImage = BitmapImageBuilder(bitmap).build()
            val result: HandLandmarkerResult = handLandmarker!!.detectForVideo(mpImage, adjustedTimestamp)

            if (result.landmarks().isNotEmpty()) {
                val landmarks = result.landmarks()[0]

                // Extract normalized landmarks (0-1 range)
                val normalizedLandmarks = landmarks.map { landmark ->
                    floatArrayOf(landmark.x(), landmark.y())
                }

                // Extract pixel coordinates for bounding box
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = Float.MIN_VALUE
                var maxY = Float.MIN_VALUE

                val pixelLandmarks = landmarks.map { landmark ->
                    val x = landmark.x() * bitmap.width
                    val y = landmark.y() * bitmap.height
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                    floatArrayOf(x, y, landmark.z())
                }

                val boundingBox = Rect(
                    (minX - PADDING).toInt().coerceAtLeast(0),
                    (minY - PADDING).toInt().coerceAtLeast(0),
                    (maxX + PADDING).toInt().coerceAtMost(bitmap.width),
                    (maxY + PADDING).toInt().coerceAtMost(bitmap.height)
                )

                return HandDetectionResult(
                    boundingBox = boundingBox,
                    landmarks = pixelLandmarks,
                    normalizedLandmarks = normalizedLandmarks
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting hand", e)
        }

        return null
    }

    /**
     * Legacy method - detects hand and returns only bounding box
     */
    fun detectHand(bitmap: Bitmap, timestampMs: Long): Rect? {
        return detectHandWithLandmarks(bitmap, timestampMs)?.boundingBox
    }

    fun close() {
        handLandmarker?.close()
        handLandmarker = null
        Log.d(TAG, "HandLandmarker closed")
    }
}
