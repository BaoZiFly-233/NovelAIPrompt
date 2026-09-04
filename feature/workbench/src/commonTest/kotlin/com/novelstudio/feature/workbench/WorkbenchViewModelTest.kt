package com.novelstudio.feature.workbench

import com.novelstudio.core.model.ArtistString
import com.novelstudio.core.model.PersonalTag
import com.novelstudio.core.model.PromptAsset
import com.novelstudio.core.model.TagSource
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkbenchViewModelTest {

    @Test
    fun `final text follows artist prompt ordered tags and free text`() {
        val state = WorkbenchUiState(
            artistPositive = "artist:a, artist:b",
            artistNegative = "style noise",
            promptPositive = "1girl, portrait",
            promptNegative = "bad hands",
            orderedTags = listOf("silver hair", "blue eyes"),
            freePrompt = "moonlight",
            negativePrompt = "text",
        )

        assertEquals(
            "artist:a, artist:b, 1girl, portrait, silver hair, blue eyes, moonlight",
            state.finalPositive,
        )
        assertEquals("style noise, bad hands, text", state.finalNegative)
    }

    @Test
    fun `asset sections remain independent and tags keep explicit order`() {
        val artist = ArtistString("a", "A", "artist:a", "bad style", createdAt = 1L, updatedAt = 1L)
        val prompt = PromptAsset("p", "P", "1girl", "bad hands", createdAt = 1L, updatedAt = 1L)
        val tags = listOf(
            PersonalTag("t2", "blue eyes", "blue eyes", source = TagSource.PERSONAL, createdAt = 1L, updatedAt = 1L),
            PersonalTag("t1", "silver hair", "silver hair", source = TagSource.PERSONAL, createdAt = 1L, updatedAt = 1L),
        )
        val state = WorkbenchUiState(
            artistStrings = listOf(artist),
            promptAssets = listOf(prompt),
            availableTags = tags,
            selectedArtistStringId = artist.id,
            selectedPromptAssetId = prompt.id,
            artistPositive = artist.positivePrompt,
            promptPositive = prompt.positivePrompt,
            orderedTags = tags.map { it.displayValue },
        )

        assertEquals("artist:a", state.artistPositive)
        assertEquals("1girl", state.promptPositive)
        assertEquals(listOf("blue eyes", "silver hair"), state.orderedTags)
    }

    @Test
    fun `batch mode alone raises request sample count`() {
        assertEquals(1, WorkbenchUiState(generationMode = GenerationMode.SINGLE, nSamples = 4).parameters(1L).nSamples)
        assertEquals(4, WorkbenchUiState(generationMode = GenerationMode.BATCH_REVIEW, nSamples = 4).parameters(1L).nSamples)
        assertEquals(1, WorkbenchUiState(generationMode = GenerationMode.DRAW_UNTIL_LIKED, nSamples = 4).parameters(1L).nSamples)
    }
}
