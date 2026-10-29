package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class ScanMode(val title: String, val subtitle: String) {
    SINGLE("Single Page", "Standard single document scan"),
    BATCH("Batch Mode", "Scan multiple pages continuously"),
    ID_CARD("ID Card", "Front and back side merged into one"),
    BOOK_NOTE("Books & Notes", "Optimized dual page / notebook capture"),
    RECEIPT("Receipt / Invoice", "High contrast financial document")
}

enum class FilterType(val displayName: String) {
    ORIGINAL("Original"),
    MAGIC_COLOR("Magic Color"),
    AUTO_ENHANCE("Auto Enhance"),
    GRAYSCALE("Grayscale"),
    BLACK_WHITE("B&W Clean")
}

data class PointFNorm(
    val x: Float, // 0.0 to 1.0 normalized
    val y: Float
)

data class CropQuad(
    val topLeft: PointFNorm = PointFNorm(0.08f, 0.08f),
    val topRight: PointFNorm = PointFNorm(0.92f, 0.08f),
    val bottomRight: PointFNorm = PointFNorm(0.92f, 0.92f),
    val bottomLeft: PointFNorm = PointFNorm(0.08f, 0.92f)
)

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val folderId: String? = null,
    val isFavorite: Boolean = false,
    val isTrash: Boolean = false,
    val tags: String = "", // Comma-separated tags
    val extractedText: String = "",
    val pageCount: Int = 1,
    val thumbnailPath: String? = null,
    val fileSizeBytes: Long = 0L,
    val pdfPath: String? = null
)

@Entity(tableName = "document_pages")
data class DocumentPageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val documentId: String,
    val pageIndex: Int,
    val originalImagePath: String,
    val processedImagePath: String,
    val filterType: String = FilterType.MAGIC_COLOR.name,
    val rotationDegrees: Int = 0,
    val brightness: Float = 0f,
    val contrast: Float = 1.0f,
    val sharpness: Float = 0f,
    val exposure: Float = 0f,
    // Stored normalized crop points
    val tlX: Float = 0.08f, val tlY: Float = 0.08f,
    val trX: Float = 0.92f, val trY: Float = 0.08f,
    val brX: Float = 0.92f, val brY: Float = 0.92f,
    val blX: Float = 0.08f, val blY: Float = 0.92f,
    val ocrText: String = ""
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#00695C",
    val createdAt: Long = System.currentTimeMillis()
)
