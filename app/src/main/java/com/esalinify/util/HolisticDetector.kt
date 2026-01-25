package com.esalinify.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

/**
 * Data class to hold holistic detection results
 * Contains 543 landmarks: 468 face + 33 pose + 21 left hand + 21 right hand
 */
data class HolisticDetectionResult(
    val boundingBox: Rect,
    val allLandmarks: List<FloatArray> // 543 landmarks, each [x, y, z]
)

/**
 * Holistic detector combining face, pose, and hand landmarks
 * for the Kaggle phrase recognition model
 */
class HolisticDetector(context: Context) {

    private var faceLandmarker: FaceLandmarker? = null
    private var poseLandmarker: PoseLandmarker? = null
    private var handLandmarker: HandLandmarker? = null

    companion object {
        private const val TAG = "HolisticDetector"
        private const val PADDING = 30

        // Landmark counts from MediaPipe Holistic
        private const val NUM_FACE_LANDMARKS = 468
        private const val NUM_POSE_LANDMARKS = 33
        private const val NUM_HAND_LANDMARKS = 21
        private const val TOTAL_LANDMARKS = NUM_FACE_LANDMARKS + NUM_POSE_LANDMARKS + (NUM_HAND_LANDMARKS * 2) // 543
    }

    init {
        try {
            Log.d(TAG, "Initializing MediaPipe Holistic components...")

            // Initialize Face Landmarker
            val faceBaseOptions = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")
                .build()

            val faceOptions = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(faceBaseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, faceOptions)
            Log.d(TAG, "✓ FaceLandmarker initialized")

            // Initialize Pose Landmarker
            val poseBaseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker.task")
                .build()

            val poseOptions = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(poseBaseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, poseOptions)
            Log.d(TAG, "✓ PoseLandmarker initialized")

            // Initialize Hand Landmarker (for both hands)
            val handBaseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val handOptions = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(handBaseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .setNumHands(2) // Detect both hands
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, handOptions)
            Log.d(TAG, "✓ HandLandmarker initialized")

            Log.i(TAG, "✓✓✓ HolisticDetector initialized successfully (543 landmarks)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize HolisticDetector", e)
        }
    }

    private var lastFaceTimestamp = 0L
    private var lastPoseTimestamp = 0L
    private var lastHandTimestamp = 0L

