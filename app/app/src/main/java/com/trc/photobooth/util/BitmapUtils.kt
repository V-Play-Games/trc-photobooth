package com.trc.photobooth.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.trc.photobooth.filters.FilterPreset
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import android.util.Log

object BitmapUtils {

    fun decodeJpeg(bytes: ByteArray): Bitmap? {
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    fun bakeFilter(
        source: Bitmap,
        preset: FilterPreset,
        withPolaroidBorder: Boolean = false,
    ): Bitmap {
        val origW = source.width
        val origH = source.height

        val useBorder = withPolaroidBorder || preset.isPolaroid
        val borderPadX = if (useBorder) (origW * 0.05f).toInt() else 0
        val borderPadY = if (useBorder) (origH * 0.05f).toInt() else 0
        val borderPadBottom = if (useBorder) (origH * 0.18f).toInt() else 0

        val targetW = origW + borderPadX * 2
        val targetH = origH + borderPadY + borderPadBottom

        val outBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)

        if (useBorder) {
            // Vintage polaroid cream-white card background
            val cardPaint = Paint().apply {
                color = Color.parseColor("#F8F6F0")
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, targetW.toFloat(), targetH.toFloat(), cardPaint)
        }

        // Draw source image with ColorMatrix filter if present
        val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (preset.colorMatrix != null) {
            val androidColorMatrix = android.graphics.ColorMatrix(preset.colorMatrix.values)
            imgPaint.colorFilter = ColorMatrixColorFilter(androidColorMatrix)
        }

        val dstRect = Rect(
            borderPadX,
            borderPadY,
            borderPadX + origW,
            borderPadY + origH,
        )
        canvas.drawBitmap(source, null, dstRect, imgPaint)

        // Procedural Vignette Overlay
        if (preset.isVignette) {
            val cx = borderPadX + origW / 2f
            val cy = borderPadY + origH / 2f
            val radius = Math.hypot((origW / 2).toDouble(), (origH / 2).toDouble()).toFloat()
            val gradient = RadialGradient(
                cx, cy, radius,
                intArrayOf(Color.TRANSPARENT, Color.argb(120, 0, 0, 0), Color.argb(220, 0, 0, 0)),
                floatArrayOf(0.45f, 0.75f, 1.0f),
                Shader.TileMode.CLAMP
            )
            val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = gradient
            }
            canvas.drawRect(dstRect, vignettePaint)
        }

