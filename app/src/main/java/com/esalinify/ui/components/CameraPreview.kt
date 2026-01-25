package com.esalinify.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

private const val TAG = "CameraPreview"

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraFacing: com.esalinify.data.CameraFacing = com.esalinify.data.CameraFacing.BACK,
    onFrameAnalyzed: (Bitmap, Long) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Executor lifecycle - only dispose when component unmounts
    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "Component unmounting - shutting down camera executor")
            cameraExecutor.shutdown()
        }
    }

    // Camera binding - rebind when cameraFacing changes
    DisposableEffect(cameraFacing) {
        Log.i(TAG, "Setting up CameraX with facing: $cameraFacing...")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                Log.d(TAG, "CameraProvider obtained")

                // Preview use case
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                Log.d(TAG, "Preview use case created")

                // Image analysis use case for frame processing
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy, onFrameAnalyzed)
                        }
                    }
                Log.d(TAG, "ImageAnalysis use case created")

                // Select camera based on facing parameter
                val cameraSelector = when (cameraFacing) {
                    com.esalinify.data.CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                    com.esalinify.data.CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                }
                Log.d(TAG, "Using camera: $cameraFacing")

                // Unbind all use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.i(TAG, "✓ Camera initialized successfully - frames will be analyzed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error starting camera", e)
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            Log.d(TAG, "Camera facing changed - unbinding camera (executor stays alive)")
            // Don't shutdown executor here - just let camera rebind
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize()
    )
}

private var processedFrameCount = 0

/**
 * Processes ImageProxy and converts to Bitmap for analysis
 */
private fun processImageProxy(
    imageProxy: ImageProxy,
    onFrameAnalyzed: (Bitmap, Long) -> Unit
) {
    try {
        processedFrameCount++
        if (processedFrameCount == 1) {
            Log.i(TAG, "✓ First frame received! Starting frame processing...")
        }
        if (processedFrameCount % 60 == 0) {
            Log.d(TAG, "Processed $processedFrameCount frames total")
        }

        val bitmap = imageProxy.toBitmap()
        val rotatedBitmap = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())

        // Pass bitmap and timestamp to callback
        val timestampMs = imageProxy.imageInfo.timestamp / 1_000_000 // Convert to ms
        onFrameAnalyzed(rotatedBitmap, timestampMs)

        // Clean up
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error processing image proxy", e)
        e.printStackTrace()
    } finally {
        imageProxy.close()
    }
}

/**
 * Rotates bitmap to correct orientation
 */
private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return bitmap

    val matrix = Matrix().apply {
        postRotate(degrees)
    }

    return try {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (e: Exception) {
        Log.e(TAG, "Error rotating bitmap", e)
        bitmap
    }
}
