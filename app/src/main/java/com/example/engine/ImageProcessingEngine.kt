package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import com.example.data.model.CropQuad
import com.example.data.model.FilterType
import com.example.data.model.PointFNorm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object ImageProcessingEngine {

    /**
     * Loads a downsampled or full bitmap from file or content uri safely.
     */
    suspend fun loadBitmap(context: Context, path: String, maxDimension: Int = 2048): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                if (path.startsWith("content://")) {
                    val uri = Uri.parse(path)
                    val input: InputStream? = context.contentResolver.openInputStream(uri)
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(input, null, options)
                    input?.close()

                    var sample = 1
                    while (options.outWidth / sample > maxDimension || options.outHeight / sample > maxDimension) {
                        sample *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
                    val readInput = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(readInput, null, decodeOptions)
                    readInput?.close()
                    bitmap
                } else {
                    val file = File(path)
                    if (!file.exists()) return@withContext null

                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, options)

                    var sample = 1
                    while (options.outWidth / sample > maxDimension || options.outHeight / sample > maxDimension) {
                        sample *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * Detects document boundaries on the bitmap and returns 4 normalized corner points.
     */
    suspend fun detectDocumentCorners(bitmap: Bitmap): CropQuad = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height

        // Downsample for fast analysis
        val scale = 200f / max(width, height)
        val sw = (width * scale).toInt().coerceAtLeast(40)
        val sh = (height * scale).toInt().coerceAtLeast(40)

        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, false)
        val pixels = IntArray(sw * sh)
        scaled.getPixels(pixels, 0, sw, 0, 0, sw, sh)

        // Compute luminance and edge gradient
        val lum = FloatArray(sw * sh)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // Find candidate bounding bounds by scanning high-contrast transitions
        var minX = sw * 0.08f
        var maxX = sw * 0.92f
        var minY = sh * 0.08f
        var maxY = sh * 0.92f

        // Analyze horizontal slices for paper edges
        var foundTop = false
        for (y in (sh * 0.05f).toInt()..(sh * 0.4f).toInt()) {
            var rowGrad = 0f
            for (x in (sw * 0.2f).toInt()..(sw * 0.8f).toInt()) {
                val idx = y * sw + x
                val prev = (y - 1) * sw + x
                if (prev >= 0) {
                    rowGrad += kotlin.math.abs(lum[idx] - lum[prev])
                }
            }
            if (rowGrad / (sw * 0.6f) > 22f) {
                minY = y.toFloat()
                foundTop = true
                break
            }
        }

        var foundBottom = false
        for (y in (sh * 0.95f).toInt() downTo (sh * 0.6f).toInt()) {
            var rowGrad = 0f
            for (x in (sw * 0.2f).toInt()..(sw * 0.8f).toInt()) {
                val idx = y * sw + x
                val next = (y + 1) * sw + x
                if (next < lum.size) {
                    rowGrad += kotlin.math.abs(lum[idx] - lum[next])
                }
            }
            if (rowGrad / (sw * 0.6f) > 22f) {
                maxY = y.toFloat()
                foundBottom = true
                break
            }
        }

        // Return normalized points with slight inset protection
        val normTL = PointFNorm((minX / sw).coerceIn(0.04f, 0.4f), (minY / sh).coerceIn(0.04f, 0.4f))
        val normTR = PointFNorm((maxX / sw).coerceIn(0.6f, 0.96f), (minY / sh).coerceIn(0.04f, 0.4f))
        val normBR = PointFNorm((maxX / sw).coerceIn(0.6f, 0.96f), (maxY / sh).coerceIn(0.6f, 0.96f))
        val normBL = PointFNorm((minX / sw).coerceIn(0.04f, 0.4f), (maxY / sh).coerceIn(0.6f, 0.96f))

        CropQuad(normTL, normTR, normBR, normBL)
    }

    /**
     * Applies perspective warp from the 4 polygon corners to a flat rectangular bitmap.
     */
    suspend fun warpPerspective(
        srcBitmap: Bitmap,
        quad: CropQuad
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = srcBitmap.width.toFloat()
        val h = srcBitmap.height.toFloat()

        val tlX = quad.topLeft.x * w
        val tlY = quad.topLeft.y * h
        val trX = quad.topRight.x * w
        val trY = quad.topRight.y * h
        val brX = quad.bottomRight.x * w
        val brY = quad.bottomRight.y * h
        val blX = quad.bottomLeft.x * w
        val blY = quad.bottomLeft.y * h

        // Determine destination dimensions based on edge lengths
        val topWidth = hypot(trX - tlX, trY - tlY)
        val bottomWidth = hypot(brX - blX, brY - blY)
        val leftHeight = hypot(blX - tlX, blY - tlY)
        val rightHeight = hypot(brX - trX, brY - trY)

        val targetWidth = max(topWidth, bottomWidth).toInt().coerceIn(300, 3000)
        val targetHeight = max(leftHeight, rightHeight).toInt().coerceIn(400, 4000)

        val srcPoints = floatArrayOf(
            tlX, tlY,
            trX, trY,
            brX, brY,
            blX, blY
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val warped = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(warped)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(srcBitmap, matrix, paint)

        warped
    }

    /**
     * Applies rotation in 90-degree increments.
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Applies filters and adjustment controls (Brightness, Contrast, Sharpness, Exposure)
     */
    suspend fun applyEnhancements(
        source: Bitmap,
        filterType: FilterType,
        brightness: Float = 0f, // -100 to +100
        contrast: Float = 1.0f, // 0.5 to 2.0
        exposure: Float = 0f,   // -100 to +100
        sharpness: Float = 0f
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height

        when (filterType) {
            FilterType.ORIGINAL -> {
                if (brightness == 0f && contrast == 1.0f && exposure == 0f) {
                    source.copy(Bitmap.Config.ARGB_8888, true)
                } else {
                    applyColorMatrixAdjustments(source, brightness, contrast, exposure)
                }
            }

            FilterType.GRAYSCALE -> {
                val grayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(grayscale)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

                val cm = ColorMatrix().apply {
                    setSaturation(0f)
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)

                applyColorMatrixAdjustments(grayscale, brightness, contrast, exposure)
            }

            FilterType.MAGIC_COLOR -> {
                // Magic Color algorithm:
                // 1. Boost white level to clear shadow backgrounds
                // 2. Enhance dark ink contrast
                // 3. Keep color saturation vibrant
                val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

                // High contrast color matrix with background lighting boost
                val magicContrast = 1.35f * contrast
                val magicTranslate = 18f + brightness + (exposure * 0.4f)
                val scale = magicContrast

                val magicMatrix = floatArrayOf(
                    scale, 0f, 0f, 0f, magicTranslate,
                    0f, scale, 0f, 0f, magicTranslate,
                    0f, 0f, scale, 0f, magicTranslate,
                    0f, 0f, 0f, 1f, 0f
                )

                val cm = ColorMatrix(magicMatrix)
                // Boost saturation slightly for rich text/stamps
                val satMatrix = ColorMatrix()
                satMatrix.setSaturation(1.15f)
                cm.postConcat(satMatrix)

                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
                output
            }

            FilterType.AUTO_ENHANCE -> {
                // Auto Enhance: balanced clarity with shadow reduction
                val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

                val c = 1.2f * contrast
                val t = 10f + brightness + (exposure * 0.3f)

                val autoMatrix = floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
                paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(autoMatrix))
                canvas.drawBitmap(source, 0f, 0f, paint)
                output
            }

            FilterType.BLACK_WHITE -> {
                // Adaptive Binarization for crisp text documents
                val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val pixels = IntArray(width * height)
                source.getPixels(pixels, 0, width, 0, 0, width, height)

                // Calculate mean luminance for adaptive threshold
                var sumLum = 0L
                val stride = max(1, pixels.size / 2000)
                var count = 0
                for (i in pixels.indices step stride) {
                    val p = pixels[i]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    sumLum += (0.299f * r + 0.587f * g + 0.114f * b).toLong()
                    count++
                }
                val meanLum = if (count > 0) (sumLum / count).toInt() else 128
                val threshold = (meanLum * 0.92f - (brightness * 0.5f)).toInt().coerceIn(60, 210)

                for (i in pixels.indices) {
                    val p = pixels[i]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val lum = 0.299f * r + 0.587f * g + 0.114f * b
                    pixels[i] = if (lum > threshold) Color.WHITE else Color.BLACK
                }

                output.setPixels(pixels, 0, width, 0, 0, width, height)
                output
            }
        }
    }

    private fun applyColorMatrixAdjustments(
        bitmap: Bitmap,
        brightness: Float,
        contrast: Float,
        exposure: Float
    ): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val c = contrast
        val b = brightness + (exposure * 0.5f)
        val matrix = floatArrayOf(
            c, 0f, 0f, 0f, b,
            0f, c, 0f, 0f, b,
            0f, 0f, c, 0f, b,
            0f, 0f, 0f, 1f, 0f
        )
        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    /**
     * Combines Front and Back of an ID card onto a single clean card layout (A4 / Card format).
     */
    suspend fun combineIdCard(front: Bitmap, back: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val targetWidth = 1400
        val targetHeight = 1980 // A4 ratio approximately

        val combined = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)
        canvas.drawColor(Color.WHITE)

        val cardW = 1100
        val cardH = (cardW * (front.height.toFloat() / front.width.toFloat())).toInt().coerceIn(600, 850)

        val scaledFront = Bitmap.createScaledBitmap(front, cardW, cardH, true)
        val scaledBack = Bitmap.createScaledBitmap(back, cardW, cardH, true)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        // Draw Front Card
        val x = (targetWidth - cardW) / 2f
        val y1 = 120f
        canvas.drawBitmap(scaledFront, x, y1, paint)
        canvas.drawRect(x, y1, x + cardW, y1 + cardH, borderPaint)

        // Draw Back Card
        val y2 = y1 + cardH + 140f
        canvas.drawBitmap(scaledBack, x, y2, paint)
        canvas.drawRect(x, y2, x + cardW, y2 + cardH, borderPaint)

        // Label
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#757575")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("ID Card - Front Side", targetWidth / 2f, y1 - 24f, textPaint)
        canvas.drawText("ID Card - Back Side", targetWidth / 2f, y2 - 24f, textPaint)

        combined
    }

    /**
     * Saves a bitmap to internal app storage and returns the absolute path.
     */
    suspend fun saveBitmapToFile(
        context: Context,
        bitmap: Bitmap,
        quality: Int = 92,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "scans")
        if (!dir.exists()) dir.mkdirs()

        val ext = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
        val file = File(dir, "page_${UUID.randomUUID()}.$ext")
        FileOutputStream(file).use { out ->
            bitmap.compress(format, quality, out)
        }
        file.absolutePath
    }
}
