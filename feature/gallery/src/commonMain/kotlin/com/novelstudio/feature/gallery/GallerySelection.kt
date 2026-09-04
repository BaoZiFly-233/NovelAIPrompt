package com.novelstudio.feature.gallery

/** 不可变且保序的多选状态；稳定顺序同时定义导出顺序和左右对比槽位。 */
internal class GallerySelection private constructor(
    val ids: List<String>,
) {
    val size: Int get() = ids.size
    val isEmpty: Boolean get() = ids.isEmpty()

    fun contains(id: String): Boolean = id in ids

    fun containsAll(otherIds: Collection<String>): Boolean = ids.containsAll(otherIds)

    fun toggle(id: String): GallerySelection {
        if (id.isBlank()) return this
        return if (id in ids) from(ids - id) else from(ids + id)
    }

    companion object {
        fun empty(): GallerySelection = GallerySelection(emptyList())

        fun from(ids: Iterable<String>): GallerySelection = GallerySelection(
            ids.asSequence().filter(String::isNotBlank).distinct().toList(),
        )
    }
}