    /**
     * Detects face, pose, and hands and returns 543 combined landmarks
     * @return HolisticDetectionResult with all 543 landmarks, or null if detection fails
     */
    @Synchronized
    fun detectHolistic(bitmap: Bitmap, timestampMs: Long): HolisticDetectionResult? {
        if (faceLandmarker == null || poseLandmarker == null || handLandmarker == null) {
            Log.e(TAG, "One or more landmarkers not initialized")
            return null
        }

        try {
            val mpImage = BitmapImageBuilder(bitmap).build()

            // Ensure monotonically increasing timestamps for each detector
            // Add 10ms between each to avoid conflicts
            lastFaceTimestamp = maxOf(timestampMs, lastFaceTimestamp + 10)
            lastPoseTimestamp = maxOf(timestampMs + 5, lastPoseTimestamp + 10)
            lastHandTimestamp = maxOf(timestampMs + 10, lastHandTimestamp + 10)

            // Run all three detectors with their own timestamps
            val faceResult = faceLandmarker!!.detectForVideo(mpImage, lastFaceTimestamp)
            val poseResult = poseLandmarker!!.detectForVideo(mpImage, lastPoseTimestamp)
            val handResult = handLandmarker!!.detectForVideo(mpImage, lastHandTimestamp)

            // Combine all landmarks into a single list of 543 landmarks
            val allLandmarks = mutableListOf<FloatArray>()

            // 1. Face landmarks (468 points) - indices 0-467
            if (faceResult.faceLandmarks().isNotEmpty()) {
                val faceLandmarks = faceResult.faceLandmarks()[0]
                faceLandmarks.forEach { landmark ->
                    allLandmarks.add(floatArrayOf(landmark.x(), landmark.y(), landmark.z()))
                }
            } else {
                // Fill with zeros if no face detected
                repeat(NUM_FACE_LANDMARKS) {
                    allLandmarks.add(floatArrayOf(0f, 0f, 0f))
                }
            }

            // 2. Pose landmarks (33 points) - indices 468-500
            if (poseResult.landmarks().isNotEmpty()) {
                val poseLandmarks = poseResult.landmarks()[0]
                poseLandmarks.forEach { landmark ->
                    allLandmarks.add(floatArrayOf(landmark.x(), landmark.y(), landmark.z()))
                }
            } else {
                // Fill with zeros if no pose detected
                repeat(NUM_POSE_LANDMARKS) {
                    allLandmarks.add(floatArrayOf(0f, 0f, 0f))
                }
            }

            // 3. Left hand landmarks (21 points) - indices 501-521
            // 4. Right hand landmarks (21 points) - indices 522-542
            var leftHandAdded = false
            var rightHandAdded = false

            if (handResult.landmarks().isNotEmpty()) {
                handResult.handedness().forEachIndexed { index, handedness ->
                    val hand = handResult.landmarks()[index]
                    val isLeft = handedness[0].categoryName() == "Left"

                    if (isLeft && !leftHandAdded) {
                        // Add to left hand position
                        hand.forEach { landmark ->
                            allLandmarks.add(floatArrayOf(landmark.x(), landmark.y(), landmark.z()))
                        }
                        leftHandAdded = true
                    } else if (!isLeft && !rightHandAdded) {
                        // Will add to right hand position after left hand
                        rightHandAdded = true
                    }
                }
            }

            // Fill left hand with zeros if not detected
            if (!leftHandAdded) {
                repeat(NUM_HAND_LANDMARKS) {
                    allLandmarks.add(floatArrayOf(0f, 0f, 0f))
                }
            }

            // Add right hand landmarks or fill with zeros
            if (handResult.landmarks().isNotEmpty()) {
                handResult.handedness().forEachIndexed { index, handedness ->
                    val hand = handResult.landmarks()[index]
                    val isRight = handedness[0].categoryName() == "Right"

                    if (isRight && !rightHandAdded) {
                        hand.forEach { landmark ->
                            allLandmarks.add(floatArrayOf(landmark.x(), landmark.y(), landmark.z()))
                        }
                        rightHandAdded = true
                    }
                }
            }

            if (!rightHandAdded) {
                repeat(NUM_HAND_LANDMARKS) {
                    allLandmarks.add(floatArrayOf(0f, 0f, 0f))
                }
            }

            // Calculate bounding box from all detected landmarks
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            var nonZeroCount = 0

            allLandmarks.forEach { landmark ->
                if (landmark[0] > 0) { // Only consider non-zero landmarks
                    nonZeroCount++
                    val x = landmark[0] * bitmap.width
                    val y = landmark[1] * bitmap.height
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }

            // Count detected components
            val faceDetected = faceResult.faceLandmarks().isNotEmpty()
            val poseDetected = poseResult.landmarks().isNotEmpty()
            val handsDetected = handResult.landmarks().isNotEmpty()

            Log.d(TAG, "Holistic detection - Face: $faceDetected, Pose: $poseDetected, Hands: $handsDetected, Non-zero landmarks: $nonZeroCount/$TOTAL_LANDMARKS")

            // Only return result if we have meaningful detection (at least hands detected)
            if (!handsDetected) {
                Log.d(TAG, "No hands detected - skipping holistic result")
                return null
            }

            val boundingBox = if (minX != Float.MAX_VALUE) {
                Rect(
                    (minX - PADDING).toInt().coerceAtLeast(0),
                    (minY - PADDING).toInt().coerceAtLeast(0),
                    (maxX + PADDING).toInt().coerceAtMost(bitmap.width),
                    (maxY + PADDING).toInt().coerceAtMost(bitmap.height)
                )
            } else {
                Rect(0, 0, bitmap.width, bitmap.height)
            }

            return HolisticDetectionResult(
                boundingBox = boundingBox,
                allLandmarks = allLandmarks
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in holistic detection", e)
        }

        return null
    }

    fun close() {
        faceLandmarker?.close()
        poseLandmarker?.close()
        handLandmarker?.close()
        faceLandmarker = null
        poseLandmarker = null
        handLandmarker = null
        Log.d(TAG, "HolisticDetector closed")
    }
}
