package com.novelstudio.feature.swipe

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 确保同一张可见卡片最多只产生一个进行中的偏好决策。 */
internal class SwipeActionGate {
    private val mutex = Mutex()
    private val active = mutableSetOf<String>()

    suspend fun tryAcquire(id: String): Boolean = mutex.withLock { active.add(id) }
    suspend fun release(id: String) { mutex.withLock { active.remove(id) } }
}
