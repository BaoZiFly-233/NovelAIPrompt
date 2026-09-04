package com.novelstudio.feature.swipe

import com.novelstudio.core.data.SwipeImageRepository
import com.novelstudio.core.model.ImageRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SwipeViewModelTest {

    @Test
    fun `repeated decisions update one card only once`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeSwipeRepository()
            val viewModel = SwipeViewModel(repository)
            repeat(20) { viewModel.swipeLike(RECORD) }

            advanceUntilIdle()

            assertEquals(listOf(RECORD.id to "archive"), repository.updates)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed decision releases gate for retry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeSwipeRepository(failuresRemaining = 1)
            val viewModel = SwipeViewModel(repository)

            viewModel.swipeDislike(RECORD)
            advanceUntilIdle()
            viewModel.swipeDislike(RECORD)
            advanceUntilIdle()

            assertEquals(2, repository.attemptCount)
            assertEquals(listOf(RECORD.id to "trash"), repository.updates)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeSwipeRepository(
        private var failuresRemaining: Int = 0,
    ) : SwipeImageRepository {
        private val top = MutableStateFlow<ImageRecord?>(RECORD)
        private val count = MutableStateFlow(1)
        val updates = mutableListOf<Pair<String, String>>()
        var attemptCount = 0

        override fun observeNextUnreviewed(): Flow<ImageRecord?> = top
        override fun observeUnreviewedCount(): Flow<Int> = count

        override suspend fun archive(id: String, artistStringId: String?) {
            attemptCount++
            if (failuresRemaining > 0) {
                failuresRemaining--
                error("database unavailable")
            }
            updates += id to "archive"
        }

        override suspend fun moveToTrash(id: String) {
            attemptCount++
            if (failuresRemaining > 0) {
                failuresRemaining--
                error("database unavailable")
            }
            updates += id to "trash"
        }

        override suspend fun restoreFromTrash(id: String) = Unit
    }

    private companion object {
        val RECORD = ImageRecord(
            id = "image-1",
            filePath = "/images/image-1.png",
            prompt = "1girl",
            model = "nai-diffusion-5-full",
        )
    }
}
