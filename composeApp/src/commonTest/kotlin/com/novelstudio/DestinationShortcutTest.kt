package com.novelstudio

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DestinationShortcutTest {
    @Test
    fun ctrlNumberKeysMapToTheSixDestinations() {
        assertEquals(Destination.Gallery, destinationForShortcut(Key.One))
        assertEquals(Destination.Workbench, destinationForShortcut(Key.Two))
        assertEquals(Destination.ArtistStrings, destinationForShortcut(Key.Three))
        assertEquals(Destination.Prompts, destinationForShortcut(Key.Four))
        assertEquals(Destination.Tags, destinationForShortcut(Key.Five))
        assertEquals(null, destinationForShortcut(Key.Six))
    }

    @Test
    fun unrelatedKeyDoesNotNavigate() {
        assertNull(destinationForShortcut(Key.Enter))
    }
}
