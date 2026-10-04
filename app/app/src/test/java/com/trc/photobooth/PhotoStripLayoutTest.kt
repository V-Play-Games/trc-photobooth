package com.trc.photobooth

import com.trc.photobooth.util.BitmapUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoStripLayoutTest {

    @Test
    fun photoStrip_hasExactPhysicalAndPixelDimensions() {
        assertEquals(74.25f, BitmapUtils.STRIP_WIDTH_MM, 0.001f)
        assertEquals(210.0f, BitmapUtils.STRIP_HEIGHT_MM, 0.001f)
        assertEquals(300, BitmapUtils.PRINT_DPI)

        assertEquals(877, BitmapUtils.STRIP_WIDTH_PX)
        assertEquals(2480, BitmapUtils.STRIP_HEIGHT_PX)
    }

    @Test
    fun photoStrip_verticalSumMatchesHeightExactly() {
        val totalCalculatedHeight = BitmapUtils.MARGIN_TOP_PX +
            (4 * BitmapUtils.PHOTO_HEIGHT_PX) +
            (3 * BitmapUtils.GAP_Y_PX) +
            BitmapUtils.MARGIN_TOP_PX

        assertEquals(BitmapUtils.STRIP_HEIGHT_PX.toFloat(), totalCalculatedHeight, 0.01f)
    }

    @Test
    fun photoStrip_horizontalSumMatchesWidthExactly() {
        val totalCalculatedWidth = (2 * BitmapUtils.MARGIN_X_PX) + BitmapUtils.PHOTO_WIDTH_PX
        assertEquals(BitmapUtils.STRIP_WIDTH_PX.toFloat(), totalCalculatedWidth, 0.01f)
    }

    @Test
    fun photoStrip_aspectRatioMatchesDivision() {
        // Physical aspect ratio
        val physicalRatio = BitmapUtils.STRIP_WIDTH_MM / BitmapUtils.STRIP_HEIGHT_MM
        // Pixel aspect ratio
        val pixelRatio = BitmapUtils.STRIP_WIDTH_PX.toFloat() / BitmapUtils.STRIP_HEIGHT_PX.toFloat()

        assertEquals(physicalRatio, pixelRatio, 0.001f)
    }

    @Test
    fun photoStrip_photoCellsHaveStandardPhotoRatio() {
        // 4:3 is ~1.3333
        val cellRatio = BitmapUtils.PHOTO_WIDTH_PX / BitmapUtils.PHOTO_HEIGHT_PX
        assertTrue(cellRatio in 1.33f..1.34f)
    }
}
