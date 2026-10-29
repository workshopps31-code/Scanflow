package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.model.CropQuad
import com.example.data.model.DocumentEntity
import com.example.data.model.FilterType
import com.example.data.model.ScanMode
import com.example.data.repository.DocumentRepository
import com.example.engine.ImageProcessingEngine
import com.example.engine.PdfEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PageDraft(
    val bitmap: Bitmap,
    val quad: CropQuad,
    val filter: FilterType = FilterType.MAGIC_COLOR,
    val rotationDegrees: Int = 0
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DocumentRepository(
        context = application.applicationContext,
        database = AppDatabase.getInstance(application.applicationContext)
    )

    val currentMode = MutableStateFlow(ScanMode.SINGLE)
    val flashEnabled = MutableStateFlow(false)
    val isFrontCamera = MutableStateFlow(false)

    // Current session pages
    private val _capturedPages = MutableStateFlow<List<PageDraft>>(emptyList())
    val capturedPages = _capturedPages.asStateFlow()

    // ID Card state machine
    val idCardFront = MutableStateFlow<Bitmap?>(null)
    val idCardStep = MutableStateFlow(1) // 1: Front, 2: Back

    val isProcessing = MutableStateFlow(false)
    val newlyCreatedDocId = MutableStateFlow<String?>(null)

    fun setMode(mode: ScanMode) {
        currentMode.value = mode
        if (mode == ScanMode.ID_CARD) {
            idCardStep.value = 1
            idCardFront.value = null
        }
    }

    fun toggleFlash() {
        flashEnabled.value = !flashEnabled.value
    }

    fun toggleCamera() {
        isFrontCamera.value = !isFrontCamera.value
    }

    fun onImageCaptured(bitmap: Bitmap, onSingleReady: (PageDraft) -> Unit) {
        viewModelScope.launch {
            isProcessing.value = true
            val quad = ImageProcessingEngine.detectDocumentCorners(bitmap)

            when (currentMode.value) {
                ScanMode.ID_CARD -> {
                    if (idCardStep.value == 1) {
                        idCardFront.value = bitmap
                        idCardStep.value = 2
                        isProcessing.value = false
                    } else {
                        val front = idCardFront.value
                        if (front != null) {
                            val combined = ImageProcessingEngine.combineIdCard(front, bitmap)
                            val combinedQuad = CropQuad()
                            val draft = PageDraft(combined, combinedQuad, FilterType.ORIGINAL)
                            _capturedPages.value = listOf(draft)
                            onSingleReady(draft)
                        }
                        isProcessing.value = false
                    }
                }
                ScanMode.SINGLE, ScanMode.RECEIPT, ScanMode.BOOK_NOTE -> {
                    val defaultFilter = when (currentMode.value) {
                        ScanMode.RECEIPT -> FilterType.BLACK_WHITE
                        ScanMode.BOOK_NOTE -> FilterType.AUTO_ENHANCE
                        else -> FilterType.MAGIC_COLOR
                    }
                    val draft = PageDraft(bitmap, quad, defaultFilter)
                    _capturedPages.value = listOf(draft)
                    isProcessing.value = false
                    onSingleReady(draft)
                }
                ScanMode.BATCH -> {
                    val draft = PageDraft(bitmap, quad, FilterType.MAGIC_COLOR)
                    _capturedPages.value = _capturedPages.value + draft
                    isProcessing.value = false
                }
            }
        }
    }

    fun importGalleryImages(uris: List<Uri>, onDone: () -> Unit) {
        viewModelScope.launch {
            isProcessing.value = true
            val context = getApplication<Application>().applicationContext
            val drafts = mutableListOf<PageDraft>()

            for (uri in uris) {
                val bitmap = ImageProcessingEngine.loadBitmap(context, uri.toString())
                if (bitmap != null) {
                    val quad = ImageProcessingEngine.detectDocumentCorners(bitmap)
                    drafts.add(PageDraft(bitmap, quad, FilterType.MAGIC_COLOR))
                }
            }

            if (drafts.isNotEmpty()) {
                _capturedPages.value = _capturedPages.value + drafts
            }
            isProcessing.value = false
            onDone()
        }
    }

    fun importPdfFile(pdfUri: Uri, onDone: () -> Unit) {
        viewModelScope.launch {
            isProcessing.value = true
            val context = getApplication<Application>().applicationContext
            val bitmaps = PdfEngine.renderPdfPagesToBitmaps(context, pdfUri, maxPages = 25)
            val drafts = bitmaps.map { b ->
                PageDraft(b, CropQuad(), FilterType.ORIGINAL)
            }
            if (drafts.isNotEmpty()) {
                _capturedPages.value = _capturedPages.value + drafts
            }
            isProcessing.value = false
            onDone()
        }
    }

    fun removeDraftPage(index: Int) {
        val current = _capturedPages.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _capturedPages.value = current
        }
    }

    fun saveAllPagesToDocument(
        title: String? = null,
        onSuccess: (String) -> Unit
    ) {
        val pages = _capturedPages.value
        if (pages.isEmpty()) return

        viewModelScope.launch {
            isProcessing.value = true
            val pairs = pages.map { Pair(it.bitmap, it.quad) }
            val doc = repository.createDocumentFromPages(
                title = title,
                pages = pairs,
                defaultFilter = pages.firstOrNull()?.filter ?: FilterType.MAGIC_COLOR
            )
            isProcessing.value = false
            _capturedPages.value = emptyList()
            newlyCreatedDocId.value = doc.id
            onSuccess(doc.id)
        }
    }
}
