package com.novelstudio.feature.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelstudio.core.data.CompareSelectionStore
import com.novelstudio.core.data.ImageRepository
import com.novelstudio.core.model.ImageRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CompareUiState(
    val requestedIds: List<String> = emptyList(),
    val records: List<ImageRecord> = emptyList(),
    val missingIds: List<String> = emptyList(),
)

/** 对比实验室仅解析图库明确带入的稳定 ID，不再全量物化万级图库。 */
class CompareViewModel(
    imageRepository: ImageRepository,
    private val selectionStore: CompareSelectionStore,
) : ViewModel() {

    val state: StateFlow<CompareUiState> = selectionStore.selectedIds
        .map { ids ->
            val records = imageRepository.getByIds(ids)
            val foundIds = records.mapTo(hashSetOf(), ImageRecord::id)
            CompareUiState(
                requestedIds = ids,
                records = records,
                missingIds = ids.filterNot(foundIds::contains),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareUiState())

    fun clearSelection() = selectionStore.clear()
}
