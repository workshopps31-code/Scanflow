package com.example

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.example.data.model.CropQuad
import com.example.data.model.FilterType
import com.example.ui.screens.CameraScannerScreen
import com.example.ui.screens.CropScreen
import com.example.ui.screens.DocumentDetailScreen
import com.example.ui.screens.EnhanceStudioScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OcrResultScreen
import com.example.ui.screens.PdfToolsScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.theme.DocuLensTheme
import com.example.ui.viewmodel.DocumentDetailViewModel
import com.example.ui.viewmodel.DocumentListViewModel
import com.example.ui.viewmodel.PageDraft
import com.example.ui.viewmodel.ScannerViewModel

enum class MainNavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    SCAN("Scan", Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt),
    TOOLS("Tools", Icons.Filled.Build, Icons.Outlined.Build),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

sealed class AppScreen {
    object Main : AppScreen()
    object CameraScan : AppScreen()
    data class Crop(val bitmap: Bitmap, val quad: CropQuad) : AppScreen()
    data class Enhance(val warpedBitmap: Bitmap, val quad: CropQuad, val filter: FilterType) : AppScreen()
    data class DocumentDetail(val documentId: String) : AppScreen()
    data class OcrViewer(val documentId: String) : AppScreen()
}

class MainActivity : ComponentActivity() {

    private val documentListViewModel: DocumentListViewModel by viewModels()
    private val scannerViewModel: ScannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DocuLensTheme {
                DocuLensApp(
                    documentListViewModel = documentListViewModel,
                    scannerViewModel = scannerViewModel
                )
            }
        }
    }
}

@Composable
fun DocuLensApp(
    documentListViewModel: DocumentListViewModel,
    scannerViewModel: ScannerViewModel
) {
    var currentNavTab by remember { mutableStateOf(MainNavTab.HOME) }
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Main) }

    // Screen navigation router
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "AppScreenTransition"
    ) { screen ->
        when (screen) {
            is AppScreen.Main -> {
                Scaffold(
                    bottomBar = {
                        NavigationBar(modifier = Modifier.testTag("main_navigation_bar")) {
                            MainNavTab.values().forEach { tab ->
                                val isSelected = currentNavTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        if (tab == MainNavTab.SCAN) {
                                            currentScreen = AppScreen.CameraScan
                                        } else {
                                            currentNavTab = tab
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title
                                        )
                                    },
                                    label = { Text(tab.title) },
                                    modifier = Modifier.testTag("nav_item_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentNavTab) {
                            MainNavTab.HOME -> {
                                HomeScreen(
                                    viewModel = documentListViewModel,
                                    onOpenDocument = { docId ->
                                        currentScreen = AppScreen.DocumentDetail(docId)
                                    },
                                    onStartScan = { currentScreen = AppScreen.CameraScan },
                                    onOpenPdfTools = { currentNavTab = MainNavTab.TOOLS }
                                )
                            }
                            MainNavTab.SCAN -> {
                                // Handled via camera direct screen
                            }
                            MainNavTab.TOOLS -> {
                                PdfToolsScreen(
                                    onScanRequested = { currentScreen = AppScreen.CameraScan }
                                )
                            }
                            MainNavTab.PROFILE -> {
                                val totalBytes by documentListViewModel.totalStorageBytes.collectAsState()
                                val docCount by documentListViewModel.activeCount.collectAsState()
                                ProfileSettingsScreen(
                                    storageBytes = totalBytes ?: 0L,
                                    docCount = docCount
                                )
                            }
                        }
                    }
                }
            }

            is AppScreen.CameraScan -> {
                CameraScannerScreen(
                    viewModel = scannerViewModel,
                    onBack = { currentScreen = AppScreen.Main },
                    onSingleDraftReady = { draft ->
                        currentScreen = AppScreen.Crop(draft.bitmap, draft.quad)
                    },
                    onBatchFinished = {
                        scannerViewModel.saveAllPagesToDocument { docId ->
                            currentScreen = AppScreen.DocumentDetail(docId)
                        }
                    }
                )
            }

            is AppScreen.Crop -> {
                CropScreen(
                    initialBitmap = screen.bitmap,
                    initialQuad = screen.quad,
                    onBack = { currentScreen = AppScreen.CameraScan },
                    onCropConfirmed = { warpedBitmap, quad ->
                        currentScreen = AppScreen.Enhance(warpedBitmap, quad, FilterType.MAGIC_COLOR)
                    }
                )
            }

            is AppScreen.Enhance -> {
                EnhanceStudioScreen(
                    originalWarpedBitmap = screen.warpedBitmap,
                    initialFilter = screen.filter,
                    onBack = {
                        currentScreen = AppScreen.Crop(screen.warpedBitmap, screen.quad)
                    },
                    onSave = { processedBitmap, chosenFilter ->
                        scannerViewModel.saveAllPagesToDocument { docId ->
                            currentScreen = AppScreen.DocumentDetail(docId)
                        }
                    }
                )
            }

            is AppScreen.DocumentDetail -> {
                val detailViewModel = remember(screen.documentId) {
                    DocumentDetailViewModel(
                        application = documentListViewModel.getApplication(),
                        documentId = screen.documentId
                    )
                }

                DocumentDetailScreen(
                    viewModel = detailViewModel,
                    onBack = { currentScreen = AppScreen.Main },
                    onOpenOcr = { currentScreen = AppScreen.OcrViewer(screen.documentId) }
                )
            }

            is AppScreen.OcrViewer -> {
                val detailViewModel = remember(screen.documentId) {
                    DocumentDetailViewModel(
                        application = documentListViewModel.getApplication(),
                        documentId = screen.documentId
                    )
                }

                OcrResultScreen(
                    viewModel = detailViewModel,
                    onBack = { currentScreen = AppScreen.DocumentDetail(screen.documentId) }
                )
            }
        }
    }
}
