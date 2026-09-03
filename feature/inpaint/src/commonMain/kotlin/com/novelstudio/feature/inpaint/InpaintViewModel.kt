package com.novelstudio.feature.inpaint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelstudio.core.data.ImageRepository
import com.novelstudio.core.model.ImageRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 局部重绘 ViewModel（只依赖 ImageRepository 抽象）：提供画板底图候选（优先喜欢/收藏） */
class InpaintViewModel(imageRepository: ImageRepository) : ViewModel() {

    val records: StateFlow<List<ImageRecord>> = imageRepository.observeAll()
        .map { list -> list.sortedByDescending { it.starRating } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
