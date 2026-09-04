package com.novelstudio.feature.workbench

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromptEditorTest {

    @Test
    fun `finds token and prefix at cursor without consuming separators`() {
        val text = "1girl, silver hair, smile"
        val cursor = text.indexOf("ver") + 3

        val token = assertNotNull(promptTokenAtCursor(text, cursor))

        assertEquals("silver", token.query)
        assertEquals("silver hair", text.substring(token.range.start, token.range.end))
    }

    @Test
    fun `weighted token keeps brackets outside replacement range`() {
        val text = "solo, {{sil}}, [smile]"
        val token = assertNotNull(promptTokenAtCursor(text, text.indexOf("sil") + 3))

        val replacement = replacePromptToken(text, token, "silver hair")

        assertEquals("solo, {{silver hair}}, [smile]", replacement.text)
        assertEquals(replacement.text.indexOf("silver hair") + "silver hair".length, replacement.cursor)
    }

    @Test
    fun `unicode before token preserves utf16 cursor offsets`() {
        val text = "女孩😀, sil, smile"
        val cursor = text.indexOf("sil") + 3
        val token = assertNotNull(promptTokenAtCursor(text, cursor))

        val replacement = replacePromptToken(text, token, "silver hair")

        assertEquals("女孩😀, silver hair, smile", replacement.text)
        assertEquals(replacement.text.indexOf("silver hair") + "silver hair".length, replacement.cursor)
    }

    @Test
    fun `empty unknown and out of range cursor have no token`() {
        assertNull(promptTokenAtCursor("1girl,   ", "1girl,   ".length))
        assertNull(promptTokenAtCursor("1girl", -1))
        assertNull(promptTokenAtCursor("1girl", 99))
    }

    @Test
    fun `suggestion shortcuts yield to active ime composition`() {
        val composing = TextFieldValue(
            text = "银",
            selection = TextRange(1),
            composition = TextRange(0, 1),
        )

        assertTrue(!canHandlePromptSuggestionShortcut(composing))
        assertTrue(canHandlePromptSuggestionShortcut(composing.copy(composition = null)))
    }

    @Test
    fun `syntax highlighting is text preserving with identity offsets`() {
        val input = AnnotatedString("solo, {smile}, [blurry]")
        val transformed = PromptWeightVisualTransformation(Color.Red, Color.Gray).filter(input)

        assertEquals(input.text, transformed.text.text)
        assertEquals(8, transformed.offsetMapping.originalToTransformed(8))
        assertEquals(14, transformed.offsetMapping.transformedToOriginal(14))
        assertTrue(transformed.text.spanStyles.any { it.start == input.text.indexOf('{') })
        assertTrue(transformed.text.spanStyles.any { it.start == input.text.indexOf('[') })
    }
}
