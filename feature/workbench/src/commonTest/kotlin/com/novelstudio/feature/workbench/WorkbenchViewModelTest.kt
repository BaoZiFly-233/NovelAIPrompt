package com.novelstudio.feature.workbench

import com.novelstudio.core.data.GenerationOutcome
import com.novelstudio.core.data.GenerationRepository
import com.novelstudio.core.model.AspectPreset
import com.novelstudio.core.model.DispatchDecision
import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.ImageRecord
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.PromptDraft
import com.novelstudio.core.model.PromptDraftStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorkbenchViewModelTest {

    private val store = object : PromptDraftStore {
        private var draft: PromptDraft? = null
        override fun peek(): PromptDraft? = draft
        override fun push(draft: PromptDraft) { this.draft = draft }
        override fun clear() { draft = null }
    }

    private lateinit var repository: FakeGenerationRepository

    private fun vmTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        repository = FakeGenerationRepository()
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `generate success updates state with message`() = vmTest {
        val viewModel = WorkbenchViewModel(repository, store)
        viewModel.updatePrompt("1girl, silver hair")
        viewModel.generate()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isGenerating)
        assertTrue(viewModel.uiState.value.message?.contains("生成成功") == true)
        assertEquals(1, repository.generateCalls.size)
        assertEquals("1girl, silver hair", repository.generateCalls.first().prompt)
    }

    @Test
    fun `anlas confirmation flow requires explicit confirm`() = vmTest {
        val viewModel = WorkbenchViewModel(repository, store)
        repository.nextOutcome = GenerationOutcome.NeedsAnlasConfirmation(
            GenerationParameters(),
            DispatchDecision.CONFIRM_ANLAS,
        )
        viewModel.updatePrompt("1girl")
        viewModel.generate()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.needsAnlasConfirmation)

        viewModel.confirmAnlas()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.needsAnlasConfirmation)
        assertEquals(1, repository.anlasCalls.size, "确认后应走 generateWithAnlas")
    }

    @Test
    fun `failure surfaces translated message`() = vmTest {
        val viewModel = WorkbenchViewModel(repository, store)
        repository.nextOutcome = GenerationOutcome.Failure("API Token 无效")
        viewModel.updatePrompt("1girl")
        viewModel.generate()
        advanceUntilIdle()

        assertEquals("API Token 无效", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun `aspect selection clamps to opus free quota`() = vmTest {
        val viewModel = WorkbenchViewModel(repository, store)
        viewModel.selectAspect(AspectPreset.PORTRAIT_2_3)
        advanceUntilIdle()

        assertEquals(832, viewModel.uiState.value.width)
        assertEquals(1216, viewModel.uiState.value.height)
        assertEquals(AspectPreset.PORTRAIT_2_3, viewModel.uiState.value.aspect)
    }

    @Test
    fun `draft adoption consumes store`() = vmTest {
        store.push(PromptDraft(prompt = "from gallery", uc = "lowres", model = NaiModel.V4_5_FULL))

        val viewModel = WorkbenchViewModel(repository, store)
        advanceUntilIdle()
        assertEquals("from gallery", viewModel.uiState.value.prompt)
        assertNull(store.peek(), "草稿应被消费置空")
    }

    private class FakeGenerationRepository : GenerationRepository {
        var nextOutcome: GenerationOutcome = GenerationOutcome.Success(
            record = ImageRecord(
                id = "id0",
                filePath = "/fake/x.png",
                prompt = "1girl",
                model = NaiModel.V5_FULL.id,
            ),
            previewBytes = ByteArray(0),
            decision = DispatchDecision.USE_V5_BATTERY,
        )
        val generateCalls = mutableListOf<GenerationParameters>()
        val anlasCalls = mutableListOf<GenerationParameters>()

        override suspend fun generate(
            parameters: GenerationParameters,
            explorationMode: Boolean,
        ): GenerationOutcome {
            generateCalls += parameters
            return nextOutcome
        }

        override suspend fun generateWithAnlas(parameters: GenerationParameters): GenerationOutcome {
            anlasCalls += parameters
            return nextOutcome
        }
    }
}
