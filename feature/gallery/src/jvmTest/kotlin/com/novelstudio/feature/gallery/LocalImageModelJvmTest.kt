package com.novelstudio.feature.gallery

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LocalImageModelJvmTest {

    @Test
    fun windowsPathIsPassedToCoilAsFileInsteadOfHandBuiltUri() {
        val path = "C:\\Users\\Test User\\NovelAI images\\sample one.png"

        val model = assertIs<File>(localImageModel(path))

        assertEquals(File(path), model)
    }
}
