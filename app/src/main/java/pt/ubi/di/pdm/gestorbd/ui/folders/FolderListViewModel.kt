package pt.ubi.di.pdm.gestorbd.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import pt.ubi.di.pdm.gestorbd.data.ComicRepository
import pt.ubi.di.pdm.gestorbd.data.FolderWithCount
import pt.ubi.di.pdm.gestorbd.ui.common.app

/** Lista de pastas/coleções com contagem de exemplares. */
class FolderListViewModel(repository: ComicRepository) : ViewModel() {

    val folders: StateFlow<List<FolderWithCount>?> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { FolderListViewModel(app.repository) }
        }
    }
}
