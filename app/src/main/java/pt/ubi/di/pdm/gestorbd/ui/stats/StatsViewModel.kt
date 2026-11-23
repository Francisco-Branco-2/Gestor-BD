package pt.ubi.di.pdm.gestorbd.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pt.ubi.di.pdm.gestorbd.data.CollectionSummary
import pt.ubi.di.pdm.gestorbd.data.ComicRepository
import pt.ubi.di.pdm.gestorbd.data.GradeBucket
import pt.ubi.di.pdm.gestorbd.data.PublisherCount
import pt.ubi.di.pdm.gestorbd.ui.common.app

data class StatsUiState(
    val summary: CollectionSummary,
    val publishers: List<PublisherCount>,
    val grades: List<GradeBucket>,
)

/** Estatísticas agregadas apenas dos exemplares da pasta indicada. */
class StatsViewModel(
    repository: ComicRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val folderId: Long = savedStateHandle[StatsActivity.EXTRA_FOLDER_ID] ?: 0L

    val uiState: StateFlow<StatsUiState?> = combine(
        repository.observeSummary(folderId),
        repository.observePublisherCounts(folderId),
        repository.observeGradeDistribution(folderId),
    ) { summary, publishers, grades ->
        StatsUiState(summary, publishers, fillMissingBuckets(grades))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** O SQL só devolve buckets com dados; preenchemos 1..10 para escala fixa. */
    private fun fillMissingBuckets(buckets: List<GradeBucket>): List<GradeBucket> {
        val totals = buckets.associate { it.bucket to it.total }
        return (1..10).map { GradeBucket(it, totals[it] ?: 0) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { StatsViewModel(app.repository, createSavedStateHandle()) }
        }
    }
}
