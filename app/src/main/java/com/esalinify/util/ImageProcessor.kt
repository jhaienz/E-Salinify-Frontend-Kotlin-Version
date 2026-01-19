package com.esalinify.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

object ImageProcessor {

    /**
     * Crops the hand region from the original bitmap using the bounding box
     */
    fun cropHandRegion(bitmap: Bitmap, boundingBox: Rect): Bitmap {
        // Ensure bounding box is within bitmap bounds
        val x = boundingBox.left.coerceAtLeast(0)
        val y = boundingBox.top.coerceAtLeast(0)
        val width = boundingBox.width().coerceAtMost(bitmap.width - x)
        val height = boundingBox.height().coerceAtMost(bitmap.height - y)

        return if (width > 0 && height > 0) {
            Bitmap.createBitmap(bitmap, x, y, width, height)
        } else {
            // Return a small blank bitmap if bounds are invalid
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }

    /**
     * Preprocesses the bitmap for model input:
     * 1. Resize to 64x64
     * 2. Keep RGB channels (no grayscale conversion)
     */
    fun preprocessForModel(bitmap: Bitmap): Bitmap {
        // Resize to 64x64 (model input size) - keep RGB
        return Bitmap.createScaledBitmap(bitmap, 64, 64, true)
    }

    /**
     * Converts a bitmap to grayscale
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayscaleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)

                // Standard grayscale conversion formula
                val gray = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()
                val grayPixel = Color.rgb(gray, gray, gray)

                grayscaleBitmap.setPixel(x, y, grayPixel)
            }
        }

        return grayscaleBitmap
    }

    /**
     * Normalizes pixel values from 0-255 range to 0-1 range
     * Returns a FloatArray suitable for TensorFlow Lite input (grayscale)
     */
    fun bitmapToNormalizedFloatArray(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val floatArray = FloatArray(width * height)

        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                // Get grayscale value (all channels are the same in grayscale)
                val gray = Color.red(pixel)
                // Normalize to 0-1 range
                floatArray[index++] = gray / 255.0f
            }
        }

        return floatArray
    }

    /**
     * Converts RGB bitmap to normalized float array for TensorFlow Lite input
     * Returns a FloatArray with shape [height * width * 3] (RGB channels)
     * Normalized to 0-1 range
     */
    fun bitmapToNormalizedRgbFloatArray(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val floatArray = FloatArray(width * height * 3)

        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                // Extract and normalize RGB channels to 0-1 range
                floatArray[index++] = Color.red(pixel) / 255.0f
                floatArray[index++] = Color.green(pixel) / 255.0f
                floatArray[index++] = Color.blue(pixel) / 255.0f
            }
        }

        return floatArray
    }
}
