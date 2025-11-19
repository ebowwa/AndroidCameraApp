package com.camerapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
// import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * Custom overlay view for drawing hand landmarks and connections
 * Renders detected hand landmarks on top of camera preview
 */
class HandTrackingOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paint objects for drawing
    private val landmarkPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val connectionPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val boundingBoxPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    // Hand tracking results
    // private var handLandmarkerResult: HandLandmarkerResult? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    // Demo data for testing
    private var demoMode: Boolean = true

    companion object {
        // Hand landmark connections for drawing skeleton
        private val HAND_CONNECTIONS = listOf(
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),     // Thumb
            Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8),     // Index finger
            Pair(5, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12), // Middle finger
            Pair(9, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16), // Ring finger
            Pair(13, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20), // Pinky
            Pair(0, 17)                                          // Palm
        )
    }

    /**
     * Update the hand detection results
     */
    /*
    fun setResults(
        result: HandLandmarkerResult,
        imageWidth: Int,
        imageHeight: Int
    ) {
        this.handLandmarkerResult = result
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }
    */

    /**
     * Demo method to simulate hand detection
     */
    fun setDemoResults(imageWidth: Int, imageHeight: Int) {
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        demoMode = true
        invalidate()
    }

    /**
     * Clear the overlay
     */
    fun clear() {
        // this.handLandmarkerResult = null
        demoMode = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (demoMode) {
            // Demo mode: Draw simulated hand landmarks
            drawDemoHand(canvas)
            return
        }

        /*
        val result = handLandmarkerResult ?: return

        // Calculate scale factors to map from image coordinates to view coordinates
        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight

        // Draw each detected hand
        result.landmarks().forEachIndexed { handIndex, landmarks ->
            // Adjust colors for multiple hands
            landmarkPaint.color = if (handIndex == 0) Color.RED else Color.BLUE
            connectionPaint.color = if (handIndex == 0) Color.GREEN else Color.CYAN
            boundingBoxPaint.color = if (handIndex == 0) Color.YELLOW else Color.MAGENTA

            // Convert landmarks to screen coordinates
            val screenLandmarks = landmarks.map { landmark ->
                Pair(
                    landmark.x() * scaleX,
                    landmark.y() * scaleY
                )
            }

            // Draw connections (hand skeleton)
            HAND_CONNECTIONS.forEach { (startIdx, endIdx) ->
                if (startIdx < screenLandmarks.size && endIdx < screenLandmarks.size) {
                    val start = screenLandmarks[startIdx]
                    val end = screenLandmarks[endIdx]
                    canvas.drawLine(
                        start.first, start.second,
                        end.first, end.second,
                        connectionPaint
                    )
                }
            }

            // Draw landmark points
            screenLandmarks.forEach { (x, y) ->
                canvas.drawCircle(x, y, 6f, landmarkPaint)
            }

            // Draw bounding box around the hand
            if (landmarks.isNotEmpty()) {
                val minX = landmarks.minOf { it.x() } * scaleX
                val maxX = landmarks.maxOf { it.x() } * scaleX
                val minY = landmarks.minOf { it.y() } * scaleY
                val maxY = landmarks.maxOf { it.y() } * scaleY

                val padding = 20f
                val boundingBox = RectF(
                    minX - padding,
                    minY - padding,
                    maxX + padding,
                    maxY + padding
                )
                canvas.drawRect(boundingBox, boundingBoxPaint)
            }
        }

        // Draw hand count
        if (result.landmarks().isNotEmpty()) {
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = 48f
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            canvas.drawText(
                "${result.landmarks().size} Hand${if (result.landmarks().size > 1) "s" else ""}",
                50f,
                100f,
                paint
            )
        }
        */
    }

    /**
     * Draw demo hand for testing purposes
     */
    private fun drawDemoHand(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f

        // Draw simple hand shape for demo
        // Palm
        canvas.drawCircle(centerX, centerY, 40f, boundingBoxPaint)

        // Fingers (simplified)
        val fingerLength = 60f
        val fingerSpacing = 30f

        // Thumb
        canvas.drawLine(centerX - 40f, centerY, centerX - 60f, centerY - 20f, connectionPaint)
        canvas.drawCircle(centerX - 60f, centerY - 20f, 8f, landmarkPaint)

        // Index finger
        canvas.drawLine(centerX - 20f, centerY - 40f, centerX - 20f, centerY - 40f - fingerLength, connectionPaint)
        canvas.drawCircle(centerX - 20f, centerY - 40f - fingerLength, 8f, landmarkPaint)

        // Middle finger
        canvas.drawLine(centerX, centerY - 40f, centerX, centerY - 40f - fingerLength, connectionPaint)
        canvas.drawCircle(centerX, centerY - 40f - fingerLength, 8f, landmarkPaint)

        // Ring finger
        canvas.drawLine(centerX + 20f, centerY - 40f, centerX + 20f, centerY - 40f - fingerLength + 10f, connectionPaint)
        canvas.drawCircle(centerX + 20f, centerY - 40f - fingerLength + 10f, 8f, landmarkPaint)

        // Pinky
        canvas.drawLine(centerX + 40f, centerY - 35f, centerX + 40f, centerY - 35f - fingerLength + 20f, connectionPaint)
        canvas.drawCircle(centerX + 40f, centerY - 35f - fingerLength + 20f, 8f, landmarkPaint)

        // Draw demo text
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 32f
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawText("DEMO MODE", centerX - 80f, 100f, paint)
        canvas.drawText("Hand Tracking Ready", centerX - 100f, height - 100f, paint)
    }
}