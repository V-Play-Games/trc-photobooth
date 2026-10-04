package com.trc.photobooth.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.widget.Toast
import androidx.print.PrintHelper
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
     * Prints a bitmap using Android's PrintHelper.
     * Launches the system print dialog allowing the guest or operator to send the 2x2 collage
     * directly to any connected Wi-Fi, Mopria, Bluetooth, thermal, or USB printer.
     */
    fun printBitmap(context: Context, bitmap: Bitmap, title: String = "TRC Photo Booth Collage") {
        try {
            if (!PrintHelper.systemSupportsPrint()) {
                Toast.makeText(context, "Printing is not supported on this device", Toast.LENGTH_LONG).show()
                return
            }
            val printHelper = PrintHelper(context).apply {
                scaleMode = PrintHelper.SCALE_MODE_FIT
                colorMode = PrintHelper.COLOR_MODE_COLOR
            }
            printHelper.printBitmap(title, bitmap)
        } catch (e: Exception) {
            Log.e("BitmapUtils", "Failed to print bitmap: ${e.message}", e)
            Toast.makeText(context, "Printing error: ${e.message}", Toast.LENGTH_SHORT).show()
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

    const val STRIP_WIDTH_MM = 74.25f
    const val STRIP_HEIGHT_MM = 210.0f
    const val PRINT_DPI = 300

    const val STRIP_WIDTH_PX = 877
    const val STRIP_HEIGHT_PX = 2480
    const val PHOTO_WIDTH_PX = 749f
    const val PHOTO_HEIGHT_PX = 562f
    const val MARGIN_X_PX = 64f
    const val MARGIN_TOP_PX = 44f
    const val GAP_Y_PX = 48f

    const val TEMPLATE_WIDTH_PX = 721
    const val TEMPLATE_HEIGHT_PX = 1024

    data class FrameBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /**
     * Exact coordinates of the 4 photo frames within the 721x1024 template canvas.
     */
    val FRAME_BOXES = listOf(
        FrameBox(30f, 23f, 335f, 248f),
        FrameBox(30f, 269f, 335f, 494f),
        FrameBox(30f, 514f, 335f, 739f),
        FrameBox(30f, 758f, 335f, 983f),
    )

    /**
     * Decodes a template overlay bitmap with exact pixel resolution without density scaling.
     */
    fun loadTemplateBitmap(context: Context, @androidx.annotation.DrawableRes resId: Int): Bitmap {
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeResource(context.resources, resId, options)
    }

    /**
     * Creates a composite photo collage fitting photos into the 4 frames on the 721x1024 template canvas.
     * The background is white, each photo is center-cropped to fit the frame dimensions perfectly,
     * and the template overlay (borders, stickers, characters) is composited on top.
     */
    fun createThemedCollage(
        photos: List<Bitmap>,
        templateOverlay: Bitmap?,
    ): Bitmap {
        val collage = Bitmap.createBitmap(TEMPLATE_WIDTH_PX, TEMPLATE_HEIGHT_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(collage)
        canvas.drawColor(Color.WHITE)

        val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val emptyPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        for (i in 0 until 4) {
            val box = FRAME_BOXES.getOrElse(i) { FRAME_BOXES[0] }
            val dstRect = RectF(box.left, box.top, box.right, box.bottom)
            val photo = photos.getOrNull(i)
            if (photo != null) {
                // Center-crop source photo into cell
                val srcW = photo.width
                val srcH = photo.height
                val targetRatio = box.width / box.height
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
                canvas.drawRect(dstRect, emptyPaint)
            }
        }

        if (templateOverlay != null) {
            val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            if (templateOverlay.width == TEMPLATE_WIDTH_PX && templateOverlay.height == TEMPLATE_HEIGHT_PX) {
                canvas.drawBitmap(templateOverlay, 0f, 0f, overlayPaint)
            } else {
                val dst = RectF(0f, 0f, TEMPLATE_WIDTH_PX.toFloat(), TEMPLATE_HEIGHT_PX.toFloat())
                canvas.drawBitmap(templateOverlay, null, dst, overlayPaint)
            }
        }

        return collage
    }

    /**
     * Backward-compatible collage creation.
     */
    fun createCollage(
        photos: List<Bitmap>,
        sessionTimestamp: String = "",
        title: String = "TRC PHOTO BOOTH",
        templateOverlay: Bitmap? = null,
    ): Bitmap {
        return createThemedCollage(photos, templateOverlay)
    }

    /**
     * Saves the photo strip bitmap to the session directory in Pictures.
     */
    fun saveCollage(
        context: Context,
        collageBitmap: Bitmap,
        sessionTimestamp: String,
        fileName: String = "photo_collage_grid.jpg",
    ): File? {
        val baseDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "TRCPhotoBooth/sessions/$sessionTimestamp"
        )
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }

        val collageFile = File(baseDir, fileName)
        return try {
            FileOutputStream(collageFile).use { out ->
                collageBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
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
