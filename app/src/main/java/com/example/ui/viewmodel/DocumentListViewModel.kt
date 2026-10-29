package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.model.DocumentEntity
import com.example.data.model.FolderEntity
import com.example.data.repository.DocumentRepository
import com.example.engine.PdfEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class DocTab {
    ALL, FOLDERS, FAVORITES, TRASH
}

enum class SortOption(val title: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SIZE_DESC("Largest size"),
    PAGES_DESC("Most pages")
}

class DocumentListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DocumentRepository(
        context = application.applicationContext,
        database = AppDatabase.getInstance(application.applicationContext)
    )

    val currentTab = MutableStateFlow(DocTab.ALL)
    val searchQuery = MutableStateFlow("")
    val isGridView = MutableStateFlow(true)
    val sortOption = MutableStateFlow(SortOption.DATE_DESC)
    val selectedFolderId = MutableStateFlow<String?>(null)

    val selectedDocumentIds = MutableStateFlow<Set<String>>(emptySet())
    val isSelectionMode = MutableStateFlow(false)

    val allFolders: StateFlow<List<FolderEntity>> = repository.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCount: StateFlow<Int> = repository.activeDocumentCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalStorageBytes: StateFlow<Long?> = repository.totalStorageBytes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private data class FilterState(
        val tab: DocTab,
        val folderId: String?,
        val query: String,
        val sort: SortOption
    )

    private val filterState = combine(
        currentTab,
        selectedFolderId,
        searchQuery,
        sortOption
    ) { tab, folderId, query, sort ->
        FilterState(tab, folderId, query, sort)
    }

    val documents: StateFlow<List<DocumentEntity>> = combine(
        filterState,
        repository.allActiveDocuments,
        repository.favoriteDocuments,
        repository.trashDocuments
    ) { filter, activeDocs, favDocs, trashDocs ->
        val sourceList = when (filter.tab) {
            DocTab.ALL -> activeDocs
            DocTab.FAVORITES -> favDocs
            DocTab.TRASH -> trashDocs
            DocTab.FOLDERS -> {
                if (filter.folderId != null) activeDocs.filter { it.folderId == filter.folderId }
                else activeDocs
            }
        }

        // Apply Search across title, tags, and OCR text
        val filtered = if (filter.query.isBlank()) {
            sourceList
        } else {
            val q = filter.query.trim().lowercase()
            sourceList.filter { doc ->
                doc.title.lowercase().contains(q) ||
                doc.tags.lowercase().contains(q) ||
                doc.extractedText.lowercase().contains(q)
            }
        }

        // Apply Sorting
        when (filter.sort) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.updatedAt }
            SortOption.DATE_ASC -> filtered.sortedBy { it.updatedAt }
            SortOption.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            SortOption.SIZE_DESC -> filtered.sortedByDescending { it.fileSizeBytes }
            SortOption.PAGES_DESC -> filtered.sortedByDescending { it.pageCount }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleTab(tab: DocTab) {
        currentTab.value = tab
        if (tab != DocTab.FOLDERS) selectedFolderId.value = null
        clearSelection()
    }

    fun selectFolder(folderId: String?) {
        selectedFolderId.value = folderId
    }

    fun toggleViewMode() {
        isGridView.value = !isGridView.value
    }

    fun toggleFavorite(docId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(docId)
        }
    }

    fun renameDocument(docId: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateDocumentTitle(docId, newTitle)
        }
    }

    fun moveToTrash(docId: String) {
        viewModelScope.launch {
            repository.moveToTrash(docId)
        }
    }

    fun restoreFromTrash(docId: String) {
        viewModelScope.launch {
            repository.restoreFromTrash(docId)
        }
    }

    fun deletePermanently(docId: String) {
        viewModelScope.launch {
            repository.deletePermanently(docId)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
        }
    }

    fun createFolder(name: String, colorHex: String) {
        viewModelScope.launch {
            repository.addFolder(name, colorHex)
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            if (selectedFolderId.value == folderId) {
                selectedFolderId.value = null
            }
        }
    }

    fun toggleSelection(docId: String) {
        val current = selectedDocumentIds.value.toMutableSet()
        if (current.contains(docId)) {
            current.remove(docId)
        } else {
            current.add(docId)
        }
        selectedDocumentIds.value = current
        isSelectionMode.value = current.isNotEmpty()
    }

    fun selectAll() {
        selectedDocumentIds.value = documents.value.map { it.id }.toSet()
        isSelectionMode.value = true
    }

    fun clearSelection() {
        selectedDocumentIds.value = emptySet()
        isSelectionMode.value = false
    }

    fun batchMoveToTrash() {
        val ids = selectedDocumentIds.value
        viewModelScope.launch {
            ids.forEach { repository.moveToTrash(it) }
            clearSelection()
        }
    }

    fun batchDeletePermanently() {
        val ids = selectedDocumentIds.value
        viewModelScope.launch {
            ids.forEach { repository.deletePermanently(it) }
            clearSelection()
        }
    }

    fun shareDocument(context: Context, docId: String) {
        viewModelScope.launch {
            val doc = repository.getDocument(docId) ?: return@launch
            // Export or fetch existing PDF
            val pdfFile = doc.pdfPath?.let { File(it) } ?: repository.exportDocumentToPdf(docId)
            if (pdfFile.exists()) {
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    pdfFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, doc.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share Document"))
            }
        }
    }
}
