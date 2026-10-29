package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.FilterType
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.components.SignaturePadDialog
import com.example.ui.components.WatermarkDialog
import com.example.ui.viewmodel.DocumentDetailViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    viewModel: DocumentDetailViewModel,
    onBack: () -> Unit,
    onOpenOcr: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val doc by viewModel.document.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val selectedPageIndex by viewModel.selectedPageIndex.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()
    val isOcrRunning by viewModel.isOcrRunning.collectAsState()
    val isExportingPdf by viewModel.isExportingPdf.collectAsState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showFolderMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showPdfConfigSheet by remember { mutableStateOf(false) }

    val currentPage = pages.getOrNull(selectedPageIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = doc?.title ?: "Document",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${pages.size} ${if (pages.size == 1) "Page" else "Pages"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { showRenameDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename")
                }
                IconButton(onClick = { viewModel.toggleFavorite() }) {
                    Icon(
                        imageVector = if (doc?.isFavorite == true) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (doc?.isFavorite == true) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
                    )
                }
                Box {
                    IconButton(onClick = { showFolderMenu = true }) {
                        Icon(Icons.Default.Folder, contentDescription = "Folder")
                    }
                    DropdownMenu(
                        expanded = showFolderMenu,
                        onDismissRequest = { showFolderMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("No Folder (Root)") },
                            onClick = {
                                viewModel.assignFolder(null)
                                showFolderMenu = false
                            }
                        )
                        allFolders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text(folder.name) },
                                onClick = {
                                    viewModel.assignFolder(folder.id)
                                    showFolderMenu = false
                                }
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Main Page Preview (Center stage)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (currentPage != null && File(currentPage.processedImagePath).exists()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = File(currentPage.processedImagePath),
                            contentDescription = "Page ${selectedPageIndex + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                        )

                        // Page index badge
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Page ${selectedPageIndex + 1} of ${pages.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        // Filter badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = currentPage.filterType.replace("_", " "),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            } else {
                CircularProgressIndicator()
            }
        }

        // Page Actions Toolbar (Rotate, Filter, Sign, Watermark)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.rotateCurrentPage() }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(22.dp))
                        Text("Rotate", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Box {
                    IconButton(onClick = { showFilterMenu = true }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter", modifier = Modifier.size(22.dp))
                            Text("Filter", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                        FilterType.values().forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.displayName) },
                                onClick = {
                                    viewModel.applyFilterToCurrentPage(filter)
                                    showFilterMenu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Magic Color (All Pages)") },
                            onClick = {
                                viewModel.applyFilterToAllPages(FilterType.MAGIC_COLOR)
                                showFilterMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("B&W Clean (All Pages)") },
                            onClick = {
                                viewModel.applyFilterToAllPages(FilterType.BLACK_WHITE)
                                showFilterMenu = false
                            }
                        )
                    }
                }

                IconButton(onClick = { showSignatureDialog = true }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Draw, contentDescription = "Sign", modifier = Modifier.size(22.dp))
                        Text("Sign", style = MaterialTheme.typography.labelSmall)
                    }
                }

                IconButton(onClick = { showWatermarkDialog = true }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.TextFields, contentDescription = "Watermark", modifier = Modifier.size(22.dp))
                        Text("Watermark", style = MaterialTheme.typography.labelSmall)
                    }
                }

                IconButton(onClick = {
                    viewModel.runOcr()
                    onOpenOcr()
                }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isOcrRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.TextFields, contentDescription = "OCR", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }
                        Text("OCR Text", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Thumbnails Strip
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(pages) { idx, page ->
                    val isSelected = idx == selectedPageIndex
                    Box(
                        modifier = Modifier
                            .size(width = 54.dp, height = 72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 2.5.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.selectPage(idx) }
                    ) {
                        AsyncImage(
                            model = File(page.processedImagePath),
                            contentDescription = "Thumbnail ${idx + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .size(16.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${idx + 1}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // Primary Action Bar (Export PDF & Share)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.sharePdf(context) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("share_pdf_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }

                Button(
                    onClick = {
                        viewModel.exportPdf {
                            showPdfConfigSheet = true
                        }
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("export_pdf_button")
                ) {
                    if (isExportingPdf) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export PDF")
                    }
                }
            }
        }
    }

    // Modal Dialogs
    if (showRenameDialog) {
        RenameDocumentDialog(
            currentTitle = doc?.title ?: "",
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle ->
                viewModel.renameDocument(newTitle)
                showRenameDialog = false
            }
        )
    }

    if (showWatermarkDialog) {
        WatermarkDialog(
            initialText = viewModel.watermarkText.value ?: "CONFIDENTIAL",
            onDismiss = { showWatermarkDialog = false },
            onConfirm = { text, opacity ->
                viewModel.watermarkText.value = text
                viewModel.watermarkOpacity.value = opacity
                showWatermarkDialog = false
            }
        )
    }

    if (showSignatureDialog) {
        SignaturePadDialog(
            onDismiss = { showSignatureDialog = false },
            onSignatureCaptured = { sigBitmap ->
                viewModel.signatureBitmap.value = sigBitmap
                viewModel.signaturePage.value = selectedPageIndex + 1
                showSignatureDialog = false
            }
        )
    }

    // Export PDF Result & Settings Sheet
    if (showPdfConfigSheet) {
        ModalBottomSheet(onDismissRequest = { showPdfConfigSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "PDF Ready",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "File saved: ${doc?.title}.pdf\nPages: ${pages.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Include Page Numbers", style = MaterialTheme.typography.bodyMedium)
                    val includeNums by viewModel.includePageNumbers.collectAsState()
                    Switch(
                        checked = includeNums,
                        onCheckedChange = { viewModel.includePageNumbers.value = it }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        showPdfConfigSheet = false
                        viewModel.sharePdf(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share / Save to Files")
                }
            }
        }
    }
}
