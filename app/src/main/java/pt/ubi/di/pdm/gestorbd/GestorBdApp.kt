package pt.ubi.di.pdm.gestorbd

import android.app.Application
import pt.ubi.di.pdm.gestorbd.data.AppDatabase
import pt.ubi.di.pdm.gestorbd.data.ComicRepository
import pt.ubi.di.pdm.gestorbd.data.CoverImageStorage
import pt.ubi.di.pdm.gestorbd.ui.common.CoverLoader
import pt.ubi.di.pdm.gestorbd.ui.common.ThemePreferences

/**
 * Application personalizada: ponto único de criação das dependências partilhadas.
 *
 * Em projetos pequenos, a injeção manual via [Application] evita frameworks de DI
 * (Hilt/Koin) e mantém o código fácil de seguir na demonstração.
 * Os objetos usam [lazy] para só serem criados na primeira utilização.
 */
class GestorBdApp : Application() {

    private val database by lazy { AppDatabase.getInstance(this) }
    val coverStorage by lazy { CoverImageStorage(this) }
    val coverLoader by lazy { CoverLoader(coverStorage) }
    val repository by lazy {
        ComicRepository(database.folderDao(), database.comicDao(), coverStorage)
    }

    override fun onCreate() {
        super.onCreate()
        ThemePreferences(this).applySavedTheme()
    }
}
