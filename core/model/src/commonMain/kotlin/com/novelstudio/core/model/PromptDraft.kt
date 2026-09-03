package com.novelstudio.core.model

/**
 * 「一键回填到工作台」共享草稿契约：
 * 图库灯箱把 PNG 元数据写入 DraftStore，工作台在初始化时采纳。
 */
data class PromptDraft(
    val prompt: String,
    val uc: String,
    val model: NaiModel = NaiModel.V5_FULL,
    val width: Int = 1024,
    val height: Int = 1024,
    val seed: Long = -1L,
    val steps: Int = 28,
    val scale: Float = 6f,
    val sampler: Sampler = Sampler.K_EULER,
)

interface PromptDraftStore {
    /** 最近一次回填的草稿，工作台消费后置空 */
    fun peek(): PromptDraft?
    fun push(draft: PromptDraft)
    fun clear()
}

/** 进程内简单实现（Koin 单例） */
class InMemoryPromptDraftStore : PromptDraftStore {
    private var draft: PromptDraft? = null

    override fun peek(): PromptDraft? = draft

    override fun push(draft: PromptDraft) {
        this.draft = draft
    }

    override fun clear() {
        draft = null
    }
}
