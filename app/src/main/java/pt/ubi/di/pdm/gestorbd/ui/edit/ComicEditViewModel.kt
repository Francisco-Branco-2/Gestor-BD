package pt.ubi.di.pdm.gestorbd.ui.edit

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import pt.ubi.di.pdm.gestorbd.data.Comic
import pt.ubi.di.pdm.gestorbd.data.ComicRepository
import pt.ubi.di.pdm.gestorbd.data.CoverImageStorage
import pt.ubi.di.pdm.gestorbd.domain.ComicValidator
import pt.ubi.di.pdm.gestorbd.ui.common.app
import java.time.LocalDate

sealed interface EditEvent {
    /** Preenche o formulário com os dados do exemplar a editar (apenas uma vez). */
    data class Populate(val comic: Comic) : EditEvent
    data class ValidationFailed(val errors: Map<ComicValidator.Field, ComicValidator.Error>) : EditEvent
    data object Saved : EditEvent
    data object SaveFailed : EditEvent
    data object ImageFailed : EditEvent
    data object NotFound : EditEvent
}

/**
 * ViewModel do formulário criar/editar.
 *
 * Data e capa vivem no [SavedStateHandle] para sobreviver a rotação e à morte
 * do processo pelo sistema (low memory). Capas escolhidas e não gravadas são
 * apagadas em [onCleared] para não deixar ficheiros órfãos em filesDir.
 */
class ComicEditViewModel(
    private val repository: ComicRepository,
    private val coverStorage: CoverImageStorage,
    private val state: SavedStateHandle,
) : ViewModel() {

    private val comicId: Long = state[ComicEditActivity.EXTRA_COMIC_ID] ?: 0L
    /** Pasta destino: no create vem do Intent; na edição herda-se do exemplar carregado. */
    private val folderId: Long = state[ComicEditActivity.EXTRA_FOLDER_ID] ?: 0L
    val isEditing: Boolean get() = comicId != 0L

    private var original: Comic? = null
    private var isSaving = false
    private var saved = false
    /** Snapshot dos campos de texto no momento do Populate (create = vazio). */
    private var baselineText: BaselineText = BaselineText()
    /** Valor por omissão do slider no layout (create); na edição é substituído em loadComic. */
    private var baselineGrade: Double = 9.0
    private var baselineDate: LocalDate? = if (isEditing) null else LocalDate.now()
    private var baselineCover: String? = null

    private val _events = Channel<EditEvent>(Channel.BUFFERED)
    val events: Flow<EditEvent> = _events.receiveAsFlow()

    val acquisitionDate: Flow<LocalDate?> =
        state.getStateFlow<Long?>(KEY_DATE, null).map { it?.let(LocalDate::ofEpochDay) }

    val currentDate: LocalDate?
        get() = state.get<Long>(KEY_DATE)?.let(LocalDate::ofEpochDay)

    val coverFileName: StateFlow<String?> = state.getStateFlow(KEY_COVER, null)

    val publishers: Flow<List<String>> = repository.observePublishers(folderId)

    init {
        if (isEditing) {
            viewModelScope.launch { loadComic() }
        } else if (currentDate == null) {
            state[KEY_DATE] = LocalDate.now().toEpochDay()
            baselineDate = currentDate
        }
    }

    private suspend fun loadComic() {
        val comic = repository.get(comicId)
        if (comic == null) {
            _events.send(EditEvent.NotFound)
            return
        }
        original = comic
        // Baseline = valores gravados (também após rotação, para o DiscardChangesGuard).
        captureBaseline(comic)
        if (state.get<Boolean>(KEY_POPULATED) == true) return
        state[KEY_DATE] = comic.acquisitionDate.toEpochDay()
        state[KEY_COVER] = comic.coverFileName
        state[KEY_POPULATED] = true
        _events.send(EditEvent.Populate(comic))
    }

    private fun captureBaseline(comic: Comic) {
        baselineText = BaselineText(
            title = comic.title,
            publisher = comic.publisher,
            issue = comic.issueNumber,
            certification = comic.certification,
            notes = comic.notes,
        )
        baselineGrade = comic.grade
        baselineDate = comic.acquisitionDate
        baselineCover = comic.coverFileName
    }

    fun setAcquisitionDate(date: LocalDate) {
        state[KEY_DATE] = date.toEpochDay()
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
                // Imagem corrompida, formato não suportado ou sem permissão de leitura.
                _events.send(EditEvent.ImageFailed)
            }
        }
    }

    fun removeCover() {
        viewModelScope.launch {
            discardPendingCover()
            state[KEY_COVER] = null
        }
    }

    /** Apaga a capa escolhida nesta edição (a original só é apagada quando se grava). */
    private suspend fun discardPendingCover() {
        val current = coverFileName.value
        if (current != null && current != original?.coverFileName) coverStorage.delete(current)
    }

    /**
     * Compara o formulário atual com o baseline (valores iniciais / carregados).
     * Usado pelo DiscardChangesGuard antes de sair sem gravar.
     */
    fun isDirty(text: BaselineText, grade: Double): Boolean {
        if (saved) return false
        if (text != baselineText) return true
        if (grade != baselineGrade) return true
        if (currentDate != baselineDate) return true
        if (coverFileName.value != baselineCover) return true
        return false
    }

    fun save(input: ComicValidator.Input) {
        if (isSaving || (isEditing && original == null)) return
        isSaving = true
        viewModelScope.launch {
            try {
                val errors = ComicValidator.validate(input, LocalDate.now())
                if (errors.isNotEmpty()) {
                    _events.send(EditEvent.ValidationFailed(errors))
                    return@launch
                }
                val comic = Comic(
                    id = comicId,
                    folderId = original?.folderId ?: folderId,
                    title = input.title.trim(),
                    publisher = input.publisher.trim(),
                    issueNumber = input.issueNumber.trim(),
                    grade = input.grade,
                    acquisitionDate = input.acquisitionDate!!,
                    notes = input.notes.trim(),
                    certification = input.certification.trim(),
                    coverFileName = coverFileName.value,
                )
                repository.save(comic, original?.coverFileName)
                saved = true
                _events.send(EditEvent.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Falha na BD ou no sistema de ficheiros: a UI mostra mensagem sem fechar a app.
                _events.send(EditEvent.SaveFailed)
            } finally {
                isSaving = false
            }
        }
    }

    override fun onCleared() {
        // Se o utilizador saiu sem gravar, a capa nova que escolheu deixa de ser necessária.
        if (!saved) {
            val current = coverFileName.value
            if (current != null && current != original?.coverFileName) coverStorage.deleteNow(current)
        }
    }

    /** Campos de texto comparáveis para detetar alterações por gravar. */
    data class BaselineText(
        val title: String = "",
        val publisher: String = "",
        val issue: String = "",
        val certification: String = "",
        val notes: String = "",
    )

    companion object {
        private const val KEY_DATE = "acquisition_date"
        private const val KEY_COVER = "cover_file_name"
        private const val KEY_POPULATED = "populated"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ComicEditViewModel(app.repository, app.coverStorage, createSavedStateHandle()) }
        }
    }
}
