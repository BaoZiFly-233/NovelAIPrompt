package com.novelstudio.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GalleryPagingConfigTest {

    @Test
    fun galleryPagingWindowStaysBounded() {
        val config = galleryPagingConfig()

        assertEquals(60, config.pageSize)
        assertEquals(60, config.initialLoadSize)
        assertEquals(20, config.prefetchDistance)
        assertEquals(300, config.maxSize)
        assertFalse(config.enablePlaceholders)
    }
}
