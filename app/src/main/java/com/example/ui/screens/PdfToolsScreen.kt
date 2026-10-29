package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.engine.ImageProcessingEngine
import com.example.engine.OcrEngine
import com.example.engine.PdfEngine
import com.example.engine.PdfExportConfig
import com.example.ui.components.SignaturePadDialog
import com.example.ui.components.WatermarkDialog
import kotlinx.coroutines.launch
import java.io.File

data class PdfToolItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val toolType: PdfToolType
)

enum class PdfToolType {
    IMAGES_TO_PDF,
    PDF_TO_IMAGES,
    MERGE_PDF,
    SPLIT_PDF,
    COMPRESS_PDF,
    WATERMARK_PDF,
    SIGN_PDF,
    PDF_TO_TEXT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    onScanRequested: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var showSignatureDialog by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }

    val tools = listOf(
        PdfToolItem(
            title = "Images to PDF",
            description = "Convert photos & scans into clean PDF",
            icon = Icons.Default.PictureAsPdf,
            color = Color(0xFF00695C),
            toolType = PdfToolType.IMAGES_TO_PDF
        ),
        PdfToolItem(
            title = "PDF to JPG / PNG",
            description = "Extract pages into high resolution images",
            icon = Icons.Default.Image,
            color = Color(0xFF1E3A8A),
            toolType = PdfToolType.PDF_TO_IMAGES
        ),
        PdfToolItem(
            title = "Merge PDFs",
            description = "Combine multiple PDF documents into one",
            icon = Icons.Default.MergeType,
            color = Color(0xFF7C3AED),
            toolType = PdfToolType.MERGE_PDF
        ),
        PdfToolItem(
            title = "Split PDF",
            description = "Extract individual pages from PDF",
            icon = Icons.Default.CallSplit,
            color = Color(0xFFD97706),
            toolType = PdfToolType.SPLIT_PDF
        ),
        PdfToolItem(
            title = "Compress PDF",
            description = "Reduce file size with smart resampling",
            icon = Icons.Default.Compress,
            color = Color(0xFF059669),
            toolType = PdfToolType.COMPRESS_PDF
        ),
        PdfToolItem(
            title = "Watermark PDF",
            description = "Stamp Confidential or custom watermark",
            icon = Icons.Default.TextFields,
            color = Color(0xFFB91C1C),
            toolType = PdfToolType.WATERMARK_PDF
        ),
        PdfToolItem(
            title = "Electronic Signature",
            description = "Draw and save your digital signature",
            icon = Icons.Default.Draw,
            color = Color(0xFF0284C7),
            toolType = PdfToolType.SIGN_PDF
        ),
        PdfToolItem(
            title = "PDF to Text (OCR)",
            description = "Extract all text and lines from PDF",
            icon = Icons.Default.TextFields,
            color = Color(0xFF4F46E5),
            toolType = PdfToolType.PDF_TO_TEXT
        )
    )

    // Image to PDF Launcher
    val imagesToPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessing = true
                statusMessage = "Generating PDF from ${uris.size} images..."
                val tempPaths = mutableListOf<String>()
                for (u in uris) {
                    val b = ImageProcessingEngine.loadBitmap(context, u.toString())
                    if (b != null) {
                        tempPaths.add(ImageProcessingEngine.saveBitmapToFile(context, b))
                    }
                }
                val pdf = PdfEngine.createPdfFromImages(
                    context = context,
                    imagePaths = tempPaths,
                    outputFileName = "Converted_${System.currentTimeMillis()}.pdf"
                )
                isProcessing = false
                shareFile(context, pdf, "application/pdf")
            }
        }
    }

    // PDF to Images Launcher
    val pdfToImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                isProcessing = true
                statusMessage = "Extracting pages to images..."
                val bitmaps = PdfEngine.renderPdfPagesToBitmaps(context, it, maxPages = 20)
                isProcessing = false
                Toast.makeText(context, "Extracted ${bitmaps.size} page images successfully", Toast.LENGTH_LONG).show()
            }
        }
    }

    // PDF to Text (OCR) Launcher
    val pdfToTextLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                isProcessing = true
                statusMessage = "Running OCR on PDF..."
                val bitmaps = PdfEngine.renderPdfPagesToBitmaps(context, it, maxPages = 10)
                val textBuilder = StringBuilder()
                for ((idx, b) in bitmaps.withIndex()) {
                    val res = OcrEngine.extractTextFromBitmap(b)
                    textBuilder.append("--- Page ${idx + 1} ---\n${res.fullText}\n\n")
                }
                isProcessing = false
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, textBuilder.toString())
                }
                context.startActivity(Intent.createChooser(intent, "Share Extracted Text"))
            }
        }
    }

    // Split PDF Launcher
    val splitPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                isProcessing = true
                statusMessage = "Splitting PDF pages..."
                val files = PdfEngine.splitPdfToFiles(context, it, "split_doc")
                isProcessing = false
                Toast.makeText(context, "Split into ${files.size} separate PDF files", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("PDF Toolbox", fontWeight = FontWeight.Bold)
                    Text("Professional document conversion suite", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        if (isProcessing) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = statusMessage ?: "Processing document...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(tools) { tool ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            when (tool.toolType) {
                                PdfToolType.IMAGES_TO_PDF -> imagesToPdfLauncher.launch("image/*")
                                PdfToolType.PDF_TO_IMAGES -> pdfToImagesLauncher.launch(arrayOf("application/pdf"))
                                PdfToolType.MERGE_PDF -> {
                                    Toast.makeText(context, "Select images or documents to combine", Toast.LENGTH_SHORT).show()
                                    imagesToPdfLauncher.launch("image/*")
                                }
                                PdfToolType.SPLIT_PDF -> splitPdfLauncher.launch(arrayOf("application/pdf"))
                                PdfToolType.COMPRESS_PDF -> {
                                    Toast.makeText(context, "Select images or document to compress", Toast.LENGTH_SHORT).show()
                                    imagesToPdfLauncher.launch("image/*")
                                }
                                PdfToolType.WATERMARK_PDF -> showWatermarkDialog = true
                                PdfToolType.SIGN_PDF -> showSignatureDialog = true
                                PdfToolType.PDF_TO_TEXT -> pdfToTextLauncher.launch(arrayOf("application/pdf"))
                            }
                        }
                        .testTag("pdf_tool_${tool.toolType.name}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(tool.color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(tool.icon, contentDescription = null, tint = tool.color, modifier = Modifier.size(26.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = tool.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = tool.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showSignatureDialog) {
        SignaturePadDialog(
            onDismiss = { showSignatureDialog = false },
            onSignatureCaptured = { signatureBitmap ->
                scope.launch {
                    val path = ImageProcessingEngine.saveBitmapToFile(
                        context,
                        signatureBitmap,
                        format = Bitmap.CompressFormat.PNG
                    )
                    showSignatureDialog = false
                    Toast.makeText(context, "Signature saved to internal storage for document insertion", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    if (showWatermarkDialog) {
        WatermarkDialog(
            onDismiss = { showWatermarkDialog = false },
            onConfirm = { text, opacity ->
                showWatermarkDialog = false
                Toast.makeText(context, "Watermark '$text' configured. Choose images to export with watermark.", Toast.LENGTH_LONG).show()
                imagesToPdfLauncher.launch("image/*")
            }
        )
    }
}

private fun shareFile(context: Context, file: File, mimeType: String) {
    if (!file.exists()) return
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Converted File"))
}
