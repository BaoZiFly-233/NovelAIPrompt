package com.novelstudio.core.data

import com.novelstudio.core.database.GenerationTaskDao
import com.novelstudio.core.database.GenerationTaskEntity
import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.GenerationPreflight
import com.novelstudio.core.model.TaskStatus
import com.novelstudio.core.network.NaiApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** 队列中的不可变生成快照及其持久化状态。 */
data class GenerationQueueItem(
    val id: String,
    val status: TaskStatus,
    val parameters: GenerationParameters,
    val preflight: GenerationPreflight? = null,
    val errorMessage: String? = null,
    val resultImageIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val completedAt: Long? = null,
    val isSnapshotCorrupted: Boolean = false,
)

data class GenerationQueueSuccess(val taskId: String, val outcome: GenerationOutcome.Success)
data class GenerationQueueFailure(val taskId: String, val outcome: GenerationOutcome.Failure)

interface GenerationQueue {
    val state: StateFlow<List<GenerationQueueItem>>
    val success: SharedFlow<GenerationQueueSuccess>
    val failure: SharedFlow<GenerationQueueFailure>

    suspend fun enqueue(parameters: GenerationParameters): String
    suspend fun confirmAnlas(taskId: String): Boolean
    suspend fun cancel(taskId: String): Boolean
}

/**
 * 持久化 FIFO 生成队列。每次只运行一个任务；任何已开始的计费提交均不会自动重试。
 * WAITING_ANLAS_CONFIRMATION 必须由绑定 taskId 的显式确认重新放行。
 */
