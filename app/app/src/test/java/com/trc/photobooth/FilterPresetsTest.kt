package com.trc.photobooth

import com.trc.photobooth.filters.FilterPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FilterPresetsTest {

    @Test
    fun filterPresets_hasTenDistinctPresets() {
        val all = FilterPresets.ALL
        assertEquals(10, all.size)

        // All IDs must be unique
        val ids = all.map { it.id }.toSet()
        assertEquals(10, ids.size)
    }

    @Test
    fun filterPresets_retrievalByIdWorks() {
        val vintage = FilterPresets.getById("vintage")
        assertNotNull(vintage)
        assertEquals("Vintage", vintage.name)

        val unknown = FilterPresets.getById("unknown_preset")
        assertEquals(FilterPresets.NONE, unknown)
    }
}
