package com.vatsyahub.workout.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

fun shrinkAvatar(context: Context, uri: Uri): String? {
    return try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        val longest = max(decoded.width, decoded.height).coerceAtLeast(1)
        val scale = 256f / longest
        val width = (decoded.width * scale).toInt().coerceAtLeast(1)
        val height = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (width != decoded.width || height != decoded.height) {
            Bitmap.createScaledBitmap(decoded, width, height, true)
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        var quality = 70
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        while (out.size() > 32_000 && quality > 30) {
            out.reset()
            quality -= 10
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }
}
