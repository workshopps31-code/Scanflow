package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.example.data.AppDatabase
import com.example.data.model.CropQuad
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentPageEntity
import com.example.data.model.FilterType
import com.example.data.model.FolderEntity
import com.example.engine.ImageProcessingEngine
import com.example.engine.OcrEngine
import com.example.engine.OcrLanguage
import com.example.engine.PdfEngine
import com.example.engine.PdfExportConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DocumentRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val docDao = database.documentDao()
    private val pageDao = database.documentPageDao()
    private val folderDao = database.folderDao()

    val allActiveDocuments: Flow<List<DocumentEntity>> = docDao.getAllActiveDocuments()
    val favoriteDocuments: Flow<List<DocumentEntity>> = docDao.getFavoriteDocuments()
    val trashDocuments: Flow<List<DocumentEntity>> = docDao.getTrashDocuments()
    val allFolders: Flow<List<FolderEntity>> = folderDao.getAllFolders()
    val activeDocumentCount: Flow<Int> = docDao.getActiveDocumentCount()
    val totalStorageBytes: Flow<Long?> = docDao.getTotalStorageBytes()

    fun getDocumentsByFolder(folderId: String): Flow<List<DocumentEntity>> =
        docDao.getDocumentsByFolder(folderId)

    fun getPagesForDocument(documentId: String): Flow<List<DocumentPageEntity>> =
        pageDao.getPagesForDocument(documentId)

    suspend fun getDocument(id: String): DocumentEntity? = docDao.getDocumentById(id)

    suspend fun createDocumentFromPages(
        title: String? = null,
        folderId: String? = null,
        pages: List<Pair<Bitmap, CropQuad>>,
        defaultFilter: FilterType = FilterType.MAGIC_COLOR
    ): DocumentEntity = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date(timestamp))
        val finalTitle = if (!title.isNullOrBlank()) title else "Doc_$dateStr"

        val pageEntities = mutableListOf<DocumentPageEntity>()
        var totalBytes = 0L
        var firstThumbPath: String? = null

        pages.forEachIndexed { index, (origBitmap, quad) ->
            // Save original image
            val origPath = ImageProcessingEngine.saveBitmapToFile(context, origBitmap)
            val origFile = File(origPath)
            totalBytes += origFile.length()

            // Warp perspective and apply filter
            val warped = ImageProcessingEngine.warpPerspective(origBitmap, quad)
            val enhanced = ImageProcessingEngine.applyEnhancements(warped, defaultFilter)
            val procPath = ImageProcessingEngine.saveBitmapToFile(context, enhanced)
            val procFile = File(procPath)
            totalBytes += procFile.length()

            if (index == 0) {
                firstThumbPath = procPath
            }

            val pageEntity = DocumentPageEntity(
                id = UUID.randomUUID().toString(),
                documentId = docId,
                pageIndex = index,
                originalImagePath = origPath,
                processedImagePath = procPath,
                filterType = defaultFilter.name,
                tlX = quad.topLeft.x, tlY = quad.topLeft.y,
                trX = quad.topRight.x, trY = quad.topRight.y,
                brX = quad.bottomRight.x, brY = quad.bottomRight.y,
                blX = quad.bottomLeft.x, blY = quad.bottomLeft.y
            )
            pageEntities.add(pageEntity)
        }

        val docEntity = DocumentEntity(
            id = docId,
            title = finalTitle,
            createdAt = timestamp,
            updatedAt = timestamp,
            folderId = folderId,
            pageCount = pages.size,
            thumbnailPath = firstThumbPath,
            fileSizeBytes = totalBytes
        )

        docDao.insertDocument(docEntity)
        pageDao.insertPages(pageEntities)

        docEntity
    }

    suspend fun updateDocumentTitle(docId: String, newTitle: String) = withContext(Dispatchers.IO) {
        val doc = docDao.getDocumentById(docId) ?: return@withContext
        docDao.updateDocument(doc.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
    }

    suspend fun toggleFavorite(docId: String) = withContext(Dispatchers.IO) {
        val doc = docDao.getDocumentById(docId) ?: return@withContext
        docDao.updateDocument(doc.copy(isFavorite = !doc.isFavorite, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setDocumentFolder(docId: String, folderId: String?) = withContext(Dispatchers.IO) {
        val doc = docDao.getDocumentById(docId) ?: return@withContext
        docDao.updateDocument(doc.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun moveToTrash(docId: String) = withContext(Dispatchers.IO) {
        docDao.moveToTrash(docId)
    }

    suspend fun restoreFromTrash(docId: String) = withContext(Dispatchers.IO) {
        docDao.restoreFromTrash(docId)
    }

    suspend fun deletePermanently(docId: String) = withContext(Dispatchers.IO) {
        val pages = pageDao.getPagesList(docId)
        pages.forEach { p ->
            File(p.originalImagePath).delete()
            File(p.processedImagePath).delete()
        }
        val doc = docDao.getDocumentById(docId)
        doc?.pdfPath?.let { File(it).delete() }

        pageDao.deletePagesForDocument(docId)
        docDao.permanentlyDelete(docId)
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        docDao.emptyTrash()
    }

    suspend fun addFolder(name: String, colorHex: String) = withContext(Dispatchers.IO) {
        folderDao.insertFolder(FolderEntity(name = name, colorHex = colorHex))
    }

    suspend fun deleteFolder(folderId: String) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(folderId)
    }

    suspend fun updatePageEnhancement(
        pageId: String,
        filterType: FilterType,
        brightness: Float,
        contrast: Float,
        exposure: Float,
        rotationDegrees: Int
    ) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        val origBitmap = ImageProcessingEngine.loadBitmap(context, page.originalImagePath) ?: return@withContext

        val quad = CropQuad(
            topLeft = com.example.data.model.PointFNorm(page.tlX, page.tlY),
            topRight = com.example.data.model.PointFNorm(page.trX, page.trY),
            bottomRight = com.example.data.model.PointFNorm(page.brX, page.brY),
            bottomLeft = com.example.data.model.PointFNorm(page.blX, page.blY)
        )

        val warped = ImageProcessingEngine.warpPerspective(origBitmap, quad)
        val rotated = ImageProcessingEngine.rotateBitmap(warped, rotationDegrees)
        val enhanced = ImageProcessingEngine.applyEnhancements(
            source = rotated,
            filterType = filterType,
            brightness = brightness,
            contrast = contrast,
            exposure = exposure
        )

        val newProcPath = ImageProcessingEngine.saveBitmapToFile(context, enhanced)

        // Delete old processed image
        File(page.processedImagePath).delete()

        val updatedPage = page.copy(
            processedImagePath = newProcPath,
            filterType = filterType.name,
            rotationDegrees = rotationDegrees,
            brightness = brightness,
            contrast = contrast,
            exposure = exposure
        )
        pageDao.updatePage(updatedPage)

        // Update doc thumbnail if it was page 0
        if (page.pageIndex == 0) {
            val doc = docDao.getDocumentById(page.documentId)
            doc?.let { docDao.updateDocument(it.copy(thumbnailPath = newProcPath, updatedAt = System.currentTimeMillis())) }
        }
    }

    suspend fun exportDocumentToPdf(
        documentId: String,
        config: PdfExportConfig = PdfExportConfig()
    ): File = withContext(Dispatchers.IO) {
        val doc = docDao.getDocumentById(documentId) ?: throw IllegalArgumentException("Document not found")
        val pages = pageDao.getPagesList(documentId)

        val imagePaths = pages.map { it.processedImagePath }
        val pdfFile = PdfEngine.createPdfFromImages(
            context = context,
            imagePaths = imagePaths,
            outputFileName = doc.title,
            config = config
        )

        docDao.updateDocument(doc.copy(pdfPath = pdfFile.absolutePath, updatedAt = System.currentTimeMillis()))
        pdfFile
    }

    suspend fun runOcrOnDocument(documentId: String, language: OcrLanguage = OcrLanguage.AUTO): String =
        withContext(Dispatchers.IO) {
            val doc = docDao.getDocumentById(documentId) ?: return@withContext ""
            val pages = pageDao.getPagesList(documentId)

            val fullTextBuilder = StringBuilder()

            pages.forEachIndexed { index, page ->
                val bitmap = ImageProcessingEngine.loadBitmap(context, page.processedImagePath)
                if (bitmap != null) {
                    val result = OcrEngine.extractTextFromBitmap(bitmap, language)
                    val pageText = result.fullText
                    pageDao.updatePage(page.copy(ocrText = pageText))
                    fullTextBuilder.append("--- Page ${index + 1} ---\n")
                    fullTextBuilder.append(pageText)
                    fullTextBuilder.append("\n\n")
                }
            }

            val aggregatedText = fullTextBuilder.toString()
            docDao.updateDocument(doc.copy(extractedText = aggregatedText, updatedAt = System.currentTimeMillis()))
            aggregatedText
        }
}
