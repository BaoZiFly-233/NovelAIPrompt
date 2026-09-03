package com.novelstudio.feature.inpaint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelstudio.core.database.ImageDao
import com.novelstudio.core.database.ImageEntity
import com.novelstudio.core.model.ImageRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 局部重绘 ViewModel：提供画板底图候选（优先喜欢/收藏） */
class InpaintViewModel(imageDao: ImageDao) : ViewModel() {

    val records: StateFlow<List<ImageEntity>> = imageDao.observeAll()
        .map { list -> list.sortedByDescending { it.starRating } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
