package com.formula2latex.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream

class ImagePipeline(private val context: Context) {
    fun fromUri(uri: Uri, maxSide: Int = 1800, quality: Int = 85): ByteArray {
        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected image could not be decoded." }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide * 2)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("The selected image could not be decoded.")
        return normalize(decoded, orientation, maxSide, quality)
    }

    fun fromFile(path: String, maxSide: Int = 1800, quality: Int = 85): ByteArray {
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The captured image could not be decoded." }
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide * 2)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: error("The captured image could not be decoded.")
        return normalize(decoded, orientation, maxSide, quality)
    }

    fun reduceFurther(bytes: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("The image could not be resized.")
        return normalize(bitmap, ExifInterface.ORIENTATION_NORMAL, 1024, 75)
    }

    private fun normalize(source: Bitmap, orientation: Int, maxSide: Int, quality: Int): ByteArray {
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        val oriented = if (!matrix.isIdentity) Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true) else source
        val scale = minOf(1f, maxSide.toFloat() / maxOf(oriented.width, oriented.height))
        val resized = if (scale < 1f) oriented.scale(
            (oriented.width * scale).toInt().coerceAtLeast(1),
            (oriented.height * scale).toInt().coerceAtLeast(1),
            filter = true,
        ) else oriented
        return ByteArrayOutputStream().use {
            check(resized.compress(Bitmap.CompressFormat.JPEG, quality, it))
            it.toByteArray()
        }.also {
            if (resized !== oriented) resized.recycle()
            if (oriented !== source) oriented.recycle()
            source.recycle()
        }
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
        return sample
    }
}
