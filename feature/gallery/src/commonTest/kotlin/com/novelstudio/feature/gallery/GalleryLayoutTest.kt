package com.novelstudio.feature.gallery

import com.novelstudio.core.model.ImageRecord
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class GalleryLayoutTest {

    @Test
    fun aspectRatioUsesImageDimensions() {
        assertEquals(2f, safeThumbnailAspectRatio(record(width = 1600, height = 800)))
        assertEquals(0.5f, safeThumbnailAspectRatio(record(width = 600, height = 1200)))
    }

    @Test
    fun aspectRatioClampsDamagedMetadata() {
        assertEquals(0.25f, safeThumbnailAspectRatio(record(width = 0, height = 10_000)))
        assertEquals(4f, safeThumbnailAspectRatio(record(width = 10_000, height = 0)))
    }

    @Test
    fun lightboxUsesVerticalLayoutBelowDesktopBreakpoint() {
        assertEquals(true, isCompactLightbox(719.dp))
        assertEquals(false, isCompactLightbox(720.dp))
    }

    private fun record(width: Int, height: Int) = ImageRecord(
        id = "image",
        filePath = "/images/image.png",
        thumbnailPath = "/thumbs/image.png",
        prompt = "prompt",
        model = "nai-diffusion-5-full",
        width = width,
        height = height,
    )
}
