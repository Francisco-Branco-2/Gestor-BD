package pt.ubi.di.pdm.gestorbd.ui.folders

import android.net.Uri
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import pt.ubi.di.pdm.gestorbd.data.BdFolder
import pt.ubi.di.pdm.gestorbd.data.ComicRepository
import pt.ubi.di.pdm.gestorbd.data.CoverImageStorage
import pt.ubi.di.pdm.gestorbd.domain.FolderValidator
import pt.ubi.di.pdm.gestorbd.ui.common.app

sealed interface FolderEditEvent {
    data class Populate(val folder: BdFolder) : FolderEditEvent
    data class ValidationFailed(val errors: Map<FolderValidator.Field, FolderValidator.Error>) : FolderEditEvent
    data object Saved : FolderEditEvent
    data object SaveFailed : FolderEditEvent
    data object ImageFailed : FolderEditEvent
    data object NotFound : FolderEditEvent
}

/**
 * Formulário criar/editar pasta.
 * Espelha a lógica do ComicEditViewModel, mas só com nome, autores e capa.
 */
class FolderEditViewModel(
    private val repository: ComicRepository,
    private val coverStorage: CoverImageStorage,
    private val state: SavedStateHandle,
) : ViewModel() {

    private val folderId: Long = state[FolderEditActivity.EXTRA_FOLDER_ID] ?: 0L
    val isEditing: Boolean get() = folderId != 0L

    private var original: BdFolder? = null
    private var isSaving = false
    private var saved = false
    private var baselineName: String = ""
    private var baselineAuthors: String = ""
    private var baselineCover: String? = null

    private val _events = Channel<FolderEditEvent>(Channel.BUFFERED)
    val events: Flow<FolderEditEvent> = _events.receiveAsFlow()

    val coverFileName: StateFlow<String?> = state.getStateFlow(KEY_COVER, null)

    init {
        if (isEditing) viewModelScope.launch { loadFolder() }
    }

    private suspend fun loadFolder() {
        val folder = repository.getFolder(folderId)
        if (folder == null) {
            _events.send(FolderEditEvent.NotFound)
            return
        }
        original = folder
        baselineName = folder.name
        baselineAuthors = folder.authors
        baselineCover = folder.coverFileName
        if (state.get<Boolean>(KEY_POPULATED) == true) return
        state[KEY_COVER] = folder.coverFileName
        state[KEY_POPULATED] = true
        _events.send(FolderEditEvent.Populate(folder))
    }

    fun onCoverPicked(uri: Uri) {
        viewModelScope.launch {
            try {
                val newFile = coverStorage.importFromUri(uri)
                discardPendingCover()
                state[KEY_COVER] = newFile
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(FolderEditEvent.ImageFailed)
            }
        }
    }

    fun removeCover() {
        viewModelScope.launch {
            discardPendingCover()
            state[KEY_COVER] = null
        }
    }

    private suspend fun discardPendingCover() {
        val current = coverFileName.value
        if (current != null && current != original?.coverFileName) coverStorage.delete(current)
    }

    /** true se nome, autores ou capa diferem do estado inicial. */
    fun isDirty(name: String, authors: String): Boolean {
        if (saved) return false
        return name != baselineName || authors != baselineAuthors || coverFileName.value != baselineCover
    }

    fun save(input: FolderValidator.Input) {
        if (isSaving || (isEditing && original == null)) return
        isSaving = true
        viewModelScope.launch {
            try {
                val errors = FolderValidator.validate(input)
                if (errors.isNotEmpty()) {
                    _events.send(FolderEditEvent.ValidationFailed(errors))
                    return@launch
                }
                val folder = BdFolder(
                    id = folderId,
                    name = input.name.trim(),
                    authors = input.authors.trim(),
                    coverFileName = coverFileName.value,
                )
                repository.saveFolder(folder, original?.coverFileName)
                saved = true
                _events.send(FolderEditEvent.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(FolderEditEvent.SaveFailed)
            } finally {
                isSaving = false
            }
        }
    }

    override fun onCleared() {
        if (!saved) {
            val current = coverFileName.value
            if (current != null && current != original?.coverFileName) coverStorage.deleteNow(current)
        }
    }

    companion object {
        private const val KEY_COVER = "cover_file_name"
        private const val KEY_POPULATED = "populated"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { FolderEditViewModel(app.repository, app.coverStorage, createSavedStateHandle()) }
        }
    }
}
