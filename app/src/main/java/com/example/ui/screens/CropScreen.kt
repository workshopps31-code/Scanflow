package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.model.CropQuad
import com.example.data.model.PointFNorm
import com.example.engine.ImageProcessingEngine
import kotlinx.coroutines.launch
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    initialBitmap: Bitmap,
    initialQuad: CropQuad,
    onBack: () -> Unit,
    onCropConfirmed: (Bitmap, CropQuad) -> Unit
) {
    BackHandler { onBack() }

    val scope = rememberCoroutineScope()
    var currentBitmap by remember { mutableStateOf(initialBitmap) }
    var quad by remember { mutableStateOf(initialQuad) }
    var isWarping by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    text = "Crop & Straighten",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            actions = {
                IconButton(onClick = {
                    val rotated = ImageProcessingEngine.rotateBitmap(currentBitmap, 90)
                    currentBitmap = rotated
                }) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "Rotate 90°",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
        )

        // Interactive Crop Canvas Area
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()

            val imgW = currentBitmap.width.toFloat()
            val imgH = currentBitmap.height.toFloat()

            // Calculate scaled image size and position within container
            val scale = minOf(containerWidth / imgW, containerHeight / imgH)
            val renderW = imgW * scale
            val renderH = imgH * scale
            val offsetX = (containerWidth - renderW) / 2f
            val offsetY = (containerHeight - renderH) / 2f

            // Draggable handles tracking
            var draggingCorner by remember { mutableStateOf<String?>(null) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentBitmap) {
                        detectDragGestures(
                            onDragStart = { touch ->
                                val localX = (touch.x - offsetX) / renderW
                                val localY = (touch.y - offsetY) / renderH

                                // Find closest corner
                                val dTL = hypot(localX - quad.topLeft.x, localY - quad.topLeft.y)
                                val dTR = hypot(localX - quad.topRight.x, localY - quad.topRight.y)
                                val dBR = hypot(localX - quad.bottomRight.x, localY - quad.bottomRight.y)
                                val dBL = hypot(localX - quad.bottomLeft.x, localY - quad.bottomLeft.y)

                                val threshold = 0.18f
                                draggingCorner = when {
                                    dTL < threshold && dTL <= minOf(dTR, dBR, dBL) -> "TL"
                                    dTR < threshold && dTR <= minOf(dTL, dBR, dBL) -> "TR"
                                    dBR < threshold && dBR <= minOf(dTL, dTR, dBL) -> "BR"
                                    dBL < threshold && dBL <= minOf(dTL, dTR, dBR) -> "BL"
                                    else -> null
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val corner = draggingCorner ?: return@detectDragGestures
                                val normX = ((change.position.x - offsetX) / renderW).coerceIn(0f, 1f)
                                val normY = ((change.position.y - offsetY) / renderH).coerceIn(0f, 1f)

                                quad = when (corner) {
                                    "TL" -> quad.copy(topLeft = PointFNorm(normX, normY))
                                    "TR" -> quad.copy(topRight = PointFNorm(normX, normY))
                                    "BR" -> quad.copy(bottomRight = PointFNorm(normX, normY))
                                    "BL" -> quad.copy(bottomLeft = PointFNorm(normX, normY))
                                    else -> quad
                                }
                            },
                            onDragEnd = { draggingCorner = null },
                            onDragCancel = { draggingCorner = null }
                        )
                    }
            ) {
                // Background bitmap & Quad polygon outline
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw the image
                    drawImage(
                        image = currentBitmap.asImageBitmap(),
                        dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                        dstSize = IntSize(renderW.toInt(), renderH.toInt())
                    )

                    // Draw quadrilateral polygon
                    val pTL = Offset(offsetX + quad.topLeft.x * renderW, offsetY + quad.topLeft.y * renderH)
                    val pTR = Offset(offsetX + quad.topRight.x * renderW, offsetY + quad.topRight.y * renderH)
                    val pBR = Offset(offsetX + quad.bottomRight.x * renderW, offsetY + quad.bottomRight.y * renderH)
                    val pBL = Offset(offsetX + quad.bottomLeft.x * renderW, offsetY + quad.bottomLeft.y * renderH)

                    val path = Path().apply {
                        moveTo(pTL.x, pTL.y)
                        lineTo(pTR.x, pTR.y)
                        lineTo(pBR.x, pBR.y)
                        lineTo(pBL.x, pBL.y)
                        close()
                    }

                    // Stroke
                    drawPath(
                        path = path,
                        color = Color(0xFF00E5FF),
                        style = Stroke(width = 4f)
                    )

                    // Corner circles
                    val corners = listOf(pTL, pTR, pBR, pBL)
                    corners.forEach { c ->
                        drawCircle(color = Color.White, radius = 16f, center = c)
                        drawCircle(color = Color(0xFF00B4D8), radius = 12f, center = c)
                        drawCircle(color = Color.White, radius = 4f, center = c)
                    }

                    // Midpoint handles
                    val midpoints = listOf(
                        Offset((pTL.x + pTR.x) / 2f, (pTL.y + pTR.y) / 2f),
                        Offset((pTR.x + pBR.x) / 2f, (pTR.y + pBR.y) / 2f),
                        Offset((pBR.x + pBL.x) / 2f, (pBR.y + pBL.y) / 2f),
                        Offset((pBL.x + pTL.x) / 2f, (pBL.y + pTL.y) / 2f)
                    )
                    midpoints.forEach { m ->
                        drawCircle(color = Color(0xFF00E5FF), radius = 8f, center = m)
                    }
                }
            }
        }

        // Action Toolbar
        Surface(
            color = Color(0xFF1E1E1E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                quad = ImageProcessingEngine.detectDocumentCorners(currentBitmap)
                            }
                        },
                        modifier = Modifier.testTag("auto_detect_button")
                    ) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto Detect", color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            quad = CropQuad(
                                topLeft = PointFNorm(0.01f, 0.01f),
                                topRight = PointFNorm(0.99f, 0.01f),
                                bottomRight = PointFNorm(0.99f, 0.99f),
                                bottomLeft = PointFNorm(0.01f, 0.99f)
                            )
                        },
                        modifier = Modifier.testTag("full_image_button")
                    ) {
                        Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Full Image", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isWarping = true
                            val warped = ImageProcessingEngine.warpPerspective(currentBitmap, quad)
                            isWarping = false
                            onCropConfirmed(warped, quad)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("confirm_crop_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isWarping) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Next: Enhance Document", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }
}
