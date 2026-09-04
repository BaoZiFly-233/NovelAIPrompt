package com.novelstudio.core.data

import com.novelstudio.core.database.GenerationTaskDao
import com.novelstudio.core.database.GenerationTaskEntity
import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.GenerationPreflight
import com.novelstudio.core.model.ImageRecord
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.OpusBatteryState
import com.novelstudio.core.model.TaskStatus
import com.novelstudio.core.model.VibeReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GenerationQueueControllerTest {

    @Test
    fun `tasks execute FIFO with maximum concurrency one`() = runTest {
        val dao = FakeTaskDao()
        val repository = FakeQueueRepository().apply { blockedPrompt = "first" }
        val controller = controller(repository, dao)

        controller.enqueue(GenerationParameters(prompt = "first"))
        controller.enqueue(GenerationParameters(prompt = "second"))
        runCurrent()

        assertEquals(listOf("first"), repository.startedPrompts)
        assertEquals(1, repository.maxActive)

        repository.releaseBlocked.complete(Unit)
        runCurrent()

        assertEquals(listOf("first", "second"), repository.startedPrompts)
        assertEquals(1, repository.maxActive)
        assertEquals(listOf(TaskStatus.SUCCEEDED, TaskStatus.SUCCEEDED), dao.rows.value.map { TaskStatus.valueOf(it.status) })
    }

    @Test
    fun `Anlas confirmation is bound to task and duplicate confirm submits once`() = runTest {
        val dao = FakeTaskDao()
        val repository = FakeQueueRepository().apply { paidPrompt = "paid" }
        val controller = controller(repository, dao)
        val taskId = controller.enqueue(GenerationParameters(prompt = "paid"))
        runCurrent()

        assertEquals(TaskStatus.WAITING_ANLAS_CONFIRMATION.name, dao.findById(taskId)?.status)
        assertEquals(0, repository.paidSubmissions)

        assertTrue(controller.confirmAnlas(taskId))
        assertTrue(controller.confirmAnlas(taskId))
        runCurrent()

        assertEquals(1, repository.paidSubmissions)
        assertEquals(TaskStatus.SUCCEEDED.name, dao.findById(taskId)?.status)
    }

    @Test
    fun `queued cancellation never reaches repository`() = runTest {
        val dao = FakeTaskDao()
        val repository = FakeQueueRepository().apply { blockedPrompt = "first" }
        val controller = controller(repository, dao)
        controller.enqueue(GenerationParameters(prompt = "first"))
        val cancelledId = controller.enqueue(GenerationParameters(prompt = "cancelled"))
        runCurrent()

        assertTrue(controller.cancel(cancelledId))
        repository.releaseBlocked.complete(Unit)
        runCurrent()

        assertEquals(listOf("first"), repository.startedPrompts)
        assertEquals(TaskStatus.CANCELLED.name, dao.findById(cancelledId)?.status)
    }

    @Test
    fun `running cancellation marks unknown and worker continues with next task`() = runTest {
        val dao = FakeTaskDao()
        val repository = FakeQueueRepository().apply { blockedPrompt = "first" }
        val controller = controller(repository, dao)
        val firstId = controller.enqueue(GenerationParameters(prompt = "first"))
        val secondId = controller.enqueue(GenerationParameters(prompt = "second"))
        runCurrent()

        assertTrue(controller.cancel(firstId))
        runCurrent()

        assertEquals(TaskStatus.FAILED_UNKNOWN.name, dao.findById(firstId)?.status)
        assertTrue(dao.findById(firstId)?.errorMessage?.contains("不会自动重试") == true)
        assertEquals(TaskStatus.SUCCEEDED.name, dao.findById(secondId)?.status)
        assertEquals(listOf("first", "second"), repository.startedPrompts)
    }

    @Test
    fun `startup converts interrupted running task to unknown without resubmission`() = runTest {
        val dao = FakeTaskDao().apply {
            upsert(taskEntity("interrupted", TaskStatus.RUNNING))
        }
        val repository = FakeQueueRepository()

        controller(repository, dao)
        runCurrent()

        assertEquals(TaskStatus.FAILED_UNKNOWN.name, dao.findById("interrupted")?.status)
        assertTrue(repository.startedPrompts.isEmpty())
    }

    @Test
    fun `corrupted waiting confirmation is failed instead of showing default parameters`() = runTest {
        val dao = FakeTaskDao().apply {
            upsert(
                taskEntity("broken", TaskStatus.WAITING_ANLAS_CONFIRMATION).copy(
                    parametersJson = "not-json",
                ),
            )
        }
        val repository = FakeQueueRepository()

        val controller = controller(repository, dao)
        runCurrent()

        assertEquals(TaskStatus.FAILED.name, dao.findById("broken")?.status)
        assertTrue(controller.state.value.single().isSnapshotCorrupted)
        assertTrue(repository.startedPrompts.isEmpty())
    }

    @Test
    fun `application scope cancellation converges running task to unknown`() = runTest {
        val dao = FakeTaskDao()
        val repository = FakeQueueRepository().apply { blockedPrompt = "running" }
        val applicationJob = SupervisorJob()
        val applicationScope = CoroutineScope(applicationJob + StandardTestDispatcher(testScheduler))
        val controller = GenerationQueueController(
            repository = repository,
            dao = dao,
            scope = applicationScope,
            idGenerator = { "scope-task" },
            clock = { 10L },
        )
        controller.enqueue(GenerationParameters(prompt = "running"))
        runCurrent()
        assertEquals(TaskStatus.RUNNING.name, dao.findById("scope-task")?.status)

        applicationJob.cancel()
        runCurrent()

        assertEquals(TaskStatus.FAILED_UNKNOWN.name, dao.findById("scope-task")?.status)
    }

    @Test
    fun `success persists every image id from one batch outcome`() = runTest {
        val dao = FakeTaskDao()
        val repository = FakeQueueRepository().apply { returnTwoImages = true }
        val controller = controller(repository, dao)
        val taskId = controller.enqueue(GenerationParameters(prompt = "batch"))

        runCurrent()

        val row = assertNotNull(dao.findById(taskId))
        assertEquals("image-batch-1", row.resultImageId)
        assertTrue(row.resultImageIdsJson.contains("image-batch-1"))
        assertTrue(row.resultImageIdsJson.contains("image-batch-2"))
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        repository: GenerationRepository,
        dao: GenerationTaskDao,
    ): GenerationQueueController {
        var id = 0
        var time = 100L
        return GenerationQueueController(
            repository = repository,
            dao = dao,
            scope = backgroundScope,
            idGenerator = { "task-${id++}" },
            clock = { time++ },
        )
    }

    private class FakeQueueRepository : GenerationRepository {
        val startedPrompts = mutableListOf<String>()
        val releaseBlocked = CompletableDeferred<Unit>()
        var blockedPrompt: String? = null
        var paidPrompt: String? = null
        var paidSubmissions = 0
        var active = 0
        var maxActive = 0
        var returnTwoImages = false

        override suspend fun getBatteryState(): OpusBatteryState = OpusBatteryState()

        override suspend fun encodeVibe(
            imageBytes: ByteArray,
            displayName: String,
            model: NaiModel,
            informationExtracted: Float,
        ): VibeReference = error("not used")

        override suspend fun preflight(parameters: GenerationParameters): GenerationPreflight =
            if (parameters.prompt == paidPrompt) {
                GenerationPreflight.RequiresConfirmation("需要确认")
            } else {
                GenerationPreflight.Free
            }

        override suspend fun generate(parameters: GenerationParameters): GenerationOutcome {
            startedPrompts += parameters.prompt
            if (parameters.prompt == paidPrompt) {
                return GenerationOutcome.NeedsAnlasConfirmation(
                    parameters,
                    GenerationPreflight.RequiresConfirmation("需要确认"),
                )
            }
            active++
            maxActive = maxOf(maxActive, active)
            try {
                if (parameters.prompt == blockedPrompt) releaseBlocked.await()
                return success(parameters.prompt)
            } finally {
                active--
            }
        }

        override suspend fun generateWithAnlas(parameters: GenerationParameters): GenerationOutcome {
            paidSubmissions++
            return success(parameters.prompt)
        }

        private fun success(prompt: String): GenerationOutcome.Success {
            val first = record("image-$prompt-1")
            val additional = if (returnTwoImages) listOf(record("image-$prompt-2")) else emptyList()
            return GenerationOutcome.Success(
                record = first,
                previewBytes = byteArrayOf(1),
                preflight = GenerationPreflight.Free,
                additionalRecords = additional,
            )
        }

        private fun record(id: String) = ImageRecord(
            id = id,
            filePath = "/images/$id.png",
            prompt = "prompt",
            model = NaiModel.V5_FULL.id,
        )
    }

    private class FakeTaskDao : GenerationTaskDao {
        val rows = MutableStateFlow<List<GenerationTaskEntity>>(emptyList())

        override fun observeAll(): Flow<List<GenerationTaskEntity>> = rows

        override suspend fun findById(id: String): GenerationTaskEntity? = rows.value.firstOrNull { it.id == id }

        override suspend fun findPending(): List<GenerationTaskEntity> = rows.value.filter {
            it.status == TaskStatus.QUEUED.name || it.status == TaskStatus.WAITING_ANLAS_CONFIRMATION.name
        }.sortedBy { it.createdAt }

        override suspend fun findNextQueued(): GenerationTaskEntity? = rows.value
            .filter { it.status == TaskStatus.QUEUED.name }
            .minByOrNull { it.createdAt }

        override suspend fun maxCreatedAt(): Long? = rows.value.maxOfOrNull { it.createdAt }

        override suspend fun upsert(task: GenerationTaskEntity) {
            rows.value = (rows.value.filterNot { it.id == task.id } + task).sortedBy { it.createdAt }
        }

        override suspend fun claimQueued(id: String, updatedAt: Long): Int = updateIf(id, TaskStatus.QUEUED) {
            it.copy(status = TaskStatus.RUNNING.name, updatedAt = updatedAt)
        }

        override suspend fun markWaitingForAnlas(id: String, updatedAt: Long): Int = updateIf(id, TaskStatus.RUNNING) {
            it.copy(
                status = TaskStatus.WAITING_ANLAS_CONFIRMATION.name,
                decision = "REQUIRES_CONFIRMATION",
                updatedAt = updatedAt,
            )
        }

        override suspend fun confirmWaitingAnlas(id: String, updatedAt: Long): Int =
            updateIf(id, TaskStatus.WAITING_ANLAS_CONFIRMATION) {
                it.copy(
                    status = TaskStatus.QUEUED.name,
                    decision = "REQUIRES_CONFIRMATION_CONFIRMED",
                    updatedAt = updatedAt,
                )
            }

        override suspend fun cancelPending(id: String, message: String, completedAt: Long): Int {
            val current = findById(id) ?: return 0
            if (current.status !in setOf(TaskStatus.QUEUED.name, TaskStatus.WAITING_ANLAS_CONFIRMATION.name)) return 0
            upsert(
                current.copy(
                    status = TaskStatus.CANCELLED.name,
                    errorMessage = message,
                    completedAt = completedAt,
                    updatedAt = completedAt,
                ),
            )
            return 1
        }

        override suspend fun failPending(id: String, message: String, completedAt: Long): Int {
            val current = findById(id) ?: return 0
            if (current.status !in setOf(TaskStatus.QUEUED.name, TaskStatus.WAITING_ANLAS_CONFIRMATION.name)) return 0
            upsert(
                current.copy(
                    status = TaskStatus.FAILED.name,
                    errorMessage = message,
                    completedAt = completedAt,
                    updatedAt = completedAt,
                ),
            )
            return 1
        }

        override suspend fun finishRunning(
            id: String,
            status: String,
            errorMessage: String?,
            resultImageId: String?,
            resultImageIdsJson: String,
            completedAt: Long,
        ): Int = updateIf(id, TaskStatus.RUNNING) {
            it.copy(
                status = status,
                errorMessage = errorMessage,
                resultImageId = resultImageId,
                resultImageIdsJson = resultImageIdsJson,
                completedAt = completedAt,
                updatedAt = completedAt,
            )
        }

        override suspend fun markInterruptedRunning(message: String, completedAt: Long) {
            rows.value.filter { it.status == TaskStatus.RUNNING.name }.forEach { task ->
                upsert(
                    task.copy(
                        status = TaskStatus.FAILED_UNKNOWN.name,
                        errorMessage = message,
                        completedAt = completedAt,
                        updatedAt = completedAt,
                    ),
                )
            }
        }

        override suspend fun delete(id: String) {
            rows.value = rows.value.filterNot { it.id == id }
        }

        private suspend fun updateIf(
            id: String,
            expectedStatus: TaskStatus,
            transform: (GenerationTaskEntity) -> GenerationTaskEntity,
        ): Int {
            val current = findById(id) ?: return 0
            if (current.status != expectedStatus.name) return 0
            upsert(transform(current))
            return 1
        }
    }

    private companion object {
        fun taskEntity(id: String, status: TaskStatus) = GenerationTaskEntity(
            id = id,
            parametersJson = """{"parameters":{"prompt":"$id"}}""",
            status = status.name,
            decision = null,
            errorMessage = null,
            resultImageId = null,
            createdAt = 1L,
            completedAt = null,
            updatedAt = 1L,
        )
    }
}
