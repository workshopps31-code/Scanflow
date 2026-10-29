package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.DocumentEntity
import com.example.ui.components.CreateFolderDialog
import com.example.ui.components.DocumentCard
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.viewmodel.DocTab
import com.example.ui.viewmodel.DocumentListViewModel
import com.example.ui.viewmodel.SortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DocumentListViewModel,
    onOpenDocument: (String) -> Unit,
    onStartScan: () -> Unit,
    onOpenPdfTools: () -> Unit
) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()
    val selectedFolderId by viewModel.selectedFolderId.collectAsState()
    val selectedIds by viewModel.selectedDocumentIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val activeCount by viewModel.activeCount.collectAsState()
    val totalBytes by viewModel.totalStorageBytes.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var docToRename by remember { mutableStateOf<DocumentEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            if (!isSelectionMode && currentTab != DocTab.TRASH) {
                ExtendedFloatingActionButton(
                    onClick = onStartScan,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Scan") },
                    text = { Text("Scan Document", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("fab_scan_document")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header with Brand and View Controls
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (isSelectionMode) {
                        // Multi-select header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.clearSelection() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear selection")
                                }
                                Text(
                                    text = "${selectedIds.size} selected",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row {
                                TextButton(onClick = { viewModel.selectAll() }) {
                                    Text("Select All")
                                }
                                if (currentTab == DocTab.TRASH) {
                                    IconButton(onClick = { viewModel.batchDeletePermanently() }) {
                                        Icon(Icons.Default.DeleteForever, contentDescription = "Delete Permanently", tint = MaterialTheme.colorScheme.error)
                                    }
                                } else {
                                    IconButton(onClick = { viewModel.batchMoveToTrash() }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Move to Trash")
                                    }
                                }
                            }
                        }
                    } else {
                        // Regular Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.DocumentScanner,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "DocuLens",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "$activeCount ${if (activeCount == 1) "doc" else "docs"} • ${(totalBytes ?: 0L) / (1024 * 1024)} MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.toggleViewMode() }) {
                                    Icon(
                                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                        contentDescription = "Toggle Grid/List"
                                    )
                                }

                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        SortOption.values().forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option.title) },
                                                onClick = {
                                                    viewModel.sortOption.value = option
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            placeholder = { Text("Search title, tags, or OCR text...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("search_document_input")
                        )
                    }
                }
            }

            // Tabs Row (All Docs, Folders, Favorites, Trash)
            PrimaryTabRow(
                selectedTabIndex = currentTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                DocTab.values().forEach { tab ->
                    Tab(
                        selected = currentTab == tab,
                        onClick = { viewModel.toggleTab(tab) },
                        text = {
                            Text(
                                text = when (tab) {
                                    DocTab.ALL -> "All"
                                    DocTab.FOLDERS -> "Folders"
                                    DocTab.FAVORITES -> "Favorites"
                                    DocTab.TRASH -> "Trash"
                                },
                                fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Folders sub-bar if FOLDERS tab selected
            if (currentTab == DocTab.FOLDERS) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFolderId == null,
                                onClick = { viewModel.selectFolder(null) },
                                label = { Text("All Folders") }
                            )
                        }
                        items(allFolders) { folder ->
                            FilterChip(
                                selected = selectedFolderId == folder.id,
                                onClick = { viewModel.selectFolder(folder.id) },
                                label = { Text(folder.name) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color(android.graphics.Color.parseColor(folder.colorHex)),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                        item {
                            FilterChip(
                                selected = false,
                                onClick = { showCreateFolderDialog = true },
                                label = { Text("+ New") },
                                leadingIcon = {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }

            // Trash Controls bar if TRASH tab selected
            if (currentTab == DocTab.TRASH && documents.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${documents.size} items in trash",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = { viewModel.emptyTrash() }) {
                            Text("Empty Trash", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Documents List or Empty State
            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = when (currentTab) {
                                DocTab.TRASH -> Icons.Default.Delete
                                DocTab.FAVORITES -> Icons.Default.Folder
                                else -> Icons.Default.DocumentScanner
                            },
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when (currentTab) {
                                DocTab.TRASH -> "Trash is empty"
                                DocTab.FAVORITES -> "No favorite documents yet"
                                DocTab.FOLDERS -> "No documents in this folder"
                                else -> if (searchQuery.isNotEmpty()) "No matching documents" else "No documents scanned yet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try searching for a different keyword or file name."
                            else "Tap the Scan button to capture pages, receipts, book notes, or ID cards.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (currentTab != DocTab.TRASH && searchQuery.isEmpty()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(onClick = onStartScan) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start First Scan")
                            }
                        }
                    }
                }
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(documents, key = { it.id }) { doc ->
                            DocumentCard(
                                document = doc,
                                isGrid = true,
                                isSelected = selectedIds.contains(doc.id),
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (currentTab == DocTab.TRASH) {
                                        viewModel.restoreFromTrash(doc.id)
                                        Toast.makeText(context, "Restored '${doc.title}'", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onOpenDocument(doc.id)
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(doc.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(doc.id) },
                                onRename = { docToRename = doc },
                                onShare = { viewModel.shareDocument(context, doc.id) },
                                onMoveToTrash = {
                                    if (currentTab == DocTab.TRASH) {
                                        viewModel.deletePermanently(doc.id)
                                    } else {
                                        viewModel.moveToTrash(doc.id)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(documents, key = { it.id }) { doc ->
                            DocumentCard(
                                document = doc,
                                isGrid = false,
                                isSelected = selectedIds.contains(doc.id),
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (currentTab == DocTab.TRASH) {
                                        viewModel.restoreFromTrash(doc.id)
                                        Toast.makeText(context, "Restored '${doc.title}'", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onOpenDocument(doc.id)
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(doc.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(doc.id) },
                                onRename = { docToRename = doc },
                                onShare = { viewModel.shareDocument(context, doc.id) },
                                onMoveToTrash = {
                                    if (currentTab == DocTab.TRASH) {
                                        viewModel.deletePermanently(doc.id)
                                    } else {
                                        viewModel.moveToTrash(doc.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create Folder Dialog
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name, color ->
                viewModel.createFolder(name, color)
                showCreateFolderDialog = false
            }
        )
    }

    // Rename Dialog
    docToRename?.let { doc ->
        RenameDocumentDialog(
            currentTitle = doc.title,
            onDismiss = { docToRename = null },
            onConfirm = { newTitle ->
                viewModel.renameDocument(doc.id, newTitle)
                docToRename = null
            }
        )
    }
}
