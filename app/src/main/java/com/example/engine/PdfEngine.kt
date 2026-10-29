package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class PageSizeFormat(val title: String, val widthPt: Int, val heightPt: Int) {
    A4("A4 (595 x 842 pt)", 595, 842),
    LETTER("US Letter (612 x 792 pt)", 612, 792),
    AUTO_FIT("Fit to Image Size", 0, 0)
}

data class PdfExportConfig(
    val pageSize: PageSizeFormat = PageSizeFormat.A4,
    val qualityPercent: Int = 90,
    val addPageNumbers: Boolean = true,
    val watermarkText: String? = null,
    val watermarkOpacity: Float = 0.25f,
    val signatureBitmap: Bitmap? = null,
    val signaturePage: Int = 1 // 1-indexed
)

object PdfEngine {

    /**
     * Converts a list of image paths to a high quality multi-page PDF.
     */
    suspend fun createPdfFromImages(
        context: Context,
        imagePaths: List<String>,
        outputFileName: String,
        config: PdfExportConfig = PdfExportConfig()
    ): File = withContext(Dispatchers.IO) {
        val pdfDir = File(context.filesDir, "pdfs")
        if (!pdfDir.exists()) pdfDir.mkdirs()

        val safeName = outputFileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val finalName = if (safeName.endsWith(".pdf", ignoreCase = true)) safeName else "$safeName.pdf"
        val pdfFile = File(pdfDir, finalName)

        val document = PdfDocument()

        try {
            val totalPages = imagePaths.size

            imagePaths.forEachIndexed { index, path ->
                val bitmap = BitmapFactory.decodeFile(path) ?: return@forEachIndexed

                val pageW: Int
                val pageH: Int

                if (config.pageSize == PageSizeFormat.AUTO_FIT) {
                    pageW = bitmap.width.coerceIn(300, 1800)
                    pageH = bitmap.height.coerceIn(400, 2400)
                } else {
                    pageW = config.pageSize.widthPt
                    pageH = config.pageSize.heightPt
                }

                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                // Background
                canvas.drawColor(Color.WHITE)

                // Scale image to fit within margins
                val margin = if (config.pageSize == PageSizeFormat.AUTO_FIT) 0 else 24
                val availW = pageW - (margin * 2)
                val availH = pageH - (margin * 2) - if (config.addPageNumbers) 30 else 0

                val imgRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val pageRatio = availW.toFloat() / availH.toFloat()

                val drawW: Float
                val drawH: Float
                if (imgRatio > pageRatio) {
                    drawW = availW.toFloat()
                    drawH = drawW / imgRatio
                } else {
                    drawH = availH.toFloat()
                    drawW = drawH * imgRatio
                }

                val left = margin + (availW - drawW) / 2f
                val top = margin + (availH - drawH) / 2f

                val dstRect = Rect(left.toInt(), top.toInt(), (left + drawW).toInt(), (top + drawH).toInt())
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(bitmap, null, dstRect, paint)

                // Optional Watermark
                config.watermarkText?.let { wmText ->
                    if (wmText.isNotBlank()) {
                        drawWatermark(canvas, pageW, pageH, wmText, config.watermarkOpacity)
                    }
                }

                // Optional Signature on specified page
                if (config.signatureBitmap != null && (index + 1) == config.signaturePage) {
                    val sigW = 140
                    val sigH = (sigW * (config.signatureBitmap.height.toFloat() / config.signatureBitmap.width.toFloat())).toInt()
                    val sigLeft = pageW - margin - sigW - 20
                    val sigTop = pageH - margin - sigH - 40
                    canvas.drawBitmap(
                        config.signatureBitmap,
                        null,
                        Rect(sigLeft, sigTop, sigLeft + sigW, sigTop + sigH),
                        paint
                    )
                }

                // Page numbering
                if (config.addPageNumbers) {
                    val pageText = "Page ${index + 1} of $totalPages"
                    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.DKGRAY
                        textSize = 10f
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText(pageText, pageW / 2f, pageH - 14f, textPaint)
                }

                document.finishPage(page)
                bitmap.recycle()
            }

            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }
        } finally {
            document.close()
        }

        pdfFile
    }

    private fun drawWatermark(
        canvas: Canvas,
        pageW: Int,
        pageH: Int,
        text: String,
        opacity: Float
    ) {
        canvas.save()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            alpha = (opacity.coerceIn(0.05f, 0.8f) * 255).toInt()
            textSize = (pageW * 0.08f).coerceIn(24f, 64f)
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        canvas.rotate(-45f, pageW / 2f, pageH / 2f)
        canvas.drawText(text.uppercase(), pageW / 2f, pageH / 2f, textPaint)
        canvas.restore()
    }

    /**
     * Renders pages of an existing PDF into a list of Bitmaps (PDF to Images).
     */
    suspend fun renderPdfPagesToBitmaps(
        context: Context,
        pdfUri: Uri,
        maxPages: Int = 10
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: return@withContext emptyList()
            val renderer = PdfRenderer(pfd)

            val count = minOf(renderer.pageCount, maxPages)
            for (i in 0 until count) {
                val page = renderer.openPage(i)
                val width = page.width * 2
                val height = page.height * 2
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
                page.close()
            }
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        bitmaps
    }

    /**
     * Merges multiple PDF files into one combined PDF file.
     */
    suspend fun mergePdfFiles(
        context: Context,
        pdfUris: List<Uri>,
        outputName: String
    ): File = withContext(Dispatchers.IO) {
        val allBitmaps = mutableListOf<Bitmap>()
        for (uri in pdfUris) {
            val pages = renderPdfPagesToBitmaps(context, uri, maxPages = 20)
            allBitmaps.addAll(pages)
        }

        val tempImagePaths = mutableListOf<String>()
        for (b in allBitmaps) {
            val path = ImageProcessingEngine.saveBitmapToFile(context, b)
            tempImagePaths.add(path)
        }

        createPdfFromImages(
            context = context,
            imagePaths = tempImagePaths,
            outputFileName = outputName,
            config = PdfExportConfig(pageSize = PageSizeFormat.A4)
        )
    }

    /**
     * Splits a PDF file into separate pages saved as individual PDFs.
     */
    suspend fun splitPdfToFiles(
        context: Context,
        pdfUri: Uri,
        baseName: String
    ): List<File> = withContext(Dispatchers.IO) {
        val bitmaps = renderPdfPagesToBitmaps(context, pdfUri, maxPages = 30)
        val files = mutableListOf<File>()

        bitmaps.forEachIndexed { index, bitmap ->
            val imgPath = ImageProcessingEngine.saveBitmapToFile(context, bitmap)
            val file = createPdfFromImages(
                context = context,
                imagePaths = listOf(imgPath),
                outputFileName = "${baseName}_page_${index + 1}.pdf",
                config = PdfExportConfig(pageSize = PageSizeFormat.A4)
            )
            files.add(file)
        }
        files
    }
}
