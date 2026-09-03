package com.novelstudio.feature.swipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelstudio.core.database.ImageDao
import com.novelstudio.core.database.ImageEntity
import com.novelstudio.core.model.ImageRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 滑动喜欢/不喜欢卡片流 ViewModel */
class SwipeViewModel(private val imageDao: ImageDao) : ViewModel() {

    /** 卡组：未打标的普通图片（starRating == 3） */
    val deck: StateFlow<List<ImageEntity>> = imageDao.observeAll()
        .map { list -> list.filter { it.starRating == ImageRecord.STAR_NEUTRAL } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun swipeLike(entity: ImageEntity) {
        viewModelScope.launch { imageDao.updateStarRating(entity.id, ImageRecord.STAR_LIKE) }
    }

    fun swipeDislike(entity: ImageEntity) {
        viewModelScope.launch { imageDao.updateStarRating(entity.id, ImageRecord.STAR_DISLIKE) }
    }
}
