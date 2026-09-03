package com.novelstudio.feature.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelstudio.core.data.ImageRepository
import com.novelstudio.core.model.ImageRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** 对比实验室 ViewModel（只依赖 ImageRepository 抽象）：提供候选记录与双图选择 */
class CompareViewModel(imageRepository: ImageRepository) : ViewModel() {

    val records: StateFlow<List<ImageRecord>> = imageRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
