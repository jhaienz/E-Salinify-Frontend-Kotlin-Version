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

class HandDetector(context: Context) {

    private var handLandmarker: HandLandmarker? = null
    private var latestResult: HandLandmarkerResult? = null

    companion object {
        private const val TAG = "HandDetector"
        private const val PADDING = 25 // Padding around detected hand (matching Python implementation)
    }

    init {
        try {
            Log.d(TAG, "Initializing HandLandmarker with model from assets...")

            // Load hand landmarker model from assets
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            Log.d(TAG, "BaseOptions configured with model: hand_landmarker.task")

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ ->
                    latestResult = result
                    if (result.landmarks().isNotEmpty()) {
                        Log.v(TAG, "✓ Hand detected with ${result.landmarks()[0].size} landmarks")
                    }
                }
                .setErrorListener { error ->
                    Log.e(TAG, "❌ Hand detection error: ${error.message}", error)
                }
                .build()

            Log.d(TAG, "Creating HandLandmarker from options...")
            handLandmarker = HandLandmarker.createFromOptions(context, options)
            Log.i(TAG, "✓✓✓ HandLandmarker initialized successfully! ✓✓✓")
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL: Failed to initialize HandLandmarker", e)
            e.printStackTrace()
        }
    }

    /**
     * Detects hand in the bitmap and returns bounding box with padding
     * @param bitmap The input image
     * @param timestampMs The timestamp in milliseconds (required for LIVE_STREAM mode)
     * @return Bounding box rectangle or null if no hand detected
     */
    fun detectHand(bitmap: Bitmap, timestampMs: Long): Rect? {
        if (handLandmarker == null) {
            Log.e(TAG, "❌ HandLandmarker not initialized - skipping detection")
            return null
        }

        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            handLandmarker?.detectAsync(mpImage, timestampMs)

            // Wait a bit for result (since we're in LIVE_STREAM mode)
            Thread.sleep(10)

            val result = latestResult
            if (result != null && result.landmarks().isNotEmpty()) {
                val landmarks = result.landmarks()[0]

                // Calculate bounding box from landmarks
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = Float.MIN_VALUE
                var maxY = Float.MIN_VALUE

                landmarks.forEach { landmark ->
                    minX = minOf(minX, landmark.x())
                    minY = minOf(minY, landmark.y())
                    maxX = maxOf(maxX, landmark.x())
                    maxY = maxOf(maxY, landmark.y())
                }

                // Convert normalized coordinates to pixel coordinates
                val width = bitmap.width
                val height = bitmap.height

                val left = ((minX * width) - PADDING).toInt().coerceAtLeast(0)
                val top = ((minY * height) - PADDING).toInt().coerceAtLeast(0)
                val right = ((maxX * width) + PADDING).toInt().coerceAtMost(width)
                val bottom = ((maxY * height) + PADDING).toInt().coerceAtMost(height)

                val boundingBox = Rect(left, top, right, bottom)
                Log.d(TAG, "✓ Bounding box calculated: $boundingBox")
                return boundingBox
            } else {
                Log.v(TAG, "No hand detected in current frame")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error detecting hand", e)
            e.printStackTrace()
        }

        return null
    }

    /**
     * Releases resources
     */
    fun close() {
        handLandmarker?.close()
        handLandmarker = null
        Log.d(TAG, "HandLandmarker closed")
    }
}
