package pt.ubi.di.pdm.gestorbd.ui.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.ubi.di.pdm.gestorbd.data.BdFolder
import pt.ubi.di.pdm.gestorbd.data.Comic
import pt.ubi.di.pdm.gestorbd.data.ComicFilters
import pt.ubi.di.pdm.gestorbd.data.ComicRepository
import pt.ubi.di.pdm.gestorbd.data.GradeFilter
import pt.ubi.di.pdm.gestorbd.data.SortOrder
import pt.ubi.di.pdm.gestorbd.ui.common.app

/** Lista de exemplares de uma pasta (filtros, ordenação, exportação). */
class ComicListViewModel(
    private val repository: ComicRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val folderId: Long = savedStateHandle[ComicListActivity.EXTRA_FOLDER_ID] ?: 0L

    private val _filters = MutableStateFlow(ComicFilters(folderId = folderId))
    val filters: StateFlow<ComicFilters> = _filters.asStateFlow()

    val folder: StateFlow<BdFolder?> = repository.observeFolder(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val comics: StateFlow<List<Comic>?> = _filters
        .flatMapLatest { repository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val publishers: StateFlow<List<String>> = repository.observePublishers(folderId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _deleted = MutableStateFlow(false)
    val folderDeleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun setQuery(query: String) = _filters.update { it.copy(query = query) }

    fun setPublisher(publisher: String?) = _filters.update { it.copy(publisher = publisher) }

    fun setGrade(grade: GradeFilter) = _filters.update { it.copy(grade = grade) }

    fun setSort(sort: SortOrder) = _filters.update { it.copy(sort = sort) }

    fun deleteFolder() {
        val current = folder.value ?: return
        viewModelScope.launch {
            try {
                repository.deleteFolder(current)
                _deleted.value = true
            } catch (_: Exception) {
                // Falha rara: a UI mantém-se; o utilizador pode tentar de novo.
            }
        }
    }

    /** Lista completa da pasta (sem filtros) para gerar o CSV. */
    suspend fun comicsForExport(): List<Comic> = repository.listForExport(folderId)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ComicListViewModel(app.repository, createSavedStateHandle()) }
        }
    }
}
