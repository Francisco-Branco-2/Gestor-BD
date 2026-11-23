package pt.ubi.di.pdm.gestorbd.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.ubi.di.pdm.gestorbd.data.Comic
import pt.ubi.di.pdm.gestorbd.data.ComicRepository
import pt.ubi.di.pdm.gestorbd.ui.common.app

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Loaded(val comic: Comic) : DetailUiState
}

sealed interface DetailEvent {
    data object Deleted : DetailEvent
    data object DeleteFailed : DetailEvent
}

class ComicDetailViewModel(
    private val repository: ComicRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val comicId: Long = savedStateHandle[ComicDetailActivity.EXTRA_COMIC_ID] ?: 0L

    /** Flow do Room: ao gravar na Activity de edição, este ecrã atualiza-se sozinho. */
    val uiState: StateFlow<DetailUiState> = repository.observe(comicId)
        .map<Comic?, DetailUiState> { comic -> if (comic == null) DetailUiState.NotFound else DetailUiState.Loaded(comic) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    private val _events = Channel<DetailEvent>(Channel.BUFFERED)
    val events: Flow<DetailEvent> = _events.receiveAsFlow()

    var isDeleting = false
        private set

    fun delete() {
        val comic = (uiState.value as? DetailUiState.Loaded)?.comic ?: return
        isDeleting = true
        viewModelScope.launch {
            try {
                repository.delete(comic)
                _events.send(DetailEvent.Deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isDeleting = false
                _events.send(DetailEvent.DeleteFailed)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ComicDetailViewModel(app.repository, createSavedStateHandle()) }
        }
    }
}