class GenerationQueueController(
    private val repository: GenerationRepository,
    private val dao: GenerationTaskDao,
    private val scope: CoroutineScope,
    private val idGenerator: () -> String = { GenerationRepositoryImpl.randomId() },
    private val clock: () -> Long = { System.currentTimeMillis() },
) : GenerationQueue {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<List<GenerationQueueItem>>(emptyList())
    override val state: StateFlow<List<GenerationQueueItem>> = _state.asStateFlow()

    private val _success = MutableSharedFlow<GenerationQueueSuccess>(extraBufferCapacity = EVENT_BUFFER_SIZE)
    override val success: SharedFlow<GenerationQueueSuccess> = _success.asSharedFlow()
    private val _failure = MutableSharedFlow<GenerationQueueFailure>(extraBufferCapacity = EVENT_BUFFER_SIZE)
    override val failure: SharedFlow<GenerationQueueFailure> = _failure.asSharedFlow()

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val transitionMutex = Mutex()
    private var activeId: String? = null
    private var activeExecution: Deferred<GenerationOutcome>? = null

    init {
        scope.launch {
            dao.observeAll().collect { rows ->
                _state.value = rows.map(::toQueueItem)
            }
        }
        scope.launch { recoverAndRun() }
    }

    override suspend fun enqueue(parameters: GenerationParameters): String =
        transitionMutex.withLock {
            val id = idGenerator()
            val persistedMax = dao.maxCreatedAt()
            check(persistedMax != Long.MAX_VALUE) { "任务时间序列已溢出" }
            val now = maxOf(clock(), persistedMax?.plus(1L) ?: Long.MIN_VALUE)
            val snapshot = Snapshot(parameters = parameters)
            dao.upsert(
                GenerationTaskEntity(
                    id = id,
                    parametersJson = json.encodeToString(Snapshot.serializer(), snapshot),
                    status = TaskStatus.QUEUED.name,
                    decision = null,
                    errorMessage = null,
                    resultImageId = null,
                    createdAt = now,
                    completedAt = null,
                    updatedAt = now,
                ),
            )
            wake.trySend(Unit)
            id
        }

    /** 重复确认是幂等的；只有 WAITING 状态能首次转回已授权的 QUEUED。 */
    override suspend fun confirmAnlas(taskId: String): Boolean = transitionMutex.withLock {
        val changed = dao.confirmWaitingAnlas(taskId, clock()) == 1
        val alreadyConfirmed = if (!changed) {
            dao.findById(taskId)?.let {
                it.status == TaskStatus.QUEUED.name && it.decision == PREFLIGHT_CONFIRMED
            } == true
        } else {
            true
        }
        if (alreadyConfirmed) wake.trySend(Unit)
        alreadyConfirmed
    }

    /**
     * 排队/待确认任务可安全取消；运行中只停止本地等待并记为结果未知，绝不重新提交。
     */
    override suspend fun cancel(taskId: String): Boolean = transitionMutex.withLock {
        val now = clock()
        if (dao.cancelPending(taskId, "用户取消了尚未提交的任务", now) == 1) {
            wake.trySend(Unit)
            return@withLock true
        }
        if (activeId == taskId && activeExecution?.isActive == true) {
            activeExecution?.cancel(CancellationException("用户停止本地等待"))
            return@withLock true
        }
        false
    }

    private suspend fun recoverAndRun() {
        withContext(NonCancellable) {
            dao.markInterruptedRunning(INTERRUPTED_MESSAGE, clock())
            dao.findPending().forEach { task ->
                if (runCatching { json.decodeFromString(Snapshot.serializer(), task.parametersJson) }.isFailure) {
                    dao.failPending(task.id, "任务参数快照损坏，已停止且不会提交", clock())
                }
            }
        }
        while (true) {
            val task = dao.findNextQueued()
            if (task == null) {
                wake.receive()
            } else {
                process(task)
            }
        }
    }

    private suspend fun process(task: GenerationTaskEntity): Unit = coroutineScope {
        val snapshot = runCatching {
            json.decodeFromString(Snapshot.serializer(), task.parametersJson)
        }.getOrElse {
            if (dao.claimQueued(task.id, clock()) == 1) {
                finishFailure(task.id, GenerationOutcome.Failure("任务参数快照损坏", it))
            }
            return@coroutineScope
        }

        val execution = transitionMutex.withLock {
            if (dao.claimQueued(task.id, clock()) != 1) return@withLock null
            this@coroutineScope.async(start = CoroutineStart.LAZY) {
                if (task.decision == PREFLIGHT_CONFIRMED) {
                    repository.generateWithAnlas(snapshot.parameters)
                } else {
                    repository.generate(snapshot.parameters)
                }
            }.also {
                activeId = task.id
                activeExecution = it
            }
        } ?: return@coroutineScope

        try {
            when (val outcome = execution.await()) {
                is GenerationOutcome.Success -> finishSuccess(task.id, outcome)
                is GenerationOutcome.NeedsAnlasConfirmation -> {
                    dao.markWaitingForAnlas(task.id, clock())
                }
                is GenerationOutcome.Failure -> finishFailure(task.id, outcome)
            }
        } catch (_: CancellationException) {
            finishFailure(
                task.id,
                GenerationOutcome.Failure(
                    message = CANCELLED_RUNNING_MESSAGE,
                    submissionMayHaveCompleted = true,
                ),
            )
        } catch (throwable: Throwable) {
            finishFailure(
                task.id,
                GenerationOutcome.Failure(
                    message = NaiApiException.describe(throwable),
                    cause = throwable,
                    submissionMayHaveCompleted = true,
                ),
            )
        } finally {
            withContext(NonCancellable) {
                transitionMutex.withLock {
                    if (activeId == task.id) {
                        activeId = null
                        activeExecution = null
                    }
                }
                wake.trySend(Unit)
            }
        }
    }

    private suspend fun finishSuccess(taskId: String, outcome: GenerationOutcome.Success) {
        val resultIds = outcome.records.map { it.id }
        val now = clock()
        val changed = dao.finishRunning(
            id = taskId,
            status = TaskStatus.SUCCEEDED.name,
            errorMessage = null,
            resultImageId = resultIds.firstOrNull(),
            resultImageIdsJson = json.encodeToString(ListSerializer(String.serializer()), resultIds),
            completedAt = now,
        )
        if (changed == 1) _success.emit(GenerationQueueSuccess(taskId, outcome))
    }

    private suspend fun finishFailure(taskId: String, outcome: GenerationOutcome.Failure) {
        val status = if (outcome.submissionMayHaveCompleted) TaskStatus.FAILED_UNKNOWN else TaskStatus.FAILED
        val changed = withContext(NonCancellable) {
            dao.finishRunning(
                id = taskId,
                status = status.name,
                errorMessage = outcome.message,
                resultImageId = null,
                resultImageIdsJson = "[]",
                completedAt = clock(),
            )
        }
        if (changed == 1) _failure.emit(GenerationQueueFailure(taskId, outcome))
    }

    private fun toQueueItem(row: GenerationTaskEntity): GenerationQueueItem {
        val snapshot = runCatching {
            json.decodeFromString(Snapshot.serializer(), row.parametersJson)
        }.getOrNull()
        val status = runCatching { TaskStatus.valueOf(row.status) }.getOrDefault(TaskStatus.FAILED)
        val preflight = if (row.decision == PREFLIGHT_CONFIRMED) {
            GenerationPreflight.RequiresConfirmation("已由用户确认 ImageAnlas 请求")
        } else {
            null
        }
        val resultIds = runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), row.resultImageIdsJson)
        }.getOrDefault(row.resultImageId?.let(::listOf).orEmpty())
        return GenerationQueueItem(
            id = row.id,
            status = status,
            parameters = snapshot?.parameters ?: GenerationParameters(),
            preflight = preflight,
            errorMessage = row.errorMessage ?: if (snapshot == null) "任务参数快照损坏" else null,
            resultImageIds = resultIds,
            createdAt = row.createdAt,
            completedAt = row.completedAt,
            isSnapshotCorrupted = snapshot == null,
        )
    }

    @Serializable
    private data class Snapshot(
        val parameters: GenerationParameters,
    )

    private companion object {
        const val EVENT_BUFFER_SIZE = 16
        const val INTERRUPTED_MESSAGE = "上次运行被中断，提交结果未知；为避免重复扣费，本任务不会自动重试"
        const val CANCELLED_RUNNING_MESSAGE = "已停止本地等待；服务端可能已完成生成，本任务不会自动重试"
        const val PREFLIGHT_CONFIRMED = "REQUIRES_CONFIRMATION_CONFIRMED"
    }
}
