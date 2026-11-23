package pt.ubi.di.pdm.gestorbd.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Base de dados Room (versão 3: campo [Comic.certification]).
 * [fallbackToDestructiveMigration] recria a BD em mudanças de esquema de demo.
 */
@Database(
    entities = [BdFolder::class, Comic::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun comicDao(): ComicDao

    companion object {
        private const val DATABASE_NAME = "gestor_bd.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { instance = it }
            }
    }
}
