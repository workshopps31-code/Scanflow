package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.model.DocumentEntity
import com.example.data.model.DocumentPageEntity
import com.example.data.model.FilterType
import com.example.data.model.FolderEntity
import com.example.data.repository.DocumentRepository
import com.example.engine.OcrEngine
import com.example.engine.OcrLanguage
import com.example.engine.PdfEngine
import com.example.engine.PdfExportConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class DocumentDetailViewModel(
    application: Application,
    val documentId: String
) : AndroidViewModel(application) {
    private val repository = DocumentRepository(
        context = application.applicationContext,
        database = AppDatabase.getInstance(application.applicationContext)
    )

    val document = MutableStateFlow<DocumentEntity?>(null)
    val pages: StateFlow<List<DocumentPageEntity>> = repository.getPagesForDocument(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFolders: StateFlow<List<FolderEntity>> = repository.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedPageIndex = MutableStateFlow(0)
    val isOcrRunning = MutableStateFlow(false)
    val ocrResultText = MutableStateFlow<String?>(null)
    val selectedOcrLanguage = MutableStateFlow(OcrLanguage.AUTO)

    val isExportingPdf = MutableStateFlow(false)
    val exportedPdfFile = MutableStateFlow<File?>(null)

    // PDF Customization state
    val watermarkText = MutableStateFlow<String?>(null)
    val watermarkOpacity = MutableStateFlow(0.25f)
    val signatureBitmap = MutableStateFlow<Bitmap?>(null)
    val signaturePage = MutableStateFlow(1)
    val includePageNumbers = MutableStateFlow(true)

    init {
        loadDocument()
    }

    fun loadDocument() {
        viewModelScope.launch {
            val doc = repository.getDocument(documentId)
            document.value = doc
            if (doc != null && doc.extractedText.isNotBlank()) {
                ocrResultText.value = doc.extractedText
            }
        }
    }

    fun selectPage(index: Int) {
        selectedPageIndex.value = index
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            repository.toggleFavorite(documentId)
            loadDocument()
        }
    }

    fun renameDocument(newTitle: String) {
        viewModelScope.launch {
            repository.updateDocumentTitle(documentId, newTitle)
            loadDocument()
        }
    }

    fun assignFolder(folderId: String?) {
        viewModelScope.launch {
            repository.setDocumentFolder(documentId, folderId)
            loadDocument()
        }
    }

    fun rotateCurrentPage() {
        val currentPages = pages.value
        val idx = selectedPageIndex.value
        if (idx !in currentPages.indices) return

        val page = currentPages[idx]
        val newRotation = (page.rotationDegrees + 90) % 360
        val filter = try {
            FilterType.valueOf(page.filterType)
        } catch (e: Exception) {
            FilterType.MAGIC_COLOR
        }

        viewModelScope.launch {
            repository.updatePageEnhancement(
                pageId = page.id,
                filterType = filter,
                brightness = page.brightness,
                contrast = page.contrast,
                exposure = page.exposure,
                rotationDegrees = newRotation
            )
            loadDocument()
        }
    }

    fun applyFilterToCurrentPage(filterType: FilterType) {
        val currentPages = pages.value
        val idx = selectedPageIndex.value
        if (idx !in currentPages.indices) return
        val page = currentPages[idx]

        viewModelScope.launch {
            repository.updatePageEnhancement(
                pageId = page.id,
                filterType = filterType,
                brightness = page.brightness,
                contrast = page.contrast,
                exposure = page.exposure,
                rotationDegrees = page.rotationDegrees
            )
            loadDocument()
        }
    }

    fun applyFilterToAllPages(filterType: FilterType) {
        val currentPages = pages.value
        viewModelScope.launch {
            currentPages.forEach { page ->
                repository.updatePageEnhancement(
                    pageId = page.id,
                    filterType = filterType,
                    brightness = page.brightness,
                    contrast = page.contrast,
                    exposure = page.exposure,
                    rotationDegrees = page.rotationDegrees
                )
            }
            loadDocument()
        }
    }

    fun runOcr(language: OcrLanguage = selectedOcrLanguage.value) {
        selectedOcrLanguage.value = language
        viewModelScope.launch {
            isOcrRunning.value = true
            val text = repository.runOcrOnDocument(documentId, language)
            ocrResultText.value = text
            isOcrRunning.value = false
            loadDocument()
        }
    }

    fun updateOcrText(newText: String) {
        ocrResultText.value = newText
        viewModelScope.launch {
            val doc = document.value ?: return@launch
            val db = AppDatabase.getInstance(getApplication<Application>().applicationContext)
            db.documentDao().updateDocument(doc.copy(extractedText = newText, updatedAt = System.currentTimeMillis()))
            loadDocument()
        }
    }

    fun exportPdf(onDone: (File) -> Unit) {
        viewModelScope.launch {
            isExportingPdf.value = true
            val config = PdfExportConfig(
                watermarkText = watermarkText.value,
                watermarkOpacity = watermarkOpacity.value,
                signatureBitmap = signatureBitmap.value,
                signaturePage = signaturePage.value,
                addPageNumbers = includePageNumbers.value
            )
            val file = repository.exportDocumentToPdf(documentId, config)
            exportedPdfFile.value = file
            isExportingPdf.value = false
            onDone(file)
        }
    }

    fun sharePdf(context: Context) {
        exportPdf { file ->
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, document.value?.title ?: "Scanned Document")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PDF"))
        }
    }

    fun sharePageAsImage(context: Context) {
        val currentPages = pages.value
        val idx = selectedPageIndex.value
        if (idx !in currentPages.indices) return
        val page = currentPages[idx]
        val imgFile = File(page.processedImagePath)
        if (!imgFile.exists()) return

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imgFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${document.value?.title ?: "Scan"}_Page_${idx + 1}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Image"))
    }
}