        // Procedural Grain Overlay
        if (preset.isGrain) {
            val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(35, 255, 255, 255)
                strokeWidth = 2f
            }
            val rng = Random(System.currentTimeMillis())
            for (i in 0 until (origW * origH / 40)) {
                val rx = borderPadX + rng.nextInt(origW)
                val ry = borderPadY + rng.nextInt(origH)
                canvas.drawPoint(rx.toFloat(), ry.toFloat(), grainPaint)
            }
        }

        // Polaroid footer caption
        if (useBorder) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#475569")
                textSize = (targetW * 0.028f).coerceAtLeast(28f)
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
            val textY = borderPadY + origH + (borderPadBottom / 2f) + (textPaint.textSize / 3f)
            canvas.drawText("TRC Photo Booth • $dateStr", targetW / 2f, textY, textPaint)
        }

        return outBitmap
    }

    fun saveToGallery(context: Context, bitmap: Bitmap, title: String): Uri? {
        val filename = "TRC_PHOTO_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TRCPhotoBooth")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        try {
            resolver.openOutputStream(uri)?.use { stream: OutputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }

    fun shareImage(context: Context, uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Photo Booth Capture"))
    }

    fun shareBitmap(context: Context, bitmap: Bitmap, title: String) {
        val uri = saveToGallery(context, bitmap, title)
        if (uri != null) {
            shareImage(context, uri)
        }
    }

    /**
     * Saves a 4-photo booth session to a dedicated session folder:
     * Pictures/TRCPhotoBooth/sessions/<timestamp>/
     * Ready for Cloudinary upload and local browsing.
     * Returns the list of saved Files.
     */
    fun saveSessionPhotos(
        context: Context,
        sessionTimestamp: String,
        photos: List<Bitmap>,
        filters: List<FilterPreset>,
    ): List<File> {
        val baseDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "TRCPhotoBooth/sessions/$sessionTimestamp"
        )
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }

        val savedFiles = mutableListOf<File>()

        photos.forEachIndexed { index, bitmap ->
            val presetId = filters.getOrNull(index)?.id ?: "raw"
            val filename = "photo_${index + 1}_$presetId.jpg"
            val targetFile = File(baseDir, filename)

            try {
                FileOutputStream(targetFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    out.flush()
                }
                savedFiles.add(targetFile)

                // Index in MediaStore on Android 10+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_PICTURES}/TRCPhotoBooth/sessions/$sessionTimestamp"
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    try {
                        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    } catch (ignored: Exception) {}
                } else {
                    val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                        data = Uri.fromFile(targetFile)
                    }
                    context.sendBroadcast(scanIntent)
                }
            } catch (e: Exception) {
                Log.e("BitmapUtils", "Failed to save photo ${index + 1}: ${e.message}")
            }
        }
        return savedFiles
    }

    /**
     * Creates a high-resolution 2x2 grid collage of the 4 captured photos.
     * Dimensions: 1200 x 1400 px, with a branded header, 2x2 photo cells with sleek borders,
     * and a branded footer with timestamp.
     */
    fun createCollage(
        photos: List<Bitmap>,
        sessionTimestamp: String,
        title: String = "TRC PHOTO BOOTH"
    ): Bitmap {
        val collageWidth = 1200
        val collageHeight = 1400

        val collage = Bitmap.createBitmap(collageWidth, collageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(collage)

        // Dark cyberpunk background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#0B0F17")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, collageWidth.toFloat(), collageHeight.toFloat(), bgPaint)

        // Subtle gradient card border
        val borderPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRect(12f, 12f, collageWidth - 12f, collageHeight - 12f, borderPaint)

        // Accent top bar
        val accentPaint = Paint().apply {
            color = Color.parseColor("#FF3366")
            style = Paint.Style.FILL
        }
        canvas.drawRect(24f, 24f, collageWidth - 24f, 30f, accentPaint)

        // Header text: "TRC PHOTO BOOTH"
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            isFakeBoldText = true
            letterSpacing = 0.15f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title, collageWidth / 2f, 95f, headerPaint)

        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00F0FF")
            textSize = 20f
            isFakeBoldText = true
            letterSpacing = 0.1f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("4-SHOT COMPOSITE MEMORY", collageWidth / 2f, 130f, subHeaderPaint)

        // 2x2 Grid Layout
        val gridMarginLeft = 40f
        val gridMarginTop = 160f
        val gridGap = 20f
        val availableWidth = collageWidth - (gridMarginLeft * 2) - gridGap
        val cellWidth = availableWidth / 2f
        val cellHeight = cellWidth * (3f / 4f) // standard 4:3 photo ratio, approx 550x412.5 px

        val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val photoBorderPaint = Paint().apply {
            color = Color.parseColor("#334155")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        // 4 slots: 0=TL, 1=TR, 2=BL, 3=BR
        val coords = listOf(
            Pair(gridMarginLeft, gridMarginTop),
            Pair(gridMarginLeft + cellWidth + gridGap, gridMarginTop),
            Pair(gridMarginLeft, gridMarginTop + cellHeight + gridGap),
            Pair(gridMarginLeft + cellWidth + gridGap, gridMarginTop + cellHeight + gridGap)
        )

        for (i in 0 until 4) {
            val (left, top) = coords[i]
            val right = left + cellWidth
            val bottom = top + cellHeight
            val dstRect = RectF(left, top, right, bottom)

            val photo = photos.getOrNull(i)
            if (photo != null) {
                // Center-crop source photo into cell
                val srcW = photo.width
                val srcH = photo.height
                val targetRatio = cellWidth / cellHeight
                val srcRatio = srcW.toFloat() / srcH.toFloat()

                val srcCrop = if (srcRatio > targetRatio) {
                    val newW = (srcH * targetRatio).toInt()
                    val xOffset = (srcW - newW) / 2
                    Rect(xOffset, 0, xOffset + newW, srcH)
                } else {
                    val newH = (srcW / targetRatio).toInt()
                    val yOffset = (srcH - newH) / 2
                    Rect(0, yOffset, srcW, yOffset + newH)
                }

                canvas.drawBitmap(photo, srcCrop, dstRect, photoPaint)
            } else {
                val emptyPaint = Paint().apply {
                    color = Color.parseColor("#151C2C")
                    style = Paint.Style.FILL
                }
                canvas.drawRect(dstRect, emptyPaint)
            }

            // Cell border
            canvas.drawRect(dstRect, photoBorderPaint)
        }

        // Footer Section
        val gridBottom = gridMarginTop + (cellHeight * 2) + gridGap
        val footerAccentPaint = Paint().apply {
            color = Color.parseColor("#00E599")
            style = Paint.Style.FILL
        }
        canvas.drawRect(24f, collageHeight - 30f, collageWidth - 24f, collageHeight - 24f, footerAccentPaint)

        val footerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 22f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        val dateStr = try {
            val parsed = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).parse(sessionTimestamp)
            SimpleDateFormat("MMMM d, yyyy • h:mm a", Locale.getDefault()).format(parsed ?: Date())
        } catch (e: Exception) {
            sessionTimestamp
        }

        canvas.drawText("CAPTURED WITH TRC PHOTO BOOTH • $dateStr", collageWidth / 2f, gridBottom + 65f, footerTextPaint)

        val urlBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SCAN QR CODE TO VIEW DIGITAL ORIGINAL", collageWidth / 2f, gridBottom + 105f, urlBadgePaint)

        return collage
    }

    /**
     * Saves the 2x2 collage bitmap to the session directory in Pictures.
     */
    fun saveCollage(
        context: Context,
        collageBitmap: Bitmap,
        sessionTimestamp: String,
    ): File? {
        val baseDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "TRCPhotoBooth/sessions/$sessionTimestamp"
        )
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }

        val collageFile = File(baseDir, "photo_collage_grid.jpg")
        return try {
            FileOutputStream(collageFile).use { out ->
                collageBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "photo_collage_grid.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/TRCPhotoBooth/sessions/$sessionTimestamp"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                try {
                    context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                } catch (ignored: Exception) {}
            } else {
                val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                    data = Uri.fromFile(collageFile)
                }
                context.sendBroadcast(scanIntent)
            }
            collageFile
        } catch (e: Exception) {
            Log.e("BitmapUtils", "Failed to save collage: ${e.message}")
            null
        }
    }

    /**
     * Generates a QR code bitmap for the given URL/content string.
     */
    fun generateQrCodeBitmap(
        content: String,
        sizePx: Int = 512,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE,
    ): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints
            )
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) foregroundColor else backgroundColor)
                }
            }
            bmp
        } catch (e: Exception) {
            Log.e("BitmapUtils", "Failed to generate QR code: ${e.message}", e)
            null
        }
    }
}
