package com.trc.photobooth

import com.trc.photobooth.filters.FilterPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FilterPresetsTest {

    @Test
    fun filterPresets_hasExpectedPresets() {
        val concrete = FilterPresets.CONCRETE_PRESETS
        assertEquals(10, concrete.size)

        val all = FilterPresets.ALL
        assertEquals(11, all.size)

        // All IDs must be unique
        val ids = all.map { it.id }.toSet()
        assertEquals(11, ids.size)
    }

    @Test
    fun filterPresets_randomPickerReturnsConcretePreset() {
        val randomFilter = FilterPresets.getRandomConcreteFilter()
        assertNotNull(randomFilter)
        assert(randomFilter.id != FilterPresets.RANDOM.id)
        assert(FilterPresets.CONCRETE_PRESETS.contains(randomFilter))
    }

    @Test
    fun filterPresets_retrievalByIdWorks() {
        val vintage = FilterPresets.getById("vintage")
        assertNotNull(vintage)
        assertEquals("Vintage", vintage.name)

        val random = FilterPresets.getById("random")
        assertNotNull(random)
        assertEquals("Random", random.name)

        val unknown = FilterPresets.getById("unknown_preset")
        assertEquals(FilterPresets.NONE, unknown)
    }
}
