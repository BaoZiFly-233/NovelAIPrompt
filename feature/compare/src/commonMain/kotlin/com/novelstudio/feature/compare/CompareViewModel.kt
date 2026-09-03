package com.novelstudio.feature.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelstudio.core.database.ImageDao
import com.novelstudio.core.database.ImageEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** 对比实验室 ViewModel：提供候选记录与双图选择 */
class CompareViewModel(imageDao: ImageDao) : ViewModel() {

    val records: StateFlow<List<ImageEntity>> = imageDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
