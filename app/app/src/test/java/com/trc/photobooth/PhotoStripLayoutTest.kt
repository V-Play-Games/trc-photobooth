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

    @Test
    fun template_dimensionsMatchOriginalSpecifications() {
        assertEquals(721, BitmapUtils.TEMPLATE_WIDTH_PX)
        assertEquals(360, BitmapUtils.TEMPLATE_STRIP_WIDTH_PX)
        assertEquals(1024, BitmapUtils.TEMPLATE_HEIGHT_PX)
        assertEquals(4, BitmapUtils.FRAME_BOXES.size)

        for (box in BitmapUtils.FRAME_BOXES) {
            assertEquals(305f, box.width, 0.01f)
            assertEquals(225f, box.height, 0.01f)
            assertTrue("Box must be within the single strip (x <= 360)", box.right <= BitmapUtils.TEMPLATE_STRIP_WIDTH_PX.toFloat())
            assertTrue("Box must be within canvas height", box.bottom <= 1024f)
        }
    }

    @Test
    fun photoBoothTemplate_enumContainsAllThemes() {
        val templates = com.trc.photobooth.data.models.PhotoBoothTemplate.ALL
        assertEquals(7, templates.size)

        val ids = templates.map { it.id }.toSet()
        assertTrue(ids.containsAll(listOf("blank", "harry_potter", "retro", "spiderman", "pokemon", "cat_meme", "comic")))

        assertEquals(
            com.trc.photobooth.data.models.PhotoBoothTemplate.HARRY_POTTER,
            com.trc.photobooth.data.models.PhotoBoothTemplate.fromId("harry_potter")
        )
        assertEquals(
            com.trc.photobooth.data.models.PhotoBoothTemplate.DEFAULT,
            com.trc.photobooth.data.models.PhotoBoothTemplate.fromId("unknown_id")
        )
        assertEquals(
            com.trc.photobooth.data.models.PhotoBoothTemplate.BLANK,
            com.trc.photobooth.data.models.PhotoBoothTemplate.DEFAULT
        )
        assertEquals(
            "Classic",
            com.trc.photobooth.data.models.PhotoBoothTemplate.DEFAULT.title
        )
    }
}

