package com.formula2latex.data.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class InkPoint(val x: Float, val y: Float)
data class InkStroke(val points: List<InkPoint>, val width: Float, val eraser: Boolean = false)
data class PixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

object DrawingExporter {
    fun export(
        strokes: List<InkStroke>,
        canvasWidth: Float,
        canvasHeight: Float,
        minimumLongSide: Int = 1800,
    ): ByteArray {
        require(canvasWidth > 0 && canvasHeight > 0) { "The drawing canvas is not ready." }
        require(strokes.any { !it.eraser && it.points.isNotEmpty() }) { "Draw a formula before converting." }
        val scale = max(2f, minimumLongSide / max(canvasWidth, canvasHeight))
        val width = (canvasWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (canvasHeight * scale).roundToInt().coerceAtLeast(1)
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            paint.color = if (stroke.eraser) Color.WHITE else Color.BLACK
            paint.strokeWidth = stroke.width * scale
            canvas.drawPath(strokePath(stroke.points, scale), paint)
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val bounds = findInkBounds(pixels, width, height)
            ?: throw IllegalArgumentException("The drawing is blank.")
        val padding = (max(width, height) * 0.04f).roundToInt().coerceAtLeast(24)
        val left = (bounds.left - padding).coerceAtLeast(0)
        val top = (bounds.top - padding).coerceAtLeast(0)
        val right = (bounds.right + padding).coerceAtMost(width - 1)
        val bottom = (bounds.bottom + padding).coerceAtMost(height - 1)
        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
        return ByteArrayOutputStream().use {
            check(cropped.compress(Bitmap.CompressFormat.PNG, 100, it))
            it.toByteArray()
        }.also {
            cropped.recycle()
            bitmap.recycle()
        }
    }

    fun findInkBounds(pixels: IntArray, width: Int, height: Int, whiteThreshold: Int = 245): PixelBounds? {
        require(pixels.size >= width * height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = pixels[y * width + x]
                val red = (color ushr 16) and 0xff
                val green = (color ushr 8) and 0xff
                val blue = color and 0xff
                if (red < whiteThreshold || green < whiteThreshold || blue < whiteThreshold) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right >= left && bottom >= top) PixelBounds(left, top, right, bottom) else null
    }

    private fun strokePath(points: List<InkPoint>, scale: Float): Path = Path().apply {
        val first = points.first()
        moveTo(first.x * scale, first.y * scale)
        if (points.size == 1) {
            lineTo(first.x * scale + 0.01f, first.y * scale + 0.01f)
        } else {
            for (index in 1 until points.lastIndex) {
                val point = points[index]
                val next = points[index + 1]
                quadTo(point.x * scale, point.y * scale, (point.x + next.x) * scale / 2f, (point.y + next.y) * scale / 2f)
            }
            val last = points.last()
            lineTo(last.x * scale, last.y * scale)
        }
    }
}
