package pt.ubi.di.pdm.gestorbd.ui.common

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import pt.ubi.di.pdm.gestorbd.GestorBdApp

/**
 * Atalho usado nas `Factory` dos ViewModels para obter [GestorBdApp]
 * (e assim o Repository) sem acoplar a Activity concreta.
 */
val CreationExtras.app: GestorBdApp
    get() = this[APPLICATION_KEY] as GestorBdApp
